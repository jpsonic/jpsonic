/*
 * This file is part of Jpsonic.
 *
 * Jpsonic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Jpsonic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * (C) 2009 Sindre Mehus
 * (C) 2016 Airsonic Authors
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.service.scrobbler;

import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import com.tesshu.jpsonic.SuppressFBWarnings;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.concurrent.ConcurrentUtils;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.util.PlayerUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides services for "audioscrobbling" at www.last.fm. <br/>
 * See https://www.last.fm/api/submissions
 */
public class LastFMScrobbler {

    private static final Logger LOG = LoggerFactory.getLogger(LastFMScrobbler.class);
    private static final int MAX_PENDING_REGISTRATION = 2000;
    private static final RequestConfig REQUEST_CONFIG = RequestConfig
        .custom()
        .setConnectionRequestTimeout(Timeout.ofSeconds(15))
        .setResponseTimeout(Timeout.ofSeconds(15))
        .build();
    private static final String MSG_PREF_ON_FAIL = "Failed to scrobble song '";

    private final LinkedBlockingQueue<RegistrationData> queue;
    private final ReentrantLock registrationLock = new ReentrantLock();

    private RegistrationTask task;

    public LastFMScrobbler() {
        queue = new LinkedBlockingQueue<>();
    }

    /**
     * Registers the given media file at www.last.fm. This method returns
     * immediately, the actual registration is done by a separate thread.
     *
     * @param mediaFile  The media file to register.
     * @param username   last.fm username.
     * @param password   last.fm password.
     * @param submission Whether this is a submission or a now playing notification.
     * @param time       Event time, or {@code null} to use current time.
     */
    public void register(MediaFile mediaFile, String username, String password, boolean submission,
            Instant time, Executor executor) {

        registrationLock.lock();
        try {

            if (task == null) {
                task = new RegistrationTask(queue);
                executor.execute(task);
            }

            if (queue.size() >= MAX_PENDING_REGISTRATION) {
                writeWarn("Last.fm scrobbler queue is full. Ignoring " + mediaFile);
                return;
            }

            RegistrationData registrationData = new RegistrationData(mediaFile, username, password,
                    submission, time);
            try {
                queue.put(registrationData);
            } catch (InterruptedException e) {
                writeWarn("Interrupted while queuing Last.fm scrobble.", e);
            }
        } finally {
            registrationLock.unlock();
        }
    }

    /**
     * Scrobbles the given song data at last.fm, using the protocol defined at
     * http://www.last.fm/api/submissions.
     *
     * @param registrationData Registration data for the song.
     */
    protected static final void scrobble(RegistrationData registrationData)
            throws ExecutionException {
        if (registrationData == null) {
            return;
        }

        String[] lines = authenticate(registrationData);
        if (lines.length == 0) {
            return;
        }

        String sessionId = lines[1];
        String nowPlayingUrl = lines[2];
        String submissionUrl = lines[3];

        if (registrationData.isSubmission()) {
            lines = registerSubmission(registrationData, sessionId, submissionUrl);
        } else {
            lines = registerNowPlaying(registrationData, sessionId, nowPlayingUrl);
        }

        if (lines[0].startsWith("FAILED")) {
            writeWarn(MSG_PREF_ON_FAIL + registrationData.getTitle() + "' at Last.fm: " + lines[0]);
        } else if (lines[0].startsWith("BADSESSION")) {
            writeWarn(MSG_PREF_ON_FAIL + registrationData.getTitle()
                    + "' at Last.fm.  Invalid session.");
        } else if (LOG.isInfoEnabled() && lines[0].startsWith("OK")) {
            LOG
                .info("Successfully registered "
                        + (registrationData.isSubmission() ? "submission" : "now playing")
                        + " for song '" + registrationData.getTitle() + "' for user "
                        + registrationData.getUsername() + " at Last.fm: "
                        + registrationData.getTime());
        }
    }

    /**
     * Returns the following lines if authentication succeeds:
     * <p/>
     * Line 0: Always "OK" Line 1: Session ID, e.g.,
     * "17E61E13454CDD8B68E8D7DEEEDF6170" Line 2: URL to use for now playing, e.g.,
     * "http://post.audioscrobbler.com:80/np_1.2" Line 3: URL to use for
     * submissions, e.g., "http://post2.audioscrobbler.com:80/protocol_1.2"
     * <p/>
     * If authentication fails, <code>null</code> is returned.
     */
    private static String[] authenticate(RegistrationData registrationData)
            throws ExecutionException {
        String clientId = "sub";
        String clientVersion = "0.1";
        long epochSecond = Instant.now().getEpochSecond();
        String authToken = calculateAuthenticationToken(registrationData.getPassword(),
                epochSecond);
        // NOTE: HTTPS support DOES NOT WORK on the AudioScrobbler v1 API.
        URI uri;
        try {
            uri = new URI("http", /* userInfo= */ null, "post.audioscrobbler.com", -1, "/",
                    String
                        .format("hs=true&p=1.2.1&c=%s&v=%s&u=%s&t=%s&a=%s", clientId, clientVersion,
                                registrationData.getUsername(), epochSecond, authToken),
                    /* fragment= */ null);
        } catch (URISyntaxException e) {
            throw new ExecutionException("Unable to generate AudioScrobbler URI.", e);
        }

        String[] lines = executeGetRequest(uri);

        if (lines[0].startsWith("BANNED")) {
            writeWarn(MSG_PREF_ON_FAIL + registrationData.getTitle()
                    + "' at Last.fm. Client version is banned.");
            return new String[0];
        }

        if (lines[0].startsWith("BADAUTH")) {
            writeWarn(MSG_PREF_ON_FAIL + registrationData.getTitle()
                    + "' at Last.fm. Wrong username or password.");
            return new String[0];
        }

        if (lines[0].startsWith("BADTIME")) {
            writeWarn(MSG_PREF_ON_FAIL + registrationData.getTitle()
                    + "' at Last.fm. Bad timestamp, please check local clock.");
            return new String[0];
        }

        if (lines[0].startsWith("FAILED")) {
            writeWarn(MSG_PREF_ON_FAIL + registrationData.getTitle() + "' at Last.fm: " + lines[0]);
            return new String[0];
        }

        if (!lines[0].startsWith("OK")) {
            writeWarn(MSG_PREF_ON_FAIL + registrationData.getTitle()
                    + "' at Last.fm.  Unknown response: " + lines[0]);
            return new String[0];
        }

        return lines;
    }

    protected static void writeWarn(String msg) {
        if (LOG.isWarnEnabled()) {
            LOG.warn(msg);
        }
    }

    protected static void writeWarn(String msg, Throwable cause) {
        if (LOG.isWarnEnabled()) {
            LOG.warn(msg, cause);
        }
    }

    private static String[] registerSubmission(RegistrationData registrationData, String sessionId,
            String url) throws ExecutionException {
        Map<String, String> params = LegacyMap.of();
        params.put("s", sessionId);
        params.put("a[0]", registrationData.getArtist());
        params.put("t[0]", registrationData.getTitle());
        params.put("i[0]", String.valueOf(registrationData.getTime().getEpochSecond()));
        params.put("o[0]", "P");
        params.put("r[0]", "");
        params.put("l[0]", String.valueOf(registrationData.getDuration()));
        params.put("b[0]", registrationData.getAlbum());
        params.put("n[0]", "");
        params.put("m[0]", "");
        return executePostRequest(url, params);
    }

    private static String[] registerNowPlaying(RegistrationData registrationData, String sessionId,
            String url) throws ExecutionException {
        Map<String, String> params = LegacyMap.of();
        params.put("s", sessionId);
        params.put("a", registrationData.getArtist());
        params.put("t", registrationData.getTitle());
        params.put("b", registrationData.getAlbum());
        params.put("l", String.valueOf(registrationData.getDuration()));
        params.put("n", "");
        params.put("m", "");
        return executePostRequest(url, params);
    }

    @SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_MD5", justification = "That's Last.fm Auth API Spec 1.0 (https://www.last.fm/api/authspec")
    private static String calculateAuthenticationToken(String password, long timestamp) {
        return DigestUtils.md5Hex(DigestUtils.md5Hex(password) + timestamp);
    }

    private static String[] executeGetRequest(URI url) throws ExecutionException {
        HttpGet method = new HttpGet(url);
        method.setConfig(REQUEST_CONFIG);
        return executeRequest(method);
    }

    private static String[] executePostRequest(String url, Map<String, String> parameters)
            throws ExecutionException {
        List<NameValuePair> params = new ArrayList<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            params.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }

        HttpPost request = new HttpPost(url);
        request.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
        request.setConfig(REQUEST_CONFIG);
        return executeRequest(request);
    }

    private static String[] executeRequest(HttpUriRequest request) throws ExecutionException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpClientResponseHandler<String> responseHandler = new BasicHttpClientResponseHandler();
            String response = client.execute(request, responseHandler);
            return response.split("\\r?\\n");
        } catch (IOException e) {
            throw new ExecutionException("Unable to execute Http request.", e);
        }
    }

    /*
     * httpClient can be reused #833
     */
    private static class RegistrationTask implements Runnable {

        private final LinkedBlockingQueue<RegistrationData> queue;

        private static final Logger LOG = LoggerFactory.getLogger(RegistrationTask.class);

        RegistrationTask(LinkedBlockingQueue<RegistrationData> queue) {
            this.queue = queue;
        }

        @Override
        public void run() {
            while (true) {
                RegistrationData registrationData = null;
                try {
                    try {
                        registrationData = queue.take();
                    } catch (InterruptedException e) {
                        writeWarn("Error in Last.fm registration.", e);
                        break;
                    }
                    scrobble(registrationData);
                } catch (ExecutionException e) {
                    ConcurrentUtils.handleCauseUnchecked(e);
                    if (e.getCause() instanceof IOException) {
                        handleError(registrationData, e);
                    } else {
                        writeWarn("Error in Last.fm registration.", e);
                    }
                    break;
                }
            }
        }

        private void handleError(RegistrationData registrationData, Throwable cause) {
            try {
                queue.put(registrationData);
                if (LOG.isInfoEnabled()) {
                    LOG
                        .info("Last.fm registration for '" + registrationData.getTitle()
                                + "' encountered network error. Will try again later. In queue: "
                                + queue.size(), cause);
                }
            } catch (InterruptedException x) {
                if (LOG.isErrorEnabled()) {
                    LOG
                        .error("Failed to reschedule Last.fm registration for '"
                                + registrationData.getTitle() + "'.", cause);
                }
            }
            try {
                Thread.sleep(60L * 1000L); // Wait 60 seconds.
            } catch (InterruptedException e) {
                if (LOG.isErrorEnabled()) {
                    LOG
                        .error("Failed to sleep after Last.fm registration failure for '"
                                + registrationData.getTitle() + "'.", e);
                }
            }
        }
    }

    private static class RegistrationData {

        private final String username;
        private final String password;
        private final String artist;
        private final String album;
        private final String title;
        private final int duration;
        private final Instant time;
        final boolean submission;

        RegistrationData(MediaFile mediaFile, String username, String password, boolean submission,
                Instant time) {
            this.username = username;
            this.password = password;
            this.artist = mediaFile.getArtist();
            this.album = mediaFile.getAlbumName();
            this.title = mediaFile.getTitle();
            this.duration = PlayerUtils.defaultIfNull(mediaFile.getDurationSeconds(), 0);
            this.time = time == null ? now() : time;
            this.submission = submission;
        }

        String getUsername() {
            return username;
        }

        String getPassword() {
            return password;
        }

        String getArtist() {
            return artist;
        }

        String getAlbum() {
            return album;
        }

        String getTitle() {
            return title;
        }

        int getDuration() {
            return duration;
        }

        Instant getTime() {
            return time;
        }

        boolean isSubmission() {
            return submission;
        }
    }
}

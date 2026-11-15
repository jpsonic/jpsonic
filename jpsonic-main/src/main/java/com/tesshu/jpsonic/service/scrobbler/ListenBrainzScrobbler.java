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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.concurrent.ConcurrentUtils;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.util.StringUtil;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Provides services for "audioscrobbling" at listenbrainz.org. <br/>
 * See https://listenbrainz.readthedocs.io/
 */
public class ListenBrainzScrobbler {

    private static final Logger LOG = LoggerFactory.getLogger(ListenBrainzScrobbler.class);
    private static final int MAX_PENDING_REGISTRATION = 2000;

    private final LinkedBlockingQueue<RegistrationData> queue;
    private final ReentrantLock registrationLock = new ReentrantLock();
    private final ObjectMapper objectMapper;

    private RegistrationTask task;

    public ListenBrainzScrobbler(ObjectMapper objectMapper) {
        queue = new LinkedBlockingQueue<>();
        this.objectMapper = objectMapper;
    }

    /**
     * Registers the given media file at listenbrainz.org. This method returns
     * immediately, the actual registration is done by a separate task.
     *
     * @param mediaFile  The media file to register.
     * @param token      The token to authentication user on ListenBrainz.
     * @param submission Whether this is a submission or a now playing notification.
     * @param time       Event time, or {@code null} to use current time.
     */
    public void register(MediaFile mediaFile, String token, boolean submission, Instant time,
            Executor executor) {

        registrationLock.lock();
        try {

            if (task == null) {
                task = new RegistrationTask(queue);
                executor.execute(task);
            }

            if (queue.size() >= MAX_PENDING_REGISTRATION) {
                writeWarn("ListenBrainz scrobbler queue is full. Ignoring '" + mediaFile.getTitle()
                        + "'");
                return;
            }

            RegistrationData registrationData = new RegistrationData(mediaFile, token, submission,
                    time);
            try {
                queue.put(registrationData);
            } catch (InterruptedException e) {
                writeWarn("Interrupted while queuing ListenBrainz scrobble.", e);
            }
        } finally {
            registrationLock.unlock();
        }
    }

    /**
     * Scrobbles the given song data at listenbrainz.org, using the protocol defined
     * at https://listenbrainz.readthedocs.io/en/latest/dev/api.html.
     *
     * @param registrationData Registration data for the song.
     */
    protected void scrobble(RegistrationData registrationData) throws ExecutionException {
        if (registrationData == null || registrationData.getToken() == null) {
            return;
        }

        if (submit(registrationData)) {
            if (LOG.isInfoEnabled()) {
                LOG
                    .info("Successfully registered "
                            + (registrationData.isSubmission() ? "submission" : "now playing")
                            + " for song '" + registrationData.getTitle() + "'"
                            + " at ListenBrainz: " + registrationData.getTime());
            }
        } else {
            if (LOG.isWarnEnabled()) {
                writeWarn("Failed to scrobble song '" + registrationData.getTitle()
                        + "' at ListenBrainz.");
            }
        }
    }

    /**
     * Returns if submission succeeds.
     */
    boolean submit(RegistrationData registrationData) throws ExecutionException {
        Map<String, Object> additionalInfo = LegacyMap.of();
        additionalInfo
            .computeIfAbsent("release_mbid", k -> registrationData.getMusicBrainzReleaseId());
        additionalInfo
            .computeIfAbsent("recording_mbid", k -> registrationData.getMusicBrainzRecordingId());
        additionalInfo.computeIfAbsent("tracknumber", k -> registrationData.getTrackNumber());

        Map<String, Object> trackMetadata = LegacyMap.of();
        if (!additionalInfo.isEmpty()) {
            trackMetadata.put("additional_info", additionalInfo);
        }
        trackMetadata.computeIfAbsent("artist_name", k -> registrationData.getArtist());
        trackMetadata.computeIfAbsent("track_name", k -> registrationData.getTitle());
        trackMetadata.computeIfAbsent("release_name", k -> registrationData.getAlbum());

        Map<String, Object> payload = LegacyMap.of();
        if (!trackMetadata.isEmpty()) {
            payload.put("track_metadata", trackMetadata);
        }

        Map<String, Object> content = LegacyMap.of();

        if (registrationData.submission) {
            payload.put("listened_at", registrationData.getTime().getEpochSecond());
            content.put("listen_type", "single");
        } else {
            content.put("listen_type", "playing_now");
        }

        List<Map<String, Object>> payloads = new ArrayList<>();
        payloads.add(payload);
        content.put("payload", payloads);

        String json;
        try {
            json = objectMapper.writeValueAsString(content);
        } catch (JacksonException e) {
            throw new ExecutionException("Error when writing Json", e);
        }

        return executeJsonPostRequest("https://api.listenbrainz.org/1/submit-listens/",
                registrationData.getToken(), json);
    }

    private static boolean executeJsonPostRequest(String url, String token, String json)
            throws ExecutionException {
        HttpPost request = new HttpPost(url);
        request.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
        request.setHeader("Authorization", "token " + token);
        request.setHeader("Content-type", "application/json; charset=utf-8");
        return executeRequest(request);
    }

    private static boolean executeRequest(HttpUriRequest request) throws ExecutionException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            String result = client.execute(request, new BasicHttpClientResponseHandler());
            // In the original code, submission success/failure determination is not
            // implemented.
            // return e.g. "{\"status\": \"ok\"}".equals(result);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Successful submission communication - {}", result);
            }
            return true;
        } catch (HttpResponseException e) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Submit failed", e);
            } else if (LOG.isWarnEnabled()) {
                LOG.warn("Submit failed - {}", e.getMessage());
            }
        } catch (IOException e) {
            throw new ExecutionException("Unable to execute Http request.", e);
        }
        return false;
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

    /*
     * httpClient can be reused #833
     */
    private class RegistrationTask implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(ListenBrainzScrobbler.class);

        private final LinkedBlockingQueue<RegistrationData> queue;

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
                    Throwable cause = e.getCause();
                    if (cause instanceof ClientProtocolException) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Error in ListenBrainz registration.", e);
                        }
                    } else if (cause instanceof IOException) {
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
                        .info("ListenBrainz registration for '" + registrationData.getTitle()
                                + "' encountered network error. Will try again later. In queue: "
                                + queue.size(), cause);
                }
            } catch (InterruptedException x) {
                if (LOG.isErrorEnabled()) {
                    LOG
                        .error("Failed to reschedule ListenBrainz registration for '"
                                + registrationData.getTitle() + "'.", cause);
                }
            }
            try {
                Thread.sleep(60L * 1000L); // Wait 60 seconds.
            } catch (InterruptedException x) {
                if (LOG.isErrorEnabled()) {
                    LOG
                        .error("Failed to sleep after ListenBrainz registration failure for '"
                                + registrationData.getTitle() + "': " + x.toString());
                }
            }
        }
    }

    static class RegistrationData {
        private final String token;
        private final String artist;
        private final String album;
        private final String title;
        private final String musicBrainzReleaseId;
        private final String musicBrainzRecordingId;
        private final Integer trackNumber;
        // private int duration;
        private final Instant time;
        boolean submission;

        RegistrationData(MediaFile mediaFile, String token, boolean submission, Instant time) {
            super();
            this.token = token;
            this.artist = mediaFile.getArtist();
            this.album = mediaFile.getAlbumName();
            this.title = mediaFile.getTitle();
            this.musicBrainzReleaseId = mediaFile.getMusicBrainzReleaseId();
            this.musicBrainzRecordingId = mediaFile.getMusicBrainzRecordingId();
            this.trackNumber = mediaFile.getTrackNumber();
            // reg.duration = mediaFile.getDurationSeconds() == null ? 0 :
            // mediaFile.getDurationSeconds();
            this.time = time == null ? now() : time;
            this.submission = submission;
        }

        String getTitle() {
            return title;
        }

        String getToken() {
            return token;
        }

        Instant getTime() {
            return time;
        }

        boolean isSubmission() {
            return submission;
        }

        String getArtist() {
            return artist;
        }

        String getAlbum() {
            return album;
        }

        String getMusicBrainzReleaseId() {
            return musicBrainzReleaseId;
        }

        String getMusicBrainzRecordingId() {
            return musicBrainzRecordingId;
        }

        Integer getTrackNumber() {
            return trackNumber;
        }
    }
}

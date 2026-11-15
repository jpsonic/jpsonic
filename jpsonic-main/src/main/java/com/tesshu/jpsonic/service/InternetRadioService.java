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

package com.tesshu.jpsonic.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import chameleon.playlist.Media;
import chameleon.playlist.Parallel;
import chameleon.playlist.Playlist;
import chameleon.playlist.PlaylistVisitor;
import chameleon.playlist.Sequence;
import chameleon.playlist.SpecificPlaylist;
import chameleon.playlist.SpecificPlaylistFactory;
import com.tesshu.jpsonic.SuppressFBWarnings;
import com.tesshu.jpsonic.domain.language.StringUtil;
import com.tesshu.jpsonic.infrastructure.concurrent.ConcurrentUtils;
import com.tesshu.jpsonic.persistence.api.entity.InternetRadio;
import com.tesshu.jpsonic.persistence.api.repository.InternetRadioDao;
import org.apache.commons.io.input.BoundedInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InternetRadioService {

    private static final Logger LOG = LoggerFactory.getLogger(InternetRadioService.class);

    /**
     * The maximum number of source URLs in a remote playlist.
     */
    private static final int PLAYLIST_REMOTE_MAX_LENGTH = 250;

    /**
     * The maximum size, in bytes, for a remote playlist response.
     */
    private static final long PLAYLIST_REMOTE_MAX_BYTE_SIZE = 100 * 1024; // 100 kB

    /**
     * The maximum number of redirects for a remote playlist response.
     */
    private static final int PLAYLIST_REMOTE_MAX_REDIRECTS = 20;

    /**
     * A list of cached source URLs for remote playlists.
     */
    private final Map<Integer, List<InternetRadioSource>> cachedSources;

    private final InternetRadioDao internetRadioDao;

    private final List<String> availableProtocols = Collections
        .unmodifiableList(Arrays.asList("http", "https"));

    /**
     * Returns all internet radio stations. Disabled stations are not returned.
     *
     * @return Possibly empty list of all internet radio stations.
     */
    public List<InternetRadio> getAllInternetRadios() {
        return getAllInternetRadios(false);
    }

    /**
     * Returns all internet radio stations.
     *
     * @param includeAll Whether disabled stations should be included.
     *
     * @return Possibly empty list of all internet radio stations.
     */
    public List<InternetRadio> getAllInternetRadios(boolean includeAll) {
        List<InternetRadio> all = internetRadioDao.getAllInternetRadios();
        List<InternetRadio> result = new ArrayList<>(all.size());
        for (InternetRadio folder : all) {
            if (includeAll || folder.isEnabled()) {
                result.add(folder);
            }
        }
        return result;
    }

    /**
     * Creates a new internet radio station.
     *
     * @param radio The internet radio station to create.
     */
    public void createInternetRadio(InternetRadio radio) {
        internetRadioDao.createInternetRadio(radio);
    }

    /**
     * Deletes the internet radio station with the given ID.
     *
     * @param id The internet radio station ID.
     */
    public void deleteInternetRadio(Integer id) {
        internetRadioDao.deleteInternetRadio(id);
    }

    /**
     * Updates the given internet radio station.
     *
     * @param radio The internet radio station to update.
     */
    public void updateInternetRadio(InternetRadio radio) {
        internetRadioDao.updateInternetRadio(radio);
    }

    /**
     * Generic exception class for playlists.
     */
    @SuppressWarnings("serial")
    private static class PlaylistException extends Exception {
        PlaylistException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when the remote playlist is too large to be parsed
     * completely.
     */
    @SuppressWarnings("serial")
    private static class PlaylistTooLarge extends PlaylistException {
        PlaylistTooLarge(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when the remote playlist format cannot be determined.
     */
    @SuppressWarnings("serial")
    private static class PlaylistFormatUnsupported extends PlaylistException {
        PlaylistFormatUnsupported(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when too many redirects occurred when retrieving a remote
     * playlist.
     */
    @SuppressWarnings("serial")
    private static class PlaylistHasTooManyRedirects extends PlaylistException {
        PlaylistHasTooManyRedirects(String message) {
            super(message);
        }
    }

    public InternetRadioService(InternetRadioDao internetRadioDao) {
        this.internetRadioDao = internetRadioDao;
        this.cachedSources = new ConcurrentHashMap<>();
    }

    /**
     * Clear the radio source cache.
     */
    public void clearInternetRadioSourceCache() {
        cachedSources.clear();
    }

    /**
     * Clear the radio source cache for the given radio id
     *
     * @param internetRadioId a radio id
     */
    public void clearInternetRadioSourceCache(Integer internetRadioId) {
        if (internetRadioId != null) {
            cachedSources.remove(internetRadioId);
        }
    }

    /**
     * Retrieve a list of sources for the given internet radio.
     * <p>
     * This method caches the sources using the InternetRadio.getId method as a key,
     * until clearInternetRadioSourceCache is called.
     *
     * @param radio an internet radio
     *
     * @return a list of internet radio sources
     */
    public List<InternetRadioSource> getInternetRadioSources(InternetRadio radio) {
        List<InternetRadioSource> sources;
        if (cachedSources.containsKey(radio.getId())) {
            sources = cachedSources.get(radio.getId());
        } else {
            try {
                sources = retrieveInternetRadioSources(radio);
            } catch (ExecutionException e) {
                ConcurrentUtils.handleCauseUnchecked(e);
                if (LOG.isErrorEnabled()) {
                    LOG
                        .error("Failed to retrieve sources for internet radio {}.",
                                radio.getStreamUrl(), e);
                }
                sources = new ArrayList<>();
            }
            cachedSources.put(radio.getId(), sources);
        }
        return sources;
    }

    /**
     * Retrieve a list of sources from the given internet radio.
     * <p>
     * This method uses a default maximum limit of PLAYLIST_REMOTE_MAX_LENGTH
     * sources.
     *
     * @param radio an internet radio
     *
     * @return a list of internet radio sources
     */
    private List<InternetRadioSource> retrieveInternetRadioSources(InternetRadio radio)
            throws ExecutionException {
        List<InternetRadioSource> sources = retrieveInternetRadioSources(radio,
                PLAYLIST_REMOTE_MAX_LENGTH, PLAYLIST_REMOTE_MAX_BYTE_SIZE,
                PLAYLIST_REMOTE_MAX_REDIRECTS);
        if (sources.isEmpty()) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("No entries found for internet radio {}.", radio.getStreamUrl());
            }
        } else {
            if (LOG.isInfoEnabled()) {
                LOG
                    .info("Retrieved playlist for internet radio {}, got {} sources.",
                            radio.getStreamUrl(), sources.size());
            }
        }
        return sources;
    }

    /**
     * Retrieve a list of sources from the given internet radio.
     *
     * @param radio        an internet radio
     * @param maxCount     the maximum number of items to read from the remote
     *                     playlist, or 0 if unlimited
     * @param maxByteSize  maximum size of the response, in bytes, or 0 if unlimited
     * @param maxRedirects maximum number of redirects, or 0 if unlimited
     *
     * @return a list of internet radio sources
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException") // chameleon/Playlist#acceptDown
    private List<InternetRadioSource> retrieveInternetRadioSources(InternetRadio radio,
            int maxCount, long maxByteSize, int maxRedirects) throws ExecutionException {
        // Retrieve the remote playlist
        String playlistUrl = radio.getStreamUrl();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Parsing internet radio playlist at {}...", playlistUrl);
        }
        URL url;
        try {
            url = StringUtil.parseURL(playlistUrl);
        } catch (MalformedURLException e) {
            throw new ExecutionException("Unable to generate playlist URI.", e);
        }
        SpecificPlaylist inputPlaylist = retrievePlaylist(url, maxByteSize, maxRedirects);

        // Retrieve stream URLs
        List<InternetRadioSource> entries = new ArrayList<>();

        PlaylistVisitor visitor = new PlaylistVisitorImpl(maxCount, entries);
        try {
            inputPlaylist.toPlaylist().acceptDown(visitor);
        } catch (ExecutionException e) {
            ConcurrentUtils.handleCauseUnchecked(e);
            if (!(e.getCause() instanceof PlaylistTooLarge)) {
                throw e;
            }
            // Ignore if playlist is too large, but truncate the rest and log a warning.
            if (LOG.isWarnEnabled()) {
                LOG.warn("The playlist is too large, so truncate the rest.", e);
            }
        } catch (Exception e) {
            throw new ExecutionException("The visitor cannot be accepted.", e);
        }

        return entries;
    }

    private static class PlaylistVisitorImpl implements PlaylistVisitor {

        private static final Logger LOG = LoggerFactory.getLogger(PlaylistVisitorImpl.class);

        private final int maxCount;
        private final List<InternetRadioSource> entries;

        PlaylistVisitorImpl(int maxCount, List<InternetRadioSource> entries) {
            super();
            this.maxCount = maxCount;
            this.entries = entries;
        }

        @Override
        public void beginVisitPlaylist(Playlist playlist) {
            // Nothing is currently done.
        }

        @Override
        public void endVisitPlaylist(Playlist playlist) {
            // Nothing is currently done.
        }

        @Override
        public void beginVisitParallel(Parallel parallel) {
            // Nothing is currently done.
        }

        @Override
        public void endVisitParallel(Parallel parallel) {
            // Nothing is currently done.
        }

        @Override
        public void beginVisitSequence(Sequence sequence) {
            // Nothing is currently done.
        }

        @Override
        public void endVisitSequence(Sequence sequence) {
            // Nothing is currently done.
        }

        @Override
        public void beginVisitMedia(Media media) throws ExecutionException {
            // Since we're dealing with remote content, we place a hard
            // limit on the maximum number of items to load from the playlist,
            // in order to avoid parsing erroneous data.
            if (maxCount > 0 && entries.size() >= maxCount) {
                throw new ExecutionException(new PlaylistTooLarge(
                        "Remote playlist has too many sources (maximum " + maxCount + ")"));
            }
            String streamUrl;
            try {
                streamUrl = media.getSource().getURI().toString();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Got source media at {}", streamUrl);
                }
            } catch (URISyntaxException e) {
                throw new ExecutionException("Unable to generate URI for remote content.", e);
            }
            entries.add(new InternetRadioSource(streamUrl));
        }

        @Override
        public void endVisitMedia(Media media) {
            // Nothing is currently done.
        }
    }

    /**
     * Retrieve playlist data from a given URL.
     *
     * @param url          URL to the remote playlist
     * @param maxByteSize  maximum size of the response, in bytes, or 0 if unlimited
     * @param maxRedirects maximum number of redirects, or 0 if unlimited
     *
     * @return the remote playlist data
     */
    protected SpecificPlaylist retrievePlaylist(URL url, long maxByteSize, int maxRedirects)
            throws ExecutionException {

        SpecificPlaylist playlist;
        HttpURLConnection urlConnection = connectToURLWithRedirects(url, maxRedirects);
        try (InputStream in = urlConnection.getInputStream()) {
            String contentEncoding = urlConnection.getContentEncoding();
            if (maxByteSize > 0) {
                try (BoundedInputStream bis = BoundedInputStream
                    .builder()
                    .setInputStream(in)
                    .setMaxCount(maxByteSize)
                    .get()) {
                    playlist = SpecificPlaylistFactory.getInstance().readFrom(bis, contentEncoding);
                }
            } else {
                playlist = SpecificPlaylistFactory.getInstance().readFrom(in, contentEncoding);
            }
        } catch (IOException e) {
            throw new ExecutionException("Cannot get playlist data from the specified URL.", e);
        } finally {
            urlConnection.disconnect();
        }

        if (playlist == null) {
            throw new ExecutionException(
                    new PlaylistFormatUnsupported("Unsupported playlist format " + url.toString()));
        }
        return playlist;
    }

    /**
     * Start a new connection to a remote URL, and follow redirects.
     *
     * @param url          the remote URL
     * @param maxRedirects maximum number of redirects, or 0 if unlimited
     *
     * @return an open connection
     */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // (URL) Not reusable
    protected HttpURLConnection connectToURLWithRedirects(URL url, int maxRedirects)
            throws ExecutionException {

        int redirectCount = 0;
        URL currentURL = url;

        try {
            // Start a first connection.
            HttpURLConnection connection = connectToURL(currentURL);

            // While it redirects, follow redirects in new connections.
            while (connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM
                    || connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP
                    || connection.getResponseCode() == HttpURLConnection.HTTP_SEE_OTHER) {
                // Check if redirect count is not too large.
                redirectCount += 1;
                if (maxRedirects > 0 && redirectCount > maxRedirects) {
                    connection.disconnect();
                    throw new ExecutionException("Redirect failed.",
                            new PlaylistHasTooManyRedirects("Too many redirects (%d) for URL %s"
                                .formatted(redirectCount, url)));
                }

                // Reconnect to the new URL.
                currentURL = StringUtil.parseURL(connection.getHeaderField("Location"));
                connection.disconnect();
                connection = connectToURL(currentURL);
            }

            // Return the last connection that did not redirect.
            return connection;
        } catch (IOException e) {
            throw new ExecutionException("Redirect failed.", e);
        }
    }

    void verifyRadioURL(URL url) throws IOException {
        if (availableProtocols.stream().anyMatch(p -> p.equals(url.getProtocol()))) {
            return;
        }
        throw new IOException("Only http or https protocol can be used.");
    }

    /**
     * Start a new connection to a remote URL.
     *
     * @param url the remote URL
     *
     * @return an open connection
     */
    @SuppressFBWarnings(value = "URLCONNECTION_SSRF_FD", justification = "The URL can only be specified by authorized users. Also, this service is suppressed and unavailable by default.")
    protected HttpURLConnection connectToURL(URL url) throws IOException {
        verifyRadioURL(url);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setAllowUserInteraction(false);
        urlConnection.setConnectTimeout(10_000);
        urlConnection.setDoInput(true);
        urlConnection.setDoOutput(false);
        urlConnection.setReadTimeout(60_000);
        urlConnection.setUseCaches(true);
        urlConnection.connect();
        return urlConnection;
    }

    // VO
    public static final class InternetRadioSource {

        private final String streamUrl;

        public InternetRadioSource(String streamUrl) {
            this.streamUrl = streamUrl;
        }

        public String getStreamUrl() {
            return streamUrl;
        }
    }
}

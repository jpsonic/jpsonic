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

package com.tesshu.jpsonic.infrastructure.scanner;

import static com.tesshu.jpsonic.util.XMLUtil.createSAXBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.domain.system.PodcastStatus;
import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.infrastructure.filesystem.FileNameSanitizer;
import com.tesshu.jpsonic.infrastructure.filesystem.FileOperations;
import com.tesshu.jpsonic.infrastructure.filesystem.MediaTypeDetector;
import com.tesshu.jpsonic.infrastructure.filesystem.PathInspector;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.PodcastChannel;
import com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode;
import com.tesshu.jpsonic.persistence.api.repository.PodcastDao;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.PodcastService;
import com.tesshu.jpsonic.service.metadata.MetaData;
import com.tesshu.jpsonic.service.metadata.MetaDataParser;
import com.tesshu.jpsonic.service.metadata.MetaDataParserFactory;
import com.tesshu.jpsonic.util.StringUtil;
import jakarta.annotation.PostConstruct;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.UncheckedException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.util.Timeout;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Provides services for Podcast reception.
 *
 * @author Sindre Mehus
 */
@Service
@DependsOn({ "podcastDownloadExecutor", "podcastRefreshExecutor" })
public class PodcastServiceImpl implements PodcastService {

    private static final Logger LOG = LoggerFactory.getLogger(PodcastServiceImpl.class);

    /**
     * RFC 2822.
     *
     * @link https://podcasters.apple.com/support/823-podcast-requirements
     */
    // Correct date format:
    // Wed, 6 Jul 2014 13:00:00 PDT
    // Wed, 6 Jul 2014 13:00:00 -0700
    private static final DateTimeFormatter RSS_DATE_FORMAT = DateTimeFormatter
        .ofPattern("EEE, d MMM yyyy H:m:s [Z][zzz]", Locale.ROOT);

    private static final Namespace[] ITUNES_NAMESPACES = {
            Namespace.getNamespace("http://www.itunes.com/DTDs/Podcast-1.0.dtd"),
            Namespace.getNamespace("http://www.itunes.com/dtds/podcast-1.0.dtd") };

    private final PodcastDao podcastDao;
    private final SettingsFacade settingsFacade;
    private final LibraryAccessPolicy libraryAccessPolicy;
    private final MediaFileService mediaFileService;
    private final WritableMediaFileService writableMediaFileService;
    private final MetaDataParserFactory metaDataParserFactory;
    private final ThreadPoolTaskExecutor podcastDownloadExecutor;
    private final ThreadPoolTaskExecutor podcastRefreshExecutor;
    private final ScannerStateServiceImpl scannerState;
    private final IndexManager indexManager;

    private final AtomicBoolean destroy = new AtomicBoolean();
    private final ReentrantLock episodesLock = new ReentrantLock();
    private final ReentrantLock fileLock = new ReentrantLock();

    public PodcastServiceImpl(PodcastDao podcastDao, SettingsFacade settingsFacade,
            LibraryAccessPolicy libraryAccessPolicy, MediaFileService mediaFileService,
            WritableMediaFileService writableMediaFileService,
            MetaDataParserFactory metaDataParserFactory,
            @Qualifier("podcastDownloadExecutor") ThreadPoolTaskExecutor podcastDownloadExecutor,
            @Qualifier("podcastRefreshExecutor") ThreadPoolTaskExecutor podcastRefreshExecutor,
            ScannerStateServiceImpl scannerState, IndexManager indexManager) {
        this.podcastDao = podcastDao;
        this.settingsFacade = settingsFacade;
        this.libraryAccessPolicy = libraryAccessPolicy;
        this.mediaFileService = mediaFileService;
        this.writableMediaFileService = writableMediaFileService;
        this.metaDataParserFactory = metaDataParserFactory;
        this.podcastDownloadExecutor = podcastDownloadExecutor;
        this.podcastRefreshExecutor = podcastRefreshExecutor;
        this.scannerState = scannerState;
        this.indexManager = indexManager;
    }

    @PostConstruct
    void init() {
        // Clean up partial downloads.
        episodesLock.lock();
        try {
            getAllChannels().forEach(channel -> getEpisodes(channel.getId()).forEach(episode -> {
                if (episode.getStatus() == PodcastStatus.DOWNLOADING) {
                    deleteEpisode(episode.getId(), false);
                    writeInfo("""
                            Deleted Podcast episode '%s(%s)' \
                            since download was interrupted.
                            """.formatted(channel.getTitle(), episode.getTitle()));
                }
            }));
        } finally {
            episodesLock.unlock();
        }
        destroy.set(false);
    }

    boolean isRunning() {
        return !destroy.get();
    }

    public void markDestroy() {
        destroy.set(true);
    }

    /**
     * Creates a new Podcast channel.
     *
     * @param url The URL of the Podcast channel.
     */
    @Override
    public void createChannel(final String url) {
        PodcastChannel channel = new PodcastChannel(sanitizeUrl(url));
        int channelId = podcastDao.createChannel(channel);

        refreshChannels(Arrays.asList(getChannel(channelId)), true);
    }

    private String sanitizeUrl(String url) {
        return url.replace(" ", "%20");
    }

    /**
     * Returns a single Podcast channel.
     */
    @Override
    public @Nullable PodcastChannel getChannel(int channelId) {
        PodcastChannel channel = podcastDao.getChannel(channelId);
        if (channel != null && channel.getTitle() != null) {
            addMediaFileIdToChannels(Arrays.asList(channel));
        }
        return channel;
    }

    /**
     * Returns all Podcast channels.
     *
     * @return Possibly empty list of all Podcast channels.
     */
    @Override
    public List<PodcastChannel> getAllChannels() {
        return addMediaFileIdToChannels(podcastDao.getAllChannels());
    }

    private PodcastEpisode getEpisodeByUrl(String url) {
        PodcastEpisode episode = podcastDao.getEpisodeByUrl(url);
        if (episode == null) {
            return null;
        }
        List<PodcastEpisode> episodes = Arrays.asList(episode);
        episodes = filterAllowed(episodes);
        addMediaFileIdToEpisodes(episodes);
        return episodes.isEmpty() ? null : episodes.get(0);
    }

    /**
     * Returns all Podcast episodes for a given channel.
     *
     * @param channelId The Podcast channel ID.
     *
     * @return Possibly empty list of all Podcast episodes for the given channel,
     *         sorted in reverse chronological order (newest episode first).
     */
    @Override
    public List<PodcastEpisode> getEpisodes(int channelId) {
        List<PodcastEpisode> episodes = filterAllowed(podcastDao.getEpisodes(channelId));
        return addMediaFileIdToEpisodes(episodes);
    }

    /**
     * Returns the N newest episodes.
     *
     * @return Possibly empty list of the newest Podcast episodes, sorted in reverse
     *         chronological order (newest episode first).
     */
    @Override
    public List<PodcastEpisode> getNewestEpisodes(int count) {
        List<PodcastEpisode> episodes = addMediaFileIdToEpisodes(
                podcastDao.getNewestEpisodes(count));

        return episodes.stream().filter(episode -> {
            Integer mediaFileId = episode.getMediaFileId();
            if (mediaFileId == null) {
                return false;
            }
            MediaFile mediaFile = mediaFileService.getMediaFile(mediaFileId);
            return mediaFile != null && mediaFile.isPresent();
        }).collect(Collectors.toList());
    }

    private List<PodcastEpisode> filterAllowed(List<PodcastEpisode> episodes) {
        List<PodcastEpisode> result = new ArrayList<>(episodes.size());
        for (PodcastEpisode episode : episodes) {
            if (episode.getPath() == null
                    || libraryAccessPolicy.isReadAllowed(Path.of(episode.getPath()))) {
                result.add(episode);
            }
        }
        return result;
    }

    @Override
    public @Nullable PodcastEpisode getEpisode(int episodeId, boolean includeDeleted) {
        PodcastEpisode episode = podcastDao.getEpisode(episodeId);
        if (episode == null) {
            return null;
        }
        if (episode.getStatus() == PodcastStatus.DELETED && !includeDeleted) {
            return null;
        }
        addMediaFileIdToEpisodes(Arrays.asList(episode));
        return episode;
    }

    @Override
    public @NonNull PodcastEpisode getEpisodeStrict(int episodeId, boolean includeDeleted) {
        PodcastEpisode episode = getEpisode(episodeId, includeDeleted);
        if (episode == null) {
            throw new IllegalArgumentException("The specified PodcastEpisode cannot be found.");
        }
        return episode;
    }

    private List<PodcastEpisode> addMediaFileIdToEpisodes(List<PodcastEpisode> episodes) {
        for (PodcastEpisode episode : episodes) {
            if (episode.getPath() != null) {
                MediaFile mediaFile = mediaFileService.getMediaFile(episode.getPath());
                if (mediaFile != null && mediaFile.isPresent()) {
                    episode.setMediaFileId(mediaFile.getId());
                }
            }
        }
        return episodes;
    }

    private List<PodcastChannel> addMediaFileIdToChannels(List<PodcastChannel> channels) {
        for (PodcastChannel channel : channels) {
            try {
                if (channel.getTitle() == null) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Podcast channel id {} has null title", channel.getId());
                    }
                    continue;
                }
                Path dir = getChannelDirectory(channel);
                MediaFile mediaFile = writableMediaFileService.getMediaFile(dir);
                if (mediaFile != null) {
                    channel.setMediaFileId(mediaFile.getId());
                }
            } catch (SecurityException e) {
                if (LOG.isErrorEnabled()) {
                    LOG
                        .error("Failed to resolve media file ID for podcast channel: "
                                + channel.getTitle(), e);
                }
            }
        }
        return channels;
    }

    @Override
    public void refreshChannel(int channelId, boolean downloadEpisodes) {
        refreshChannels(Arrays.asList(getChannel(channelId)), downloadEpisodes);
    }

    @Override
    public void refreshAllChannels(boolean downloadEpisodes) {
        refreshChannels(getAllChannels(), downloadEpisodes);
    }

    private void refreshChannels(final List<PodcastChannel> channels,
            final boolean downloadEpisodes) {
        for (final PodcastChannel channel : channels) {
            if (destroy.get()) {
                return;
            }
            podcastRefreshExecutor.execute(() -> doRefreshChannel(channel, downloadEpisodes));
        }
    }

    private HttpClientResponseHandler<PodcastChannel> refreshChannelHandler(
            PodcastChannel channel) {
        return (response) -> {
            try {
                Document document = createSAXBuilder().build(response.getEntity().getContent());
                Element channelElement = document.getRootElement().getChild("channel");
                channel.setTitle(removeMarkup(channelElement.getChildTextTrim("title")));
                channel
                    .setDescription(removeMarkup(channelElement.getChildTextTrim("description")));
                channel.setImageUrl(getChannelImageUrl(channelElement));
                channel.setStatus(PodcastStatus.COMPLETED);
                channel.setErrorMessage(null);
                podcastDao.updateChannel(channel);
                refreshEpisodes(channel, channelElement.getChildren("item"));
            } catch (JDOMException | IOException e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Failed to get/parse Podcast channel " + channel.getUrl(), e);
                }
                channel.setStatus(PodcastStatus.ERROR);
                channel.setErrorMessage(getErrorMessage(e));
                podcastDao.updateChannel(channel);
                return null;
            }
            return channel;
        };
    }

    private void doRefreshChannel(PodcastChannel channel, boolean downloadEpisodes) {

        if (!scannerState.tryScanningLock()) {
            return;
        }
        indexManager.startIndexing();

        channel.setStatus(PodcastStatus.DOWNLOADING);
        channel.setErrorMessage(null);
        podcastDao.updateChannel(channel);
        RequestConfig requestConfig = RequestConfig
            .custom()
            .setConnectionRequestTimeout(Timeout.ofMinutes(2))
            .setResponseTimeout(Timeout.ofMinutes(10))
            .build();
        HttpGet method = new HttpGet(URI.create(channel.getUrl()));
        method.setConfig(requestConfig);
        HttpClientResponseHandler<PodcastChannel> handler = refreshChannelHandler(channel);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            PodcastChannel refreshed = client.execute(method, handler);
            if (refreshed != null) {
                downloadImage(refreshed);
            }
        } catch (IOException ioe) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to get/parse Podcast channel " + channel.getUrl(), ioe);
            }
            channel.setStatus(PodcastStatus.ERROR);
            channel.setErrorMessage(getErrorMessage(ioe));
            podcastDao.updateChannel(channel);
        } finally {
            indexManager.stopIndexing();
            scannerState.unlockScanning();
        }

        if (downloadEpisodes) {
            for (final PodcastEpisode episode : getEpisodes(channel.getId())) {
                if (episode.getStatus() == PodcastStatus.NEW && episode.getUrl() != null) {
                    downloadEpisode(episode);
                }
            }
        }
    }

    private void downloadImage(PodcastChannel channel) {

        String imageUrl = channel.getImageUrl();
        if (imageUrl == null) {
            return;
        }

        Path dir = getChannelDirectory(channel);
        MediaFile channelMediaFile = writableMediaFileService.getMediaFile(dir);
        if (channelMediaFile == null) {
            return;
        }

        Path existingCoverArt = mediaFileService.getCoverArt(channelMediaFile);
        boolean imageFileExists = existingCoverArt != null
                && writableMediaFileService.getMediaFile(existingCoverArt) == null;
        if (imageFileExists) {
            return;
        }

        Path saved = null;
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            saved = client.execute(new HttpGet(URI.create(imageUrl)), (response) -> {
                Path coverPath = Path.of(dir.toString(), "cover." + getCoverArtSuffix(response));
                try (InputStream in = response.getEntity().getContent();
                        OutputStream out = Files.newOutputStream(coverPath)) {
                    IOUtils.copy(in, out);
                }
                return coverPath;
            });
        } catch (UnsupportedOperationException | IOException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to DL podcast channel cover art'" + channel.getTitle() + "'", e);
            }
        }
        if (saved != null) {
            writableMediaFileService.refreshCoverArt(channelMediaFile);
        }
    }

    private String getCoverArtSuffix(HttpResponse response) {
        Header contentTypeHeader = null;
        try {
            contentTypeHeader = response.getHeader("Content-Type");
        } catch (ProtocolException e) {
            if (LOG.isInfoEnabled()) {
                LOG.warn("Failed to get contentTypeHeader", e);
            }
        }
        String result = null;
        if (contentTypeHeader != null && contentTypeHeader.getValue() != null) {
            ContentType contentType = ContentType.parse(contentTypeHeader.getValue());
            String mimeType = contentType.getMimeType();
            result = MediaTypeDetector.getSuffix(mimeType);
        }
        return result == null ? "jpeg" : result;
    }

    private String getChannelImageUrl(Element channelElement) {
        String result = getITunesAttribute(channelElement, "image", "href");
        if (result == null) {
            Element imageElement = channelElement.getChild("image");
            if (imageElement != null) {
                result = imageElement.getChildTextTrim("url");
            }
        }
        return result;
    }

    private String getErrorMessage(Exception x) {
        return x.getMessage() == null ? x.toString() : x.getMessage();
    }

    @Override
    public void downloadEpisode(final PodcastEpisode episode) {
        if (destroy.get()) {
            return;
        }
        podcastDownloadExecutor.execute(() -> doDownloadEpisode(episode));
    }

    private void refreshEpisodes(PodcastChannel channel, List<Element> episodeElements) {

        Integer channelId = channel.getId();
        // Create episodes object.
        List<PodcastEpisode> episodes = createPodcastEpisodes(channelId, episodeElements);

        // Sort episode in reverse chronological order (newest first)
        episodes.sort((a, b) -> {
            long timeA = a.getPublishDate() == null ? 0L : a.getPublishDate().toEpochMilli();
            long timeB = b.getPublishDate() == null ? 0L : b.getPublishDate().toEpochMilli();
            return Long.compare(timeB, timeA);
        });

        // Create episodes in database, skipping the proper number of episodes.
        int downloadCount = settingsFacade.get(SKeys.podcast.episodeDownloadCount);
        if (downloadCount == -1) {
            downloadCount = Integer.MAX_VALUE;
        }

        for (int i = 0; i < episodes.size(); i++) {
            PodcastEpisode episode = episodes.get(i);
            if (i >= downloadCount) {
                episode.setStatus(PodcastStatus.SKIPPED);
            }
            podcastDao.createEpisode(episode);
        }
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // (PodcastEpisode) Not reusable
    private List<PodcastEpisode> createPodcastEpisodes(Integer channelId,
            List<Element> episodeElements) {
        List<PodcastEpisode> episodes = new ArrayList<>();

        for (Element episodeElement : episodeElements) {

            String title = removeMarkup(episodeElement.getChildTextTrim("title"));

            Element enclosure = episodeElement.getChild("enclosure");
            if (enclosure == null) {
                writeInfo("No enclosure found for episode " + title);
                continue;
            }

            String url = enclosure.getAttributeValue("url");
            url = sanitizeUrl(url);
            if (!isAudioEpisode(url)) {
                LOG
                    .warn("Audio file specified in episode enclosure does not match extension for scanning : {}",
                            url);
                continue;
            }

            if (getEpisodeByUrl(url) == null) {
                Long length = null;
                try {
                    length = Long.valueOf(enclosure.getAttributeValue("length"));
                } catch (NumberFormatException e) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Failed to parse enclosure length.", e);
                    }
                }

                Instant date = parseDate(episodeElement.getChildTextTrim("pubDate"));
                String duration = formatDuration(getITunesElement(episodeElement, "duration"));
                String description = getDescription(episodeElement);
                PodcastEpisode episode = new PodcastEpisode(null, channelId, url, null, title,
                        description, date, duration, length, 0L, PodcastStatus.NEW, null);
                episodes.add(episode);
                writeInfo("Created Podcast episode " + title);
            }
        }
        return episodes;
    }

    private String getExtension(String url) {
        try {
            URI uri = new URI(url);
            return PathInspector
                .getExtension(new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null,
                        uri.getFragment())
                    .toString());
        } catch (URISyntaxException e) {
            throw new UncheckedException(e);
        }
    }

    boolean isAudioEpisode(String url) {
        return mediaFileService.isAudioFile(getExtension(url));
    }

    private String getDescription(Element element) {
        String description = element.getChildTextTrim("description");
        if (StringUtils.isBlank(description)) {
            description = getITunesElement(element, "summary");
        }
        return removeMarkup(description);
    }

    Instant parseDate(String s) {
        if (s == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Date is null.");
            }
            return null;
        }
        try {
            return ZonedDateTime.parse(s, RSS_DATE_FORMAT).toInstant();
        } catch (DateTimeParseException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Podcast dates must comply with RFC 2822. : {}", e.getMessage());
            }
        }
        return null;
    }

    String formatDuration(String duration) {
        if (duration == null) {
            return null;
        } else if (duration.matches("^\\d+$")) {
            int seconds = Integer.parseInt(duration);
            return StringUtil.formatDuration(seconds);
        }
        return duration;
    }

    private String getITunesElement(Element element, String childName) {
        for (Namespace ns : ITUNES_NAMESPACES) {
            String value = element.getChildTextTrim(childName, ns);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String getITunesAttribute(Element element, String childName, String attributeName) {
        for (Namespace ns : ITUNES_NAMESPACES) {
            Element elem = element.getChild(childName, ns);
            if (elem != null) {
                return StringUtils.trimToNull(elem.getAttributeValue(attributeName));
            }
        }
        return null;
    }

    private HttpGet createHttpGet(String url) {
        RequestConfig requestConfig = RequestConfig
            .custom()
            .setConnectionRequestTimeout(Timeout.ofMinutes(2))
            .setResponseTimeout(Timeout.ofMinutes(10))
            // Workaround HttpClient circular redirects, which some feeds use (with query
            // parameters)
            .setCircularRedirectsAllowed(true)
            // Workaround HttpClient not understanding latest RFC-compliant cookie 'expires'
            // attributes
            .build();
        HttpGet method = new HttpGet(URI.create(url));
        method.setConfig(requestConfig);
        return method;
    }

    private <E extends Exception> void consumeDownloadError(PodcastEpisode episode, E e) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("Failed to download Podcast from " + episode.getUrl(), e);
        }
        episode.setStatus(PodcastStatus.ERROR);
        episode.setErrorMessage(getErrorMessage(e));
        podcastDao.updateEpisode(episode);
    }

    private void writeInfo(String format, Object... args) {
        if (LOG.isInfoEnabled()) {
            LOG.info(format, args);
        }
    }

    private void doDownloadEpisode(PodcastEpisode episode) {

        if (destroy.get()) {
            if (LOG.isInfoEnabled()) {
                LOG
                    .info("Shutdown has been called. It will not be downloaded.: {}",
                            episode.getTitle());
            }
            return;
        }

        episodesLock.lock();
        try {

            if (!scannerState.tryScanningLock()) {
                return;
            }
            indexManager.startIndexing();

            if (isEpisodeDeleted(episode)) {
                writeInfo("Podcast " + episode.getUrl() + " was deleted. Aborting download.");
                return;
            }

            writeInfo("Starting to download Podcast from " + episode.getUrl());

            try (CloseableHttpClient client = HttpClients.createDefault()) {

                PodcastChannel channel = getChannel(episode.getChannelId());
                if (channel == null) {
                    writeInfo("Podcast channel for " + episode.getUrl()
                            + " was deleted. Aborting download.");
                    return;
                }

                client.execute(createHttpGet(episode.getUrl()), response -> {
                    fileLock.lock();
                    try {

                        Path path = getFile(channel, episode);
                        episode.setStatus(PodcastStatus.DOWNLOADING);
                        episode.setBytesDownloaded(0L);
                        episode.setErrorMessage(null);
                        episode.setPath(path.toString());
                        podcastDao.updateEpisode(episode);

                        long bytesDownloaded = updateEpisode(episode, path,
                                response.getEntity().getContent());

                        if (isEpisodeDeleted(episode)) {
                            writeInfo("Podcast " + episode.getUrl()
                                    + " was deleted. Aborting download.");
                            FileOperations.deleteIfExists(path);
                        } else {
                            addMediaFileIdToEpisodes(Arrays.asList(episode));
                            episode.setBytesDownloaded(bytesDownloaded);
                            podcastDao.updateEpisode(episode);
                            writeInfo("Downloaded " + bytesDownloaded + " bytes from Podcast "
                                    + episode.getUrl());
                            updateTags(path, episode);
                            episode.setStatus(PodcastStatus.COMPLETED);
                            podcastDao.updateEpisode(episode);
                            deleteObsoleteEpisodes(channel);
                        }
                    } finally {
                        fileLock.unlock();
                    }

                    return null;
                });
            } catch (IOException e) {
                consumeDownloadError(episode, e);
            } finally {
                indexManager.stopIndexing();
                scannerState.unlockScanning();
            }
        } finally {
            episodesLock.unlock();
        }
    }

    private long updateEpisode(PodcastEpisode episode, Path path, InputStream in)
            throws IOException {
        long bytesDownloaded = 0;
        byte[] buffer = new byte[4096];
        long nextLogCount = 30_000L;
        try (OutputStream out = Files.newOutputStream(path)) {
            for (int n = in.read(buffer); n != -1; n = in.read(buffer)) {
                out.write(buffer, 0, n);
                bytesDownloaded += n;

                if (bytesDownloaded > nextLogCount) {
                    episode.setBytesDownloaded(bytesDownloaded);
                    nextLogCount += 30_000L;

                    // Abort download if episode was deleted by user.
                    if (isEpisodeDeleted(episode)) {
                        break;
                    }
                    podcastDao.updateEpisode(episode);
                }
            }
        }
        return bytesDownloaded;
    }

    private boolean isEpisodeDeleted(PodcastEpisode episode) {
        PodcastEpisode e = podcastDao.getEpisode(episode.getId());
        return e == null || e.getStatus() == PodcastStatus.DELETED;
    }

    private void updateTags(Path path, PodcastEpisode episode) {
        MediaFile mediaFile = writableMediaFileService.getMediaFile(path);
        if (mediaFile == null) {
            return;
        }
        if (StringUtils.isNotBlank(episode.getTitle())) {
            MetaDataParser parser = metaDataParserFactory.getParser(path);
            if (parser == null || !parser.isEditingSupported(path)) {
                return;
            }
            MetaData metaData = parser.getRawMetaData(path);
            metaData.setTitle(episode.getTitle());
            parser.setMetaData(mediaFile, metaData);
            writableMediaFileService.refreshMediaFile(mediaFile);
        }
    }

    private void deleteObsoleteEpisodes(PodcastChannel channel) {
        episodesLock.lock();
        try {
            int episodeCount = settingsFacade.get(SKeys.podcast.episodeRetentionCount);
            if (episodeCount == -1) {
                return;
            }

            List<PodcastEpisode> episodes = getEpisodes(channel.getId());

            // Don't do anything if other episodes of the same channel is currently
            // downloading.
            for (PodcastEpisode episode : episodes) {
                if (episode.getStatus() == PodcastStatus.DOWNLOADING) {
                    return;
                }
            }

            // Reverse array to get chronological order (oldest episodes first).
            Collections.reverse(episodes);

            int episodesToDelete = Math.max(0, episodes.size() - episodeCount);
            for (int i = 0; i < episodesToDelete; i++) {
                deleteEpisode(episodes.get(i).getId(), true);
                writeInfo("Deleted old Podcast episode " + episodes.get(i).getUrl());
            }
        } finally {
            episodesLock.unlock();
        }
    }

    Path getFile(PodcastChannel channel, PodcastEpisode episode) {

        String episodeDate = episode.getPublishDate() == null ? StringUtils.EMPTY
                : DateTimeFormatter
                    .ofPattern("yyyy-MM-dd")
                    .withZone(ZoneId.systemDefault())
                    .format(episode.getPublishDate());
        String filename = channel.getTitle() + " - " + episodeDate + " - " + episode.getId() + " - "
                + episode.getTitle();
        filename = filename.substring(0, Math.min(filename.length(), 146));

        filename = FileNameSanitizer.sanitize(filename);
        String extension = getExtension(episode.getUrl());

        Path channelDir = getChannelDirectory(channel);
        Path file = Path.of(channelDir.toString(), filename + "." + extension);
        for (int i = 0; Files.exists(file); i++) {
            file = Path.of(channelDir.toString(), filename + i + "." + extension);
        }

        if (!libraryAccessPolicy.isWriteAllowed(file)) {
            throw new SecurityException("Access denied to file " + file);
        }
        return file;
    }

    private Path getChannelDirectory(PodcastChannel channel) {
        Path podcastDir = Path.of(settingsFacade.get(SKeys.podcast.folder));
        if (!Files.exists(podcastDir) && FileOperations.createDirectories(podcastDir) == null) {
            throw new IllegalStateException("Failed to create directory " + podcastDir);
        }
        if (!Files.isWritable(podcastDir)) {
            throw new IllegalStateException(
                    "The podcasts directory " + podcastDir + " isn't writeable.");
        }

        Path channelDir = Path
            .of(podcastDir.toString(), FileNameSanitizer.sanitize(channel.getTitle()));
        if (!Files.exists(channelDir)) {
            if (FileOperations.createDirectories(channelDir) == null) {
                throw new IllegalStateException("Failed to create directory " + channelDir);
            }
            MediaFile mediaFile = writableMediaFileService.getMediaFile(channelDir);
            if (mediaFile != null) {
                mediaFile.setComment(channel.getDescription());
                writableMediaFileService.updateComment(mediaFile);
            }
        }
        return channelDir;
    }

    /**
     * Deletes the Podcast channel with the given ID.
     *
     * @param channelId The Podcast channel ID.
     */
    @Override
    public void deleteChannel(int channelId) {
        // Delete all associated episodes (in case they have files that need to be
        // deleted).
        List<PodcastEpisode> episodes = getEpisodes(channelId);
        for (PodcastEpisode episode : episodes) {
            deleteEpisode(episode.getId(), false);
        }
        podcastDao.deleteChannel(channelId);
    }

    /**
     * Deletes the Podcast episode with the given ID.
     *
     * @param episodeId     The Podcast episode ID.
     * @param logicalDelete Whether to perform a logical delete by setting the
     *                      episode status to {@link PodcastStatus#DELETED}.
     */
    @Override
    public void deleteEpisode(int episodeId, boolean logicalDelete) {
        PodcastEpisode episode = podcastDao.getEpisode(episodeId);
        if (episode == null) {
            return;
        }

        String episodePath = episode.getPath();
        if (episodePath != null) {
            fileLock.lock();
            try {
                FileOperations.deleteIfExists(Path.of(episodePath));
            } finally {
                fileLock.unlock();
            }
        }

        if (logicalDelete) {
            episode.setStatus(PodcastStatus.DELETED);
            episode.setErrorMessage(null);
            podcastDao.updateEpisode(episode);
        } else {
            podcastDao.deleteEpisode(episodeId);
        }
    }

    @Nullable
    String removeMarkup(String s) {
        if (s == null) {
            return null;
        }
        return s.replaceAll("<.*?>", "");
    }
}

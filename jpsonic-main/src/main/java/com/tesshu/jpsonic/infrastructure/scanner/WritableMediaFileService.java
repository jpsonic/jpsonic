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
 * (C) 2022 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.scanner;

import static com.tesshu.jpsonic.util.PlayerUtils.FAR_FUTURE;
import static com.tesshu.jpsonic.util.PlayerUtils.FAR_PAST;
import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.tesshu.jpsonic.SuppressLint;
import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.infrastructure.filesystem.PathInspector;
import com.tesshu.jpsonic.infrastructure.filesystem.ScanningExclusionPolicy;
import com.tesshu.jpsonic.infrastructure.language.MetadataReadingProcessor;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.repository.AlbumDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.service.MediaFileCache;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.metadata.MetaData;
import com.tesshu.jpsonic.service.metadata.MusicParser;
import com.tesshu.jpsonic.service.metadata.ParserUtils;
import com.tesshu.jpsonic.service.metadata.VideoParser;
import com.tesshu.jpsonic.util.PlayerUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.stereotype.Service;

/**
 * A service interface that provides write operations—such as registering,
 * updating, and deleting media files—during the scanning process.
 *
 * <p>
 * This service is primarily used during scan phases to persistently reflect
 * media files detected and analyzed through various scan procedures. In
 * particular, it plays a central role in recording results from metadata
 * analysis phases.
 * </p>
 *
 * <h3>Primary Responsibilities</h3>
 * <ul>
 * <li>Registering newly detected media files during a scan</li>
 * <li>Updating or deleting existing media files as needed</li>
 * </ul>
 *
 * <p>
 * Each update is applied incrementally as the scan progresses. Overall
 * integrity and consistency across the scan process is ensured through
 * cooperation with other related services.
 * </p>
 *
 * <h3>Design Notes</h3>
 * <p>
 * This interface was designed to specialize in write operations that occur
 * during scanning, ensuring that batch updates from a scan do not conflict with
 * ordinary updates performed outside of scanning. <br>
 * By clearly separating these responsibilities, the design provides a safe and
 * predictable update mechanism, even in concurrent environments.
 * </p>
 * <ul>
 * <li>Non-scan updates are limited to updating only specific fields, avoiding
 * full record overwrites</li>
 * <li>Updates that could affect scan results are protected through mutual
 * exclusion using {@link ScannerStateService}</li>
 * </ul>
 * <p>
 * This design helps maintain consistent data structures throughout scanning and
 * prevents unintended overwrites or data inconsistencies.
 * </p>
 *
 * @see MediaFile
 * @see ScanContext
 * @see ScannerStateService
 */
@Service
public class WritableMediaFileService {

    private final ScannerStateProvider scannerState;
    private final MediaFileDao mediaFileDao;
    private final MediaFileService mediaFileService;
    private final AlbumDao albumDao;
    private final MediaFileCache mediaFileCache;
    private final MusicParser musicParser;
    private final VideoParser videoParser;

    private final SettingsFacade settingsFacade;
    private final LibraryAccessPolicy libraryAccessPolicy;
    private final ScanningExclusionPolicy scanningExclusionPolicy;
    private final StrictReadingMediaFileAnalysis mediaFileAnalysis;
    private final Indexer indexer;
    private final MusicIndexProviderImpl musicIndexProvider;

    public WritableMediaFileService(MediaFileDao mediaFileDao, ScannerStateProvider scannerState,
            MediaFileService mediaFileService, AlbumDao albumDao, MediaFileCache mediaFileCache,
            MusicParser musicParser, VideoParser videoParser, SettingsFacade settingsFacade,
            LibraryAccessPolicy libraryAccessPolicy,
            ScanningExclusionPolicy scanningExclusionPolicy,
            MetadataReadingProcessor readingProcessor, Indexer indexer,
            MusicIndexProviderImpl musicIndexProvider) {
        super();
        this.mediaFileDao = mediaFileDao;
        this.scannerState = scannerState;
        this.mediaFileService = mediaFileService;
        this.albumDao = albumDao;
        this.mediaFileCache = mediaFileCache;
        this.musicParser = musicParser;
        this.videoParser = videoParser;
        this.settingsFacade = settingsFacade;
        this.libraryAccessPolicy = libraryAccessPolicy;
        this.scanningExclusionPolicy = scanningExclusionPolicy;
        this.mediaFileAnalysis = readingProcessor::analyzeStrictReadingMeta;
        this.indexer = indexer;
        this.musicIndexProvider = musicIndexProvider;
    }

    /**
     * Logic that relies on this method needs to be rewritten since v111.7.0. It
     * suggests imperfect workflow design.
     *
     * @deprecated Use the date logged should be used instead of now()
     */
    @Deprecated
    Instant newScanDate() {
        return now();
    }

    /**
     * Logic that relies on this method needs to be rewritten since v111.7.0. It
     * suggests imperfect workflow design.
     *
     * @deprecated Use MediaFileService#getMediaFile if use the cache, otherwise use
     *             WritableMediaFileService#getMediaFile(Instant, Path)
     */
    @Deprecated
    @Nullable
    MediaFile getMediaFile(@Nullable Path path) {
        return getMediaFile(newScanDate(), path);
    }

    /**
     * Returns a Mediafile for the directory or file indicated by path. Logic using
     * this method was used outside of the normal scan flow in legacy code.
     * Therefore, special data may be created. In particular, the record lifecycle
     * and format should be scrutinized.
     */
    @Nullable
    MediaFile getMediaFile(@NonNull Instant scanDate, @Nullable Path path) {

        if (path == null || !Files.exists(path)) {
            return null;
        } else if (!libraryAccessPolicy.isReadAllowed(path)) {
            throw new SecurityException("Access denied to file " + path);
        }

        MediaFile registered = mediaFileDao.getMediaFile(path.toString());
        if (registered != null) {
            Optional<MediaFile> op = checkLastModified(scanDate, registered);
            op.ifPresent(m -> mediaFileCache.put(path, m));
            return op.orElse(registered);
        }

        return createMediaFile(scanDate, path).get();
    }

    /*
     * In Jpsonic, ChildrenLastUpdated is used to detect changes in related child
     * data fields. The meaning depends on the MediaType: for ALBUM, it indicates a
     * change in the album in the file structure. For MUSIC, AUDIOBOOK, MOVIE, it
     * indicates a change in the album in the ID3.
     */

    private void updateAlbumChildrenLastUpdated(@NonNull MediaFile album) {
        mediaFileDao
            .updateChildrenLastUpdated(album.getPathString(),
                    album.getMediaType() == MediaType.ALBUM ? FAR_FUTURE : album.getChanged());
    }

    /*
     * If there is a difference in the children, set ChildrenLastUpdated to
     * FAR_FUTURE
     */
    private void updateSongChildrenLastUpdated(@NonNull MediaFile updated, @NonNull MediaFile old) {
        if (!(updated.getMediaType() == MediaType.MUSIC
                || updated.getMediaType() == MediaType.AUDIOBOOK
                || updated.getMediaType() == MediaType.VIDEO)) {
            return;
        }
        if (!(Objects.equals(updated.getFolder(), old.getFolder())
                && Objects.equals(updated.getPathString(), old.getPathString())
                && Objects.equals(updated.getCoverArtPath(), old.getCoverArtPath())
                && Objects.equals(updated.getAlbumArtist(), old.getAlbumArtist())
                && Objects.equals(updated.getAlbumName(), old.getAlbumName())
                && Objects.equals(updated.getYear(), old.getYear())
                && Objects.equals(updated.getGenre(), old.getGenre()) && Objects
                    .equals(updated.getMusicBrainzReleaseId(), old.getMusicBrainzReleaseId()))) {
            mediaFileDao.updateChildrenLastUpdated(updated.getPathString(), FAR_FUTURE);
        }
    }

    /*
     * If any children are deleted, set ChildrenLastUpdated of any existing children
     * to FAR_FUTURE.
     */
    private void updateSongChildrenLastUpdated(@NonNull MediaFile parent) {
        for (MediaFile child : mediaFileDao.getChildrenOf(parent.getPathString())) {
            if (child.getMediaType() == MediaType.MUSIC
                    || child.getMediaType() == MediaType.AUDIOBOOK
                    || child.getMediaType() == MediaType.VIDEO) {
                mediaFileDao.updateChildrenLastUpdated(child.getPathString(), FAR_FUTURE);
            }
        }
    }

    Optional<Path> updateChildren(@NonNull Instant scanDate, @NonNull MediaFile parent) {

        Map<String, MediaFile> stored = mediaFileDao
            .getChildrenOf(parent.getPathString())
            .stream()
            .collect(Collectors.toMap(MediaFile::getPathString, mf -> mf));

        LongAdder updateCount = new LongAdder();
        CoverArtDetector coverArtDetector = new CoverArtDetector(scanningExclusionPolicy,
                mediaFileService);
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(parent.toPath())) {
            for (Path childPath : ds) {

                coverArtDetector.setChildFilePath(childPath);

                if (!mediaFileService.includeMediaFile(childPath)) {
                    continue;
                }

                coverArtDetector.setMediaFilePath(childPath);

                MediaFile child = stored.get(childPath.toString());
                createOrUpdateChild(child, childPath, scanDate).ifPresentOrElse(updated -> {
                    if (child != null) {
                        /*
                         * Updates the ChildrenLastUpdated which is used to detect changes when
                         * updating ID3 Album records. Note that this process does not include
                         * detecting changes to the Sort tag.
                         */
                        updateSongChildrenLastUpdated(updated, child);
                    }
                    updateCount.increment();
                }, () -> {
                    if (child != null && !scanDate.equals(child.getLastScanned())
                            && !FAR_FUTURE.equals(child.getLastScanned())) {
                        mediaFileDao.updateLastScanned(child.getId(), scanDate);
                    }
                });
                stored.remove(childPath.toString());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (updateCount.intValue() > 0) {
            updateAlbumChildrenLastUpdated(parent);
        }

        LongAdder deleteCount = new LongAdder();
        stored
            .values()
            .stream()
            .filter(m -> mediaFileDao.deleteMediaFile(m.getId()) > 0)
            .forEach(m -> {
                deleteMediafileIndex(m);
                deleteCount.increment();
            });
        if (deleteCount.intValue() > 0) {
            updateSongChildrenLastUpdated(parent);
        }

        return coverArtDetector.getCoverArtAvailable();
    }

    private void deleteMediafileIndex(MediaFile mediaFile) {
        switch (mediaFile.getMediaType()) {
        case DIRECTORY:
            indexer.expungeArtist(mediaFile.getId());
            break;
        case ALBUM:
            indexer.expungeAlbum(mediaFile.getId());
            break;
        case MUSIC:
            indexer.expungeSong(mediaFile.getId());
            break;
        default:
            break;
        }
    }

    @SuppressLint(value = "NULL_DEREFERENCE", justification = "False positive. getMediaFile is pre-checked and thread safe here.")
    Optional<MediaFile> createOrUpdateChild(@Nullable MediaFile child, @NonNull Path childPath,
            @NonNull Instant scanDate) {
        return child == null ? createMediaFile(scanDate, childPath)
                : checkLastModified(scanDate, child);
    }

    List<MediaFile> getChildrenOf(@NonNull Instant scanDate, @NonNull MediaFile parent,
            boolean fileOnly) {

        List<MediaFile> result = new ArrayList<>();
        if (!parent.isDirectory()) {
            return result;
        }

        updateChildren(scanDate, parent).ifPresentOrElse(covrerArtPath -> {
            if (!Objects.equals(parent.getCoverArtPathString(), covrerArtPath.toString())) {
                mediaFileDao.updateCoverArtPath(parent.getPathString(), covrerArtPath.toString());
            }
        }, () -> {
            if (parent.getPathString() != null) {
                mediaFileDao.updateCoverArtPath(parent.getPathString(), null);
            }
        });

        for (MediaFile child : mediaFileDao.getChildrenOf(parent.getPathString())) {
            if (child.isDirectory() && !fileOnly
                    && mediaFileService.includeMediaFile(child.toPath())) {
                result.add(child);
            }
            if (child.isFile() && fileOnly && mediaFileService.includeMediaFile(child.toPath())) {
                result.add(child);
            }
        }

        return result;
    }

    Optional<MediaFile> checkLastModified(@NonNull Instant scanDate,
            @NonNull final MediaFile mediaFile) {
        boolean ignoreFileTimestamps = settingsFacade
            .get(SKeys.musicFolder.scan.ignoreFileTimestamps);
        if (scanDate.equals(mediaFile.getLastScanned())
                || FAR_FUTURE.equals(mediaFile.getLastScanned())) {
            return Optional.empty();
        } else if (mediaFile.getVersion() >= MediaFileDao.VERSION) {
            if (ignoreFileTimestamps && !FAR_PAST.equals(mediaFile.getLastScanned())) {
                return Optional.empty();
            } else if (!ignoreFileTimestamps
                    && !mediaFile.getChanged().isBefore(getLastModified(mediaFile.toPath()))
                    && !FAR_PAST.equals(mediaFile.getLastScanned())) {
                return Optional.empty();
            }
        }
        return refreshMediaFile(scanDate, mediaFile);
    }

    Instant getLastModified(@NonNull Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant().truncatedTo(ChronoUnit.MILLIS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressLint(value = "NULL_DEREFERENCE", justification = "False positive. parseMediaFile is NonNull")
    Optional<MediaFile> createMediaFile(@NonNull Instant scanDate, @NonNull Path path) {
        MediaFile created = mediaFileDao.createMediaFile(parseMediaFile(scanDate, path, null));
        if (created != null && created.getMediaType() != MediaType.ALBUM) {
            indexer.index(created);
        }
        return Optional.ofNullable(created);
    }

    @NonNull
    MediaFile parseMediaFile(@NonNull Instant scanDate, @NonNull Path path, MediaFile registered) {
        MediaFile mediaFile = instanceOf(path, registered);
        if (Files.isDirectory(path)) {
            applyDirectory(path, mediaFile, scanDate);
        } else {
            applyFile(path, mediaFile, scanDate);
        }
        return mediaFile;
    }

    private @NonNull MediaFile instanceOf(@NonNull Path path, @Nullable MediaFile registered) {
        MediaFile mediaFile = new MediaFile();

        mediaFile.setId(registered == null ? 0 : registered.getId());

        Instant lastModified = getLastModified(path);
        mediaFile.setChanged(lastModified);
        mediaFile.setCreated(lastModified);

        mediaFile.setLastScanned(registered == null ? FAR_PAST : registered.getLastScanned());
        mediaFile.setChildrenLastUpdated(FAR_PAST);

        mediaFile.setPathString(path.toString());
        mediaFile.setFolder(libraryAccessPolicy.getRootFolderForFile(path));

        Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Illegal path specified: " + path);
        }
        mediaFile.setParentPathString(parent.toString());
        mediaFile.setPlayCount(registered == null ? 0 : registered.getPlayCount());
        mediaFile.setLastPlayed(registered == null ? null : registered.getLastPlayed());
        mediaFile.setComment(registered == null ? null : registered.getComment());
        mediaFile.setMediaType(MediaFile.MediaType.DIRECTORY);
        mediaFile.setPresent(true);
        mediaFile.setOrder(registered == null ? -1 : registered.getOrder());
        return mediaFile;
    }

    private void applyFile(@NonNull Path path, @NonNull MediaFile to, @NonNull Instant scanDate) {

        if (musicParser.isApplicable(path)) {
            MetaData metaData = musicParser.getMetaData(path);
            to.setArtist(metaData.getArtist());
            to.setAlbumArtist(metaData.getAlbumArtist());
            to.setAlbumName(metaData.getAlbumName());
            to.setTitle(metaData.getTitle());
            to.setDiscNumber(metaData.getDiscNumber());
            to.setTrackNumber(metaData.getTrackNumber());
            to.setGenre(metaData.getGenre());
            to.setYear(metaData.getYear());
            to.setDurationSeconds(metaData.getDurationSeconds());
            to.setBitRate(metaData.getBitRate());
            to.setVariableBitRate(metaData.isVariableBitRate());
            to.setTitleSort(metaData.getTitleSort());
            to.setAlbumSort(metaData.getAlbumSort());
            to.setAlbumSortRaw(metaData.getAlbumSort());
            to.setArtistSort(metaData.getArtistSort());
            to.setArtistSortRaw(metaData.getArtistSort());
            to.setAlbumArtistSort(metaData.getAlbumArtistSort());
            to.setAlbumArtistSortRaw(metaData.getAlbumArtistSort());
            to.setMusicBrainzReleaseId(metaData.getMusicBrainzReleaseId());
            to.setMusicBrainzRecordingId(metaData.getMusicBrainzRecordingId());
            to.setComposer(metaData.getComposer());
            to.setComposerSort(metaData.getComposerSort());
            to.setComposerSortRaw(metaData.getComposerSort());
            mediaFileAnalysis.analyze(to);
            to.setLastScanned(scanDate);
        } else if (videoParser.isApplicable(path)) {
            to.setLastScanned(FAR_FUTURE);
        } else {
            mediaFileAnalysis.analyze(to);
            to.setLastScanned(scanDate);
        }

        String format = StringUtils
            .trimToNull(StringUtils.lowerCase(PathInspector.getExtension(to.getPathString())));
        to.setFormat(format);
        try {
            to.setFileSize(Files.size(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        to.setMediaType(getMediaType(to));
    }

    private void applyDirectory(@NonNull Path dirPath, @NonNull MediaFile to,
            @NonNull Instant scanDate) {
        if (!mediaFileService.isRoot(to)) {
            getFirstChildMediaFile(dirPath).ifPresentOrElse(firstChildPath -> {
                to.setMediaType(MediaFile.MediaType.ALBUM);
                to.setLastScanned(FAR_FUTURE);
            }, () -> {
                to.setArtist(dirPath.getFileName().toString());
                if (!settingsFacade.get(SKeys.advanced.sort.strict)) {
                    String index = musicIndexProvider.getParser().getIndex(to).index();
                    to.setMusicIndex(index);
                }
                to.setLastScanned(scanDate);
            });
        }
    }

    private Optional<Path> getFirstChildMediaFile(@NonNull Path parent) {
        try (Stream<Path> children = Files.list(parent)) {
            return children
                .filter(Files::isRegularFile)
                .filter(mediaFileService::includeMediaFile)
                .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private MediaFile.MediaType getMediaType(@NonNull MediaFile mediaFile) {
        if (mediaFileService.isVideoFile(mediaFile.getFormat())) {
            return MediaFile.MediaType.VIDEO;
        }
        String path = mediaFile.getPathString().toLowerCase(Locale.ENGLISH);
        String genre = StringUtils.trimToEmpty(mediaFile.getGenre()).toLowerCase(Locale.ENGLISH);
        if (path.contains("podcast") || genre.contains("podcast")) {
            return MediaFile.MediaType.PODCAST;
        }
        if (path.contains("audiobook") || genre.contains("audiobook") || path.contains("audio book")
                || genre.contains("audio book")) {
            return MediaFile.MediaType.AUDIOBOOK;
        }
        return MediaFile.MediaType.MUSIC;
    }

    @NonNull
    MediaFile parseVideo(@NonNull Instant scanDate, MediaFile registered) {
        Path path = registered.toPath();
        /*
         * See #1368.If we limit yourself to ffprobe, we can handle Sort and
         * MusicBrainz. Currently, MP4 ... old QuickTime format specification range is
         * used considering versatility. That is the lowest common multiple.
         */
        if (videoParser.isApplicable(path)) {
            MetaData metaData = videoParser.getMetaData(path);
            registered.setArtist(metaData.getArtist());
            registered.setAlbumArtist(metaData.getAlbumArtist());
            registered.setAlbumName(metaData.getAlbumName());
            registered.setTitle(metaData.getTitle());
            registered.setDiscNumber(metaData.getDiscNumber());
            registered.setTrackNumber(metaData.getTrackNumber());
            registered.setGenre(metaData.getGenre());
            registered.setYear(metaData.getYear());
            registered.setDurationSeconds(metaData.getDurationSeconds());
            registered.setBitRate(metaData.getBitRate()); // ffprobe Only!
            registered.setHeight(metaData.getHeight());
            registered.setWidth(metaData.getWidth());
            // registered.setTitleSort(metaData.getTitleSort());
            // registered.setAlbumSort(metaData.getAlbumSort());
            // registered.setAlbumSortRaw(metaData.getAlbumSort());
            // registered.setArtistSort(metaData.getArtistSort());
            // registered.setArtistSortRaw(metaData.getArtistSort());
            // registered.setAlbumArtistSort(metaData.getAlbumArtistSort());
            // registered.setAlbumArtistSortRaw(metaData.getAlbumArtistSort());
            // registered.setMusicBrainzReleaseId(metaData.getMusicBrainzReleaseId());
            // registered.setMusicBrainzRecordingId(metaData.getMusicBrainzRecordingId());
            registered.setComposer(metaData.getComposer());
            // registered.setComposerSort(metaData.getComposerSort());
            // registered.setComposerSortRaw(metaData.getComposerSort());
        }
        mediaFileAnalysis.analyze(registered);
        registered.setLastScanned(scanDate);
        return registered;
    }

    int updateOrder(@NonNull final MediaFile file) {
        return mediaFileDao.updateOrder(file.getId(), file.getOrder());
    }

    /*
     * TODO To be fixed in v111.7.0 later #1925. Used for some tag updates. Note
     * that it only updates the tags and does not take into account the completeness
     * of the scan. Strictly speaking, processing equivalent to partial scan is
     * required.
     */
    @Deprecated
    void refreshMediaFile(@NonNull MediaFile mediaFile) {
        refreshMediaFile(newScanDate(), mediaFile);
    }

    @SuppressLint(value = "NULL_DEREFERENCE", justification = "False positive. parseMediaFile is NonNull")
    Optional<MediaFile> refreshMediaFile(@NonNull Instant scanDate, @NonNull MediaFile registered) {
        MediaFile parsed = parseMediaFile(scanDate, registered.toPath(), registered);
        Optional<MediaFile> updated = mediaFileDao.updateMediaFile(parsed);
        updated.ifPresent(m -> {
            if (m.getMediaType() != MediaType.ALBUM) {
                indexer.index(m);
            }
        });
        mediaFileCache.remove(parsed.toPath());
        return updated;
    }

    // Updateable even during scanning
    public void refreshCoverArt(@NonNull final MediaFile dir) {
        if (!(dir.getMediaType() == MediaType.ALBUM || dir.getMediaType() == MediaType.DIRECTORY)) {
            return;
        }
        Path dirPath = dir.toPath();
        mediaFileService.findCoverArt(dirPath).ifPresent(coverArtPath -> {
            mediaFileDao.updateCoverArtPath(dirPath.toString(), coverArtPath.toString());
            albumDao
                .updateCoverArtPath(dir.getAlbumArtist(), dir.getAlbumName(),
                        coverArtPath.toString());
            mediaFileCache.remove(dirPath);
        });
    }

    // Cannot be updated while scanning
    public void updateTags(@NonNull final MediaFile file) {
        if (scannerState.isScanning()) {
            // It will be skipped during scanning. No rigor required. Do not acquire locks.
            return;
        }
        refreshMediaFile(file);
        MediaFile refreshed = mediaFileService.getMediaFileStrict(file.getId());
        mediaFileService.getParent(refreshed).ifPresent(this::refreshMediaFile);
    }

    // Updateable even during scanning
    public void incrementPlayCount(@NonNull MediaFile file) {
        Instant now = now();
        mediaFileDao.updatePlayCount(file.getPathString(), now, file.getPlayCount() + 1);
        MediaFile parent = mediaFileService.getParentOf(file);
        if (parent != null && !mediaFileService.isRoot(parent)) {
            mediaFileDao.updatePlayCount(parent.getPathString(), now, parent.getPlayCount() + 1);
        }
        Album album = albumDao.getAlbum(file.getAlbumArtist(), file.getAlbumName());
        if (album != null) {
            albumDao
                .updatePlayCount(album.getArtist(), album.getName(), now, album.getPlayCount() + 1);
        }
    }

    // Updateable even during scanning
    public void updateComment(@NonNull MediaFile mediaFile) {
        mediaFileDao.updateComment(mediaFile.getPathString(), mediaFile.getComment());
    }

    // Cannot be updated while scanning
    public void resetLastScanned(@NonNull MediaFile album) {
        if (scannerState.isScanning()) {
            // It will be skipped during scanning. No rigor required. Do not acquire locks.
            return;
        }
        mediaFileDao.resetLastScanned(album.getId());
        for (MediaFile child : mediaFileDao.getChildrenOf(album.getPathString())) {
            mediaFileDao.resetLastScanned(child.getId());
        }
    }

    private static class CoverArtDetector {

        private final ScanningExclusionPolicy scanningExclusionPolicy;
        private final MediaFileService mediaFileService;
        private Path coverArtAvailable;
        private Path firstCoverArtEmbeddable;

        CoverArtDetector(ScanningExclusionPolicy scanningExclusionPolicy,
                MediaFileService mediaFileService) {
            this.scanningExclusionPolicy = scanningExclusionPolicy;
            this.mediaFileService = mediaFileService;
        }

        void setChildFilePath(Path childPath) {
            try {
                if (coverArtAvailable == null && !scanningExclusionPolicy.isExcluded(childPath)
                        && mediaFileService
                            .isAvailableCoverArtPath(childPath,
                                    Files.readAttributes(childPath, BasicFileAttributes.class))) {
                    coverArtAvailable = childPath;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        void setMediaFilePath(Path childPath) {
            if (firstCoverArtEmbeddable == null
                    && ParserUtils.isEmbeddedArtworkApplicable(childPath)) {
                firstCoverArtEmbeddable = childPath;
            }
        }

        Optional<Path> getCoverArtAvailable() {
            return Optional
                .ofNullable(PlayerUtils.defaultIfNull(coverArtAvailable, firstCoverArtEmbeddable));
        }
    }
}

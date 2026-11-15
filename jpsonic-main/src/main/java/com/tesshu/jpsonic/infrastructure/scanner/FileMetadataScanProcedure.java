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
 * (C) 2025 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.scanner;

import static com.tesshu.jpsonic.util.PlayerUtils.FAR_FUTURE;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators;
import com.tesshu.jpsonic.infrastructure.language.MetadataReadingProcessor;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao.ChildOrder;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent.ScanEventType;
import com.tesshu.jpsonic.service.MediaFileService;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * <h2>Scan Flow Position</h2> This procedure is executed as the <strong>third
 * step</strong> in the overall scan flow. It analyzes and organizes media files
 * according to the FileStructure defined by the Subsonic specification,
 * establishing a logical structure of albums and artists.
 *
 * <h2>Overview</h2> This scan procedure interprets the file and folder layout
 * based on Subsonic's FileStructure rules. By analyzing the directory
 * structure, it maps tracks to their respective albums and artists.
 *
 * <p>
 * {@code FileMetadataScanProcedure} is responsible for building album
 * groupings, generating artist entities, and performing initial sort and
 * playback order corrections.
 *
 * <h3>Main Responsibilities</h3>
 * <ul>
 * <li>{@link #createAlbums()} Constructs album structures based on the
 * FileStructure layout.</li>
 * <li>{@link #parseAlbum()} Validates and organizes each album unit.</li>
 * <li>{@link #updateSortOfAlbum()}, {@link #updateSortOfArtist()} Applies
 * sorting corrections (e.g., by track number, alphabetical order, etc.).</li>
 * <li>{@link #updateOrderOfSongs()}, {@link #updateOrderOfArtist()} Ensures
 * consistency in playback or display order.</li>
 * </ul>
 *
 * <h3>Notes</h3>
 * <ul>
 * <li>The sort order is not used solely for UI display, but may also be
 * leveraged for resolving tag duplication and cover art collisions.</li>
 * <li>Sort order determined in this phase is <strong>not final</strong>; it may
 * be overwritten or adjusted during later metadata correction stages.</li>
 * </ul>
 *
 * <p>
 * By the end of this phase, the logical structure based on FileStructure is
 * fully established, allowing the system to proceed smoothly to the next step
 * of detailed ID3 metadata analysis.
 *
 * @see ScanProcedure
 * @see Id3MetadataScanProcedure
 * @see MediaScannerServiceImpl
 */
@Service
public class FileMetadataScanProcedure {

    private static final Logger LOG = LoggerFactory.getLogger(FileMetadataScanProcedure.class);

    private final MusicFolderServiceImpl musicFolderService;
    private final Indexer indexer;
    private final MediaFileService mediaFileService;
    private final WritableMediaFileService wmfs;
    private final MediaFileDao mediaFileDao;
    private final SortProcedureService sortProcedure;
    private final ScannerStateServiceImpl scannerState;
    private final ScanHelper scanHelper;
    private final MusicIndexProviderImpl musicIndexProvider;
    private final StrictReadingMediaFileAnalysis mediaFileAnalysis;
    private final JpsonicComparators comparators;

    public FileMetadataScanProcedure(MusicFolderServiceImpl musicFolderService, Indexer indexer,
            MediaFileService mediaFileService, WritableMediaFileService wmfs,
            MediaFileDao mediaFileDao, SortProcedureService sortProcedure,
            ScannerStateServiceImpl scannerState, ScanHelper scanHelper,
            MusicIndexProviderImpl musicIndexProvider, MetadataReadingProcessor readingProcessor,
            JpsonicComparators comparators) {
        super();
        this.musicFolderService = musicFolderService;
        this.indexer = indexer;
        this.mediaFileService = mediaFileService;
        this.wmfs = wmfs;
        this.mediaFileDao = mediaFileDao;
        this.sortProcedure = sortProcedure;
        this.scannerState = scannerState;
        this.scanHelper = scanHelper;
        this.musicIndexProvider = musicIndexProvider;
        this.mediaFileAnalysis = readingProcessor::analyzeStrictReadingMeta;
        this.comparators = comparators;
    }

    /**
     * Parses album metadata by updating existing albums and creating new ones. Also
     * triggers a scan event indicating the result.
     *
     * @return true if any albums were parsed (updated or created), false otherwise
     */
    boolean parseAlbum(@NonNull ScanContext context) {
        if (scanHelper.isInterrupted()) {
            return false;
        }

        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();

        int updatedCount = updateAlbums(context, folders);
        int createdCount = createAlbums(context, folders);
        boolean parsed = updatedCount > 0 || createdCount > 0;

        String comment = "Update(%d)/New(%d)".formatted(updatedCount, createdCount);
        scanHelper.createScanEvent(context, ScanEventType.PARSE_ALBUM, comment);

        return parsed;
    }

    /**
     * Updates album records that have changed, by updating their song order,
     * setting metadata, and indexing them.
     *
     * @param folders the list of music folders to restrict the scope
     * @return the number of albums updated
     * @param context The scan context, including scan date and flags.
     */
    private int updateAlbums(@NonNull ScanContext context, List<MusicFolder> folders) {
        List<MediaFile> candidates = mediaFileDao
            .getChangedAlbums(ScanConstants.ACQUISITION_MAX, folders);
        LongAdder updatedCount = new LongAdder();

        updateAlbums: while (!candidates.isEmpty()) {
            for (int i = 0; i < candidates.size(); i++) {
                if (i % 1_000 == 0) {
                    scanHelper.repeatWait();
                    if (scanHelper.isInterrupted()) {
                        break updateAlbums;
                    }
                }

                MediaFile registered = candidates.get(i);
                MediaFile fetchedFirstChild = updateOrderOfSongs(context, registered);
                MediaFile album = (fetchedFirstChild == null) ? registered
                        : albumOf(context, fetchedFirstChild, registered);

                album.setChildrenLastUpdated(context.scanDate());

                mediaFileDao.updateMediaFile(album).ifPresent(updated -> {
                    indexer.index(updated);
                    updatedCount.increment();
                });
            }

            candidates = mediaFileDao.getChangedAlbums(ScanConstants.ACQUISITION_MAX, folders);
        }

        return updatedCount.intValue();
    }

    /**
     * Creates album entries from unparsed album folders. Updates song order, sets
     * album metadata, and indexes the updated albums.
     *
     * @param folders the list of music folders to process
     * @return the number of albums created
     * @param context The scan context, including scan date and flags.
     */
    private int createAlbums(@NonNull ScanContext context, List<MusicFolder> folders) {
        List<MediaFile> candidates = mediaFileDao
            .getUnparsedAlbums(ScanConstants.ACQUISITION_MAX, folders);
        LongAdder createdCount = new LongAdder();

        createAlbums: while (!candidates.isEmpty()) {
            for (int i = 0; i < candidates.size(); i++) {
                if (i % 1_000 == 0) {
                    scanHelper.repeatWait();
                    if (scanHelper.isInterrupted()) {
                        break createAlbums;
                    }
                }

                MediaFile registered = candidates.get(i);
                MediaFile fetchedFirstChild = updateOrderOfSongs(context, registered);
                MediaFile album = (fetchedFirstChild == null) ? registered
                        : albumOf(context, fetchedFirstChild, registered);

                album.setChildrenLastUpdated(context.scanDate());
                album.setLastScanned(context.scanDate());

                mediaFileDao.updateMediaFile(album).ifPresent(updated -> {
                    indexer.index(updated);
                    createdCount.increment();
                });
            }

            candidates = mediaFileDao.getUnparsedAlbums(ScanConstants.ACQUISITION_MAX, folders);
        }

        return createdCount.intValue();
    }

    /**
     * Populates the given album media file with album-level metadata, based on the
     * provided representative track from the album.
     *
     * <p>
     * This method copies artist, album, year, and genre information from the
     * representative track to the album directory MediaFile, updates timestamps,
     * and marks the album as present. The album is then analyzed for indexing.
     * </p>
     *
     * @param representativeTrack the track from which album metadata is derived
     * @param album               the album media file to populate and return
     * @return the updated album media file with applied metadata
     */
    private MediaFile albumOf(@NonNull ScanContext context, @NonNull MediaFile representativeTrack,
            @NonNull MediaFile album) {

        // Artist and album metadata
        album.setArtist(representativeTrack.getAlbumArtist());
        album.setArtistSort(representativeTrack.getAlbumArtistSort());
        album.setArtistSortRaw(representativeTrack.getAlbumArtistSort());
        album.setAlbumName(representativeTrack.getAlbumName());
        album.setAlbumSort(representativeTrack.getAlbumSort());
        album.setAlbumSortRaw(representativeTrack.getAlbumSort());
        album.setYear(representativeTrack.getYear());
        album.setGenre(representativeTrack.getGenre());

        // Timestamps
        album.setChanged(album.getChanged()); // Preserves existing change date
        album.setCreated(album.getChanged());
        album.setLastScanned(context.scanDate());

        // Mark as present and analyze
        album.setPresent(true);
        mediaFileAnalysis.analyze(album);

        return album;
    }

    /**
     * Updates the order of songs directly under the given parent media file.
     *
     * @param parent the parent media file (e.g., album or folder)
     * @return the first song after sorting, or null if none exist or parent is null
     */
    @Nullable
    MediaFile updateOrderOfSongs(@NonNull ScanContext context, MediaFile parent) {
        if (parent == null) {
            return null;
        }

        List<MediaFile> childFiles = mediaFileService
            .getChildrenOf(parent, 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA, MediaType.DIRECTORY,
                    MediaType.ALBUM);

        if (childFiles.isEmpty()) {
            return null;
        }

        scanHelper.invokeUpdateOrder(childFiles, comparators.songsDefault(), wmfs::updateOrder);

        return childFiles.get(0);
    }

    /**
     * Updates the sort fields of album-type media files across all music folders.
     * Applies merge, copy, and compensate sort operations as necessary, and
     * reindexes updated records.
     *
     * @return true if any sort field was updated, false otherwise
     */
    boolean updateSortOfAlbum(@NonNull ScanContext context) {
        boolean updated = false;

        // Cancel if scan was interrupted
        if (scanHelper.isInterrupted()) {
            return updated;
        }

        // Skip sort cleansing if not enabled or not required, and update index instead
        if (!scannerState.isEnableCleansing() || !context.sortStrict()) {
            updateIndexOfAlbum();
            scanHelper
                .createScanEvent(context, ScanEventType.UPDATE_SORT_OF_ALBUM,
                        ScanConstants.MSG_SKIP);
            return updated;
        }

        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();

        // Step 1: Merge sort fields
        final List<Integer> merged = sortProcedure.mergeSortOfAlbum(folders);
        scanHelper.repeatWait();
        if (scanHelper.isInterrupted()) {
            return updated;
        }

        // Step 2: Copy sort fields
        final List<Integer> copied = sortProcedure.copySortOfAlbum(folders);
        scanHelper.repeatWait();
        if (scanHelper.isInterrupted()) {
            return updated;
        }

        // Step 3: Compensate missing sort fields
        final List<Integer> compensated = sortProcedure.compensateSortOfAlbum(folders);
        scanHelper.repeatWait();
        if (scanHelper.isInterrupted()) {
            return updated;
        }

        // Update index if any sorting changes were made
        updated = !merged.isEmpty() || !copied.isEmpty() || !compensated.isEmpty();
        if (updated) {
            invokeUpdateIndex(context, merged, copied, compensated);
        }

        // Always update album index as final step
        updateIndexOfAlbum();

        String comment = "Merged(%d)/Copied(%d)/Compensated(%d)"
            .formatted(merged.size(), copied.size(), compensated.size());
        scanHelper.createScanEvent(context, ScanEventType.UPDATE_SORT_OF_ALBUM, comment);

        return updated;
    }

    /**
     * Re-indexes media files affected by merging, copying, or compensating
     * operations.
     * <p>
     * The method:
     * <ul>
     * <li>Combines and deduplicates the given ID lists</li>
     * <li>Fetches each {@link MediaFile} and updates its index</li>
     * <li>If the media file is an album and its children are not yet updated, it
     * updates the {@code childrenLastUpdated} timestamp</li>
     * <li>Performs periodic wait and interruption check during the loop</li>
     * </ul>
     *
     * @param merged      list of merged media file IDs
     * @param copied      list of copied media file IDs
     * @param compensated list of compensated media file IDs
     */
    private void invokeUpdateIndex(@NonNull ScanContext context, List<Integer> merged,
            List<Integer> copied, List<Integer> compensated) {

        List<Integer> ids = Stream
            .concat(Stream.concat(merged.stream(), copied.stream()).distinct(),
                    compensated.stream())
            .distinct()
            .collect(Collectors.toList());

        for (int i = 0; i < ids.size(); i++) {
            MediaFile mediaFile = mediaFileService.getMediaFileStrict(ids.get(i));

            indexer.index(mediaFile);

            if (mediaFile.getMediaType() == MediaType.ALBUM
                    && FAR_FUTURE.equals(mediaFile.getChildrenLastUpdated())) {
                mediaFileDao
                    .updateChildrenLastUpdated(mediaFile.getPathString(), context.scanDate());
            }

            if (i % 10_000 == 0) {
                scanHelper.repeatWait();
                if (scanHelper.isInterrupted()) {
                    LOG.warn("""
                            Registration of the search index was interrupted.
                            Rescanning with IgnoreTimestamp enabled is recommended.
                            """);
                    break;
                }
            }
        }
    }

    /**
     * Updates the music index of all album-type MediaFiles across all music
     * folders. Only processes MediaFiles of type ALBUM and skips others.
     */
    private void updateIndexOfAlbum() {
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();

        // Check if there are any album-type children; if not, skip processing
        if (mediaFileDao.getChildSizeOf(folders, MediaType.ALBUM) == 0) {
            return;
        }

        // Prepare an array of MediaTypes excluding ALBUM
        MediaType[] otherThanAlbum = Stream
            .of(MediaType.values())
            .filter(type -> type != MediaType.ALBUM)
            .toArray(MediaType[]::new);

        for (MusicFolder folder : folders) {
            int offset = 0;

            // Retrieve album-type children, excluding other types
            List<MediaFile> albums = mediaFileDao
                .getChildrenOf(folder.getPathString(), offset, ScanConstants.ACQUISITION_MAX,
                        ChildOrder.BY_ALPHA, otherThanAlbum);

            // Iterate in batches until all albums are processed
            while (!albums.isEmpty()) {
                for (MediaFile album : albums) {
                    // Update music index using the parser
                    String musicIndex = musicIndexProvider.getParser().getIndex(album).index();
                    album.setMusicIndex(musicIndex);
                    mediaFileDao.updateMediaFile(album);
                }

                offset += ScanConstants.ACQUISITION_MAX;

                // Fetch the next batch of albums
                albums = mediaFileDao
                    .getChildrenOf(folder.getPathString(), offset, ScanConstants.ACQUISITION_MAX,
                            ChildOrder.BY_ALPHA, otherThanAlbum);
            }
        }
    }

    /**
     * Updates the order of all album media files in all music folders based on
     * alphabetical sorting. Skips update if the process is marked as skippable or
     * if interrupted.
     *
     * @param skippable Whether this step can be skipped (e.g. if no changes were
     *                  detected)
     */
    void updateOrderOfAlbum(@NonNull ScanContext context, boolean skippable) {
        if (scanHelper.isInterrupted()) {
            return;
        }

        // Skip the update if marked as skippable
        if (skippable) {
            scanHelper
                .createScanEvent(context, ScanEventType.UPDATE_ORDER_OF_ALBUM,
                        ScanConstants.MSG_UNNECESSARY);
            return;
        }

        // Retrieve all albums across music folders in alphabetical order
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();
        List<MediaFile> albums = mediaFileService
            .getAlphabeticalAlbums(0, Integer.MAX_VALUE, false, folders);

        // Update order using alphabetical comparator
        int count = scanHelper
            .invokeUpdateOrder(albums, comparators.mediaFileOrderByAlpha(), wmfs::updateOrder);

        // Log scan event with number of updated entries
        String comment = "Updated order of (%d) albums".formatted(count);
        scanHelper.createScanEvent(context, ScanEventType.UPDATE_ORDER_OF_ALBUM, comment);
    }

    /**
     * Updates the sort key information for artists.
     * <p>
     * If cleansing is enabled and strict sort is requested in settings, this
     * method:
     * <ul>
     * <li>Attempts to merge existing artist sort keys</li>
     * <li>Copies missing sort values where applicable</li>
     * <li>Compensates for missing or inconsistent sort data</li>
     * <li>Re-indexes affected artists if there are any changes</li>
     * </ul>
     * A {@link ScanEvent} is recorded to log the result.
     *
     * @return true if any artist sort key was updated
     */
    boolean updateSortOfArtist(@NonNull ScanContext context) {
        boolean updated = false;

        // Skip if the scan has been interrupted
        if (scanHelper.isInterrupted()) {
            return updated;
        }

        // Skip if cleansing is disabled or strict sort is not required
        if (!scannerState.isEnableCleansing() || !context.sortStrict()) {
            scanHelper
                .createScanEvent(context, ScanEventType.UPDATE_SORT_OF_ARTIST,
                        ScanConstants.MSG_SKIP);
            return updated;
        }

        // Retrieve all music folders
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();

        // Merge existing sort values where possible
        final List<Integer> merged = sortProcedure.mergeSortOfArtist(folders);
        scanHelper.repeatWait();
        if (scanHelper.isInterrupted()) {
            return updated;
        }

        // Copy sort values from available sources
        final List<Integer> copied = sortProcedure.copySortOfArtist(folders);
        scanHelper.repeatWait();
        if (scanHelper.isInterrupted()) {
            return updated;
        }

        // Fill in missing sort values using fallback logic
        final List<Integer> compensated = sortProcedure.compensateSortOfArtist(folders);
        scanHelper.repeatWait();
        if (scanHelper.isInterrupted()) {
            return updated;
        }

        // Re-index affected artists if any updates were made
        updated = !merged.isEmpty() || !copied.isEmpty() || !compensated.isEmpty();
        if (updated) {
            invokeUpdateIndex(context, merged, copied, compensated);
        }

        // Record the result as a scan event
        String comment = "Merged(%d)/Copied(%d)/Compensated(%d)"
            .formatted(merged.size(), copied.size(), compensated.size());
        scanHelper.createScanEvent(context, ScanEventType.UPDATE_SORT_OF_ARTIST, comment);

        return updated;
    }

    /**
     * Updates the order of all artist media files based on alphabetical sorting.
     * Skips update if the process is marked as skippable or if interrupted.
     *
     * @param skippable Whether this step can be skipped
     */
    void updateOrderOfArtist(@NonNull ScanContext context, boolean skippable) {
        if (scanHelper.isInterrupted()) {
            return;
        }

        // Skip the update if marked as skippable
        if (skippable) {
            scanHelper
                .createScanEvent(context, ScanEventType.UPDATE_ORDER_OF_ARTIST,
                        ScanConstants.MSG_UNNECESSARY);
            return;
        }

        // Retrieve all artist media files from all music folders
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();
        List<MediaFile> artists = mediaFileDao.getArtistAll(folders);

        // Update order based on alphabetical comparator
        int count = scanHelper
            .invokeUpdateOrder(artists, comparators.mediaFileOrderByAlpha(), wmfs::updateOrder);

        // Record scan event with update count
        String comment = "Updated order of (%d) artists".formatted(count);
        scanHelper.createScanEvent(context, ScanEventType.UPDATE_ORDER_OF_ARTIST, comment);
    }

    /**
     * Updates the order of songs located directly under each registered music
     * folder. Skips processing if the scan was interrupted.
     *
     */
    void updateOrderOfSongsDirectlyUnderMusicfolder(@NonNull ScanContext context) {
        if (scanHelper.isInterrupted()) {
            return;
        }

        // For each music folder, update the order of songs directly under it
        for (MusicFolder folder : musicFolderService.getAllMusicFolders()) {
            MediaFile root = mediaFileService.getMediaFileStrict(folder.getPathString());
            updateOrderOfSongs(context, root);
        }

        // Log scan event
        scanHelper.createScanEvent(context, ScanEventType.UPDATE_ORDER_OF_SONG, null);
    }

}

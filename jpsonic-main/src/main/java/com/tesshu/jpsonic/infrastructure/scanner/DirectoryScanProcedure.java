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

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent.ScanEventType;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>Scan Flow Position</h2> This procedure is executed as the <strong>second
 * step</strong> in the scan workflow. It traverses target directories and
 * directly performs classification and parsing of media files.
 *
 * <h2>Overview</h2> This scan procedure recursively scans the file structure
 * under configured music folders, identifying and analyzing candidate media
 * files such as audio tracks, podcasts, and videos.
 *
 * <p>
 * {@code DirectoryScanProcedure} walks through each music folder recursively
 * and, during traversal, classifies and processes media files to determine
 * their eligibility for further metadata analysis. Non-media files are excluded
 * at this stage.
 *
 * <h3>Main Responsibilities</h3>
 * <ul>
 * <li>{@link #iterateFileStructure()} Recursively traverses the directory
 * structure and delegates file parsing.</li>
 * <li>{@link #parseFileStructure()} Parses and classifies audio files under
 * music folders.</li>
 * <li>{@link #parsePodcast()}, {@link #parseVideo()} Performs specialized
 * parsing for podcast and video directories.</li>
 * <li>{@link #scanFile()}, {@link #scanPodcast()} Scans individual files for
 * validation and categorization.</li>
 * <li>{@link #writeParsedCount()} Outputs the number of parsed files for
 * logging or monitoring purposes.</li>
 * </ul>
 *
 * <p>
 * By the end of this step, valid media files have already been identified and
 * preliminarily parsed, allowing the system to proceed directly to detailed
 * metadata extraction.
 *
 * @see ScanProcedure
 * @see FileMetadataScanProcedure
 * @see MediaScannerServiceImpl
 */
@Service
public class DirectoryScanProcedure {

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryScanProcedure.class);

    private final MediaFileDao mediaFileDao;
    private final MusicFolderServiceImpl musicFolderService;
    private final WritableMediaFileService wmfs;
    private final ScannerStateServiceImpl scannerState;
    private final Indexer indexer;
    private final ScanHelper scanHelper;

    public DirectoryScanProcedure(MediaFileDao mediaFileDao,
            MusicFolderServiceImpl musicFolderService, WritableMediaFileService wmfs,
            ScannerStateServiceImpl scannerState, Indexer indexer, ScanHelper scanHelper) {
        super();
        this.mediaFileDao = mediaFileDao;
        this.musicFolderService = musicFolderService;
        this.wmfs = wmfs;
        this.scannerState = scannerState;
        this.indexer = indexer;
        this.scanHelper = scanHelper;
    }

    /**
     * Parses the media folder structure by scanning all registered music folders.
     * <p>
     * For each music folder, attempts to resolve it to a root {@link MediaFile} and
     * scan its contents. If the operation is interrupted, the process is stopped
     * early.
     * </p>
     *
     */
    void parseFileStructure(@NonNull ScanContext context) {
        for (MusicFolder folder : musicFolderService.getAllMusicFolders()) {
            scanHelper
                .getRootDirectory(context, folder.toPath())
                .ifPresent(root -> scanFile(context, folder, root));
        }

        if (scanHelper.isInterrupted()) {
            return;
        }

        scanHelper.createScanEvent(context, ScanEventType.PARSE_FILE_STRUCTURE, null);
    }

    /**
     * Logs the current number of scanned media files at fixed intervals, and emits
     * a SCANNED_COUNT scan event.
     * <p>
     * If {@code scanCount % ScanConstants.SCAN_LOG_INTERVAL != 0}, nothing is
     * logged.
     * </p>
     *
     * @param file    the media file currently being scanned (used for trace
     *                logging)
     * @param context The scan context, including scan date and flags.
     */
    private void writeParsedCount(@NonNull ScanContext context, @NonNull MediaFile file) {
        long scanCount = scannerState.getScanCount();

        if (scanCount % ScanConstants.SCAN_LOG_INTERVAL != 0) {
            return;
        }

        String msg = "Scanned media library with " + scanCount + " entries.";

        if (LOG.isInfoEnabled()) {
            LOG.info(msg);
        } else if (LOG.isTraceEnabled()) {
            LOG.trace("Scanning file {}", file.toPath());
        }

        scanHelper.createScanEvent(context, ScanEventType.SCANNED_COUNT, msg);
    }

    /**
     * Recursively scans the given media file and its children.
     * <p>
     * Skips scanning if the process is interrupted. Increments scan count for
     * non-video files and logs progress at intervals. If the file is a directory,
     * scans both directory and non-directory children recursively.
     * </p>
     *
     * @param folder the music folder to which the file belongs
     * @param file   the media file to scan
     */
    void scanFile(@NonNull ScanContext context, @NonNull MusicFolder folder,
            @NonNull MediaFile file) {
        if (scanHelper.isInterrupted()) {
            return;
        }

        if (file.getMediaType() != MediaType.VIDEO) {
            scannerState.incrementScanCount();
            writeParsedCount(context, file);
        }

        if (file.isDirectory()) {
            // First scan child directories
            for (MediaFile childDir : wmfs.getChildrenOf(context.scanDate(), file, true)) {
                scanFile(context, folder, childDir);
            }

            // Then scan child files
            for (MediaFile childFile : wmfs.getChildrenOf(context.scanDate(), file, false)) {
                scanFile(context, folder, childFile);
            }
        }
    }

    /**
     * Parses unprocessed video files across all registered music folders.
     * <p>
     * The method repeatedly acquires unparsed video entries in batches and
     * processes them by parsing metadata, updating the database, indexing them, and
     * incrementing scan count. The operation can be interrupted at any time, in
     * which case it will stop early.
     * </p>
     *
     */
    void parseVideo(@NonNull ScanContext context) {
        if (scanHelper.isInterrupted()) {
            return;
        }

        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();
        List<MediaFile> videos = mediaFileDao
            .getUnparsedVideos(ScanConstants.ACQUISITION_MAX, folders);
        LongAdder count = new LongAdder();

        while (!videos.isEmpty()) {
            for (MediaFile video : videos) {
                if (scanHelper.isInterrupted()) {
                    break;
                }

                mediaFileDao
                    .updateMediaFile(wmfs.parseVideo(context.scanDate(), video))
                    .ifPresent(updated -> {
                        indexer.index(updated); // index only if update succeeded
                        count.increment(); // count only parsed + indexed videos
                    });

                scannerState.incrementScanCount();
                writeParsedCount(context, video);
            }

            if (scanHelper.isInterrupted()) {
                return;
            }

            // Re-fetch next batch of unparsed videos
            videos = mediaFileDao.getUnparsedVideos(ScanConstants.ACQUISITION_MAX, folders);
        }

        scanHelper
            .createScanEvent(context, ScanEventType.PARSE_VIDEO,
                    "Parsed(%d)".formatted(count.intValue()));
    }

    /**
     * Parses the podcast folder and registers its contents as {@link MediaFile}
     * entries.
     * <p>
     * If a podcast folder is defined in the settings and not interrupted, this
     * method:
     * <ul>
     * <li>Creates a dummy {@link MusicFolder} wrapper</li>
     * <li>Fetches the root directory as a {@link MediaFile}</li>
     * <li>Scans the directory recursively</li>
     * <li>Creates a {@link ScanEvent} for the podcast parsing</li>
     * </ul>
     *
     */
    void parsePodcast(@NonNull ScanContext context) {
        if (scanHelper.isInterrupted()) {
            return;
        }

        String podcastFolderPath = context.podcastFolder();
        if (podcastFolderPath == null) {
            return;
        }

        Path path = Path.of(podcastFolderPath);
        MusicFolder dummy = new MusicFolder(path.toString(), null, true, null, false);

        scanHelper.getRootDirectory(context, path).ifPresent(root -> {
            scanPodcast(context, dummy, root);
            scanHelper.createScanEvent(context, ScanEventType.PARSE_PODCAST, null);
        });
    }

    // TODO To be fixed in v111.7.0 later #1925
    void scanPodcast(@NonNull ScanContext context, @NonNull MusicFolder folder,
            @NonNull MediaFile file) {
        if (scanHelper.isInterrupted()) {
            return;
        }
        scannerState.incrementScanCount();
        writeParsedCount(context, file);

        if (file.isDirectory()) {
            for (MediaFile child : wmfs.getChildrenOf(context.scanDate(), file, true)) {
                scanPodcast(context, folder, child);
            }
            for (MediaFile child : wmfs.getChildrenOf(context.scanDate(), file, false)) {
                scanPodcast(context, folder, child);
            }
        }
    }

    /**
     * Iterates through the file structure to clean up non-present files and remove
     * obsolete data.
     * <p>
     * This method performs the following:
     * <ul>
     * <li>Marks media files as non-present if they are missing as of
     * {@code context.scanDate()}</li>
     * <li>Expunges orphaned artist, album, and song entries from index and
     * database</li>
     * <li>Emits a scan event recording the cleanup operation</li>
     * </ul>
     * If the scan has been interrupted, the method exits early.
     * </p>
     * 
     * @param context The scan context, including scan date and flags.
     */
    @Transactional
    public void iterateFileStructure(@NonNull ScanContext context) {
        if (scanHelper.isInterrupted()) {
            return;
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("Marking non-present files.");
        }
        mediaFileDao.markNonPresent(context.scanDate());

        scanHelper.expungeFileStructure();

        String comment = "%d files checked or parsed.".formatted(scannerState.getScanCount());
        scanHelper.createScanEvent(context, ScanEventType.CLEAN_UP_FILE_STRUCTURE, comment);
    }
}

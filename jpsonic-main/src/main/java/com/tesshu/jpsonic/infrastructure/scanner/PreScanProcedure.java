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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.ArtistDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent.ScanEventType;
import com.tesshu.jpsonic.service.MediaFileCache;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * <h2>Scan Flow Position</h2> This procedure is executed as the <strong>first
 * step</strong> in the scan workflow. It validates the music folder
 * configuration and ensures the environment is ready for scanning.
 *
 * <h2>Overview</h2> This scan procedure performs preparatory tasks before the
 * main scanning begins.
 *
 * <p>
 * {@code PreScanProcedure} is invoked at the very beginning of the scan flow.
 * It verifies the configuration and accessibility of the target music folders
 * and determines whether the scan can proceed safely. This ensures that all
 * subsequent procedures operate on valid, well-formed input data.
 *
 * <h3>Main Responsibilities</h3>
 * <ul>
 * <li>{@link #beforeScan()} Entry point for the pre-scan phase. Validates the
 * environment and performs initial setup.</li>
 * <li>{@link #checkMusicFolders()} Checks the existence and configuration of
 * music folders. Automatically corrects issues such as missing paths, invalid
 * directories, or incorrect ordering.</li>
 * </ul>
 *
 * <p>
 * This procedure follows a fail-fast design: if any critical misconfiguration
 * or access problem is found, the scan process is aborted before any further
 * operations take place.
 *
 * @see ScanProcedure
 * @see MediaScannerServiceImpl
 */
@Service
public class PreScanProcedure {

    private static final Logger LOG = LoggerFactory.getLogger(PreScanProcedure.class);

    private final MusicFolderServiceImpl musicFolderService;
    private final IndexManager indexManager;
    private final MediaFileDao mediaFileDao;
    private final ArtistDao artistDao;
    private final MediaFileCache mediaFileCache;
    private final ScanHelper scanHelper;

    public PreScanProcedure(MusicFolderServiceImpl musicFolderService, IndexManager indexManager,
            MediaFileDao mediaFileDao, ArtistDao artistDao, MediaFileCache mediaFileCache,
            ScanHelper scanHelper) {
        super();
        this.musicFolderService = musicFolderService;
        this.indexManager = indexManager;
        this.mediaFileDao = mediaFileDao;
        this.artistDao = artistDao;
        this.mediaFileCache = mediaFileCache;
        this.scanHelper = scanHelper;
    }

    /**
     * Prepares the system for a media scan.
     * <p>
     * This includes:
     * <ul>
     * <li>Starting indexing</li>
     * <li>Clearing caches and optionally resetting media data based on
     * settings</li>
     * <li>Cleaning up stale file structures if needed</li>
     * <li>Creating a BEFORE_SCAN scan event</li>
     * </ul>
     * </p>
     *
     */
    void beforeScan(@NonNull ScanContext context) {
        // Start indexing process
        indexManager.startIndexing();

        // If file timestamps should be ignored, clear scan history and index
        if (context.ignoreFileTimestamps()) {
            mediaFileDao.resetLastScanned(null);
            artistDao.deleteAll();
            indexManager.deleteAll();
        }

        // Clear and disable in-memory media file cache
        mediaFileCache.setEnabled(false);
        mediaFileCache.removeAll();

        // Clean up orphaned file structures if any
        if (mediaFileDao.existsNonPresent()) {
            scanHelper.expungeFileStructure();
        }

        // Log BEFORE_SCAN event
        scanHelper.createScanEvent(context, ScanEventType.BEFORE_SCAN, null);
    }

    /**
     * Checks the registered music folders for existence and order integrity.
     * <p>
     * - If a folder does not exist or is not a directory, it is marked as disabled
     * and updated.<br>
     * - If any folder has an invalid order (-1), all folders are renumbered
     * sequentially.<br>
     * - A scan event is created to record the outcome.
     * </p>
     */
    void checkMusicFolders(@NonNull ScanContext context) {
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders(false, true);
        LongAdder missingCount = new LongAdder();

        for (MusicFolder folder : folders) {
            Path folderPath = folder.toPath();
            if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
                folder.setEnabled(false);
                folder.setChanged(context.scanDate());
                musicFolderService.updateMusicFolder(context.scanDate(), folder);
                missingCount.increment();
            }
        }

        String comment = "All registered music folders exist.";
        if (missingCount.intValue() > 0) {
            comment = "(%d) music folders changed to enabled.".formatted(missingCount.intValue());
            if (LOG.isWarnEnabled()) {
                LOG.warn(comment);
            }
        }

        boolean needsReorder = folders.stream().anyMatch(folder -> folder.getFolderOrder() == -1);
        if (needsReorder) {
            LongAdder order = new LongAdder();
            for (MusicFolder folder : folders) {
                order.increment();
                folder.setFolderOrder(order.intValue());
                folder.setChanged(context.scanDate());
                musicFolderService.updateMusicFolder(context.scanDate(), folder);
            }
        }

        scanHelper.createScanEvent(context, ScanEventType.MUSIC_FOLDER_CHECK, comment);
    }

}

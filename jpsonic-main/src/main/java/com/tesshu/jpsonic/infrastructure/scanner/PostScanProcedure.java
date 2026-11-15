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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.tesshu.jpsonic.domain.event.ScanFinishedEvent;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import com.tesshu.jpsonic.persistence.core.entity.MediaLibraryStatistics;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent.ScanEventType;
import com.tesshu.jpsonic.persistence.core.repository.StaticsDao;
import com.tesshu.jpsonic.service.MediaFileCache;
import com.tesshu.jpsonic.service.PlaylistService;
import org.apache.commons.lang3.exception.UncheckedException;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * <h2>Scan Flow Position</h2> This procedure is executed as the <strong>fifth
 * and final stage</strong> of the scan flow. It performs post-scan
 * consolidation, logging, statistics, playlist import, and finalization.
 *
 * <h2>Overview</h2> This class performs a series of operations after the
 * scanning process completes:
 * <ul>
 * <li>Collects and stores library statistics per music folder</li>
 * <li>Finalizes memory caches and terminates Lucene indexing</li>
 * <li>Reloads and imports playlists</li>
 * <li>Commits a database checkpoint</li>
 * <li>Issues scan-completion notifications</li>
 * <li>Rotates scan logs stored in the database</li>
 * </ul>
 * All operations are coordinated via the scan helper and respect the scan
 * cancellation flag.
 *
 * <h3>Main Responsibilities</h3>
 * <ul>
 * <li>{@link #runStats(ScanContext)} Collects and stores statistical data for
 * each music folder. An interval is inserted to reduce processing load.</li>
 * <li>{@link #afterScan(ScanContext)} Resets indexers and memory caches, and
 * records post-scan completion events.</li>
 * <li>{@link #importPlaylists(ScanContext)} Re-imports playlists from files.
 * </li>
 * <li>{@link #checkpoint(ScanContext)} Commits a database checkpoint to persist
 * scan results.</li>
 * <li>{@link #success(ScanContext)} Logs the successful completion of the scan
 * and optionally emits a completion event after a short sleep.</li>
 * <li>{@link #rotateScanLog(ScanContext)} Rotates scan logs stored in the
 * database, based on the configured retention period.</li>
 * </ul>
 *
 * @see ScanProcedure
 * @see MediaScannerServiceImpl
 */
@Service
public class PostScanProcedure {

    private static final Logger LOG = LoggerFactory.getLogger(PostScanProcedure.class);

    private final MusicFolderServiceImpl musicFolderService;
    private final IndexManager indexManager;
    private final PlaylistService playlistService;
    private final TemplateWrapper template;
    private final StaticsDao staticsDao;
    private final SortProcedureService sortProcedure;
    private final MediaFileCache mediaFileCache;
    private final ScanHelper scanHelper;
    private final ApplicationEventPublisher eventPublisher;

    public PostScanProcedure(MusicFolderServiceImpl musicFolderService, IndexManager indexManager,
            PlaylistService playlistService, TemplateWrapper template, StaticsDao staticsDao,
            SortProcedureService sortProcedure, MediaFileCache mediaFileCache,
            ScanHelper scanHelper, ApplicationEventPublisher eventPublisher) {
        super();
        this.musicFolderService = musicFolderService;
        this.indexManager = indexManager;
        this.playlistService = playlistService;
        this.template = template;
        this.staticsDao = staticsDao;
        this.sortProcedure = sortProcedure;
        this.mediaFileCache = mediaFileCache;
        this.scanHelper = scanHelper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Gathers and stores media library statistics for each registered music folder.
     * Includes interruption check and staggered execution with repeat wait.
     *
     */
    void runStats(@NonNull ScanContext context) {
        // Log starting message
        if (LOG.isInfoEnabled()) {
            LOG.info("""
                    Collecting media library statistics ...
                    """);
        }

        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();

        // Iterate over all folders and gather statistics
        for (int i = 0; i < folders.size(); i++) {
            // Wait every 4 folders to reduce processing load
            if (i % 4 == 0) {
                scanHelper.repeatWait();
                if (scanHelper.isInterrupted()) {
                    return;
                }
            }

            // Gather and store statistics for each folder
            MediaLibraryStatistics stats = staticsDao
                .gatherMediaLibraryStatistics(context.scanDate(), folders.get(i));
            staticsDao.createMediaLibraryStatistics(stats);
        }

        // Record scan event after statistics run completes
        scanHelper.createScanEvent(context, ScanEventType.RUN_STATS, null);
    }

    void afterScan(@NonNull ScanContext context) {
        mediaFileCache.setEnabled(true);
        indexManager.stopIndexing();
        sortProcedure.clearMemoryCache();
        scanHelper.createScanEvent(context, ScanEventType.AFTER_SCAN, null);
        eventPublisher.publishEvent(new ScanFinishedEvent());
    }

    void importPlaylists(@NonNull ScanContext context) {
        if (LOG.isInfoEnabled()) {
            LOG.info("Starting playlist import.");
        }
        playlistService.importPlaylists();

        if (LOG.isInfoEnabled()) {
            LOG.info("Completed playlist import.");
        }
        scanHelper.createScanEvent(context, ScanEventType.IMPORT_PLAYLISTS, null);
    }

    void checkpoint(@NonNull ScanContext context) {
        template.checkpoint();
        scanHelper.createScanEvent(context, ScanEventType.CHECKPOINT, null);
    }

    void success(@NonNull ScanContext context) {
        try {
            Thread.sleep(1);
            LOG.info("Completed media library scan.");
            scanHelper.createScanEvent(context, ScanEventType.SUCCESS, null);
        } catch (InterruptedException e) {
            scanHelper.createScanEvent(context, ScanEventType.FAILED, null);
            throw new UncheckedException(e);
        }
    }

    /**
     * Rotates old scan logs based on the retention setting. If the retention is set
     * to the default, only the latest entry is kept. Otherwise, entries older than
     * the retention threshold are deleted.
     */
    void rotateScanLog(@NonNull ScanContext context) {
        int retention = context.scanLogRetention();
        int defaultRetention = context.defaultScanLogRetention();

        if (retention == defaultRetention) {
            staticsDao.deleteOtherThanLatest();
            return;
        }

        Instant threshold = Instant
            .now()
            .truncatedTo(ChronoUnit.DAYS)
            .minus(retention, ChronoUnit.DAYS);

        staticsDao.deleteBefore(threshold);
    }

}

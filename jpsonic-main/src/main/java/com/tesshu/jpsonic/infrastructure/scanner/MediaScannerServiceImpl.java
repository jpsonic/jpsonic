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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent.ScanEventType;
import com.tesshu.jpsonic.persistence.core.entity.ScanLog.ScanLogType;
import com.tesshu.jpsonic.persistence.core.repository.StaticsDao;
import com.tesshu.jpsonic.service.MediaScannerService;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Coordinates the full scan process for media files.
 *
 * <p>
 * {@code MediaScannerServiceImpl} serves as the central orchestrator that
 * initiates and manages a sequence of scan procedure classes, each responsible
 * for a specific step in the scan flow (e.g., directory traversal, metadata
 * extraction, indexing).
 *
 * <p>
 * The scan is executed in a fixed order using a list of {@code ScanProcedure}
 * instances, which are invoked sequentially. Each {@code ScanProcedure}
 * modifies or interprets the shared {@code ScanContext}, which carries
 * immutable configuration and collected data across the scan.
 *
 * <p>
 * This class does not perform any scanning logic itself. Instead, it delegates
 * all actual processing to the corresponding procedure classes and ensures the
 * correct order of execution.
 *
 * <p>
 * It also handles basic error propagation, logging, and state management via
 * {@code ScannerStateServiceImpl}.
 *
 * @see ScanProcedure
 * @see ScanContext
 * @see ScannerStateServiceImpl
 */
@Service("mediaScannerService")
@DependsOn("scanExecutor")
public class MediaScannerServiceImpl implements MediaScannerService {

    private static final Logger LOG = LoggerFactory.getLogger(MediaScannerService.class);

    private final SettingsFacade settingsFacade;
    private final ScannerStateServiceImpl scannerState;

    private final PreScanProcedure preScanProc;
    private final DirectoryScanProcedure directoryScanProc;
    private final FileMetadataScanProcedure fileMetaProc;
    private final Id3MetadataScanProcedure id3MetaProc;
    private final PostScanProcedure postScanProc;
    private final ScanHelper scanHelper;

    private final StaticsDao staticsDao;
    private final ThreadPoolTaskExecutor scanExecutor;

    private final ReentrantLock cancelLock = new ReentrantLock();

    public MediaScannerServiceImpl(SettingsFacade settingsFacade,
            ScannerStateServiceImpl scannerState, PreScanProcedure preScanProc,
            DirectoryScanProcedure directoryScanProc, FileMetadataScanProcedure fileMetaProc,
            Id3MetadataScanProcedure id3MetaProc, PostScanProcedure postScanProc,
            ScanHelper scanHelper, StaticsDao staticsDao,
            @Qualifier("scanExecutor") ThreadPoolTaskExecutor scanExecutor) {
        super();
        this.settingsFacade = settingsFacade;
        this.scannerState = scannerState;

        this.preScanProc = preScanProc;
        this.directoryScanProc = directoryScanProc;
        this.fileMetaProc = fileMetaProc;
        this.id3MetaProc = id3MetaProc;
        this.postScanProc = postScanProc;

        this.scanHelper = scanHelper;
        this.staticsDao = staticsDao;
        this.scanExecutor = scanExecutor;
    }

    @Override
    public boolean neverScanned() {
        return scannerState.neverScanned();
    }

    @Override
    public boolean isScanning() {
        return scannerState.isScanning();
    }

    @Override
    public boolean isCancel() {
        return scanHelper.isCancel();
    }

    @Override
    public void tryCancel() {
        cancelLock.lock();
        try {
            if (isScanning()) {
                scanHelper.setCancel(true);
            }
        } finally {
            cancelLock.unlock();
        }
    }

    @Override
    public long getScanCount() {
        return scannerState.getScanCount();
    }

    @Override
    public Optional<ScanEventType> getLastScanEventType() {
        return getLastScanEventType(true);
    }

    private Optional<ScanEventType> getLastScanEventType(boolean scanningCheck) {
        if (scanningCheck && isScanning() || neverScanned()) {
            return Optional.empty();
        }
        List<ScanEvent> scanEvents = staticsDao.getLastScanAllStatuses();
        if (scanEvents.isEmpty()) {
            return Optional.of(ScanEventType.FAILED);
        }
        return Optional.of(scanEvents.get(0).getType());
    }

    boolean isOptionalProcessSkippable() {
        MutableBoolean skippable = new MutableBoolean(
                !settingsFacade.get(SKeys.musicFolder.scan.ignoreFileTimestamps));
        if (skippable.isTrue()) {
            getLastScanEventType(false)
                .ifPresentOrElse(
                        type -> skippable.setValue(ScanEventType.SUCCESS.compareTo(type) == 0),
                        () -> skippable.setValue(false));
        }
        if (skippable.isTrue()) {
            skippable.setValue(!staticsDao.isfolderChangedSinceLastScan());
        }
        return skippable.booleanValue();
    }

    @Override
    public void scanLibrary() {
        scanExecutor.execute(this::doScanLibrary);
    }

    @SuppressWarnings("PMD.NPathComplexity") // TODO This will be resolved in 114.3.0
    void doScanLibrary() {

        if (!scannerState.tryScanningLock()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Cleanup/Scan/Podcast Download is already running.");
            }
            return;
        }

        LOG.info("Starting to scan media library.");

        ScanContext context = new ScanContext(scannerState.getScanDate(),
                settingsFacade.get(SKeys.musicFolder.scan.ignoreFileTimestamps),
                settingsFacade.get(SKeys.podcast.folder),
                settingsFacade.get(SKeys.advanced.sort.strict),
                settingsFacade.get(SKeys.advanced.scanLog.useScanLog),
                settingsFacade.get(SKeys.advanced.scanLog.scanLogRetention),
                SKeys.advanced.scanLog.scanLogRetention.defaultValue(),
                settingsFacade.get(SKeys.advanced.scanLog.useScanEvents),
                settingsFacade.get(SKeys.advanced.scanLog.measureMemory));

        scanHelper.createScanLog(context, ScanLogType.SCAN_ALL);

        preScanProc.beforeScan(context);
        preScanProc.checkMusicFolders(context);

        directoryScanProc.parseFileStructure(context);
        directoryScanProc.parseVideo(context);
        directoryScanProc.parsePodcast(context);
        directoryScanProc.iterateFileStructure(context);

        boolean parsedAlbum = fileMetaProc.parseAlbum(context);
        boolean updatedSortOfAlbum = fileMetaProc.updateSortOfAlbum(context);
        boolean skippable = isOptionalProcessSkippable();
        fileMetaProc.updateOrderOfAlbum(context, skippable && !parsedAlbum && !updatedSortOfAlbum);
        boolean updatedSortOfArtist = fileMetaProc.updateSortOfArtist(context);
        fileMetaProc
            .updateOrderOfArtist(context, skippable && !parsedAlbum && !updatedSortOfArtist);
        fileMetaProc.updateOrderOfSongsDirectlyUnderMusicfolder(context);
        if (LOG.isInfoEnabled()) {
            LOG.info("Scanned media library with " + scannerState.getScanCount() + " entries.");
        }

        boolean refleshedAlbumId3 = id3MetaProc.refleshAlbumId3(context);
        id3MetaProc.updateOrderOfAlbumId3(context, skippable && !refleshedAlbumId3);
        boolean refleshedArtistId3 = id3MetaProc.refleshArtistId3(context);
        id3MetaProc.updateOrderOfArtistId3(context, skippable && !refleshedArtistId3);
        id3MetaProc
            .updateAlbumCounts(context, skippable && !refleshedAlbumId3 && !refleshedArtistId3);
        id3MetaProc.updateGenreMaster(context);

        postScanProc.runStats(context);
        postScanProc.afterScan(context);

        if (scannerState.isDestroy()) {
            LOG.warn("The scan was stopped due to the shutdown.");
            scanHelper.createScanEvent(context, ScanEventType.DESTROYED, null);
            return;
        } else if (isCancel()) {
            LOG.warn("The scan was stopped due to cancellation.");
            scanHelper.createScanEvent(context, ScanEventType.CANCELED, null);
        } else {
            postScanProc.importPlaylists(context);
            postScanProc.checkpoint(context);
            postScanProc.success(context);
        }

        postScanProc.rotateScanLog(context);

        cancelLock.lock();
        try {
            scannerState.unlockScanning();
            scanHelper.setCancel(false);
        } finally {
            cancelLock.unlock();
        }
    }

    @Override
    public Optional<ScanPhaseInfo> getScanPhaseInfo() {
        // Not currently scanning
        if (!scannerState.isScanning()) {
            return Optional.empty();
        }

        // Get the last recorded scan event
        ScanEventType lastEvent = scannerState.getLastEvent();

        // Normalize SCANNED_COUNT to MUSIC_FOLDER_CHECK (non-phase event)
        if (lastEvent == ScanEventType.SCANNED_COUNT) {
            lastEvent = ScanEventType.MUSIC_FOLDER_CHECK;
        }

        // Find the last phase index
        int lastPhaseIndex = ScanConstants.SCAN_PHASE_ALL.indexOf(lastEvent);

        // Unknown phase (non-standard scan sequence)
        if (lastPhaseIndex == -1) {
            return Optional.of(new ScanPhaseInfo(-1, -1, "Semi Scan Proc", -1));
        }

        // Calculate the current phase index (advance if not at the end)
        int currentPhaseIndex = (lastPhaseIndex + 1 < ScanConstants.SCAN_PHASE_ALL.size())
                ? lastPhaseIndex + 1
                : lastPhaseIndex;

        // Build and return ScanPhaseInfo
        return Optional
            .of(new ScanPhaseInfo(currentPhaseIndex, ScanConstants.SCAN_PHASE_ALL.size(),
                    ScanConstants.SCAN_PHASE_ALL.get(currentPhaseIndex).name(),
                    scanExecutor.getActiveCount()));
    }
}

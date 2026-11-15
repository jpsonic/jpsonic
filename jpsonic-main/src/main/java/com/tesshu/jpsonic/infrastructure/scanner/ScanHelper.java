package com.tesshu.jpsonic.infrastructure.scanner;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

import com.tesshu.jpsonic.domain.contract.Orderable;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent.ScanEventType;
import com.tesshu.jpsonic.persistence.core.entity.ScanLog.ScanLogType;
import com.tesshu.jpsonic.persistence.core.repository.StaticsDao;
import org.apache.commons.lang3.exception.UncheckedException;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.stereotype.Service;

/**
 * A utility class that provides shared helper logic for scan procedures.
 *
 * <p>
 * {@code ScanHelper} centralizes commonly used operations across scan phases,
 * such as detecting cancellation, deleting file structures, and creating scan
 * logs and events.
 *
 * <p>
 * This class maintains an internal {@code AtomicBoolean} to track cancellation
 * state, allowing scan procedures to cooperatively respond to user-initiated
 * interruptions or thread-level cancellations in a safe and consistent way.
 *
 * <h3>Typical Use Cases</h3>
 * <ul>
 * <li>{@link #createScanEvent(String, Object)} Creates a scan event based on
 * the specified event type and associated data</li>
 * <li>{@link #createScanLog(String, Object)} Assists with logging operations or
 * notable events that occur during scanning</li>
 * <li>{@link #expungeFileStructure(File)} Deletes the specified file or
 * directory structure recursively and safely</li>
 * <li>{@link #isCancel()} / {@link #isInterrupted()} Checks whether the scan
 * has been cancelled or the thread has been interrupted</li>
 * <li>{@link #invokeUpdateOrder(Collection)} Applies batch update operations to
 * entities that require ordering (e.g., albums or songs)</li>
 * <li>{@link #repeatWait(long)} Pauses execution for a fixed interval during
 * scan processing (e.g., throttling)</li>
 * </ul>
 *
 * <p>
 * These helper functions improve the accuracy and robustness of the scan
 * process and are reused across multiple {@code ScanProcedure} implementations
 * and in {@code MediaScannerServiceImpl}.
 *
 * @see ScanProcedure
 * @see MediaScannerServiceImpl
 */
@Service
public class ScanHelper {

    private final ScannerStateServiceImpl scannerState;
    private final SettingsFacade settingsFacade;
    private final StaticsDao staticsDao;
    private final MediaFileDao mediaFileDao;
    private final Indexer indexer;
    private final WritableMediaFileService wmfs;

    private final AtomicBoolean cancel = new AtomicBoolean();

    public ScanHelper(ScannerStateServiceImpl scannerState, SettingsFacade settingsFacade,
            StaticsDao staticsDao, MediaFileDao mediaFileDao, Indexer indexer,
            WritableMediaFileService wmfs) {
        this.scannerState = scannerState;
        this.settingsFacade = settingsFacade;
        this.staticsDao = staticsDao;
        this.mediaFileDao = mediaFileDao;
        this.indexer = indexer;
        this.wmfs = wmfs;
    }

    /**
     * Sets the cancellation flag to the specified value. Used to signal that the
     * scan should be aborted.
     *
     * @param b true to cancel; false to reset
     */
    public void setCancel(boolean b) {
        cancel.set(b);
    }

    /**
     * Checks whether the scan has been cancelled.
     *
     * @return true if cancelled; false otherwise
     */
    public boolean isCancel() {
        return cancel.get();
    }

    /**
     * Determines if the current scan should be interrupted due to either
     * cancellation or system shutdown.
     *
     * @return true if interrupted; false otherwise
     */
    public boolean isInterrupted() {
        return isCancel() || scannerState.isDestroy();
    }

    /**
     * Sleeps the thread for a configured interval to wait before retrying or
     * looping. Throws an unchecked exception if interrupted.
     */
    public void repeatWait() {
        try {
            Thread.sleep(ScanConstants.REPEAT_WAIT_MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedException(e);
        }
    }

    /**
     * Creates a scan log entry if the type requires logging or logging is enabled.
     *
     * @param logType the type of scan log to create
     */
    void createScanLog(@NonNull ScanContext context, @NonNull ScanLogType logType) {
        boolean shouldCreate = switch (logType) {
        case SCAN_ALL, EXPUNGE, FOLDER_CHANGED -> true;
        default -> context.useScanLog();
        };

        if (shouldCreate) {
            staticsDao.createScanLog(context.scanDate(), logType);
        }
    }

    /**
     * Creates and persists a {@link ScanEvent} with optional memory usage data and
     * comments. Respects configuration flags for memory measurement and event
     * logging.
     *
     * @param scanDate the date when the scan started
     * @param logType  the type of event being recorded
     * @param comment  optional message to include with the event
     */
    public void createScanEvent(@NonNull ScanContext context, @NonNull ScanEventType logType,
            @Nullable String comment) {

        scannerState.setLastEvent(logType);

        boolean shouldCreateEvent = switch (logType) {
        case SUCCESS, DESTROYED, CANCELED -> true;
        default -> settingsFacade.get(SKeys.advanced.scanLog.useScanEvents);
        };

        if (!shouldCreateEvent) {
            return;
        }

        Long maxMemory = null;
        Long totalMemory = null;
        Long freeMemory = null;

        if (settingsFacade.get(SKeys.advanced.scanLog.measureMemory)) {
            Runtime runtime = Runtime.getRuntime();
            maxMemory = runtime.maxMemory();
            totalMemory = runtime.totalMemory();
            freeMemory = runtime.freeMemory();
        }

        ScanEvent scanEvent = new ScanEvent(context.scanDate(), Instant.now(), logType, maxMemory,
                totalMemory, freeMemory, null, comment);

        staticsDao.createScanEvent(scanEvent);
    }

    /**
     * Expunges obsolete artist, album, and song entries from both the index and
     * database.
     * <p>
     * This method performs:
     * <ul>
     * <li>Index-level expunging of artist, album, and song entries marked as
     * removable.</li>
     * <li>Database-level batch expunging in ID ranges.</li>
     * </ul>
     * Periodic wait is introduced every
     * {@value ScanConstants#EXPUNGE_WAIT_INTERVAL} entries to avoid long blocking.
     * The process checks {@link #isInterrupted()} to allow early termination.
     * </p>
     */
    void expungeFileStructure() {
        // Step 1: Remove artists from index
        mediaFileDao.getArtistExpungeCandidates().forEach(indexer::expungeArtist);

        // Step 2: Remove albums from index
        mediaFileDao.getAlbumExpungeCandidates().forEach(indexer::expungeAlbum);

        // Step 3: Remove songs from index, with periodic wait
        List<Integer> songIds = mediaFileDao.getSongExpungeCandidates();
        for (int i = 0; i < songIds.size(); i++) {
            indexer.expungeSong(songIds.get(i));
            if (i % ScanConstants.EXPUNGE_WAIT_INTERVAL == 0) {
                repeatWait();
                if (isInterrupted()) {
                    break;
                }
            }
        }

        // Step 4: Expunge from database in batches
        int minId = mediaFileDao.getMinId();
        int maxId = mediaFileDao.getMaxId();
        LongAdder deleted = new LongAdder();
        int nextWaitThreshold = ScanConstants.EXPUNGE_WAIT_INTERVAL;

        for (int id = minId; id <= maxId; id += ScanConstants.EXPUNGE_BATCH_SIZE) {
            deleted.add(mediaFileDao.expunge(id, id + ScanConstants.EXPUNGE_BATCH_SIZE));

            if (deleted.intValue() > nextWaitThreshold) {
                nextWaitThreshold += ScanConstants.EXPUNGE_WAIT_INTERVAL;
                repeatWait();
                if (isInterrupted()) {
                    break;
                }
            }
        }
    }

    /**
     * Retrieves the media file corresponding to the given path as a root directory,
     * and updates its last scanned timestamp if found.
     *
     * @param path the path to resolve as a media file root
     * @return an {@link Optional} containing the {@link MediaFile} if found, or
     *         empty otherwise
     */
    Optional<MediaFile> getRootDirectory(@NonNull ScanContext context, Path path) {
        MediaFile root = wmfs.getMediaFile(context.scanDate(), path);
        if (root == null) {
            return Optional.empty();
        }

        mediaFileDao.updateLastScanned(root.getId(), context.scanDate());
        return Optional.of(root);
    }

    /**
     * Sorts and reorders a list of {@link Orderable} items based on a given
     * comparator. If the new order differs from the current one, applies the
     * provided updater function to persist the new order and returns the total
     * number of updated entries.
     * <p>
     * The method inserts wait intervals every 6000 updates and checks for
     * interruption.
     * </p>
     *
     * @param <T>        the type of the elements, must implement {@link Orderable}
     * @param list       the list of items to reorder
     * @param comparator the comparator defining the desired order
     * @param updater    a function that updates the item and returns an update
     *                   count (e.g., 1 if updated)
     * @return the total number of items updated
     */
    <T extends Orderable> int invokeUpdateOrder(List<T> list, Comparator<T> comparator,
            Function<T, Integer> updater) {
        // Capture current order before sorting
        List<Integer> rawOrders = list.stream().map(Orderable::getOrder).toList();

        // Sort list in-place according to comparator
        Collections.sort(list, comparator);

        LongAdder count = new LongAdder();

        for (int i = 0; i < list.size(); i++) {
            int expectedOrder = i + 1;
            int currentOrder = rawOrders.get(i);

            if (expectedOrder == currentOrder) {
                continue;
            }

            T item = list.get(i);
            item.setOrder(expectedOrder);
            count.add(updater.apply(item));

            if (count.intValue() % 6_000 == 0) {
                repeatWait();
                if (isInterrupted()) {
                    break;
                }
            }
        }

        return count.intValue();
    }
}

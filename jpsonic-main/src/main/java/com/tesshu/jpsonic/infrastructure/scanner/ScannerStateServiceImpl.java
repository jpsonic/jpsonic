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
 * (C) 2023 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.scanner;

import static com.tesshu.jpsonic.util.PlayerUtils.FAR_PAST;
import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

import com.tesshu.jpsonic.ThreadSafe;
import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent.ScanEventType;
import com.tesshu.jpsonic.persistence.core.repository.StaticsDao;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for managing the runtime state of the scan process,
 * including current execution status and recent scan history.
 * <p>
 * This service centralizes control over various aspects of scanning: whether
 * scanning is currently enabled, whether a scan is in progress, the timestamp
 * of the most recent scan, the total number of executions, and concurrency
 * control through locking. It also serves as a synchronization point for UI
 * components or monitoring services.
 *
 * <h3>Main Responsibilities</h3>
 * <ul>
 * <li>Maintaining and exposing whether scanning is enabled or disabled</li>
 * <li>Determining whether a scan is currently in progress (via reentrant
 * locking)</li>
 * <li>Tracking the timestamp of the most recent scan</li>
 * <li>Counting the total number of scan executions</li>
 * <li>Resetting internal state upon server shutdown</li>
 * </ul>
 *
 * <h3>Design Notes</h3>
 * <p>
 * While {@link ScanContext} holds immutable configuration parameters related to
 * scanning, this class manages mutable runtime state during the scan process.
 * <br>
 * <br>
 * To initiate a scan, {@code setReady()} must be called beforehand. This
 * mechanism ensures that scanning cannot begin until the server has completed
 * critical startup routines (e.g., Lucene initialization or mounting host
 * volumes in Docker). These routines could interfere with scanning if allowed
 * to overlap. <br>
 * <br>
 * Classes that execute scans continue to reference this service throughout the
 * scan, but scanning will not begin until readiness is explicitly declared via
 * {@code setReady()}. This mechanism helps enforce safe and controlled scan
 * initiation timing.
 * </p>
 *
 * @see ScanContext
 * @see MediaScannerServiceImpl
 */
@Primary
@Service("scannerStateService")
public class ScannerStateServiceImpl implements ScannerStateProvider {

    private final StaticsDao staticsDao;

    private final LongAdder scanCount = new LongAdder();

    private final ReentrantLock scanningLock = new ReentrantLock();

    private final AtomicBoolean ready = new AtomicBoolean(false);

    private final AtomicBoolean destroy = new AtomicBoolean();

    private final AtomicBoolean cleansing = new AtomicBoolean(true);

    private Instant scanDate = FAR_PAST;

    private ScanEventType lastEvent = ScanEventType.UNKNOWN;

    public ScannerStateServiceImpl(StaticsDao staticsDao) {
        super();
        this.staticsDao = staticsDao;
    }

    /**
     * Called only once before shutdown.
     */
    void markDestroy() {
        destroy.set(true);
    }

    boolean isDestroy() {
        return destroy.get();
    }

    @Override
    public boolean neverScanned() {
        return staticsDao.isNeverScanned();
    }

    /**
     * Marks the scanner state as ready to begin the scan process.
     * <p>
     * This method should be called after all pre-scan preparations are completed.
     * It sets the internal readiness flag, allowing procedures dependent on
     * readiness to proceed.
     *
     * <p>
     * This readiness state is part of the shared scanner lifecycle management and
     * is not tied to any individual scan context.
     *
     * @return void
     */
    public void setReady() {
        ready.set(true);
    }

    /**
     * Attempts to acquire a lock to indicate that a scan is currently in progress.
     * This is used to coordinate scan concurrency and prevent duplicate executions.
     */
    boolean tryScanningLock() {
        if (!ready.get() || destroy.get()) {
            return false;
        }

        boolean acquired;
        try {
            acquired = scanningLock.tryLock(1500L, TimeUnit.MILLISECONDS);
            if (acquired) {
                Thread.sleep(1);
            }
        } catch (InterruptedException e) {
            acquired = false;
        }
        if (acquired) {
            scanDate = now();
            scanCount.reset();
            lastEvent = ScanEventType.UNKNOWN;
        }
        return acquired;
    }

    /**
     * Returns the date and time of the most recent scan execution.
     *
     * @return the timestamp of the last scan, or {@code null} if never scanned
     */
    @ThreadSafe(enableChecks = false)
    @NonNull
    Instant getScanDate() {
        return scanDate;
    }

    /**
     * Releases the lock acquired by {@code tryScanningLock()}, marking the scan as
     * complete. This method should be called after a scan finishes or is cancelled.
     */
    @ThreadSafe(enableChecks = false) // Use only within the thread that acquired the lock.
    void unlockScanning() {
        scanDate = FAR_PAST;
        scanCount.reset();
        lastEvent = ScanEventType.UNKNOWN;
        scanningLock.unlock();
    }

    /**
     * Checks whether a scan is currently in progress.
     *
     * @return {@code true} if a scan is running, {@code false} otherwise
     */
    @Override
    public boolean isScanning() {
        return ready.get() && scanningLock.isLocked();
    }

    /**
     * Increments the internal scan counter, used to track the number of scan
     * executions. Should be called once per scan cycle.
     */

    void incrementScanCount() {
        scanCount.increment();
    }

    /**
     * Returns the total number of scans that have been executed since startup.
     *
     * @return the scan execution count
     */
    @Override
    public long getScanCount() {
        return scanCount.sum();
    }

    /**
     * Enables or disables cleansing mode during scan. When enabled, this mode may
     * trigger cleanup logic after scanning.
     *
     * @param enable {@code true} to enable cleansing; {@code false} to disable
     */
    void enableCleansing(boolean b) {
        cleansing.set(b);
    }

    /**
     * Checks whether cleansing is enabled for the current scan cycle.
     *
     * @return {@code true} if cleansing is enabled, {@code false} otherwise
     */
    boolean isEnableCleansing() {
        return cleansing.get();
    }

    /**
     * Returns the last {@link ScanEvent} that was emitted during scanning. This is
     * useful for observers or monitoring tools to track scan progress or result.
     *
     * @return the last emitted scan event, or {@code null} if none
     */
    public ScanEventType getLastEvent() {
        return lastEvent;
    }

    /**
     * Stores the most recent {@link ScanEvent} emitted by the scanner. This value
     * can be used for external monitoring or recovery purposes.
     *
     * @param event the scan event to store
     */
    public void setLastEvent(ScanEventType lastEvent) {
        this.lastEvent = lastEvent;
    }
}

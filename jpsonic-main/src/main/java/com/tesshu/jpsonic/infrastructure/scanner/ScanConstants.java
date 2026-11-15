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

import java.util.Arrays;
import java.util.List;

import com.tesshu.jpsonic.persistence.core.entity.ScanEvent.ScanEventType;

/**
 * Defines shared scan-related constants used across scan procedures and support
 * classes.
 *
 * <p>
 * {@code ScanConstants} centralizes commonly used static values that control
 * scan flow, logging frequency, wait intervals, batch sizes, and predefined
 * messages. These constants help maintain consistency, avoid duplication, and
 * clarify the behavior of various scan operations.
 *
 * <h3>Typical Use Cases</h3>
 * <ul>
 * <li>{@link #SCAN_LOG_INTERVAL} Determines how frequently scan log entries
 * should be written (in steps or units)</li>
 * <li>{@link #EXPUNGE_WAIT_INTERVAL} Defines the wait interval (in
 * milliseconds) between expunge operations</li>
 * <li>{@link #EXPUNGE_BATCH_SIZE} Specifies the number of records to delete per
 * batch during cleanup</li>
 * <li>{@link #MSG_SKIP}, {@link #MSG_UNNECESSARY} Standardized message strings
 * for skipped or unnecessary scan results</li>
 * <li>{@link #ACQUISITION_MAX} Sets the maximum number of items to acquire
 * (e.g., during metadata fetch)</li>
 * <li>{@link #REPEAT_WAIT_MILLISECONDS} Wait time used during repeat-wait loops
 * within scan logic</li>
 * </ul>
 *
 * <p>
 * This class is static-only and not intended for instantiation.
 */
public final class ScanConstants {

    // ------------------------------------------------------------------------------------
    // Logging and Scan Event Emission
    // ------------------------------------------------------------------------------------

    /**
     * Number of parsed media files between scan event emissions and log messages.
     * <p>
     * For every {@code SCAN_LOG_INTERVAL} media files scanned, the scanner logs
     * progress and emits a {@link ScanEventType#SCANNED_COUNT} event.
     * </p>
     */
    public static final int SCAN_LOG_INTERVAL = 250;

    // ------------------------------------------------------------------------------------
    // Expunge Behavior
    // ------------------------------------------------------------------------------------

    /**
     * Wait interval (in milliseconds) after every expunge batch to reduce CPU
     * strain.
     */
    public static final int EXPUNGE_WAIT_INTERVAL = 20_000;

    /**
     * Maximum number of items to delete in a single expunge batch.
     */
    public static final int EXPUNGE_BATCH_SIZE = 1_000;

    // ------------------------------------------------------------------------------------
    // Skip Message Constants
    // ------------------------------------------------------------------------------------

    /**
     * Message used when a scan step is skipped due to user or system settings.
     */
    public static final String MSG_SKIP = "Skipped by the settings.";

    /**
     * Message used when a scan step is skipped because the operation was deemed
     * unnecessary.
     */
    public static final String MSG_UNNECESSARY = "Skipped as it is not needed.";

    // ------------------------------------------------------------------------------------
    // Full Scan Phase Definitions
    // ------------------------------------------------------------------------------------

    /**
     * Ordered list of all phases executed during a full scan procedure.
     * <p>
     * Used to determine current phase, progress tracking, and conditional
     * branching.
     * </p>
     */
    public static final List<ScanEventType> SCAN_PHASE_ALL = Arrays
        .asList(ScanEventType.BEFORE_SCAN, ScanEventType.MUSIC_FOLDER_CHECK,
                ScanEventType.PARSE_FILE_STRUCTURE, ScanEventType.PARSE_VIDEO,
                ScanEventType.PARSE_PODCAST, ScanEventType.CLEAN_UP_FILE_STRUCTURE,
                ScanEventType.PARSE_ALBUM, ScanEventType.UPDATE_SORT_OF_ALBUM,
                ScanEventType.UPDATE_ORDER_OF_ALBUM, ScanEventType.UPDATE_SORT_OF_ARTIST,
                ScanEventType.UPDATE_ORDER_OF_ARTIST, ScanEventType.UPDATE_ORDER_OF_SONG,
                ScanEventType.REFRESH_ALBUM_ID3, ScanEventType.UPDATE_ORDER_OF_ALBUM_ID3,
                ScanEventType.REFRESH_ARTIST_ID3, ScanEventType.UPDATE_ORDER_OF_ARTIST_ID3,
                ScanEventType.UPDATE_ALBUM_COUNTS, ScanEventType.UPDATE_GENRE_MASTER,
                ScanEventType.RUN_STATS, ScanEventType.IMPORT_PLAYLISTS, ScanEventType.CHECKPOINT,
                ScanEventType.AFTER_SCAN);

    // ------------------------------------------------------------------------------------
    // Acquisition and Wait Timing
    // ------------------------------------------------------------------------------------

    /**
     * Maximum number of records to acquire in a single batch (e.g., from database).
     */
    public static final int ACQUISITION_MAX = 10_000;

    /**
     * Time in milliseconds to pause during long-running operations (e.g., after
     * each batch).
     */
    public static final int REPEAT_WAIT_MILLISECONDS = 50;

    private ScanConstants() {
        throw new AssertionError("ScanConstants should not be instantiated.");
    }
}

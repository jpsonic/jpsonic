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

package com.tesshu.jpsonic.service;

import java.util.Optional;

import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent.ScanEventType;

/**
 * MediaScanner interface.
 */
public interface MediaScannerService extends ScannerStateProvider {

    /**
     * Returns whether a media library scan is currently in the process of being
     * canceled.
     *
     * @since jpsonic
     */
    boolean isCancel();

    /**
     * Attempt to cancel a scan if it is running.
     *
     * @since jpsonic
     */
    void tryCancel();

    /**
     * Returns the status of whether the previous scan was successful.
     *
     * @return Returns the status of the last completed scan. Empty if neverScanned
     *         or scanning is true, otherwise FINISHED, FAILED, DESTROYED, CANCELED.
     *
     * @since jpsonic
     */
    Optional<ScanEventType> getLastScanEventType();

    /**
     * Scans the media library. The scanning is done asynchronously, i.e., this
     * method returns immediately.
     *
     * @since airsonic
     */
    void scanLibrary();

    /**
     * Returns the current scan phase information if a scan is in progress.
     *
     * @since jpsonic
     * @return An Optional containing the current ScanPhaseInfo, or empty if not
     *         scanning
     */
    Optional<ScanPhaseInfo> getScanPhaseInfo();

    public record ScanPhaseInfo(int phase, int phaseMax, String phaseName, int thread) {
    }
}

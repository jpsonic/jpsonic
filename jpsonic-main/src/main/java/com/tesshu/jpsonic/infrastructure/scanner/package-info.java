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

/**
 * Provides the procedures and supporting utility classes for performing the
 * music file scan process.
 *
 * <p>
 * This package implements a multi-phase scan process targeting media files
 * under the music folder. The process is modularized into distinct
 * {@code ScanProcedure} classes, each handling a specific phase. The execution
 * order of the procedures is fixed, and each step passes its results to the
 * next, enabling a consistent and predictable flow.
 * </p>
 *
 * <h2>Structure of the Scan Flow</h2> The scan flow consists of the following
 * five {@code ScanProcedure} implementations, executed in order:
 *
 * <ol>
 * <li>{@code PreScanProcedure} — Performs validation and preparation before the
 * scan (e.g., verifying configuration integrity).</li>
 * <li>{@code DirectoryScanProcedure} — Recursively traverses the music folder
 * to collect media files and perform initial analysis.</li>
 * <li>{@code FileMetadataScanProcedure} — Constructs album and artist metadata
 * based on file names and folder structure.</li>
 * <li>{@code Id3MetadataScanProcedure} — Builds more accurate metadata using
 * ID3 tags, including a logical {@code ArtistId3}/{@code AlbumId3} structure.
 * Genre information is also gathered here.</li>
 * <li>{@code PostScanProcedure} — Performs final tasks such as logging and
 * statistics updates, and finalizes scan state.</li>
 * </ol>
 *
 * <h2>Supporting and Configuration Classes</h2>
 *
 * <ul>
 * <li>{@code ScanContext} — An immutable object holding scan-related settings
 * and flags shared across all {@code ScanProcedure} implementations. It
 * includes the scan start time, whether to ignore file timestamps, logging
 * flags, and memory measurement options.</li>
 * <li>{@code ScanHelper} — Provides utility methods used across scan phases. It
 * centralizes logic such as metadata normalization, sort order calculation, and
 * cover art selection, and serves as a bridge between procedures.</li>
 * <li>{@code ScanConstants} — Contains fixed values and string constants used
 * throughout the scan process.</li>
 * <li>{@code WritableMediaFileService} — A write-focused service for creating,
 * updating, and deleting media file records during the scan. It is designed to
 * prevent transactional conflicts between scan-time updates and other updates,
 * using targeted field-level updates and optional locking via
 * {@code ScannerStateService}.</li>
 * <li>{@code ScannerStateService} — Manages the current state of the scan
 * process, including locking and cancellation flags. Note that the scan cannot
 * begin until {@code setReady()} is called, which is intended to be invoked
 * only after system startup tasks (e.g., Lucene initialization, disk mounting)
 * have completed. This ensures that scanning does not start during unstable
 * system states.</li>
 * </ul>
 *
 * <p>
 * Each {@code ScanProcedure} operates based on the configuration provided in
 * {@code ScanContext}, and coordinates with {@code ScanHelper},
 * {@code WritableMediaFileService}, and {@code ScannerStateService} to carry
 * out its responsibilities. This modular design improves the clarity,
 * extensibility, and maintainability of the entire scan process.
 * </p>
 */
package com.tesshu.jpsonic.infrastructure.scanner;

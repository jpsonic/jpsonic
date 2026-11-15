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

package com.tesshu.jpsonic.infrastructure.scanner;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Documented;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.persistence.core.repository.StaticsDao;
import com.tesshu.jpsonic.util.PlayerUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SuppressWarnings("PMD.TooManyStaticImports")
class ScannerStateServiceImplTest {

    private StaticsDao staticsDao;
    private ScannerStateServiceImpl scannerStateService;

    @BeforeEach
    void setup() {
        staticsDao = mock(StaticsDao.class);
        scannerStateService = new ScannerStateServiceImpl(staticsDao);
    }

    @Test
    void testNeverScanned() {
        Mockito.when(staticsDao.isNeverScanned()).thenReturn(true);
        assertTrue(scannerStateService.neverScanned());

        Mockito.when(staticsDao.isNeverScanned()).thenReturn(false);
        assertFalse(scannerStateService.neverScanned());
    }

    @Documented
    private @interface TryScanningLockDecisions {
        @interface Conditions {
            @interface Ready {
                @interface False {
                }

                @interface True {
                }
            }

            @interface Destroy {
                @interface False {
                }

                @interface True {
                }
            }

            @interface IsScanning {
                @interface False {
                }

                @interface True {
                }
            }
        }

        @interface Result {
            @interface False {
            }

            @interface True {
            }
        }
    }

    @Nested
    class TryScanningLockTest {

        @Test
        @TryScanningLockDecisions.Conditions.Ready.False
        @TryScanningLockDecisions.Result.False
        void c01() {
            assertFalse(scannerStateService.tryScanningLock());
        }

        @Test
        @TryScanningLockDecisions.Conditions.Ready.True
        @TryScanningLockDecisions.Conditions.Destroy.True
        @TryScanningLockDecisions.Result.False
        void c02() {
            scannerStateService.setReady();
            scannerStateService.markDestroy();
            assertTrue(scannerStateService.isDestroy());
            assertFalse(scannerStateService.tryScanningLock());
        }

        @Test
        @TryScanningLockDecisions.Conditions.Ready.True
        @TryScanningLockDecisions.Conditions.Destroy.False
        @TryScanningLockDecisions.Conditions.IsScanning.True
        @TryScanningLockDecisions.Result.False
        void c03() throws InterruptedException, ExecutionException {
            scannerStateService.setReady();
            assertFalse(scannerStateService.isDestroy());
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.initialize();
            executor.submit(scannerStateService::tryScanningLock).get();
            assertTrue(scannerStateService.isScanning());
            assertFalse(scannerStateService.tryScanningLock());
            executor.shutdown();
        }

        @Test
        @TryScanningLockDecisions.Conditions.Ready.True
        @TryScanningLockDecisions.Conditions.Destroy.False
        @TryScanningLockDecisions.Conditions.IsScanning.False
        @TryScanningLockDecisions.Result.True
        void c04() {
            scannerStateService.setReady();
            assertFalse(scannerStateService.isDestroy());
            assertFalse(scannerStateService.isScanning());
            assertTrue(scannerStateService.tryScanningLock());
            assertTrue(scannerStateService.tryScanningLock());
        }
    }

    @Documented
    private @interface GetScanDateDecisions {
        @interface Conditions {
            @interface IsScanning {
                @interface False {
                }

                @interface True {
                }
            }
        }

        @interface Result {
            @interface EqFarPast {
            }

            @interface NeFarPast {
            }
        }
    }

    @Nested
    class GetScanDateTest {

        @Test
        @GetScanDateDecisions.Conditions.IsScanning.False
        @GetScanDateDecisions.Result.EqFarPast
        void c01() {
            assertFalse(scannerStateService.isScanning());
            assertEquals(PlayerUtils.FAR_PAST, scannerStateService.getScanDate());
        }

        @Test
        @GetScanDateDecisions.Conditions.IsScanning.True
        @GetScanDateDecisions.Result.NeFarPast
        void c02() {
            scannerStateService.setReady();
            assertTrue(scannerStateService.tryScanningLock());
            assertTrue(scannerStateService.isScanning());
            assertNotEquals(PlayerUtils.FAR_PAST, scannerStateService.getScanDate());
            scannerStateService.unlockScanning();
            assertFalse(scannerStateService.isScanning());
            assertEquals(PlayerUtils.FAR_PAST, scannerStateService.getScanDate());
        }
    }

    @Documented
    private @interface UnlockScanningDecisions {
        @interface Conditions {
            @interface IsScanning {
                @interface False {
                }

                @interface True {
                    @interface OthersLock {
                    }

                    @interface OwnLock {
                    }
                }
            }
        }

        @interface Result {
            @interface IllegalMonitorStateException {
            }

            @interface Success {
                @interface ScanDateEqFarPast {
                }

                @interface ScanCountEqZero {
                }
            }
        }
    }

    @Nested
    class UnlockScanningTest {

        @Test
        @UnlockScanningDecisions.Conditions.IsScanning.False
        @UnlockScanningDecisions.Result.IllegalMonitorStateException
        // (Unreachable case)
        void c01() {
            assertFalse(scannerStateService.isScanning());
            assertThatExceptionOfType(IllegalMonitorStateException.class)
                .isThrownBy(scannerStateService::unlockScanning)
                .withNoCause();
        }

        @Test
        @UnlockScanningDecisions.Conditions.IsScanning.True.OthersLock
        @UnlockScanningDecisions.Result.IllegalMonitorStateException
        // (Unreachable case)
        void c02() throws InterruptedException, ExecutionException {
            scannerStateService.setReady();
            assertFalse(scannerStateService.isDestroy());
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.initialize();
            executor.submit(scannerStateService::tryScanningLock).get();
            assertTrue(scannerStateService.isScanning());
            assertThatExceptionOfType(IllegalMonitorStateException.class)
                .isThrownBy(scannerStateService::unlockScanning)
                .withNoCause();
            executor.shutdown();
        }

        @Test
        @UnlockScanningDecisions.Conditions.IsScanning.True.OwnLock
        @UnlockScanningDecisions.Result.Success.ScanDateEqFarPast
        @UnlockScanningDecisions.Result.Success.ScanCountEqZero
        void c03() {
            assertFalse(scannerStateService.isScanning());
            scannerStateService.setReady();
            assertTrue(scannerStateService.tryScanningLock());
            assertTrue(scannerStateService.isScanning());

            assertNotEquals(PlayerUtils.FAR_PAST, scannerStateService.getScanDate());
            scannerStateService.incrementScanCount();
            assertEquals(1, scannerStateService.getScanCount());

            scannerStateService.unlockScanning();
            assertEquals(PlayerUtils.FAR_PAST, scannerStateService.getScanDate());
            assertEquals(0, scannerStateService.getScanCount());
        }
    }

    @Test
    void testScanCount() {

        scannerStateService.setReady();

        assertEquals(0, scannerStateService.getScanCount());
        scannerStateService.incrementScanCount();
        assertEquals(1, scannerStateService.getScanCount());
        scannerStateService.incrementScanCount();
        assertEquals(2, scannerStateService.getScanCount());

        scannerStateService.tryScanningLock();
        assertEquals(0, scannerStateService.getScanCount());
        scannerStateService.incrementScanCount();
        assertEquals(1, scannerStateService.getScanCount());
        scannerStateService.incrementScanCount();
        assertEquals(2, scannerStateService.getScanCount());
        scannerStateService.unlockScanning();

        assertEquals(0, scannerStateService.getScanCount());
        scannerStateService.incrementScanCount();
        assertEquals(1, scannerStateService.getScanCount());
        scannerStateService.incrementScanCount();
        assertEquals(2, scannerStateService.getScanCount());
    }
}

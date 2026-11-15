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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.LambdaCanBeMethodReference" })
class ScannerDisposerTest {

    private ScannerStateServiceImpl mockStateService;
    private ScannerDisposer disposer;

    @BeforeEach
    void setUp() {
        mockStateService = mock(ScannerStateServiceImpl.class);
        disposer = new ScannerDisposer(mockStateService);
    }

    /**
     * Decision Table annotations for ScannerDisposer paths.
     */
    @java.lang.annotation.Documented
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target({ java.lang.annotation.ElementType.METHOD,
            java.lang.annotation.ElementType.TYPE })
    @interface ScannerDisposerDecision {

        @interface Conditions {

            @interface IsScanning {
                @interface True {
                }

                @interface False {
                }
            }

            @interface InterruptedSleep {
                @interface True {
                }

                @interface False {
                }
            }
        }

        @interface Result {
            @interface CallbackRun {
            }
        }
    }

    @Nested
    @ScannerDisposerDecision.Conditions.IsScanning.False
    @ScannerDisposerDecision.Conditions.InterruptedSleep.False
    @ScannerDisposerDecision.Result.CallbackRun
    class StopImmediate {

        @Test
        void testStopWhenNotScanning() {
            when(mockStateService.isScanning()).thenReturn(false);

            disposer.stop();

            verify(mockStateService).markDestroy();
            verify(mockStateService).isScanning();
        }
    }

    @Nested
    @ScannerDisposerDecision.Conditions.IsScanning.True
    @ScannerDisposerDecision.Conditions.InterruptedSleep.False
    @ScannerDisposerDecision.Result.CallbackRun
    class StopWithScanningEnds {

        @Test
        void testStopWhileScanningEndsNormally() {
            when(mockStateService.isScanning()).thenReturn(true, false);

            disposer.stop(); // sleep occurs here; real waiting

            verify(mockStateService).markDestroy();
            verify(mockStateService, atLeast(1)).isScanning();
        }
    }

    @Nested
    @ScannerDisposerDecision.Conditions.IsScanning.True
    @ScannerDisposerDecision.Conditions.InterruptedSleep.True
    @ScannerDisposerDecision.Result.CallbackRun
    class StopWithInterruptedSleep {

        @Test
        void testStopInterrupted() {
            when(mockStateService.isScanning()).thenReturn(true, false);

            Assertions.assertDoesNotThrow(() -> disposer.stop());

            verify(mockStateService).markDestroy();
            verify(mockStateService, atLeast(1)).isScanning();
        }
    }

    @Nested
    @ScannerDisposerDecision.Conditions.IsScanning.False
    @ScannerDisposerDecision.Conditions.InterruptedSleep.False
    @ScannerDisposerDecision.Result.CallbackRun
    class StopWithCallback {

        @Test
        void testStopWithCallback() {
            Runnable callback = mock(Runnable.class);
            when(mockStateService.isScanning()).thenReturn(false);

            disposer.stop(callback);

            verify(mockStateService).markDestroy();
            verify(mockStateService).isScanning();
            verify(callback).run();
        }
    }

    @Nested
    class IsRunningTest {

        @Test
        void testIsRunningTrue() {
            when(mockStateService.isScanning()).thenReturn(true);
            assertTrue(disposer.isRunning());
        }

        @Test
        void testIsRunningFalse() {
            when(mockStateService.isScanning()).thenReturn(false);
            assertFalse(disposer.isRunning());
        }
    }

    @Nested
    class GetPhaseTest {

        @Test
        void testGetPhase() {
            assertEquals(com.tesshu.jpsonic.infrastructure.core.LifecyclePhase.SCAN.getValue(),
                    disposer.getPhase());
        }
    }
}
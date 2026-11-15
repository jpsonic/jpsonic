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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tesshu.jpsonic.infrastructure.core.LifecyclePhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.TooManyStaticImports")
class PodcastDisposerTest {

    private PodcastServiceImpl podcastService;
    private PodcastDisposer disposer;

    @BeforeEach
    void setUp() {
        podcastService = mock(PodcastServiceImpl.class);
        disposer = new PodcastDisposer(podcastService);
    }

    @Test
    void testStopCallsMarkDestroy() {
        disposer.stop();
        verify(podcastService).markDestroy();
    }

    @Test
    void testStopWithCallbackCallsMarkDestroyAndCallback() {
        Runnable callback = mock(Runnable.class);
        disposer.stop(callback);
        verify(podcastService).markDestroy();
        verify(callback).run();
    }

    @Test
    void testIsRunningDelegatesToService() {
        when(podcastService.isRunning()).thenReturn(true, false);

        assertTrue(disposer.isRunning());
        assertFalse(disposer.isRunning());

        verify(podcastService, times(2)).isRunning();
    }

    @Test
    void testGetPhaseReturnsScanPhase() {
        assertEquals(LifecyclePhase.PODCAST.getValue(), disposer.getPhase());
    }
}

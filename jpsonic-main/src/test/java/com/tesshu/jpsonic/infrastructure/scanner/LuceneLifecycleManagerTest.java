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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.tesshu.jpsonic.infrastructure.core.NeedsHome;
import com.tesshu.jpsonic.persistence.api.repository.ArtistDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@NeedsHome
class LuceneLifecycleManagerTest {

    private AnalyzerFactory analyzerFactory;
    private IndexManager indexManager;
    private LuceneLifecycleManager lifecycleManager;

    @BeforeEach
    void setUp() {
        analyzerFactory = mock(AnalyzerFactory.class);
        indexManager = mock(IndexManager.class);
        ScannerStateServiceImpl scannerState = mock(ScannerStateServiceImpl.class);
        ArtistDao artistDao = mock(ArtistDao.class);
        lifecycleManager = new LuceneLifecycleManager(analyzerFactory, indexManager, scannerState,
                artistDao);
    }

    @Test
    void startSetsRunningTrue() {
        lifecycleManager.start();
        assertTrue(lifecycleManager.isRunning());
    }

    @Test
    void stopCallsDestroyAndSetsRunningFalse() {
        lifecycleManager.start();
        lifecycleManager.stop();

        verify(indexManager).destroy();
        verify(analyzerFactory).destroy();
        assertFalse(lifecycleManager.isRunning());
    }

    @Test
    void stopWithCallbackCallsDestroyAndCallback() {
        lifecycleManager.start();
        Runnable callback = mock(Runnable.class);

        lifecycleManager.stop(callback);

        verify(indexManager).destroy();
        verify(analyzerFactory).destroy();
        verify(callback).run();
        assertFalse(lifecycleManager.isRunning());
    }

    @Test
    void isRunningReturnsCorrectValue() {
        assertFalse(lifecycleManager.isRunning());

        lifecycleManager.start();
        assertTrue(lifecycleManager.isRunning());

        lifecycleManager.stop();
        assertFalse(lifecycleManager.isRunning());
    }
}

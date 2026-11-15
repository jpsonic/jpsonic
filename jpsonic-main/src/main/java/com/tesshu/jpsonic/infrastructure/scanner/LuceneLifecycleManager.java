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

import java.util.concurrent.atomic.AtomicBoolean;

import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.infrastructure.core.LifecyclePhase;
import com.tesshu.jpsonic.persistence.api.repository.ArtistDao;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class LuceneLifecycleManager implements SmartLifecycle {

    private final AnalyzerFactory analyzerFactory;
    private final IndexManager indexManager;
    private final ScannerStateServiceImpl scannerState;
    private final ArtistDao artistDao;

    private final AtomicBoolean running;

    public LuceneLifecycleManager(AnalyzerFactory analyzerFactory, IndexManager indexManager,
            ScannerStateServiceImpl scannerState, ArtistDao artistDao) {
        this.analyzerFactory = analyzerFactory;
        this.indexManager = indexManager;
        this.scannerState = scannerState;
        this.artistDao = artistDao;
        running = new AtomicBoolean(false);
    }

    @Override
    public void start() {

        /*
         * Reset artist data to allow a consistent full scan when the index directory is
         * missing. This is intended for recovery from an abnormal state (e.g. manual
         * deletion of the index), not for a fresh installation.
         */
        Runnable onFirstCreation = artistDao::deleteAll;

        // Initialize IndexManager
        EnvironmentProvider.getInstance().deleteLegacyFiles();
        EnvironmentProvider.getInstance().deleteOldFiles();
        EnvironmentProvider.getInstance().initializeIndexDirectory(onFirstCreation);
        scannerState.setReady();

        running.set(true);
    }

    @Override
    public void stop() {
        indexManager.destroy();
        analyzerFactory.destroy();
        running.set(false);
    }

    @Override
    public void stop(Runnable callback) {
        if (running.get()) {
            indexManager.destroy();
            analyzerFactory.destroy();
            running.set(false);
        }
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return LifecyclePhase.SEARCHER.getValue();
    }
}

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
 * (C) 2009 Sindre Mehus
 * (C) 2016 Airsonic Authors
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic;

import static org.springframework.util.ObjectUtils.isEmpty;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.tesshu.jpsonic.infrastructure.scanner.DirectoryScanProcedure;
import com.tesshu.jpsonic.infrastructure.scanner.FileMetadataScanProcedure;
import com.tesshu.jpsonic.infrastructure.scanner.Id3MetadataScanProcedure;
import com.tesshu.jpsonic.infrastructure.scanner.MediaScannerServiceImpl;
import com.tesshu.jpsonic.infrastructure.scanner.MusicFolderServiceImpl;
import com.tesshu.jpsonic.infrastructure.scanner.PostScanProcedure;
import com.tesshu.jpsonic.infrastructure.scanner.PreScanProcedure;
import com.tesshu.jpsonic.infrastructure.scanner.ScanHelper;
import com.tesshu.jpsonic.infrastructure.scanner.ScannerStateServiceImpl;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.NeedsDB;
import com.tesshu.jpsonic.persistence.api.repository.MusicFolderDao;
import com.tesshu.jpsonic.persistence.base.DaoHelper;
import com.tesshu.jpsonic.persistence.core.repository.StaticsDao;
import com.tesshu.jpsonic.service.MediaScannerService;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.UserService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/*
 * Abstract class for scanning MusicFolder.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@NeedsDB
public abstract class AbstractNeedsScan implements NeedsScan {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractNeedsScan.class);

    /*
     * Currently, Maven is executing test classes in series, so this class can hold
     * the state. When executing in parallel, subclasses should override this.
     */
    private static final AtomicBoolean DATA_BASE_POPULATED = new AtomicBoolean();

    // Above.
    private static final AtomicBoolean DATABASE_READY = new AtomicBoolean();

    @Autowired
    protected DaoHelper daoHelper;
    @Autowired
    protected MusicFolderDao musicFolderDao;
    @Autowired
    protected SettingsFacade settingsFacade;
    @Autowired
    protected MusicFolderServiceImpl musicFolderService;
    @Autowired
    protected UserService userService;
    @Autowired
    private ScannerStateServiceImpl scannerStateService;
    @Autowired
    private PreScanProcedure preScanProc;
    @Autowired
    private DirectoryScanProcedure directoryScanProc;
    @Autowired
    private FileMetadataScanProcedure fileMetaProc;
    @Autowired
    private Id3MetadataScanProcedure id3MetaProc;
    @Autowired
    private PostScanProcedure postScanProc;
    @Autowired
    private ScanHelper scanHelper;

    @Autowired
    private StaticsDao staticsDao;

    private final ThreadPoolTaskExecutor scanExecutor = ServiceMockUtils.mockNoAsyncTaskExecutor();

    protected MediaScannerService mediaScannerService;

    @PostConstruct
    public void init() {
        mediaScannerService = new MediaScannerServiceImpl(settingsFacade, scannerStateService,
                preScanProc, directoryScanProc, fileMetaProc, id3MetaProc, postScanProc, scanHelper,
                staticsDao, scanExecutor);
    }

    @FunctionalInterface
    public interface BeforeScan extends Supplier<Boolean> {
    }

    @FunctionalInterface
    public interface AfterScan extends Supplier<Boolean> {
    }

    protected static final String resolveBaseMediaPath(String childPath) {
        return Path
            .of(MusicFolderTestDataUtils.resolveBaseMediaPath().concat(childPath))
            .toString();
    }

    @Override
    public boolean isDataBasePopulated() {
        return DATA_BASE_POPULATED.get();
    }

    @Override
    public boolean isDataBaseReady() {
        return DATABASE_READY.get();
    }

    @Override
    public final void populateDatabase() {
        DATA_BASE_POPULATED.set(false);
        DATABASE_READY.set(false);
        populateDatabaseOnlyOnce(null);
    }

    public final void populateDatabase(BeforeScan beforeScan, AfterScan afterscan) {
        DATA_BASE_POPULATED.set(false);
        DATABASE_READY.set(false);
        populateDatabaseOnlyOnce(beforeScan, afterscan);
    }

    @Override
    public final void populateDatabaseOnlyOnce() {
        populateDatabaseOnlyOnce(null);
    }

    public final void populateDatabaseOnlyOnce(BeforeScan beforeScan) {
        populateDatabaseOnlyOnce(beforeScan, null);
    }

    @SuppressWarnings({ "PMD.CognitiveComplexity", "PMD.AvoidDeeplyNestedIfStmts" })
    // AvoidDeeplyNestedIfStmts (false positive)
    public final void populateDatabaseOnlyOnce(BeforeScan beforeScan, AfterScan afterscan) {
        if (isDataBasePopulated()) {
            while (!isDataBaseReady()) {
                try {
                    // The subsequent test method waits while reading DB data.
                    for (int i = 0; i < 10; i++) {
                        Thread.sleep(500);
                    }
                } catch (InterruptedException e) {
                    LOG.error("Database initialization was interrupted unexpectedly.", e);
                }
            }
        } else {
            DATA_BASE_POPULATED.set(true);
            getMusicFolders().forEach(musicFolderDao::createMusicFolder);
            musicFolderService.clearMusicFolderCache();
            try {
                // Await time to avoid scan failure.
                for (int i = 0; i < 5; i++) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                LOG.error("Database initialization was interrupted unexpectedly.", e);
            }

            if (!isEmpty(beforeScan) && beforeScan.get() && LOG.isDebugEnabled()) {
                LOG.debug("Pre-processing of scan was called.");
            }

            TestCaseUtils.execScan(mediaScannerService);

            if (!isEmpty(afterscan) && afterscan.get() && LOG.isDebugEnabled()) {
                LOG.debug("Post-processing of scan was called.");
            }

            logRecordsPerTables();

            try {
                // Await for Lucene to finish writing(asynchronous).
                for (int i = 0; i < 5; i++) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                LOG.error("Database initialization was interrupted unexpectedly.", e);
            }
            DATABASE_READY.set(true);
        }
    }

    @SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts")
    // AvoidDeeplyNestedIfStmts (false positive)
    private void logRecordsPerTables() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("--- Report of records count per table ---");
        }
        Map<String, Integer> records = TestCaseUtils.recordsInAllTables(daoHelper);
        records
            .keySet()
            .stream()
            .filter(s -> "MEDIA_FILE".equals(s) | "ARTIST".equals(s) | "MUSIC_FOLDER".equals(s)
                    | "ALBUM".equals(s) | "GENRE".equals(s))
            .forEach(tableName -> {
                if (LOG.isInfoEnabled()) {
                    LOG.info("\t" + tableName + " : " + records.get(tableName).toString());
                }
            });
        if (LOG.isDebugEnabled()) {
            LOG.debug("--- *********************** ---");
        }

    }
}

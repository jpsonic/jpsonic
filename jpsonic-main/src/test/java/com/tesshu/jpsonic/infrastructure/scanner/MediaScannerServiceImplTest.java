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

package com.tesshu.jpsonic.infrastructure.scanner;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.annotation.Documented;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.tesshu.jpsonic.MusicFolderTestDataUtils;
import com.tesshu.jpsonic.TestCaseUtils;
import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators;
import com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators.OrderBy;
import com.tesshu.jpsonic.infrastructure.filesystem.FileOperations;
import com.tesshu.jpsonic.infrastructure.filesystem.ScanningExclusionPolicy;
import com.tesshu.jpsonic.infrastructure.language.JapaneseReadingUtils;
import com.tesshu.jpsonic.infrastructure.language.MetadataReadingProcessor;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.NeedsDB;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.Artist;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.AlbumDao;
import com.tesshu.jpsonic.persistence.api.repository.ArtistDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.api.repository.MusicFolderDao;
import com.tesshu.jpsonic.persistence.base.DaoHelper;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent.ScanEventType;
import com.tesshu.jpsonic.persistence.core.repository.StaticsDao;
import com.tesshu.jpsonic.service.MediaFileCache;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.MediaScannerService;
import com.tesshu.jpsonic.service.PlaylistService;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.metadata.MusicParser;
import com.tesshu.jpsonic.service.metadata.VideoParser;
import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals" })
class MediaScannerServiceImplTest {

    private static final Logger LOG = LoggerFactory.getLogger(MediaScannerServiceImplTest.class);

    @Documented
    private @interface TryCancelDecisions {
        @interface Conditions {
            @interface IsScanning {
                @interface True {
                    @interface OwnLock {
                    }

                    @interface OthersLock {
                    }
                }

                @interface False {
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

    @Documented
    private @interface IsOptionalProcessSkippableDecisions {

        @interface Conditions {
            @interface IgnoreFileTimestamps {
                @interface False {
                }

                @interface True {
                }
            }

            @interface LastScanEventType {
                @interface NotPresent {

                }

                @interface Present {
                    @interface ScanEventType {
                        @interface EqFinished {
                        }

                        @interface NeFinished {
                        }
                    }
                }
            }

            @interface FolderChangedSinceLastScan {
                @interface True {
                }

                @interface False {
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

    @SuppressWarnings("PMD.SingularField") // pmd/pmd#4616
    @Nested
    class UnitTest {

        private SettingsFacade settingsFacade;
        private Indexer indexer;
        private IndexManager indexManager;
        private ArtistDao artistDao;
        private AlbumDao albumDao;
        private MediaFileService mediaFileService;
        private MediaFileDao mediaFileDao;
        private StaticsDao staticsDao;
        private ScannerStateServiceImpl scannerStateService;
        private SortProcedureService utils;
        private WritableMediaFileService writableMediaFileService;

        private ScanHelper scanHelper;
        private PreScanProcedure preScanProc;
        private DirectoryScanProcedure directoryScanProc;
        private FileMetadataScanProcedure fileMetaProc;
        private Id3MetadataScanProcedure id3MetaProc;
        private PostScanProcedure postScanProc;
        private MediaScannerServiceImpl mediaScannerService;

        @BeforeEach
        void setup() {
            settingsFacade = SettingsFacadeBuilder.create().build();
            init();
        }

        @Ignore
        void init() {
            indexManager = mock(IndexManager.class);
            indexer = mock(Indexer.class);
            mediaFileService = mock(MediaFileService.class);
            mediaFileDao = mock(MediaFileDao.class);
            artistDao = mock(ArtistDao.class);
            albumDao = mock(AlbumDao.class);
            utils = mock(SortProcedureService.class);
            staticsDao = mock(StaticsDao.class);
            scannerStateService = new ScannerStateServiceImpl(staticsDao);

            JapaneseReadingUtils readingUtils = new JapaneseReadingUtils(settingsFacade);
            MetadataReadingProcessor readingProcessor = new MetadataReadingProcessor(settingsFacade,
                    readingUtils);
            writableMediaFileService = new WritableMediaFileService(mediaFileDao,
                    scannerStateService, mediaFileService, albumDao, mock(MediaFileCache.class),
                    mock(MusicParser.class), mock(VideoParser.class), settingsFacade,
                    mock(LibraryAccessPolicy.class), new ScanningExclusionPolicy(settingsFacade),
                    readingProcessor, mock(Indexer.class), mock(MusicIndexProviderImpl.class));

            final MusicFolderServiceImpl musicFolderService = mock(MusicFolderServiceImpl.class);
            final PlaylistService playlistService = mock(PlaylistService.class);
            final TemplateWrapper templateWrapper = mock(TemplateWrapper.class);
            final MusicIndexProviderImpl musicIndexProviderImpl = mock(
                    MusicIndexProviderImpl.class);
            final MediaFileCache mediaFileCache = mock(MediaFileCache.class);
            final MetadataReadingProcessor proc = mock(MetadataReadingProcessor.class);
            final JpsonicComparators comparators = mock(JpsonicComparators.class);
            final ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);

            scanHelper = new ScanHelper(scannerStateService, settingsFacade, staticsDao,
                    mediaFileDao, indexer, writableMediaFileService);
            preScanProc = new PreScanProcedure(musicFolderService, indexManager, mediaFileDao,
                    artistDao, mediaFileCache, scanHelper);
            directoryScanProc = new DirectoryScanProcedure(mediaFileDao, musicFolderService,
                    writableMediaFileService, scannerStateService, indexer, scanHelper);
            fileMetaProc = new FileMetadataScanProcedure(musicFolderService, indexer,
                    mediaFileService, writableMediaFileService, mediaFileDao, utils,
                    scannerStateService, scanHelper, musicIndexProviderImpl, proc, comparators);
            GenreMasterProviderAdapter genreMasterProviderAdapter = mock(
                    GenreMasterProviderAdapter.class);
            id3MetaProc = new Id3MetadataScanProcedure(musicFolderService, indexer,
                    genreMasterProviderAdapter, mediaFileService, mediaFileDao, artistDao, albumDao,
                    musicIndexProviderImpl, comparators, scanHelper);
            postScanProc = new PostScanProcedure(musicFolderService, indexManager, playlistService,
                    templateWrapper, staticsDao, utils, mediaFileCache, scanHelper,
                    mock(ApplicationEventPublisher.class));

            mediaScannerService = new MediaScannerServiceImpl(settingsFacade, scannerStateService,
                    preScanProc, directoryScanProc, fileMetaProc, id3MetaProc, postScanProc,
                    scanHelper, staticsDao, executor);
        }

        @SuppressWarnings("PMD.UnitTestShouldIncludeAssert") // It doesn't seem to be able to
                                                             // capture
        @Test
        void testPodcast() throws URISyntaxException {
            indexManager = mock(IndexManager.class);
            indexer = mock(Indexer.class);
            Mockito.doNothing().when(indexManager).startIndexing();
            Path podcastPath = Path
                .of(MediaScannerServiceImplTest.class.getResource("/MEDIAS/Scan/Null").toURI());
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.podcast.folder, podcastPath.toString())
                .build();
            init();

            MediaFile mediaFile = new MediaFile();
            mediaFile.setPathString(podcastPath.toString());
            Mockito.when(mediaFileService.getMediaFile(podcastPath)).thenReturn(mediaFile);
            ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
            mediaScannerService = new MediaScannerServiceImpl(settingsFacade, scannerStateService,
                    preScanProc, directoryScanProc, fileMetaProc, id3MetaProc, postScanProc,
                    scanHelper, staticsDao, executor);
            mediaScannerService.scanLibrary();
        }

        @Nested
        class SetCancelTest {

            @Test
            @TryCancelDecisions.Conditions.IsScanning.False
            @TryCancelDecisions.Result.False
            void c01() {
                assertFalse(mediaScannerService.isCancel());
                mediaScannerService.tryCancel();
                assertFalse(mediaScannerService.isCancel());
            }

            @Test
            @TryCancelDecisions.Conditions.IsScanning.True.OwnLock
            @TryCancelDecisions.Result.True
            void c02() {
                scannerStateService.setReady();
                assertTrue(scannerStateService.tryScanningLock());
                assertTrue(mediaScannerService.isScanning());
                assertFalse(mediaScannerService.isCancel());
                mediaScannerService.tryCancel();
                assertTrue(mediaScannerService.isCancel());
            }

            @Test
            @TryCancelDecisions.Conditions.IsScanning.True.OthersLock
            @TryCancelDecisions.Result.True
            void c03() throws InterruptedException {
                scannerStateService.setReady();
                ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
                executor.initialize();
                executor.submit(scannerStateService::tryScanningLock);
                Thread.sleep(50);
                assertTrue(scannerStateService.isScanning());
                assertFalse(mediaScannerService.isCancel());
                mediaScannerService.tryCancel();
                assertTrue(mediaScannerService.isCancel());
                executor.shutdown();
            }
        }

        @Nested
        class GetLastScanEventTypeTest {

            /*
             * If scanning is in progress, recovery is expected, so previous results are not
             * returned.
             */
            @Test
            void testWithScanning() {
                scannerStateService.setReady();
                assertTrue(scannerStateService.tryScanningLock());
                assertTrue(mediaScannerService.getLastScanEventType().isEmpty());
            }

            /* No results are returned if a scan has not been run. */
            @Test
            void testNeverScanned() {
                assertFalse(mediaScannerService.isScanning());
                Mockito.when(staticsDao.isNeverScanned()).thenReturn(true);
                assertTrue(mediaScannerService.getLastScanEventType().isEmpty());
            }

            /*
             * A record may not have been recorded if the scan was interrupted by an
             * unchecked exception.
             */
            @Test
            void testNoRecords() {
                assertFalse(mediaScannerService.isScanning());
                Mockito.when(staticsDao.isNeverScanned()).thenReturn(false);
                assertFalse(mediaScannerService.getLastScanEventType().isEmpty());
                assertEquals(ScanEventType.FAILED,
                        mediaScannerService.getLastScanEventType().get());
            }

            /*
             * If the scan ran to completion, it would have logged FINISHED, DESTROYED, or
             * CANCELED.
             */
            @Test
            void testWithRecords() {
                assertFalse(mediaScannerService.isScanning());
                Mockito.when(staticsDao.isNeverScanned()).thenReturn(false);
                ScanEvent success = new ScanEvent(null, null, ScanEventType.SUCCESS, 0L, 0L, 0L,
                        null, null);
                Mockito
                    .when(staticsDao.getLastScanAllStatuses())
                    .thenReturn(Arrays.asList(success));
                assertFalse(mediaScannerService.getLastScanEventType().isEmpty());
                assertEquals(ScanEventType.SUCCESS,
                        mediaScannerService.getLastScanEventType().get());
            }
        }

        @Nested
        class IsOptionalProcessSkippableTest {

            @Test
            @IsOptionalProcessSkippableDecisions.Conditions.IgnoreFileTimestamps.True
            @IsOptionalProcessSkippableDecisions.Result.False
            void c01() {
                settingsFacade = SettingsFacadeBuilder
                    .create()
                    .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, true)
                    .build();

                assertFalse(mediaScannerService.isOptionalProcessSkippable());
            }

            @Test
            @IsOptionalProcessSkippableDecisions.Conditions.IgnoreFileTimestamps.False
            @IsOptionalProcessSkippableDecisions.Conditions.LastScanEventType.NotPresent
            @IsOptionalProcessSkippableDecisions.Result.False
            void c02() {
                settingsFacade = SettingsFacadeBuilder
                    .create()
                    .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, false)
                    .build();

                Mockito.when(staticsDao.isNeverScanned()).thenReturn(true);
                assertFalse(mediaScannerService.isOptionalProcessSkippable());
            }

            @Test
            @IsOptionalProcessSkippableDecisions.Conditions.IgnoreFileTimestamps.False
            @IsOptionalProcessSkippableDecisions.Conditions.LastScanEventType.Present.ScanEventType.NeFinished
            @IsOptionalProcessSkippableDecisions.Result.False
            void c03() {
                settingsFacade = SettingsFacadeBuilder
                    .create()
                    .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, false)
                    .build();
                Mockito
                    .when(staticsDao.getLastScanAllStatuses())
                    .thenReturn(Arrays
                        .asList(new ScanEvent(null, null, ScanEventType.CANCELED, null, null, null,
                                null, null)));

                assertFalse(mediaScannerService.isOptionalProcessSkippable());
            }

            @Test
            @IsOptionalProcessSkippableDecisions.Conditions.IgnoreFileTimestamps.False
            @IsOptionalProcessSkippableDecisions.Conditions.LastScanEventType.Present.ScanEventType.EqFinished
            @IsOptionalProcessSkippableDecisions.Conditions.FolderChangedSinceLastScan.True
            @IsOptionalProcessSkippableDecisions.Result.False
            void c04() {
                settingsFacade = SettingsFacadeBuilder
                    .create()
                    .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, false)
                    .build();
                Mockito
                    .when(staticsDao.getLastScanAllStatuses())
                    .thenReturn(Arrays
                        .asList(new ScanEvent(null, null, ScanEventType.SUCCESS, null, null, null,
                                null, null)));
                Mockito.when(staticsDao.isfolderChangedSinceLastScan()).thenReturn(true);
                assertFalse(mediaScannerService.isOptionalProcessSkippable());
            }

            @Test
            @IsOptionalProcessSkippableDecisions.Conditions.IgnoreFileTimestamps.False
            @IsOptionalProcessSkippableDecisions.Conditions.LastScanEventType.Present.ScanEventType.EqFinished
            @IsOptionalProcessSkippableDecisions.Conditions.FolderChangedSinceLastScan.False
            @IsOptionalProcessSkippableDecisions.Result.True
            void c05() {
                settingsFacade = SettingsFacadeBuilder
                    .create()
                    .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, false)
                    .build();
                Mockito
                    .when(staticsDao.getLastScanAllStatuses())
                    .thenReturn(Arrays
                        .asList(new ScanEvent(null, null, ScanEventType.SUCCESS, null, null, null,
                                null, null)));
                Mockito.when(staticsDao.isfolderChangedSinceLastScan()).thenReturn(false);
                assertTrue(mediaScannerService.isOptionalProcessSkippable());
            }
        }
    }

    @Nested
    @SpringBootTest
    @NeedsDB
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
    class IntegrationTest {

        private final MetricRegistry metrics = new MetricRegistry();

        @Autowired
        private MusicFolderDao musicFolderDao;
        @Autowired
        private DaoHelper daoHelper;
        @Autowired
        private SettingsFacade settingsFacade;
        @Autowired
        private MusicFolderServiceImpl musicFolderService;
        @Autowired
        private MediaFileService mediaFileService;
        @Autowired
        private MediaFileDao mediaFileDao;
        @Autowired
        private ArtistDao artistDao;
        @Autowired
        private AlbumDao albumDao;
        @Autowired
        private ScannerStateServiceImpl scannerStateService;
        @Autowired
        private ScanHelper scanHelper;
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
        private StaticsDao staticsDao;

        private MediaScannerService mediaScannerService;

        @BeforeEach
        void setup() {
            ThreadPoolTaskExecutor scanExecutor = ServiceMockUtils.mockNoAsyncTaskExecutor();
            mediaScannerService = new MediaScannerServiceImpl(settingsFacade, scannerStateService,
                    preScanProc, directoryScanProc, fileMetaProc, id3MetaProc, postScanProc,
                    scanHelper, staticsDao, scanExecutor);
        }

        /**
         * Tests the MediaScannerService by scanning the test media library into an
         * empty database.
         */
        @Test
        void testScanLibrary() {

            // Test case ported from a legacy server.

            musicFolderDao
                .getAllMusicFolders()
                .forEach(musicFolder -> musicFolderDao.deleteMusicFolder(musicFolder.getId()));
            MusicFolderTestDataUtils
                .getTestMusicFolders()
                .forEach(musicFolderDao::createMusicFolder);
            musicFolderService.clearMusicFolderCache();

            Timer globalTimer = metrics
                .timer(MetricRegistry.name(MediaScannerServiceImplTest.class, "Timer.global"));

            try (Timer.Context globalTimerContext = globalTimer.time()) {
                TestCaseUtils.execScan(mediaScannerService);
                globalTimerContext.stop();
            }
            assertEquals(ScanEventType.SUCCESS, mediaScannerService.getLastScanEventType().get());

            logRecords(TestCaseUtils.recordsInAllTables(daoHelper));

            // Music Folder Music must have 3 children
            List<MediaFile> listeMusicChildren = mediaFileDao
                .getChildrenOf(
                        Path.of(MusicFolderTestDataUtils.resolveMusicFolderPath()).toString());
            assertEquals(3, listeMusicChildren.size());
            // Music Folder Music2 must have 1 children
            List<MediaFile> listeMusic2Children = mediaFileDao
                .getChildrenOf(
                        Path.of(MusicFolderTestDataUtils.resolveMusic2FolderPath()).toString());
            assertEquals(1, listeMusic2Children.size());

            logArtistsAll();

            if (LOG.isInfoEnabled()) {
                LOG.info("--- *********************** ---");
                LOG.info("--- List of all albums ---");
                LOG.info("name#artist");
            }
            List<Album> allAlbums = albumDao
                .getAlphabeticalAlbums(0, Integer.MAX_VALUE, true, true,
                        musicFolderDao.getAllMusicFolders());
            allAlbums.forEach(album -> {
                if (LOG.isInfoEnabled()) {
                    LOG.info(album.getName() + "#" + album.getArtist());
                }
            });
            assertEquals(5, allAlbums.size());

            if (LOG.isInfoEnabled()) {
                LOG.info("--- *********************** ---");
            }

            List<MediaFile> listeSongs = mediaFileDao
                .getSongsByGenre(Arrays.asList("Baroque Instrumental"), 0, 0,
                        musicFolderDao.getAllMusicFolders(), Arrays.asList(MediaType.MUSIC));
            assertEquals(2, listeSongs.size());

            // display out metrics report
            try (ConsoleReporter reporter = ConsoleReporter
                .forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build()) {
                reporter.report();
            }

            if (LOG.isInfoEnabled()) {
                LOG.info("End");
            }

            // Add below with Jpsonic. Whether MediaFile#folder is registered correctly.
            Map<String, MusicFolder> folders = musicFolderDao
                .getAllMusicFolders()
                .stream()
                .collect(Collectors.toMap(MusicFolder::getName, m -> m));
            Map<String, MediaFile> albums = mediaFileDao
                .getAlphabeticalAlbums(0, Integer.MAX_VALUE, false,
                        musicFolderDao.getAllMusicFolders())
                .stream()
                .collect(Collectors.toMap(MediaFile::getName, m -> m));
            assertEquals(5, albums.size());
            assertEquals(folders.get("Music").getPathString(), albums
                .get("_DIR_ Céline Frisch- Café Zimmermann - Bach- Goldberg Variations, Canons [Disc 1]")
                .getFolder());
            assertEquals(folders.get("Music").getPathString(),
                    albums.get("_DIR_ Ravel - Chamber Music With Voice").getFolder());
            assertEquals(folders.get("Music").getPathString(),
                    albums.get("_DIR_ Ravel - Complete Piano Works").getFolder());
            assertEquals(folders.get("Music").getPathString(),
                    albums.get("_DIR_ Sackcloth 'n' Ashes").getFolder());
            assertEquals(folders.get("Music2").getPathString(),
                    albums.get("_DIR_ chrome hoof - 2004").getFolder());
        }

        private void logRecords(Map<String, Integer> records) {
            if (LOG.isInfoEnabled()) {
                LOG.info("--- Report of records count per table ---");
            }
            records.keySet().forEach(tableName -> {
                if (LOG.isInfoEnabled()) {
                    LOG.info(tableName + " : " + records.get(tableName).toString());
                }
            });
            if (LOG.isInfoEnabled()) {
                LOG.info("--- *********************** ---");
            }
        }

        private void logArtistsAll() {
            if (LOG.isInfoEnabled()) {
                LOG.info("--- List of all artists ---");
                LOG.info("artistName#albumCount");
            }
            List<Artist> allArtists = artistDao
                .getAlphabetialArtists(0, Integer.MAX_VALUE, musicFolderDao.getAllMusicFolders());
            allArtists.forEach(artist -> {
                if (LOG.isInfoEnabled()) {
                    LOG.info(artist.getName() + "#" + artist.getAlbumCount());
                }
            });
        }

        @Test
        void testSpecialCharactersInFilename(@TempDir Path tempDirPath) throws Exception {

            Path musicPath;
            try (InputStream resource = Thread
                .currentThread()
                .getContextClassLoader()
                .getResourceAsStream("MEDIAS/piano.mp3")) {
                assertNotNull(resource);
                String directoryName = "Muff1nman\u2019s \uFF0FMusic"; // Muff1nman’s ／Music
                String fileName = "Muff1nman\u2019s\uFF0FPiano.mp3"; // Muff1nman’s／Piano.mp3

                Path artistDir = Path.of(tempDirPath.toString(), directoryName);
                FileOperations.createDirectories(artistDir);

                musicPath = Path.of(artistDir.toString(), fileName);
                IOUtils.copy(resource, Files.newOutputStream(musicPath));
            }

            MusicFolder musicFolder = new MusicFolder(1, tempDirPath.toString(), "Music", true,
                    now(), 1, false);
            musicFolderDao.createMusicFolder(musicFolder);
            musicFolderService.clearMusicFolderCache();
            TestCaseUtils.execScan(mediaScannerService);
            MediaFile mediaFile = mediaFileService.getMediaFile(musicPath);
            assertEquals(mediaFile.toPath(), musicPath);
            assertNotNull(mediaFile);
        }

        @Test
        void testNeverScanned() {
            assertFalse(mediaScannerService.neverScanned());
        }

        @Test
        void testMusicBrainzReleaseIdTag() {

            // Add the "Music3" folder to the database
            Path musicFolderPath = Path.of(MusicFolderTestDataUtils.resolveMusic3FolderPath());
            MusicFolder musicFolder = new MusicFolder(1, musicFolderPath.toString(), "Music3", true,
                    now(), 1, false);
            musicFolderDao.createMusicFolder(musicFolder);
            musicFolderService.clearMusicFolderCache();
            TestCaseUtils.execScan(mediaScannerService);

            // Retrieve the "Music3" folder from the database to make
            // sure that we don't accidentally operate on other folders
            // from previous tests.
            musicFolder = musicFolderDao.getMusicFolderForPath(musicFolder.getPathString());
            List<MusicFolder> folders = new ArrayList<>();
            folders.add(musicFolder);

            // Test that the artist is correctly imported
            List<Artist> allArtists = artistDao
                .getAlphabetialArtists(0, Integer.MAX_VALUE, folders);
            assertEquals(1, allArtists.size());
            Artist artist = allArtists.get(0);
            assertEquals("TestArtist", artist.getName());
            assertEquals(1, artist.getAlbumCount());

            // Test that the album is correctly imported, along with its MusicBrainz release
            // ID
            List<Album> allAlbums = albumDao
                .getAlphabeticalAlbums(0, Integer.MAX_VALUE, true, true, folders);
            assertEquals(1, allAlbums.size());
            Album album = allAlbums.get(0);
            assertEquals("TestAlbum", album.getName());
            assertEquals("TestArtist", album.getArtist());
            assertEquals(1, album.getSongCount());
            assertEquals("0820752d-1043-4572-ab36-2df3b5cc15fa", album.getMusicBrainzReleaseId());
            assertEquals(musicFolderPath.resolve("TestAlbum").toString(), album.getPath());

            // Test that the music file is correctly imported, along with its MusicBrainz
            // release ID and recording ID
            List<MediaFile> albumFiles = mediaFileDao.getChildrenOf(allAlbums.get(0).getPath());
            assertEquals(1, albumFiles.size());
            MediaFile file = albumFiles.get(0);
            assertEquals("Aria", file.getTitle());
            assertEquals("flac", file.getFormat());
            assertEquals("TestAlbum", file.getAlbumName());
            assertEquals("TestArtist", file.getArtist());
            assertEquals("TestArtist", file.getAlbumArtist());
            assertEquals(1, (long) file.getTrackNumber());
            assertEquals(2001, (long) file.getYear());
            assertEquals(album.getPath(), file.getParentPathString());
            assertEquals(Path.of(album.getPath()).resolve("01 - Aria.flac"), file.toPath());
            assertEquals("0820752d-1043-4572-ab36-2df3b5cc15fa", file.getMusicBrainzReleaseId());
            assertEquals("831586f4-56f9-4785-ac91-447ae20af633", file.getMusicBrainzRecordingId());
        }
    }

    @Nested
    class StrictSortTest {

        private SettingsFacade settingsFacade;
        private ArtistDao artistDao;
        private MediaFileDao mediaFileDao;
        private ScannerStateServiceImpl scannerStateService;
        private SortProcedureService sortProcedureService;
        private MusicFolderServiceImpl musicFolderService;

        private MediaScannerServiceImpl mediaScannerService;
        private JpsonicComparators comparators;

        @BeforeEach
        void setup() {
            settingsFacade = SettingsFacadeBuilder.create().build();
            init();
        }

        @Ignore
        void init() {
            mediaFileDao = mock(MediaFileDao.class);
            artistDao = mock(ArtistDao.class);
            sortProcedureService = mock(SortProcedureService.class);
            scannerStateService = mock(ScannerStateServiceImpl.class);
            final MediaFileService mediaFileService = mock(MediaFileService.class);
            final AlbumDao albumDao = mock(AlbumDao.class);
            JapaneseReadingUtils readingUtils = new JapaneseReadingUtils(settingsFacade);
            MetadataReadingProcessor readingProcessor = new MetadataReadingProcessor(settingsFacade,
                    readingUtils);
            final WritableMediaFileService writableMediaFileService = new WritableMediaFileService(
                    mediaFileDao, scannerStateService, mediaFileService, albumDao,
                    mock(MediaFileCache.class), mock(MusicParser.class), mock(VideoParser.class),
                    settingsFacade, mock(LibraryAccessPolicy.class),
                    new ScanningExclusionPolicy(settingsFacade), readingProcessor,
                    mock(Indexer.class), mock(MusicIndexProviderImpl.class));
            musicFolderService = mock(MusicFolderServiceImpl.class);
            comparators = mock(JpsonicComparators.class);
            final StaticsDao staticsDao = mock(StaticsDao.class);
            final IndexManager indexManager = mock(IndexManager.class);
            final Indexer indexer = mock(Indexer.class);
            final ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);

            final MusicFolderServiceImpl musicFolderService = mock(MusicFolderServiceImpl.class);
            final PlaylistService playlistService = mock(PlaylistService.class);
            final TemplateWrapper templateWrapper = mock(TemplateWrapper.class);
            final MusicIndexProviderImpl musicIndexProviderImpl = mock(
                    MusicIndexProviderImpl.class);
            final MediaFileCache mediaFileCache = mock(MediaFileCache.class);
            final MetadataReadingProcessor proc = mock(MetadataReadingProcessor.class);

            ScanHelper scanHelper = mock(ScanHelper.class);
            PreScanProcedure preScanProc = new PreScanProcedure(musicFolderService, indexManager,
                    mediaFileDao, artistDao, mediaFileCache, scanHelper);
            DirectoryScanProcedure directoryScanProc = new DirectoryScanProcedure(mediaFileDao,
                    musicFolderService, writableMediaFileService, scannerStateService, indexer,
                    scanHelper);
            FileMetadataScanProcedure fileMetaProc = new FileMetadataScanProcedure(
                    musicFolderService, indexer, mediaFileService, writableMediaFileService,
                    mediaFileDao, sortProcedureService, scannerStateService, scanHelper,
                    musicIndexProviderImpl, proc, comparators);
            GenreMasterProviderAdapter genreMasterProviderAdapter = mock(
                    GenreMasterProviderAdapter.class);
            Id3MetadataScanProcedure id3MetaProc = new Id3MetadataScanProcedure(musicFolderService,
                    indexer, genreMasterProviderAdapter, mediaFileService, mediaFileDao, artistDao,
                    albumDao, musicIndexProviderImpl, comparators, scanHelper);
            PostScanProcedure postScanProc = new PostScanProcedure(musicFolderService, indexManager,
                    playlistService, templateWrapper, staticsDao, sortProcedureService,
                    mediaFileCache, scanHelper, mock(ApplicationEventPublisher.class));
            mediaScannerService = new MediaScannerServiceImpl(settingsFacade, scannerStateService,
                    preScanProc, directoryScanProc, fileMetaProc, id3MetaProc, postScanProc,
                    scanHelper, staticsDao, executor);
        }

        @Test
        void testDoScanLibraryWithSortStrict() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(SKeys.advanced.sort.strict, true)
                .build();
            init();
            Mockito
                .when(musicFolderService.getAllMusicFolders())
                .thenReturn(
                        Arrays.asList(new MusicFolder(0, "path", "name", true, null, -1, false)));
            Mockito.when(artistDao.getAlbumCounts()).thenReturn(Arrays.asList(new Artist()));

            Mockito.when(scannerStateService.isEnableCleansing()).thenReturn(true);
            Mockito.when(scannerStateService.tryScanningLock()).thenReturn(true);

            mediaScannerService.doScanLibrary();

            // parseAlbum
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .getChangedAlbums(Mockito.anyInt(), Mockito.anyList());
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .getUnparsedAlbums(Mockito.anyInt(), Mockito.anyList());

            // updateSortOfAlbum
            Mockito
                .verify(sortProcedureService, Mockito.times(1))
                .mergeSortOfAlbum(Mockito.anyList());
            Mockito
                .verify(sortProcedureService, Mockito.times(1))
                .copySortOfAlbum(Mockito.anyList());
            Mockito
                .verify(sortProcedureService, Mockito.times(1))
                .compensateSortOfAlbum(Mockito.anyList());

            // (updateOrderOfAlbum/updateSortOfArtist)
            Mockito.verify(comparators, Mockito.times(2)).mediaFileOrderByAlpha();
            Mockito.verify(comparators, Mockito.never()).mediaFileOrderBy(OrderBy.ALBUM);
            Mockito.verify(comparators, Mockito.never()).mediaFileOrderBy(OrderBy.ARTIST);

            Mockito
                .verify(sortProcedureService, Mockito.times(1))
                .mergeSortOfArtist(Mockito.anyList());
            Mockito
                .verify(sortProcedureService, Mockito.times(1))
                .copySortOfArtist(Mockito.anyList());
            Mockito
                .verify(sortProcedureService, Mockito.times(1))
                .compensateSortOfArtist(Mockito.anyList());

            // refleshAlbumId3
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .getChangedId3Albums(Mockito.anyInt(), Mockito.anyList(), Mockito.anyBoolean());
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .getUnregisteredId3Albums(Mockito.anyInt(), Mockito.anyList(),
                        Mockito.anyBoolean());

            // (updateOrderOfAlbumId3)
            // Mockito.verify(scannerProcedureService,
            // Mockito.times(1)).updateOrderOfAlbumID3();
            Mockito.verify(comparators, Mockito.times(1)).albumOrderByAlpha();

            // refleshArtistId3
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .getChangedId3Artists(Mockito.anyInt(), Mockito.anyList(), Mockito.anyBoolean());
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .getUnregisteredId3Artists(Mockito.anyInt(), Mockito.anyList(),
                        Mockito.anyBoolean());

            // (updateOrderOfArtistId3)
            // Mockito.verify(scannerProcedureService,
            // Mockito.times(1)).updateOrderOfArtistID3();
            Mockito.verify(comparators, Mockito.times(1)).artistOrderByAlpha();

            // updateAlbumCounts
            Mockito
                .verify(artistDao, Mockito.times(1))
                .updateAlbumCount(Mockito.anyInt(), Mockito.anyInt());
        }

        /**
         * From v112.0.0 onwards, updateOrder will be a mandatory process when data is
         * updated. Because it is used for fetch in incremental updates.
         */
        @Test
        void testDoScanLibraryWithoutSortStrict() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(SKeys.advanced.sort.strict, false)
                .build();

            Mockito
                .when(musicFolderService.getAllMusicFolders())
                .thenReturn(
                        Arrays.asList(new MusicFolder(0, "path", "name", true, null, -1, false)));
            Mockito.when(artistDao.getAlbumCounts()).thenReturn(Arrays.asList(new Artist()));

            Mockito.when(scannerStateService.isEnableCleansing()).thenReturn(true);
            Mockito.when(scannerStateService.tryScanningLock()).thenReturn(true);

            mediaScannerService.doScanLibrary();

            // parseAlbum
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .getChangedAlbums(Mockito.anyInt(), Mockito.anyList());
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .getUnparsedAlbums(Mockito.anyInt(), Mockito.anyList());

            // updateSortOfAlbum
            Mockito
                .verify(sortProcedureService, Mockito.never())
                .mergeSortOfAlbum(Mockito.anyList());
            Mockito
                .verify(sortProcedureService, Mockito.never())
                .copySortOfAlbum(Mockito.anyList());
            Mockito
                .verify(sortProcedureService, Mockito.never())
                .compensateSortOfAlbum(Mockito.anyList());

            // updateSortOfArtist
            Mockito
                .verify(sortProcedureService, Mockito.never())
                .mergeSortOfArtist(Mockito.anyList());
            Mockito
                .verify(sortProcedureService, Mockito.never())
                .copySortOfArtist(Mockito.anyList());
            Mockito
                .verify(sortProcedureService, Mockito.never())
                .compensateSortOfArtist(Mockito.anyList());

            // (updateOrderOfAlbum/updateOrderOfArtist)
            Mockito.verify(comparators, Mockito.times(2)).mediaFileOrderByAlpha();
            Mockito.verify(comparators, Mockito.never()).mediaFileOrderBy(OrderBy.ALBUM);
            Mockito.verify(comparators, Mockito.never()).mediaFileOrderBy(OrderBy.ARTIST);

            // refleshAlbumId3
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .getChangedId3Albums(Mockito.anyInt(), Mockito.anyList(), Mockito.anyBoolean());
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .getUnregisteredId3Albums(Mockito.anyInt(), Mockito.anyList(),
                        Mockito.anyBoolean());

            // (updateOrderOfAlbumId3)
            // Mockito.verify(scannerProcedureService,
            // Mockito.times(1)).updateOrderOfAlbumID3();
            Mockito.verify(comparators, Mockito.times(1)).albumOrderByAlpha();

            // refleshArtistId3
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .getChangedId3Artists(Mockito.anyInt(), Mockito.anyList(), Mockito.anyBoolean());
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .getUnregisteredId3Artists(Mockito.anyInt(), Mockito.anyList(),
                        Mockito.anyBoolean());

            // (updateOrderOfArtistId3)
            // Mockito.verify(scannerProcedureService,
            // Mockito.times(1)).updateOrderOfArtistID3();
            Mockito.verify(comparators, Mockito.times(1)).artistOrderByAlpha();

            // updateAlbumCounts
            Mockito
                .verify(artistDao, Mockito.times(1))
                .updateAlbumCount(Mockito.anyInt(), Mockito.anyInt());
        }
    }

    @Nested
    class GetScanPhaseInfoTest {
        private SettingsFacade settingsFacade;
        private ScanHelper scanHelper;
        private PreScanProcedure preScanProc;
        private DirectoryScanProcedure directoryScanProc;
        private FileMetadataScanProcedure fileMetaProc;
        private Id3MetadataScanProcedure id3MetaProc;
        private PostScanProcedure postScanProc;
        private StaticsDao staticsDao;
        private ThreadPoolTaskExecutor executor;

        @BeforeEach
        void setup() {
            settingsFacade = SettingsFacadeBuilder.create().build();
            init();
        }

        @Ignore
        void init() {
            final MediaFileDao mediaFileDao = mock(MediaFileDao.class);
            final ArtistDao artistDao = mock(ArtistDao.class);
            final SortProcedureService sortProcedureService = mock(SortProcedureService.class);
            final ScannerStateServiceImpl scannerStateService = mock(ScannerStateServiceImpl.class);
            final MediaFileService mediaFileService = mock(MediaFileService.class);
            final AlbumDao albumDao = mock(AlbumDao.class);
            JapaneseReadingUtils readingUtils = new JapaneseReadingUtils(settingsFacade);
            MetadataReadingProcessor readingProcessor = new MetadataReadingProcessor(settingsFacade,
                    readingUtils);
            final WritableMediaFileService writableMediaFileService = new WritableMediaFileService(
                    mediaFileDao, scannerStateService, mediaFileService, albumDao,
                    mock(MediaFileCache.class), mock(MusicParser.class), mock(VideoParser.class),
                    settingsFacade, mock(LibraryAccessPolicy.class),
                    new ScanningExclusionPolicy(settingsFacade), readingProcessor,
                    mock(Indexer.class), mock(MusicIndexProviderImpl.class));
            final MusicFolderServiceImpl musicFolderService = mock(MusicFolderServiceImpl.class);
            final JpsonicComparators comparators = mock(JpsonicComparators.class);
            staticsDao = mock(StaticsDao.class);
            final IndexManager indexManager = mock(IndexManager.class);
            final Indexer indexer = mock(Indexer.class);
            executor = mock(ThreadPoolTaskExecutor.class);

            final PlaylistService playlistService = mock(PlaylistService.class);
            final TemplateWrapper templateWrapper = mock(TemplateWrapper.class);
            final MusicIndexProviderImpl musicIndexProviderImpl = mock(
                    MusicIndexProviderImpl.class);
            final MediaFileCache mediaFileCache = mock(MediaFileCache.class);
            final MetadataReadingProcessor proc = mock(MetadataReadingProcessor.class);

            scanHelper = mock(ScanHelper.class);
            preScanProc = new PreScanProcedure(musicFolderService, indexManager, mediaFileDao,
                    artistDao, mediaFileCache, scanHelper);
            directoryScanProc = new DirectoryScanProcedure(mediaFileDao, musicFolderService,
                    writableMediaFileService, scannerStateService, indexer, scanHelper);
            fileMetaProc = new FileMetadataScanProcedure(musicFolderService, indexer,
                    mediaFileService, writableMediaFileService, mediaFileDao, sortProcedureService,
                    scannerStateService, scanHelper, musicIndexProviderImpl, proc, comparators);
            GenreMasterProviderAdapter genreMasterProviderAdapter = mock(
                    GenreMasterProviderAdapter.class);
            id3MetaProc = new Id3MetadataScanProcedure(musicFolderService, indexer,
                    genreMasterProviderAdapter, mediaFileService, mediaFileDao, artistDao, albumDao,
                    musicIndexProviderImpl, comparators, scanHelper);
            postScanProc = new PostScanProcedure(musicFolderService, indexManager, playlistService,
                    templateWrapper, staticsDao, sortProcedureService, mediaFileCache, scanHelper,
                    mock(ApplicationEventPublisher.class));
        }

        @Test
        void testGetScanPhaseInfo() {
            ScannerStateServiceImpl scannerStateService = mock(ScannerStateServiceImpl.class);
            MediaScannerServiceImpl mediaScannerService = new MediaScannerServiceImpl(
                    settingsFacade, scannerStateService, preScanProc, directoryScanProc,
                    fileMetaProc, id3MetaProc, postScanProc, scanHelper, staticsDao, executor);

            Mockito.when(scannerStateService.isScanning()).thenReturn(false);
            Mockito.when(scannerStateService.getLastEvent()).thenReturn(ScanEventType.UNKNOWN);
            assertFalse(mediaScannerService.getScanPhaseInfo().isPresent());

            Mockito.when(scannerStateService.isScanning()).thenReturn(true);
            Mockito
                .when(scannerStateService.getLastEvent())
                .thenReturn(ScanEventType.MUSIC_FOLDER_CHECK);
            Mockito
                .when(staticsDao.getScanEvents(Mockito.nullable(Instant.class)))
                .thenReturn(Collections.emptyList());
            assertTrue(mediaScannerService.getScanPhaseInfo().isPresent());
            mediaScannerService.getScanPhaseInfo().ifPresent(scanPhaseInfo -> {
                assertEquals(2, scanPhaseInfo.phase());
                assertEquals(22, scanPhaseInfo.phaseMax());
                assertEquals("PARSE_FILE_STRUCTURE", scanPhaseInfo.phaseName());
                assertEquals(0, scanPhaseInfo.thread());
            });

            Mockito.when(scannerStateService.isScanning()).thenReturn(true);
            Mockito
                .when(scannerStateService.getLastEvent())
                .thenReturn(ScanEventType.SCANNED_COUNT);
            Mockito
                .when(staticsDao.getScanEvents(Mockito.nullable(Instant.class)))
                .thenReturn(Collections.emptyList());
            assertTrue(mediaScannerService.getScanPhaseInfo().isPresent());
            mediaScannerService.getScanPhaseInfo().ifPresent(scanPhaseInfo -> {
                assertEquals(2, scanPhaseInfo.phase());
                assertEquals(22, scanPhaseInfo.phaseMax());
                assertEquals("PARSE_FILE_STRUCTURE", scanPhaseInfo.phaseName());
                assertEquals(0, scanPhaseInfo.thread());
            });

            Mockito.when(scannerStateService.getLastEvent()).thenReturn(ScanEventType.CHECKPOINT);
            Mockito
                .when(staticsDao.getScanEvents(Mockito.nullable(Instant.class)))
                .thenReturn(Collections.emptyList());
            assertTrue(mediaScannerService.getScanPhaseInfo().isPresent());
            mediaScannerService.getScanPhaseInfo().ifPresent(scanPhaseInfo -> {
                assertEquals(21, scanPhaseInfo.phase());
                assertEquals(22, scanPhaseInfo.phaseMax());
                assertEquals("AFTER_SCAN", scanPhaseInfo.phaseName());
                assertEquals(0, scanPhaseInfo.thread());
            });

            Mockito.when(scannerStateService.getLastEvent()).thenReturn(ScanEventType.AFTER_SCAN);
            Mockito
                .when(staticsDao.getScanEvents(Mockito.nullable(Instant.class)))
                .thenReturn(Collections.emptyList());
            assertTrue(mediaScannerService.getScanPhaseInfo().isPresent());
            mediaScannerService.getScanPhaseInfo().ifPresent(scanPhaseInfo -> {
                assertEquals(21, scanPhaseInfo.phase());
                assertEquals(22, scanPhaseInfo.phaseMax());
                assertEquals("AFTER_SCAN", scanPhaseInfo.phaseName());
                assertEquals(0, scanPhaseInfo.thread());
            });

            Mockito.when(scannerStateService.getLastEvent()).thenReturn(ScanEventType.AFTER_SCAN);
            Mockito
                .when(staticsDao.getScanEvents(Mockito.nullable(Instant.class)))
                .thenReturn(Collections.emptyList());
            assertTrue(mediaScannerService.getScanPhaseInfo().isPresent());
            mediaScannerService.getScanPhaseInfo().ifPresent(scanPhaseInfo -> {
                assertEquals(21, scanPhaseInfo.phase());
                assertEquals(22, scanPhaseInfo.phaseMax());
                assertEquals("AFTER_SCAN", scanPhaseInfo.phaseName());
                assertEquals(0, scanPhaseInfo.thread());
            });

            Mockito.when(scannerStateService.getLastEvent()).thenReturn(ScanEventType.CANCELED);
            Mockito
                .when(staticsDao.getScanEvents(Mockito.nullable(Instant.class)))
                .thenReturn(Collections.emptyList());
            assertTrue(mediaScannerService.getScanPhaseInfo().isPresent());
            mediaScannerService.getScanPhaseInfo().ifPresent(scanPhaseInfo -> {
                assertEquals(-1, scanPhaseInfo.phase());
                assertEquals(-1, scanPhaseInfo.phaseMax());
                assertEquals("Semi Scan Proc", scanPhaseInfo.phaseName());
                assertEquals(-1, scanPhaseInfo.thread());
            });
        }
    }
}

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

import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.Artist;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.AlbumDao;
import com.tesshu.jpsonic.persistence.api.repository.ArtistDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao.ChildOrder;
import com.tesshu.jpsonic.persistence.core.entity.ScanLog.ScanLogType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Jpsonic may supplement the sort tag internally if the sort tag is easily
 * predictable. In multi-byte languages such as Japanese, sorting tags will
 * affect the sorting orderand accuracy of voice search. In particular, systems
 * that handle big data and large-scale searches may have modern mechanism to
 * preventmissing results when necessary.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class SortProcedureServiceTest {

    /**
     * If SORT exists for one name and null-sort data exists, unify it to SORT.
     */
    @Order(2)
    @Nested
    class CompensateSortOfArtistTest extends AbstractNeedsScan {

        private final List<MusicFolder> musicFolders = Arrays
            .asList(new MusicFolder(1,
                    resolveBaseMediaPath("Sort/Cleansing/ArtistSort/Compensation"), "Duplicate",
                    true, now(), 1, false));

        @Autowired
        private MediaFileDao mediaFileDao;

        @Autowired
        private ArtistDao artistDao;

        @Autowired
        private PreScanProcedure preScanProc;
        @Autowired
        private FileMetadataScanProcedure fileMetaProc;
        @Autowired
        private Id3MetadataScanProcedure id3MetaProc;
        @Autowired
        private PostScanProcedure postScanProc;
        @Autowired
        private ScanHelper scanHelper;

        @Autowired
        private SortProcedureService sortProcedureService;

        @Autowired
        private ScannerStateServiceImpl scannerStateService;

        @Override
        public List<MusicFolder> getMusicFolders() {
            return musicFolders;
        }

        @BeforeEach
        void setup() {
            scannerStateService.enableCleansing(false);
            populateDatabase();
            scannerStateService.enableCleansing(true);
        }

        /**
         * Completely missing sort tags are compensated.
         */
        @Test
        void testCompensateSortOfArtist() throws ExecutionException {

            sortProcedureService.mergeSortOfArtist(musicFolders);
            sortProcedureService.copySortOfArtist(musicFolders);

            List<MediaFile> artists = mediaFileDao.getArtistAll(musicFolders);
            MediaFile artist = artists.get(0);
            assertEquals("ARTIST", artist.getName());
            MediaFile album = mediaFileDao
                .getChildrenOf(artist.getPathString(), 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA)
                .get(0);
            assertEquals("ALBUM1", album.getName());
            List<MediaFile> songs = mediaFileDao
                .getChildrenOf(album.getPathString(), 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA);
            assertEquals(3, songs.size());
            songs.sort((m1, m2) -> m1.toPath().compareTo(m2.toPath()));

            assertEquals("file1", songs.get(0).getName());
            assertEquals("近衛秀麿", songs.get(0).getAlbumArtist());
            assertNull(songs.get(0).getAlbumArtistSort());
            assertEquals("近衛秀麿", songs.get(0).getArtist());
            assertNull(songs.get(0).getArtistSort());

            assertEquals("file2", songs.get(1).getName());
            assertEquals("中山晋平", songs.get(1).getAlbumArtist());
            assertNull(songs.get(1).getAlbumArtistSort());
            assertEquals("中山晋平", songs.get(1).getArtist());
            assertNull(songs.get(1).getArtistSort());

            assertEquals("file3", songs.get(2).getName());
            assertEquals("ARTIST", songs.get(2).getAlbumArtist());
            assertNull(songs.get(2).getAlbumArtistSort());
            assertEquals("ARTIST", songs.get(2).getArtist());
            assertNull(songs.get(2).getArtistSort());
            assertEquals("世阿弥", songs.get(2).getComposer());
            assertNull(songs.get(2).getComposerSort());

            artist = artists.get(1);
            assertEquals("山田耕筰", artist.getName());
            album = mediaFileDao
                .getChildrenOf(artist.getPathString(), 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA)
                .get(0);
            assertEquals("ALBUM2", album.getName());
            songs = mediaFileDao
                .getChildrenOf(album.getPathString(), 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA);
            assertEquals(1, songs.size());

            assertEquals("file4", songs.get(0).getName());
            assertEquals("山田耕筰", songs.get(0).getArtist());
            assertNull(songs.get(0).getArtistSort());

            List<Artist> artistID3s = artistDao
                .getAlphabetialArtists(0, Integer.MAX_VALUE, musicFolders);
            assertEquals(4, artistID3s.size());
            artistID3s.sort((m1, m2) -> Integer.compare(m1.getOrder(), m2.getOrder()));

            assertEquals("ARTIST", artistID3s.get(0).getName());
            assertNull(artistID3s.get(0).getSort());
            assertEquals("近衛秀麿", artistID3s.get(1).getName());
            assertNull(artistID3s.get(1).getSort());
            assertEquals("中山晋平", artistID3s.get(2).getName());
            assertNull(artistID3s.get(2).getSort());
            assertEquals("山田耕筰", artistID3s.get(3).getName());
            assertNull(artistID3s.get(3).getSort());

            // Execution of Complementary Processing

            ScanContext context = new ScanContext(now(), false,
                    settingsFacade.get(SKeys.podcast.folder),
                    settingsFacade.get(SKeys.advanced.sort.strict),
                    settingsFacade.get(SKeys.advanced.scanLog.useScanLog),
                    settingsFacade.get(SKeys.advanced.scanLog.scanLogRetention),
                    SKeys.advanced.scanLog.scanLogRetention.defaultValue(),
                    settingsFacade.get(SKeys.advanced.scanLog.useScanEvents),
                    settingsFacade.get(SKeys.advanced.scanLog.measureMemory));

            scanHelper.createScanLog(context, ScanLogType.SCAN_ALL);
            preScanProc.beforeScan(context);
            fileMetaProc.updateSortOfArtist(context);
            id3MetaProc.refleshArtistId3(context);
            postScanProc.afterScan(context);

            artist = artists.get(0);
            assertEquals("ARTIST", artist.getName());
            album = mediaFileDao
                .getChildrenOf(artist.getPathString(), 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA)
                .get(0);
            assertEquals("ALBUM1", album.getName());
            songs = mediaFileDao
                .getChildrenOf(album.getPathString(), 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA);
            assertEquals(3, songs.size());
            songs.sort((m1, m2) -> m1.toPath().compareTo(m2.toPath()));

            assertEquals("file1", songs.get(0).getName());
            assertEquals("近衛秀麿", songs.get(0).getAlbumArtist());
            assertEquals("コノエヒデマロ", songs.get(0).getAlbumArtistSort());
            assertEquals("近衛秀麿", songs.get(0).getArtist());
            assertEquals("コノエヒデマロ", songs.get(0).getArtistSort());

            assertEquals("file2", songs.get(1).getName());
            assertEquals("中山晋平", songs.get(1).getAlbumArtist());
            assertEquals("ナカヤマシンペイ", songs.get(1).getAlbumArtistSort());
            assertEquals("中山晋平", songs.get(1).getArtist());
            assertEquals("ナカヤマシンペイ", songs.get(1).getArtistSort());

            assertEquals("file3", songs.get(2).getName());
            assertEquals("ARTIST", songs.get(2).getAlbumArtist());
            assertEquals("ARTIST", songs.get(2).getAlbumArtistSort());
            assertEquals("ARTIST", songs.get(2).getArtist());
            assertEquals("ARTIST", songs.get(2).getArtistSort());
            assertEquals("世阿弥", songs.get(2).getComposer());
            assertEquals("ゼアミ", songs.get(2).getComposerSort());

            artist = artists.get(1);
            assertEquals("山田耕筰", artist.getName());
            album = mediaFileDao
                .getChildrenOf(artist.getPathString(), 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA)
                .get(0);
            assertEquals("ALBUM2", album.getName());
            songs = mediaFileDao
                .getChildrenOf(album.getPathString(), 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA);
            assertEquals(1, songs.size());

            assertEquals("file4", songs.get(0).getName());
            assertEquals("山田耕筰", songs.get(0).getArtist());
            assertEquals("ヤマダコウサク", songs.get(0).getArtistSort());

            artistID3s = artistDao.getAlphabetialArtists(0, Integer.MAX_VALUE, musicFolders);
            assertEquals(4, artistID3s.size());
            artistID3s.sort((m1, m2) -> Integer.compare(m1.getOrder(), m2.getOrder()));

            assertEquals("ARTIST", artistID3s.get(0).getName());
            assertEquals("ARTIST", artistID3s.get(0).getSort());
            assertEquals("近衛秀麿", artistID3s.get(1).getName());
            assertEquals("コノエヒデマロ", artistID3s.get(1).getSort());
            assertEquals("中山晋平", artistID3s.get(2).getName());
            assertEquals("ナカヤマシンペイ", artistID3s.get(2).getSort());
            assertEquals("山田耕筰", artistID3s.get(3).getName());
            assertEquals("ヤマダコウサク", artistID3s.get(3).getSort());
        }
    }

    /**
     * If SORT exists for one name and null-sort data exists, unify it to SORT.
     */
    @Order(3)
    @Nested
    class CopySortOfArtistTest extends AbstractNeedsScan {

        private final List<MusicFolder> musicFolders = Arrays
            .asList(new MusicFolder(1, resolveBaseMediaPath("Sort/Cleansing/ArtistSort/Copy"),
                    "Duplicate", true, now(), 1, false));

        @Autowired
        private MediaFileDao mediaFileDao;

        @Autowired
        private ArtistDao artistDao;

        @Override
        public List<MusicFolder> getMusicFolders() {
            return musicFolders;
        }

        @BeforeEach
        void setup() {
            populateDatabase();
        }

        @Test
        void testCopySortOfArtist() throws ExecutionException {

            List<MediaFile> artists = mediaFileDao.getArtistAll(musicFolders);
            assertEquals(1, artists.size());
            List<MediaFile> albums = mediaFileDao
                .getChildrenOf(artists.get(0).getPathString(), 0, Integer.MAX_VALUE,
                        ChildOrder.BY_ALPHA);
            assertEquals(1, albums.size());
            MediaFile album = albums.get(0);

            List<MediaFile> files = mediaFileDao
                .getChildrenOf(album.getPathString(), 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA);
            assertEquals(2, files.size());
            for (int i = 0; i < files.size(); i++) {
                assertEquals(i + 1, files.get(i).getOrder());
            }
            files.sort((m1, m2) -> Integer.compare(m1.getOrder(), m2.getOrder()));
            assertEquals("file1", files.get(0).getName());
            assertEquals("case1", files.get(0).getArtist());
            assertEquals("artistA", files.get(0).getArtistSort()); // Copied (not in tag)
            assertEquals("file2", files.get(1).getName());
            assertEquals("case1", files.get(1).getArtist());
            assertEquals("artistA", files.get(1).getArtistSort());

            List<Artist> artistID3s = artistDao
                .getAlphabetialArtists(0, Integer.MAX_VALUE, musicFolders);
            assertEquals(1, artistID3s.size());
            assertEquals("case1", artistID3s.get(0).getName());
            assertEquals("artistA", artistID3s.get(0).getSort()); // It will definitely be
                                                                  // registered
        }
    }

    /**
     * If two or more SORTs exist in one name, unify them into one SORT. In terms of
     * person name sort tags, accuracy is conventionally in order of album artist,
     * artist, and composer. This can be assumed from the general data input
     * frequency.Also, if there is duplication among them, the newer ones should be
     * preferred over the older ones.
     */
    @Order(1)
    @Nested
    class MergeSortOfArtistTest extends AbstractNeedsScan {

        private final List<MusicFolder> musicFolders = Arrays
            .asList(new MusicFolder(1, resolveBaseMediaPath("Sort/Cleansing/ArtistSort/Merge"),
                    "Duplicate", true, now(), 1, false));

        @Autowired
        private MediaFileDao mediaFileDao;

        @Autowired
        private ArtistDao artistDao;

        @Autowired
        private AlbumDao albumDao;

        @Override
        public List<MusicFolder> getMusicFolders() {
            return musicFolders;
        }

        @BeforeEach
        void setup() {
            populateDatabaseOnlyOnce();
        }

        @Order(1)
        @Test
        void testArtistOfFileStructure() {
            List<MediaFile> artists = mediaFileDao.getArtistAll(musicFolders);
            assertEquals(3, artists.size());
            for (int i = 0; i < artists.size(); i++) {
                assertEquals(i + 1, artists.get(i).getOrder());
            }
            artists.sort((m1, m2) -> Integer.compare(m1.getOrder(), m2.getOrder()));

            MediaFile artist = artists.get(0);
            assertEquals("ARTIST", artist.getArtist());
            assertEquals("ARTIST", artist.getArtistReading());
            assertEquals("ARTIST", artist.getArtistSort());
            assertNull(artist.getAlbumArtist());
            assertNull(artist.getAlbumReading());
            assertNull(artist.getAlbumSort());
            MediaFile case10 = artists.get(1);
            assertEquals("case10", case10.getArtist());
            assertEquals("case10", case10.getArtistReading());
            // Complemented from album-artist-sort in file16.mp3 with higher priority
            assertEquals("artistT", case10.getArtistSort());
            assertNull(case10.getAlbumArtist());
            assertNull(case10.getAlbumReading());
            assertNull(case10.getAlbumSort());
            MediaFile case11 = artists.get(2);
            assertEquals("case11", case11.getArtist());
            assertEquals("case11", case11.getArtistReading());
            // Complemented from artist-sort in file18.mp3 with higher priority
            assertEquals("artistV", case11.getArtistSort());
            assertNull(case10.getAlbumArtist());
            assertNull(case10.getAlbumReading());
            assertNull(case10.getAlbumSort());
        }

        @Order(2)
        @Test
        void testAlbumOfFileStructure() {
            List<MediaFile> artists = mediaFileDao.getArtistAll(musicFolders);

            assertEquals("ARTIST", artists.get(0).getArtist());
            List<MediaFile> albums = mediaFileDao
                .getChildrenOf(artists.get(0).getPathString(), 0, Integer.MAX_VALUE,
                        ChildOrder.BY_ALPHA);
            assertEquals(9, albums.size());
            for (int i = 0; i < albums.size(); i++) {
                assertEquals(i + 1, albums.get(i).getOrder());
            }
            albums.sort((m1, m2) -> Integer.compare(m1.getOrder(), m2.getOrder()));

            assertEquals("ALBUM1", albums.get(0).getName());
            assertEquals("case01", albums.get(0).getArtist());
            assertEquals("case01", albums.get(0).getArtistReading());
            assertEquals("artistA", albums.get(0).getArtistSort());

            assertEquals("ALBUM2", albums.get(1).getName());
            assertEquals("case02", albums.get(1).getArtist());
            assertEquals("case02", albums.get(1).getArtistReading());
            assertEquals("artistD", albums.get(1).getArtistSort());

            assertEquals("ALBUM3", albums.get(2).getName());
            assertEquals("case03", albums.get(2).getArtist());
            assertEquals("case03", albums.get(2).getArtistReading());
            assertEquals("artistE", albums.get(2).getArtistSort());

            assertEquals("ALBUM4", albums.get(3).getName());
            assertEquals("ARTIST", albums.get(3).getArtist());
            assertEquals("ARTIST", albums.get(3).getArtistReading());
            assertEquals("ARTIST", albums.get(3).getArtistSort());

            assertEquals("ALBUM5", albums.get(4).getName());
            assertEquals("case05", albums.get(4).getArtist());
            assertEquals("case05", albums.get(4).getArtistReading());
            assertEquals("artistJ", albums.get(4).getArtistSort());

            assertEquals("ALBUM6", albums.get(5).getName());
            assertEquals("case06", albums.get(5).getArtist());
            assertEquals("case06", albums.get(5).getArtistReading());
            assertEquals("artistL", albums.get(5).getArtistSort());

            assertEquals("ALBUM7", albums.get(6).getName());
            assertEquals("ARTIST", albums.get(6).getArtist());
            assertEquals("ARTIST", albums.get(6).getArtistReading());
            assertEquals("ARTIST", albums.get(6).getArtistSort());

            assertEquals("ALBUM8", albums.get(7).getName());
            assertEquals("case08", albums.get(7).getArtist());
            assertEquals("case08", albums.get(7).getArtistReading());
            assertEquals("artistP", albums.get(7).getArtistSort());

            assertEquals("ALBUM9", albums.get(8).getName());
            assertEquals("case09", albums.get(8).getArtist());
            assertEquals("case09", albums.get(8).getArtistReading());
            assertEquals("artistR", albums.get(8).getArtistSort());

            assertEquals("case10", artists.get(1).getArtist());
            albums = mediaFileDao
                .getChildrenOf(artists.get(1).getPathString(), 0, Integer.MAX_VALUE,
                        ChildOrder.BY_ALPHA);
            assertEquals(1, albums.size());
            assertEquals("ALBUM10", albums.get(0).getName());
            assertEquals("case10", albums.get(0).getArtist());
            assertEquals("case10", albums.get(0).getArtistReading());
            assertEquals("artistT", albums.get(0).getArtistSort());

            assertEquals("case11", artists.get(2).getArtist());
            albums = mediaFileDao
                .getChildrenOf(artists.get(2).getPathString(), 0, Integer.MAX_VALUE,
                        ChildOrder.BY_ALPHA);
            assertEquals(1, albums.size());
            assertEquals("ALBUM11", albums.get(0).getName());
            assertEquals("case11", albums.get(0).getArtist());
            assertEquals("case11", albums.get(0).getArtistReading());
            assertEquals("artistV", albums.get(0).getArtistSort());
        }

        @Order(3)
        @Test
        void testSongOfFileStructure() {
            List<MediaFile> artists = mediaFileDao.getArtistAll(musicFolders);

            MediaFile artist = artists.get(0);
            List<MediaFile> albums = mediaFileDao
                .getChildrenOf(artist.getPathString(), 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA);
            assertEquals(9, albums.size());
            List<MediaFile> songs = albums
                .stream()
                .flatMap(al -> mediaFileDao
                    .getChildrenOf(al.getPathString(), 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA)
                    .stream())
                .collect(Collectors.toList());
            assertEquals(15, songs.size());
            // The order property is valid only in the same directory.
            songs.sort((m1, m2) -> m1.toPath().compareTo(m2.toPath()));

            assertEquals("file1", songs.get(0).getName());
            assertEquals("case01", songs.get(0).getArtist());
            assertEquals("case01", songs.get(0).getArtistReading());
            assertEquals("artistA", songs.get(0).getArtistSort());
            assertEquals("case01", songs.get(0).getAlbumArtist());
            assertEquals("case01", songs.get(0).getAlbumArtistReading());
            assertEquals("artistA", songs.get(0).getAlbumArtistSort());
            assertEquals("case01", songs.get(0).getComposer());
            assertEquals("artistA", songs.get(0).getComposerSort());

            assertEquals("file2", songs.get(1).getName());
            assertEquals("case02", songs.get(1).getArtist());
            assertEquals("case02", songs.get(1).getArtistReading());
            assertEquals("artistD", songs.get(1).getArtistSort());
            assertEquals("case02", songs.get(1).getAlbumArtist());
            assertEquals("case02", songs.get(1).getAlbumArtistReading());
            assertEquals("artistD", songs.get(1).getAlbumArtistSort());
            assertNull(songs.get(1).getComposer());
            assertNull(songs.get(1).getComposerSort());

            assertEquals("file3", songs.get(2).getName());
            assertEquals("case03", songs.get(2).getArtist());
            assertEquals("case03", songs.get(2).getArtistReading());
            assertEquals("artistE", songs.get(2).getArtistSort());
            assertEquals("case03", songs.get(2).getAlbumArtist());
            assertEquals("case03", songs.get(2).getAlbumArtistReading());
            assertEquals("artistE", songs.get(2).getAlbumArtistSort());
            assertEquals("case03", songs.get(2).getComposer());
            assertEquals("artistE", songs.get(2).getComposerSort());

            assertEquals("file4", songs.get(3).getName());
            assertEquals("ARTIST", songs.get(3).getArtist());
            assertEquals("ARTIST", songs.get(3).getArtistReading());
            assertEquals("ARTIST", songs.get(3).getArtistSort());
            assertEquals("ARTIST", songs.get(3).getAlbumArtist());
            assertEquals("ARTIST", songs.get(3).getAlbumArtistReading());
            assertEquals("ARTIST", songs.get(3).getAlbumArtistSort());
            assertEquals("case04", songs.get(3).getComposer());
            assertEquals("artistH", songs.get(3).getComposerSort());

            assertEquals("file5", songs.get(4).getName());
            assertEquals("case04", songs.get(4).getArtist());
            assertEquals("case04", songs.get(4).getArtistReading());
            assertEquals("artistH", songs.get(4).getArtistSort());
            assertEquals("case04", songs.get(4).getAlbumArtist());
            assertEquals("case04", songs.get(4).getAlbumArtistReading());
            assertEquals("artistH", songs.get(4).getAlbumArtistSort());
            assertEquals("case04", songs.get(4).getComposer());
            assertEquals("artistH", songs.get(4).getComposerSort());

            assertEquals("file6", songs.get(5).getName());
            assertEquals("case05", songs.get(5).getArtist());
            assertEquals("case05", songs.get(5).getArtistReading());
            assertEquals("artistJ", songs.get(5).getArtistSort());
            assertEquals("case05", songs.get(5).getAlbumArtist());
            assertEquals("case05", songs.get(5).getAlbumArtistReading());
            assertEquals("artistJ", songs.get(5).getAlbumArtistSort());
            assertNull(songs.get(5).getComposer());
            assertNull(songs.get(5).getComposerSort());

            assertEquals("file7", songs.get(6).getName());
            assertEquals("case05", songs.get(6).getArtist());
            assertEquals("case05", songs.get(6).getArtistReading());
            assertEquals("artistJ", songs.get(6).getArtistSort());
            assertEquals("case05", songs.get(6).getAlbumArtist());
            assertEquals("case05", songs.get(6).getAlbumArtistReading());
            assertEquals("artistJ", songs.get(6).getAlbumArtistSort());
            assertNull(songs.get(6).getComposer());
            assertNull(songs.get(6).getComposerSort());

            assertEquals("file8", songs.get(7).getName());
            assertEquals("case06", songs.get(7).getArtist());
            assertEquals("case06", songs.get(7).getArtistReading());
            assertEquals("artistL", songs.get(7).getArtistSort());
            assertEquals("case06", songs.get(7).getAlbumArtist());
            assertEquals("case06", songs.get(7).getAlbumArtistReading());
            assertEquals("artistL", songs.get(7).getAlbumArtistSort());
            assertEquals("case06", songs.get(7).getComposer());
            assertEquals("artistL", songs.get(7).getComposerSort());

            assertEquals("file9", songs.get(8).getName());
            assertEquals("case06", songs.get(8).getArtist());
            assertEquals("case06", songs.get(8).getArtistReading());
            assertEquals("artistL", songs.get(8).getArtistSort());
            assertEquals("case06", songs.get(8).getAlbumArtist());
            assertEquals("case06", songs.get(8).getAlbumArtistReading());
            assertEquals("artistL", songs.get(8).getAlbumArtistSort());
            assertNull(songs.get(8).getComposer());
            assertNull(songs.get(8).getComposerSort());

            assertEquals("file10", songs.get(9).getName());
            assertEquals("ARTIST", songs.get(9).getArtist());
            assertEquals("ARTIST", songs.get(9).getArtistReading());
            assertEquals("ARTIST", songs.get(9).getArtistSort());
            assertEquals("ARTIST", songs.get(9).getAlbumArtist());
            assertEquals("ARTIST", songs.get(9).getAlbumArtistReading());
            assertEquals("ARTIST", songs.get(9).getAlbumArtistSort());
            assertEquals("case07", songs.get(9).getComposer());
            assertEquals("artistN", songs.get(9).getComposerSort());

            assertEquals("file11", songs.get(10).getName());
            assertEquals("case07", songs.get(10).getArtist());
            assertEquals("case07", songs.get(10).getArtistReading());
            assertEquals("artistN", songs.get(10).getArtistSort());
            assertEquals("case07", songs.get(10).getAlbumArtist());
            assertEquals("case07", songs.get(10).getAlbumArtistReading());
            assertEquals("artistN", songs.get(10).getAlbumArtistSort());
            assertEquals("case07", songs.get(10).getComposer());
            assertEquals("artistN", songs.get(10).getComposerSort());

            assertEquals("file12", songs.get(11).getName());
            assertEquals("case08", songs.get(11).getArtist());
            assertEquals("case08", songs.get(11).getArtistReading());
            assertEquals("artistP", songs.get(11).getArtistSort());
            assertEquals("case08", songs.get(11).getAlbumArtist());
            assertEquals("case08", songs.get(11).getAlbumArtistReading());
            assertEquals("artistP", songs.get(11).getAlbumArtistSort());
            assertNull(songs.get(11).getComposer());
            assertNull(songs.get(11).getComposerSort());

            assertEquals("file13", songs.get(12).getName());
            assertEquals("case08", songs.get(12).getArtist());
            assertEquals("case08", songs.get(12).getArtistReading());
            assertEquals("artistP", songs.get(12).getArtistSort());
            assertEquals("case08", songs.get(12).getAlbumArtist());
            assertEquals("case08", songs.get(12).getAlbumArtistReading());
            assertEquals("artistP", songs.get(12).getAlbumArtistSort());
            assertNull(songs.get(12).getComposer());
            assertNull(songs.get(12).getComposerSort());

            assertEquals("file14", songs.get(13).getName());
            assertEquals("case09", songs.get(13).getArtist());
            assertEquals("case09", songs.get(13).getArtistReading());
            assertEquals("artistR", songs.get(13).getArtistSort());
            assertEquals("case09", songs.get(13).getAlbumArtist());
            assertEquals("case09", songs.get(13).getAlbumArtistReading());
            assertEquals("artistR", songs.get(13).getAlbumArtistSort());
            assertEquals("case09", songs.get(13).getComposer());
            assertEquals("artistR", songs.get(13).getComposerSort());

            assertEquals("file15", songs.get(14).getName());
            assertEquals("case09", songs.get(14).getArtist());
            assertEquals("case09", songs.get(14).getArtistReading());
            assertEquals("artistR", songs.get(14).getArtistSort());
            assertEquals("case09", songs.get(14).getAlbumArtist());
            assertEquals("case09", songs.get(14).getAlbumArtistReading());
            assertEquals("artistR", songs.get(14).getAlbumArtistSort());
            assertNull(songs.get(14).getComposer());
            assertNull(songs.get(14).getComposerSort());

            artist = artists.get(1);
            albums = mediaFileDao
                .getChildrenOf(artist.getPathString(), 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA);
            assertEquals(1, albums.size());
            songs = albums
                .stream()
                .flatMap(al -> mediaFileDao
                    .getChildrenOf(al.getPathString(), 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA)
                    .stream())
                .collect(Collectors.toList());
            assertEquals(1, songs.size());

            assertEquals("file16", songs.get(0).getName());
            assertEquals("case10", songs.get(0).getArtist());
            assertEquals("case10", songs.get(0).getArtistReading());
            assertEquals("artistT", songs.get(0).getArtistSort());
            assertEquals("case10", songs.get(0).getAlbumArtist());
            assertEquals("case10", songs.get(0).getAlbumArtistReading());
            assertEquals("artistT", songs.get(0).getAlbumArtistSort());
            assertNull(songs.get(0).getComposer());
            assertNull(songs.get(0).getComposerSort());

            artist = artists.get(2);
            albums = mediaFileDao
                .getChildrenOf(artist.getPathString(), 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA);
            assertEquals(1, albums.size());
            songs = albums
                .stream()
                .flatMap(al -> mediaFileDao
                    .getChildrenOf(al.getPathString(), 0, Integer.MAX_VALUE, ChildOrder.BY_ALPHA)
                    .stream())
                .collect(Collectors.toList());
            assertEquals(2, songs.size());
            for (int i = 0; i < songs.size(); i++) {
                assertEquals(i + 1, songs.get(i).getOrder());
            }
            songs.sort((m1, m2) -> Integer.compare(m1.getOrder(), m2.getOrder()));

            assertEquals("file17", songs.get(0).getName());
            assertEquals("case11", songs.get(0).getArtist());
            assertEquals("case11", songs.get(0).getArtistReading());
            assertEquals("artistV", songs.get(0).getArtistSort());
            assertEquals("case11", songs.get(0).getAlbumArtist());
            assertEquals("case11", songs.get(0).getAlbumArtistReading());
            assertEquals("artistV", songs.get(0).getAlbumArtistSort());
            assertEquals("case11", songs.get(0).getComposer());
            assertEquals("artistV", songs.get(0).getComposerSort());
        }

        @Order(4)
        @Test
        void testArtistOfId3() {
            // test/resources/MEDIAS/Sort/Cleansing/ArtistSort/Merge
            List<Artist> artistID3s = artistDao
                .getAlphabetialArtists(0, Integer.MAX_VALUE, musicFolders);
            assertEquals(12, artistID3s.size());
            for (int i = 0; i < artistID3s.size(); i++) {
                assertEquals(i + 1, artistID3s.get(i).getOrder());
            }
            artistID3s.sort((m1, m2) -> Integer.compare(m1.getOrder(), m2.getOrder()));

            assertEquals("ARTIST", artistID3s.get(0).getName());
            assertEquals("ARTIST", artistID3s.get(0).getReading());
            assertEquals("ARTIST", artistID3s.get(0).getSort());

            assertEquals("case01", artistID3s.get(1).getName());
            assertEquals("case01", artistID3s.get(1).getReading());
            assertEquals("artistA", artistID3s.get(1).getSort());

            assertEquals("case02", artistID3s.get(2).getName());
            assertEquals("case02", artistID3s.get(2).getReading());
            assertEquals("artistD", artistID3s.get(2).getSort());

            assertEquals("case03", artistID3s.get(3).getName());
            assertEquals("case03", artistID3s.get(3).getReading());
            assertEquals("artistE", artistID3s.get(3).getSort());

            assertEquals("case04", artistID3s.get(4).getName());
            assertEquals("case04", artistID3s.get(4).getReading());
            assertEquals("artistH", artistID3s.get(4).getSort());

            assertEquals("case05", artistID3s.get(5).getName());
            assertEquals("case05", artistID3s.get(5).getReading());
            assertEquals("artistJ", artistID3s.get(5).getSort());

            assertEquals("case06", artistID3s.get(6).getName());
            assertEquals("case06", artistID3s.get(6).getReading());
            assertEquals("artistL", artistID3s.get(6).getSort());

            assertEquals("case07", artistID3s.get(7).getName());
            assertEquals("case07", artistID3s.get(7).getReading());
            assertEquals("artistN", artistID3s.get(7).getSort());

            assertEquals("case08", artistID3s.get(8).getName());
            assertEquals("case08", artistID3s.get(8).getReading());
            assertEquals("artistP", artistID3s.get(8).getSort());

            assertEquals("case09", artistID3s.get(9).getName());
            assertEquals("case09", artistID3s.get(9).getReading());
            assertEquals("artistR", artistID3s.get(9).getSort());

            assertEquals("case10", artistID3s.get(10).getName());
            assertEquals("case10", artistID3s.get(10).getReading());
            assertEquals("artistT", artistID3s.get(10).getSort());

            assertEquals("case11", artistID3s.get(11).getName());
            assertEquals("case11", artistID3s.get(11).getReading());
            assertEquals("artistV", artistID3s.get(11).getSort());
        }

        @Order(5)
        @Test
        void testAlbumOfId3() {

            List<Album> albumId3s = albumDao
                .getAlphabeticalAlbums(0, Integer.MAX_VALUE, false, true, musicFolders);
            assertEquals(13, albumId3s.size());
            for (int i = 0; i < albumId3s.size(); i++) {
                assertEquals(i + 1, albumId3s.get(i).getOrder());
            }
            albumId3s.sort((m1, m2) -> Integer.compare(m1.getOrder(), m2.getOrder()));

            assertEquals("ALBUM1", albumId3s.get(0).getName());
            assertEquals("case01", albumId3s.get(0).getArtist());
            assertEquals("case01", albumId3s.get(0).getArtistReading());
            assertEquals("artistA", albumId3s.get(0).getArtistSort());

            assertEquals("ALBUM2", albumId3s.get(1).getName());
            assertEquals("case02", albumId3s.get(1).getArtist());
            assertEquals("case02", albumId3s.get(1).getArtistReading());
            assertEquals("artistD", albumId3s.get(1).getArtistSort());

            assertEquals("ALBUM3", albumId3s.get(2).getName());
            assertEquals("case03", albumId3s.get(2).getArtist());
            assertEquals("case03", albumId3s.get(2).getArtistReading());
            assertEquals("artistE", albumId3s.get(2).getArtistSort());

            // Verify the entries regardless of their order.
            assertEquals("ALBUM4", albumId3s.get(3).getName());
            assertEquals("ALBUM4", albumId3s.get(4).getName());
            if ("ARTIST".equals(albumId3s.get(3).getArtist())) {
                assertEquals("ARTIST", albumId3s.get(3).getArtistReading());
                assertEquals("ARTIST", albumId3s.get(3).getArtistSort());
                assertEquals("case04", albumId3s.get(4).getArtist());
                assertEquals("case04", albumId3s.get(4).getArtistReading());
                assertEquals("artistH", albumId3s.get(4).getArtistSort());
            } else {
                assertEquals("case04", albumId3s.get(3).getArtist());
                assertEquals("case04", albumId3s.get(3).getArtistReading());
                assertEquals("artistH", albumId3s.get(3).getArtistSort());
                assertEquals("ARTIST", albumId3s.get(4).getArtist());
                assertEquals("ARTIST", albumId3s.get(4).getArtistReading());
                assertEquals("ARTIST", albumId3s.get(4).getArtistSort());
            }

            assertEquals("ALBUM5", albumId3s.get(5).getName());
            assertEquals("case05", albumId3s.get(5).getArtist());
            assertEquals("case05", albumId3s.get(5).getArtistReading());
            assertEquals("artistJ", albumId3s.get(5).getArtistSort());

            assertEquals("ALBUM6", albumId3s.get(6).getName());
            assertEquals("case06", albumId3s.get(6).getArtist());
            assertEquals("case06", albumId3s.get(6).getArtistReading());
            assertEquals("artistL", albumId3s.get(6).getArtistSort());

            // Verify the entries regardless of their order.
            assertEquals("ALBUM7", albumId3s.get(7).getName());
            assertEquals("ALBUM7", albumId3s.get(8).getName());
            if ("ARTIST".equals(albumId3s.get(7).getArtist())) {
                assertEquals("ARTIST", albumId3s.get(7).getArtistReading());
                assertEquals("ARTIST", albumId3s.get(7).getArtistSort());
                assertEquals("case07", albumId3s.get(8).getArtist());
                assertEquals("case07", albumId3s.get(8).getArtistReading());
                assertEquals("artistN", albumId3s.get(8).getArtistSort());
            } else {
                assertEquals("case07", albumId3s.get(7).getArtist());
                assertEquals("case07", albumId3s.get(7).getArtistReading());
                assertEquals("artistN", albumId3s.get(7).getArtistSort());
                assertEquals("ARTIST", albumId3s.get(8).getArtist());
                assertEquals("ARTIST", albumId3s.get(8).getArtistReading());
                assertEquals("ARTIST", albumId3s.get(8).getArtistSort());
            }

            assertEquals("ALBUM8", albumId3s.get(9).getName());
            assertEquals("case08", albumId3s.get(9).getArtist());
            assertEquals("case08", albumId3s.get(9).getArtistReading());
            assertEquals("artistP", albumId3s.get(9).getArtistSort());

            assertEquals("ALBUM9", albumId3s.get(10).getName());
            assertEquals("case09", albumId3s.get(10).getArtist());
            assertEquals("case09", albumId3s.get(10).getArtistReading());
            assertEquals("artistR", albumId3s.get(10).getArtistSort());

            assertEquals("ALBUM10", albumId3s.get(11).getName());
            assertEquals("case10", albumId3s.get(11).getArtist());
            assertEquals("case10", albumId3s.get(11).getArtistReading());
            assertEquals("artistT", albumId3s.get(11).getArtistSort());

            assertEquals("ALBUM11", albumId3s.get(12).getName());
            assertEquals("case11", albumId3s.get(12).getArtist());
            assertEquals("case11", albumId3s.get(12).getArtistReading());
            assertEquals("artistV", albumId3s.get(12).getArtistSort());
        }
    }

    @Order(4)
    @Nested
    class UpdateSortOfAlbumTest extends AbstractNeedsScan {

        private final List<MusicFolder> musicFolders = Arrays
            .asList(new MusicFolder(1, resolveBaseMediaPath("Sort/Cleansing/AlbumSort"),
                    "Duplicate", true, now(), 1, false));
        @Autowired
        private MediaFileDao mediaFileDao;

        @Autowired
        private AlbumDao albumDao;

        @Override
        public List<MusicFolder> getMusicFolders() {
            return musicFolders;
        }

        @BeforeEach
        void setup() {
            populateDatabase();
        }

        /**
         * The test cases below are only valid when checked out and run locally. Because
         * merging is a logic that takes into account file dates, it cannot be
         * reproduced in the test on Github. This means that if the file dates are
         * exactly the same, it is difficult to decide which one to use as a reference
         * when merging. This is by design. Exactly identical file dates are rare cases.
         * Additionally, it can be circumvented by the user modifying the sort tag
         * accordingly.
         */
        @Test
        @Disabled
        void testUpdateSortOfAlbum() throws ExecutionException {

            List<MediaFile> albums = mediaFileDao
                .getAlphabeticalAlbums(0, Integer.MAX_VALUE, false, musicFolders);
            assertEquals(5, albums.size());
            List<Album> albumId3s = albumDao
                .getAlphabeticalAlbums(0, Integer.MAX_VALUE, false, false, musicFolders);
            assertEquals(5, albumId3s.size());

            assertEquals(2L, albums.stream().filter(m -> "albumA".equals(m.getAlbumSort())).count(),
                    "copied");
            assertEquals(0L, albums.stream().filter(m -> m.getAlbumSort() == null).count(),
                    "became null-safe");
            assertEquals(0L, albums.stream().filter(m -> "albumC".equals(m.getAlbumSort())).count(),
                    "merged(1)");
            assertEquals(2L, albums.stream().filter(m -> "albumD".equals(m.getAlbumSort())).count(),
                    "merged(2)");
            assertEquals(1L,
                    albums.stream().filter(m -> "ニホンゴノアルバムメイ".equals(m.getAlbumSort())).count(),
                    "compensated(1)");
            assertEquals(2L,
                    albumId3s.stream().filter(a -> "albumA".equals(a.getNameSort())).count(),
                    "copied");
            assertEquals(0L, albumId3s.stream().filter(a -> a.getNameSort() == null).count(),
                    "became null-safe");
            assertEquals(0L,
                    albumId3s.stream().filter(a -> "albumC".equals(a.getNameSort())).count(),
                    "merged(3)");
            assertEquals(2L,
                    albumId3s.stream().filter(a -> "albumD".equals(a.getNameSort())).count(),
                    "merged(4)");
            assertEquals(1L,
                    albumId3s.stream().filter(a -> "ニホンゴノアルバムメイ".equals(a.getNameSort())).count(),
                    "compensated(2)");
        }
    }
}

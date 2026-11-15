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
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.scanner;

import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.TestCaseUtils;
import com.tesshu.jpsonic.infrastructure.filesystem.FileOperations;
import com.tesshu.jpsonic.infrastructure.search.LegacySearch;
import com.tesshu.jpsonic.infrastructure.search.LegacySearchResult;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * getChildrenOf Integration Test. This method changes the records of repository
 * without scanning. Note that many properties are overwritten in real time.
 */
@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals" })
class MediaScannerServiceImplGetChildrenOfTest extends AbstractNeedsScan {

    private List<MusicFolder> musicFolders;
    private Path artist;
    private Path album;
    private Path song;

    @Autowired
    private MediaFileDao mediaFileDao;
    @Autowired
    private WritableMediaFileService writableMediaFileService;
    @Autowired
    private IndexManager indexManager;
    @Autowired
    private HttpSearchCriteriaDirector criteriaDirector;
    @Autowired
    private LegacySearch legacySearch;

    @Override
    public List<MusicFolder> getMusicFolders() {
        return musicFolders;
    }

    @BeforeEach
    void setup(@TempDir Path tempDir) throws IOException, URISyntaxException {

        // Create a musicfolder for verification
        artist = Path.of(tempDir.toString(), "ARTIST");
        assertNotNull(FileOperations.createDirectories(artist));
        this.album = Path.of(artist.toString(), "ALBUM");
        assertNotNull(FileOperations.createDirectories(album));
        this.musicFolders = Arrays
            .asList(new MusicFolder(1, tempDir.toString(), "musicFolder", true, now(), 1, false));

        // Copy the song file from the test resource. No tags are registered in this
        // file.
        Path sample = Path
            .of(MediaScannerServiceImplTest.class
                .getResource("/MEDIAS/Scan/Timestamp/ARTIST/ALBUM/sample.mp3")
                .toURI());
        this.song = Path.of(this.album.toString(), "sample.mp3");
        assertNotNull(Files.copy(sample, song));
        assertTrue(Files.exists(song));

        // Exec scan
        populateDatabase();
    }

    /*
     * On platforms with Timestamp disabled the result will be different from the
     * case. Windows for home use will work fine.
     */
    @DisabledOnOs(OS.WINDOWS) // Windows Server 2022 & JDK17
    @Test
    void testBehavioralSpecForTagReflesh()
            throws URISyntaxException, IOException, InterruptedException {

        MediaFile artist = mediaFileDao.getMediaFile(this.artist.toString());
        assertEquals(this.artist, artist.toPath());
        assertEquals("ARTIST", artist.getName());
        MediaFile album = mediaFileDao.getMediaFile(this.album.toString());
        assertEquals(this.album, album.toPath());
        assertEquals("ALBUM", album.getName());
        MediaFile song = mediaFileDao.getMediaFile(this.song.toString());
        assertEquals(this.song, song.toPath());
        assertEquals("ARTIST", song.getArtist());
        assertEquals("ALBUM", song.getAlbumName());

        // Copy the song file from the test resource. Tags are registered in this file.
        FileOperations.deleteIfExists(this.song);
        Path sampleEdited = Path
            .of(MediaScannerServiceImplTest.class
                .getResource("/MEDIAS/Scan/Timestamp/ARTIST/ALBUM/sampleEdited.mp3")
                .toURI());
        this.song = Path.of(this.album.toString(), "sample.mp3");
        Files.copy(sampleEdited, this.song);
        assertTrue(song.exists());

        /*
         * If you get it via Dao, you can get the record before had been copied. (It's a
         * expected behavior)
         */
        List<MediaFile> albums = mediaFileDao.getChildrenOf(this.artist.toString());
        assertEquals(1, albums.size());
        List<MediaFile> songs = mediaFileDao.getChildrenOf(this.album.toString());
        assertEquals(1, songs.size());
        song = songs.get(0);
        assertEquals(this.song, song.toPath());
        assertEquals("ARTIST", song.getArtist());
        assertEquals("ALBUM", song.getAlbumName());

        /*
         * Note that the name of getChildrenOf is get, but the actual process is get &
         * Update, in legacy sonic servers. Jpsonic analyzes files only when scanning.
         */
        Instant scanStart = now();
        indexManager.startIndexing();
        albums = writableMediaFileService.getChildrenOf(scanStart, artist, false);
        assertEquals(1, albums.size());
        songs = writableMediaFileService.getChildrenOf(scanStart, album, true);
        assertEquals(1, songs.size());
        indexManager.stopIndexing();

        // Artist and Album are not subject to the update process
        artist = mediaFileDao.getMediaFile(this.artist.toString());
        assertEquals(this.artist, artist.toPath());
        assertEquals("ARTIST", artist.getName());
        album = mediaFileDao.getMediaFile(this.album.toString());
        assertEquals(this.album, album.toPath());
        assertEquals("ALBUM", album.getName());

        song = mediaFileDao.getMediaFile(this.song.toString());
        assertEquals(this.song, song.toPath());
        assertEquals("Edited artist!", song.getArtist());
        assertEquals("Edited album!", song.getAlbumName());

        /*
         * Not reflected in the search at this point. (It's a expected behavior)
         */
        LegacySearchResult result = legacySearch
            .search(criteriaDirector
                .construct("Edited", 0, Integer.MAX_VALUE, false, musicFolders, IndexType.SONG));
        assertEquals(1, result.getMediaFiles().size());
        result = legacySearch
            .search(criteriaDirector
                .construct("sample", 0, Integer.MAX_VALUE, false, musicFolders, IndexType.SONG));
        assertEquals(0, result.getMediaFiles().size());
        result = legacySearch
            .search(criteriaDirector
                .construct("ALBUM", 0, Integer.MAX_VALUE, false, musicFolders, IndexType.ALBUM));
        assertEquals(1, result.getMediaFiles().size());
        result = legacySearch
            .search(criteriaDirector
                .construct("ARTIST", 0, Integer.MAX_VALUE, false, musicFolders, IndexType.ARTIST));
        assertEquals(1, result.getMediaFiles().size());

        // Exec scan
        TestCaseUtils.execScan(mediaScannerService);
        // Await for Lucene to finish writing(asynchronous).
        for (int i = 0; i < 5; i++) {
            Thread.sleep(1000);
        }

        artist = mediaFileDao.getMediaFile(this.artist.toString());
        assertEquals(this.artist, artist.toPath());
        assertNull(artist.getTitle());
        assertEquals("ARTIST", artist.getName());
        assertEquals("ARTIST", artist.getArtist());
        album = mediaFileDao.getMediaFile(this.album.toString());
        assertEquals(this.album, album.toPath());
        assertNull(album.getTitle());
        assertEquals("ALBUM", album.getName());
        assertEquals("Edited album!", album.getAlbumName());

        song = mediaFileDao.getMediaFile(this.song.toString());
        assertEquals(this.song, song.toPath());
        assertEquals("Edited song!", song.getTitle());
        assertEquals("Edited song!", song.getName());
        assertEquals("Edited artist!", song.getArtist());
        assertEquals("Edited album!", song.getAlbumName());

        result = legacySearch
            .search(criteriaDirector
                .construct("sample", 0, Integer.MAX_VALUE, false, musicFolders, IndexType.SONG));
        assertEquals(0, result.getMediaFiles().size()); // good (1 -> 0)
        result = legacySearch
            .search(criteriaDirector
                .construct("Edited song!", 0, Integer.MAX_VALUE, false, musicFolders,
                        IndexType.SONG));
        assertEquals(1, result.getMediaFiles().size()); // good (0 -> 1)

        result = legacySearch
            .search(criteriaDirector
                .construct("Edited album!", 0, Integer.MAX_VALUE, false, musicFolders,
                        IndexType.ALBUM));
        assertEquals(1, result.getMediaFiles().size()); // good (0 -> 1)

        /*
         * Not reflected in the artist of file structure. (It's a expected behavior)
         */
        result = legacySearch
            .search(criteriaDirector
                .construct("Edited artist!", 0, Integer.MAX_VALUE, false, musicFolders,
                        IndexType.ARTIST));
        assertEquals(0, result.getMediaFiles().size()); // good
        result = legacySearch
            .search(criteriaDirector
                .construct("ARTIST", 0, Integer.MAX_VALUE, false, musicFolders, IndexType.ARTIST));
        assertEquals(1, result.getMediaFiles().size()); // good (1 -> 1)

        result = legacySearch
            .search(criteriaDirector
                .construct("Edited album!", 0, Integer.MAX_VALUE, false, musicFolders,
                        IndexType.ALBUM_ID3));
        assertEquals(1, result.getAlbums().size());
        result = legacySearch
            .search(criteriaDirector
                .construct("Edited artist!", 0, Integer.MAX_VALUE, false, musicFolders,
                        IndexType.ARTIST_ID3));
        assertEquals(1, result.getArtists().size());
    }
}

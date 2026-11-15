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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.TestCaseUtils;
import com.tesshu.jpsonic.infrastructure.filesystem.FileOperations;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals" })
class MediaScannerServiceImplChangeFolderTest extends AbstractNeedsScan {

    private List<MusicFolder> musicFolders;
    private Path artist;
    private Path album;
    private Path song;

    @TempDir
    private Path tempDir1;

    @TempDir
    private Path tempDir2;

    @Autowired
    private MediaFileDao mediaFileDao;

    @Override
    public List<MusicFolder> getMusicFolders() {
        return musicFolders;
    }

    @BeforeEach
    void setup() throws IOException, URISyntaxException {
        artist = Path.of(tempDir1.toString(), "ARTIST");
        assertNotNull(FileOperations.createDirectories(artist));
        this.album = Path.of(artist.toString(), "ALBUM");
        assertNotNull(FileOperations.createDirectories(album));
        this.musicFolders = Arrays
            .asList(new MusicFolder(1, tempDir1.toString(), "musicFolder1", true, now(), 0, false),
                    new MusicFolder(2, tempDir2.toString(), "musicFolder2", true, now(), 1, false));

        Path sample = Path
            .of(MediaScannerServiceImplTest.class
                .getResource("/MEDIAS/Scan/Timestamp/ARTIST/ALBUM/sample.mp3")
                .toURI());
        this.song = Path.of(this.album.toString(), "sample.mp3");
        assertNotNull(Files.copy(sample, song));
        assertTrue(Files.exists(song));

        populateDatabase();
    }

    @Test
    void testChangeFolder() throws URISyntaxException, IOException, InterruptedException {

        MediaFile artist = mediaFileDao.getMediaFile(this.artist.toString());
        assertEquals(this.artist, artist.toPath());
        assertEquals("ARTIST", artist.getName());
        MediaFile album = mediaFileDao.getMediaFile(this.album.toString());
        assertEquals(this.album, album.toPath());
        assertEquals("ALBUM", album.getName());
        MediaFile song = mediaFileDao.getMediaFile(this.song.toString());
        assertEquals(this.song, song.toPath());

        Map<String, MusicFolder> folders = musicFolders
            .stream()
            .collect(Collectors.toMap(MusicFolder::getName, mf -> mf));
        assertEquals(song.getFolder(), folders.get("musicFolder1").getPathString());

        // Create a directory and move the files there
        Path artist2 = Path.of(tempDir2.toString(), "ARTIST2");
        assertNotNull(FileOperations.createDirectories(artist2));
        Path album2 = Path.of(artist2.toString(), "ALBUM2");
        assertNotNull(FileOperations.createDirectories(album2));
        Path movedSong = Path.of(album2.toString(), "sample.mp3");
        Files.move(this.song, movedSong);
        assertFalse(Files.exists(this.song));
        assertTrue(Files.exists(movedSong));

        // Exec scan
        TestCaseUtils.execScan(mediaScannerService);

        assertNull(mediaFileDao.getMediaFile(this.song.toString()));
        song = mediaFileDao.getMediaFile(movedSong.toString());
        assertNotNull(song);
        assertEquals(song.getFolder(), folders.get("musicFolder2").getPathString());
    }
}

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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.TestCaseUtils;
import com.tesshu.jpsonic.infrastructure.filesystem.FileOperations;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SuppressWarnings("PMD.TooManyStaticImports")
class MediaScannerServiceImplFolderEnabledTest extends AbstractNeedsScan {

    private List<MusicFolder> musicFolders;
    private Path song;

    @Autowired
    private MediaFileDao mediaFileDao;

    @Override
    public List<MusicFolder> getMusicFolders() {
        return musicFolders;
    }

    @BeforeEach
    void setup(@TempDir Path tempDir) throws IOException, URISyntaxException {
        Path artist = Path.of(tempDir.toString(), "ARTIST");
        assertNotNull(FileOperations.createDirectories(artist));
        Path album = Path.of(artist.toString(), "ALBUM");
        assertNotNull(FileOperations.createDirectories(album));
        this.musicFolders = Arrays
            .asList(new MusicFolder(1, tempDir.toString(), "musicFolder1", true, now(), 1, false));
        Path sample = Path
            .of(MediaScannerServiceImplTest.class
                .getResource("/MEDIAS/Scan/Timestamp/ARTIST/ALBUM/sample.mp3")
                .toURI());
        this.song = Path.of(album.toString(), "sample.mp3");
        assertNotNull(Files.copy(sample, song));
        assertTrue(Files.exists(song));
        populateDatabase();
    }

    /**
     * Scan after Music folder is set to enable=false and rescan with enable=true
     * before cleanup. In this case, the previous record that has already been
     * registered but not deleted is reused. In this case the previous record that
     * was already registered but not deleted (enable=false) is reused. Retains play
     * counts etc, but becomes a performance barrier.
     */
    @Test
    void testRestoreUpdate()
            throws URISyntaxException, IOException, InterruptedException, ExecutionException {

        MediaFile song = mediaFileDao.getMediaFile(this.song.toString());
        assertEquals(this.song, song.toPath());
        assertTrue(song.isPresent());

        MusicFolder folder = musicFolders.get(0);
        folder.setEnabled(false);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.initialize();
        executor.submit(() -> musicFolderService.updateMusicFolder(now(), folder)).get();

        TestCaseUtils.execScan(mediaScannerService);

        assertNull(mediaFileDao.getMediaFile(this.song.toString()));

        folder.setEnabled(true);
        executor.submit(() -> musicFolderService.updateMusicFolder(now(), folder)).get();
        TestCaseUtils.execScan(mediaScannerService);

        song = mediaFileDao.getMediaFile(this.song.toString());
        assertEquals(this.song, song.toPath());
        assertTrue(song.isPresent());

        executor.shutdown();
    }
}

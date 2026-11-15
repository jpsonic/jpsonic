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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.TestCaseUtils;
import com.tesshu.jpsonic.domain.model.Genre;
import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterCriteria;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterProvider;
import com.tesshu.jpsonic.domain.type.GenreMasterScope;
import com.tesshu.jpsonic.domain.type.GenreMasterSort;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.UncheckedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;

class MediaScannerServiceImplGenreCRUDTest extends AbstractNeedsScan {

    @Autowired
    private GenreMasterProvider genreMasterProvider;

    @TempDir
    private Path tempDir;
    private List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> folders;
    private List<MusicFolder> domainFolders;

    @Override
    public List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> getMusicFolders() {
        if (ObjectUtils.isEmpty(folders)) {
            folders = List
                .of(new com.tesshu.jpsonic.persistence.api.entity.MusicFolder(1, tempDir.toString(),
                        "MultiGenre", true, now(), 0, false));
            domainFolders = List
                .of(new MusicFolder(1, tempDir.toString(), "MultiGenre", true, now(), 0, false));
        }
        return folders;
    }

    @BeforeEach
    void setup() throws IOException {
        FileUtils.copyDirectory(new File(resolveBaseMediaPath("MultiGenre")), tempDir.toFile());
        populateDatabase();
    }

    /*
     * Used if NIO2 fails
     */
    private boolean copy(Path in, Path out) {
        try (InputStream is = Files.newInputStream(in);
                OutputStream os = Files.newOutputStream(out);) {
            byte[] buf = new byte[256];
            while (is.read(buf) != -1) {
                os.write(buf);
            }
        } catch (IOException e) {
            throw new UncheckedException(e);
        }
        return true;
    }

    @Test
    void testCRUD() throws IOException {

        // Test CR
        GenreMasterCriteria criteria = new GenreMasterCriteria(domainFolders,
                GenreMasterScope.ALBUM, GenreMasterSort.NAME);
        List<Genre> genres = genreMasterProvider.getGenres(criteria, 0, Integer.MAX_VALUE);
        assertEquals(14, genres.size());
        assertEquals("Audiobook - Historical", genres.get(0).name());
        assertEquals(1, genres.get(0).albumCount());
        assertEquals("Audiobook - Sports", genres.get(1).name());
        assertEquals(1, genres.get(1).albumCount());
        assertEquals("GENRE_A", genres.get(2).name());
        assertEquals(1, genres.get(2).albumCount());
        assertEquals("GENRE_B", genres.get(3).name());
        assertEquals(1, genres.get(3).albumCount());
        assertEquals("GENRE_C", genres.get(4).name());
        assertEquals(1, genres.get(4).albumCount());
        assertEquals("GENRE_D", genres.get(5).name());
        assertEquals(2, genres.get(5).albumCount());
        assertEquals("GENRE_E", genres.get(6).name());
        assertEquals(1, genres.get(6).albumCount());
        assertEquals("GENRE_F", genres.get(7).name());
        assertEquals(1, genres.get(7).albumCount());
        assertEquals("GENRE_G", genres.get(8).name());
        assertEquals(1, genres.get(8).albumCount());
        assertEquals("GENRE_H", genres.get(9).name());
        assertEquals(1, genres.get(9).albumCount());
        assertEquals("GENRE_I", genres.get(10).name());
        assertEquals(1, genres.get(10).albumCount());
        assertEquals("GENRE_J", genres.get(11).name());
        assertEquals(1, genres.get(11).albumCount());
        assertEquals("GENRE_K", genres.get(12).name());
        assertEquals(2, genres.get(12).albumCount());
        assertEquals("GENRE_L", genres.get(13).name());
        assertEquals(2, genres.get(13).albumCount());

        // ### Test D(File Delete)
        Files.delete(Path.of(tempDir.toString(), "ARTIST1/ALBUM1/FILE02.mp3"));
        Files.delete(Path.of(tempDir.toString(), "ARTIST1/ALBUM3/FILE05.mp3"));
        Files.delete(Path.of(tempDir.toString(), "ARTIST1/ALBUM6/FILE10.mp3"));
        TestCaseUtils.execScan(mediaScannerService);

        // Deleting a file will reduce the number of genres by 2.
        genres = genreMasterProvider.getGenres(criteria, 0, Integer.MAX_VALUE);
        assertEquals(12, genres.size());

        assertEquals("Audiobook - Historical", genres.get(0).name());
        assertEquals(1, genres.get(0).albumCount());
        assertEquals("Audiobook - Sports", genres.get(1).name());
        assertEquals(1, genres.get(1).albumCount());

        // Even if FILE02 is deleted,
        // the number of albums will not change because FILE01 still exists.
        assertEquals("GENRE_A", genres.get(2).name());
        assertEquals(1, genres.get(2).albumCount()); // (1->1)

        assertEquals("GENRE_B", genres.get(3).name());
        assertEquals(1, genres.get(3).albumCount());
        assertEquals("GENRE_C", genres.get(4).name());
        assertEquals(1, genres.get(4).albumCount());

        // FILE05 has been deleted.
        assertEquals("GENRE_D", genres.get(5).name());
        assertEquals(1, genres.get(5).albumCount()); // (2->1)

        assertEquals("GENRE_E", genres.get(6).name());
        assertEquals(1, genres.get(6).albumCount());
        assertEquals("GENRE_F", genres.get(7).name());
        assertEquals(1, genres.get(7).albumCount());
        assertEquals("GENRE_G", genres.get(8).name());
        assertEquals(1, genres.get(8).albumCount());
        assertEquals("GENRE_H", genres.get(9).name());
        assertEquals(1, genres.get(9).albumCount());

        // GENRE_I, GENRE_J has been deleted.

        assertEquals("GENRE_K", genres.get(10).name());
        assertEquals(2, genres.get(10).albumCount());
        assertEquals("GENRE_L", genres.get(11).name());
        assertEquals(2, genres.get(11).albumCount());

        // ### Test UD(Tag Update&Delete)
        Files.delete(Path.of(tempDir.toString(), "ARTIST1/ALBUM1/FILE01.mp3"));
        copy(Path.of(resolveBaseMediaPath("Scan/MultiGenreCRUD/ARTIST1/ALBUM1/FILE01.mp3")),
                Path.of(tempDir.toString(), "ARTIST1/ALBUM1/FILE01.mp3"));
        Files.delete(Path.of(tempDir.toString(), "ARTIST1/ALBUM7/FILE11.mp3"));
        copy(Path.of(resolveBaseMediaPath("Scan/MultiGenreCRUD/ARTIST1/ALBUM7/FILE11.mp3")),
                Path.of(tempDir.toString(), "ARTIST1/ALBUM7/FILE11.mp3"));
        TestCaseUtils.execScan(mediaScannerService);

        // Deleting a file will reduce the number of genres by 2.
        genres = genreMasterProvider.getGenres(criteria, 0, Integer.MAX_VALUE);
        assertEquals(13, genres.size());

        assertEquals("Audiobook - Historical", genres.get(0).name());
        assertEquals(1, genres.get(0).albumCount());
        assertEquals("Audiobook - Sports", genres.get(1).name());
        assertEquals(1, genres.get(1).albumCount());

        assertEquals("GENRE_A-CHANGED", genres.get(2).name());
        assertEquals(1, genres.get(2).albumCount());

        // GENRE_A -> GENRE_A-CHANGED

        assertEquals("GENRE_B", genres.get(3).name());
        assertEquals(1, genres.get(3).albumCount());
        assertEquals("GENRE_C", genres.get(4).name());
        assertEquals(1, genres.get(4).albumCount());
        assertEquals("GENRE_D", genres.get(5).name());
        assertEquals(1, genres.get(5).albumCount());
        assertEquals("GENRE_E", genres.get(6).name());
        assertEquals(1, genres.get(6).albumCount());
        assertEquals("GENRE_F", genres.get(7).name());
        assertEquals(1, genres.get(7).albumCount());
        assertEquals("GENRE_G", genres.get(8).name());
        assertEquals(1, genres.get(8).albumCount());
        assertEquals("GENRE_H", genres.get(9).name());
        assertEquals(1, genres.get(9).albumCount());
        assertEquals("GENRE_K", genres.get(10).name());
        assertEquals(2, genres.get(10).albumCount());

        assertEquals("GENRE_L", genres.get(11).name());
        assertEquals(1, genres.get(11).albumCount()); // (2->1)

        // Some of the multi-genres have been changed.
        assertEquals("GENRE_L-CHANGED", genres.get(12).name());
        assertEquals(1, genres.get(12).albumCount());
    }
}

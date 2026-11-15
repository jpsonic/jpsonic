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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.TestCaseUtils;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterCriteria;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterProvider;
import com.tesshu.jpsonic.domain.type.GenreMasterScope;
import com.tesshu.jpsonic.domain.type.GenreMasterSort;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class MediaScannerServiceImplGenrePersistenceTest extends AbstractNeedsScan {

    @Autowired
    private GenreMasterProvider genreMasterProvider;

    private final List<MusicFolder> folders = List
        .of(new MusicFolder(1, resolveBaseMediaPath("MultiGenre"), "MultiGenre", true, now(), 0,
                false));
    private final List<com.tesshu.jpsonic.domain.model.MusicFolder> domainFolders = List
        .of(new com.tesshu.jpsonic.domain.model.MusicFolder(1, resolveBaseMediaPath("MultiGenre"),
                "MultiGenre", true, now(), 0, false));

    @Override
    public List<MusicFolder> getMusicFolders() {
        return folders;
    }

    @BeforeEach
    void setup() throws IOException {
        populateDatabase();
    }

    /*
     * Confirm that the Genre Count does not increase or decrease during Normal-Scan
     * and Scan with IgnoreTimestamp.
     */
    @Test
    void testGenreCountPersistence() throws IOException {
        GenreMasterCriteria albumGenreCriteria = new GenreMasterCriteria(domainFolders,
                GenreMasterScope.ALBUM, GenreMasterSort.NAME);
        GenreMasterCriteria songGenreCriteria = new GenreMasterCriteria(domainFolders,
                GenreMasterScope.SONG, GenreMasterSort.NAME);
        assertTrue(assertAlbumGenreCount(
                genreMasterProvider.getGenres(albumGenreCriteria, 0, Integer.MAX_VALUE)));
        assertTrue(assertSongGenreCount(
                genreMasterProvider.getGenres(songGenreCriteria, 0, Integer.MAX_VALUE)));

        // Run a scan
        TestCaseUtils.execScan(mediaScannerService);
        assertTrue(assertAlbumGenreCount(
                genreMasterProvider.getGenres(albumGenreCriteria, 0, Integer.MAX_VALUE)));
        assertTrue(assertSongGenreCount(
                genreMasterProvider.getGenres(songGenreCriteria, 0, Integer.MAX_VALUE)));

        // Scan with IgnoreFileTimestamps enabled
        settingsFacade.commit(SKeys.musicFolder.scan.ignoreFileTimestamps, true);
        TestCaseUtils.execScan(mediaScannerService);
        assertTrue(assertAlbumGenreCount(
                genreMasterProvider.getGenres(albumGenreCriteria, 0, Integer.MAX_VALUE)));
        assertTrue(assertSongGenreCount(
                genreMasterProvider.getGenres(songGenreCriteria, 0, Integer.MAX_VALUE)));
    }

    private boolean assertAlbumGenreCount(List<com.tesshu.jpsonic.domain.model.Genre> genres) {
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
        return true;
    }

    private boolean assertSongGenreCount(List<com.tesshu.jpsonic.domain.model.Genre> genres) {
        assertEquals(15, genres.size());
        assertEquals("Audiobook - Historical", genres.get(0).name());
        assertEquals(1, genres.get(0).songCount());
        assertEquals("Audiobook - Sports", genres.get(1).name());
        assertEquals(1, genres.get(1).songCount());
        assertEquals("GENRE_A", genres.get(2).name());
        assertEquals(2, genres.get(2).songCount());
        assertEquals("GENRE_B", genres.get(3).name());
        assertEquals(1, genres.get(3).songCount());
        assertEquals("GENRE_C", genres.get(4).name());
        assertEquals(1, genres.get(4).songCount());
        assertEquals("GENRE_D", genres.get(5).name());
        assertEquals(2, genres.get(5).songCount());
        assertEquals("GENRE_E", genres.get(6).name());
        assertEquals(2, genres.get(6).songCount());
        assertEquals("GENRE_F", genres.get(7).name());
        assertEquals(2, genres.get(7).songCount());
        assertEquals("GENRE_G", genres.get(8).name());
        assertEquals(1, genres.get(8).songCount());
        assertEquals("GENRE_H", genres.get(9).name());
        assertEquals(1, genres.get(9).songCount());
        assertEquals("GENRE_I", genres.get(10).name());
        assertEquals(1, genres.get(10).songCount());
        assertEquals("GENRE_J", genres.get(11).name());
        assertEquals(1, genres.get(11).songCount());
        assertEquals("GENRE_K", genres.get(12).name());
        assertEquals(2, genres.get(12).songCount());
        assertEquals("GENRE_L", genres.get(13).name());
        assertEquals(2, genres.get(13).songCount());
        assertEquals("NO_ALBUM", genres.get(14).name());
        assertEquals(1, genres.get(14).songCount());
        return true;
    }
}

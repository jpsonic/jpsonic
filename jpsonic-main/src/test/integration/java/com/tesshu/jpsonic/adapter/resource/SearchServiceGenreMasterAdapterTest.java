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

package com.tesshu.jpsonic.adapter.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.Genre;
import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterCriteria;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterProvider;
import com.tesshu.jpsonic.domain.type.GenreMasterScope;
import com.tesshu.jpsonic.domain.type.GenreMasterSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class SearchServiceGenreMasterAdapterTest extends AbstractNeedsScan {

    @Autowired
    private GenreMasterProvider genreMasterProvider;

    private final List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = Arrays
        .asList(new com.tesshu.jpsonic.persistence.api.entity.MusicFolder(1,
                resolveBaseMediaPath("MultiGenre"), "MultiGenre", true, Instant.now(), 1, false));
    private final List<MusicFolder> domainMusicFolders = List
        .of(new MusicFolder(1, resolveBaseMediaPath("MultiGenre"), "MultiGenre", true, Instant.now(), 1,
                false));

    @Override
    public List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> getMusicFolders() {
        return musicFolders;
    }

    @BeforeEach
    void setup() {
        populateDatabaseOnlyOnce();
    }

    /*
     * @see IndexManagerTest$CreateGenreMasterTest
     */
    @Test
    void testGet() {
        GenreMasterCriteria criteria = new GenreMasterCriteria(domainMusicFolders,
                GenreMasterScope.ALBUM, GenreMasterSort.NAME);
        List<Genre> genres = genreMasterProvider.getGenres(criteria, 0, Integer.MAX_VALUE);
        assertEquals(14, genres.size());
        assertEquals("Audiobook - Historical", genres.get(0).name());
        assertEquals("Audiobook - Sports", genres.get(1).name());
        assertEquals("GENRE_A", genres.get(2).name());
        assertEquals("GENRE_B", genres.get(3).name());
        assertEquals("GENRE_C", genres.get(4).name());
        assertEquals("GENRE_D", genres.get(5).name());
        assertEquals("GENRE_E", genres.get(6).name());
        assertEquals("GENRE_F", genres.get(7).name());
        assertEquals("GENRE_G", genres.get(8).name());
        assertEquals("GENRE_H", genres.get(9).name());
        assertEquals("GENRE_I", genres.get(10).name());
        assertEquals("GENRE_J", genres.get(11).name());
        assertEquals("GENRE_K", genres.get(12).name());
        assertEquals("GENRE_L", genres.get(13).name());
        assertEquals(genres.size(), genreMasterProvider.getGenresCount(criteria));
    }

    @Test
    void testOffsetCount() {

        GenreMasterCriteria criteria = new GenreMasterCriteria(domainMusicFolders,
                GenreMasterScope.ALBUM, GenreMasterSort.NAME);
        List<Genre> genres = genreMasterProvider.getGenres(criteria, 0, 3);
        assertEquals(3, genres.size());
        assertEquals("Audiobook - Historical", genres.get(0).name());
        assertEquals("Audiobook - Sports", genres.get(1).name());
        assertEquals("GENRE_A", genres.get(2).name());

        genres = genreMasterProvider.getGenres(criteria, 3, 6);
        assertEquals(6, genres.size());
        assertEquals("GENRE_B", genres.get(0).name());
        assertEquals("GENRE_C", genres.get(1).name());
        assertEquals("GENRE_D", genres.get(2).name());
        assertEquals("GENRE_E", genres.get(3).name());
        assertEquals("GENRE_F", genres.get(4).name());
        assertEquals("GENRE_G", genres.get(5).name());

        genres = genreMasterProvider.getGenres(criteria, 9, 6);
        assertEquals(5, genres.size());
        assertEquals("GENRE_H", genres.get(0).name());
        assertEquals("GENRE_I", genres.get(1).name());
        assertEquals("GENRE_J", genres.get(2).name());
        assertEquals("GENRE_K", genres.get(3).name());
        assertEquals("GENRE_L", genres.get(4).name());
    }
}

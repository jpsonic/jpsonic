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

package com.tesshu.jpsonic.persistence.api.repository;

import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparatorsTestUtils;
import com.tesshu.jpsonic.persistence.api.entity.Artist;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ArtistDaoTest extends AbstractNeedsScan {

    private static final List<MusicFolder> MUSIC_FOLDERS = Arrays
        .asList(new MusicFolder(1, resolveBaseMediaPath("Sort/Compare"), "Artists", true, now(), 1,
                false));

    @Autowired
    private ArtistDao artistDao;

    @Override
    public List<MusicFolder> getMusicFolders() {
        return MUSIC_FOLDERS;
    }

    @BeforeEach
    void setup() {
        populateDatabaseOnlyOnce();
    }

    @Test
    void testGetAlphabetialArtists() {
        List<Artist> all = artistDao
            .getAlphabetialArtists(0, Integer.MAX_VALUE, Arrays.asList(MUSIC_FOLDERS.get(0)));
        List<String> names = all.stream().map(Artist::getName).collect(Collectors.toList());
        assertTrue(JpsonicComparatorsTestUtils.validateNaturalList(names));
    }
}

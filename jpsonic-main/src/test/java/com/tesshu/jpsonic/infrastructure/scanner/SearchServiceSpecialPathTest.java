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

import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.util.ObjectUtils.isEmpty;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test cases related to #1139. Confirming whether shuffle search can be
 * performed correctly in MusicFolder containing special strings.
 * <p>
 * (Since the query of getRandomAlbums consists of folder paths only, this
 * verification is easy to perform.)
 * <p>
 * This test case is a FalsePattern for search, but there may be problems with
 * the data flow prior to creating the search index.
 */
class SearchServiceSpecialPathTest extends AbstractNeedsScan {

    private List<MusicFolder> musicFolders;

    @Autowired
    private MediaSearchProvider mediaSearchProvider;

    @Override
    public List<MusicFolder> getMusicFolders() {
        if (isEmpty(musicFolders)) {
            musicFolders = Arrays
                .asList(new MusicFolder(1, resolveBaseMediaPath("Search/SpecialPath/accessible"),
                        "accessible", true, now(), 0, false),
                        new MusicFolder(2, resolveBaseMediaPath("Search/SpecialPath/accessible's"),
                                "accessible's", true, now(), 1, false),
                        new MusicFolder(3, resolveBaseMediaPath("Search/SpecialPath/accessible+s"),
                                "accessible+s", true, now(), 2, false));
        }
        return musicFolders;
    }

    @BeforeEach
    void setup() {
        populateDatabase();
    }

    @Test
    void testSpecialCharactersInDirName() {

        List<MusicFolder> folders = getMusicFolders();

        // ALL Songs
        List<MediaFile> randomAlbums = mediaSearchProvider
            .getRandomAlbums(Integer.MAX_VALUE, folders);
        assertEquals(3, randomAlbums.size(), "ALL Albums ");

        // dir - accessible
        List<MusicFolder> folder01 = folders
            .stream()
            .filter(m -> "accessible".equals(m.getName()))
            .collect(Collectors.toList());
        randomAlbums = mediaSearchProvider.getRandomAlbums(Integer.MAX_VALUE, folder01);
        assertEquals(1, randomAlbums.size(), "Albums in \"accessible\" ");

        // dir - accessible's
        List<MusicFolder> folder02 = folders
            .stream()
            .filter(m -> "accessible's".equals(m.getName()))
            .collect(Collectors.toList());
        randomAlbums = mediaSearchProvider.getRandomAlbums(Integer.MAX_VALUE, folder02);
        assertEquals(1, randomAlbums.size(), "Albums in \"accessible's\" ");

        // dir - accessible+s
        List<MusicFolder> folder03 = folders
            .stream()
            .filter(m -> "accessible+s".equals(m.getName()))
            .collect(Collectors.toList());
        randomAlbums = mediaSearchProvider.getRandomAlbums(Integer.MAX_VALUE, folder03);
        assertEquals(1, randomAlbums.size(), "Albums in \"accessible+s\" ");
    }
}

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

package com.tesshu.jpsonic.service.upnp.processor;

import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.Genre;
import com.tesshu.jpsonic.domain.MediaFile;
import com.tesshu.jpsonic.domain.MusicFolder;
import com.tesshu.jpsonic.util.LegacyMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/*
 * Test to correct sort inconsistencies.
 */
class SongByGenreUpnpProcessorTest extends AbstractNeedsScan {

    private static final List<MusicFolder> MUSIC_FOLDERS = Arrays
            .asList(new MusicFolder(1, resolveBaseMediaPath("Sort/Pagination/Artists"), "Artists", true, now(), 1));

    @Autowired
    private SongByGenreUpnpProcessor songByGenreUpnpProcessor;

    @Override
    public List<MusicFolder> getMusicFolders() {
        return MUSIC_FOLDERS;
    }

    @BeforeEach
    public void setup() {
        setSortStrict(true);
        setSortAlphanum(true);
        settingsService.setSortAlbumsByYear(false);
        settingsService.setSortGenresByAlphabet(false);
        populateDatabaseOnlyOnce();
    }

    @Test
    void testGetItemCount() {
        assertEquals(31, songByGenreUpnpProcessor.getDirectChildrenCount());
    }

    @Test
    void testGetItems() {

        Map<String, Genre> c = LegacyMap.of();

        List<Genre> items = songByGenreUpnpProcessor.getDirectChildren(0, 10);
        items.stream().filter(g -> !c.containsKey(g.getName())).forEach(g -> c.put(g.getName(), g));
        assertEquals(c.size(), 10);

        items = songByGenreUpnpProcessor.getDirectChildren(10, 10);
        items.stream().filter(g -> !c.containsKey(g.getName())).forEach(g -> c.put(g.getName(), g));
        assertEquals(c.size(), 20);

        items = songByGenreUpnpProcessor.getDirectChildren(20, 100);
        assertEquals(11, items.size());
        items.stream().filter(g -> !c.containsKey(g.getName())).forEach(g -> c.put(g.getName(), g));
        assertEquals(c.size(), 31);

    }

    @Test
    void testGetChildSizeOf() {
        List<Genre> artists = songByGenreUpnpProcessor.getDirectChildren(0, 1);
        assertEquals(1, artists.size());
        // assertEquals("A;B;C", artists.get(0).getName());
        assertEquals(31, songByGenreUpnpProcessor.getChildSizeOf(artists.get(0)));
    }

    @Test
    void testGetChildren() {

        List<Genre> artists = songByGenreUpnpProcessor.getDirectChildren(0, 1);
        assertEquals(1, artists.size());
        // assertEquals("A;B;C", artists.get(0).getName());

        Map<String, MediaFile> c = LegacyMap.of();

        List<MediaFile> children = songByGenreUpnpProcessor.getChildren(artists.get(0), 0, 10);
        children.stream().filter(m -> !c.containsKey(m.getGenre())).forEach(m -> c.put(m.getGenre(), m));
        assertEquals(c.size(), 10);

        children = songByGenreUpnpProcessor.getChildren(artists.get(0), 10, 10);
        children.stream().filter(m -> !c.containsKey(m.getGenre())).forEach(m -> c.put(m.getGenre(), m));
        assertEquals(c.size(), 20);

        children = songByGenreUpnpProcessor.getChildren(artists.get(0), 20, 100);
        assertEquals(11, children.size());
        children.stream().filter(m -> !c.containsKey(m.getGenre())).forEach(m -> c.put(m.getGenre(), m));
        assertEquals(c.size(), 31);

    }

}

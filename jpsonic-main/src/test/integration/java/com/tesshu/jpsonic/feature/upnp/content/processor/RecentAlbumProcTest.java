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

package com.tesshu.jpsonic.feature.upnp.content.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.MediaFile;
import com.tesshu.jpsonic.feature.upnp.UPnPSKeys;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jupnp.support.model.BrowseResult;
import org.springframework.beans.factory.annotation.Autowired;

class RecentAlbumProcTest extends AbstractNeedsScan {

    private static final List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> MUSIC_FOLDERS = Arrays
        .asList(new com.tesshu.jpsonic.persistence.api.entity.MusicFolder(1,
                resolveBaseMediaPath("Sort/Pagination/Albums"), "Albums", true, Instant.now(), 1,
                false));

    @Autowired
    private RecentAlbumProc processor;

    @Override
    public List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> getMusicFolders() {
        return MUSIC_FOLDERS;
    }

    @BeforeEach
    void setup() {
        populateDatabaseOnlyOnce();
        settingsFacade.commit(UPnPSKeys.basic.baseLanUrl, "https://192.168.1.1:4040");
    }

    @Test
    void testGetProcId() {
        assertEquals("r", processor.getProcId().getValue());
    }

    @Test
    void testBrowseRoot() throws ExecutionException {
        BrowseResult result = processor.browseRoot(null, 0, 30);
        assertEquals(30, result.getCount().getValue());
        result = processor.browseRoot(null, 0, 500);
        assertEquals(31, result.getCount().getValue());
    }

    @Test
    void testGetDirectChildren() {
        assertEquals(30, processor.getDirectChildren(0, 30).size());
        assertEquals(1, processor.getDirectChildren(30, 30).size());

        Map<Integer, MediaFile> c = LegacyMap.of();

        List<MediaFile> items = processor.getDirectChildren(0, 10);

        items.stream().filter(m -> !c.containsKey(m.id())).forEach(m -> c.put(m.id(), m));
        assertEquals(10, c.size());

        items = processor.getDirectChildren(10, 10);
        items.stream().filter(m -> !c.containsKey(m.id())).forEach(m -> c.put(m.id(), m));
        assertEquals(20, c.size());

        items = processor.getDirectChildren(20, 100);
        assertEquals(11, items.size());
        items.stream().filter(m -> !c.containsKey(m.id())).forEach(m -> c.put(m.id(), m));
        assertEquals(31, c.size());

        assertEquals(4, processor.getDirectChildren(0, 4).size());
        assertEquals(3, processor.getDirectChildren(0, 3).size());
        assertEquals(2, processor.getDirectChildren(0, 2).size());
        assertEquals(1, processor.getDirectChildren(0, 1).size());

        assertEquals(4, processor.getDirectChildren(1, 4).size());
        assertEquals(3, processor.getDirectChildren(1, 3).size());
        assertEquals(2, processor.getDirectChildren(1, 2).size());
        assertEquals(1, processor.getDirectChildren(1, 1).size());

        List<MediaFile> albums = processor.getDirectChildren(1, 1);
        assertEquals(1, albums.size());
        // A fast scan will not necessarily give this result
        // assertEquals(1, processor.getChildSizeOf(albums.get(0)));
    }

    @Test
    void testDirectChildrenCount() {
        assertEquals(31, processor.getDirectChildrenCount());
    }
}

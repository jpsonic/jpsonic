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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.Album;
import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.provider.resource.AlbumProvider;
import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.feature.upnp.content.UPnPDIDLFactory;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jupnp.support.model.BrowseResult;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("PMD.TooManyStaticImports")
class RecentAlbumId3ProcTest {

    @Nested
    class UnitTest {

        private AlbumProvider albumProvider;
        private RecentAlbumId3Proc proc;

        @BeforeEach
        void setup() {
            UPnPDIDLFactory factory = mock(UPnPDIDLFactory.class);
            MediaFileProvider mediaFileProvider = mock(MediaFileProvider.class);
            MusicFolderProvider musicFolderProvider = mock(MusicFolderProvider.class);
            albumProvider = mock(AlbumProvider.class);
            proc = new RecentAlbumId3Proc(musicFolderProvider, albumProvider, mediaFileProvider,
                    factory);
        }

        @Test
        void testGetProcId() {
            assertEquals("rid3", proc.getProcId().getValue());
        }

        @Test
        void testBrowseRoot() throws ExecutionException {
            when(albumProvider.findNewestAlbums(anyList(), anyLong(), anyLong()))
                .thenReturn(List.of(new Album(0, "name", "artist", 0, "")));
            when(albumProvider.countAlbums(anyList())).thenReturn(99);
            BrowseResult result = proc.browseRoot(null, 0, 0);
            assertEquals(1, result.getCountLong());
            assertEquals(50, result.getTotalMatches().getValue()); // RECENT_COUNT
            verify(albumProvider, times(1)).findNewestAlbums(anyList(), anyLong(), anyLong());
            verify(albumProvider, times(2)).countAlbums(anyList());

            clearInvocations(albumProvider);
            result = proc.browseRoot(null, 0, 1);
            assertEquals(1, result.getCountLong());
            assertEquals(50, result.getTotalMatches().getValue()); // RECENT_COUNT
            verify(albumProvider, times(1)).findNewestAlbums(anyList(), anyLong(), anyLong());
            verify(albumProvider, times(2)).countAlbums(anyList());
        }

        @Test
        void testGetDirectChildren() {
            when(albumProvider.countAlbums(ArgumentMatchers.<MusicFolder>anyList())).thenReturn(50);

            assertEquals(0, proc.getDirectChildren(0, 0).size());
            verify(albumProvider, times(1))
                .findNewestAlbums(ArgumentMatchers.<MusicFolder>anyList(), anyLong(), anyLong());

            clearInvocations(albumProvider);
            assertEquals(0, proc.getDirectChildren(0, 1).size());
            verify(albumProvider, times(1))
                .findNewestAlbums(ArgumentMatchers.<MusicFolder>anyList(), anyLong(), anyLong());
        }

        @Test
        void testGetDirectChildrenCount() {
            assertEquals(0, proc.getDirectChildrenCount());
            verify(albumProvider, times(1)).countAlbums(anyList());
        }
    }

    @Nested
    class IntegrationTest extends AbstractNeedsScan {

        private static final List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> MUSIC_FOLDERS = Arrays
            .asList(new com.tesshu.jpsonic.persistence.api.entity.MusicFolder(1,
                    resolveBaseMediaPath("Sort/Pagination/Albums"), "Albums", true, Instant.now(),
                    1, false));

        @Autowired
        private RecentAlbumId3Proc processor;

        @Override
        public List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> getMusicFolders() {
            return MUSIC_FOLDERS;
        }

        @BeforeEach
        void setup() {
            populateDatabaseOnlyOnce();
        }

        @Test
        void testGetDirectChildren() {
            assertEquals(30, processor.getDirectChildren(0, 30).size());
            assertEquals(1, processor.getDirectChildren(30, 30).size());

            Map<Integer, Album> c = LegacyMap.of();

            List<Album> items = processor.getDirectChildren(0, 10);
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
        }

        @Test
        void testGetDirectChildrenCount() {
            assertEquals(31, processor.getDirectChildrenCount());
        }

        @Test
        void testGetChildSizeOf() {
            List<Album> albums = processor.getDirectChildren(1, 1);
            assertEquals(1, albums.size());
            // A fast scan will not necessarily give this result
            // assertEquals(1, processor.getChildSizeOf(albums.get(0)));
        }
    }
}

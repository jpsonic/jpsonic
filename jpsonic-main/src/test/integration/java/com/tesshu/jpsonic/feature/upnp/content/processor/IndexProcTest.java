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

import static com.tesshu.jpsonic.feature.upnp.content.processor.UPnPProcessorTestUtils.INDEX_LIST;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.MediaFile;
import com.tesshu.jpsonic.domain.model.MusicFolderContent;
import com.tesshu.jpsonic.domain.model.MusicIndex;
import com.tesshu.jpsonic.domain.policy.RuntimeOrderPolicy.ChildOrder;
import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicIndexProvider;
import com.tesshu.jpsonic.feature.upnp.content.ProcId;
import com.tesshu.jpsonic.feature.upnp.content.UPnPDIDLFactory;
import com.tesshu.jpsonic.feature.upnp.content.processor.composite.IndexOrSong;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.jupnp.support.model.DIDLContent;
import org.springframework.beans.factory.annotation.Autowired;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals" })
class IndexProcTest {

    @Order(1)
    @Nested
    class UnitTest {

        private MusicIndexProvider musicIndexProvider;
        private MediaFileProvider mediaFileProvider;
        private UPnPDIDLFactory factory;
        private IndexProc proc;

        @BeforeEach
        void setup() {
            musicIndexProvider = mock(MusicIndexProvider.class);
            mediaFileProvider = mock(MediaFileProvider.class);
            factory = mock(UPnPDIDLFactory.class);
            proc = new IndexProc(mock(MusicFolderProvider.class), musicIndexProvider,
                    mediaFileProvider, factory);
        }

        @Test
        void testGetProcId() {
            assertEquals("index", proc.getProcId().getValue());
        }

        @Test
        void testCreateContainer() {
            MusicIndex musicIndex = new MusicIndex("A", Collections.emptyList());
            SortedMap<MusicIndex, Integer> map = new TreeMap<>((a, b) -> 0);
            map.put(musicIndex, 99);
            when(musicIndexProvider.countMusicFolderContent(anyList()))
                .thenReturn(new MusicFolderContent.Counts(map, 0));
            assertNull(proc.createContainer(new IndexOrSong(musicIndex)));
            verify(factory, times(1))
                .toMusicIndex(any(ProcId.class), any(MusicIndex.class), anyInt());
        }

        @Test
        void testAddItem() {
            DIDLContent parent = new DIDLContent();
            assertEquals(0, parent.getCount());
            assertEquals(0, parent.getContainers().size());
            assertEquals(0, parent.getItems().size());

            MusicIndex musicIndex = new MusicIndex("A", Collections.emptyList());
            proc.addDirectChild(parent, new IndexOrSong(musicIndex));
            assertEquals(1, parent.getCount());
            assertEquals(1, parent.getContainers().size());
            assertEquals(0, parent.getItems().size());

            proc
                .addDirectChild(parent,
                        new IndexOrSong(new MediaFile(1, "pathString", 0, "format", "MUSIC", 256,
                                60, 9999, "artist", "album", "title", "albumArtist", 0, "genre",
                                2026, "thumbUri", "composer", "reading", "#", "comment")));
            assertEquals(2, parent.getCount());
            assertEquals(1, parent.getContainers().size());
            assertEquals(1, parent.getItems().size());
        }

        @Test
        void testGetDirectChildren() {
            MusicIndex musicIndex = new MusicIndex("A", Collections.emptyList());
            SortedMap<MusicIndex, Integer> map = new TreeMap<>((a, b) -> 0);
            map.put(musicIndex, 99);
            when(musicIndexProvider
                .countMusicFolderContent(anyList(), any(new MediaFile.Type[0].getClass())))
                .thenReturn(new MusicFolderContent.Counts(map, 0));

            assertEquals(Collections.emptyList(), proc.getDirectChildren(0, 0));
            verify(musicIndexProvider, times(1))
                .countMusicFolderContent(anyList(), any(new MediaFile.Type[0].getClass()));

            mediaFileProvider.countChildren(Collections.emptyList());
            verify(mediaFileProvider, times(1))
                .countChildren(anyList(), any(new MediaFile.Type[0].getClass()));
        }

        @Test
        void testGetDirectChildrenCount() {
            MusicIndex musicIndex = new MusicIndex("A", Collections.emptyList());
            SortedMap<MusicIndex, Integer> map = new TreeMap<>((a, b) -> 0);
            map.put(musicIndex, 99);
            when(musicIndexProvider
                .countMusicFolderContent(anyList(), any(new MediaFile.Type[0].getClass())))
                .thenReturn(new MusicFolderContent.Counts(map, 1));
            assertEquals(2, proc.getDirectChildrenCount());
        }

        @Test
        void testGetDirectChild() {
            MusicIndex musicIndex = new MusicIndex("A", Collections.emptyList());
            SortedMap<MusicIndex, Integer> map = new TreeMap<>((a, b) -> 0);
            map.put(musicIndex, 99);
            when(musicIndexProvider
                .countMusicFolderContent(anyList(), any(new MediaFile.Type[0].getClass())))
                .thenReturn(new MusicFolderContent.Counts(map, map.size()));

            IndexOrSong indexOrSong = proc.getDirectChild("A");
            assertEquals(musicIndex, indexOrSong.getMusicIndex());

            assertNull(proc.getDirectChild("81"));

            MediaFile song = new MediaFile(82, "pathString", 0, "format", "MUSIC", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            when(mediaFileProvider.requireMediaFile(song.id())).thenReturn(song);
            indexOrSong = proc.getDirectChild("82");
            assertEquals(song, indexOrSong.getSong());
        }

        @Test
        void testGetChildren() {
            MediaFile song = new MediaFile(82, "pathString", 0, "format", "MUSIC", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");

            assertEquals(Collections.emptyList(), proc.getChildren(new IndexOrSong(song), 0, 100));
            verify(mediaFileProvider, never())
                .findChildren(anyList(), any(MusicIndex.class), anyLong(), anyLong(),
                        any(new MediaFile.Type[0].getClass()));

            MusicIndex musicIndex = new MusicIndex("A", Collections.emptyList());
            assertEquals(Collections.emptyList(),
                    proc.getChildren(new IndexOrSong(musicIndex), 0, 100));
            verify(mediaFileProvider, times(1))
                .findChildren(anyList(), any(MusicIndex.class), anyLong(), anyLong(),
                        any(new MediaFile.Type[0].getClass()));
        }

        @Test
        void testGetChildSizeOf() {
            MusicIndex musicIndex = new MusicIndex("A", Collections.emptyList());
            assertEquals(0, proc.getChildSizeOf(new IndexOrSong(musicIndex)));

            SortedMap<MusicIndex, Integer> map = new TreeMap<>((a, b) -> 0);
            map.put(musicIndex, 99);
            when(musicIndexProvider
                .countMusicFolderContent(anyList(), any(new MediaFile.Type[0].getClass())))
                .thenReturn(new MusicFolderContent.Counts(map, 0));
            assertEquals(99, proc.getChildSizeOf(new IndexOrSong(musicIndex)));

            MediaFile song = new MediaFile(82, "pathString", 0, "format", "MUSIC", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            assertEquals(0, proc.getChildSizeOf(new IndexOrSong(song)));
        }

        @Test
        void testAddChild() {

            DIDLContent parent = new DIDLContent();
            assertEquals(0, parent.getCount());
            assertEquals(0, parent.getContainers().size());
            assertEquals(0, parent.getItems().size());

            MediaFile directory = new MediaFile(82, "pathString", 0, "format", "DIRECTORY", 256, 60,
                    9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            proc.addChild(parent, directory);
            assertEquals(1, parent.getCount());
            assertEquals(1, parent.getContainers().size());
            assertEquals(0, parent.getItems().size());
            verify(factory, times(1)).toArtist(any(MediaFile.class), anyInt());

            MediaFile album = new MediaFile(82, "pathString", 0, "format", "ALBUM", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            parent = new DIDLContent();
            clearInvocations(factory);
            proc.addChild(parent, album);
            assertEquals(1, parent.getCount());
            assertEquals(1, parent.getContainers().size());
            assertEquals(0, parent.getItems().size());
            verify(factory, times(1)).toAlbum(any(MediaFile.class), anyInt());

            MediaFile song = new MediaFile(82, "pathString", 0, "format", "MUSIC", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            parent = new DIDLContent();
            clearInvocations(factory);
            proc.addChild(parent, song);
            assertEquals(1, parent.getCount());
            assertEquals(0, parent.getContainers().size());
            assertEquals(1, parent.getItems().size());
            verify(factory, times(1)).toMusicTrack(any(MediaFile.class));

            MediaFile video = new MediaFile(82, "pathString", 0, "format", "VIDEO", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            parent = new DIDLContent();
            clearInvocations(factory);
            proc.addChild(parent, video);
            assertEquals(0, parent.getCount());
            assertEquals(0, parent.getContainers().size());
            assertEquals(0, parent.getItems().size());
            verify(factory, never()).toMusicTrack(any(MediaFile.class));

            MediaFile book = new MediaFile(82, "pathString", 0, "format", "AUDIOBOOK", 256, 60,
                    9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            parent = new DIDLContent();
            clearInvocations(factory);
            proc.addChild(parent, book);
            assertEquals(0, parent.getCount());
            assertEquals(0, parent.getContainers().size());
            assertEquals(0, parent.getItems().size());
            verify(factory, never()).toMusicTrack(any(MediaFile.class));

            MediaFile podcast = new MediaFile(82, "pathString", 0, "format", "PODCAST", 256, 60,
                    9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            parent = new DIDLContent();
            clearInvocations(factory);
            proc.addChild(parent, podcast);
            assertEquals(0, parent.getCount());
            assertEquals(0, parent.getContainers().size());
            assertEquals(0, parent.getItems().size());
            verify(factory, never()).toMusicTrack(any(MediaFile.class));
        }
    }

    @Order(2)
    @Nested
    class IntegrationTest extends AbstractNeedsScan {

        private final List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = Arrays
            .asList(new com.tesshu.jpsonic.persistence.api.entity.MusicFolder(1,
                    resolveBaseMediaPath("Sort/Pagination/Artists"), "Artists", true, Instant.now(),
                    1, false));

        @Autowired
        private IndexProc indexProc;
        @Autowired
        private MediaFileProvider mediaFileProvider;

        @Override
        public List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> getMusicFolders() {
            return musicFolders;
        }

        @BeforeEach
        void setup() {
            String simpleIndex = """
                    A B C D E F G H I J K L M N O P Q R S T U V W X-Z(XYZ) \
                    \u3042(\u30A2\u30A4\u30A6\u30A8\u30AA) \
                    \u304B(\u30AB\u30AD\u30AF\u30B1\u30B3) \
                    \u3055(\u30B5\u30B7\u30B9\u30BB\u30BD) \
                    \u305F(\u30BF\u30C1\u30C4\u30C6\u30C8) \
                    \u306A(\u30CA\u30CB\u30CC\u30CD\u30CE) \
                    \u306F(\u30CF\u30D2\u30D5\u30D8\u30DB) \
                    \u307E(\u30DE\u30DF\u30E0\u30E1\u30E2) \
                    \u3084(\u30E4\u30E6\u30E8) \
                    \u3089(\u30E9\u30EA\u30EB\u30EC\u30ED) \
                    \u308F(\u30EF\u30F2\u30F3)
                    """; // JP Index

            // Test case is created on the premise of simpleIndex.
            settingsFacade.commit(SKeys.general.index.indexString, simpleIndex);
            populateDatabaseOnlyOnce();
        }

        @Test
        void testGetItemCount() {
            assertEquals(31, indexProc.getDirectChildrenCount());
        }

        @Test
        void testGetItems() {

            List<IndexOrSong> items = indexProc.getDirectChildren(0, 10);
            assertEquals(10, items.size());
            assertEquals("A", items.get(0).getMusicIndex().index());
            assertEquals("B", items.get(1).getMusicIndex().index());
            assertEquals("C", items.get(2).getMusicIndex().index());
            assertEquals("D", items.get(3).getMusicIndex().index());
            assertEquals("E", items.get(4).getMusicIndex().index());
            assertEquals("あ", items.get(5).getMusicIndex().index());
            assertEquals("さ", items.get(6).getMusicIndex().index());
            assertEquals("は", items.get(7).getMusicIndex().index());
            assertEquals("#", items.get(8).getMusicIndex().index());
            assertEquals("single1", items.get(9).getSong().name());

            items = indexProc.getDirectChildren(10, 10);
            assertEquals(10, items.size());
            assertEquals("single2", items.get(0).getSong().name());
            assertEquals("single3", items.get(1).getSong().name());
            assertEquals("single4", items.get(2).getSong().name());
            assertEquals("single5", items.get(3).getSong().name());
            assertEquals("single6", items.get(4).getSong().name());
            assertEquals("single7", items.get(5).getSong().name());
            assertEquals("single8", items.get(6).getSong().name());
            assertEquals("single9", items.get(7).getSong().name());
            assertEquals("single10", items.get(8).getSong().name());
            assertEquals("single11", items.get(9).getSong().name());

            items = indexProc.getDirectChildren(20, 10);
            assertEquals(10, items.size());
            assertEquals("single12", items.get(0).getSong().name());
            assertEquals("single13", items.get(1).getSong().name());
            assertEquals("single14", items.get(2).getSong().name());
            assertEquals("single15", items.get(3).getSong().name());
            assertEquals("single16", items.get(4).getSong().name());
            assertEquals("single17", items.get(5).getSong().name());
            assertEquals("single18", items.get(6).getSong().name());
            assertEquals("single19", items.get(7).getSong().name());
            assertEquals("single20", items.get(8).getSong().name());
            assertEquals("single21", items.get(9).getSong().name());

            items = indexProc.getDirectChildren(30, 10);
            assertEquals(1, items.size());
            assertEquals("single22", items.get(0).getSong().name());

            items = indexProc.getDirectChildren(0, 5);
            assertEquals(5, items.size());
            assertEquals("A", items.get(0).getMusicIndex().index());
            assertEquals("B", items.get(1).getMusicIndex().index());
            assertEquals("C", items.get(2).getMusicIndex().index());
            assertEquals("D", items.get(3).getMusicIndex().index());
            assertEquals("E", items.get(4).getMusicIndex().index());

            items = indexProc.getDirectChildren(5, 100);
            assertEquals(26, items.size());
            assertEquals("あ", items.get(0).getMusicIndex().index());
            assertEquals("さ", items.get(1).getMusicIndex().index());
            assertEquals("は", items.get(2).getMusicIndex().index());
            assertEquals("#", items.get(3).getMusicIndex().index());
            assertEquals("single1", items.get(4).getSong().name());

            items = indexProc.getDirectChildren(0, 9);
            assertEquals(9, items.size());
            assertEquals("A", items.get(0).getMusicIndex().index());
            assertEquals("#", items.get(8).getMusicIndex().index());

            items = indexProc.getDirectChildren(8, 1);
            assertEquals(1, items.size());
            assertEquals("#", items.get(0).getMusicIndex().index());

            items = indexProc.getDirectChildren(9, 1);
            assertEquals(1, items.size());
            assertEquals("single1", items.get(0).getSong().name());

            items = indexProc.getDirectChildren(30, 1);
            assertEquals(1, items.size());
            assertEquals("single22", items.get(0).getSong().name());
        }

        @Test
        void testGetChildSizeOf() {
            List<IndexOrSong> items = indexProc.getDirectChildren(0, 100);
            assertEquals(31, items.size());
            assertEquals(1, indexProc.getChildSizeOf(items.get(0)));
            assertEquals(1, indexProc.getChildSizeOf(items.get(1)));
            assertEquals(1, indexProc.getChildSizeOf(items.get(2)));
            assertEquals(1, indexProc.getChildSizeOf(items.get(3)));
            assertEquals(1, indexProc.getChildSizeOf(items.get(4)));
            assertEquals(5, indexProc.getChildSizeOf(items.get(5)));
            assertEquals(1, indexProc.getChildSizeOf(items.get(6)));
            assertEquals(5, indexProc.getChildSizeOf(items.get(7)));
            assertEquals(15, indexProc.getChildSizeOf(items.get(8)));
        }

        @Test
        void testGetChildren() {
            List<String> artistNames = indexProc
                .getDirectChildren(0, 100)
                .stream()
                .filter(IndexOrSong::isMusicIndex)
                .flatMap(m -> indexProc.getChildren(m, 0, 100).stream())
                .map(MediaFile::name)
                .toList();
            assertEquals(INDEX_LIST, artistNames);

            List<IndexOrSong> items = indexProc.getDirectChildren(0, 100);
            assertEquals(31, items.size());
            assertEquals(5, indexProc.getChildSizeOf(items.get(5)));
            assertEquals(5, indexProc.getChildSizeOf(items.get(7)));
            assertEquals(15, indexProc.getChildSizeOf(items.get(8)));

            List<MediaFile> artist = indexProc.getChildren(items.get(5), 0, 3);
            assertEquals(INDEX_LIST.get(5), artist.get(0).name());
            assertEquals(INDEX_LIST.get(6), artist.get(1).name());
            assertEquals(INDEX_LIST.get(7), artist.get(2).name());

            artist = indexProc.getChildren(items.get(5), 3, 100);
            assertEquals(INDEX_LIST.get(8), artist.get(0).name());
            assertEquals(INDEX_LIST.get(9), artist.get(1).name());

            artist = indexProc.getChildren(items.get(7), 0, 3);
            assertEquals(INDEX_LIST.get(11), artist.get(0).name());
            assertEquals(INDEX_LIST.get(12), artist.get(1).name());
            assertEquals(INDEX_LIST.get(13), artist.get(2).name());
            artist = indexProc.getChildren(items.get(7), 3, 100);
            assertEquals(INDEX_LIST.get(14), artist.get(0).name());
            assertEquals(INDEX_LIST.get(15), artist.get(1).name());

            artist = indexProc.getChildren(items.get(8), 0, 3);

            assertEquals(3, artist.size());
            assertEquals(INDEX_LIST.get(16), artist.get(0).name());
            assertEquals(INDEX_LIST.get(17), artist.get(1).name());
            assertEquals(INDEX_LIST.get(18), artist.get(2).name());
            artist = indexProc.getChildren(items.get(8), 3, 100);
            assertEquals(12, artist.size());
            assertEquals(INDEX_LIST.get(19), artist.get(0).name());
            assertEquals(INDEX_LIST.get(20), artist.get(1).name());
            assertEquals(INDEX_LIST.get(21), artist.get(2).name());
            assertEquals(INDEX_LIST.get(22), artist.get(3).name());
            assertEquals(INDEX_LIST.get(23), artist.get(4).name());
            assertEquals(INDEX_LIST.get(24), artist.get(5).name());
            assertEquals(INDEX_LIST.get(25), artist.get(6).name());
            assertEquals(INDEX_LIST.get(26), artist.get(7).name());
            assertEquals(INDEX_LIST.get(27), artist.get(8).name());
            assertEquals(INDEX_LIST.get(28), artist.get(9).name());
            assertEquals(INDEX_LIST.get(29), artist.get(10).name());
            assertEquals(INDEX_LIST.get(30), artist.get(11).name());
        }

        @Test
        void testAlbum() {

            settingsFacade.commit(SKeys.general.sort.albumsByYear, false);

            List<IndexOrSong> indexes = indexProc.getDirectChildren(0, 100);
            assertEquals(31, indexes.size());

            IndexOrSong indexOrSong = indexes.get(8);
            assertEquals("#", indexOrSong.getMusicIndex().index());

            List<MediaFile> artists = indexProc.getChildren(indexOrSong, 0, Integer.MAX_VALUE);
            assertEquals(15, artists.size());
            MediaFile artist = artists.get(0);
            assertEquals("10", artist.name());

            List<MediaFile> albums = mediaFileProvider
                .findChildren(artist, ChildOrder.DEFAULT, 0, Integer.MAX_VALUE,
                        MediaFile.Type.PODCAST, MediaFile.Type.AUDIOBOOK, MediaFile.Type.VIDEO);
            assertEquals(31, albums.size());

            assertTrue(UPnPProcessorTestUtils
                .validateJPSonicNaturalList(
                        albums.stream().map(MediaFile::name).collect(Collectors.toList())));
        }

        @Test
        void testSongs() {

            List<IndexOrSong> indexes = indexProc
                .getDirectChildren(0, 100)
                .stream()
                .filter(IndexOrSong::isMusicIndex)
                .filter(a -> "#".equals(a.getMusicIndex().index()))
                .collect(Collectors.toList());
            assertEquals(1, indexes.size());

            MusicIndex index = indexes.get(0).getMusicIndex();
            assertEquals("#", index.index());

            List<MediaFile> artists = indexProc
                .getChildren(indexes.get(0), 0, Integer.MAX_VALUE)
                .stream()
                .filter(a -> "20".equals(a.name()))
                .collect(Collectors.toList());
            assertEquals(1, artists.size());

            MediaFile artist = artists.get(0);
            assertEquals("20", artist.name());

            List<MediaFile> albums = mediaFileProvider
                .findChildren(artist, ChildOrder.DEFAULT, 0, Integer.MAX_VALUE,
                        MediaFile.Type.PODCAST, MediaFile.Type.AUDIOBOOK, MediaFile.Type.VIDEO);
            assertEquals(1, albums.size());

            MediaFile album = albums.get(0);
            assertEquals("ALBUM", album.name()); // the case where album name is different
                                                 // between file and id3

            List<MediaFile> songs = mediaFileProvider
                .findChildren(album, ChildOrder.DEFAULT, 0, Integer.MAX_VALUE);
            assertEquals(1, songs.size());

            MediaFile song = songs.get(0);
            assertEquals("empty", song.name());
        }
    }
}

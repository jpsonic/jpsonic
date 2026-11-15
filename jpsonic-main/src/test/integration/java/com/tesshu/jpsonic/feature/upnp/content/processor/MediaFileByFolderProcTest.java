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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.MediaFile;
import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.feature.upnp.UPnPSKeys;
import com.tesshu.jpsonic.feature.upnp.content.ProcId;
import com.tesshu.jpsonic.feature.upnp.content.UPnPDIDLFactory;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jupnp.support.model.DIDLContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals" })
class MediaFileByFolderProcTest {

    @Nested
    class UnitTest {

        private MediaFileByFolderProc proc;

        private MusicFolderProvider musicFolderProvider;
        private MediaFileProvider mediaFileProvider;
        private UPnPDIDLFactory factory;

        @BeforeEach
        void setup() {
            musicFolderProvider = mock(MusicFolderProvider.class);
            mediaFileProvider = mock(MediaFileProvider.class);
            SettingsFacade settingsFacade = SettingsFacadeBuilder.create().buildWithDefault();
            factory = mock(UPnPDIDLFactory.class);
            proc = new MediaFileByFolderProc(musicFolderProvider, mediaFileProvider, settingsFacade,
                    factory);
        }

        @Test
        void testGetProcId() {
            assertEquals("folder", proc.getProcId().getValue());
        }

        @Test
        void testCreateContainer() {
            MediaFile entity = new MediaFile(1, "pathString", 0, "format", "MUSIC", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> proc.createContainer(entity))
                .withNoCause();

            assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> proc
                    .createContainer(new MediaFile(1, "pathString", 0, "format", "VIDEO", 256, 60,
                            9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026,
                            "thumbUri", "composer", "reading", "#", "comment")))
                .withNoCause();

            assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> proc
                    .createContainer(new MediaFile(1, "pathString", 0, "format", "AUDIOBOOK", 256,
                            60, 9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026,
                            "thumbUri", "composer", "reading", "#", "comment")))
                .withNoCause();

            assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> proc
                    .createContainer(new MediaFile(1, "pathString", 0, "format", "PODCAST", 256, 60,
                            9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026,
                            "thumbUri", "composer", "reading", "#", "comment")))
                .withNoCause();

            clearInvocations(factory);
            proc
                .createContainer(new MediaFile(1, "pathString", 0, "format", "ALBUM", 256, 60, 9999,
                        "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                        "composer", "reading", "#", "comment"));
            verify(factory, times(1)).toAlbum(any(MediaFile.class), anyInt());

            clearInvocations(factory);
            proc
                .createContainer(new MediaFile(1, "pathString", 0, "format", "DIRECTORY", 256, 60,
                        9999, null, "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                        "composer", "reading", "#", "comment"));
            verify(factory, times(1))
                .toMusicFolder(any(ProcId.class), any(MediaFile.class), anyInt());

            clearInvocations(factory);
            proc
                .createContainer(new MediaFile(1, "pathString", 0, "format", "DIRECTORY", 256, 60,
                        9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026,
                        "thumbUri", "composer", "reading", "#", "comment"));
            verify(factory, times(1)).toArtist(any(MediaFile.class), anyInt());
        }

        @Test
        void testAddItem() {
            DIDLContent parent = new DIDLContent();
            assertEquals(0, parent.getItems().size());
            assertEquals(0, parent.getContainers().size());

            MediaFile song = new MediaFile(1, "pathString", 0, "format", "MUSIC", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            proc.addDirectChild(parent, song);
            assertEquals(1, parent.getItems().size());
            assertEquals(0, parent.getContainers().size());

            MediaFile album = new MediaFile(1, "pathString", 0, "format", "ALBUM", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");

            proc.addDirectChild(parent, album);
            assertEquals(1, parent.getItems().size());
            assertEquals(1, parent.getContainers().size());
        }

        @Nested
        class GetGetDirectChildrenTest {

            private final MusicFolder folder1 = new MusicFolder(0, "/path1", "folder1", false, null,
                    0, false);
            private final MusicFolder folder2 = new MusicFolder(0, "/path2", "folder2", false, null,
                    0, false);

            private final MediaFile mfolder1 = new MediaFile(1, "/path1", 0, "format", "DIRECTORY",
                    256, 60, 9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026,
                    "thumbUri", "composer", "reading", "#", "comment");
            private final MediaFile mfolder2 = new MediaFile(1, "/path2", 0, "format", "DIRECTORY",
                    256, 60, 9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026,
                    "thumbUri", "composer", "reading", "#", "comment");
            private final MediaFile mfolder3 = new MediaFile(1, "/path3", 0, "format", "DIRECTORY",
                    256, 60, 9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026,
                    "thumbUri", "composer", "reading", "#", "comment");

            @BeforeEach
            void setup() {
                when(mediaFileProvider
                    .findChildren(any(MediaFile.class), any(
                            com.tesshu.jpsonic.domain.policy.RuntimeOrderPolicy.ChildOrder.class),
                            anyLong(), anyLong(), any(new MediaFile.Type[0].getClass())))
                    .thenReturn(List.of(mfolder1, mfolder2, mfolder3));
                when(mediaFileProvider.requireMediaFile(folder1)).thenReturn(mfolder1);
                when(mediaFileProvider.requireMediaFile(folder2)).thenReturn(mfolder2);

                MediaFile file1 = new MediaFile(1, "/file1", 0, "format", "DIRECTORY", 256, 60,
                        9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026,
                        "thumbUri", "composer", "reading", "#", "comment");
                MediaFile file2 = new MediaFile(1, "/file1", 0, "format", "DIRECTORY", 256, 60,
                        9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026,
                        "thumbUri", "composer", "reading", "#", "comment");
                MediaFile file3 = new MediaFile(1, "/file1", 0, "format", "DIRECTORY", 256, 60,
                        9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026,
                        "thumbUri", "composer", "reading", "#", "comment");

                when(mediaFileProvider
                    .findChildren(any(MediaFile.class), any(
                            com.tesshu.jpsonic.domain.policy.RuntimeOrderPolicy.ChildOrder.class),
                            anyLong(), anyLong(), any(new MediaFile.Type[0].getClass())))
                    .thenReturn(List.of(file1, file2, file3));
            }

            @Test
            void testNoFolder() {
                assertEquals(0, proc.getDirectChildren(0, 30).size());
            }

            @Test
            void testSingleFolder() {
                when(musicFolderProvider.getGuestFolders()).thenReturn(List.of(folder1));
                assertEquals(3, proc.getDirectChildren(0, 30).size());
            }

            @Test
            void testMultiFolder() {
                when(musicFolderProvider.getGuestFolders()).thenReturn(List.of(folder1, folder1));
                assertEquals(0, proc.getDirectChildren(10, 0).size());
                assertEquals(2, proc.getDirectChildren(0, 3).size());
                assertEquals(1, proc.getDirectChildren(1, 3).size());
                assertEquals(0, proc.getDirectChildren(2, 2).size());
            }
        }

        @Nested
        class GetGetDirectChildrenCountTest {

            private final MusicFolder folder1 = new MusicFolder(0, "/path1", "folder1", false, null,
                    0, false);
            private final MusicFolder folder2 = new MusicFolder(0, "/path2", "folder2", false, null,
                    0, false);
            private final MusicFolder folder3 = new MusicFolder(0, "/path3", "folder3", false, null,
                    0, false);

            @BeforeEach
            void setup() {
                MediaFile file1 = new MediaFile(1, "/file1", 0, "format", "DIRECTORY", 256, 60,
                        9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026,
                        "thumbUri", "composer", "reading", "#", "comment");
                MediaFile file2 = new MediaFile(1, "/file1", 0, "format", "DIRECTORY", 256, 60,
                        9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026,
                        "thumbUri", "composer", "reading", "#", "comment");
                MediaFile file3 = new MediaFile(1, "/file1", 0, "format", "DIRECTORY", 256, 60,
                        9999, "artist", "album", "title", "albumArtist", 0, "genre", 2026,
                        "thumbUri", "composer", "reading", "#", "comment");
                when(mediaFileProvider
                    .findChildren(any(MediaFile.class), any(
                            com.tesshu.jpsonic.domain.policy.RuntimeOrderPolicy.ChildOrder.class),
                            anyLong(), anyLong(), any(new MediaFile.Type[0].getClass())))
                    .thenReturn(List.of(file1, file2, file3));
            }

            @Test
            void testNoFolder() {
                assertEquals(0, proc.getDirectChildrenCount());
            }

            @Test
            void testSingleFolder() {
                when(musicFolderProvider.getGuestFolders()).thenReturn(List.of(folder1));
                assertEquals(0, proc.getDirectChildrenCount());
                verify(mediaFileProvider, times(1))
                    .countChildren(anyList(), any(new MediaFile.Type[0].getClass()));
            }

            @Test
            void testMultiFolder() {
                when(musicFolderProvider.getGuestFolders())
                    .thenReturn(List.of(folder1, folder2, folder3));
                assertEquals(0, proc.getDirectChildren(10, 0).size());
                assertEquals(3, proc.getDirectChildren(0, 3).size());
                assertEquals(2, proc.getDirectChildren(1, 3).size());
                assertEquals(1, proc.getDirectChildren(2, 3).size());
                verify(mediaFileProvider, never())
                    .countChildren(anyList(), any(MediaFile.Type.class));
            }
        }

        @Test
        void testDirectChild() {
            assertNull(proc.getDirectChild("0"));
            verify(mediaFileProvider, times(1)).requireMediaFile(anyInt());
        }

        // TODO We may want to consider file filtering.
        @Test
        void testGetChildren() {
            MediaFile root = new MediaFile(1, "/path", 0, "format", "DIRECTORY", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            assertEquals(0, proc.getChildren(root, 0, 0).size());

            MediaFile artist = new MediaFile(1, "/path", 0, "format", "DIRECTORY", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            assertEquals(0, proc.getChildren(artist, 0, 0).size());

            MediaFile album = new MediaFile(1, "/path", 0, "format", "ALBUM", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            assertEquals(0, proc.getChildren(album, 0, 0).size());
        }

        @Test
        void testGetChildSizeOf() {
            assertEquals(0, proc.getChildSizeOf(null));
            verify(mediaFileProvider, times(1))
                .countChildren(nullable(MediaFile.class), any(new MediaFile.Type[0].getClass()));
        }

        @Test
        void testAddChild() {
            DIDLContent parent = new DIDLContent();
            assertEquals(0, parent.getItems().size());
            assertEquals(0, parent.getContainers().size());

            MediaFile song = new MediaFile(1, "/file", 0, "format", "MUSIC", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            proc.addChild(parent, song);
            assertEquals(1, parent.getItems().size());
            assertEquals(0, parent.getContainers().size());

            MediaFile album = new MediaFile(1, "/file", 0, "format", "ALBUM", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            proc.addChild(parent, album);
            assertEquals(1, parent.getItems().size());
            assertEquals(1, parent.getContainers().size());
        }
    }

    @SpringBootTest
    @Nested
    class IntegrationTest extends AbstractNeedsScan {

        private List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders;

        @Autowired
        private MediaFileProvider mediaFileProvider;
        @Autowired
        private UPnPDIDLFactory factory;
        @Autowired
        private MediaFileByFolderProc mediaFileByFolderProc;

        @Override
        public List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> getMusicFolders() {
            return musicFolders;
        }

        @BeforeEach
        void setup() throws URISyntaxException {
            String path = Path
                .of(MediaFileByFolderProcTest.class
                    .getResource("/MEDIAS/Sort/Pagination/Artists")
                    .toURI())
                .toString();
            musicFolders = Arrays
                .asList(new com.tesshu.jpsonic.persistence.api.entity.MusicFolder(1, path,
                        "Artists", true, Instant.now(), 1, false));

            MusicFolderProvider musicFolderProvider = mock(MusicFolderProvider.class);
            MusicFolder folder = new MusicFolder(1, path, "Artists", true, Instant.now(), 1, false);
            when(musicFolderProvider.getGuestFolders()).thenReturn(List.of(folder));
            mediaFileByFolderProc = new MediaFileByFolderProc(musicFolderProvider,
                    mediaFileProvider, settingsFacade, factory);

            settingsFacade.staging(SKeys.general.sort.albumsByYear, false);
            settingsFacade.staging(UPnPSKeys.basic.baseLanUrl, "https://192.168.1.1:4040");
            settingsFacade.commitAll();
            populateDatabaseOnlyOnce();
        }

        @Test
        void testDirectChildren() {

            List<MediaFile> items = mediaFileByFolderProc.getDirectChildren(0, 10);
            assertEquals(10, items.size());

            items = mediaFileByFolderProc.getDirectChildren(10, 10);
            assertEquals(10, items.size());

            items = mediaFileByFolderProc.getDirectChildren(20, 100);
            assertEquals(33, items.size());

            items = mediaFileByFolderProc
                .getDirectChildren(0, 100)
                .stream()
                .filter(a -> !a.name().startsWith("single"))
                .collect(Collectors.toList());
            assertTrue(UPnPProcessorTestUtils
                .validateJPSonicNaturalList(
                        items.stream().map(MediaFile::name).collect(Collectors.toList())));
        }

        @Test
        void testDirectChildrenCount() {
            // 31 + 22(topnodes)
            assertEquals(53, mediaFileByFolderProc.getDirectChildrenCount());
        }

        @Test
        void testgetChildren() {

            List<MediaFile> artists = mediaFileByFolderProc
                .getDirectChildren(0, 100)
                .stream()
                .filter(a -> "10".equals(a.name()))
                .collect(Collectors.toList());
            assertEquals(1, artists.size());
            assertEquals("10", artists.get(0).name());

            List<MediaFile> children = mediaFileByFolderProc.getChildren(artists.get(0), 0, 10);
            for (int i = 0; i < children.size(); i++) {
                assertEquals(UPnPProcessorTestUtils.JPSONIC_NATURAL_LIST.get(i),
                        children.get(i).name());
            }

            children = mediaFileByFolderProc.getChildren(artists.get(0), 10, 10);
            for (int i = 0; i < children.size(); i++) {
                assertEquals(UPnPProcessorTestUtils.JPSONIC_NATURAL_LIST.get(i + 10),
                        children.get(i).name());
            }

            children = mediaFileByFolderProc.getChildren(artists.get(0), 20, 100);
            assertEquals(11, children.size());
            for (int i = 0; i < children.size(); i++) {
                assertEquals(UPnPProcessorTestUtils.JPSONIC_NATURAL_LIST.get(i + 20),
                        children.get(i).name());
            }
        }

        @Test
        void testGetChildSizeOf() {
            List<MediaFile> artists = mediaFileByFolderProc
                .getDirectChildren(0, 100)
                .stream()
                .filter(a -> "10".equals(a.name()))
                .collect(Collectors.toList());
            assertEquals(1, artists.size());
            assertEquals("10", artists.get(0).name());
            assertEquals(31, mediaFileByFolderProc.getChildSizeOf(artists.get(0)));
        }

        @Test
        void testAlbumByName() {

            settingsFacade.commit(SKeys.general.sort.albumsByYear, false);

            List<MediaFile> artists = mediaFileByFolderProc
                .getDirectChildren(0, 100)
                .stream()
                .filter(a -> "10".equals(a.name()))
                .collect(Collectors.toList());
            assertEquals(1, artists.size());
            assertEquals("10", artists.get(0).name());

            MediaFile artist = artists.get(0);

            List<MediaFile> albums = mediaFileByFolderProc
                .getChildren(artist, 0, Integer.MAX_VALUE);
            assertEquals(31, albums.size());
            assertTrue(UPnPProcessorTestUtils
                .validateJPSonicNaturalList(
                        albums.stream().map(MediaFile::name).collect(Collectors.toList())));

        }

        @Test
        void testAlbumByYear() {

            // The result change depending on the setting
            settingsFacade.commit(SKeys.general.sort.albumsByYear, true);
            List<String> reversedByYear = new ArrayList<>(
                    UPnPProcessorTestUtils.JPSONIC_NATURAL_LIST);
            Collections.reverse(reversedByYear);

            List<MediaFile> artists = mediaFileByFolderProc
                .getDirectChildren(0, 100)
                .stream()
                .filter(a -> "10".equals(a.name()))
                .collect(Collectors.toList());
            assertEquals(1, artists.size());
            assertEquals("10", artists.get(0).name());

            MediaFile artist = artists.get(0);

            List<MediaFile> albums = mediaFileByFolderProc
                .getChildren(artist, 0, Integer.MAX_VALUE);
            assertEquals(31, albums.size());
            assertEquals(reversedByYear,
                    albums.stream().map(MediaFile::name).collect(Collectors.toList()));

        }

        @Test
        void testSongs() {

            settingsFacade.commit(SKeys.general.sort.albumsByYear, false);

            List<MediaFile> artists = mediaFileByFolderProc
                .getDirectChildren(0, 100)
                .stream()
                .filter(a -> "20".equals(a.name()))
                .collect(Collectors.toList());
            assertEquals(1, artists.size());

            MediaFile artist = artists.get(0);
            assertEquals("20", artist.name());

            List<MediaFile> albums = mediaFileByFolderProc
                .getChildren(artist, 0, Integer.MAX_VALUE);
            assertEquals(1, albums.size());

            MediaFile album = albums.get(0);
            assertEquals("ALBUM", album.name()); // the case where album name is different
                                                 // between file and id3

            List<MediaFile> songs = mediaFileByFolderProc.getChildren(album, 0, Integer.MAX_VALUE);
            assertEquals(1, songs.size());

            MediaFile song = songs.get(0);
            assertEquals("empty", song.name());
        }
    }
}

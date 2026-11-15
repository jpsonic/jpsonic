/*
z * This file is part of Jpsonic.
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
 * (C) 2023 tesshucom
 */

package com.tesshu.jpsonic.feature.upnp.content.processor;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.adapter.resource.ProviderFactory;
import com.tesshu.jpsonic.domain.model.Album;
import com.tesshu.jpsonic.domain.model.MediaFile;
import com.tesshu.jpsonic.domain.model.Player;
import com.tesshu.jpsonic.domain.model.Playlist;
import com.tesshu.jpsonic.domain.policy.RuntimeOrderPolicy;
import com.tesshu.jpsonic.domain.provider.resource.AlbumProvider;
import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.domain.provider.resource.PlayerProvider;
import com.tesshu.jpsonic.domain.provider.resource.PlaylistProvider;
import com.tesshu.jpsonic.feature.crypt.upnp.UpnpPayloadCodec;
import com.tesshu.jpsonic.feature.transcoding.ResolvedAudioTranscodingParameters;
import com.tesshu.jpsonic.feature.transcoding.TranscodingParametersPlanner;
import com.tesshu.jpsonic.feature.upnp.UPnPSKeys;
import com.tesshu.jpsonic.feature.upnp.content.UPnPDIDLFactory;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.comparator.ComparatorsFacade;
import com.tesshu.jpsonic.infrastructure.language.MetadataReadingProcessor;
import com.tesshu.jpsonic.infrastructure.language.JapaneseReadingUtils;
import com.tesshu.jpsonic.infrastructure.locale.ServerLocaleManager;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.api.repository.PlaylistDao;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.PlaylistContainer;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals" })
class PlaylistProcTest {

    @Nested
    class UnitTest {

        private PlaylistDao playlistDao;
        private MediaFileDao mediaFileDao;
        private SettingsFacade settingsFacade;
        private MusicFolderProvider musicFolderProvider;
        private PlaylistProvider playlistProvider;
        private PlaylistProc proc;

        @BeforeEach
        void setup() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(UPnPSKeys.basic.baseLanUrl, "https://192.168.1.1:4040")
                .build();
            init();
        }

        @Ignore
        void init() {
            TranscodingParametersPlanner parametersPlanner = mock(
                    TranscodingParametersPlanner.class);
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFile.format()).thenReturn(new MediaFile.Format("mp3"));
            ResolvedAudioTranscodingParameters param = new ResolvedAudioTranscodingParameters(false,
                    mediaFile, null, null);
            assertNotNull(param.outputFormat());
            when(parametersPlanner
                .resolveAudioTranscodingParameters(nullable(Player.class),
                        nullable(MediaFile.class), nullable(Integer.class), nullable(String.class)))
                .thenReturn(param);

            ServerLocaleManager serverLocaleManager = new ServerLocaleManager(settingsFacade);
            JapaneseReadingUtils japaneseReadingUtils = new JapaneseReadingUtils(settingsFacade);
            MetadataReadingProcessor processor = new MetadataReadingProcessor(settingsFacade,
                    japaneseReadingUtils);
            ComparatorsFacade comparatorsFacade = new ComparatorsFacade(settingsFacade, serverLocaleManager, processor);
            mediaFileDao = mock(MediaFileDao.class);
            playlistDao = mock(PlaylistDao.class);
            playlistProvider = ProviderFactory
                .createPlaylistProviderAdapter(playlistDao, mediaFileDao, comparatorsFacade, processor);
            musicFolderProvider = mock(MusicFolderProvider.class);

            MediaFileProvider mediaFileProvider = mock(MediaFileProvider.class);
            PlayerProvider playerProvider = mock(PlayerProvider.class);
            UpnpPayloadCodec upnpPayloadCodec = mock(UpnpPayloadCodec.class);
            UPnPDIDLFactory factory = new UPnPDIDLFactory(settingsFacade, upnpPayloadCodec,
                    mediaFileProvider, playerProvider, parametersPlanner);
            proc = new PlaylistProc(settingsFacade, musicFolderProvider, playlistProvider, factory);
        }

        @Test
        void testGetProcId() {
            assertEquals("playlist", proc.getProcId().getValue());
        }

        @Test
        void testGetDirectChildren() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(UPnPSKeys.basic.baseLanUrl, "https://192.168.1.1:4040")
                .withBoolean(UPnPSKeys.options.guestPublish, true)
                .build();
            init();

            Playlist playlist1 = new Playlist(0, "name", "comment", 0);
            Playlist playlist2 = new Playlist(1, "name", "comment", 0);
            Playlist playlist3 = new Playlist(2, "name", "comment", 0);
            Playlist playlist4 = new Playlist(3, "name", "comment", 0);
            when(playlistDao.findPublishedPlaylists(anyLong(), anyLong()))
                .thenReturn(List.of(playlist1, playlist2, playlist3, playlist4));

            assertEquals(1, proc.getDirectChildren(0, 1).size());
            assertEquals(0, proc.getDirectChildren(0, 1).get(0).id());
            assertEquals(3, proc.getDirectChildren(1, 3).size());
            assertEquals(1, proc.getDirectChildren(1, 3).get(0).id());
            assertEquals(2, proc.getDirectChildren(1, 3).get(1).id());
            assertEquals(3, proc.getDirectChildren(1, 3).get(2).id());
            assertEquals(4, proc.getDirectChildren(0, 100).size());

            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(UPnPSKeys.basic.baseLanUrl, "https://192.168.1.1:4040")
                .withBoolean(UPnPSKeys.options.guestPublish, true)
                .build();
            init();
            assertEquals(0, proc.getDirectChildren(0, 100).size());
        }

        @Test
        void testGetDirectChildrenCount() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(UPnPSKeys.basic.baseLanUrl, "https://192.168.1.1:4040")
                .withBoolean(UPnPSKeys.options.guestPublish, true)
                .build();
            init();

            assertEquals(0, proc.getDirectChildrenCount());
            verify(playlistDao, times(1)).countPublishedPlaylists();
            verify(playlistDao, never()).countPlaylists();

            clearInvocations(playlistDao);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(UPnPSKeys.basic.baseLanUrl, "https://192.168.1.1:4040")
                .withBoolean(UPnPSKeys.options.guestPublish, false)
                .build();
            init();

            assertEquals(0, proc.getDirectChildrenCount());
            verify(playlistDao, never()).countPublishedPlaylists();
            verify(playlistDao, times(1)).countPlaylists();
        }

        @Test
        void testGetDirectChild() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> proc.getDirectChild("0"));

            clearInvocations(playlistDao);
            Playlist playlist1 = new Playlist(0, "name", "comment", 0);
            when(playlistDao.getDomainPlaylist(anyInt())).thenReturn(playlist1);
            assertNotNull(proc.getDirectChild("0"));
            verify(playlistDao, times(1)).getDomainPlaylist(anyInt());
        }

        @Test
        void testGetChildren() {
            Playlist playlist = new Playlist(0, null, null, 0);
            assertEquals(0, proc.getChildren(playlist, 0, 0).size());
            verify(mediaFileDao, times(1))
                .findChildren(anyList(), any(Playlist.class), anyLong(), anyLong(),
                        any(MediaFile.Type[].class));
        }

        @Test
        void testGetChildSizeOf() {
            Playlist playlist = new Playlist(0, "name", "comment", 99);
            assertEquals(99, proc.getChildSizeOf(playlist));
        }

        @Test
        void testAddChild() {
            DIDLContent content = new DIDLContent();
            assertEquals(0, content.getContainers().size());
            MediaFile song = new MediaFile(1, "pathString", 0, "format", "MUSIC", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            UPnPDIDLFactory factory = mock(UPnPDIDLFactory.class);
            proc = new PlaylistProc(settingsFacade, musicFolderProvider, playlistProvider, factory);
            proc.addChild(content, song);
            verify(factory, times(1)).toMusicTrack(any(MediaFile.class));
            assertEquals(1, content.getCount());
            assertEquals(1, content.getItems().size());
            assertEquals(0, content.getContainers().size());
        }
    }

    @Nested
    class IntegrationTest extends AbstractNeedsScan {

        private static final List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> MUSIC_FOLDERS = Arrays
            .asList(new com.tesshu.jpsonic.persistence.api.entity.MusicFolder(1,
                    resolveBaseMediaPath("Sort/Pagination/Artists"), "Artists", true, Instant.now(),
                    1, false));

        @Autowired
        private PlaylistProc playlistProc;

        @Autowired
        private MediaFileProvider mediaFileProvider;

        @Autowired
        private PlaylistDao playlistDao;

        @Autowired
        private SettingsFacade settingsFacade;

        @Autowired
        private MusicFolderProvider musicFolderProvider;

        @Autowired
        private AlbumProvider albumProvider;
        @Autowired
        private PlaylistProvider playlistProvider;

        @Override
        public List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> getMusicFolders() {
            return MUSIC_FOLDERS;
        }

        @BeforeEach
        void setup() {
            settingsFacade.staging(SKeys.general.sort.albumsByYear, false);
            settingsFacade.staging(UPnPSKeys.options.guestPublish, false);
            settingsFacade.staging(UPnPSKeys.basic.baseLanUrl, "https://192.168.1.1:4040");
            settingsFacade.commitAll();
            populateDatabaseOnlyOnce();

            if (!playlistDao.getAllPlaylists().isEmpty()) {
                return;
            }

            List<String> playlestNames = List
                .of("10", "20", "30", "40", "50", "60", "70", "80", "90", "98", "99", "abcde",
                        "ＢＣＤＥＡ", "ĆḊÉÁḂ", "DEABC", "eabcd", "亜伊鵜絵尾", "αβγ", "いうえおあ", "ゥェォァィ",
                        "ｴｵｱｲｳ", "ｪｫｧｨｩ", "ぉぁぃぅぇ", "オアイウエ", "春夏秋冬", "貼られる", "パラレル", "馬力", "張り切る",
                        "はるなつあきふゆ", "♂くんつ");

            List<String> shuffled = new ArrayList<>(playlestNames);
            Collections.shuffle(shuffled);

            AtomicInteger c = new AtomicInteger(0);
            shuffled.stream().map((title) -> {
                Instant now = Instant.now();
                com.tesshu.jpsonic.persistence.api.entity.Playlist playlist = new com.tesshu.jpsonic.persistence.api.entity.Playlist();
                playlist.setName(title);
                playlist.setUsername("admin");
                playlist.setCreated(now);
                playlist.setChanged(now);
                playlist.setShared(c.getAndIncrement() < 10);
                return playlist;
            }).forEach(playlistDao::createPlaylist);
            assertEquals(31, playlistDao.getCountAll());

            List<Album> albums = albumProvider
                .findAlbums(musicFolderProvider.getGuestFolders(),
                        RuntimeOrderPolicy.AlbumSortOrder.DEFAULT, 0, Integer.MAX_VALUE);
            assertEquals(61, albums.size());

            List<MediaFile> files = albums
                .stream()
                .flatMap(album -> mediaFileProvider
                    .findChildren(musicFolderProvider.getGuestFolders(), album, 0L,
                            Integer.MAX_VALUE)
                    .stream())
                .toList();
            assertEquals(61, files.size());
            playlistDao
                .setDomainFilesInPlaylist(playlistProvider.findPublishedPlaylists(0, 1).get(0).id(),
                        files);
        }

        @Test
        void testCreateContainer() {
            Playlist playlist = new Playlist(0, "testPlaylist", "comment", 0);
            Container container = playlistProc.createContainer(playlist);
            assertInstanceOf(PlaylistContainer.class, container);
            assertEquals("playlist/0", container.getId());
            assertEquals("playlist", container.getParentID());
            assertEquals("testPlaylist", container.getTitle());
            assertEquals(0, container.getChildCount());
        }

        @Test
        void testGetDirectChildrenCount() {
            settingsFacade.commit(UPnPSKeys.options.guestPublish, false);
            assertEquals(31, playlistProc.getDirectChildrenCount());
            settingsFacade.commit(UPnPSKeys.options.guestPublish, true);
            assertEquals(10, playlistProc.getDirectChildrenCount());
        }

        @Test
        void testGetDirectChildren() {
            settingsFacade.commit(UPnPSKeys.options.guestPublish, false);

            Map<String, Playlist> c = LegacyMap.of();

            List<Playlist> items = playlistProc.getDirectChildren(0, 10);
            items.stream().filter(g -> !c.containsKey(g.name())).forEach(g -> c.put(g.name(), g));
            assertEquals(10, items.size());
            assertEquals(10, c.size());

            items = playlistProc.getDirectChildren(10, 10);
            items.stream().filter(g -> !c.containsKey(g.name())).forEach(g -> c.put(g.name(), g));
            assertEquals(10, items.size());
            assertEquals(20, c.size());

            items = playlistProc.getDirectChildren(20, 100);
            assertEquals(11, items.size());
            items.stream().filter(g -> !c.containsKey(g.name())).forEach(g -> c.put(g.name(), g));
            assertEquals(31, c.size());
        }

        @Test
        void testGetChildren() {
            settingsFacade.commit(UPnPSKeys.options.guestPublish, true);

            List<Playlist> playlists = playlistProc.getDirectChildren(0, 1);
            assertEquals(1, playlists.size());
            assertEquals(61, playlistProc.getChildSizeOf(playlists.get(0)));

            Map<Integer, MediaFile> c = LegacyMap.of();

            List<MediaFile> children = playlistProc.getChildren(playlists.get(0), 0, 20);
            children.stream().filter(m -> !c.containsKey(m.id())).forEach(m -> c.put(m.id(), m));
            assertEquals(20, children.size());

            children = playlistProc.getChildren(playlists.get(0), 20, 20);
            children.stream().filter(m -> !c.containsKey(m.id())).forEach(m -> c.put(m.id(), m));
            assertEquals(40, c.size());

            children = playlistProc.getChildren(playlists.get(0), 40, 100);
            assertEquals(21, children.size());
            children.stream().filter(m -> !c.containsKey(m.id())).forEach(m -> c.put(m.id(), m));
            assertEquals(61, c.size());
        }

        @Test
        void testGetChildSizeOf() {
            settingsFacade.commit(UPnPSKeys.options.guestPublish, true);

            List<Playlist> playlists = playlistProc.getDirectChildren(0, 1);
            assertEquals(1, playlists.size());
            assertEquals(61, playlistProc.getChildSizeOf(playlists.get(0)));
        }
    }
}

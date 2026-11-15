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

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.Artist;
import com.tesshu.jpsonic.domain.model.MediaFile;
import com.tesshu.jpsonic.domain.model.Player;
import com.tesshu.jpsonic.domain.provider.resource.ArtistProvider;
import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.domain.provider.resource.PlayerProvider;
import com.tesshu.jpsonic.feature.crypt.upnp.UpnpPayloadCodec;
import com.tesshu.jpsonic.feature.transcoding.ResolvedAudioTranscodingParameters;
import com.tesshu.jpsonic.feature.transcoding.TranscodingParametersPlanner;
import com.tesshu.jpsonic.feature.upnp.UPnPSKeys;
import com.tesshu.jpsonic.feature.upnp.content.UPnPDIDLFactory;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.MusicArtist;
import org.jupnp.support.model.item.MusicTrack;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("PMD.TooManyStaticImports")
class RandomSongByArtistProcTest {

    @Nested
    @SuppressWarnings("PMD.SingularField") // pmd/pmd#4616
    class UnitTest {

        private UPnPDIDLFactory factory;
        private MediaFileProvider mediaFileProvider;
        private SettingsFacade settingsFacade;
        private ArtistProvider artistProvider;

        private RandomSongByArtistProc proc;

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
            when(parametersPlanner
                .resolveAudioTranscodingParameters(nullable(Player.class),
                        nullable(MediaFile.class), nullable(Integer.class), nullable(String.class)))
                .thenReturn(param);

            mediaFileProvider = mock(MediaFileProvider.class);
            PlayerProvider playerProvider = mock(PlayerProvider.class);
            UpnpPayloadCodec upnpPayloadCodec = mock(UpnpPayloadCodec.class);
            factory = new UPnPDIDLFactory(settingsFacade, upnpPayloadCodec, mediaFileProvider,
                    playerProvider, parametersPlanner);

            mediaFileProvider = mock(MediaFileProvider.class);
            MusicFolderProvider musicFolderProvider = mock(MusicFolderProvider.class);
            artistProvider = mock(ArtistProvider.class);
            proc = new RandomSongByArtistProc(musicFolderProvider, mediaFileProvider,
                    artistProvider, settingsFacade, factory);
        }

        @Test
        void testGetProcId() {
            assertEquals("rsbar", proc.getProcId().getValue());
        }

        @Test
        void testCreateContainer() {
            Artist artist = new Artist(0, "artistName", "path", 50, 0, "reading", 0, "#");
            Container container = proc.createContainer(artist);
            assertInstanceOf(MusicArtist.class, container);
            assertEquals("rsbar/0", container.getId());
            assertEquals("rsbar", container.getParentID());
            assertEquals("artistName", container.getTitle());
            assertEquals(50, container.getChildCount());
        }

        @Test
        void testGetDirectChildren() {
            proc.getDirectChildren(0, 0);
            verify(artistProvider, times(1)).findArtists(anyList(), anyLong(), anyLong());
        }

        @Test
        void testGetDirectChildrenCount() {
            assertEquals(0, proc.getDirectChildrenCount());
            verify(artistProvider, times(1)).countArtists(anyList());
        }

        @Test
        void testGetDirectChild() {
            assertNull(proc.getDirectChild("0"));
            verify(artistProvider, times(1)).requireArtist(anyInt());
        }

        @Test
        void testGetChildren() {
            Artist artist = new Artist(0, "artistName", "path", 50, 0, "reading", 0, "#");
            assertEquals(0, proc.getChildren(artist, 0, 0).size());
            verify(mediaFileProvider, times(1))
                .findRandomSongsByArtist(anyList(), any(Artist.class), anyLong(), anyLong(),
                        anyInt());
        }

        @Test
        void testGetChildSizeOf() {
            AtomicInteger randomMaxCount = new AtomicInteger();
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withIntAnswer(UPnPSKeys.options.randomMax, invocation -> {
                    randomMaxCount.incrementAndGet();
                    return 0;
                })
                .build();
            init();

            proc.getChildSizeOf(null);
            assertEquals(1, randomMaxCount.get());
        }

        @Test
        void testAddChild() {
            DIDLContent content = new DIDLContent();
            assertEquals(0, content.getContainers().size());
            MediaFile song = new MediaFile(1, "pathString", null, "format", "MUSIC", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            factory = mock(UPnPDIDLFactory.class);
            when(factory.toMusicTrack(song)).thenReturn(new MusicTrack());

            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(UPnPSKeys.basic.baseLanUrl, "https://192.168.1.1:4040")
                .build();
            init();

            proc.addChild(content, song);
            assertEquals(1, content.getItems().size());
        }
    }

    /*
     * Test to correct sort inconsistencies.
     */
    @Nested
    class IntegrationTest extends AbstractNeedsScan {

        private static final List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> MUSIC_FOLDERS = Arrays
            .asList(new com.tesshu.jpsonic.persistence.api.entity.MusicFolder(1,
                    resolveBaseMediaPath("Sort/Pagination/Artists"), "Artists", true, Instant.now(),
                    1, false));

        @Autowired
        private RandomSongByArtistProc randomSongByArtistProc;
        @Autowired
        private SettingsFacade settingsFacade;

        @Override
        public List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> getMusicFolders() {
            return MUSIC_FOLDERS;
        }

        @BeforeEach
        void setup() {
            settingsFacade.staging(UPnPSKeys.basic.baseLanUrl, "https://192.168.1.1:4040");
            settingsFacade.staging(SKeys.general.sort.albumsByYear, false);
            settingsFacade.commitAll();
            populateDatabaseOnlyOnce();
        }

        @Test
        void testGetDirectChildren() {

            Map<String, Artist> c = LegacyMap.of();

            List<Artist> items = randomSongByArtistProc.getDirectChildren(0, 10);
            items.stream().filter(g -> !c.containsKey(g.name())).forEach(g -> c.put(g.name(), g));
            assertEquals(10, c.size());

            items = randomSongByArtistProc.getDirectChildren(10, 10);
            items.stream().filter(g -> !c.containsKey(g.name())).forEach(g -> c.put(g.name(), g));
            assertEquals(20, c.size());

            items = randomSongByArtistProc.getDirectChildren(20, 100);
            assertEquals(11, items.size());
            items.stream().filter(g -> !c.containsKey(g.name())).forEach(g -> c.put(g.name(), g));
            assertEquals(31, c.size());
        }

        @Test
        void testGetDirectChildrenCount() {
            assertEquals(31, randomSongByArtistProc.getDirectChildrenCount());
        }

        @Test
        void testGetChildren() {
            List<Artist> artists = randomSongByArtistProc.getDirectChildren(0, 1);
            assertEquals(1, artists.size());

            Map<String, MediaFile> c = LegacyMap.of();
            List<MediaFile> children = randomSongByArtistProc.getChildren(artists.get(0), 0, 10);
            children
                .stream()
                .filter(m -> !c.containsKey(m.artist()))
                .forEach(m -> c.put(m.artist(), m));
            assertEquals(1, c.size());
        }

        @Test
        void testGetChildSizeOf() {
            List<Artist> artists = randomSongByArtistProc.getDirectChildren(0, 1);
            assertEquals(1, artists.size());
            assertEquals(50, randomSongByArtistProc.getChildSizeOf(artists.get(0)));
        }
    }
}

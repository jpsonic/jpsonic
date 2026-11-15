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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.Artist;
import com.tesshu.jpsonic.domain.model.MediaFile;
import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.model.Player;
import com.tesshu.jpsonic.domain.model.TranscodingDefinition.BitRateLimit;
import com.tesshu.jpsonic.domain.model.UserSettings;
import com.tesshu.jpsonic.domain.provider.resource.ArtistProvider;
import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.domain.provider.resource.PlayerProvider;
import com.tesshu.jpsonic.domain.provider.resource.TranscodingProvider;
import com.tesshu.jpsonic.domain.provider.resource.UserProvider;
import com.tesshu.jpsonic.feature.crypt.upnp.UpnpPayloadCodec;
import com.tesshu.jpsonic.feature.transcoding.TranscodingParametersPlanner;
import com.tesshu.jpsonic.feature.upnp.UPnPSKeys;
import com.tesshu.jpsonic.feature.upnp.content.UPnPDIDLFactory;
import com.tesshu.jpsonic.feature.upnp.content.processor.composite.FArtistOrSong;
import com.tesshu.jpsonic.feature.upnp.content.processor.composite.FolderArtist;
import com.tesshu.jpsonic.feature.upnp.content.processor.composite.FolderOrFArtist;
import com.tesshu.jpsonic.feature.upnp.content.processor.logic.FolderOrArtistLogic;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.infrastructure.core.NeedsHome;
import com.tesshu.jpsonic.infrastructure.scanner.MusicFolderServiceImpl;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jupnp.support.model.DIDLContent;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

@NeedsHome
@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals",
        "PMD.AvoidUsingHardCodedIP" })
class RandomSongByFolderArtistProcTest {

    @Nested
    class UnitTest {

        private MediaFileProvider mediaFileProvider;
        private SettingsFacade settingsFacade;
        private FolderOrArtistLogic folderOrArtistProc;
        private ArtistProvider artistProvider;
        private RandomSongByFolderArtistProc proc;

        @BeforeEach
        void setup() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(UPnPSKeys.basic.baseLanUrl, "https://192.168.1.1:4040")
                .buildWithDefault();
            mediaFileProvider = mock(MediaFileProvider.class);
            PlayerProvider playerProvider = mock(PlayerProvider.class);
            UpnpPayloadCodec upnpPayloadCodec = mock(UpnpPayloadCodec.class);
            TranscodingParametersPlanner transcodingParametersPlanner = mock(
                    TranscodingParametersPlanner.class);
            UPnPDIDLFactory factory = new UPnPDIDLFactory(settingsFacade, upnpPayloadCodec,
                    mediaFileProvider, playerProvider, transcodingParametersPlanner);

            MusicFolderProvider musicFolderProvider = mock(MusicFolderProvider.class);
            artistProvider = mock(ArtistProvider.class);
            folderOrArtistProc = new FolderOrArtistLogic(musicFolderProvider, artistProvider,
                    factory);

            proc = new RandomSongByFolderArtistProc(musicFolderProvider, mediaFileProvider,
                    artistProvider, folderOrArtistProc, settingsFacade, factory);
        }

        @Test
        void testGetProcId() {
            assertEquals("rsbfar", proc.getProcId().getValue());
        }

        @Test
        void testGetChildrenWithArtist() {
            int id = 99;
            Artist artist = new Artist(id, "artist", "path", 50, 0, "reading", 0, "#");
            when(artistProvider.requireArtist(id)).thenReturn(artist);
            MusicFolder folder = new MusicFolder(99, "/Music", "Music", false, null, 0, false);
            FolderOrFArtist folderOrArtist = new FolderOrFArtist(new FolderArtist(folder, artist));

            assertEquals(0, proc.getChildren(folderOrArtist, 0, 2).size());
            Mockito
                .verify(mediaFileProvider, Mockito.times(1))
                .findRandomSongsByArtist(anyList(), any(Artist.class), anyLong(), anyLong(),
                        anyInt());
        }

        @Test
        void testGetChildrenWithFolder() {
            Artist artist = new Artist(0, "artist", "path", 50, 0, "reading", 0, "#");
            MusicFolder folder = new MusicFolder(0, "/folder1", "folder1", false, Instant.now(), 0,
                    false);
            when(artistProvider.findArtists(anyList(), anyLong(), anyLong()))
                .thenReturn(List.of(artist));
            FolderOrFArtist folderOrArtist = new FolderOrFArtist(folder);
            assertEquals(1, proc.getChildren(folderOrArtist, 0, 2).size());
            Mockito
                .verify(artistProvider, Mockito.times(1))
                .findArtists(anyList(), anyLong(), anyLong());
        }

        @Test
        void testAddChild() {
            MusicFolder folder = new MusicFolder(99, "/Music", "Music", false, null, 0, false);
            Artist artist = new Artist(0, "artist", "path", 3, 0, "reading", 0, "#");
            FArtistOrSong artistOrSong = new FArtistOrSong(new FolderArtist(folder, artist));

            DIDLContent content = new DIDLContent();
            assertEquals(0, content.getContainers().size());
            proc.addChild(content, artistOrSong);
            assertEquals(1, content.getContainers().size());

            content = new DIDLContent();
            MediaFile song = new MediaFile(1, "/path2", null, "format", "DIRECTORY", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            artistOrSong = new FArtistOrSong(song);

            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFile.format()).thenReturn(new MediaFile.Format("mp3"));

            PlayerProvider playerProvider = mock(PlayerProvider.class);
            Player player = new Player(0, "guest", BitRateLimit.OFF, "127.0.0.0", Instant.now());
            when(playerProvider.getUPnPPlayer()).thenReturn(player);

            UserSettings settings = new UserSettings("guest", BitRateLimit.MAX_128);
            UserProvider userProvider = mock(UserProvider.class);
            when(userProvider.getUserSettings(anyString())).thenReturn(settings);

            TranscodingProvider transcodingProvider = mock(TranscodingProvider.class);
            TranscodingParametersPlanner parametersPlanner = new TranscodingParametersPlanner(
                    settingsFacade, userProvider, transcodingProvider);
            UpnpPayloadCodec upnpPayloadCodec = mock(UpnpPayloadCodec.class);
            MediaFileProvider mediaFileProvider = mock(MediaFileProvider.class);
            UPnPDIDLFactory factory = new UPnPDIDLFactory(settingsFacade, upnpPayloadCodec,
                    mediaFileProvider, playerProvider, parametersPlanner);
            MusicFolderProvider musicFolderProvider = mock(MusicFolderProvider.class);
            proc = new RandomSongByFolderArtistProc(musicFolderProvider, mediaFileProvider,
                    artistProvider, folderOrArtistProc, settingsFacade, factory);

            assertEquals(0, content.getItems().size());
            proc.addChild(content, artistOrSong);
            assertEquals(1, content.getItems().size());
        }
    }

    @Nested
    class IntegrationTest extends AbstractNeedsScan {

        private static final List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> MUSIC_FOLDERS = Arrays
            .asList(new com.tesshu.jpsonic.persistence.api.entity.MusicFolder(1,
                    resolveBaseMediaPath("Sort/Pagination/Artists"), "Artists", true, Instant.now(),
                    1, false));

        @Autowired
        private MusicFolderServiceImpl musicFolderService;

        @Autowired
        private RandomSongByFolderArtistProc proc;

        @Autowired
        private SettingsFacade settingsFacade;

        @Override
        public List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> getMusicFolders() {
            return MUSIC_FOLDERS;
        }

        @BeforeEach
        void setup() {
            settingsFacade.commit(SKeys.general.sort.albumsByYear, false);
            musicFolderService
                .getAllMusicFolders()
                .stream()
                .filter(mf -> mf
                    .getPathString()
                    .equals(EnvironmentProvider.getInstance().getDefaultMusicFolder()))
                .findFirst()
                .ifPresent(mf -> musicFolderService.deleteMusicFolder(Instant.now(), mf.getId()));
            populateDatabaseOnlyOnce();
        }

        @Test
        void testGetDirectChildren() {
            List<FolderOrFArtist> items = proc.getDirectChildren(0, 10);
            assertEquals(10, items.size());
        }

        @Test
        void testGetDirectChildrenCount() {
            assertEquals(31, proc.getDirectChildrenCount());
        }

        @Test
        void testGetChildren() {
            List<FolderOrFArtist> folderOrArtists = proc.getDirectChildren(0, 10);
            assertEquals(10, folderOrArtists.size());
            assertEquals("10", folderOrArtists.get(0).getFolderArtist().artist().name());
            assertEquals("20", folderOrArtists.get(1).getFolderArtist().artist().name());
            assertEquals("30", folderOrArtists.get(2).getFolderArtist().artist().name());

            List<FArtistOrSong> songs = proc
                .getChildren(new FolderOrFArtist(folderOrArtists.get(0).getFolderArtist()), 0,
                        Integer.MAX_VALUE);
            assertEquals(31, songs.size());
        }

        @Test
        void testGetChildSizeOf() {
            List<FolderOrFArtist> artists = proc.getDirectChildren(0, 1);
            assertEquals(1, artists.size());
            assertEquals(31, proc.getChildSizeOf(artists.get(0)));
        }
    }
}

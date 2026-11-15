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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.Genre;
import com.tesshu.jpsonic.domain.model.MediaFile;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterCriteria;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterProvider;
import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.domain.provider.resource.PlayerProvider;
import com.tesshu.jpsonic.feature.crypt.upnp.UpnpPayloadCodec;
import com.tesshu.jpsonic.feature.transcoding.TranscodingParametersPlanner;
import com.tesshu.jpsonic.feature.upnp.content.UPnPDIDLFactory;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.GenreContainer;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals" })
class SongByGenreProcTest {

    @Nested
    @SuppressWarnings("PMD.SingularField") // pmd/pmd#4616
    class UnitTest {

        private MusicFolderProvider musicFolderProvider;
        private MediaFileProvider mediaFileProvider;
        private GenreMasterProvider genreMasterProvider;
        private SongByGenreProc proc;
        private SettingsFacade settingsFacade;
        private UPnPDIDLFactory factory;

        @BeforeEach
        void setup() {
            settingsFacade = SettingsFacadeBuilder.create().build();
            factory = new UPnPDIDLFactory(settingsFacade, mock(UpnpPayloadCodec.class),
                    mock(MediaFileProvider.class), mock(PlayerProvider.class),
                    mock(TranscodingParametersPlanner.class));
            mediaFileProvider = mock(MediaFileProvider.class);
            genreMasterProvider = mock(GenreMasterProvider.class);
            musicFolderProvider = mock(MusicFolderProvider.class);
            proc = new SongByGenreProc(musicFolderProvider, mediaFileProvider, genreMasterProvider,
                    settingsFacade, factory);
        }

        @Test
        void testGetProcId() {
            assertEquals("sbg", proc.getProcId().getValue());
        }

        @Test
        void testCreateContainer() {
            Genre genre = new Genre("English/Japanese", 50, 100);
            Container container = proc.createContainer(genre);
            assertInstanceOf(GenreContainer.class, container);
            assertEquals("sbg/English/Japanese", container.getId());
            assertEquals("sbg", container.getParentID());
            assertEquals("English/Japanese", container.getTitle());
            assertEquals(50, container.getChildCount());
        }

        @Test
        void testGetDirectChildren() {
            assertEquals(Collections.emptyList(), proc.getDirectChildren(0, 0));
            verify(genreMasterProvider, times(1))
                .getGenres(any(GenreMasterCriteria.class), anyLong(), anyLong());
        }

        @Test
        void testGetDirectChildrenCount() {
            assertEquals(0, proc.getDirectChildrenCount());
            verify(genreMasterProvider, times(1)).getGenresCount(any(GenreMasterCriteria.class));
        }

        @Test
        void testGetDirectChild() {
            Genre genre = new Genre("English/Japanese", 50, 100);
            when(genreMasterProvider
                .getGenres(any(GenreMasterCriteria.class), anyLong(), anyLong()))
                .thenReturn(List.of(genre));
            assertEquals("English/Japanese", proc.getDirectChild("English/Japanese").name());
            assertNull(proc.getDirectChild("None"));
        }

        @Test
        void testGetChildren() {
            Genre genre = new Genre("English/Japanese", 50, 100);
            assertEquals(Collections.emptyList(), proc.getChildren(genre, 0, 0));
            verify(mediaFileProvider, times(1))
                .findSongsByGenres(ArgumentMatchers.anyList(), ArgumentMatchers.anyString(),
                        anyLong(), anyLong(), any(MediaFile.Type[].class));
        }

        @Test
        void testGetChildSizeOf() {
            Genre genre = new Genre("English/Japanese", 50, 100);
            assertEquals(50, proc.getChildSizeOf(genre));
        }

        @Test
        void testAddChild() {
            DIDLContent content = new DIDLContent();
            MediaFile song = new MediaFile(1, "pathString", 0, "format", "MUSIC", 256, 60, 9999,
                    "artist", "album", "title", "albumArtist", 0, "genre", 2026, "thumbUri",
                    "composer", "reading", "#", "comment");
            factory = mock(UPnPDIDLFactory.class);
            proc = new SongByGenreProc(musicFolderProvider, mediaFileProvider, genreMasterProvider,
                    settingsFacade, factory);
            proc.addChild(content, song);
            verify(factory, times(1)).toMusicTrack(any(MediaFile.class));
            assertEquals(1, content.getCount());
            assertEquals(0, content.getContainers().size());
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
        private SongByGenreProc songByGenreProc;

        @Override
        public List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> getMusicFolders() {
            return MUSIC_FOLDERS;
        }

        @BeforeEach
        void setup() {
            settingsFacade.staging(SKeys.general.sort.albumsByYear, false);
            settingsFacade.staging(SKeys.general.sort.genresByAlphabet, false);
            settingsFacade.commitAll();
            populateDatabaseOnlyOnce();
        }

        @Test
        void testGetItemCount() {
            assertEquals(31, songByGenreProc.getDirectChildrenCount());
        }

        @Test
        void testGetDirectChildren() {

            Map<String, Genre> c = LegacyMap.of();

            List<Genre> items = songByGenreProc.getDirectChildren(0, 10);
            items.stream().forEach(s -> System.out.println(s));
            items.stream().filter(g -> !c.containsKey(g.name())).forEach(g -> c.put(g.name(), g));
            assertEquals(10, c.size());

            items = songByGenreProc.getDirectChildren(10, 10);
            items.stream().filter(g -> !c.containsKey(g.name())).forEach(g -> c.put(g.name(), g));
            assertEquals(20, c.size());

            items = songByGenreProc.getDirectChildren(20, 100);
            assertEquals(11, items.size());
            items.stream().filter(g -> !c.containsKey(g.name())).forEach(g -> c.put(g.name(), g));
            assertEquals(31, c.size());

        }

        @Test
        void testGetChildren() {

            List<Genre> genres = songByGenreProc.getDirectChildren(0, 1);
            assertEquals(1, genres.size());
            assertEquals("A", genres.get(0).name());

            Map<String, MediaFile> c = LegacyMap.of();
            assertEquals(31, songByGenreProc.getChildSizeOf(genres.get(0)));

            List<MediaFile> songs = songByGenreProc.getChildren(genres.get(0), 0, 10);
            assertEquals(10, songs.size());

            songs.stream().filter(m -> !c.containsKey(m.genre())).forEach(m -> c.put(m.genre(), m));
            assertEquals(10, c.size());

            songs = songByGenreProc.getChildren(genres.get(0), 10, 10);
            songs.stream().filter(m -> !c.containsKey(m.genre())).forEach(m -> c.put(m.genre(), m));
            assertEquals(20, c.size());

            songs = songByGenreProc.getChildren(genres.get(0), 20, 100);
            assertEquals(11, songs.size());
            songs.stream().filter(m -> !c.containsKey(m.genre())).forEach(m -> c.put(m.genre(), m));
            assertEquals(31, c.size());

        }

        @Test
        void testGetChildSizeOf() {
            List<Genre> artists = songByGenreProc.getDirectChildren(0, 1);
            assertEquals(1, artists.size());
            // assertEquals("A;B;C", artists.get(0).getName());
            assertEquals(31, songByGenreProc.getChildSizeOf(artists.get(0)));
        }
    }
}

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
 * (C) 2024 tesshucom
 */

package com.tesshu.jpsonic.feature.upnp.content.processor;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.GenreContainer;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals" })
class AudiobookByGenreProcTest {

    @Nested
    @SuppressWarnings("PMD.SingularField")
    class UnitTest {
        private MusicFolderProvider musicFolderProvider;
        private MediaFileProvider mediaFileProvider;
        private GenreMasterProvider genreMasterProvider;
        private SettingsFacade settingsFacade;
        private UPnPDIDLFactory factory;
        private AudiobookByGenreProc proc;

        @BeforeEach
        void setup() {
            musicFolderProvider = mock(MusicFolderProvider.class);
            mediaFileProvider = mock(MediaFileProvider.class);
            genreMasterProvider = mock(GenreMasterProvider.class);
            settingsFacade = SettingsFacadeBuilder.create().build();
            factory = new UPnPDIDLFactory(settingsFacade, mock(UpnpPayloadCodec.class),
                    mock(MediaFileProvider.class), mock(PlayerProvider.class),
                    mock(TranscodingParametersPlanner.class));
            proc = new AudiobookByGenreProc(musicFolderProvider, mediaFileProvider,
                    genreMasterProvider, settingsFacade, factory);
        }

        @Test
        void testGetProcId() {
            assertEquals("abbg", proc.getProcId().getValue());
        }

        @Test
        void testCreateContainer() {
            Genre genre = new Genre("English/Japanese", 50, 100);
            Container container = proc.createContainer(genre);
            assertInstanceOf(GenreContainer.class, container);
            assertEquals("abbg/English/Japanese", container.getId());
            assertEquals("abbg", container.getParentID());
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
                .findSongsByGenres(anyList(), anyString(), anyLong(), anyLong(),
                        any(MediaFile.Type[].class));
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
            SettingsFacade settingsFacade = SettingsFacadeBuilder.create().build();
            proc = new AudiobookByGenreProc(musicFolderProvider, mediaFileProvider,
                    genreMasterProvider, settingsFacade, factory);
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
                    resolveBaseMediaPath("MultiGenre"), "MultiGenre", true, Instant.now(), 1,
                    false));

        @Autowired
        private AudiobookByGenreProc audiobookByGenreProc;

        @Override
        public List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> getMusicFolders() {
            return MUSIC_FOLDERS;
        }

        @BeforeEach
        void setup() {
            populateDatabaseOnlyOnce();
        }

        @Test
        void testGetItemCount() {
            assertEquals(2, audiobookByGenreProc.getDirectChildrenCount());
        }

        @Test
        void testGetDirectChildren() {
            Map<String, Genre> c = LegacyMap.of();
            List<Genre> items = audiobookByGenreProc.getDirectChildren(0, 10);
            items.stream().filter(g -> !c.containsKey(g.name())).forEach(g -> c.put(g.name(), g));
            assertEquals(2, c.size());
        }

        @Test
        void testGetChildren() {
            List<Genre> genre = audiobookByGenreProc.getDirectChildren(0, 1);
            assertEquals(1, genre.size());
            assertEquals("Audiobook - Historical", genre.get(0).name());

            List<MediaFile> children = audiobookByGenreProc.getChildren(genre.get(0), 0, 10);
            assertEquals(1, children.size());
            assertEquals("FILE14", children.get(0).name());
            assertEquals("Audiobook - Historical", children.get(0).genre());
        }

        @Test
        void testGetChildSizeOf() {
            List<Genre> genre = audiobookByGenreProc.getDirectChildren(0, 1);
            assertEquals(1, genre.size());
            assertEquals("Audiobook - Historical", genre.get(0).name());

            assertEquals(1, audiobookByGenreProc.getChildSizeOf(genre.get(0)));
        }
    }
}

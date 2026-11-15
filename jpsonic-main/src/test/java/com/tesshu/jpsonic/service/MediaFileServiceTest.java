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
 * (C) 2021 tesshucom
 */

package com.tesshu.jpsonic.service;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators;
import com.tesshu.jpsonic.infrastructure.filesystem.ScanningExclusionPolicy;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.Mockito;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class MediaFileServiceTest {

    private LibraryAccessPolicy libraryAccessPolicy;
    private MediaFileDao mediaFileDao;
    private MediaFileService mediaFileService;

    @BeforeEach
    void setup() throws URISyntaxException {
        SettingsFacade settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.general.extension.musicFileTypes, "mp3")
            .withString(SKeys.general.extension.excludedCoverArt,
                    "AlbumArtSmall.jpg small.jpg large.jpg")
            .build();
        libraryAccessPolicy = mock(LibraryAccessPolicy.class);
        mediaFileDao = mock(MediaFileDao.class);
        mediaFileService = new MediaFileService(settingsFacade,
                new ScanningExclusionPolicy(settingsFacade), mock(MusicFolderService.class),
                libraryAccessPolicy, mock(MediaFileCache.class), mediaFileDao,
                mock(JpsonicComparators.class));
        Mockito.when(libraryAccessPolicy.isReadAllowed(Mockito.any(Path.class))).thenReturn(true);
    }

    @Nested
    class FindCoverArtTest {

        private Path createPath(String path) throws URISyntaxException {
            return Path.of(MediaFileServiceTest.class.getResource(path).toURI());
        }

        @Test
        void coverArtFileTypesTest() throws ExecutionException, URISyntaxException {
            // fileNames
            Path path = createPath("/MEDIAS/Metadata/coverart/cover.jpg");
            assertEquals(path.toString(), mediaFileService.findCoverArt(path).get().toString());
            path = createPath("/MEDIAS/Metadata/coverart/cover.png");
            assertEquals(path.toString(), mediaFileService.findCoverArt(path).get().toString());
            path = createPath("/MEDIAS/Metadata/coverart/cover.gif");
            assertEquals(path.toString(), mediaFileService.findCoverArt(path).get().toString());
            path = createPath("/MEDIAS/Metadata/coverart/folder.gif");
            assertEquals(path.toString(), mediaFileService.findCoverArt(path).get().toString());

            // extensions
            path = createPath("/MEDIAS/Metadata/coverart/album.gif");
            assertEquals(path.toString(), mediaFileService.findCoverArt(path).get().toString());
            path = createPath("/MEDIAS/Metadata/coverart/album.jpeg");
            assertEquals(path.toString(), mediaFileService.findCoverArt(path).get().toString());
            path = createPath("/MEDIAS/Metadata/coverart/album.gif");
            assertEquals(path.toString(), mediaFileService.findCoverArt(path).get().toString());
            path = createPath("/MEDIAS/Metadata/coverart/album.png");
            assertEquals(path.toString(), mediaFileService.findCoverArt(path).get().toString());

            // letter case
            path = createPath("/MEDIAS/Metadata/coverart/coveratrt.GIF");
            assertEquals(path.toString(), mediaFileService.findCoverArt(path).get().toString());

            // dir
            path = createPath("/MEDIAS/Metadata/coverart/coveratrt.jpg");
            assertTrue(Files.exists(path));
            assertTrue(mediaFileService.findCoverArt(path).isEmpty());

            // Exclude specific cover art (like caches WMP makes without permission)
            path = createPath("/MEDIAS/Metadata/coverart/exclude");
            assertTrue(Files.exists(path));
            assertTrue(mediaFileService.findCoverArt(path).isEmpty());
        }

        @Test
        @DisabledOnOs(OS.LINUX)
        void testIsEmbeddedArtworkApplicableOnWin() throws ExecutionException, URISyntaxException {

            Mockito
                .when(libraryAccessPolicy.isReadAllowed(Mockito.any(Path.class)))
                .thenReturn(true);

            // coverArt(Since it depends on NIO, it is OS dependent. That's the
            // specification.)
            Path parent = createPath("/MEDIAS/Metadata/coverart");
            Path firstChild = createPath("/MEDIAS/Metadata/coverart/album.gif");
            assertEquals(firstChild, mediaFileService.findCoverArt(parent).get());

            // coverArt
            Path containsEmbeddedFormats = createPath("/MEDIAS/Metadata/v2.4");
            Path embeddedFormat = createPath("/MEDIAS/Metadata/v2.4/Mp3tag2.9.7.mp3");
            assertEquals(embeddedFormat,
                    mediaFileService.findCoverArt(containsEmbeddedFormats).get());

            // empty
            Path containsDirOnly = createPath("/MEDIAS/Metadata/tagger3");
            assertTrue(mediaFileService.findCoverArt(containsDirOnly).isEmpty());
        }
    }

    @Test
    void testGetGenresString() {
        List<String> genres = Collections.emptyList();
        Mockito
            .when(mediaFileDao.getID3AlbumGenres(Mockito.any(MediaFile.class)))
            .thenReturn(genres);
        MediaFile album = new MediaFile();
        assertNull(mediaFileService.getID3AlbumGenresString(album));

        genres = Arrays.asList("GenreA");
        Mockito
            .when(mediaFileDao.getID3AlbumGenres(Mockito.any(MediaFile.class)))
            .thenReturn(genres);
        assertEquals("GenreA", mediaFileService.getID3AlbumGenresString(album));

        genres = Arrays.asList("GenreA", "GenreB");
        Mockito
            .when(mediaFileDao.getID3AlbumGenres(Mockito.any(MediaFile.class)))
            .thenReturn(genres);
        assertEquals("GenreA;GenreB", mediaFileService.getID3AlbumGenresString(album));

        genres = Arrays.asList("GenreA", "GenreA;GenreB");
        Mockito
            .when(mediaFileDao.getID3AlbumGenres(Mockito.any(MediaFile.class)))
            .thenReturn(genres);
        assertEquals("GenreA;GenreA;GenreB", mediaFileService.getID3AlbumGenresString(album));
    }
}

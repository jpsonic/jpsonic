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
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.scanner;

import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.AlbumDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;

@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals" })
class MediaScannerServiceImplUpdateAlbumTest extends AbstractNeedsScan {

    private List<MusicFolder> musicFolders;

    @Autowired
    private MediaFileDao mediaFileDao;

    @Autowired
    private AlbumDao albumDao;

    @Override
    public List<MusicFolder> getMusicFolders() {
        if (ObjectUtils.isEmpty(musicFolders)) {
            musicFolders = Arrays
                .asList(new MusicFolder(1, resolveBaseMediaPath("Scan/Id3LIFO"),
                        "alphaBeticalProps", true, now(), 0, false),
                        new MusicFolder(2, resolveBaseMediaPath("Scan/Null"), "noTagFirstChild",
                                true, now(), 1, false),
                        new MusicFolder(3, resolveBaseMediaPath("Scan/Reverse"),
                                "fileAndPropsNameInReverse", true, now(), 2, false));
        }
        return musicFolders;
    }

    @BeforeEach
    void setup() {
        populateDatabase();
    }

    /*
     * If data with the same name exists in both albums in the file structure/Id3,
     * the tag of the first child file takes precedence. The Sonic server relies on
     * his NIO for this "first child", so the result of the first-fetch depends on
     * the OS filesystem. Jpsonic has solved this problem in v111.6.0, and the same
     * analysis is now performed on all platforms.
     */
    @Test
    void testUpdateAlbum() {

        // LIFO
        List<MusicFolder> folder = getMusicFolders()
            .stream()
            .filter(f -> "alphaBeticalProps".equals(f.getName()))
            .collect(Collectors.toList());
        assertEquals(1, folder.size());
        List<MediaFile> albums = mediaFileDao
            .getAlphabeticalAlbums(0, Integer.MAX_VALUE, false, folder);
        assertEquals(1, albums.size());
        MediaFile album = albums.get(0);
        assertEquals("ALBUM1", album.getName());
        assertEquals("albumArtistA", album.getArtist());
        assertNull(album.getAlbumArtist());
        assertEquals("genreA", album.getGenre());
        assertEquals(2001, album.getYear());
        assertNull(album.getMusicBrainzReleaseId());
        assertNull(album.getMusicBrainzRecordingId());

        List<Album> albumId3s = albumDao
            .getAlphabeticalAlbums(0, Integer.MAX_VALUE, false, true, folder);
        Map<String, Album> albumId3Map = albumId3s
            .stream()
            .collect(Collectors.toMap(Album::getArtist, a -> a));

        assertEquals(2, albumId3s.size());
        Album albumA = albumId3Map.get("albumArtistA");
        assertEquals("albumA", albumA.getName());
        assertEquals("albumArtistA", albumA.getArtist());
        assertEquals("genreA", albumA.getGenre());
        assertEquals(2001, albumA.getYear());
        assertNull(albumA.getMusicBrainzReleaseId());
        Album albumB = albumId3Map.get("albumArtistB");
        assertEquals("albumA", albumB.getName());
        assertEquals("albumArtistB", albumB.getArtist());
        assertEquals("genreB", albumB.getGenre());
        assertEquals(2002, albumB.getYear());
        assertNull(albumB.getMusicBrainzReleaseId());

        // Null
        folder = getMusicFolders()
            .stream()
            .filter(f -> "noTagFirstChild".equals(f.getName()))
            .collect(Collectors.toList());
        albums = mediaFileDao.getAlphabeticalAlbums(0, Integer.MAX_VALUE, false, folder);
        assertEquals(1, albums.size());
        album = albums.get(0);
        assertEquals("ALBUM2", album.getName());
        assertEquals("ARTIST2", album.getArtist());
        assertNull(album.getAlbumArtist());
        assertNull(album.getGenre());
        assertNull(album.getYear());
        assertNull(album.getMusicBrainzReleaseId());
        assertNull(album.getMusicBrainzRecordingId());

        albumId3s = albumDao.getAlphabeticalAlbums(0, Integer.MAX_VALUE, false, false, folder);
        assertEquals(2, albumId3s.size());

        albumA = albumId3s.get(0);
        assertEquals("ALBUM2", albumA.getName());
        assertEquals("ARTIST2", albumA.getArtist());
        assertNull(albumA.getGenre());
        assertNull(albumA.getYear());
        assertNull(albumA.getMusicBrainzReleaseId());

        albumA = albumId3s.get(1);
        assertEquals("albumC", albumA.getName());
        assertEquals("albumArtistC", albumA.getArtist());
        assertEquals("genreC", albumA.getGenre());
        assertEquals(2002, albumA.getYear());
        assertNull(albumA.getMusicBrainzReleaseId());

        // Reverse
        folder = getMusicFolders()
            .stream()
            .filter(f -> "fileAndPropsNameInReverse".equals(f.getName()))
            .collect(Collectors.toList());
        albums = mediaFileDao.getAlphabeticalAlbums(0, Integer.MAX_VALUE, false, folder);
        assertEquals(1, albums.size());
        album = albums.get(0);
        assertEquals("ALBUM3", album.getName());
        assertEquals("albumArtistD", album.getArtist());
        assertNull(album.getAlbumArtist());
        assertEquals("genreD", album.getGenre());
        assertEquals(2001, album.getYear());
        assertNull(album.getMusicBrainzReleaseId());
        assertNull(album.getMusicBrainzRecordingId());

        albumId3s = albumDao.getAlphabeticalAlbums(0, Integer.MAX_VALUE, false, false, folder);
        assertEquals(2, albumId3s.size());

        albumA = albumId3s.get(0);
        assertEquals("albumD", albumA.getName());
        assertEquals("albumArtistD", albumA.getArtist());
        assertEquals("genreD", albumA.getGenre());
        assertEquals(2001, albumA.getYear());
        assertNull(albumA.getMusicBrainzReleaseId());

        albumA = albumId3s.get(1);
        assertEquals("albumE", albumA.getName());
        assertEquals("albumArtistE", albumA.getArtist());
        assertEquals("genreE", albumA.getGenre());
        assertEquals(2002, albumA.getYear());
        assertNull(albumA.getMusicBrainzReleaseId());
    }
}

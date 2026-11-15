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
 * (C) 2026 tesshucom
 */

package com.tesshu.jpsonic.adapter.resource;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertEquals;

import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.Album;
import com.tesshu.jpsonic.domain.model.Artist;
import com.tesshu.jpsonic.domain.policy.RuntimeOrderPolicy;
import com.tesshu.jpsonic.domain.provider.resource.AlbumProvider;
import com.tesshu.jpsonic.domain.provider.resource.ArtistProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.persistence.NeedsDB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@NeedsDB
public class AlbumProviderAdapterTest extends AbstractNeedsScan {

    @Autowired
    private MusicFolderProvider musicFolderProvider;
    @Autowired
    private AlbumProvider albumProvider;
    @Autowired
    private ArtistProvider artistProvider;

    @BeforeEach
    void setup() {
        populateDatabaseOnlyOnce();
    }

    @Test
    public void testCountAlbums() {
        assertEquals(5, albumProvider.countAlbums(musicFolderProvider.getGuestFolders()));
    }

    @Test
    public void testFindAlbumsListOfMusicFolderAlbumSortOrderLongLong() {
        List<Album> albums = 
        albumProvider
            .findAlbums(musicFolderProvider.getGuestFolders(),
                    RuntimeOrderPolicy.AlbumSortOrder.DEFAULT, 0, Integer.MAX_VALUE);
        int count = albumProvider.countAlbums(musicFolderProvider.getGuestFolders());
        assertEquals(5, count);
        assertEquals(count, albums.size());
        assertEquals("_ID3_ALBUM_ Bach: Goldberg Variations, Canons [Disc 1]", albums.get(0).name());
        assertEquals("_ID3_ALBUM_ Chrome Hoof", albums.get(1).name());
        assertEquals("_ID3_ALBUM_ Ravel - Chamber Music With Voice", albums.get(2).name());
        assertEquals("_ID3_ALBUM_ Sackcloth 'n' Ashes", albums.get(3).name());
        assertEquals("Complete Piano Works", albums.get(4).name());

        albums = 
                albumProvider
                    .findAlbums(musicFolderProvider.getGuestFolders(),
                            RuntimeOrderPolicy.AlbumSortOrder.BY_ARTIST_AND_ALBUM, 0, Integer.MAX_VALUE);
        assertEquals("Complete Piano Works", albums.get(0).name());
        assertEquals("_ID3_ALBUM_ Ravel - Chamber Music With Voice", albums.get(1).name());
        assertEquals("_ID3_ALBUM_ Bach: Goldberg Variations, Canons [Disc 1]", albums.get(2).name());
        assertEquals("_ID3_ALBUM_ Chrome Hoof", albums.get(3).name());
        assertEquals("_ID3_ALBUM_ Sackcloth 'n' Ashes", albums.get(4).name());

        assertEquals("_DIR_ Ravel", albums.get(0).artist().get());
        assertEquals("_ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble", albums.get(1).artist().get());
        assertEquals("_ID3_ARTIST_ Céline Frisch: Café Zimmermann", albums.get(2).artist().get());
        assertEquals("_ID3_ARTIST_ Chrome Hoof", albums.get(3).artist().get());
        assertEquals("_ID3_ARTIST_ Sixteen Horsepower", albums.get(4).artist().get());
        
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> albumProvider
                .findAlbums(musicFolderProvider.getGuestFolders(),
                        RuntimeOrderPolicy.AlbumSortOrder.YEAR, 0, Integer.MAX_VALUE));
    }

    @Test
    public void testFindChildren() {
        int count = artistProvider.countArtists(musicFolderProvider.getGuestFolders());
        assertEquals(5, count);

        Artist artist = artistProvider
            .findArtists(musicFolderProvider.getGuestFolders(), 0, count)
            .get(0);
        assertEquals("_DIR_ Ravel", artist.name());

        List<Album> albums = albumProvider
            .findChildren(musicFolderProvider.getGuestFolders(), artist,
                    RuntimeOrderPolicy.AlbumSortOrder.DEFAULT, 0, Integer.MAX_VALUE);
        assertEquals(1, albums.size());

        albums = albumProvider
            .findChildren(musicFolderProvider.getGuestFolders(), artist,
                    RuntimeOrderPolicy.AlbumSortOrder.YEAR, 0, Integer.MAX_VALUE);
        assertEquals(1, albums.size());

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> albumProvider
                .findChildren(musicFolderProvider.getGuestFolders(), artist,
                        RuntimeOrderPolicy.AlbumSortOrder.BY_ARTIST_AND_ALBUM, 0,
                        Integer.MAX_VALUE));
    }

    @Test
    public void testFindNewestAlbums() {
        List<Album> albums = albumProvider
            .findNewestAlbums(musicFolderProvider.getGuestFolders(), 0, Integer.MAX_VALUE);
        assertEquals(5, albums.size());
    }

    @Test
    public void testRequireAlbum() {
        List<Album> albums = albumProvider
            .findAlbums(musicFolderProvider.getGuestFolders(),
                    RuntimeOrderPolicy.AlbumSortOrder.DEFAULT, 0, Integer.MAX_VALUE);
        int count = albumProvider.countAlbums(musicFolderProvider.getGuestFolders());
        assertEquals(5, count);
        assertEquals(count, albums.size());
        assertEquals("_ID3_ALBUM_ Bach: Goldberg Variations, Canons [Disc 1]",
                albumProvider.requireAlbum(albums.get(0).id()).name());
        assertEquals("_ID3_ALBUM_ Chrome Hoof",
                albumProvider.requireAlbum(albums.get(1).id()).name());
        assertEquals("_ID3_ALBUM_ Ravel - Chamber Music With Voice",
                albumProvider.requireAlbum(albums.get(2).id()).name());
        assertEquals("_ID3_ALBUM_ Sackcloth 'n' Ashes",
                albumProvider.requireAlbum(albums.get(3).id()).name());
        assertEquals("Complete Piano Works", albumProvider.requireAlbum(albums.get(4).id()).name());
    }
}

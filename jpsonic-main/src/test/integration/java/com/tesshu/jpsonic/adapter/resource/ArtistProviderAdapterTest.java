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
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.Artist;
import com.tesshu.jpsonic.domain.model.IndexWithCount;
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
class ArtistProviderAdapterTest extends AbstractNeedsScan {

    @Autowired
    private MusicFolderProvider musicFolderProvider;
    @Autowired
    private ArtistProvider artistProvider;

    @BeforeEach
    void setup() {
        populateDatabaseOnlyOnce();
    }

    @Test
    void testCountArtistsListOfMusicFolder() {
        assertEquals(5, artistProvider.countArtists(musicFolderProvider.getGuestFolders()));
    }

    @Test
    void testCountArtistsListOfMusicFolderString() {
        assertEquals(1,
                artistProvider
                    .countChildren(musicFolderProvider.getGuestFolders(),
                            "_ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble"));
    }

    @Test
    void testCountMudicIndexes() {
        assertEquals(1, artistProvider.countMudicIndexes(musicFolderProvider.getGuestFolders()));
    }

    @Test
    void testFindArtistsListOfMusicFolderLongLong() {
        int count = artistProvider.countArtists(musicFolderProvider.getGuestFolders());
        assertEquals(5, count);
        List<Artist> artists = artistProvider
            .findArtists(musicFolderProvider.getGuestFolders(), 0, Integer.MAX_VALUE);
        assertEquals(5, artists.size());
        assertEquals("_DIR_ Ravel", artists.get(0).name());
        assertEquals("_ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble", artists.get(1).name());
        assertEquals("_ID3_ARTIST_ Céline Frisch: Café Zimmermann", artists.get(2).name());
        assertEquals("_ID3_ARTIST_ Chrome Hoof", artists.get(3).name());
        assertEquals("_ID3_ARTIST_ Sixteen Horsepower", artists.get(4).name());
        artists = artistProvider
                .findArtists(musicFolderProvider.getGuestFolders(), 1, 1);
        assertEquals("_ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble", artists.get(0).name());
    }

    @Test
    void testFindArtistsListOfMusicFolderMusicIndexLongLong() {
        List<IndexWithCount> indexWithCounts = artistProvider
            .findIndexWithCounts(musicFolderProvider.getGuestFolders());
        List<Artist> artists = artistProvider
            .findArtists(musicFolderProvider.getGuestFolders(), indexWithCounts.get(0).index(), 0,
                    Integer.MAX_VALUE);
        assertEquals(1, indexWithCounts.size());
        assertEquals("#", indexWithCounts.get(0).index());
        assertEquals(5, indexWithCounts.get(0).count());
        assertEquals(artists.size(), indexWithCounts.get(0).count());
        assertEquals("_DIR_ Ravel", artists.get(0).name());
        assertEquals("_ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble", artists.get(1).name());
        assertEquals("_ID3_ARTIST_ Céline Frisch: Café Zimmermann", artists.get(2).name());
        assertEquals("_ID3_ARTIST_ Chrome Hoof", artists.get(3).name());
        assertEquals("_ID3_ARTIST_ Sixteen Horsepower", artists.get(4).name());
        artists = artistProvider
                .findArtists(musicFolderProvider.getGuestFolders(), 1, 1);
        assertEquals("_ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble", artists.get(0).name());
    }

    @Test
    void testFindIndexWithCounts() {
        List<IndexWithCount> indexWithCounts = artistProvider.findIndexWithCounts(musicFolderProvider.getGuestFolders());
        assertEquals(1, indexWithCounts.size());
        assertEquals("#", indexWithCounts.get(0).index());
        assertEquals(5, indexWithCounts.get(0).count());
    }

    @Test
    void testRequireArtist() {
        Artist artist = artistProvider.requireArtist(1);
        assertEquals(1, artist.albumCount());
        assertEquals(1, artist.id());
        assertTrue(artist.thumbUri().isEmpty());
        assertEquals(1, artist.folderId());
        assertEquals("#", artist.musicIndex().get());
        assertEquals("_ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble", artist.name());
        assertEquals(2, artist.order());
        assertEquals("_ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble", artist.reading());

        Artist fake = new Artist(99, "name", "path", 0, 0, "reading", 0, "#");
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> artistProvider.requireArtist(fake.id()));
    }
}

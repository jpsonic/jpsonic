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
 * (C) 2025 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.scanner;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.tesshu.jpsonic.infrastructure.search.LegacySearchResult;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.Artist;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.repository.AlbumDao;
import com.tesshu.jpsonic.persistence.api.repository.ArtistDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import org.apache.lucene.document.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals" })
class SearchServiceUtilitiesTest {

    private ArtistDao artistDao;
    private AlbumDao albumDao;
    private MediaFileDao mediaFileDao;
    private SearchServiceUtilities utilities;

    @BeforeEach
    void setUp() {
        artistDao = mock(ArtistDao.class);
        albumDao = mock(AlbumDao.class);
        mediaFileDao = mock(MediaFileDao.class);
        utilities = new SearchServiceUtilities(artistDao, albumDao, mediaFileDao);
    }

    @Test
    void testPostConstruct() {
        SearchServiceUtilities utilities = new SearchServiceUtilities(artistDao, albumDao,
                mediaFileDao);
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> utilities.nextInt(50));
        utilities.postConstruct();
        assertNotNull(utilities.nextInt(50));
    }

    @Test
    void testAddMediaFileIfAnyMatch() {
        List<MediaFile> dist = new ArrayList<>();
        Integer id = 1;
        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(id);

        // Does not exist in DB/not added
        utilities.addMediaFileIfAnyMatch(dist, id);
        assertEquals(0, dist.size());

        // Exists in DB/Not added
        when(mediaFileDao.getMediaFile(id)).thenReturn(mediaFile);
        utilities.addMediaFileIfAnyMatch(dist, id);
        assertEquals(1, dist.size());

        // Exists in DB/Added
        when(mediaFileDao.getMediaFile(id)).thenReturn(mediaFile);
        utilities.addMediaFileIfAnyMatch(dist, id);
        assertEquals(1, dist.size());

        // Objects that exist in DB/have different IDs have been already added
        MediaFile secondMediaFile = new MediaFile();
        Integer secondId = 99;
        secondMediaFile.setId(secondId);
        when(mediaFileDao.getMediaFile(secondId)).thenReturn(secondMediaFile);
        utilities.addMediaFileIfAnyMatch(dist, secondId);
        assertEquals(2, dist.size());
    }

    @Test
    void testAddArtistId3IfAnyMatch() {
        List<Artist> dist = new ArrayList<>();
        Integer id = 1;
        Artist artist = new Artist();
        artist.setId(id);

        // Does not exist in DB/not added
        utilities.addArtistId3IfAnyMatch(dist, id);
        assertEquals(0, dist.size());

        // Exists in DB/Not added
        when(artistDao.getArtist(id)).thenReturn(artist);
        utilities.addArtistId3IfAnyMatch(dist, id);
        assertEquals(1, dist.size());

        // Exists in DB/Added
        when(artistDao.getArtist(id)).thenReturn(artist);
        utilities.addArtistId3IfAnyMatch(dist, id);
        assertEquals(1, dist.size());

        // Objects that exist in DB/have different IDs have been already added
        Artist secondArtist = new Artist();
        Integer secondId = 99;
        secondArtist.setId(secondId);
        when(artistDao.getArtist(secondId)).thenReturn(secondArtist);
        utilities.addArtistId3IfAnyMatch(dist, secondId);
        assertEquals(2, dist.size());
    }

    @Test
    void testAddAlbumId3IfAnyMatch() {
        List<Album> dist = new ArrayList<>();
        Integer id = 1;
        Album album = new Album();
        album.setId(id);

        // Does not exist in DB/not added
        utilities.addAlbumId3IfAnyMatch(dist, id);
        assertEquals(0, dist.size());

        // Exists in DB/Not added
        when(albumDao.getAlbum(id)).thenReturn(album);
        utilities.addAlbumId3IfAnyMatch(dist, id);
        assertEquals(1, dist.size());

        // Exists in DB/Added
        when(albumDao.getAlbum(id)).thenReturn(album);
        utilities.addAlbumId3IfAnyMatch(dist, id);
        assertEquals(1, dist.size());

        // Objects that exist in DB/have different IDs have been already added
        Album secondAlbum = new Album();
        Integer secondId = 99;
        secondAlbum.setId(secondId);
        when(albumDao.getAlbum(secondId)).thenReturn(secondAlbum);
        utilities.addAlbumId3IfAnyMatch(dist, secondId);
        assertEquals(2, dist.size());
    }

    @Test
    void testAddIgnoreNullCollectionObject() {
        List<MediaFile> dist = new ArrayList<>();
        utilities.addIgnoreNull(dist, null);
        assertEquals(0, dist.size());
        utilities.addIgnoreNull(dist, new MediaFile());
        assertEquals(1, dist.size());
    }

    @Test
    void testAddIgnoreNullCollectionOfQIndexTypeInt() {
        List<MediaFile> dist = new ArrayList<>();
        Integer id = 99;
        utilities.addMediaFileIfAnyMatch(dist, id);
        assertEquals(0, dist.size());

        List<Artist> distArtist = new ArrayList<>();
        utilities.addArtistId3IfAnyMatch(distArtist, id);
        assertEquals(0, distArtist.size());

        List<Album> distAlbum = new ArrayList<>();
        utilities.addAlbumId3IfAnyMatch(distAlbum, id);
        assertEquals(0, distAlbum.size());

        id = 1;
        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(id);
        when(mediaFileDao.getMediaFile(id)).thenReturn(mediaFile);
        utilities.addMediaFileIfAnyMatch(dist, id);
        assertEquals(1, dist.size());
    }

    @Test
    void testAddIgnoreNullParamSearchResultOfTIndexTypeIntClassOfT() {
        Integer id = 99;
        com.tesshu.jpsonic.domain.model.MediaFile mediaFile = new com.tesshu.jpsonic.domain.model.MediaFile(
                99, "pathString", 0, "format", "MUSIC", 256, 60, 9999, "artist", "album", "title",
                "albumArtist", 0, "genre", 2026, "thumbUri", "composer", "reading", "#", "comment");

        List<com.tesshu.jpsonic.domain.model.MediaFile> dist = new ArrayList<>();
        utilities
            .addEntityIfPresent(dist, IndexType.SONG, id,
                    com.tesshu.jpsonic.domain.model.MediaFile.class);
        assertEquals(0, dist.size());

        when(mediaFileDao.getDomainMediaFile(id)).thenReturn(mediaFile);
        utilities
            .addEntityIfPresent(dist, IndexType.SONG, id,
                    com.tesshu.jpsonic.domain.model.MediaFile.class);
        assertEquals(1, dist.size());
    }

    @Test
    void testAddIfAnyMatch() {

        Integer songId = 1;
        MediaFile song = new MediaFile();
        song.setId(songId);
        Integer artistId = 2;
        MediaFile artist = new MediaFile();
        artist.setId(artistId);
        Integer albumId = 3;
        MediaFile album = new MediaFile();
        album.setId(albumId);
        Integer artistId3Id = 4;
        Artist artistId3 = new Artist();
        artistId3.setId(artistId3Id);
        Integer albumId3Id = 5;
        Album albumId3 = new Album();
        albumId3.setId(albumId3Id);

        when(mediaFileDao.getMediaFile(songId)).thenReturn(song);
        when(mediaFileDao.getMediaFile(artistId)).thenReturn(artist);
        when(mediaFileDao.getMediaFile(albumId)).thenReturn(album);
        when(artistDao.getArtist(artistId3Id)).thenReturn(artistId3);
        when(albumDao.getAlbum(albumId3Id)).thenReturn(albumId3);

        LegacySearchResult dist = new LegacySearchResult();
        Document subject = mock(Document.class);
        when(subject.get(SearchIndexFields.id.value())).thenReturn(songId.toString());
        utilities.addIfAnyMatch(dist, IndexType.SONG, subject);
        assertEquals(1, dist.getMediaFiles().size());
        assertEquals(songId, dist.getMediaFiles().get(0).getId());

        dist = new LegacySearchResult();
        subject = mock(Document.class);
        when(subject.get(SearchIndexFields.id.value())).thenReturn(artistId.toString());
        utilities.addIfAnyMatch(dist, IndexType.ARTIST, subject);
        assertEquals(1, dist.getMediaFiles().size());
        assertEquals(artistId, dist.getMediaFiles().get(0).getId());

        dist = new LegacySearchResult();
        subject = mock(Document.class);
        when(subject.get(SearchIndexFields.id.value())).thenReturn(albumId.toString());
        utilities.addIfAnyMatch(dist, IndexType.ALBUM, subject);
        assertEquals(1, dist.getMediaFiles().size());
        assertEquals(albumId, dist.getMediaFiles().get(0).getId());

        dist = new LegacySearchResult();
        subject = mock(Document.class);
        when(subject.get(SearchIndexFields.id.value())).thenReturn(artistId3Id.toString());
        utilities.addIfAnyMatch(dist, IndexType.ARTIST_ID3, subject);
        assertEquals(1, dist.getArtists().size());
        assertEquals(artistId3Id, dist.getArtists().get(0).getId());

        dist = new LegacySearchResult();
        subject = mock(Document.class);
        when(subject.get(SearchIndexFields.id.value())).thenReturn(albumId3Id.toString());
        utilities.addIfAnyMatch(dist, IndexType.ALBUM_ID3, subject);
        assertEquals(1, dist.getAlbums().size());
        assertEquals(albumId3Id, dist.getAlbums().get(0).getId());

        dist = new LegacySearchResult();
        subject = mock(Document.class);
        when(subject.get(SearchIndexFields.id.value())).thenReturn(albumId3Id.toString());
        utilities.addIfAnyMatch(dist, IndexType.GENRE, subject);
        assertEquals(0, dist.getMediaFiles().size());
        assertEquals(0, dist.getArtists().size());
        assertEquals(0, dist.getAlbums().size());

    }
}

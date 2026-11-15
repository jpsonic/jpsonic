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

package com.tesshu.jpsonic.infrastructure.scanner;

import java.util.List;

import com.tesshu.jpsonic.ThreadSafe;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.Artist;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Component;

@Component
class Indexer {

    private final DocumentFactory documentFactory;
    private final IndexManager indexManager;

    Indexer(DocumentFactory documentFactory, IndexManager indexManager) {
        super();
        this.documentFactory = documentFactory;
        this.indexManager = indexManager;
    }

    void index(@NonNull Album album) {
        Term key = DocumentFactory.createPrimarykey(album.getId());
        // spotless:off
        Document doc = documentFactory.createAlbumId3Doc(
                album.getId(),
                album.getName(),
                album.getNameSort(),
                album.getNameReading(),
                album.getArtist(),
                album.getArtistSort(),
                album.getArtistReading(),
                album.getGenre(),
                album.getFolderId());
        // spotless:on

        indexManager.updateDocument(IndexType.ALBUM_ID3, key, doc);
        String genre = album.getGenre();
        if (genre != null && !genre.isEmpty()) {
            key = DocumentFactory.createPrimarykey(genre.hashCode());
            doc = documentFactory.createGenreDocument(genre);
            indexManager.updateDocument(IndexType.ALBUM_ID3_GENRE, key, doc);
        }
    }

    void index(Artist artist, MusicFolder folder) {
        Term key = DocumentFactory.createPrimarykey(artist.getId());
        // spotless:off
        Document doc = documentFactory.createArtistId3Document(
                artist.getId(),
                artist.getName(),
                artist.getSort(),
                artist.getReading(),
                folder.getId());
        // spotless:on
        indexManager.updateDocument(IndexType.ARTIST_ID3, key, doc);
    }

    void index(@NonNull MediaFile mediaFile) {
        Term key = DocumentFactory.createPrimarykey(mediaFile.getId());
        Document doc;
        if (mediaFile.isFile()) {
            // spotless:off
            doc = documentFactory.createSongDoc(
                mediaFile.getId(),
                mediaFile.getMediaType().name(),
                mediaFile.getTitle(),
                mediaFile.getArtist(),
                mediaFile.getArtistSort(),
                mediaFile.getArtistReading(),
                mediaFile.getComposer(),
                mediaFile.getComposerSortRaw(),
                mediaFile.getComposerSort(),
                mediaFile.getGenre(),
                mediaFile.getYear(),
                mediaFile.getFolder());
            // spotless:on
            indexManager.updateDocument(IndexType.SONG, key, doc);
        } else if (mediaFile.isAlbum()) {
            // spotless:off
            doc = documentFactory.createAlbumDoc(
                mediaFile.getId(),
                mediaFile.getArtist(),
                mediaFile.getArtistSort(),
                mediaFile.getArtistReading(),
                mediaFile.getAlbumName(),
                mediaFile.getAlbumSort(),
                mediaFile.getAlbumArtistReading(),
                mediaFile.getGenre(),
                mediaFile.getFolder());
            // spotless:on
            indexManager.updateDocument(IndexType.ALBUM, key, doc);
        } else {
            // spotless:off
            doc = documentFactory.createArtistDoc(
                    mediaFile.getId(),
                    mediaFile.getArtist(),
                    mediaFile.getArtistSort(),
                    mediaFile.getArtistReading(),
                    mediaFile.getFolder());
            // spotless:on
            indexManager.updateDocument(IndexType.ARTIST, key, doc);
        }

        String genre = mediaFile.getGenre();
        if (genre != null && !genre.isEmpty() && mediaFile.getMediaType() != MediaType.PODCAST) {
            key = DocumentFactory.createPrimarykey(genre.hashCode());
            doc = documentFactory.createGenreDocument(genre);
            indexManager.updateDocument(IndexType.GENRE, key, doc);
        }
    }

    void expungeArtist(int id) {
        indexManager.deleteDocuments(IndexType.ARTIST, DocumentFactory.createPrimarykey(id));
    }

    void expungeAlbum(int id) {
        indexManager.deleteDocuments(IndexType.ALBUM, DocumentFactory.createPrimarykey(id));
    }

    void expungeSong(int id) {
        indexManager.deleteDocuments(IndexType.SONG, DocumentFactory.createPrimarykey(id));
    }

    void expungeArtistId3(@NonNull List<Integer> expungeCandidates) {
        Term[] primaryKeys = expungeCandidates
            .stream()
            .map(DocumentFactory::createPrimarykey)
            .toArray(Term[]::new);
        indexManager.deleteDocuments(IndexType.ARTIST_ID3, primaryKeys);
    }

    @ThreadSafe(enableChecks = false)
    void expungeAlbumId3(List<Integer> candidates) {
        Term[] primaryKeys = candidates
            .stream()
            .map(DocumentFactory::createPrimarykey)
            .toArray(Term[]::new);
        indexManager.deleteDocuments(IndexType.ALBUM_ID3, primaryKeys);
    }
}

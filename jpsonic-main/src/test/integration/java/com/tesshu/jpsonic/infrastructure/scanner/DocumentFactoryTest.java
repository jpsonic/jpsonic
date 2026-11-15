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

package com.tesshu.jpsonic.infrastructure.scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.annotation.Documented;
import java.time.Instant;

import com.tesshu.jpsonic.domain.system.IndexScheme;
import com.tesshu.jpsonic.infrastructure.language.JapaneseReadingUtils;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.Artist;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.MusicFolderTestDataUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "PMD.TooManyStaticImports" })
class DocumentFactoryTest {

    private SettingsFacade settingsFacade;
    private DocumentFactory documentFactory;
    private Indexer indexer;
    @Mock
    private IndexManager indexManager;
    @Captor
    private ArgumentCaptor<Document> documentCaptor;

    @BeforeEach
    void setup() {
        settingsFacade = SettingsFacadeBuilder.create().build();
        indexManager = mock(IndexManager.class);
        documentFactory = new DocumentFactory(settingsFacade,
                new JapaneseReadingUtils(settingsFacade));
        indexer = new Indexer(documentFactory, indexManager);
        documentCaptor = ArgumentCaptor.forClass(Document.class);
        when(indexManager
            .updateDocument(any(IndexType.class), any(Term.class), any(Document.class)))
            .thenReturn(1L);
    }

    private Document indexAndCapture(MediaFile mediaFile) {
        indexer.index(mediaFile);
        verify(indexManager, atLeastOnce())
            .updateDocument(any(IndexType.class), any(Term.class), documentCaptor.capture());
        return documentCaptor.getValue();
    }

    @Test
    void testCreateArtistDocument() {
        MediaFile artist = new MediaFile();
        artist.setId(1);
        artist.setArtist("artist");
        artist.setArtistSort("artistSort");
        artist.setFolder("folder");
        artist.setMediaType(MediaType.DIRECTORY);

        Document document = indexAndCapture(artist);

        assertEquals(6, document.getFields().size(), "fields.size");
        assertEquals("1", document.get(SearchIndexFields.id.value()));
        assertEquals("artist", document.get(SearchIndexFields.artist.value()));
        assertEquals("artistSort", document.get(SearchIndexFields.artistReading.value()));
        assertEquals("folder", document.get(SearchIndexFields.folder.value()));

        clearInvocations(indexManager);

        MediaFile mediaFile = new MediaFile();
        mediaFile.setMediaType(MediaType.DIRECTORY);
        mediaFile.setFolder("folder");

        document = indexAndCapture(mediaFile);

        assertEquals(2, document.getFields().size(), "fields.size");
        assertEquals("0", document.get(SearchIndexFields.id.value()));
        assertNull(document.get(SearchIndexFields.artist.value()));
        assertNull(document.get(SearchIndexFields.artistReading.value()));

        clearInvocations(indexManager);

        MediaFile file = new MediaFile();
        file.setMediaType(MediaType.DIRECTORY);

        assertThrows(IllegalArgumentException.class, () -> indexer.index(file));
    }

    @Test
    void testCreateAlbumId3Document() {
        Album album = new Album();
        album.setId(1);
        album.setName("name");
        album.setNameSort("nameSort");
        album.setArtist("artist");
        album.setArtistSort("artistSort");
        album.setGenre("genre");
        album.setFolderId(10);

        indexer.index(album);

        verify(indexManager, times(2))
            .updateDocument(any(IndexType.class), any(Term.class), documentCaptor.capture());

        Document document = documentCaptor.getAllValues().get(0);

        assertEquals(12, document.getFields().size(), "fields.size");
        assertEquals("1", document.get(SearchIndexFields.id.value()));
        assertEquals("name", document.get(SearchIndexFields.album.value()));
        assertEquals("nameSort", document.get(SearchIndexFields.albumReading.value()));
        assertEquals("artist", document.get(SearchIndexFields.artist.value()));
        assertEquals("artistSort", document.get(SearchIndexFields.artistReading.value()));
        assertEquals("genre", document.get(SearchIndexFields.genre.value()));
        assertEquals("10", document.get(SearchIndexFields.folderId.value()));

        Document genreDocument = documentCaptor.getAllValues().get(1);
        assertEquals("genre", genreDocument.get(SearchIndexFields.genreKey.value()));
        assertEquals("genre", genreDocument.get(SearchIndexFields.genre.value()));

        assertThrows(NullPointerException.class, () -> indexer.index(new Album()));
    }

    @Test
    void testCreateArtistId3Document() {
        Artist artist = new Artist();
        artist.setId(1);
        artist.setName("name");
        artist.setSort("sort");
        artist.setFolderId(10);

        MusicFolder musicFolder = new MusicFolder(100,
                MusicFolderTestDataUtils.resolveMusicFolderPath(), "Music", true, Instant.now(), 0,
                false);

        indexer.index(artist, musicFolder);

        verify(indexManager)
            .updateDocument(eq(IndexType.ARTIST_ID3), any(Term.class), documentCaptor.capture());

        Document document = documentCaptor.getValue();

        assertEquals(6, document.getFields().size(), "fields.size");
        assertEquals("1", document.get(SearchIndexFields.id.value()));
        assertEquals("name", document.get(SearchIndexFields.artist.value()));
        assertEquals("sort", document.get(SearchIndexFields.artistReading.value()));
        assertEquals("100", document.get(SearchIndexFields.folderId.value()));

        clearInvocations(indexManager);

        indexer.index(new Artist(), musicFolder);

        verify(indexManager)
            .updateDocument(eq(IndexType.ARTIST_ID3), any(Term.class), documentCaptor.capture());

        document = documentCaptor.getValue();

        assertEquals(2, document.getFields().size(), "fields.size");
        assertEquals("0", document.get(SearchIndexFields.id.value()));
        assertNull(document.get(SearchIndexFields.artistReading.value()));
        assertEquals("100", document.get(SearchIndexFields.folderId.value()));

        assertThrows(NullPointerException.class, () -> indexer.index(new Artist(), null));
    }

    @Test
    void testCreateSongDocument() {
        MediaFile song = new MediaFile();
        song.setId(1);
        song.setArtist("artist");
        song.setArtistSort("artistSort");
        song.setTitle("title");
        song.setTitleSort("titleSort");
        song.setMediaType(MediaType.MUSIC);
        song.setGenre("genre");
        song.setYear(2000);
        song.setFolder("folder");
        song.setComposer("composer.value()");
        song.setComposerSortRaw("composer.value()Sort");

        indexer.index(song);

        verify(indexManager, times(2))
            .updateDocument(any(IndexType.class), any(Term.class), documentCaptor.capture());

        Document document = documentCaptor.getAllValues().get(0);

        assertEquals(18, document.getFields().size(), "fields.size");
        assertEquals("1", document.get(SearchIndexFields.id.value()));
        assertEquals("artist", document.get(SearchIndexFields.artist.value()));
        assertEquals("artistSort", document.get(SearchIndexFields.artistReading.value()));
        assertEquals("title", document.get(SearchIndexFields.title.value()));
        assertEquals("title", document.get(SearchIndexFields.titleReading.value()));
        assertEquals("MUSIC", document.get(SearchIndexFields.mediaType.value()));
        assertEquals("genre", document.get(SearchIndexFields.genre.value()));
        assertNull(document.get(SearchIndexFields.year.value()));
        assertEquals("folder", document.get(SearchIndexFields.folder.value()));
        assertEquals("composer.value()", document.get(SearchIndexFields.composer.value()));
        assertEquals("composer.value()Sort",
                document.get(SearchIndexFields.composerReading.value()));

        Document genreDocument = documentCaptor.getAllValues().get(1);
        assertEquals("genre", genreDocument.get(SearchIndexFields.genreKey.value()));
        assertEquals("genre", genreDocument.get(SearchIndexFields.genre.value()));

        clearInvocations(indexManager);

        MediaFile emptySong = new MediaFile();
        emptySong.setMediaType(MediaType.MUSIC);
        emptySong.setFolder("folder");

        document = indexAndCapture(emptySong);

        assertEquals(3, document.getFields().size(), "fields.size");
        assertEquals("0", document.get(SearchIndexFields.id.value()));
        assertNull(document.get(SearchIndexFields.artistReading.value()));
        assertNull(document.get(SearchIndexFields.title.value()));
        assertNull(document.get(SearchIndexFields.titleReading.value()));
        assertNull(document.get(SearchIndexFields.genre.value()));
        assertNull(document.get(SearchIndexFields.year.value()));
        assertNull(document.get(SearchIndexFields.composer.value()));
        assertNull(document.get(SearchIndexFields.composerReading.value()));

        clearInvocations(indexManager);

        MediaFile missingFolder = new MediaFile();
        missingFolder.setMediaType(MediaType.MUSIC);

        assertThrows(IllegalArgumentException.class, () -> indexer.index(missingFolder));

        MediaFile missingMediaType = new MediaFile();
        assertThrows(NullPointerException.class, () -> indexer.index(missingMediaType));

        clearInvocations(indexManager);

        MediaFile podcast = new MediaFile();
        podcast.setFolder("folder");
        podcast.setMediaType(MediaType.PODCAST);
        podcast.setGenre("genre");

        document = indexAndCapture(podcast);

        assertNull(document.get(SearchIndexFields.genre.value()));
        verify(indexManager, never())
            .updateDocument(eq(IndexType.GENRE), any(Term.class), any(Document.class));
    }

    @Documented
    private @interface ReadingDecisions {
        @interface Conditions {
            @interface IndexScheme {
                @interface NativeJapanese {
                }

                @interface RomanizedJapanese {
                }

                @interface WithoutJpLangProcessing {
                }
            }

            @interface ForceInternalValueInsteadOfTags {
                @interface False {
                }

                @interface True {
                }
            }

            @interface Value {
                @interface Null {
                }

                @interface NotNull {
                    @interface EqSort {
                    }

                    @interface Japanese {

                    }

                    @interface NotJapanese {

                    }
                }
            }
        }
    }

    @Nested
    class AcceptReadingTest {

        private MediaFile createSong() {
            MediaFile song = new MediaFile();
            song.setId(1);
            song.setArtist("artist");
            song.setArtistSort("artistSort");
            song.setTitle("title");
            song.setTitleSort("titleSort");
            song.setMediaType(MediaType.MUSIC);
            song.setGenre("genre");
            song.setYear(2000);
            song.setFolder("folder");
            song.setComposer("composer.value()");
            song.setComposerSortRaw("composer.value()Sort");
            return song;
        }

        @ReadingDecisions.Conditions.Value.Null
        @Test
        void c01() {
            MediaFile song = createSong();
            song.setArtist(null);
            song.setComposer(null);

            indexer.index(song);

            verify(indexManager)
                .updateDocument(eq(IndexType.SONG), any(Term.class), documentCaptor.capture());
            Document document = documentCaptor.getValue();

            assertNull(document.get(SearchIndexFields.artist.value()));
            assertNull(document.get(SearchIndexFields.artistReading.value()));
            assertNull(document.get(SearchIndexFields.composer.value()));
            assertNull(document.get(SearchIndexFields.composerReading.value()));
        }

        @ReadingDecisions.Conditions.Value.NotNull.EqSort
        @Test
        void c02() {
            MediaFile song = createSong();
            song.setArtist("Artist");
            song.setArtistSort("Artist");
            song.setComposer("composer.value()");
            song.setComposerSortRaw("composer.value()");

            indexer.index(song);

            verify(indexManager)
                .updateDocument(eq(IndexType.SONG), any(Term.class), documentCaptor.capture());
            Document document = documentCaptor.getValue();

            assertEquals("Artist", document.get(SearchIndexFields.artist.value()));
            assertNull(document.get(SearchIndexFields.artistReading.value()));
            assertEquals("composer.value()", document.get(SearchIndexFields.composer.value()));
            assertNull(document.get(SearchIndexFields.composerReading.value()));
        }

        @ReadingDecisions.Conditions.IndexScheme.NativeJapanese
        @ReadingDecisions.Conditions.Value.NotNull.NotJapanese
        @Test
        void c03() {
            MediaFile song = createSong();

            indexer.index(song);

            verify(indexManager)
                .updateDocument(eq(IndexType.SONG), any(Term.class), documentCaptor.capture());
            Document document = documentCaptor.getValue();

            assertEquals("artist", document.get(SearchIndexFields.artist.value()));
            assertEquals("artistSort", document.get(SearchIndexFields.artistReading.value()));
            assertEquals("composer.value()", document.get(SearchIndexFields.composer.value()));
            assertEquals("composer.value()Sort",
                    document.get(SearchIndexFields.composerReading.value()));
        }

        @ReadingDecisions.Conditions.IndexScheme.NativeJapanese
        @ReadingDecisions.Conditions.Value.NotNull.Japanese
        @Test
        void c04() {
            MediaFile song = createSong();
            song.setArtist("アーティスト");
            song.setArtistSort("あーてぃすと");
            song.setComposer("作曲者");
            song.setComposerSortRaw("さっきょくしゃ");

            indexer.index(song);

            verify(indexManager)
                .updateDocument(eq(IndexType.SONG), any(Term.class), documentCaptor.capture());
            Document document = documentCaptor.getValue();

            assertEquals("アーティスト", document.get(SearchIndexFields.artist.value()));
            assertEquals("あーてぃすと", document.get(SearchIndexFields.artistReading.value()));
            assertEquals("作曲者", document.get(SearchIndexFields.composer.value()));
            assertEquals("さっきょくしゃ", document.get(SearchIndexFields.composerReading.value()));
        }

        @ReadingDecisions.Conditions.IndexScheme.RomanizedJapanese
        @ReadingDecisions.Conditions.Value.NotNull.NotJapanese
        @Test
        void c05() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.advanced.index.indexSchemeName,
                        IndexScheme.ROMANIZED_JAPANESE.name())
                .build();
            documentFactory = new DocumentFactory(settingsFacade,
                    new JapaneseReadingUtils(settingsFacade));
            indexer = new Indexer(documentFactory, indexManager);

            MediaFile song = createSong();

            indexer.index(song);

            verify(indexManager)
                .updateDocument(eq(IndexType.SONG), any(Term.class), documentCaptor.capture());
            Document document = documentCaptor.getValue();

            assertEquals("artist", document.get(SearchIndexFields.artist.value()));
            assertEquals("artistSort", document.get(SearchIndexFields.artistReading.value()));
            assertEquals("composer.value()", document.get(SearchIndexFields.composer.value()));
            assertEquals("composer.value()Sort",
                    document.get(SearchIndexFields.composerReading.value()));
        }

        @ReadingDecisions.Conditions.IndexScheme.RomanizedJapanese
        @ReadingDecisions.Conditions.ForceInternalValueInsteadOfTags.False
        @ReadingDecisions.Conditions.Value.NotNull.Japanese
        @Test
        void c06() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.advanced.index.indexSchemeName,
                        IndexScheme.ROMANIZED_JAPANESE.name())
                .build();
            documentFactory = new DocumentFactory(settingsFacade,
                    new JapaneseReadingUtils(settingsFacade));
            indexer = new Indexer(documentFactory, indexManager);

            MediaFile song = createSong();
            song.setArtist("アーティスト");
            song.setArtistReading("analyzed artist-reading-value");
            song.setArtistSort("あーてぃすと");
            song.setComposer("作曲者");
            song.setComposerSort("analyzed composer.value()-reading-value");
            song.setComposerSortRaw("さっきょくしゃ");

            indexer.index(song);

            verify(indexManager)
                .updateDocument(eq(IndexType.SONG), any(Term.class), documentCaptor.capture());
            Document document = documentCaptor.getValue();

            assertEquals("アーティスト", document.get(SearchIndexFields.artist.value()));
            assertEquals("あーてぃすと", document.get(SearchIndexFields.artistReading.value()));
            assertEquals("あーてぃすと", document.get(SearchIndexFields.artistReadingRomanized.value()));
            assertEquals("作曲者", document.get(SearchIndexFields.composer.value()));
            assertEquals("さっきょくしゃ", document.get(SearchIndexFields.composerReading.value()));
            assertEquals("さっきょくしゃ",
                    document.get(SearchIndexFields.composerReadingRomanized.value()));
        }

        @ReadingDecisions.Conditions.IndexScheme.RomanizedJapanese
        @ReadingDecisions.Conditions.ForceInternalValueInsteadOfTags.True
        @ReadingDecisions.Conditions.Value.NotNull.Japanese
        @Test
        void c07() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.advanced.index.indexSchemeName,
                        IndexScheme.ROMANIZED_JAPANESE.name())
                .withBoolean(SKeys.advanced.index.forceInternalValueInsteadOfTags, true)
                .build();
            documentFactory = new DocumentFactory(settingsFacade,
                    new JapaneseReadingUtils(settingsFacade));
            indexer = new Indexer(documentFactory, indexManager);

            MediaFile song = createSong();
            song.setArtist("アーティスト");
            song.setArtistSort("あーてぃすと");
            song.setArtistReading("analyzed artist-reading-value");
            song.setComposer("作曲者");
            song.setComposerSort("analyzed composer.value()-reading-value");
            song.setComposerSortRaw("さっきょくしゃ");

            indexer.index(song);

            verify(indexManager)
                .updateDocument(eq(IndexType.SONG), any(Term.class), documentCaptor.capture());
            Document document = documentCaptor.getValue();

            assertEquals("アーティスト", document.get(SearchIndexFields.artist.value()));
            assertEquals("あーてぃすと", document.get(SearchIndexFields.artistReading.value()));
            assertEquals("analyzed artist-reading-value",
                    document.get(SearchIndexFields.artistReadingRomanized.value()));
            assertEquals("作曲者", document.get(SearchIndexFields.composer.value()));
            assertEquals("さっきょくしゃ", document.get(SearchIndexFields.composerReading.value()));
            assertEquals("analyzed composer.value()-reading-value",
                    document.get(SearchIndexFields.composerReadingRomanized.value()));
        }

        @ReadingDecisions.Conditions.IndexScheme.WithoutJpLangProcessing
        @ReadingDecisions.Conditions.Value.NotNull.NotJapanese
        @Test
        void c08() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.advanced.index.indexSchemeName,
                        IndexScheme.WITHOUT_JP_LANG_PROCESSING.name())
                .withBoolean(SKeys.advanced.index.forceInternalValueInsteadOfTags, true)
                .build();
            documentFactory = new DocumentFactory(settingsFacade,
                    new JapaneseReadingUtils(settingsFacade));
            indexer = new Indexer(documentFactory, indexManager);

            MediaFile song = createSong();

            indexer.index(song);

            verify(indexManager)
                .updateDocument(eq(IndexType.SONG), any(Term.class), documentCaptor.capture());
            Document document = documentCaptor.getValue();

            assertEquals("artist", document.get(SearchIndexFields.artist.value()));
            assertEquals("artistSort", document.get(SearchIndexFields.artistReading.value()));
            assertEquals("composer.value()", document.get(SearchIndexFields.composer.value()));
            assertEquals("composer.value()Sort",
                    document.get(SearchIndexFields.composerReading.value()));
        }

        @ReadingDecisions.Conditions.IndexScheme.WithoutJpLangProcessing
        @ReadingDecisions.Conditions.Value.NotNull.Japanese
        @Test
        void c09() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.advanced.index.indexSchemeName,
                        IndexScheme.WITHOUT_JP_LANG_PROCESSING.name())
                .withBoolean(SKeys.advanced.index.forceInternalValueInsteadOfTags, true)
                .build();
            documentFactory = new DocumentFactory(settingsFacade,
                    new JapaneseReadingUtils(settingsFacade));
            indexer = new Indexer(documentFactory, indexManager);

            MediaFile song = createSong();
            song.setArtist("アーティスト");
            song.setArtistSort("あーてぃすと");
            song.setComposer("さっきょくしゃ");
            song.setComposerSortRaw("サッキョクシャ");

            indexer.index(song);

            verify(indexManager)
                .updateDocument(eq(IndexType.SONG), any(Term.class), documentCaptor.capture());
            Document document = documentCaptor.getValue();

            assertEquals("アーティスト", document.get(SearchIndexFields.artist.value()));
            assertEquals("あーてぃすと", document.get(SearchIndexFields.artistReading.value()));
            assertEquals("さっきょくしゃ", document.get(SearchIndexFields.composer.value()));
            assertEquals("サッキョクシャ", document.get(SearchIndexFields.composerReading.value()));
        }
    }
}

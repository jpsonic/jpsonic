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

import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.tesshu.jpsonic.domain.system.IndexScheme;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.param.ShuffleSelectionParam;
import org.apache.lucene.search.Query;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;

/*
 * The query syntax has not changed significantly since Lucene 1.3. (A slight difference) If you
 * face a problem reaping from 3.x to 7.x It may be faster to look at the query than to look at the
 * API.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class QueryFactoryTest {

    private LuceneQueryBuilder queryBuilder;
    private QueryFactory queryFactory;

    private static final String SEPA = System.getProperty("file.separator");

    private static final String PATH1 = SEPA + "var" + SEPA + "music1";
    private static final String PATH2 = SEPA + "var" + SEPA + "music2";

    private static final int FID1 = 10;
    private static final int FID2 = 20;

    private static final MusicFolder MUSIC_FOLDER1 = new MusicFolder(FID1, PATH1, "music1", true,
            now(), 0, false);
    private static final MusicFolder MUSIC_FOLDER2 = new MusicFolder(FID2, PATH2, "music2", true,
            now(), 1, false);

    private static final List<MusicFolder> SINGLE_FOLDERS = Arrays.asList(MUSIC_FOLDER1);
    private static final List<MusicFolder> MULTI_FOLDERS = Arrays
        .asList(MUSIC_FOLDER1, MUSIC_FOLDER2);

    private static final com.tesshu.jpsonic.domain.model.MusicFolder DOMAIN_MUSIC_FOLDER1 = new com.tesshu.jpsonic.domain.model.MusicFolder(
            FID1, PATH1, "music1", true, null, 0, false);
    private static final com.tesshu.jpsonic.domain.model.MusicFolder DOMAIN_MUSIC_FOLDER2 = new com.tesshu.jpsonic.domain.model.MusicFolder(
            FID2, PATH2, "music2", true, null, 0, false);

    private static final List<com.tesshu.jpsonic.domain.model.MusicFolder> DOMAIN_SINGLE_FOLDERS = Arrays
        .asList(DOMAIN_MUSIC_FOLDER1);
    private static final List<com.tesshu.jpsonic.domain.model.MusicFolder> DOMAIN_MULTI_FOLDERS = Arrays
        .asList(DOMAIN_MUSIC_FOLDER1, DOMAIN_MUSIC_FOLDER2);

    private SettingsFacade settingsFacade;

    @BeforeEach
    void setup() {
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.advanced.index.indexSchemeName, IndexScheme.NATIVE_JAPANESE.name())
            .build();
        init();
    }

    @Ignore
    void init() {
        AnalyzerFactory analyzerFactory = new AnalyzerFactory(settingsFacade);
        queryBuilder = new LuceneQueryBuilder(analyzerFactory, settingsFacade);
        queryFactory = new QueryFactory(settingsFacade, analyzerFactory);
    }

    @Order(1)
    @Nested
    class FilterFieldsTest {

        @Test
        void testComposer() {
            // spotless:off
            String[] notContainsComposer = { SearchIndexFields.title.value(),
                    SearchIndexFields.titleReading.value(),
                    SearchIndexFields.artist.value(),
                    SearchIndexFields.artistReading.value() };
            // spotless:on

            assertArrayEquals(notContainsComposer,
                    queryBuilder
                        .filterFields(IndexType.SONG.getFields(), false)
                        .toArray(new String[0]));

            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(SKeys.general.search.searchComposer, true)
                .build();
            init();

            // spotless:off
            String[] containsComposer = { SearchIndexFields.title.value(),
                    SearchIndexFields.titleReading.value(),
                    SearchIndexFields.artist.value(),
                    SearchIndexFields.artistReading.value(),
                    SearchIndexFields.composer.value(),
                    SearchIndexFields.composerReading.value() };
            // spotless:on

            assertArrayEquals(containsComposer,
                    queryBuilder
                        .filterFields(IndexType.SONG.getFields(), true)
                        .toArray(new String[0]));
        }

        @Test
        void testScheme() {

            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.advanced.index.indexSchemeName,
                        IndexScheme.NATIVE_JAPANESE.name())
                .build();
            init();

            // spotless:off
            String[] notRomanize = { SearchIndexFields.title.value(),
                    SearchIndexFields.titleReading.value(),
                    SearchIndexFields.artist.value(),
                    SearchIndexFields.artistReading.value() };
            // spotless:on

            assertArrayEquals(notRomanize,
                    queryBuilder
                        .filterFields(IndexType.SONG.getFields(), false)
                        .toArray(new String[0]));

            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.advanced.index.indexSchemeName,
                        IndexScheme.WITHOUT_JP_LANG_PROCESSING.name())
                .build();
            init();

            assertArrayEquals(notRomanize,
                    queryBuilder
                        .filterFields(IndexType.SONG.getFields(), false)
                        .toArray(new String[0]));

            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.advanced.index.indexSchemeName,
                        IndexScheme.ROMANIZED_JAPANESE.name())
                .build();
            init();

            // spotless:off
            String[] romanize = { SearchIndexFields.title.value(),
                    SearchIndexFields.titleReading.value(),
                    SearchIndexFields.artist.value(),
                    SearchIndexFields.artistReading.value(),
                    SearchIndexFields.artistReadingRomanized.value() };
            // spotless:on

            assertArrayEquals(romanize,
                    queryBuilder
                        .filterFields(IndexType.SONG.getFields(), false)
                        .toArray(new String[0]));

            // and Composer
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.advanced.index.indexSchemeName,
                        IndexScheme.NATIVE_JAPANESE.name())
                .build();
            init();

            // spotless:off
            String[] notRomanizeAndCmp = { SearchIndexFields.title.value(),
                    SearchIndexFields.titleReading.value(),
                    SearchIndexFields.artist.value(),
                    SearchIndexFields.artistReading.value(),
                    SearchIndexFields.composer.value(),
                    SearchIndexFields.composerReading.value() };
            // spotless:on

            assertArrayEquals(notRomanizeAndCmp,
                    queryBuilder
                        .filterFields(IndexType.SONG.getFields(), true)
                        .toArray(new String[0]));

            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.advanced.index.indexSchemeName,
                        IndexScheme.WITHOUT_JP_LANG_PROCESSING.name())
                .build();
            init();
            assertArrayEquals(notRomanizeAndCmp,
                    queryBuilder
                        .filterFields(IndexType.SONG.getFields(), true)
                        .toArray(new String[0]));

            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.advanced.index.indexSchemeName,
                        IndexScheme.ROMANIZED_JAPANESE.name())
                .build();
            init();

            // spotless:off
            String[] romanizeAndCmp = { SearchIndexFields.title.value(),
                    SearchIndexFields.titleReading.value(),
                    SearchIndexFields.artist.value(),
                    SearchIndexFields.artistReading.value(),
                    SearchIndexFields.artistReadingRomanized.value(),
                    SearchIndexFields.composer.value(),
                    SearchIndexFields.composerReading.value(),
                    SearchIndexFields.composerReadingRomanized.value() };
            // spotless:on

            assertArrayEquals(romanizeAndCmp,
                    queryBuilder
                        .filterFields(IndexType.SONG.getFields(), true)
                        .toArray(new String[0]));
        }
    }

    /**
     * Related {@link UPnPSearchCriteriaDirectorTest}
     */
    @Order(2)
    @Test
    void testCreatePhraseQuery() throws IOException {
        assertEquals(
                "(tit:\"cats and dogs\"~1)^6.0 (art:\"cats and dogs\"~1)^4.0 (artR:\"cats and dogs\"~1)^4.2",
                queryFactory
                    .createPhraseQuery(IndexType.SONG.getFields(), "Cats and Dogs", IndexType.SONG)
                    .get()
                    .toString());
        assertEquals(
                "(tit:\"いぬ と ねこ\"~1)^6.0 (titR:\"いぬ ぬと とね ねこ\"~1)^6.2 (art:\"いぬ と ねこ\"~1)^4.0 (artR:\"いぬ ぬと とね ねこ\"~1)^4.2",
                queryFactory
                    .createPhraseQuery(IndexType.SONG.getFields(), "いぬとねこ", IndexType.SONG)
                    .get()
                    .toString());
    }

    @Order(3)
    @Test
    void testSearchByPhrase() throws IOException {

        assertEquals(
                "+((tit:\"cats and dogs\"~1)^6.0 (art:\"cats and dogs\"~1)^4.0 (artR:\"cats and dogs\"~1)^4.2) +(f:"
                        + PATH1 + ")",
                queryFactory
                    .searchByPhrase("Cats and Dogs", false, SINGLE_FOLDERS, IndexType.SONG)
                    .toString());
        assertEquals(
                "+((alb:\"cats and dogs\"~1)^4.0 art:\"cats and dogs\"~1 (artR:\"cats and dogs\"~1)^2.2) +(fId:"
                        + FID1 + ")",
                queryFactory
                    .searchByPhrase("Cats and Dogs", false, SINGLE_FOLDERS, IndexType.ALBUM_ID3)
                    .toString());
        assertEquals(
                "+(art:\"cats and dogs\"~1 (artR:\"cats and dogs\"~1)^2.2) +(fId:" + FID1 + ")",
                queryFactory
                    .searchByPhrase("Cats and Dogs", false, SINGLE_FOLDERS, IndexType.ARTIST_ID3)
                    .toString());
        assertEquals(
                "+((tit:\"いぬ と ねこ\"~1)^6.0 (titR:\"いぬ ぬと とね ねこ\"~1)^6.2 (art:\"いぬ と ねこ\"~1)^4.0 (artR:\"いぬ ぬと とね ねこ\"~1)^4.2)"
                        + " +(f:" + PATH1 + ")",
                queryFactory
                    .searchByPhrase("いぬとねこ", false, SINGLE_FOLDERS, IndexType.SONG)
                    .toString());

        settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.advanced.index.indexSchemeName,
                    IndexScheme.WITHOUT_JP_LANG_PROCESSING.name())
            .build();
        init();
        assertEquals(
                "+((tit:\"い ぬ と ね こ\"~1)^6.0 (titR:\"いぬ ぬと とね ねこ\"~1)^6.2 (art:\"い ぬ と ね こ\"~1)^4.0 (artR:\"いぬ ぬと とね ねこ\"~1)^4.2) +(f:"
                        + PATH1 + ")",
                queryFactory
                    .searchByPhrase("いぬとねこ", false, SINGLE_FOLDERS, IndexType.SONG)
                    .toString());

        settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.advanced.index.indexSchemeName, IndexScheme.ROMANIZED_JAPANESE.name())
            .build();
        init();
        String query = "Inu to Neko";

        assertEquals("+((tit:\"inu to neko\"~1)^6.0 " //
                + "(art:\"inu to neko\"~1)^4.0 " //
                + "(artR:\"inu to neko\"~1)^4.2 " //
                + "(artRR:inu artRR:to artRR:neko)^4.2) " //
                + "+(f:" + PATH1 + ")",
                queryFactory
                    .searchByPhrase(query, false, SINGLE_FOLDERS, IndexType.SONG)
                    .toString());

        settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.advanced.index.indexSchemeName, IndexScheme.ROMANIZED_JAPANESE.name())
            .withBoolean(SKeys.general.search.searchComposer, true)
            .build();
        init();
        assertEquals("+((tit:\"inu to neko\"~1)^6.0 " //
                + "(art:\"inu to neko\"~1)^4.0 " //
                + "(artR:\"inu to neko\"~1)^4.2 " //
                + "(artRR:inu artRR:to artRR:neko)^4.2 " //
                + "cmp:\"inu to neko\"~1 " //
                + "(cmpR:\"inu to neko\"~1)^2.2 " //
                + "(cmpRR:inu cmpRR:to cmpRR:neko)^2.2) " //
                + "+(f:" + PATH1 + ")",
                queryFactory
                    .searchByPhrase(query, false, SINGLE_FOLDERS, IndexType.SONG)
                    .toString());
    }

    @Order(4)
    @Test
    void testGetRandomSongs() throws IOException {
        ShuffleSelectionParam criteria = new ShuffleSelectionParam(50,
                Arrays.asList("Classic Rock"), 1900, 2000, SINGLE_FOLDERS);
        Query query = queryFactory.getRandomSongs(criteria);
        assertEquals("+m:MUSIC +(g:Classic Rock) +y:[1900 TO 2000] +(f:" + PATH1 + ")",
                query.toString());
        criteria = new ShuffleSelectionParam(50, Arrays.asList("Classic Rock"), 1900, 2000,
                MULTI_FOLDERS);
        query = queryFactory.getRandomSongs(criteria);
        assertEquals(
                "+m:MUSIC +(g:Classic Rock) +y:[1900 TO 2000] +(f:" + PATH1 + " f:" + PATH2 + ")",
                query.toString());
        criteria = new ShuffleSelectionParam(50, Arrays.asList("Classic Rock"), null, null,
                MULTI_FOLDERS);
        query = queryFactory.getRandomSongs(criteria);
        assertEquals("+m:MUSIC +(g:Classic Rock) +(f:" + PATH1 + " f:" + PATH2 + ")",
                query.toString());
        criteria = new ShuffleSelectionParam(50, Arrays.asList("Classic Rock"), 1900, null,
                MULTI_FOLDERS);
        query = queryFactory.getRandomSongs(criteria);
        assertEquals("+m:MUSIC +(g:Classic Rock) +y:[1900 TO 2147483647] +(f:" + PATH1 + " f:"
                + PATH2 + ")", query.toString());
        criteria = new ShuffleSelectionParam(50, Arrays.asList("Classic Rock"), null, 2000,
                MULTI_FOLDERS);
        query = queryFactory.getRandomSongs(criteria);
        assertEquals("+m:MUSIC +(g:Classic Rock) +y:[-2147483648 TO 2000] +(f:" + PATH1 + " f:"
                + PATH2 + ")", query.toString());

        criteria = new ShuffleSelectionParam(50, Arrays.asList("Classic Rock", "Rock & Roll"), 1900,
                2000, SINGLE_FOLDERS);
        query = queryFactory.getRandomSongs(criteria);
        assertEquals(
                "+m:MUSIC +(g:Classic Rock g:Rock & Roll) +y:[1900 TO 2000] +(f:" + PATH1 + ")",
                query.toString(), "multi genre");

        criteria = new ShuffleSelectionParam(50, null, 1900, 2000, SINGLE_FOLDERS);
        query = queryFactory.getRandomSongs(criteria);
        assertEquals("+m:MUSIC +y:[1900 TO 2000] +(f:" + PATH1 + ")", query.toString(),
                "null genre");
    }

    @Order(5)
    @Test
    void testGetRandomSongsByMusicFolder() throws IOException {
        Query query = queryFactory.getRandomSongs(DOMAIN_SINGLE_FOLDERS);
        assertEquals("+m:MUSIC +(f:" + PATH1 + ")", query.toString());
        query = queryFactory.getRandomSongs(DOMAIN_MULTI_FOLDERS);
        assertEquals("+m:MUSIC +(f:" + PATH1 + " f:" + PATH2 + ")", query.toString());
    }

    @Order(6)
    @Test
    void testGetRandomSongsByMusicFolderAndGenre() throws IOException {
        Query query = queryFactory.getRandomSongs(DOMAIN_SINGLE_FOLDERS, "Rock & Roll", "Pop/Funk");
        assertEquals("+m:MUSIC +(f:" + PATH1 + ") +(g:Rock & Roll g:Pop/Funk)", query.toString());
        query = queryFactory.getRandomSongs(DOMAIN_MULTI_FOLDERS, "Rock & Roll", "Pop/Funk");
        assertEquals("+m:MUSIC +(f:" + PATH1 + " f:" + PATH2 + ") +(g:Rock & Roll g:Pop/Funk)",
                query.toString());
    }

    @Order(7)
    @Test
    void testGetRandomAlbums() {
        Query query = queryFactory.getRandomAlbums(SINGLE_FOLDERS);
        assertEquals("(f:" + PATH1 + ")", query.toString());
        query = queryFactory.getRandomAlbums(MULTI_FOLDERS);
        assertEquals("(f:" + PATH1 + " f:" + PATH2 + ")", query.toString());
    }

    @Order(8)
    @Test
    void testGetRandomAlbumsId3() {
        Query query = queryFactory.getRandomAlbumsId3(SINGLE_FOLDERS);
        assertEquals("(fId:" + FID1 + ")", query.toString());
        query = queryFactory.getRandomAlbumsId3(MULTI_FOLDERS);
        assertEquals("(fId:" + FID1 + " fId:" + FID2 + ")", query.toString());
    }

    @Order(9)
    @Test
    void testGetAlbumId3sByGenre() throws IOException {
        Query query = queryFactory.getAlbumId3sByGenres("Instrumental pop", SINGLE_FOLDERS);
        assertEquals("+(g:Instrumental pop) +(fId:" + FID1 + ")", query.toString());
        query = queryFactory.getAlbumId3sByGenres("Rock & Roll", MULTI_FOLDERS);
        assertEquals("+(g:Rock & Roll) +(fId:" + FID1 + " fId:" + FID2 + ")", query.toString());
        query = queryFactory.getAlbumId3sByGenres("Pop;Pop/Funk", MULTI_FOLDERS);
        assertEquals("+(g:Pop g:Pop/Funk) +(fId:" + FID1 + " fId:" + FID2 + ")", query.toString());
        query = queryFactory.getAlbumId3sByGenres("", MULTI_FOLDERS);
        assertEquals("+(fId:" + FID1 + " fId:" + FID2 + ")", query.toString());
    }

    @Order(10)
    @Test
    void testGetMediasByGenre() throws IOException {
        Query query = queryFactory.getMediasByGenres("Instrumental pop", SINGLE_FOLDERS);
        assertEquals("+(g:Instrumental pop) +(f:" + PATH1 + ")", query.toString());
        query = queryFactory.getMediasByGenres("Rock & Roll", MULTI_FOLDERS);
        assertEquals("+(g:Rock & Roll) +(f:" + PATH1 + " f:" + PATH2 + ")", query.toString());
        query = queryFactory.getMediasByGenres("Pop;Pop/Funk", MULTI_FOLDERS);
        assertEquals("+(g:Pop g:Pop/Funk) +(f:" + PATH1 + " f:" + PATH2 + ")", query.toString());
        query = queryFactory.getMediasByGenres("Pop;Pop/Funk;Rock & Roll", MULTI_FOLDERS);
        assertEquals("+(g:Pop g:Pop/Funk g:Rock & Roll) +(f:" + PATH1 + " f:" + PATH2 + ")",
                query.toString(), "multi genre");
        query = queryFactory.getMediasByGenres("", MULTI_FOLDERS);
        assertEquals("+(f:" + PATH1 + " f:" + PATH2 + ")", query.toString(), "null genre");
    }

    @Order(11)
    @Test
    void testToPreAnalyzedGenres() throws IOException {
        Query query = queryBuilder.toPreAnalyzedGenres(Arrays.asList("Classic Rock"));
        assertEquals("+(g:Classic Rock)", query.toString(), "genre");
        query = queryBuilder.toPreAnalyzedGenres(Arrays.asList("Classic Rock", "Rock & Roll"));
        assertEquals("+(g:Classic Rock g:Rock & Roll)", query.toString(), "multi genres");
        query = queryBuilder.toPreAnalyzedGenres(Arrays.asList(""));
        assertEquals("+()", query.toString(), "multi genres");
    }
}

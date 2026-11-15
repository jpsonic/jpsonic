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

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.Genre;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterCriteria;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterProvider;
import com.tesshu.jpsonic.domain.provider.resource.ServerLocaleProvider;
import com.tesshu.jpsonic.domain.type.GenreMasterScope;
import com.tesshu.jpsonic.domain.type.GenreMasterSort;
import com.tesshu.jpsonic.infrastructure.comparator.ComparatorsFacade;
import com.tesshu.jpsonic.infrastructure.language.I18nSKeys;
import com.tesshu.jpsonic.infrastructure.language.JapaneseReadingUtils;
import com.tesshu.jpsonic.infrastructure.language.MetadataReadingProcessor;
import com.tesshu.jpsonic.infrastructure.locale.ServerLocaleManager;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.NeedsDB;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class GenreMasterProviderAdapterTest {

    @Documented
    private @interface GetGenresDecisions {
        @interface Conditions {
            @interface Settings {
                @interface IsSortGenresByAlphabet {
                    @interface FALSE {
                    }

                    @interface TRUE {
                    }
                }
            }

            @interface Params {
                @interface SortByAlbum {
                    @interface FALSE {
                    }

                    @interface TRUE {
                    }
                }
            }
        }

        @interface Result {
            @interface GenreSort {
                @interface SongCount {

                }

                @interface AlbumCount {

                }

                @interface SongAlphabetical {
                }

                @interface AlbumAlphabetical {
                }
            }
        }
    }

    @Nested
    @NeedsDB
    @SuppressWarnings("PMD.UnitTestShouldIncludeAssert")
    @Order(1)
    class GetGenresTest {

        private SettingsFacade settingsFacade;
        private GenreMasterProvider genreMasterProvider;
        private GenreMasterProviderAdapter adapter;

        @BeforeEach
        void setup() {
            settingsFacade = SettingsFacadeBuilder.create().build();
            init();
        }

        @Ignore
        void init() {
            AnalyzerFactory analyzerFactory = new AnalyzerFactory(settingsFacade);
            LuceneQueryBuilder queryFactory = new LuceneQueryBuilder(analyzerFactory,
                    settingsFacade);
            JapaneseReadingUtils readingUtils = new JapaneseReadingUtils(settingsFacade);
            MetadataReadingProcessor proc = new MetadataReadingProcessor(settingsFacade,
                    readingUtils);
            ServerLocaleProvider serverLocaleProvider = new ServerLocaleManager(settingsFacade);
            ComparatorsFacade comparatorsFacade = new ComparatorsFacade(settingsFacade, serverLocaleProvider, proc);
            IndexManager indexManager = new IndexManager(null, null, null);
            adapter = new GenreMasterProviderAdapter(indexManager, queryFactory, proc,
                    comparatorsFacade, settingsFacade, mock(InternalSearchCache.class));
            genreMasterProvider = adapter;
        }

        @Test
        void testGetGenre() throws IOException {
            assertEquals("g:Classic Rock", adapter.getGenreQuery("Classic Rock").toString());
        }

        @GetGenresDecisions.Conditions.Settings.IsSortGenresByAlphabet.FALSE
        @GetGenresDecisions.Conditions.Params.SortByAlbum.FALSE
        @GetGenresDecisions.Result.GenreSort.SongCount
        @Test
        void c00() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(SKeys.general.sort.genresByAlphabet, false)
                .build();
            init();

            genreMasterProvider.getLegacyGenres(false, 0, Integer.MAX_VALUE);
        }

        @GetGenresDecisions.Conditions.Settings.IsSortGenresByAlphabet.FALSE
        @GetGenresDecisions.Conditions.Params.SortByAlbum.TRUE
        @GetGenresDecisions.Result.GenreSort.AlbumCount
        @Test
        void c01() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(SKeys.general.sort.genresByAlphabet, false)
                .build();
            init();

            genreMasterProvider.getLegacyGenres(true, 0, Integer.MAX_VALUE);
        }

        @GetGenresDecisions.Conditions.Settings.IsSortGenresByAlphabet.TRUE
        @GetGenresDecisions.Conditions.Params.SortByAlbum.FALSE
        @GetGenresDecisions.Result.GenreSort.SongAlphabetical
        @Test
        void c02() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(I18nSKeys.localeLanguage, Locale.ROOT.getLanguage())
                .withString(I18nSKeys.localeCountry, Locale.ROOT.getCountry())
                .withString(I18nSKeys.localeVariant, Locale.ROOT.getVariant())
                .withBoolean(SKeys.general.sort.genresByAlphabet, true)
                .build();
            init();

            genreMasterProvider.getLegacyGenres(false, 0, Integer.MAX_VALUE);
        }

        @GetGenresDecisions.Conditions.Settings.IsSortGenresByAlphabet.TRUE
        @GetGenresDecisions.Conditions.Params.SortByAlbum.TRUE
        @GetGenresDecisions.Result.GenreSort.AlbumAlphabetical
        @Test
        void c03() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(I18nSKeys.localeLanguage, Locale.ROOT.getLanguage())
                .withString(I18nSKeys.localeCountry, Locale.ROOT.getCountry())
                .withString(I18nSKeys.localeVariant, Locale.ROOT.getVariant())
                .withBoolean(SKeys.general.sort.genresByAlphabet, true)
                .build();
            init();

            genreMasterProvider.getLegacyGenres(true, 0, Integer.MAX_VALUE);
        }
    }

    @Documented
    private @interface CreateGenreMasterDecisions {
        @interface Conditions {
            @interface Criteria {
                @interface Scope {
                    @interface Album {
                    }

                    @interface Song {
                    }
                }

                @interface Sort {
                    @interface Frequency {
                    }

                    @interface Name {
                    }

                    @interface AlbumCount {
                    }

                    @interface SongCount {
                    }
                }
            }
        }
    }

    /*
     * The implementation is poor and difficult to assert. This is a coverage test
     * for later fix.
     */
    @Nested
    @Order(3)
    class CreateGenreMasterTest extends AbstractNeedsScan {

        @Autowired
        private GenreMasterProviderAdapter genreMasterProviderAdapter;

        private final List<MusicFolder> musicFolders = Arrays
            .asList(new MusicFolder(1, resolveBaseMediaPath("MultiGenre"), "MultiGenre", true,
                    now(), 1, false));
        private final List<com.tesshu.jpsonic.domain.model.MusicFolder> domainMusicFolders = Arrays
            .asList(new com.tesshu.jpsonic.domain.model.MusicFolder(1,
                    resolveBaseMediaPath("MultiGenre"), "MultiGenre", true, now(), 1, false));

        private static boolean populated;

        @Override
        public List<MusicFolder> getMusicFolders() {
            return musicFolders;
        }

        @BeforeEach
        void setup() {
            if (!populated) {
                populateDatabase();
                populated = true;
            }
        }

        @CreateGenreMasterDecisions.Conditions.Criteria.Scope.Album
        @CreateGenreMasterDecisions.Conditions.Criteria.Sort.Name
        @Test
        void c00() {
            GenreMasterCriteria criteria = new GenreMasterCriteria(domainMusicFolders,
                    GenreMasterScope.ALBUM, GenreMasterSort.NAME);
            List<com.tesshu.jpsonic.domain.model.Genre> genres = genreMasterProviderAdapter
                .createGenreMaster(criteria);
            assertEquals(14, genres.size());
            assertEquals("Audiobook - Historical", genres.get(0).name());
            assertEquals("Audiobook - Sports", genres.get(1).name());
            assertEquals("GENRE_A", genres.get(2).name());
            assertEquals("GENRE_B", genres.get(3).name());
            assertEquals("GENRE_C", genres.get(4).name());
            assertEquals("GENRE_D", genres.get(5).name());
            assertEquals("GENRE_E", genres.get(6).name());
            assertEquals("GENRE_F", genres.get(7).name());
            assertEquals("GENRE_G", genres.get(8).name());
            assertEquals("GENRE_H", genres.get(9).name());
            assertEquals("GENRE_I", genres.get(10).name());
            assertEquals("GENRE_J", genres.get(11).name());
            assertEquals("GENRE_K", genres.get(12).name());
            assertEquals("GENRE_L", genres.get(13).name());
        }

        @CreateGenreMasterDecisions.Conditions.Criteria.Scope.Album
        @CreateGenreMasterDecisions.Conditions.Criteria.Sort.AlbumCount
        @Test
        void c01() {
            GenreMasterCriteria criteria = new GenreMasterCriteria(domainMusicFolders,
                    GenreMasterScope.ALBUM, GenreMasterSort.ALBUM_COUNT);
            List<com.tesshu.jpsonic.domain.model.Genre> genres = genreMasterProviderAdapter
                .createGenreMaster(criteria);
            assertEquals(14, genres.size());
            assertEquals("GENRE_D", genres.get(0).name());
            assertEquals("GENRE_K", genres.get(1).name());
            assertEquals("GENRE_L", genres.get(2).name());
            assertEquals("Audiobook - Historical", genres.get(3).name());
            assertEquals("Audiobook - Sports", genres.get(4).name());
            assertEquals("GENRE_A", genres.get(5).name());
            assertEquals("GENRE_B", genres.get(6).name());
            assertEquals("GENRE_C", genres.get(7).name());
            assertEquals("GENRE_E", genres.get(8).name());
            assertEquals("GENRE_F", genres.get(9).name());
            assertEquals("GENRE_G", genres.get(10).name());
            assertEquals("GENRE_H", genres.get(11).name());
            assertEquals("GENRE_I", genres.get(12).name());
            assertEquals("GENRE_J", genres.get(13).name());
            assertEquals(2, genres.get(0).albumCount());
            assertEquals(2, genres.get(1).albumCount());
            assertEquals(2, genres.get(2).albumCount());
            assertEquals(1, genres.get(3).albumCount());
            assertEquals(1, genres.get(4).albumCount());
            assertEquals(1, genres.get(5).albumCount());
            assertEquals(1, genres.get(6).albumCount());
            assertEquals(1, genres.get(7).albumCount());
            assertEquals(1, genres.get(8).albumCount());
            assertEquals(1, genres.get(9).albumCount());
            assertEquals(1, genres.get(10).albumCount());
            assertEquals(1, genres.get(11).albumCount());
            assertEquals(1, genres.get(12).albumCount());
            assertEquals(1, genres.get(13).albumCount());
        }

        @CreateGenreMasterDecisions.Conditions.Criteria.Scope.Album
        @CreateGenreMasterDecisions.Conditions.Criteria.Sort.SongCount
        @Test
        void c02() {
            GenreMasterCriteria criteria = new GenreMasterCriteria(domainMusicFolders,
                    GenreMasterScope.ALBUM, GenreMasterSort.SONG_COUNT);
            List<com.tesshu.jpsonic.domain.model.Genre> genres = genreMasterProviderAdapter
                .createGenreMaster(criteria);
            assertEquals(14, genres.size());
            assertEquals("GENRE_A", genres.get(0).name());
            assertEquals("GENRE_D", genres.get(1).name());
            assertEquals("GENRE_E", genres.get(2).name());
            assertEquals("GENRE_F", genres.get(3).name());
            assertEquals("GENRE_K", genres.get(4).name());
            assertEquals("GENRE_L", genres.get(5).name());
            assertEquals("Audiobook - Historical", genres.get(6).name());
            assertEquals("Audiobook - Sports", genres.get(7).name());
            assertEquals("GENRE_B", genres.get(8).name());
            assertEquals("GENRE_C", genres.get(9).name());
            assertEquals("GENRE_G", genres.get(10).name());
            assertEquals("GENRE_H", genres.get(11).name());
            assertEquals("GENRE_I", genres.get(12).name());
            assertEquals("GENRE_J", genres.get(13).name());
            assertEquals(2, genres.get(0).songCount());
            assertEquals(2, genres.get(1).songCount());
            assertEquals(2, genres.get(2).songCount());
            assertEquals(2, genres.get(3).songCount());
            assertEquals(2, genres.get(4).songCount());
            assertEquals(2, genres.get(5).songCount());
            assertEquals(1, genres.get(6).songCount());
            assertEquals(1, genres.get(7).songCount());
            assertEquals(1, genres.get(8).songCount());
            assertEquals(1, genres.get(9).songCount());
            assertEquals(1, genres.get(10).songCount());
            assertEquals(1, genres.get(11).songCount());
            assertEquals(1, genres.get(12).songCount());
            assertEquals(1, genres.get(13).songCount());
        }

        @CreateGenreMasterDecisions.Conditions.Criteria.Scope.Song
        @CreateGenreMasterDecisions.Conditions.Criteria.Sort.Name
        @Test
        void c03() {
            GenreMasterCriteria criteria = new GenreMasterCriteria(domainMusicFolders,
                    GenreMasterScope.SONG, GenreMasterSort.NAME,
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.MUSIC);
            List<com.tesshu.jpsonic.domain.model.Genre> genres = genreMasterProviderAdapter
                .createGenreMaster(criteria);
            assertEquals(13, genres.size());
            assertEquals("GENRE_A", genres.get(0).name());
            assertEquals("GENRE_B", genres.get(1).name());
            assertEquals("GENRE_C", genres.get(2).name());
            assertEquals("GENRE_D", genres.get(3).name());
            assertEquals("GENRE_E", genres.get(4).name());
            assertEquals("GENRE_F", genres.get(5).name());
            assertEquals("GENRE_G", genres.get(6).name());
            assertEquals("GENRE_H", genres.get(7).name());
            assertEquals("GENRE_I", genres.get(8).name());
            assertEquals("GENRE_J", genres.get(9).name());
            assertEquals("GENRE_K", genres.get(10).name());
            assertEquals("GENRE_L", genres.get(11).name());
            assertEquals("NO_ALBUM", genres.get(12).name());
        }

        @CreateGenreMasterDecisions.Conditions.Criteria.Scope.Song
        @CreateGenreMasterDecisions.Conditions.Criteria.Sort.SongCount
        @Test
        void c04() {
            GenreMasterCriteria criteria = new GenreMasterCriteria(domainMusicFolders,
                    GenreMasterScope.SONG, GenreMasterSort.SONG_COUNT,
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.MUSIC);
            List<com.tesshu.jpsonic.domain.model.Genre> genres = genreMasterProviderAdapter
                .createGenreMaster(criteria);
            assertEquals(13, genres.size());
            assertEquals("GENRE_A", genres.get(0).name());
            assertEquals("GENRE_D", genres.get(1).name());
            assertEquals("GENRE_E", genres.get(2).name());
            assertEquals("GENRE_F", genres.get(3).name());
            assertEquals("GENRE_K", genres.get(4).name());
            assertEquals("GENRE_L", genres.get(5).name());
            assertEquals("GENRE_B", genres.get(6).name());
            assertEquals("GENRE_C", genres.get(7).name());
            assertEquals("GENRE_G", genres.get(8).name());
            assertEquals("GENRE_H", genres.get(9).name());
            assertEquals("GENRE_I", genres.get(10).name());
            assertEquals("GENRE_J", genres.get(11).name());
            assertEquals("NO_ALBUM", genres.get(12).name());
            assertEquals(2, genres.get(0).songCount());
            assertEquals(2, genres.get(1).songCount());
            assertEquals(2, genres.get(2).songCount());
            assertEquals(2, genres.get(3).songCount());
            assertEquals(2, genres.get(4).songCount());
            assertEquals(2, genres.get(5).songCount());
            assertEquals(1, genres.get(6).songCount());
            assertEquals(1, genres.get(7).songCount());
            assertEquals(1, genres.get(8).songCount());
            assertEquals(1, genres.get(9).songCount());
            assertEquals(1, genres.get(10).songCount());
            assertEquals(1, genres.get(11).songCount());
            assertEquals(1, genres.get(12).songCount());
        }

        @CreateGenreMasterDecisions.Conditions.Criteria.Scope.Song
        @CreateGenreMasterDecisions.Conditions.Criteria.Sort.Name
        @Test
        void c05() {
            GenreMasterCriteria criteria = new GenreMasterCriteria(domainMusicFolders,
                    GenreMasterScope.SONG, GenreMasterSort.NAME,
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.MUSIC,
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.AUDIOBOOK);
            List<com.tesshu.jpsonic.domain.model.Genre> genres = genreMasterProviderAdapter
                .createGenreMaster(criteria);
            assertEquals(15, genres.size());
            assertEquals("Audiobook - Historical", genres.get(0).name());
            assertEquals("Audiobook - Sports", genres.get(1).name());
            assertEquals("GENRE_A", genres.get(2).name());
            assertEquals("GENRE_B", genres.get(3).name());
            assertEquals("GENRE_C", genres.get(4).name());
            assertEquals("GENRE_D", genres.get(5).name());
            assertEquals("GENRE_E", genres.get(6).name());
            assertEquals("GENRE_F", genres.get(7).name());
            assertEquals("GENRE_G", genres.get(8).name());
            assertEquals("GENRE_H", genres.get(9).name());
            assertEquals("GENRE_I", genres.get(10).name());
            assertEquals("GENRE_J", genres.get(11).name());
            assertEquals("GENRE_K", genres.get(12).name());
            assertEquals("GENRE_L", genres.get(13).name());
            assertEquals("NO_ALBUM", genres.get(14).name());
        }

        @CreateGenreMasterDecisions.Conditions.Criteria.Scope.Album
        @CreateGenreMasterDecisions.Conditions.Criteria.Sort.Frequency
        @Test
        void c06() {
            GenreMasterCriteria criteria = new GenreMasterCriteria(domainMusicFolders,
                    GenreMasterScope.ALBUM, GenreMasterSort.FREQUENCY,
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.MUSIC,
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.AUDIOBOOK);
            List<com.tesshu.jpsonic.domain.model.Genre> genres = genreMasterProviderAdapter
                .createGenreMaster(criteria);
            assertEquals(14, genres.size());
            for (int i = 0; i < genres.size(); i++) {
                if (i < 3) {
                    assertTrue("GENRE_D".equals(genres.get(i).name())
                            || "GENRE_K".equals(genres.get(i).name())
                            || "GENRE_L".equals(genres.get(i).name()));
                    assertEquals(2, genres.get(i).albumCount());
                } else {
                    assertEquals(1, genres.get(i).albumCount());
                }
            }
        }

        @CreateGenreMasterDecisions.Conditions.Criteria.Scope.Song
        @CreateGenreMasterDecisions.Conditions.Criteria.Sort.Frequency
        @Test
        void c07() {
            GenreMasterCriteria criteria = new GenreMasterCriteria(domainMusicFolders,
                    GenreMasterScope.SONG, GenreMasterSort.FREQUENCY,
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.MUSIC,
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.AUDIOBOOK);
            List<com.tesshu.jpsonic.domain.model.Genre> genres = genreMasterProviderAdapter
                .createGenreMaster(criteria);
            assertEquals(15, genres.size());
            for (int i = 0; i < genres.size(); i++) {
                if (i < 6) {
                    assertTrue("GENRE_A".equals(genres.get(i).name())
                            || "GENRE_D".equals(genres.get(i).name())
                            || "GENRE_E".equals(genres.get(i).name())
                            || "GENRE_F".equals(genres.get(i).name())
                            || "GENRE_K".equals(genres.get(i).name())
                            || "GENRE_L".equals(genres.get(i).name()));
                    assertEquals(2, genres.get(i).songCount());
                } else {
                    assertEquals(1, genres.get(i).songCount());
                }
            }
        }
        
        
        @GetGenresDecisions.Conditions.Settings.IsSortGenresByAlphabet.TRUE
        @GetGenresDecisions.Conditions.Params.SortByAlbum.TRUE
        @GetGenresDecisions.Result.GenreSort.AlbumAlphabetical
        @Test
        void testSkipOffset() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(I18nSKeys.localeLanguage, Locale.ROOT.getLanguage())
                .withString(I18nSKeys.localeCountry, Locale.ROOT.getCountry())
                .withString(I18nSKeys.localeVariant, Locale.ROOT.getVariant())
                .withBoolean(SKeys.general.sort.genresByAlphabet, true)
                .build();
            init();

            List<Genre> genres = genreMasterProviderAdapter.getLegacyGenres(true, 0, Integer.MAX_VALUE);
            assertEquals(11, genres.size());
            assertEquals("Audiobook - Historical", genres.get(0).name());
            assertEquals("GENRE_A", genres.get(1).name());
            assertEquals("GENRE_B", genres.get(2).name());
            assertEquals("GENRE_D", genres.get(3).name());
            assertEquals("GENRE_E", genres.get(4).name());
            assertEquals("GENRE_F", genres.get(5).name());
            assertEquals("GENRE_G", genres.get(6).name());
            assertEquals("GENRE_H", genres.get(7).name());
            assertEquals("GENRE_K", genres.get(8).name());
            assertEquals("GENRE_L", genres.get(9).name());
            assertEquals("NO_ALBUM", genres.get(10).name());
            
            genres = genreMasterProviderAdapter.getLegacyGenres(true, 1, 1);
            assertEquals(1, genres.size());
            assertEquals("GENRE_A", genres.get(0).name());
            
            genres = genreMasterProviderAdapter.getLegacyGenres(true, 10, 10);
            assertEquals(1, genres.size());
            assertEquals("NO_ALBUM", genres.get(0).name());

            genres = genreMasterProviderAdapter.getLegacyGenres(false, 0, Integer.MAX_VALUE);
            assertEquals(15, genres.size());
            assertEquals("Audiobook - Historical", genres.get(0).name());
            assertEquals("Audiobook - Sports", genres.get(1).name());
            assertEquals("GENRE_A", genres.get(2).name());
            assertEquals("GENRE_B", genres.get(3).name());
            assertEquals("GENRE_C", genres.get(4).name());
            assertEquals("GENRE_D", genres.get(5).name());
            assertEquals("GENRE_E", genres.get(6).name());
            assertEquals("GENRE_F", genres.get(7).name());
            assertEquals("GENRE_G", genres.get(8).name());
            assertEquals("GENRE_H", genres.get(9).name());
            assertEquals("GENRE_I", genres.get(10).name());
            assertEquals("GENRE_J", genres.get(11).name());
            assertEquals("GENRE_K", genres.get(12).name());
            assertEquals("GENRE_L", genres.get(13).name());
            assertEquals("NO_ALBUM", genres.get(14).name());
            
            genres = genreMasterProviderAdapter.getLegacyGenres(false, 1, 1);
            assertEquals(1, genres.size());
            assertEquals("Audiobook - Sports", genres.get(0).name());
            
            genres = genreMasterProviderAdapter.getLegacyGenres(false, 10, 10);
            assertEquals(5, genres.size());
            assertEquals("GENRE_I", genres.get(0).name());

        
        }
    }
}

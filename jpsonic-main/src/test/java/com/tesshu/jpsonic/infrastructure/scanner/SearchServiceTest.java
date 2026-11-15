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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.Genre;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterProvider;
import com.tesshu.jpsonic.infrastructure.search.LegacySearch;
import com.tesshu.jpsonic.infrastructure.search.LegacySearchResult;
import com.tesshu.jpsonic.infrastructure.search.criteria.HttpSearchCriteria;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.AlbumDao;
import com.tesshu.jpsonic.persistence.api.repository.MusicFolderDao;
import com.tesshu.jpsonic.persistence.param.ShuffleSelectionParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("PMD.AvoidDuplicateLiterals") // In the testing class, it may be less readable.
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class SearchServiceTest extends AbstractNeedsScan {

    private static final Logger LOG = LoggerFactory.getLogger(SearchServiceTest.class);

    @Autowired
    private AlbumDao albumDao;

    private final MetricRegistry metrics = new MetricRegistry();

    @Autowired
    private MusicFolderDao musicFolderDao;

    @Autowired
    private MediaSearchProvider mediaSearchProvider;
    @Autowired
    private GenreMasterProvider genreMasterProvider;
    @Autowired
    private LegacySearch legacySearch;

    @Autowired
    private HttpSearchCriteriaDirector director;

    @BeforeEach
    void setup() {
        populateDatabaseOnlyOnce();
    }

    @Test
    void testSearchTypical() throws IOException {

        /*
         * A simple test that is expected to easily detect API syntax differences when
         * updating lucene. Complete route coverage and data coverage in this case alone
         * are not conscious.
         */

        List<MusicFolder> allMusicFolders = musicFolderDao.getAllMusicFolders();
        assertEquals(3, allMusicFolders.size());

        // *** testSearch() ***

        int offset = 0;
        int count = Integer.MAX_VALUE;

        /*
         * _ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble Should find any version of
         * Lucene.
         */
        HttpSearchCriteria searchCriteria = director
            .construct("Sarah Walker", offset, count, false, allMusicFolders, IndexType.ALBUM);
        LegacySearchResult result = legacySearch.search(searchCriteria);
        assertEquals(1, result.getTotalHits(),
                "(0) Specify '" + searchCriteria.input() + "' as query, total Hits is");
        assertEquals(0, result.getArtists().size(),
                "(1) Specify artist '" + searchCriteria.input() + "' as query. Artist SIZE is");
        assertEquals(0, result.getAlbums().size(),
                "(2) Specify artist '" + searchCriteria.input() + "' as query. Album SIZE is");
        assertEquals(1, result.getMediaFiles().size(),
                "(3) Specify artist '" + searchCriteria.input() + "' as query, MediaFile SIZE is");
        assertEquals(MediaType.ALBUM, result.getMediaFiles().get(0).getMediaType(), "(4) ");
        assertEquals("_ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble",
                result.getMediaFiles().get(0).getArtist(), "(5) Specify artist '"
                        + searchCriteria.input() + "' as query, and get a album. Name is ");
        assertEquals("_ID3_ALBUM_ Ravel - Chamber Music With Voice",
                result.getMediaFiles().get(0).getAlbumName(), "(6) Specify artist '"
                        + searchCriteria.input() + "' as query, and get a album. Name is ");

        /*
         * _ID3_ALBUM_ Ravel - Chamber Music With Voice Should find any version of
         * Lucene.
         */
        searchCriteria = director
            .construct("music", offset, count, false, allMusicFolders, IndexType.ALBUM_ID3);
        result = legacySearch.search(searchCriteria);
        assertEquals(1, result.getTotalHits(),
                "Specify '" + searchCriteria.input() + "' as query, total Hits is");
        assertEquals(0, result.getArtists().size(), "(7) Specify '" + searchCriteria.input()
                + "' as query, and get a song. Artist SIZE is ");
        assertEquals(1, result.getAlbums().size(), "(8) Specify '" + searchCriteria.input()
                + "' as query, and get a song. Album SIZE is ");
        assertEquals(0, result.getMediaFiles().size(), "(9) Specify '" + searchCriteria.input()
                + "' as query, and get a song. MediaFile SIZE is ");
        assertEquals("_ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble",
                result.getAlbums().get(0).getArtist(),
                "(9) Specify '" + searchCriteria.input() + "' as query, and get a album. Name is ");
        assertEquals("_ID3_ALBUM_ Ravel - Chamber Music With Voice",
                result.getAlbums().get(0).getName(), "(10) Specify '" + searchCriteria.input()
                        + "' as query, and get a album. Name is ");

        /*
         * _ID3_ALBUM_ Ravel - Chamber Music With Voice Should find any version of
         * Lucene.
         */
        searchCriteria = director
            .construct("Ravel - Chamber Music", offset, count, false, allMusicFolders,
                    IndexType.SONG);
        result = legacySearch.search(searchCriteria);

        assertEquals(0, result.getTotalHits(),
                "(11) Specify album '" + searchCriteria.input() + "' as query, total Hits is");
        assertEquals(0, result.getArtists().size(), "(12) Specify album '" + searchCriteria.input()
                + "', and get a song. Artist SIZE is");
        assertEquals(0, result.getAlbums().size(), "(13) Specify album '" + searchCriteria.input()
                + "', and get a song. Album SIZE is");
        assertEquals(0, result.getMediaFiles().size(), "(14) Specify album '"
                + searchCriteria.input() + "', and get a song. MediaFile SIZE is");

        // *** testGetRandomSongs() ***

        /*
         * Regardless of the Lucene version, ShuffleSelectionParam can specify null and
         * means the maximum range. 11 should be obtainable.
         */
        ShuffleSelectionParam shuffleSelectionParam = new ShuffleSelectionParam(Integer.MAX_VALUE, // count
                null, // genre,
                null, // fromYear
                null, // toYear
                allMusicFolders // musicFolders
        );
        List<MediaFile> allRandomSongs = mediaSearchProvider.getRandomSongs(shuffleSelectionParam);
        assertEquals(11, allRandomSongs.size(),
                "(22) Specify MAX_VALUE as the upper limit, and randomly acquire songs.");

        /*
         * Regardless of the Lucene version, 7 should be obtainable.
         */
        shuffleSelectionParam = new ShuffleSelectionParam(Integer.MAX_VALUE, // count
                null, // genre,
                1900, // fromYear
                null, // toYear
                allMusicFolders // musicFolders
        );
        allRandomSongs = mediaSearchProvider.getRandomSongs(shuffleSelectionParam);
        assertEquals(7, allRandomSongs.size(),
                "(23) Specify 1900 as 'fromYear', and randomly acquire songs.");

        /*
         * Regardless of the Lucene version, It should be 0 because it is a non-existent
         * genre.
         */
        shuffleSelectionParam = new ShuffleSelectionParam(Integer.MAX_VALUE, // count
                Arrays.asList("Alternative/Indie"), // genre,
                null, // fromYear
                null, // toYear
                allMusicFolders // musicFolders
        );

        allRandomSongs = mediaSearchProvider.getRandomSongs(shuffleSelectionParam);
        assertEquals(1, allRandomSongs.size(),
                "(24) Specify music as 'genre', and randomly acquire songs.");

        /*
         * Genre including blank. Regardless of the Lucene version, It should be 2.
         */
        shuffleSelectionParam = new ShuffleSelectionParam(Integer.MAX_VALUE, // count
                Arrays.asList("Baroque Instrumental"), // genre,
                null, // fromYear
                null, // toYear
                allMusicFolders // musicFolders
        );
        allRandomSongs = mediaSearchProvider.getRandomSongs(shuffleSelectionParam);
        assertEquals(2, allRandomSongs.size(),
                "(25) Search by specifying genres including spaces and hyphens.");

        // *** testGetRandomAlbums() ***

        /*
         * Acquisition of maximum number(5).
         */
        List<Album> allAlbums = albumDao
            .getAlphabeticalAlbums(0, Integer.MAX_VALUE, true, true, allMusicFolders);
        assertEquals(5, allAlbums.size(), "(26) Get all albums with Dao.");
        List<MediaFile> allRandomAlbums = mediaSearchProvider
            .getRandomAlbums(Integer.MAX_VALUE, allMusicFolders);
        assertEquals(5, allRandomAlbums.size(), """
                (27) Specify Integer.MAX_VALUE as the upper limit,\
                and randomly acquire albums(file struct).\
                """);

        /*
         * Acquisition of maximum number(5).
         */
        List<Album> allRandomAlbumsId3 = mediaSearchProvider
            .getRandomAlbumsId3(Integer.MAX_VALUE, allMusicFolders);
        assertEquals(5, allRandomAlbumsId3.size(),
                "(28) Specify Integer.MAX_VALUE as the upper limit, and randomly acquire albums(ID3).");

        /*
         * Total is 4.
         */
        searchCriteria = director
            .construct("ID 3 ARTIST", offset, count, false, allMusicFolders, IndexType.ARTIST_ID3);
        result = legacySearch.search(searchCriteria);
        assertEquals(3, result.getTotalHits(),
                "(29) Specify '" + searchCriteria.input() + "', total Hits is");
        assertEquals(3, result.getArtists().size(), "(30) Specify '" + searchCriteria.input()
                + "', and get an artists. Artist SIZE is ");
        assertEquals(0, result.getAlbums().size(),
                "(31) Specify '" + searchCriteria.input() + "', and get a artists. Album SIZE is ");
        assertEquals(0, result.getMediaFiles().size(), "(32) Specify '" + searchCriteria.input()
                + "', and get a artists. MediaFile SIZE is ");

        /*
         * Three hits to the artist. ALBUMARTIST is not registered with these.
         * Therefore, the registered value of ARTIST is substituted in ALBUMARTIST.
         */
        long l = result
            .getArtists()
            .stream()
            .filter(a -> a.getName().startsWith("_ID3_ARTIST_"))
            .count();
        assertEquals(3L, l, "(33) Artist whose name contains \\\"_ID3_ARTIST_\\\" is 3 records.");

        /*
         * The structure of "01 - Sonata Violin & Cello I. Allegro.ogg" ARTIST ->
         * _ID3_ARTIST_ Sarah Walker/Nash Ensemble ALBUMARTIST -> _ID3_ALBUMARTIST_
         * Sarah Walker/Nash Ensemble (The result must not contain duplicates. And
         * ALBUMARTIST must be returned correctly.)
         */
        l = result
            .getArtists()
            .stream()
            .filter(a -> a.getName().startsWith("_ID3_ALBUMARTIST_"))
            .count();
        assertEquals(0L, l, "(34) Artist whose name is \"_ID3_ARTIST_\" is 1 records.");

        /*
         * Below is a simple loop test. How long is the total time?
         */
        int countForEachMethod = 500;
        String[] randomWords4Search = createRandomWords(countForEachMethod);

        Timer globalTimer = metrics
            .timer(MetricRegistry.name(SearchServiceTest.class, "Timer.global"));

        try (Timer.Context globalTimerContext = globalTimer.time()) {
            if (LOG.isInfoEnabled()) {
                LOG.info("--- Random search (" + countForEachMethod * 4 + " times) ---");
            }

            // testSearch()
            for (String word : randomWords4Search) {
                searchCriteria = director
                    .construct(word, offset, count, false, allMusicFolders, IndexType.ALBUM);
                legacySearch.search(searchCriteria);
            }

            // testGetRandomSongs()
            ShuffleSelectionParam criteria = new ShuffleSelectionParam(Integer.MAX_VALUE, // count
                    null, // genre,
                    null, // fromYear
                    null, // toYear
                    allMusicFolders // musicFolders
            );
            for (int i = 0; i < countForEachMethod; i++) {
                mediaSearchProvider.getRandomSongs(criteria);
            }

            // testGetRandomAlbums()
            for (int i = 0; i < countForEachMethod; i++) {
                mediaSearchProvider.getRandomAlbums(Integer.MAX_VALUE, allMusicFolders);
            }

            // testGetRandomAlbumsId3()
            for (int i = 0; i < countForEachMethod; i++) {
                mediaSearchProvider.getRandomAlbumsId3(Integer.MAX_VALUE, allMusicFolders);
            }

            globalTimerContext.stop();
        }

        /*
         * Whether or not IndexReader is exhausted.
         */
        searchCriteria = director
            .construct("Sarah Walker", offset, count, false, allMusicFolders, IndexType.ALBUM);
        result = legacySearch.search(searchCriteria);
        assertEquals(0, result.getArtists().size(), "(35) Can the normal case be implemented.");
        assertEquals(0, result.getAlbums().size(), "(36) Can the normal case be implemented.");
        assertEquals(1, result.getMediaFiles().size(), "(37) Can the normal case be implemented.");
        assertEquals(MediaType.ALBUM, result.getMediaFiles().get(0).getMediaType(),
                "(38) Can the normal case be implemented.");
        assertEquals("_ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble",
                result.getMediaFiles().get(0).getArtist(),
                "(39) Can the normal case be implemented.");

        if (LOG.isInfoEnabled()) {
            LOG.info("--- SUCCESS ---");
        }

        try (ConsoleReporter reporter = ConsoleReporter
            .forRegistry(metrics)
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .build()) {
            reporter.report();
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("End. ");
        }
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private String[] createRandomWords(int count) {
        String[] randomStrings = new String[count];
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            char[] word = new char[random.nextInt(8) + 3];
            for (int j = 0; j < word.length; j++) {
                word[j] = (char) ('a' + random.nextInt(26));
            }
            randomStrings[i] = String.valueOf(word);
        }
        return randomStrings;
    }

    @Test
    @SuppressWarnings("PMD.CognitiveComplexity")
    void testGenre() {

        List<MusicFolder> allMusicFolders = musicFolderDao.getAllMusicFolders();

        // #### song ####
        String genre = "Baroque Instrumental";
        List<MediaFile> songs = mediaSearchProvider
            .getSongsByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        assertEquals(2, songs.size(), "song - genre : " + genre);

        genre = "Impressionist Era";
        songs = mediaSearchProvider.getSongsByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        assertEquals(2, songs.size(), "song - genre : " + genre);

        genre = "Gothik Folk Psychobilly";
        songs = mediaSearchProvider.getSongsByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        assertEquals(3, songs.size(), "song - genre : " + genre);

        genre = "Metal";
        songs = mediaSearchProvider.getSongsByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        assertEquals(1, songs.size(), "song - genre : " + genre);
        assertEquals("_ID3_ALBUM_ Chrome Hoof", songs.get(0).getAlbumName(), "album name");

        genre = "Alternative/Indie";
        songs = mediaSearchProvider.getSongsByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        assertEquals(1, songs.size(), "song - genre : " + genre);
        assertEquals("_ID3_ALBUM_ Chrome Hoof", songs.get(0).getAlbumName(), "album name");

        // #### file struct album ####
        genre = "Baroque Instrumental";
        List<MediaFile> albums = mediaSearchProvider
            .getAlbumsByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        assertEquals(1, albums.size(), "albums - genre : " + genre);

        genre = "Impressionist Era";
        albums = mediaSearchProvider
            .getAlbumsByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        assertEquals(1, albums.size(), "albums - genre : " + genre);

        genre = "Gothik Folk Psychobilly";
        albums = mediaSearchProvider
            .getAlbumsByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        assertEquals(1, albums.size(), "albums - genre : " + genre);

        genre = "Metal";
        albums = mediaSearchProvider
            .getAlbumsByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        long countOfMetal = albums.size();
        if (countOfMetal == 0) {
            // https://github.com/tesshucom/jpsonic/issues/797
            if (LOG.isInfoEnabled()) {
                LOG
                    .info("In this environment, the genre count of the album may be partially different from other environments.");
            }
        } else {
            // Originally. Or on Windows.
            assertEquals(1, countOfMetal, "albums - genre : " + genre);
            assertEquals("_ID3_ALBUM_ Chrome Hoof", albums.get(0).getAlbumName(),
                    "album name" + genre);
        }

        /* "Metal" and "Alternative/Indie" exist in child of _ID3_ALBUM_ Chrome Hoof */
        genre = "Alternative/Indie";
        albums = mediaSearchProvider
            .getAlbumsByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        long countOfIndie = albums.size();
        /* https://github.com/tesshucom/jpsonic/issues/797 */
        long countOfIndieMagic = 1;
        if (countOfIndie == countOfIndieMagic) {
            if (LOG.isInfoEnabled()) {
                LOG
                    .info("In this environment, the genre count of the album may be partially different from other environments.");
            }
        } else {
            // Originally. Or on Windows.
            assertEquals(0, albums.size(), "albums - genre : " + genre);
        }

        genre = "TestAlbum";
        albums = mediaSearchProvider
            .getAlbumsByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        assertEquals(0, albums.size(), "albums - genre : " + genre);

        // #### id3 album ####
        genre = "Baroque Instrumental";
        List<Album> albumid3s = mediaSearchProvider
            .getAlbumId3sByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        assertEquals(1, albumid3s.size(), "albums - genre : " + genre);

        genre = "Impressionist Era";
        albumid3s = mediaSearchProvider
            .getAlbumId3sByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        assertEquals(1, albumid3s.size(), "albums - genre : " + genre);

        genre = "Gothik Folk Psychobilly";
        albumid3s = mediaSearchProvider
            .getAlbumId3sByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        assertEquals(1, albumid3s.size(), "albums - genre : " + genre);

        genre = "Metal";
        albumid3s = mediaSearchProvider
            .getAlbumId3sByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        countOfMetal = albumid3s.size();
        if (countOfMetal == 0) {
            // https://github.com/tesshucom/jpsonic/issues/797
            if (LOG.isInfoEnabled()) {
                LOG
                    .info("In this environment, the genre count of the album may be partially different from other environments.");
            }
        } else {
            // Originally. Or on Windows.
            assertEquals(1, countOfMetal, "albums - genre : " + genre);
            assertEquals("_ID3_ALBUM_ Chrome Hoof", albumid3s.get(0).getName(),
                    "album name" + genre);
        }

        /* "Metal" and "Alternative/Indie" exist in child of _ID3_ALBUM_ Chrome Hoof */
        genre = "Alternative/Indie";
        albumid3s = mediaSearchProvider
            .getAlbumId3sByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        countOfIndie = albumid3s.size();
        // https://github.com/tesshucom/jpsonic/issues/797
        if (countOfIndie == countOfIndieMagic) {
            if (LOG.isInfoEnabled()) {
                LOG
                    .info("In this environment, the genre count of the album may be partially different from other environments.");
            }
        } else {
            // Originally. Or on Windows.
            assertEquals(0, albumid3s.size(), "albums - genre : " + genre);
        }

        genre = "TestAlbum";
        albumid3s = mediaSearchProvider
            .getAlbumId3sByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        assertEquals(0, albumid3s.size(), "albums - genre : " + genre);

        genre = "Rock & Roll";
        albumid3s = mediaSearchProvider
            .getAlbumId3sByGenres(genre, 0, Integer.MAX_VALUE, allMusicFolders);
        assertEquals(0, albumid3s.size(), "albums - genre : " + genre);

    }

    @Test
    void testGenreMaster() {

        List<Genre> genres = genreMasterProvider.getLegacyGenres(false, 0, Integer.MAX_VALUE);
        assertEquals(5, genres.size(), "Song genre size");
        assertEquals(1L,
                genres.stream().filter(g -> "Gothik Folk Psychobilly".equals(g.name())).count(),
                "Song genre: Gothik Folk Psychobilly");
        assertEquals(1L, genres.stream().filter(g -> "Impressionist Era".equals(g.name())).count(),
                "Song genre: Impressionist Era");
        assertEquals(1L,
                genres.stream().filter(g -> "Baroque Instrumental".equals(g.name())).count(),
                "Song genre: Baroque Instrumental");
        assertEquals(1L, genres.stream().filter(g -> "Alternative/Indie".equals(g.name())).count(),
                "Song genre: Alternative/Indie");
        assertEquals(1L, genres.stream().filter(g -> "Metal".equals(g.name())).count(),
                "Song genre: Metal");

        genres = genreMasterProvider.getLegacyGenres(true, 0, Integer.MAX_VALUE);
        assertEquals(4, genres.size(), "Album genre size");
        assertEquals(1L,
                genres.stream().filter(g -> "Gothik Folk Psychobilly".equals(g.name())).count(),
                "Album genre: Gothik Folk Psychobilly");
        assertEquals(1L, genres.stream().filter(g -> "Impressionist Era".equals(g.name())).count(),
                "Album genre: Impressionist Era");
        assertEquals(1L,
                genres.stream().filter(g -> "Baroque Instrumental".equals(g.name())).count(),
                "Album genre: Baroque Instrumental");
        long countOfMetal = genres.stream().filter(g -> "Metal".equals(g.name())).count();
        if (countOfMetal == 0) {
            // https://github.com/tesshucom/jpsonic/issues/797
            if (LOG.isInfoEnabled()) {
                LOG
                    .info("In this environment, the genre count of the album may be partially different from other environments.");
            }
        } else {
            // Originally. Or on Windows.
            assertEquals(1L, countOfMetal, "Album genre: Metal");
        }
    }

    @Test
    void testToPreAnalyzedGenres() {

        List<String> preAnalyzedGenres = genreMasterProvider
            .findPreAnalyzedGenres(Arrays.asList("Baroque Instrumental", "Gothik Folk Psychobilly"),
                    true);
        assertEquals(2, preAnalyzedGenres.size(), "size");
        assertEquals("Baroque Instrumental", preAnalyzedGenres.get(0), "genre1");
        assertEquals("Gothik Folk Psychobilly", preAnalyzedGenres.get(1), "genre2");

        preAnalyzedGenres = genreMasterProvider
            .findPreAnalyzedGenres(Arrays.asList("Baroque Instrumental"), true);
        assertEquals(1, preAnalyzedGenres.size(), "size");
        assertEquals("Baroque Instrumental", preAnalyzedGenres.get(0), "genre");

        preAnalyzedGenres = genreMasterProvider
            .findPreAnalyzedGenres(Arrays.asList("Baroque"), true);
        assertEquals(0, preAnalyzedGenres.size(), "size");

    }
}

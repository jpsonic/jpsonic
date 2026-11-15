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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.util.ObjectUtils.isEmpty;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.param.ShuffleSelectionParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tests to prove what kind of strings/chars can be used in the genre field.
 */
class SearchServiceSpecialGenreTest extends AbstractNeedsScan {

    private List<MusicFolder> musicFolders;

    @Autowired
    private MediaSearchProvider mediaSearchProvider;

    @Override
    public List<MusicFolder> getMusicFolders() {
        if (isEmpty(musicFolders)) {
            musicFolders = Arrays
                .asList(new MusicFolder(1, resolveBaseMediaPath("Search/SpecialGenre"),
                        "accessible", true, now(), 1, false));
        }
        return musicFolders;
    }

    @BeforeEach
    void setup() {
        populateDatabaseOnlyOnce();
    }

    /*
     * There are 19 files in
     * src/test/resources/MEDIAS/Search/SpecialGenre/ARTIST1/ALBUM_A. In FILE01 to
     * FILE16, Special strings for Lucene syntax are stored as tag values ​​of
     * Genre.<p> Legacy can not search all these genres. (Strictly speaking, the
     * genre field is not created at index creation.) <p> // 3.x -> 8.x : Do the
     * process more strictly. <p> - Values ​​that can be cross-referenced with DB
     * are stored in the index.<br> - Search is also possible with user's readable
     * value (file tag value).<br> - However, there is an exception in parentheses.
     */
    @Test
    void testQueryEscapeRequires() {

        Function<String, ShuffleSelectionParam> simpleStringCriteria = s -> new ShuffleSelectionParam(
                Integer.MAX_VALUE, // count
                Arrays.asList(s), // genre,
                null, // fromYear
                null, // toYear
                getMusicFolders() // musicFolders
        );

        List<MediaFile> songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("+"));
        assertEquals(1, songs.size()); // Searchable
        assertEquals("+", songs.get(0).getGenre());
        assertEquals("Query Escape Requires 1", songs.get(0).getTitle());

        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("-"));
        assertEquals(1, songs.size()); // Searchable
        assertEquals("-", songs.get(0).getGenre());
        assertEquals("Query Escape Requires 2", songs.get(0).getTitle());

        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("&&"));
        assertEquals(1, songs.size()); // Searchable
        assertEquals("&&", songs.get(0).getGenre());
        assertEquals("Query Escape Requires 3", songs.get(0).getTitle());

        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("||"));
        assertEquals(1, songs.size()); // Searchable
        assertEquals("||", songs.get(0).getGenre());
        assertEquals("Query Escape Requires 4", songs.get(0).getTitle());

        /*
         * // 3.x -> 8.x : Brackets ()<p> Lucene can handle these. However, brackets are
         * specially parsed before the index creation process. <p>This string is never
         * stored in the index. This is the only exception.
         */
        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply(" (")); // space &
        // bracket
        assertEquals(0, songs.size());

        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply(")"));
        assertEquals(0, songs.size());

        /*
         * // 3.x -> 8.x : Brackets {}[]<p> Lucene can handle these. However, brackets
         * are specially parsed before the index creation process.<p> This can be done
         * with a filter that performs the reverse process on the input values ​​when
         * searching. As a result, the values ​​stored in the file can be retrieved by
         * search.
         */
        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("{}"));
        assertEquals(1, songs.size()); // Searchable
        /*
         * This is the result of the tag parser and domain value. It is different from
         * the tag value in file.
         */
        assertEquals("{ }", songs.get(0).getGenre());
        assertEquals("Query Escape Requires 7", songs.get(0).getTitle());
        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("{ }"));
        assertEquals(1, songs.size()); // Searchable
        assertEquals("Query Escape Requires 7", songs.get(0).getTitle());

        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("[]"));
        assertEquals(1, songs.size()); // Searchable
        /*
         * This is the result of the tag parser and domain value. It is different from
         * the tag value in file.
         */
        assertEquals("[ ]", songs.get(0).getGenre());
        assertEquals("Query Escape Requires 8", songs.get(0).getTitle());
        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("[ ]"));
        assertEquals(1, songs.size()); // Searchable
        assertEquals("Query Escape Requires 8", songs.get(0).getTitle());
        // <<<<<

        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("^"));
        assertEquals(1, songs.size()); // Searchable
        assertEquals("^", songs.get(0).getGenre());
        assertEquals("Query Escape Requires 9", songs.get(0).getTitle());

        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("\""));
        assertEquals(1, songs.size()); // Searchable
        assertEquals("\"", songs.get(0).getGenre());
        assertEquals("Query Escape Requires 10", songs.get(0).getTitle());

        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("~"));
        assertEquals(1, songs.size()); // Searchable
        assertEquals("~", songs.get(0).getGenre());
        assertEquals("Query Escape Requires 11", songs.get(0).getTitle());

        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("*"));
        assertEquals(1, songs.size()); // Searchable
        assertEquals("*", songs.get(0).getGenre());
        assertEquals("Query Escape Requires 12", songs.get(0).getTitle());

        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("?"));
        assertEquals(1, songs.size()); // Searchable
        assertEquals("?", songs.get(0).getGenre());
        assertEquals("Query Escape Requires 13", songs.get(0).getTitle());

        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply(":"));
        assertEquals(1, songs.size()); // Searchable
        assertEquals(":", songs.get(0).getGenre());
        assertEquals("Query Escape Requires 14", songs.get(0).getTitle());

        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("\\"));
        assertEquals(1, songs.size()); // Searchable
        assertEquals("\\", songs.get(0).getGenre());
        assertEquals("Query Escape Requires 15", songs.get(0).getTitle());

        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("/"));
        assertEquals(1, songs.size()); // Searchable
        assertEquals("/", songs.get(0).getGenre());
        assertEquals("Query Escape Requires 16", songs.get(0).getTitle());

    }

    /*
     * 3.x -> 8.x : Specification of genre search. Jaudiotagger applies special
     * treatment to bracket (FILE17).
     */
    @Test
    void testBrackets() {

        Function<String, ShuffleSelectionParam> simpleStringCriteria = s -> new ShuffleSelectionParam(
                Integer.MAX_VALUE, // count
                Arrays.asList(s), // genre,
                null, // fromYear
                null, // toYear
                getMusicFolders() // musicFolders
        );

        // -(GENRE)- is registered as genre of FILE17.

        /*
         * Search by genre string registered in file.<p> The value stored in the index
         * is different from legacy. Domain value is kept as it is.
         */
        List<MediaFile> songs = mediaSearchProvider
            .getRandomSongs(simpleStringCriteria.apply("-(GENRE)-"));
        assertEquals(1, songs.size());
        assertEquals("-GENRE -", songs.get(0).getGenre());
        assertEquals("Consistency with Tag Parser 1", songs.get(0).getTitle());

        /*
         * Search by Domain value.
         */
        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("-GENRE -"));
        assertEquals(1, songs.size());
        assertEquals("-GENRE -", songs.get(0).getGenre());
        assertEquals("Consistency with Tag Parser 1", songs.get(0).getTitle());

        /*
         * Legacy genre search
         */
        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply(" genre"));
        // Strong unique parsing rules have been removed.
        assertEquals(0, songs.size());

        /*
         * Jaudiotagger applies special treatment to numeric. (FILE18)
         */

        List<MusicFolder> folders = getMusicFolders();

        ShuffleSelectionParam criteria = new ShuffleSelectionParam(Integer.MAX_VALUE, // count
                Arrays.asList("Rock"), // genre,
                null, // fromYear
                null, // toYear
                folders // musicFolders
        );

        songs = mediaSearchProvider.getRandomSongs(criteria);
        assertEquals(1, songs.size());
        assertEquals("Numeric mapping specification of genre 1", songs.get(0).getTitle());

        // The value registered in the file is 17
        assertEquals("Rock", songs.get(0).getGenre());

    }

    /*
     * Other special strings. (FILE19)<p> {'“『【【】】[︴○◎@ $〒→+]ＦＵＬＬ－ＷＩＤＴＨCæsar's<p>
     * Legacy stores with Analyze, so searchable characters are different.
     */
    @Test
    void testOthers() {

        Function<String, ShuffleSelectionParam> simpleStringCriteria = s -> new ShuffleSelectionParam(
                Integer.MAX_VALUE, // count
                Arrays.asList(s), // genre,
                null, // fromYear
                null, // toYear
                getMusicFolders() // musicFolders
        );

        // 3.x -> 8.x : Do the process more strictly.
        List<MediaFile> songs = mediaSearchProvider
            .getRandomSongs(simpleStringCriteria.apply("{'“『【【】】[︴○◎@ $〒→+]ＦＵＬＬ－ＷＩＤＴＨCæsar's"));
        assertEquals(1, songs.size());
        assertEquals(1, songs.size());
        assertEquals("Other special strings 1", songs.get(0).getTitle());
        assertEquals("{'“『【【】】[︴○◎@ $〒→+]ＦＵＬＬ－ＷＩＤＴＨCæsar's", songs.get(0).getGenre());

        /*
         * Legacy kept "widthcaesar" using their own rules. The previous rule has been
         * discarded.
         */
        songs = mediaSearchProvider.getRandomSongs(simpleStringCriteria.apply("widthcaesar"));
        assertEquals(0, songs.size());

    }
}

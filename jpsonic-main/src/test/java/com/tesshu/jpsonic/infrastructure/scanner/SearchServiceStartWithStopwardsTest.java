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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.infrastructure.search.LegacySearch;
import com.tesshu.jpsonic.infrastructure.search.LegacySearchResult;
import com.tesshu.jpsonic.infrastructure.search.criteria.HttpSearchCriteria;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test cases related to #1142. The filter is not properly applied when
 * analyzing the query,
 * <p>
 * In the process of hardening the Analyzer implementation, this problem is
 * solved side by side.
 */
class SearchServiceStartWithStopwardsTest extends AbstractNeedsScan {

    private List<MusicFolder> musicFolders;

    @Autowired
    private LegacySearch legacySearch;

    @Autowired
    private HttpSearchCriteriaDirector director;

    @Override
    public List<MusicFolder> getMusicFolders() {
        if (isEmpty(musicFolders)) {
            musicFolders = Arrays
                .asList(new MusicFolder(1, resolveBaseMediaPath("Search/StartWithStopwards"),
                        "accessible", true, now(), 1, false));
        }
        return musicFolders;
    }

    @BeforeEach
    void setup() {
        populateDatabase();
    }

    @Test
    void testStartWithStopwards() throws IOException {

        int offset = 0;
        int count = Integer.MAX_VALUE;
        List<MusicFolder> folders = getMusicFolders();

        HttpSearchCriteria criteria = director
            .construct("will", offset, count, false, folders, IndexType.ARTIST_ID3);
        LegacySearchResult result = legacySearch.search(criteria);
        // Will hit because Airsonic's stopword is defined(#1235) => This case does not
        // hit because it is a phrase
        // search rather than a term prefix match.
        assertEquals(0, result.getTotalHits(), "Williams hit by \"will\" ");

        criteria = director
            .construct("williams", offset, count, false, folders, IndexType.ARTIST_ID3);
        result = legacySearch.search(criteria);
        assertEquals(1, result.getTotalHits(), "Williams hit by \"williams\" ");

        criteria = director.construct("the", offset, count, false, folders, IndexType.SONG);
        result = legacySearch.search(criteria);
        // 3.x -> 8.x : The filter is properly applied to the input(Stopward)
        assertEquals(0, result.getTotalHits(), "Theater hit by \"the\" ");

        criteria = director.construct("willi", offset, count, false, folders, IndexType.ARTIST_ID3);
        result = legacySearch.search(criteria);
        // 3.x -> 8.x : Normal forward matching => This case does not hit because it is
        // a phrase search rather
        // than
        // a term prefix match.
        assertEquals(0, result.getTotalHits(), "Williams hit by \"Williams\" ");

        criteria = director.construct("thea", offset, count, false, folders, IndexType.SONG);
        result = legacySearch.search(criteria);
        // 3.x -> 8.x : Normal forward matching
        assertEquals(0, result.getTotalHits(), "Theater hit by \"thea\" "); // => This case does
                                                                            // not hit
                                                                            // because
        // it is a phrase search rather than
        // a term prefix match.

        // legacy -> phrase
        criteria = director.construct("theater", offset, count, false, folders, IndexType.SONG);
        result = legacySearch.search(criteria);
        assertEquals(1, result.getTotalHits(), "Theater hit by \"theater\" ");
    }
}

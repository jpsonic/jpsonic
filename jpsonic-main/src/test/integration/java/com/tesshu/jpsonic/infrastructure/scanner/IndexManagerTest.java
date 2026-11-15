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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.infrastructure.search.LegacySearch;
import com.tesshu.jpsonic.infrastructure.search.LegacySearchResult;
import com.tesshu.jpsonic.infrastructure.search.criteria.HttpSearchCriteria;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.api.repository.RatingDao;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;

@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals",
        "PMD.UseUtilityClass" })
class IndexManagerTest extends AbstractNeedsScan {

    private List<MusicFolder> musicFolders;

    @Autowired
    private LegacySearch legacySearch;

    @Autowired
    private IndexManager indexManager;

    @Autowired
    private HttpSearchCriteriaDirector director;

    @Autowired
    private TemplateWrapper template;

    @Autowired
    private MediaFileDao mediaFileDao;

    @Autowired
    private RatingDao ratingDao;

    @Autowired
    private DirectoryScanProcedure directoryScanProcedure;

    @Autowired
    private Id3MetadataScanProcedure id3MetadataScanProcedure;

    private static final String USER_NAME = "admin";

    @Override
    public List<MusicFolder> getMusicFolders() {
        if (ObjectUtils.isEmpty(musicFolders)) {
            musicFolders = Arrays
                .asList(new MusicFolder(1, resolveBaseMediaPath("Music"), "Music", true, now(), 1,
                        false));
        }
        return musicFolders;
    }

    @BeforeEach
    void setup() {
        populateDatabaseOnlyOnce(() -> {
            return true;
        }, () -> {

            // #1842 Airsonic does not implement Rating expunge

            List<MediaFile> albums = mediaFileDao
                .getAlphabeticalAlbums(0, Integer.MAX_VALUE, true, getMusicFolders());
            assertEquals(4, albums.size());

            albums.forEach(m -> ratingDao.setRatingForUser(USER_NAME, m, 1));
            assertEquals(4, ratingDao.getRatedAlbumCount(USER_NAME, musicFolders));
            int ratingsCount = template
                .getJdbcTemplate()
                .queryForObject("select count(*) from user_rating where user_rating.username = ?",
                        Integer.class, USER_NAME);
            assertEquals(4, ratingsCount, "Because explicitly registered 4 Ratings.");

            // Register a dummy rate (reproduce old path data by moving files)
            MediaFile dummyMediaFile = new MediaFile();
            dummyMediaFile.setPathString("oldPath");
            ratingDao.setRatingForUser(USER_NAME, dummyMediaFile, 1);

            assertEquals(4, ratingDao.getRatedAlbumCount(USER_NAME, musicFolders),
                    "Because the SELECT condition only references real paths.");
            ratingsCount = template
                .getJdbcTemplate()
                .queryForObject("select count(*) from user_rating where user_rating.username = ?",
                        Integer.class, USER_NAME);
            assertEquals(5, ratingsCount,
                    "Because counted directly, including non-existent paths.");

            return true;
        });

    }

    /**
     * This test was originally created to cover the now-obsolete expungeService,
     * which has already been removed. Although the expungeService no longer exists,
     * an equivalent process is expected to run automatically as part of the scan.
     * Therefore, in the current implementation, invoking a scan under similar
     * conditions should result in the removal of unnecessary data from both the
     * database and the Lucene index.
     */
    @Order(1)
    @Test
    void testExpunge() throws IOException {

        int offset = 0;
        int count = Integer.MAX_VALUE;

        final HttpSearchCriteria criteriaArtist = director
            .construct("_DIR_ Ravel", offset, count, false, getMusicFolders(), IndexType.ARTIST);
        final HttpSearchCriteria criteriaAlbum = director
            .construct("Complete Piano Works", offset, count, false, musicFolders, IndexType.ALBUM);
        final HttpSearchCriteria criteriaSong = director
            .construct("Gaspard", offset, count, false, musicFolders, IndexType.SONG);
        final HttpSearchCriteria criteriaArtistId3 = director
            .construct("_DIR_ Ravel", offset, count, false, musicFolders, IndexType.ARTIST_ID3);
        final HttpSearchCriteria criteriaAlbumId3 = director
            .construct("Complete Piano Works", offset, count, false, musicFolders,
                    IndexType.ALBUM_ID3);

        /* Delete DB record. */

        // artist
        LegacySearchResult result = legacySearch.search(criteriaArtist);
        assertEquals(1, result.getMediaFiles().size());
        assertEquals("_DIR_ Ravel", result.getMediaFiles().get(0).getName());

        List<Integer> candidates = mediaFileDao.getArtistExpungeCandidates();
        assertEquals(0, candidates.size());

        result.getMediaFiles().forEach(a -> mediaFileDao.deleteMediaFile(a.getId()));

        candidates = mediaFileDao.getArtistExpungeCandidates();
        assertEquals(1, candidates.size());

        // album
        result = legacySearch.search(criteriaAlbum);
        assertEquals(1, result.getMediaFiles().size());
        assertEquals("_DIR_ Ravel - Complete Piano Works", result.getMediaFiles().get(0).getName());

        candidates = mediaFileDao.getAlbumExpungeCandidates();
        assertEquals(0, candidates.size());

        result.getMediaFiles().forEach(a -> mediaFileDao.deleteMediaFile(a.getId()));

        candidates = mediaFileDao.getAlbumExpungeCandidates();
        assertEquals(1, candidates.size());

        // song
        result = legacySearch.search(criteriaSong);
        assertEquals(2, result.getMediaFiles().size());
        if ("01 - Gaspard de la Nuit - i. Ondine".equals(result.getMediaFiles().get(0).getName())) {
            assertEquals("02 - Gaspard de la Nuit - ii. Le Gibet",
                    result.getMediaFiles().get(1).getName());
        } else if ("02 - Gaspard de la Nuit - ii. Le Gibet"
            .equals(result.getMediaFiles().get(0).getName())) {
            assertEquals("01 - Gaspard de la Nuit - i. Ondine",
                    result.getMediaFiles().get(1).getName());
        } else {
            Assertions.fail("Search results are not correct.");
        }

        candidates = mediaFileDao.getSongExpungeCandidates();
        assertEquals(0, candidates.size());

        result.getMediaFiles().forEach(a -> mediaFileDao.deleteMediaFile(a.getId()));

        candidates = mediaFileDao.getSongExpungeCandidates();
        assertEquals(2, candidates.size());

        // artistid3
        result = legacySearch.search(criteriaArtistId3);
        assertEquals(1, result.getArtists().size());
        assertEquals("_DIR_ Ravel", result.getArtists().get(0).getName());

        // albumId3
        result = legacySearch.search(criteriaAlbumId3);
        assertEquals(1, result.getAlbums().size());
        assertEquals("Complete Piano Works", result.getAlbums().get(0).getName());

        // The following reproduces the behavior of the expungeService.
        // Note the processing order.
        // ->
        ScanContext scanContext = new ScanContext(now(), false, USER_NAME, false, false, 0, 0,
                false, false);
        indexManager.startIndexing();
        id3MetadataScanProcedure.iterateAlbumId3(scanContext, false);
        id3MetadataScanProcedure.iterateArtistId3(scanContext, false);
        directoryScanProcedure.iterateFileStructure(scanContext);
        indexManager.stopIndexing();
        ratingDao.expunge();
        template.checkpoint();
        // <-

        result = legacySearch.search(criteriaArtist);
        assertEquals(0, result.getMediaFiles().size());

        result = legacySearch.search(criteriaAlbum);
        assertEquals(0, result.getMediaFiles().size());

        result = legacySearch.search(criteriaSong);
        assertEquals(0, result.getMediaFiles().size());

        result = legacySearch.search(criteriaArtistId3);
        assertEquals(0, result.getArtists().size());

        result = legacySearch.search(criteriaAlbumId3);
        assertEquals(0, result.getAlbums().size());

        // See this#setup
        assertEquals(0, ratingDao.getRatedAlbumCount(USER_NAME, musicFolders),
                "Because one album has been deleted.");
        int ratingsCount = template
            .getJdbcTemplate()
            .queryForObject("select count(*) from user_rating where user_rating.username = ?",
                    Integer.class, USER_NAME);
        assertEquals(0, ratingsCount, "Will be removed, including oldPath");
    }
}

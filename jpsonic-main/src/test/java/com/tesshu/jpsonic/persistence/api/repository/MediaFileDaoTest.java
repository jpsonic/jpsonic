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
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.persistence.api.repository;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.infrastructure.core.DisabledOnWindowsJdk21OrEarlier;
import com.tesshu.jpsonic.infrastructure.core.NeedsTranscode;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao.ChildOrder;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao.RandomSongsQueryBuilder;
import com.tesshu.jpsonic.persistence.base.DaoHelper;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import com.tesshu.jpsonic.persistence.dialect.DialectMediaFileDao;
import com.tesshu.jpsonic.persistence.param.ShuffleSelectionParam;
import com.tesshu.jpsonic.persistence.result.ArtistSortCandidate;
import com.tesshu.jpsonic.persistence.result.ArtistSortCandidate.TargetField;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "PMD.TooManyStaticImports" })
@DisabledOnWindowsJdk21OrEarlier // Flaky Test on Windows Server
class MediaFileDaoTest {

    @Nested
    class UnitTest {

        private JdbcTemplate jdbcTemplate;
        private MediaFileDao mediaFileDao;

        @BeforeEach
        void setup() {
            jdbcTemplate = mock(JdbcTemplate.class);
            DaoHelper daoHelper = mock(DaoHelper.class);
            Mockito.when(daoHelper.getJdbcTemplate()).thenReturn(jdbcTemplate);
            TemplateWrapper templateWrapper = new TemplateWrapper(daoHelper);
            mediaFileDao = new MediaFileDao(templateWrapper, mock(DialectMediaFileDao.class));
        }

        @Test
        void updateArtistSortTest() {
            ArtistSortCandidate artist = new ArtistSortCandidate("artist", "artistSort", 1,
                    "DIRECTORY", TargetField.ARTIST.getValue());
            artist.setMusicIndex("A");

            List<ArtistSortCandidate> cands = List.of(artist);
            ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> argCaptor = ArgumentCaptor.forClass(Object[].class);
            Mockito
                .when(jdbcTemplate.update(queryCaptor.capture(), argCaptor.capture()))
                .thenReturn(0);
            mediaFileDao.updateArtistSort(cands);
            assertEquals(
                    "update media_file set artist_reading=?, artist_sort=?, music_index = ? where id = ?",
                    queryCaptor.getValue());
            assertEquals(1, argCaptor.getAllValues().size());
            assertEquals(4, argCaptor.getValue().length);

            ArtistSortCandidate artistOfSong = new ArtistSortCandidate("artist", "artistSort", 1,
                    "MUSIC", TargetField.ARTIST.getValue());
            cands = List.of(artistOfSong);
            queryCaptor = ArgumentCaptor.forClass(String.class);
            argCaptor = ArgumentCaptor.forClass(Object[].class);
            Mockito
                .when(jdbcTemplate.update(queryCaptor.capture(), argCaptor.capture()))
                .thenReturn(0);
            mediaFileDao.updateArtistSort(cands);
            assertEquals("update media_file set artist_reading=?, artist_sort=? where id = ?",
                    queryCaptor.getValue());
            assertEquals(1, argCaptor.getAllValues().size());
            assertEquals(3, argCaptor.getValue().length);

            ArtistSortCandidate albumArtist = new ArtistSortCandidate("albumArtist",
                    "albumArtistSort", 1, "MUSIC", TargetField.ALBUM_ARTIST.getValue());
            cands = List.of(artistOfSong, albumArtist);
            queryCaptor = ArgumentCaptor.forClass(String.class);
            argCaptor = ArgumentCaptor.forClass(Object[].class);
            Mockito
                .when(jdbcTemplate.update(queryCaptor.capture(), argCaptor.capture()))
                .thenReturn(0);
            mediaFileDao.updateArtistSort(cands);
            assertEquals("""
                    update media_file \
                    set artist_reading=?, artist_sort=?, album_artist_reading=?, \
                    album_artist_sort=?, \
                    children_last_updated = ? \
                    where id = ?\
                    """, queryCaptor.getValue());
            assertEquals(1, argCaptor.getAllValues().size());
            assertEquals(6, argCaptor.getValue().length);

            ArtistSortCandidate composer = new ArtistSortCandidate("albumArtist", "albumArtistSort",
                    1, "MUSIC", TargetField.COMPOSER.getValue());
            cands = List.of(artistOfSong, albumArtist, composer);
            queryCaptor = ArgumentCaptor.forClass(String.class);
            argCaptor = ArgumentCaptor.forClass(Object[].class);
            Mockito
                .when(jdbcTemplate.update(queryCaptor.capture(), argCaptor.capture()))
                .thenReturn(0);
            mediaFileDao.updateArtistSort(cands);
            assertEquals("""
                    update media_file \
                    set artist_reading=?, artist_sort=?, \
                    album_artist_reading=?, album_artist_sort=?, children_last_updated = ?, \
                    composer_sort=? \
                    where id = ?\
                    """, queryCaptor.getValue());
            assertEquals(1, argCaptor.getAllValues().size());
            assertEquals(7, argCaptor.getValue().length);
        }

        @Nested
        class GetChildrenOfTest {

            private TemplateWrapper templateWrapper;
            private MediaFileDao mediaFileDao;

            @BeforeEach
            void setup() {
                this.templateWrapper = Mockito.mock(TemplateWrapper.class);
                mediaFileDao = new MediaFileDao(templateWrapper, mock(DialectMediaFileDao.class));
            }

            private String getOrderBy(ArgumentCaptor<String> queryCaptor) {
                String query = queryCaptor.getValue();
                return query
                    .replaceAll("\n", "")
                    .replaceAll("^.*order\sby", "")
                    .replaceAll("offset.*$", "")
                    .trim();
            }

            @SuppressWarnings("unchecked")
            private ArgumentCaptor<String> getQuery(ChildOrder byYear) {
                ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
                Mockito
                    .when(templateWrapper
                        .namedQuery(queryCaptor.capture(), Mockito.any(RowMapper.class),
                                Mockito.anyMap()))
                    .thenReturn(Collections.emptyList());
                mediaFileDao.getChildrenOf("path", 0, Integer.MAX_VALUE, byYear);
                return queryCaptor;
            }

            @Test
            void testByAlphabetical() {
                ArgumentCaptor<String> query = getQuery(ChildOrder.BY_ALPHA);
                assertEquals("type_order, media_file_order", getOrderBy(query));
            }

            @Test
            void testByYear() {
                ArgumentCaptor<String> query = getQuery(ChildOrder.BY_YEAR);
                assertEquals("type_order, year is null, year, media_file_order", getOrderBy(query));
            }

            @Test
            void testByTrackNo() {
                ArgumentCaptor<String> query = getQuery(ChildOrder.BY_TRACK);
                assertEquals("""
                        type_order, \
                        disc_number is null, disc_number, \
                        track_number is null, track_number, \
                        media_file_order\
                        """, getOrderBy(query));
            }
        }

        @Nested
        class RandomSongsQueryBuilderTest {

            // Below is a test of MediaFileDao#RandomSongsQueryBuilder

            private static final String SELECT = """
                    select media_file.id, media_file.path, media_file.folder, media_file.type, \
                    media_file.format, media_file.title, media_file.album, media_file.artist, media_file.album_artist, \
                    media_file.disc_number, media_file.track_number, media_file.year, media_file.genre, \
                    media_file.bit_rate, media_file.variable_bit_rate, media_file.duration_seconds, \
                    media_file.file_size, media_file.width, media_file.height, media_file.cover_art_path, \
                    media_file.parent_path, media_file.play_count, media_file.last_played, media_file.comment, \
                    media_file.created, media_file.changed, media_file.last_scanned, media_file.children_last_updated, \
                    media_file.present, media_file.version, media_file.mb_release_id, media_file.mb_recording_id, \
                    media_file.composer, media_file.artist_sort, media_file.album_sort, media_file.title_sort, \
                    media_file.album_artist_sort, media_file.composer_sort, media_file.artist_reading, \
                    media_file.album_reading, media_file.album_artist_reading, media_file.artist_sort_raw, \
                    media_file.album_sort_raw, media_file.album_artist_sort_raw, media_file.composer_sort_raw, \
                    media_file.media_file_order, media_file.music_index from media_file\
                    """;
            private static final String CONDITION_JOIN1 = """
                     left outer join starred_media_file \
                    on media_file.id = starred_media_file.media_file_id and starred_media_file.username = :username\
                    """;
            private static final String CONDITION_JOIN2 = """
                     left outer join media_file media_album \
                    on media_album.type = 'ALBUM' and media_album.album = media_file.album \
                    and media_album.artist = media_file.artist \
                    left outer join user_rating \
                    on user_rating.path = media_album.path and user_rating.username = :username\
                    """;
            private static final String WHERE = " where media_file.present and media_file.type = 'MUSIC'";
            private static final String ORDER_LIMIT = " order by rand() limit 0";
            private static final String CONDITION_SHOW_STARRED_SONGS = " and starred_media_file.id is not null";
            private static final String CONDITION_MIN_ALBUM_RATING = " and user_rating.rating >= :minAlbumRating";

            @Test
            void testGetIfJoinStarred() throws ExecutionException {

                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        null, null, null, null, null, null, null, false, false, null);
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                Optional<String> op = builder.getIfJoinStarred();
                assertFalse(op.isPresent());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, null,
                        null, null, null, true, false, null);
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getIfJoinStarred();
                assertEquals(CONDITION_JOIN1, op.get());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, null,
                        null, null, null, false, true, null);
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getIfJoinStarred();
                assertEquals(CONDITION_JOIN1, op.get());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, null,
                        null, null, null, true, true, null);
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getIfJoinStarred();
                assertFalse(op.isPresent());
            }

            @Test
            void testGetIfJoinAlbumRating() throws ExecutionException {

                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        null, null, null, null, null, null, null, false, false, null);
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                Optional<String> op = builder.getIfJoinAlbumRating();
                assertFalse(op.isPresent());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, 1, null,
                        null, null, false, false, null);
                assertEquals(1, criteria.getMinAlbumRating());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getIfJoinAlbumRating();
                assertEquals(CONDITION_JOIN2, op.get());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, null, 1,
                        null, null, false, false, null);
                assertEquals(1, criteria.getMaxAlbumRating());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getIfJoinAlbumRating();
                assertEquals(CONDITION_JOIN2, op.get());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, 1, 1,
                        null, null, false, false, null);
                assertEquals(1, criteria.getMinAlbumRating());
                assertEquals(1, criteria.getMaxAlbumRating());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getIfJoinAlbumRating();
                assertEquals(CONDITION_JOIN2, op.get());
            }

            @Test
            void testGetFolderCondition() throws ExecutionException {

                final String condition = " and media_file.folder in (:folders)";

                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        Collections.emptyList(), null, null, null, null, null, null, false, false,
                        null);
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                Optional<String> op = builder.getFolderCondition();
                assertFalse(op.isPresent());

                List<MusicFolder> folders = new ArrayList<>();
                folders.add(new MusicFolder("/", "", false, null, false));
                criteria = new ShuffleSelectionParam(0, null, null, null, folders, null, null, null,
                        null, null, null, false, false, null);
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getFolderCondition();
                assertEquals(condition, op.get());
            }

            @Test
            void testGetGenreCondition() throws ExecutionException {

                final String condition = " and media_file.genre in (:genres)";

                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        Collections.emptyList(), null, null, null, null, null, null, false, false,
                        null);
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                Optional<String> op = builder.getGenreCondition();
                assertFalse(op.isPresent());

                List<String> genres = new ArrayList<>();
                genres.add("genre");
                criteria = new ShuffleSelectionParam(0, genres, null, null, null, null, null, null,
                        null, null, null, false, false, null);
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getGenreCondition();
                assertEquals(condition, op.get());
            }

            @Test
            void testGetFormatCondition() throws ExecutionException {

                final String condition = " and media_file.format = :format";

                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        Collections.emptyList(), null, null, null, null, null, null, false, false,
                        null);
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                Optional<String> op = builder.getFormatCondition();
                assertFalse(op.isPresent());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, null,
                        null, null, null, false, false, "mp3");
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getFormatCondition();
                assertEquals(condition, op.get());
            }

            @Test
            void testGetFromYearCondition() throws ExecutionException {

                final String condition = " and media_file.year >= :fromYear";

                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        Collections.emptyList(), null, null, null, null, null, null, false, false,
                        null);
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                Optional<String> op = builder.getFromYearCondition();
                assertFalse(op.isPresent());

                criteria = new ShuffleSelectionParam(0, null, 1900, null, null, null, null, null,
                        null, null, null, false, false, null);
                assertEquals(1900, criteria.getFromYear());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getFromYearCondition();
                assertEquals(condition, op.get());
            }

            @Test
            void testGetToYearCondition() throws ExecutionException {

                final String condition = " and media_file.year <= :toYear";

                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        null, null, null, null, null, null, null, false, false, null);
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                Optional<String> op = builder.getToYearCondition();
                assertFalse(op.isPresent());

                criteria = new ShuffleSelectionParam(0, null, null, 2020, null, null, null, null,
                        null, null, null, false, false, null);
                assertEquals(2020, criteria.getToYear());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getToYearCondition();
                assertEquals(condition, op.get());
            }

            @Test
            void testGetMinLastPlayedCondition() throws ExecutionException {

                final String condition = " and media_file.last_played >= :minLastPlayed";

                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        null, null, null, null, null, null, null, false, false, null);
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                Optional<String> op = builder.getMinLastPlayedDateCondition();
                assertFalse(op.isPresent());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, now(), null, null,
                        null, null, null, false, false, null);
                Assertions.assertNotNull(criteria.getMinLastPlayedDate());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getMinLastPlayedDateCondition();
                assertEquals(condition, op.get());
            }

            @Test
            void testGetMaxLastPlayedCondition() throws ExecutionException {

                final String condition1 = " and media_file.last_played <= :maxLastPlayed";
                final String condition2 = " and (media_file.last_played is null or media_file.last_played <= :maxLastPlayed)";

                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        null, null, null, null, null, null, null, false, false, null);
                assertNull(criteria.getMinLastPlayedDate());
                assertNull(criteria.getMaxLastPlayedDate());
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                Optional<String> op = builder.getMaxLastPlayedDateCondition();
                assertFalse(op.isPresent());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, now(), now(), null,
                        null, null, null, false, false, null);
                Assertions.assertNotNull(criteria.getMinLastPlayedDate());
                Assertions.assertNotNull(criteria.getMaxLastPlayedDate());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getMaxLastPlayedDateCondition();
                assertEquals(condition1, op.get());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, now(), null,
                        null, null, null, false, false, null);
                assertNull(criteria.getMinLastPlayedDate());
                Assertions.assertNotNull(criteria.getMaxLastPlayedDate());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getMaxLastPlayedDateCondition();
                assertEquals(condition2, op.get());
            }

            @Test
            void testGetMinAlbumRatingCondition() throws ExecutionException {

                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        null, null, null, null, null, null, null, false, false, null);
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                Optional<String> op = builder.getMinAlbumRatingCondition();
                assertFalse(op.isPresent());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, 1, null,
                        null, null, false, false, null);
                assertEquals(1, criteria.getMinAlbumRating());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getMinAlbumRatingCondition();
                assertEquals(CONDITION_MIN_ALBUM_RATING, op.get());
            }

            @Test
            void testGetMaxAlbumRatingCondition() throws ExecutionException {

                final String condition1 = " and user_rating.rating <= :maxAlbumRating";
                final String condition2 = " and (user_rating.rating is null or user_rating.rating <= :maxAlbumRating)";

                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        null, null, null, null, null, null, null, false, false, null);
                assertNull(criteria.getMinLastPlayedDate());
                assertNull(criteria.getMaxLastPlayedDate());
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                Optional<String> op = builder.getMaxAlbumRatingCondition();
                assertFalse(op.isPresent());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, 1, 2,
                        null, null, false, false, null);
                assertEquals(1, criteria.getMinAlbumRating());
                assertEquals(2, criteria.getMaxAlbumRating());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getMaxAlbumRatingCondition();
                assertEquals(condition1, op.get());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, null, 1,
                        null, null, false, false, null);
                assertNull(criteria.getMinAlbumRating());
                assertEquals(1, criteria.getMaxAlbumRating());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getMaxAlbumRatingCondition();
                assertEquals(condition2, op.get());
            }

            @Test
            void testGetMinPlayCountCondition() throws ExecutionException {

                final String condition = " and media_file.play_count >= :minPlayCount";

                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        null, null, null, null, null, null, null, false, false, null);
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                Optional<String> op = builder.getMinPlayCountCondition();
                assertFalse(op.isPresent());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, null,
                        null, 1, null, false, false, null);
                assertEquals(1, criteria.getMinPlayCount());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getMinPlayCountCondition();
                assertEquals(condition, op.get());
            }

            @Test
            void testGetMaxPlayCountCondition() throws ExecutionException {

                final String condition1 = " and media_file.play_count <= :maxPlayCount";
                final String condition2 = " and (media_file.play_count is null or media_file.play_count <= :maxPlayCount)";

                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        null, null, null, null, null, null, null, false, false, null);
                assertNull(criteria.getMinLastPlayedDate());
                assertNull(criteria.getMaxLastPlayedDate());
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                Optional<String> op = builder.getMaxPlayCountCondition();
                assertFalse(op.isPresent());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, null,
                        null, 1, 2, false, false, null);
                assertEquals(1, criteria.getMinPlayCount());
                assertEquals(2, criteria.getMaxPlayCount());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getMaxPlayCountCondition();
                assertEquals(condition1, op.get());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, null,
                        null, null, 1, false, false, null);
                assertNull(criteria.getMinPlayCount());
                assertEquals(1, criteria.getMaxPlayCount());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getMaxPlayCountCondition();
                assertEquals(condition2, op.get());
            }

            @Test
            void testGetShowStarredSongsCondition() throws ExecutionException {

                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        null, null, null, null, null, null, null, false, false, null);
                assertFalse(criteria.isShowStarredSongs());
                assertFalse(criteria.isShowUnstarredSongs());
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                Optional<String> op = builder.getShowStarredSongsCondition();
                assertFalse(op.isPresent());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, null,
                        null, null, null, true, false, null);
                assertTrue(criteria.isShowStarredSongs());
                assertFalse(criteria.isShowUnstarredSongs());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getShowStarredSongsCondition();
                assertEquals(CONDITION_SHOW_STARRED_SONGS, op.get());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, null,
                        null, null, null, true, true, null);
                assertTrue(criteria.isShowStarredSongs());
                assertTrue(criteria.isShowUnstarredSongs());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getShowStarredSongsCondition();
                assertFalse(op.isPresent());
            }

            @Test
            void testGetShowUnstarredSongsCondition() throws ExecutionException {

                final String condition = " and starred_media_file.id is null";

                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        null, null, null, null, null, null, null, false, false, null);
                assertFalse(criteria.isShowStarredSongs());
                assertFalse(criteria.isShowUnstarredSongs());
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                Optional<String> op = builder.getShowUnstarredSongsCondition();
                assertFalse(op.isPresent());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, null,
                        null, null, null, false, true, null);
                assertFalse(criteria.isShowStarredSongs());
                assertTrue(criteria.isShowUnstarredSongs());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getShowUnstarredSongsCondition();
                assertEquals(condition, op.get());

                criteria = new ShuffleSelectionParam(0, null, null, null, null, null, null, null,
                        null, null, null, true, true, null);
                assertTrue(criteria.isShowStarredSongs());
                assertTrue(criteria.isShowUnstarredSongs());
                builder = new RandomSongsQueryBuilder(criteria);
                op = builder.getShowUnstarredSongsCondition();
                assertFalse(op.isPresent());
            }

            @Test
            void testBuild() throws ExecutionException {

                final String noOp = SELECT + WHERE + ORDER_LIMIT;
                ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null,
                        Collections.emptyList(), null, null, null, null, null, null, false, false,
                        null);
                RandomSongsQueryBuilder builder = new RandomSongsQueryBuilder(criteria);
                String query = builder.build();
                assertEquals(noOp, query);

                final String ifJoinStarred = SELECT + CONDITION_JOIN1 + WHERE
                        + CONDITION_SHOW_STARRED_SONGS + ORDER_LIMIT;
                criteria = new ShuffleSelectionParam(0, null, null, null, Collections.emptyList(),
                        null, null, null, null, null, null, true, false, null);
                builder = new RandomSongsQueryBuilder(criteria);
                query = builder.build();
                assertEquals(ifJoinStarred, query);

                final String ifJoinAlbumRating = SELECT + CONDITION_JOIN2 + WHERE
                        + CONDITION_MIN_ALBUM_RATING + ORDER_LIMIT;
                criteria = new ShuffleSelectionParam(0, null, null, null, Collections.emptyList(),
                        null, null, 1, null, null, null, false, false, null);
                builder = new RandomSongsQueryBuilder(criteria);
                query = builder.build();
                assertEquals(ifJoinAlbumRating, query);

                final String ifJoinStarredAndRating = SELECT + CONDITION_JOIN1 + CONDITION_JOIN2
                        + WHERE + CONDITION_MIN_ALBUM_RATING + CONDITION_SHOW_STARRED_SONGS
                        + ORDER_LIMIT;
                criteria = new ShuffleSelectionParam(0, null, null, null, Collections.emptyList(),
                        null, null, 1, null, null, null, true, false, null);
                builder = new RandomSongsQueryBuilder(criteria);
                query = builder.build();
                assertEquals(ifJoinStarredAndRating, query);

                final Function<String, Boolean> assertWithCondition = (q) -> {
                    String suffix = SELECT + WHERE;
                    return q.startsWith(suffix) && q.endsWith(ORDER_LIMIT)
                            && suffix.length() < q.length();
                };

                // folder
                List<MusicFolder> folders = new ArrayList<>();
                folders.add(new MusicFolder("/", "", false, null, false));
                criteria = new ShuffleSelectionParam(0, null, null, null, folders, null, null, null,
                        null, null, null, false, false, null);
                builder = new RandomSongsQueryBuilder(criteria);
                query = builder.build();
                assertTrue(assertWithCondition.apply(query));

                // genre
                List<String> genres = new ArrayList<>();
                genres.add("genre");
                criteria = new ShuffleSelectionParam(0, genres, null, null, Collections.emptyList(),
                        null, null, null, null, null, null, false, false, null);
                builder = new RandomSongsQueryBuilder(criteria);
                query = builder.build();
                assertTrue(assertWithCondition.apply(query));

                // format
                criteria = new ShuffleSelectionParam(0, null, null, null, Collections.emptyList(),
                        null, null, null, null, null, null, false, false, "mp3");
                builder = new RandomSongsQueryBuilder(criteria);
                query = builder.build();
                assertTrue(assertWithCondition.apply(query));

                // fromYear
                criteria = new ShuffleSelectionParam(0, null, 1900, null, Collections.emptyList(),
                        null, null, null, null, null, null, false, false, null);
                builder = new RandomSongsQueryBuilder(criteria);
                query = builder.build();
                assertTrue(assertWithCondition.apply(query));

                // toYear
                criteria = new ShuffleSelectionParam(0, null, null, 2021, Collections.emptyList(),
                        null, null, null, null, null, null, false, false, null);
                builder = new RandomSongsQueryBuilder(criteria);
                query = builder.build();
                assertTrue(assertWithCondition.apply(query));

                // minLastPlayedDateCondition
                criteria = new ShuffleSelectionParam(0, null, null, null, Collections.emptyList(),
                        now(), null, null, null, null, null, false, false, null);
                builder = new RandomSongsQueryBuilder(criteria);
                query = builder.build();
                assertTrue(assertWithCondition.apply(query));

                // maxLastPlayedDateCondition
                criteria = new ShuffleSelectionParam(0, null, null, null, Collections.emptyList(),
                        null, now(), null, null, null, null, false, false, null);
                builder = new RandomSongsQueryBuilder(criteria);
                query = builder.build();
                assertTrue(assertWithCondition.apply(query));

                // maxAlbumRatingCondition
                criteria = new ShuffleSelectionParam(0, null, null, null, Collections.emptyList(),
                        null, null, 1, 2, null, null, false, false, null);
                builder = new RandomSongsQueryBuilder(criteria);
                query = builder.build();
                String suffix = SELECT + CONDITION_JOIN2 + WHERE;
                assertTrue(query.startsWith(suffix));
                assertTrue(query.endsWith(ORDER_LIMIT));
                assertTrue(suffix.length() < query.length());

                // minPlayCountCondition
                criteria = new ShuffleSelectionParam(0, null, null, null, Collections.emptyList(),
                        null, null, null, null, 1, null, false, false, null);
                builder = new RandomSongsQueryBuilder(criteria);
                query = builder.build();
                assertTrue(assertWithCondition.apply(query));

                // maxPlayCountCondition
                criteria = new ShuffleSelectionParam(0, null, null, null, Collections.emptyList(),
                        null, null, null, null, null, 1, false, false, null);
                builder = new RandomSongsQueryBuilder(criteria);
                query = builder.build();
                assertTrue(assertWithCondition.apply(query));

                // showUnstarredSongsCondition
                criteria = new ShuffleSelectionParam(0, null, null, null, Collections.emptyList(),
                        null, null, null, null, null, null, false, true, null);
                builder = new RandomSongsQueryBuilder(criteria);
                query = builder.build();
                suffix = SELECT + CONDITION_JOIN1 + WHERE;
                assertTrue(query.startsWith(suffix));
                assertTrue(query.endsWith(ORDER_LIMIT));
                assertTrue(suffix.length() < query.length());
            }
        }
    }

    @Nested
    @NeedsTranscode
    class IntegrationTest extends AbstractNeedsScan {

        private static final MusicFolder MUSIC_FOLDER = new MusicFolder(0,
                resolveBaseMediaPath("Browsing/MessyFileStructure/Folder"), "Folder", true, now(),
                1, false);

        @Autowired
        private MediaFileDao mediaFileDao;

        @Override
        public List<MusicFolder> getMusicFolders() {
            return List.of(MUSIC_FOLDER);
        }

        @BeforeEach
        void setup() {
            populateDatabaseOnlyOnce();
        }

        @Test
        void testGetChildSizeOfMusicFolder() {
            assertEquals(5, mediaFileDao.getChildSizeOf(getMusicFolders()), "No filter");
            assertEquals(4, mediaFileDao.getChildSizeOf(getMusicFolders(), MediaType.MUSIC),
                    "Other than Music");
            assertEquals(4, mediaFileDao.getChildSizeOf(getMusicFolders(), MediaType.VIDEO),
                    "Other than Video");
            assertEquals(4, mediaFileDao.getChildSizeOf(getMusicFolders(), MediaType.ALBUM),
                    "Other than Album");
            assertEquals(3, mediaFileDao.getChildSizeOf(getMusicFolders(), MediaType.DIRECTORY),
                    "Other than Dir");
            assertEquals(3,
                    mediaFileDao
                        .getChildSizeOf(getMusicFolders(), MediaType.MUSIC, MediaType.VIDEO),
                    "Other than Music and Video");
        }

        /**
         * SQL: Boundary value testing for count. NewestAlbums is the only place within
         * Apps where count can be 0 (UPnP's View-Paging). Please note that in the
         * current Dao implementation, a count of 0 is treated as having no condition.
         */
        @Test
        void testGetNewestAlbums() {

            // There are 4 test data albums in total.
            List<MediaFile> albums = mediaFileDao
                .getNewestAlbums(0, Integer.MAX_VALUE, getMusicFolders());
            assertEquals(4, albums.size());

            albums = mediaFileDao.getNewestAlbums(0, 1, getMusicFolders());
            assertEquals(1, albums.size());

            albums = mediaFileDao.getNewestAlbums(0, 2, getMusicFolders());
            assertEquals(2, albums.size());

            albums = mediaFileDao.getNewestAlbums(0, 3, getMusicFolders());
            assertEquals(3, albums.size());

            albums = mediaFileDao.getNewestAlbums(0, 4, getMusicFolders());
            assertEquals(4, albums.size());

            // Up until this point, everything has gone as expected...
            albums = mediaFileDao.getNewestAlbums(0, 5, getMusicFolders());
            assertEquals(4, albums.size());

            // Please note that even if we specify 0, it will not return 0.
            albums = mediaFileDao.getNewestAlbums(0, 0, getMusicFolders());
            assertEquals(4, albums.size());
        }
    }
}

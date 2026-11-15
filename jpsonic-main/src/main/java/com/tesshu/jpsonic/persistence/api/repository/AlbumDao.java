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

package com.tesshu.jpsonic.persistence.api.repository;

import static com.tesshu.jpsonic.persistence.base.DaoUtils.prefix;
import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.tesshu.jpsonic.domain.model.Artist;
import com.tesshu.jpsonic.domain.policy.RuntimeOrderPolicy.AlbumSortOrder;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.base.DaoUtils;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import com.tesshu.jpsonic.persistence.dialect.DialectAlbumDao;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Provides database services for albums.
 *
 * @author Sindre Mehus
 */
@SuppressWarnings({ "checkstyle:OverloadMethodsDeclarationOrder", "PMD.AvoidDuplicateLiterals",
        "PMD.FieldDeclarationsShouldBeAtStartOfClass" })
@Repository
public class AlbumDao {

    private static final String INSERT_COLUMNS = DaoUtils.getInsertColumns(Album.class);
    private static final String QUERY_COLUMNS = DaoUtils.getQueryColumns(Album.class);

    private final RowMapper<Album> rowMapper = DaoUtils.createRowMapper(Album.class);
    private final TemplateWrapper template;

    private final DialectAlbumDao dialect;

    public AlbumDao(TemplateWrapper templateWrapper, DialectAlbumDao dialect) {
        template = templateWrapper;
        this.dialect = dialect;
    }

    public @Nullable Album getAlbum(int id) {
        return template.queryOne("select " + QUERY_COLUMNS + """
                from album
                where id=?
                """, rowMapper, id);
    }

    public @Nullable Album getAlbum(String artistName, String albumName) {
        return template.queryOne("select " + QUERY_COLUMNS + """
                from album
                where artist=? and name=?
                """, rowMapper, artistName, albumName);
    }

    public List<Album> getAlbumsForArtist(final long offset, final long count, final String artist,
            boolean byYear, final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("artist", artist, "folders", MusicFolder.toIdList(musicFolders), "offset", offset,
                    "count", count);
        String orderWithYear = byYear ? "year is null, year, " : "";
        return template.namedQuery("select " + QUERY_COLUMNS + """
                from album
                where artist = :artist and present and folder_id in (:folders)
                order by %s album_order
                limit :count offset :offset
                """.formatted(orderWithYear), rowMapper, args);
    }

    public @Nullable Album createAlbum(Album album) {
        String query = "insert into album (" + INSERT_COLUMNS + """
                ) values (?, ?, ?,
                        (select count(*)
                            from media_file
                            where parent_path = ? and (type=? or type=? or type=?)),
                        (select sum(duration_seconds)
                            from media_file
                            where parent_path = ? and (type=? or type=? or type=?)),
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        int c = template
            .update(query, album.getPath(), album.getName(), album.getArtist(), album.getPath(),
                    MediaType.MUSIC.name(), MediaType.PODCAST.name(), MediaType.AUDIOBOOK.name(),
                    album.getPath(), MediaType.MUSIC.name(), MediaType.PODCAST.name(),
                    MediaType.AUDIOBOOK.name(), album.getCoverArtPath(), album.getYear(),
                    album.getGenre(), album.getPlayCount(), album.getLastPlayed(),
                    album.getComment(), album.getCreated(), album.getLastScanned(),
                    album.isPresent(), album.getFolderId(), album.getMusicBrainzReleaseId(),
                    album.getArtistSort(), album.getNameSort(), album.getArtistReading(),
                    album.getNameReading(), -1);
        if (c > 0) {
            return getAlbum(album.getArtist(), album.getName());
        }
        return null;
    }

    public @Nullable Album updateAlbum(Album album) {
        String sql = """
                update album
                set path=?,
                        song_count=
                                (select count(*)
                                from media_file
                                where parent_path = ? and (type=? or type=? or type=?)),
                        duration_seconds=
                                (select sum(duration_seconds)
                                from media_file
                                where parent_path = ? and (type=? or type=? or type=?)),
                        cover_art_path=?, year=?, genre=?, play_count=?, last_played=?,
                        comment=?, created=?, last_scanned=?, present=?, folder_id=?,
                        mb_release_id=?, artist_sort=?, name_sort=?, artist_reading=?,
                        name_reading=?, album_order=?  where artist=? and name=?
                """;
        int c = template
            .update(sql, album.getPath(), album.getPath(), MediaType.MUSIC.name(),
                    MediaType.PODCAST.name(), MediaType.AUDIOBOOK.name(), album.getPath(),
                    MediaType.MUSIC.name(), MediaType.PODCAST.name(), MediaType.AUDIOBOOK.name(),
                    album.getCoverArtPath(), album.getYear(), album.getGenre(),
                    album.getPlayCount(), album.getLastPlayed(), album.getComment(),
                    album.getCreated(), album.getLastScanned(), album.isPresent(),
                    album.getFolderId(), album.getMusicBrainzReleaseId(), album.getArtistSort(),
                    album.getNameSort(), album.getArtistReading(), album.getNameReading(),
                    album.getOrder(), album.getArtist(), album.getName());
        if (c > 0) {
            return getAlbum(album.getArtist(), album.getName());
        }
        return null;
    }

    public int updateOrder(int id, int order) {
        return template.update("""
                update album
                set album_order = ?
                where id=?
                """, order, id);
    }

    public void updateCoverArtPath(String artist, String name, String coverArtPath) {
        template.update("""
                update album
                set cover_art_path = ?
                where artist=? and name=?
                """, coverArtPath, artist, name);
    }

    public void updatePlayCount(String artist, String name, Instant lastPlayed, int playCount) {
        template.update("""
                update album
                set last_played = ?, play_count = ?
                where artist=? and name=?
                """, lastPlayed, playCount, artist, name);
    }

    public List<Album> getAlphabeticalAlbums(final int offset, final int count, boolean byArtist,
            boolean ignoreCase, final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("folders", MusicFolder.toIdList(musicFolders), "count", count, "offset", offset);

        String join = "";
        String order;
        if (byArtist && ignoreCase) {
            join = "left join artist on artist.present and artist.name = album.artist";
            order = "artist_order, album_order";
        } else if (byArtist && !ignoreCase) {
            join = "left join artist on artist.present and artist.name = album.artist";
            order = "artist.reading, album.name_reading";
        } else if (!byArtist && ignoreCase) {
            order = "album_order";
        } else {
            order = "album.name_reading";
        }

        return template.namedQuery("select " + prefix(QUERY_COLUMNS, "album") + """
                from album
                %s
                where album.present and album.folder_id in (:folders)
                order by %s
                limit :count offset :offset
                """.formatted(join, order), rowMapper, args);
    }

    public List<Album> getMostFrequentlyPlayedAlbums(final int offset, final int count,
            final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("folders", MusicFolder.toIdList(musicFolders), "count", count, "offset", offset);
        return template.namedQuery("select " + QUERY_COLUMNS + """
                from album
                where play_count > 0 and present and folder_id in (:folders)
                order by play_count desc
                limit :count offset :offset
                """, rowMapper, args);
    }

    public List<Album> getMostRecentlyPlayedAlbums(final int offset, final int count,
            final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("folders", MusicFolder.toIdList(musicFolders), "count", count, "offset", offset);
        return template.namedQuery("select " + QUERY_COLUMNS + """
                from album
                where last_played is not null and present and folder_id in (:folders)
                order by last_played desc
                limit :count offset :offset
                """, rowMapper, args);
    }

    public List<Album> getNewestAlbums(final int offset, final int count,
            final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("folders", MusicFolder.toIdList(musicFolders), "count", count, "offset", offset);
        return template.namedQuery("select " + QUERY_COLUMNS + """
                from album
                where present and folder_id in (:folders)
                order by created desc
                limit :count offset :offset
                """, rowMapper, args);
    }

    public List<Album> getStarredAlbums(final int offset, final int count, final String username,
            final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("folders", MusicFolder.toIdList(musicFolders), "count", count, "offset", offset,
                    "username", username);
        return template.namedQuery("select " + prefix(QUERY_COLUMNS, "album") + """
                from starred_album, album
                where album.id = starred_album.album_id and album.present
                        and album.folder_id in (:folders) and starred_album.username = :username
                order by starred_album.created desc
                limit :count offset :offset
                """, rowMapper, args);
    }

    public List<Album> getAlbumsByYear(final int offset, final int count, final int fromYear,
            final int toYear, final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("folders", MusicFolder.toIdList(musicFolders), "count", count, "offset", offset,
                    "fromYear", fromYear, "toYear", toYear);
        if (fromYear <= toYear) {
            return template.namedQuery("select " + QUERY_COLUMNS + """
                    from album
                    where present and folder_id in (:folders) and year between :fromYear and :toYear
                    order by year, album_order limit :count offset :offset
                    """, rowMapper, args);
        } else {
            return template.namedQuery("select " + QUERY_COLUMNS + """
                    from album
                    where present and folder_id in (:folders) and year between :toYear and :fromYear
                    order by year desc, album_order
                    limit :count offset :offset
                    """, rowMapper, args);
        }
    }

    public List<Album> getAlbumsByGenre(long offset, long count, List<String> genres,
            List<MusicFolder> folders) {
        return dialect.getAlbumsByGenre(offset, count, genres, folders);
    }

    public void iterateLastScanned(@NonNull Instant scanDate, boolean withPodcast) {
        String podcastQuery = withPodcast ? "or child.type=?" : "";
        String query = """
                update album
                set last_scanned = ?, present = ?
                where id in
                        (select distinct al.id
                            from album al
                            join media_file child
                            on child.present and child.album = al.name
                                    and child.album_artist = al.artist
                                    and (child.type=? or child.type=? %s))
                """.formatted(podcastQuery);
        if (withPodcast) {
            template
                .update(query, scanDate, true, MediaType.MUSIC.name(), MediaType.AUDIOBOOK.name(),
                        MediaType.PODCAST.name());
        } else {
            template
                .update(query, scanDate, true, MediaType.MUSIC.name(), MediaType.AUDIOBOOK.name());
        }
    }

    public List<Integer> getExpungeCandidates(@NonNull Instant scanDate) {
        return template.queryForInts("""
                select id
                from album
                where last_scanned <> ? or not present
                """, scanDate);
    }

    public void expunge(@NonNull Instant scanDate) {
        template.update("""
                delete from album
                where last_scanned <> ? or not present
                """, scanDate);
    }

    public void starAlbum(int albumId, String username) {
        unstarAlbum(albumId, username);
        template.update("""
                insert into starred_album(album_id, username, created)
                values (?,?,?)
                """, albumId, username, now());
    }

    public void unstarAlbum(int albumId, String username) {
        template.update("""
                delete from starred_album
                where album_id=? and username=?
                """, albumId, username);
    }

    public Instant getAlbumStarredDate(int albumId, String username) {
        return template.queryForInstant("""
                select created
                from starred_album
                where album_id=? and username=?
                """, null, albumId, username);
    }

    public int getAlbumsCountForArtist(final String artist, final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return 0;
        }
        Map<String, Object> args = LegacyMap
            .of("artist", artist, "folders", MusicFolder.toIdList(musicFolders));
        return template.namedQueryForInt("""
                select count(id)
                from album
                where artist = :artist and present and folder_id in (:folders)
                """, 0, args);
    }

    // ############################################################################
    // Jpsonic domain

    private static final String DOMAIN_COLUMNS_QUERY = """
            id, name, artist, song_count, comment\s
            """;

    private final RowMapper<com.tesshu.jpsonic.domain.model.Album> domainRowMapper = (rs,
            num) -> new com.tesshu.jpsonic.domain.model.Album(rs.getInt(1), // id,
                    rs.getString(2), // String name,
                    rs.getString(3), // String artist,
                    rs.getInt(4), // int songCount,
                    rs.getString(5) // String comment
    );

    public int countAlbums(List<com.tesshu.jpsonic.domain.model.MusicFolder> folders) {
        if (folders.isEmpty()) {
            return 0;
        }
        Map<String, Object> args = Map
            .of("folders",
                    folders.stream().map(com.tesshu.jpsonic.domain.model.MusicFolder::id).toList());
        return template.namedQueryForInt("""
                select count(*)
                from album
                where folder_id in (:folders)
                """, 0, args);
    }

    public List<com.tesshu.jpsonic.domain.model.Album> findAlbums(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders, AlbumSortOrder order,
            long offset, long count) {
        if (folders.isEmpty()) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = Map
            .of("folders",
                    folders.stream().map(com.tesshu.jpsonic.domain.model.MusicFolder::id).toList(),
                    "offset", offset,
                    "count", count);
        // spotless:on

        String query = switch (order) {
        case DEFAULT -> "select " + DOMAIN_COLUMNS_QUERY + """
                from album
                where album.folder_id in (:folders)
                order by album_order
                offset :offset limit :count
                """;
        case BY_ARTIST_AND_ALBUM -> "select " + prefix(DOMAIN_COLUMNS_QUERY, "album") + """
                from album
                left join artist on artist.name = album.artist
                where album.folder_id in (:folders)
                order by artist_order, album_order
                offset :offset limit :count
                """;
        case YEAR -> throw new IllegalArgumentException("Not yet supported.");
        };

        return template.namedQuery(query, domainRowMapper, args);
    }

    public List<com.tesshu.jpsonic.domain.model.Album> findAlbums(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders, Artist parent,
            AlbumSortOrder order, long offset, long count) {
        if (folders.isEmpty()) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = Map.of(
                    "artist", parent.name(),
                    "folders",
                    folders.stream().map(com.tesshu.jpsonic.domain.model.MusicFolder::id).toList(),
                    "offset", offset,
                    "count", count);
        // spotless:on

        String orderQuery = switch (order) {
        case DEFAULT -> "order by album_order";
        case YEAR -> "order by year is null, year, album_order";
        case BY_ARTIST_AND_ALBUM -> throw new IllegalArgumentException("Not yet supported.");
        };

        return template.namedQuery("select " + DOMAIN_COLUMNS_QUERY + """
                from album
                where artist = :artist and folder_id in (:folders)
                %s
                offset :offset limit :count
                """.formatted(orderQuery), domainRowMapper, args);
    }

    public List<com.tesshu.jpsonic.domain.model.Album> findNewestAlbums(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders, long offset, long count) {
        if (folders.isEmpty()) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = Map.of(
                    "folders",
                    folders.stream().map(com.tesshu.jpsonic.domain.model.MusicFolder::id).toList(),
                    "offset", offset,
                    "count", count);
        // spotless:on

        return template.namedQuery("select " + DOMAIN_COLUMNS_QUERY + """
                from album
                where present and folder_id in (:folders)
                order by created desc
                offset :offset limit :count
                """, domainRowMapper, args);
    }

    // ############################################################################
    // Jpsonic domain (Search)

    public com.tesshu.jpsonic.domain.model.Album getDomainAlbum(int id) {
        return template.queryOne("select " + DOMAIN_COLUMNS_QUERY + """
                from album
                where id=?
                """, domainRowMapper, id);
    }

    public List<com.tesshu.jpsonic.domain.model.Album> findAlbums(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders, List<String> genres,
            AlbumSortOrder order, long offset, long count) {
        if (genres.isEmpty() || folders.isEmpty()) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = Map.of(
                    "folders",
                    folders.stream().map(com.tesshu.jpsonic.domain.model.MusicFolder::id).toList(),
                    "genres", genres,
                    "offset", offset,
                    "count", count);
        // spotless:on

        String query = switch (order) {
        case DEFAULT -> "select " + DOMAIN_COLUMNS_QUERY + """
                from album
                where album.folder_id in (:folders) and album.genre in (:genres)
                order by album_order
                offset :offset limit :count
                """;
        case BY_ARTIST_AND_ALBUM -> "select " + prefix(DOMAIN_COLUMNS_QUERY, "album") + """
                from album
                left join artist on artist.name = album.artist
                where album.folder_id in (:folders) and album.genre in (:genre)
                order by artist_order, album_order
                offset :offset limit :count
                """;
        case YEAR -> throw new IllegalArgumentException("Not yet supported.");
        };

        return template.namedQuery(query, domainRowMapper, args);
    }
}

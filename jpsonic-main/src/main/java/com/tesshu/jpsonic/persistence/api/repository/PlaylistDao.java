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

import static com.tesshu.jpsonic.persistence.base.DaoUtils.nullableInstantOf;
import static com.tesshu.jpsonic.persistence.base.DaoUtils.prefix;
import static com.tesshu.jpsonic.persistence.base.DaoUtils.questionMarks;
import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.Playlist;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides database services for playlists.
 *
 * @author Sindre Mehus
 */
@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "PMD.FieldDeclarationsShouldBeAtStartOfClass" })
@Repository
public class PlaylistDao {

    private static final String INSERT_COLUMNS = """
            username, is_public, name, comment, file_count, duration_seconds,
            created, changed, imported_from\s
            """;
    private static final String QUERY_COLUMNS = "id, " + INSERT_COLUMNS;

    private final TemplateWrapper template;
    private final RowMapper<Playlist> rowMapper;

    public PlaylistDao(TemplateWrapper templateWrapper) {
        template = templateWrapper;
        rowMapper = new PlaylistMapper();
    }

    public List<Playlist> getReadablePlaylistsForUser(String username) {

        List<Playlist> result1 = getWritablePlaylistsForUser(username);
        List<Playlist> result2 = template.query("select " + QUERY_COLUMNS + """
                from playlist
                where is_public
                """, rowMapper);
        List<Playlist> result3 = template.query("select " + prefix(QUERY_COLUMNS, "playlist") + """
                from playlist, playlist_user
                where playlist.id = playlist_user.playlist_id
                        and playlist.username != ?
                        and playlist_user.username = ?
                """, rowMapper, username, username);

        // Put in sorted map to avoid duplicates.
        SortedMap<Integer, Playlist> map = new TreeMap<>();
        for (Playlist playlist : result1) {
            map.put(playlist.getId(), playlist);
        }
        for (Playlist playlist : result2) {
            map.put(playlist.getId(), playlist);
        }
        for (Playlist playlist : result3) {
            map.put(playlist.getId(), playlist);
        }
        return new ArrayList<>(map.values());
    }

    public List<Playlist> getWritablePlaylistsForUser(String username) {
        return template.query("select " + QUERY_COLUMNS + """
                from playlist
                where username=?
                """, rowMapper, username);
    }

    public @Nullable Playlist getPlaylist(int id) {
        return template.queryOne("select " + QUERY_COLUMNS + """
                from playlist
                where id=?
                """, rowMapper, id);
    }

    public List<Playlist> getAllPlaylists() {
        return template.query("select " + QUERY_COLUMNS + """
                from playlist
                """, rowMapper);
    }

    @Transactional
    public void createPlaylist(Playlist playlist) {
        template
            .update("insert into playlist(" + INSERT_COLUMNS + ") values("
                    + questionMarks(INSERT_COLUMNS) + ")", playlist.getUsername(),
                    playlist.isShared(), playlist.getName(), playlist.getComment(), 0, 0,
                    playlist.getCreated(), playlist.getChanged(), playlist.getImportedFrom());

        int id = template.queryForInt("select max(id) from playlist", 0);
        playlist.setId(id);
    }

    @Transactional
    public void setFilesInPlaylist(int id, List<MediaFile> files) {
        template.update("""
                delete from playlist_file
                where playlist_id=?
                """, id);
        int duration = 0;
        for (MediaFile file : files) {
            template.update("""
                    insert into playlist_file (playlist_id, media_file_id)
                    values (?, ?)
                    """, id, file.getId());
            Integer ds = file.getDurationSeconds();
            if (ds != null) {
                duration += ds;
            }
        }
        template.update("""
                update playlist
                set file_count=?, duration_seconds=?, changed=?
                where id=?
                """, files.size(), duration, now(), id);
    }

    public List<String> getPlaylistUsers(int playlistId) {
        return template.queryForStrings("""
                select username
                from playlist_user
                where playlist_id=?
                """, playlistId);
    }

    public void addPlaylistUser(int playlistId, String username) {
        if (!getPlaylistUsers(playlistId).contains(username)) {
            template.update("""
                    insert into playlist_user(playlist_id,username)
                    values (?,?)
                    """, playlistId, username);
        }
    }

    public void deletePlaylistUser(int playlistId, String username) {
        template.update("""
                delete from
                playlist_user
                where playlist_id=? and username=?
                """, playlistId, username);
    }

    @Transactional
    public void deletePlaylist(int id) {
        template.update("""
                delete from playlist
                where id=?
                """, id);
    }

    public void updatePlaylist(Playlist playlist) {
        template
            .update("""
                    update playlist
                    set username=?, is_public=?, name=?, comment=?, changed=?, imported_from=?
                    where id=?
                    """, playlist.getUsername(), playlist.isShared(), playlist.getName(),
                    playlist.getComment(), now(), playlist.getImportedFrom(), playlist.getId());
    }

    public int getCountAll() {
        return template.queryForInt("""
                select count(id)
                from playlist
                """, 0);
    }

    private static class PlaylistMapper implements RowMapper<Playlist> {
        @Override
        public Playlist mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Playlist(rs.getInt(1), rs.getString(2), rs.getBoolean(3), rs.getString(4),
                    rs.getString(5), rs.getInt(6), rs.getInt(7),
                    nullableInstantOf(rs.getTimestamp(8)), nullableInstantOf(rs.getTimestamp(9)),
                    rs.getString(10));
        }
    }

    // ############################################################################
    // Jpsonic domain

    private static final String DOMAIN_COLUMNS_QUERY = "id, name, comment, file_count\s";
    private static final String DOMAIN_TABLES_QUERY = "from playlist\s";
    private final RowMapper<com.tesshu.jpsonic.domain.model.Playlist> domainRowMapper = (
            ResultSet rs, int rowNum) -> new com.tesshu.jpsonic.domain.model.Playlist(rs.getInt(1), // id
                    rs.getString(2), // name
                    rs.getString(3), // comment,
                    rs.getInt(4)); // fileCount

    public int countPlaylists() {
        return template.queryForInt("""
                select count(id)
                from playlist
                """, 0);
    }

    public int countPublishedPlaylists() {
        return template.queryForInt("""
                select count(id)
                from playlist where is_public
                """, 0);
    }

    public com.tesshu.jpsonic.domain.model.Playlist getDomainPlaylist(int id) {
        return template
            .queryOne("select " + DOMAIN_COLUMNS_QUERY + DOMAIN_TABLES_QUERY + "where id=?",
                    domainRowMapper, id);
    }

    public List<com.tesshu.jpsonic.domain.model.Playlist> findPlaylists(long offset, long count) {
        Map<String, Object> args = Map.of("offset", offset, "count", count);
        String query = "select " + DOMAIN_COLUMNS_QUERY + DOMAIN_TABLES_QUERY
                + "offset :offset limit :count";
        return template.namedQuery(query, domainRowMapper, args);
    }

    public List<com.tesshu.jpsonic.domain.model.Playlist> findPublishedPlaylists(long offset,
            long count) {
        Map<String, Object> args = Map.of("offset", offset, "count", count);
        String query = "select " + DOMAIN_COLUMNS_QUERY + DOMAIN_TABLES_QUERY + """
                    where is_public
                    offset :offset limit :count
                """;
        return template.namedQuery(query, domainRowMapper, args);
    }

    @Transactional
    public void setDomainFilesInPlaylist(int id,
            List<com.tesshu.jpsonic.domain.model.MediaFile> files) {
        template.update("""
                delete from playlist_file
                where playlist_id=?
                """, id);
        int duration = 0;
        for (com.tesshu.jpsonic.domain.model.MediaFile file : files) {
            template.update("""
                    insert into playlist_file (playlist_id, media_file_id)
                    values (?, ?)
                    """, id, file.id());
            if (file.durationSeconds().isPresent()) {
                duration += file.durationSeconds().value();
            }
        }
        template.update("""
                update playlist
                set file_count=?, duration_seconds=?, changed=?
                where id=?
                """, files.size(), duration, now(), id);
    }
}

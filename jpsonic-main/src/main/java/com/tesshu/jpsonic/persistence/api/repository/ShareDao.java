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
import static com.tesshu.jpsonic.persistence.base.DaoUtils.questionMarks;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.entity.Share;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides database services for shared media.
 *
 * @author Sindre Mehus
 */
@Repository
public class ShareDao {

    private static final String INSERT_COLUMNS = """
            name, description, username, created, expires, last_visited, visit_count\s
            """;
    private static final String QUERY_COLUMNS = "id, " + INSERT_COLUMNS;

    private final TemplateWrapper template;
    private final ShareRowMapper shareRowMapper;
    private final ShareFileRowMapper shareFileRowMapper;

    public ShareDao(TemplateWrapper templateWrapper) {
        template = templateWrapper;
        shareRowMapper = new ShareRowMapper();
        shareFileRowMapper = new ShareFileRowMapper();
    }

    @Transactional
    public void createShare(Share share) {
        String sql = "insert into share (" + INSERT_COLUMNS + ") values ("
                + questionMarks(INSERT_COLUMNS) + ")";
        template
            .update(sql, share.getName(), share.getDescription(), share.getUsername(),
                    share.getCreated(), share.getExpires(), share.getLastVisited(),
                    share.getVisitCount());
        Integer id = template.queryForInt("select max(id) from share", null);
        if (id != null) {
            share.setId(id);
        }
    }

    public List<Share> getAllShares() {
        String sql = "select " + QUERY_COLUMNS + "from share";
        return template.query(sql, shareRowMapper);
    }

    public @Nullable Share getShareByName(String shareName) {
        String sql = "select " + QUERY_COLUMNS + """
                from share
                where name=?
                """;
        return template.queryOne(sql, shareRowMapper, shareName);
    }

    public @Nullable Share getShareById(int id) {
        String sql = "select " + QUERY_COLUMNS + """
                from share
                where id=?
                """;
        return template.queryOne(sql, shareRowMapper, id);
    }

    public void updateShare(Share share) {
        String sql = """
                update share
                set name=?, description=?, username=?, created=?,
                        expires=?, last_visited=?, visit_count=?
                where id=?
                """;
        template
            .update(sql, share.getName(), share.getDescription(), share.getUsername(),
                    share.getCreated(), share.getExpires(), share.getLastVisited(),
                    share.getVisitCount(), share.getId());
    }

    public void createSharedFiles(int shareId, String... paths) {
        String sql = """
                insert into share_file (share_id, path)
                values (?, ?)
                """;
        for (String path : paths) {
            template.update(sql, shareId, path);
        }
    }

    public List<String> getSharedFiles(final int shareId, final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("shareId", shareId, "folders", MusicFolder.toPathList(musicFolders));
        return template.namedQuery("""
                select share_file.path
                from share_file, media_file
                where share_id = :shareId and share_file.path = media_file.path
                        and media_file.present and media_file.folder in (:folders)
                """, shareFileRowMapper, args);
    }

    public void deleteShare(Integer id) {
        template.update("""
                delete from share
                where id=?
                """, id);
    }

    private static class ShareRowMapper implements RowMapper<Share> {
        @Override
        public Share mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Share(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    nullableInstantOf(rs.getTimestamp(5)), nullableInstantOf(rs.getTimestamp(6)),
                    nullableInstantOf(rs.getTimestamp(7)), rs.getInt(8));
        }
    }

    private static class ShareFileRowMapper implements RowMapper<String> {
        @Override
        public String mapRow(ResultSet rs, int rowNum) throws SQLException {
            return rs.getString(1);
        }
    }
}

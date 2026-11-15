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
import static com.tesshu.jpsonic.util.PlayerUtils.defaultIfNull;

import java.util.List;

import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import com.tesshu.jpsonic.persistence.core.repository.UserDao;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Provides database services for music folders.
 *
 * @author Sindre Mehus
 */
@Repository
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class MusicFolderDao {

    private static final Logger LOG = LoggerFactory.getLogger(MusicFolderDao.class);
    private static final String INSERT_COLUMNS = """
            path, name, enabled, changed, folder_order, archived\s
            """;
    private static final String QUERY_COLUMNS = "id, " + INSERT_COLUMNS;

    private final TemplateWrapper template;
    private final RowMapper<MusicFolder> rowMapper = (rs, rowNum) -> new MusicFolder(rs.getInt(1),
            rs.getString(2), rs.getString(3), rs.getBoolean(4),
            nullableInstantOf(rs.getTimestamp(5)), rs.getInt(6), rs.getBoolean(7));

    private final RowMapper<com.tesshu.jpsonic.domain.model.MusicFolder> domainRowMapper = (rs,
            rowNum) -> new com.tesshu.jpsonic.domain.model.MusicFolder(rs.getInt(1),
                    rs.getString(2), rs.getString(3), rs.getBoolean(4),
                    nullableInstantOf(rs.getTimestamp(5)), rs.getInt(6), rs.getBoolean(7));

    private final UserDao userDao;

    public MusicFolderDao(TemplateWrapper templateWrapper, UserDao userDao) {
        template = templateWrapper;
        this.userDao = userDao;
    }

    public List<MusicFolder> getAllMusicFolders() {
        String sql = "select " + QUERY_COLUMNS + """
                from music_folder
                order by enabled desc, folder_order
                """;
        return template.query(sql, rowMapper);
    }

    public @Nullable MusicFolder getMusicFolderForPath(String path) {
        String sql = "select " + QUERY_COLUMNS + """
                from music_folder
                where path = ?
                """;
        return template.queryOne(sql, rowMapper, path);
    }

    public void createMusicFolder(MusicFolder musicFolder) {
        String sql = """
                insert into music_folder (%s)
                values (?, ?, ?, ?,
                        (select count(*) + 1 from music_folder), ?)
                """.formatted(INSERT_COLUMNS);
        template
            .update(sql, musicFolder.getPathString(), musicFolder.getName(),
                    musicFolder.isEnabled(), musicFolder.getChanged(), musicFolder.isArchived());

        Integer id = template.queryForInt("select max(id) from music_folder", 0);
        template.update("""
                insert into music_folder_user (music_folder_id, username)
                select ?, username from %s
                """.formatted(userDao.getUserTable()), id);
        if (LOG.isInfoEnabled()) {
            LOG.info("Created music folder " + musicFolder.getPathString());
        }
    }

    public void deleteMusicFolder(Integer id) {
        String sql = """
                delete from music_folder
                where id=?
                """;
        template.update(sql, id);
        if (LOG.isInfoEnabled()) {
            LOG.info("Deleted music folder with ID " + id);
        }
    }

    public void updateMusicFolder(@NonNull MusicFolder musicFolder) {
        String sql = """
                update music_folder
                set path=?, name=?, enabled=?, changed=?, folder_order=?, archived=?
                where id=?
                """;
        template
            .update(sql, musicFolder.getPathString(), musicFolder.getName(),
                    musicFolder.isEnabled(), musicFolder.getChanged(),
                    defaultIfNull(musicFolder.getFolderOrder(),
                            template.queryForInt("select count(*) from music_folder", -1)),
                    musicFolder.isArchived(), musicFolder.getId());
    }

    public com.tesshu.jpsonic.domain.model.MusicFolder getDomainMusicFolders(int id) {
        String sql = "select " + QUERY_COLUMNS + " from music_folder where music_folder.id = ?";
        return template.queryOne(sql, domainRowMapper, id);
    }

    public List<com.tesshu.jpsonic.domain.model.MusicFolder> getDomainMusicFoldersForUser(
            String username) {
        String sql = "select " + prefix(QUERY_COLUMNS, "music_folder") + """
                from music_folder, music_folder_user
                where music_folder.id = music_folder_user.music_folder_id
                        and music_folder_user.username = ?
                order by enabled desc, folder_order
                """;
        return template.query(sql, domainRowMapper, username);
    }

    public List<MusicFolder> getMusicFoldersForUser(String username) {
        String sql = "select " + prefix(QUERY_COLUMNS, "music_folder") + """
                from music_folder, music_folder_user
                where music_folder.id = music_folder_user.music_folder_id
                        and music_folder_user.username = ?
                order by enabled desc, folder_order
                """;
        return template.query(sql, rowMapper, username);
    }

    public void setMusicFoldersForUser(String username, List<Integer> musicFolderIds) {
        template.update("""
                delete from music_folder_user
                where username = ?
                """, username);
        for (Integer musicFolderId : musicFolderIds) {
            template
                .update("insert into music_folder_user(music_folder_id, username) values (?, ?)",
                        musicFolderId, username);
        }
    }
}

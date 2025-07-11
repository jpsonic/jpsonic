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

package com.tesshu.jpsonic.dao;

import static com.tesshu.jpsonic.dao.base.DaoUtils.nullableInstantOf;
import static com.tesshu.jpsonic.dao.base.DaoUtils.questionMarks;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.tesshu.jpsonic.dao.base.TemplateWrapper;
import com.tesshu.jpsonic.domain.Avatar;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Provides database services for avatars.
 *
 * @author Sindre Mehus
 */
@Repository
public class AvatarDao {

    private static final String INSERT_COLUMNS = """
            name, created_date, mime_type, width, height, data\s
            """;
    private static final String QUERY_COLUMNS = "id, " + INSERT_COLUMNS;

    private final TemplateWrapper template;
    private final AvatarRowMapper rowMapper;

    public AvatarDao(TemplateWrapper templateWrapper) {
        template = templateWrapper;
        rowMapper = new AvatarRowMapper();
    }

    public List<Avatar> getAllSystemAvatars() {
        String sql = "select " + QUERY_COLUMNS + "from system_avatar";
        return template.query(sql, rowMapper);
    }

    public @Nullable Avatar getSystemAvatar(int id) {
        String sql = "select " + QUERY_COLUMNS + """
                from system_avatar
                where id=?
                """;
        return template.queryOne(sql, rowMapper, id);
    }

    public @Nullable Avatar getCustomAvatar(String username) {
        String sql = "select " + QUERY_COLUMNS + """
                from custom_avatar
                where username=?
                """;
        return template.queryOne(sql, rowMapper, username);
    }

    /**
     * Sets the custom avatar for the given user.
     *
     * @param avatar   The avatar, or <code>null</code> to remove the avatar.
     * @param username The username.
     */
    public void setCustomAvatar(Avatar avatar, String username) {
        String sql = """
                delete from custom_avatar
                where username=?
                """;
        template.update(sql, username);

        if (avatar != null) {
            template
                .update("insert into custom_avatar(" + INSERT_COLUMNS + ", username) values("
                        + questionMarks(INSERT_COLUMNS) + ", ?)", avatar.getName(),
                        avatar.getCreatedDate(), avatar.getMimeType(), avatar.getWidth(),
                        avatar.getHeight(), avatar.getData(), username);
        }
    }

    private static class AvatarRowMapper implements RowMapper<Avatar> {
        @Override
        public Avatar mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Avatar(rs.getInt(1), rs.getString(2), nullableInstantOf(rs.getTimestamp(3)),
                    rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getBytes(7));
        }
    }

}

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
 * (C) 2024 tesshucom
 */

package com.tesshu.jpsonic.persistence.dialect;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.db.DatabaseConfiguration.ProfileNameConstants;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.base.DaoUtils;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
@Profile({ ProfileNameConstants.URL, ProfileNameConstants.JNDI })
public class AnsiAlbumDao implements DialectAlbumDao {

    private static final String QUERY_COLUMNS = DaoUtils.getQueryColumns(Album.class);
    private final RowMapper<Album> rowMapper = DaoUtils.createRowMapper(Album.class);
    private final TemplateWrapper template;

    public AnsiAlbumDao(TemplateWrapper templateWrapper) {
        template = templateWrapper;
    }

    @Override
    public List<Album> getAlbumsByGenre(long offset, long count, List<String> genres,
            List<MusicFolder> folders) {
        if (genres.isEmpty() || folders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("folders", MusicFolder.toIdList(folders), "genres", genres, "count", count,
                    "offset", offset);
        return template.namedQuery("select " + QUERY_COLUMNS + """
                from album
                where present and folder_id in (:folders) and genre in (:genres)
                order by album_order
                limit :count offset :offset
                """, rowMapper, args);
    }
}

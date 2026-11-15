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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides database services for ratings.
 *
 * @author Sindre Mehus
 */
@Repository("musicFileInfoDao")
public class RatingDao {

    private final TemplateWrapper template;

    public RatingDao(TemplateWrapper templateWrapper) {
        template = templateWrapper;
    }

    public List<String> getHighestRatedAlbums(final int offset, final int count,
            final List<MusicFolder> musicFolders) {
        if (count < 1 || musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("type", MediaFile.MediaType.ALBUM.name(), "folders",
                    MusicFolder.toPathList(musicFolders), "count", count, "offset", offset);
        String sql = """
                select user_rating.path
                from user_rating, media_file
                where user_rating.path = media_file.path and media_file.present
                        and media_file.type = :type and media_file.folder in (:folders)
                group by user_rating.path
                order by avg(rating) desc
                limit :count offset :offset
                """;
        return template.namedQueryForStrings(sql, args);
    }

    @Transactional
    public void setRatingForUser(String username, MediaFile mediaFile, Integer rating) {
        if (rating != null && (rating < 1 || rating > 5)) {
            return;
        }
        template.update("""
                delete from user_rating
                where username=? and path=?
                """, username, mediaFile.getPathString());
        if (rating != null) {
            template
                .update("insert into user_rating values(?, ?, ?)", username,
                        mediaFile.getPathString(), rating);
        }
    }

    public @Nullable Double getAverageRating(MediaFile mediaFile) {
        try {
            return template.getJdbcTemplate().queryForObject("""
                    select avg(rating)
                    from user_rating
                    where path=?
                    """, Double.class, mediaFile.getPathString());
        } catch (EmptyResultDataAccessException x) {
            return null;
        }
    }

    public @Nullable Integer getRatingForUser(String username, MediaFile mediaFile) {
        try {
            return template.queryForInt("""
                    select rating
                    from user_rating
                    where username=? and path=?
                    """, null, username, mediaFile.getPathString());
        } catch (EmptyResultDataAccessException x) {
            return null;
        }
    }

    public int getRatedAlbumCount(final String username, final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return 0;
        }
        Map<String, Object> args = LegacyMap
            .of("type", MediaFile.MediaType.ALBUM.name(), "folders",
                    MusicFolder.toPathList(musicFolders), "username", username);
        return template.namedQueryForInt("""
                select count(*) from user_rating, media_file
                where media_file.path = user_rating.path
                        and media_file.type = :type and media_file.present
                        and media_file.folder in (:folders)
                        and user_rating.username = :username
                """, 0, args);
    }

    public void expunge() {
        template.update("""
                delete from user_rating
                where path in
                        (select user_rating.path
                        from user_rating
                        left join media_file
                        on media_file.path = user_rating.path
                        where media_file.path is null)
                """);
    }
}

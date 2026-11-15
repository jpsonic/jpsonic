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

import java.util.List;

import com.tesshu.jpsonic.infrastructure.db.DatabaseConfiguration.ProfileNameConstants;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({ ProfileNameConstants.HOST })
public class HsqlDBAlbumDao implements DialectAlbumDao {

    private final AnsiAlbumDao deligate;

    public HsqlDBAlbumDao(TemplateWrapper templateWrapper) {
        deligate = new AnsiAlbumDao(templateWrapper);
    }

    @Override
    public List<Album> getAlbumsByGenre(long offset, long count, List<String> genres,
            List<MusicFolder> folders) {
        return deligate.getAlbumsByGenre(offset, count, genres, folders);
    }
}

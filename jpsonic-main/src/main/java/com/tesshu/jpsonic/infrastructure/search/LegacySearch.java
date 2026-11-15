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

package com.tesshu.jpsonic.infrastructure.search;

import java.util.List;

import com.tesshu.jpsonic.infrastructure.search.criteria.HttpSearchCriteria;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.param.ShuffleSelectionParam;

/**
 * Legacy search interface for the migration from persistence entities to domain
 * models. This interface will be removed once the migration is complete.
 */
public interface LegacySearch {

    LegacySearchResult search(HttpSearchCriteria criteria);

    List<MediaFile> getRandomSongs(ShuffleSelectionParam criteria);

    List<MediaFile> getRandomAlbums(int count, List<MusicFolder> musicFolders);

    List<Album> getRandomAlbumsId3(int count, List<MusicFolder> musicFolders);

    List<Album> getAlbumId3sByGenres(String genres, long offset, long count,
            List<MusicFolder> musicFolders);

    List<MediaFile> getAlbumsByGenres(String genres, long offset, long count,
            List<MusicFolder> musicFolders);

    List<MediaFile> getSongsByGenres(String genres, long offset, long count,
            List<MusicFolder> musicFolders, MediaFile.MediaType... types);
}

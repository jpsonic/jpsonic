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
 * (C) 2026 tesshucom
 */

package com.tesshu.jpsonic.adapter.resource;

import static org.springframework.util.ObjectUtils.isEmpty;

import java.util.Collections;
import java.util.List;

import com.tesshu.jpsonic.domain.model.Album;
import com.tesshu.jpsonic.domain.model.Artist;
import com.tesshu.jpsonic.domain.model.MediaFile.Type;
import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.policy.RuntimeOrderPolicy.AlbumSortOrder;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterProvider;
import com.tesshu.jpsonic.domain.provider.resource.AlbumProvider;
import com.tesshu.jpsonic.infrastructure.scanner.MediaSearchProvider;
import com.tesshu.jpsonic.persistence.api.repository.AlbumDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import org.springframework.stereotype.Component;

@Component
class AlbumProviderAdapter implements AlbumProvider {

    private final AlbumDao albumDao;
    private final MediaSearchProvider deligate;
    private final GenreMasterProvider genreMasterProvider;
    private final MediaFileDao mediaFileDao;

    AlbumProviderAdapter(AlbumDao albumDao, MediaSearchProvider deligate,
            GenreMasterProvider genreMasterProvider, MediaFileDao mediaFileDao) {
        super();
        this.albumDao = albumDao;
        this.deligate = deligate;
        this.genreMasterProvider = genreMasterProvider;
        this.mediaFileDao = mediaFileDao;
    }

    @Override
    public int countAlbums(List<MusicFolder> folders) {
        return albumDao.countAlbums(folders);
    }

    @Override
    public int countChldren(List<MusicFolder> folders, String genres, Album album, Type... types) {
        if (isEmpty(genres)) {
            return 0;
        }
        List<String> preAnalyzedGenres = genreMasterProvider
            .findPreAnalyzedGenres(List.of(genres), true);
        return mediaFileDao.countChldren(folders, preAnalyzedGenres, album, types);
    }

    @Override
    public List<Album> findAlbums(List<MusicFolder> folders, AlbumSortOrder order, long offset,
            long count) {
        return albumDao.findAlbums(folders, order, offset, count);
    }

    @Override
    public List<Album> findAlbums(List<MusicFolder> musicFolders, String genres, long offset,
            long count) {
        if (isEmpty(genres)) {
            return Collections.emptyList();
        }
        List<String> preAnalyzedGenres = genreMasterProvider
            .findPreAnalyzedGenres(List.of(genres), true);
        return albumDao
            .findAlbums(musicFolders, preAnalyzedGenres, AlbumSortOrder.DEFAULT, offset, count);
    }

    @Override
    public List<Album> findChildren(List<MusicFolder> folders, Artist parent, AlbumSortOrder order,
            long offset, long count) {
        return albumDao.findAlbums(folders, parent, order, offset, count);
    }

    @Override
    public List<Album> findNewestAlbums(List<MusicFolder> folders, long offset, long count) {
        return albumDao.findNewestAlbums(folders, offset, count);
    }

    @Override
    public List<Album> findRandomAlbums(List<MusicFolder> musicFolders, long offset, long count,
            int cacheMax) {
        return deligate.getRandomAlbumsId3(musicFolders, offset, count, cacheMax);
    }

    @Override
    public Album requireAlbum(int id) {
        Album album = albumDao.getDomainAlbum(id);
        if (album == null) {
            throw new IllegalArgumentException("The specified Album cannot be found.");
        }
        return album;
    }
}

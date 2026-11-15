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

import java.util.List;

import com.tesshu.jpsonic.domain.model.Artist;
import com.tesshu.jpsonic.domain.model.IndexWithCount;
import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.provider.resource.ArtistProvider;
import com.tesshu.jpsonic.persistence.api.repository.ArtistDao;
import org.springframework.stereotype.Component;

@Component
class ArtistProviderAdapter implements ArtistProvider {

    private final ArtistDao artistDao;

    ArtistProviderAdapter(ArtistDao artistDao) {
        this.artistDao = artistDao;
    }

    @Override
    public int countArtists(List<MusicFolder> folders) {
        return artistDao.countArtists(folders);
    }

    @Override
    public int countChildren(List<MusicFolder> folders, String artistName) {
        return artistDao.countChildren(folders, artistName);
    }

    @Override
    public int countMudicIndexes(List<MusicFolder> folders) {
        return artistDao.countMudicIndexes(folders);
    }

    @Override
    public List<Artist> findArtists(List<MusicFolder> folders, long offset, long count) {
        return artistDao.findArtists(folders, offset, count);
    }

    @Override
    public List<Artist> findArtists(List<MusicFolder> folders, String musicIndex, long offset,
            long count) {
        return artistDao.findArtists(folders, musicIndex, offset, count);
    }

    @Override
    public List<IndexWithCount> findIndexWithCounts(List<MusicFolder> folders) {
        return artistDao.findIndexWithCounts(folders);
    }

    @Override
    public Artist requireArtist(int id) {
        Artist artist = artistDao.getDomainArtist(id);
        if (artist == null) {
            throw new IllegalArgumentException("The specified Artist cannot be found.");
        }
        return artist;
    }
}

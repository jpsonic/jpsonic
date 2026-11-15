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

import java.util.ArrayList;
import java.util.List;

import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.Artist;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;

/**
 * The outcome of a search.
 *
 * @author Sindre Mehus
 *
 * @see SearchService#search
 */
public class LegacySearchResult {

    private final List<MediaFile> mediaFiles;
    private final List<Artist> artists;
    private final List<Album> albums;

    private int offset;
    private int totalHits;

    public LegacySearchResult() {
        mediaFiles = new ArrayList<>();
        artists = new ArrayList<>();
        albums = new ArrayList<>();
    }

    public List<MediaFile> getMediaFiles() {
        return mediaFiles;
    }

    public List<Artist> getArtists() {
        return artists;
    }

    public List<Album> getAlbums() {
        return albums;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(int totalHits) {
        this.totalHits = totalHits;
    }
}

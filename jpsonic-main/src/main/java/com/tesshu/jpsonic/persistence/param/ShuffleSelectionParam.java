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

package com.tesshu.jpsonic.persistence.param;

import java.time.Instant;
import java.util.List;

import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;

/**
 * Defines criteria used when generating random playlists.
 *
 * @author Sindre Mehus
 *
 * @see SearchService#getRandomSongs
 */
public class ShuffleSelectionParam {

    private final int count;
    private final List<String> genres;
    private final Integer fromYear;
    private final Integer toYear;
    private final List<MusicFolder> musicFolders;
    private final Instant minLastPlayedDate;
    private final Instant maxLastPlayedDate;
    private final Integer minAlbumRating;
    private final Integer maxAlbumRating;
    private final Integer minPlayCount;
    private final Integer maxPlayCount;
    private final boolean showStarredSongs;
    private final boolean showUnstarredSongs;
    private final String format;

    /**
     * Creates a new instance.
     *
     * @param count        Maximum number of songs to return.
     * @param genres       Only return songs of the given genre. May be
     *                     <code>null</code>.
     * @param fromYear     Only return songs released after (or in) this year. May
     *                     be <code>null</code>.
     * @param toYear       Only return songs released before (or in) this year. May
     *                     be <code>null</code>.
     * @param musicFolders Only return songs from these music folder. May NOT be
     *                     <code>null</code>.
     */
    public ShuffleSelectionParam(int count, List<String> genres, Integer fromYear, Integer toYear,
            List<MusicFolder> musicFolders) {
        this(count, genres, fromYear, toYear, musicFolders, null, null, null, null, null, null,
                true, true, null);
    }

    /**
     * Creates a new instance.
     *
     * @param count              Maximum number of songs to return.
     * @param genres             Only return songs of the given genre. May be
     *                           <code>null</code>.
     * @param fromYear           Only return songs released after (or in) this year.
     *                           May be <code>null</code>.
     * @param toYear             Only return songs released before (or in) this
     *                           year. May be <code>null</code>.
     * @param musicFolders       Only return songs from these music folder. May NOT
     *                           be <code>null</code>.
     * @param minLastPlayedDate  Only return songs last played after this date. May
     *                           be <code>null</code>.
     * @param maxLastPlayedDate  Only return songs last played before this date. May
     *                           be <code>null</code>.
     * @param minAlbumRating     Only return songs rated more or equalt to this
     *                           value. May be <code>null</code>.
     * @param maxAlbumRating     Only return songs rated less or equal to this
     *                           value. May be <code>null</code>.
     * @param minPlayCount       Only return songs whose play count is more or equal
     *                           to this value. May be <code>null</code>.
     * @param maxPlayCount       Only return songs whose play count is less or equal
     *                           to this value. May be <code>null</code>.
     * @param showStarredSongs   Show starred songs. May NOT be <code>null</code>.
     * @param showUnstarredSongs Show unstarred songs. May NOT be <code>null</code>.
     * @param format             Only return songs whose file format is equal to
     *                           this value. May be <code>null</code>.
     */
    public ShuffleSelectionParam(int count, List<String> genres, Integer fromYear, Integer toYear,
            List<MusicFolder> musicFolders, Instant minLastPlayedDate, Instant maxLastPlayedDate,
            Integer minAlbumRating, Integer maxAlbumRating, Integer minPlayCount,
            Integer maxPlayCount, boolean showStarredSongs, boolean showUnstarredSongs,
            String format) {

        this.count = count;
        this.genres = genres;
        this.fromYear = fromYear;
        this.toYear = toYear;
        this.musicFolders = musicFolders;
        this.minLastPlayedDate = minLastPlayedDate;
        this.maxLastPlayedDate = maxLastPlayedDate;
        this.minAlbumRating = minAlbumRating;
        this.maxAlbumRating = maxAlbumRating;
        this.minPlayCount = minPlayCount;
        this.maxPlayCount = maxPlayCount;
        this.showStarredSongs = showStarredSongs;
        this.showUnstarredSongs = showUnstarredSongs;
        this.format = format;
    }

    public int getCount() {
        return count;
    }

    public List<String> getGenres() {
        return genres;
    }

    public Integer getFromYear() {
        return fromYear;
    }

    public Integer getToYear() {
        return toYear;
    }

    public List<MusicFolder> getMusicFolders() {
        return musicFolders;
    }

    public Instant getMinLastPlayedDate() {
        return minLastPlayedDate;
    }

    public Instant getMaxLastPlayedDate() {
        return maxLastPlayedDate;
    }

    public Integer getMinAlbumRating() {
        return minAlbumRating;
    }

    public Integer getMaxAlbumRating() {
        return maxAlbumRating;
    }

    public boolean isShowStarredSongs() {
        return showStarredSongs;
    }

    public boolean isShowUnstarredSongs() {
        return showUnstarredSongs;
    }

    public String getFormat() {
        return format;
    }

    public Integer getMinPlayCount() {
        return minPlayCount;
    }

    public Integer getMaxPlayCount() {
        return maxPlayCount;
    }
}

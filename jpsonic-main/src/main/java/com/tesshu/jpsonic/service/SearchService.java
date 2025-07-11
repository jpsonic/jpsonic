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

package com.tesshu.jpsonic.service;

import java.util.List;

import com.tesshu.jpsonic.domain.Album;
import com.tesshu.jpsonic.domain.Artist;
import com.tesshu.jpsonic.domain.Genre;
import com.tesshu.jpsonic.domain.GenreMasterCriteria;
import com.tesshu.jpsonic.domain.MediaFile;
import com.tesshu.jpsonic.domain.MediaFile.MediaType;
import com.tesshu.jpsonic.domain.MusicFolder;
import com.tesshu.jpsonic.domain.ParamSearchResult;
import com.tesshu.jpsonic.domain.RandomSearchCriteria;
import com.tesshu.jpsonic.domain.SearchResult;
import com.tesshu.jpsonic.service.search.SearchCriteria;
import com.tesshu.jpsonic.service.search.UPnPSearchCriteria;

/**
 * Performs Lucene-based searching.
 *
 * @author Sindre Mehus
 *
 * @see MediaScannerService
 */
public interface SearchService {

    /**
     * Perform a multi-field search corresponding to SearchCriteria.
     * <p>
     * It is the most popular search inherited from legacy servers and has been used
     * from the Web and REST since ancient times.
     *
     * @since 107.3.0
     *
     * @return search result
     */
    SearchResult search(SearchCriteria criteria);

    /**
     * Perform a search that comply with the UPnP Service Template with
     * UPnPCriteria. Criteria is built using a dedicated Director class
     * (UPnPCriteriaDirector).
     *
     * @param <T> see UPnPCriteria#getAssignableClass
     *
     * @since 106.1.0
     *
     * @return search result
     */
    <T> ParamSearchResult<T> search(UPnPSearchCriteria criteria);

    /**
     * Returns a number of random songs.
     *
     * @param criteria Search criteria.
     *
     * @return List of random songs.
     */
    List<MediaFile> getRandomSongs(RandomSearchCriteria criteria);

    /**
     * Returns random songs. The song returned by this list is limited to
     * MesiaType=SONG. In other words, PODCAST, AUDIOBOOK and VIDEO are not
     * included.
     * <p>
     * This method uses a very short-lived cache. This cache is not for long-running
     * transactions like paging, but for short-term repetitive calls.
     *
     * @version 114.2.0
     *
     * @since 106.1.0
     *
     * @param count        Number of albums to return.
     * @param offset       offset
     * @param casheMax     Data duplication due to paging is avoided when the cache
     *                     is an iterative call within the valid period.
     * @param musicFolders Only return albums from these folders.
     * @param genres       Genres
     *
     * @return List of random albums.
     */
    List<MediaFile> getRandomSongs(int count, int offset, int casheMax,
            List<MusicFolder> musicFolders, String... genres);

    /**
     * Returns random songs. The song returned by this list is limited to
     * MesiaType=SONG. In other words, PODCAST, AUDIOBOOK and VIDEO are not
     * included.
     * <p>
     * This method uses a very short-lived cache. This cache is not for long-running
     * transactions like paging, but for short-term repetitive calls.
     *
     * @since 107.0.0
     *
     * @param count        Number of albums to return.
     * @param offset       offset
     * @param casheMax     Data duplication due to paging is avoided when the cache
     *                     is an iterative call within the valid period.
     * @param musicFolders Only return albums from these folders.
     *
     * @return List of random albums.
     */
    List<MediaFile> getRandomSongsByArtist(Artist artist, int count, int offset, int casheMax,
            List<MusicFolder> musicFolders);

    /**
     * Returns a number of random albums.
     *
     * @param count        Number of albums to return.
     * @param musicFolders Only return albums from these folders.
     *
     * @return List of random albums.
     */
    List<MediaFile> getRandomAlbums(int count, List<MusicFolder> musicFolders);

    /**
     * Returns random albums, using ID3 tag.
     *
     * @param count        Number of albums to return.
     * @param musicFolders Only return albums from these folders.
     *
     * @return List of random albums.
     */
    List<Album> getRandomAlbumsId3(int count, List<MusicFolder> musicFolders);

    /**
     * Returns random albums, using ID3 tag.
     * <p>
     * Unlike getRandom Album Id3, this method uses a very short-lived. This cache
     * is not for long-running transactions like paging, but for short-term
     * repetitive calls.
     *
     * @since 106.1.0
     *
     * @param count        Number of albums to return.
     * @param offset       offset
     * @param casheMax     Data duplication due to paging is avoided when the cache
     *                     is an iterative call within the valid period.
     * @param musicFolders Only return albums from these folders.
     *
     * @return List of random albums.
     */
    List<Album> getRandomAlbumsId3(int count, int offset, int casheMax,
            List<MusicFolder> musicFolders);

    /**
     * Returns all genres in the music collection. The method for simulating the
     * genre specification of legacy servers. Use
     * {@link #getGenres(GenreMasterCriteria, long, long)}, if you don't need
     * backward compatibility.
     *
     * @since 101.2.0
     *
     * @param sortByAlbum Whether to sort by album count, rather than song count.
     *
     * @return Sorted list of genres.
     */
    List<Genre> getGenres(boolean sortByAlbum);

    /**
     * Returns all genres in the music collection. The method for simulating the
     * genre specification of legacy servers. Use
     * {@link #getGenres(GenreMasterCriteria, long, long)}, if you don't need
     * backward compatibility.
     *
     * @since 105.3.0
     *
     * @param sortByAlbum Whether to sort by album count, rather than song count.
     * @param offset      offset
     * @param maxResults  maxResults
     *
     * @return Sorted list of genres.
     */
    List<Genre> getGenres(boolean sortByAlbum, long offset, long maxResults);

    /**
     * Returns all genres in the music collection.
     *
     * @since 114.2.0
     */
    List<Genre> getGenres(GenreMasterCriteria criteria, long offset, long maxResults);

    /**
     * Returns count of Genres. The method for simulating the genre specification of
     * legacy servers. Use {@link #getGenresCount(GenreMasterCriteria)}, if you
     * don't need backward compatibility.
     *
     * @since 105.3.0
     *
     * @param sortByAlbum Whether to sort by album count, rather than song count.
     *
     * @return Count of Genres
     */
    int getGenresCount(boolean sortByAlbum);

    /**
     * Returns the number of genres in the specified Folders and Scope.
     *
     * @since 114.2.0
     */
    int getGenresCount(GenreMasterCriteria criteria);

    /**
     * Returns albums in a genre.
     *
     * @since 101.2.0
     *
     * @param offset       Number of albums to skip.
     * @param count        Maximum number of albums to return.
     * @param genres       A genre name or multiple genres represented by delimiter
     *                     strings defined in the specification.
     * @param musicFolders Only return albums in these folders.
     *
     * @return Albums in the genre.
     */
    List<MediaFile> getAlbumsByGenres(String genres, int offset, int count,
            List<MusicFolder> musicFolders);

    /**
     * Returns albums in a genre.
     *
     * @since 101.2.0
     *
     * @param offset       Number of albums to skip.
     * @param count        Maximum number of albums to return.
     * @param genres       A genre name or multiple genres represented by delimiter
     *                     strings defined in the specification.
     * @param musicFolders Only return albums from these folders.
     *
     * @return Albums in the genre.
     */
    List<Album> getAlbumId3sByGenres(String genres, int offset, int count,
            List<MusicFolder> musicFolders);

    /**
     * Returns songs in a genre.
     *
     * @version 114.2.0
     *
     * @since 101.2.0
     *
     * @param offset       Number of songs to skip.
     * @param count        Maximum number of songs to return.
     * @param genres       A genre name or multiple genres represented by delimiter
     *                     strings defined in the specification.
     * @param musicFolders Only return songs from these folders.
     *
     * @return songs in the genre.
     */
    List<MediaFile> getSongsByGenres(String genres, int offset, int count,
            List<MusicFolder> musicFolders, MediaType... types);

    /**
     * Returns only the children size of an Album that match the specified criteria.
     *
     * @since 114.2.0
     */
    int getChildSizeOf(String genre, Album album, List<MusicFolder> folders, MediaType... types);

    /**
     * Returns only the children of an Album that match the specified criteria. The
     * size of the expected result is assumed to be finite, so offset and count are
     * unsupported.
     *
     * @since 114.2.0
     */
    List<MediaFile> getChildrenOf(String genre, Album album, int offset, int count,
            List<MusicFolder> folders, MediaType... types);
}

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
 * (C) 2023 tesshucom
 */

package com.tesshu.jpsonic.persistence.dialect;

import java.util.List;
import java.util.function.BiFunction;

import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.result.ArtistSortCandidate;
import com.tesshu.jpsonic.persistence.result.DuplicateSort;
import com.tesshu.jpsonic.persistence.result.SortCandidate;

public interface DialectMediaFileDao {

    /**
     * Used for Sort tag merge.
     */
    List<DuplicateSort> getDuplicateSortPersons(List<MusicFolder> folders);

    /**
     * Used for Sort tag merge.
     */
    List<ArtistSortCandidate> getSortCandidatePersons(List<DuplicateSort> dups);

    /**
     * Used for Sort tag merge.
     */
    List<SortCandidate> getDuplicateSortAlbums(List<MusicFolder> folders);

    /**
     * Used for Sort tag copy.
     */
    List<ArtistSortCandidate> getCopyableSortPersons(List<MusicFolder> folders);

    /**
     * Used for Sort tag copy.
     */
    List<SortCandidate> getCopyableSortAlbums(List<MusicFolder> folders);

    /**
     * Used for Sort tag compensate.
     */
    List<ArtistSortCandidate> getNoSortPersons(List<MusicFolder> folders);

    /**
     * Used for Sort tag compensate.
     */
    List<SortCandidate> getNoSortAlbums(List<MusicFolder> folders);

    List<MediaFile> getChangedId3Albums(int count, List<MusicFolder> musicFolders,
            boolean withPodcast);

    List<MediaFile> getChangedId3Artists(int count, List<MusicFolder> folders, boolean withPodcast);

    List<MediaFile> getUnregisteredId3Albums(int count, List<MusicFolder> musicFolders,
            boolean withPodcast);

    List<MediaFile> getUnregisteredId3Artists(int count, List<MusicFolder> folders,
            boolean withPodcast);

    List<MediaFile> getRandomSongsForAlbumArtist(int limit, String albumArtist,
            List<MusicFolder> musicFolders,
            BiFunction<Integer, Integer, List<Integer>> randomCallback);

    List<com.tesshu.jpsonic.domain.model.MediaFile> getDomainRandomSongsForAlbumArtist(int limit,
            String albumArtist, List<com.tesshu.jpsonic.domain.model.MusicFolder> musicFolders,
            BiFunction<Integer, Integer, List<Integer>> randomCallback);

    List<MediaFile> getSongsByGenre(List<String> genres, long offset, long count,
            List<MusicFolder> musicFolders, List<MediaType> types);
}

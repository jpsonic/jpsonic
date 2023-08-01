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
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.service.scanner;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.dao.MediaFileDao;
import com.tesshu.jpsonic.domain.JapaneseReadingUtils;
import com.tesshu.jpsonic.domain.MusicFolder;
import com.tesshu.jpsonic.domain.SortCandidate;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

/**
 * The class to complement sorting and reading. It does not exist in conventional Sonic servers. This class has a large
 * impact on sorting and searching accuracy.
 */
@Service
@DependsOn({ "musicFolderService", "mediaFileDao", "artistDao", "albumDao", "japaneseReadingUtils", "indexManager",
        "jpsonicComparators" })
public class SortProcedureService {

    private final MediaFileDao mediaFileDao;
    private final JapaneseReadingUtils utils;

    public SortProcedureService(MediaFileDao mediaFileDao, JapaneseReadingUtils utils) {
        super();
        this.mediaFileDao = mediaFileDao;
        this.utils = utils;
    }

    void clearMemoryCache() {
        utils.clear();
    }

    List<Integer> compensateSortOfAlbum(List<MusicFolder> folders) {
        List<SortCandidate> candidatesWithId = mediaFileDao.getSortForAlbumWithoutSorts(folders);
        candidatesWithId.forEach(utils::analyze);
        return updateSortOfAlbumsWithId(candidatesWithId);
    }

    List<Integer> compensateSortOfArtist(List<MusicFolder> folders) {
        List<SortCandidate> candidatesWithId = mediaFileDao.getSortForPersonWithoutSorts(folders);
        candidatesWithId.forEach(utils::analyze);
        return updateSortOfArtistWithId(candidatesWithId);
    }

    List<Integer> copySortOfAlbum(List<MusicFolder> folders) {
        List<SortCandidate> candidatesWithId = mediaFileDao.getCopyableSortForAlbums(folders);
        candidatesWithId.forEach(utils::analyze);
        return updateSortOfAlbumsWithId(candidatesWithId);
    }

    List<Integer> copySortOfArtist(List<MusicFolder> folders) {
        List<SortCandidate> candidatesWithId = mediaFileDao.getCopyableSortForPersons(folders);
        candidatesWithId.forEach(utils::analyze);
        return updateSortOfArtistWithId(candidatesWithId);
    }

    List<Integer> mergeSortOfAlbum(List<MusicFolder> folders) {
        List<SortCandidate> candidatesWithId = mediaFileDao.guessAlbumSorts(folders);
        candidatesWithId.forEach(utils::analyze);
        return updateSortOfAlbumsWithId(candidatesWithId);
    }

    List<Integer> mergeSortOfArtist(List<MusicFolder> folders) {
        List<SortCandidate> candidatesWithoutId = mediaFileDao.guessPersonsSorts(folders);
        if (candidatesWithoutId.isEmpty()) {
            return Collections.emptyList();
        }
        List<SortCandidate> candidatesWithId = mediaFileDao.getSortOfArtistToBeFixedWithId(candidatesWithoutId);
        candidatesWithId.forEach(utils::analyze);
        return updateSortOfArtistWithId(candidatesWithId);
    }

    private List<Integer> updateSortOfAlbumsWithId(@NonNull List<SortCandidate> candidatesWithId) {
        if (candidatesWithId.isEmpty()) {
            return Collections.emptyList();
        }
        candidatesWithId.forEach(c -> mediaFileDao.updateAlbumSortWithId(c));
        return candidatesWithId.stream().map(SortCandidate::getId).collect(Collectors.toList());
    }

    private List<Integer> updateSortOfArtistWithId(@NonNull List<SortCandidate> candidatesWithId) {
        if (candidatesWithId.isEmpty()) {
            return Collections.emptyList();
        }
        candidatesWithId.forEach(c -> mediaFileDao.updateArtistSortWithId(c));
        return candidatesWithId.stream().map(SortCandidate::getId).collect(Collectors.toList());
    }
}

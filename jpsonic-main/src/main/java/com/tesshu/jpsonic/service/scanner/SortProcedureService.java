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

import com.tesshu.jpsonic.dao.AlbumDao;
import com.tesshu.jpsonic.dao.ArtistDao;
import com.tesshu.jpsonic.dao.MediaFileDao;
import com.tesshu.jpsonic.domain.Album;
import com.tesshu.jpsonic.domain.Artist;
import com.tesshu.jpsonic.domain.JapaneseReadingUtils;
import com.tesshu.jpsonic.domain.JpsonicComparators;
import com.tesshu.jpsonic.domain.MediaFile;
import com.tesshu.jpsonic.domain.MusicFolder;
import com.tesshu.jpsonic.domain.SortCandidate;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.MusicFolderService;
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

    private final MusicFolderService musicFolderService;
    private final MediaFileService mediaFileService;
    private final WritableMediaFileService writableMediaFileService;
    private final MediaFileDao mediaFileDao;
    private final ArtistDao artistDao;
    private final AlbumDao albumDao;
    private final JapaneseReadingUtils utils;
    private final JpsonicComparators comparators;

    public SortProcedureService(MusicFolderService musicFolderService, MediaFileService mediaFileService,
            WritableMediaFileService writableMediaFileService, MediaFileDao mediaFileDao, ArtistDao artistDao,
            AlbumDao albumDao, JapaneseReadingUtils utils, JpsonicComparators jpsonicComparator) {
        super();
        this.musicFolderService = musicFolderService;
        this.mediaFileService = mediaFileService;
        this.writableMediaFileService = writableMediaFileService;
        this.mediaFileDao = mediaFileDao;
        this.artistDao = artistDao;
        this.albumDao = albumDao;
        this.utils = utils;
        this.comparators = jpsonicComparator;
    }

    void clearMemoryCache() {
        utils.clear();
    }

    List<Integer> compensateSortOfAlbum(List<MusicFolder> folders) {
        List<SortCandidate> candidates = mediaFileDao.getSortForAlbumWithoutSorts(folders);
        candidates.forEach(utils::analyze);
        return updateSortOfAlbums(candidates);
    }

    List<Integer> compensateSortOfArtist(List<MusicFolder> folders) {
        List<SortCandidate> candidates = mediaFileDao.getSortForPersonWithoutSorts(folders);
        candidates.forEach(utils::analyze);
        return updateSortOfArtist(candidates);
    }

    List<Integer> copySortOfAlbum(List<MusicFolder> folders) {
        List<SortCandidate> candidates = mediaFileDao.getCopyableSortForAlbums(folders);
        candidates.forEach(utils::analyze);
        return updateSortOfAlbums(candidates);
    }

    List<Integer> copySortOfArtist(List<MusicFolder> folders) {
        List<SortCandidate> candidates = mediaFileDao.getCopyableSortForPersons(folders);
        candidates.forEach(utils::analyze);
        return updateSortOfArtist(candidates);
    }

    List<Integer> mergeSortOfAlbum(List<MusicFolder> folders) {
        List<SortCandidate> candidates = mediaFileDao.guessAlbumSorts(folders);
        candidates.forEach(utils::analyze);
        return updateSortOfAlbums(candidates);
    }

    List<Integer> mergeSortOfArtist(List<MusicFolder> folders) {
        List<SortCandidate> candidates = mediaFileDao.guessPersonsSorts(folders);
        candidates.forEach(utils::analyze);
        return updateSortOfArtist(candidates);
    }

    int updateOrderOfAlbumID3() {
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();
        List<Album> albums = albumDao.getAlphabeticalAlbums(0, Integer.MAX_VALUE, false, false, folders);
        albums.sort(comparators.albumOrderByAlpha());
        int i = 0;
        for (Album album : albums) {
            albumDao.updateOrder(album.getArtist(), album.getName(), i++);
        }
        return i;
    }

    int updateOrderOfArtistID3() {
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();
        List<Artist> artists = artistDao.getAlphabetialArtists(0, Integer.MAX_VALUE, folders);
        artists.sort(comparators.artistOrderByAlpha());
        int i = 0;
        for (Artist artist : artists) {
            artistDao.updateOrder(artist.getName(), i++);
        }
        return i;
    }

    int updateOrderOfArtist() {
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();
        List<MediaFile> artists = mediaFileDao.getArtistAll(folders);
        artists.sort(comparators.mediaFileOrderByAlpha());
        int i = 0;
        for (MediaFile artist : artists) {
            artist.setOrder(i++);
            writableMediaFileService.updateOrder(artist);
        }
        return i;
    }

    int updateOrderOfAlbum() {
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();
        List<MediaFile> albums = mediaFileService.getAlphabeticalAlbums(0, Integer.MAX_VALUE, true, folders);
        albums.sort(comparators.mediaFileOrderByAlpha());
        int i = 0;
        for (MediaFile album : albums) {
            album.setOrder(i++);
            writableMediaFileService.updateOrder(album);
        }
        return i;
    }

    void updateOrderOfSongs(MediaFile parent) {
        List<MediaFile> songs = mediaFileDao.getChildrenOf(parent.getPathString()).stream()
                .filter(child -> mediaFileService.isAudioFile(child.getFormat())
                        || mediaFileService.isVideoFile(child.getFormat()))
                .collect(Collectors.toList());
        Collections.sort(songs, comparators.songsDefault());
        for (int i = 0; i < songs.size(); i++) {
            mediaFileDao.updateOrder(songs.get(i).getPathString(), i);
        }
    }

    private List<Integer> updateSortOfAlbums(@NonNull List<SortCandidate> candidates) {
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> toBeFixed = mediaFileDao.getSortOfAlbumToBeFixed(candidates);
        candidates.forEach(c -> mediaFileDao.updateAlbumSort(c));
        return toBeFixed;
    }

    private List<Integer> updateSortOfArtist(@NonNull List<SortCandidate> candidates) {
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> toBeFixed = mediaFileDao.getSortOfArtistToBeFixed(candidates);
        candidates.forEach(c -> mediaFileDao.updateArtistSort(c));
        return toBeFixed;
    }
}
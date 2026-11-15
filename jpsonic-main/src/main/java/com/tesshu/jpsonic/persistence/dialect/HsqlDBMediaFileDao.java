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

import com.tesshu.jpsonic.infrastructure.db.DatabaseConfiguration.ProfileNameConstants;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import com.tesshu.jpsonic.persistence.result.ArtistSortCandidate;
import com.tesshu.jpsonic.persistence.result.DuplicateSort;
import com.tesshu.jpsonic.persistence.result.SortCandidate;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({ ProfileNameConstants.HOST })
public class HsqlDBMediaFileDao implements DialectMediaFileDao {

    private final AnsiMediaFileDao deligate;

    public HsqlDBMediaFileDao(TemplateWrapper templateWrapper) {
        deligate = new AnsiMediaFileDao(templateWrapper);
    }

    @Override
    public List<MediaFile> getChangedId3Artists(final int count, List<MusicFolder> folders,
            boolean withPodcast) {
        return deligate.getChangedId3Artists(count, folders, withPodcast);
    }

    @Override
    public List<MediaFile> getUnregisteredId3Artists(final int count, List<MusicFolder> folders,
            boolean withPodcast) {
        return deligate.getUnregisteredId3Artists(count, folders, withPodcast);
    }

    @Override
    public List<MediaFile> getChangedId3Albums(final int count, List<MusicFolder> musicFolders,
            boolean withPodcast) {
        return deligate.getChangedId3Albums(count, musicFolders, withPodcast);
    }

    @Override
    public List<MediaFile> getUnregisteredId3Albums(final int count, List<MusicFolder> musicFolders,
            boolean withPodcast) {
        return deligate.getUnregisteredId3Albums(count, musicFolders, withPodcast);
    }

    @Override
    public List<SortCandidate> getCopyableSortAlbums(List<MusicFolder> folders) {
        return deligate.getCopyableSortAlbums(folders);
    }

    @Override
    public List<ArtistSortCandidate> getCopyableSortPersons(List<MusicFolder> folders) {
        return deligate.getCopyableSortPersons(folders);
    }

    @Override
    public List<MediaFile> getRandomSongsForAlbumArtist(int limit, String albumArtist,
            List<MusicFolder> musicFolders,
            BiFunction<Integer, Integer, List<Integer>> randomCallback) {
        return deligate
            .getRandomSongsForAlbumArtist(limit, albumArtist, musicFolders, randomCallback);
    }

    @Override
    public List<ArtistSortCandidate> getNoSortPersons(List<MusicFolder> folders) {
        return deligate.getNoSortPersons(folders);
    }

    @Override
    public List<ArtistSortCandidate> getSortCandidatePersons(
            @NonNull List<DuplicateSort> duplicates) {
        return deligate.getSortCandidatePersons(duplicates);
    }

    @Override
    public List<SortCandidate> getNoSortAlbums(List<MusicFolder> folders) {
        return deligate.getNoSortAlbums(folders);
    }

    @Override
    public List<SortCandidate> getDuplicateSortAlbums(List<MusicFolder> folders) {
        return deligate.getDuplicateSortAlbums(folders);
    }

    @Override
    public List<DuplicateSort> getDuplicateSortPersons(List<MusicFolder> folders) {
        return deligate.getDuplicateSortPersons(folders);
    }

    @Override
    public List<MediaFile> getSongsByGenre(List<String> genres, long offset, long count,
            List<MusicFolder> musicFolders, List<MediaType> types) {
        return deligate.getSongsByGenre(genres, offset, count, musicFolders, types);
    }

    @Override
    public List<com.tesshu.jpsonic.domain.model.MediaFile> getDomainRandomSongsForAlbumArtist(
            int limit, String albumArtist,
            List<com.tesshu.jpsonic.domain.model.MusicFolder> musicFolders,
            BiFunction<Integer, Integer, List<Integer>> randomCallback) {
        return deligate
            .getDomainRandomSongsForAlbumArtist(limit, albumArtist, musicFolders, randomCallback);
    }
}

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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.tesshu.jpsonic.domain.model.Album;
import com.tesshu.jpsonic.domain.model.Artist;
import com.tesshu.jpsonic.domain.model.IndexWithCount;
import com.tesshu.jpsonic.domain.model.MediaFile;
import com.tesshu.jpsonic.domain.model.MediaFile.Type;
import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.model.MusicIndex;
import com.tesshu.jpsonic.domain.policy.RuntimeOrderPolicy;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterProvider;
import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.infrastructure.scanner.MediaSearchProvider;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Component;

@Component
class MediaFileProviderAdapter implements MediaFileProvider {

    private final SettingsFacade settingsFacade;
    private final MediaFileDao mediaFileDao;
    @Deprecated
    private final MediaSearchProvider deligate;
    private final GenreMasterProvider genreMasterProvider;

    MediaFileProviderAdapter(SettingsFacade settingsFacade, MediaFileDao mediaFileDao,
            MediaSearchProvider mediaSearchProvider, GenreMasterProvider genreMasterProvider) {
        this.settingsFacade = settingsFacade;
        this.mediaFileDao = mediaFileDao;
        this.deligate = mediaSearchProvider;
        this.genreMasterProvider = genreMasterProvider;
    }

    @Override
    public int countAlbums(List<MusicFolder> folders) {
        return mediaFileDao.countMediaFile(folders, MediaFile.Type.ALBUM);
    }

    @Override
    public int countChildren(List<MusicFolder> folders, MediaFile.Type... excludes) {
        return mediaFileDao.countChildren(folders, excludes);
    }

    @Override
    public int countChildren(MediaFile parent, MediaFile.Type... excludes) {
        return mediaFileDao.countChildren(parent, excludes);
    }

    @Override
    public int countSongs(List<MusicFolder> folders) {
        return mediaFileDao.countMediaFile(folders, MediaFile.Type.MUSIC);
    }

    @Override
    public int countVideos(List<MusicFolder> folders) {
        return mediaFileDao.countMediaFile(folders, MediaFile.Type.VIDEO);
    }

    @Override
    public boolean existsAccessibleMediaFile(String username, MediaFile mediaFile) {
        return mediaFileDao.existsAccessibleMediaFile(username, mediaFile);
    }

    @Override
    public List<MediaFile> findAlbums(List<MusicFolder> folders,
            RuntimeOrderPolicy.AlbumSortOrder order, long offset, long count) {
        return mediaFileDao.findAlbums(folders, order, offset, count);
    }

    @Override
    public List<MediaFile> findAlbumsByGenres(List<MusicFolder> folders, String genres, long offset,
            long count) {
        if (isEmpty(genres)) {
            return Collections.emptyList();
        }
        List<String> preAnalyzedGenres = genreMasterProvider
            .findPreAnalyzedGenres(Arrays.asList(genres), true);
        return mediaFileDao.findAlbumsByGenres(folders, preAnalyzedGenres, offset, count);
    }

    @Override
    public List<MediaFile> findChildren(List<MusicFolder> folders, Album album, long offset,
            long count, MediaFile.Type... excludes) {
        return mediaFileDao.findChildren(folders, album, offset, count, excludes);
    }

    @Override
    public List<MediaFile> findChildren(List<MusicFolder> folders, long offset, long count,
            MediaFile.Type... excludes) {
        return mediaFileDao.findChildren(folders, offset, count, excludes);
    }

    @Override
    public List<MediaFile> findChildren(List<MusicFolder> folders, MusicIndex musicIndex,
            long offset, long count, MediaFile.Type... excludes) {
        return mediaFileDao.findChildren(folders, musicIndex, offset, count, excludes);
    }

    @Override
    public List<MediaFile> findChildren(List<MusicFolder> folders, String genres, Album album,
            long offset, long count, Type... types) {
        return mediaFileDao
            .findChildren(folders,
                    genreMasterProvider.findPreAnalyzedGenres(Arrays.asList(genres), true), album,
                    offset, count, types);
    }

    @Override
    public List<MediaFile> findChildren(MediaFile parent, RuntimeOrderPolicy.ChildOrder order,
            long offset, long count, MediaFile.Type... excludes) {
        return mediaFileDao.findChildren(parent, order, offset, count, excludes);
    }

    @Override
    public List<MediaFile> findIndexedDirectories(List<MusicFolder> folders) {
        return mediaFileDao
            .findIndexedDirectories(folders,
                    settingsFacade.getCachedList(SKeys.general.extension.shortcuts));
    }

    @Override
    public List<IndexWithCount> findIndexWithCounts(List<MusicFolder> folders) {
        List<String> shortcutPaths = findShortcuts(folders)
            .stream()
            .map(MediaFile::toPath)
            .map(Path::toString)
            .toList();
        return mediaFileDao.findIndexWithCounts(folders, shortcutPaths);
    }

    @Override
    public Optional<MediaFile> findMediaFile(Path path) {
        return Optional.ofNullable(mediaFileDao.getDomainMediaFile(path));
    }

    @Override
    public List<MediaFile> findNewestAlbums(List<MusicFolder> folders, long offset, long count) {
        return mediaFileDao.findNewestAlbums(folders, offset, count);
    }

    @Override
    public List<MediaFile> findRandomSongs(List<MusicFolder> musicFolders, long offset, long count,
            int cacheMax, String... genres) {
        return deligate.getRandomSongs(musicFolders, offset, count, cacheMax, genres);
    }

    @Override
    public List<MediaFile> findRandomSongsByArtist(List<MusicFolder> musicFolders, Artist artist,
            long offset, long count, int cacheMax) {
        return deligate.getRandomSongsByArtist(musicFolders, artist, offset, count, cacheMax);
    }

    @Override
    public List<MediaFile> findShortcuts(List<MusicFolder> musicFolders) {
        List<MediaFile> result = new ArrayList<>();

        // spotless:off
        final MediaFile.Type[] excludes = {
                MediaFile.Type.AUDIOBOOK,
                MediaFile.Type.PODCAST,
                MediaFile.Type.VIDEO // Not yet supported. 
                };
        // spotless:on

        settingsFacade.getCachedList(SKeys.general.extension.shortcuts).forEach(shortcut -> {
            musicFolders.forEach(musicFolder -> {
                findMediaFile(Path.of(musicFolder.pathString(), shortcut))
                    .ifPresent(shortcutMediaFile -> {
                        if (!result.contains(shortcutMediaFile)
                                && countChildren(shortcutMediaFile, excludes) != 0) {
                            result.add(shortcutMediaFile);
                        }
                    });
            });
        });
        return result;
    }

    @Override
    public List<MediaFile> findSongs(List<MusicFolder> folders, long offset, long count) {
        return mediaFileDao.findMediaFile(folders, MediaFile.Type.MUSIC, offset, count);
    }

    @Override
    public List<MediaFile> findSongsByGenres(List<MusicFolder> folders, String genres, long offset,
            long count, Type... types) {
        List<String> preAnalyzedGenres = genreMasterProvider
            .findPreAnalyzedGenres(List.of(genres), true);
        return mediaFileDao.getSongsByGenres(folders, preAnalyzedGenres, offset, count, types);
    }

    @Override
    public List<MediaFile> findVideos(List<MusicFolder> folders, long offset, long count) {
        return mediaFileDao.findMediaFile(folders, MediaFile.Type.VIDEO, offset, count);
    }

    @Override
    public @NonNull MediaFile requireMediaFile(int id) {
        MediaFile mediaFile = mediaFileDao.getDomainMediaFile(id);
        if (mediaFile == null) {
            throw new IllegalArgumentException("The specified MediaFile cannot be found.");
        }
        return mediaFile;
    }

    @Override
    public @NonNull MediaFile requireMediaFile(MusicFolder musicFolder) {
        return requireMediaFile(musicFolder.toPath());
    }

    @Override
    public @NonNull MediaFile requireMediaFile(Path path) {
        MediaFile mediaFile = mediaFileDao.getDomainMusicFolder(path);
        if (mediaFile == null) {
            throw new IllegalArgumentException("The specified MediaFile cannot be found.");
        }
        return mediaFile;
    }
}

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
import java.util.Map.Entry;
import java.util.stream.Stream;

import com.tesshu.jpsonic.domain.model.MediaFile;
import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.model.Playlist;
import com.tesshu.jpsonic.domain.provider.resource.PlaylistProvider;
import com.tesshu.jpsonic.infrastructure.comparator.ComparatorsFacade;
import com.tesshu.jpsonic.infrastructure.language.MetadataReadingProcessor;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.api.repository.PlaylistDao;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Component;

@Component
class PlaylistProviderAdapter implements PlaylistProvider {

    private static final MediaFile.Type[] EXCLUDED_TYPES = Stream
        .of(MediaFile.Type.PODCAST, MediaFile.Type.VIDEO, MediaFile.Type.DIRECTORY)
        .toArray(size -> new MediaFile.Type[size]);

    private final PlaylistDao playlistDao;
    private final MediaFileDao mediaFileDao;
    private final ComparatorsFacade comparatorsFacade;
    private final MetadataReadingProcessor readingProcessor;

    PlaylistProviderAdapter(PlaylistDao playlistDao, MediaFileDao mediaFileDao,
            ComparatorsFacade comparatorsFacade, MetadataReadingProcessor readingProcessor) {
        this.playlistDao = playlistDao;
        this.mediaFileDao = mediaFileDao;
        this.comparatorsFacade = comparatorsFacade;
        this.readingProcessor = readingProcessor;
    }

    @Override
    public int countPlaylists() {
        return playlistDao.countPlaylists();
    }

    @Override
    public int countPublishedPlaylists() {
        return playlistDao.countPublishedPlaylists();
    }

    @Override
    public List<MediaFile> findChildren(List<MusicFolder> folders, Playlist playlist, long offset,
            long count) {
        return mediaFileDao.findChildren(folders, playlist, offset, count, EXCLUDED_TYPES);
    }

    @Override
    public List<Playlist> findPlaylists(long offset, long count) {
        /*
         * Sorted in memory until the playlist has an 'order' column.
         */
        return playlistDao
            .findPlaylists(0, Integer.MAX_VALUE)
            .stream()
            .map(readingProcessor::analyzeName)
            .sorted(comparatorsFacade.nameableOrder())
            .map(Entry::getKey)
            .skip(offset)
            .limit(count)
            .toList();
    }

    @Override
    public List<Playlist> findPublishedPlaylists(long offset, long count) {
        /*
         * Sorted in memory until the playlist has an 'order' column.
         */
        return playlistDao
            .findPublishedPlaylists(0, Integer.MAX_VALUE)
            .stream()
            .map(readingProcessor::analyzeName)
            .sorted(comparatorsFacade.nameableOrder())
            .map(Entry::getKey)
            .skip(offset)
            .limit(count)
            .toList();
    }

    @Override
    public @NonNull Playlist requirePlaylist(int id) {
        Playlist playlist = playlistDao.getDomainPlaylist(id);
        if (playlist == null) {
            throw new IllegalArgumentException("The specified Playlist cannot be found.");
        }
        return playlist;
    }
}

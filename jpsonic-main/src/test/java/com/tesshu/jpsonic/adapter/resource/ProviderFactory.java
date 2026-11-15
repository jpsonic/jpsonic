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

import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.domain.provider.resource.PlaylistProvider;
import com.tesshu.jpsonic.infrastructure.comparator.ComparatorsFacade;
import com.tesshu.jpsonic.infrastructure.language.MetadataReadingProcessor;
import com.tesshu.jpsonic.infrastructure.scanner.MusicFolderServiceImpl;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.api.repository.MusicFolderDao;
import com.tesshu.jpsonic.persistence.api.repository.PlaylistDao;

public class ProviderFactory {

    private ProviderFactory() {
    }

    public static MusicFolderProvider createMusicFolderProvider(MusicFolderDao musicFolderDao,
            MusicFolderServiceImpl musicFolderService) {
        return new MusicFolderProviderAdapter(musicFolderDao, musicFolderService);
    }

    public static PlaylistProvider createPlaylistProviderAdapter(PlaylistDao playlistDao,
            MediaFileDao mediaFileDao, ComparatorsFacade comparatorsFacade,
            MetadataReadingProcessor readingProcessor) {
        return new PlaylistProviderAdapter(playlistDao, mediaFileDao, comparatorsFacade,
                readingProcessor);
    }
}

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

import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.infrastructure.scanner.MusicFolderServiceImpl;
import com.tesshu.jpsonic.persistence.api.repository.MusicFolderDao;
import org.springframework.stereotype.Component;

@Component
class MusicFolderProviderAdapter implements MusicFolderProvider {

    private final MusicFolderDao musicFolderDao;
    private final MusicFolderServiceImpl musicFolderService;

    MusicFolderProviderAdapter(MusicFolderDao musicFolderDao,
            MusicFolderServiceImpl musicFolderService) {
        this.musicFolderDao = musicFolderDao;
        this.musicFolderService = musicFolderService;
    }

    @Override
    public List<MusicFolder> getGuestFolders() {
        return musicFolderService.getDomainGuestFolders();
    }

    @Override
    public MusicFolder requireMusicFolder(int id) {
        MusicFolder musicFolder = musicFolderDao.getDomainMusicFolders(id);
        if (musicFolder == null) {
            throw new IllegalArgumentException("The specified MusicFolder cannot be found.");
        }
        return musicFolder;
    }
}

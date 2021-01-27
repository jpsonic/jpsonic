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

package org.airsonic.player.service.playlist;

import java.util.List;

import chameleon.content.Content;
import chameleon.playlist.Media;
import chameleon.playlist.Playlist;
import chameleon.playlist.SpecificPlaylist;
import chameleon.playlist.SpecificPlaylistProvider;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Component
public class DefaultPlaylistExportHandler implements PlaylistExportHandler {

    @Autowired
    MediaFileDao mediaFileDao;

    @Override
    public boolean canHandle(Class<? extends SpecificPlaylistProvider> providerClass) {
        return true;
    }

    @Override
    public SpecificPlaylist handle(int id, SpecificPlaylistProvider provider) throws Exception {
        Playlist playlist = createChameleonGenericPlaylistFromDBId(id);
        return provider.toSpecificPlaylist(playlist);
    }

    Playlist createChameleonGenericPlaylistFromDBId(int id) {
        Playlist newPlaylist = new Playlist();
        List<MediaFile> files = mediaFileDao.getFilesInPlaylist(id);
        files.forEach(file -> {
            Media component = new Media();
            Content content = new Content(file.getPath());
            component.setSource(content);
            newPlaylist.getRootSequence().addComponent(component);
        });
        return newPlaylist;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

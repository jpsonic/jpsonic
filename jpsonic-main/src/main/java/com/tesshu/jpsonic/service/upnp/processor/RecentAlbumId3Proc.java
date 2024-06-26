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

package com.tesshu.jpsonic.service.upnp.processor;

import java.util.List;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.dao.AlbumDao;
import com.tesshu.jpsonic.domain.Album;
import com.tesshu.jpsonic.service.MediaFileService;
import org.jupnp.support.model.BrowseResult;
import org.jupnp.support.model.DIDLContent;
import org.springframework.stereotype.Service;

@Service
public class RecentAlbumId3Proc extends AlbumProc implements CountLimitProc {

    private static final int RECENT_COUNT = 50;

    private final UpnpProcessorUtil util;
    private final AlbumDao albumDao;

    public RecentAlbumId3Proc(UpnpProcessorUtil util, UpnpDIDLFactory factory, MediaFileService mediaFileService,
            AlbumDao albumDao) {
        super(util, factory, mediaFileService, albumDao);
        this.util = util;
        this.albumDao = albumDao;
    }

    @Override
    public ProcId getProcId() {
        return ProcId.RECENT_ID3;
    }

    @Override
    public BrowseResult browseRoot(String filter, long firstResult, long maxResults) throws ExecutionException {
        DIDLContent parent = new DIDLContent();
        int offset = (int) firstResult;
        int directChildrenCount = getDirectChildrenCount();
        int count = toCount(firstResult, maxResults, directChildrenCount);
        getDirectChildren(offset, count).forEach(a -> addDirectChild(parent, a));
        return createBrowseResult(parent, (int) parent.getCount(), directChildrenCount);
    }

    @Override
    public List<Album> getDirectChildren(long offset, long max) {
        return albumDao.getNewestAlbums((int) offset, (int) max, util.getGuestFolders());
    }

    @Override
    public int getDirectChildrenCount() {
        return Math.min(albumDao.getAlbumCount(util.getGuestFolders()), RECENT_COUNT);
    }
}

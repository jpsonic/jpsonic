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

import com.tesshu.jpsonic.dao.ArtistDao;
import com.tesshu.jpsonic.domain.Artist;
import com.tesshu.jpsonic.domain.MediaFile;
import com.tesshu.jpsonic.service.SearchService;
import com.tesshu.jpsonic.service.SettingsService;
import com.tesshu.jpsonic.service.upnp.ProcId;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.container.MusicArtist;
import org.springframework.stereotype.Service;

@Service
public class RandomSongByArtistUpnpProcessor extends DirectChildrenContentProcessor<Artist, MediaFile> {

    private final UpnpProcessorUtil util;
    private final UpnpDIDLFactory factory;
    private final ArtistDao artistDao;
    private final SearchService searchService;
    private final SettingsService settingsService;

    public RandomSongByArtistUpnpProcessor(UpnpProcessorUtil util, UpnpDIDLFactory factory, ArtistDao artistDao,
            SearchService searchService, SettingsService settingsService) {
        super();
        this.util = util;
        this.factory = factory;
        this.artistDao = artistDao;
        this.searchService = searchService;
        this.settingsService = settingsService;
    }

    @Override
    public ProcId getProcId() {
        return ProcId.RANDOM_SONG_BY_ARTIST;
    }

    @Override
    public Container createContainer(Artist artist) {
        MusicArtist container = factory.toArtist(artist);
        container.setId(ProcId.RANDOM_SONG_BY_ARTIST.getValue() + ProcId.CID_SEPA + artist.getId());
        container.setParentID(ProcId.RANDOM_SONG_BY_ARTIST.getValue());
        return container;
    }

    @Override
    public int getDirectChildrenCount() {
        return artistDao.getArtistsCount(util.getGuestFolders());
    }

    @Override
    public List<Artist> getDirectChildren(long offset, long count) {
        return artistDao.getAlphabetialArtists((int) offset, (int) count, util.getGuestFolders());
    }

    @Override
    public Artist getDirectChild(String id) {
        return artistDao.getArtist(Integer.parseInt(id));
    }

    @Override
    public int getChildSizeOf(Artist artist) {
        return settingsService.getDlnaRandomMax();
    }

    @Override
    public List<MediaFile> getChildren(Artist artist, long first, long maxResults) {
        int randomMax = settingsService.getDlnaRandomMax();
        int offset = (int) first;
        int count = (offset + (int) maxResults) > randomMax ? randomMax - offset : (int) maxResults;
        return searchService.getRandomSongsByArtist(artist, count, offset, randomMax, util.getGuestFolders());
    }

    @Override
    public void addChild(DIDLContent didl, MediaFile song) {
        didl.addItem(factory.toMusicTrack(song));
    }
}

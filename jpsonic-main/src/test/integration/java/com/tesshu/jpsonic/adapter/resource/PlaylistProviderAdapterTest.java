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

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.Album;
import com.tesshu.jpsonic.domain.model.MediaFile;
import com.tesshu.jpsonic.domain.model.Playlist;
import com.tesshu.jpsonic.domain.policy.RuntimeOrderPolicy;
import com.tesshu.jpsonic.domain.provider.resource.AlbumProvider;
import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.domain.provider.resource.PlaylistProvider;
import com.tesshu.jpsonic.feature.upnp.UPnPSKeys;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.NeedsDB;
import com.tesshu.jpsonic.persistence.api.repository.PlaylistDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@NeedsDB
public class PlaylistProviderAdapterTest extends AbstractNeedsScan {

    private static final List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> MUSIC_FOLDERS = Arrays
            .asList(new com.tesshu.jpsonic.persistence.api.entity.MusicFolder(1,
                    resolveBaseMediaPath("Sort/Pagination/Artists"), "Artists", true, Instant.now(), 1,
                    false));
    @Autowired
    private MediaFileProvider mediaFileProvider;
    @Autowired
    private AlbumProvider albumProvider;
    @Autowired
    private PlaylistDao playlistDao;
    @Autowired
    private SettingsFacade settingsFacade;
    @Autowired
    private MusicFolderProvider musicFolderProvider;
    @Autowired
    private PlaylistProvider playlistProvider;

    private List<String> playlestNames;

    @Override
    public List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> getMusicFolders() {
        return MUSIC_FOLDERS;
    }
    
    @BeforeEach
    void setup() {
        settingsFacade.staging(SKeys.general.sort.albumsByYear, false);
        settingsFacade.staging(UPnPSKeys.options.guestPublish, false);
        settingsFacade.staging(UPnPSKeys.basic.baseLanUrl, "https://192.168.1.1:4040");
        settingsFacade.commitAll();
        populateDatabaseOnlyOnce();

        if (!playlistDao.getAllPlaylists().isEmpty()) {
            return;
        }

        playlestNames = List.of("10", "20", "30", "40", "50", "60", "70", "80", "90", "98", "99", "abcde",
                    "ＢＣＤＥＡ", "ĆḊÉÁḂ", "DEABC", "eabcd", "亜伊鵜絵尾", "αβγ", "いうえおあ", "ゥェォァィ", "ｴｵｱｲｳ",
                    "ｪｫｧｨｩ", "ぉぁぃぅぇ", "オアイウエ", "春夏秋冬", "貼られる", "パラレル", "馬力", "張り切る", "はるなつあきふゆ",
                    "♂くんつ");

        List<String> shuffled = new ArrayList<>(playlestNames);
        Collections.shuffle(shuffled);

        AtomicInteger c = new AtomicInteger(0);
        shuffled.stream().map((title) -> {
            Instant now = Instant.now();
            com.tesshu.jpsonic.persistence.api.entity.Playlist playlist = new com.tesshu.jpsonic.persistence.api.entity.Playlist();
            playlist.setName(title);
            playlist.setUsername("admin");
            playlist.setCreated(now);
            playlist.setChanged(now);
            playlist.setShared(c.getAndIncrement() < 10);
            return playlist;
        }).forEach(playlistDao::createPlaylist);
        assertEquals(31, playlistDao.getCountAll());

        List<Album> albums = albumProvider
            .findAlbums(musicFolderProvider.getGuestFolders(),
                    RuntimeOrderPolicy.AlbumSortOrder.DEFAULT, 0, Integer.MAX_VALUE);
        assertEquals(61, albums.size());

        List<MediaFile> files = albums
            .stream()
            .flatMap(album -> mediaFileProvider
                .findChildren(musicFolderProvider.getGuestFolders(), album, 0L, Integer.MAX_VALUE).stream()).toList();
        assertEquals(61, files.size());
        playlistDao.setDomainFilesInPlaylist(playlistProvider.findPublishedPlaylists(0, 1).get(0).id(), files);
    }

    @Test
    public void testCountPlaylists() {
        assertEquals(31, playlistProvider.countPlaylists());
    }
    @Test
    public void testCountPublishedPlaylists() {
        assertEquals(10, playlistProvider.countPublishedPlaylists());
    }

    @Test
    public void testFindChildren() {
        Playlist playlist = playlistProvider.findPublishedPlaylists(0, 1).get(0);
        List<MediaFile> files = playlistProvider.findChildren(musicFolderProvider.getGuestFolders(), playlist, 0, Integer.MAX_VALUE);
        assertEquals(61, files.size());
    }

    @Test
    public void testFindPlaylists() {
        assertEquals(31, playlistProvider.findPlaylists(0, Integer.MAX_VALUE).size());
    }

    @Test
    public void testFindPublishedPlaylists() {
        assertEquals(10, playlistProvider.findPublishedPlaylists(0, Integer.MAX_VALUE).size());
    }

    @Test
    public void testRequirePlaylist() {
        Playlist playlist = playlistProvider.findPublishedPlaylists(0, 1).get(0);
        assertNotNull(playlistProvider.requirePlaylist(playlist.id()));
    }
}

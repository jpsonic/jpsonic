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

package com.tesshu.jpsonic.persistence.api.repository;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.tesshu.jpsonic.persistence.NeedsDB;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.Artist;
import com.tesshu.jpsonic.persistence.api.entity.Avatar;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.entity.Playlist;
import com.tesshu.jpsonic.persistence.core.entity.ScanLog.ScanLogType;
import com.tesshu.jpsonic.persistence.core.repository.StaticsDao;
import com.tesshu.jpsonic.persistence.result.SortCandidate;
import com.tesshu.jpsonic.util.PlayerUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit tests for Dao methods without coverage since 112.2.1(#2334). With the
 * end of Java 11 support, Dao's SQL will be rewritten using Text Blocks. These
 * test cases are created to ensure that there are no errors during the work.
 * Note that no logic is mentioned, nor is it used in integration test cases.
 */
@SuppressWarnings("PMD.UnitTestShouldIncludeAssert")
@ActiveProfiles("test")
@SpringBootTest
@NeedsDB
class DaoUnitTest {

    private static final String USER_NAME = "admin";

    @Autowired
    private AlbumDao albumDao;
    @Autowired
    private ArtistDao artistDao;
    @Autowired
    private AvatarDao avatarDao;
    @Autowired
    private BookmarkDao bookmarkDao;
    @Autowired
    private InternetRadioDao internetRadioDao;
    @Autowired
    private MediaFileDao mediaFileDao;
    @Autowired
    private PlaylistDao playlistDao;
    @Autowired
    private PodcastDao podcastDao;
    @Autowired
    private RatingDao ratingDao;
    @Autowired
    private ShareDao shareDao;
    @Autowired
    private StaticsDao staticsDao;

    @Test
    void testAlbumDao() {
        albumDao.updateAlbum(new Album());
        List<MusicFolder> folders = List.of(new MusicFolder(null, null, false, null, false));
        albumDao.getMostFrequentlyPlayedAlbums(0, 0, folders);
        albumDao.getMostRecentlyPlayedAlbums(0, 0, folders);
        albumDao.getAlbumsByYear(0, 0, 1990, 2020, folders);
        albumDao.getAlbumsByYear(0, 0, 2020, 1990, folders);

        Instant now = PlayerUtils.now();
        MediaFile file = new MediaFile();
        file.setPathString("/1/song.mp3");
        file.setMediaType(MediaType.MUSIC);
        file.setAlbumName("album");
        file.setParentPathString("albumPath");
        file.setDurationSeconds(0);
        file.setCreated(now);
        file.setChanged(now);
        file.setLastScanned(now);
        file.setChildrenLastUpdated(now);
        file = mediaFileDao.createMediaFile(file);

        Album album = new Album();
        album.setName("albumName");
        album.setArtist("artist");
        album.setPath("albumPath");
        album.setCreated(now);
        album.setLastScanned(now);
        album = albumDao.createAlbum(album);

        albumDao.starAlbum(album.getId(), USER_NAME);
        albumDao.unstarAlbum(album.getId(), USER_NAME);
        mediaFileDao.deleteMediaFile(file.getId());
    }

    @Test
    void testArtistDao() {
        artistDao.updateArtist(new Artist());

        Instant now = PlayerUtils.now();
        Artist artist = new Artist();
        artist.setName("artistName");
        artist.setLastScanned(now);
        artist = artistDao.createArtist(artist);

        artistDao.starArtist(artist.getId(), USER_NAME);
        artistDao.getArtistStarredDate(artist.getId(), USER_NAME);
        artistDao.unstarArtist(artist.getId(), USER_NAME);
        artistDao.deleteAll();
    }

    @Test
    void testAvatarDao() {
        avatarDao.setCustomAvatar(null, USER_NAME);

        Instant now = PlayerUtils.now();
        Avatar avatar = new Avatar(0, null, now, "image/jpeg", 0, 0, new byte[0]);
        avatarDao.setCustomAvatar(avatar, USER_NAME);
    }

    @SuppressWarnings("deprecation")
    @Test
    void testBookmarkDao() {
        bookmarkDao.getBookmarks();
    }

    @Test
    void testInternetRadioDao() {
        internetRadioDao.getInternetRadioById(0);
    }

    @Test
    void testMediaFileDao() {

        mediaFileDao.getMediaFile("path");
        List<MusicFolder> folders = List.of(new MusicFolder(null, null, false, null, false));
        mediaFileDao.exists(Path.of("path"));
        mediaFileDao.updateComment("pathString", "comment");
        mediaFileDao.getMostFrequentlyPlayedAlbums(0, 0, folders);
        mediaFileDao.getMostRecentlyPlayedAlbums(0, 0, folders);
        mediaFileDao.getAlbumsByYear(0, 0, 1990, 2020, folders);
        mediaFileDao.getAlbumsByYear(0, 0, 2020, 1990, folders);
        mediaFileDao.getSongByArtistAndTitle("artist", "title", folders);
        mediaFileDao.getPlayedAlbumCount(folders);
        mediaFileDao.getStarredAlbumCount(USER_NAME, folders);

        Instant now = PlayerUtils.now();
        MediaFile file = new MediaFile();
        file.setPathString("/2/song.mp3");
        file.setMediaType(MediaType.MUSIC);
        file.setAlbumName("album");
        file.setParentPathString("albumPath");
        file.setDurationSeconds(0);
        file.setCreated(now);
        file.setChanged(now);
        file.setLastScanned(now);
        file.setChildrenLastUpdated(now);
        file = mediaFileDao.createMediaFile(file);

        mediaFileDao.starMediaFile(file.getId(), USER_NAME);
        mediaFileDao.unstarMediaFile(file.getId(), USER_NAME);
        mediaFileDao.resetLastScanned(file.getId());
        mediaFileDao.getSortCandidatePersons(List.of(new SortCandidate("name", "sort", 0)));
        mediaFileDao.deleteMediaFile(file.getId());
    }

    @Test
    void testPlaylistDao() {
        Instant now = PlayerUtils.now();
        Playlist playlist = new Playlist(0, USER_NAME, false, "playlistName", null, 0, 0, now, now,
                null);
        playlistDao.createPlaylist(playlist);
        playlistDao.addPlaylistUser(playlist.getId(), USER_NAME);
        playlistDao.deletePlaylistUser(playlist.getId(), USER_NAME);
    }

    @Test
    void testPodcastDao() {
        podcastDao.getChannel(0);
        podcastDao.getEpisodeByUrl(null);
    }

    @Test
    void testRatingDao() {
        List<MusicFolder> folders = List.of(new MusicFolder(null, null, false, null, false));
        ratingDao.getHighestRatedAlbums(0, 10, folders);
    }

    @Test
    void testShareDao() {
        shareDao.getShareByName(null);
    }

    @Test
    void testStaticsDao() {
        staticsDao.getScanLog(ScanLogType.SCAN_ALL);
        Instant now = PlayerUtils.now();
        staticsDao.deleteBefore(now);
        staticsDao.getScanEvents(now);
        staticsDao.getLastScanEventType(now);
    }
}

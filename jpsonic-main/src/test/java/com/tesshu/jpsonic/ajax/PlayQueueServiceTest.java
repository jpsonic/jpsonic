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
 * (C) 2021 tesshucom
 */

package com.tesshu.jpsonic.ajax;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.domain.system.PodcastStatus;
import com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators;
import com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators.OrderBy;
import com.tesshu.jpsonic.infrastructure.search.LegacySearch;
import com.tesshu.jpsonic.persistence.api.entity.InternetRadio;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.entity.PlayQueue;
import com.tesshu.jpsonic.persistence.api.entity.PlayQueue.Status;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode;
import com.tesshu.jpsonic.persistence.api.repository.InternetRadioDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.api.repository.PlayQueueDao;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.persistence.param.ShuffleSelectionParam;
import com.tesshu.jpsonic.persistence.result.SavedPlayQueue;
import com.tesshu.jpsonic.service.InternetRadioService;
import com.tesshu.jpsonic.service.InternetRadioService.InternetRadioSource;
import com.tesshu.jpsonic.service.JWTSecurityService;
import com.tesshu.jpsonic.service.LastFmService;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.PlaylistService;
import com.tesshu.jpsonic.service.PodcastService;
import com.tesshu.jpsonic.service.RatingService;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.UserService;
import com.tesshu.jpsonic.util.PlayerUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.bind.ServletRequestBindingException;

@SuppressWarnings("PMD.TooManyStaticImports")
class PlayQueueServiceTest {

    private Player player;
    private MediaFileService mediaFileService;
    private PlayQueueService playQueueService;
    private UserService userService;
    private PlayQueueDao playQueueDao;
    private InternetRadioDao internetRadioDao;
    private InternetRadioService internetRadioService;
    private PlaylistService playlistService;
    private LastFmService lastFmService;
    private PodcastService podcastService;
    private MediaFileDao mediaFileDao;
    private RatingService ratingService;
    private LegacySearch legacySearch;
    private AjaxHelper ajaxHelper;
    private JpsonicComparators comparators;

    @BeforeEach
    void setup() {
        final PlayerService playerService = mock(PlayerService.class);
        player = mock(Player.class);
        when(player.getId()).thenReturn(100);
        when(player.getUsername()).thenReturn(ServiceMockUtils.ADMIN_NAME);
        PlayQueue playQueue = mock(PlayQueue.class);
        when(player.getPlayQueue()).thenReturn(playQueue);
        when(playerService.getPlayerById(player.getId())).thenReturn(player);
        when(playerService.getPlayerById(0)).thenReturn(player);
        when(playerService.getPlayer(any(HttpServletRequest.class), any(HttpServletResponse.class)))
            .thenReturn(player);
        podcastService = mock(PodcastService.class);
        when(podcastService.getEpisodeStrict(anyInt(), anyBoolean()))
            .thenReturn(mock(PodcastEpisode.class));
        when(podcastService.getEpisodes(anyInt())).thenReturn(Collections.emptyList());
        mediaFileService = mock(MediaFileService.class);
        userService = mock(UserService.class);
        when(userService.getCurrentUsername(any(HttpServletRequest.class)))
            .thenReturn(ServiceMockUtils.ADMIN_NAME);
        playQueueDao = mock(PlayQueueDao.class);
        internetRadioDao = mock(InternetRadioDao.class);
        internetRadioService = mock(InternetRadioService.class);
        playlistService = mock(PlaylistService.class);
        lastFmService = mock(LastFmService.class);
        mediaFileDao = mock(MediaFileDao.class);
        ratingService = mock(RatingService.class);
        legacySearch = mock(LegacySearch.class);
        ajaxHelper = AjaxMockUtils.mock(AjaxHelper.class);
        comparators = mock(JpsonicComparators.class);
        playQueueService = new PlayQueueService(mock(MusicFolderService.class), userService,
                playerService, comparators, mediaFileService, lastFmService, legacySearch,
                ratingService, podcastService, playlistService, mediaFileDao, playQueueDao,
                internetRadioDao, mock(JWTSecurityService.class), internetRadioService, ajaxHelper);
    }

    @Test
    void testGetPlayQueue() throws ServletRequestBindingException {
        PlayQueueInfo playQueueInfo = playQueueService.getPlayQueue();
        assertFalse(playQueueInfo.isInternetRadioEnabled());
        assertFalse(playQueueInfo.isRepeatEnabled());
        assertFalse(playQueueInfo.isShuffleRadioEnabled());
        assertFalse(playQueueInfo.isStopEnabled());

        // convertMediaFileList
        MediaFile song = new MediaFile();
        song.setId(99);
        song.setFormat("mp3");
        song.setBitRate(330);
        song.setFileSize(15_862L);
        when(player.getPlayQueue().getFiles()).thenReturn(List.of(song));

        playQueueInfo = playQueueService.getPlayQueue();
        assertEquals(1, playQueueInfo.getEntries().size());
        PlayQueueInfo.Entry entry = playQueueInfo.getEntries().get(0);
        assertEquals("http://localhost/main.view?id=99", entry.getAlbumUrl());
        assertEquals("http://localhost/stream?player=100&id=99", entry.getStreamUrl());
        assertEquals("http://localhost/coverArt.view?id=99", entry.getCoverArtUrl());
        assertEquals("mp3", entry.getFormat());
        assertEquals("audio/mpeg", entry.getContentType());
        assertEquals("330 Kbps", entry.getBitRate());
        assertEquals("15 KB", entry.getFileSize());

        song.setVariableBitRate(true);
        playQueueInfo = playQueueService.getPlayQueue();
        entry = playQueueInfo.getEntries().get(0);
        assertEquals("330 Kbps vbr", entry.getBitRate());
    }

    @Test
    void testStart() throws ServletRequestBindingException {
        playQueueService.start();
        verify(player.getPlayQueue(), times(1)).setStatus(Status.PLAYING);
    }

    @Test
    void testStop() throws ServletRequestBindingException {
        playQueueService.stop();
        verify(player.getPlayQueue(), times(1)).setStatus(Status.STOPPED);
    }

    @Test
    void testToggleStartStop() throws ServletRequestBindingException {
        when(player.getPlayQueue().getStatus()).thenReturn(Status.PLAYING);
        playQueueService.toggleStartStop();
        verify(player.getPlayQueue(), never()).setStatus(Status.PLAYING);
        verify(player.getPlayQueue(), times(1)).setStatus(Status.STOPPED);

        clearInvocations(player.getPlayQueue());
        when(player.getPlayQueue().getStatus()).thenReturn(Status.STOPPED);
        playQueueService.toggleStartStop();
        verify(player.getPlayQueue(), times(1)).setStatus(Status.PLAYING);
        verify(player.getPlayQueue(), never()).setStatus(Status.STOPPED);
    }

    @Test
    void testSkip() throws ServletRequestBindingException {
        playQueueService.skip(1);
        verify(player.getPlayQueue(), times(1)).setIndex(1);
    }

    @Test
    void testReloadSearchCriteria() throws ServletRequestBindingException {
        playQueueService.reloadSearchCriteria();
        verify(player.getPlayQueue(), never())
            .addFiles(anyBoolean(), ArgumentMatchers.<MediaFile>anyIterable());

        ShuffleSelectionParam criteria = new ShuffleSelectionParam(0, null, null, null, null);
        when(player.getPlayQueue().getShuffleSelectionParam()).thenReturn(criteria);
        playQueueService.reloadSearchCriteria();
        verify(player.getPlayQueue(), times(1))
            .addFiles(anyBoolean(), ArgumentMatchers.<MediaFile>anyIterable());
    }

    @Test
    void testSavePlayQueue() throws ServletRequestBindingException {
        MediaFile file = new MediaFile();
        file.setId(0);
        List<MediaFile> files = List.of(file);
        when(player.getPlayQueue().getFiles()).thenReturn(files);
        when(player.getPlayQueue().getFile(0)).thenReturn(files.get(0));

        playQueueService.savePlayQueue(0, 0);
        verify(playQueueDao, times(1)).savePlayQueue(any(SavedPlayQueue.class));

        clearInvocations(playQueueDao);
        playQueueService.savePlayQueue(-1, 0);
        verify(playQueueDao, times(1)).savePlayQueue(any(SavedPlayQueue.class));
    }

    @Test
    void testLoadPlayQueue() throws ServletRequestBindingException {
        playQueueService.loadPlayQueue();
        verify(mediaFileService, never()).getMediaFile(anyInt());

        MediaFile file = new MediaFile();
        file.setId(99);
        when(userService.getCurrentUsername(any(HttpServletRequest.class)))
            .thenReturn(ServiceMockUtils.ADMIN_NAME);
        SavedPlayQueue savedPlayQueue = new SavedPlayQueue(0, ServiceMockUtils.ADMIN_NAME,
                List.of(file.getId()), null, null, PlayerUtils.now(), "");
        when(playQueueDao.getPlayQueue(ServiceMockUtils.ADMIN_NAME)).thenReturn(savedPlayQueue);
        playQueueService.loadPlayQueue();

        when(mediaFileService.getMediaFile(file.getId())).thenReturn(file);
        playQueueService.loadPlayQueue();

        savedPlayQueue = new SavedPlayQueue(0, ServiceMockUtils.ADMIN_NAME, List.of(file.getId()),
                null, 0L, PlayerUtils.now(), "");
        when(playQueueDao.getPlayQueue(ServiceMockUtils.ADMIN_NAME)).thenReturn(savedPlayQueue);
        playQueueService.loadPlayQueue();

        int currentId = file.getId();
        savedPlayQueue = new SavedPlayQueue(0, ServiceMockUtils.ADMIN_NAME, List.of(file.getId()),
                currentId, 0L, PlayerUtils.now(), "");
        when(playQueueDao.getPlayQueue(ServiceMockUtils.ADMIN_NAME)).thenReturn(savedPlayQueue);

        when(player.getPlayQueue().getFiles()).thenReturn(Collections.emptyList());
        playQueueService.loadPlayQueue();

        when(player.getPlayQueue().getFiles()).thenReturn(List.of(file));
        PlayQueueInfo playQueueInfo = playQueueService.loadPlayQueue();
        assertEquals(1, playQueueInfo.getEntries().size());
    }

    @Documented
    private @interface PlayDecision {
        @interface MediaFile {
            @interface IsFile {
                @interface True {
                }

                @interface False {
                }

            }

            @interface Parent {
                @interface Null {
                }

                @interface NonNull {
                }
            }
        }

        @interface UserSettings {
            @interface IsQueueFollowingSongs {
                @interface False {
                }

                @interface True {
                }
            }
        }
    }

    @Nested
    class PlayTest {

        @PlayDecision.MediaFile.IsFile.True
        @PlayDecision.UserSettings.IsQueueFollowingSongs.False
        @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
        @Test
        void c01() throws ServletRequestBindingException {
            MediaFile song = new MediaFile();
            song.setId(0);
            when(mediaFileService.getMediaFileStrict(song.getId())).thenReturn(song);
            assertNotNull(playQueueService.play(song.getId()));
        }

        @PlayDecision.MediaFile.IsFile.True
        @PlayDecision.MediaFile.Parent.Null
        @PlayDecision.UserSettings.IsQueueFollowingSongs.True
        @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
        @Test
        void c02() throws ServletRequestBindingException {
            UserSettings mockedSetting = userService.getUserSettings(ServiceMockUtils.ADMIN_NAME);
            mockedSetting.setQueueFollowingSongs(true);
            MediaFile song = new MediaFile();
            song.setId(0);
            when(mediaFileService.getMediaFileStrict(song.getId())).thenReturn(song);
            assertNotNull(playQueueService.play(song.getId()));
        }

        @PlayDecision.MediaFile.IsFile.True
        @PlayDecision.MediaFile.Parent.NonNull
        @PlayDecision.UserSettings.IsQueueFollowingSongs.True
        @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
        @Test
        void c03() throws ServletRequestBindingException {
            UserSettings mockedSetting = userService.getUserSettings(ServiceMockUtils.ADMIN_NAME);
            mockedSetting.setQueueFollowingSongs(true);
            MediaFile parent = new MediaFile();
            parent.setId(0);
            MediaFile song1 = new MediaFile();
            song1.setId(1);
            song1.setPathString("song1");
            when(mediaFileService.getParent(song1)).thenReturn(Optional.of(parent));
            MediaFile song2 = new MediaFile();
            song2.setId(2);
            song2.setPathString("song2");
            List<MediaFile> children = List.of(song1, song2);
            when(mediaFileService.getChildrenOf(parent, true, false)).thenReturn(children);
            when(mediaFileService.getMediaFileStrict(song1.getId())).thenReturn(song1);
            assertNotNull(playQueueService.play(song1.getId()));
        }

        @PlayDecision.MediaFile.IsFile.False
        @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
        @Test
        void c04() throws ServletRequestBindingException {
            MediaFile dir = new MediaFile();
            dir.setId(0);
            dir.setMediaType(MediaType.DIRECTORY);
            when(mediaFileService.getMediaFileStrict(dir.getId())).thenReturn(dir);
            assertNotNull(playQueueService.play(dir.getId()));
        }
    }

    @Test
    void testPlayInternetRadio() throws ServletRequestBindingException, ExecutionException {
        when(player.getPlayQueue().isInternetRadioEnabled()).thenReturn(true);

        assertThatExceptionOfType(ExecutionException.class)
            .isThrownBy(() -> playQueueService.playInternetRadio(0, null))
            .withCause(new IOException("Radio is not enabled"));
        verify(internetRadioDao, times(1)).getInternetRadioById(anyInt());
        verify(internetRadioService, never()).clearInternetRadioSourceCache(anyInt());

        clearInvocations(internetRadioDao);
        InternetRadio radio = new InternetRadio(0, "inabled", "http://inabled.com",
                "http://inabled.com", false, PlayerUtils.now());
        when(internetRadioDao.getInternetRadioById(radio.getId())).thenReturn(radio);
        assertThatExceptionOfType(ExecutionException.class)
            .isThrownBy(() -> playQueueService.playInternetRadio(0, null))
            .withCause(new IOException("Radio is not enabled"));
        verify(internetRadioDao, times(1)).getInternetRadioById(anyInt());
        verify(internetRadioService, never()).clearInternetRadioSourceCache(radio.getId());

        clearInvocations(internetRadioDao);
        radio = new InternetRadio(0, "enabled", "http://enabled.com", "http://enabled.com", true,
                PlayerUtils.now());
        when(internetRadioDao.getInternetRadioById(radio.getId())).thenReturn(radio);
        when(player.getPlayQueue().getInternetRadio()).thenReturn(radio);
        InternetRadioSource radioSource = new InternetRadioSource(
                "http://enabled.com/radioStreamUrl");
        when(internetRadioService.getInternetRadioSources(radio)).thenReturn(List.of(radioSource));

        playQueueService.playInternetRadio(0, null);
        verify(internetRadioDao, times(1)).getInternetRadioById(radio.getId());
        verify(internetRadioService, times(1)).clearInternetRadioSourceCache(radio.getId());
    }

    @Test
    void testAddPlaylist() throws ServletRequestBindingException {
        assertEquals(0, player.getPlayQueue().length());

        // Not Present
        MediaFile file = new MediaFile();
        file.setId(0);
        file.setPresent(false);
        List<MediaFile> files = new ArrayList<>();
        files.add(file);
        when(playlistService.getFilesInPlaylist(anyInt(), anyBoolean())).thenReturn(files);
        when(mediaFileService.getDescendantsOf(any(MediaFile.class), anyBoolean()))
            .thenReturn(files);
        playQueueService.addPlaylist(0);
        verify(player.getPlayQueue(), times(1))
            .addFiles(anyBoolean(), ArgumentMatchers.<MediaFile>anyIterable());
        verify(player.getPlayQueue(), never())
            .addFilesAt(ArgumentMatchers.<MediaFile>anyIterable(), anyInt());
        assertEquals(0, player.getPlayQueue().length());

        // Present
        clearInvocations(player.getPlayQueue());
        file.setPresent(true);
        files = new ArrayList<>();
        files.add(file);
        when(playlistService.getFilesInPlaylist(anyInt(), anyBoolean())).thenReturn(files);
        when(mediaFileService.getDescendantsOf(any(MediaFile.class), anyBoolean()))
            .thenReturn(files);
        playQueueService.addPlaylist(0);
        verify(player.getPlayQueue(), times(1))
            .addFiles(anyBoolean(), ArgumentMatchers.<MediaFile>anyIterable());
        verify(player.getPlayQueue(), never())
            .addFilesAt(ArgumentMatchers.<MediaFile>anyIterable(), anyInt());
        assertEquals(0, player.getPlayQueue().length());
    }

    @Test
    void testPlayPlaylist() throws ServletRequestBindingException {
        // Not Present
        MediaFile file = new MediaFile();
        file.setId(0);
        file.setPresent(false);
        List<MediaFile> files = new ArrayList<>();
        files.add(file);
        when(playlistService.getFilesInPlaylist(anyInt(), anyBoolean())).thenReturn(files);
        playQueueService.playPlaylist(0, null);

        // Present
        clearInvocations(player.getPlayQueue());
        file.setPresent(true);
        files = new ArrayList<>();
        files.add(file);
        when(playlistService.getFilesInPlaylist(anyInt(), anyBoolean())).thenReturn(files);
        playQueueService.playPlaylist(0, null);

        // Present with StartIndex
        playQueueService.playPlaylist(0, 0);

        // Present with StartIndex, QueueFollowing
        UserSettings userSettings = userService.getUserSettings(ServiceMockUtils.ADMIN_NAME);
        userSettings.setQueueFollowingSongs(true);
        PlayQueueInfo playQueueInfo = playQueueService.playPlaylist(0, 0);
        assertEquals(0, playQueueInfo.getStartPlayerAt());
    }

    @Test
    void testPlayTopSong() throws ServletRequestBindingException {
        // Not Present
        MediaFile file = new MediaFile();
        file.setId(0);
        file.setPresent(false);
        List<MediaFile> files = new ArrayList<>();
        files.add(file);
        when(lastFmService
            .getTopSongs(nullable(MediaFile.class), anyInt(),
                    ArgumentMatchers.<MusicFolder>anyList()))
            .thenReturn(files);
        playQueueService.playTopSong(0, null);

        // Present
        clearInvocations(player.getPlayQueue());
        file.setPresent(true);
        files = new ArrayList<>();
        files.add(file);
        when(lastFmService
            .getTopSongs(nullable(MediaFile.class), anyInt(),
                    ArgumentMatchers.<MusicFolder>anyList()))
            .thenReturn(files);
        playQueueService.playTopSong(0, null);

        // Present with StartIndex
        playQueueService.playTopSong(0, 0);

        // Present with StartIndex, QueueFollowing
        UserSettings userSettings = userService.getUserSettings(ServiceMockUtils.ADMIN_NAME);
        userSettings.setQueueFollowingSongs(true);
        PlayQueueInfo playQueueInfo = playQueueService.playTopSong(0, 0);
        assertEquals(0, playQueueInfo.getStartPlayerAt());
    }

    @Test
    void testPlayPodcastChannel() throws ServletRequestBindingException {
        final int episodeId = 0;
        final int mediafileId = 99;

        // Empty
        playQueueService.playPodcastChannel(episodeId);

        // Not COMPLETED
        PodcastEpisode episode = new PodcastEpisode(episodeId, null, null, null, null, null, null,
                null, null, null, null, null);
        MediaFile file = new MediaFile();
        file.setId(mediafileId);
        when(mediaFileService.getMediaFile(file.getId())).thenReturn(file);
        episode.setMediaFileId(file.getId());
        when(podcastService.getEpisodes(episode.getId())).thenReturn(List.of(episode));
        playQueueService.playPodcastChannel(episode.getId());

        // COMPLETED
        episode.setStatus(PodcastStatus.COMPLETED);
        playQueueService.playPodcastChannel(episode.getId());

        // COMPLETED && present
        file.setPresent(true);
        PlayQueueInfo playQueueInfo = playQueueService.playPodcastChannel(episode.getId());
        assertEquals(0, playQueueInfo.getStartPlayerAt());
    }

    @Test
    void testPlayPodcastEpisode() throws ServletRequestBindingException {
        final int episodeId = 0;
        final int mediafileId = 99;
        final int channelId = 999;

        // Empty
        playQueueService.playPodcastEpisode(episodeId);

        // Not COMPLETED
        PodcastEpisode episode = new PodcastEpisode(episodeId, channelId, null, null, null, null,
                null, null, null, null, null, null);
        MediaFile file = new MediaFile();
        file.setId(mediafileId);
        when(mediaFileService.getMediaFile(file.getId())).thenReturn(file);
        episode.setMediaFileId(file.getId());
        when(podcastService.getEpisodes(episode.getId())).thenReturn(List.of(episode));
        playQueueService.playPodcastEpisode(episode.getId());

        // COMPLETED
        episode.setStatus(PodcastStatus.COMPLETED);
        playQueueService.playPodcastEpisode(episode.getId());

        // COMPLETED && present
        file.setPresent(true);
        playQueueService.playPodcastEpisode(episode.getId());

        // COMPLETED && present && QueueFollowing
        UserSettings userSettings = userService.getUserSettings(ServiceMockUtils.ADMIN_NAME);
        userSettings.setQueueFollowingSongs(true);
        PlayQueueInfo playQueueInfo = playQueueService.playPodcastEpisode(episode.getId());
        assertEquals(0, playQueueInfo.getStartPlayerAt());
    }

    @Test
    void testPlayNewestPodcastEpisode() throws ServletRequestBindingException {

        final int episodeId = 0;
        final int mediafileId = 99;

        // Empty
        playQueueService.playNewestPodcastEpisode(null);

        // Not COMPLETED (It doesn't seem to be reflected)
        PodcastEpisode episode = new PodcastEpisode(episodeId, null, null, null, null, null, null,
                null, null, null, null, null);
        MediaFile file = new MediaFile();
        file.setId(mediafileId);
        when(mediaFileService.getMediaFile(file.getId())).thenReturn(file);
        episode.setMediaFileId(file.getId());
        when(podcastService.getNewestEpisodes(anyInt())).thenReturn(List.of(episode));
        playQueueService.playNewestPodcastEpisode(null);

        // With StartIndex
        playQueueService.playNewestPodcastEpisode(0);

        // With StartIndex && QueueFollowing
        UserSettings userSettings = userService.getUserSettings(ServiceMockUtils.ADMIN_NAME);
        userSettings.setQueueFollowingSongs(true);
        PlayQueueInfo playQueueInfo = playQueueService.playNewestPodcastEpisode(0);
        assertEquals(0, playQueueInfo.getStartPlayerAt());
    }

    @Test
    void testPlayStarred() throws ServletRequestBindingException {
        playQueueService.playStarred();
        verify(mediaFileDao, times(1))
            .getStarredFiles(anyInt(), anyInt(), anyString(),
                    ArgumentMatchers.<MusicFolder>anyList());
    }

    @Test
    void testPlayShuffle() throws ServletRequestBindingException {
        // Empty
        playQueueService.playShuffle(null, 0, 0, null, null);
        verify(ratingService, never())
            .getHighestRatedAlbums(anyInt(), anyInt(), ArgumentMatchers.<MusicFolder>anyList());
        verify(mediaFileService, never())
            .getMostFrequentlyPlayedAlbums(anyInt(), anyInt(),
                    ArgumentMatchers.<MusicFolder>anyList());
        verify(mediaFileService, never())
            .getMostRecentlyPlayedAlbums(anyInt(), anyInt(),
                    ArgumentMatchers.<MusicFolder>anyList());
        verify(mediaFileService, never())
            .getNewestAlbums(anyInt(), anyInt(), ArgumentMatchers.<MusicFolder>anyList());
        verify(mediaFileService, never())
            .getStarredAlbums(anyInt(), anyInt(), nullable(String.class),
                    ArgumentMatchers.<MusicFolder>anyList());
        verify(legacySearch, never())
            .getRandomAlbums(anyInt(), ArgumentMatchers.<MusicFolder>anyList());
        verify(mediaFileService, never())
            .getAlphabeticalAlbums(anyInt(), anyInt(), anyBoolean(),
                    ArgumentMatchers.<MusicFolder>anyList());
        verify(mediaFileService, never())
            .getAlbumsByYear(anyInt(), anyInt(), anyInt(), anyInt(),
                    ArgumentMatchers.<MusicFolder>anyList());
        verify(legacySearch, never())
            .getAlbumsByGenres(nullable(String.class), anyInt(), anyInt(),
                    ArgumentMatchers.<MusicFolder>anyList());

        playQueueService.playShuffle("highest", 0, 0, null, null);
        verify(ratingService, times(1))
            .getHighestRatedAlbums(anyInt(), anyInt(), ArgumentMatchers.<MusicFolder>anyList());

        playQueueService.playShuffle("frequent", 0, 0, null, null);
        verify(mediaFileService, times(1))
            .getMostFrequentlyPlayedAlbums(anyInt(), anyInt(),
                    ArgumentMatchers.<MusicFolder>anyList());

        playQueueService.playShuffle("recent", 0, 0, null, null);
        verify(mediaFileService, times(1))
            .getMostRecentlyPlayedAlbums(anyInt(), anyInt(),
                    ArgumentMatchers.<MusicFolder>anyList());

        playQueueService.playShuffle("newest", 0, 0, null, null);
        verify(mediaFileService, times(1))
            .getNewestAlbums(anyInt(), anyInt(), ArgumentMatchers.<MusicFolder>anyList());

        playQueueService.playShuffle("starred", 0, 0, null, null);
        verify(mediaFileService, times(1))
            .getStarredAlbums(anyInt(), anyInt(), nullable(String.class),
                    ArgumentMatchers.<MusicFolder>anyList());

        playQueueService.playShuffle("random", 0, 0, null, null);
        verify(legacySearch, times(1))
            .getRandomAlbums(anyInt(), ArgumentMatchers.<MusicFolder>anyList());

        playQueueService.playShuffle("alphabetical", 0, 0, null, null);
        verify(mediaFileService, times(1))
            .getAlphabeticalAlbums(anyInt(), anyInt(), anyBoolean(),
                    ArgumentMatchers.<MusicFolder>anyList());

        playQueueService.playShuffle("decade", 0, 0, null, "2010");
        verify(mediaFileService, times(1))
            .getAlbumsByYear(anyInt(), anyInt(), anyInt(), anyInt(),
                    ArgumentMatchers.<MusicFolder>anyList());

        playQueueService.playShuffle("genre", 0, 0, "Rock", null);
        verify(legacySearch, times(1))
            .getAlbumsByGenres(nullable(String.class), anyLong(), anyLong(),
                    ArgumentMatchers.<MusicFolder>anyList());

        // Extract Albums
        verify(mediaFileService, never())
            .getChildrenWithoutSortOf(any(MediaFile.class), anyBoolean(), anyBoolean());
        MediaFile album = new MediaFile();
        when(ratingService
            .getHighestRatedAlbums(anyInt(), anyInt(), ArgumentMatchers.<MusicFolder>anyList()))
            .thenReturn(List.of(album));
        playQueueService.playShuffle("highest", 0, 0, null, null);
        verify(mediaFileService, times(1))
            .getChildrenWithoutSortOf(any(MediaFile.class), anyBoolean(), anyBoolean());
    }

    @Test
    void testPlayRandom() throws ServletRequestBindingException {
        MediaFile song = new MediaFile();
        song.setId(99);
        when(mediaFileService.getMediaFileStrict(song.getId())).thenReturn(song);
        playQueueService.playRandom(song.getId(), Integer.MAX_VALUE);

        verify(mediaFileService, times(1)).getMediaFileStrict(song.getId());
        verify(mediaFileService, times(1)).getRandomSongsForParent(song, Integer.MAX_VALUE);
    }

    @Test
    void testPlaySimilar() throws ServletRequestBindingException {
        playQueueService.playSimilar(0, Integer.MAX_VALUE);
        verify(lastFmService, times(1))
            .getSimilarSongs(nullable(MediaFile.class), anyInt(),
                    ArgumentMatchers.<MusicFolder>anyList());
    }

    @Test
    void testAdd() throws ServletRequestBindingException {
        playQueueService.add(0);
        verify(player.getPlayQueue(), times(1))
            .addFiles(anyBoolean(), ArgumentMatchers.<MediaFile>anyIterable());
    }

    @Test
    void testAddAt() throws ServletRequestBindingException {
        playQueueService.addAt(0, 0);
        verify(player.getPlayQueue(), times(1))
            .addFilesAt(ArgumentMatchers.<MediaFile>anyIterable(), anyInt());
    }

    @Test
    void testAddMediaFilesToPlayQueue() {
        MediaFile song = new MediaFile();
        song.setId(99);
        when(mediaFileService.getMediaFileStrict(song.getId())).thenReturn(song);
        when(mediaFileService.getDescendantsOf(any(MediaFile.class), anyBoolean()))
            .thenReturn(List.of(song));

        playQueueService
            .addMediaFilesToPlayQueue(player.getPlayQueue(), new int[] { song.getId() }, null);
        verify(mediaFileService, times(1)).removeVideoFiles(ArgumentMatchers.<MediaFile>anyList());
        verify(player.getPlayQueue(), times(1))
            .addFiles(anyBoolean(), ArgumentMatchers.<MediaFile>anyIterable());

        // With Index
        clearInvocations(player.getPlayQueue(), mediaFileService);
        playQueueService
            .addMediaFilesToPlayQueue(player.getPlayQueue(), new int[] { song.getId() }, 0);
        verify(mediaFileService, times(1)).removeVideoFiles(ArgumentMatchers.<MediaFile>anyList());
        verify(player.getPlayQueue(), times(1))
            .addFilesAt(ArgumentMatchers.<MediaFile>anyIterable(), anyInt());
    }

    @Test
    void testResetPlayQueue() {
        playQueueService.resetPlayQueue(player.getPlayQueue(), new int[] { 0 });
        verify(player.getPlayQueue(), times(1))
            .addFiles(anyBoolean(), ArgumentMatchers.<MediaFile>anyIterable());
        verify(player.getPlayQueue(), times(1)).setIndex(anyInt());
        verify(player.getPlayQueue(), times(1)).setStatus(nullable(Status.class));
    }

    @Test
    void testClear() throws ServletRequestBindingException {
        playQueueService.clear();
        verify(player.getPlayQueue(), times(1)).clear();
    }

    @Test
    void testShuffle() throws ServletRequestBindingException {
        playQueueService.shuffle();
        verify(player.getPlayQueue(), times(1)).shuffle();
    }

    @Test
    void testRemove() throws ServletRequestBindingException {
        playQueueService.remove(0);
        verify(player.getPlayQueue(), times(1)).removeFileAt(0);
    }

    @Test
    void testToggleStar() throws ServletRequestBindingException {
        int index = 0;
        MediaFile song = new MediaFile();
        song.setId(99);
        player.getPlayQueue().addFiles(true, List.of(song));
        when(player.getPlayQueue().getFile(index)).thenReturn(song);

        // Star
        playQueueService.toggleStar(index);
        verify(mediaFileDao, times(1)).starMediaFile(anyInt(), nullable(String.class));

        // UnStar
        when(mediaFileDao.getMediaFileStarredDate(anyInt(), nullable(String.class)))
            .thenReturn(PlayerUtils.now());
        playQueueService.toggleStar(index);
        verify(mediaFileDao, times(1)).unstarMediaFile(anyInt(), nullable(String.class));
    }

    @Test
    void testDoRemove() throws ServletRequestBindingException {
        playQueueService
            .doRemove(ajaxHelper.getHttpServletRequest(), ajaxHelper.getHttpServletResponse(), 0);
        verify(player.getPlayQueue(), times(1)).removeFileAt(0);
    }

    @Test
    void testRemoveMany() throws ServletRequestBindingException {
        playQueueService.removeMany(new int[] { 0 });
        verify(player.getPlayQueue(), times(1)).removeFileAt(anyInt());
    }

    @Test
    void testRearrange() throws ServletRequestBindingException {
        playQueueService.rearrange(new int[] { 0 });
        verify(player.getPlayQueue(), times(1)).rearrange(anyInt());
    }

    @Test
    void testUp() throws ServletRequestBindingException {
        playQueueService.up(0);
        verify(player.getPlayQueue(), times(1)).moveUp(anyInt());
    }

    @Test
    void testDown() throws ServletRequestBindingException {
        playQueueService.down(0);
        verify(player.getPlayQueue(), times(1)).moveDown(anyInt());
    }

    @Test
    void testToggleRepeat() throws ServletRequestBindingException {
        playQueueService.toggleRepeat();
        verify(player.getPlayQueue(), never())
            .setShuffleSelectionParam(nullable(ShuffleSelectionParam.class));
        verify(player.getPlayQueue(), times(1)).setRepeatEnabled(anyBoolean());

        clearInvocations(player.getPlayQueue());
        when(player.getPlayQueue().isRepeatEnabled()).thenReturn(true);
        playQueueService.toggleRepeat();
        verify(player.getPlayQueue(), never())
            .setShuffleSelectionParam(nullable(ShuffleSelectionParam.class));
        verify(player.getPlayQueue(), times(1)).setRepeatEnabled(anyBoolean());

        // ShuffleRadioEnabled
        clearInvocations(player.getPlayQueue());
        when(player.getPlayQueue().isShuffleRadioEnabled()).thenReturn(true);
        playQueueService.toggleRepeat();
        verify(player.getPlayQueue(), times(1))
            .setShuffleSelectionParam(nullable(ShuffleSelectionParam.class));
        verify(player.getPlayQueue(), times(1)).setRepeatEnabled(anyBoolean());
    }

    @Test
    void testUndo() throws ServletRequestBindingException {
        playQueueService.undo();
        verify(player.getPlayQueue(), times(1)).undo();
    }

    @Test
    void testSortByTrack() throws ServletRequestBindingException {
        playQueueService.sortByTrack();
        verify(comparators, times(1)).mediaFileOrderBy(OrderBy.TRACK);
    }

    @Test
    void testSortByArtist() throws ServletRequestBindingException {
        playQueueService.sortByArtist();
        verify(comparators, times(1)).mediaFileOrderBy(OrderBy.ARTIST);
    }

    @Test
    void testSortByAlbum() throws ServletRequestBindingException {
        playQueueService.sortByAlbum();
        verify(comparators, times(1)).mediaFileOrderBy(OrderBy.ALBUM);
    }
}

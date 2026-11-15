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

package com.tesshu.jpsonic.ajax;

import static com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators.OrderBy.ALBUM;
import static com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators.OrderBy.ARTIST;
import static com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators.OrderBy.TRACK;
import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.controller.ViewName;
import com.tesshu.jpsonic.domain.system.PodcastStatus;
import com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators;
import com.tesshu.jpsonic.infrastructure.filesystem.MediaTypeDetector;
import com.tesshu.jpsonic.infrastructure.search.LegacySearch;
import com.tesshu.jpsonic.persistence.api.entity.InternetRadio;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.entity.PlayQueue;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode;
import com.tesshu.jpsonic.persistence.api.repository.InternetRadioDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.api.repository.PlayQueueDao;
import com.tesshu.jpsonic.persistence.result.SavedPlayQueue;
import com.tesshu.jpsonic.service.InternetRadioService;
import com.tesshu.jpsonic.service.InternetRadioService.InternetRadioSource;
import com.tesshu.jpsonic.service.JWTSecurityService;
import com.tesshu.jpsonic.service.LastFmService;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.NetworkUtils;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.PlaylistService;
import com.tesshu.jpsonic.service.PodcastService;
import com.tesshu.jpsonic.service.RatingService;
import com.tesshu.jpsonic.service.UserService;
import com.tesshu.jpsonic.util.StringUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Provides AJAX-enabled services for manipulating the play queue of a player.
 * This class is used by the DWR framework (http://getahead.ltd.uk/dwr/).
 *
 * @author Sindre Mehus
 */
@Service("ajaxPlayQueueService")
@SuppressWarnings("PMD.UseVarargs") // Don't use varargs in Ajax
public class PlayQueueService {

    private final MusicFolderService musicFolderService;
    private final UserService userService;
    private final PlayerService playerService;
    private final JpsonicComparators comparators;
    private final MediaFileService mediaFileService;
    private final LastFmService lastFmService;
    private final LegacySearch legacySearch;
    private final RatingService ratingService;
    private final PodcastService podcastService;
    private final PlaylistService playlistService;
    private final MediaFileDao mediaFileDao;
    private final PlayQueueDao playQueueDao;
    private final InternetRadioDao internetRadioDao;
    private final JWTSecurityService jwtSecurityService;
    private final InternetRadioService internetRadioService;
    private final AjaxHelper ajaxHelper;

    public PlayQueueService(MusicFolderService musicFolderService, UserService userService,
            PlayerService playerService, JpsonicComparators comparators,
            MediaFileService mediaFileService, LastFmService lastFmService,
            LegacySearch legacySearch, RatingService ratingService, PodcastService podcastService,
            PlaylistService playlistService, MediaFileDao mediaFileDao, PlayQueueDao playQueueDao,
            InternetRadioDao internetRadioDao, JWTSecurityService jwtSecurityService,
            InternetRadioService internetRadioService, AjaxHelper ajaxHelper) {
        super();
        this.musicFolderService = musicFolderService;
        this.userService = userService;
        this.playerService = playerService;
        this.comparators = comparators;
        this.mediaFileService = mediaFileService;
        this.lastFmService = lastFmService;
        this.legacySearch = legacySearch;
        this.ratingService = ratingService;
        this.podcastService = podcastService;
        this.playlistService = playlistService;
        this.mediaFileDao = mediaFileDao;
        this.playQueueDao = playQueueDao;
        this.internetRadioDao = internetRadioDao;
        this.jwtSecurityService = jwtSecurityService;
        this.internetRadioService = internetRadioService;
        this.ajaxHelper = ajaxHelper;
    }

    /**
     * Returns the play queue for the player of the current user.
     *
     * @return The play queue.
     */
    public PlayQueueInfo getPlayQueue() throws ServletRequestBindingException {
        Player player = resolvePlayer();
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo start() throws ServletRequestBindingException {
        Player player = resolvePlayer();
        player.getPlayQueue().setStatus(PlayQueue.Status.PLAYING);
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo stop() throws ServletRequestBindingException {
        Player player = resolvePlayer();
        player.getPlayQueue().setStatus(PlayQueue.Status.STOPPED);
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo toggleStartStop() throws ServletRequestBindingException {
        Player player = resolvePlayer();
        switch (player.getPlayQueue().getStatus()) {
        case STOPPED -> player.getPlayQueue().setStatus(PlayQueue.Status.PLAYING);
        case PLAYING -> player.getPlayQueue().setStatus(PlayQueue.Status.STOPPED);
        }
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo skip(int index) throws ServletRequestBindingException {
        return doSkip(index, 0);
    }

    public PlayQueueInfo doSkip(int index, int offset) throws ServletRequestBindingException {
        Player player = resolvePlayer();
        player.getPlayQueue().setIndex(index);
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo reloadSearchCriteria() throws ServletRequestBindingException {
        Player player = resolvePlayer();
        PlayQueue playQueue = player.getPlayQueue();
        playQueue.setInternetRadio(null);
        if (playQueue.getShuffleSelectionParam() != null) {
            playQueue
                .addFiles(true, mediaFileService
                    .getRandomSongs(playQueue.getShuffleSelectionParam(), resolveUsername()));
        }
        return createPlayQueueInfo(player);
    }

    public void savePlayQueue(int currentSongIndex, long positionMillis)
            throws ServletRequestBindingException {
        Player player = resolvePlayer();
        PlayQueue playQueue = player.getPlayQueue();
        List<Integer> ids = MediaFile.toIdList(playQueue.getFiles());
        Integer currentId = currentSongIndex == -1 ? null
                : playQueue.getFile(currentSongIndex).getId();
        SavedPlayQueue savedPlayQueue = new SavedPlayQueue(null, resolveUsername(), ids, currentId,
                positionMillis, now(), "Jpsonic");
        playQueueDao.savePlayQueue(savedPlayQueue);
    }

    public PlayQueueInfo loadPlayQueue() throws ServletRequestBindingException {
        Player player = resolvePlayer();
        SavedPlayQueue savedPlayQueue = playQueueDao.getPlayQueue(resolveUsername());
        if (savedPlayQueue == null) {
            return createPlayQueueInfo(player);
        }

        PlayQueue playQueue = player.getPlayQueue();
        playQueue.clear();
        for (Integer mediaFileId : savedPlayQueue.getMediaFileIds()) {
            MediaFile mediaFile = mediaFileService.getMediaFile(mediaFileId);
            if (mediaFile != null) {
                playQueue.addFiles(true, mediaFile);
            }
        }

        PlayQueueInfo playQueueInfo = createPlayQueueInfo(player);
        Integer currentId = savedPlayQueue.getCurrentMediaFileId();
        int currentIndex = -1;
        long positionMillis = savedPlayQueue.getPositionMillis() == null ? 0L
                : savedPlayQueue.getPositionMillis();
        if (currentId != null) {
            MediaFile current = mediaFileService.getMediaFile(currentId);
            currentIndex = playQueue.getFiles().indexOf(current);
            if (currentIndex != -1) {
                playQueueInfo.startPlayerAtAndGetInfo(currentIndex);
                playQueueInfo.startPlayerAtPositionAndGetInfo(positionMillis);
            }
        }
        if (currentIndex != -1) {
            doSkip(currentIndex, (int) (positionMillis / 1000L));
        }

        return playQueueInfo;
    }

    public PlayQueueInfo play(int id) throws ServletRequestBindingException {
        Player player = resolvePlayer();
        MediaFile mediaFile = mediaFileService.getMediaFileStrict(id);

        final List<MediaFile> songs = new ArrayList<>();

        if (!mediaFile.isFile()) {
            songs.addAll(mediaFileService.getDescendantsOf(mediaFile, true));
            return doPlay(player, songs).startPlayerAtAndGetInfo(0);
        }

        boolean queueFollowingSongs = userService
            .getUserSettings(resolveUsername())
            .isQueueFollowingSongs();
        if (queueFollowingSongs) {
            mediaFileService.getParent(mediaFile).ifPresentOrElse(parent -> {
                List<MediaFile> children = mediaFileService.getChildrenOf(parent, true, false);
                if (!children.isEmpty()) {
                    int index = children.indexOf(mediaFile);
                    songs.addAll(children.subList(index, children.size()));
                }
            }, () -> songs.add(mediaFile));
        } else {
            songs.add(mediaFile);
        }
        return doPlay(player, songs).startPlayerAtAndGetInfo(0);
    }

    /*
     * Start playing at this index in the list of radio streams, or play whole radio
     * playlist if {@code null}.
     */
    public PlayQueueInfo playInternetRadio(int id, Integer startIndex)
            throws ServletRequestBindingException, ExecutionException {
        InternetRadio radio = internetRadioDao.getInternetRadioById(id);
        if (radio == null || !radio.isEnabled()) {
            throw new ExecutionException(new IOException("Radio is not enabled"));
        }
        return doPlayInternetRadio(resolvePlayer(), radio).startPlayerAtAndGetInfo(0);
    }

    public PlayQueueInfo addPlaylist(int id) throws ServletRequestBindingException {
        List<MediaFile> files = playlistService.getFilesInPlaylist(id, true);

        // Remove non-present files
        files.removeIf(file -> !file.isPresent());

        // Add to the play queue
        int[] ids = files.stream().mapToInt(MediaFile::getId).toArray();
        return doAdd(ids, null);
    }

    /*
     * Start playing at this index, or play whole playlist if {@code null}.
     */
    public PlayQueueInfo playPlaylist(int id, Integer startIndex)
            throws ServletRequestBindingException {
        boolean queueFollowingSongs = userService
            .getUserSettings(resolveUsername())
            .isQueueFollowingSongs();

        List<MediaFile> files = playlistService.getFilesInPlaylist(id, true);
        if (!files.isEmpty() && startIndex != null) {
            if (queueFollowingSongs) {
                files = files.subList(startIndex, files.size());
            } else {
                files = Arrays.asList(files.get(startIndex));
            }
        }

        // Remove non-present files
        files.removeIf(file -> !file.isPresent());

        // Play now
        Player player = resolvePlayer();
        return doPlay(player, files).startPlayerAtAndGetInfo(0);
    }

    /*
     * Start playing at this index, or play all top songs if {@code null}.
     */
    public PlayQueueInfo playTopSong(int id, Integer startIndex)
            throws ServletRequestBindingException {
        String username = resolveUsername();
        boolean queueFollowingSongs = userService.getUserSettings(username).isQueueFollowingSongs();
        List<MusicFolder> musicFolders = musicFolderService.getMusicFoldersForUser(username);
        List<MediaFile> files = lastFmService
            .getTopSongs(mediaFileService.getMediaFileStrict(id), 50, musicFolders);
        if (!files.isEmpty() && startIndex != null) {
            if (queueFollowingSongs) {
                files = files.subList(startIndex, files.size());
            } else {
                files = Arrays.asList(files.get(startIndex));
            }
        }

        Player player = resolvePlayer();
        return doPlay(player, files).startPlayerAtAndGetInfo(0);
    }

    public PlayQueueInfo playPodcastChannel(int id) throws ServletRequestBindingException {
        List<PodcastEpisode> episodes = podcastService.getEpisodes(id);
        List<MediaFile> files = new ArrayList<>();
        for (PodcastEpisode episode : episodes) {
            if (episode.getStatus() == PodcastStatus.COMPLETED) {
                MediaFile mediaFile = mediaFileService.getMediaFile(episode.getMediaFileId());
                if (mediaFile != null && mediaFile.isPresent()) {
                    files.add(mediaFile);
                }
            }
        }
        Player player = resolvePlayer();
        return doPlay(player, files).startPlayerAtAndGetInfo(0);
    }

    public PlayQueueInfo playPodcastEpisode(int id) throws ServletRequestBindingException {
        PodcastEpisode episode = podcastService.getEpisodeStrict(id, false);
        List<PodcastEpisode> allEpisodes = podcastService.getEpisodes(episode.getChannelId());
        List<MediaFile> files = new ArrayList<>();
        boolean queueFollowingSongs = userService
            .getUserSettings(resolveUsername())
            .isQueueFollowingSongs();

        for (PodcastEpisode ep : allEpisodes) {
            if (ep.getStatus() == PodcastStatus.COMPLETED) {
                MediaFile mediaFile = mediaFileService.getMediaFile(ep.getMediaFileId());
                if (mediaFile != null && mediaFile.isPresent()
                        && (ep.getId().equals(episode.getId())
                                || queueFollowingSongs && !files.isEmpty())) {
                    files.add(mediaFile);
                }
            }
        }
        Player player = resolvePlayer();
        return doPlay(player, files).startPlayerAtAndGetInfo(0);
    }

    public PlayQueueInfo playNewestPodcastEpisode(Integer startIndex)
            throws ServletRequestBindingException {
        List<PodcastEpisode> episodes = podcastService.getNewestEpisodes(10);
        List<MediaFile> files = episodes
            .stream()
            .map(episode -> mediaFileService.getMediaFile(episode.getMediaFileId()))
            .collect(Collectors.toList());
        boolean queueFollowingSongs = userService
            .getUserSettings(resolveUsername())
            .isQueueFollowingSongs();

        if (!files.isEmpty() && startIndex != null) {
            if (queueFollowingSongs) {
                files = files.subList(startIndex, files.size());
            } else {
                files = Arrays.asList(files.get(startIndex));
            }
        }

        Player player = resolvePlayer();
        return doPlay(player, files).startPlayerAtAndGetInfo(0);
    }

    public PlayQueueInfo playStarred() throws ServletRequestBindingException {
        String username = resolveUsername();
        List<MusicFolder> musicFolders = musicFolderService.getMusicFoldersForUser(username);
        List<MediaFile> files = mediaFileDao
            .getStarredFiles(0, Integer.MAX_VALUE, username, musicFolders);
        Player player = resolvePlayer();
        return doPlay(player, files).startPlayerAtAndGetInfo(0);
    }

    public PlayQueueInfo playShuffle(String albumListType, int offset, int count, String genre,
            String decade) throws ServletRequestBindingException {
        String username = resolveUsername();
        MusicFolder selectedMusicFolder = userService.getSelectedMusicFolder(username);
        List<MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(username,
                    selectedMusicFolder == null ? null : selectedMusicFolder.getId());
        List<MediaFile> albums;
        if ("highest".equals(albumListType)) {
            albums = ratingService.getHighestRatedAlbums(offset, count, musicFolders);
        } else if ("frequent".equals(albumListType)) {
            albums = mediaFileService.getMostFrequentlyPlayedAlbums(offset, count, musicFolders);
        } else if ("recent".equals(albumListType)) {
            albums = mediaFileService.getMostRecentlyPlayedAlbums(offset, count, musicFolders);
        } else if ("newest".equals(albumListType)) {
            albums = mediaFileService.getNewestAlbums(offset, count, musicFolders);
        } else if ("starred".equals(albumListType)) {
            albums = mediaFileService.getStarredAlbums(offset, count, username, musicFolders);
        } else if ("random".equals(albumListType)) {
            albums = legacySearch.getRandomAlbums(count, musicFolders);
        } else if ("alphabetical".equals(albumListType)) {
            albums = mediaFileService.getAlphabeticalAlbums(offset, count, true, musicFolders);
        } else if ("decade".equals(albumListType)) {
            int fromYear = Integer.parseInt(decade);
            int toYear = fromYear + 9;
            albums = mediaFileService
                .getAlbumsByYear(offset, count, fromYear, toYear, musicFolders);
        } else if ("genre".equals(albumListType)) {
            albums = legacySearch.getAlbumsByGenres(genre, offset, count, musicFolders);
        } else {
            albums = Collections.emptyList();
        }

        List<MediaFile> songs = new ArrayList<>();
        for (MediaFile album : albums) {
            songs.addAll(mediaFileService.getChildrenWithoutSortOf(album, true, false));
        }
        Collections.shuffle(songs);
        songs = songs.subList(0, Math.min(40, songs.size()));
        Player player = resolvePlayer();
        return doPlay(player, songs).startPlayerAtAndGetInfo(0);
    }

    private PlayQueueInfo doPlay(Player player, List<MediaFile> files) {
        mediaFileService.removeVideoFiles(files);
        player.getPlayQueue().addFiles(false, files);
        player.getPlayQueue().setShuffleSelectionParam(null);
        player.getPlayQueue().setInternetRadio(null);
        return createPlayQueueInfo(player);
    }

    private PlayQueueInfo doPlayInternetRadio(Player player, InternetRadio radio) {
        internetRadioService.clearInternetRadioSourceCache(radio.getId());
        player.getPlayQueue().clear();
        player.getPlayQueue().setShuffleSelectionParam(null);
        player.getPlayQueue().setInternetRadio(radio);
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo playRandom(int id, int count) throws ServletRequestBindingException {
        MediaFile file = mediaFileService.getMediaFileStrict(id);
        List<MediaFile> randomFiles = mediaFileService.getRandomSongsForParent(file, count);
        Player player = resolvePlayer();
        player.getPlayQueue().addFiles(false, randomFiles);
        player.getPlayQueue().setShuffleSelectionParam(null);
        player.getPlayQueue().setInternetRadio(null);
        return createPlayQueueInfo(player).startPlayerAtAndGetInfo(0);
    }

    public PlayQueueInfo playSimilar(int id, int count) throws ServletRequestBindingException {
        MediaFile artist = mediaFileService.getMediaFileStrict(id);
        List<MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(resolveUsername());
        List<MediaFile> similarSongs = lastFmService.getSimilarSongs(artist, count, musicFolders);
        Player player = resolvePlayer();
        player.getPlayQueue().addFiles(false, similarSongs);
        player.getPlayQueue().setShuffleSelectionParam(null);
        player.getPlayQueue().setInternetRadio(null);
        return createPlayQueueInfo(player).startPlayerAtAndGetInfo(0);
    }

    public PlayQueueInfo add(int id) throws ServletRequestBindingException {
        return doAdd(new int[] { id }, null);
    }

    public PlayQueueInfo addAt(int id, int index) throws ServletRequestBindingException {
        return doAdd(new int[] { id }, index);
    }

    /**
     * TODO This method should be moved to a real PlayQueueService not dedicated to
     * Ajax DWR.
     *
     * @param addAtIndex if not null, insert the media files at the specified index
     *                   otherwise, append the media files at the end of the play
     *                   queue
     */
    public PlayQueue addMediaFilesToPlayQueue(PlayQueue playQueue, int[] ids, Integer addAtIndex) {
        List<MediaFile> files = new ArrayList<>(ids.length);
        for (int id : ids) {
            MediaFile ancestor = mediaFileService.getMediaFileStrict(id);
            files.addAll(mediaFileService.getDescendantsOf(ancestor, true));
        }
        mediaFileService.removeVideoFiles(files);
        if (addAtIndex == null) {
            playQueue.addFiles(true, files);
        } else {
            playQueue.addFilesAt(files, addAtIndex);
        }
        playQueue.setShuffleSelectionParam(null);
        playQueue.setInternetRadio(null);
        return playQueue;
    }

    /*
     * if not null, insert the media files at the specified index otherwise, append
     * the media files at the end of the play queue
     */
    public PlayQueueInfo doAdd(int[] ids, Integer addAtIndex)
            throws ServletRequestBindingException {
        Player player = resolvePlayer();
        addMediaFilesToPlayQueue(player.getPlayQueue(), ids, addAtIndex);
        return createPlayQueueInfo(player);
    }

    /**
     * TODO This method should be moved to a real PlayQueueService not dedicated to
     * Ajax DWR.
     */
    public PlayQueue resetPlayQueue(PlayQueue playQueue, int[] ids) {
        MediaFile currentFile = playQueue.getCurrentFile();

        playQueue.clear();
        addMediaFilesToPlayQueue(playQueue, ids, null);

        int index = currentFile == null ? -1 : playQueue.getFiles().indexOf(currentFile);
        playQueue.setIndex(index);
        playQueue.setStatus(playQueue.getStatus());
        return playQueue;
    }

    public PlayQueueInfo clear() throws ServletRequestBindingException {
        Player player = resolvePlayer();
        player.getPlayQueue().clear();
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo shuffle() throws ServletRequestBindingException {
        Player player = resolvePlayer();
        player.getPlayQueue().shuffle();
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo remove(int index) throws ServletRequestBindingException {
        Player player = resolvePlayer();
        player.getPlayQueue().removeFileAt(index);
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo toggleStar(int index) throws ServletRequestBindingException {
        Player player = resolvePlayer();
        MediaFile file = player.getPlayQueue().getFile(index);
        String username = resolveUsername();
        boolean starred = mediaFileDao.getMediaFileStarredDate(file.getId(), username) != null;
        if (starred) {
            mediaFileDao.unstarMediaFile(file.getId(), username);
        } else {
            mediaFileDao.starMediaFile(file.getId(), username);
        }
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo doRemove(HttpServletRequest request, HttpServletResponse response,
            int index) throws ServletRequestBindingException {
        Player player = resolvePlayer();
        player.getPlayQueue().removeFileAt(index);
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo removeMany(int[] indexes) throws ServletRequestBindingException {
        Player player = resolvePlayer();
        for (int i = indexes.length - 1; i >= 0; i--) {
            player.getPlayQueue().removeFileAt(indexes[i]);
        }
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo rearrange(int[] indexes) throws ServletRequestBindingException {
        Player player = resolvePlayer();
        player.getPlayQueue().rearrange(indexes);
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo up(int index) throws ServletRequestBindingException {
        Player player = resolvePlayer();
        player.getPlayQueue().moveUp(index);
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo down(int index) throws ServletRequestBindingException {
        Player player = resolvePlayer();
        player.getPlayQueue().moveDown(index);
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo toggleRepeat() throws ServletRequestBindingException {
        Player player = resolvePlayer();
        PlayQueue playQueue = player.getPlayQueue();
        if (playQueue.isShuffleRadioEnabled()) {
            playQueue.setShuffleSelectionParam(null);
            playQueue.setRepeatEnabled(false);
        } else {
            playQueue.setRepeatEnabled(!player.getPlayQueue().isRepeatEnabled());
        }
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo undo() throws ServletRequestBindingException {
        Player player = resolvePlayer();
        player.getPlayQueue().undo();
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo sortByTrack() throws ServletRequestBindingException {
        Player player = resolvePlayer();
        player.getPlayQueue().sort(comparators.mediaFileOrderBy(TRACK));
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo sortByArtist() throws ServletRequestBindingException {
        Player player = resolvePlayer();
        player.getPlayQueue().sort(comparators.mediaFileOrderBy(ARTIST));
        return createPlayQueueInfo(player);
    }

    public PlayQueueInfo sortByAlbum() throws ServletRequestBindingException {
        Player player = resolvePlayer();
        player.getPlayQueue().sort(comparators.mediaFileOrderBy(ALBUM));
        return createPlayQueueInfo(player);
    }

    private PlayQueueInfo createPlayQueueInfo(Player player) {
        PlayQueue playQueue = player.getPlayQueue();
        List<PlayQueueInfo.Entry> entries = playQueue.isInternetRadioEnabled()
                ? convertInternetRadio(player)
                : convertMediaFileList(player);
        boolean isStopEnabled = playQueue.getStatus() == PlayQueue.Status.PLAYING;
        return new PlayQueueInfo(entries, isStopEnabled, playQueue.isRepeatEnabled(),
                playQueue.isShuffleRadioEnabled(), playQueue.isInternetRadioEnabled());
    }

    private List<PlayQueueInfo.Entry> convertMediaFileList(Player player) {

        String url = resolveBaseUrl();
        Locale locale = resolveLocale();
        PlayQueue playQueue = player.getPlayQueue();

        List<PlayQueueInfo.Entry> entries = new ArrayList<>();
        for (MediaFile file : playQueue.getFiles()) {

            String albumUrl = url + ViewName.MAIN.value() + "?id=" + file.getId();
            String streamUrl = url + "stream?player=" + player.getId() + "&id=" + file.getId();
            String coverArtUrl = url + ViewName.COVER_ART.value() + "?id=" + file.getId();

            String remoteStreamUrl = jwtSecurityService
                .addJWTToken(url + "ext/stream?player=" + player.getId() + "&id=" + file.getId());
            String remoteCoverArtUrl = jwtSecurityService
                .addJWTToken(url + "ext/" + ViewName.COVER_ART.value() + "?id=" + file.getId());

            String format = file.getFormat();
            boolean starred = mediaFileService
                .getMediaFileStarredDate(file.getId(), resolveUsername()) != null;
            entries
                .add(new PlayQueueInfo.Entry(file.getId(), file.getTrackNumber(), file.getTitle(),
                        file.getArtist(), file.getComposer(), file.getAlbumName(), file.getGenre(),
                        file.getYear(), formatBitRate(file), file.getDurationSeconds(),
                        file.getDurationString(), format, formatContentType(format),
                        formatFileSize(file.getFileSize(), locale), starred, albumUrl, streamUrl,
                        remoteStreamUrl, coverArtUrl, remoteCoverArtUrl));
        }

        return entries;
    }

    private List<PlayQueueInfo.Entry> convertInternetRadio(Player player) {

        PlayQueue playQueue = player.getPlayQueue();
        InternetRadio radio = playQueue.getInternetRadio();

        final String radioHomepageUrl = radio.getHomepageUrl();
        final String radioName = radio.getName();

        List<PlayQueueInfo.Entry> entries = new ArrayList<>();
        for (InternetRadioSource streamSource : internetRadioService
            .getInternetRadioSources(radio)) {
            // Fake entry id so that the source can be selected in the UI
            int streamId = -(1 + entries.size());
            Integer streamTrackNumber = entries.size();
            String streamUrl = streamSource.getStreamUrl();
            entries
                .add(new PlayQueueInfo.Entry(streamId, // Entry id
                        streamTrackNumber, // Track number
                        streamUrl, // Track title (use radio stream URL for now)
                        "", // Track artist
                        "", // Track composer
                        radioName, // Album name (use radio name)
                        "Internet Radio", // Genre
                        0, // Year
                        "", // Bit rate
                        0, // Duration
                        "", // Duration (as string)
                        "", // Format
                        "", // Content Type
                        "", // File size
                        false, // Starred
                        radioHomepageUrl, // Album URL (use radio home page URL)
                        streamUrl, // Stream URL
                        streamUrl, // Remote stream URL
                        null, // Cover art URL
                        null // Remote cover art URL
                ));
        }

        return entries;
    }

    private String formatFileSize(Long fileSize, Locale locale) {
        if (fileSize == null) {
            return null;
        }
        return StringUtil.formatBytes(fileSize, locale);
    }

    private String formatContentType(String format) {
        return MediaTypeDetector.getMimeType(format);
    }

    private String formatBitRate(MediaFile mediaFile) {
        if (mediaFile.getBitRate() == null) {
            return null;
        }
        if (mediaFile.isVariableBitRate()) {
            return mediaFile.getBitRate() + " Kbps vbr";
        }
        return mediaFile.getBitRate() + " Kbps";
    }

    private String resolveUsername() {
        return userService.getCurrentUsername(ajaxHelper.getHttpServletRequest());
    }

    private Player resolvePlayer() throws ServletRequestBindingException {
        return playerService
            .getPlayer(ajaxHelper.getHttpServletRequest(), ajaxHelper.getHttpServletResponse());
    }

    private String resolveBaseUrl() {
        return NetworkUtils.getBaseUrl(ajaxHelper.getHttpServletRequest());
    }

    private Locale resolveLocale() {
        return RequestContextUtils.getLocale(ajaxHelper.getHttpServletRequest());
    }
}

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

package com.tesshu.jpsonic.controller;

import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.tesshu.jpsonic.domain.MusicFolder;
import com.tesshu.jpsonic.domain.PlayQueue;
import com.tesshu.jpsonic.domain.Player;
import com.tesshu.jpsonic.domain.RandomSearchCriteria;
import com.tesshu.jpsonic.domain.User;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.SecurityService;
import com.tesshu.jpsonic.service.search.IndexManager;
import com.tesshu.jpsonic.util.LegacyMap;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for the creating a random play queue.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/" + ViewName.ViewNameConstants.RANDOM_PLAYQUEUE)
public class RandomPlayQueueController {

    private static final Logger LOG = LoggerFactory.getLogger(RandomPlayQueueController.class);
    private static final String REQUEST_VALUE_ANY = "any";

    private final MusicFolderService musicFolderService;
    private final SecurityService securityService;
    private final PlayerService playerService;
    private final MediaFileService mediaFileService;
    private final IndexManager indexManager;

    public RandomPlayQueueController(MusicFolderService musicFolderService,
            SecurityService securityService, PlayerService playerService,
            MediaFileService mediaFileService, IndexManager indexManager) {
        super();
        this.musicFolderService = musicFolderService;
        this.securityService = securityService;
        this.playerService = playerService;
        this.mediaFileService = mediaFileService;
        this.indexManager = indexManager;
    }

    @SuppressWarnings("PMD.NullAssignment")
    /*
     * (genre, lastPlayed, format) Intentional assignment in the case of receiving a
     * param indicating no condition value.
     */
    @PostMapping
    protected String handleRandomPlayQueue(ModelMap model, HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(Attributes.Request.NameConstants.SIZE) final Integer sizeParam,
            @RequestParam(value = Attributes.Request.NameConstants.GENRE, required = false) final String genreParam,
            @RequestParam(value = Attributes.Request.NameConstants.YEAR, required = false) String yearParam,
            @RequestParam(value = Attributes.Request.NameConstants.SONG_RATING, required = false) String songRating,
            @RequestParam(value = Attributes.Request.NameConstants.LAST_PLAYED_VALUE, required = true) String lastPlayedValue,
            @RequestParam(value = Attributes.Request.NameConstants.LAST_PLAYED_COMP, required = true) String lastPlayedComp,
            @RequestParam(value = Attributes.Request.NameConstants.ALBUM_RATING_VALUE, required = false) Integer albumRatingValue,
            @RequestParam(value = Attributes.Request.NameConstants.ALBUM_RATING_COMP, required = false) String albumRatingComp,
            @RequestParam(value = Attributes.Request.NameConstants.PLAY_COUNT_VALUE, required = false) Integer playCountValue,
            @RequestParam(value = Attributes.Request.NameConstants.PLAY_COUNT_COMP, required = false) String playCountComp,
            @RequestParam(value = Attributes.Request.NameConstants.FORMAT, required = false) final String formatParam,
            @RequestParam(value = Attributes.Request.NameConstants.AUTO_RANDOM, required = false) String autoRandom)
            throws ServletRequestBindingException {

        // Parse request parameters. All of these results are params to criteria.
        int size = sizeParam == null ? 24 : sizeParam;
        List<MusicFolder> musicFolders = getMusicFolders(request);
        LastPlayed lastPlayed = getLastPlayed(lastPlayedValue, lastPlayedComp);
        String genre = StringUtils.equalsIgnoreCase(REQUEST_VALUE_ANY, genreParam) ? null
                : genreParam;
        List<String> genres = parseGenre(genre);
        InceptionYear year = getInceptionYear(yearParam);
        AlbumRating albumRating = getAlbumRating(albumRatingValue, albumRatingComp);
        PlayCount playCount = getPlayCount(playCountValue, playCountComp);
        SongRating rating = getSongRating(songRating);
        String format = StringUtils.equalsIgnoreCase(formatParam, REQUEST_VALUE_ANY) ? null
                : formatParam;

        // Create instance of Criteria from parsed request parameters
        RandomSearchCriteria criteria = new RandomSearchCriteria(size, genres, year.getFromYear(),
                year.getToYear(), musicFolders, lastPlayed.getMinLastPlayedDate(),
                lastPlayed.getMaxLastPlayedDate(), albumRating.getMinAlbumRating(),
                albumRating.getMaxAlbumRating(), playCount.getMinPlayCount(),
                playCount.getMaxPlayCount(), rating.isDoesShowStarredSongs(),
                rating.isDoesShowUnstarredSongs(), format);

        User user = securityService.getCurrentUserStrict(request);
        Player player = playerService.getPlayer(request, response);
        PlayQueue playQueue = player.getPlayQueue();
        // Do we add to the current playlist or do we replace it?
        boolean shouldAddToPlayList = request
            .getParameter(Attributes.Request.ADD_TO_PLAYLIST.value()) != null;

        playQueue
            .addFiles(shouldAddToPlayList,
                    mediaFileService.getRandomSongs(criteria, user.getUsername()));
        if (autoRandom != null) {
            playQueue.setRandomSearchCriteria(criteria);
            playQueue.setInternetRadio(null);
        }

        // Render the 'reload' view to reload the play queue and the main page
        List<ReloadFrame> reloadFrames = new ArrayList<>();
        reloadFrames.add(new ReloadFrame("playQueue", ViewName.PLAY_QUEUE.value() + "?"));
        reloadFrames.add(new ReloadFrame("upper", ViewName.TOP.value() + "?"));
        Map<String, Object> map = LegacyMap.of("reloadFrames", reloadFrames);
        model.addAttribute("model", map);
        return "reload";
    }

    private InceptionYear getInceptionYear(String year) {
        Integer fromYear = null;
        Integer toYear = null;
        if (!StringUtils.equalsIgnoreCase(REQUEST_VALUE_ANY, year)) {
            String[] tmp = StringUtils.split(year);
            fromYear = Integer.parseInt(tmp[0]);
            toYear = Integer.parseInt(tmp[1]);
        }
        return new InceptionYear(fromYear, toYear);
    }

    private static class InceptionYear {
        private final Integer fromYear;
        private final Integer toYear;

        public InceptionYear(Integer fromYear, Integer toYear) {
            super();
            this.fromYear = fromYear;
            this.toYear = toYear;
        }

        public Integer getFromYear() {
            return fromYear;
        }

        public Integer getToYear() {
            return toYear;
        }
    }

    @SuppressWarnings("PMD.NullAssignment")
    /*
     * (lastPlayed) Intentional assignment in the case of receiving a param
     * indicating no condition value.
     */
    @NonNull
    LastPlayed getLastPlayed(@NonNull String lastPlayedValue, @NonNull String lastPlayedComp) {
        Instant minLastPlayedDate = null;
        Instant maxLastPlayedDate = null;
        // Handle the last played date filter
        Instant lastPlayed = now();
        switch (lastPlayedValue) {
        case REQUEST_VALUE_ANY:
            lastPlayed = null;
            break;
        case "1day":
            lastPlayed = lastPlayed.minus(1, ChronoUnit.DAYS);
            break;
        case "1week":
            lastPlayed = lastPlayed.minus(7, ChronoUnit.DAYS);
            break;
        case "1month":
            lastPlayed = lastPlayed.minus(30, ChronoUnit.DAYS);
            break;
        case "3months":
            lastPlayed = lastPlayed.minus(90, ChronoUnit.DAYS);
            break;
        case "6months":
            lastPlayed = lastPlayed.minus(180, ChronoUnit.DAYS);
            break;
        case "1year":
            lastPlayed = lastPlayed.minus(365, ChronoUnit.DAYS);
            break;
        default:
            // none
            break;
        }
        if (lastPlayed != null) {
            switch (lastPlayedComp) {
            case "lt":
                maxLastPlayedDate = lastPlayed;
                break;
            case "gt":
                minLastPlayedDate = lastPlayed;
                break;
            default:
                // none
                break;
            }
        }
        return new LastPlayed(minLastPlayedDate, maxLastPlayedDate);
    }

    static class LastPlayed {
        private final Instant minLastPlayedDate;
        private final Instant maxLastPlayedDate;

        public LastPlayed(@Nullable Instant minLastPlayedDate,
                @Nullable Instant maxLastPlayedDate) {
            super();
            this.minLastPlayedDate = minLastPlayedDate;
            this.maxLastPlayedDate = maxLastPlayedDate;
        }

        public Instant getMinLastPlayedDate() {
            return minLastPlayedDate;
        }

        public Instant getMaxLastPlayedDate() {
            return maxLastPlayedDate;
        }
    }

    private AlbumRating getAlbumRating(Integer albumRatingValue, String albumRatingComp) {
        Integer minAlbumRating = null;
        Integer maxAlbumRating = null;
        if (albumRatingValue != null) {
            switch (albumRatingComp) { // nullable
            case "lt":
                maxAlbumRating = albumRatingValue - 1;
                break;
            case "gt":
                minAlbumRating = albumRatingValue + 1;
                break;
            case "le":
                maxAlbumRating = albumRatingValue;
                break;
            case "ge":
                minAlbumRating = albumRatingValue;
                break;
            case "eq":
                minAlbumRating = albumRatingValue;
                maxAlbumRating = albumRatingValue;
                break;
            default:
                // none
                break;
            }
        }
        return new AlbumRating(minAlbumRating, maxAlbumRating);
    }

    private static class AlbumRating {
        private final Integer minAlbumRating;
        private final Integer maxAlbumRating;

        public AlbumRating(Integer minAlbumRating, Integer maxAlbumRating) {
            super();
            this.minAlbumRating = minAlbumRating;
            this.maxAlbumRating = maxAlbumRating;
        }

        public Integer getMinAlbumRating() {
            return minAlbumRating;
        }

        public Integer getMaxAlbumRating() {
            return maxAlbumRating;
        }
    }

    private PlayCount getPlayCount(Integer playCountValue, String playCountComp) {
        Integer minPlayCount = null;
        Integer maxPlayCount = null;
        if (playCountValue != null) {
            switch (playCountComp) { // nullable
            case "lt":
                maxPlayCount = playCountValue - 1;
                break;
            case "gt":
                minPlayCount = playCountValue + 1;
                break;
            case "le":
                maxPlayCount = playCountValue;
                break;
            case "ge":
                minPlayCount = playCountValue;
                break;
            case "eq":
                minPlayCount = playCountValue;
                maxPlayCount = playCountValue;
                break;
            default:
                // none
                break;
            }
        }
        return new PlayCount(minPlayCount, maxPlayCount);
    }

    private static class PlayCount {
        private final Integer minPlayCount;
        private final Integer maxPlayCount;

        public PlayCount(Integer minPlayCount, Integer maxPlayCount) {
            super();
            this.minPlayCount = minPlayCount;
            this.maxPlayCount = maxPlayCount;
        }

        public Integer getMinPlayCount() {
            return minPlayCount;
        }

        public Integer getMaxPlayCount() {
            return maxPlayCount;
        }

    }

    private SongRating getSongRating(String songRating) {
        boolean doesShowStarredSongs = false;
        boolean doesShowUnstarredSongs = false;
        if (StringUtils.equalsIgnoreCase(REQUEST_VALUE_ANY, songRating)) {
            doesShowStarredSongs = true;
            doesShowUnstarredSongs = true;
        } else if (StringUtils.equalsIgnoreCase("starred", songRating)) {
            doesShowStarredSongs = true;
        } else if (StringUtils.equalsIgnoreCase("unstarred", songRating)) {
            doesShowUnstarredSongs = true;
        }
        return new SongRating(doesShowStarredSongs, doesShowUnstarredSongs);
    }

    private static class SongRating {
        private final boolean doesShowStarredSongs;
        private final boolean doesShowUnstarredSongs;

        public SongRating(boolean doesShowStarredSongs, boolean doesShowUnstarredSongs) {
            super();
            this.doesShowStarredSongs = doesShowStarredSongs;
            this.doesShowUnstarredSongs = doesShowUnstarredSongs;
        }

        public boolean isDoesShowStarredSongs() {
            return doesShowStarredSongs;
        }

        public boolean isDoesShowUnstarredSongs() {
            return doesShowUnstarredSongs;
        }
    }

    private List<String> parseGenre(String genre) {
        List<String> genres = null;
        if (null != genre) {
            List<String> preAnalyzeds = indexManager
                .toPreAnalyzedGenres(Arrays.asList(genre), true);
            if (preAnalyzeds.isEmpty()) {
                // #267 Invalid search for genre containing specific string
                if (LOG.isWarnEnabled()) {
                    LOG
                        .warn("Could not find the specified genre. A forbidden string such as \"double quotes\" may be used.");
                }
            } else {
                genres = preAnalyzeds;
            }
        }
        return genres;
    }

    @SuppressWarnings("PMD.NullAssignment") // (selectedMusicFolderId) Intentional assignment in the
                                            // case of receiving a
                                            // param indicating no condition value.
    private List<MusicFolder> getMusicFolders(HttpServletRequest request)
            throws ServletRequestBindingException {
        String username = securityService.getCurrentUsernameStrict(request);
        Integer selectedMusicFolderId = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.MUSIC_FOLDER_ID.value());
        if (selectedMusicFolderId == -1) {
            selectedMusicFolderId = null;
        }
        return musicFolderService.getMusicFoldersForUser(username, selectedMusicFolderId);
    }
}

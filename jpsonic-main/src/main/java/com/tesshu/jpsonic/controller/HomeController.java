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

import static org.springframework.web.bind.ServletRequestUtils.getIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getStringParameter;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.tesshu.jpsonic.domain.model.Genre;
import com.tesshu.jpsonic.domain.model.MusicFolderContent;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicIndexProvider;
import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.domain.system.AlbumListType;
import com.tesshu.jpsonic.domain.system.CoverArtScheme;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.search.LegacySearch;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.RatingService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Controller for the home page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/home", "/home.view" })
public class HomeController {

    private static final int LIST_SIZE = 40;

    private final SettingsFacade settingsFacade;
    private final UserService userService;
    private final MusicFolderService musicFolderService;
    private final ScannerStateProvider scannerStateProvider;
    private final RatingService ratingService;
    private final MediaFileService mediaFileService;
    private final LegacySearch legacySearch;
    private final GenreMasterProvider genreMasterProvider;
    private final MusicFolderProvider musicFolderProvider;
    private final MusicIndexProvider musicIndexProvider;

    public HomeController(SettingsFacade settingsFacade, UserService userService,
            MusicFolderService musicFolderService, ScannerStateProvider scannerStateProvider,
            RatingService ratingService, MediaFileService mediaFileService,
            LegacySearch legacySearch, GenreMasterProvider genreMasterProvider,
            MusicFolderProvider musicFolderProvider, MusicIndexProvider musicIndexProvider) {
        super();
        this.settingsFacade = settingsFacade;
        this.userService = userService;
        this.musicFolderService = musicFolderService;
        this.scannerStateProvider = scannerStateProvider;
        this.ratingService = ratingService;
        this.mediaFileService = mediaFileService;
        this.legacySearch = legacySearch;
        this.genreMasterProvider = genreMasterProvider;
        this.musicFolderProvider = musicFolderProvider;
        this.musicIndexProvider = musicIndexProvider;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request)
            throws ServletRequestBindingException {

        User user = userService.getCurrentUserStrict(request);
        if (user.isAdminRole() && settingsFacade.get(SKeys.general.welcome.gettingStartedEnabled)) {
            return new ModelAndView(new RedirectView(ViewName.GETTING_STARTED.value()));
        }
        int listOffset = getIntParameter(request, Attributes.Request.LIST_OFFSET.value(), 0);
        AlbumListType listType = AlbumListType
            .fromId(getStringParameter(request, Attributes.Request.LIST_TYPE.value()));
        UserSettings userSettings = userService.getUserSettings(user.getUsername());
        if (listType == null) {
            listType = userSettings.getDefaultAlbumList();
        }

        MusicFolder selectedMusicFolder = userService.getSelectedMusicFolder(user.getUsername());
        List<MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername(),
                    selectedMusicFolder == null ? null : selectedMusicFolder.getId());

        Map<String, Object> map = LegacyMap.of();
        List<Album> albums = Collections.emptyList();
        switch (listType) {
        case HIGHEST:
            albums = getHighestRated(listOffset, LIST_SIZE, musicFolders);
            break;
        case FREQUENT:
            albums = getMostFrequent(listOffset, LIST_SIZE, musicFolders);
            break;
        case RECENT:
            albums = getMostRecent(listOffset, LIST_SIZE, musicFolders);
            break;
        case NEWEST:
            albums = getNewest(listOffset, LIST_SIZE, musicFolders);
            break;
        case STARRED:
            albums = getStarred(listOffset, LIST_SIZE, user.getUsername(), musicFolders);
            break;
        case RANDOM:
            albums = getRandom(LIST_SIZE, musicFolders);
            break;
        case ALPHABETICAL:
            albums = getAlphabetical(listOffset, LIST_SIZE, true, musicFolders);
            break;
        case DECADE:
            List<Integer> decades = createDecades();
            map.put("decades", decades);
            int decade = getIntParameter(request, Attributes.Request.DECADE.value(),
                    decades.get(0));
            map.put("decade", decade);
            albums = getByYear(listOffset, LIST_SIZE, decade, decade + 9, musicFolders);
            break;
        case GENRE:
            List<Genre> genres = genreMasterProvider.getLegacyGenres(true, 0, Integer.MAX_VALUE);
            map.put("genres", genres);
            if (!genres.isEmpty()) {
                String genre = getStringParameter(request, Attributes.Request.GENRE.value(),
                        genres.get(0).name());
                map.put("genre", genre);
                albums = getByGenre(listOffset, LIST_SIZE, genre, musicFolders);
            }
            break;
        case INDEX:
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders = musicFolders
                .stream()
                .map(MusicFolder::getId)
                .map(musicFolderProvider::requireMusicFolder)
                .toList();
            MusicFolderContent musicFolderContent = musicIndexProvider
                .findMusicFolderContent(folders);
            map.put("indexedArtists", musicFolderContent.indexedArtists());
            map.put("singleSongs", musicFolderContent.singleSongs());
            map.put("indexes", musicFolderContent.indexedArtists().keySet());
            map.put("isOpenDetailIndex", userSettings.isOpenDetailIndex());
            map.put("assignAccesskeyToNumber", userSettings.isAssignAccesskeyToNumber());
            break;
        }

        map.put("user", user);
        map.put("partyMode", userSettings.isPartyModeEnabled());
        map.put("downloadRole", user.isDownloadRole());
        map.put("showDownload", userSettings.isShowDownload());

        map.put("albums", albums);
        map.put("welcomeTitle", settingsFacade.get(SKeys.general.welcome.title));
        map.put("welcomeSubtitle", settingsFacade.get(SKeys.general.welcome.subtitle));
        map.put("welcomeMessage", settingsFacade.get(SKeys.general.welcome.message));
        map.put("isIndexBeingCreated", scannerStateProvider.isScanning());
        map.put("musicFoldersExist", !musicFolderService.getAllMusicFolders().isEmpty());
        map.put("listType", listType.getId());
        map.put("listSize", LIST_SIZE);
        map.put("coverArtSize", CoverArtScheme.MEDIUM.getSize());
        map.put("listOffset", listOffset);
        map.put("musicFolder", selectedMusicFolder);
        map.put("showRate", userSettings.isShowRate());
        return new ModelAndView("home", "model", map);
    }

    private List<Album> getHighestRated(int offset, int count, List<MusicFolder> musicFolders) {
        List<Album> result = new ArrayList<>();
        for (MediaFile mediaFile : ratingService
            .getHighestRatedAlbums(offset, count, musicFolders)) {
            Album album = createAlbum(mediaFile);
            album.setRating((int) Math.round(ratingService.getAverageRating(mediaFile) * 10.0D));
            result.add(album);
        }
        return result;
    }

    private List<Album> getMostFrequent(int offset, int count, List<MusicFolder> musicFolders) {
        List<Album> result = new ArrayList<>();
        for (MediaFile mediaFile : mediaFileService
            .getMostFrequentlyPlayedAlbums(offset, count, musicFolders)) {
            Album album = createAlbum(mediaFile);
            album.setPlayCount(mediaFile.getPlayCount());
            result.add(album);
        }
        return result;
    }

    private List<Album> getMostRecent(int offset, int count, List<MusicFolder> musicFolders) {
        List<Album> result = new ArrayList<>();
        for (MediaFile mediaFile : mediaFileService
            .getMostRecentlyPlayedAlbums(offset, count, musicFolders)) {
            Album album = createAlbum(mediaFile);
            if (mediaFile.getLastPlayed() != null) {
                album
                    .setLastPlayed(ZonedDateTime
                        .ofInstant(mediaFile.getLastPlayed(), ZoneId.systemDefault()));
            }
            result.add(album);
        }
        return result;
    }

    private List<Album> getNewest(int offset, int count, List<MusicFolder> musicFolders) {
        List<Album> result = new ArrayList<>();
        for (MediaFile file : mediaFileService.getNewestAlbums(offset, count, musicFolders)) {
            Album album = createAlbum(file);
            album.setCreated(ZonedDateTime.ofInstant(file.getCreated(), ZoneId.systemDefault()));
            result.add(album);
        }
        return result;
    }

    private List<Album> getStarred(int offset, int count, String username,
            List<MusicFolder> musicFolders) {
        List<Album> result = new ArrayList<>();
        for (MediaFile file : mediaFileService
            .getStarredAlbums(offset, count, username, musicFolders)) {
            result.add(createAlbum(file));
        }
        return result;
    }

    private List<Album> getRandom(int count, List<MusicFolder> musicFolders) {
        List<Album> result = new ArrayList<>();
        for (MediaFile file : legacySearch.getRandomAlbums(count, musicFolders)) {
            result.add(createAlbum(file));
        }
        return result;
    }

    private List<Album> getAlphabetical(int offset, int count, boolean byArtist,
            List<MusicFolder> musicFolders) {
        List<Album> result = new ArrayList<>();
        for (MediaFile file : mediaFileService
            .getAlphabeticalAlbums(offset, count, byArtist, musicFolders)) {
            result.add(createAlbum(file));
        }
        return result;
    }

    private List<Album> getByYear(int offset, int count, int fromYear, int toYear,
            List<MusicFolder> musicFolders) {
        List<Album> result = new ArrayList<>();
        for (MediaFile file : mediaFileService
            .getAlbumsByYear(offset, count, fromYear, toYear, musicFolders)) {
            Album album = createAlbum(file);
            album.setYear(file.getYear());
            result.add(album);
        }
        return result;
    }

    private List<Integer> createDecades() {
        List<Integer> result = new ArrayList<>();
        int decade = Calendar.getInstance().get(Calendar.YEAR) / 10;
        for (int i = 0; i < 10; i++) {
            result.add((decade - i) * 10);
        }
        return result;
    }

    private List<Album> getByGenre(int offset, int count, String genre,
            List<MusicFolder> musicFolders) {
        List<Album> result = new ArrayList<>();
        for (MediaFile file : legacySearch.getAlbumsByGenres(genre, offset, count, musicFolders)) {
            result.add(createAlbum(file));
        }
        return result;
    }

    private Album createAlbum(MediaFile file) {
        Album album = new Album();
        album.setId(file.getId());
        album.setPath(file.getPathString());
        album.setArtist(file.getArtist());
        album.setAlbumTitle(file.getAlbumName());
        album.setCoverArtPath(file.getCoverArtPathString());
        return album;
    }

    /**
     * Contains info for a single album.
     */
    public static class Album {
        private String path;
        private String coverArtPath;
        private String artist;
        private String albumTitle;
        private ZonedDateTime created;
        private ZonedDateTime lastPlayed;
        private Integer playCount;
        private Integer rating;
        private int id;
        private Integer year;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getCoverArtPath() {
            return coverArtPath;
        }

        public void setCoverArtPath(String coverArtPath) {
            this.coverArtPath = coverArtPath;
        }

        public String getArtist() {
            return artist;
        }

        public void setArtist(String artist) {
            this.artist = artist;
        }

        public String getAlbumTitle() {
            return albumTitle;
        }

        public void setAlbumTitle(String albumTitle) {
            this.albumTitle = albumTitle;
        }

        public @NonNull ZonedDateTime getCreated() {
            return created;
        }

        public void setCreated(ZonedDateTime created) {
            this.created = created;
        }

        public @Nullable ZonedDateTime getLastPlayed() {
            return lastPlayed;
        }

        public void setLastPlayed(ZonedDateTime lastPlayed) {
            this.lastPlayed = lastPlayed;
        }

        public Integer getPlayCount() {
            return playCount;
        }

        public void setPlayCount(Integer playCount) {
            this.playCount = playCount;
        }

        public Integer getRating() {
            return rating;
        }

        public void setRating(Integer rating) {
            this.rating = rating;
        }

        public void setYear(Integer year) {
            this.year = year;
        }

        public Integer getYear() {
            return year;
        }
    }
}

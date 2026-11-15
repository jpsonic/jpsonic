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

import static com.tesshu.jpsonic.util.PlayerUtils.FAR_PAST;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.domain.system.CoverArtScheme;
import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.RatingService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Controller for the main page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/main.view")
public class MainController {

    private final SettingsFacade settingsFacade;
    private final LibraryAccessPolicy libraryAccessPolicy;
    private final UserService userService;
    private final JpsonicComparators jpsonicComparator;
    private final RatingService ratingService;
    private final MediaFileService mediaFileService;
    private final ViewAsListSelector viewSelector;

    public MainController(SettingsFacade settingsFacade, LibraryAccessPolicy libraryAccessPolicy,
            UserService userService, JpsonicComparators jpsonicComparator,
            RatingService ratingService, MediaFileService mediaFileService,
            ViewAsListSelector viewSelector) {
        super();
        this.settingsFacade = settingsFacade;
        this.libraryAccessPolicy = libraryAccessPolicy;
        this.userService = userService;
        this.jpsonicComparator = jpsonicComparator;
        this.ratingService = ratingService;
        this.mediaFileService = mediaFileService;
        this.viewSelector = viewSelector;
    }

    @GetMapping
    protected ModelAndView get(HttpServletRequest request, HttpServletResponse response,
            @RequestParam(name = Attributes.Request.NameConstants.SHOW_ALL, required = false) Boolean showAll)
            throws IOException {

        List<MediaFile> mediaFiles = getMediaFiles(request);
        if (mediaFiles.isEmpty()) {
            return new ModelAndView(new RedirectView(ViewName.NOTFOUND.value()));
        }

        MediaFile dir = mediaFiles.get(0);
        if (dir.isFile()) {
            dir = mediaFileService.getParentOf(dir);
        }
        if (mediaFileService.isRoot(dir)) {
            return new ModelAndView(new RedirectView(ViewName.HOME.value() + "?"));
        }

        final String username = userService.getCurrentUsernameStrict(request);
        if (!libraryAccessPolicy.isFolderAccessAllowed(dir, username)) {
            return new ModelAndView(new RedirectView(ViewName.ACCESS_DENIED.value()));
        }

        Map<String, Object> map = LegacyMap.of();

        // dir
        mediaFileService.populateStarredDate(dir, username);
        map.put("dir", dir);
        putLastPlayed(map, dir);
        map.put("ancestors", getAncestors(dir));
        map.put("userRating", getUserRating(username, dir));
        map.put("averageRating", getAverageRating(dir));
        map.put("starred", mediaFileService.getMediaFileStarredDate(dir.getId(), username) != null);
        if (!libraryAccessPolicy.isInPodcastFolder(dir.toPath())) {
            MediaFile parent = mediaFileService.getParentOf(dir);
            map.put("parent", parent);
            map.put("navigateUpAllowed", !mediaFileService.isRoot(parent));
        }
        map.put("scanForcable", !FAR_PAST.equals(dir.getLastScanned()));

        // children
        List<MediaFile> children = mediaFiles.size() == 1 // children
                ? mediaFileService.getChildrenOf(dir, true, true) // expected code
                : getMultiFolderChildren(mediaFiles); // Suspicion of dead code
        mediaFileService.populateStarredDate(children, username);
        map.put("files", children.stream().filter(MediaFile::isFile).collect(Collectors.toList()));

        // subDirs
        final List<MediaFile> subDirs = children
            .stream()
            .filter(f -> !f.isFile())
            .collect(Collectors.toList());
        map.put("subDirs", subDirs);

        final UserSettings userSettings = userService.getUserSettings(username);
        final int userPaginationPreference = userSettings.getPaginationSize();
        boolean thereIsMoreSiblingAlbums = false;
        final boolean isShowAll = userPaginationPreference <= 0 || null != showAll && showAll;
        if (dir.isAlbum()) {
            if (userSettings.isShowSibling()) {
                List<MediaFile> siblingAlbums = getSiblingAlbums(dir);
                thereIsMoreSiblingAlbums = trimToSize(isShowAll, siblingAlbums,
                        userPaginationPreference);
                map.put("siblingAlbums", siblingAlbums);
            }
            map.put("artist", guessArtist(children));
            map.put("album", guessAlbum(children));
            map.put("musicBrainzReleaseId", guessMusicBrainzReleaseId(children));
        }
        map
            .put("thereIsMore", getThereIsMore(thereIsMoreSiblingAlbums, isShowAll, subDirs,
                    userPaginationPreference));

        // others
        map.put("user", userService.getCurrentUserStrict(request));
        map.put("selectedMusicFolder", userService.getSelectedMusicFolder(username));
        map.put("viewAsList", viewSelector.isViewAsList(request, userSettings.getUsername()));

        map.put("visibility", userSettings.getMainVisibility());
        map.put("partyMode", userSettings.isPartyModeEnabled());
        map.put("simpleDisplay", userSettings.isSimpleDisplay());

        map.put("showAlbumYear", settingsFacade.get(SKeys.general.sort.albumsByYear));
        map.put("showArtistInfo", userSettings.isShowArtistInfoEnabled());
        map.put("showTopSongs", userSettings.isShowTopSongs());
        map.put("showSimilar", userSettings.isShowSimilar());
        map.put("showDownload", userSettings.isShowDownload());
        map.put("showTag", userSettings.isShowTag());
        map.put("showChangeCoverArt", userSettings.isShowChangeCoverArt());
        map.put("showComment", userSettings.isShowComment());
        map.put("showShare", userSettings.isShowShare());
        map.put("showRate", userSettings.isShowRate());
        map.put("showAlbumSearch", userSettings.isShowAlbumSearch());
        map.put("showLastPlay", userSettings.isShowLastPlay());
        map.put("showSibling", userSettings.isShowSibling());
        map.put("showAlbumActions", userSettings.isShowAlbumActions());
        map.put("useRadio", settingsFacade.get(SKeys.general.legacy.useRadio));

        map.put("brand", EnvironmentProvider.getInstance().getBrand());
        map.put("coverArtSizeMedium", CoverArtScheme.MEDIUM.getSize());
        map.put("coverArtSizeLarge", CoverArtScheme.LARGE.getSize());
        map.put("breadcrumbIndex", userSettings.isBreadcrumbIndex());

        return new ModelAndView(getTargetView(dir, children), "model", map);
    }

    private void putLastPlayed(Map<String, Object> map, MediaFile dir) {
        if (dir.getLastPlayed() != null) {
            map
                .put("lastPlayed",
                        ZonedDateTime.ofInstant(dir.getLastPlayed(), ZoneId.systemDefault()));
        }
    }

    private boolean getThereIsMore(boolean thereIsMoreSiblingAlbums, boolean isShowAll,
            List<MediaFile> subDirs, int userPaginationPreference) {
        boolean thereIsMoreSubDirs = trimToSize(isShowAll, subDirs, userPaginationPreference);
        return (thereIsMoreSubDirs || thereIsMoreSiblingAlbums) && !isShowAll;
    }

    private Integer getUserRating(String username, MediaFile dir) {
        Integer userRating = ratingService.getRatingForUser(username, dir);
        if (userRating == null) {
            userRating = 0;
        }
        return 10 * userRating;
    }

    private long getAverageRating(MediaFile dir) {
        Double averageRating = ratingService.getAverageRating(dir);
        if (averageRating == null) {
            averageRating = 0.0D;
        }
        return Math.round(10.0D * averageRating);
    }

    private String getTargetView(MediaFile dir, List<MediaFile> children) {
        String view;
        if (isVideoOnly(children)) {
            view = "videoMain";
        } else if (dir.isAlbum()) {
            view = "albumMain";
        } else {
            view = "artistMain";
        }
        return view;
    }

    private <T> boolean trimToSize(Boolean showAll, List<T> list, int userPaginationPreference) {
        boolean trimmed = false;
        if (!BooleanUtils.isTrue(showAll) && list.size() > userPaginationPreference) {
            trimmed = true;
            list.subList(userPaginationPreference, list.size()).clear();
        }
        return trimmed;
    }

    private boolean isVideoOnly(List<MediaFile> children) {
        boolean videoFound = false;
        for (MediaFile child : children) {
            if (child.isAudio()) {
                return false;
            }
            if (child.isVideo()) {
                videoFound = true;
            }
        }
        return videoFound;
    }

    private List<MediaFile> getMediaFiles(HttpServletRequest request) {
        List<MediaFile> mediaFiles = new ArrayList<>();
        for (String path : ServletRequestUtils
            .getStringParameters(request, Attributes.Request.PATH.value())) {
            MediaFile mediaFile = mediaFileService.getMediaFile(path);
            if (mediaFile != null) {
                mediaFiles.add(mediaFile);
            }
        }
        for (int id : ServletRequestUtils
            .getIntParameters(request, Attributes.Request.ID.value())) {
            MediaFile mediaFile = mediaFileService.getMediaFile(id);
            if (mediaFile != null) {
                mediaFiles.add(mediaFile);
            }
        }
        return mediaFiles;
    }

    private String guessArtist(List<MediaFile> children) {
        for (MediaFile child : children) {
            if (child.isFile() && child.getArtist() != null) {
                return child.getArtist();
            }
        }
        return null;
    }

    private String guessAlbum(List<MediaFile> children) {
        for (MediaFile child : children) {
            if (child.isFile() && child.getArtist() != null) {
                return child.getAlbumName();
            }
        }
        return null;
    }

    private String guessMusicBrainzReleaseId(List<MediaFile> children) {
        for (MediaFile child : children) {
            if (child.isFile() && child.getMusicBrainzReleaseId() != null) {
                return child.getMusicBrainzReleaseId();
            }
        }
        return null;
    }

    public List<MediaFile> getMultiFolderChildren(List<MediaFile> mediaFiles) throws IOException {
        SortedSet<MediaFile> result = new TreeSet<>(jpsonicComparator.mediaFileOrder(null));
        for (MediaFile mediaFile : mediaFiles) {
            MediaFile m = mediaFile;
            if (m.isFile()) {
                MediaFile parent = mediaFileService.getParentOf(m);
                if (parent != null) {
                    m = parent;
                }
            }
            result.addAll(mediaFileService.getChildrenOf(m, true, true));
        }
        return new ArrayList<>(result);
    }

    private List<MediaFile> getAncestors(MediaFile dir) {
        List<MediaFile> result = new ArrayList<>();
        if (libraryAccessPolicy.isInPodcastFolder(dir.toPath())) {
            // For podcasts, don't use ancestors
            return result;
        }

        MediaFile parent = mediaFileService.getParentOf(dir);
        while (parent != null && !mediaFileService.isRoot(parent)) {
            result.add(parent);
            parent = mediaFileService.getParentOf(parent);
        }
        return result;
    }

    private List<MediaFile> getSiblingAlbums(MediaFile dir) {
        List<MediaFile> result = new ArrayList<>();

        MediaFile parent = mediaFileService.getParentOf(dir);
        if (!mediaFileService.isRoot(parent)) {
            List<MediaFile> siblings = mediaFileService.getChildrenOf(parent, false, true);
            result
                .addAll(siblings
                    .stream()
                    .filter(sibling -> sibling.isAlbum() && !sibling.equals(dir))
                    .collect(Collectors.toList()));
        }
        return result;
    }

}

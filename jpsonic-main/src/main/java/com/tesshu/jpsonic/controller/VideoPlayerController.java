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

import java.util.LinkedHashMap;
import java.util.Map;

import com.tesshu.jpsonic.SuppressLint;
import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.NetworkUtils;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.UserService;
import com.tesshu.jpsonic.util.StringUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Controller for the page used to play videos.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/videoPlayer", "/videoPlayer.view" })
public class VideoPlayerController {

    public static final int DEFAULT_BIT_RATE = 2000;
    private static final int[] BIT_RATES = { 200, 300, 400, 500, 700, 1000, 1200, 1500, 2000, 3000,
            5000 };

    private final SettingsFacade settingsFacade;
    private final LibraryAccessPolicy libraryAccessPolicy;
    private final UserService userService;
    private final MediaFileService mediaFileService;
    private final PlayerService playerService;

    public VideoPlayerController(SettingsFacade settingsFacade,
            LibraryAccessPolicy libraryAccessPolicy, UserService userService,
            MediaFileService mediaFileService, PlayerService playerService) {
        super();
        this.settingsFacade = settingsFacade;
        this.libraryAccessPolicy = libraryAccessPolicy;
        this.userService = userService;
        this.mediaFileService = mediaFileService;
        this.playerService = playerService;
    }

    @GetMapping
    @SuppressLint(value = "CROSS_SITE_SCRIPTING", justification = "False positive. Header value is used to make Proxy transparent.")
    protected ModelAndView handleRequestInternal(HttpServletRequest request,
            HttpServletResponse response) throws ServletRequestBindingException {

        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        MediaFile file = mediaFileService.getMediaFileStrict(id);
        MediaFile parentDir = mediaFileService.getParentOf(file);

        String username = userService.getCurrentUsernameStrict(request);
        if (parentDir != null && !libraryAccessPolicy.isFolderAccessAllowed(parentDir, username)) {
            return new ModelAndView(new RedirectView(ViewName.ACCESS_DENIED.value()));
        }

        User user = userService.getCurrentUserStrict(request);
        mediaFileService.populateStarredDate(file, user.getUsername());
        final Integer duration = file.getDurationSeconds();
        Integer playerId = playerService.getPlayer(request, response).getId();
        String url = NetworkUtils.getBaseUrl(request);
        final String streamUrl = url + "stream?id=" + file.getId() + "&player=" + playerId
                + "&format=mp4";
        String coverArtUrl = url + ViewName.COVER_ART.value() + "?id=" + file.getId();
        UserSettings userSettings = userService.getUserSettings(user.getUsername());

        Map<String, Object> map = LegacyMap.of();
        map.put("dir", parentDir);
        map.put("breadcrumbIndex", userSettings.isBreadcrumbIndex());
        map.put("selectedMusicFolder", userService.getSelectedMusicFolder(user.getUsername()));
        map.put("video", file);
        map.put("streamUrl", streamUrl);
        map.put("remoteStreamUrl", streamUrl);
        map.put("remoteCoverArtUrl", coverArtUrl);
        map.put("duration", duration);
        map.put("bitRates", BIT_RATES);
        map.put("defaultBitRate", DEFAULT_BIT_RATE);
        map.put("user", user);
        map.put("isShowDownload", userSettings.isShowDownload());
        map.put("isShowShare", userSettings.isShowShare());

        if (parentDir != null && !libraryAccessPolicy.isInPodcastFolder(parentDir.toPath())) {
            MediaFile parent = mediaFileService.getParentOf(file);
            map.put("parent", parent);
            map.put("navigateUpAllowed", !mediaFileService.isRoot(parent));
        }

        map.put("useCast", settingsFacade.get(SKeys.general.legacy.useCast));

        return new ModelAndView("videoPlayer", "model", map);
    }

    /*
     * LinkedHashMap used in Legacy code. Should be triaged in #831.
     */
    public static Map<String, Integer> createSkipOffsets(int durationSeconds) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < durationSeconds; i += 60) {
            result.put(StringUtil.formatDurationMSS(i), i);
        }
        return result;
    }

}

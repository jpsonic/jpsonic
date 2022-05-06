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
 * (C) 2014 Sindre Mehus
 * (C) 2016 Airsonic Authors
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.controller;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.tesshu.jpsonic.domain.CoverArtScheme;
import com.tesshu.jpsonic.domain.Playlist;
import com.tesshu.jpsonic.domain.User;
import com.tesshu.jpsonic.service.PlaylistService;
import com.tesshu.jpsonic.service.SecurityService;
import com.tesshu.jpsonic.service.SettingsService;
import com.tesshu.jpsonic.util.LegacyMap;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for the playlists page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/playlists", "/playlists.view", })
public class PlaylistsController {

    private final SettingsService settingsService;
    private final SecurityService securityService;
    private final PlaylistService playlistService;
    private final ViewAsListSelector viewSelector;

    public PlaylistsController(SettingsService settingsService, SecurityService securityService,
            PlaylistService playlistService, ViewAsListSelector viewSelector) {
        super();
        this.settingsService = settingsService;
        this.securityService = securityService;
        this.playlistService = playlistService;
        this.viewSelector = viewSelector;
    }

    @GetMapping
    public String doGet(HttpServletRequest request, Model model) {
        User user = securityService.getCurrentUserStrict(request);
        List<Playlist> playlists = playlistService.getReadablePlaylistsForUser(user.getUsername());
        model.addAttribute("model",
                LegacyMap.of("playlists", playlists, "viewAsList",
                        viewSelector.isViewAsList(request, user.getUsername()), "coverArtSize",
                        CoverArtScheme.MEDIUM.getSize(), "publishPodcast", settingsService.isPublishPodcast()));
        return "playlists";
    }
}

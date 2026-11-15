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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.domain.system.CoverArtScheme;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.service.PlaylistService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
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

    private final UserService userService;
    private final PlaylistService playlistService;
    private final ViewAsListSelector viewSelector;

    public PlaylistsController(UserService userService, PlaylistService playlistService,
            ViewAsListSelector viewSelector) {
        super();
        this.userService = userService;
        this.playlistService = playlistService;
        this.viewSelector = viewSelector;
    }

    @GetMapping
    public String doGet(HttpServletRequest request, Model model) {
        User user = userService.getCurrentUserStrict(request);
        List<com.tesshu.jpsonic.persistence.api.entity.Playlist> playlists = playlistService
            .getReadablePlaylistsForUser(user.getUsername());
        model
            .addAttribute("model", LegacyMap
                .of("playlists", playlists.stream().map(Playlist::new).collect(Collectors.toList()),
                        "viewAsList", viewSelector.isViewAsList(request, user.getUsername()),
                        "coverArtSize", CoverArtScheme.MEDIUM.getSize()));
        return "playlists";
    }

    // VO
    public static class Playlist extends com.tesshu.jpsonic.persistence.api.entity.Playlist {

        Playlist(com.tesshu.jpsonic.persistence.api.entity.Playlist playlist) {
            super(playlist.getId(), playlist.getUsername(), playlist.isShared(), playlist.getName(),
                    playlist.getComment(), playlist.getFileCount(), playlist.getDurationSeconds(),
                    playlist.getCreated(), playlist.getChanged(), playlist.getImportedFrom());
        }

        public ZonedDateTime getCreatedDateTime() {
            return ZonedDateTime.ofInstant(getCreated(), ZoneId.systemDefault());
        }
    }
}

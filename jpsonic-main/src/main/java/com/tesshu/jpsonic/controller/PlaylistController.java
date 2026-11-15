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

import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.tesshu.jpsonic.domain.system.CoverArtScheme;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.persistence.api.entity.Playlist;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.PlaylistService;
import com.tesshu.jpsonic.service.UserService;
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
 * Controller for the playlist page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/playlist", "/playlist.view" })
public class PlaylistController {

    private final UserService userService;
    private final PlaylistService playlistService;
    private final PlayerService playerService;

    public PlaylistController(UserService userService, PlaylistService playlistService,
            PlayerService playerService) {
        super();
        this.userService = userService;
        this.playlistService = playlistService;
        this.playerService = playerService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request,
            HttpServletResponse response) throws ServletRequestBindingException {

        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        Playlist playlist = playlistService.getPlaylist(id);
        if (playlist == null) {
            return new ModelAndView(new RedirectView("notFound"));
        }

        User user = userService.getCurrentUserStrict(request);
        String username = user.getUsername();
        UserSettings userSettings = userService.getUserSettings(username);
        Player player = playerService.getPlayer(request, response);

        return new ModelAndView("playlist", "model",
                LegacyMap
                    .of("playlist", playlist, "created",
                            ZonedDateTime.ofInstant(playlist.getCreated(), ZoneId.systemDefault()),
                            "user", user, "visibility", userSettings.getMainVisibility(), "player",
                            player, "editAllowed",
                            username.equals(playlist.getUsername())
                                    || userService.isAdmin(username),
                            "coverArtSize", CoverArtScheme.LARGE.getSize(), "partyMode",
                            userSettings.isPartyModeEnabled(), "simpleDisplay",
                            userSettings.isSimpleDisplay()));
    }

}

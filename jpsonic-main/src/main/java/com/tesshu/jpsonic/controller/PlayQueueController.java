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

import com.tesshu.jpsonic.domain.system.CoverArtScheme;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for the playlist frame.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/playQueue", "/playQueue.view" })
public class PlayQueueController {

    private final SettingsFacade settingsFacade;
    private final UserService userService;
    private final PlayerService playerService;

    public PlayQueueController(SettingsFacade settingsFacade, UserService userService,
            PlayerService playerService) {
        super();
        this.settingsFacade = settingsFacade;
        this.userService = userService;
        this.playerService = playerService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request,
            HttpServletResponse response) throws ServletRequestBindingException {

        User user = userService.getCurrentUserStrict(request);
        UserSettings userSettings = userService.getUserSettings(user.getUsername());
        Player player = playerService.getPlayer(request, response);

        return new ModelAndView("playQueue", "model", LegacyMap
            .of("user", user, "player", player, "players",
                    playerService.getPlayersForUserAndClientId(user.getUsername(), null),
                    "userSettings", userSettings, "coverArtSize", CoverArtScheme.SMALL.getSize(),
                    "useCast", settingsFacade.get(SKeys.general.legacy.useRadio)));
    }
}

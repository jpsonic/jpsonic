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

import java.util.Map;

import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping({ "/index", "/index.view" })
public class IndexController {

    private final UserService userService;

    public IndexController(UserService userService) {
        super();
        this.userService = userService;
    }

    @GetMapping
    public ModelAndView get(HttpServletRequest request) {
        return new ModelAndView("index", "model", createModel(request));
    }

    @PostMapping
    public ModelAndView post(HttpServletRequest request,
            @RequestParam("mainView") String mainView) {
        Map<String, Object> model = createModel(request);
        model.put("mainView", mainView);
        return new ModelAndView("index", "model", model);
    }

    private Map<String, Object> createModel(HttpServletRequest request) {
        UserSettings userSettings = userService
            .getUserSettings(userService.getCurrentUsernameStrict(request));
        return LegacyMap
            .of("keyboardShortcutsEnabled", userSettings.isKeyboardShortcutsEnabled(), "showLeft",
                    userSettings.isCloseDrawer(), "brand",
                    EnvironmentProvider.getInstance().getBrand());
    }
}

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

import com.tesshu.jpsonic.SuppressLint;
import com.tesshu.jpsonic.domain.language.StringUtil;
import com.tesshu.jpsonic.feature.auth.rememberme.RMSKeys;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Spring MVC Controller that serves the login page.
 */
@Controller
@RequestMapping({ "/login", "/login.view" })
public class LoginController {

    private final SettingsFacade settingsFacade;
    private final UserService userService;

    public LoginController(SettingsFacade settingsFacade, UserService userService) {
        super();
        this.settingsFacade = settingsFacade;
        this.userService = userService;
    }

    @GetMapping
    @SuppressLint(value = "CROSS_SITE_SCRIPTING", justification = "No unnecessary sanitization here.")
    public ModelAndView get(HttpServletRequest request, HttpServletResponse response) {

        // Auto-login if "user" and "password" parameters are given.
        String username = request.getParameter(Attributes.Request.USER.value());
        String password = request.getParameter(Attributes.Request.PASSWORD.value());
        if (username != null && password != null) {
            username = StringUtil.urlEncode(username);
            password = StringUtil.urlEncode(password);
            return new ModelAndView(new RedirectView("/login"), Map
                .of(UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY,
                        username,
                        UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_PASSWORD_KEY,
                        password));
        }

        Map<String, Object> map = LegacyMap
            .of("logout", request.getParameter(Attributes.Request.LOGOUT.value()) != null, "error",
                    request.getParameter(Attributes.Request.ERROR.value()) != null, "brand",
                    EnvironmentProvider.getInstance().getBrand(), "loginMessage",
                    settingsFacade.get(SKeys.general.welcome.loginMessage), "rememberMeEnable",
                    settingsFacade.get(RMSKeys.enable));

        User admin = userService.getUserByName("admin");
        if (admin != null && admin.getUsername().equals(admin.getPassword())) {
            map.put("insecure", true);
        }

        return new ModelAndView("login", "model", map);
    }
}

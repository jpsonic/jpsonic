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

import java.security.SecureRandom;
import java.util.Map;

import com.tesshu.jpsonic.SuppressLint;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.service.RecoverService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Spring MVC Controller that serves the login page.
 */
@Controller
@RequestMapping({ "/recover", "/recover.view" })
public class RecoverController {

    private static final Logger LOG = LoggerFactory.getLogger(RecoverController.class);
    private static final String SYMBOLS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
    private static final int PASSWORD_LENGTH = 32;

    private final SettingsFacade settingsFacade;
    private final UserService userService;
    private final RecoverService recoverService;
    private final SecureRandom random;

    public RecoverController(SettingsFacade settingsFacade, UserService userService,
            RecoverService recoverService) {
        super();
        this.settingsFacade = settingsFacade;
        this.userService = userService;
        this.recoverService = recoverService;
        this.random = new SecureRandom();
    }

    @GetMapping
    public ModelAndView get(HttpServletRequest request) {
        Map<String, Object> map = LegacyMap.of();
        if (settingsFacade.get(SKeys.advanced.captcha.enabled)) {
            map.put("recaptchaSiteKey", settingsFacade.get(SKeys.advanced.captcha.siteKey));
        }
        return new ModelAndView("recover", "model", map);
    }

    @PostMapping
    @SuppressLint(value = "CROSS_SITE_SCRIPTING", justification = "No unnecessary sanitization here.")
    public ModelAndView post(HttpServletRequest request) {

        Map<String, Object> map = LegacyMap.of();
        String usernameOrEmail = StringUtils
            .trimToNull(request.getParameter(Attributes.Request.USERNAME_OR_EMAIL.value()));
        map.put(Attributes.Request.USERNAME_OR_EMAIL.value(), usernameOrEmail);
        User user = recoverService.getUserByUsernameOrEmail(usernameOrEmail);
        String errorMsg = validateParam(request, user);
        if (errorMsg == null) {
            StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
            for (int i = 0; i < PASSWORD_LENGTH; i++) {
                int index = random.nextInt(SYMBOLS.length());
                sb.append(SYMBOLS.charAt(index));
            }
            String newPass = sb.toString();
            if (sendEmail(user.getUsername(), user.getEmail())) {
                map.put("sentTo", user.getEmail());
                userService.updatePassword(user, newPass, false);
            } else {
                map.put(Attributes.Model.ERROR.getValue(), "recover.error.sendfailed");
            }
        } else {
            map.put(Attributes.Model.ERROR.getValue(), errorMsg);
        }

        if (settingsFacade.get(SKeys.advanced.captcha.enabled)) {
            map.put("recaptchaSiteKey", settingsFacade.get(SKeys.advanced.captcha.siteKey));
        }

        return new ModelAndView("recover", "model", map);
    }

    private String validateParam(HttpServletRequest request, User user) {

        String captchaResponseToken = request
            .getParameter(Attributes.Request.G_RECAPTCHA_RESPONSE.value());
        boolean isCaptchaFailed = !recoverService.validateCaptcha(captchaResponseToken);

        if (isCaptchaFailed) {
            return "recover.error.invalidcaptcha";
        } else if (user == null) {
            return "recover.error.usernotfound";
        } else if (user.getEmail() == null) {
            return "recover.error.noemail";
        }

        return null;
    }

    /*
     * e-mail user via configured Smtp server
     */
    private boolean sendEmail(String username, String email) {
        /* Default to protocol smtp when SmtpEncryption is set to "None" */
        String smtpServer = settingsFacade.get(SKeys.advanced.smtp.server);
        if (smtpServer == null || smtpServer.isEmpty()) {
            LOG.warn("Can not send email; no Smtp server configured.");
            return false;
        }
        return recoverService.sendEmail(username, email);
    }

}

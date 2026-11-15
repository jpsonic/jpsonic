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

import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.InternetRadio;
import com.tesshu.jpsonic.service.InternetRadioService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Controller for the page used to administrate the set of internet radio/tv
 * stations.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/internetRadioSettings", "/internetRadioSettings.view" })
public class InternetRadioSettingsController {

    private final SettingsFacade settingsFacade;
    private final InternetRadioService internetRadioService;

    public InternetRadioSettingsController(SettingsFacade settingsFacade,
            InternetRadioService internetRadioService) {
        super();
        this.settingsFacade = settingsFacade;
        this.internetRadioService = internetRadioService;
    }

    @GetMapping
    public String doGet(Model model,
            @RequestParam(Attributes.Request.NameConstants.TOAST) Optional<Boolean> toast) {
        Map<String, Object> map = LegacyMap.of();
        map.put("internetRadios", internetRadioService.getAllInternetRadios(true));
        map.put("useRadio", settingsFacade.get(SKeys.general.legacy.useRadio));
        map.put("useDatabaseSetting", settingsFacade.get(SKeys.general.legacy.useDatabaseSettings));
        toast.ifPresent(b -> map.put("showToast", b));
        model.addAttribute("model", map);
        return "internetRadioSettings";
    }

    @PostMapping
    public ModelAndView doPost(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        String error = handleParameters(request);
        if (error == null) {
            redirectAttributes.addFlashAttribute(Attributes.Redirect.RELOAD_FLAG.value(), true);
        } else {
            redirectAttributes.addFlashAttribute(Attributes.Redirect.ERROR.value(), error);
        }
        return new ModelAndView(new RedirectView(ViewName.INTERNET_RADIO_SETTINGS.value()));
    }

    private String handleParameters(HttpServletRequest request) {
        List<InternetRadio> radios = internetRadioService.getAllInternetRadios(true);
        Instant current = now();
        for (InternetRadio radio : radios) {
            String msg = updateOrDeleteInternetRadio(request, radio, current);
            if (msg != null) {
                return msg;
            }
        }

        String name = StringUtils.trimToNull(request.getParameter(Attributes.Request.NAME.value()));
        String streamUrl = StringUtils
            .trimToNull(request.getParameter(Attributes.Request.STREAM_URL.value()));
        String homepageUrl = StringUtils
            .trimToNull(request.getParameter(Attributes.Request.HOMEPAGE_URL.value()));
        boolean enabled = StringUtils
            .trimToNull(request.getParameter(Attributes.Request.ENABLED.value())) != null;

        if (name != null || streamUrl != null || homepageUrl != null) {
            if (name == null) {
                return "internetradiosettings.noname";
            }
            if (streamUrl == null) {
                return "internetradiosettings.nourl";
            }
            internetRadioService
                .createInternetRadio(
                        new InternetRadio(name, streamUrl, homepageUrl, enabled, now()));
        }

        return null;
    }

    private String updateOrDeleteInternetRadio(HttpServletRequest request, InternetRadio radio,
            Instant current) {
        Integer id = radio.getId();
        String streamUrl = getParam4Array(request, Attributes.Request.STREAM_URL.value(), id);
        String homepageUrl = getParam4Array(request, Attributes.Request.HOMEPAGE_URL.value(), id);
        String name = getParam4Array(request, Attributes.Request.NAME.value(), id);
        boolean enabled = getParam4Array(request, Attributes.Request.ENABLED.value(), id) != null;
        boolean delete = getParam4Array(request, Attributes.Request.DELETE.value(), id) != null;

        if (delete) {
            internetRadioService.deleteInternetRadio(id);
        } else {
            if (name == null) {
                return "internetradiosettings.noname";
            }
            if (streamUrl == null) {
                return "internetradiosettings.nourl";
            }
            internetRadioService
                .updateInternetRadio(
                        new InternetRadio(id, name, streamUrl, homepageUrl, enabled, current));
        }
        return null;
    }

    private String getParam4Array(HttpServletRequest request, String name, Integer id) {
        return StringUtils.trimToNull(request.getParameter(name + "[" + id + "]"));
    }

}

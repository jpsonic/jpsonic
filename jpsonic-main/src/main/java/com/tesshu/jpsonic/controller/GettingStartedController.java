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

import java.util.Locale;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.controller.form.GettingStartedCommand;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.infrastructure.locale.ServerLocaleManager;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping({ "/gettingStarted", "/gettingStarted.view" })
public class GettingStartedController {

    private final SettingsFacade settingsFacade;
    private final ServerLocaleManager serverLocaleManager;

    public GettingStartedController(SettingsFacade settingsFacade,
            ServerLocaleManager serverLocaleManager) {
        super();
        this.settingsFacade = settingsFacade;
        this.serverLocaleManager = serverLocaleManager;
    }

    @ModelAttribute
    protected void formBackingObject(HttpServletRequest request, Model model) {
        GettingStartedCommand command = new GettingStartedCommand();
        serverLocaleManager
            .getAvailableLocales()
            .stream()
            .filter(locale -> locale.equals(serverLocaleManager.getLocale()))
            .findFirst()
            .ifPresent(locale -> command
                .setLocaleIndex(
                        String.valueOf(serverLocaleManager.getAvailableLocales().indexOf(locale))));
        command
            .setLocales(serverLocaleManager
                .getAvailableLocales()
                .stream()
                .map(Locale::getDisplayName)
                .collect(Collectors.toList()));
        model.addAttribute(Attributes.Model.Command.VALUE, command);
        model
            .addAttribute("runningAsRoot",
                    "root".equals(EnvironmentProvider.getInstance().getUserName()));
    }

    @GetMapping
    public ModelAndView get(HttpServletRequest request) {
        if (request.getParameter(Attributes.Request.HIDE.value()) != null) {
            settingsFacade.commit(SKeys.general.welcome.gettingStartedEnabled, false);
            return new ModelAndView(new RedirectView(ViewName.HOME.value()));
        }
        return new ModelAndView("gettingStarted");
    }

    @PostMapping
    protected ModelAndView post(
            @ModelAttribute(Attributes.Model.Command.VALUE) GettingStartedCommand command,
            RedirectAttributes redirectAttributes) {
        int localeIndex = Integer.parseInt(command.getLocaleIndex());
        Locale locale = serverLocaleManager.getAvailableLocales().get(localeIndex);
        boolean isReload = !serverLocaleManager.getLocale().equals(locale);
        redirectAttributes.addFlashAttribute(Attributes.Redirect.RELOAD_FLAG.value(), isReload);
        serverLocaleManager.stagingLocale(locale);
        settingsFacade.commitAll();
        return new ModelAndView(new RedirectView(ViewName.GETTING_STARTED.value()));
    }
}

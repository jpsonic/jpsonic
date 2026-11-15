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

import com.tesshu.jpsonic.domain.system.PreferredFormatScheme;
import com.tesshu.jpsonic.feature.transcoding.Transcodings;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.Transcoding;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.TranscodingService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Controller for the page used to administrate the set of transcoding
 * configurations.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/transcodingSettings", "/transcodingSettings.view" })
public class TranscodingSettingsController {

    private final SettingsFacade settingsFacade;
    private final UserService userService;
    private final TranscodingService transcodingService;
    private final ShareService shareService;
    private final OutlineHelpSelector outlineHelpSelector;

    public TranscodingSettingsController(SettingsFacade settingsFacade, UserService userService,
            TranscodingService transcodingService, ShareService shareService,
            OutlineHelpSelector outlineHelpSelector) {
        super();
        this.settingsFacade = settingsFacade;
        this.userService = userService;
        this.transcodingService = transcodingService;
        this.shareService = shareService;
        this.outlineHelpSelector = outlineHelpSelector;
    }

    @GetMapping
    public String doGet(HttpServletRequest request, Model model) {

        User user = userService.getCurrentUserStrict(request);
        UserSettings userSettings = userService.getUserSettings(user.getUsername());

        // spotless:off
        model
            .addAttribute("model", LegacyMap
                .of("transcodings", transcodingService.getAllTranscodings(), "preferredFormat",
                        settingsFacade.get(SKeys.transcoding.preferredFormat),
                        "preferredFormatSchemeName",
                        PreferredFormatScheme
                            .of(settingsFacade.get(SKeys.transcoding.preferredFormatShemeName)),
                        "hlsCommand", settingsFacade.get(SKeys.transcoding.hlsCommand), "brand",
                        EnvironmentProvider.getInstance().getBrand(), "isOpenDetailSetting",
                        userSettings.isOpenDetailSetting(), "useRadio",
                        settingsFacade.get(SKeys.general.legacy.useRadio), "useDatabaseSettings",
                        settingsFacade.get(SKeys.general.legacy.useDatabaseSettings),
                        "showOutlineHelp",
                        outlineHelpSelector.isShowOutlineHelp(request, user.getUsername()),
                        "shareCount", shareService.getAllShares().size()));
        return "transcodingSettings";
    }

    @PostMapping
    public ModelAndView doPost(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        String error = handleParameters(request, redirectAttributes);
        if (error == null) {
            redirectAttributes.addFlashAttribute(Attributes.Redirect.TOAST_FLAG.value(), true);
        } else {
            redirectAttributes.addFlashAttribute(Attributes.Redirect.ERROR.value(), error);
        }

        String preferredFormat = request.getParameter("preferredFormat");
        if (preferredFormat != null && transcodingService
            .getAllTranscodings()
            .stream()
            .anyMatch(t -> preferredFormat.equals(t.getTargetFormat()))) {
            settingsFacade.staging(SKeys.transcoding.preferredFormat, preferredFormat);
        }

        settingsFacade
            .staging(SKeys.transcoding.preferredFormatShemeName,
                    request.getParameter("preferredFormatSchemeName"));

        settingsFacade.commitAll();

        return new ModelAndView(new RedirectView(ViewName.TRANSCODING_SETTINGS.value()));
    }

    private static boolean isRevervedTranscodingName(String name) {
        return Transcodings.of(name) != null;
    }

    private String handleParameters(HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        if (restoreTranscoding(request)) {
            return null;
        }

        String errorMsg = updateOrDeleteTranscoding(request);
        if (errorMsg != null) {
            return errorMsg;
        }

        String name = StringUtils.trimToNull(request.getParameter(Attributes.Request.NAME.value()));
        String sourceFormats = StringUtils
            .trimToNull(request.getParameter(Attributes.Request.SOURCE_FORMATS.value()));
        String targetFormat = StringUtils
            .trimToNull(request.getParameter(Attributes.Request.TARGET_FORMAT.value()));
        String step1 = StringUtils
            .trimToNull(request.getParameter(Attributes.Request.STEP1.value()));
        String step2 = StringUtils
            .trimToNull(request.getParameter(Attributes.Request.STEP2.value()));
        boolean defaultActive = request
            .getParameter(Attributes.Request.DEFAULT_ACTIVE.value()) != null;

        if (name != null || sourceFormats != null || targetFormat != null || step1 != null
                || step2 != null) {
            Transcoding transcoding = new Transcoding(null, name, sourceFormats, targetFormat,
                    step1, step2, null, defaultActive);
            String error = null;
            if (name == null) {
                error = "transcodingsettings.noname";
            } else if (isRevervedTranscodingName(name)) {
                return "transcodingsettings.duplicate";
            } else if (sourceFormats == null) {
                error = "transcodingsettings.nosourceformat";
            } else if (targetFormat == null) {
                error = "transcodingsettings.notargetformat";
            } else if (step1 == null) {
                error = "transcodingsettings.nostep1";
            } else {
                transcodingService.createTranscoding(transcoding);
            }
            if (error != null) {
                redirectAttributes
                    .addAttribute(Attributes.Redirect.NEW_TRANSCODING.value(), transcoding);
                return error;
            }
        }

        settingsFacade
            .commit(SKeys.transcoding.hlsCommand,
                    StringUtils.trim(request.getParameter(Attributes.Request.HLS_COMMAND.value())));

        return null;
    }

    private boolean restoreTranscoding(HttpServletRequest request) {
        String[] restoredNames = request.getParameterValues("restoredNames");
        if (restoredNames != null && restoredNames.length > 0) {
            for (String restoredName : restoredNames) {
                transcodingService.restoreTranscoding(Transcodings.of(restoredName));
            }
            return true;
        }
        return false;
    }

    private String updateOrDeleteTranscoding(HttpServletRequest request) {

        for (Transcoding transcoding : transcodingService.getAllTranscodings()) {

            Integer id = transcoding.getId();
            String name = getParam4Array(request, Attributes.Request.NAME.value(), id);
            String sourceFormats = getParam4Array(request,
                    Attributes.Request.SOURCE_FORMATS.value(), id);
            String targetFormat = getParam4Array(request, Attributes.Request.TARGET_FORMAT.value(),
                    id);
            String step1 = getParam4Array(request, Attributes.Request.STEP1.value(), id);
            String step2 = getParam4Array(request, Attributes.Request.STEP2.value(), id);
            boolean delete = getParam4Array(request, Attributes.Request.DELETE.value(), id) != null;
            if (delete) {
                transcodingService.deleteTranscoding(id);
            } else if (name == null) {
                return "transcodingsettings.noname";
            } else if (sourceFormats == null) {
                return "transcodingsettings.nosourceformat";
            } else if (targetFormat == null) {
                return "transcodingsettings.notargetformat";
            } else if (step1 == null) {
                return "transcodingsettings.nostep1";
            } else {
                transcoding.setName(name);
                transcoding.setSourceFormats(sourceFormats);
                transcoding.setTargetFormat(targetFormat);
                transcoding.setStep1(step1);
                transcoding.setStep2(step2);
                transcodingService.updateTranscoding(transcoding);
            }

        }
        return null;
    }

    private String getParam4Array(HttpServletRequest request, String name, Integer id) {
        return StringUtils.trimToNull(request.getParameter(name + "[" + id + "]"));
    }
}

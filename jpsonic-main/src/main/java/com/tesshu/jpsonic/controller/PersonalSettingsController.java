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
import static org.springframework.util.ObjectUtils.isEmpty;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.controller.form.PersonalSettingsCommand;
import com.tesshu.jpsonic.domain.provider.resource.ServerLocaleProvider;
import com.tesshu.jpsonic.domain.system.AlbumListType;
import com.tesshu.jpsonic.domain.system.AvatarScheme;
import com.tesshu.jpsonic.domain.system.SpeechToTextLangScheme;
import com.tesshu.jpsonic.domain.system.SupportableBCP47;
import com.tesshu.jpsonic.feature.theme.ServerThemeService;
import com.tesshu.jpsonic.feature.theme.Theme;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.AvatarService;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Controller for the page used to administrate per-user settings.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/personalSettings", "/personalSettings.view" })
public class PersonalSettingsController {

    private final SettingsFacade settingsFacade;
    private final UserService userService;
    private final ServerLocaleProvider serverLocaleProvider;
    private final ServerThemeService serverThemeService;
    private final ShareService shareService;
    private final AvatarService avatarService;
    private final OutlineHelpSelector outlineHelpSelector;

    public PersonalSettingsController(SettingsFacade settingsFacade, UserService userService,
            ServerLocaleProvider serverLocaleProvider, ServerThemeService serverThemeService,
            ShareService shareService, AvatarService avatarService,
            OutlineHelpSelector outlineHelpSelector) {
        super();
        this.settingsFacade = settingsFacade;
        this.userService = userService;
        this.serverLocaleProvider = serverLocaleProvider;
        this.serverThemeService = serverThemeService;
        this.shareService = shareService;
        this.avatarService = avatarService;
        this.outlineHelpSelector = outlineHelpSelector;
    }

    @ModelAttribute
    protected void formBackingObject(HttpServletRequest request, Model model,
            @RequestParam(Attributes.Request.NameConstants.TOAST) Optional<Boolean> toast) {
        PersonalSettingsCommand command = new PersonalSettingsCommand();

        User user = userService.getCurrentUserStrict(request);
        command.setUser(user);

        // Language and theme

        // - Default language
        command.setLocaleIndex("-1");
        serverLocaleProvider
            .getAvailableLocales()
            .stream()
            .filter(locale -> locale.equals(serverLocaleProvider.getLocale()))
            .findFirst()
            .ifPresent(locale -> command
                .setLocaleIndex(String
                    .valueOf(serverLocaleProvider.getAvailableLocales().indexOf(locale))));
        command
            .setLocales(serverLocaleProvider
                .getAvailableLocales()
                .stream()
                .map(Locale::getDisplayName)
                .collect(Collectors.toList()));

        // - Theme
        UserSettings userSettings = userService.getUserSettings(user.getUsername());
        command.setThemeIndex("-1");
        List<Theme> themes = serverThemeService.getAvailableThemes();
        command.setThemes(themes);
        themes
            .stream()
            .filter(theme -> theme.getId().equals(userSettings.getThemeId()))
            .findFirst()
            .ifPresent(theme -> command.setThemeIndex(String.valueOf(themes.indexOf(theme))));

        // - Font
        WebFontUtils.setToCommand(userSettings, command);
        command.setFontFamilyDefault(WebFontUtils.DEFAULT_FONT_FAMILY);
        command
            .setFontFamilyJpEmbedDefault(WebFontUtils.JP_FONT_NAME
                .concat(", ")
                .concat(WebFontUtils.DEFAULT_FONT_FAMILY));
        command.setFontSizeDefault(WebFontUtils.DEFAULT_FONT_SIZE);
        command.setFontSizeJpEmbedDefault(WebFontUtils.DEFAULT_JP_FONT_SIZE);

        // Options related to display and playing control
        command.setDefaultSettings(userService.getUserSettings(""));
        command.setTabletSettings(userService.createDefaultTabletUserSettings(""));
        command.setSmartphoneSettings(userService.createDefaultSmartphoneUserSettings(""));
        command.setKeyboardShortcutsEnabled(userSettings.isKeyboardShortcutsEnabled());
        command.setAlbumLists(Arrays.asList(AlbumListType.values()));
        command.setAlbumListId(userSettings.getDefaultAlbumList().getId());
        command.setPutMenuInDrawer(userSettings.isPutMenuInDrawer());
        command.setShowIndex(userSettings.isShowIndex());
        command.setCloseDrawer(userSettings.isCloseDrawer());
        command.setAlternativeDrawer(userSettings.isAlternativeDrawer());
        command.setClosePlayQueue(userSettings.isClosePlayQueue());
        command.setAutoHidePlayQueue(userSettings.isAutoHidePlayQueue());
        command.setBreadcrumbIndex(userSettings.isBreadcrumbIndex());
        command.setAssignAccesskeyToNumber(userSettings.isAssignAccesskeyToNumber());
        command.setSimpleDisplay(userSettings.isSimpleDisplay());
        command.setQueueFollowingSongs(userSettings.isQueueFollowingSongs());
        command.setShowCurrentSongInfo(userSettings.isShowCurrentSongInfo());

        // Column to be displayed
        command.setMainVisibility(userSettings.getMainVisibility());
        command.setPlaylistVisibility(userSettings.getPlaylistVisibility());

        // Additional display features
        command.setSongNotificationEnabled(userSettings.isSongNotificationEnabled());
        command.setVoiceInputEnabled(userSettings.isVoiceInputEnabled());
        command
            .setSpeechToTextLangScheme(
                    SpeechToTextLangScheme.of(userSettings.getSpeechLangSchemeName()));
        if (SpeechToTextLangScheme.DEFAULT.name().equals(userSettings.getSpeechLangSchemeName())) {
            command
                .setIetf(SupportableBCP47
                    .valueOf(isEmpty(userSettings.getLocale()) ? serverLocaleProvider.getLocale()
                            : userSettings.getLocale())
                    .getValue());
        } else {
            command.setIetf(userSettings.getIetf());
        }
        if (isEmpty(userSettings.getLocale())) {
            command
                .setIetfDefault(
                        SupportableBCP47.valueOf(serverLocaleProvider.getLocale()).getValue());
            command
                .setIetfDisplayDefault(serverLocaleProvider
                    .getLocale()
                    .getDisplayName(serverLocaleProvider.getLocale()));
        } else {
            command.setIetfDefault(SupportableBCP47.valueOf(userSettings.getLocale()).getValue());
            command
                .setIetfDisplayDefault(
                        userSettings.getLocale().getDisplayName(userSettings.getLocale()));
        }
        command.setOpenDetailSetting(userSettings.isOpenDetailSetting());
        command.setOpenDetailIndex(userSettings.isOpenDetailIndex());
        command.setOpenDetailStar(userSettings.isOpenDetailStar());
        command.setShowArtistInfoEnabled(userSettings.isShowArtistInfoEnabled());
        command.setForceBio2Eng(userSettings.isForceBio2Eng());
        command.setShowTopSongs(userSettings.isShowTopSongs());
        command.setShowSimilar(userSettings.isShowSimilar());
        command.setShowComment(userSettings.isShowComment());
        command.setShowSibling(userSettings.isShowSibling());
        command.setPaginationSize(userSettings.getPaginationSize());
        command.setShowTag(userSettings.isShowTag());
        command.setShowChangeCoverArt(userSettings.isShowChangeCoverArt());
        command.setShowAlbumSearch(userSettings.isShowAlbumSearch());
        command.setShowLastPlay(userSettings.isShowLastPlay());
        command.setShowRate(userSettings.isShowRate());
        command.setShowAlbumActions(userSettings.isShowAlbumActions());
        command.setShowDownload(userSettings.isShowDownload());
        command.setShowShare(userSettings.isShowShare());
        command.setPartyModeEnabled(userSettings.isPartyModeEnabled());
        command.setUsePartyMode(settingsFacade.get(SKeys.general.legacy.usePartyMode));

        // Personal image
        command.setAvatarId(getAvatarId(userSettings));
        command.setAvatars(avatarService.getAllSystemAvatars());
        command.setCustomAvatar(avatarService.getCustomAvatar(user.getUsername()));

        // Cooperation with Music SNS
        command.setListenBrainzEnabled(userSettings.isListenBrainzEnabled());
        command.setListenBrainzToken(userSettings.getListenBrainzToken());
        command.setLastFmEnabled(userSettings.isLastFmEnabled());
        command.setLastFmUsername(userSettings.getLastFmUsername());
        command.setLastFmPassword(userSettings.getLastFmPassword());

        // Update notification
        command
            .setFinalVersionNotificationEnabled(userSettings.isFinalVersionNotificationEnabled());

        // for view page control
        command
            .setShowOutlineHelp(outlineHelpSelector.isShowOutlineHelp(request, user.getUsername()));
        toast.ifPresent(command::setShowToast);
        command.setShareCount(shareService.getAllShares().size());
        command.setUseRadio(settingsFacade.get(SKeys.general.legacy.useRadio));
        command
            .setUseDatabaseSettings(settingsFacade.get(SKeys.general.legacy.useDatabaseSettings));

        model.addAttribute(Attributes.Model.Command.VALUE, command);
    }

    @GetMapping
    protected String displayForm() {
        return "personalSettings";
    }

    @PostMapping
    protected ModelAndView doSubmitAction(
            @ModelAttribute(Attributes.Model.Command.VALUE) PersonalSettingsCommand command,
            RedirectAttributes redirectAttributes) {

        String username = command.getUser().getUsername();
        UserSettings settings = userService.getUserSettings(username);

        // Language and theme

        // - Default language
        int localeIndex = Integer.parseInt(command.getLocaleIndex());
        Locale locale = null;
        if (localeIndex != -1) {
            locale = serverLocaleProvider.getAvailableLocales().get(localeIndex);
        }
        settings.setLocale(locale);

        // - Theme
        int themeIndex = Integer.parseInt(command.getThemeIndex());
        String themeId = null;
        if (themeIndex != -1) {
            themeId = serverThemeService.getAvailableThemes().get(themeIndex).getId();
        }
        settings.setThemeId(themeId);

        // - Font
        WebFontUtils.setToSettings(command, settings);

        // Options related to display and playing control
        settings.setKeyboardShortcutsEnabled(command.isKeyboardShortcutsEnabled());
        settings.setDefaultAlbumList(AlbumListType.fromId(command.getAlbumListId()));
        settings.setPutMenuInDrawer(command.isPutMenuInDrawer());
        settings.setShowIndex(command.isShowIndex());
        settings.setCloseDrawer(command.isCloseDrawer());
        settings.setAlternativeDrawer(command.isAlternativeDrawer());
        settings.setClosePlayQueue(command.isClosePlayQueue());
        settings.setAutoHidePlayQueue(command.isAutoHidePlayQueue());

        settings.setBreadcrumbIndex(command.isBreadcrumbIndex());
        settings.setAssignAccesskeyToNumber(command.isAssignAccesskeyToNumber());
        settings.setSimpleDisplay(command.isSimpleDisplay());
        settings.setQueueFollowingSongs(command.isQueueFollowingSongs());
        settings.setShowCurrentSongInfo(command.isShowCurrentSongInfo());

        // Column to be displayed
        settings.setMainVisibility(command.getMainVisibility());
        settings.setPlaylistVisibility(command.getPlaylistVisibility());

        // Additional display features
        settings.setSongNotificationEnabled(command.isSongNotificationEnabled());
        settings.setVoiceInputEnabled(command.isVoiceInputEnabled());
        settings.setSpeechLangSchemeName(command.getSpeechToTextLangScheme().name());
        if (SpeechToTextLangScheme.DEFAULT == command.getSpeechToTextLangScheme()) {
            settings.setIetf(SupportableBCP47.valueOf(locale).getValue());
        } else if (StringUtils.isNotBlank(command.getIetf())
                && command.getIetf().matches("[a-zA-Z\\-\\_]+")) {
            settings.setIetf(command.getIetf());
        }
        settings.setShowArtistInfoEnabled(command.isShowArtistInfoEnabled());
        settings.setForceBio2Eng(command.isForceBio2Eng());
        settings.setShowTopSongs(command.isShowTopSongs());
        settings.setShowSimilar(command.isShowSimilar());
        settings.setShowComment(command.isShowComment());
        settings.setShowSibling(command.isShowSibling());
        settings.setPaginationSize(command.getPaginationSize());
        settings.setShowTag(command.isShowTag());
        settings.setShowChangeCoverArt(command.isShowChangeCoverArt());
        settings.setShowAlbumSearch(command.isShowAlbumSearch());
        settings.setShowLastPlay(command.isShowLastPlay());
        settings.setShowRate(command.isShowRate());
        settings.setShowAlbumActions(command.isShowAlbumActions());
        settings.setShowDownload(command.isShowDownload());
        settings.setShowShare(command.isShowShare());
        settings.setPartyModeEnabled(command.isPartyModeEnabled());

        // Personal image
        settings.setAvatarScheme(getAvatarScheme(command));
        settings.setSystemAvatarId(getSystemAvatarId(command));

        // Cooperation with Music SNS
        settings.setListenBrainzEnabled(command.isListenBrainzEnabled());
        settings.setListenBrainzToken(command.getListenBrainzToken());
        settings.setLastFmEnabled(command.isLastFmEnabled());
        settings.setLastFmUsername(command.getLastFmUsername());
        if (StringUtils.isNotBlank(command.getLastFmPassword())) {
            settings.setLastFmPassword(command.getLastFmPassword());
        }

        // Update notification
        settings.setFinalVersionNotificationEnabled(command.isFinalVersionNotificationEnabled());

        // for view page control
        settings.setOpenDetailIndex(command.isOpenDetailIndex());
        settings.setOpenDetailSetting(command.isOpenDetailSetting());
        settings.setOpenDetailStar(command.isOpenDetailStar());
        settings.setChanged(now());
        redirectAttributes.addFlashAttribute(Attributes.Redirect.RELOAD_FLAG.value(), true);

        userService.updateUserSettings(settings);

        return new ModelAndView(new RedirectView(ViewName.PERSONAL_SETTINGS.value()));
    }

    private int getAvatarId(UserSettings userSettings) {
        AvatarScheme avatarScheme = userSettings.getAvatarScheme();
        return avatarScheme == AvatarScheme.SYSTEM ? userSettings.getSystemAvatarId()
                : avatarScheme.getCode();
    }

    private AvatarScheme getAvatarScheme(PersonalSettingsCommand command) {
        if (command.getAvatarId() == AvatarScheme.NONE.getCode()) {
            return AvatarScheme.NONE;
        }
        if (command.getAvatarId() == AvatarScheme.CUSTOM.getCode()) {
            return AvatarScheme.CUSTOM;
        }
        return AvatarScheme.SYSTEM;
    }

    private Integer getSystemAvatarId(PersonalSettingsCommand command) {
        int avatarId = command.getAvatarId();
        if (avatarId == AvatarScheme.NONE.getCode() || avatarId == AvatarScheme.CUSTOM.getCode()) {
            return null;
        }
        return avatarId;
    }
}

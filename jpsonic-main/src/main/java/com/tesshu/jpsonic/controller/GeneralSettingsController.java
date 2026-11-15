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

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.controller.form.GeneralSettingsCommand;
import com.tesshu.jpsonic.domain.provider.resource.MusicIndexProvider;
import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.domain.system.IndexScheme;
import com.tesshu.jpsonic.feature.theme.ServerThemeService;
import com.tesshu.jpsonic.feature.theme.Theme;
import com.tesshu.jpsonic.infrastructure.filesystem.RootPathEntryGuard;
import com.tesshu.jpsonic.infrastructure.locale.ServerLocaleManager;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
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
 * Controller for the page used to administrate general settings.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/generalSettings", "/generalSettings.view" })
public class GeneralSettingsController {

    /*
     * It's EN and JP(consonant)
     */
    static final String SIMPLE_INDEX_STRING = """
            A B C D E F G H I J K L M N O P Q R S T U V W X-Z(XYZ)\s\
            \u3042(\u30A2\u30A4\u30A6\u30A8\u30AA)\s\
            \u304B(\u30AB\u30AD\u30AF\u30B1\u30B3)\s\
            \u3055(\u30B5\u30B7\u30B9\u30BB\u30BD)\s\
            \u305F(\u30BF\u30C1\u30C4\u30C6\u30C8)\s\
            \u306A(\u30CA\u30CB\u30CC\u30CD\u30CE)\s\
            \u306F(\u30CF\u30D2\u30D5\u30D8\u30DB)\s\
            \u307E(\u30DE\u30DF\u30E0\u30E1\u30E2)\s\
            \u3084(\u30E4\u30E6\u30E8)\s\
            \u3089(\u30E9\u30EA\u30EB\u30EC\u30ED)\s\
            \u308F(\u30EF\u30F2\u30F3)\
            """; // JP Index

    private final SettingsFacade settingsFacade;
    private final UserService userService;
    private final ServerLocaleManager serverLocaleManager;
    private final ServerThemeService serverThemeService;
    private final ShareService shareService;
    private final OutlineHelpSelector outlineHelpSelector;
    private final ScannerStateProvider scannerStateProvider;
    private final MusicIndexProvider musicIndexProvider;

    public GeneralSettingsController(SettingsFacade settingsFacade, UserService userService,
            ServerLocaleManager serverLocaleManager, ServerThemeService serverThemeService,
            ShareService shareService, OutlineHelpSelector outlineHelpSelector,
            ScannerStateProvider scannerStateProvider, MusicIndexProvider musicIndexProvider) {
        super();
        this.settingsFacade = settingsFacade;
        this.userService = userService;
        this.serverLocaleManager = serverLocaleManager;
        this.serverThemeService = serverThemeService;
        this.shareService = shareService;
        this.outlineHelpSelector = outlineHelpSelector;
        this.scannerStateProvider = scannerStateProvider;
        this.musicIndexProvider = musicIndexProvider;
    }

    @ModelAttribute
    protected void formBackingObject(HttpServletRequest request, Model model,
            @RequestParam(Attributes.Request.NameConstants.TOAST) Optional<Boolean> toast) {
        GeneralSettingsCommand command = new GeneralSettingsCommand();

        // Language and theme
        List<Theme> themes = serverThemeService.getAvailableThemes();
        themes
            .stream()
            .filter(theme -> theme.getId().equals(serverThemeService.getThemeId()))
            .findFirst()
            .ifPresent(theme -> command.setThemeIndex(String.valueOf(themes.indexOf(theme))));
        command.setThemes(themes);

        List<Locale> locales = serverLocaleManager.getAvailableLocales();
        locales
            .stream()
            .filter(locale -> locale.equals(serverLocaleManager.getLocale()))
            .findFirst()
            .ifPresent(locale -> command.setLocaleIndex(String.valueOf(locales.indexOf(locale))));
        command
            .setLocales(locales.stream().map(Locale::getDisplayName).collect(Collectors.toList()));
        command
            .setIndexScheme(
                    IndexScheme.of(settingsFacade.get(SKeys.advanced.index.indexSchemeName)));

        // Index settings
        command.setDefaultIndexString(SKeys.general.index.indexString.defaultValue());
        command.setSimpleIndexString(SIMPLE_INDEX_STRING);
        command.setIndex(settingsFacade.get(SKeys.general.index.indexString));
        command.setIgnoredArticles(settingsFacade.get(SKeys.general.index.ignoredArticles));
        command.setDeleteDiacritic(settingsFacade.get(SKeys.advanced.index.deleteDiacritic));
        command.setIgnoreFullWidth(settingsFacade.get(SKeys.advanced.index.ignoreFullWidth));

        // Sort settings
        command.setSortAlbumsByYear(settingsFacade.get(SKeys.general.sort.albumsByYear));
        command.setSortGenresByAlphabet(settingsFacade.get(SKeys.general.sort.genresByAlphabet));
        command.setProhibitSortVarious(settingsFacade.get(SKeys.general.sort.prohibitSortVarious));
        command.setDefaultSortAlbumsByYear(SKeys.general.sort.albumsByYear.defaultValue());
        command.setDefaultSortGenresByAlphabet(SKeys.general.sort.genresByAlphabet.defaultValue());
        command
            .setDefaultProhibitSortVarious(SKeys.general.sort.prohibitSortVarious.defaultValue());

        // Search settings
        command.setSearchComposer(settingsFacade.get(SKeys.general.search.searchComposer));
        command.setOutputSearchQuery(settingsFacade.get(SKeys.general.search.outputSearchQuery));

        // Suppressed legacy features
        command.setUseJsonp(settingsFacade.get(SKeys.general.legacy.useJsonp));
        command.setUseCast(settingsFacade.get(SKeys.general.legacy.useCast));
        command.setUsePartyMode(settingsFacade.get(SKeys.general.legacy.usePartyMode));

        // Extensions and shortcuts
        command.setMusicFileTypes(settingsFacade.get(SKeys.general.extension.musicFileTypes));
        command.setVideoFileTypes(settingsFacade.get(SKeys.general.extension.videoFileTypes));
        command.setCoverArtFileTypes(settingsFacade.get(SKeys.general.extension.coverArtFileTypes));
        command.setExcludedCoverArts(settingsFacade.get(SKeys.general.extension.excludedCoverArt));
        command.setPlaylistFolder(settingsFacade.get(SKeys.general.extension.playlistFolder));
        command.setShortcuts(settingsFacade.get(SKeys.general.extension.shortcuts));

        command.setDefaultMusicFileTypes(SKeys.general.extension.musicFileTypes.defaultValue());
        command.setDefaultVideoFileTypes(SKeys.general.extension.videoFileTypes.defaultValue());
        command
            .setDefaultCoverArtFileTypes(SKeys.general.extension.coverArtFileTypes.defaultValue());
        command
            .setDefaultExcludedCoverArts(SKeys.general.extension.excludedCoverArt.defaultValue());
        command
            .setDefaultPlaylistFolder(SKeys.general.extension.playlistFolder
                .defaultValue()
                .replaceAll("\\\\", "\\\\\\\\"));
        command.setDefaultShortcuts(SKeys.general.extension.shortcuts.defaultValue());

        // Welcom message
        command
            .setGettingStartedEnabled(
                    settingsFacade.get(SKeys.general.welcome.gettingStartedEnabled));
        command.setWelcomeTitle(settingsFacade.get(SKeys.general.welcome.title));
        command.setWelcomeSubtitle(settingsFacade.get(SKeys.general.welcome.subtitle));
        command.setWelcomeMessage(settingsFacade.get(SKeys.general.welcome.message));
        command.setLoginMessage(settingsFacade.get(SKeys.general.welcome.loginMessage));

        // for view page control
        command.setUseRadio(settingsFacade.get(SKeys.general.legacy.useRadio));
        command
            .setUseDatabaseSettings(settingsFacade.get(SKeys.general.legacy.useDatabaseSettings));
        User user = userService.getCurrentUserStrict(request);
        command
            .setShowOutlineHelp(outlineHelpSelector.isShowOutlineHelp(request, user.getUsername()));
        toast.ifPresent(command::setShowToast);
        command.setShareCount(shareService.getAllShares().size());
        UserSettings userSettings = userService.getUserSettings(user.getUsername());
        command.setOpenDetailSetting(userSettings.isOpenDetailSetting());
        command.setScanning(scannerStateProvider.isScanning());

        model.addAttribute(Attributes.Model.Command.VALUE, command);
    }

    @GetMapping
    protected String get() {
        return "generalSettings";
    }

    @PostMapping
    @SuppressWarnings("PMD.NPathComplexity") // TODO This will be resolved in 114.3.0
    protected ModelAndView post(
            @ModelAttribute(Attributes.Model.Command.VALUE) GeneralSettingsCommand command,
            RedirectAttributes redirectAttributes) {

        // Language and theme
        int themeIndex = Integer.parseInt(command.getThemeIndex());
        Theme theme = serverThemeService.getAvailableThemes().get(themeIndex);
        int localeIndex = Integer.parseInt(command.getLocaleIndex());
        Locale locale = serverLocaleManager.getAvailableLocales().get(localeIndex);

        /*
         * To transition the mainframe after reloading the entire web page, not a simple
         * transition. (Compare before reflecting settings)
         */
        boolean isReload = !settingsFacade
            .get(SKeys.general.index.indexString)
            .equals(command.getIndex())
                || !settingsFacade
                    .get(SKeys.general.index.ignoredArticles)
                    .equals(command.getIgnoredArticles())
                || !settingsFacade
                    .get(SKeys.general.extension.shortcuts)
                    .equals(command.getShortcuts())
                || !serverThemeService.getThemeId().equals(theme.getId())
                || !serverLocaleManager.getLocale().equals(locale);
        redirectAttributes.addFlashAttribute(Attributes.Redirect.RELOAD_FLAG.value(), isReload);

        serverThemeService.stagingThemeId(theme.getId());
        serverLocaleManager.stagingLocale(locale);

        // Index settings
        if (settingsFacade.get(SKeys.general.index.indexString) != null && !command
            .getIndex()
            .equals(settingsFacade.get(SKeys.general.index.indexString))) {
            settingsFacade.staging(SKeys.general.index.indexString, command.getIndex());
            musicIndexProvider.invalidate();
        }
        settingsFacade.staging(SKeys.general.index.ignoredArticles, command.getIgnoredArticles());

        if (command.getIndexScheme() == IndexScheme.NATIVE_JAPANESE) {
            settingsFacade.staging(SKeys.advanced.index.deleteDiacritic, true);
            settingsFacade.staging(SKeys.advanced.index.ignoreFullWidth, true);
        } else if (command.getIndexScheme() == IndexScheme.ROMANIZED_JAPANESE) {
            settingsFacade
                .staging(SKeys.advanced.index.deleteDiacritic, command.isDeleteDiacritic());
            settingsFacade.staging(SKeys.advanced.index.ignoreFullWidth, true);
        } else if (command.getIndexScheme() == IndexScheme.WITHOUT_JP_LANG_PROCESSING) {
            settingsFacade
                .staging(SKeys.advanced.index.deleteDiacritic, command.isDeleteDiacritic());
            settingsFacade
                .staging(SKeys.advanced.index.ignoreFullWidth, command.isIgnoreFullWidth());
        }

        // Sort settings
        settingsFacade.staging(SKeys.general.sort.albumsByYear, command.isSortAlbumsByYear());
        settingsFacade
            .staging(SKeys.general.sort.genresByAlphabet, command.isSortGenresByAlphabet());
        settingsFacade
            .staging(SKeys.general.sort.prohibitSortVarious, command.isProhibitSortVarious());

        // Search settings
        settingsFacade.staging(SKeys.general.search.searchComposer, command.isSearchComposer());
        settingsFacade
            .staging(SKeys.general.search.outputSearchQuery, command.isOutputSearchQuery());

        // Suppressed legacy features
        settingsFacade.staging(SKeys.general.legacy.useRadio, command.isUseRadio());
        settingsFacade
            .staging(SKeys.general.legacy.useDatabaseSettings, command.isUseDatabaseSettings());
        settingsFacade.staging(SKeys.general.legacy.useJsonp, command.isUseJsonp());
        settingsFacade.staging(SKeys.general.legacy.useCast, command.isUseCast());
        settingsFacade.staging(SKeys.general.legacy.usePartyMode, command.isUsePartyMode());

        // Extensions and shortcuts
        if (!scannerStateProvider.isScanning()) {

            settingsFacade
                .staging(SKeys.general.extension.musicFileTypes, command.getMusicFileTypes());
            settingsFacade
                .staging(SKeys.general.extension.videoFileTypes, command.getVideoFileTypes());
            settingsFacade
                .staging(SKeys.general.extension.coverArtFileTypes, command.getCoverArtFileTypes());
            settingsFacade
                .staging(SKeys.general.extension.excludedCoverArt, command.getExcludedCoverArts());
            RootPathEntryGuard
                .validateFolderPath(command.getPlaylistFolder())
                .ifPresent(pathStr -> settingsFacade
                    .staging(SKeys.general.extension.playlistFolder, pathStr));
            settingsFacade.staging(SKeys.general.extension.shortcuts, command.getShortcuts());
        }

        // Welcom message
        settingsFacade
            .staging(SKeys.general.welcome.gettingStartedEnabled,
                    command.isGettingStartedEnabled());
        settingsFacade.staging(SKeys.general.welcome.title, command.getWelcomeTitle());
        settingsFacade.staging(SKeys.general.welcome.subtitle, command.getWelcomeSubtitle());
        settingsFacade.staging(SKeys.general.welcome.message, command.getWelcomeMessage());
        settingsFacade.staging(SKeys.general.welcome.loginMessage, command.getLoginMessage());

        settingsFacade.commitAll();

        if (!isReload) {
            redirectAttributes.addFlashAttribute(Attributes.Redirect.TOAST_FLAG.value(), true);
        }

        return new ModelAndView(new RedirectView(ViewName.GENERAL_SETTINGS.value()));
    }
}

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

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.tesshu.jpsonic.SuppressLint;
import com.tesshu.jpsonic.domain.model.MusicFolderContent;
import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicIndexProvider;
import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.domain.system.AvatarScheme;
import com.tesshu.jpsonic.domain.system.SpeechToTextLangScheme;
import com.tesshu.jpsonic.feature.i18n.AirsonicLocaleResolver;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.infrastructure.metadata.BuildInfoProvider;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.InternetRadio;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.InternetRadioService;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for the top frame.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/top", "/top.view" })
public class TopController {

    private static final Logger LOG = LoggerFactory.getLogger(TopController.class);

    // Update this time if you want to force a refresh in clients.
    private static final List<String> RELOADABLE_MAIN_VIEW_NAME = Arrays
        .asList(ViewName.MUSIC_FOLDER_SETTINGS.value(), ViewName.GENERAL_SETTINGS.value(),
                ViewName.PERSONAL_SETTINGS.value(), ViewName.USER_SETTINGS.value(),
                ViewName.PLAYER_SETTINGS.value(), ViewName.INTERNET_RADIO_SETTINGS.value(),
                ViewName.MORE.value());

    private final SettingsFacade settingsFacade;
    private final MusicFolderService musicFolderService;
    private final UserService userService;
    private final ScannerStateProvider scannerStateProvider;
    private final MusicFolderProvider musicFolderProvider;
    private final MusicIndexProvider musicIndexProvider;
    private final MediaFileProvider mediaFileProvider;
    private final BuildInfoProvider buildInfoProvider;
    private final InternetRadioService internetRadioService;
    private final AirsonicLocaleResolver localeResolver;

    public TopController(SettingsFacade settingsFacade, MusicFolderService musicFolderService,
            UserService userService, ScannerStateProvider scannerStateProvider,
            MusicFolderProvider musicFolderProvider, MusicIndexProvider musicIndexProvider,
            MediaFileProvider mediaFileProvider, BuildInfoProvider buildInfoProvider,
            InternetRadioService internetRadioService, AirsonicLocaleResolver localeResolver) {
        super();
        this.settingsFacade = settingsFacade;
        this.musicFolderService = musicFolderService;
        this.userService = userService;
        this.scannerStateProvider = scannerStateProvider;
        this.musicFolderProvider = musicFolderProvider;
        this.musicIndexProvider = musicIndexProvider;
        this.mediaFileProvider = mediaFileProvider;
        this.buildInfoProvider = buildInfoProvider;
        this.internetRadioService = internetRadioService;
        this.localeResolver = localeResolver;
    }

    @GetMapping
    @SuppressLint(value = "CROSS_SITE_SCRIPTING", justification = "False positive. VersionService reads static local files.")
    protected ModelAndView handleRequestInternal(HttpServletRequest request,
            @RequestParam("mainView") Optional<String> mainView,
            @RequestParam("selectedItem") Optional<String> selectedItem)
            throws ServletRequestBindingException {

        Map<String, Object> map = LegacyMap.of();

        User user = userService.getCurrentUserStrict(request);
        UserSettings userSettings = userService.getUserSettings(user.getUsername());
        map.put("user", user);
        map.put("showCurrentSongInfo", userSettings.isShowCurrentSongInfo());
        map.put("closeDrawer", userSettings.isCloseDrawer());
        map.put("showAvatar", userSettings.getAvatarScheme() != AvatarScheme.NONE);
        map.put("showIndex", userSettings.isShowIndex());
        map.put("putMenuInDrawer", userSettings.isPutMenuInDrawer());
        map.put("assignAccesskeyToNumber", userSettings.isAssignAccesskeyToNumber());
        map.put("voiceInputEnabled", userSettings.isVoiceInputEnabled());
        map.put("useRadio", settingsFacade.get(SKeys.general.legacy.useRadio));

        if (SpeechToTextLangScheme.DEFAULT.name().equals(userSettings.getSpeechLangSchemeName())) {
            map.put("voiceInputLocale", localeResolver.resolveLocale(request).getLanguage());
        } else if (SpeechToTextLangScheme.BCP47
            .name()
            .equals(userSettings.getSpeechLangSchemeName()) && userSettings.getIetf() != null) {
            map.put("voiceInputLocale", userSettings.getIetf());
        } else {
            map.put("voiceInputLocale", localeResolver.resolveLocale(request).getLanguage());
        }

        String username = userService.getCurrentUsernameStrict(request);
        List<MusicFolder> allMusicFolders = musicFolderService.getMusicFoldersForUser(username);
        boolean musicFolderChanged = saveSelectedMusicFolder(request);
        map.put("musicFolderChanged", musicFolderChanged);
        MusicFolder selectedMusicFolder = userService.getSelectedMusicFolder(username);

        map.put("musicFolders", allMusicFolders);
        map.put("selectedMusicFolder", selectedMusicFolder);
        map.put("radios", internetRadioService.getAllInternetRadios());
        map.put("partyMode", userSettings.isPartyModeEnabled());
        map.put("alternativeDrawer", userSettings.isAlternativeDrawer());

        if (userSettings.isFinalVersionNotificationEnabled()
                && buildInfoProvider.isNewVersionAvailable()) {
            map.put("newVersionAvailable", true);
            map.put("latestVersion", buildInfoProvider.getLatestVersion());
        }
        map.put("brand", EnvironmentProvider.getInstance().getBrand());

        List<MusicFolder> musicFoldersToUse = selectedMusicFolder == null ? allMusicFolders
                : Collections.singletonList(selectedMusicFolder);
        List<com.tesshu.jpsonic.domain.model.MusicFolder> folders = musicFoldersToUse
            .stream()
            .map(MusicFolder::getId)
            .map(musicFolderProvider::requireMusicFolder)
            .toList();
        MusicFolderContent musicFolderContent = musicIndexProvider.findMusicFolderContent(folders);
        map.put("shortcuts", mediaFileProvider.findShortcuts(folders));
        map.put("indexedArtists", musicFolderContent.indexedArtists());
        map.put("singleSongs", musicFolderContent.singleSongs());
        map.put("indexes", musicFolderContent.indexedArtists().keySet());
        map.put("user", userService.getCurrentUserStrict(request));
        mainView.ifPresent(v -> {
            if (validateMainViewName(v)) {
                map.put("mainView", v);
            }
        });
        selectedItem.ifPresent(v -> map.put("selectedItem", v));
        selectedItem.ifPresent(v -> map.put("scanning", scannerStateProvider.isScanning()));

        return new ModelAndView("top", "model", map);
    }

    private boolean validateMainViewName(String mainView) {
        return RELOADABLE_MAIN_VIEW_NAME.contains(mainView);
    }

    /**
     * This method is only used by RESTController.
     */
    public long getLastModified(HttpServletRequest request) throws ServletRequestBindingException {
        saveSelectedMusicFolder(request);

        if (scannerStateProvider.isScanning()) {
            return -1L;
        }

        long lastModified = Instant.now().toEpochMilli();
        String username = userService.getCurrentUsernameStrict(request);

        // When was music folder(s) on disk last changed?
        List<MusicFolder> allMusicFolders = musicFolderService.getMusicFoldersForUser(username);
        MusicFolder selectedMusicFolder = userService.getSelectedMusicFolder(username);
        if (selectedMusicFolder == null) {
            for (MusicFolder musicFolder : allMusicFolders) {
                try {
                    lastModified = Math
                        .max(lastModified,
                                Files.getLastModifiedTime(musicFolder.toPath()).toMillis());
                } catch (IOException e) {
                    LOG.error("Unable to get last modified.", e);
                }
            }
        } else {
            try {
                lastModified = Math
                    .max(lastModified,
                            Files.getLastModifiedTime(selectedMusicFolder.toPath()).toMillis());
            } catch (IOException e) {
                LOG.error("Unable to get last modified.", e);
            }
        }

        // When was music folder table last changed?
        for (MusicFolder musicFolder : allMusicFolders) {
            lastModified = Math.max(lastModified, musicFolder.getChanged().toEpochMilli());
        }

        // When was internet radio table last changed?
        for (InternetRadio internetRadio : internetRadioService.getAllInternetRadios()) {
            lastModified = Math.max(lastModified, internetRadio.getChanged().toEpochMilli());
        }

        // When was user settings last changed?
        UserSettings userSettings = userService.getUserSettings(username);
        lastModified = Math.max(lastModified, userSettings.getChanged().toEpochMilli());

        return lastModified;
    }

    private boolean saveSelectedMusicFolder(HttpServletRequest request)
            throws ServletRequestBindingException {
        Integer musicFolderId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.MUSIC_FOLDER_ID.value());
        if (musicFolderId == null) {
            return false;
        }
        // Note: UserSettings.setChanged() is intentionally not called. This would break
        // browser caching
        // of the left frame.
        UserSettings settings = userService
            .getUserSettings(userService.getCurrentUsernameStrict(request));
        settings.setSelectedMusicFolderId(musicFolderId);
        userService.updateUserSettings(settings);

        return true;
    }
}

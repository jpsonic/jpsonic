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

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import com.tesshu.jpsonic.SuppressFBWarnings;
import com.tesshu.jpsonic.controller.form.MusicFolderSettingsCommand;
import com.tesshu.jpsonic.infrastructure.filesystem.FileSystemSKeys;
import com.tesshu.jpsonic.infrastructure.filesystem.RootPathEntryGuard;
import com.tesshu.jpsonic.infrastructure.scanner.MusicFolderServiceImpl;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.MediaScannerService;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Controller for the page used to administrate the set of music folders.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/musicFolderSettings", "/musicFolderSettings.view" })
public class MusicFolderSettingsController {

    private final SettingsFacade settingsFacade;
    private final MusicFolderServiceImpl musicFolderService;
    private final UserService userService;
    private final MediaScannerService mediaScannerService;
    private final ShareService shareService;
    private final OutlineHelpSelector outlineHelpSelector;

    public MusicFolderSettingsController(SettingsFacade settingsFacade,
            MusicFolderServiceImpl musicFolderService, UserService userService,
            MediaScannerService mediaScannerService, ShareService shareService,
            OutlineHelpSelector outlineHelpSelector) {
        super();
        this.settingsFacade = settingsFacade;
        this.musicFolderService = musicFolderService;
        this.userService = userService;
        this.mediaScannerService = mediaScannerService;
        this.shareService = shareService;
        this.outlineHelpSelector = outlineHelpSelector;
    }

    @ModelAttribute
    protected void formBackingObject(HttpServletRequest request,
            @RequestParam(value = Attributes.Request.NameConstants.SCAN_NOW, required = false) String scanNow,
            @RequestParam(value = Attributes.Request.NameConstants.SCAN_CANCEL, required = false) String scanCancel,
            @RequestParam(value = Attributes.Request.NameConstants.EXPUNGE, required = false) String expunge,
            @RequestParam(value = Attributes.Request.NameConstants.UPWARD, required = false) Integer id,
            @RequestParam(Attributes.Request.NameConstants.TOAST) Optional<Boolean> toast,
            Model model) {
        if (!ObjectUtils.isEmpty(scanNow)) {
            musicFolderService.clearMusicFolderCache();
            mediaScannerService.scanLibrary();
        }
        if (!ObjectUtils.isEmpty(scanCancel)) {
            mediaScannerService.tryCancel();

        }
        if (!ObjectUtils.isEmpty(id)) {
            musicFolderService.updateMusicFolderOrder(now(), id);
        }

        MusicFolderSettingsCommand command = new MusicFolderSettingsCommand();

        // Specify folder
        command
            .setMusicFolders(musicFolderService
                .getAllMusicFolders(true, true)
                .stream()
                .map(MusicFolderSettingsCommand.MusicFolderInfo::new)
                .toList());
        command.setNewMusicFolder(new MusicFolderSettingsCommand.MusicFolderInfo());

        // Run a scan
        mediaScannerService.getLastScanEventType().ifPresent(command::setLastScanEventType);
        command
            .setIgnoreFileTimestamps(
                    settingsFacade.get(SKeys.musicFolder.scan.ignoreFileTimestamps));
        command
            .setInterval(String
                .valueOf(settingsFacade.get(SKeys.musicFolder.scan.indexCreationInterval)));
        command
            .setHour(String.valueOf(settingsFacade.get(SKeys.musicFolder.scan.indexCreationHour)));

        // Exclusion settings
        command.setExcludePatternString(settingsFacade.get(FileSystemSKeys.excludePatternString));
        command.setIgnoreSymLinks(settingsFacade.get(FileSystemSKeys.ignoreSymlinks));

        // for view page control
        command.setUseRadio(settingsFacade.get(SKeys.general.legacy.useRadio));
        command
            .setUseDatabaseSettings(settingsFacade.get(SKeys.general.legacy.useDatabaseSettings));
        toast.ifPresent(command::setShowToast);
        command.setShareCount(shareService.getAllShares().size());

        User user = userService.getCurrentUserStrict(request);
        command
            .setShowOutlineHelp(outlineHelpSelector.isShowOutlineHelp(request, user.getUsername()));
        UserSettings userSettings = userService.getUserSettings(user.getUsername());
        command.setOpenDetailSetting(userSettings.isOpenDetailSetting());
        command.setScanning(mediaScannerService.isScanning());
        command.setCancel(mediaScannerService.isCancel());

        model.addAttribute(Attributes.Model.Command.VALUE, command);
    }

    @GetMapping
    protected String get() {
        return "musicFolderSettings";
    }

    @PostMapping
    protected ModelAndView post(
            @ModelAttribute(Attributes.Model.Command.VALUE) MusicFolderSettingsCommand command,
            RedirectAttributes redirectAttributes) {

        final ModelAndView result = new ModelAndView(
                new RedirectView(ViewName.MUSIC_FOLDER_SETTINGS.value()));
        if (mediaScannerService.isScanning()) {
            return result;
        }

        Instant executed = now();

        // Specify folder
        for (MusicFolderSettingsCommand.MusicFolderInfo musicFolderInfo : command
            .getMusicFolders()) {
            if (musicFolderInfo.isDelete()) {
                musicFolderService.deleteMusicFolder(executed, musicFolderInfo.getId());
            } else {
                toMusicFolder(musicFolderInfo)
                    .ifPresent(folder -> musicFolderService.updateMusicFolder(executed, folder));
            }
        }
        toMusicFolder(command.getNewMusicFolder()).ifPresent(newFolder -> {
            if (musicFolderService
                .getAllMusicFolders(false, true)
                .stream()
                .noneMatch(
                        oldFolder -> oldFolder.getPathString().equals(newFolder.getPathString()))) {
                musicFolderService.createMusicFolder(executed, newFolder);
            }
        });

        // Run a scan
        settingsFacade
            .staging(SKeys.musicFolder.scan.ignoreFileTimestamps, command.isIgnoreFileTimestamps());
        settingsFacade
            .staging(SKeys.musicFolder.scan.indexCreationInterval,
                    Integer.parseInt(command.getInterval()));
        settingsFacade
            .staging(SKeys.musicFolder.scan.indexCreationHour, Integer.parseInt(command.getHour()));

        // Exclusion settings
        settingsFacade
            .staging(FileSystemSKeys.excludePatternString, command.getExcludePatternString());
        settingsFacade.staging(FileSystemSKeys.ignoreSymlinks, command.isIgnoreSymLinks());

        settingsFacade.commitAll();

        // for view page control
        redirectAttributes.addFlashAttribute(Attributes.Redirect.RELOAD_FLAG.value(), true);

        return result;
    }

    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "Validated.")
    public Optional<MusicFolder> toMusicFolder(MusicFolderSettingsCommand.MusicFolderInfo info) {
        Optional<String> validated = RootPathEntryGuard
            .validateFolderPath(StringUtils.trimToNull(info.getPath()));
        if (validated.isEmpty()) {
            return Optional.empty();
        }

        Path newPath = Path.of(validated.get());
        if (musicFolderService
            .getAllMusicFolders(true, true)
            .stream()
            .anyMatch(old -> !old.toPath().equals(newPath)
                    && (old.toPath().startsWith(newPath) || newPath.startsWith(old.toPath())))) {
            return Optional.empty();
        }

        String name = StringUtils.trimToNull(info.getName());
        if (name == null) {
            name = newPath.getFileName().toString();
        }
        return Optional
            .of(new MusicFolder(info.getId(), validated.get(), name, info.isEnabled(), now(),
                    info.getFolderOrder(), false));
    }
}

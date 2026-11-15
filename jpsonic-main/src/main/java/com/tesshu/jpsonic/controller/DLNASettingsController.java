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
 * (C) 2013 Sindre Mehus
 * (C) 2016 Airsonic Authors
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.controller;

import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.tesshu.jpsonic.controller.form.DLNASettingsCommand;
import com.tesshu.jpsonic.controller.form.DLNASettingsCommand.SubMenuItemRowInfo;
import com.tesshu.jpsonic.domain.model.AuthKey;
import com.tesshu.jpsonic.domain.model.MenuItem.ViewType;
import com.tesshu.jpsonic.domain.model.TranscodingDefinition.BitRateLimit;
import com.tesshu.jpsonic.domain.system.MenuItemId;
import com.tesshu.jpsonic.domain.type.GenreMasterSort;
import com.tesshu.jpsonic.feature.crypt.upnp.KeyRotationPeriod;
import com.tesshu.jpsonic.feature.crypt.upnp.KeyRotationType;
import com.tesshu.jpsonic.feature.crypt.upnp.UpnpKeyManager;
import com.tesshu.jpsonic.feature.crypt.upnp.UpnpKeyStagingApplier;
import com.tesshu.jpsonic.feature.upnp.UPnPSKeys;
import com.tesshu.jpsonic.feature.upnp.UPnPService;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.infrastructure.menu.MenuItemData;
import com.tesshu.jpsonic.infrastructure.menu.MenuItemManager;
import com.tesshu.jpsonic.infrastructure.menu.MenuItemManager.MenuItemWithDefaultName;
import com.tesshu.jpsonic.infrastructure.search.SearchSKeys;
import com.tesshu.jpsonic.infrastructure.search.UPnPSearchMethod;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.persistence.api.entity.Transcoding;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.TranscodingService;
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
 * Controller for the page used to administrate the UPnP server settings.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/dlnaSettings", "/dlnaSettings.view" })
public class DLNASettingsController {

    private static final int DLNA_RANDOM_DEFAULT = 50;
    private static final int DLNA_RANDOM_LIMIT = 1999;
    private static final Pattern IPV4_STR = Pattern
        .compile("^(([0-1]?\\d?\\d|2[0-4]\\d|25[0-5])\\.){3}([0-1]?\\d?\\d|2[0-4]\\d|25[0-5])$");

    private final SettingsFacade settingsFacade;
    private final MusicFolderService musicFolderService;
    private final UserService userService;
    private final PlayerService playerService;
    private final TranscodingService transcodingService;
    private final UPnPService upnpService;
    private final ShareService shareService;
    private final MenuItemManager menuItemManager;
    private final UpnpKeyManager upnpKeyManager;
    private final OutlineHelpSelector outlineHelpSelector;

    public DLNASettingsController(SettingsFacade settingsFacade,
            MusicFolderService musicFolderService, UserService userService,
            PlayerService playerService, TranscodingService transcodingService,
            UPnPService upnpService, ShareService shareService, MenuItemManager menuItemManager,
            UpnpKeyManager upnpKeyManager, OutlineHelpSelector outlineHelpSelector) {
        super();
        this.settingsFacade = settingsFacade;
        this.musicFolderService = musicFolderService;
        this.userService = userService;
        this.playerService = playerService;
        this.transcodingService = transcodingService;
        this.upnpService = upnpService;
        this.shareService = shareService;
        this.menuItemManager = menuItemManager;
        this.upnpKeyManager = upnpKeyManager;
        this.outlineHelpSelector = outlineHelpSelector;
    }

    @ModelAttribute
    protected void formBackingObject(HttpServletRequest request, Model model,
            @RequestParam(value = Attributes.Request.NameConstants.UPWARD, required = false) Optional<Integer> id,
            @RequestParam(value = Attributes.Request.NameConstants.RESET, required = false) Optional<String> mode) {

        id.ifPresent(i -> menuItemManager.updateMenuItemOrder(ViewType.UPNP, i));
        mode
            .ifPresent(m -> menuItemManager
                .resetMenuItem(ViewType.UPNP, MenuItemManager.ResetMode.of(m)));

        DLNASettingsCommand command = new DLNASettingsCommand();

        // UPnP basic settings

        command.setDlnaEnabled(settingsFacade.get(UPnPSKeys.basic.enabled));
        command.setWsl(EnvironmentProvider.getInstance().isWsl());
        command.setDlnaServerName(settingsFacade.get(UPnPSKeys.basic.serverName));
        command.setDlnaBaseLANURL(settingsFacade.get(UPnPSKeys.basic.baseLanUrl));
        command.setAllMusicFolders(musicFolderService.getAllMusicFolders());
        User guestUser = userService.getGuestUser();
        command
            .setAllowedMusicFolderIds(musicFolderService
                .getMusicFoldersForUser(guestUser.getUsername())
                .stream()
                .mapToInt(MusicFolder::getId)
                .toArray());
        command.setAllTranscodings(transcodingService.getAllTranscodings());
        Player guestPlayer = playerService.getUPnPPlayer();
        command
            .setActiveTranscodingIds(transcodingService
                .getTranscodingsForPlayer(guestPlayer)
                .stream()
                .mapToInt(Transcoding::getId)
                .toArray());
        command.setTranscodingSupported(transcodingService.isTranscodingSupported(null));
        command.setBitRateLimit(guestPlayer.getBitRateLimit());

        // Menu settings
        List<MenuItemWithDefaultName> topMenuItems = menuItemManager.getTopMenuItems(ViewType.UPNP);
        command.setTopMenuItems(topMenuItems);

        // Menu detail settings
        command
            .setTopMenuEnableds(topMenuItems
                .stream()
                .collect(Collectors.toMap(MenuItemData::getId, MenuItemData::isEnabled)));
        List<MenuItemWithDefaultName> subMenuItems = menuItemManager.getSubMenuItems(ViewType.UPNP);
        command.setSubMenuItems(subMenuItems);

        Map<MenuItemId, SubMenuItemRowInfo> subMenuItemRowInfos = new ConcurrentHashMap<>();
        topMenuItems.stream().map(MenuItemData::getId).forEach(topMenuItemId -> {
            int count = (int) subMenuItems
                .stream()
                .filter(subMenuItem -> subMenuItem.getParent() == topMenuItemId)
                .count();
            subMenuItems
                .stream()
                .filter(subMenuItem -> subMenuItem.getParent() == topMenuItemId)
                .findFirst()
                .ifPresent(firstChild -> subMenuItemRowInfos
                    .put(topMenuItemId, new SubMenuItemRowInfo(firstChild, count)));
        });
        command.setSubMenuItemRowInfos(subMenuItemRowInfos);

        // Display options / Access control
        command.setAvairableAlbumGenreSort(Arrays.asList(GenreMasterSort.values()));
        command
            .setAlbumGenreSort(
                    GenreMasterSort.of(settingsFacade.get(UPnPSKeys.options.upnpAlbumGenreSort)));
        command
            .setAvairableSongGenreSort(Arrays
                .asList(GenreMasterSort.FREQUENCY, GenreMasterSort.NAME,
                        GenreMasterSort.SONG_COUNT));
        command
            .setSongGenreSort(
                    GenreMasterSort.of(settingsFacade.get(UPnPSKeys.options.upnpSongGenreSort)));
        command.setDlnaRandomMax(settingsFacade.get(UPnPSKeys.options.randomMax));
        command.setDlnaGuestPublish(settingsFacade.get(UPnPSKeys.options.guestPublish));

        // Search
        command
            .setSearchMethod(
                    UPnPSearchMethod.of(settingsFacade.get(SearchSKeys.search.upnpSearchMethod)));

        // Advanced
        command.setDlnaDefaultFilteredIp(UPnPSKeys.advanced.filteredIp.defaultValue());
        command.setDlnaEnabledFilteredIp(settingsFacade.get(UPnPSKeys.advanced.enabledFilteredIp));
        command.setDlnaFilteredIp(settingsFacade.get(UPnPSKeys.advanced.filteredIp));
        command
            .setDlnaKeyRotationType(
                    KeyRotationType.of(settingsFacade.get(UPnPSKeys.advanced.rotationType)));
        command
            .setDlnaKeyRotationPeriod(
                    KeyRotationPeriod.of(settingsFacade.get(UPnPSKeys.advanced.rotationPeriod)));
        AuthKey dlnaKey = upnpKeyManager.getAuthKey();
        LocalDateTime lastUpdate = dlnaKey
            .lastUpdate()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
        command
            .setDlnaKeyLastUpdate(
                    lastUpdate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // for view page control
        User user = userService.getCurrentUserStrict(request);
        UserSettings userSettings = userService.getUserSettings(user.getUsername());
        command.setOpenDetailSetting(userSettings.isOpenDetailSetting());
        command.setShareCount(shareService.getAllShares().size());
        command.setUseRadio(settingsFacade.get(SKeys.general.legacy.useRadio));
        command
            .setUseDatabaseSettings(settingsFacade.get(SKeys.general.legacy.useDatabaseSettings));
        command
            .setShowOutlineHelp(outlineHelpSelector.isShowOutlineHelp(request, user.getUsername()));

        model.addAttribute(Attributes.Model.Command.VALUE, command);
    }

    @GetMapping
    public String get(HttpServletRequest request, Model model) {
        return "dlnaSettings";
    }

    @PostMapping("/rotate")
    protected String postRotate() {
        upnpKeyManager.rotate();
        return "redirect:/" + ViewName.DLNA_SETTINGS.value();
    }

    @PostMapping
    @SuppressWarnings("PMD.NPathComplexity") // TODO This will be resolved in 114.3.0
    public ModelAndView post(
            @ModelAttribute(Attributes.Model.Command.VALUE) DLNASettingsCommand command,
            RedirectAttributes redirectAttributes) {

        final boolean enabledChanged = settingsFacade.get(UPnPSKeys.basic.enabled) != command
            .isDlnaEnabled();
        final boolean restartRequired = !isEmpty(command.getDlnaServerName())
                && !command
                    .getDlnaServerName()
                    .equals(settingsFacade.get(UPnPSKeys.basic.serverName))
                || !isEmpty(command.getDlnaBaseLANURL()) && !command
                    .getDlnaBaseLANURL()
                    .equals(settingsFacade.get(UPnPSKeys.basic.baseLanUrl))
                || command.isDlnaEnabledFilteredIp() != settingsFacade
                    .get(UPnPSKeys.advanced.enabledFilteredIp)
                || !isEmpty(command.getDlnaFilteredIp()) && !command
                    .getDlnaFilteredIp()
                    .equals(settingsFacade.get(UPnPSKeys.advanced.filteredIp));

        /*
         * Changes to property file
         */

        // UPnP basic settings
        settingsFacade.staging(UPnPSKeys.basic.enabled, command.isDlnaEnabled());

        String serverName = StringUtils
            .defaultIfEmpty(command.getDlnaServerName(),
                    EnvironmentProvider.getInstance().getBrand());
        settingsFacade.staging(UPnPSKeys.basic.serverName, serverName);

        settingsFacade.staging(UPnPSKeys.basic.baseLanUrl, command.getDlnaBaseLANURL());

        // Display options / Access control
        settingsFacade
            .staging(UPnPSKeys.options.upnpAlbumGenreSort, command.getAlbumGenreSort().name());
        settingsFacade
            .staging(UPnPSKeys.options.upnpSongGenreSort, command.getSongGenreSort().name());

        final List<Integer> allowedIds = Arrays
            .stream(command.getAllowedMusicFolderIds())
            .boxed()
            .collect(Collectors.toList());

        settingsFacade.staging(UPnPSKeys.options.guestPublish, command.isDlnaGuestPublish());

        Integer input = command.getDlnaRandomMax();
        int normalized = (input == null || input <= 0) ? DLNA_RANDOM_DEFAULT
                : Math.min(input, DLNA_RANDOM_LIMIT);
        settingsFacade.staging(UPnPSKeys.options.randomMax, normalized);

        // Search
        settingsFacade
            .staging(SearchSKeys.search.upnpSearchMethod, command.getSearchMethod().name());

        // Advanced
        settingsFacade
            .staging(UPnPSKeys.advanced.enabledFilteredIp, command.isDlnaEnabledFilteredIp());
        String filteredIpIn = command.getDlnaFilteredIp();
        String filteredIp = filteredIpIn != null && IPV4_STR.matcher(filteredIpIn).matches()
                ? filteredIpIn
                : UPnPSKeys.advanced.filteredIp.defaultValue();
        settingsFacade.staging(UPnPSKeys.advanced.filteredIp, filteredIp);
        new UpnpKeyStagingApplier().apply(command, settingsFacade);

        settingsFacade.commitAll();

        /*
         * Changes to the database
         */

        // UPnP basic settings
        User guestUser = userService.getGuestUser();
        musicFolderService.setMusicFoldersForUser(guestUser.getUsername(), allowedIds);
        UserSettings userSettings = userService.getUserSettings(guestUser.getUsername());
        userSettings.setBitRateLimit(command.getBitRateLimit());
        userSettings.setChanged(now());
        Player guestPlayer = playerService.getUPnPPlayer();
        transcodingService.setTranscodingsForPlayer(guestPlayer, command.getActiveTranscodingIds());
        if (command.getActiveTranscodingIds().length == 0) {
            guestPlayer.setBitRateLimit(BitRateLimit.OFF);
        } else {
            guestPlayer.setBitRateLimit(command.getBitRateLimit());
        }
        playerService.updatePlayer(guestPlayer);

        // Menu detail settings
        menuItemManager
            .updateMenuItems(Stream
                .concat(command.getTopMenuItems().stream(), command.getSubMenuItems().stream()));

        /*
         * Service reboot: If some properties are changed, UPnP will be restarted. (Do
         * not restart if the settings related to the contents that can be changed
         * dynamically are changed.)
         */

        if (enabledChanged) {
            upnpService.setEnabled(command.isDlnaEnabled());
        } else if (restartRequired && settingsFacade.get(UPnPSKeys.basic.enabled)) {
            upnpService.setEnabled(false);
            upnpService.setEnabled(true);
        }

        /*
         * For view page control
         */
        redirectAttributes.addFlashAttribute(Attributes.Redirect.TOAST_FLAG.value(), true);

        return new ModelAndView(new RedirectView(ViewName.DLNA_SETTINGS.value()));
    }
}

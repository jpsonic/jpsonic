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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.controller.form.UserSettingsCommand;
import com.tesshu.jpsonic.controller.validator.UserSettingsValidator;
import com.tesshu.jpsonic.domain.language.StringUtil;
import com.tesshu.jpsonic.domain.model.TranscodingDefinition.BitRateLimit;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.TranscodingService;
import com.tesshu.jpsonic.service.UserService;
import com.tesshu.jpsonic.util.PlayerUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Controller for the page used to administrate users.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/userSettings", "/userSettings.view" })
public class UserSettingsController {

    private final SettingsFacade settingsFacade;
    private final MusicFolderService musicFolderService;
    private final UserService userService;
    private final TranscodingService transcodingService;
    private final ShareService shareService;
    private final PlayerService playerService;

    public UserSettingsController(SettingsFacade settingsFacade,
            MusicFolderService musicFolderService, UserService userService,
            TranscodingService transcodingService, ShareService shareService,
            PlayerService playerService) {
        super();
        this.settingsFacade = settingsFacade;
        this.musicFolderService = musicFolderService;
        this.userService = userService;
        this.transcodingService = transcodingService;
        this.shareService = shareService;
        this.playerService = playerService;
    }

    @InitBinder
    protected void initBinder(WebDataBinder binder, HttpServletRequest request) {
        binder.addValidators(new UserSettingsValidator(userService, settingsFacade, request));
    }

    @GetMapping
    protected String get(HttpServletRequest request, Model model,
            @RequestParam(Attributes.Request.NameConstants.TOAST) Optional<Boolean> toast)
            throws ServletRequestBindingException {

        UserSettingsCommand command;
        if (model.containsAttribute(Attributes.Model.Command.VALUE)) {
            command = (UserSettingsCommand) model.asMap().get(Attributes.Model.Command.VALUE);
        } else {
            command = new UserSettingsCommand();
            User user = getUser(request);
            if (user == null) {
                // User creation
                command.setNewUser(true);
                command.setStreamRole(true);
                command.setSettingsRole(true);
                command.setBitRateLimit(BitRateLimit.OFF);
            } else {
                // User update
                command.setUser(user);
                command.setEmail(user.getEmail());
                UserSettings userSettings = userService.getUserSettings(user.getUsername());
                command.setBitRateLimit(userSettings.getBitRateLimit());
                command
                    .setAllowedMusicFolderIds(
                            PlayerUtils.toIntArray(getAllowedMusicFolderIds(user)));
                command
                    .setCurrentUser(userService
                        .getCurrentUserStrict(request)
                        .getUsername()
                        .equals(user.getUsername()));
                command.setNowPlayingAllowed(userSettings.isNowPlayingAllowed());
            }
        }

        command.setLdapEnabled(settingsFacade.get(SKeys.advanced.ldap.enabled));
        command
            .setUsers(userService
                .getAllUsers()
                .stream()
                .filter(u -> !User.USERNAME_GUEST.equals(u.getUsername()))
                .collect(Collectors.toList()));
        command.setAllMusicFolders(musicFolderService.getAllMusicFolders());
        command.setTranscodingSupported(transcodingService.isTranscodingSupported(null));

        // for view page control
        command.setUseRadio(settingsFacade.get(SKeys.general.legacy.useRadio));
        command
            .setUseDatabaseSettings(settingsFacade.get(SKeys.general.legacy.useDatabaseSettings));
        toast.ifPresent(command::setShowToast);
        command.setShareCount(shareService.getAllShares().size());

        model.addAttribute(Attributes.Model.Command.VALUE, command);
        return "userSettings";
    }

    private User getUser(HttpServletRequest request) throws ServletRequestBindingException {
        Integer userIndex = ServletRequestUtils
            .getIntParameter(request, Attributes.Redirect.USER_INDEX.value());
        if (userIndex != null) {
            List<User> users = userService
                .getAllUsers()
                .stream()
                .filter(u -> !User.USERNAME_GUEST.equals(u.getUsername()))
                .collect(Collectors.toList());
            if (userIndex >= 0 && userIndex < users.size()) {
                return users.get(userIndex);
            }
        }
        return null;
    }

    private List<Integer> getAllowedMusicFolderIds(User user) {
        List<Integer> result = new ArrayList<>();
        List<MusicFolder> allowedMusicFolders = user == null
                ? musicFolderService.getAllMusicFolders()
                : musicFolderService.getMusicFoldersForUser(user.getUsername());

        for (MusicFolder musicFolder : allowedMusicFolders) {
            result.add(musicFolder.getId());
        }
        return result;
    }

    @PostMapping
    protected ModelAndView post(
            @ModelAttribute(Attributes.Model.Command.VALUE) @Validated UserSettingsCommand command,
            BindingResult bindingResult, RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(Attributes.Redirect.COMMAND.value(), command);
            redirectAttributes
                .addFlashAttribute(Attributes.Redirect.BINDING_RESULT.value(), bindingResult);
        } else {
            if (command.isDeleteUser()) {
                deleteUser(command);
            } else if (command.isNewUser()) {
                createUser(command);
            } else {
                updateUser(command);
            }
            redirectAttributes.addFlashAttribute(Attributes.Redirect.RELOAD_FLAG.value(), true);
        }

        redirectAttributes
            .addFlashAttribute(Attributes.Redirect.USER_INDEX.value(),
                    getUserIndex(command.getUsername()));

        return new ModelAndView(new RedirectView(ViewName.USER_SETTINGS.value()));
    }

    private Integer getUserIndex(String userName) {
        List<User> users = userService
            .getAllUsers()
            .stream()
            .filter(u -> !User.USERNAME_GUEST.equals(u.getUsername()))
            .collect(Collectors.toList());
        for (int i = 0; i < users.size(); i++) {
            if (StringUtil.equalsIgnoreCase(users.get(i).getUsername(), userName)) {
                return i;
            }
        }
        return null;
    }

    public void deleteUser(UserSettingsCommand command) {
        userService.deleteUser(command.getUsername());
    }

    public void createUser(UserSettingsCommand command) {
        User user = new User(command.getUsername(), command.getPassword(),
                StringUtils.trimToNull(command.getEmail()));
        user.setLdapAuthenticated(command.isLdapAuthenticated());
        userService.createUser(user);
        updateUser(command);
    }

    public void updateUser(UserSettingsCommand command) {
        User user = userService.getUserByNameStrict(command.getUsername());
        user.setLdapAuthenticated(command.isLdapAuthenticated());
        if (command.isPasswordChange()) {
            user.setPassword(command.getPassword());
        }
        user.setEmail(StringUtils.trimToNull(command.getEmail()));
        user.setAdminRole(command.isAdminRole());
        user.setSettingsRole(command.isSettingsRole());
        user.setStreamRole(command.isStreamRole());
        user.setDownloadRole(command.isDownloadRole());
        user.setUploadRole(command.isUploadRole());
        user.setShareRole(command.isShareRole());
        user.setCoverArtRole(command.isCoverArtRole());
        user.setCommentRole(command.isCommentRole());
        user.setPodcastRole(command.isPodcastRole());

        userService.updateUser(user);

        UserSettings userSettings = userService.getUserSettings(command.getUsername());
        userSettings.setBitRateLimit(command.getBitRateLimit());
        if (command.getBitRateLimit() != BitRateLimit.OFF) {
            List<Player> userPlayers = playerService
                .getAllPlayers()
                .stream()
                .filter(p -> command.getUsername().equals(p.getUsername()))
                .collect(Collectors.toList());
            for (Player player : userPlayers) {
                if (player.getBitRateLimit() == BitRateLimit.OFF || player
                    .getBitRateLimit()
                    .getMaxBitRate() > command.getBitRateLimit().getMaxBitRate()) {
                    player.setBitRateLimit(command.getBitRateLimit());
                    playerService.updatePlayer(player);
                }
            }
        }
        userSettings.setNowPlayingAllowed(command.isNowPlayingAllowed());
        userSettings.setChanged(now());
        userService.updateUserSettings(userSettings);

        List<Integer> allowedMusicFolderIds = PlayerUtils
            .toIntegerList(command.getAllowedMusicFolderIds());
        musicFolderService.setMusicFoldersForUser(command.getUsername(), allowedMusicFolderIds);
    }
}

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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.tesshu.jpsonic.controller.form.PlayerSettingsCommand;
import com.tesshu.jpsonic.feature.auth.jwt.JWTAuthenticationToken;
import com.tesshu.jpsonic.feature.upnp.UPnPSubnet;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.persistence.api.entity.Transcoding;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.TranscodingService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Controller for the player settings page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/playerSettings", "/playerSettings.view" })
public class PlayerSettingsController {

    private final SettingsFacade settingsFacade;
    private final UserService userService;
    private final UPnPSubnet subnet;
    private final PlayerService playerService;
    private final TranscodingService transcodingService;
    private final ShareService shareService;
    private final OutlineHelpSelector outlineHelpSelector;

    public PlayerSettingsController(SettingsFacade settingsFacade, UserService userService,
            UPnPSubnet subnet, PlayerService playerService, TranscodingService transcodingService,
            ShareService shareService, OutlineHelpSelector outlineHelpSelector) {
        super();
        this.settingsFacade = settingsFacade;
        this.userService = userService;
        this.subnet = subnet;
        this.playerService = playerService;
        this.transcodingService = transcodingService;
        this.shareService = shareService;
        this.outlineHelpSelector = outlineHelpSelector;
    }

    @GetMapping
    protected String displayForm() {
        return "playerSettings";
    }

    @ModelAttribute
    protected void formBackingObject(HttpServletRequest request, Model model,
            @RequestParam(Attributes.Request.NameConstants.TOAST) Optional<Boolean> toast)
            throws ServletRequestBindingException {

        deleteOrClone(request, model);

        PlayerSettingsCommand command = new PlayerSettingsCommand();
        List<Player> players = getPlayers(request);
        command.setPlayers(players);
        User user = userService.getCurrentUserStrict(request);
        command.setAdmin(user.isAdminRole());
        command.setTranscodingSupported(transcodingService.isTranscodingSupported(null));

        Player player = null;
        Integer playerId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.ID.value());
        if (playerId != null) {
            player = playerService.getPlayerById(playerId);
        } else if (!players.isEmpty()) {
            player = players.get(0);
        }

        if (player != null) {
            // Player settings
            command.setPlayerId(player.getId());
            command.setName(player.getName());
            command.setType(player.getType());
            command.setIpAddress(player.getIpAddress());
            command.setGuest(User.USERNAME_GUEST.equals(player.getUsername()));
            command
                .setAnonymous(
                        JWTAuthenticationToken.USERNAME_ANONYMOUS.equals(player.getUsername()));
            command.setSameSegment(subnet.isInUPnPRange(player.getIpAddress()));
            command.setAllTranscodings(transcodingService.getAllTranscodings());
            UserSettings userSettings = userService.getUserSettings(player.getUsername());
            command.setMaxBitrate(userSettings.getBitRateLimit());
            command.setBitRateLimit(player.getBitRateLimit());
            command
                .setActiveTranscodingIds(transcodingService
                    .getTranscodingsForPlayer(player)
                    .stream()
                    .mapToInt(Transcoding::getId)
                    .toArray());
            command.setDynamicIp(player.isDynamicIp());
            if (player.getLastSeen() != null) {
                command
                    .setLastSeen(
                            ZonedDateTime.ofInstant(player.getLastSeen(), ZoneId.systemDefault()));
            }
        }

        // for view page control
        UserSettings userSettings = userService.getUserSettings(user.getUsername());
        command.setOpenDetailSetting(userSettings.isOpenDetailSetting());
        command.setUseRadio(settingsFacade.get(SKeys.general.legacy.useRadio));
        command
            .setUseDatabaseSettings(settingsFacade.get(SKeys.general.legacy.useDatabaseSettings));
        toast.ifPresent(command::setShowToast);
        command.setShareCount(shareService.getAllShares().size());
        command
            .setShowOutlineHelp(outlineHelpSelector.isShowOutlineHelp(request, user.getUsername()));

        model.addAttribute(Attributes.Model.Command.VALUE, command);
    }

    @PostMapping
    protected ModelAndView doSubmitAction(
            @ModelAttribute(Attributes.Model.Command.VALUE) PlayerSettingsCommand command,
            RedirectAttributes redirectAttributes) {
        Player player = playerService.getPlayerById(command.getPlayerId());
        if (player == null) {
            return new ModelAndView(new RedirectView("notFound"));
        } else {

            // Player settings
            player.setName(StringUtils.trimToNull(command.getName()));
            player.setBitRateLimit(command.getBitRateLimit());
            player.setDynamicIp(command.isDynamicIp());

            playerService.updatePlayer(player);
            transcodingService.setTranscodingsForPlayer(player, command.getActiveTranscodingIds());

            // for view page control
            redirectAttributes.addFlashAttribute(Attributes.Redirect.RELOAD_FLAG.value(), true);
            redirectAttributes
                .addFlashAttribute(Attributes.Redirect.PLAYER_ID.value(), player.getId());
            redirectAttributes.addFlashAttribute(Attributes.Redirect.TOAST_FLAG.value(), true);

            return new ModelAndView(new RedirectView(ViewName.PLAYER_SETTINGS.value()));
        }
    }

    private List<Player> getPlayers(HttpServletRequest request) {
        User user = userService.getCurrentUserStrict(request);
        String username = user.getUsername();
        List<Player> players = playerService.getAllPlayers();
        List<Player> authorizedPlayers = new ArrayList<>();
        for (Player player : players) {
            // - Guest user is not displayed
            // - Only display authorized players.
            if (!User.USERNAME_GUEST.equals(player.getUsername())
                    && (user.isAdminRole() || username.equals(player.getUsername()))) {
                authorizedPlayers.add(player);
            }
        }
        return authorizedPlayers;
    }

    private void deleteOrClone(HttpServletRequest request, Model model)
            throws ServletRequestBindingException {
        if (request.getParameter(Attributes.Request.DELETE.value()) != null) {
            Integer delete = ServletRequestUtils
                .getIntParameter(request, Attributes.Request.DELETE.value());
            if (delete != null) {
                playerService.removePlayerById(delete);
            }
        } else if (request.getParameter(Attributes.Request.CLONE.value()) != null) {
            Integer clone = ServletRequestUtils
                .getIntParameter(request, Attributes.Request.CLONE.value());
            if (clone != null) {
                Player clonedPlayer = playerService.clonePlayer(clone);
                model.addAttribute(Attributes.Redirect.PLAYER_ID.value(), clonedPlayer.getId());
            }
        }
    }
}

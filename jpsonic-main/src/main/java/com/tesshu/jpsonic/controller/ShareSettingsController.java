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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.entity.Share;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Controller for the page used to administrate the set of shared media.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/shareSettings", "/shareSettings.view" })
public class ShareSettingsController {

    private final SettingsFacade settingsFacade;
    private final MusicFolderService musicFolderService;
    private final UserService userService;
    private final ShareService shareService;
    private final MediaFileService mediaFileService;

    public ShareSettingsController(SettingsFacade settingsFacade,
            MusicFolderService musicFolderService, UserService userService,
            ShareService shareService, MediaFileService mediaFileService) {
        super();
        this.settingsFacade = settingsFacade;
        this.musicFolderService = musicFolderService;
        this.userService = userService;
        this.shareService = shareService;
        this.mediaFileService = mediaFileService;
    }

    @GetMapping
    public String doGet(HttpServletRequest request, Model model) {
        // spotless:off
        model.addAttribute("model", LegacyMap.of(
                "shareInfos", getShareInfos(request),
                "user", userService.getCurrentUserStrict(request),
                "useRadio", settingsFacade.get(SKeys.general.legacy.useRadio),
                "useDatabaseSettings", settingsFacade.get(SKeys.general.legacy.useDatabaseSettings),
                "shareCount", shareService.getAllShares().size()));
        // spotless:on
        return "shareSettings";
    }

    @PostMapping
    public ModelAndView doPost(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        handleParameters(request);
        redirectAttributes.addFlashAttribute(Attributes.Redirect.TOAST_FLAG.value(), true);
        return new ModelAndView(new RedirectView(ViewName.SHARE_SETTINGS.value()));
    }

    private void handleParameters(HttpServletRequest request) {
        User user = userService.getCurrentUserStrict(request);
        for (Share share : shareService.getSharesForUser(user)) {
            int id = share.getId();

            String description = getParameter(request, Attributes.Request.DESCRIPTION.value(), id);
            boolean delete = getParameter(request, Attributes.Request.DELETE.value(), id) != null;
            String expireIn = getParameter(request, Attributes.Request.EXPIRE_IN.value(), id);

            if (delete) {
                shareService.deleteShare(id);
            } else {
                if (expireIn != null) {
                    share.setExpires(parseExpireIn(expireIn));
                }
                share.setDescription(description);
                shareService.updateShare(share);
            }
        }

        boolean deleteExpired = ServletRequestUtils
            .getBooleanParameter(request, Attributes.Request.DELETE_EXPIRED.value(), false);
        if (deleteExpired) {
            Instant now = now();
            for (Share share : shareService.getSharesForUser(user)) {
                Instant expires = share.getExpires();
                if (expires != null && expires.isBefore(now)) {
                    shareService.deleteShare(share.getId());
                }
            }
        }
    }

    private List<ShareInfo> getShareInfos(HttpServletRequest request) {
        List<ShareInfo> result = new ArrayList<>();
        User user = userService.getCurrentUserStrict(request);
        List<MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername());

        for (Share share : shareService.getSharesForUser(user)) {
            List<MediaFile> files = shareService.getSharedFiles(share.getId(), musicFolders);
            if (!files.isEmpty()) {
                MediaFile file = files.get(0);
                result
                    .add(new ShareInfo(shareService.getShareUrl(request, share), new ShareVO(share),
                            file.isDirectory() ? file : mediaFileService.getParentOf(file)));
            }
        }
        return result;
    }

    private String getParameter(HttpServletRequest request, String name, int id) {
        return StringUtils.trimToNull(request.getParameter(name + "[" + id + "]"));
    }

    private Instant parseExpireIn(String expireIn) {
        int days = Integer.parseInt(expireIn);
        if (days == 0) {
            return null;
        }
        return now().plus(days, ChronoUnit.DAYS);
    }

    public static class ShareInfo {
        private final String shareUrl;
        private final ShareVO share;
        private final MediaFile dir;

        public ShareInfo(String shareUrl, ShareVO share, MediaFile dir) {
            this.shareUrl = shareUrl;
            this.share = share;
            this.dir = dir;
        }

        public ShareVO getShare() {
            return share;
        }

        public String getShareUrl() {
            return shareUrl;
        }

        public MediaFile getDir() {
            return dir;
        }
    }

    public static class ShareVO extends Share {

        public ShareVO(Share share) {
            super(share.getId(), share.getName(), share.getDescription(), share.getUsername(),
                    share.getCreated(), share.getExpires(), share.getLastVisited(),
                    share.getVisitCount());
        }

        public ZonedDateTime getCreatedWithZone() {
            return ZonedDateTime.ofInstant(getCreated(), ZoneId.systemDefault());
        }

        public ZonedDateTime getExpiresWithZone() {
            return getExpires() == null ? null
                    : ZonedDateTime.ofInstant(getExpires(), ZoneId.systemDefault());
        }

        public ZonedDateTime getLastVisitedWithZone() {
            return getLastVisited() == null ? null
                    : ZonedDateTime.ofInstant(getLastVisited(), ZoneId.systemDefault());
        }
    }
}

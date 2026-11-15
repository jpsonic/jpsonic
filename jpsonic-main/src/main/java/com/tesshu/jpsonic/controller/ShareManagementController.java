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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.PlayQueue;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.persistence.api.entity.Share;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.PlaylistService;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for sharing music on Twitter, Facebook etc.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/createShare", "/createShare.view" })
public class ShareManagementController {

    private final MediaFileService mediaFileService;
    private final UserService userService;
    private final ShareService shareService;
    private final PlayerService playerService;
    private final PlaylistService playlistService;

    public ShareManagementController(MediaFileService mediaFileService, UserService userService,
            ShareService shareService, PlayerService playerService,
            PlaylistService playlistService) {
        super();
        this.mediaFileService = mediaFileService;
        this.userService = userService;
        this.shareService = shareService;
        this.playerService = playerService;
        this.playlistService = playlistService;
    }

    @GetMapping
    public ModelAndView createShare(HttpServletRequest request)
            throws ServletRequestBindingException {

        List<MediaFile> files = getMediaFiles(request);
        MediaFile dir = null;
        if (!files.isEmpty()) {
            dir = files.get(0);
            if (!dir.isAlbum()) {
                dir = mediaFileService.getParentOf(dir);
            }
        }

        Share share = shareService.createShare(request, files);
        String description = getDescription(request);
        if (description != null) {
            share.setDescription(description);
            shareService.updateShare(share);
        }

        return new ModelAndView("createShare", "model",
                LegacyMap
                    .of("dir", dir, "user", userService.getCurrentUserStrict(request), "playUrl",
                            shareService.getShareUrl(request, share)));
    }

    private String getDescription(HttpServletRequest request)
            throws ServletRequestBindingException {
        Integer playlistId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.PLAYLIST.value());
        return playlistId == null ? null : playlistService.getPlaylistStrict(playlistId).getName();
    }

    private List<MediaFile> getMediaFiles(HttpServletRequest request)
            throws ServletRequestBindingException {
        Integer id = ServletRequestUtils.getIntParameter(request, Attributes.Request.ID.value());
        Integer playerId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.PLAYER.value());
        Integer playlistId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.PLAYLIST.value());

        List<MediaFile> result = new ArrayList<>();
        if (id != null) {
            MediaFile album = mediaFileService.getMediaFile(id);
            int[] indexes = ServletRequestUtils
                .getIntParameters(request, Attributes.Request.I.value());
            if (indexes.length == 0) {
                return Arrays.asList(album);
            }
            List<MediaFile> children = mediaFileService.getChildrenOf(album, true, false);
            for (int index : indexes) {
                result.add(children.get(index));
            }
        } else if (playerId != null) {
            Player player = playerService.getPlayerById(playerId);
            if (player == null) {
                throw new IllegalArgumentException("The specified Player cannot be found.");
            }
            PlayQueue playQueue = player.getPlayQueue();
            result = playQueue.getFiles();
        } else if (playlistId != null) {
            result = playlistService.getFilesInPlaylist(playlistId);
        }

        return result;
    }

}

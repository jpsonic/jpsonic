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
import java.util.List;

import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for changing cover art.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/changeCoverArt", "/changeCoverArt.view" })
public class ChangeCoverArtController {

    private final UserService userService;
    private final LibraryAccessPolicy libraryAccessPolicy;
    private final MediaFileService mediaFileService;

    public ChangeCoverArtController(UserService userService,
            LibraryAccessPolicy libraryAccessPolicy, MediaFileService mediaFileService) {
        super();
        this.userService = userService;
        this.libraryAccessPolicy = libraryAccessPolicy;
        this.mediaFileService = mediaFileService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request,
            HttpServletResponse response) throws ServletRequestBindingException {

        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        MediaFile dir = mediaFileService.getMediaFileStrict(id);

        String artist = request.getParameter(Attributes.Request.ARTIST.value());
        String album = request.getParameter(Attributes.Request.ALBUM.value());
        if (StringUtils.isBlank(artist)) {
            artist = dir.getArtist();
        }
        if (StringUtils.isBlank(album)) {
            album = dir.getAlbumName();
        }

        String username = userService.getCurrentUsernameStrict(request);
        UserSettings userSettings = userService.getUserSettings(username);
        return new ModelAndView("changeCoverArt", "model",
                LegacyMap
                    .of("id", id, "artist", artist, "album", album, "ancestors", getAncestors(dir),
                            "breadcrumbIndex", userSettings.isBreadcrumbIndex(), "dir", dir,
                            "selectedMusicFolder", userService.getSelectedMusicFolder(username)));
    }

    private List<MediaFile> getAncestors(MediaFile dir) {
        List<MediaFile> result = new ArrayList<>();
        if (libraryAccessPolicy.isInPodcastFolder(dir.toPath())) {
            // For podcasts, don't use ancestors
            return result;
        }

        MediaFile parent = mediaFileService.getParentOf(dir);
        while (parent != null && !mediaFileService.isRoot(parent)) {
            result.add(parent);
            parent = mediaFileService.getParentOf(parent);
        }
        result.add(dir);
        return result;
    }
}

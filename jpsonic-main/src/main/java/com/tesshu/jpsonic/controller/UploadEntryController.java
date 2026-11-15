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
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.controller;

import java.nio.file.Path;
import java.util.List;

import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping({ "/uploadEntry", "/uploadEntry.view" })
public class UploadEntryController {

    private final MusicFolderService musicFolderService;
    private final UserService userService;
    private final ScannerStateProvider scannerStateProvider;

    public UploadEntryController(MusicFolderService musicFolderService, UserService userService,
            ScannerStateProvider scannerStateProvider) {
        super();
        this.musicFolderService = musicFolderService;
        this.userService = userService;
        this.scannerStateProvider = scannerStateProvider;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request) {

        User user = userService.getCurrentUserStrict(request);

        String uploadDirectory = null;
        List<MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername());
        if (!musicFolders.isEmpty()) {
            uploadDirectory = Path.of(musicFolders.get(0).getPathString(), "Incoming").toString();
        }

        ModelAndView result = new ModelAndView();
        result
            .addObject("model",
                    LegacyMap
                        .of("user", user, "uploadDirectory", uploadDirectory, "musicFolders",
                                musicFolders, "scanning", scannerStateProvider.isScanning()));
        return result;
    }
}

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
import java.util.List;
import java.util.Map;

import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.service.AvatarService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.fileupload2.core.FileItemFactory;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.jakarta.JakartaServletFileUpload;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller which receives uploaded avatar images.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/avatarUpload.view")
public class AvatarUploadController {

    private static final Logger LOG = LoggerFactory.getLogger(AvatarUploadController.class);

    private final UserService userService;
    private final AvatarService avatarService;

    public AvatarUploadController(UserService userService, AvatarService avatarService) {
        super();
        this.userService = userService;
        this.avatarService = avatarService;
    }

    @SuppressWarnings({ "PMD.UseDiamondOperator", "rawtypes", "unchecked" })
    // TODO UseDiamondOperator -> 114.0.0.beta.1
    @PostMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request)
            throws FileUploadException {

        // Check that we have a file upload request.
        if (!JakartaServletFileUpload.isMultipartContent(request)) {
            throw new IllegalArgumentException("Illegal request.");
        }

        String username = userService.getCurrentUsername(request);

        FileItemFactory factory = DiskFileItemFactory.builder().get();
        JakartaServletFileUpload upload = new JakartaServletFileUpload(factory);
        List<FileItem> items = upload.parseRequest(request);

        Map<String, Object> map = LegacyMap.of();
        // Look for file items.
        for (FileItem item : items) {
            if (!item.isFormField()) {
                createAvatar(item, username, map);
                break;
            }
        }
        map.put("username", username);
        map.put("avatar", avatarService.getCustomAvatar(username));
        return new ModelAndView("avatarUploadResult", "model", map);
    }

    private void createAvatar(FileItem<?> fileItem, String username, Map<String, Object> map) {
        if (StringUtils.isNotBlank(fileItem.getName()) && fileItem.getSize() > 0) {
            try {
                boolean resized = avatarService
                    .createAvatar(fileItem.getFieldName(), fileItem.getInputStream(),
                            fileItem.getSize(), username);
                map.put("resized", resized);
            } catch (IOException e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Failed to upload personal image: " + e, e);
                }
                map.put("error", e);
            }
        } else {
            map.put("error", new Exception("Missing file."));
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to upload personal image. No file specified.");
            }
        }
    }
}

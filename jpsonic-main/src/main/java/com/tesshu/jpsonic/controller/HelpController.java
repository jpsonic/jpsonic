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
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.tesshu.jpsonic.SuppressLint;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.infrastructure.metadata.BuildInfoProvider;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for the help page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/help", "/help.view" })
public class HelpController {

    private static final Logger LOG = LoggerFactory.getLogger(HelpController.class);
    private static final int LOG_LINES_TO_SHOW = 50;

    private final BuildInfoProvider buildInfoProvider;
    private final UserService userService;

    public HelpController(BuildInfoProvider buildInfoProvider, UserService userService) {
        super();
        this.buildInfoProvider = buildInfoProvider;
        this.userService = userService;
    }

    @GetMapping
    @SuppressLint(value = "CROSS_SITE_SCRIPTING", justification = "False positive. VersionService reads static local files.")
    protected ModelAndView get(HttpServletRequest request) {
        Map<String, Object> map = LegacyMap.of();

        if (buildInfoProvider.isNewVersionAvailable()) {
            map.put("newVersionAvailable", true);
            map.put("latestVersion", buildInfoProvider.getLatestVersion());
        }

        User user = userService.getCurrentUserStrict(request);
        map.put("user", user);
        map.put("admin", userService.isAdmin(user.getUsername()));
        map.put("brand", EnvironmentProvider.getInstance().getBrand());
        map.put("localVersion", buildInfoProvider.getLocalVersion());
        map.put("buildDate", buildInfoProvider.getLocalBuildDate());
        map.put("buildNumber", buildInfoProvider.getLocalBuildNumber());
        Path logFile = EnvironmentProvider.getInstance().getLogFilePath();
        if (Files.exists(logFile)) {
            List<String> latestLogEntries = getLatestLogEntries(logFile);
            map.put("logEntries", latestLogEntries);
            map.put("logFile", logFile);
            try {
                LocalDateTime localDateTime = Files
                    .getLastModifiedTime(logFile)
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
                map
                    .put("lastModified", localDateTime
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return new ModelAndView("help", "model", map);
    }

    private static List<String> getLatestLogEntries(Path logFile) {
        List<String> lines = new ArrayList<>(LOG_LINES_TO_SHOW);
        try (ReversedLinesFileReader reader = ReversedLinesFileReader
            .builder()
            .setPath(logFile)
            .setCharset(Charset.defaultCharset())
            .get()) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (lines.size() >= LOG_LINES_TO_SHOW) {
                    break;
                }
                lines.add(0, line);
            }
            return Collections.unmodifiableList(lines);
        } catch (IOException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Could not open log file " + logFile, e);
            }
            return Collections.emptyList();
        }
    }

}

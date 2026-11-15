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
 * (C) 2015 Sindre Mehus
 * (C) 2016 Airsonic Authors
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.controller;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.domain.system.CoverArtScheme;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.persistence.api.entity.PodcastChannel;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.service.PodcastService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for the "Podcast channels" page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/podcastChannels", "/podcastChannels.view" })
public class PodcastChannelsController {

    private final UserService userService;
    private final PodcastService podcastService;
    private final ScannerStateProvider scannerStateProvider;
    private final ViewAsListSelector viewSelector;

    public PodcastChannelsController(UserService userService, PodcastService podcastService,
            ScannerStateProvider scannerStateProvider, ViewAsListSelector viewSelector) {
        super();
        this.userService = userService;
        this.podcastService = podcastService;
        this.scannerStateProvider = scannerStateProvider;
        this.viewSelector = viewSelector;
    }

    @GetMapping
    @SuppressWarnings("PMD.UseConcurrentHashMap")
    /*
     * LinkedHashMap used in Legacy code. Should be triaged in #831.
     */
    protected ModelAndView get(HttpServletRequest request, HttpServletResponse response) {

        Map<PodcastChannel, List<com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode>> channels = new LinkedHashMap<>();
        Map<Integer, PodcastChannel> channelMap = LegacyMap.of();
        for (PodcastChannel channel : podcastService.getAllChannels()) {
            channels.put(channel, podcastService.getEpisodes(channel.getId()));
            channelMap.put(channel.getId(), channel);
        }

        User user = userService.getCurrentUserStrict(request);
        Map<String, Object> map = LegacyMap
            .of("user", userService.getCurrentUserStrict(request), "channels", channels,
                    "channelMap", channelMap, "newestEpisodes",
                    podcastService
                        .getNewestEpisodes(10)
                        .stream()
                        .map(PodcastEpisode::new)
                        .collect(Collectors.toList()),
                    "viewAsList", viewSelector.isViewAsList(request, user.getUsername()),
                    "coverArtSize", CoverArtScheme.MEDIUM.getSize(), "scanning",
                    scannerStateProvider.isScanning());

        ModelAndView result = new ModelAndView();
        result.addObject("model", map);
        return result;
    }

    // VO
    public static class PodcastEpisode
            extends com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode {

        public PodcastEpisode(com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode episode) {
            super(episode.getId(), episode.getChannelId(), episode.getUrl(), episode.getPath(),
                    episode.getTitle(), episode.getDescription(), episode.getPublishDate(),
                    episode.getDuration(), episode.getBytesTotal(), episode.getBytesDownloaded(),
                    episode.getStatus(), episode.getErrorMessage());
        }

        public ZonedDateTime getPublishDateWithZone() {
            return getPublishDate() == null ? null
                    : ZonedDateTime.ofInstant(getPublishDate(), ZoneId.systemDefault());
        }
    }
}

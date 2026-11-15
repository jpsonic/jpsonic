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

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.domain.system.PodcastStatus;
import com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode;
import com.tesshu.jpsonic.service.PodcastService;
import com.tesshu.jpsonic.util.StringUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Controller for the "Podcast receiver" page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/podcastReceiverAdmin.view")
public class PodcastReceiverAdminController {

    private final PodcastService podcastService;
    private final ScannerStateProvider scannerStateProvider;

    public PodcastReceiverAdminController(PodcastService podcastService,
            ScannerStateProvider scannerStateProvider) {
        super();
        this.podcastService = podcastService;
        this.scannerStateProvider = scannerStateProvider;
    }

    @GetMapping
    protected ModelAndView get(HttpServletRequest request) throws ServletRequestBindingException {

        if (scannerStateProvider.isScanning()) {
            return createModelAndView();
        }

        final Integer channelId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.CHANNEL_ID.value());
        if (!isEmpty(request.getParameter(Attributes.Request.REFRESH.value()))) {
            if (channelId == null) {
                podcastService.refreshAllChannels(true);
                return createModelAndView();
            } else {
                podcastService.refreshChannel(channelId, true);
                return createRedirect(channelId);
            }
        }

        if (isEmpty(channelId)) {
            return createModelAndView();
        }

        String deleteEpisodeId = request.getParameter(Attributes.Request.DELETE_EPISODE.value());
        if (deleteEpisode(deleteEpisodeId)) {
            return createRedirect(channelId);
        }

        String downloadEpisodeIds = request
            .getParameter(Attributes.Request.DOWNLOAD_EPISODE.value());
        if (!isEmpty(downloadEpisodeIds)) {
            download(StringUtil.parseInts(downloadEpisodeIds));
            return createRedirect(channelId);
        }

        if (!isEmpty(request.getParameter(Attributes.Request.DELETE_CHANNEL.value()))
                && channelId != null) {
            podcastService.deleteChannel(channelId);
        }

        return createModelAndView();
    }

    @PostMapping
    protected ModelAndView post(HttpServletRequest request) throws ServletRequestBindingException {
        String addURL = request.getParameter(Attributes.Request.ADD.value());
        if (!isEmpty(addURL)) {
            podcastService.createChannel(StringUtils.trim(addURL));
        }
        return createModelAndView();
    }

    private ModelAndView createModelAndView() {
        return new ModelAndView(new RedirectView(ViewName.PODCAST_CHANNELS.value()));
    }

    private ModelAndView createRedirect(Integer channelId) {
        return new ModelAndView(new RedirectView(ViewName.PODCAST_CHANNELS.value() + "?"
                + Attributes.Request.ID.value() + "=" + channelId));
    }

    private boolean deleteEpisode(String deleteEpisodeId) {
        if (!isEmpty(deleteEpisodeId)) {
            for (int episodeId : StringUtil.parseInts(deleteEpisodeId)) {
                podcastService.deleteEpisode(episodeId, true);
            }
            return true;
        }
        return false;
    }

    private void download(int... episodeIds) {
        for (int episodeId : episodeIds) {
            PodcastEpisode episode = podcastService.getEpisode(episodeId, false);
            if (episode != null && episode.getUrl() != null
                    && (episode.getStatus() == PodcastStatus.NEW
                            || episode.getStatus() == PodcastStatus.ERROR
                            || episode.getStatus() == PodcastStatus.SKIPPED)) {
                podcastService.downloadEpisode(episode);
            }
        }
    }
}

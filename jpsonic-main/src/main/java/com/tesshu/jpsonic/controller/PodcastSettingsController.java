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

import com.tesshu.jpsonic.controller.form.PodcastSettingsCommand;
import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.infrastructure.filesystem.RootPathEntryGuard;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Controller for the page used to administrate the Podcast receiver.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/podcastSettings", "/podcastSettings.view" })
public class PodcastSettingsController {

    private final SettingsFacade settingsFacade;
    private final ScannerStateProvider scannerStateProvider;

    public PodcastSettingsController(SettingsFacade settingsFacade,
            ScannerStateProvider scannerStateProvider) {
        super();
        this.settingsFacade = settingsFacade;
        this.scannerStateProvider = scannerStateProvider;
    }

    @GetMapping
    protected String formBackingObject(Model model) {
        PodcastSettingsCommand command = new PodcastSettingsCommand();

        command.setInterval(String.valueOf(settingsFacade.get(SKeys.podcast.updateInterval)));
        command
            .setEpisodeRetentionCount(
                    String.valueOf(settingsFacade.get(SKeys.podcast.episodeRetentionCount)));
        command
            .setEpisodeDownloadCount(
                    String.valueOf(settingsFacade.get(SKeys.podcast.episodeDownloadCount)));
        command.setFolder(settingsFacade.get(SKeys.podcast.folder));

        // for view page control
        command.setScanning(scannerStateProvider.isScanning());

        model.addAttribute(Attributes.Model.Command.VALUE, command);
        return "podcastSettings";
    }

    @PostMapping
    protected ModelAndView doSubmitAction(
            @ModelAttribute(Attributes.Model.Command.VALUE) PodcastSettingsCommand command,
            RedirectAttributes redirectAttributes) {

        if (!scannerStateProvider.isScanning()) {
            settingsFacade
                .staging(SKeys.podcast.updateInterval, Integer.parseInt(command.getInterval()));
            settingsFacade
                .staging(SKeys.podcast.episodeRetentionCount,
                        Integer.parseInt(command.getEpisodeRetentionCount()));
            settingsFacade
                .staging(SKeys.podcast.episodeDownloadCount,
                        Integer.parseInt(command.getEpisodeDownloadCount()));
            RootPathEntryGuard
                .validateFolderPath(command.getFolder())
                .ifPresent(folder -> settingsFacade.staging(SKeys.podcast.folder, folder));

            settingsFacade.commitAll();
            redirectAttributes.addFlashAttribute(Attributes.Redirect.TOAST_FLAG.value(), true);
        }

        return new ModelAndView(new RedirectView(ViewName.PODCAST_SETTINGS.value()));
    }
}

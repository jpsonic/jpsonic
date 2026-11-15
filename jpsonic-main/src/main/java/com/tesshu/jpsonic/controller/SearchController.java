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

import com.tesshu.jpsonic.controller.form.SearchCommand;
import com.tesshu.jpsonic.infrastructure.scanner.HttpSearchCriteriaDirector;
import com.tesshu.jpsonic.infrastructure.scanner.IndexType;
import com.tesshu.jpsonic.infrastructure.search.LegacySearch;
import com.tesshu.jpsonic.infrastructure.search.LegacySearchResult;
import com.tesshu.jpsonic.infrastructure.search.criteria.HttpSearchCriteria;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for the search page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/search", "/search.view" })
public class SearchController {

    private static final int MATCH_COUNT = 25;

    private final SettingsFacade settingsFacade;
    private final MusicFolderService musicFolderService;
    private final UserService userService;
    private final PlayerService playerService;
    private final LegacySearch legacySearch;
    private final HttpSearchCriteriaDirector director;

    public SearchController(SettingsFacade settingsFacade, MusicFolderService musicFolderService,
            UserService userService, PlayerService playerService, LegacySearch legacySearch,
            HttpSearchCriteriaDirector director) {
        super();
        this.settingsFacade = settingsFacade;
        this.musicFolderService = musicFolderService;
        this.userService = userService;
        this.playerService = playerService;
        this.legacySearch = legacySearch;
        this.director = director;
    }

    @GetMapping
    protected String displayForm() {
        return "search";
    }

    @ModelAttribute
    protected void formBackingObject(Model model) {
        model.addAttribute(Attributes.Model.Command.VALUE, new SearchCommand());
    }

    @PostMapping
    protected String onSubmit(HttpServletRequest request, HttpServletResponse response,
            @ModelAttribute(Attributes.Model.Command.VALUE) SearchCommand command)
            throws ServletRequestBindingException, IOException {

        User user = userService.getCurrentUserStrict(request);
        UserSettings userSettings = userService.getUserSettings(user.getUsername());
        command.setUser(user);
        command.setPartyModeEnabled(userSettings.isPartyModeEnabled());
        command.setComposerVisible(userSettings.getMainVisibility().isComposerVisible());
        command.setGenreVisible(userSettings.getMainVisibility().isGenreVisible());
        command.setSimpleDisplay(userSettings.isSimpleDisplay());

        List<MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername());
        String query = StringUtils.trimToNull(command.getQuery());

        if (query != null) {

            int offset = 0;
            int count = MATCH_COUNT;
            boolean includeComposer = settingsFacade.get(SKeys.general.search.searchComposer)
                    || userSettings.getMainVisibility().isComposerVisible();

            HttpSearchCriteria criteria = director
                .construct(query, offset, count, includeComposer, musicFolders, IndexType.ARTIST);
            LegacySearchResult artists = legacySearch.search(criteria);
            command.setArtists(artists.getMediaFiles());

            criteria = director
                .construct(query, offset, count, includeComposer, musicFolders, IndexType.ALBUM);
            LegacySearchResult albums = legacySearch.search(criteria);
            command.setAlbums(albums.getMediaFiles());

            criteria = director
                .construct(query, offset, count, includeComposer, musicFolders, IndexType.SONG);
            LegacySearchResult songs = legacySearch.search(criteria);
            command.setSongs(songs.getMediaFiles());

            command.setPlayer(playerService.getPlayer(request, response));
        }

        return "search";
    }
}

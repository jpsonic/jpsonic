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
 * (C) 2021 tesshucom
 */

package com.tesshu.jpsonic.controller;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.controller.form.SearchCommand;
import com.tesshu.jpsonic.infrastructure.scanner.HttpSearchCriteriaDirector;
import com.tesshu.jpsonic.infrastructure.search.LegacySearch;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.ModelAndView;

class SearchControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setup() throws ExecutionException {
        SettingsFacade settingsFacade = SettingsFacadeBuilder.create().build();
        mockMvc = MockMvcBuilders
            .standaloneSetup(new SearchController(settingsFacade, mock(MusicFolderService.class),
                    mock(UserService.class), mock(PlayerService.class), mock(LegacySearch.class),
                    mock(HttpSearchCriteriaDirector.class)))
            .build();
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testDisplayForm() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/search.view"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals("search", modelAndView.getViewName());

        SearchCommand command = (SearchCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testOnSubmit() throws Exception {

        SearchCommand command = new SearchCommand();
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders
                .post("/search.view")
                .flashAttr(Attributes.Model.Command.VALUE, command))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);

        ModelAndView modelAndView = result.getModelAndView();
        assertEquals("search", modelAndView.getViewName());

        command = (SearchCommand) modelAndView.getModelMap().get(Attributes.Model.Command.VALUE);
        assertNotNull(command);
    }
}

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

import com.tesshu.jpsonic.controller.form.PodcastSettingsCommand;
import com.tesshu.jpsonic.infrastructure.scanner.ScannerStateServiceImpl;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.ModelAndView;

class PodcastSettingsControllerTest {

    private MockMvc mockMvc;
    private SettingsFacade settingsFacade;
    private ScannerStateServiceImpl scannerStateService;

    @BeforeEach
    void setup() {
        settingsFacade = SettingsFacadeBuilder.create().build();
        init();
    }

    @Ignore
    void init() {
        scannerStateService = mock(ScannerStateServiceImpl.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new PodcastSettingsController(settingsFacade, scannerStateService))
            .build();
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testFormBackingObject() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.PODCAST_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals("podcastSettings", modelAndView.getViewName());

        PodcastSettingsCommand command = (PodcastSettingsCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testDoSubmitAction() throws Exception {
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        settingsFacade = SettingsFacadeBuilder
            .create()
            .captureInt(SKeys.podcast.updateInterval, captor)
            .build();
        init();

        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.PODCAST_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals("podcastSettings", modelAndView.getViewName());

        PodcastSettingsCommand command = (PodcastSettingsCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);

        result = mockMvc
            .perform(MockMvcRequestBuilders
                .post("/" + ViewName.PODCAST_SETTINGS.value())
                .flashAttr(Attributes.Model.Command.VALUE, command))
            .andExpect(MockMvcResultMatchers.status().isFound())
            .andExpect(MockMvcResultMatchers.redirectedUrl(ViewName.PODCAST_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
            .andReturn();
        assertNotNull(result);
        assertEquals(1, captor.getAllValues().size());

        captor = ArgumentCaptor.forClass(Integer.class);
        settingsFacade = SettingsFacadeBuilder
            .create()
            .captureInt(SKeys.podcast.updateInterval, captor)
            .build();
        init();
        Mockito.when(scannerStateService.isScanning()).thenReturn(true);

        result = mockMvc
            .perform(MockMvcRequestBuilders
                .post("/" + ViewName.PODCAST_SETTINGS.value())
                .flashAttr(Attributes.Model.Command.VALUE, command))
            .andExpect(MockMvcResultMatchers.status().isFound())
            .andExpect(MockMvcResultMatchers.redirectedUrl(ViewName.PODCAST_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
            .andReturn();
        assertNotNull(result);
        assertEquals(0, captor.getAllValues().size());
    }
}

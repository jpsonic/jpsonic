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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tesshu.jpsonic.controller.form.GeneralSettingsCommand;
import com.tesshu.jpsonic.domain.provider.resource.MusicIndexProvider;
import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.domain.system.IndexScheme;
import com.tesshu.jpsonic.feature.theme.ServerThemeService;
import com.tesshu.jpsonic.infrastructure.locale.ServerLocaleManager;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.UserService;
import org.junit.Ignore;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GeneralSettingsControllerTest {

    private static final String VIEW_NAME = "generalSettings";

    private SettingsFacade settingsFacade;
    private GeneralSettingsController controller;
    private MockMvc mockMvc;

    @Ignore
    void init() {
        ServerLocaleManager serverLocaleManager = new ServerLocaleManager(settingsFacade);
        ServerThemeService serverThemeService = new ServerThemeService(settingsFacade);
        controller = new GeneralSettingsController(settingsFacade, mock(UserService.class),
                serverLocaleManager, serverThemeService, mock(ShareService.class),
                mock(OutlineHelpSelector.class), mock(ScannerStateProvider.class),
                mock(MusicIndexProvider.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Order(1)
    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testGet() throws Exception {

        settingsFacade = SettingsFacadeBuilder.create().build();
        init();

        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.GENERAL_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);

        ModelAndView modelAndView = result.getModelAndView();
        assertEquals(VIEW_NAME, modelAndView.getViewName());

        GeneralSettingsCommand command = (GeneralSettingsCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);
    }

    @Order(2)
    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testPost() throws Exception {

        settingsFacade = SettingsFacadeBuilder.create().buildWithDefault();
        init();

        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.GENERAL_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals(VIEW_NAME, modelAndView.getViewName());

        GeneralSettingsCommand command = (GeneralSettingsCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);

        command.setThemeIndex("1");

        result = mockMvc
            .perform(MockMvcRequestBuilders
                .post("/" + ViewName.GENERAL_SETTINGS.value())
                .flashAttr(Attributes.Model.Command.VALUE, command))
            .andExpect(MockMvcResultMatchers.status().isFound())
            .andExpect(MockMvcResultMatchers
                .flash()
                .attribute(Attributes.Redirect.RELOAD_FLAG.value(), Boolean.FALSE))
            .andExpect(MockMvcResultMatchers
                .flash()
                .attribute(Attributes.Redirect.TOAST_FLAG.value(), Boolean.TRUE))
            .andExpect(MockMvcResultMatchers.redirectedUrl(ViewName.GENERAL_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
            .andReturn();
        assertNotNull(result);
    }

    @Order(3)
    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testReload() throws Exception {

        settingsFacade = SettingsFacadeBuilder.create().buildWithDefault();
        init();

        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.GENERAL_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals(VIEW_NAME, modelAndView.getViewName());

        GeneralSettingsCommand command = (GeneralSettingsCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);

        // When the theme is changed
        command.setThemeIndex("1");

        controller.post(command, Mockito.mock(RedirectAttributes.class));
    }

    @Order(3)
    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testPostWithIndexOptions() throws Exception {

        settingsFacade = SettingsFacadeBuilder.create().buildWithDefault();
        init();
        assertEquals(SKeys.general.index.indexString.defaultValue(),
                settingsFacade.get(SKeys.general.index.indexString));

        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.GENERAL_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals(VIEW_NAME, modelAndView.getViewName());

        GeneralSettingsCommand command = (GeneralSettingsCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);

        assertEquals(IndexScheme.NATIVE_JAPANESE, command.getIndexScheme());
        assertTrue(command.isIgnoreFullWidth());
        assertTrue(command.isDeleteDiacritic());
        command.setDeleteDiacritic(false);
        command.setIgnoreFullWidth(false);
        ArgumentCaptor<Boolean> deleteDiacritic = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> ignoreFullWidth = ArgumentCaptor.forClass(Boolean.class);
        settingsFacade = SettingsFacadeBuilder
            .create()
            .captureBoolean(SKeys.advanced.index.deleteDiacritic, deleteDiacritic)
            .captureBoolean(SKeys.advanced.index.ignoreFullWidth, ignoreFullWidth)
            .buildWithDefault();
        init();
        assertEquals(SKeys.general.index.indexString.defaultValue(),
                settingsFacade.get(SKeys.general.index.indexString));

        controller.post(command, Mockito.mock(RedirectAttributes.class));
        assertTrue(deleteDiacritic.getValue());
        assertTrue(ignoreFullWidth.getValue());

        command.setIndexScheme(IndexScheme.ROMANIZED_JAPANESE);
        command.setDeleteDiacritic(true);
        command.setIgnoreFullWidth(false);
        deleteDiacritic = ArgumentCaptor.forClass(Boolean.class);
        ignoreFullWidth = ArgumentCaptor.forClass(Boolean.class);
        settingsFacade = SettingsFacadeBuilder
            .create()
            .captureBoolean(SKeys.advanced.index.deleteDiacritic, deleteDiacritic)
            .captureBoolean(SKeys.advanced.index.ignoreFullWidth, ignoreFullWidth)
            .buildWithDefault();
        init();
        controller.post(command, Mockito.mock(RedirectAttributes.class));
        assertTrue(deleteDiacritic.getValue());
        assertTrue(ignoreFullWidth.getValue());

        command.setIndexScheme(IndexScheme.WITHOUT_JP_LANG_PROCESSING);
        command.setDeleteDiacritic(true);
        command.setIgnoreFullWidth(true);
        deleteDiacritic = ArgumentCaptor.forClass(Boolean.class);
        ignoreFullWidth = ArgumentCaptor.forClass(Boolean.class);
        settingsFacade = SettingsFacadeBuilder
            .create()
            .captureBoolean(SKeys.advanced.index.deleteDiacritic, deleteDiacritic)
            .captureBoolean(SKeys.advanced.index.ignoreFullWidth, ignoreFullWidth)
            .buildWithDefault();
        init();
        controller.post(command, Mockito.mock(RedirectAttributes.class));
        assertTrue(deleteDiacritic.getValue());
        assertTrue(ignoreFullWidth.getValue());
    }
}

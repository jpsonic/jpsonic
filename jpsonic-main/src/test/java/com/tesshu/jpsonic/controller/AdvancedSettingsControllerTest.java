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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.controller.form.AdvancedSettingsCommand;
import com.tesshu.jpsonic.domain.model.AuthKey;
import com.tesshu.jpsonic.domain.model.AuthKey.AuthKeyType;
import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.domain.system.IndexScheme;
import com.tesshu.jpsonic.feature.auth.rememberme.RememberMeForm;
import com.tesshu.jpsonic.feature.auth.rememberme.RememberMeKeyManager;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.UserService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@SuppressWarnings("PMD.TooManyStaticImports")
class AdvancedSettingsControllerTest {

    private static final String VIEW_NAME = "advancedSettings";

    private SettingsFacade settingsFacade;
    private AdvancedSettingsController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() throws ExecutionException {
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.advanced.index.indexSchemeName,
                    SKeys.advanced.index.indexSchemeName.defaultValue())
            .build();
        init();
    }

    @Ignore
    void init() {
        RememberMeKeyManager rememberMeKeyManager = mock(RememberMeKeyManager.class);
        AuthKey authKey = new AuthKey(AuthKeyType.REMEMBERME, "XXX", Instant.now());
        Mockito.when(rememberMeKeyManager.getAuthKey()).thenReturn(authKey);
        controller = new AdvancedSettingsController(settingsFacade, mock(UserService.class),
                rememberMeKeyManager, mock(ShareService.class), mock(OutlineHelpSelector.class),
                mock(ScannerStateProvider.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testGet() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.ADVANCED_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals(VIEW_NAME, modelAndView.getViewName());

        RememberMeForm form = (RememberMeForm) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(form);
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testPost() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.ADVANCED_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals(VIEW_NAME, modelAndView.getViewName());

        RememberMeForm command = (RememberMeForm) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);

        result = mockMvc
            .perform(MockMvcRequestBuilders
                .post("/" + ViewName.ADVANCED_SETTINGS.value())
                .flashAttr(Attributes.Model.Command.VALUE, command))
            .andExpect(MockMvcResultMatchers.status().isFound())
            .andExpect(MockMvcResultMatchers.redirectedUrl(ViewName.ADVANCED_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
            .andReturn();
        assertNotNull(result);
    }

    @Test
    void testSetScanLog() {
        ArgumentCaptor<Integer> scanLogRetention = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Boolean> useScanEvents = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> measureMemory = ArgumentCaptor.forClass(Boolean.class);
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withInt(SKeys.advanced.scanLog.scanLogRetention, -1)
            .captureInt(SKeys.advanced.scanLog.scanLogRetention, scanLogRetention)
            .captureBoolean(SKeys.advanced.scanLog.useScanEvents, useScanEvents)
            .captureBoolean(SKeys.advanced.scanLog.measureMemory, measureMemory)
            .build();
        init();

        AdvancedSettingsCommand command = new AdvancedSettingsCommand();
        command.setUseScanLog(false);
        command.setScanLogRetention(30);
        command.setUseScanEvents(true);
        command.setMeasureMemory(true);

        controller.setScanLog(command);

        assertEquals(-1, scanLogRetention.getValue());
        assertFalse(useScanEvents.getValue());
        assertFalse(measureMemory.getValue());

        command.setUseScanLog(true);
        controller.setScanLog(command);

        assertEquals(30, scanLogRetention.getValue());
        assertTrue(useScanEvents.getValue());
        assertTrue(measureMemory.getValue());
    }

    /*
     * Full scan required if schema changes (Compare before reflecting settings)
     */
    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testChangeIndexScheme() throws Exception {
        ArgumentCaptor<Boolean> forceInternalValueInsteadOfTags = ArgumentCaptor
            .forClass(Boolean.class);
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.advanced.index.indexSchemeName,
                    IndexScheme.WITHOUT_JP_LANG_PROCESSING.name())
            .captureBoolean(SKeys.advanced.index.forceInternalValueInsteadOfTags,
                    forceInternalValueInsteadOfTags)
            .build();
        init();
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.ADVANCED_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);

        ModelAndView modelAndView = result.getModelAndView();
        assertEquals(VIEW_NAME, modelAndView.getViewName());

        AdvancedSettingsCommand command = (AdvancedSettingsCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);

        assertFalse(command.isForceInternalValueInsteadOfTags());
        command.setForceInternalValueInsteadOfTags(true);

        assertEquals(IndexScheme.WITHOUT_JP_LANG_PROCESSING, command.getIndexScheme());
        assertFalse(settingsFacade.get(SKeys.musicFolder.scan.ignoreFileTimestamps));

        command.setIndexScheme(IndexScheme.NATIVE_JAPANESE);
        controller.post(command, Mockito.mock(RedirectAttributes.class));
        assertEquals(1, forceInternalValueInsteadOfTags.getAllValues().size());
        assertFalse(forceInternalValueInsteadOfTags.getValue());

        forceInternalValueInsteadOfTags = ArgumentCaptor.forClass(Boolean.class);
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.advanced.index.indexSchemeName,
                    IndexScheme.WITHOUT_JP_LANG_PROCESSING.name())
            .captureBoolean(SKeys.advanced.index.forceInternalValueInsteadOfTags,
                    forceInternalValueInsteadOfTags)
            .build();
        init();

        command.setIndexScheme(IndexScheme.ROMANIZED_JAPANESE);
        controller.post(command, Mockito.mock(RedirectAttributes.class));
        assertEquals(1, forceInternalValueInsteadOfTags.getAllValues().size());
        assertTrue(forceInternalValueInsteadOfTags.getValue());

        forceInternalValueInsteadOfTags = ArgumentCaptor.forClass(Boolean.class);
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.advanced.index.indexSchemeName, IndexScheme.NATIVE_JAPANESE.name())
            .captureBoolean(SKeys.advanced.index.forceInternalValueInsteadOfTags,
                    forceInternalValueInsteadOfTags)
            .build();
        init();

        command.setIndexScheme(IndexScheme.WITHOUT_JP_LANG_PROCESSING);
        controller.post(command, Mockito.mock(RedirectAttributes.class));
        assertEquals(1, forceInternalValueInsteadOfTags.getAllValues().size());
        controller.post(command, Mockito.mock(RedirectAttributes.class));
        assertEquals(2, forceInternalValueInsteadOfTags.getAllValues().size());

        forceInternalValueInsteadOfTags = ArgumentCaptor.forClass(Boolean.class);
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.advanced.index.indexSchemeName, IndexScheme.NATIVE_JAPANESE.name())
            .captureBoolean(SKeys.advanced.index.forceInternalValueInsteadOfTags,
                    forceInternalValueInsteadOfTags)
            .build();
        init();
        command.setIndexScheme(IndexScheme.WITHOUT_JP_LANG_PROCESSING);
        controller.post(command, Mockito.mock(RedirectAttributes.class));
        assertEquals(1, forceInternalValueInsteadOfTags.getAllValues().size());
        assertFalse(forceInternalValueInsteadOfTags.getValue());
    }
}

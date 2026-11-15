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
import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.domain.model.MusicFolderContent;
import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicIndexProvider;
import com.tesshu.jpsonic.domain.provider.resource.ServerLocaleProvider;
import com.tesshu.jpsonic.feature.i18n.AirsonicLocaleResolver;
import com.tesshu.jpsonic.infrastructure.locale.ServerLocaleManager;
import com.tesshu.jpsonic.infrastructure.metadata.BuildInfoProvider;
import com.tesshu.jpsonic.infrastructure.scanner.ScannerStateServiceImpl;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.service.InternetRadioService;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.servlet.ModelAndView;

@SuppressWarnings("PMD.TooManyStaticImports")
class TopControllerTest {

    private MusicFolderService musicFolderService;
    private ScannerStateServiceImpl scannerState;
    private UserService userService;
    private TopController controller;

    private MockMvc mockMvc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws ExecutionException {
        musicFolderService = mock(MusicFolderService.class);
        scannerState = mock(ScannerStateServiceImpl.class);
        MusicIndexProvider musicIndexProvider = mock(MusicIndexProvider.class);

        SettingsFacade settingsFacade = SettingsFacadeBuilder.create().buildWithDefault();
        ServerLocaleProvider serverLocaleProvider = new ServerLocaleManager(settingsFacade);
        userService = mock(UserService.class);
        AirsonicLocaleResolver airsonicLocaleResolver = new AirsonicLocaleResolver(userService,
                serverLocaleProvider);

        Mockito
            .when(musicIndexProvider.findMusicFolderContent(Mockito.nullable(List.class)))
            .thenReturn(new MusicFolderContent(new TreeMap<>(), Collections.emptyList()));
        controller = new TopController(settingsFacade, musicFolderService, userService,
                scannerState, mock(MusicFolderProvider.class), musicIndexProvider,
                mock(MediaFileProvider.class), mock(BuildInfoProvider.class),
                mock(InternetRadioService.class), airsonicLocaleResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testHandleRequestInternal() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders
                .get("/" + ViewName.TOP.value())
                .param(Attributes.Request.MUSIC_FOLDER_ID.value(), "0"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals("top", modelAndView.getViewName());

        @SuppressWarnings("unchecked")
        Map<String, Object> model = (Map<String, Object>) modelAndView.getModel().get("model");
        assertNotNull(model);
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testGetLastModified() throws ServletRequestBindingException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        assertNotEquals(0, controller.getLastModified(req));
    }

    @Nested
    class GetLastModifiedTest {

        // ... This method is not currently used, but is probably wrong.

        @Test
        void testWithScanning() throws ServletRequestBindingException {
            Mockito.when(scannerState.isScanning()).thenReturn(true);
            MockHttpServletRequest request = mock(MockHttpServletRequest.class);
            long lastModified = controller.getLastModified(request);
            assertEquals(-1L, lastModified);
        }

        @Test
        void testWithoutSelectedMusicFolders()
                throws ServletRequestBindingException, URISyntaxException {
            List<MusicFolder> musicFolders = Arrays
                .asList(new MusicFolder(1,
                        Path
                            .of(TopControllerTest.class
                                .getResource("/MEDIAS/Sort/Pagination/Artists")
                                .toURI())
                            .toString(),
                        "MEDIAS", true, now(), 1, false));
            Mockito
                .when(musicFolderService.getMusicFoldersForUser(Mockito.anyString()))
                .thenReturn(musicFolders);

            MockHttpServletRequest request = mock(MockHttpServletRequest.class);
            long lastModified = controller.getLastModified(request);
            assertNotEquals(-1L, lastModified);
        }

        @Test
        void testWithSelectedMusicFolders()
                throws ServletRequestBindingException, URISyntaxException {
            List<MusicFolder> musicFolders = Arrays
                .asList(new MusicFolder(1,
                        Path
                            .of(TopControllerTest.class
                                .getResource("/MEDIAS/Sort/Pagination/Artists")
                                .toURI())
                            .toString(),
                        "MEDIAS", true, now(), 1, false));
            Mockito
                .when(musicFolderService.getMusicFoldersForUser(Mockito.anyString()))
                .thenReturn(musicFolders);
            Mockito
                .when(userService.getSelectedMusicFolder(Mockito.anyString()))
                .thenReturn(musicFolders.get(0));

            MockHttpServletRequest request = mock(MockHttpServletRequest.class);
            long lastModified = controller.getLastModified(request);
            assertNotEquals(-1L, lastModified);
        }

    }
}

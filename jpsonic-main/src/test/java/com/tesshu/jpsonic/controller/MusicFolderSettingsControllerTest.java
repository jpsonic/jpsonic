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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Documented;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.MusicFolderTestDataUtils;
import com.tesshu.jpsonic.controller.form.MusicFolderSettingsCommand;
import com.tesshu.jpsonic.controller.form.MusicFolderSettingsCommand.MusicFolderInfo;
import com.tesshu.jpsonic.infrastructure.scanner.MusicFolderServiceImpl;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.service.MediaScannerService;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.UserService;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
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
@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals" })
class MusicFolderSettingsControllerTest {

    private static final String VIEW_NAME = "musicFolderSettings";

    private SettingsFacade settingsFacade;
    private MusicFolderServiceImpl musicFolderService;
    private MediaScannerService mediaScannerService;
    private MusicFolderSettingsController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        settingsFacade = SettingsFacadeBuilder.create().build();
        init();
    }

    @Ignore
    void init() {
        musicFolderService = mock(MusicFolderServiceImpl.class);
        mediaScannerService = mock(MediaScannerService.class);
        controller = new MusicFolderSettingsController(settingsFacade, musicFolderService,
                mock(UserService.class), mediaScannerService, mock(ShareService.class),
                mock(OutlineHelpSelector.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Order(1)
    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testGet() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.MUSIC_FOLDER_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals(VIEW_NAME, modelAndView.getViewName());

        MusicFolderSettingsCommand command = (MusicFolderSettingsCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);
    }

    @Order(2)
    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testDoScan() throws Exception {

        Mockito.doNothing().when(mediaScannerService).scanLibrary();

        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders
                .get("/" + ViewName.MUSIC_FOLDER_SETTINGS.value())
                .param(Attributes.Request.NameConstants.SCAN_NOW, "true"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals(VIEW_NAME, modelAndView.getViewName());

        MusicFolderSettingsCommand command = (MusicFolderSettingsCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);

        Mockito.verify(mediaScannerService, Mockito.times(1)).scanLibrary();
    }

    @Order(4)
    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testPost() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.MUSIC_FOLDER_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals(VIEW_NAME, modelAndView.getViewName());

        MusicFolderSettingsCommand command = (MusicFolderSettingsCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);

        result = mockMvc
            .perform(MockMvcRequestBuilders
                .post("/" + ViewName.MUSIC_FOLDER_SETTINGS.value())
                .flashAttr(Attributes.Model.Command.VALUE, command))
            .andExpect(MockMvcResultMatchers.status().isFound())
            .andExpect(MockMvcResultMatchers.redirectedUrl(ViewName.MUSIC_FOLDER_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
            .andReturn();
        assertNotNull(result);
    }

    @Order(5)
    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testCreateMusicFolder() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.MUSIC_FOLDER_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals(VIEW_NAME, modelAndView.getViewName());

        MusicFolderSettingsCommand command = (MusicFolderSettingsCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);

        MusicFolder musicFolder = new MusicFolder(MusicFolderTestDataUtils.resolveMusicFolderPath(),
                "Music", true, now(), false);
        MusicFolderInfo musicFolderInfo = new MusicFolderInfo(musicFolder);
        command.setNewMusicFolder(musicFolderInfo);

        // success case
        ArgumentCaptor<MusicFolder> captor = ArgumentCaptor.forClass(MusicFolder.class);
        Mockito
            .doNothing()
            .when(musicFolderService)
            .createMusicFolder(Mockito.any(Instant.class), captor.capture());
        controller.post(command, Mockito.mock(RedirectAttributes.class));
        assertEquals(captor.getValue().getId(), musicFolder.getId());

        // double registration
        Mockito.clearInvocations(musicFolderService);
        Mockito
            .when(musicFolderService.getAllMusicFolders(false, true))
            .thenReturn(Arrays.asList(musicFolder));
        controller.post(command, Mockito.mock(RedirectAttributes.class));
        Mockito
            .verify(musicFolderService, Mockito.never())
            .createMusicFolder(Mockito.any(Instant.class), Mockito.nullable(MusicFolder.class));
    }

    @Order(6)
    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testUpdateAndDelteMusicFolder() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.MUSIC_FOLDER_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals(VIEW_NAME, modelAndView.getViewName());

        MusicFolderSettingsCommand command = (MusicFolderSettingsCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);

        MusicFolder musicFolder1 = new MusicFolder(99,
                MusicFolderTestDataUtils.resolveMusicFolderPath(), "Music", true, now(), 1, false);
        MusicFolderInfo musicFolderInfo1 = new MusicFolderInfo(musicFolder1);
        musicFolderInfo1.setDelete(true);
        MusicFolder musicFolder2 = new MusicFolder(
                MusicFolderTestDataUtils.resolveMusic2FolderPath(), "Music2", true, now(), false);
        MusicFolderInfo musicFolderInfo2 = new MusicFolderInfo(musicFolder2);
        command.setMusicFolders(Arrays.asList(musicFolderInfo1, musicFolderInfo2));

        // Case where the registered path is deleted on the web page
        MusicFolder musicFolder3 = new MusicFolder(
                MusicFolderTestDataUtils.resolveMusic3FolderPath(), null, true, now(), false);
        MusicFolderInfo musicFolderInfo3 = new MusicFolderInfo(musicFolder3);
        musicFolderInfo3.setPath(null);
        // Cases that do not (already) exist. Update will be executed but will be
        // ignored in Dao.
        MusicFolder musicFolder4 = new MusicFolder("UnknownPath", "Music4", true, now(), false);
        MusicFolderInfo musicFolderInfo4 = new MusicFolderInfo(musicFolder4);

        command
            .setMusicFolders(Arrays
                .asList(musicFolderInfo1, musicFolderInfo2, musicFolderInfo3, musicFolderInfo4));

        ArgumentCaptor<Integer> captorDelete = ArgumentCaptor.forClass(int.class);
        Mockito
            .doNothing()
            .when(musicFolderService)
            .deleteMusicFolder(Mockito.any(Instant.class), captorDelete.capture());
        ArgumentCaptor<MusicFolder> captorUpdate = ArgumentCaptor.forClass(MusicFolder.class);
        Mockito
            .doNothing()
            .when(musicFolderService)
            .updateMusicFolder(Mockito.any(Instant.class), captorUpdate.capture());

        RedirectAttributes redirectAttributes = Mockito.mock(RedirectAttributes.class);
        controller.post(command, redirectAttributes);

        assertEquals(captorDelete.getValue(), musicFolder1.getId());
        assertEquals(2, captorUpdate.getAllValues().size());
        Map<String, MusicFolder> updateCalled = captorUpdate
            .getAllValues()
            .stream()
            .collect(Collectors.toMap(MusicFolder::getName, m -> m));
        assertEquals(MusicFolderTestDataUtils.resolveMusic2FolderPath(),
                updateCalled.get("Music2").getPathString());
        assertEquals("UnknownPath", updateCalled.get("Music4").getPathString());
    }

    @Order(11)
    @Test
    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    void testIfIgnoreFileTimestamps() throws Exception {
        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        settingsFacade = SettingsFacadeBuilder
            .create()
            .captureBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, captor)
            .build();
        init();

        MusicFolderSettingsCommand command = (MusicFolderSettingsCommand) mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.MUSIC_FOLDER_SETTINGS.value()))
            .andReturn()
            .getModelAndView()
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertFalse(command.isIgnoreFileTimestamps());

        command.setIgnoreFileTimestamps(true);
        controller.post(command, Mockito.mock(RedirectAttributes.class));
        assertTrue(captor.getValue());

        captor = ArgumentCaptor.forClass(Boolean.class);
        settingsFacade = SettingsFacadeBuilder
            .create()
            .captureBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, captor)
            .build();
        init();
        command.setIgnoreFileTimestamps(false);
        controller.post(command, Mockito.mock(RedirectAttributes.class));
        assertFalse(captor.getValue());
    }

    @Documented
    private @interface ToMusicFolderDecisions {
        @interface Conditions {
            @interface MusicFolderInfo {
                @interface Path {
                    @interface Null {
                    }

                    @interface NonNull {
                        @interface Root {
                        }

                        @interface Invalid {
                        }

                        @interface Traversal {
                        }

                        @interface NonTraversal {

                            @interface OldPathStartWithNewPath {

                            }

                            @interface NewPathStartWithOldPath {

                            }

                            @interface NonDuplication {

                            }

                            @interface Equals {

                            }
                        }
                    }

                    @interface DirName {
                        @interface Null {
                        }

                        @interface NonNull {
                        }
                    }

                }

                @interface Name {
                    @interface Null {
                    }

                    @interface NonNull {
                    }
                }
            }
        }

        @interface Results {
            @interface Empty {
            }

            @interface NotEmpty {
            }
        }
    }

    @Nested
    class ToMusicFolderTest {

        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Path.Null
        @ToMusicFolderDecisions.Results.Empty
        @Test
        void c01() {
            MusicFolderInfo info = new MusicFolderInfo();
            assertTrue(controller.toMusicFolder(info).isEmpty());
        }

        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Path.NonNull.Traversal
        @ToMusicFolderDecisions.Results.Empty
        @Test
        void c02() {
            MusicFolderInfo info = new MusicFolderInfo();
            String path = "foo/../../bar";
            info.setPath(path);
            assertTrue(controller.toMusicFolder(info).isEmpty());
        }

        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Path.NonNull.NonTraversal.OldPathStartWithNewPath
        @ToMusicFolderDecisions.Results.Empty
        @Test
        void c03() {
            List<MusicFolder> oldMusicFolders = Arrays
                .asList(new MusicFolder(0, "/jpsonic", "old", false, null, 0, false));
            Mockito
                .when(musicFolderService.getAllMusicFolders(true, true))
                .thenReturn(oldMusicFolders);
            MusicFolderInfo info = new MusicFolderInfo();
            String path = "/jpsonic/subDirectory";
            info.setPath(path);
            assertTrue(controller.toMusicFolder(info).isEmpty());
        }

        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Path.NonNull.NonTraversal.NewPathStartWithOldPath
        @ToMusicFolderDecisions.Results.Empty
        @Test
        void c04() {
            List<MusicFolder> oldMusicFolders = Arrays
                .asList(new MusicFolder(0, "/jpsonic/subDirectory", "old", false, null, 0, false));
            Mockito
                .when(musicFolderService.getAllMusicFolders(true, true))
                .thenReturn(oldMusicFolders);
            MusicFolderInfo info = new MusicFolderInfo();
            String path = "/jpsonic";
            info.setPath(path);
            assertTrue(controller.toMusicFolder(info).isEmpty());
        }

        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Path.NonNull.NonTraversal.NonDuplication
        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Name.NonNull
        @ToMusicFolderDecisions.Results.NotEmpty
        @Test
        void c05() {
            List<MusicFolder> oldMusicFolders = Arrays
                .asList(new MusicFolder(0, "/jpsonic", "old", false, null, 0, false));
            Mockito
                .when(musicFolderService.getAllMusicFolders(true, true))
                .thenReturn(oldMusicFolders);
            MusicFolderInfo info = new MusicFolderInfo();
            String path = "foo/bar";
            info.setPath(path);
            info.setName("name");
            assertFalse(controller.toMusicFolder(info).isEmpty());
        }

        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Path.NonNull.NonTraversal.Equals
        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Name.NonNull
        @ToMusicFolderDecisions.Results.NotEmpty
        @Test
        void c06() {
            List<MusicFolder> oldMusicFolders = Arrays
                .asList(new MusicFolder(0, "/jpsonic", "old", false, null, 0, false));
            Mockito
                .when(musicFolderService.getAllMusicFolders(true, true))
                .thenReturn(oldMusicFolders);
            MusicFolderInfo info = new MusicFolderInfo();
            String path = "/jpsonic";
            info.setPath(path);
            info.setName("name");
            assertFalse(controller.toMusicFolder(info).isEmpty());
        }

        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Path.NonNull.NonTraversal.NonDuplication
        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Name.Null
        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Path.DirName.NonNull
        @ToMusicFolderDecisions.Results.NotEmpty
        @Test
        void c07() {
            MusicFolderInfo info = new MusicFolderInfo();
            String path = "foo/bar";
            info.setPath(path);
            assertFalse(controller.toMusicFolder(info).isEmpty());
        }

        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Path.NonNull.Root
        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Name.Null
        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Path.DirName.Null
        @ToMusicFolderDecisions.Results.Empty
        @Test
        void c08() {
            MusicFolderInfo info = new MusicFolderInfo();
            String path = "/";
            info.setPath(path);
            assertTrue(controller.toMusicFolder(info).isEmpty());
        }

        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Path.NonNull.Invalid
        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Name.Null
        @ToMusicFolderDecisions.Conditions.MusicFolderInfo.Path.DirName.Null
        @ToMusicFolderDecisions.Results.Empty
        @EnabledOnOs(OS.WINDOWS)
        @Test
        void c09() {
            MusicFolderInfo info = new MusicFolderInfo();
            String path = "/:";
            info.setPath(path);
            assertTrue(controller.toMusicFolder(info).isEmpty());
        }
    }
}

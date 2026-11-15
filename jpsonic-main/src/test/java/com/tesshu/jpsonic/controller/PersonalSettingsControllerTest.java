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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Documented;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.controller.form.PersonalSettingsCommand;
import com.tesshu.jpsonic.domain.language.StringUtil;
import com.tesshu.jpsonic.domain.system.AvatarScheme;
import com.tesshu.jpsonic.domain.system.FontScheme;
import com.tesshu.jpsonic.domain.system.SpeechToTextLangScheme;
import com.tesshu.jpsonic.infrastructure.core.NeedsHome;
import com.tesshu.jpsonic.infrastructure.locale.ServerLocaleManager;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@SpringBootTest
@ActiveProfiles("test")
@NeedsHome
@SuppressWarnings("PMD.TooManyStaticImports")
class PersonalSettingsControllerTest {

    private static final String VIEW_NAME = "personalSettings";

    @Autowired
    private PersonalSettingsController controller;

    @Autowired
    private ServerLocaleManager serverLocaleManager;

    @Autowired
    private SettingsFacade settingsFacade;

    @Autowired
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() throws ExecutionException {
        serverLocaleManager.stagingLocale(Locale.US);
        settingsFacade.commitAll();
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testDisplayForm() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals(VIEW_NAME, modelAndView.getViewName());

        PersonalSettingsCommand command = (PersonalSettingsCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertEquals("random", command.getAlbumListId());
        assertEquals(10, command.getAlbumLists().size());
        assertTrue(command.isAlternativeDrawer());
        assertTrue(command.isAssignAccesskeyToNumber());
        assertTrue(command.isAutoHidePlayQueue());
        assertEquals(-1, command.getAvatarId());
        assertEquals(30, command.getAvatars().size());
        assertTrue(command.isBreadcrumbIndex());
        assertFalse(command.isCloseDrawer());
        assertTrue(command.isClosePlayQueue());
        assertNull(command.getCustomAvatar());
        assertNotNull(command.getDefaultSettings());
        assertFalse(command.isFinalVersionNotificationEnabled());
        assertFalse(command.isForceBio2Eng());
        assertNotNull(command.getFontFamily());
        assertNotNull(command.getFontFamilyDefault());
        assertNotNull(command.getFontFamilyJpEmbedDefault());
        assertEquals(FontScheme.DEFAULT, command.getFontScheme());
        assertEquals(14, command.getFontSize());
        assertEquals(14, command.getFontSizeDefault());
        assertNotNull(command.getFontSizeJpEmbedDefault());
        assertNotNull(command.getFontFamilyJpEmbedDefault());
        assertNotNull(command.getIetf());
        assertNotNull(command.getIetfDefault());
        assertNotNull(command.getIetfDisplayDefault());
        assertTrue(command.isKeyboardShortcutsEnabled());
        assertFalse(command.isLastFmEnabled());
        assertNull(command.getLastFmPassword());
        assertNull(command.getLastFmUsername());
        assertFalse(command.isListenBrainzEnabled());
        assertNull(command.getListenBrainzToken());
        assertEquals("2", command.getLocaleIndex());
        assertEquals(28, command.getLocales().size());
        assertNotNull(command.getMainVisibility());
        assertFalse(command.isOpenDetailIndex());
        assertFalse(command.isOpenDetailSetting());
        assertFalse(command.isOpenDetailStar());
        assertEquals(40, command.getPaginationSize());
        assertFalse(command.isPartyModeEnabled());
        assertNotNull(command.getPlaylistVisibility());
        assertFalse(command.isPutMenuInDrawer());
        assertTrue(command.isQueueFollowingSongs());
        assertEquals(0, command.getShareCount());
        assertFalse(command.isShowAlbumActions());
        assertFalse(command.isShowAlbumSearch());
        assertFalse(command.isShowArtistInfoEnabled());
        assertFalse(command.isShowChangeCoverArt());
        assertFalse(command.isShowComment());
        assertTrue(command.isShowCurrentSongInfo());
        assertFalse(command.isShowDownload());
        assertTrue(command.isShowIndex());
        assertFalse(command.isShowLastPlay());
        assertFalse(command.isShowOutlineHelp());
        assertFalse(command.isShowRate());
        assertFalse(command.isShowShare());
        assertFalse(command.isShowSibling());
        assertFalse(command.isShowSimilar());
        assertFalse(command.isShowTag());
        assertFalse(command.isShowToast());
        assertFalse(command.isShowTopSongs());
        assertTrue(command.isSimpleDisplay());
        assertNotNull(command.getSmartphoneSettings());
        assertFalse(command.isSongNotificationEnabled());
        assertEquals(SpeechToTextLangScheme.DEFAULT, command.getSpeechToTextLangScheme());
        assertNotNull(command.getTabletSettings());
        assertEquals("-1", command.getThemeIndex());
        assertEquals(21, command.getThemes().size());
        assertEquals(ServiceMockUtils.ADMIN_NAME, command.getUser().getUsername());
        assertFalse(command.isUseRadio());
        assertFalse(command.isVoiceInputEnabled());
    }

    @Documented
    private @interface DoSubmitDecision {
        @interface Conditions {
            @interface UserSettings {
                @interface Locale {
                    @interface Null {
                    }

                    @interface NotNull {
                    }
                }

                @interface SpeechLangSchemeName {
                    @interface Default {

                    }

                    @interface NotDefault {
                    }
                }

                @interface ThemeId {
                    @interface Null {
                    }

                    @interface NotNull {
                    }
                }
            }

            @interface Command {

                @interface SpeechLangSchemeName {
                    @interface Default {

                    }

                    @interface NotDefault {
                    }
                }

                @interface Ietf {
                    @interface Blank {
                    }

                    @interface NotBlank {
                        @interface FineFormat {
                        }

                        @interface NotFine {
                        }
                    }
                }

                @interface LastFmPassword {
                    @interface Blank {
                    }

                    @interface NotBlank {
                    }
                }

                @interface AvatarScheme {
                    @interface NONE {
                    }

                    @interface CUSTOM {
                    }

                    @interface SYSTEM {
                    }
                }
            }

        }
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @Nested
    class DoSubmitAction {

        @DoSubmitDecision.Conditions.UserSettings.Locale.Null
        @DoSubmitDecision.Conditions.UserSettings.SpeechLangSchemeName.Default
        @DoSubmitDecision.Conditions.UserSettings.ThemeId.Null
        @DoSubmitDecision.Conditions.Command.Ietf.NotBlank.FineFormat
        @DoSubmitDecision.Conditions.Command.LastFmPassword.Blank
        @DoSubmitDecision.Conditions.Command.SpeechLangSchemeName.Default
        @DoSubmitDecision.Conditions.Command.AvatarScheme.NONE
        @Order(1)
        @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
        @Test
        void c1() throws Exception {

            MvcResult result = mockMvc
                .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

            assertNotNull(result);
            ModelAndView modelAndView = result.getModelAndView();
            assertEquals(VIEW_NAME, modelAndView.getViewName());
            PersonalSettingsCommand command = (PersonalSettingsCommand) modelAndView
                .getModelMap()
                .get(Attributes.Model.Command.VALUE);
            assertNotNull(command);
            assertNotNull(command.getIetf()); // Depends on the test environment

            UserSettings userSettings = userService.getUserSettings(ServiceMockUtils.ADMIN_NAME);
            assertNull(userSettings.getLocale());
            assertEquals(SpeechToTextLangScheme.DEFAULT.name(),
                    userSettings.getSpeechLangSchemeName());
            assertNull(userSettings.getThemeId());

            result = mockMvc
                .perform(MockMvcRequestBuilders
                    .post("/" + ViewName.PERSONAL_SETTINGS.value())
                    .requestAttr(Attributes.Model.Command.VALUE, command))
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.redirectedUrl(ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andReturn();
            assertNotNull(result);
        }

        @DoSubmitDecision.Conditions.UserSettings.Locale.NotNull
        @DoSubmitDecision.Conditions.UserSettings.SpeechLangSchemeName.NotDefault
        @Order(2)
        @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
        @Test
        void c2() throws Exception {
            MvcResult result = mockMvc
                .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

            assertNotNull(result);
            ModelAndView modelAndView = result.getModelAndView();
            assertEquals(VIEW_NAME, modelAndView.getViewName());
            PersonalSettingsCommand command = (PersonalSettingsCommand) modelAndView
                .getModelMap()
                .get(Attributes.Model.Command.VALUE);
            assertNotNull(command);

            UserSettings userSettings = userService.getUserSettings(ServiceMockUtils.ADMIN_NAME);
            userSettings.setLocale(Locale.JAPANESE);
            userSettings.setSpeechLangSchemeName(SpeechToTextLangScheme.BCP47.name());
            userService.updateUserSettings(userSettings);

            result = mockMvc
                .perform(MockMvcRequestBuilders
                    .post("/" + ViewName.PERSONAL_SETTINGS.value())
                    .requestAttr(Attributes.Model.Command.VALUE, command))
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.redirectedUrl(ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andReturn();
            assertNotNull(result);
        }

        @DoSubmitDecision.Conditions.UserSettings.Locale.NotNull
        @DoSubmitDecision.Conditions.UserSettings.SpeechLangSchemeName.Default
        @Order(3)
        @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
        @Test
        void c3() throws Exception {
            MvcResult result = mockMvc
                .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

            assertNotNull(result);
            ModelAndView modelAndView = result.getModelAndView();
            assertEquals(VIEW_NAME, modelAndView.getViewName());
            PersonalSettingsCommand command = (PersonalSettingsCommand) modelAndView
                .getModelMap()
                .get(Attributes.Model.Command.VALUE);
            assertNotNull(command);

            UserSettings userSettings = userService.getUserSettings(ServiceMockUtils.ADMIN_NAME);
            userSettings.setLocale(StringUtil.parseLocale("ja_JP"));
            userSettings.setSpeechLangSchemeName(SpeechToTextLangScheme.DEFAULT.name());
            userService.updateUserSettings(userSettings);

            result = mockMvc
                .perform(MockMvcRequestBuilders
                    .post("/" + ViewName.PERSONAL_SETTINGS.value())
                    .requestAttr(Attributes.Model.Command.VALUE, command))
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.redirectedUrl(ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andReturn();
            assertNotNull(result);
        }

        @DoSubmitDecision.Conditions.UserSettings.ThemeId.NotNull
        @Order(4)
        @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
        @Test
        void c4() throws Exception {
            MvcResult result = mockMvc
                .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

            assertNotNull(result);
            ModelAndView modelAndView = result.getModelAndView();
            assertEquals(VIEW_NAME, modelAndView.getViewName());
            PersonalSettingsCommand command = (PersonalSettingsCommand) modelAndView
                .getModelMap()
                .get(Attributes.Model.Command.VALUE);
            assertNotNull(command);

            UserSettings userSettings = userService.getUserSettings(ServiceMockUtils.ADMIN_NAME);
            userSettings.setThemeId("jpsonic");
            userService.updateUserSettings(userSettings);

            result = mockMvc
                .perform(MockMvcRequestBuilders
                    .post("/" + ViewName.PERSONAL_SETTINGS.value())
                    .requestAttr(Attributes.Model.Command.VALUE, command))
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.redirectedUrl(ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andReturn();
            assertNotNull(result);
        }

        @Order(5)
        @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
        @Test
        void c5() throws Exception {
            MvcResult result = mockMvc
                .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

            assertNotNull(result);
            ModelAndView modelAndView = result.getModelAndView();
            assertEquals(VIEW_NAME, modelAndView.getViewName());
            PersonalSettingsCommand command = (PersonalSettingsCommand) modelAndView
                .getModelMap()
                .get(Attributes.Model.Command.VALUE);
            assertNotNull(command);

            result = mockMvc
                .perform(MockMvcRequestBuilders
                    .post("/" + ViewName.PERSONAL_SETTINGS.value())
                    .requestAttr(Attributes.Model.Command.VALUE, command))
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.redirectedUrl(ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andReturn();
            assertNotNull(result);
        }

        @DoSubmitDecision.Conditions.Command.SpeechLangSchemeName.NotDefault
        @DoSubmitDecision.Conditions.Command.Ietf.Blank
        @Order(6)
        @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
        @Test
        void c6() throws Exception {
            MvcResult result = mockMvc
                .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

            assertNotNull(result);
            ModelAndView modelAndView = result.getModelAndView();
            assertEquals(VIEW_NAME, modelAndView.getViewName());
            PersonalSettingsCommand command = (PersonalSettingsCommand) modelAndView
                .getModelMap()
                .get(Attributes.Model.Command.VALUE);
            assertNotNull(command);
            command.setSpeechToTextLangScheme(SpeechToTextLangScheme.BCP47);
            command.setIetf(null);

            modelAndView = controller
                .doSubmitAction(command, Mockito.mock(RedirectAttributes.class));
            assertNull(modelAndView.getViewName());

            result = mockMvc
                .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

            assertNotNull(result);
            modelAndView = result.getModelAndView();
            assertEquals(VIEW_NAME, modelAndView.getViewName());
            command = (PersonalSettingsCommand) modelAndView
                .getModelMap()
                .get(Attributes.Model.Command.VALUE);

            assertNotNull(command);
            assertNotNull(command.getIetf());
        }

        @DoSubmitDecision.Conditions.Command.SpeechLangSchemeName.NotDefault
        @DoSubmitDecision.Conditions.Command.Ietf.NotBlank.NotFine
        @Order(7)
        @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
        @Test
        void c7() throws Exception {
            MvcResult result = mockMvc
                .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

            assertNotNull(result);
            ModelAndView modelAndView = result.getModelAndView();
            assertEquals(VIEW_NAME, modelAndView.getViewName());
            PersonalSettingsCommand command = (PersonalSettingsCommand) modelAndView
                .getModelMap()
                .get(Attributes.Model.Command.VALUE);
            assertNotNull(command);
            command.setSpeechToTextLangScheme(SpeechToTextLangScheme.BCP47);
            command.setIetf("Unknown.Unknown");

            modelAndView = controller
                .doSubmitAction(command, Mockito.mock(RedirectAttributes.class));
            assertNull(modelAndView.getViewName());

            result = mockMvc
                .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

            assertNotNull(result);
            modelAndView = result.getModelAndView();
            assertEquals(VIEW_NAME, modelAndView.getViewName());
            command = (PersonalSettingsCommand) modelAndView
                .getModelMap()
                .get(Attributes.Model.Command.VALUE);

            assertNotNull(command);
            assertNotNull(command.getIetf());
        }

        @DoSubmitDecision.Conditions.Command.LastFmPassword.NotBlank
        @Order(8)
        @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
        @Test
        void c8() throws Exception {
            MvcResult result = mockMvc
                .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

            assertNotNull(result);
            ModelAndView modelAndView = result.getModelAndView();
            assertEquals(VIEW_NAME, modelAndView.getViewName());
            PersonalSettingsCommand command = (PersonalSettingsCommand) modelAndView
                .getModelMap()
                .get(Attributes.Model.Command.VALUE);
            assertNotNull(command);
            command.setLastFmPassword("pass");

            modelAndView = controller
                .doSubmitAction(command, Mockito.mock(RedirectAttributes.class));
            assertNull(modelAndView.getViewName());

            result = mockMvc
                .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

            assertNotNull(result);
            modelAndView = result.getModelAndView();
            assertEquals(VIEW_NAME, modelAndView.getViewName());
            command = (PersonalSettingsCommand) modelAndView
                .getModelMap()
                .get(Attributes.Model.Command.VALUE);

            assertNotNull(command);
            assertNotNull(command.getLastFmPassword());
        }

        @DoSubmitDecision.Conditions.Command.AvatarScheme.CUSTOM
        @Order(9)
        @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
        @Test
        void c9() throws Exception {
            MvcResult result = mockMvc
                .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

            assertNotNull(result);
            ModelAndView modelAndView = result.getModelAndView();
            assertEquals(VIEW_NAME, modelAndView.getViewName());
            PersonalSettingsCommand command = (PersonalSettingsCommand) modelAndView
                .getModelMap()
                .get(Attributes.Model.Command.VALUE);
            assertNotNull(command);
            command.setAvatarId(AvatarScheme.CUSTOM.getCode());

            modelAndView = controller
                .doSubmitAction(command, Mockito.mock(RedirectAttributes.class));
            assertNull(modelAndView.getViewName());

            result = mockMvc
                .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

            assertNotNull(result);
            modelAndView = result.getModelAndView();
            assertEquals(VIEW_NAME, modelAndView.getViewName());
            command = (PersonalSettingsCommand) modelAndView
                .getModelMap()
                .get(Attributes.Model.Command.VALUE);

            assertNotNull(command);
            assertEquals(AvatarScheme.CUSTOM.getCode(), command.getAvatarId());
        }

        @DoSubmitDecision.Conditions.Command.AvatarScheme.SYSTEM
        @Order(10)
        @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
        // @Test NeedScan
        void c10() throws Exception {
            MvcResult result = mockMvc
                .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

            assertNotNull(result);
            ModelAndView modelAndView = result.getModelAndView();
            assertEquals(VIEW_NAME, modelAndView.getViewName());
            PersonalSettingsCommand command = (PersonalSettingsCommand) modelAndView
                .getModelMap()
                .get(Attributes.Model.Command.VALUE);
            assertNotNull(command);
            command.setAvatarId(1);

            modelAndView = controller
                .doSubmitAction(command, Mockito.mock(RedirectAttributes.class));
            assertNull(modelAndView.getViewName());

            result = mockMvc
                .perform(MockMvcRequestBuilders.get("/" + ViewName.PERSONAL_SETTINGS.value()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

            assertNotNull(result);
            modelAndView = result.getModelAndView();
            assertEquals(VIEW_NAME, modelAndView.getViewName());
            command = (PersonalSettingsCommand) modelAndView
                .getModelMap()
                .get(Attributes.Model.Command.VALUE);

            assertNotNull(command);
            assertEquals(AvatarScheme.CUSTOM.getCode(), command.getAvatarId());
        }
    }
}

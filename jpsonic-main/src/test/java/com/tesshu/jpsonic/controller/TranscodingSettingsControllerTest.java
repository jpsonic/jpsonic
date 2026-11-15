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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.annotation.Documented;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.domain.system.PreferredFormatScheme;
import com.tesshu.jpsonic.feature.transcoding.Transcodings;
import com.tesshu.jpsonic.feature.upnp.UPnPSubnet;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.Transcoding;
import com.tesshu.jpsonic.persistence.api.repository.TranscodingDao;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.TranscodingService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Ignore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class TranscodingSettingsControllerTest {

    private TranscodingSettingsController controller;
    private TranscodingService transcodingService;
    private SettingsFacade settingsFacade;
    private TranscodingDao transcodingDao;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() throws ExecutionException {
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.transcoding.preferredFormat, "mp3")
            .withString(SKeys.transcoding.preferredFormatShemeName,
                    PreferredFormatScheme.ANNOYMOUS.name())
            .build();
        init();
    }

    @Ignore
    void init() {
        UserService userService = mock(UserService.class);
        transcodingDao = mock(TranscodingDao.class);
        transcodingService = new TranscodingService(settingsFacade, userService,
                mock(UPnPSubnet.class), transcodingDao, mock(PlayerService.class), null);
        controller = new TranscodingSettingsController(settingsFacade, userService,
                transcodingService, mock(ShareService.class), mock(OutlineHelpSelector.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @WithMockUser(username = "admin")
    @Test
    void testDoGet() throws Exception {
        MvcResult mvcResult = mockMvc
            .perform(MockMvcRequestBuilders.get("/transcodingSettings.view"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.view().name("transcodingSettings"))
            .andReturn();
        Assertions.assertNotNull(mvcResult);
        @SuppressWarnings("unchecked")
        Map<String, Object> model = (Map<String, Object>) mvcResult
            .getModelAndView()
            .getModel()
            .get("model");
        assertEquals(10, model.size());
        assertEquals(settingsFacade.get(SKeys.transcoding.hlsCommand), model.get("hlsCommand"));
        assertFalse((Boolean) model.get("isOpenDetailSetting"));
        assertFalse((Boolean) model.get("showOutlineHelp"));
        assertEquals(0, model.get("shareCount"));
        assertFalse((Boolean) model.get("useRadio"));
        assertEquals(settingsFacade.get(SKeys.transcoding.preferredFormat),
                model.get("preferredFormat"));
        assertEquals(PreferredFormatScheme.ANNOYMOUS, model.get("preferredFormatSchemeName"));
        assertEquals(EnvironmentProvider.getInstance().getBrand(), model.get("brand"));
        assertEquals(transcodingService.getAllTranscodings(), model.get("transcodings"));
    }

    @Test
    void testPreferredFormat() {

        ArgumentCaptor<String> formatCaptor = ArgumentCaptor.forClass(String.class);
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.transcoding.preferredFormat, "mp3")
            .withString(SKeys.transcoding.preferredFormatShemeName,
                    PreferredFormatScheme.ANNOYMOUS.name())
            .captureString(SKeys.transcoding.preferredFormat, formatCaptor)
            .build();
        init();
        MockHttpServletRequest req = new MockHttpServletRequest();
        controller.doPost(req, mock(RedirectAttributes.class));
        assertEquals(0, formatCaptor.getAllValues().size());

        formatCaptor = ArgumentCaptor.forClass(String.class);
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.transcoding.preferredFormat, "mp3")
            .withString(SKeys.transcoding.preferredFormatShemeName,
                    PreferredFormatScheme.ANNOYMOUS.name())
            .captureString(SKeys.transcoding.preferredFormat, formatCaptor)
            .build();
        init();
        req = new MockHttpServletRequest();
        req.setParameter("preferredFormat", "ogg");
        controller.doPost(req, mock(RedirectAttributes.class));
        assertEquals(0, formatCaptor.getAllValues().size());

        formatCaptor = ArgumentCaptor.forClass(String.class);
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.transcoding.preferredFormat, "mp3")
            .withString(SKeys.transcoding.preferredFormatShemeName,
                    PreferredFormatScheme.ANNOYMOUS.name())
            .captureString(SKeys.transcoding.preferredFormat, formatCaptor)
            .build();
        init();
        req = new MockHttpServletRequest();
        req.setParameter("preferredFormat", "mp3");
        controller.doPost(req, mock(RedirectAttributes.class));
        assertEquals(1, formatCaptor.getAllValues().size());
        assertEquals("mp3", formatCaptor.getValue());
    }

    @Test
    void testRestore() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setParameter("restoredNames", new String[0]);
        controller.doPost(req, mock(RedirectAttributes.class));
        Mockito
            .verify(transcodingDao, Mockito.never())
            .createTranscoding(Mockito.any(Transcoding.class));

        req.setParameter("restoredNames", Transcodings.MP3.getName(), Transcodings.MP4.getName());
        ArgumentCaptor<Transcoding> transcodingCaptor = ArgumentCaptor.forClass(Transcoding.class);
        Mockito.when(transcodingDao.createTranscoding(transcodingCaptor.capture())).thenReturn(0);
        controller.doPost(req, mock(RedirectAttributes.class));
        Mockito
            .verify(transcodingDao, Mockito.times(2))
            .createTranscoding(Mockito.any(Transcoding.class));
        assertEquals(2, transcodingCaptor.getAllValues().size());
        assertEquals(Transcodings.MP3.getName(), transcodingCaptor.getAllValues().get(0).getName());
        assertEquals(Transcodings.MP4.getName(), transcodingCaptor.getAllValues().get(1).getName());
    }

    @Documented
    private @interface RequestDecision {
        @interface Actions {
            @interface Delete {
            }

            @interface Update {
            }

            @interface New {
            }
        }

        @interface Conditions {
            @interface Delete {
                @interface Null {
                }

                @interface NotNull {
                }
            }

            @interface Name {
                @interface Null {
                }

                @interface NotNull {
                }

                @interface DuplicateWithReserved {
                    @interface False {
                    }

                    @interface True {
                    }
                }
            }

            @interface SourceFormats {
                @interface Null {
                }

                @interface NotNull {
                }
            }

            @interface TargetFormat {
                @interface Null {
                }

                @interface NotNull {
                }
            }

            @interface Step1 {
                @interface Null {
                }

                @interface NotNull {
                }
            }

            @interface Step2 {
                @interface Null {
                }

                @interface NotNull {
                }
            }

            @interface DefaultActive {
                @interface Null {
                }

                @interface NotNull {
                }
            }

        }
    }

    @Nested
    class TestErrorMessages {

        private MockHttpServletRequest createRequest() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            transcodingService.getAllTranscodings().stream().forEach(t -> {
                req
                    .setParameter(Attributes.Request.NAME.value() + "[" + t.getId() + "]",
                            t.getName());
                req
                    .setParameter(Attributes.Request.SOURCE_FORMATS.value() + "[" + t.getId() + "]",
                            t.getSourceFormats());
                req
                    .setParameter(Attributes.Request.TARGET_FORMAT.value() + "[" + t.getId() + "]",
                            t.getTargetFormat());
                req
                    .setParameter(Attributes.Request.STEP1.value() + "[" + t.getId() + "]",
                            t.getStep1());
                req.setParameter(Attributes.Request.STEP2.value() + "[" + t.getId() + "]", "");
            });
            return req;
        }

        private String getRedirectError(HttpServletRequest req) throws ExecutionException {
            RedirectAttributes attributes = Mockito.mock(RedirectAttributes.class);
            ArgumentCaptor<Object> errorCaptor = ArgumentCaptor.forClass(Object.class);
            Mockito
                .doReturn(attributes)
                .when(attributes)
                .addFlashAttribute(Mockito.anyString(), errorCaptor.capture());
            controller.doPost(req, attributes);
            Object o = errorCaptor.getValue();
            if (o instanceof Boolean) {
                return null;
            }
            return errorCaptor.getValue().toString();
        }

        @RequestDecision.Actions.Update
        @RequestDecision.Conditions.Name.NotNull
        @RequestDecision.Conditions.SourceFormats.NotNull
        @RequestDecision.Conditions.TargetFormat.NotNull
        @RequestDecision.Conditions.Step1.NotNull
        @RequestDecision.Conditions.Delete.Null
        @Test
        void c01() throws ExecutionException {
            HttpServletRequest req = createRequest();
            assertNull(getRedirectError(req));
        }

        @RequestDecision.Actions.Update
        @RequestDecision.Conditions.Name.Null
        @RequestDecision.Conditions.SourceFormats.NotNull
        @RequestDecision.Conditions.TargetFormat.NotNull
        @RequestDecision.Conditions.Step1.NotNull
        @RequestDecision.Conditions.Delete.Null
        @Test
        void c02() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            transcodingService.getAllTranscodings().stream().findFirst().ifPresent(t -> {
                req.setParameter(Attributes.Request.NAME.value() + "[" + t.getId() + "]", "");
            });
            assertEquals("transcodingsettings.noname", getRedirectError(req));
        }

        @RequestDecision.Actions.Update
        @RequestDecision.Conditions.Name.NotNull
        @RequestDecision.Conditions.SourceFormats.Null
        @RequestDecision.Conditions.TargetFormat.NotNull
        @RequestDecision.Conditions.Step1.NotNull
        @RequestDecision.Conditions.Delete.Null
        @Test
        void c03() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            transcodingService.getAllTranscodings().stream().findFirst().ifPresent(t -> {
                req
                    .setParameter(Attributes.Request.SOURCE_FORMATS.value() + "[" + t.getId() + "]",
                            "");
            });
            assertEquals("transcodingsettings.nosourceformat", getRedirectError(req));
        }

        @RequestDecision.Actions.Update
        @RequestDecision.Conditions.Name.NotNull
        @RequestDecision.Conditions.SourceFormats.NotNull
        @RequestDecision.Conditions.TargetFormat.Null
        @RequestDecision.Conditions.Step1.NotNull
        @RequestDecision.Conditions.Delete.Null
        @Test
        void c04() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            transcodingService.getAllTranscodings().stream().findFirst().ifPresent(t -> {
                req
                    .setParameter(Attributes.Request.TARGET_FORMAT.value() + "[" + t.getId() + "]",
                            "");
            });
            assertEquals("transcodingsettings.notargetformat", getRedirectError(req));
        }

        @RequestDecision.Actions.Update
        @RequestDecision.Conditions.Name.NotNull
        @RequestDecision.Conditions.SourceFormats.NotNull
        @RequestDecision.Conditions.TargetFormat.NotNull
        @RequestDecision.Conditions.Step1.Null
        @RequestDecision.Conditions.Delete.Null
        @Test
        void c05() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            transcodingService.getAllTranscodings().stream().findFirst().ifPresent(t -> {
                req.setParameter(Attributes.Request.STEP1.value() + "[" + t.getId() + "]", "");
            });
            assertEquals("transcodingsettings.nostep1", getRedirectError(req));
        }

        @RequestDecision.Actions.Delete
        @RequestDecision.Conditions.Delete.NotNull
        @Test
        void c06() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            transcodingService.getAllTranscodings().stream().findFirst().ifPresent(t -> {
                req.setParameter(Attributes.Request.DELETE.value() + "[" + t.getId() + "]", "true");
            });
            assertNull(getRedirectError(req));
        }

        @RequestDecision.Actions.New
        @RequestDecision.Conditions.Name.NotNull
        @RequestDecision.Conditions.Name.DuplicateWithReserved.False
        @RequestDecision.Conditions.SourceFormats.Null
        @Test
        void c07() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            req.setParameter(Attributes.Request.NAME.value(), "*name*");
            req.setParameter(Attributes.Request.SOURCE_FORMATS.value(), "");
            assertEquals("transcodingsettings.nosourceformat", getRedirectError(req));
        }

        @RequestDecision.Actions.New
        @RequestDecision.Conditions.Name.NotNull
        @RequestDecision.Conditions.Name.DuplicateWithReserved.False
        @RequestDecision.Conditions.SourceFormats.NotNull
        @RequestDecision.Conditions.TargetFormat.Null
        @Test
        void c08() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            req.setParameter(Attributes.Request.NAME.value(), "*name*");
            req.setParameter(Attributes.Request.SOURCE_FORMATS.value(), "*source*");
            req.setParameter(Attributes.Request.TARGET_FORMAT.value(), "");
            assertEquals("transcodingsettings.notargetformat", getRedirectError(req));
        }

        @RequestDecision.Actions.New
        @RequestDecision.Conditions.Name.NotNull
        @RequestDecision.Conditions.Name.DuplicateWithReserved.False
        @RequestDecision.Conditions.SourceFormats.NotNull
        @RequestDecision.Conditions.TargetFormat.NotNull
        @RequestDecision.Conditions.Step1.Null
        @Test
        void c09() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            req.setParameter(Attributes.Request.NAME.value(), "*name*");
            req.setParameter(Attributes.Request.SOURCE_FORMATS.value(), "*source*");
            req.setParameter(Attributes.Request.TARGET_FORMAT.value(), "*target*");
            req.setParameter(Attributes.Request.STEP1.value(), "");
            assertEquals("transcodingsettings.nostep1", getRedirectError(req));
        }

        @RequestDecision.Actions.New
        @RequestDecision.Conditions.Name.NotNull
        @RequestDecision.Conditions.Name.DuplicateWithReserved.False
        @RequestDecision.Conditions.SourceFormats.NotNull
        @RequestDecision.Conditions.TargetFormat.NotNull
        @RequestDecision.Conditions.Step1.NotNull
        @RequestDecision.Conditions.Step2.Null
        @RequestDecision.Conditions.DefaultActive.Null
        @Test
        void c10() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            req.setParameter(Attributes.Request.NAME.value(), "*name*");
            req.setParameter(Attributes.Request.SOURCE_FORMATS.value(), "*source*");
            req.setParameter(Attributes.Request.TARGET_FORMAT.value(), "*target*");
            req.setParameter(Attributes.Request.STEP1.value(), "*step1*");
            assertNull(getRedirectError(req));
        }

        @RequestDecision.Actions.New
        @RequestDecision.Conditions.Name.NotNull
        @RequestDecision.Conditions.Name.DuplicateWithReserved.False
        @RequestDecision.Conditions.SourceFormats.NotNull
        @RequestDecision.Conditions.TargetFormat.NotNull
        @RequestDecision.Conditions.Step1.NotNull
        @RequestDecision.Conditions.Step2.NotNull
        @RequestDecision.Conditions.DefaultActive.NotNull
        @Test
        void c11() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            req.setParameter(Attributes.Request.NAME.value(), "*name*");
            req.setParameter(Attributes.Request.SOURCE_FORMATS.value(), "*source*");
            req.setParameter(Attributes.Request.TARGET_FORMAT.value(), "*target*");
            req.setParameter(Attributes.Request.STEP1.value(), "*step1*");
            req.setParameter(Attributes.Request.STEP2.value(), "*step2*");
            req.setParameter(Attributes.Request.DEFAULT_ACTIVE.value(), "*active*");
            assertNull(getRedirectError(req));
        }

        @RequestDecision.Actions.New
        @RequestDecision.Conditions.Name.NotNull
        @RequestDecision.Conditions.Name.DuplicateWithReserved.True
        @RequestDecision.Conditions.SourceFormats.NotNull
        @RequestDecision.Conditions.TargetFormat.NotNull
        @RequestDecision.Conditions.Step1.NotNull
        @RequestDecision.Conditions.Step2.NotNull
        @RequestDecision.Conditions.DefaultActive.NotNull
        @Test
        void c12() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            req.setParameter(Attributes.Request.NAME.value(), Transcodings.MP3.getName());
            req.setParameter(Attributes.Request.SOURCE_FORMATS.value(), "*source*");
            req.setParameter(Attributes.Request.TARGET_FORMAT.value(), "*target*");
            req.setParameter(Attributes.Request.STEP1.value(), "*step1*");
            req.setParameter(Attributes.Request.STEP2.value(), "*step2*");
            req.setParameter(Attributes.Request.DEFAULT_ACTIVE.value(), "*active*");
            assertEquals("transcodingsettings.duplicate", getRedirectError(req));
        }

        @RequestDecision.Actions.New
        @RequestDecision.Conditions.Name.Null
        @RequestDecision.Conditions.SourceFormats.Null
        @RequestDecision.Conditions.TargetFormat.Null
        @RequestDecision.Conditions.Step1.Null
        @RequestDecision.Conditions.Step2.Null
        @Test
        void c13() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            req.setParameter(Attributes.Request.NAME.value(), "");
            req.setParameter(Attributes.Request.SOURCE_FORMATS.value(), "");
            req.setParameter(Attributes.Request.TARGET_FORMAT.value(), "");
            req.setParameter(Attributes.Request.STEP1.value(), "");
            req.setParameter(Attributes.Request.STEP2.value(), "");
            assertNull(getRedirectError(req));
        }

        @RequestDecision.Actions.New
        @RequestDecision.Conditions.Name.Null
        @RequestDecision.Conditions.SourceFormats.NotNull
        @Test
        void c14() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            req.setParameter(Attributes.Request.NAME.value(), "");
            req.setParameter(Attributes.Request.SOURCE_FORMATS.value(), "*source*");
            assertEquals("transcodingsettings.noname", getRedirectError(req));
        }

        @RequestDecision.Actions.New
        @RequestDecision.Conditions.Name.Null
        @RequestDecision.Conditions.TargetFormat.NotNull
        @Test
        void c15() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            req.setParameter(Attributes.Request.NAME.value(), "");
            req.setParameter(Attributes.Request.TARGET_FORMAT.value(), "*target*");
            assertEquals("transcodingsettings.noname", getRedirectError(req));
        }

        @RequestDecision.Actions.New
        @RequestDecision.Conditions.Name.Null
        @RequestDecision.Conditions.Step1.NotNull
        @Test
        void c16() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            req.setParameter(Attributes.Request.NAME.value(), "");
            req.setParameter(Attributes.Request.STEP1.value(), "*step1*");
            assertEquals("transcodingsettings.noname", getRedirectError(req));
        }

        @RequestDecision.Actions.New
        @RequestDecision.Conditions.Name.Null
        @RequestDecision.Conditions.Step2.NotNull
        @Test
        void c17() throws ExecutionException {
            MockHttpServletRequest req = createRequest();
            req.setParameter(Attributes.Request.NAME.value(), "");
            req.setParameter(Attributes.Request.STEP2.value(), "*step2*");
            assertEquals("transcodingsettings.noname", getRedirectError(req));
        }
    }
}

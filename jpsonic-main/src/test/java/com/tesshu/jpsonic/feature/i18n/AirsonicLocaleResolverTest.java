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
 * (C) 2026 tesshucom
 */

package com.tesshu.jpsonic.feature.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Locale;

import com.tesshu.jpsonic.domain.provider.resource.ServerLocaleProvider;
import com.tesshu.jpsonic.infrastructure.locale.ServerLocaleManager;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class AirsonicLocaleResolverTest {

    private UserService userService;
    private AirsonicLocaleResolver resolver;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        SettingsFacade settingsFacade = SettingsFacadeBuilder.create().buildWithDefault();
        ServerLocaleProvider serverLocaleProvider = new ServerLocaleManager(settingsFacade);
        resolver = new AirsonicLocaleResolver(userService, serverLocaleProvider);
    }

    @Test
    void returnsCachedLocale() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("airsonic.locale", Locale.JAPANESE);
        Locale result = resolver.resolveLocale(req);
        assertEquals(Locale.JAPANESE, result);
    }

    @Test
    void userLocaleSupported() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        when(userService.getCurrentUsername(req)).thenReturn("admin");
        UserSettings settings = new UserSettings();
        settings.setLocale(Locale.JAPANESE);
        when(userService.getUserSettings("admin")).thenReturn(settings);
        Locale result = resolver.resolveLocale(req);
        assertEquals(Locale.JAPAN, result);
    }

    @Test
    void userLocaleUnsupportedFallsBackToSystemLocale() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        when(userService.getCurrentUsername(req)).thenReturn("admin");
        UserSettings settings = new UserSettings();
        settings
            .setLocale(
                    new Locale.Builder().setLanguage("zz").setRegion("ZZ").setVariant("").build());
        when(userService.getUserSettings("admin")).thenReturn(settings);
        Locale result = resolver.resolveLocale(req);
        assertEquals(Locale.JAPAN, result);
    }
}

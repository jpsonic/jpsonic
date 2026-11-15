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

package com.tesshu.jpsonic.feature.i18n;

import java.util.Locale;

import com.tesshu.jpsonic.domain.provider.resource.ServerLocaleProvider;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Service;

/**
 * Locale resolver implementation which returns the locale selected in the
 * settings.
 *
 * @author Sindre Mehus
 */
@SuppressWarnings("UnnecessarilyFullyQualified")
@Service
public class AirsonicLocaleResolver implements org.springframework.web.servlet.LocaleResolver {

    private final UserService userService;
    private final ServerLocaleProvider serverLocaleProvider;

    public AirsonicLocaleResolver(UserService userService,
            ServerLocaleProvider serverLocaleService) {
        super();
        this.userService = userService;
        this.serverLocaleProvider = serverLocaleService;
    }

    /**
     * Resolve the current locale via the given request.
     *
     * @param request Request to be used for resolution.
     *
     * @return The current locale.
     */
    @Override
    public @NonNull Locale resolveLocale(HttpServletRequest request) {
        Locale locale = (Locale) request.getAttribute("airsonic.locale");
        if (locale != null) {
            return locale;
        }

        // Optimization: Cache locale in the request.
        locale = doResolveLocale(request);
        request.setAttribute("airsonic.locale", locale);

        return locale;
    }

    private Locale doResolveLocale(HttpServletRequest request) {
        Locale locale = null;

        // Look for user-specific locale.
        String username = userService.getCurrentUsername(request);
        if (username != null) {
            UserSettings userSettings = userService.getUserSettings(username);
            if (userSettings != null) {
                locale = userSettings.getLocale();
            }
        }

        if (locale != null && localeExists(locale)) {
            return locale;
        }

        // Return system locale.
        locale = serverLocaleProvider.getLocale();
        return localeExists(locale) ? locale : Locale.ENGLISH;
    }

    private boolean localeExists(Locale locale) {
        return serverLocaleProvider.getAvailableLocales().contains(locale);
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException(
                "Cannot change locale - use a different locale resolution strategy");
    }
}

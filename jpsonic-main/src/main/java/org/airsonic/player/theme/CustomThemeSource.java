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

package org.airsonic.player.theme;

import org.airsonic.player.domain.Theme;
import org.airsonic.player.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;
import org.springframework.ui.context.support.ResourceBundleThemeSource;
import org.springframework.ui.context.support.UiApplicationContextUtils;

/**
 * Theme source implementation which uses two resource bundles: the theme specific (e.g., barents.properties), and the
 * default (default.properties).
 *
 * @author Sindre Mehus
 */
@Component(UiApplicationContextUtils.THEME_SOURCE_BEAN_NAME)
public class CustomThemeSource extends ResourceBundleThemeSource {

    private SettingsService settingsService;
    private String basenamePrefix;

    @Override
    protected MessageSource createMessageSource(String basename) {
        ResourceBundleMessageSource messageSource = (ResourceBundleMessageSource) super.createMessageSource(basename);

        // Create parent theme recursively.
        for (Theme theme : settingsService.getAvailableThemes()) {
            String maybeBasename = basenamePrefix + theme.getId();
            if (maybeBasename.equals(basename) && theme.getParent() != null) {
                String parent = basenamePrefix + theme.getParent();
                messageSource.setParentMessageSource(createMessageSource(parent));
                break;
            }
        }
        return messageSource;
    }

    @Autowired
    @Value("org.airsonic.player.theme.")
    @Override
    public void setBasenamePrefix(String basenamePrefix) {
        this.basenamePrefix = basenamePrefix;
        super.setBasenamePrefix(basenamePrefix);
    }

    @Autowired
    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }
}

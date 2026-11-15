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

package com.tesshu.jpsonic.adapter.resource;

import com.tesshu.jpsonic.domain.model.TranscodingDefinition.BitRateLimit;
import com.tesshu.jpsonic.domain.model.UserSettings;
import com.tesshu.jpsonic.domain.provider.resource.UserProvider;
import com.tesshu.jpsonic.persistence.core.repository.UserDao;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Component;

@Component
class UserProviderAdapter implements UserProvider {

    static final String USERNAME_GUEST = "guest";

    private final UserDao userDao;

    UserProviderAdapter(UserDao userDao) {
        super();
        this.userDao = userDao;
    }

    private @NonNull UserSettings createDefaultDomainUserSettings(String username) {
        return new UserSettings(username, BitRateLimit.OFF);
    }

    @Override
    public UserSettings getUserSettings(String username) {
        UserSettings userSettings = userDao.getDomainUserSettings(username);
        return userSettings == null ? createDefaultDomainUserSettings(username) : userSettings;
    }
}

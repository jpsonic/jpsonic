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

import com.tesshu.jpsonic.domain.model.User;
import com.tesshu.jpsonic.domain.registrar.UserRegister;
import com.tesshu.jpsonic.persistence.core.repository.UserDao;
import org.springframework.stereotype.Component;

@Component
class UserRegisterAdapter implements UserRegister {

    private final UserDao userDao;

    UserRegisterAdapter(UserDao userDao) {
        super();
        this.userDao = userDao;
    }

    @Override
    public void incrementByteCounts(String userName, long bytesStreamedIncrement,
            long bytesDownloadedIncrement, long bytesUploadedIncrement) {
        userDao
            .incrementUserByteCounts(bytesStreamedIncrement, bytesDownloadedIncrement,
                    bytesUploadedIncrement, userName);
    }

    @Override
    public void updateLastSeen(User user) {
        //
    }
}

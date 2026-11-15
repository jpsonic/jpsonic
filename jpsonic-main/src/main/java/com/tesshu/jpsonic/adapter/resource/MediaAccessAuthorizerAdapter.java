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

import com.tesshu.jpsonic.domain.model.MediaFile;
import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.feature.filesystem.MediaAccessAuthorizer;
import org.springframework.stereotype.Component;

@Component
class MediaAccessAuthorizerAdapter implements MediaAccessAuthorizer {

    private final LibraryAccessPolicy libraryAccessPolicy;

    MediaAccessAuthorizerAdapter(LibraryAccessPolicy libraryAccessPolicy) {
        super();
        this.libraryAccessPolicy = libraryAccessPolicy;
    }

    @Override
    public boolean canAccessMediaFile(String username, MediaFile mediaFile) {
        return libraryAccessPolicy.canAccessMediaFile(username, mediaFile);
    }
}

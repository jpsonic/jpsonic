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

import com.tesshu.jpsonic.controller.CoverArtController;
import com.tesshu.jpsonic.feature.coverart.CoverArtProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

/**
 * Transitional facade interface for cover art HTTP delivery.
 *
 * <p>
 * This interface exists to isolate image request handling logic from
 * protocol-specific controllers while the legacy cover art pipeline is being
 * incrementally refactored into smaller components.
 * </p>
 *
 * <p>
 * The current implementation intentionally preserves the existing
 * servlet-oriented response handling behavior.
 * </p>
 */
@Component
class CoverArtAdapter implements CoverArtProvider {

    private final CoverArtController delegate;

    CoverArtAdapter(CoverArtController delegate) {
        super();
        this.delegate = delegate;
    }

    @Override
    public void processImageRequest(HttpServletResponse response, String id, Integer size,
            Integer offset) {
        delegate.processImageRequest(response, id, size, offset);
    }
}

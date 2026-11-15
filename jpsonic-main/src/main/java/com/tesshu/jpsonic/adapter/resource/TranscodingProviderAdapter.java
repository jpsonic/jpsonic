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

import java.util.List;

import com.tesshu.jpsonic.domain.model.Player;
import com.tesshu.jpsonic.domain.model.TranscodingDefinition;
import com.tesshu.jpsonic.domain.provider.resource.TranscodingProvider;
import com.tesshu.jpsonic.persistence.api.repository.TranscodingDao;
import org.springframework.stereotype.Component;

@Component
class TranscodingProviderAdapter implements TranscodingProvider {

    private final TranscodingDao transcodingDao;

    TranscodingProviderAdapter(TranscodingDao transcodingDao) {
        super();
        this.transcodingDao = transcodingDao;
    }

    @Override
    public List<TranscodingDefinition> get(Player player) {
        return transcodingDao.getDomainTranscodingsForPlayer(player.id());
    }
}

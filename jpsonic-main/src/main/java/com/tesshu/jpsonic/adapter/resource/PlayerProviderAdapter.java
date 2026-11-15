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

import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.time.temporal.ChronoUnit;

import com.tesshu.jpsonic.domain.model.Player;
import com.tesshu.jpsonic.domain.provider.resource.PlayerProvider;
import com.tesshu.jpsonic.domain.registrar.PlayerRegister;
import com.tesshu.jpsonic.persistence.api.repository.PlayerDao;
import com.tesshu.jpsonic.service.PlayerService;
import org.springframework.stereotype.Component;

@Component
class PlayerProviderAdapter implements PlayerProvider, PlayerRegister {

    private final PlayerService playerService;
    private final PlayerDao playerDao;

    PlayerProviderAdapter(PlayerService playerService, PlayerDao playerDao) {
        super();
        this.playerService = playerService;
        this.playerDao = playerDao;
    }

    @Override
    public Player getUPnPPlayer() {
        Player player = playerService.getDomainUPnPPlayer();
        if (player.lastSeen().plus(1, ChronoUnit.DAYS).isBefore(now())) {
            updateLastSeen(player.id());
        }
        return player;
    }

    @Override
    public void updateLastSeen(int id) {
        playerDao.updateLastSeen(id);
    }
}

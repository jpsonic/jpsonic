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

import com.tesshu.jpsonic.application.runtime.TransferSession;
import com.tesshu.jpsonic.application.runtime.TransferSessionRuntime;
import com.tesshu.jpsonic.domain.model.Player;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.StatusService;
import com.tesshu.jpsonic.service.StatusService.TransferStatus;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.stereotype.Component;

@Component
class TransferSessionRuntimeAdapter implements TransferSessionRuntime {

    private final PlayerService playerService;

    private final StatusService statusService;

    TransferSessionRuntimeAdapter(PlayerService playerService, StatusService statusService) {
        super();
        this.playerService = playerService;
        this.statusService = statusService;
    }

    @Override
    public TransferSession create(Player player) {
        return new TransferSessionAdapter(
                statusService.createStreamStatus(playerService.getPlayerById(player.id())));
    }

    @Override
    public void remove(@NonNull Player arg0, @Nullable TransferSession session) {
        statusService.removeStreamStatus(((TransferSessionAdapter) session).toStatus());
    }

    static class TransferSessionAdapter implements TransferSession {

        private final TransferStatus deligate;

        TransferSessionAdapter(TransferStatus transferStatus) {
            super();
            this.deligate = transferStatus;
        }

        @Override
        public void addBytesTransferred(long bytes) {
            deligate.addBytesTransfered(bytes);
        }

        @Override
        public long bytesTransferred() {
            return deligate.getBytesTransfered();
        }

        @Override
        public void setPathString(String pathString) {
            deligate.setPathString(pathString);
        }

        TransferStatus toStatus() {
            return deligate;
        }
    }
}

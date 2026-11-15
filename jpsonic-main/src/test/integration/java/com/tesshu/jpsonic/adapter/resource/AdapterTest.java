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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.model.Player;
import com.tesshu.jpsonic.domain.model.TranscodingDefinition;
import com.tesshu.jpsonic.domain.model.TranscodingDefinition.BitRateLimit;
import com.tesshu.jpsonic.domain.model.User;
import com.tesshu.jpsonic.domain.model.UserSettings;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.domain.provider.resource.PlayerProvider;
import com.tesshu.jpsonic.domain.provider.resource.TranscodingProvider;
import com.tesshu.jpsonic.domain.provider.resource.UserProvider;
import com.tesshu.jpsonic.domain.registrar.UserRegister;
import com.tesshu.jpsonic.feature.crypt.upnp.StreamPayload;
import com.tesshu.jpsonic.feature.crypt.upnp.StreamPayload.StreamType;
import com.tesshu.jpsonic.feature.crypt.upnp.UpnpPayloadCodec;
import com.tesshu.jpsonic.persistence.NeedsDB;
import com.tesshu.jpsonic.persistence.api.entity.Transcoding;
import com.tesshu.jpsonic.persistence.api.repository.PlayerDao;
import com.tesshu.jpsonic.persistence.core.repository.UserDao;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.TranscodingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@NeedsDB
class AdapterTest extends AbstractNeedsScan {

    @Autowired
    private PlayerProvider playerProvider;
    @Autowired
    private TranscodingProvider transcodingProvider;
    @Autowired
    private MusicFolderProvider musicFolderProvider;

    @Autowired
    private TranscodingService transcodingService;
    @Autowired
    private UserProvider userProvider;
    @Autowired
    private UserRegister userRegister;
    @Autowired
    private PlayerDao playerDao;
    @Autowired
    private PlayerService playerService;

    @Autowired
    private UpnpPayloadCodec upnpPayloadCodec;

    @Autowired
    private UserDao userDao;

    @BeforeEach
    void setup() {
        populateDatabaseOnlyOnce();
    }

    @Test
    void testPlayerAndUser() {
        assertEquals(1, playerDao.getAllPlayers().size());
        Player player = playerProvider.getUPnPPlayer();
        assertEquals(1, playerDao.getAllPlayers().size());

        assertNotNull(player);
        assertEquals("guest", player.userName());
        assertEquals(BitRateLimit.OFF, player.bitRateLimit());
        assertNull(player.ipAddress());
        assertNotNull(player.lastSeen());

        UserSettings settings = userProvider.getUserSettings(User.USERNAME_GUEST);
        assertNotNull(settings);
        assertEquals(BitRateLimit.OFF, settings.bitRateLimit());
    }

    @Test
    void testUpnpPayloadCodec() {
        int id = 15_555_500;
        String payloadStr = upnpPayloadCodec.encodeStream(id, StreamType.MUSIC);
        StreamPayload payload = upnpPayloadCodec.decodeStream(payloadStr);
        assertEquals(id, payload.id());
        assertEquals(StreamType.MUSIC, payload.streamType());
    }

    @Test
    void testTranscodingProviderAdapter() {
        Player domainPlayer = playerProvider.getUPnPPlayer();
        List<TranscodingDefinition> transcodingDefinitions = transcodingProvider.get(domainPlayer);
        assertEquals(0, transcodingDefinitions.size());

        com.tesshu.jpsonic.persistence.api.entity.Player player = playerService.getUPnPPlayer();
        List<Transcoding> transcodings = transcodingService.getTranscodingsForPlayer(player);
        assertEquals(0, transcodings.size());
    }

    @Test
    void testUserRegister() {
        com.tesshu.jpsonic.persistence.core.entity.User user = userDao
            .getUserByName(User.USERNAME_GUEST, false);
        assertEquals(0, user.getBytesStreamed());
        assertEquals(0, user.getBytesDownloaded());
        assertEquals(0, user.getBytesUploaded());

        userRegister.incrementByteCounts(User.USERNAME_GUEST, 1, 2, 3);
        user = userDao.getUserByName(User.USERNAME_GUEST, false);
        assertEquals(1, user.getBytesStreamed());
        assertEquals(2, user.getBytesDownloaded());
        assertEquals(3, user.getBytesUploaded());

        userRegister.incrementByteCounts(User.USERNAME_GUEST, 1, 2, 3);
        user = userDao.getUserByName(User.USERNAME_GUEST, false);
        assertEquals(2, user.getBytesStreamed());
        assertEquals(4, user.getBytesDownloaded());
        assertEquals(6, user.getBytesUploaded());
    }

    @Test
    void testMusicFolderProvider() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        assertEquals(3, folders.size());
        MusicFolder musicFolder = folders.get(0);
        int id = musicFolder.id();
        musicFolder = musicFolderProvider.requireMusicFolder(id);
        assertEquals(id, musicFolder.id());
        assertNotNull(musicFolder.name());
        assertNotNull(musicFolder.pathString());
        assertNotNull(musicFolder.changed());
    }
}

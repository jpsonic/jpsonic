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
 * (C) 2021 tesshucom
 */

package com.tesshu.jpsonic.service.upnp.processor;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static org.junit.Assert.assertNotNull;

import com.tesshu.jpsonic.dao.MusicFolderDao;
import com.tesshu.jpsonic.domain.JpsonicComparators;
import com.tesshu.jpsonic.service.JWTSecurityService;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.SettingsService;
import com.tesshu.jpsonic.service.TranscodingService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UpnpProcessorUtilTest {

    @Test
    void testGetAllMusicFolders() {
        SettingsService settingsService = mock(SettingsService.class);
        Mockito.when(settingsService.isDlnaGuestPublish()).thenReturn(true);
        UpnpProcessorUtil upnpProcessorUtil = new UpnpProcessorUtil(settingsService, mock(MusicFolderService.class),
                mock(JpsonicComparators.class), mock(JWTSecurityService.class), mock(TranscodingService.class),
                mock(MusicFolderDao.class));
        assertNotNull(upnpProcessorUtil.getAllMusicFolders());
    }
}

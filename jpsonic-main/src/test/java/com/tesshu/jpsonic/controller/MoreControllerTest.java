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

package com.tesshu.jpsonic.controller;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.domain.provider.master.GenreMasterProvider;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.ModelAndView;

class MoreControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setup() throws ExecutionException, URISyntaxException {
        MusicFolderService musicFolderService = mock(MusicFolderService.class);
        GenreMasterProvider genreMasterProvider = mock(GenreMasterProvider.class);
        PlayerService playerService = mock(PlayerService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new MoreController(musicFolderService, mock(UserService.class),
                    genreMasterProvider))
            .build();
        Mockito
            .when(genreMasterProvider.getLegacyGenres(false, 0, Integer.MAX_VALUE))
            .thenReturn(Collections.emptyList());
        Mockito
            .when(playerService.getPlayer(Mockito.any(), Mockito.any()))
            .thenReturn(new Player());
        List<MusicFolder> musicFolders = Arrays
            .asList(new MusicFolder(1,
                    Path.of(MoreControllerTest.class.getResource("/MEDIAS").toURI()).toString(),
                    "MEDIAS", true, now(), 1, false));
        Mockito
            .when(musicFolderService.getMusicFoldersForUser(ServiceMockUtils.ADMIN_NAME))
            .thenReturn(musicFolders);
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testHandleRequestInternal() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.MORE.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);

        ModelAndView modelAndView = result.getModelAndView();
        assertEquals("more", modelAndView.getViewName());
    }
}

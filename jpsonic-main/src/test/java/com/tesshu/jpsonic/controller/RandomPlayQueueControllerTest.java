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
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.persistence.api.entity.PlayQueue;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.ModelAndView;

@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "PMD.TooManyStaticImports" })
class RandomPlayQueueControllerTest {

    private RandomPlayQueueController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() throws ExecutionException {
        PlayerService playerService = mock(PlayerService.class);
        Player player = new Player();
        player.setUsername(ServiceMockUtils.ADMIN_NAME);
        player.setPlayQueue(new PlayQueue());
        when(playerService.getPlayer(any(), any())).thenReturn(player);
        controller = new RandomPlayQueueController(mock(MusicFolderService.class),
                mock(UserService.class), playerService, mock(MediaFileService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testDoSubmitAction() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders
                .post("/" + ViewName.RANDOM_PLAYQUEUE.value())
                .param(Attributes.Request.SIZE.value(), Integer.toString(24))
                .param(Attributes.Request.MUSIC_FOLDER_ID.value(), Integer.toString(0))
                .param(Attributes.Request.NameConstants.LAST_PLAYED_VALUE, "any")
                .param(Attributes.Request.NameConstants.LAST_PLAYED_COMP, "lt")
                .param(Attributes.Request.NameConstants.YEAR, "any"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);

        ModelAndView modelAndView = result.getModelAndView();
        assertEquals("reload", modelAndView.getViewName());

        @SuppressWarnings("unchecked")
        Map<String, Object> model = (Map<String, Object>) modelAndView.getModel().get("model");
        assertNotNull(model);
    }

    @Test
    void testGetLastPlayed() {
        assertNull(controller.getLastPlayed("any", "lt").getMinLastPlayedDate());
        assertNull(controller.getLastPlayed("any", "lt").getMaxLastPlayedDate());
        assertNull(controller.getLastPlayed("any", "gt").getMinLastPlayedDate());
        assertNull(controller.getLastPlayed("any", "gt").getMaxLastPlayedDate());

        assertNull(controller.getLastPlayed("1day", "lt").getMinLastPlayedDate());
        assertNotNull(controller.getLastPlayed("1day", "lt").getMaxLastPlayedDate());
        assertNotNull(controller.getLastPlayed("1day", "gt").getMinLastPlayedDate());
        assertNull(controller.getLastPlayed("1day", "gt").getMaxLastPlayedDate());

        assertNull(controller.getLastPlayed("1week", "lt").getMinLastPlayedDate());
        assertNotNull(controller.getLastPlayed("1week", "lt").getMaxLastPlayedDate());
        assertNotNull(controller.getLastPlayed("1week", "gt").getMinLastPlayedDate());
        assertNull(controller.getLastPlayed("1week", "gt").getMaxLastPlayedDate());

        assertNull(controller.getLastPlayed("1month", "lt").getMinLastPlayedDate());
        assertNotNull(controller.getLastPlayed("1month", "lt").getMaxLastPlayedDate());
        assertNotNull(controller.getLastPlayed("1month", "gt").getMinLastPlayedDate());
        assertNull(controller.getLastPlayed("1month", "gt").getMaxLastPlayedDate());

        assertNull(controller.getLastPlayed("3months", "lt").getMinLastPlayedDate());
        assertNotNull(controller.getLastPlayed("3months", "lt").getMaxLastPlayedDate());
        assertNotNull(controller.getLastPlayed("3months", "gt").getMinLastPlayedDate());
        assertNull(controller.getLastPlayed("3months", "gt").getMaxLastPlayedDate());

        assertNull(controller.getLastPlayed("6months", "lt").getMinLastPlayedDate());
        assertNotNull(controller.getLastPlayed("6months", "lt").getMaxLastPlayedDate());
        assertNotNull(controller.getLastPlayed("6months", "gt").getMinLastPlayedDate());
        assertNull(controller.getLastPlayed("6months", "gt").getMaxLastPlayedDate());

        assertNull(controller.getLastPlayed("1year", "lt").getMinLastPlayedDate());
        assertNotNull(controller.getLastPlayed("1year", "lt").getMaxLastPlayedDate());
        assertNotNull(controller.getLastPlayed("1year", "gt").getMinLastPlayedDate());
        assertNull(controller.getLastPlayed("1year", "gt").getMaxLastPlayedDate());
    }
}

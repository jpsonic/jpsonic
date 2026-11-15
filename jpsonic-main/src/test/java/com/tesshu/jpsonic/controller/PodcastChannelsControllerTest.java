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
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.infrastructure.scanner.ScannerStateServiceImpl;
import com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode;
import com.tesshu.jpsonic.service.PodcastService;
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

@SuppressWarnings("PMD.TooManyStaticImports")
class PodcastChannelsControllerTest {

    private PodcastService podcastService;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() throws ExecutionException {
        podcastService = mock(PodcastService.class);

        PodcastEpisode episode = new PodcastEpisode(null, null, null, null, null, null, null, null,
                null, null, null, null);
        Mockito.when(podcastService.getNewestEpisodes(10)).thenReturn(Arrays.asList(episode));

        mockMvc = MockMvcBuilders
            .standaloneSetup(new PodcastChannelsController(mock(UserService.class), podcastService,
                    mock(ScannerStateServiceImpl.class), mock(ViewAsListSelector.class)))
            .build();
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    @SuppressWarnings("unchecked")
    void testGet() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.PODCAST_CHANNELS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);

        ModelAndView modelAndView = result.getModelAndView();
        assertEquals("podcastChannels", modelAndView.getViewName());
        Map<String, Object> model = (Map<String, Object>) modelAndView.getModel().get("model");
        List<PodcastChannelsController.PodcastEpisode> episodes = (List<PodcastChannelsController.PodcastEpisode>) model
            .get("newestEpisodes");
        assertEquals(1, episodes.size());
        assertNull(episodes.get(0).getPublishDate());
        assertNull(episodes.get(0).getPublishDateWithZone());
        Mockito.clearInvocations(podcastService);

        Instant now = now();
        PodcastEpisode episode = new PodcastEpisode(null, null, null, null, null, null, null, null,
                null, null, null, null);
        episode.setPublishDate(now);
        Mockito.when(podcastService.getNewestEpisodes(10)).thenReturn(Arrays.asList(episode));
        result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.PODCAST_CHANNELS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        model = (Map<String, Object>) result.getModelAndView().getModel().get("model");
        episodes = (List<PodcastChannelsController.PodcastEpisode>) model.get("newestEpisodes");
        assertEquals(1, episodes.size());
        assertEquals(now, episodes.get(0).getPublishDate());
        assertEquals(ZonedDateTime.ofInstant(now, ZoneId.systemDefault()),
                episodes.get(0).getPublishDateWithZone());
    }
}

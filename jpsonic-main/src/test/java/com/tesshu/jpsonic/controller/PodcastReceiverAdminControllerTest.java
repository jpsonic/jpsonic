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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.domain.system.PodcastStatus;
import com.tesshu.jpsonic.infrastructure.scanner.ScannerStateServiceImpl;
import com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode;
import com.tesshu.jpsonic.service.PodcastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.ServletRequestBindingException;

class PodcastReceiverAdminControllerTest {

    private PodcastService podcastService;
    private ScannerStateServiceImpl scannerStateService;
    private PodcastReceiverAdminController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() throws ExecutionException {
        podcastService = mock(PodcastService.class);
        scannerStateService = mock(ScannerStateServiceImpl.class);
        controller = new PodcastReceiverAdminController(podcastService, scannerStateService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @WithMockUser(username = "admin")
    @Test
    void testGet() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/podcastReceiverAdmin.view"))
            .andExpect(MockMvcResultMatchers.status().isFound())
            .andExpect(MockMvcResultMatchers.redirectedUrl(ViewName.PODCAST_CHANNELS.value()))
            .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
            .andReturn();
        assertNotNull(result);
    }

    @Nested
    class PostTest {

        @Test
        void testAdd() throws ServletRequestBindingException {
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            String url = "http://boo.foo";
            Mockito.when(req.getParameter(Attributes.Request.ADD.value())).thenReturn(url);
            controller.post(req);
            Mockito.verify(podcastService, Mockito.times(1)).createChannel(url);
        }
    }

    @Nested
    class GetTest {

        @Test
        void testDownload() throws ServletRequestBindingException {
            int episodeId = 99;
            PodcastEpisode episode = new PodcastEpisode(episodeId, null, "url", null, null, null,
                    null, null, null, null, PodcastStatus.NEW, null);
            Mockito.when(podcastService.getEpisode(episodeId, false)).thenReturn(episode);
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            Mockito
                .when(req.getParameter(Attributes.Request.DOWNLOAD_EPISODE.value()))
                .thenReturn(Integer.toString(episodeId));
            int channelId = 1;
            Mockito
                .when(req.getParameter(Attributes.Request.CHANNEL_ID.value()))
                .thenReturn(Integer.toString(channelId));

            controller.get(req);
            Mockito.verify(podcastService, Mockito.times(1)).downloadEpisode(episode);
        }

        @Test
        void testDeleteChannel() throws ServletRequestBindingException {
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            int channelId = 99;
            Mockito
                .when(req.getParameter(Attributes.Request.CHANNEL_ID.value()))
                .thenReturn(Integer.toString(channelId));

            controller.get(req);
            Mockito.verify(podcastService, Mockito.never()).deleteChannel(channelId);

            Mockito
                .when(req.getParameter(Attributes.Request.DELETE_CHANNEL.value()))
                .thenReturn(Integer.toString(channelId));
            controller.get(req);
            Mockito.verify(podcastService, Mockito.times(1)).deleteChannel(channelId);
        }

        @Test
        void testDeleteEpisode() throws ServletRequestBindingException {
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            int episodeId = 99;
            Mockito
                .when(req.getParameter(Attributes.Request.CHANNEL_ID.value()))
                .thenReturn(Integer.toString(episodeId));
            Mockito
                .when(req.getParameter(Attributes.Request.DELETE_EPISODE.value()))
                .thenReturn(Integer.toString(episodeId));
            controller.get(req);
            Mockito.verify(podcastService, Mockito.times(1)).deleteEpisode(episodeId, true);
        }

        @Test
        void testReflesh() throws ServletRequestBindingException {
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            int channelId = 99;
            Mockito
                .when(req.getParameter(Attributes.Request.REFRESH.value()))
                .thenReturn(Integer.toString(channelId));
            controller.get(req);
            Mockito.verify(podcastService, Mockito.times(1)).refreshAllChannels(true);
            Mockito.verify(podcastService, Mockito.never()).refreshChannel(channelId, true);
            Mockito.clearInvocations(podcastService);

            Mockito
                .when(req.getParameter(Attributes.Request.CHANNEL_ID.value()))
                .thenReturn(Integer.toString(channelId));
            controller.get(req);
            Mockito.verify(podcastService, Mockito.never()).refreshAllChannels(true);
            Mockito.verify(podcastService, Mockito.times(1)).refreshChannel(channelId, true);
        }

        @Test
        void testIsScanning() throws ServletRequestBindingException {
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            String url = "http://boo.foo";
            Mockito.when(req.getParameter(Attributes.Request.ADD.value())).thenReturn(url);
            int episodeId = 99;
            PodcastEpisode episode = new PodcastEpisode(episodeId, null, "url", null, null, null,
                    null, null, null, null, PodcastStatus.NEW, null);
            Mockito.when(podcastService.getEpisode(episodeId, false)).thenReturn(episode);
            Mockito
                .when(req.getParameter(Attributes.Request.DOWNLOAD_EPISODE.value()))
                .thenReturn(Integer.toString(episodeId));
            int channelId = 99;
            Mockito
                .when(req.getParameter(Attributes.Request.CHANNEL_ID.value()))
                .thenReturn(Integer.toString(channelId));
            Mockito
                .when(req.getParameter(Attributes.Request.DELETE_CHANNEL.value()))
                .thenReturn(Integer.toString(channelId));
            Mockito
                .when(req.getParameter(Attributes.Request.DELETE_EPISODE.value()))
                .thenReturn(Integer.toString(episodeId));
            Mockito
                .when(req.getParameter(Attributes.Request.REFRESH.value()))
                .thenReturn(Integer.toString(channelId));

            Mockito.when(scannerStateService.isScanning()).thenReturn(true);
            controller.get(req);

            Mockito.verify(podcastService, Mockito.never()).createChannel(url);
            Mockito.verify(podcastService, Mockito.never()).downloadEpisode(episode);
            Mockito.verify(podcastService, Mockito.never()).deleteChannel(channelId);
            Mockito.verify(podcastService, Mockito.never()).deleteEpisode(episodeId, true);
            Mockito.verify(podcastService, Mockito.never()).refreshAllChannels(true);
            Mockito.verify(podcastService, Mockito.never()).refreshChannel(channelId, true);
        }
    }
}

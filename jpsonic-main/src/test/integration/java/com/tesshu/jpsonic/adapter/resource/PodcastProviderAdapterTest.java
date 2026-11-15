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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.tesshu.jpsonic.domain.model.PodcastChannel;
import com.tesshu.jpsonic.domain.model.PodcastChannel.ChannelPhase;
import com.tesshu.jpsonic.domain.model.PodcastEpisode;
import com.tesshu.jpsonic.domain.provider.resource.PodcastProvider;
import com.tesshu.jpsonic.domain.system.PodcastStatus;
import com.tesshu.jpsonic.persistence.NeedsDB;
import com.tesshu.jpsonic.persistence.api.repository.PodcastDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@NeedsDB
public class PodcastProviderAdapterTest {

    @Autowired
    private PodcastDao podcastDao;
    
    @Autowired
    private PodcastProvider podcastProvider; 

    @Test
    public void testCountChannels() {
        assertEquals(0, podcastDao.countChannels());

        int channelId = podcastDao
            .createChannel(
                    new com.tesshu.jpsonic.persistence.api.entity.PodcastChannel("http://foo"));

        assertEquals(0, podcastProvider.countChannels());
        assertEquals(0, podcastProvider.countChannels(PodcastChannel.ChannelPhase.COMPLETED));
        assertEquals(0, podcastProvider.countChannels(PodcastChannel.ChannelPhase.DELETED));
        assertEquals(0, podcastProvider.countChannels(PodcastChannel.ChannelPhase.DOWNLOADING));
        assertEquals(0, podcastProvider.countChannels(PodcastChannel.ChannelPhase.SKIPPED));
        assertEquals(1, podcastProvider.countChannels(PodcastChannel.ChannelPhase.NEW));
        assertEquals(0, podcastProvider.countChannels(PodcastChannel.ChannelPhase.ERROR));

        podcastDao.deleteChannel(channelId);
    }

    @Test
    public void testCountEpisodes() {
        int channelId = podcastDao
                .createChannel(
                        new com.tesshu.jpsonic.persistence.api.entity.PodcastChannel("http://foo"));
        PodcastChannel channel = podcastProvider.requireChannel(channelId);
        podcastDao
            .createEpisode(new com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode(null,
                    channelId, "http://bar", null, null, null, null, null, null, null,
                    PodcastStatus.NEW, null));

        assertEquals(0, podcastProvider.countEpisodes(channel));
        assertEquals(0, podcastProvider.countEpisodes(channel, PodcastEpisode.EpisodePhase.COMPLETED));
        assertEquals(0, podcastProvider.countEpisodes(channel, PodcastEpisode.EpisodePhase.DELETED));
        assertEquals(0, podcastProvider.countEpisodes(channel, PodcastEpisode.EpisodePhase.DOWNLOADING));
        assertEquals(0, podcastProvider.countEpisodes(channel, PodcastEpisode.EpisodePhase.SKIPPED));
        assertEquals(1, podcastProvider.countEpisodes(channel, PodcastEpisode.EpisodePhase.NEW));
        assertEquals(0, podcastProvider.countEpisodes(channel, PodcastEpisode.EpisodePhase.ERROR));

        podcastDao.deleteChannel(channelId);
        podcastDao
            .getEpisodes(channelId)
            .forEach(episode -> podcastDao.deleteEpisode(episode.getId()));
        assertEquals(0, podcastDao.countEpisodes(channel, PodcastEpisode.EpisodePhase.NEW));
    }

    @Test
    public void testFindChannels() {

        assertTrue(podcastProvider.findChannels(0, Integer.MAX_VALUE).isEmpty());
        assertEquals(0, podcastDao.countChannels());

        int channelId = podcastDao
            .createChannel(
                    new com.tesshu.jpsonic.persistence.api.entity.PodcastChannel("http://foo"));

        assertTrue(podcastProvider.findChannels(0, Integer.MAX_VALUE).isEmpty());
        assertTrue(podcastProvider.findChannels(0, Integer.MAX_VALUE, PodcastChannel.ChannelPhase.COMPLETED).isEmpty());
        assertTrue(podcastProvider.findChannels(0, Integer.MAX_VALUE, PodcastChannel.ChannelPhase.DELETED).isEmpty());
        assertTrue(podcastProvider.findChannels(0, Integer.MAX_VALUE, PodcastChannel.ChannelPhase.DOWNLOADING).isEmpty());
        assertTrue(podcastProvider.findChannels(0, Integer.MAX_VALUE, PodcastChannel.ChannelPhase.SKIPPED).isEmpty());
        assertFalse(podcastProvider.findChannels(0, Integer.MAX_VALUE, PodcastChannel.ChannelPhase.NEW).isEmpty());
        assertTrue(podcastProvider.findChannels(0, Integer.MAX_VALUE, PodcastChannel.ChannelPhase.ERROR).isEmpty());

        assertTrue(podcastProvider.findChannels(1, Integer.MAX_VALUE, PodcastChannel.ChannelPhase.NEW).isEmpty());
        assertTrue(podcastProvider.findChannels(1, 0, PodcastChannel.ChannelPhase.NEW).isEmpty());
        assertFalse(podcastProvider.findChannels(0, 1, PodcastChannel.ChannelPhase.NEW).isEmpty());

        podcastDao.deleteChannel(channelId);
    }

    @Test
    public void testFindEpisodes() {
        int channelId = podcastDao
                .createChannel(
                        new com.tesshu.jpsonic.persistence.api.entity.PodcastChannel("http://foo"));
        PodcastChannel channel = podcastProvider.requireChannel(channelId);
        podcastDao
            .createEpisode(new com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode(null,
                    channelId, "http://bar", null, null, null, null, null, null, null,
                    PodcastStatus.NEW, null));
        assertEquals(0, podcastDao.countChannels());
        assertTrue(podcastProvider.findEpisodes(channel, 0, Integer.MAX_VALUE).isEmpty());
        assertTrue(podcastProvider.findEpisodes(channel, 0, Integer.MAX_VALUE, PodcastEpisode.EpisodePhase.COMPLETED).isEmpty());
        assertTrue(podcastProvider.findEpisodes(channel, 0, Integer.MAX_VALUE, PodcastEpisode.EpisodePhase.DELETED).isEmpty());
        assertTrue(podcastProvider.findEpisodes(channel, 0, Integer.MAX_VALUE, PodcastEpisode.EpisodePhase.DOWNLOADING).isEmpty());
        assertTrue(podcastProvider.findEpisodes(channel, 0, Integer.MAX_VALUE, PodcastEpisode.EpisodePhase.SKIPPED).isEmpty());
        assertFalse(podcastProvider.findEpisodes(channel, 0, Integer.MAX_VALUE, PodcastEpisode.EpisodePhase.NEW).isEmpty());
        assertTrue(podcastProvider.findEpisodes(channel, 0, Integer.MAX_VALUE, PodcastEpisode.EpisodePhase.ERROR).isEmpty());

        assertTrue(podcastProvider.findEpisodes(channel, 1, Integer.MAX_VALUE, PodcastEpisode.EpisodePhase.NEW).isEmpty());
        assertTrue(podcastProvider.findEpisodes(channel, 1, 0, PodcastEpisode.EpisodePhase.NEW).isEmpty());
        assertFalse(podcastProvider.findEpisodes(channel, 0, 1, PodcastEpisode.EpisodePhase.NEW).isEmpty());

        podcastDao.deleteChannel(channelId);
        podcastProvider.findEpisodes(channel, 0, Integer.MAX_VALUE, PodcastEpisode.EpisodePhase.NEW).stream().forEach(episode ->
        podcastDao.deleteEpisode(episode.id()));
    }

    @Test
    public void testRequireChannel() {
        int channelId = podcastDao
            .createChannel(
                    new com.tesshu.jpsonic.persistence.api.entity.PodcastChannel("http://foo"));

        PodcastChannel podcastChannel = podcastProvider.requireChannel(channelId);
        assertNotNull(podcastChannel);
        assertEquals(channelId, podcastChannel.id());
        assertTrue(podcastChannel.title().isEmpty());
        assertEquals(ChannelPhase.NEW, podcastChannel.channelPhase());
        assertTrue(podcastChannel.thumbUri().isEmpty());

        podcastDao.deleteChannel(channelId);
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> podcastProvider.requireChannel(channelId));
    }
}

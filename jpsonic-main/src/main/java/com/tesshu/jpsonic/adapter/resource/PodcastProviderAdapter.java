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

import com.tesshu.jpsonic.domain.model.PodcastChannel;
import com.tesshu.jpsonic.domain.model.PodcastChannel.ChannelPhase;
import com.tesshu.jpsonic.domain.model.PodcastEpisode;
import com.tesshu.jpsonic.domain.model.PodcastEpisode.EpisodePhase;
import com.tesshu.jpsonic.domain.provider.resource.PodcastProvider;
import com.tesshu.jpsonic.persistence.api.repository.PodcastDao;
import org.springframework.stereotype.Component;

@Component
class PodcastProviderAdapter implements PodcastProvider {

    private final PodcastDao podcastDao;

    PodcastProviderAdapter(PodcastDao podcastDao) {
        this.podcastDao = podcastDao;
    }

    @Override
    public int countChannels(ChannelPhase... phases) {
        return podcastDao.countChannels(phases);
    }

    @Override
    public int countEpisodes(PodcastChannel channel, EpisodePhase... phases) {
        return podcastDao.countEpisodes(channel, phases);
    }

    @Override
    public List<PodcastChannel> findChannels(long offset, long count,
            PodcastChannel.ChannelPhase... phases) {
        return podcastDao.findChannels(offset, count, phases);
    }

    @Override
    public List<PodcastEpisode> findEpisodes(PodcastChannel channel, long offset, long count,
            EpisodePhase... phases) {
        return podcastDao.findEpisodes(channel, offset, count, phases);
    }

    @Override
    public PodcastChannel requireChannel(int channelId) {
        PodcastChannel podcastChannel = podcastDao.requireChannel(channelId);
        if (podcastChannel == null) {
            throw new IllegalArgumentException("The specified PodcastChannel cannot be found.");
        }
        return podcastChannel;
    }
}

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
 * (C) 2009 Sindre Mehus
 * (C) 2016 Airsonic Authors
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.persistence.api.repository;

import static com.tesshu.jpsonic.persistence.base.DaoUtils.nullableInstantOf;
import static com.tesshu.jpsonic.persistence.base.DaoUtils.questionMarks;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.tesshu.jpsonic.domain.model.PodcastEpisode.EpisodePhase;
import com.tesshu.jpsonic.domain.system.PodcastStatus;
import com.tesshu.jpsonic.persistence.api.entity.PodcastChannel;
import com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides database services for Podcast channels and episodes.
 *
 * @author Sindre Mehus
 */
@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "PMD.FieldDeclarationsShouldBeAtStartOfClass" })
@Repository
public class PodcastDao {

    private static final String CHANNEL_INSERT_COLUMNS = """
            url, title, description, image_url, status, error_message\s
            """;
    private static final String CHANNEL_QUERY_COLUMNS = "id, " + CHANNEL_INSERT_COLUMNS;
    private static final String EPISODE_INSERT_COLUMNS = """
            channel_id, url, path, title, description, publish_date,
            duration, bytes_total, bytes_downloaded, status, error_message\s
            """;
    private static final String EPISODE_QUERY_COLUMNS = "id, " + EPISODE_INSERT_COLUMNS;

    private final TemplateWrapper template;
    private final PodcastChannelRowMapper channelRowMapper;
    private final PodcastEpisodeRowMapper episodeRowMapper;

    public PodcastDao(TemplateWrapper templateWrapper) {
        template = templateWrapper;
        channelRowMapper = new PodcastChannelRowMapper();
        episodeRowMapper = new PodcastEpisodeRowMapper();
    }

    @Transactional
    public int createChannel(PodcastChannel channel) {
        String sql = "insert into podcast_channel (" + CHANNEL_INSERT_COLUMNS + ") values ("
                + questionMarks(CHANNEL_INSERT_COLUMNS) + ")";
        template
            .update(sql, channel.getUrl(), channel.getTitle(), channel.getDescription(),
                    channel.getImageUrl(), channel.getStatus().name(), channel.getErrorMessage());
        return template.queryForInt("select max(id) from podcast_channel", -1);
    }

    public List<PodcastChannel> getAllChannels() {
        String sql = "select " + CHANNEL_QUERY_COLUMNS + "from podcast_channel";
        return template.query(sql, channelRowMapper);
    }

    public @Nullable PodcastChannel getChannel(int channelId) {
        String sql = "select " + CHANNEL_QUERY_COLUMNS + """
                from podcast_channel
                where id=?
                """;
        return template.queryOne(sql, channelRowMapper, channelId);
    }

    public void updateChannel(PodcastChannel channel) {
        String sql = """
                update podcast_channel
                set url=?, title=?, description=?, image_url=?, status=?, error_message=?
                where id=?
                """;
        template
            .update(sql, channel.getUrl(), channel.getTitle(), channel.getDescription(),
                    channel.getImageUrl(), channel.getStatus().name(), channel.getErrorMessage(),
                    channel.getId());
    }

    public void deleteChannel(int id) {
        String sql = """
                delete from podcast_channel
                where id=?
                """;
        template.update(sql, id);
    }

    public void createEpisode(PodcastEpisode episode) {
        String sql = "insert into podcast_episode (" + EPISODE_INSERT_COLUMNS + ") values ("
                + questionMarks(EPISODE_INSERT_COLUMNS) + ")";
        template
            .update(sql, episode.getChannelId(), episode.getUrl(), episode.getPath(),
                    episode.getTitle(), episode.getDescription(), episode.getPublishDate(),
                    episode.getDuration(), episode.getBytesTotal(), episode.getBytesDownloaded(),
                    episode.getStatus().name(), episode.getErrorMessage());
    }

    public List<PodcastEpisode> getEpisodes(int channelId) {
        String sql = "select " + EPISODE_QUERY_COLUMNS + """
                from podcast_episode
                where channel_id = ? and status != ?
                order by publish_date desc
                """;
        return template.query(sql, episodeRowMapper, channelId, PodcastStatus.DELETED.name());
    }

    public List<PodcastEpisode> getNewestEpisodes(int count) {
        String sql = "select " + EPISODE_QUERY_COLUMNS + """
                from podcast_episode
                where status = ? and publish_date is not null
                order by publish_date desc
                limit ?
                """;
        return template.query(sql, episodeRowMapper, PodcastStatus.COMPLETED.name(), count);
    }

    public @Nullable PodcastEpisode getEpisode(int episodeId) {
        String sql = "select " + EPISODE_QUERY_COLUMNS + """
                from podcast_episode
                where id=?
                """;
        return template.queryOne(sql, episodeRowMapper, episodeId);
    }

    public @Nullable PodcastEpisode getEpisodeByUrl(String url) {
        String sql = "select " + EPISODE_QUERY_COLUMNS + " from podcast_episode where url=?";
        return template.queryOne(sql, episodeRowMapper, url);
    }

    public int updateEpisode(PodcastEpisode episode) {
        String sql = """
                update podcast_episode
                set url=?, path=?, title=?, description=?, publish_date=?, duration=?,
                        bytes_total=?, bytes_downloaded=?, status=?, error_message=?
                where id=?
                """;
        return template
            .update(sql, episode.getUrl(), episode.getPath(), episode.getTitle(),
                    episode.getDescription(), episode.getPublishDate(), episode.getDuration(),
                    episode.getBytesTotal(), episode.getBytesDownloaded(),
                    episode.getStatus().name(), episode.getErrorMessage(), episode.getId());
    }

    public void deleteEpisode(int id) {
        String sql = """
                delete from podcast_episode
                where id=?
                """;
        template.update(sql, id);
    }

    private static class PodcastChannelRowMapper implements RowMapper<PodcastChannel> {
        @Override
        public PodcastChannel mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PodcastChannel(rs.getInt(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), PodcastStatus.valueOf(rs.getString(6)),
                    rs.getString(7));
        }
    }

    private static class PodcastEpisodeRowMapper implements RowMapper<PodcastEpisode> {
        @Override
        public PodcastEpisode mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PodcastEpisode(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), rs.getString(6), nullableInstantOf(rs.getTimestamp(7)),
                    rs.getString(8), (Long) rs.getObject(9), (Long) rs.getObject(10),
                    PodcastStatus.valueOf(rs.getString(11)), rs.getString(12));
        }
    }

    // ############################################################################
    // Jpsonic domain

    private final RowMapper<com.tesshu.jpsonic.domain.model.PodcastChannel> domainChannelRowMapper = (
            rs, rowNum) -> new com.tesshu.jpsonic.domain.model.PodcastChannel(rs.getInt(1), // id
                    rs.getString(2), // title
                    rs.getString(3), // channelPhase
                    rs.getString(4)); // thumbUri

    private final RowMapper<com.tesshu.jpsonic.domain.model.PodcastEpisode> domainEpisodeRowMapper = (
            rs, rowNum) -> new com.tesshu.jpsonic.domain.model.PodcastEpisode(rs.getInt(1), // id
                    rs.getString(2), // title
                    rs.getInt(3), // channelId
                    rs.getString(4), // episodePhase
                    nullableInstantOf(rs.getTimestamp(5)), // publishDate
                    rs.getString(6)); // path

    private static final String DOMAIN_CHANNEL_COLUMNS_QUERY = "id, title, status, image_url\s";
    private static final String DOMAIN_CHANNEL_TABLE_QUERY = "from podcast_channel\s";
    private static final String DOMAIN_EPISODE_COLUMNS_QUERY = "id, title, channel_id, status, publish_date, path\s";
    private static final String DOMAIN_EPISODE_TABLE_QUERY = "from podcast_episode\s";

    public int countChannels(
            com.tesshu.jpsonic.domain.model.PodcastChannel.ChannelPhase... phases) {
        if (phases.length == 0) {
            return 0;
        }
        Map<String, Object> args = Map
            .of("phases",
                    Stream
                        .of(phases)
                        .map(com.tesshu.jpsonic.domain.model.PodcastChannel.ChannelPhase::name)
                        .toList());
        return template
            .namedQueryForInt(
                    "select count(*) " + DOMAIN_CHANNEL_TABLE_QUERY + "where status in (:phases)",
                    0, args);
    }

    public int countEpisodes(com.tesshu.jpsonic.domain.model.PodcastChannel channel,
            EpisodePhase... phases) {
        if (phases.length == 0) {
            return 0;
        }

        // spotless:off
        Map<String, Object> args = Map.of(
                "channelId", channel.id(),
                "phases",
                    Stream
                        .of(phases)
                        .map(com.tesshu.jpsonic.domain.model.PodcastEpisode.EpisodePhase::name)
                        .toList());
        // spotless:on

        return template.namedQueryForInt("select count(*) " + DOMAIN_EPISODE_TABLE_QUERY + """
                where status in (:phases) and channel_id = :channelId
                """, 0, args);
    }

    public List<com.tesshu.jpsonic.domain.model.PodcastChannel> findChannels(long offset,
            long count, com.tesshu.jpsonic.domain.model.PodcastChannel.ChannelPhase... phases) {
        if (phases.length == 0) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = Map.of(
                "phases", Stream
                        .of(phases)
                        .map(com.tesshu.jpsonic.domain.model.PodcastChannel.ChannelPhase::name)
                        .toList(),
                "offset", offset,
                "count", count);
        // spotless:on

        String sql = "select " + DOMAIN_CHANNEL_COLUMNS_QUERY + DOMAIN_CHANNEL_TABLE_QUERY + """
                where status in (:phases)
                offset :offset limit :count
                """;
        return template.namedQuery(sql, domainChannelRowMapper, args);
    }

    public List<com.tesshu.jpsonic.domain.model.PodcastEpisode> findEpisodes(
            com.tesshu.jpsonic.domain.model.PodcastChannel channel, long offset, long count,
            EpisodePhase... phases) {
        if (phases.length == 0) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = Map.of(
                "channelId", channel.id(),
                "phases", Stream
                        .of(phases)
                        .map(com.tesshu.jpsonic.domain.model.PodcastEpisode.EpisodePhase::name)
                        .toList(),
                "offset", offset,
                "count", count);
        // spotless:on

        String sql = "select " + DOMAIN_EPISODE_COLUMNS_QUERY + DOMAIN_EPISODE_TABLE_QUERY + """
                where status in (:phases) and channel_id = :channelId
                offset :offset limit :count
                """;
        return template.namedQuery(sql, domainEpisodeRowMapper, args);
    }

    public com.tesshu.jpsonic.domain.model.PodcastChannel requireChannel(int channelId) {
        String sql = "select " + DOMAIN_CHANNEL_COLUMNS_QUERY + DOMAIN_CHANNEL_TABLE_QUERY
                + "where id=?";
        return template.queryOne(sql, domainChannelRowMapper, channelId);
    }
}

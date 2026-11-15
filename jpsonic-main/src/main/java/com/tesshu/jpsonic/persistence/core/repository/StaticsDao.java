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
 * (C) 2022 tesshucom
 */

package com.tesshu.jpsonic.persistence.core.repository;

import static com.tesshu.jpsonic.persistence.base.DaoUtils.nullableInstantOf;
import static com.tesshu.jpsonic.persistence.base.DaoUtils.prefix;
import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import com.tesshu.jpsonic.persistence.core.entity.MediaLibraryStatistics;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent.ScanEventType;
import com.tesshu.jpsonic.persistence.core.entity.ScanLog;
import com.tesshu.jpsonic.persistence.core.entity.ScanLog.ScanLogType;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class StaticsDao {

    private static final String EVENT_QUERY_COLUMNS = """
            start_date, executed, type, max_memory, total_memory,
            free_memory, max_thread, comment\s
            """;

    private final TemplateWrapper template;
    private final RowMapper<ScanLog> scanLogMapper = (ResultSet rs, int rowNum) -> new ScanLog(
            nullableInstantOf(rs.getTimestamp(1)), ScanLogType.valueOf(rs.getString(2)));
    private final RowMapper<ScanEvent> scanEventMapper = (ResultSet rs,
            int rowNum) -> new ScanEvent(nullableInstantOf(rs.getTimestamp(1)),
                    nullableInstantOf(rs.getTimestamp(2)), ScanEventType.of(rs.getString(3)),
                    rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getInt(7), rs.getString(8));
    private final RowMapper<MediaLibraryStatistics> libStatsMapper = (ResultSet rs,
            int rowNum) -> new MediaLibraryStatistics(nullableInstantOf(rs.getTimestamp(1)),
                    rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6),
                    rs.getLong(7), rs.getLong(8));

    public StaticsDao(TemplateWrapper templateWrapper) {
        template = templateWrapper;
    }

    @Transactional
    public void createFolderLog(@NonNull Instant executed, @NonNull ScanEventType type) {
        boolean exist = template.queryForInt("""
                select count(*)
                from scan_log
                where start_date = ?
                """, 0, executed) > 0;
        if (!exist) {
            createScanLog(executed, ScanLogType.FOLDER_CHANGED);
        }
        createScanEvent(new ScanEvent(executed, now(), type, null, null, null, null, null));
    }

    public List<ScanLog> getScanLog(ScanLogType type) {
        return template.query("""
                select start_date, type
                from scan_log
                where type=?
                order by start_date desc
                """, scanLogMapper, type.name());
    }

    public void createScanLog(@NonNull Instant scanDate, @NonNull ScanLogType type) {
        template.update("""
                insert into scan_log (start_date, type)
                values (?, ?)
                """, scanDate, type.name());
    }

    public void deleteBefore(Instant retention) {
        template.update("""
                delete from scan_log
                where type = ?
                        and (start_date <>
                                (select start_date
                                from scan_log
                                where type = ?
                                order by start_date desc
                                limit 1))
                        and start_date < ?
                """, ScanLogType.SCAN_ALL.name(), ScanLogType.SCAN_ALL.name(), retention);
        template.update("""
                delete from scan_log
                where type <> ? and start_date < ?
                """, ScanLogType.SCAN_ALL.name(), retention);
        template
            .update("""
                    delete from scan_event
                    where (type=? or type=? or type=?)
                    """, ScanEventType.FOLDER_CREATE.name(), ScanEventType.FOLDER_DELETE.name(),
                    ScanEventType.FOLDER_UPDATE.name());
    }

    public void createScanEvent(@NonNull ScanEvent scanEvent) {
        template
            .update("""
                    insert into scan_event (%s)
                    values(?, ?, ?, ?, ?, ?, ?, ?)
                    """.formatted(EVENT_QUERY_COLUMNS), scanEvent.getStartDate(),
                    scanEvent.getExecuted(), scanEvent.getType().name(), scanEvent.getMaxMemory(),
                    scanEvent.getTotalMemory(), scanEvent.getFreeMemory(), scanEvent.getMaxThread(),
                    scanEvent.getComment());
    }

    public void deleteOtherThanLatest() {
        template.update("""
                delete from scan_log
                where type = ? and start_date <>
                        (select start_date
                        from scan_log
                        where type = ?
                        order by start_date desc
                        limit 1)
                """, ScanLogType.SCAN_ALL.name(), ScanLogType.SCAN_ALL.name());
        template.update("""
                delete from scan_log
                where type <> ?
                """, ScanLogType.SCAN_ALL.name());
        template
            .update("""
                    delete from scan_event
                    where (type=? or type=? or type=?)
                    """, ScanEventType.FOLDER_CREATE.name(), ScanEventType.FOLDER_DELETE.name(),
                    ScanEventType.FOLDER_UPDATE.name());
    }

    public List<MediaLibraryStatistics> getRecentMediaLibraryStatistics() {
        String sql = """
                select start_date, folder_id, artist_count, album_count,
                        song_count, video_count, total_size, total_duration
                from media_library_statistics
                where start_date =
                        (select max(start_date)
                        from scan_log
                        where type = 'SCAN_ALL')
                """;
        return template.query(sql, libStatsMapper);
    }

    public boolean isNeverScanned() {
        return template.queryForInt("""
                select count(*)
                from scan_log
                where type='SCAN_ALL'
                """, 0) == 0;
    }

    public boolean isfolderChangedSinceLastScan() {
        Map<String, Object> args = LegacyMap
            .of("folderChanges", Arrays
                .asList(ScanEventType.FOLDER_CREATE.name(), ScanEventType.FOLDER_DELETE.name(),
                        ScanEventType.FOLDER_UPDATE.name()),
                    "scanAll", ScanLogType.SCAN_ALL.name());
        return template.namedQueryForInt("""
                select count(*)
                from scan_event events
                where type in (:folderChanges) and events.start_date >
                        (select start_date
                        from scan_log
                        where type = :scanAll
                        order by start_date desc
                        limit 1)
                """, 0, args) > 0;
    }

    public @NonNull MediaLibraryStatistics gatherMediaLibraryStatistics(@NonNull Instant scanDate,
            @NonNull MusicFolder folder) {
        String query = """
                select (select id from music_folder where path = :folder) as folder_id,
                        count(
                                case
                                    when present
                                            and folder = :folder
                                            and type = :directory
                                            and media_file.path <> folder
                                    then 1
                                end) as artist_count,
                        count(
                                case
                                    when present
                                            and folder = :folder
                                            and type = :album
                                    then 1
                                end) as album_count,
                        count(
                                case
                                    when present and folder = :folder and type = :music
                                    then 1
                                end) as song_count,
                        count(
                                case
                                    when present and folder = :folder and type = :video
                                    then 1
                                end) as video_count,
                        sum(
                                case
                                    when present and folder = :folder and type = :music
                                    then file_size
                                end) as total_size,
                        sum(
                                case
                                    when present and folder = :folder and type = :music
                                    then duration_seconds
                                end) as total_duration
                from media_file
                """;
        RowMapper<MediaLibraryStatistics> mapper = (ResultSet rs, int rowNum) -> {
            return new MediaLibraryStatistics(scanDate, rs.getInt(1), rs.getInt(2), rs.getInt(3),
                    rs.getInt(4), rs.getInt(5), rs.getLong(6), rs.getLong(7));
        };
        Map<String, Object> args = Map
            .of("folder", folder.getPathString(), "directory", MediaFile.MediaType.DIRECTORY.name(),
                    "album", MediaFile.MediaType.ALBUM.name(), "music",
                    MediaFile.MediaType.MUSIC.name(), "video", MediaFile.MediaType.VIDEO.name());
        return template.namedQuery(query, mapper, args).get(0);
    }

    public void createMediaLibraryStatistics(MediaLibraryStatistics stats) {
        String sql = """
                insert into media_library_statistics
                        (start_date, folder_id, artist_count,
                        album_count, song_count, video_count,
                        total_size, total_duration)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        template
            .update(sql, stats.getExecuted(), stats.getFolderId(), stats.getArtistCount(),
                    stats.getAlbumCount(), stats.getSongCount(), stats.getVideoCount(),
                    stats.getTotalSize(), stats.getTotalDuration());
    }

    public List<ScanEvent> getScanEvents(@NonNull Instant scanDate) {
        String sql = "select " + EVENT_QUERY_COLUMNS + """
                from scan_event
                where start_date = ? order by executed
                """;
        return template.query(sql, scanEventMapper, scanDate);
    }

    public @NonNull ScanEventType getLastScanEventType(@NonNull Instant startDate) {
        String sql = """
                select type
                from scan_event
                where start_date=?
                order by executed desc
                limit 1
                """;
        List<String> result = template.queryForStrings(sql, startDate);
        if (result.isEmpty()) {
            return ScanEventType.UNKNOWN;
        }
        return ScanEventType.of(result.get(0));
    }

    public List<ScanEvent> getLastScanAllStatuses() {
        @SuppressWarnings("deprecation")
        Map<String, Object> args = LegacyMap
            .of("eventTypes",
                    Arrays
                        .asList(ScanEventType.SUCCESS.name(), ScanEventType.FINISHED.name(),
                                ScanEventType.DESTROYED.name(), ScanEventType.CANCELED.name()),
                    "logType", ScanLogType.SCAN_ALL.name());
        String sql = "select " + prefix(EVENT_QUERY_COLUMNS, "event") + """
                from scan_event event
                join
                        (select start_date
                        from scan_log
                        where type = :logType
                        order by start_date desc limit 1) last_log
                on last_log.start_date = event.start_date
                where type in (:eventTypes)
                """;
        return template.namedQuery(sql, scanEventMapper, args);
    }
}

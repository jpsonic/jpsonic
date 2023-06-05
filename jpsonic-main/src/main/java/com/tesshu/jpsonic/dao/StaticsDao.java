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

package com.tesshu.jpsonic.dao;

import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.tesshu.jpsonic.domain.MediaLibraryStatistics;
import com.tesshu.jpsonic.domain.ScanEvent;
import com.tesshu.jpsonic.domain.ScanEvent.ScanEventType;
import com.tesshu.jpsonic.domain.ScanLog;
import com.tesshu.jpsonic.domain.ScanLog.ScanLogType;
import com.tesshu.jpsonic.util.LegacyMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class StaticsDao extends AbstractDao {

    private static final String EVENT_QUERY_COLUMNS = "start_date, executed, type, max_memory, total_memory, free_memory, max_thread, comment";

    private final RowMapper<ScanLog> scanLogMapper = (ResultSet rs, int rowNum) -> {
        return new ScanLog(nullableInstantOf(rs.getTimestamp(1)), ScanLogType.valueOf(rs.getString(2)));
    };
    private final RowMapper<ScanEvent> scanEventMapper = (ResultSet rs, int rowNum) -> {
        return new ScanEvent(nullableInstantOf(rs.getTimestamp(1)), nullableInstantOf(rs.getTimestamp(2)),
                ScanEventType.of(rs.getString(3)), rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getInt(7),
                rs.getString(8));
    };
    private final RowMapper<MediaLibraryStatistics> libStatsMapper = (ResultSet rs, int rowNum) -> {
        return new MediaLibraryStatistics(nullableInstantOf(rs.getTimestamp(1)), rs.getInt(2), rs.getInt(3),
                rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getLong(7), rs.getLong(8));
    };

    public StaticsDao(DaoHelper daoHelper) {
        super(daoHelper);
    }

    @Transactional
    public void createFolderLog(@NonNull Instant executed, @NonNull ScanEventType type) {
        boolean exist = queryForInt("select count(*) from scan_log where start_date = ?", 0, executed) > 0;
        if (!exist) {
            createScanLog(executed, ScanLogType.FOLDER_CHANGED);
        }
        createScanEvent(new ScanEvent(executed, now(), type, null, null, null, null, null));
    }

    public List<ScanLog> getScanLog(ScanLogType type) {
        return query("select start_date, type from scan_log where type=? order by start_date desc", scanLogMapper,
                type.name());
    }

    public void createScanLog(@NonNull Instant scanDate, @NonNull ScanLogType type) {
        update("insert into scan_log (start_date, type) values (?, ?)", scanDate, type.name());
    }

    public void deleteBefore(Instant retention) {
        update("delete from scan_log where type = ? and (start_date <> (select start_date from scan_log "
                + "where type = ? order by start_date desc limit 1)) and start_date < ?", ScanLogType.SCAN_ALL.name(),
                ScanLogType.SCAN_ALL.name(), retention);
        update("delete from scan_log where type <> ? and start_date < ?", ScanLogType.SCAN_ALL.name(), retention);
        update("delete from scan_event where (type=? or type=? or type=?)", ScanEventType.FOLDER_CREATE.name(),
                ScanEventType.FOLDER_DELETE.name(), ScanEventType.FOLDER_UPDATE.name());
    }

    public void createScanEvent(@NonNull ScanEvent scanEvent) {
        update("insert into scan_event (" + EVENT_QUERY_COLUMNS + ") values(?, ?, ?, ?, ?, ?, ?, ?)",
                scanEvent.getStartDate(), scanEvent.getExecuted(), scanEvent.getType().name(), scanEvent.getMaxMemory(),
                scanEvent.getTotalMemory(), scanEvent.getFreeMemory(), scanEvent.getMaxThread(),
                scanEvent.getComment());
    }

    public void deleteOtherThanLatest() {
        update("delete from scan_log where type = ? and start_date <> (select start_date from scan_log "
                + "where type = ? order by start_date desc limit 1)", ScanLogType.SCAN_ALL.name(),
                ScanLogType.SCAN_ALL.name());
        update("delete from scan_log where type <> ?", ScanLogType.SCAN_ALL.name());
        update("delete from scan_event where (type=? or type=? or type=?)", ScanEventType.FOLDER_CREATE.name(),
                ScanEventType.FOLDER_DELETE.name(), ScanEventType.FOLDER_UPDATE.name());
    }

    public List<MediaLibraryStatistics> getRecentMediaLibraryStatistics() {
        String sql = "select start_date, folder_id, artist_count, album_count, song_count, video_count, total_size, total_duration "
                + "from media_library_statistics where start_date = (select max(start_date) from scan_log where type = 'SCAN_ALL')";
        return query(sql, libStatsMapper);
    }

    public boolean isNeverScanned() {
        return queryForInt("select count(*) from scan_log where type='SCAN_ALL'", 0) == 0;
    }

    public boolean isfolderChangedSinceLastScan() {
        Map<String, Object> args = LegacyMap.of("folderChanges", Arrays.asList(ScanEventType.FOLDER_CREATE.name(),
                ScanEventType.FOLDER_DELETE.name(), ScanEventType.FOLDER_UPDATE.name()), "scanAll",
                ScanLogType.SCAN_ALL.name());
        return namedQueryForInt(
                "select count(*) from scan_event events where type in (:folderChanges) and events.start_date > "
                        + "(select start_date from scan_log where type = :scanAll order by start_date desc limit 1)",
                0, args) > 0;
    }

    public void createMediaLibraryStatistics(MediaLibraryStatistics stats) {
        String sql = "insert into media_library_statistics (start_date, folder_id, artist_count, album_count, "
                + "song_count, video_count, total_size, total_duration) values (?, ?, ?, ?, ?, ?, ?, ?)";
        update(sql, stats.getExecuted(), stats.getFolderId(), stats.getArtistCount(), stats.getAlbumCount(),
                stats.getSongCount(), stats.getVideoCount(), stats.getTotalSize(), stats.getTotalDuration());
    }

    public List<ScanEvent> getScanEvents(@NonNull Instant scanDate) {
        String sql = "select " + EVENT_QUERY_COLUMNS + " from scan_event where start_date = ? order by executed";
        return query(sql, scanEventMapper, scanDate);
    }

    public ScanEventType getLastScanEventType(@NonNull Instant startDate) {
        String sql = "select type from scan_event where start_date=? order by executed desc limit 1";
        List<String> result = queryForStrings(sql, startDate);
        if (result.isEmpty()) {
            return ScanEventType.UNKNOWN;
        }
        return ScanEventType.of(result.get(0));
    }

    public List<ScanEvent> getLastScanAllStatuses() {
        @SuppressWarnings("deprecation")
        Map<String, Object> args = LegacyMap.of("eventTypes",
                Arrays.asList(ScanEventType.SUCCESS.name(), ScanEventType.FINISHED.name(),
                        ScanEventType.DESTROYED.name(), ScanEventType.CANCELED.name()),
                "logType", ScanLogType.SCAN_ALL.name());
        String sql = "select " + prefix(EVENT_QUERY_COLUMNS, "event") + " from scan_event event "
                + "join (select start_date from scan_log where type = :logType order by start_date desc limit 1) last_log "
                + "on last_log.start_date = event.start_date where type in (:eventTypes)";
        return namedQuery(sql, scanEventMapper, args);
    }
}
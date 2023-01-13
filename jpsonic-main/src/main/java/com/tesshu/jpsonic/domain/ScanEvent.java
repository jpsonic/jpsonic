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

package com.tesshu.jpsonic.domain;

import java.time.Instant;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ScanEvent {

    private Instant startDate;
    private Instant executed;
    private String type;
    private long maxMemory;
    private long totalMemory;
    private long freeMemory;
    private String comment;

    public ScanEvent(@NonNull Instant startDate, @NonNull Instant executed, @NonNull ScanEventType type,
            @Nullable Long maxMemory, @Nullable Long totalMemory, @Nullable Long freeMemory, @Nullable String comment) {
        super();
        this.startDate = startDate;
        this.executed = executed;
        this.type = type.name();
        setMaxMemory(maxMemory);
        setTotalMemory(totalMemory);
        setFreeMemory(freeMemory);
        this.comment = comment;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getExecuted() {
        return executed;
    }

    public void setExecuted(Instant executed) {
        this.executed = executed;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getMaxMemory() {
        return maxMemory;
    }

    public final void setMaxMemory(Long maxMemory) {
        this.maxMemory = maxMemory == null ? -1 : maxMemory;
    }

    public long getTotalMemory() {
        return totalMemory;
    }

    public final void setTotalMemory(Long totalMemory) {
        this.totalMemory = totalMemory == null ? -1 : totalMemory;
    }

    public long getFreeMemory() {
        return freeMemory;
    }

    public final void setFreeMemory(Long freeMemory) {
        this.freeMemory = freeMemory == null ? -1 : freeMemory;
    }

    public @Nullable String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public enum ScanEventType {
        FINISHED,
        FAILED,
        DESTROYED,
        BEFORE_SCAN,
        PARSE_AUDIO,
        PARSE_PODCAST,
        PARSED_COUNT,
        MARK_NON_PRESENT,
        UPDATE_ALBUM_COUNTS,
        UPDATE_GENRE_MASTER,
        UPDATE_ORDER,
        RUN_STATS,
        IMPORT_PLAYLISTS,
        CHECKPOINT,
        AFTER_SCAN
    }
}

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

package com.tesshu.jpsonic.persistence.api.entity;

import java.time.Instant;

import com.tesshu.jpsonic.domain.contract.Nameable;
import com.tesshu.jpsonic.util.StringUtil;
import org.checkerframework.checker.nullness.qual.NonNull;

@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public class Playlist implements Nameable {

    private int id;
    private String username;
    private boolean shared;
    private String name;
    private String comment;
    private int fileCount;
    private int durationSeconds;
    private Instant created;
    private Instant changed;
    private String importedFrom;
    private transient String reading;

    public Playlist() {
    }

    public Playlist(int id, String username, boolean shared, String name, String comment,
            int fileCount, int durationSeconds, Instant created, Instant changed,
            String importedFrom) {
        this.id = id;
        this.username = username;
        this.shared = shared;
        this.name = name;
        this.comment = comment;
        this.fileCount = fileCount;
        this.durationSeconds = durationSeconds;
        this.created = created;
        this.changed = changed;
        this.importedFrom = importedFrom;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getDurationAsString() {
        return StringUtil.formatDurationMSS(durationSeconds);
    }

    public @NonNull Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getChanged() {
        return changed;
    }

    public void setChanged(Instant changed) {
        this.changed = changed;
    }

    public String getImportedFrom() {
        return importedFrom;
    }

    public void setImportedFrom(String importedFrom) {
        this.importedFrom = importedFrom;
    }

    public String getReading() {
        return reading;
    }

    public void setReading(String reading) {
        this.reading = reading;
    }

    @Override
    public @NonNull String name() {
        return getName();
    }
}

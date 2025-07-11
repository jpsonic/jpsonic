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
 * (C) 2015 Sindre Mehus
 * (C) 2016 Airsonic Authors
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.domain;

import java.time.Instant;
import java.util.List;

/**
 * Used to save the play queue state for a user.
 * <p/>
 * Can be used to share the play queue (including currently playing track and
 * position within that track) across client apps.
 *
 * @author Sindre Mehus
 */
public class SavedPlayQueue {

    private Integer id;
    private String username;
    private List<Integer> mediaFileIds;
    private Integer currentMediaFileId;
    private Long positionMillis;
    private Instant changed;
    private String changedBy;

    public SavedPlayQueue(Integer id, String username, List<Integer> mediaFileIds,
            Integer currentMediaFileId, Long positionMillis, Instant changed, String changedBy) {
        this.id = id;
        this.username = username;
        this.mediaFileIds = mediaFileIds;
        this.currentMediaFileId = currentMediaFileId;
        this.positionMillis = positionMillis;
        this.changed = changed;
        this.changedBy = changedBy;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<Integer> getMediaFileIds() {
        return mediaFileIds;
    }

    public void setMediaFileIds(List<Integer> mediaFileIds) {
        this.mediaFileIds = mediaFileIds;
    }

    public Integer getCurrentMediaFileId() {
        return currentMediaFileId;
    }

    public void setCurrentMediaFileId(Integer currentMediaFileId) {
        this.currentMediaFileId = currentMediaFileId;
    }

    public Long getPositionMillis() {
        return positionMillis;
    }

    public void setPositionMillis(Long positionMillis) {
        this.positionMillis = positionMillis;
    }

    public Instant getChanged() {
        return changed;
    }

    public void setChanged(Instant changed) {
        this.changed = changed;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }
}

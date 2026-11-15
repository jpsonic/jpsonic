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
import java.util.Optional;

import com.tesshu.jpsonic.domain.contract.Indexable;
import com.tesshu.jpsonic.domain.contract.Orderable;
import org.checkerframework.checker.nullness.qual.NonNull;

@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public final class Artist implements Orderable, Indexable {

    private int id;
    private String name;
    private String coverArtPath;
    private int albumCount;
    private Instant lastScanned;
    private boolean present;
    private Integer folderId;
    private String sort;
    private String reading;
    private int order;
    private String musicIndex;

    public Artist() {
        musicIndex = "";
    }

    public Artist(int id, String name, String coverArtPath, int albumCount, Instant lastScanned,
            boolean present, Integer folderId, String sort, String reading, int order,
            String musicIndex) {
        this();
        this.id = id;
        this.name = name;
        this.coverArtPath = coverArtPath;
        this.albumCount = albumCount;
        this.lastScanned = lastScanned;
        this.present = present;
        this.folderId = folderId;
        this.sort = sort;
        this.reading = reading;
        this.order = order;
        this.musicIndex = musicIndex;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public @NonNull String getName() {
        return name;
    }

    @Override
    public @NonNull String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCoverArtPath() {
        return coverArtPath;
    }

    public void setCoverArtPath(String coverArtPath) {
        this.coverArtPath = coverArtPath;
    }

    public int getAlbumCount() {
        return albumCount;
    }

    public void setAlbumCount(int albumCount) {
        this.albumCount = albumCount;
    }

    public Instant getLastScanned() {
        return lastScanned;
    }

    public void setLastScanned(Instant lastScanned) {
        this.lastScanned = lastScanned;
    }

    public boolean isPresent() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public void setFolderId(Integer folderId) {
        this.folderId = folderId;
    }

    public Integer getFolderId() {
        return folderId;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public String getReading() {
        return reading;
    }

    @Override
    public String reading() {
        return reading;
    }

    public void setReading(String reading) {
        this.reading = reading;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public void setOrder(int order) {
        this.order = order;
    }

    public String getMusicIndex() {
        return musicIndex;
    }

    @Override
    public Optional<String> musicIndex() {
        return Optional.ofNullable(musicIndex);
    }

    public void setMusicIndex(String musicIndex) {
        this.musicIndex = musicIndex;
    }
}

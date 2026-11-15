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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.domain.contract.Indexable;
import com.tesshu.jpsonic.domain.contract.Orderable;
import com.tesshu.jpsonic.infrastructure.filesystem.PathInspector;
import com.tesshu.jpsonic.util.PlayerUtils;
import com.tesshu.jpsonic.util.StringUtil;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A media file (audio, video or directory) with an assortment of its meta data.
 *
 * @author Sindre Mehus
 */
// Will change from non-sealed to final in the future.
@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public class MediaFile implements Orderable, Indexable {

    private int id;
    private String pathString;
    private String folder;
    private MediaType mediaType;
    private String format;
    private String title;
    private String albumName;
    private String artist;
    private String albumArtist;
    private Integer discNumber;
    private Integer trackNumber;
    private Integer year;
    private String genre;
    private Integer bitRate;
    private boolean variableBitRate;
    private Integer durationSeconds;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private String coverArtPathString;
    private String parentPathString;
    private int playCount;
    private Instant lastPlayed;
    private String comment;
    private Instant created;
    private Instant changed;
    private Instant lastScanned;
    private Instant starredDate;
    private Instant childrenLastUpdated;
    private boolean present;
    private int version;
    private String musicBrainzReleaseId;
    private String musicBrainzRecordingId;
    private String composer;
    private String artistSort;
    private String albumSort;
    private String titleSort;
    private String albumArtistSort;
    private String composerSort;
    private String artistReading;
    private String albumReading;
    private String albumArtistReading;
    private int order;
    private String artistSortRaw;
    private String albumSortRaw;
    private String albumArtistSortRaw;
    private String composerSortRaw;
    private String musicIndex;
    private transient int rownum;

    public MediaFile() {
        this.musicIndex = "";
    }

    public MediaFile(int id, String path, String folder, MediaType mediaType, String format,
            String title, String albumName, String artist, String albumArtist, Integer discNumber,
            Integer trackNumber, Integer year, String genre, Integer bitRate,
            boolean variableBitRate, Integer durationSeconds, Long fileSize, Integer width,
            Integer height, String coverArtPath, String parentPath, int playCount,
            Instant lastPlayed, String comment, Instant created, Instant changed,
            Instant lastScanned, Instant childrenLastUpdated, boolean present, int version,
            String musicBrainzReleaseId, String musicBrainzRecordingId, String composer,
            String artistSort, String albumSort, String titleSort, String albumArtistSort,
            String composerSort, String artistReading, String albumReading,
            String albumArtistReading, String artistSortRaw, String albumSortRaw,
            String albumArtistSortRaw, String composerSortRaw, int order, String musicIndex) {
        this();
        this.id = id;
        this.pathString = path;
        this.folder = folder;
        this.mediaType = mediaType;
        this.format = format;
        this.title = title;
        this.albumName = albumName;
        this.artist = artist;
        this.albumArtist = albumArtist;
        this.discNumber = discNumber;
        this.trackNumber = trackNumber;
        this.year = year;
        this.genre = genre;
        this.bitRate = bitRate;
        this.variableBitRate = variableBitRate;
        this.durationSeconds = durationSeconds;
        this.fileSize = fileSize;
        this.width = width;
        this.height = height;
        this.coverArtPathString = coverArtPath;
        this.parentPathString = parentPath;
        this.playCount = playCount;
        this.lastPlayed = lastPlayed;
        this.comment = comment;
        this.created = created;
        this.changed = changed;
        this.lastScanned = lastScanned;
        this.childrenLastUpdated = childrenLastUpdated;
        this.present = present;
        this.version = version;
        this.musicBrainzReleaseId = musicBrainzReleaseId;
        this.musicBrainzRecordingId = musicBrainzRecordingId;
        this.composer = composer;
        this.artistSort = artistSort;
        this.albumSort = albumSort;
        this.titleSort = titleSort;
        this.albumArtistSort = albumArtistSort;
        this.composerSort = composerSort;
        this.artistReading = artistReading;
        this.albumReading = albumReading;
        this.albumArtistReading = albumArtistReading;
        this.artistSortRaw = artistSortRaw;
        this.albumSortRaw = albumSortRaw;
        this.albumArtistSortRaw = albumArtistSortRaw;
        this.composerSortRaw = composerSortRaw;
        this.order = order;
        this.musicIndex = musicIndex;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPathString() {
        return pathString;
    }

    public void setPathString(String path) {
        this.pathString = path;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public Path toPath() {
        return Path.of(pathString);
    }

    public boolean exists() {
        return Files.exists(toPath());
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    public boolean isVideo() {
        return mediaType == MediaType.VIDEO;
    }

    public boolean isAudio() {
        return mediaType == MediaType.MUSIC || mediaType == MediaType.AUDIOBOOK
                || mediaType == MediaType.PODCAST;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isDirectory() {
        return !isFile();
    }

    public boolean isFile() {
        return mediaType != MediaType.DIRECTORY && mediaType != MediaType.ALBUM;
    }

    public boolean isAlbum() {
        return mediaType == MediaType.ALBUM;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public @Nullable String getAlbumName() {
        return albumName;
    }

    public void setAlbumName(String album) {
        this.albumName = album;
    }

    public @Nullable String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public @Nullable String getAlbumArtist() {
        return albumArtist;
    }

    public void setAlbumArtist(String albumArtist) {
        this.albumArtist = albumArtist;
    }

    public @NonNull String getName() {
        return name();
    }

    @Override
    public @NonNull String name() {
        if (isFile()) {
            return PlayerUtils.defaultIfNull(title, PathInspector.getBaseName(pathString));
        }
        return PathInspector.getBaseName(pathString);
    }

    public @Nullable Integer getDiscNumber() {
        return discNumber;
    }

    public void setDiscNumber(Integer discNumber) {
        this.discNumber = discNumber;
    }

    public @Nullable Integer getTrackNumber() {
        return trackNumber;
    }

    public void setTrackNumber(Integer trackNumber) {
        this.trackNumber = trackNumber;
    }

    public @Nullable Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public @Nullable String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public @Nullable Integer getBitRate() {
        return bitRate;
    }

    public void setBitRate(Integer bitRate) {
        this.bitRate = bitRate;
    }

    public boolean isVariableBitRate() {
        return variableBitRate;
    }

    public void setVariableBitRate(boolean variableBitRate) {
        this.variableBitRate = variableBitRate;
    }

    public @Nullable Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public @Nullable String getDurationString() {
        if (durationSeconds == null) {
            return null;
        }
        // Return in M:SS or H:MM:SS
        return StringUtil.formatDuration(durationSeconds);
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public @Nullable Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public @Nullable Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public @Nullable String getCoverArtPathString() {
        return coverArtPathString;
    }

    public void setCoverArtPathString(String coverArtPath) {
        this.coverArtPathString = coverArtPath;
    }

    public String getParentPathString() {
        return parentPathString;
    }

    public void setParentPathString(String parentPath) {
        this.parentPathString = parentPath;
    }

    public @Nullable Path getParent() {
        return toPath().getParent();
    }

    public int getPlayCount() {
        return playCount;
    }

    public void setPlayCount(int playCount) {
        this.playCount = playCount;
    }

    public @Nullable Instant getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(Instant lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public @Nullable String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public @NonNull Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public @NonNull Instant getChanged() {
        return changed;
    }

    public void setChanged(Instant changed) {
        this.changed = changed;
    }

    public Instant getLastScanned() {
        return lastScanned;
    }

    public void setLastScanned(Instant lastScanned) {
        this.lastScanned = lastScanned;
    }

    public Instant getStarredDate() {
        return starredDate;
    }

    public void setStarredDate(Instant starredDate) {
        this.starredDate = starredDate;
    }

    public @Nullable String getMusicBrainzReleaseId() {
        return musicBrainzReleaseId;
    }

    public void setMusicBrainzReleaseId(String musicBrainzReleaseId) {
        this.musicBrainzReleaseId = musicBrainzReleaseId;
    }

    public @Nullable String getMusicBrainzRecordingId() {
        return musicBrainzRecordingId;
    }

    public void setMusicBrainzRecordingId(String musicBrainzRecordingId) {
        this.musicBrainzRecordingId = musicBrainzRecordingId;
    }

    /**
     * Returns when the children was last updated in the database.
     */
    public Instant getChildrenLastUpdated() {
        return childrenLastUpdated;
    }

    public void setChildrenLastUpdated(Instant childrenLastUpdated) {
        this.childrenLastUpdated = childrenLastUpdated;
    }

    public boolean isPresent() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (pathString == null) {
            return false;
        } else if (o instanceof MediaFile that) {
            return pathString.equals(that.pathString);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return pathString.hashCode();
    }

    public Optional<Path> getCoverArtPath() {
        return coverArtPathString == null ? Optional.empty()
                : Optional.of(Path.of(coverArtPathString));
    }

    public @Nullable String getComposer() {
        return composer;
    }

    public void setComposer(String composer) {
        this.composer = composer;
    }

    public @Nullable String getArtistSort() {
        return artistSort;
    }

    public void setArtistSort(String artistSort) {
        this.artistSort = artistSort;
    }

    public @Nullable String getAlbumSort() {
        return albumSort;
    }

    public void setAlbumSort(String albumSort) {
        this.albumSort = albumSort;
    }

    public @Nullable String getTitleSort() {
        return titleSort;
    }

    public void setTitleSort(String titleSort) {
        this.titleSort = titleSort;
    }

    public @Nullable String getAlbumArtistSort() {
        return albumArtistSort;
    }

    public void setAlbumArtistSort(String albumArtistSort) {
        this.albumArtistSort = albumArtistSort;
    }

    public @Nullable String getComposerSort() {
        return composerSort;
    }

    public void setComposerSort(String composerSort) {
        this.composerSort = composerSort;
    }

    public @Nullable String getArtistReading() {
        return artistReading;
    }

    public void setArtistReading(String artistReading) {
        this.artistReading = artistReading;
    }

    public @Nullable String getReading() {
        return reading();
    }

    @Override
    public @Nullable String reading() {
        if (MediaType.ALBUM == mediaType) {
            return albumReading;
        }
        return artistReading;
    }

    public @Nullable String getAlbumReading() {
        return albumReading;
    }

    public void setAlbumReading(String albumReading) {
        this.albumReading = albumReading;
    }

    public @Nullable String getAlbumArtistReading() {
        return albumArtistReading;
    }

    public void setAlbumArtistReading(String albumArtistReading) {
        this.albumArtistReading = albumArtistReading;
    }

    public String getArtistSortRaw() {
        return artistSortRaw;
    }

    public void setArtistSortRaw(String artistSortRaw) {
        this.artistSortRaw = artistSortRaw;
    }

    public String getAlbumSortRaw() {
        return albumSortRaw;
    }

    public void setAlbumSortRaw(String albumSortRaw) {
        this.albumSortRaw = albumSortRaw;
    }

    public String getAlbumArtistSortRaw() {
        return albumArtistSortRaw;
    }

    public void setAlbumArtistSortRaw(String albumArtistSortRaw) {
        this.albumArtistSortRaw = albumArtistSortRaw;
    }

    public String getComposerSortRaw() {
        return composerSortRaw;
    }

    public void setComposerSortRaw(String composerSortRaw) {
        this.composerSortRaw = composerSortRaw;
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

    public int getRownum() {
        return rownum;
    }

    public void setRownum(int rownum) {
        this.rownum = rownum;
    }

    @Override
    public String toString() {
        return getName();
    }

    public static List<Integer> toIdList(List<MediaFile> from) {
        return from.stream().map(toId()).collect(Collectors.toList());
    }

    public static Function<MediaFile, Integer> toId() {
        return MediaFile::getId;
    }

    public enum MediaType {
        MUSIC, PODCAST, AUDIOBOOK, VIDEO, DIRECTORY, ALBUM
    }
}

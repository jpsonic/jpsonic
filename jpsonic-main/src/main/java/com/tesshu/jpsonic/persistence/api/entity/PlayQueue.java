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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.tesshu.jpsonic.SuppressFBWarnings;
import com.tesshu.jpsonic.ThreadSafe;
import com.tesshu.jpsonic.infrastructure.concurrent.ReadWriteLockSupport;
import com.tesshu.jpsonic.persistence.param.ShuffleSelectionParam;

/**
 * A play queue is a list of music files that are associated to a remote player.
 *
 * @author Sindre Mehus
 */
@SuppressFBWarnings(value = { "AT_NONATOMIC_OPERATIONS_ON_SHARED_VARIABLE",
        "AT_STALE_THREAD_WRITE_OF_PRIMITIVE" }, justification = "False positive. Guaranteed by locking.")
@ThreadSafe(enableChecks = false)
public class PlayQueue implements ReadWriteLockSupport {

    private final ReentrantReadWriteLock sequenceLock = new ReentrantReadWriteLock();
    private final AtomicBoolean repeatEnabled;

    private List<MediaFile> files;
    private String name;
    private Status status;

    /**
     * Historical debt: This field exists only because PlayQueue has been used as a
     * View Object and carries UI-related state in the legacy design. It is not part
     * of the domain model and should not influence persistence-layer structure.
     *
     * The field is marked transient to avoid accidental persistence. It must be
     * removed once PlayQueue and its View Object responsibilities are separated in
     * a future redesign.
     */
    private transient ShuffleSelectionParam shuffleSelectionParam;
    private InternetRadio internetRadio;
    private int indexBackup;

    /**
     * The index of the current song, or -1 is the end of the playlist is reached.
     * Note that both the index and the playlist size can be zero.
     */
    private int index;

    /**
     * Used for undo functionality.
     */
    private List<MediaFile> filesBackup;

    public PlayQueue() {
        repeatEnabled = new AtomicBoolean();
        files = new ArrayList<>();
        name = "(unnamed)";
        status = Status.PLAYING;
        filesBackup = new ArrayList<>();
    }

    /**
     * Returns the user-defined name of the playlist.
     *
     * @return The name of the playlist, or <code>null</code> if no name has been
     *         assigned.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the user-defined name of the playlist.
     *
     * @param name The name of the playlist.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the current song in the playlist.
     *
     * @return The current song in the playlist, or <code>null</code> if no current
     *         song exists.
     */
    public MediaFile getCurrentFile() {
        writeLock(sequenceLock);
        try {
            if (index == -1 || index == 0 && size() == 0) {
                if (getStatus() != Status.STOPPED) {
                    setStatus(Status.STOPPED);
                }
                return null;
            } else {
                MediaFile file = files.get(index);
                // Remove file from playlist if it doesn't exist.
                if (!file.exists()) {
                    files.remove(index);
                    index = Math.max(0, Math.min(index, size() - 1));
                    return getCurrentFile();
                }
                return file;
            }
        } finally {
            writeUnlock(sequenceLock);
        }
    }

    /**
     * Returns all music files in the playlist.
     *
     * @return All music files in the playlist.
     */
    public List<MediaFile> getFiles() {
        readLock(sequenceLock);
        try {
            return files;
        } finally {
            readUnlock(sequenceLock);
        }
    }

    /**
     * Returns the music file at the given index.
     *
     * @param index The index.
     *
     * @return The music file at the given index.
     *
     * @throws IndexOutOfBoundsException If the index is out of range.
     */
    public MediaFile getFile(int index) {
        readLock(sequenceLock);
        try {
            return files.get(index);
        } finally {
            readUnlock(sequenceLock);
        }
    }

    /**
     * Skip to the next song in the playlist.
     */
    public void next() {
        writeLock(sequenceLock);
        try {
            index++;
            if (index >= size()) {
                index = isRepeatEnabled() ? 0 : -1;
            }
        } finally {
            writeUnlock(sequenceLock);
        }
    }

    /**
     * Returns the number of songs in the playlists.
     *
     * @return The number of songs in the playlists.
     */
    public int size() {
        readLock(sequenceLock);
        try {
            return files.size();
        } finally {
            readUnlock(sequenceLock);
        }
    }

    /**
     * Returns whether the playlist is empty.
     *
     * @return Whether the playlist is empty.
     */
    public boolean isEmpty() {
        readLock(sequenceLock);
        try {
            return files.isEmpty();
        } finally {
            readUnlock(sequenceLock);
        }
    }

    /**
     * Returns the index of the current song.
     *
     * @return The index of the current song, or -1 if the end of the playlist is
     *         reached.
     */
    public int getIndex() {
        readLock(sequenceLock);
        try {
            return index;
        } finally {
            readUnlock(sequenceLock);
        }
    }

    /**
     * Sets the index of the current song.
     *
     * @param index The index of the current song.
     */
    public void setIndex(int index) {
        writeLock(sequenceLock);
        try {
            makeBackup();
            this.index = Math.max(0, Math.min(index, size() - 1));
            setStatus(Status.PLAYING);
        } finally {
            writeUnlock(sequenceLock);
        }
    }

    /**
     * Adds one or more music file to the playlist.
     *
     * @param mediaFiles The music files to add.
     * @param index      Where to add them.
     */
    public void addFilesAt(Iterable<MediaFile> mediaFiles, final int index) {
        writeLock(sequenceLock);
        try {
            makeBackup();
            AtomicInteger i = new AtomicInteger(index);
            mediaFiles.forEach(m -> files.add(i.getAndIncrement(), m));
            setStatus(Status.PLAYING);
        } finally {
            writeUnlock(sequenceLock);
        }
    }

    /**
     * Adds one or more music file to the playlist.
     *
     * @param append     Whether existing songs in the playlist should be kept.
     * @param mediaFiles The music files to add.
     */
    public void addFiles(boolean append, Iterable<MediaFile> mediaFiles) {
        writeLock(sequenceLock);
        try {
            makeBackup();
            if (!append) {
                index = 0;
                files.clear();
            }
            mediaFiles.forEach(files::add);
            setStatus(Status.PLAYING);
        } finally {
            writeUnlock(sequenceLock);
        }
    }

    /**
     * Convenience method, equivalent to {@link #addFiles(boolean, Iterable)}.
     */
    public void addFiles(boolean append, MediaFile... mediaFiles) {
        writeLock(sequenceLock);
        try {
            addFiles(append, Arrays.asList(mediaFiles));
        } finally {
            writeUnlock(sequenceLock);
        }
    }

    /**
     * Removes the music file at the given index.
     *
     * @param index The playlist index.
     */
    public void removeFileAt(final int index) {
        writeLock(sequenceLock);
        try {
            makeBackup();
            int i = index;
            i = Math.max(0, Math.min(i, size() - 1));
            if (this.index > i) {
                this.index--;
            }
            files.remove(i);

            this.index = Math.max(0, Math.min(this.index, size() - 1));
        } finally {
            writeUnlock(sequenceLock);
        }
    }

    /**
     * Clears the playlist.
     */
    public void clear() {
        writeLock(sequenceLock);
        try {
            makeBackup();
            files.clear();
            setShuffleSelectionParam(null);
            setInternetRadio(null);
            index = 0;
        } finally {
            writeUnlock(sequenceLock);
        }
    }

    /**
     * Shuffles the playlist.
     */
    public void shuffle() {
        writeLock(sequenceLock);
        try {
            makeBackup();
            MediaFile currentFile = getCurrentFile();
            Collections.shuffle(files);
            if (currentFile != null) {
                Collections.swap(files, files.indexOf(currentFile), 0);
                index = 0;
            }
        } finally {
            writeUnlock(sequenceLock);
        }
    }

    /**
     * Sorts the playlist according to the given sort order.
     */
    public void sort(Comparator<MediaFile> comparator) {
        writeLock(sequenceLock);
        try {
            makeBackup();
            MediaFile currentFile = getCurrentFile();
            files.sort(comparator);
            if (currentFile != null) {
                index = files.indexOf(currentFile);
            }
        } finally {
            writeUnlock(sequenceLock);
        }
    }

    /**
     * Rearranges the playlist using the provided indexes.
     */
    public void rearrange(int... indexes) {
        writeLock(sequenceLock);
        try {
            makeBackup();
            if (indexes == null || indexes.length != size()) {
                return;
            }
            MediaFile[] newFiles = new MediaFile[files.size()];
            for (int i = 0; i < indexes.length; i++) {
                newFiles[i] = files.get(indexes[i]);
            }
            for (int i = 0; i < indexes.length; i++) {
                if (index == indexes[i]) {
                    index = i;
                    break;
                }
            }

            files.clear();
            files.addAll(Arrays.asList(newFiles));
        } finally {
            writeUnlock(sequenceLock);
        }
    }

    /**
     * Moves the song at the given index one step up.
     *
     * @param index The playlist index.
     */
    public void moveUp(int index) {
        moveDown(index - 1);
    }

    /**
     * Moves the song at the given index one step down.
     *
     * @param index The playlist index.
     */
    public void moveDown(int index) {
        writeLock(sequenceLock);
        try {
            makeBackup();
            if (index < 0 || index >= size() - 1) {
                return;
            }
            Collections.swap(files, index, index + 1);

            if (this.index == index) {
                this.index++;
            } else if (this.index == index + 1) {
                this.index--;
            }
        } finally {
            writeUnlock(sequenceLock);
        }
    }

    /**
     * Returns whether the playlist is repeating.
     *
     * @return Whether the playlist is repeating.
     */
    public boolean isRepeatEnabled() {
        return repeatEnabled.get();
    }

    /**
     * Sets whether the playlist is repeating.
     *
     * @param repeatEnabled Whether the playlist is repeating.
     */
    public void setRepeatEnabled(boolean repeatEnabled) {
        this.repeatEnabled.set(repeatEnabled);
    }

    /**
     * Returns whether the play queue is in shuffle radio mode.
     *
     * @return Whether the play queue is a shuffle radio mode.
     */
    public boolean isShuffleRadioEnabled() {
        return this.shuffleSelectionParam != null;
    }

    /**
     * Returns whether the play queue is a internet radio mode.
     *
     * @return Whether the play queue is a internet radio mode.
     */
    public boolean isInternetRadioEnabled() {
        return this.internetRadio != null;
    }

    /**
     * Revert the last operation.
     */
    public void undo() {
        writeLock(sequenceLock);
        try {
            int indexTmp = index;
            index = indexBackup;
            files = filesBackup;
            indexBackup = indexTmp;
            filesBackup = new ArrayList<>(files);
        } finally {
            writeUnlock(sequenceLock);
        }
    }

    /**
     * Returns the playlist status.
     *
     * @return The playlist status.
     */
    public Status getStatus() {
        readLock(sequenceLock);
        try {
            return status;
        } finally {
            readUnlock(sequenceLock);
        }
    }

    /**
     * Sets the playlist status.
     *
     * @param status The playlist status.
     */
    public void setStatus(Status status) {
        writeLock(sequenceLock);
        try {
            this.status = status;
            if (index == -1) {
                index = Math.max(0, Math.min(index, size() - 1));
            }
        } finally {
            writeUnlock(sequenceLock);
        }
    }

    /**
     * Sets the current internet radio
     *
     * @param internetRadio An internet radio, or <code>null</code> if this is not
     *                      an internet radio playlist
     */
    public void setInternetRadio(InternetRadio internetRadio) {
        this.internetRadio = internetRadio;
    }

    /**
     * Gets the current internet radio
     *
     * @return The current internet radio, or <code>null</code> if this is not an
     *         internet radio playlist
     */
    public InternetRadio getInternetRadio() {
        return internetRadio;
    }

    /**
     * Returns the criteria used to generate this random playlist
     *
     * @return The search criteria, or <code>null</code> if this is not a random
     *         playlist.
     */
    public ShuffleSelectionParam getShuffleSelectionParam() {
        return shuffleSelectionParam;
    }

    /**
     * Sets the criteria used to generate this random playlist
     *
     * @param shuffleSelectionParam The search criteria, or <code>null</code> if
     *                              this is not a random playlist.
     */
    public void setShuffleSelectionParam(ShuffleSelectionParam shuffleSelectionParam) {
        this.shuffleSelectionParam = shuffleSelectionParam;
    }

    /**
     * Returns the total length in bytes.
     *
     * @return The total length in bytes.
     */
    public long length() {
        long length;
        readLock(sequenceLock);
        try {
            length = files.stream().mapToLong(MediaFile::getFileSize).sum();
        } finally {
            readUnlock(sequenceLock);
        }
        return length;
    }

    private void makeBackup() {
        filesBackup = new ArrayList<>(files);
        indexBackup = index;
    }

    /**
     * Playlist status.
     */
    public enum Status {
        PLAYING, STOPPED
    }

    /**
     * Playlist sort order.
     */
    public enum SortOrder {
        TRACK, ARTIST, ALBUM
    }
}

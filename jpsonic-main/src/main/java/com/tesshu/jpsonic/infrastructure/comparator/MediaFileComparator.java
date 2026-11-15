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
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.comparator;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.text.Collator;
import java.util.Comparator;
import java.util.Objects;

import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.util.PlayerUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Comparator for sorting media files.
 */
class MediaFileComparator implements Comparator<MediaFile> {

    private final boolean sortAlbumsByYear;
    private final Comparator<Object> comparator;

    MediaFileComparator(boolean sortAlbumsByYear, Collator collator) {
        this.sortAlbumsByYear = sortAlbumsByYear;
        comparator = collator;
    }

    @Override
    public int compare(MediaFile a, MediaFile b) {

        // Directories before files.
        int i = compareDirectoryAndFile(a, b);
        if (i != 0) {
            return i;
        }

        // Non-album directories before album directories.
        i = compareAlbumAndNotAlbum(a, b);
        if (i != 0) {
            return i;
        }

        // Sort albums by year
        if (sortAlbumsByYear && a.isAlbum()) {
            i = nullSafeCompare(a.getYear(), b.getYear(), false);
            if (i != 0) {
                return i;
            }
        }

        if (a.isDirectory()) {
            i = compareDirectory(a, b);
            if (i != 0) {
                return i;
            }
        }

        // Compare by disc and track numbers, if present.
        Integer trackA = getSortableDiscAndTrackNumber(a);
        Integer trackB = getSortableDiscAndTrackNumber(b);
        i = nullSafeCompare(trackA, trackB, false);
        if (i != 0) {
            return i;
        }

        return comparator.compare(a.getPathString(), b.getPathString());
    }

    int compareDirectoryAndFile(MediaFile a, MediaFile b) {
        if (a.isFile() && b.isDirectory()) {
            return 1;
        }
        if (a.isDirectory() && b.isFile()) {
            return -1;
        }
        return 0;
    }

    int compareAlbumAndNotAlbum(@NonNull MediaFile a, @NonNull MediaFile b) {
        if (a.isAlbum() && b.getMediaType() == MediaFile.MediaType.DIRECTORY) {
            return 1;
        }
        if (a.getMediaType() == MediaFile.MediaType.DIRECTORY && b.isAlbum()) {
            return -1;
        }
        return 0;
    }

    int compareDirectory(MediaFile o1, MediaFile o2) {
        if (o1.isAlbum() && o2.isAlbum() && !isEmpty(o1.getAlbumReading())
                && !isEmpty(o2.getAlbumReading())) {
            return comparator.compare(o1.getAlbumReading(), o2.getAlbumReading());
        }
        return comparator
            .compare(Objects.toString(o1.getArtistReading(), EMPTY),
                    Objects.toString(o2.getArtistReading(), EMPTY));
    }

    <T extends Comparable<T>> int nullSafeCompare(@Nullable T a, @Nullable T b,
            boolean nullIsSmaller) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return nullIsSmaller ? -1 : 1;
        }
        if (b == null) {
            return nullIsSmaller ? 1 : -1;
        }
        return a.compareTo(b);
    }

    @Nullable
    Integer getSortableDiscAndTrackNumber(@NonNull MediaFile file) {
        Integer trackNumber = file.getTrackNumber();
        if (trackNumber == null) {
            return null;
        }
        int discNumber = PlayerUtils.defaultIfNull(file.getDiscNumber(), 1);
        return discNumber * 1000 + trackNumber;
    }
}

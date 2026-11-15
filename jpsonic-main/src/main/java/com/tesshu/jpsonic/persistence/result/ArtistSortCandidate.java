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

package com.tesshu.jpsonic.persistence.result;

import java.util.Optional;

import com.tesshu.jpsonic.domain.contract.Indexable;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * A class that represents suggestions for correction of artist sort tags.
 */
@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public final class ArtistSortCandidate extends SortCandidate implements Indexable {

    private MediaType targetType;
    private TargetField targetField;
    private String musicIndex;

    public ArtistSortCandidate(String name, String sort, int targetId, String targetType,
            int targetField) {
        super(name, sort, targetId);
        this.targetField = TargetField.of(targetField);
        this.targetType = MediaType.valueOf(targetType);
    }

    public MediaType getTargetType() {
        return targetType;
    }

    public void setTargetType(MediaType targetType) {
        this.targetType = targetType;
    }

    public @NonNull TargetField getTargetField() {
        return targetField;
    }

    public void setTargetField(TargetField targetField) {
        this.targetField = targetField;
    }

    @Override
    public Optional<String> musicIndex() {
        return Optional.ofNullable(musicIndex);
    }

    @Override
    public @NonNull String name() {
        return getName();
    }

    @Override
    public @NonNull String reading() {
        return getReading();
    }

    public void setMusicIndex(String musicIndex) {
        this.musicIndex = musicIndex;
    }

    public enum TargetField {

        UNKNOWN(-1), ALBUM_ARTIST(0), ARTIST(1), COMPOSER(2);

        private final int value;

        TargetField(int value) {
            this.value = value;
        }

        static TargetField of(int v) {
            if (ALBUM_ARTIST.getValue() == v) {
                return ALBUM_ARTIST;
            } else if (ARTIST.getValue() == v) {
                return ARTIST;
            } else if (COMPOSER.getValue() == v) {
                return COMPOSER;
            }
            return UNKNOWN;
        }

        public int getValue() {
            return value;
        }
    }
}

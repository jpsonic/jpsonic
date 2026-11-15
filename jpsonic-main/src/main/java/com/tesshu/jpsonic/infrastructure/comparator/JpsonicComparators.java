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
import static org.springframework.util.ObjectUtils.isEmpty;

import java.text.Collator;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Supplier;

import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.Artist;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * This class provides Comparator for domain objects.
 * <p>
 * The sorting rules can be changed with some global options and are dynamic.
 * Executed through this class whenever domain objects in the system are sorted.
 */
@Component
@DependsOn({ "settingsFacade", "japaneseReadingUtils" })
public class JpsonicComparators {

    public enum OrderBy {
        TRACK, ARTIST, ALBUM
    }

    private final SettingsFacade settingsFacade;
    private final ComparatorsFacade comparatorsFacade;

    public JpsonicComparators(SettingsFacade settingsFacade, ComparatorsFacade comparatorsFacade) {
        super();
        this.settingsFacade = settingsFacade;
        this.comparatorsFacade = comparatorsFacade;
    }

    /**
     * Returns a comparator that sorts in dictionary order.
     *
     * @return Comparator
     */
    public Comparator<Album> albumOrderByAlpha() {
        return new Comparator<>() {
            private final Collator collator = comparatorsFacade.createCollator();

            @Override
            public int compare(Album o1, Album o2) {
                return collator
                    .compare(Objects.toString(o1.getNameReading(), EMPTY),
                            Objects.toString(o2.getNameReading(), EMPTY));
            }
        };
    }

    /**
     * Returns a comparator that sorts in dictionary order.
     *
     * @return Comparator
     */
    public Comparator<Artist> artistOrderByAlpha() {
        return new Comparator<>() {
            private final Collator collator = comparatorsFacade.createCollator();

            @Override
            public int compare(Artist o1, Artist o2) {
                return collator
                    .compare(Objects.toString(o1.getReading(), EMPTY),
                            Objects.toString(o2.getReading(), EMPTY));
            }
        };
    }

    private boolean isSortAlbumsByYear(MediaFile parent) {
        return settingsFacade.get(SKeys.general.sort.albumsByYear)
                && (isEmpty(parent) || comparatorsFacade.isSortAlbumsByYear(parent.getArtist()));
    }

    /**
     * Returns the comparator that changes the sorting rules by hierarchy. Mainly
     * used when expanding files. The result is affected by the global settings
     * related to sorting.
     *
     * @param parent The common parent of the list to sort. Null for
     *               hierarchy-independent or top-level sorting.
     */
    public Comparator<MediaFile> mediaFileOrder(@Nullable MediaFile parent) {
        return new MediaFileComparator(isSortAlbumsByYear(parent),
                comparatorsFacade.createCollator());
    }

    /**
     * Returns a comparator for sorting MediaFiles by specifying a field regardless
     * of MediaType.
     */

    @SuppressWarnings("PMD.ExhaustiveSwitchHasDefault")
    public Comparator<MediaFile> mediaFileOrderBy(@NonNull OrderBy orderBy) {
        return (a, b) -> {
            switch (orderBy) {
            case TRACK:
                Integer trackA = a.getTrackNumber();
                Integer trackB = b.getTrackNumber();
                if (trackA == null) {
                    trackA = 0;
                }
                if (trackB == null) {
                    trackB = 0;
                }
                return trackA.compareTo(trackB);
            case ARTIST:
                return comparatorsFacade
                    .createCollator()
                    .compare(a.getArtistReading(), b.getArtistReading());
            case ALBUM:
                return comparatorsFacade
                    .createCollator()
                    .compare(a.getAlbumReading(), b.getAlbumReading());
            default:
                return 0;
            }
        };
    }

    public Comparator<MediaFile> songsDefault() {
        final LazyPathComparator comparator = new LazyPathComparator(
                comparatorsFacade::createCollator);
        return (a, b) -> {
            Integer trackA = a.getTrackNumber();
            Integer trackB = b.getTrackNumber();
            if (trackA == null) {
                trackA = 0;
            }
            if (trackB == null) {
                trackB = 0;
            }
            int compare = trackA.compareTo(trackB);
            if (compare != 0) {
                return compare;
            }
            return comparator.compare(a, b);
        };
    }

    /**
     * Returns a comparator for dictionary order sorting MediaFiles with different
     * MediaTypes. Ignores some of the global settings related to sorting, and sorts
     * naturally based on Type and name. It is mainly used for order index during
     * scanning.
     *
     * @return Comparator
     */
    public MediaFileComparator mediaFileOrderByAlpha() {
        return new MediaFileComparator(false, comparatorsFacade.createCollator());
    }

    static class LazyPathComparator implements Comparator<MediaFile> {

        private final Supplier<Collator> supplier;
        private Collator collator;

        LazyPathComparator(Supplier<Collator> supplier) {
            this.supplier = supplier;
        }

        @Override
        public int compare(MediaFile o1, MediaFile o2) {
            if (collator == null) {
                collator = supplier.get();
            }
            return collator.compare(o1.getPathString(), o2.getPathString());
        }
    }
}

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
 * (C) 2023 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.comparator;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.Collator;
import java.util.Locale;

import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class MediaFileComparatorTest {

    private MediaFileComparator alphabetical;
    private MediaFileComparator sortAlbumsByYear;

    @BeforeEach
    void setup() {
        Collator c = Collator.getInstance(Locale.US);
        alphabetical = new MediaFileComparator(false, c);
        sortAlbumsByYear = new MediaFileComparator(true, c);
    }

    @Nested
    class CompareTest {

        @Test
        void testCompareDirectoryAndFile() {
            MediaFile file1 = new MediaFile();
            file1.setPathString("path1");
            file1.setMediaType(MediaType.DIRECTORY);
            MediaFile file2 = new MediaFile();
            file2.setPathString("path2");
            file2.setMediaType(MediaType.MUSIC);

            assertTrue(file1.isDirectory());
            assertTrue(file2.isFile());
            assertEquals(-1, alphabetical.compare(file1, file2));
            assertEquals(1, alphabetical.compare(file2, file1));
            assertEquals(0, alphabetical.compare(file1, file1));
            assertEquals(0, alphabetical.compare(file2, file2));
        }

        @Test
        void testCompareAlbumAndNotAlbum() {
            MediaFile file1 = new MediaFile();
            file1.setPathString("path1");
            file1.setMediaType(MediaType.DIRECTORY);
            MediaFile file2 = new MediaFile();
            file2.setPathString("path2");
            file2.setMediaType(MediaType.ALBUM);

            assertEquals(-1, alphabetical.compare(file1, file2));
            assertEquals(1, alphabetical.compare(file2, file1));
            assertEquals(0, alphabetical.compare(file1, file1));
            assertEquals(0, alphabetical.compare(file2, file2));
        }

        @Test
        void testCompareAlbumsByYear() {
            MediaFile file1 = new MediaFile();
            file1.setPathString("path1");
            file1.setYear(1900);
            MediaFile file2 = new MediaFile();
            file2.setPathString("path2");
            file2.setYear(2000);

            file1.setMediaType(MediaType.ALBUM);
            file2.setMediaType(MediaType.ALBUM);
            assertEquals(-1, sortAlbumsByYear.compare(file1, file2));
            assertEquals(1, sortAlbumsByYear.compare(file2, file1));
            assertEquals(0, sortAlbumsByYear.compare(file1, file1));
            assertEquals(0, sortAlbumsByYear.compare(file2, file2));

            file1.setMediaType(MediaType.ALBUM);
            file2.setMediaType(MediaType.DIRECTORY);
            assertEquals(1, sortAlbumsByYear.compare(file1, file2));
            assertEquals(-1, sortAlbumsByYear.compare(file2, file1));
            assertEquals(0, sortAlbumsByYear.compare(file1, file1));
            assertEquals(0, sortAlbumsByYear.compare(file2, file2));
        }

        @Nested
        class CompareDirectoryTest {

            @Test
            void testCompareDirectory() {
                MediaFile file1 = new MediaFile();
                file1.setPathString("path1");
                file1.setMediaType(MediaType.DIRECTORY);
                MediaFile file2 = new MediaFile();
                file2.setPathString("path2");
                file2.setMediaType(MediaType.DIRECTORY);

                assertEquals(-1, alphabetical.compare(file1, file2));
                assertEquals(1, alphabetical.compare(file2, file1));
                assertEquals(0, sortAlbumsByYear.compare(file1, file1));
                assertEquals(0, sortAlbumsByYear.compare(file2, file2));
            }

            @Test
            void testCompareDirectoryAndAlbum() {
                MediaFile file1 = new MediaFile();
                file1.setPathString("path1");
                file1.setMediaType(MediaType.DIRECTORY);
                MediaFile file2 = new MediaFile();
                file2.setPathString("path2");
                file2.setMediaType(MediaType.ALBUM);

                assertEquals(-1, alphabetical.compare(file1, file2));
                assertEquals(1, alphabetical.compare(file2, file1));
                assertEquals(0, sortAlbumsByYear.compare(file1, file1));
                assertEquals(0, sortAlbumsByYear.compare(file2, file2));
            }
        }

        @Test
        void testCompareDiscAndTrackNumber() {
            MediaFile file1 = new MediaFile();
            file1.setPathString("path1");
            file1.setMediaType(MediaType.MUSIC);
            file1.setTrackNumber(1);
            MediaFile file2 = new MediaFile();
            file2.setPathString("path2");
            file2.setMediaType(MediaType.MUSIC);
            file2.setTrackNumber(2);

            assertEquals(-1, alphabetical.compare(file1, file2));
            assertEquals(1, alphabetical.compare(file2, file1));
            assertEquals(0, sortAlbumsByYear.compare(file1, file1));
            assertEquals(0, sortAlbumsByYear.compare(file2, file2));
        }

        @Test
        void testComparePath() {
            MediaFile file1 = new MediaFile();
            file1.setPathString("path1");
            file1.setMediaType(MediaType.MUSIC);
            MediaFile file2 = new MediaFile();
            file2.setPathString("path2");
            file2.setMediaType(MediaType.MUSIC);

            assertEquals(-1, alphabetical.compare(file1, file2));
            assertEquals(1, alphabetical.compare(file2, file1));
            assertEquals(0, sortAlbumsByYear.compare(file1, file1));
            assertEquals(0, sortAlbumsByYear.compare(file2, file2));
        }

        @Test
        void testComparePathForDirectory() {
            MediaFile file1 = new MediaFile();
            file1.setPathString("path1");
            file1.setMediaType(MediaType.DIRECTORY);
            file1.setArtistReading("reading");
            MediaFile file2 = new MediaFile();
            file2.setPathString("path2");
            file2.setMediaType(MediaType.DIRECTORY);
            file2.setArtistReading("reading");

            assertEquals(-1, alphabetical.compare(file1, file2));
            assertEquals(1, alphabetical.compare(file2, file1));
            assertEquals(0, sortAlbumsByYear.compare(file1, file1));
            assertEquals(0, sortAlbumsByYear.compare(file2, file2));

            file1.setArtistReading("reading1");
            file2.setArtistReading("reading2");

            assertEquals(-1, alphabetical.compare(file1, file2));
            assertEquals(1, alphabetical.compare(file2, file1));
            assertEquals(0, sortAlbumsByYear.compare(file1, file1));
            assertEquals(0, sortAlbumsByYear.compare(file2, file2));
        }
    }

    @Test
    void testCompareDirectoryAndFile() {
        MediaFile file1 = new MediaFile();
        file1.setPathString("path1");
        file1.setMediaType(MediaType.DIRECTORY);
        MediaFile file2 = new MediaFile();
        file2.setPathString("path2");
        file2.setMediaType(MediaType.MUSIC);

        assertTrue(file1.isDirectory());
        assertTrue(file2.isFile());
        assertEquals(-1, alphabetical.compareDirectoryAndFile(file1, file2));
        assertEquals(1, alphabetical.compareDirectoryAndFile(file2, file1));
        assertEquals(0, alphabetical.compareDirectoryAndFile(file1, file1));
        assertEquals(0, alphabetical.compareDirectoryAndFile(file2, file2));
    }

    @Test
    void testCompareAlbumAndNotAlbum() {
        MediaFile file1 = new MediaFile();
        file1.setPathString("path1");
        file1.setMediaType(MediaType.DIRECTORY);
        MediaFile file2 = new MediaFile();
        file2.setPathString("path2");
        file2.setMediaType(MediaType.ALBUM);

        assertEquals(-1, alphabetical.compareAlbumAndNotAlbum(file1, file2));
        assertEquals(1, alphabetical.compareAlbumAndNotAlbum(file2, file1));
        assertEquals(0, alphabetical.compareAlbumAndNotAlbum(file1, file1));
        assertEquals(0, alphabetical.compareAlbumAndNotAlbum(file2, file2));
    }

    @Nested
    class CompareDirectoryTest {

        @Test
        void testCompareForAlbumReading() {
            MediaFile file1 = new MediaFile();
            file1.setPathString("path1");
            MediaFile file2 = new MediaFile();
            file2.setPathString("path2");
            assertEquals("path1", file1.getName());
            assertEquals("path2", file2.getName());

            file1.setMediaType(MediaType.ALBUM);
            assertEquals(0, alphabetical.compareDirectory(file1, file2));
            assertEquals(0, alphabetical.compareDirectory(file2, file1));

            file2.setMediaType(MediaType.ALBUM);
            assertEquals(0, alphabetical.compareDirectory(file1, file2));
            assertEquals(0, alphabetical.compareDirectory(file2, file1));

            file1.setAlbumReading("albumReading1");
            assertEquals(0, alphabetical.compareDirectory(file1, file2));
            assertEquals(0, alphabetical.compareDirectory(file2, file1));

            file2.setAlbumReading("albumReading2");
            assertEquals(-1, alphabetical.compareDirectory(file1, file2));
            assertEquals(1, alphabetical.compareDirectory(file2, file1));
            assertEquals(0, alphabetical.compareDirectory(file1, file1));
            assertEquals(0, alphabetical.compareDirectory(file2, file2));
        }

        @Test
        void testCompareForArtistReading() {
            MediaFile file1 = new MediaFile();
            file1.setPathString("path1");
            MediaFile file2 = new MediaFile();
            file2.setPathString("path2");
            assertEquals("path1", file1.getName());
            assertEquals("path2", file2.getName());

            file1.setMediaType(MediaType.DIRECTORY);
            assertEquals(0, alphabetical.compareDirectory(file1, file2));
            assertEquals(0, alphabetical.compareDirectory(file2, file1));

            file2.setMediaType(MediaType.DIRECTORY);
            assertEquals(0, alphabetical.compareDirectory(file1, file2));
            assertEquals(0, alphabetical.compareDirectory(file2, file1));

            file1.setArtistReading("artistReading1");
            assertEquals(1, alphabetical.compareDirectory(file1, file2));
            assertEquals(-1, alphabetical.compareDirectory(file2, file1));

            file2.setArtistReading("artistReading2");
            assertEquals(-1, alphabetical.compareDirectory(file1, file2));
            assertEquals(1, alphabetical.compareDirectory(file2, file1));
            assertEquals(0, alphabetical.compareDirectory(file1, file1));
        }
    }

    @Test
    void testNullSafeCompare() {

        /*
         * This affects the specification of precedence when there is a mix of input and
         * non-input data for song numbers and year.
         */
        boolean nullIsSmaller = false;
        assertEquals(0, alphabetical.nullSafeCompare(null, null, nullIsSmaller));
        assertEquals(-1, alphabetical.nullSafeCompare("1", null, nullIsSmaller));
        assertEquals(1, alphabetical.nullSafeCompare(null, "1", nullIsSmaller));
        assertEquals(0, alphabetical.nullSafeCompare("1", "1", nullIsSmaller));
        assertEquals(-1, alphabetical.nullSafeCompare("1", "2", nullIsSmaller));
        assertEquals(1, alphabetical.nullSafeCompare("2", "1", nullIsSmaller));

        nullIsSmaller = true; // This is not currently used
        assertEquals(0, alphabetical.nullSafeCompare(null, null, nullIsSmaller));
        assertEquals(1, alphabetical.nullSafeCompare("1", null, nullIsSmaller)); // reverse
        assertEquals(-1, alphabetical.nullSafeCompare(null, "1", nullIsSmaller)); // reverse
        assertEquals(0, alphabetical.nullSafeCompare("1", "1", nullIsSmaller));
        assertEquals(-1, alphabetical.nullSafeCompare("1", "2", nullIsSmaller));
        assertEquals(1, alphabetical.nullSafeCompare("2", "1", nullIsSmaller));
    }

    @Test
    void testGetSortableDiscAndTrackNumber() {
        MediaFile file = new MediaFile();
        assertNull(alphabetical.getSortableDiscAndTrackNumber(file));
        file.setTrackNumber(2);
        assertEquals(1002, alphabetical.getSortableDiscAndTrackNumber(file));
        file.setDiscNumber(5);
        assertEquals(5002, alphabetical.getSortableDiscAndTrackNumber(file));
    }
}

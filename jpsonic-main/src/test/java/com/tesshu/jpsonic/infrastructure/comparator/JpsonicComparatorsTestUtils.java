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

import static java.util.Collections.reverse;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.tesshu.jpsonic.domain.contract.StrictReadingMetaAnalysis;
import com.tesshu.jpsonic.domain.model.Genre;
import com.tesshu.jpsonic.infrastructure.language.MetadataReadingProcessor;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.Artist;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.Playlist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("PMD.AvoidDuplicateLiterals") // In the testing class, it may be less readable.
public class JpsonicComparatorsTestUtils {

    @Autowired
    private MetadataReadingProcessor proc;
    private TestMediaFileAnalysis mediaFileAnalysis;

    /*
     * Dictionary order that Japanese feel natural.
     */
    public static final List<String> JPSONIC_NATURAL_LIST = Collections
        .unmodifiableList(Arrays
            .asList("10", "20", "50", "60", "70", "98", "99", "abcde", "abcいうえおあ", "abc亜伊鵜絵尾",
                    "ＢＣＤＥＡ", "ĆḊÉÁḂ", "DEABC", "the eabcd", "episode 1", "episode 2", "episode 19",
                    "亜伊鵜絵尾", "αβγ", "いうえおあ", "ゥェォァィ", "ｴｵｱｲｳ", "ｪｫｧｨｩ", "ぉぁぃぅぇ", "オアイウエ", "春夏秋冬",
                    "貼られる", "パラレル", "馬力", "張り切る", "はるなつあきふゆ", "♂くんつ"));

    /*
     * Expected sequence number.
     */
    private static final List<String> ALPHA_NUM_LIST = Collections
        .unmodifiableList(Arrays
            .asList("09X Radonius", "10X Radonius", "20X Radonius", "20X Radonius Prime",
                    "30X Radonius", "40X Radonius", "200X Radonius", "1000X Radonius Maximus",
                    "Allegia 6R Clasteron", "Allegia 50B Clasteron", "Allegia 50 Clasteron",
                    "Allegia 51 Clasteron", "Allegia 500 Clasteron", "Alpha 2", "Alpha 2A",
                    "Alpha 2A-900", "Alpha 2A-8000", "Alpha 100", "Alpha 200", "Callisto Morphamax",
                    "Callisto Morphamax 500", "Callisto Morphamax 600", "Callisto Morphamax 700",
                    "Callisto Morphamax 5000", "Callisto Morphamax 6000 SE",
                    "Callisto Morphamax 6000 SE2", "Callisto Morphamax 7000", "Xiph Xlater 5",
                    "Xiph Xlater 40", "Xiph Xlater 50", "Xiph Xlater 58", "Xiph Xlater 300",
                    "Xiph Xlater 500", "Xiph Xlater 2000", "Xiph Xlater 5000",
                    "Xiph Xlater 10000"));

    private final Function<String, MediaFile> toMediaArtist = (name) -> {
        MediaFile file = new MediaFile();
        file.setArtist(name);
        file.setAlbumArtist(name);
        if ("abcde".equals(name)) {
            file.setArtistSort("エービーシーディーイー");
            file.setAlbumArtistSort("エービーシーディーイー");
        } else if ("ＢＣＤＥＡ".equals(name)) {
            file.setArtistSort("ビーシーディーイーエー");
            file.setAlbumArtistSort("ビーシーディーイーエー");
        } else if ("abcいうえおあ".equals(name)) {
            file.setArtistSort("abcあいうえお");
            file.setAlbumArtistSort("abcあいうえお");
        } else if ("abc亜伊鵜絵尾".equals(name)) {
            file.setArtistSort("abcいうえおあ");
            file.setAlbumArtistSort("abcいうえおあ");
        }
        file.setTitle(name);
        file.setPathString(name);
        file.setMediaType(MediaType.DIRECTORY);

        if (mediaFileAnalysis == null) {
            mediaFileAnalysis = proc::analyzeStrictReadingMeta;
        }
        mediaFileAnalysis.analyze(file);
        return file;
    };

    private final BiFunction<String, Integer, Genre> toGenre = (name, count) -> new Genre(name,
            count, count);

    private final Function<String, Playlist> toPlaylist = (name) -> {
        Playlist playlist = new Playlist();
        playlist.setName(name);
        return playlist;
    };

    private final Function<String, Artist> toArtist = (name) -> {
        MediaFile file = toMediaArtist.apply(name);
        Artist artist = new Artist();
        artist.setOrder(-1);
        artist.setName(file.getAlbumArtist());
        artist.setReading(file.getAlbumArtistReading());
        artist.setSort(file.getAlbumArtistSort());
        return artist;
    };

    private final BiFunction<String, Integer, MediaFile> toMediaSong = (name, trackNumber) -> {

        MediaFile file = new MediaFile();
        file.setArtist(name);
        file.setAlbumName(name);
        file.setTitle(name);
        if ("abcde".equals(name)) {
            file.setArtistSort("エービーシーディーイー");
            file.setAlbumSort("エービーシーディーイー");
            file.setTitleSort("エービーシーディーイー");
        } else if ("ＢＣＤＥＡ".equals(name)) {
            file.setArtistSort("ビーシーディーイーエー");
            file.setAlbumSort("ビーシーディーイーエー");
            file.setTitleSort("ビーシーディーイーエー");
        } else if ("abcいうえおあ".equals(name)) {
            file.setArtistSort("abcあいうえお");
            file.setAlbumSort("abcあいうえお");
            file.setTitleSort("abcあいうえお");
        } else if ("abc亜伊鵜絵尾".equals(name)) {
            file.setArtistSort("abcいうえおあ");
            file.setAlbumSort("abcいうえおあ");
            file.setTitleSort("abcいうえおあ");
        }
        file.setTrackNumber(trackNumber);
        file.setPathString(name);
        file.setMediaType(MediaType.MUSIC);

        if (mediaFileAnalysis == null) {
            mediaFileAnalysis = proc::analyzeStrictReadingMeta;
        }
        mediaFileAnalysis.analyze(file);
        return file;
    };

    private final Function<String, MediaFile> toMediaAlbum = (name) -> {

        MediaFile file = new MediaFile();

        file.setAlbumName(name);
        if ("abcde".equals(name)) {
            file.setAlbumSort("エービーシーディーイー");
        } else if ("ＢＣＤＥＡ".equals(name)) {
            file.setAlbumSort("ビーシーディーイーエー");
        } else if ("abcいうえおあ".equals(name)) {
            file.setAlbumSort("abcあいうえお");
        } else if ("abc亜伊鵜絵尾".equals(name)) {
            file.setAlbumSort("abcいうえおあ");
        }

        if ("98".equals(name)) {
            file.setYear(1998);
        } else if ("99".equals(name)) {
            file.setYear(1999);
        } else if ("10".equals(name)) {
            file.setYear(2010);
        } else {
            file.setAlbumName(name);
        }

        file.setTitle(name);
        file.setPathString(name);
        file.setMediaType(MediaType.ALBUM);

        if (mediaFileAnalysis == null) {
            mediaFileAnalysis = proc::analyzeStrictReadingMeta;
        }
        mediaFileAnalysis.analyze(file);

        return file;
    };

    private final Function<String, Album> toAlbum = (name) -> {

        MediaFile file = toMediaAlbum.apply(name);
        Album album = new Album();
        album.setOrder(-1);

        String artist;
        String reading;
        String sort;
        if (isEmpty(file.getAlbumArtist())) {
            artist = file.getArtist();
            reading = file.getArtistReading();
            sort = file.getArtistSort();
        } else {
            artist = file.getAlbumArtist();
            reading = file.getAlbumArtistReading();
            sort = file.getAlbumArtistSort();
        }

        album.setName(file.getAlbumName());
        album.setNameReading(file.getAlbumReading());
        album.setNameSort(file.getAlbumSort());
        album.setArtist(artist);
        album.setArtistReading(reading);
        album.setArtistSort(sort);
        album.setYear(file.getYear());

        return album;
    };

    public static void assertAlbumOrder(List<Album> albums, Integer... ignores) {
        assertEquals(JPSONIC_NATURAL_LIST.size(), albums.size());
        for (int i = 0; i < JPSONIC_NATURAL_LIST.size(); i++) {
            if (0 > Arrays.binarySearch(ignores, i)) {
                assertEquals(JPSONIC_NATURAL_LIST.get(i), albums.get(i).getName(),
                        "(" + i + ") -> ");
            }
        }
    }

    public static boolean assertAlphanumArtistOrder(List<Artist> artists, Integer... ignores) {
        assertEquals(ALPHA_NUM_LIST.size(), artists.size());
        for (int i = 0; i < ALPHA_NUM_LIST.size(); i++) {
            if (0 > Arrays.binarySearch(ignores, i)) {
                assertEquals(ALPHA_NUM_LIST.get(i), artists.get(i).getName(), "(" + i + ") -> ");
            }
        }
        return true;
    }

    public static void assertArtistOrder(List<Artist> artists, Integer... ignores) {
        assertEquals(JPSONIC_NATURAL_LIST.size(), artists.size());
        for (int i = 0; i < JPSONIC_NATURAL_LIST.size(); i++) {
            if (0 > Arrays.binarySearch(ignores, i)) {
                assertEquals(JPSONIC_NATURAL_LIST.get(i), artists.get(i).getName(),
                        "(" + i + ") -> ");
            }
        }
    }

    public static void assertGenreOrder(List<Genre> genres, Integer... ignores) {
        assertEquals(JPSONIC_NATURAL_LIST.size(), genres.size());
        for (int i = 0; i < JPSONIC_NATURAL_LIST.size(); i++) {
            if (0 > Arrays.binarySearch(ignores, i)) {
                assertEquals(JPSONIC_NATURAL_LIST.get(i), genres.get(i).name(), "(" + i + ") -> ");
            }
        }
    }

    public static void assertMediafileOrder(List<MediaFile> files, Integer... ignores) {
        assertEquals(JPSONIC_NATURAL_LIST.size(), files.size());
        for (int i = 0; i < JPSONIC_NATURAL_LIST.size(); i++) {
            if (0 > Arrays.binarySearch(ignores, i)) {
                assertEquals(JPSONIC_NATURAL_LIST.get(i), files.get(i).getName(),
                        "(" + i + ") -> ");
            }
        }
    }

    public static void assertPlaylistOrder(List<Playlist> playlists, Integer... ignores) {
        assertEquals(JPSONIC_NATURAL_LIST.size(), playlists.size());
        for (int i = 0; i < JPSONIC_NATURAL_LIST.size(); i++) {
            if (0 > Arrays.binarySearch(ignores, i)) {
                assertEquals(JPSONIC_NATURAL_LIST.get(i), playlists.get(i).getName(),
                        "(" + i + ") -> ");
            }
        }
    }

    public static boolean validateNaturalList(List<String> l, Integer... ignores) {
        assertEquals(JPSONIC_NATURAL_LIST.size(), l.size());
        for (int i = 0; i < JPSONIC_NATURAL_LIST.size(); i++) {
            if (0 > Arrays.binarySearch(ignores, i)) {
                assertEquals(JPSONIC_NATURAL_LIST.get(i), l.get(i), "(" + i + ") -> ");
            }
        }
        return true;
    }

    public List<Album> createReversedAlbums() {
        List<Album> albums = JPSONIC_NATURAL_LIST.stream().map(toAlbum).collect(toList());
        reverse(albums);
        return albums;
    }

    public List<Artist> createReversedAlphanum() {
        List<Artist> artists = ALPHA_NUM_LIST.stream().map(toArtist).collect(toList());
        reverse(artists);
        return artists;
    }

    public List<Artist> createReversedArtists() {
        List<Artist> artists = JPSONIC_NATURAL_LIST.stream().map(toArtist).collect(toList());
        reverse(artists);
        return artists;
    }

    public List<Genre> createReversedGenres() {
        List<Genre> genres = new ArrayList<>();
        for (int i = 0; i < JPSONIC_NATURAL_LIST.size(); i++) {
            genres.add(toGenre.apply(JPSONIC_NATURAL_LIST.get(i), JPSONIC_NATURAL_LIST.size() - i));
        }
        reverse(genres);
        return genres;
    }

    public List<MediaFile> createReversedMediaSongs() {
        List<MediaFile> songs = new ArrayList<>();
        for (int i = 0; i < JPSONIC_NATURAL_LIST.size(); i++) {
            songs
                .add(toMediaSong
                    .apply(JPSONIC_NATURAL_LIST.get(i), JPSONIC_NATURAL_LIST.size() - i));
        }
        reverse(songs);
        return songs;
    }

    public List<MediaFile> createReversedMediAlbums() {
        List<MediaFile> albums = JPSONIC_NATURAL_LIST.stream().map(toMediaAlbum).collect(toList());
        reverse(albums);
        return albums;
    }

    public List<MediaFile> createReversedMediArtists() {
        List<MediaFile> artists = JPSONIC_NATURAL_LIST
            .stream()
            .map(toMediaArtist)
            .collect(toList());
        reverse(artists);
        return artists;
    }

    public List<Playlist> createReversedPlaylists() {
        List<Playlist> playlists = JPSONIC_NATURAL_LIST.stream().map(toPlaylist).collect(toList());
        reverse(playlists);
        return playlists;
    }

    public MediaFile createVariousMedifile() {
        MediaFile file = new MediaFile();
        file.setArtist("various");
        return file;
    }

    @FunctionalInterface
    interface TestMediaFileAnalysis extends StrictReadingMetaAnalysis {
        // spotless:off
        default void analyze(MediaFile m) {
            analyzeStrictReadingMeta(
                    m.getArtist(),
                    m.getArtistSort(),
                    m::setArtistSort,
                    m::setArtistReading);
            analyzeStrictReadingMeta(
                    m.getAlbumArtist(),
                    m.getAlbumArtistSort(),
                    m::setAlbumArtistSort,
                    m::setAlbumArtistReading);
            analyzeStrictReadingMeta(
                    m.getAlbumName(),
                    m.getAlbumSort(),
                    m::setAlbumSort,
                    m::setAlbumReading);
        }
        // spotless:on
    }
}

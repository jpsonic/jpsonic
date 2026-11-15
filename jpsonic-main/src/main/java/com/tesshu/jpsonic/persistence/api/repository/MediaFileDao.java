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

package com.tesshu.jpsonic.persistence.api.repository;

import static com.tesshu.jpsonic.persistence.base.DaoUtils.prefix;
import static com.tesshu.jpsonic.persistence.base.DaoUtils.questionMarks;
import static com.tesshu.jpsonic.util.PlayerUtils.FAR_FUTURE;
import static com.tesshu.jpsonic.util.PlayerUtils.FAR_PAST;
import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import com.tesshu.jpsonic.domain.model.Genre;
import com.tesshu.jpsonic.domain.model.IndexWithCount;
import com.tesshu.jpsonic.domain.model.MediaFile.Type;
import com.tesshu.jpsonic.domain.model.MusicIndex;
import com.tesshu.jpsonic.domain.model.Playlist;
import com.tesshu.jpsonic.domain.policy.RuntimeOrderPolicy;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.base.DaoUtils;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import com.tesshu.jpsonic.persistence.dialect.DialectMediaFileDao;
import com.tesshu.jpsonic.persistence.param.ShuffleSelectionParam;
import com.tesshu.jpsonic.persistence.result.ArtistSortCandidate;
import com.tesshu.jpsonic.persistence.result.ArtistSortCandidate.TargetField;
import com.tesshu.jpsonic.persistence.result.DuplicateSort;
import com.tesshu.jpsonic.persistence.result.SortCandidate;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides database services for media files.
 *
 * @author Sindre Mehus
 */
@SuppressWarnings({ "checkstyle:OverloadMethodsDeclarationOrder", "PMD.AvoidDuplicateLiterals",
        "PMD.TooManyStaticImports", "PMD.FieldDeclarationsShouldBeAtStartOfClass" })
@Repository
public class MediaFileDao {

    public static final int VERSION = 15;

    private static final String INSERT_COLUMNS = DaoUtils.getInsertColumns(MediaFile.class);
    private static final String QUERY_COLUMNS = DaoUtils.getQueryColumns(MediaFile.class);

    private final TemplateWrapper template;
    private final RowMapper<MediaFile> rowMapper = DaoUtils.createRowMapper(MediaFile.class);
    private final RowMapper<com.tesshu.jpsonic.domain.model.MediaFile> domainRowMapper = DaoUtils
        .createRowMapper(com.tesshu.jpsonic.domain.model.MediaFile.class);
    private final RowMapper<Genre> genreRowMapper = (rs, rowNum) -> new Genre(rs.getString(1),
            rs.getInt(2), rs.getInt(3));
    private final RowMapper<IndexWithCount> indexWithCountMapper = (ResultSet rs,
            int num) -> new IndexWithCount(rs.getString(1), rs.getInt(2));

    private final DialectMediaFileDao dialect;

    public enum ChildOrder {
        BY_ALPHA, BY_YEAR, BY_TRACK
    }

    public MediaFileDao(TemplateWrapper templateWrapper, DialectMediaFileDao dialect) {
        template = templateWrapper;
        this.dialect = dialect;
    }

    public @Nullable MediaFile getMediaFile(String path) {
        return template.queryOne("select " + QUERY_COLUMNS + """
                from media_file
                where path=?
                """, rowMapper, path);
    }

    public @Nullable MediaFile getMediaFile(@NonNull Path path) {
        return template
            .queryOne("select " + QUERY_COLUMNS + " from media_file where path=?", rowMapper,
                    path.toString());
    }

    public @Nullable MediaFile getMediaFile(int id) {
        return template.queryOne("select " + QUERY_COLUMNS + """
                from media_file
                where id=?
                """, rowMapper, id);
    }

    public List<MediaFile> getVideos(final int count, final int offset,
            final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("type", MediaFile.MediaType.VIDEO.name(), "folders",
                    MusicFolder.toPathList(musicFolders), "count", count, "offset", offset);
        return template.namedQuery("select " + QUERY_COLUMNS + """
                from media_file
                where type = :type and present and folder in (:folders)
                order by title
                limit :count offset :offset
                """, rowMapper, args);
    }

    public int getChildSizeOf(List<MusicFolder> folders, MediaType... excludes) {
        if (folders.isEmpty()) {
            return 0;
        }
        Map<String, Object> args = Map
            .of("folders", MusicFolder.toPathList(folders), "excludes",
                    Stream.of(excludes).map(MediaType::name).toList());
        String typeFilter = excludes.length == 0 ? "" : "and type not in(:excludes)";
        return template.namedQueryForInt("""
                select count(*)
                from media_file
                where parent_path in(:folders) and present %s
                """.formatted(typeFilter), 0, args);
    }

    public List<Genre> getGenreCounts() {
        Map<String, Object> args = Map
            .of("album", MediaType.ALBUM.name(), "music", MediaType.MUSIC.name());
        return template.namedQuery("""
                select song_genre.name, song_count, album_count
                from
                    (select genres.name, count(*) as song_count
                        from
                            (select distinct genre name
                                from media_file
                                where present and type = :album or type = :music) as genres
                        left join media_file as songs
                        on songs.present and genres.name = songs.genre and songs.type = :music
                        group by genres.name) as song_genre
                join
                    (select genres.name, count(*) as album_count
                        from
                            (select distinct genre name
                                from media_file
                                where present and type = :album or type = :music) as genres
                        left join media_file as albums
                        on albums.present and genres.name = albums.genre and albums.type = :album
                        group by genres.name) as album_genre
                on song_genre.name = album_genre.name
                order by name
                """, genreRowMapper, args);
    }

    public List<MediaFile> getChildrenOf(String path) {
        return template.query("select " + QUERY_COLUMNS + """
                from media_file
                where parent_path=? and present
                """, rowMapper, path);
    }

    public List<MediaFile> getChildrenOf(String path, long offset, long count,
            ChildOrder childOrder, MediaType... excludes) {
        Map<String, Object> args = Map
            .of("directory", MediaFile.MediaType.DIRECTORY.name(), "album",
                    MediaFile.MediaType.ALBUM.name(), "music", MediaFile.MediaType.MUSIC.name(),
                    "audiobook", MediaFile.MediaType.AUDIOBOOK.name(), "video",
                    MediaFile.MediaType.VIDEO.name(), "path", path, "offset", offset, "count",
                    count, "excludes", Stream.of(excludes).map(MediaType::name).toList());
        String typeFilter = excludes.length == 0 ? "" : "and type not in(:excludes)";
        String order = switch (childOrder) {
        case BY_ALPHA -> "media_file_order";
        case BY_YEAR -> "year is null, year, media_file_order";
        case BY_TRACK ->
            "disc_number is null, disc_number, track_number is null, track_number, media_file_order";
        };
        return template.namedQuery("select " + QUERY_COLUMNS + """
                        ,
                        case type
                            when :directory then 1
                            when :album then 2
                            when :music then 3
                            when :audiobook then 4
                            when :video then 5
                        end as type_order
                from media_file
                where parent_path=:path and present
                %s
                order by type_order, %s
                offset :offset limit :count
                """.formatted(typeFilter, order), rowMapper, args);
    }

    public List<MediaFile> getSongsForAlbum(final long offset, final long count, String albumArtist,
            String album) {
        return template
            .query("select " + QUERY_COLUMNS + """
                    from media_file
                    where album_artist=? and album=? and present and type in (?,?,?)
                    order by track_number
                    limit ? offset ?
                    """, rowMapper, albumArtist, album, MediaType.MUSIC.name(),
                    MediaType.AUDIOBOOK.name(), MediaType.PODCAST.name(), count, offset);
    }

    public List<String> getID3AlbumGenres(MediaFile mediaFile) {
        return template
            .queryForStrings("select distinct ordered.genre " + """
                    from (select genre from media_file
                            where album_artist=?
                                and album=?
                                and present
                                and genre is not null
                                and type in (?,?)
                            order by track_number) as ordered
                    """, mediaFile.getAlbumArtist(), mediaFile.getAlbumName(),
                    MediaType.MUSIC.name(), MediaType.AUDIOBOOK.name());
    }

    public List<MediaFile> getFilesInPlaylist(int playlistId, long offset, long count) {
        return template.query("select " + prefix(QUERY_COLUMNS, "media_file") + """
                from playlist_file, media_file
                where media_file.id = playlist_file.media_file_id
                        and playlist_file.playlist_id = ? and present
                order by playlist_file.id
                limit ? offset ?
                """, rowMapper, playlistId, count, offset);
    }

    public boolean exists(Path path) {
        return 0 < template.queryForInt("""
                select count(path)
                from media_file
                where path = ?
                """, 0, path.toString());
    }

    public boolean existsNonPresent() {
        return 0 < template.queryForInt("""
                select count(*)
                from media_file
                where not present
                """, 1);
    }

    public @Nullable MediaFile createMediaFile(MediaFile file) {
        int c = template
            .update("insert into media_file (" + INSERT_COLUMNS + ") values ("
                    + questionMarks(INSERT_COLUMNS) + ")", file.getPathString(), file.getFolder(),
                    file.getMediaType().name(), file.getFormat(), file.getTitle(),
                    file.getAlbumName(), file.getArtist(), file.getAlbumArtist(),
                    file.getDiscNumber(), file.getTrackNumber(), file.getYear(), file.getGenre(),
                    file.getBitRate(), file.isVariableBitRate(), file.getDurationSeconds(),
                    file.getFileSize(), file.getWidth(), file.getHeight(),
                    file.getCoverArtPathString(), file.getParentPathString(), file.getPlayCount(),
                    file.getLastPlayed(), file.getComment(), file.getCreated(), file.getChanged(),
                    file.getLastScanned(), file.getChildrenLastUpdated(), file.isPresent(), VERSION,
                    file.getMusicBrainzReleaseId(), file.getMusicBrainzRecordingId(),
                    file.getComposer(), file.getArtistSort(), file.getAlbumSort(),
                    file.getTitleSort(), file.getAlbumArtistSort(), file.getComposerSort(),
                    file.getArtistReading(), file.getAlbumReading(), file.getAlbumArtistReading(),
                    file.getArtistSortRaw(), file.getAlbumSortRaw(), file.getAlbumArtistSortRaw(),
                    file.getComposerSortRaw(), file.getOrder(), file.getMusicIndex());
        Integer id = template.queryForInt("""
                select id
                from media_file
                where path=?
                """, null, file.getPathString());
        if (c > 0 && id != null) {
            file.setId(id);
            return file;
        }
        return null;
    }

    public Optional<MediaFile> updateMediaFile(MediaFile file) {
        String sql = """
                update media_file
                set folder=?, type=?, format=?, title=?, album=?,
                        artist=?, album_artist=?, disc_number=?, track_number=?, year=?, genre=?,
                        bit_rate=?, variable_bit_rate=?, duration_seconds=?, file_size=?, width=?,
                        height=?, cover_art_path=?, parent_path=?, play_count=?, last_played=?,
                        comment=?, changed=?, last_scanned=?, children_last_updated=?, present=?,
                        version=?, mb_release_id=?, mb_recording_id=?,
                        composer=?, artist_sort=?, album_sort=?, title_sort=?,
                        album_artist_sort=?, composer_sort=?, artist_reading=?, album_reading=?,
                        album_artist_reading=?, artist_sort_raw=?, album_sort_raw=?,
                        album_artist_sort_raw=?, composer_sort_raw=?, media_file_order=?,
                        music_index=?
                where id=?
                """;
        int c = template
            .update(sql, file.getFolder(), file.getMediaType().name(), file.getFormat(),
                    file.getTitle(), file.getAlbumName(), file.getArtist(), file.getAlbumArtist(),
                    file.getDiscNumber(), file.getTrackNumber(), file.getYear(), file.getGenre(),
                    file.getBitRate(), file.isVariableBitRate(), file.getDurationSeconds(),
                    file.getFileSize(), file.getWidth(), file.getHeight(),
                    file.getCoverArtPathString(), file.getParentPathString(), file.getPlayCount(),
                    file.getLastPlayed(), file.getComment(), file.getChanged(),
                    file.getLastScanned(), file.getChildrenLastUpdated(), file.isPresent(), VERSION,
                    file.getMusicBrainzReleaseId(), file.getMusicBrainzRecordingId(),
                    file.getComposer(), file.getArtistSort(), file.getAlbumSort(),
                    file.getTitleSort(), file.getAlbumArtistSort(), file.getComposerSort(),
                    file.getArtistReading(), file.getAlbumReading(), file.getAlbumArtistReading(),
                    file.getArtistSortRaw(), file.getAlbumSortRaw(), file.getAlbumArtistSortRaw(),
                    file.getComposerSortRaw(), file.getOrder(), file.getMusicIndex(), file.getId());
        if (c > 0) {
            return Optional.of(file);
        }
        return Optional.empty();
    }

    public void updateChildrenLastUpdated(String pathString, Instant childrenLastUpdated) {
        template.update("""
                update media_file
                set children_last_updated = ?, present=?
                where path=?
                """, childrenLastUpdated, true, pathString);
    }

    public void updateChildrenLastUpdated(Album album, Instant childrenLastUpdated) {
        template
            .update("""
                    update media_file
                    set children_last_updated = ?, present=?
                    where album_artist = ? and album = ? and children_last_updated = ? and path <> ?
                    """, childrenLastUpdated, true, album.getArtist(), album.getName(), FAR_FUTURE,
                    album.getPath());
    }

    public void resetAlbumChildrenLastUpdated() {
        template
            .update("""
                    update media_file
                    set children_last_updated = ?
                    where type in (?, ?, ?) and present
                    """, FAR_FUTURE, MediaFile.MediaType.MUSIC.name(),
                    MediaFile.MediaType.AUDIOBOOK.name(), MediaFile.MediaType.VIDEO.name());
    }

    public int updateOrder(int id, int order) {
        return template.update("""
                update media_file
                set media_file_order = ?
                where id=?
                """, order, id);
    }

    public void updateCoverArtPath(String pathString, String coverArtPath) {
        template.update("""
                update media_file
                set cover_art_path = ?
                where path=?
                """, coverArtPath, pathString);
    }

    public void updatePlayCount(String pathString, Instant lastPlayed, int playCount) {
        template.update("""
                update media_file
                set last_played = ?, play_count = ?
                where path=?
                """, lastPlayed, playCount, pathString);
    }

    public void updateComment(String pathString, String comment) {
        template.update("""
                update media_file
                set comment = ?
                where path=?
                """, comment, pathString);
    }

    public int deleteMediaFile(int id) {
        return template.update("""
                update media_file
                set present=false, children_last_updated=?
                where id=?
                """, FAR_PAST, id);
    }

    public void updateGenres(List<Genre> genres) {
        template.update("delete from genre");
        for (Genre genre : genres) {
            template.update("""
                    insert into genre(name, song_count, album_count)
                    values(?, ?, ?)
                    """, genre.name(), genre.songCount(), genre.albumCount());
        }
    }

    public List<MediaFile> getArtistAll(final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("type", MediaType.DIRECTORY.name(), "folders",
                    MusicFolder.toPathList(musicFolders));
        return template.namedQuery("select " + QUERY_COLUMNS + """
                from media_file
                where type = :type and folder in (:folders) and present and artist is not null
                order by media_file_order
                """, rowMapper, args);
    }

    public @Nullable MediaFile getArtistByName(final String name,
            final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return null;
        }
        Map<String, Object> args = LegacyMap
            .of("type", MediaFile.MediaType.DIRECTORY.name(), "name", name, "folders",
                    MusicFolder.toPathList(musicFolders));
        return template.namedQueryOne("select " + QUERY_COLUMNS + """
                from media_file
                where type = :type and artist = :name and present and folder in (:folders)
                """, rowMapper, args);
    }

    public List<MediaFile> getMostFrequentlyPlayedAlbums(final int offset, final int count,
            final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("type", MediaFile.MediaType.ALBUM.name(), "folders",
                    MusicFolder.toPathList(musicFolders), "count", count, "offset", offset);

        return template.namedQuery("select " + QUERY_COLUMNS + """
                from media_file
                where type = :type and play_count > 0 and present and folder in (:folders)
                order by play_count desc
                limit :count offset :offset
                """, rowMapper, args);
    }

    public List<MediaFile> getMostRecentlyPlayedAlbums(final int offset, final int count,
            final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("type", MediaFile.MediaType.ALBUM.name(), "folders",
                    MusicFolder.toPathList(musicFolders), "count", count, "offset", offset);
        return template.namedQuery("select " + QUERY_COLUMNS + """
                from media_file
                where type = :type and last_played is not null
                        and present and folder in (:folders)
                order by last_played desc
                limit :count offset :offset
                """, rowMapper, args);
    }

    public List<MediaFile> getNewestAlbums(final int offset, final int count,
            final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("type", MediaFile.MediaType.ALBUM.name(), "folders",
                    MusicFolder.toPathList(musicFolders), "count", count, "offset", offset);

        return template.namedQuery("select " + QUERY_COLUMNS + """
                from media_file
                where type = :type and folder in (:folders) and present
                order by created desc
                limit :count offset :offset
                """, rowMapper, args);
    }

    public List<MediaFile> getAlphabeticalAlbums(final int offset, final int count,
            boolean byArtist, final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("type", MediaFile.MediaType.ALBUM.name(), "folders",
                    MusicFolder.toPathList(musicFolders), "count", count, "offset", offset);
        String namedQuery = "select " + QUERY_COLUMNS + """
                from media_file
                where type = :type and folder in (:folders) and present
                order by media_file_order
                limit :count offset :offset
                """;
        if (byArtist) {
            namedQuery = "select distinct " + prefix(QUERY_COLUMNS, "al") + """
                            , ar.media_file_order as ar_order, al.media_file_order as al_order
                    from media_file al
                    join media_file ar
                    on ar.path = al.parent_path
                    where al.type = :type and al.folder in (:folders) and al.present
                    order by ar_order, al_order
                    limit :count offset :offset
                    """;
        }

        return template.namedQuery(namedQuery, rowMapper, args);
    }

    public List<MediaFile> getAlbumsByYear(final int offset, final int count, final int fromYear,
            final int toYear, final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("type", MediaFile.MediaType.ALBUM.name(), "folders",
                    MusicFolder.toPathList(musicFolders), "fromYear", fromYear, "toYear", toYear,
                    "count", count, "offset", offset);

        if (fromYear <= toYear) {
            return template.namedQuery("select " + QUERY_COLUMNS + """
                    from media_file
                    where type = :type and folder in (:folders) and present
                            and year between :fromYear and :toYear
                    order by year, media_file_order
                    limit :count offset :offset
                    """, rowMapper, args);
        } else {
            return template.namedQuery("select " + QUERY_COLUMNS + """
                    from media_file
                    where type = :type and folder in (:folders) and present
                            and year between :toYear and :fromYear
                    order by year desc, media_file_order
                    limit :count offset :offset
                    """, rowMapper, args);
        }
    }

    public List<MediaFile> getAlbumsByGenre(final long offset, long count,
            final List<String> genres, final List<MusicFolder> musicFolders) {

        if (musicFolders.isEmpty() || genres.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("type", MediaType.ALBUM.name(), "genres", genres, "folders",
                    MusicFolder.toPathList(musicFolders), "count", count, "offset", offset);
        return template.namedQuery("select " + QUERY_COLUMNS + """
                from media_file
                where type = :type and folder in (:folders) and present and genre in (:genres)
                order by media_file_order
                limit :count offset :offset
                """, rowMapper, args);
    }

    public List<MediaFile> getUnparsedVideos(final int count,
            final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        return template.query("select " + QUERY_COLUMNS + """
                from media_file
                where type = ? and last_scanned = ?
                limit ?
                """, rowMapper, MediaFile.MediaType.VIDEO.name(), FAR_FUTURE, count);
    }

    public List<MediaFile> getChangedId3Artists(final int count, List<MusicFolder> folders,
            boolean withPodcast) {
        return dialect.getChangedId3Artists(count, folders, withPodcast);
    }

    public List<MediaFile> getUnregisteredId3Artists(final int count, List<MusicFolder> folders,
            boolean withPodcast) {
        return dialect.getUnregisteredId3Artists(count, folders, withPodcast);
    }

    public List<MediaFile> getChangedAlbums(final int count, final List<MusicFolder> folders) {
        if (folders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = Map
            .of("type", MediaFile.MediaType.ALBUM.name(), "folders",
                    MusicFolder.toPathList(folders), "future", FAR_FUTURE, "count", count);
        return template.namedQuery("select " + QUERY_COLUMNS + """
                from media_file
                where type = :type and present and folder in (:folders)
                        and children_last_updated = :future
                limit :count
                """, rowMapper, args);
    }

    public List<MediaFile> getUnparsedAlbums(final int count, final List<MusicFolder> folders) {
        if (folders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = Map
            .of("type", MediaFile.MediaType.ALBUM.name(), "folders",
                    MusicFolder.toPathList(folders), "future", FAR_FUTURE, "count", count);
        return template.namedQuery("select " + QUERY_COLUMNS + """
                from media_file
                where type = :type and present and folder in (:folders) and last_scanned = :future
                limit :count
                """, rowMapper, args);
    }

    public List<MediaFile> getChangedId3Albums(final int count, List<MusicFolder> musicFolders,
            boolean withPodcast) {
        return dialect.getChangedId3Albums(count, musicFolders, withPodcast);
    }

    public List<MediaFile> getUnregisteredId3Albums(final int count, List<MusicFolder> musicFolders,
            boolean withPodcast) {
        return dialect.getUnregisteredId3Albums(count, musicFolders, withPodcast);
    }

    public List<MediaFile> getSongsByGenre(final List<String> genres, final long offset, long count,
            final List<MusicFolder> musicFolders, List<MediaType> types) {
        return dialect.getSongsByGenre(genres, offset, count, musicFolders, types);
    }

    public List<MediaFile> getSongsByArtist(String artist, int offset, int count) {
        return template
            .query("select " + QUERY_COLUMNS + """
                    from media_file
                    where type in (?,?,?) and artist=? and present
                    limit ? offset ?
                    """, rowMapper, MediaFile.MediaType.MUSIC.name(),
                    MediaFile.MediaType.PODCAST.name(), MediaFile.MediaType.AUDIOBOOK.name(),
                    artist, count, offset);
    }

    public @Nullable MediaFile getSongByArtistAndTitle(final String artist, final String title,
            final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty() || StringUtils.isBlank(title) || StringUtils.isBlank(artist)) {
            return null;
        }
        Map<String, Object> args = LegacyMap
            .of("artist", artist, "title", title, "type", MediaFile.MediaType.MUSIC.name(),
                    "folders", MusicFolder.toPathList(musicFolders));
        return template.namedQueryOne("select " + QUERY_COLUMNS + """
                from media_file
                where artist = :artist and title = :title
                        and type = :type and present and folder in (:folders)
                """, rowMapper, args);
    }

    public List<MediaFile> getStarredAlbums(final int offset, final int count,
            final String username, final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("type", MediaFile.MediaType.ALBUM.name(), "folders",
                    MusicFolder.toPathList(musicFolders), "username", username, "count", count,
                    "offset", offset);
        return template.namedQuery("select " + prefix(QUERY_COLUMNS, "m") + """
                from starred_media_file, media_file m
                where m.id = starred_media_file.media_file_id and m.present
                        and m.type = :type and m.folder in (:folders)
                        and starred_media_file.username = :username
                order by starred_media_file.created desc
                limit :count offset :offset
                """, rowMapper, args);
    }

    public List<MediaFile> getStarredDirectories(final int offset, final int count,
            final String username, final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("type", MediaFile.MediaType.DIRECTORY.name(), "folders",
                    MusicFolder.toPathList(musicFolders), "username", username, "count", count,
                    "offset", offset);
        return template.namedQuery("select " + prefix(QUERY_COLUMNS, "m") + """
                from starred_media_file, media_file m
                where m.id = starred_media_file.media_file_id and m.present
                        and m.type = :type and starred_media_file.username = :username
                        and m.folder in (:folders)
                order by starred_media_file.created desc
                limit :count offset :offset
                """, rowMapper, args);
    }

    public List<MediaFile> getStarredFiles(final int offset, final int count, final String username,
            final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> args = LegacyMap
            .of("types", Arrays
                .asList(MediaFile.MediaType.MUSIC.name(), MediaFile.MediaType.PODCAST.name(),
                        MediaFile.MediaType.AUDIOBOOK.name(), MediaFile.MediaType.VIDEO.name()),
                    "folders", MusicFolder.toPathList(musicFolders), "username", username, "count",
                    count, "offset", offset);
        return template.namedQuery("select " + prefix(QUERY_COLUMNS, "m") + """
                from starred_media_file, media_file m
                where m.id = starred_media_file.media_file_id and m.present and m.type in (:types)
                        and starred_media_file.username = :username and m.folder in (:folders)
                order by starred_media_file.created desc
                limit :count offset :offset
                """, rowMapper, args);
    }

    public List<MediaFile> getRandomSongs(ShuffleSelectionParam criteria, final String username) {

        if (criteria.getMusicFolders().isEmpty()) {
            return Collections.emptyList();
        }

        RandomSongsQueryBuilder queryBuilder = new RandomSongsQueryBuilder(criteria);

        Map<String, Object> args = LegacyMap
            .of("folders", MusicFolder.toPathList(criteria.getMusicFolders()), "username", username,
                    "fromYear", criteria.getFromYear(), "toYear", criteria.getToYear(), "genres",
                    criteria.getGenres(), "minLastPlayed", criteria.getMinLastPlayedDate(),
                    "maxLastPlayed", criteria.getMaxLastPlayedDate(), "minAlbumRating",
                    criteria.getMinAlbumRating(), "maxAlbumRating", criteria.getMaxAlbumRating(),
                    "minPlayCount", criteria.getMinPlayCount(), "maxPlayCount",
                    criteria.getMaxPlayCount(), "starred", criteria.isShowStarredSongs(),
                    "unstarred", criteria.isShowUnstarredSongs(), "format", criteria.getFormat());

        return template.namedQuery(queryBuilder.build(), rowMapper, args);
    }

    public int getPlayedAlbumCount(final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return 0;
        }
        Map<String, Object> args = LegacyMap
            .of("type", MediaFile.MediaType.ALBUM.name(), "folders",
                    MusicFolder.toPathList(musicFolders));
        return template.namedQueryForInt("""
                select count(*)
                from media_file
                where type = :type and play_count > 0 and present and folder in (:folders)
                """, 0, args);
    }

    public int getStarredAlbumCount(final String username, final List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return 0;
        }
        Map<String, Object> args = LegacyMap
            .of("type", MediaFile.MediaType.ALBUM.name(), "folders",
                    MusicFolder.toPathList(musicFolders), "username", username);
        return template.namedQueryForInt("""
                select count(*)
                from starred_media_file, media_file
                where media_file.id = starred_media_file.media_file_id
                        and media_file.type = :type
                        and media_file.present and media_file.folder in (:folders)
                        and starred_media_file.username = :username
                """, 0, args);
    }

    @Transactional
    public void starMediaFile(int id, String username) {
        unstarMediaFile(id, username);
        template.update("""
                insert into starred_media_file(media_file_id, username, created)
                values (?,?,?)
                """, id, username, now());
    }

    public void unstarMediaFile(int id, String username) {
        template.update("""
                delete from
                starred_media_file
                where media_file_id=? and username=?
                """, id, username);
    }

    public @Nullable Instant getMediaFileStarredDate(int id, String username) {
        return template.queryForInstant("""
                select created
                from starred_media_file
                where media_file_id=? and username=?
                """, null, id, username);
    }

    public void resetLastScanned(@Nullable Integer id) {
        String query = """
                update media_file
                set last_scanned = ?, children_last_updated = ?
                where present
                """;
        if (id == null) {
            template.update(query, FAR_PAST, FAR_PAST);
        } else {
            template.update(query + " and id = ?", FAR_PAST, FAR_PAST, id);
        }
    }

    public void updateLastScanned(int id, Instant lastScanned) {
        template.update("""
                update media_file
                set last_scanned = ?
                where present and id = ?
                """, lastScanned, id);
    }

    public void markNonPresent(Instant lastScanned) {
        int minId = template.queryForInt("""
                select min(id)
                from media_file
                where last_scanned < ? and present
                """, 0, lastScanned);
        int maxId = template.queryForInt("""
                select max(id)
                from media_file
                where last_scanned < ? and present
                """, 0, lastScanned);

        final int batchSize = 1000;
        Instant childrenLastUpdated = FAR_PAST; // Used to force a children rescan if file is later
                                                // resurrected.
        for (int id = minId; id <= maxId; id += batchSize) {
            template.update("""
                    update media_file
                    set present=false, children_last_updated=?
                    where id between ? and ? and last_scanned < ? and present
                    """, childrenLastUpdated, id, id + batchSize, lastScanned);
        }
    }

    public List<Integer> getArtistExpungeCandidates() {
        return template.queryForInts("""
                select id
                from media_file
                where media_file.type = ? and not present
                """, MediaFile.MediaType.DIRECTORY.name());
    }

    public List<Integer> getAlbumExpungeCandidates() {
        return template.queryForInts("""
                select id
                from media_file
                where media_file.type = ? and not present
                """, MediaFile.MediaType.ALBUM.name());
    }

    public List<Integer> getSongExpungeCandidates() {
        return template
            .queryForInts("""
                    select id
                    from media_file
                    where media_file.type in (?,?,?,?) and not present
                    """, MediaFile.MediaType.MUSIC.name(), MediaFile.MediaType.PODCAST.name(),
                    MediaFile.MediaType.AUDIOBOOK.name(), MediaFile.MediaType.VIDEO.name());
    }

    public int getMinId() {
        return template.queryForInt("""
                select min(id)
                from media_file
                where not present
                """, 0);
    }

    public int getMaxId() {
        return template.queryForInt("""
                select max(id)
                from media_file
                where not present
                """, 0);
    }

    public int expunge(int from, int to) {
        return template.update("""
                delete from media_file
                where id between ? and ? and not present
                """, from, to);
    }

    public List<SortCandidate> getCopyableSortAlbums(List<MusicFolder> folders) {
        return dialect.getCopyableSortAlbums(folders);
    }

    public List<ArtistSortCandidate> getCopyableSortPersons(List<MusicFolder> folders) {
        return dialect.getCopyableSortPersons(folders);
    }

    public List<MediaFile> getRandomSongsForAlbumArtist(int limit, String albumArtist,
            List<MusicFolder> musicFolders,
            BiFunction<Integer, Integer, List<Integer>> randomCallback) {
        return dialect
            .getRandomSongsForAlbumArtist(limit, albumArtist, musicFolders, randomCallback);
    }

    public List<ArtistSortCandidate> getNoSortPersons(List<MusicFolder> folders) {
        return dialect.getNoSortPersons(folders);
    }

    public List<ArtistSortCandidate> getSortCandidatePersons(
            @NonNull List<DuplicateSort> duplicates) {
        return dialect.getSortCandidatePersons(duplicates);
    }

    public List<SortCandidate> getNoSortAlbums(List<MusicFolder> folders) {
        return dialect.getNoSortAlbums(folders);
    }

    public List<SortCandidate> getDuplicateSortAlbums(List<MusicFolder> folders) {
        return dialect.getDuplicateSortAlbums(folders);
    }

    public List<DuplicateSort> getDuplicateSortPersons(List<MusicFolder> folders) {
        return dialect.getDuplicateSortPersons(folders);
    }

    public void updateAlbumSort(SortCandidate cand) {
        template.update("""
                update media_file
                set album_reading = ?, album_sort = ?, children_last_updated = ?
                where present and id = ?
                """, cand.getReading(), cand.getSort(), FAR_FUTURE, cand.getTargetId());
    }

    public void updateArtistSort(ArtistSortCandidate cand) {
        if (cand.getTargetField() == TargetField.ARTIST
                && cand.getTargetType() == MediaType.DIRECTORY) {
            template
                .update("""
                        update media_file
                        set artist_reading = ?, artist_sort = ?, music_index = ?
                        where id = ?
                        """, cand.getReading(), cand.getSort(), cand.musicIndex().get(),
                        cand.getTargetId());
        } else if (cand.getTargetField() == TargetField.ARTIST) {
            template.update("""
                    update media_file
                    set artist_reading = ?, artist_sort = ?
                    where id = ?
                    """, cand.getReading(), cand.getSort(), cand.getTargetId());
        } else if (cand.getTargetField() == TargetField.ALBUM_ARTIST) {
            template.update("""
                    update media_file
                    set album_artist_reading = ?, album_artist_sort = ?, children_last_updated = ?
                    where id = ?
                    """, cand.getReading(), cand.getSort(), FAR_FUTURE, cand.getTargetId());
        } else if (cand.getTargetField() == TargetField.COMPOSER) {
            template.update("""
                    update media_file
                    set composer_sort = ?
                    where id = ?
                    """, cand.getSort(), cand.getTargetId());
        }
    }

    public void updateArtistSort(List<ArtistSortCandidate> cands) {
        final String updates = "update media_file ";
        final StringBuilder cols = new StringBuilder(130);
        cols.append("set ");
        final String cond = "where id = ?";

        List<Object> args = new ArrayList<>();
        cands.forEach(cand -> {
            if (cand.getTargetField() == TargetField.ARTIST
                    && cand.getTargetType() == MediaType.DIRECTORY) {
                cols.append("artist_reading=?, artist_sort=?, music_index = ?, ");
                args.add(cand.getReading());
                args.add(cand.getSort());
                args.add(cand.musicIndex().get());
            } else if (cand.getTargetField() == TargetField.ARTIST) {
                cols.append("artist_reading=?, artist_sort=?, ");
                args.add(cand.getReading());
                args.add(cand.getSort());
            } else if (cand.getTargetField() == TargetField.ALBUM_ARTIST) {
                cols
                    .append("album_artist_reading=?, album_artist_sort=?, children_last_updated = ?, ");
                args.add(cand.getReading());
                args.add(cand.getSort());
                args.add(FAR_FUTURE);
            } else if (cand.getTargetField() == TargetField.COMPOSER) {
                cols.append("composer_sort=?, ");
                args.add(cand.getSort());
            }
        });
        args.add(cands.get(0).getTargetId());

        String query = updates + cols.toString().replaceFirst(", $", " ") + cond;
        template.update(query, args.toArray());
    }

    static class RandomSongsQueryBuilder {

        private final ShuffleSelectionParam criteria;

        RandomSongsQueryBuilder(ShuffleSelectionParam criteria) {
            this.criteria = criteria;
        }

        String build() {
            StringBuilder query = new StringBuilder(1024); // 988 + param
            query
                .append("select ")
                .append(prefix(QUERY_COLUMNS, "media_file"))
                .append("from media_file");
            getIfJoinStarred().ifPresent(query::append);
            getIfJoinAlbumRating().ifPresent(query::append);
            query.append(" where media_file.present and media_file.type = 'MUSIC'");
            getFolderCondition().ifPresent(query::append);
            getGenreCondition().ifPresent(query::append);
            getFormatCondition().ifPresent(query::append);
            getFromYearCondition().ifPresent(query::append);
            getToYearCondition().ifPresent(query::append);
            getMinLastPlayedDateCondition().ifPresent(query::append);
            getMaxLastPlayedDateCondition().ifPresent(query::append);
            getMinAlbumRatingCondition().ifPresent(query::append);
            getMaxAlbumRatingCondition().ifPresent(query::append);
            getMinPlayCountCondition().ifPresent(query::append);
            getMaxPlayCountCondition().ifPresent(query::append);
            getShowStarredSongsCondition().ifPresent(query::append);
            getShowUnstarredSongsCondition().ifPresent(query::append);
            query.append(" order by rand() limit ").append(criteria.getCount());
            return query.toString();
        }

        Optional<String> getIfJoinStarred() {
            boolean joinStarred = criteria.isShowStarredSongs() ^ criteria.isShowUnstarredSongs();
            if (joinStarred) {
                return Optional.of("""
                        \sleft outer join starred_media_file \
                        on media_file.id = starred_media_file.media_file_id \
                        and starred_media_file.username = :username\
                        """);
            }
            return Optional.empty();
        }

        Optional<String> getIfJoinAlbumRating() {
            boolean joinAlbumRating = criteria.getMinAlbumRating() != null
                    || criteria.getMaxAlbumRating() != null;
            if (joinAlbumRating) {
                return Optional.of("""
                         left outer join media_file media_album \
                        on media_album.type = 'ALBUM' and media_album.album = media_file.album \
                        and media_album.artist = media_file.artist \
                        left outer join user_rating \
                        on user_rating.path = media_album.path and user_rating.username = :username\
                        """);
            }
            return Optional.empty();
        }

        Optional<String> getFolderCondition() {
            if (!criteria.getMusicFolders().isEmpty()) {
                return Optional.of(" and media_file.folder in (:folders)");
            }
            return Optional.empty();
        }

        Optional<String> getGenreCondition() {
            if (criteria.getGenres() != null) {
                return Optional.of(" and media_file.genre in (:genres)");
            }
            return Optional.empty();
        }

        Optional<String> getFormatCondition() {
            if (criteria.getFormat() != null) {
                return Optional.of(" and media_file.format = :format");
            }
            return Optional.empty();
        }

        Optional<String> getFromYearCondition() {
            if (criteria.getFromYear() != null) {
                return Optional.of(" and media_file.year >= :fromYear");
            }
            return Optional.empty();
        }

        Optional<String> getToYearCondition() {
            if (criteria.getToYear() != null) {
                return Optional.of(" and media_file.year <= :toYear");
            }
            return Optional.empty();
        }

        Optional<String> getMinLastPlayedDateCondition() {
            if (criteria.getMinLastPlayedDate() != null) {
                return Optional.of(" and media_file.last_played >= :minLastPlayed");
            }
            return Optional.empty();
        }

        Optional<String> getMaxLastPlayedDateCondition() {
            if (criteria.getMaxLastPlayedDate() != null) {
                if (criteria.getMinLastPlayedDate() == null) {
                    return Optional
                        .of(" and (media_file.last_played is null or media_file.last_played <= :maxLastPlayed)");
                } else {
                    return Optional.of(" and media_file.last_played <= :maxLastPlayed");
                }
            }
            return Optional.empty();
        }

        Optional<String> getMinAlbumRatingCondition() {
            if (criteria.getMinAlbumRating() != null) {
                return Optional.of(" and user_rating.rating >= :minAlbumRating");
            }
            return Optional.empty();
        }

        Optional<String> getMaxAlbumRatingCondition() {
            if (criteria.getMaxAlbumRating() != null) {
                if (criteria.getMinAlbumRating() == null) {
                    return Optional
                        .of(" and (user_rating.rating is null or user_rating.rating <= :maxAlbumRating)");
                } else {
                    return Optional.of(" and user_rating.rating <= :maxAlbumRating");
                }
            }
            return Optional.empty();
        }

        Optional<String> getMinPlayCountCondition() {
            if (criteria.getMinPlayCount() != null) {
                return Optional.of(" and media_file.play_count >= :minPlayCount");
            }
            return Optional.empty();
        }

        Optional<String> getMaxPlayCountCondition() {
            if (criteria.getMaxPlayCount() != null) {
                if (criteria.getMinPlayCount() == null) {
                    return Optional
                        .of(" and (media_file.play_count is null or media_file.play_count <= :maxPlayCount)");
                } else {
                    return Optional.of(" and media_file.play_count <= :maxPlayCount");
                }
            }
            return Optional.empty();
        }

        Optional<String> getShowStarredSongsCondition() {
            if (criteria.isShowStarredSongs() && !criteria.isShowUnstarredSongs()) {
                return Optional.of(" and starred_media_file.id is not null");
            }
            return Optional.empty();
        }

        Optional<String> getShowUnstarredSongsCondition() {
            if (criteria.isShowUnstarredSongs() && !criteria.isShowStarredSongs()) {
                return Optional.of(" and starred_media_file.id is null");
            }
            return Optional.empty();
        }
    }

    // ############################################################################
    // Jpsonic domain

    private static final String DOMAIN_JOINED_COLUMNS_QUERY = DaoUtils.DOMAIN_JOINED_COLUMNS_QUERY;
    private static final String DOMAIN_JOINED_TABLES_QUERY = DaoUtils.DOMAIN_JOINED_TABLES_QUERY;
    private static final String DOMAIN_ROOT_COLUMNS_QUERY = DaoUtils.DOMAIN_ROOT_COLUMNS_QUERY;
    private static final String DOMAIN_ROOT_TABLES_QUERY = DaoUtils.DOMAIN_ROOT_TABLES_QUERY;

    public int countMediaFile(List<com.tesshu.jpsonic.domain.model.MusicFolder> folders,
            com.tesshu.jpsonic.domain.model.MediaFile.Type mediaType) {
        if (folders.isEmpty()) {
            return 0;
        }
        Map<String, Object> args = Map
            .of("type", mediaType.name(), "folders",
                    com.tesshu.jpsonic.domain.model.MusicFolder.toPathList(folders));
        String query = """
                select count(*)
                from media_file
                where type= :type and folder in(:folders)
                """;
        return template.namedQueryForInt(query, 0, args);
    }

    public int countChildren(List<com.tesshu.jpsonic.domain.model.MusicFolder> folders,
            com.tesshu.jpsonic.domain.model.MediaFile.Type... excludes) {
        if (folders.isEmpty()) {
            return 0;
        }

        Map<String, Object> args = Map
            .of("folders", com.tesshu.jpsonic.domain.model.MusicFolder.toPathList(folders),
                    "excludes",
                    Stream
                        .of(excludes)
                        .map(com.tesshu.jpsonic.domain.model.MediaFile.Type::name)
                        .toList());
        return template.namedQueryForInt("""
                select count(*)
                from media_file
                where parent_path in(:folders) %s
                """.formatted(excludes.length == 0 ? "" : "and type not in(:excludes)"), 0, args);
    }

    public int countChildren(com.tesshu.jpsonic.domain.model.MediaFile parent,
            com.tesshu.jpsonic.domain.model.MediaFile.Type... excludes) {
        Map<String, Object> args = Map
            .of("parentPath", parent.pathString(), "excludes",
                    Stream
                        .of(excludes)
                        .map(com.tesshu.jpsonic.domain.model.MediaFile.Type::name)
                        .toList());
        return template.namedQueryForInt("""
                select count(*)
                from media_file
                where parent_path=:parentPath and present %s
                """.formatted(excludes.length == 0 ? "" : "and type not in(:excludes)"), 0, args);
    }

    public boolean existsAccessibleMediaFile(String username,
            com.tesshu.jpsonic.domain.model.MediaFile mediaFile) {
        String query = """
                select 1 from media_file mf
                join music_folder f on mf.folder = f.path
                join music_folder_user mfu on mfu.music_folder_id = f.id
                where mf.id = ? and mfu.username = ?
                """;
        return template.queryForInt(query, 0, mediaFile.id(), username) > 0;
    }

    public List<com.tesshu.jpsonic.domain.model.MediaFile> findAlbums(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders,
            RuntimeOrderPolicy.AlbumSortOrder order, long offset, long count) {
        if (folders.isEmpty()) {
            return Collections.emptyList();
        }
        // spotless:off
        Map<String, Object> args = LegacyMap.of(
                "type",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.ALBUM.name(),
                "folders", com.tesshu.jpsonic.domain.model.MusicFolder.toPathList(folders),
                "count", count,
                "offset", offset);
        // spotless:on

        String orderQuery = switch (order) {
        case BY_ARTIST_AND_ALBUM -> "order by pmf.media_file_order, mf.media_file_order";
        default -> "order by mf.media_file_order";
        };
        return template
            .namedQuery("select " + DOMAIN_JOINED_COLUMNS_QUERY + DOMAIN_JOINED_TABLES_QUERY + """
                    where mf.type = :type and mf.folder in (:folders)
                    %s
                    offset :offset limit :count
                    """.formatted(orderQuery), domainRowMapper, args);
    }

    public List<com.tesshu.jpsonic.domain.model.MediaFile> findChildren(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders,
            com.tesshu.jpsonic.domain.model.Album parent, long offset, long count,
            com.tesshu.jpsonic.domain.model.MediaFile.Type... excludes) {
        if (folders.isEmpty() || parent.artist().isEmpty()) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = LegacyMap.of(
                "directory",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.DIRECTORY.name(),
                "album",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.ALBUM.name(),
                "music",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.MUSIC.name(),
                "audiobook",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.AUDIOBOOK.name(),
                "video",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.VIDEO.name(),
                "artistName", parent.artist().get(),
                "albumName", parent.name(),
                "folders",
                    com.tesshu.jpsonic.domain.model.MusicFolder.toPathList(folders),
                "offset", offset,
                "count", count,
                "excludes", Stream.of(excludes)
                        .map(com.tesshu.jpsonic.domain.model.MediaFile.Type::name).toList());
        // spotless:on

        String typeFilter = excludes.length == 0 ? "" : "and type not in(:excludes)";

        String query = "select " + DOMAIN_JOINED_COLUMNS_QUERY + """
                ,
                case mf.type
                    when :directory then 1
                    when :album then 2
                    when :music then 3
                    when :audiobook then 4
                    when :video then 5
                end as type_order
                """ + DOMAIN_JOINED_TABLES_QUERY + """
                where mf.album_artist=:artistName
                    and mf.album=:albumName
                    and mf.folder in (:folders)
                    %s
                order by type_order,
                    mf.disc_number is null, mf.disc_number,
                    mf.track_number is null, mf.track_number,
                    mf.media_file_order
                offset :offset limit :count
                """.formatted(typeFilter);

        return template.namedQuery(query, domainRowMapper, args);
    }

    public List<com.tesshu.jpsonic.domain.model.MediaFile> findChildren(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders, long offset, long count,
            com.tesshu.jpsonic.domain.model.MediaFile.Type... excludes) {
        if (folders.isEmpty()) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = LegacyMap.of(
                "directory",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.DIRECTORY.name(),
                "album",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.ALBUM.name(),
                "music",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.MUSIC.name(),
                "audiobook",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.AUDIOBOOK.name(),
                "video",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.VIDEO.name(),
                "folders",
                    com.tesshu.jpsonic.domain.model.MusicFolder.toPathList(folders),
                "offset", offset,
                "count", count,
                "excludes", Stream.of(excludes)
                        .map(com.tesshu.jpsonic.domain.model.MediaFile.Type::name).toList());
        // spotless:on

        String typeFilter = excludes.length == 0 ? "" : "and type not in(:excludes)";
        return template.namedQuery("select " + DOMAIN_JOINED_COLUMNS_QUERY + """
                        ,
                        case mf.type
                            when :directory then 1
                            when :album then 2
                            when :music then 3
                            when :audiobook then 4
                            when :video then 5
                        end as type_order\s
                """ + DOMAIN_JOINED_TABLES_QUERY + """
                where parent_path in (:folders)
                %s
                order by type_order, mf.media_file_order
                offset :offset limit :count
                """.formatted(typeFilter), domainRowMapper, args);
    }

    public List<com.tesshu.jpsonic.domain.model.MediaFile> findChildren(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders, MusicIndex musicIndex,
            long offset, long count, com.tesshu.jpsonic.domain.model.MediaFile.Type... excludes) {
        if (folders.isEmpty()) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = LegacyMap.of(
                "directory",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.DIRECTORY.name(),
                "album",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.ALBUM.name(),
                "music",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.MUSIC.name(),
                "audiobook",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.AUDIOBOOK.name(),
                "video",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.VIDEO.name(),
                "folders",
                    com.tesshu.jpsonic.domain.model.MusicFolder.toPathList(folders),
                "musicIndex", musicIndex.index(),
                "offset", offset,
                "count", count,
                "excludes", Stream.of(excludes)
                    .map(com.tesshu.jpsonic.domain.model.MediaFile.Type::name).toList());
        // spotless:on

        String typeFilter = excludes.length == 0 ? "" : "and type not in(:excludes)";
        return template.namedQuery("select " + DOMAIN_JOINED_COLUMNS_QUERY + """
                ,
                case mf.type
                    when :directory then 1
                    when :album then 2
                    when :music then 3
                    when :audiobook then 4
                    when :video then 5
                end as type_order\s
                """ + DOMAIN_JOINED_TABLES_QUERY + """
                where mf.music_index=:musicIndex and mf.parent_path in (:folders)
                %s
                order by type_order, mf.media_file_order
                offset :offset limit :count
                """.formatted(typeFilter), domainRowMapper, args);
    }

    public List<com.tesshu.jpsonic.domain.model.MediaFile> findChildren(
            com.tesshu.jpsonic.domain.model.MediaFile parent, RuntimeOrderPolicy.ChildOrder order,
            long offset, long count, com.tesshu.jpsonic.domain.model.MediaFile.Type... excludes) {

        // spotless:off
        Map<String, Object> args = Map.of(
                "directory",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.DIRECTORY.name(),
                "album",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.ALBUM.name(),
                "music",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.MUSIC.name(),
                "audiobook",
                    com.tesshu.jpsonic.domain.model.MediaFile.Type.AUDIOBOOK.name(),
                "video",
                com.tesshu.jpsonic.domain.model.MediaFile.Type.VIDEO.name(),
                "path", parent.pathString(),
                "count", count,
                "offset", offset,
                "excludes",
                    Stream.of(excludes)
                    .map(com.tesshu.jpsonic.domain.model.MediaFile.Type::name)
                    .toList());
        // spotless:on

        String typeFilter = excludes.length == 0 ? "" : "and mf.type not in(:excludes)\s";

        String orderQuery = switch (order) {
        case DEFAULT -> "order by type_order, mf.media_file_order\s";
        case YEAR -> "order by type_order, mf.year is null, mf.year, mf.media_file_order\s";
        case TRACK -> """
                order by type_order,
                    mf.disc_number is null, mf.disc_number,
                    mf.track_number is null, mf.track_number,
                    media_file_order\s
                """;
        };

        return template.namedQuery("select " + DOMAIN_JOINED_COLUMNS_QUERY + """
                ,
                case mf.type
                    when :directory then 1
                    when :album then 2
                    when :music then 3
                    when :audiobook then 4
                    when :video then 5
                end as type_order
                """ + DOMAIN_JOINED_TABLES_QUERY + """
                where parent_path=:path
                %s
                %s
                offset :offset limit :count
                """.formatted(typeFilter, orderQuery), domainRowMapper, args);
    }

    public List<com.tesshu.jpsonic.domain.model.MediaFile> findIndexedDirectories(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders, List<String> shortcuts) {
        if (folders.isEmpty()) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = Map.of(
                "types", List.of(
                        com.tesshu.jpsonic.domain.model.MediaFile.Type.DIRECTORY.name(),
                        com.tesshu.jpsonic.domain.model.MediaFile.Type.ALBUM.name()),
                "directory", MediaFile.MediaType.DIRECTORY.name(),
                "album", MediaFile.MediaType.ALBUM.name(),
                "folders", com.tesshu.jpsonic.domain.model.MusicFolder.toPathList(folders),
                "shortcuts", shortcuts);
                // spotless:on

        String shortcutsFilter = shortcuts.isEmpty() ? "" : """
                and case mf.type
                    when :directory then mf.artist not in (:shortcuts)
                    when :album then mf.album not in (:shortcuts)
                end
                """;

        return template.namedQuery("select " + DOMAIN_ROOT_COLUMNS_QUERY + """
                        ,
                        case mf.type
                            when :directory then 1
                            when :album then 2
                        end as type_order
                """ + DOMAIN_ROOT_TABLES_QUERY + """
                where mf.folder in(:folders)
                        and mf.parent_path in(:folders)
                        and mf.path not in(:folders)
                        and mf.type in(:types)
                        and mf.music_index <> ''
                        %s
                order by type_order, mf.media_file_order
                """.formatted(shortcutsFilter), domainRowMapper, args);
    }

    public List<IndexWithCount> findIndexWithCounts(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders, List<String> shortcutPaths) {
        if (folders.isEmpty()) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = LegacyMap.of(
                "types", List.of(
                        com.tesshu.jpsonic.domain.model.MediaFile.Type.DIRECTORY.name(),
                        com.tesshu.jpsonic.domain.model.MediaFile.Type.ALBUM.name()),
                "folders", com.tesshu.jpsonic.domain.model.MusicFolder.toPathList(folders),
                "shotcuts", shortcutPaths);
        // spotless:on

        String shotcutFilter = shortcutPaths.isEmpty() ? "" : "and path not in (:shotcuts)";

        return template.namedQuery("""
                select distinct music_index, count(music_index)
                from media_file
                where type in(:types)
                        and parent_path in(:folders)
                        %s
                group by music_index
                order by music_index
                """.formatted(shotcutFilter), indexWithCountMapper, args);
    }

    public List<com.tesshu.jpsonic.domain.model.MediaFile> findMediaFile(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders,
            com.tesshu.jpsonic.domain.model.MediaFile.Type targetType, long offset, long count) {
        if (folders.isEmpty()) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = LegacyMap.of(
                "folders", com.tesshu.jpsonic.domain.model.MusicFolder.toPathList(folders),
                "type", targetType.name(),
                "count", count,
                "offset", offset);
        // spotless:on

        return template
            .namedQuery("select " + DOMAIN_JOINED_COLUMNS_QUERY + DOMAIN_JOINED_TABLES_QUERY + """
                    where type = :type and folder in(:folders)
                    offset :offset limit :count
                    """, domainRowMapper, args);
    }

    public List<com.tesshu.jpsonic.domain.model.MediaFile> findNewestAlbums(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders, long offset, long count) {
        if (folders.isEmpty()) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = Map.of(
                "type", MediaFile.MediaType.ALBUM.name(),
                "folders", com.tesshu.jpsonic.domain.model.MusicFolder.toPathList(folders),
                "count", count,
                "offset", offset);
        // spotless:on

        return template
            .namedQuery("select " + DOMAIN_JOINED_COLUMNS_QUERY + DOMAIN_JOINED_TABLES_QUERY + """
                    where type = :type and folder in (:folders)
                    order by created desc
                    offset :offset limit :count
                    """, domainRowMapper, args);
    }

    public com.tesshu.jpsonic.domain.model.MediaFile getDomainMediaFile(int id) {
        return template
            .queryOne("select " + DOMAIN_JOINED_COLUMNS_QUERY + DOMAIN_JOINED_TABLES_QUERY
                    + "where mf.id=?", domainRowMapper, id);
    }

    public com.tesshu.jpsonic.domain.model.MediaFile getDomainMediaFile(Path path) {
        return template
            .queryOne("select " + DOMAIN_JOINED_COLUMNS_QUERY + DOMAIN_JOINED_TABLES_QUERY
                    + "where mf.path=?", domainRowMapper, path.toString());
    }

    public com.tesshu.jpsonic.domain.model.MediaFile getDomainMusicFolder(Path path) {
        return template
            .queryOne("select " + DOMAIN_ROOT_COLUMNS_QUERY + DOMAIN_ROOT_TABLES_QUERY
                    + "where mf.path=?", domainRowMapper, path.toString());
    }

    // ############################################################################
    // Jpsonic domain (Search)

    public List<com.tesshu.jpsonic.domain.model.MediaFile> getSongsByGenres(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders, List<String> genres,
            long offset, long count, com.tesshu.jpsonic.domain.model.MediaFile.Type... types) {
        if (folders.isEmpty() || genres.isEmpty()) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = Map.of(
                "folders", com.tesshu.jpsonic.domain.model.MusicFolder.toPathList(folders),
                "types", Stream.of(types).map(com.tesshu.jpsonic.domain.model.MediaFile.Type::name).toList(),
                "genres", genres,
                "offset", offset,
                "count", count);
        // spotless:on

        return template.namedQuery("select " + DOMAIN_JOINED_COLUMNS_QUERY + """
                , mf.media_file_order = -1 as is_under_root
                """ + DOMAIN_JOINED_TABLES_QUERY + """
                left join media_file ar on pmf.parent_path = ar.path
                join music_folder on mf.folder = music_folder.path
                where
                    mf.folder in (:folders)
                    and mf.type in (:types)
                    and mf.genre in (:genres)
                order by
                    is_under_root,
                    ar.media_file_order, pmf.media_file_order,
                    folder_order,
                    mf.media_file_order,
                    mf.track_number
                offset :offset limit :count
                """, domainRowMapper, args);
    }

    public List<com.tesshu.jpsonic.domain.model.MediaFile> getDomainRandomSongsForAlbumArtist(
            int limit, String albumArtist,
            List<com.tesshu.jpsonic.domain.model.MusicFolder> musicFolders,
            BiFunction<Integer, Integer, List<Integer>> randomCallback) {
        return dialect
            .getDomainRandomSongsForAlbumArtist(limit, albumArtist, musicFolders, randomCallback);
    }

    public int countChldren(List<com.tesshu.jpsonic.domain.model.MusicFolder> folders,
            List<String> genres, com.tesshu.jpsonic.domain.model.Album album, Type... types) {
        if (folders.isEmpty() || genres.isEmpty()) {
            return 0;
        }

        // spotless:off
        Map<String, Object> args = Map.of(
                "folders", com.tesshu.jpsonic.domain.model.MusicFolder.toPathList(folders),
                "types", Stream.of(types).map(com.tesshu.jpsonic.domain.model.MediaFile.Type::name).toList(),
                "genres", genres,
                "albumArtist", album.artist().orElse(""),
                "album", album.name());
        // spotless:on

        return template.namedQueryForInt("select count(*) " + DOMAIN_JOINED_TABLES_QUERY + """
                where mf.folder in (:folders)
                    and mf.album_artist = :albumArtist
                    and mf.album = :album
                    and mf.genre in (:genres)
                    and mf.type in (:types)
                """, 0, args);
    }

    public List<com.tesshu.jpsonic.domain.model.MediaFile> findChildren(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders, List<String> genres,
            com.tesshu.jpsonic.domain.model.Album album, long offset, long count,
            com.tesshu.jpsonic.domain.model.MediaFile.Type... types) {
        if (folders.isEmpty() || genres.isEmpty()) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = Map.of(
                "folders", com.tesshu.jpsonic.domain.model.MusicFolder.toPathList(folders),
                "types", Stream.of(types).map(com.tesshu.jpsonic.domain.model.MediaFile.Type::name).toList(),
                "genres", genres,
                "albumArtist", album.artist().orElse(""),
                "album", album.name(),
                "count", count,
                "offset", offset);
        // spotless:on

        return template
            .namedQuery("select " + DOMAIN_JOINED_COLUMNS_QUERY + DOMAIN_JOINED_TABLES_QUERY + """
                    where mf.folder in (:folders)
                        and mf.album_artist = :albumArtist
                        and mf.album = :album
                        and mf.genre in (:genres)
                        and mf.type in (:types)
                    order by mf.media_file_order, mf.track_number
                    offset :offset limit :count
                    """, domainRowMapper, args);
    }

    public List<com.tesshu.jpsonic.domain.model.MediaFile> findChildren(
            List<com.tesshu.jpsonic.domain.model.MusicFolder> folders, Playlist playlist,
            long offset, long count, com.tesshu.jpsonic.domain.model.MediaFile.Type... excludes) {
        if (folders.isEmpty()) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = Map.of(
                "folders", com.tesshu.jpsonic.domain.model.MusicFolder.toPathList(folders),
                "playlistId", playlist.id(),
                "excludes", Stream.of(excludes).map(com.tesshu.jpsonic.domain.model.MediaFile.Type::name).toList(),
                "offset", offset,
                "count", count);
        // spotless:on

        return template
            .namedQuery("select " + DOMAIN_JOINED_COLUMNS_QUERY + DOMAIN_JOINED_TABLES_QUERY + """
                    join playlist_file on mf.id = playlist_file.media_file_id
                    where mf.folder in (:folders)
                        and mf.type not in (:excludes)
                        and playlist_file.playlist_id = :playlistId
                    order by mf.media_file_order, mf.track_number
                    offset :offset limit :count
                    """, domainRowMapper, args);
    }

    public List<com.tesshu.jpsonic.domain.model.MediaFile> findAlbumsByGenres(
            final List<com.tesshu.jpsonic.domain.model.MusicFolder> folders,
            final List<String> genres, final long offset, long count) {
        if (folders.isEmpty() || genres.isEmpty()) {
            return Collections.emptyList();
        }

        // spotless:off
        Map<String, Object> args = Map.of("type",
                MediaType.ALBUM.name(),
                "genres", genres,
                "folders", com.tesshu.jpsonic.domain.model.MusicFolder.toPathList(folders),
                "count", count,
                "offset", offset);
        // spotless:on

        return template
            .namedQuery("select " + DOMAIN_JOINED_COLUMNS_QUERY + DOMAIN_JOINED_TABLES_QUERY + """
                    where mf.type = :type
                        and mf.folder in (:folders)
                        and mf.genre in (:genres)
                    order by media_file_order
                    limit :count offset :offset
                    """, domainRowMapper, args);
    }
}

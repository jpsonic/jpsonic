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
 * (C) 2025 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.scanner;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;

import com.tesshu.jpsonic.domain.model.Genre;
import com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.Artist;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.AlbumDao;
import com.tesshu.jpsonic.persistence.api.repository.ArtistDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent.ScanEventType;
import com.tesshu.jpsonic.service.MediaFileService;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>Scan Flow Position</h2> This procedure is executed as the <strong>fourth
 * step</strong> in the overall scan flow. It builds an artist/album structure
 * (ArtistId3/AlbumId3) based on ID3 tag data, following the Subsonic
 * specification.
 *
 * <h2>Overview</h2> This scan step extracts information such as title, album
 * name, artist, genre, and track number from ID3 tags, and constructs a logical
 * ArtistId3/AlbumId3 structure in accordance with the Subsonic specification.
 * It also performs reclassification and correction of artist and album
 * ordering.
 *
 * <h3>Genre Handling</h3> In this phase, a genre master is created and
 * preserved according to the Subsonic specification. The generated genre master
 * is currently used in both the Subsonic API and the Web UI.
 *
 * <p>
 * In contrast, the genre information used in the UPnP feature is generated
 * based on user-defined conditions. These genres are not persisted in the
 * database; instead, they are dynamically built using the Lucene Genre index
 * created in the previous phase based on the ID3 tags.
 *
 * <h3>Main Responsibilities</h3>
 * <ul>
 * <li>{@link #createAlbumId3s()}, {@link #createArtistId3s()} Extract album and
 * artist information from ID3 tags and build AlbumId3/ArtistId3 structures.
 * </li>
 * <li>{@link #refleshAlbumId3()}, {@link #refleshArtistId3()} Reclassify and
 * reconstruct existing structures.</li>
 * <li>{@link #updateOrderOfAlbumId3()}, {@link #updateOrderOfArtistId3()}
 * Adjust album/artist playback and display order.</li>
 * <li>{@link #updateGenreMaster()} Build and register the genre master based on
 * ID3 tags, in compliance with the Subsonic specification.</li>
 * <li>{@link #iterateAlbumId3()}, {@link #iterateArtistId3()} Traverse and
 * reflect ID3 information per album and artist unit.</li>
 * </ul>
 *
 * <p>
 * Upon completion of this step, an ArtistId3/AlbumId3 structure consistent with
 * ID3 tag content will be established, ensuring conformance with Subsonic-style
 * scan results.
 *
 * @see ScanProcedure
 * @see FileMetadataScanProcedure
 * @see MediaScannerServiceImpl
 */
@Service
public class Id3MetadataScanProcedure {

    private final MusicFolderServiceImpl musicFolderService;
    private final Indexer indexer;
    private final GenreMasterProviderAdapter genreMasterProvider;
    private final MediaFileService mediaFileService;
    private final MediaFileDao mediaFileDao;
    private final ArtistDao artistDao;
    private final AlbumDao albumDao;
    private final MusicIndexProviderImpl musicIndexProvider;
    private final JpsonicComparators comparators;
    private final ScanHelper scanHelper;

    public Id3MetadataScanProcedure(MusicFolderServiceImpl musicFolderService, Indexer indexer,
            GenreMasterProviderAdapter genreMasterProvider, MediaFileService mediaFileService,
            MediaFileDao mediaFileDao, ArtistDao artistDao, AlbumDao albumDao,
            MusicIndexProviderImpl musicIndexProvider, JpsonicComparators comparators,
            ScanHelper scanHelper) {
        super();
        this.musicFolderService = musicFolderService;
        this.indexer = indexer;
        this.genreMasterProvider = genreMasterProvider;
        this.mediaFileService = mediaFileService;
        this.mediaFileDao = mediaFileDao;
        this.artistDao = artistDao;
        this.albumDao = albumDao;
        this.musicIndexProvider = musicIndexProvider;
        this.comparators = comparators;
        this.scanHelper = scanHelper;
    }

    /**
     * Refreshes ID3-based Album records using already-registered songs.
     * <p>
     * This method performs the following steps:
     * <ul>
     * <li>Determines whether podcast folders are included</li>
     * <li>Performs ID3 album iteration and cleanup</li>
     * <li>Updates existing Album entries from changed MediaFiles</li>
     * <li>Creates new Album entries from MediaFiles with unregistered ID3 Album
     * data</li>
     * </ul>
     * If {@code context.ignoreFileTimestamps()} is enabled, children's last-updated
     * timestamps are reset.
     *
     * @return true if any albums were updated or newly created; false otherwise
     */
    boolean refleshAlbumId3(@NonNull ScanContext context) {
        if (scanHelper.isInterrupted()) {
            return false;
        }

        boolean withPodcast = isPodcastInMusicFolders(context);

        iterateAlbumId3(context, withPodcast);

        if (context.ignoreFileTimestamps()) {
            mediaFileDao.resetAlbumChildrenLastUpdated();
        }

        int countUpdate = updateAlbumId3s(context, withPodcast);
        int countNew = createAlbumId3s(context, withPodcast);

        String comment = "Update(%d)/New(%d)".formatted(countUpdate, countNew);
        scanHelper.createScanEvent(context, ScanEventType.REFRESH_ALBUM_ID3, comment);

        return countUpdate > 0 || countNew > 0;
    }

    /**
     * Checks whether the configured podcast folder path exists among the registered
     * music folders.
     *
     * @param context The scan context, including podcastFolderPath.
     * @return true if the podcast folder is included in the music folders, false
     *         otherwise
     */
    boolean isPodcastInMusicFolders(@NonNull ScanContext context) {
        String podcastFolderPath = context.podcastFolder();

        return musicFolderService
            .getAllMusicFolders()
            .stream()
            .anyMatch(folder -> folder.getPathString().equals(podcastFolderPath));
    }

    /**
     * Iterates over album entries to update last-scanned timestamps from ID3 tags,
     * and then performs cleanup and index maintenance.
     *
     * <p>
     * This method performs the following steps:
     * <ul>
     * <li>Updates last-scanned dates of albums (optionally including podcasts)</li>
     * <li>Removes obsolete album ID3 index entries</li>
     * <li>Expunges stale album records from the database</li>
     * </ul>
     * </p>
     *
     * @param context     The scan context, including scan date and flags.
     * @param withPodcast Whether to include podcast files in the indexing.
     */
    @Transactional
    public void iterateAlbumId3(@NonNull ScanContext context, boolean withPodcast) {
        // Update album last-scanned timestamps from ID3 metadata
        albumDao.iterateLastScanned(context.scanDate(), withPodcast);

        // Expunge outdated ID3 index entries for albums
        indexer.expungeAlbumId3(albumDao.getExpungeCandidates(context.scanDate()));

        // Remove stale album records from the database
        albumDao.expunge(context.scanDate());
    }

    /**
     * Finds the corresponding MusicFolder for the given MediaFile by matching
     * folder path strings.
     *
     * @param mediaFile the media file whose associated music folder is to be found
     * @return an Optional containing the matching MusicFolder if found, otherwise
     *         empty
     */
    private Optional<MusicFolder> getMusicFolder(MediaFile mediaFile) {
        return musicFolderService
            .getAllMusicFolders()
            .stream()
            .filter(folder -> folder.getPathString().equals(mediaFile.getFolder()))
            .findFirst();
    }

    /**
     * Updates existing ID3-based albums by scanning media files for changed
     * metadata. If applicable, updates the album, indexes it, and updates children
     * timestamps.
     *
     * @param withPodcast whether to include podcast folders in the target scope
     * @return the number of updated albums
     */
    int updateAlbumId3s(@NonNull ScanContext context, boolean withPodcast) {
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();
        List<MediaFile> candidates = mediaFileDao
            .getChangedId3Albums(ScanConstants.ACQUISITION_MAX, folders, withPodcast);
        LongAdder updatedCount = new LongAdder();

        updateAlbums: while (!candidates.isEmpty()) {
            for (int i = 0; i < candidates.size(); i++) {
                if (i % 4_000 == 0) {
                    scanHelper.repeatWait();
                    if (scanHelper.isInterrupted()) {
                        break updateAlbums;
                    }
                }

                MediaFile song = candidates.get(i);
                Album registered = albumDao.getAlbum(song.getAlbumArtist(), song.getAlbumName());

                getMusicFolder(song).ifPresent(folder -> {
                    Album album = albumId3Of(context, folder.getId(), song, registered);

                    Optional.ofNullable(albumDao.updateAlbum(album)).ifPresent(updated -> {
                        indexer.index(updated);
                        mediaFileDao.updateChildrenLastUpdated(album, context.scanDate());
                        updatedCount.increment();
                    });
                });
            }

            candidates = mediaFileDao
                .getChangedId3Albums(ScanConstants.ACQUISITION_MAX, folders, withPodcast);
        }

        return updatedCount.intValue();
    }

    /**
     * Creates new ID3-based Album entries from registered songs that do not yet
     * have a corresponding Album ID3 record.
     * <p>
     * This method does not parse new files from disk. Instead, it scans
     * already-registered {@link MediaFile} entries, and for each that contains
     * album-related ID3 metadata (album name, artist, etc.) but has no
     * corresponding {@link Album} record, it creates and registers a new Album.
     * <p>
     * The resulting Album entries are indexed and have their children's timestamps
     * updated.
     *
     * @param withPodcast whether to include podcast folders in the candidate search
     * @return the number of newly created ID3 albums
     */
    int createAlbumId3s(@NonNull ScanContext context, boolean withPodcast) {
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();
        List<MediaFile> candidates = mediaFileDao
            .getUnregisteredId3Albums(ScanConstants.ACQUISITION_MAX, folders, withPodcast);
        LongAdder createdCount = new LongAdder();

        createAlbums: while (!candidates.isEmpty()) {
            for (int i = 0; i < candidates.size(); i++) {
                if (i % 4_000 == 0) {
                    scanHelper.repeatWait();
                    if (scanHelper.isInterrupted()) {
                        break createAlbums;
                    }
                }

                MediaFile song = candidates.get(i);

                getMusicFolder(song).ifPresent(folder -> {
                    Album album = albumId3Of(context, folder.getId(), song, null);

                    Optional.ofNullable(albumDao.createAlbum(album)).ifPresent(created -> {
                        indexer.index(created);
                        mediaFileDao.updateChildrenLastUpdated(album, context.scanDate());
                        createdCount.increment();
                    });
                });
            }

            candidates = mediaFileDao
                .getUnregisteredId3Albums(ScanConstants.ACQUISITION_MAX, folders, withPodcast);
        }

        return createdCount.intValue();
    }

    /**
     * Constructs or updates an ID3-based Album object from a given song media file.
     *
     * @param folderId   the folder ID to associate with the album
     * @param song       the song media file containing ID3 metadata
     * @param registered the existing Album object to update, or null to create new
     * @return the constructed or updated Album object
     */
    private Album albumId3Of(@NonNull ScanContext context, int folderId, @NonNull MediaFile song,
            @Nullable Album registered) {

        Album album = (registered == null) ? new Album() : registered;

        album.setFolderId(folderId);
        album.setPath(song.getParentPathString());

        album.setName(song.getAlbumName());
        album.setNameReading(song.getAlbumReading());
        album.setNameSort(song.getAlbumSort());

        album.setArtist(song.getAlbumArtist());
        album.setArtistReading(song.getAlbumArtistReading());
        album.setArtistSort(song.getAlbumArtistSort());

        album.setYear(song.getYear());
        album.setGenre(mediaFileService.getID3AlbumGenresString(song)); // VIDEO is not included

        album.setCreated(song.getChanged());
        album.setMusicBrainzReleaseId(song.getMusicBrainzReleaseId());

        mediaFileService
            .getParent(song)
            .ifPresent(parent -> album.setCoverArtPath(parent.getCoverArtPathString()));

        album.setLastScanned(context.scanDate());
        album.setPresent(true);

        return album;
    }

    /**
     * Updates the display order of ID3-based Album entries alphabetically. Skips
     * the process if flagged as unnecessary or if the operation is interrupted.
     *
     * @param skippable Whether the update process can be skipped
     */
    void updateOrderOfAlbumId3(@NonNull ScanContext context, boolean skippable) {
        if (scanHelper.isInterrupted()) {
            return;
        }

        // Skip updating if no meaningful changes are expected
        if (skippable) {
            scanHelper
                .createScanEvent(context, ScanEventType.UPDATE_ORDER_OF_ALBUM_ID3,
                        ScanConstants.MSG_UNNECESSARY);
            return;
        }

        // Retrieve all ID3 albums from all music folders
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();
        List<Album> albums = albumDao
            .getAlphabeticalAlbums(0, Integer.MAX_VALUE, false, true, folders);

        // Apply alphabetical ordering and update album order values
        int count = scanHelper
            .invokeUpdateOrder(albums, comparators.albumOrderByAlpha(),
                    album -> albumDao.updateOrder(album.getId(), album.getOrder()));

        // Create a scan event with the update result
        String comment = "Updated order of (%d) ID3 albums.".formatted(count);
        scanHelper.createScanEvent(context, ScanEventType.UPDATE_ORDER_OF_ALBUM_ID3, comment);
    }

    /**
     * Refreshes ID3-based {@link Artist} records based on existing songs' metadata.
     * <p>
     * This process performs the following steps:
     * <ul>
     * <li>Determines whether podcast folders should be included</li>
     * <li>Marks existing Artist records as scanned and removes outdated ones</li>
     * <li>Updates existing Artist entries based on changed song metadata</li>
     * <li>Creates new Artist entries from previously unregistered songs</li>
     * <li>Creates a {@link ScanEvent} indicating the result</li>
     * </ul>
     *
     * @return {@code true} if any Artist was created or updated; {@code false}
     *         otherwise
     */
    boolean refleshArtistId3(@NonNull ScanContext context) {
        if (scanHelper.isInterrupted()) {
            return false;
        }

        boolean withPodcast = isPodcastInMusicFolders(context);

        iterateArtistId3(context, withPodcast);

        int countUpdate = updateArtistId3s(context, withPodcast);
        int countNew = createArtistId3s(context, withPodcast);

        String comment = "Update(%d)/New(%d)".formatted(countUpdate, countNew);
        scanHelper.createScanEvent(context, ScanEventType.REFRESH_ARTIST_ID3, comment);

        return countUpdate > 0 || countNew > 0;
    }

    /**
     * Iterates and cleans up ID3-based Artist records.
     * <p>
     * This process includes:
     * <ul>
     * <li>Marking artists scanned at the given time</li>
     * <li>Expunging outdated or missing Artist ID3 entries from the index</li>
     * <li>Removing orphaned Artist ID3 records from the database</li>
     * </ul>
     *
     * @param context     The scan context, including scan date and flags.
     * @param withPodcast Whether to include podcast files in the indexing.
     */
    @Transactional
    public void iterateArtistId3(@NonNull ScanContext context, boolean withPodcast) {
        artistDao.iterateLastScanned(context.scanDate(), withPodcast);

        indexer.expungeArtistId3(artistDao.getExpungeCandidates(context.scanDate()));

        artistDao.expunge(context.scanDate());
    }

    /**
     * Updates existing ID3-based Artist records based on registered songs whose
     * metadata has changed.
     * <p>
     * For each changed song, this method attempts to retrieve its parent
     * {@link MusicFolder} and, if found, constructs a new {@link Artist} entity
     * from the song's ID3 metadata. The updated artist is saved and re-indexed if
     * applicable.
     *
     * @param withPodcast whether to include podcast folders in the candidate search
     * @return the number of updated Artist records
     */
    int updateArtistId3s(@NonNull ScanContext context, boolean withPodcast) {
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();
        List<MediaFile> representativeSongs = mediaFileDao
            .getChangedId3Artists(ScanConstants.ACQUISITION_MAX, folders, withPodcast);

        LongAdder countUpdate = new LongAdder();

        updateArtists: while (!representativeSongs.isEmpty()) {
            for (int i = 0; i < representativeSongs.size(); i++) {
                if (i % 15_000 == 0) {
                    scanHelper.repeatWait();
                    if (scanHelper.isInterrupted()) {
                        break updateArtists;
                    }
                }

                MediaFile representativeSong = representativeSongs.get(i);

                getMusicFolder(representativeSong).ifPresent(folder -> {
                    Optional
                        .ofNullable(artistDao
                            .updateArtist(
                                    artistId3Of(context, folder.getId(), representativeSong, null)))
                        .ifPresent(updated -> {
                            indexer.index(updated, folder);
                            countUpdate.increment();
                        });
                });
            }

            representativeSongs = mediaFileDao
                .getChangedId3Artists(ScanConstants.ACQUISITION_MAX, folders, withPodcast);
        }

        return countUpdate.intValue();
    }

    /**
     * Creates new ID3-based {@link Artist} records based on registered songs that
     * are not yet associated with any existing Artist entry.
     * <p>
     * For each eligible song, a new {@link Artist} is created from its ID3 album
     * artist metadata and stored. The artist is also indexed.
     *
     * @param withPodcast whether to include podcast folders in the candidate search
     * @return the number of newly created Artist records
     */
    int createArtistId3s(@NonNull ScanContext context, boolean withPodcast) {
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();
        List<MediaFile> representativeSongs = mediaFileDao
            .getUnregisteredId3Artists(ScanConstants.ACQUISITION_MAX, folders, withPodcast);

        LongAdder countNew = new LongAdder();

        createArtists: while (!representativeSongs.isEmpty()) {
            for (int i = 0; i < representativeSongs.size(); i++) {
                if (i % 15_000 == 0) {
                    scanHelper.repeatWait();
                    if (scanHelper.isInterrupted()) {
                        break createArtists;
                    }
                }

                MediaFile representativeSong = representativeSongs.get(i);

                getMusicFolder(representativeSong).ifPresent(folder -> {
                    Optional
                        .ofNullable(artistDao
                            .createArtist(
                                    artistId3Of(context, folder.getId(), representativeSong, null)))
                        .ifPresent(created -> {
                            indexer.index(created, folder);
                            countNew.increment();
                        });
                });
            }

            representativeSongs = mediaFileDao
                .getUnregisteredId3Artists(ScanConstants.ACQUISITION_MAX, folders, withPodcast);
        }

        return countNew.intValue();
    }

    /**
     * Constructs or updates an {@link Artist} entity from the ID3 metadata of a
     * representative song.
     * <p>
     * The {@code representativeSong} is a registered {@link MediaFile} whose ID3
     * album artist tags are used to populate the {@link Artist} entity.
     *
     * @param folderId           the ID of the folder containing the representative
     *                           song
     * @param representativeSong the song whose ID3 metadata is used to create or
     *                           update the artist
     * @param registered         the existing Artist entity to update, or
     *                           {@code null} to create a new one
     * @return a populated or updated {@link Artist} entity based on the ID3
     *         metadata
     */
    private Artist artistId3Of(@NonNull ScanContext context, int folderId,
            @NonNull MediaFile representativeSong, @Nullable Artist registered) {
        Artist artist = registered == null ? new Artist() : registered;

        artist.setFolderId(folderId);
        artist.setName(representativeSong.getAlbumArtist());
        artist.setReading(representativeSong.getAlbumArtistReading());
        artist.setSort(representativeSong.getAlbumArtistSort());
        artist.setCoverArtPath(representativeSong.getCoverArtPathString());
        artist.setLastScanned(context.scanDate());
        artist.setPresent(true);

        String index = musicIndexProvider.getParser().getIndex(artist).index();
        artist.setMusicIndex(index);

        return artist;
    }

    /**
     * Updates the order of ID3-based Artist entries based on alphabetical sorting.
     * If the process is skippable or interrupted, it exits early.
     *
     * @param skippable Whether this step can be skipped (e.g., no relevant changes
     *                  detected)
     */
    void updateOrderOfArtistId3(@NonNull ScanContext context, boolean skippable) {
        if (scanHelper.isInterrupted()) {
            return;
        }

        // Skip update if marked as unnecessary
        if (skippable) {
            scanHelper
                .createScanEvent(context, ScanEventType.UPDATE_ORDER_OF_ARTIST_ID3,
                        ScanConstants.MSG_UNNECESSARY);
            return;
        }

        // Retrieve ID3 artists in alphabetical order
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();
        List<Artist> artists = artistDao.getAlphabetialArtists(0, Integer.MAX_VALUE, folders);

        // Update order field of each artist based on sorted position
        int count = scanHelper
            .invokeUpdateOrder(artists, comparators.artistOrderByAlpha(),
                    (artist) -> artistDao.updateOrder(artist.getId(), artist.getOrder()));

        // Log scan event with update summary
        String comment = "Updated order of (%d) ID3 artists.".formatted(count);
        scanHelper.createScanEvent(context, ScanEventType.UPDATE_ORDER_OF_ARTIST_ID3, comment);
    }

    /**
     * Updates the album count for each {@link Artist} entity.
     * <p>
     * If the process is marked as skippable or interrupted, the update is skipped
     * and a {@link ScanEvent} is still recorded with a note indicating it was
     * unnecessary. Otherwise, the album count is refreshed for each artist
     * retrieved from the DAO.
     *
     * @param skippable whether the update should be skipped due to no preceding
     *                  changes
     */
    void updateAlbumCounts(@NonNull ScanContext context, boolean skippable) {
        if (scanHelper.isInterrupted()) {
            return;
        }

        if (skippable) {
            scanHelper
                .createScanEvent(context, ScanEventType.UPDATE_ALBUM_COUNTS,
                        ScanConstants.MSG_UNNECESSARY);
            return;
        }

        for (Artist artist : artistDao.getAlbumCounts()) {
            artistDao.updateAlbumCount(artist.getId(), artist.getAlbumCount());
        }

        scanHelper.createScanEvent(context, ScanEventType.UPDATE_ALBUM_COUNTS, null);
    }

    /**
     * Updates the genre master table based on current media file genre usage.
     * <p>
     * If not interrupted, this method:
     * <ul>
     * <li>Retrieves current genre usage counts from media files</li>
     * <li>Updates the genre master records in the DB</li>
     * <li>Removes any obsolete genres from the index</li>
     * <li>Creates a {@link ScanEvent} to log the update</li>
     * </ul>
     *
     */
    void updateGenreMaster(@NonNull ScanContext context) {
        if (scanHelper.isInterrupted()) {
            return;
        }

        List<Genre> genres = mediaFileDao.getGenreCounts();
        mediaFileDao.updateGenres(genres);
        genreMasterProvider.expungeGenreOtherThan(genres);

        scanHelper.createScanEvent(context, ScanEventType.UPDATE_GENRE_MASTER, null);
    }

}

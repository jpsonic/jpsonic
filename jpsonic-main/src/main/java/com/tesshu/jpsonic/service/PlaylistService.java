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

package com.tesshu.jpsonic.service;

import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import chameleon.playlist.SpecificPlaylist;
import chameleon.playlist.SpecificPlaylistFactory;
import chameleon.playlist.SpecificPlaylistProvider;
import com.tesshu.jpsonic.feature.upnp.UPnPSKeys;
import com.tesshu.jpsonic.infrastructure.comparator.ComparatorsFacade;
import com.tesshu.jpsonic.infrastructure.concurrent.ConcurrentUtils;
import com.tesshu.jpsonic.infrastructure.filesystem.PathInspector;
import com.tesshu.jpsonic.infrastructure.filesystem.ScanningExclusionPolicy;
import com.tesshu.jpsonic.infrastructure.language.MetadataReadingProcessor;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.PlayQueue;
import com.tesshu.jpsonic.persistence.api.entity.Playlist;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.api.repository.PlaylistDao;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.service.playlist.PlaylistExportHandler;
import com.tesshu.jpsonic.service.playlist.PlaylistImportHandler;
import org.apache.commons.lang3.tuple.Pair;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Provides services for loading and saving playlists to and from persistent
 * storage.
 *
 * @author Sindre Mehus
 *
 * @see PlayQueue
 */
@Service
public class PlaylistService {

    private static final Logger LOG = LoggerFactory.getLogger(PlaylistService.class);

    private static final String EXPORT_PLAYLIST_FORMAT = "m3u";

    private final MediaFileDao mediaFileDao;
    private final PlaylistDao playlistDao;
    private final UserService userService;
    private final SettingsFacade settingsFacade;
    private final ScanningExclusionPolicy scanningExclusionPolicy;
    private final List<PlaylistExportHandler> exportHandlers;
    private final List<PlaylistImportHandler> importHandlers;
    private final MetadataReadingProcessor readingProcessor;
    private final ComparatorsFacade comparatorsFacade;

    public PlaylistService(MediaFileDao mediaFileDao, PlaylistDao playlistDao,
            UserService userService, SettingsFacade settingsFacade,
            ScanningExclusionPolicy scanningExclusionPolicy,
            List<PlaylistExportHandler> exportHandlers, List<PlaylistImportHandler> importHandlers,
            MetadataReadingProcessor readingProcessor, ComparatorsFacade comparatorsFacade) {
        this.mediaFileDao = mediaFileDao;
        this.playlistDao = playlistDao;
        this.userService = userService;
        this.settingsFacade = settingsFacade;
        this.scanningExclusionPolicy = scanningExclusionPolicy;
        this.exportHandlers = exportHandlers;
        this.importHandlers = importHandlers;
        this.readingProcessor = readingProcessor;
        this.comparatorsFacade = comparatorsFacade;
    }

    public int getCountAll() {
        return playlistDao.getCountAll();
    }

    public List<Playlist> getAllPlaylists() {
        if (settingsFacade.get(UPnPSKeys.options.guestPublish)) {
            return sort(playlistDao.getReadablePlaylistsForUser(User.USERNAME_GUEST));
        }
        return sort(playlistDao.getAllPlaylists());
    }

    public List<Playlist> getReadablePlaylistsForUser(String username) {
        return sort(playlistDao.getReadablePlaylistsForUser(username));
    }

    public List<Playlist> getWritablePlaylistsForUser(String username) {

        // Admin users are allowed to modify all playlists that are visible to them.
        if (userService.isAdmin(username)) {
            return getReadablePlaylistsForUser(username);
        }

        return sort(playlistDao.getWritablePlaylistsForUser(username));
    }

    public @Nullable Playlist getPlaylist(int id) {
        return playlistDao.getPlaylist(id);
    }

    public @NonNull Playlist getPlaylistStrict(int id) {
        Playlist playlist = getPlaylist(id);
        if (playlist == null) {
            throw new IllegalArgumentException("Playlist not found");
        }
        return playlist;
    }

    public List<String> getPlaylistUsers(int playlistId) {
        return playlistDao.getPlaylistUsers(playlistId);
    }

    public List<MediaFile> getFilesInPlaylist(int id) {
        return getFilesInPlaylist(id, false);
    }

    public List<MediaFile> getFilesInPlaylist(int id, long offset, long count) {
        return mediaFileDao.getFilesInPlaylist(id, offset, count);
    }

    public List<MediaFile> getFilesInPlaylist(int id, boolean includeNotPresent) {
        List<MediaFile> files = mediaFileDao.getFilesInPlaylist(id, 0L, Integer.MAX_VALUE);
        if (includeNotPresent) {
            return files;
        }
        List<MediaFile> presentFiles = new ArrayList<>(files.size());
        for (MediaFile file : files) {
            if (file.isPresent()) {
                presentFiles.add(file);
            }
        }
        return presentFiles;
    }

    public void setFilesInPlaylist(int id, List<MediaFile> files) {
        playlistDao.setFilesInPlaylist(id, files);
    }

    public void createPlaylist(Playlist playlist) {
        playlistDao.createPlaylist(playlist);
    }

    public void addPlaylistUser(int playlistId, String username) {
        playlistDao.addPlaylistUser(playlistId, username);
    }

    public void deletePlaylistUser(int playlistId, String username) {
        playlistDao.deletePlaylistUser(playlistId, username);
    }

    public boolean isReadAllowed(Playlist playlist, String username) {
        if (username == null) {
            return false;
        }
        if (username.equals(playlist.getUsername()) || playlist.isShared()) {
            return true;
        }
        return playlistDao.getPlaylistUsers(playlist.getId()).contains(username);
    }

    public boolean isWriteAllowed(Playlist playlist, String username) {
        return username != null && username.equals(playlist.getUsername());
    }

    private List<Playlist> sort(List<Playlist> playlists) {
        return playlists
            .stream()
            .map(readingProcessor::analyzeName)
            .sorted(comparatorsFacade.nameableOrder())
            .map(Pair::getKey)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public void deletePlaylist(int id) {
        playlistDao.deletePlaylist(id);
    }

    public void updatePlaylist(Playlist playlist) {
        playlistDao.updatePlaylist(playlist);
    }

    public Playlist importPlaylist(String username, String playlistName, String fileName,
            InputStream inputStream, Playlist existingPlaylist) throws ExecutionException {

        SpecificPlaylist inputSpecificPlaylist;
        try {
            inputSpecificPlaylist = SpecificPlaylistFactory
                .getInstance()
                .readFrom(inputStream, "UTF-8");
        } catch (IOException e) {
            throw new ExecutionException("Unsupported playlist " + fileName, e);
        }
        PlaylistImportHandler importHandler = getImportHandler(inputSpecificPlaylist);
        if (LOG.isDebugEnabled()) {
            LOG
                .debug("Using " + importHandler.getClass().getSimpleName()
                        + " playlist import handler");
        }

        Pair<List<MediaFile>, List<String>> result = importHandler.handle(inputSpecificPlaylist);

        if (result.getLeft().isEmpty() && !result.getRight().isEmpty()) {
            for (String error : result.getRight()) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("File in playlist '" + fileName + "' not found: " + error);
                }
            }
            throw new ExecutionException(new IOException("No songs in the playlist were found."));
        }

        Instant now = now();
        Playlist playlist;
        if (existingPlaylist == null) {
            playlist = new Playlist();
            playlist.setUsername(username);
            playlist.setCreated(now);
            playlist.setChanged(now);
            playlist.setShared(true);
            playlist.setName(playlistName);
            playlist.setComment("Auto-imported from " + fileName);
            playlist.setImportedFrom(fileName);
            createPlaylist(playlist);
        } else {
            playlist = existingPlaylist;
        }

        setFilesInPlaylist(playlist.getId(), result.getLeft());

        return playlist;
    }

    public String getExportPlaylistExtension() {
        SpecificPlaylistProvider provider = SpecificPlaylistFactory
            .getInstance()
            .findProviderById(EXPORT_PLAYLIST_FORMAT);
        return provider.getContentTypes()[0].getExtensions()[0];
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException") // chameleon/SpecificPlaylist#writeTo
    public void exportPlaylist(int id, OutputStream out) throws ExecutionException {
        SpecificPlaylistProvider provider = SpecificPlaylistFactory
            .getInstance()
            .findProviderById(EXPORT_PLAYLIST_FORMAT);
        PlaylistExportHandler handler = getExportHandler(provider);
        try {
            SpecificPlaylist specificPlaylist = handler.handle(id, provider);
            specificPlaylist.writeTo(out, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new ExecutionException("Unable to write playlist to stream.", e);
        }
    }

    private PlaylistImportHandler getImportHandler(SpecificPlaylist playlist) {
        return importHandlers
            .stream()
            .filter(handler -> handler.canHandle(playlist.getClass()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException(
                    "No import handler for " + playlist.getClass().getName()));

    }

    private PlaylistExportHandler getExportHandler(SpecificPlaylistProvider provider) {
        return exportHandlers
            .stream()
            .filter(handler -> handler.canHandle(provider.getClass()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException(
                    "No export handler for " + provider.getClass().getName()));
    }

    public void importPlaylists() {
        String playlistFolderPath = settingsFacade.get(SKeys.general.extension.playlistFolder);
        if (playlistFolderPath == null) {
            return;
        }
        Path playlistFolder = Path.of(playlistFolderPath);
        if (!Files.exists(playlistFolder)) {
            return;
        }

        List<Playlist> allPlaylists = playlistDao.getAllPlaylists();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(playlistFolder)) {
            for (Path child : ds) {

                if (scanningExclusionPolicy.isExcluded(child)) {
                    continue;
                }

                try {
                    importPlaylistIfUpdated(child, allPlaylists);
                } catch (ExecutionException e) {
                    ConcurrentUtils.handleCauseUnchecked(e);
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Failed to auto-import playlist " + child + ". ", e);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void importPlaylistIfUpdated(Path file, List<Playlist> allPlaylists)
            throws ExecutionException {

        Path fileName = file.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("The file name is invalid: " + file);
        }

        Playlist existingPlaylist = null;
        for (Playlist playlist : allPlaylists) {
            if (fileName.toString().equals(playlist.getImportedFrom())) {
                existingPlaylist = playlist;
                try {
                    if (Files.getLastModifiedTime(file).toMillis() <= playlist
                        .getChanged()
                        .toEpochMilli()) {
                        // Already imported and not changed since.
                        return;
                    }
                } catch (IOException e) {
                    throw new ExecutionException(e);
                }
            }
        }
        try (InputStream in = Files.newInputStream(file)) {
            // With the transition away from a hardcoded admin account to Admin Roles, there
            // is no longer
            // a specific account to use for auto-imported playlists, so use the first admin
            // account
            importPlaylist(userService.getAdminUsername(), PathInspector.getBaseName(fileName),
                    fileName.toString(), in, existingPlaylist);
            if (LOG.isInfoEnabled()) {
                LOG.info("Auto-imported playlist " + file);
            }
        } catch (IOException e) {
            throw new ExecutionException("Unable to read the file: " + file, e);
        }
    }

}

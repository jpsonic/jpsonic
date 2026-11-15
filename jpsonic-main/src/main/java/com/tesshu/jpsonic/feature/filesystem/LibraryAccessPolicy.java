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
 * (C) 2026 tesshucom
 */

package com.tesshu.jpsonic.feature.filesystem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.infrastructure.filesystem.PathInspector;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.service.MusicFolderService;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Component;

/**
 * Core policy engine for enforcing library access rules within the application.
 * <p>
 * This component orchestrates the validation of file-level permissions by
 * reconciling physical path hierarchies (via {@link PathInspector}) with
 * configured library resources (Music and Podcast folders).
 * </p>
 * <p>
 * It serves as the definitive authority for read/write/upload permissions,
 * ensuring that all file operations remain strictly within authorized
 * directories.
 * </p>
 */
@Component
public class LibraryAccessPolicy {

    private final SettingsFacade settingsFacade;
    private final PathInspector pathInspector;
    private final MusicFolderService musicFolderService;
    private final MediaFileProvider mediaFileProvider;

    public LibraryAccessPolicy(SettingsFacade settingsFacade, PathInspector pathInspector,
            MusicFolderService musicFolderService, MediaFileProvider mediaFileProvider) {
        this.settingsFacade = settingsFacade;
        this.pathInspector = pathInspector;
        this.musicFolderService = musicFolderService;
        this.mediaFileProvider = mediaFileProvider;
    }

    /**
     * Returns whether the given file may be read.
     *
     * @return Whether the given file may be read.
     */
    public boolean isReadAllowed(@NonNull Path path) {
        // Allowed to read from both music folder and podcast folder.
        return isInMusicFolder(path.toString()) || isInPodcastFolder(path);
    }

    /**
     * Returns whether the given file may be written, created or deleted.
     *
     * @return Whether the given file may be written, created or deleted.
     */
    public boolean isWriteAllowed(@NonNull Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Illegal path specified: " + path);
        }
        Path fileName = path.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("Illegal path specified: " + path);
        }
        // Only allowed to write podcasts or cover art.
        boolean isPodcast = isInPodcastFolder(path);
        boolean isCoverArt = isInMusicFolder(path.toString())
                && fileName.toString().startsWith("cover.");
        return isPodcast || isCoverArt;
    }

    /**
     * Returns whether the given file may be uploaded.
     *
     * @return Whether the given file may be uploaded.
     */
    public boolean isUploadAllowed(Path path) {
        return isInMusicFolder(path.toString()) && !Files.exists(path);
    }

    /**
     * Returns whether the given file is located in one of the music folders (or any
     * of their sub-folders).
     *
     * @param path The file in question.
     *
     * @return Whether the given file is located in one of the music folders.
     */
    private boolean isInMusicFolder(String path) {
        return getMusicFolderForFile(path) != null;
    }

    private MusicFolder getMusicFolderForFile(String path) {
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders(false, true);
        for (MusicFolder folder : folders) {
            if (pathInspector.isWithinHierarchy(path, folder.getPathString())) {
                return folder;
            }
        }
        return null;
    }

    /**
     * Returns whether the given file is located in the Podcast folder (or any of
     * its sub-folders).
     *
     * @param path The file in question.
     *
     * @return Whether the given file is located in the Podcast folder.
     */
    public boolean isInPodcastFolder(Path path) {
        String podcastFolder = settingsFacade.get(SKeys.podcast.folder);
        return pathInspector.isWithinHierarchy(path.toString(), podcastFolder);
    }

    private boolean isInPodcastFolder(String path) {
        String podcastFolder = settingsFacade.get(SKeys.podcast.folder);
        return pathInspector.isWithinHierarchy(path, podcastFolder);
    }

    public String getRootFolderForFile(String path) {
        MusicFolder folder = getMusicFolderForFile(path);
        if (folder != null) {
            return folder.getPathString();
        }

        if (isInPodcastFolder(path)) {
            return settingsFacade.get(SKeys.podcast.folder);
        }
        return null;
    }

    public String getRootFolderForFile(Path path) {
        MusicFolder folder = getMusicFolderForFile(path.toString());
        if (folder != null) {
            return folder.getPathString();
        }

        if (isInPodcastFolder(path)) {
            return settingsFacade.get(SKeys.podcast.folder);
        }
        return null;
    }

    public boolean isFolderAccessAllowed(@NonNull MediaFile file, String username) {
        if (isInPodcastFolder(file.toPath())) {
            return true;
        }

        for (MusicFolder musicFolder : musicFolderService.getMusicFoldersForUser(username)) {
            if (musicFolder.getPathString().equals(file.getFolder())) {
                return true;
            }
        }
        return false;
    }

    public boolean canAccessMediaFile(String username,
            com.tesshu.jpsonic.domain.model.MediaFile mediaFile) {
        return isInPodcastFolder(mediaFile.toPath())
                || mediaFileProvider.existsAccessibleMediaFile(username, mediaFile);
    }
}

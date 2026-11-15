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

package com.tesshu.jpsonic.ajax;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.domain.language.StringUtil;
import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.infrastructure.concurrent.ConcurrentUtils;
import com.tesshu.jpsonic.infrastructure.filesystem.FileOperations;
import com.tesshu.jpsonic.infrastructure.scanner.WritableMediaFileService;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.service.LastFmService;
import com.tesshu.jpsonic.service.LastFmService.LastFmCoverArt;
import com.tesshu.jpsonic.service.MediaFileService;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Provides AJAX-enabled services for changing cover art images.
 * <p/>
 * This class is used by the DWR framework (http://getahead.ltd.uk/dwr/).
 *
 * @author Sindre Mehus
 */
@Service("ajaxCoverArtService")
public class CoverArtService {

    private static final Logger LOG = LoggerFactory.getLogger(CoverArtService.class);

    private final LibraryAccessPolicy libraryAccessPolicy;
    private final MediaFileService mediaFileService;
    private final WritableMediaFileService writableMediaFileService;
    private final LastFmService lastFmService;

    public CoverArtService(LibraryAccessPolicy libraryAccessPolicy,
            MediaFileService mediaFileService, WritableMediaFileService writableMediaFileService,
            LastFmService lastFmService) {
        super();
        this.libraryAccessPolicy = libraryAccessPolicy;
        this.mediaFileService = mediaFileService;
        this.writableMediaFileService = writableMediaFileService;
        this.lastFmService = lastFmService;
    }

    public List<LastFmCoverArt> searchCoverArt(String artist, String album) {
        return lastFmService.searchCoverArt(artist, album);
    }

    /**
     * Downloads and saves the cover art at the given URL.
     *
     * @param albumId ID of the album in question.
     * @param url     The image URL.
     *
     * @return The error string if something goes wrong, <code>null</code>
     *         otherwise.
     */
    public String saveCoverArtImage(int albumId, String url) {
        try {
            MediaFile mediaFile = mediaFileService.getMediaFileStrict(albumId);
            saveCoverArt(mediaFile.getPathString(), url);
            return null;
        } catch (ExecutionException e) {
            ConcurrentUtils.handleCauseUnchecked(e);
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to save cover art for album " + albumId, e);
            }
            return e.toString();
        }
    }

    private void saveCoverArt(String pathString, String url) throws ExecutionException {

        // Attempt to resolve proper suffix.
        String suffix = getProperSuffix(url);

        // Check permissions.
        Path newCoverFile = Path.of(pathString, "cover." + suffix);
        if (!libraryAccessPolicy.isWriteAllowed(newCoverFile)) {
            throw new ExecutionException(new GeneralSecurityException("Permission denied: "
                    + StringEscapeUtils.escapeHtml4(newCoverFile.toString())));
        }

        // If file exists, create a backup.
        FileOperations.atomicMove(newCoverFile, Path.of(pathString, "cover." + suffix + ".backup"));

        try (CloseableHttpClient client = HttpClients.createDefault()) {

            RequestConfig requestConfig = RequestConfig
                .custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                .setResponseTimeout(Timeout.ofSeconds(2))
                .build();
            HttpGet method = new HttpGet(URI.create(url));
            method.setConfig(requestConfig);

            int copied = client.execute(method, (response) -> {
                try (OutputStream output = Files.newOutputStream(newCoverFile)) {
                    return IOUtils.copy(response.getEntity().getContent(), output);
                }
            });

            if (0 < copied) {
                MediaFile dir = mediaFileService.getMediaFileStrict(pathString);

                // Refresh database.
                writableMediaFileService.refreshCoverArt(dir);
                dir = mediaFileService.getMediaFileStrict(dir.getId());

                // Rename existing cover files if new cover file is not the preferred.
                renameWithoutReplacement(dir, newCoverFile);
            }

        } catch (UnsupportedOperationException | IOException e) {
            throw new ExecutionException("Failed to save coverArt: " + pathString, e);
        }
    }

    void renameWithoutReplacement(MediaFile mediaFile, Path newCoverFile) throws IOException {
        MediaFile dir = mediaFile;
        while (true) {
            Path coverArtPath = mediaFileService.getCoverArt(dir);
            if (coverArtPath == null || isMediaFile(coverArtPath)
                    || newCoverFile.toString().equals(coverArtPath.toString())) {
                break;
            }

            Path oldPath = createOldPath(coverArtPath);
            Path movedPath = Files.move(coverArtPath, oldPath);
            boolean renamed = movedPath.equals(oldPath);
            if (!renamed && LOG.isWarnEnabled()) {
                LOG.warn("Unable to rename old image file " + coverArtPath);
            }
            if (renamed && LOG.isInfoEnabled()) {
                LOG.info("Renamed old image file " + coverArtPath);
            }
            if (renamed) {
                // Must refresh again.
                writableMediaFileService.refreshCoverArt(dir);
                dir = mediaFileService.getMediaFileStrict(dir.getId());
            }
        }
    }

    private Path createOldPath(Path coverArtPath) throws IOException {
        Path parent = coverArtPath.getParent();
        Path fileName = coverArtPath.getFileName();
        if (parent == null || fileName == null) {
            throw new IOException("Illegal cover art path has specified: " + coverArtPath);
        }
        return Path.of(parent.toString(), fileName.normalize().toString() + ".old");
    }

    private String getProperSuffix(String url) {
        String suffix = "jpg";
        if (StringUtil.endsWithIgnoreCase(url, ".gif")) {
            suffix = "gif";
        } else if (StringUtil.endsWithIgnoreCase(url, ".png")) {
            suffix = "png";
        }
        return suffix;
    }

    private boolean isMediaFile(Path path) {
        return mediaFileService.includeMediaFile(path);
    }
}

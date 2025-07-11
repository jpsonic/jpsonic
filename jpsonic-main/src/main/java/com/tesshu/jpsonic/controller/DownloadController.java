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

package com.tesshu.jpsonic.controller;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.tesshu.jpsonic.SuppressLint;
import com.tesshu.jpsonic.domain.MediaFile;
import com.tesshu.jpsonic.domain.PlayQueue;
import com.tesshu.jpsonic.domain.Player;
import com.tesshu.jpsonic.domain.Playlist;
import com.tesshu.jpsonic.domain.TransferStatus;
import com.tesshu.jpsonic.domain.User;
import com.tesshu.jpsonic.io.RangeOutputStream;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.PlaylistService;
import com.tesshu.jpsonic.service.SecurityService;
import com.tesshu.jpsonic.service.SettingsService;
import com.tesshu.jpsonic.service.StatusService;
import com.tesshu.jpsonic.util.FileUtil;
import com.tesshu.jpsonic.util.HttpRange;
import com.tesshu.jpsonic.util.PlayerUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * A controller used for downloading files to a remote client. If the requested
 * path refers to a file, the given file is downloaded. If the requested path
 * refers to a directory, the entire directory (including sub-directories) are
 * downloaded as an uncompressed zip-file.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/download.view")
public class DownloadController {

    private static final Logger LOG = LoggerFactory.getLogger(DownloadController.class);
    private static final int BITRATE_LIMIT_UPDATE_INTERVAL = 5000;

    private final PlayerService playerService;
    private final StatusService statusService;
    private final SecurityService securityService;
    private final PlaylistService playlistService;
    private final SettingsService settingsService;
    private final MediaFileService mediaFileService;

    public DownloadController(PlayerService playerService, StatusService statusService,
            SecurityService securityService, PlaylistService playlistService,
            SettingsService settingsService, MediaFileService mediaFileService) {
        super();
        this.playerService = playerService;
        this.statusService = statusService;
        this.securityService = securityService;
        this.playlistService = playlistService;
        this.settingsService = settingsService;
        this.mediaFileService = mediaFileService;
    }

    /*
     * To be triaged in #1122.
     */
    public long getLastModified(HttpServletRequest request) {
        try {
            MediaFile mediaFile = getMediaFile(request);
            if (mediaFile == null || mediaFile.isDirectory() || mediaFile.getChanged() == null) {
                return -1;
            }
            return mediaFile.getChanged().toEpochMilli();
        } catch (ServletRequestBindingException e) {
            return -1;
        }
    }

    @GetMapping
    public void handleRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletRequestBindingException, IOException {

        User user = securityService.getCurrentUserStrict(request);
        TransferStatus status = null;
        try {

            status = statusService
                .createDownloadStatus(playerService.getPlayer(request, response, false, false));

            MediaFile mediaFile = getMediaFile(request);

            Integer playlistId = ServletRequestUtils
                .getIntParameter(request, Attributes.Request.PLAYLIST.value());
            Integer playerId = ServletRequestUtils
                .getIntParameter(request, Attributes.Request.PLAYER.value());
            int[] indexes = request.getParameter(Attributes.Request.I.value()) == null ? null
                    : ServletRequestUtils.getIntParameters(request, Attributes.Request.I.value());

            if (mediaFile != null) {
                response.setHeader("Accept-Ranges", "bytes");
            }

            HttpRange range = HttpRange.valueOf(request.getHeader("Range"));
            if (range != null) {
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                if (LOG.isInfoEnabled()) {
                    LOG.info("Got HTTP range: " + range);
                }
            }

            if (mediaFile != null) {
                if (!securityService.isFolderAccessAllowed(mediaFile, user.getUsername())) {
                    response
                        .sendError(HttpServletResponse.SC_FORBIDDEN,
                                StringEscapeUtils
                                    .escapeHtml4("Access to file " + mediaFile.getId()
                                            + " is forbidden for user " + user.getUsername()));
                    return;
                }

                doDownload(response, status, mediaFile, range, indexes);

            } else if (playlistId != null) {
                List<MediaFile> songs = playlistService.getFilesInPlaylist(playlistId);
                Playlist playlist = playlistService.getPlaylistStrict(playlistId);
                downloadFiles(response, status, songs, null, null, range,
                        playlist.getName() + ".zip");

            } else if (playerId != null) {
                Player player = playerService.getPlayerById(playerId);
                if (player == null) {
                    throw new IllegalArgumentException("The specified Player cannot be found.");
                }
                PlayQueue playQueue = player.getPlayQueue();
                playQueue.setName("Playlist");
                downloadFiles(response, status, playQueue.getFiles(), indexes, null, range,
                        "download.zip");
            }

        } finally {
            if (status != null) {
                statusService.removeDownloadStatus(status);
                securityService.updateUserByteCounts(user, 0L, status.getBytesTransfered(), 0L);
            }
        }
    }

    private void doDownload(HttpServletResponse response, TransferStatus status,
            MediaFile mediaFile, HttpRange range, int... indexes) throws IOException {
        if (mediaFile.isFile()) {
            downloadFile(response, status, mediaFile.toPath(), range);
        } else {
            List<MediaFile> children = mediaFileService.getChildrenOf(mediaFile, true, false);
            String zipFileName = FilenameUtils.getBaseName(mediaFile.getPathString()) + ".zip";
            Path coverArtPath = indexes == null && mediaFile.getCoverArtPathString() != null
                    ? Path.of(mediaFile.getCoverArtPathString())
                    : null;
            downloadFiles(response, status, children, indexes, coverArtPath, range, zipFileName);
        }
    }

    private MediaFile getMediaFile(HttpServletRequest request)
            throws ServletRequestBindingException {
        Integer id = ServletRequestUtils.getIntParameter(request, Attributes.Request.ID.value());
        return id == null ? null : mediaFileService.getMediaFile(id);
    }

    private void writeLog(String phase, String source, Player player) {
        if (LOG.isInfoEnabled()) {
            LOG.info(phase + " '" + source + "' to " + player);
        }
    }

    /**
     * Downloads a single file.
     *
     * @param response The HTTP response.
     * @param status   The download status.
     * @param path     The file to download.
     * @param range    The byte range, may be <code>null</code>.
     *
     * @throws IOException If an I/O error occurs.
     */
    @SuppressLint(value = "PULSE_RESOURCE_LEAK", justification = "Close of response#outputStream is transferred to the container (Servlet API)")
    private void downloadFile(HttpServletResponse response, TransferStatus status, Path path,
            HttpRange range) throws IOException {
        writeLog("Starting to download", FileUtil.getShortPath(path), status.getPlayer());
        status.setPathString(path.toString());

        response.setContentType("application/x-download");
        Path fileName = path.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("Illegal path specified: " + path);
        }
        response
            .setHeader("Content-Disposition",
                    "attachment; filename*=UTF-8''" + encodeAsRFC5987(fileName.toString()));
        if (range == null) {
            PlayerUtils.setContentLength(response, Files.size(path));
        }

        copyFileToStream(path, RangeOutputStream.wrap(response.getOutputStream(), range), status,
                range);
        writeLog("Downloaded", FileUtil.getShortPath(path), status.getPlayer());
    }

    private String encodeAsRFC5987(String string) {
        byte[] stringAsByteArray = string.getBytes(StandardCharsets.UTF_8);
        char[] digits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E',
                'F' };
        byte[] attrChar = { '!', '#', '$', '&', '+', '-', '.', '^', '_', '`', '|', '~', '0', '1',
                '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I',
                'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
                'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q',
                'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z' };
        StringBuilder sb = new StringBuilder();
        for (byte b : stringAsByteArray) {
            if (Arrays.binarySearch(attrChar, b) >= 0) {
                sb.append((char) b);
            } else {
                sb.append('%').append(digits[0x0f & (b >>> 4)]).append(digits[b & 0x0f]);
            }
        }
        return sb.toString();
    }

    /**
     * Downloads the given files. The files are packed together in an uncompressed
     * zip-file.
     *
     * @param response     The HTTP response.
     * @param status       The download status.
     * @param files        The files to download.
     * @param indexes      Only download songs at these indexes. May be
     *                     <code>null</code>.
     * @param coverArtPath The cover art file to include, may be {@code null}.
     * @param range        The byte range, may be <code>null</code>.
     * @param zipFileName  The name of the resulting zip file. @throws IOException
     *                     If an I/O error occurs.
     */
    @SuppressLint(value = "PULSE_RESOURCE_LEAK", justification = "Close of response#outputStream is transferred to the container (Servlet API)")
    private void downloadFiles(HttpServletResponse response, TransferStatus status,
            List<MediaFile> files, int[] indexes, Path coverArtPath, HttpRange range,
            String zipFileName) throws IOException {
        if (indexes != null && indexes.length == 1) {
            downloadFile(response, status, files.get(indexes[0]).toPath(), range);
            return;
        }

        boolean coverEmbedded = false;

        writeLog("Starting to download", zipFileName, status.getPlayer());
        response.setContentType("application/x-download");
        response
            .setHeader("Content-Disposition",
                    "attachment; filename*=UTF-8''" + encodeAsRFC5987(zipFileName));

        try (ZipOutputStream out = new ZipOutputStream(
                RangeOutputStream.wrap(response.getOutputStream(), range))) {

            out.setMethod(ZipOutputStream.STORED); // No compression.

            Set<MediaFile> filesToDownload = createFilesToDownload(indexes, files);
            for (MediaFile mediaFile : filesToDownload) {
                zip(out, Path.of(mediaFile.getParentPathString()), mediaFile.toPath(), status,
                        range);
                if (coverArtPath != null && Files.exists(coverArtPath)
                        && mediaFile.toPath().toRealPath().equals(coverArtPath.toRealPath())) {
                    coverEmbedded = true;
                }
            }
            if (coverArtPath != null && Files.exists(coverArtPath) && !coverEmbedded) {
                zip(out, coverArtPath.getParent(), coverArtPath, status, range);
            }

        }
        writeLog("Downloaded", zipFileName, status.getPlayer());
    }

    private Set<MediaFile> createFilesToDownload(int[] indexes, List<MediaFile> files) {
        Set<MediaFile> filesToDownload = new HashSet<>();
        if (indexes == null) {
            filesToDownload.addAll(files);
        } else {
            for (int index : indexes) {
                try {
                    filesToDownload.add(files.get(index));
                } catch (IndexOutOfBoundsException e) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Error in parse of filesToDownload#add", e);
                    }
                }
            }
        }
        return filesToDownload;
    }

    /**
     * Utility method for writing the content of a given file to a given output
     * stream.
     *
     * @param path   The file to copy.
     * @param out    The output stream to write to.
     * @param status The download status.
     * @param range  The byte range, may be <code>null</code>.
     *
     * @throws IOException If an I/O error occurs.
     */
    private void copyFileToStream(Path path, OutputStream out, TransferStatus status,
            HttpRange range) throws IOException {
        writeLog("Downloading", FileUtil.getShortPath(path), status.getPlayer());

        final int bufferSize = 16 * 1024; // 16 Kbit

        try (InputStream in = new BufferedInputStream(Files.newInputStream(path), bufferSize)) {
            byte[] buf = new byte[bufferSize];
            long bitrateLimit = 0;
            long lastLimitCheck = 0;

            while (true) {
                long before = Instant.now().toEpochMilli();
                int n = in.read(buf);
                if (n == -1) {
                    break;
                }
                out.write(buf, 0, n);

                // Don't sleep if outside range.
                if (range != null && !range
                    .contains(status.getBytesSkipped() + status.getBytesTransfered())) {
                    status.addBytesSkipped(n);
                    continue;
                }

                status.addBytesTransfered(n);
                long after = Instant.now().toEpochMilli();

                // Calculate bitrate limit every 5 seconds.
                if (after - lastLimitCheck > BITRATE_LIMIT_UPDATE_INTERVAL) {
                    bitrateLimit = 1024L * settingsService.getDownloadBitrateLimit()
                            / Math.max(1, statusService.getAllDownloadStatuses().size());
                    lastLimitCheck = after;
                }

                // Sleep for a while to throttle bitrate.
                try {
                    doSleepIfNecessary(bitrateLimit, bufferSize, after, before);
                } catch (InterruptedException e) {
                    LOG.warn("Failed to sleep.", e);
                }
            }
        } finally {
            out.flush();
        }
    }

    private void doSleepIfNecessary(long bitrateLimit, int bufferSize, long after, long before)
            throws InterruptedException {
        if (bitrateLimit != 0) {
            long sleepTime = 8L * 1000 * bufferSize / bitrateLimit - (after - before);
            if (sleepTime > 0L) {
                Thread.sleep(sleepTime);
            }
        }
    }

    /**
     * Writes a file or a directory structure to a zip output stream. File entries
     * in the zip file are relative to the given root.
     *
     * @param out    The zip output stream.
     * @param root   The root of the directory structure. Used to create path
     *               information in the zip file.
     * @param path   The file or directory to zip.
     * @param status The download status.
     * @param range  The byte range, may be <code>null</code>.
     *
     * @throws IOException If an I/O error occurs.
     */
    private void zip(ZipOutputStream out, Path root, Path path, TransferStatus status,
            HttpRange range) throws IOException {

        // Exclude all hidden files starting with a "."
        Path fileName = path.getFileName();
        if (fileName == null || fileName.toString().charAt(0) == '.') {
            return;
        }

        String zipName = path
            .toRealPath()
            .toString()
            .substring(root.toRealPath().toString().length() + 1);

        if (Files.isRegularFile(path)) {
            status.setPathString(path.toString());

            ZipEntry zipEntry = new ZipEntry(zipName);
            zipEntry.setSize(Files.size(path));
            zipEntry.setCompressedSize(Files.size(path));
            zipEntry.setCrc(computeCrc(path));

            out.putNextEntry(zipEntry);
            copyFileToStream(path, out, status, range);
            out.closeEntry();

        } else {
            ZipEntry zipEntry = new ZipEntry(zipName + '/');
            zipEntry.setSize(0);
            zipEntry.setCompressedSize(0);
            zipEntry.setCrc(0);

            out.putNextEntry(zipEntry);
            out.closeEntry();

            try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
                for (Path childPath : children) {
                    zip(out, root, childPath, status, range);
                }
            }
        }
    }

    /**
     * Computes the CRC checksum for the given file.
     *
     * @param path The file to compute checksum for.
     *
     * @return A CRC32 checksum.
     *
     * @throws IOException If an I/O error occurs.
     */
    private long computeCrc(Path path) throws IOException {
        CRC32 crc = new CRC32();
        try (InputStream in = Files.newInputStream(path)) {

            byte[] buf = new byte[8192];
            int n = in.read(buf);
            while (n != -1) {
                crc.update(buf, 0, n);
                n = in.read(buf);
            }

        }

        return crc.getValue();
    }

}

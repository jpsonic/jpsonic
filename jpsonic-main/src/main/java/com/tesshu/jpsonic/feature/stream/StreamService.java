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
 * (C) 2021 tesshucom
 */

package com.tesshu.jpsonic.feature.stream;

import static org.springframework.web.bind.ServletRequestUtils.getBooleanParameter;
import static org.springframework.web.bind.ServletRequestUtils.getIntParameter;

import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tesshu.jpsonic.SuppressFBWarnings;
import com.tesshu.jpsonic.controller.Attributes;
import com.tesshu.jpsonic.domain.system.PreferredFormatScheme;
import com.tesshu.jpsonic.feature.auth.jwt.JWTAuthenticationToken;
import com.tesshu.jpsonic.infrastructure.scanner.WritableMediaFileService;
import com.tesshu.jpsonic.infrastructure.search.LegacySearch;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.PlayQueue;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.service.AudioScrobblerService;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.PlaylistService;
import com.tesshu.jpsonic.service.StatusService;
import com.tesshu.jpsonic.service.StatusService.TransferStatus;
import com.tesshu.jpsonic.service.TranscodingService;
import com.tesshu.jpsonic.service.TranscodingService.VideoTranscodingSettings;
import com.tesshu.jpsonic.service.UserService;
import com.tesshu.jpsonic.util.PlayerUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.ServletRequestBindingException;

@Service
public class StreamService {

    private static final Logger LOG = LoggerFactory.getLogger(StreamService.class);

    private static final int MAXBITRATE_THRESHOLD_FOR_VIDEO_SIZE_LEVEL1 = 400;
    private static final int MAXBITRATE_THRESHOLD_FOR_VIDEO_SIZE_LEVEL2 = 600;
    private static final int MAXBITRATE_THRESHOLD_FOR_VIDEO_SIZE_LEVEL3 = 1800;

    private final StatusService statusService;
    private final PlaylistService playlistService;
    private final UserService userService;
    private final SettingsFacade settingsFacade;
    private final TranscodingService transcodingService;
    private final AudioScrobblerService audioScrobblerService;
    private final MediaFileService mediaFileService;
    private final WritableMediaFileService writableMediaFileService;
    private final LegacySearch legacySearch;
    // Used to perform transcoding in subthreads (Priority changes)
    private final AsyncTaskExecutor shortExecutor;

    public StreamService(StatusService statusService, PlaylistService playlistService,
            UserService userService, SettingsFacade settingsFacade,
            TranscodingService transcodingService, AudioScrobblerService audioScrobblerService,
            MediaFileService mediaFileService, WritableMediaFileService writableMediaFileService,
            LegacySearch legacySearch,
            @Qualifier("shortExecutor") AsyncTaskExecutor shortExecutor) {
        super();
        this.statusService = statusService;
        this.playlistService = playlistService;
        this.userService = userService;
        this.settingsFacade = settingsFacade;
        this.transcodingService = transcodingService;
        this.audioScrobblerService = audioScrobblerService;
        this.mediaFileService = mediaFileService;
        this.writableMediaFileService = writableMediaFileService;
        this.legacySearch = legacySearch;
        this.shortExecutor = shortExecutor;
    }

    /**
     * create a separate play queue (in order to support multiple parallel Podcast
     * streams).
     */
    public void setUpPlayQueue(HttpServletRequest request, HttpServletResponse response,
            Player player, Integer playlistId) {
        PlayQueue playQueue = new PlayQueue();
        playQueue.addFiles(false, playlistService.getFilesInPlaylist(playlistId));
        player.setPlayQueue(playQueue);
        PlayerUtils.setContentLength(response, playQueue.length());
        if (LOG.isInfoEnabled()) {
            LOG
                .info("{}: Incoming Podcast request for playlist {}", request.getRemoteAddr(),
                        playlistId);
        }
    }

    /**
     * Returns the 'format'. The 'format' here is equivalent to the
     * parameter'format' of stream method on Subsonic API. This 'format' has a
     * significant impact on the transcoding results of subsequent processing.
     * (#1187). On legacy servers, This 'format' is always null for non-REST
     * requests. As a result, this can be rephrased as "some transcoding
     * specifications differ depending on the protocol used". In Jpsonic,
     * PreferredFormatSheme allows to set the default value of 'format' for requests
     * other than Rest (UPnP, Share, Browser etc) to other than null.
     */
    public @Nullable String getFormat(HttpServletRequest request, Player player, Boolean isRest) {
        String format = request.getParameter(Attributes.Request.FORMAT.value());
        PreferredFormatScheme sheme = PreferredFormatScheme
            .of(settingsFacade.get(SKeys.transcoding.preferredFormatShemeName));
        if (sheme == PreferredFormatScheme.ANNOYMOUS
                && JWTAuthenticationToken.USERNAME_ANONYMOUS.equals(player.getUsername())
                || sheme == PreferredFormatScheme.OTHER_THAN_REQUEST
                        && (isRest == null || !isRest)) {
            return StringUtils
                .defaultIfEmpty(format, settingsFacade.get(SKeys.transcoding.preferredFormat));
        }
        return format;
    }

    @SuppressFBWarnings(value = {
            "PT_ABSOLUTE_PATH_TRAVERSAL" }, justification = "Has been verified in subsequent processing.")
    public MediaFile getSingleFile(HttpServletRequest request)
            throws ServletRequestBindingException {
        String path = request.getParameter(Attributes.Request.PATH.value());
        if (path != null) {
            return mediaFileService.getMediaFile(path);
        }
        Integer id = getIntParameter(request, Attributes.Request.ID.value());
        if (id != null) {
            return mediaFileService.getMediaFile(id);
        }
        return null;
    }

    public VideoTranscodingSettings createVideoTranscodingSettings(MediaFile file,
            HttpServletRequest request) throws ServletRequestBindingException {
        final Integer existingWidth = file.getWidth();
        final Integer existingHeight = file.getHeight();
        final Integer maxBitRate = getIntParameter(request,
                Attributes.Request.MAX_BIT_RATE.value());
        int timeOffset = getIntParameter(request, Attributes.Request.TIME_OFFSET.value(), 0);
        Integer ds = file.getDurationSeconds();
        int defaultDuration = ds == null ? Integer.MAX_VALUE : ds - timeOffset;
        int duration = getIntParameter(request, Attributes.Request.DURATION.value(),
                defaultDuration);
        boolean hls = getBooleanParameter(request, Attributes.Request.HLS.value(), false);

        Dimension dim = getRequestedVideoSize(
                request.getParameter(Attributes.Request.SIZE.value()));
        if (dim == null) {
            dim = getSuitableVideoSize(existingWidth, existingHeight, maxBitRate);
        }

        return new VideoTranscodingSettings(dim.width, dim.height, timeOffset, duration, hls);
    }

    protected Dimension getRequestedVideoSize(String sizeSpec) {
        if (sizeSpec == null) {
            return null;
        }

        Pattern pattern = Pattern.compile("^(\\d+)x(\\d+)$");
        Matcher matcher = pattern.matcher(sizeSpec);
        if (matcher.find()) {
            int w = Integer.parseInt(matcher.group(1));
            int h = Integer.parseInt(matcher.group(2));
            if (w >= 0 && h >= 0 && w <= 2000 && h <= 2000) {
                return new Dimension(w, h);
            }
        }
        return null;
    }

    @SuppressWarnings("PMD.UnnecessaryBoxing") // false positive
    protected Dimension getSuitableVideoSize(Integer existingWidth, Integer existingHeight,
            Integer maxBitRate) {
        if (maxBitRate == null) {
            return new Dimension(400, 224);
        }

        int w;
        if (maxBitRate < MAXBITRATE_THRESHOLD_FOR_VIDEO_SIZE_LEVEL1) {
            w = 400;
        } else if (maxBitRate < MAXBITRATE_THRESHOLD_FOR_VIDEO_SIZE_LEVEL2) {
            w = 480;
        } else if (maxBitRate < MAXBITRATE_THRESHOLD_FOR_VIDEO_SIZE_LEVEL3) {
            w = 640;
        } else {
            w = 960;
        }
        int h = even(w * 9 / 16);

        if (existingWidth == null || existingHeight == null) {
            return new Dimension(w, h);
        }

        if (existingWidth < w || existingHeight < h) {
            return new Dimension(even(existingWidth), even(existingHeight));
        }

        double aspectRate = existingWidth.doubleValue() / existingHeight.doubleValue();
        h = (int) Math.round(w / aspectRate);

        return new Dimension(even(w), even(h));
    }

    // Make sure width and height are multiples of two, as some versions of ffmpeg
    // require it.
    private int even(int size) {
        return size + (size % 2);
    }

    public void closeAllStreamFor(Player player, boolean isPodcast, boolean isSingleFile) {
        if (!isPodcast && !isSingleFile) {
            statusService
                .getStreamStatusesForPlayer(player)
                .stream()
                .filter(TransferStatus::isActive)
                .forEach(TransferStatus::terminate);
        }
    }

    public InputStream createInputStream(Player player, TransferStatus status, Integer maxBitRate,
            String format, VideoTranscodingSettings videoTranscodingSettings) {
        return new PlayQueueInputStream(player, status, maxBitRate, format,
                videoTranscodingSettings, transcodingService, audioScrobblerService,
                writableMediaFileService, legacySearch, shortExecutor);
    }

    /**
     * Feed the other end with some dummy data to keep it from reconnecting.
     */
    public void sendDummyDelayed(byte[] buf, OutputStream out) throws IOException {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            LOG.info("Interrupted in sleep.", e);
        }
        sendDummy(buf, out, buf.length);
        out.flush();
    }

    public void sendDummy(byte[] buf, OutputStream out, long len) throws IOException {
        long bytesWritten = 0;
        int n;

        Arrays.fill(buf, (byte) 0xFF);
        while (bytesWritten < len) {
            n = (int) Math.min(buf.length, len - bytesWritten);
            out.write(buf, 0, n);
            bytesWritten += n;
        }
    }

    public void removeStreamStatus(@NonNull User user, @Nullable TransferStatus status) {
        if (status != null) {
            userService.incrementUserByteCounts(user, status.getBytesTransfered(), 0L, 0L);
            statusService.removeStreamStatus(status);
        }
    }
}

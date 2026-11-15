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

import java.awt.Dimension;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.service.JWTSecurityService;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.PlayerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Controller which produces the HLS (Http Live Streaming) playlist.
 *
 * @author Sindre Mehus
 */
@Controller("hlsController")
@RequestMapping({ "/hls/**", "/ext/hls/**" })
public class HLSController {

    private static final int SEGMENT_DURATION = 10;
    private static final Pattern BITRATE_PATTERN = Pattern.compile("(\\d+)(@(\\d+)x(\\d+))?");
    private static final int SINGLE_ELEMENT = 1;

    private final PlayerService playerService;
    private final MediaFileService mediaFileService;
    private final LibraryAccessPolicy libraryAccessPolicy;
    private final JWTSecurityService jwtSecurityService;

    public HLSController(PlayerService playerService, MediaFileService mediaFileService,
            LibraryAccessPolicy libraryAccessPolicy, JWTSecurityService jwtSecurityService) {
        super();
        this.playerService = playerService;
        this.mediaFileService = mediaFileService;
        this.libraryAccessPolicy = libraryAccessPolicy;
        this.jwtSecurityService = jwtSecurityService;
    }

    private void sendError(HttpServletResponse response, int sc, String msg) throws IOException {
        response.sendError(sc, StringEscapeUtils.escapeHtml4(msg));
    }

    @GetMapping
    public void handleRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletRequestBindingException, IOException {

        int id = ServletRequestUtils.getIntParameter(request, Attributes.Request.ID.value(), 0);
        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "Media file not found: " + id);
            return;
        }

        Player player = playerService.getPlayer(request, response);
        String username = player.getUsername();
        if (username != null && !libraryAccessPolicy.isFolderAccessAllowed(mediaFile, username)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Access to file " + mediaFile.getId() + " is forbidden for user " + username);
            return;
        }

        Integer duration = mediaFile.getDurationSeconds();
        if (duration == null || duration == 0) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unknown duration for media file: " + id);
            return;
        }

        response.setContentType("application/vnd.apple.mpegurl");
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        List<Pair<Integer, Dimension>> bitRates = parseBitRates(request);
        try (PrintWriter writer = response.getWriter()) {
            if (bitRates.size() > SINGLE_ELEMENT) {
                generateVariantPlaylist(request, id, player, bitRates, writer);
            } else {
                generateNormalPlaylist(request, id, player,
                        bitRates.size() == SINGLE_ELEMENT ? bitRates.get(0) : null, duration,
                        writer);
            }
        }
    }

    private List<Pair<Integer, Dimension>> parseBitRates(HttpServletRequest request) {
        List<Pair<Integer, Dimension>> result = new ArrayList<>();
        String[] bitRates = request.getParameterValues("bitRate");
        if (bitRates != null) {
            for (String bitRate : bitRates) {
                result.add(parseBitRate(bitRate));
            }
        }
        return result;
    }

    /**
     * Parses a string containing the bitrate and an optional width/height, e.g.,
     * 1200@640x480
     */
    protected Pair<Integer, Dimension> parseBitRate(String bitRate) {

        Matcher matcher = BITRATE_PATTERN.matcher(bitRate);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid bitrate specification: " + bitRate);
        }
        int kbps = Integer.parseInt(matcher.group(1));
        if (matcher.group(3) == null) {
            return Pair.of(kbps, null);
        } else {
            int width = Integer.parseInt(matcher.group(3));
            int height = Integer.parseInt(matcher.group(4));
            return Pair.of(kbps, new Dimension(width, height));
        }
    }

    private void println(PrintWriter writer, String x) {
        writer.println(StringEscapeUtils.escapeHtml4(x));
    }

    private void generateVariantPlaylist(HttpServletRequest request, int id, Player player,
            List<Pair<Integer, Dimension>> bitRates, PrintWriter writer) {
        writer.println("#EXTM3U");
        writer.println("#EXT-X-VERSION:1");
        // writer.println("#EXT-X-TARGETDURATION:" + SEGMENT_DURATION);

        String contextPath = getContextPath(request);
        for (Pair<Integer, Dimension> bitRate : bitRates) {
            Integer kbps = bitRate.getLeft();
            println(writer, "#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=" + kbps * 1000L);
            UriComponentsBuilder url = UriComponentsBuilder
                .fromUriString(contextPath + "ext/hls/hls.m3u8")
                .queryParam(Attributes.Request.ID.value(), id)
                .queryParam(Attributes.Request.PLAYER.value(), player.getId())
                .queryParam(Attributes.Request.BITRATE.value(), kbps);
            jwtSecurityService.addJWTToken(url);
            println(writer, url.toUriString());
            Dimension dimension = bitRate.getRight();
            if (dimension != null) {
                println(writer, "@" + dimension.width + "x" + dimension.height);
            }
            writer.println();
        }
        // writer.println("#EXT-X-ENDLIST");
    }

    private void generateNormalPlaylist(HttpServletRequest request, int id, Player player,
            Pair<Integer, Dimension> bitRate, int totalDuration, PrintWriter writer) {
        writer.println("#EXTM3U");
        writer.println("#EXT-X-VERSION:1");
        writer.println("#EXT-X-TARGETDURATION:" + SEGMENT_DURATION);

        for (int i = 0; i < totalDuration / SEGMENT_DURATION; i++) {
            int offset = i * SEGMENT_DURATION;
            writer.println("#EXTINF:" + SEGMENT_DURATION + ",");
            println(writer,
                    createStreamUrl(request, player, id, offset, SEGMENT_DURATION, bitRate));
        }

        int remainder = totalDuration % SEGMENT_DURATION;
        if (remainder > 0) {
            println(writer, "#EXTINF:" + remainder + ",");
            int offset = totalDuration - remainder;
            println(writer, createStreamUrl(request, player, id, offset, remainder, bitRate));
        }
        writer.println("#EXT-X-ENDLIST");
    }

    private String createStreamUrl(HttpServletRequest request, Player player, int id, int offset,
            int duration, Pair<Integer, Dimension> bitRate) {
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromUriString(getContextPath(request) + "ext/stream/stream.ts");
        builder.queryParam(Attributes.Request.ID.value(), id);
        builder.queryParam(Attributes.Request.HLS.value(), "true");
        builder.queryParam(Attributes.Request.TIME_OFFSET.value(), offset);
        builder.queryParam(Attributes.Request.PLAYER.value(), player.getId());
        builder.queryParam(Attributes.Request.DURATION.value(), duration);
        if (bitRate != null) {
            builder.queryParam(Attributes.Request.MAX_BIT_RATE.value(), bitRate.getLeft());
            Dimension dimension = bitRate.getRight();
            if (dimension != null) {
                builder.queryParam(Attributes.Request.SIZE.value(), dimension.width);
                builder.queryParam(Attributes.Request.X.value(), dimension.height);
            }
        }
        jwtSecurityService.addJWTToken(builder);
        return builder.toUriString();
    }

    private String getContextPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        if (StringUtils.isEmpty(contextPath)) {
            contextPath = "/";
        } else {
            contextPath = new StringBuilder(contextPath).append('/').toString();
        }
        return contextPath;
    }

}

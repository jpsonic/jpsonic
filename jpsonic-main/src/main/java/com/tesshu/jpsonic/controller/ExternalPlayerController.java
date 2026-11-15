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

import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.tesshu.jpsonic.feature.auth.jwt.JWTAuthenticationToken;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.persistence.api.entity.Share;
import com.tesshu.jpsonic.service.JWTSecurityService;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.NetworkUtils;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.ShareService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Controller for the page used to play shared music (Twitter, Facebook etc).
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/ext/share/**")
public class ExternalPlayerController {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalPlayerController.class);
    private static final String MAX_BIT_RATE_VALUE = "1200";
    private static final String MAX_SIZE_VALUE = "500";

    private final MusicFolderService musicFolderService;
    private final PlayerService playerService;
    private final ShareService shareService;
    private final MediaFileService mediaFileService;
    private final JWTSecurityService jwtSecurityService;

    public ExternalPlayerController(MusicFolderService musicFolderService,
            PlayerService playerService, ShareService shareService,
            MediaFileService mediaFileService, JWTSecurityService jwtSecurityService) {
        super();
        this.musicFolderService = musicFolderService;
        this.playerService = playerService;
        this.shareService = shareService;
        this.mediaFileService = mediaFileService;
        this.jwtSecurityService = jwtSecurityService;
    }

    @SuppressWarnings("PMD.NullAssignment") // (share) Intentional allocation to register null
    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String shareName = ControllerUtils.extractMatched(request);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Share name is {}", shareName);
        }

        if (StringUtils.isBlank(shareName)) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Could not find share with shareName " + shareName);
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        Share share = shareService.getShareByName(shareName);

        if (share != null && share.getExpires() != null && share.getExpires().isBefore(now())) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Share " + shareName + " is expired");
            }
            share = null;
        }

        if (share != null) {
            share.setLastVisited(now());
            share.setVisitCount(share.getVisitCount() + 1);
            shareService.updateShare(share);
        }

        Player player = playerService.getGuestPlayer(request);

        return new ModelAndView("externalPlayer", "model",
                LegacyMap.of("share", share, "songs", getSongs(request, share, player)));
    }

    private List<MediaFileWithUrlInfo> getSongs(HttpServletRequest request, Share share,
            Player player) {
        Instant expires = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JWTAuthenticationToken) {
            DecodedJWT token = jwtSecurityService.verify((String) authentication.getCredentials());
            expires = token.getExpiresAt().toInstant();
        }
        Instant finalExpires = expires;

        List<MediaFileWithUrlInfo> result = new ArrayList<>();
        if (share == null) {
            return result;
        }

        List<MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(player.getUsername());

        for (MediaFile file : shareService.getSharedFiles(share.getId(), musicFolders)) {
            if (!file.exists()) {
                continue;
            }

            if (file.isDirectory()) {
                List<MediaFile> childrenOf = mediaFileService.getChildrenOf(file, true, false);
                result
                    .addAll(childrenOf
                        .stream()
                        .map(mf -> addUrlInfo(request, player, mf, finalExpires))
                        .collect(Collectors.toList()));
                continue;
            }

            result.add(addUrlInfo(request, player, file, finalExpires));
        }
        return result;
    }

    public MediaFileWithUrlInfo addUrlInfo(HttpServletRequest request, Player player,
            MediaFile mediaFile, Instant expires) {
        String prefix = "ext";
        String streamUrl = jwtSecurityService
            .addJWTToken(
                    UriComponentsBuilder
                        .fromUriString(NetworkUtils.getBaseUrl(request) + prefix + "/stream")
                        .queryParam(Attributes.Request.ID.value(), mediaFile.getId())
                        .queryParam(Attributes.Request.PLAYER.value(), player.getId())
                        .queryParam(Attributes.Request.MAX_BIT_RATE.value(), MAX_BIT_RATE_VALUE),
                    expires)
            .build()
            .toUriString();

        String coverArtUrl = jwtSecurityService
            .addJWTToken(UriComponentsBuilder
                .fromUriString(NetworkUtils.getBaseUrl(request) + prefix + "/"
                        + ViewName.COVER_ART.value())
                .queryParam(Attributes.Request.ID.value(), mediaFile.getId())
                .queryParam(Attributes.Request.SIZE.value(), MAX_SIZE_VALUE), expires)
            .build()
            .toUriString();
        return new MediaFileWithUrlInfo(mediaFile, coverArtUrl, streamUrl);
    }

    // VO
    public static final class MediaFileWithUrlInfo {

        private final MediaFile file;
        private final String coverArtUrl;
        private final String streamUrl;

        public MediaFileWithUrlInfo(MediaFile file, String coverArtUrl, String streamUrl) {
            this.file = file;
            this.coverArtUrl = coverArtUrl;
            this.streamUrl = streamUrl;
        }

        public String getStreamUrl() {
            return streamUrl;
        }

        public String getCoverArtUrl() {
            return coverArtUrl;
        }

        public int getId() {
            return file.getId();
        }

        public void setId(int id) {
            file.setId(id);
        }

        public String getPathString() {
            return file.getPathString();
        }

        public void setPathString(String path) {
            file.setPathString(path);
        }

        public String getFolder() {
            return file.getFolder();
        }

        public void setFolder(String folder) {
            file.setFolder(folder);
        }

        public boolean exists() {
            return file.exists();
        }

        public MediaFile.MediaType getMediaType() {
            return file.getMediaType();
        }

        public void setMediaType(MediaFile.MediaType mediaType) {
            file.setMediaType(mediaType);
        }

        public boolean isVideo() {
            return file.isVideo();
        }

        public boolean isAudio() {
            return file.isAudio();
        }

        public String getFormat() {
            return file.getFormat();
        }

        public void setFormat(String format) {
            file.setFormat(format);
        }

        public boolean isDirectory() {
            return file.isDirectory();
        }

        public boolean isFile() {
            return file.isFile();
        }

        public boolean isAlbum() {
            return file.isAlbum();
        }

        public String getTitle() {
            return file.getTitle();
        }

        public void setTitle(String title) {
            file.setTitle(title);
        }

        public String getAlbumName() {
            return file.getAlbumName();
        }

        public void setAlbumName(String album) {
            file.setAlbumName(album);
        }

        public String getArtist() {
            return file.getArtist();
        }

        public void setArtist(String artist) {
            file.setArtist(artist);
        }

        public String getAlbumArtist() {
            return file.getAlbumArtist();
        }

        public void setAlbumArtist(String albumArtist) {
            file.setAlbumArtist(albumArtist);
        }

        public String getName() {
            return file.getName();
        }

        public Integer getDiscNumber() {
            return file.getDiscNumber();
        }

        public void setDiscNumber(Integer discNumber) {
            file.setDiscNumber(discNumber);
        }

        public Integer getTrackNumber() {
            return file.getTrackNumber();
        }

        public void setTrackNumber(Integer trackNumber) {
            file.setTrackNumber(trackNumber);
        }

        public Integer getYear() {
            return file.getYear();
        }

        public void setYear(Integer year) {
            file.setYear(year);
        }

        public String getGenre() {
            return file.getGenre();
        }

        public void setGenre(String genre) {
            file.setGenre(genre);
        }

        public Integer getBitRate() {
            return file.getBitRate();
        }

        public void setBitRate(Integer bitRate) {
            file.setBitRate(bitRate);
        }

        public boolean isVariableBitRate() {
            return file.isVariableBitRate();
        }

        public void setVariableBitRate(boolean variableBitRate) {
            file.setVariableBitRate(variableBitRate);
        }

        public Integer getDurationSeconds() {
            return file.getDurationSeconds();
        }

        public void setDurationSeconds(Integer durationSeconds) {
            file.setDurationSeconds(durationSeconds);
        }

        public String getDurationString() {
            return file.getDurationString();
        }

        public Long getFileSize() {
            return file.getFileSize();
        }

        public void setFileSize(Long fileSize) {
            file.setFileSize(fileSize);
        }

        public Integer getWidth() {
            return file.getWidth();
        }

        public void setWidth(Integer width) {
            file.setWidth(width);
        }

        public Integer getHeight() {
            return file.getHeight();
        }

        public void setHeight(Integer height) {
            file.setHeight(height);
        }

        public String getCoverArtPathString() {
            return file.getCoverArtPathString();
        }

        public void setCoverArtPathString(String coverArtPath) {
            file.setCoverArtPathString(coverArtPath);
        }

        public String getParentPathString() {
            return file.getParentPathString();
        }

        public void setParentPathString(String parentPath) {
            file.setParentPathString(parentPath);
        }

        public int getPlayCount() {
            return file.getPlayCount();
        }

        public void setPlayCount(int playCount) {
            file.setPlayCount(playCount);
        }

        public Instant getLastPlayed() {
            return file.getLastPlayed();
        }

        public void setLastPlayed(Instant lastPlayed) {
            file.setLastPlayed(lastPlayed);
        }

        public String getComment() {
            return file.getComment();
        }

        public void setComment(String comment) {
            file.setComment(comment);
        }

        public Instant getCreated() {
            return file.getCreated();
        }

        public void setCreated(Instant created) {
            file.setCreated(created);
        }

        public Instant getChanged() {
            return file.getChanged();
        }

        public void setChanged(Instant changed) {
            file.setChanged(changed);
        }

        public Instant getLastScanned() {
            return file.getLastScanned();
        }

        public void setLastScanned(Instant lastScanned) {
            file.setLastScanned(lastScanned);
        }

        public Instant getStarredDate() {
            return file.getStarredDate();
        }

        public void setStarredDate(Instant starredDate) {
            file.setStarredDate(starredDate);
        }

        public Instant getChildrenLastUpdated() {
            return file.getChildrenLastUpdated();
        }

        public void setChildrenLastUpdated(Instant childrenLastUpdated) {
            file.setChildrenLastUpdated(childrenLastUpdated);
        }

        public boolean isPresent() {
            return file.isPresent();
        }

        public void setPresent(boolean present) {
            file.setPresent(present);
        }

        public int getVersion() {
            return file.getVersion();
        }

        public static List<Integer> toIdList(List<MediaFile> from) {
            return MediaFile.toIdList(from);
        }

        public static Function<MediaFile, Integer> toId() {
            return MediaFile.toId();
        }
    }
}

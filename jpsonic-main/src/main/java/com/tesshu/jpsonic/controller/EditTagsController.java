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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.filesystem.PathInspector;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.UserService;
import com.tesshu.jpsonic.service.metadata.MetaDataParser;
import com.tesshu.jpsonic.service.metadata.MetaDataParserFactory;
import com.tesshu.jpsonic.service.metadata.ParserUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for the page used to edit MP3 tags.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/editTags", "/editTags.view" })
public class EditTagsController {

    private final LibraryAccessPolicy libraryAccessPolicy;
    private final UserService userService;
    private final MetaDataParserFactory metaDataParserFactory;
    private final MediaFileService mediaFileService;
    private final ScannerStateProvider scannerStateProvider;

    public EditTagsController(LibraryAccessPolicy libraryAccessPolicy, UserService userService,
            MetaDataParserFactory metaDataParserFactory, MediaFileService mediaFileService,
            ScannerStateProvider scannerStateProvider) {
        super();
        this.libraryAccessPolicy = libraryAccessPolicy;
        this.userService = userService;
        this.metaDataParserFactory = metaDataParserFactory;
        this.mediaFileService = mediaFileService;
        this.scannerStateProvider = scannerStateProvider;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request)
            throws ServletRequestBindingException {

        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        MediaFile dir = mediaFileService.getMediaFileStrict(id);
        List<MediaFile> files = mediaFileService.getChildrenOf(dir, true, false);

        Map<String, Object> map = LegacyMap.of();
        if (!files.isEmpty()) {
            map.put("defaultArtist", files.get(0).getArtist());
            map.put("defaultAlbum", files.get(0).getAlbumName());
            map.put("defaultYear", files.get(0).getYear());
            map.put("defaultGenre", files.get(0).getGenre());
        }
        map.put("allGenres", ParserUtils.getID3V1Genres());
        map.put("scanning", scannerStateProvider.isScanning());

        List<ParsedSong> parsedSongs = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            parsedSongs.add(createParsedSong(files.get(i), i));
        }
        map.put("id", id);
        map.put("songs", parsedSongs);
        map.put("ancestors", getAncestors(dir));

        String username = userService.getCurrentUsernameStrict(request);
        UserSettings userSettings = userService.getUserSettings(username);
        map.put("breadcrumbIndex", userSettings.isBreadcrumbIndex());
        map.put("dir", dir);
        map.put("selectedMusicFolder", userService.getSelectedMusicFolder(username));

        return new ModelAndView("editTags", "model", map);
    }

    private List<MediaFile> getAncestors(MediaFile dir) {
        List<MediaFile> result = new ArrayList<>();
        if (libraryAccessPolicy.isInPodcastFolder(dir.toPath())) {
            // For podcasts, don't use ancestors
            return result;
        }

        MediaFile parent = mediaFileService.getParentOf(dir);
        while (parent != null && !mediaFileService.isRoot(parent)) {
            result.add(parent);
            parent = mediaFileService.getParentOf(parent);
        }
        result.add(dir);
        return result;
    }

    private ParsedSong createParsedSong(MediaFile file, int index) {
        ParsedSong parsedSong = new ParsedSong();
        parsedSong.setId(file.getId());
        parsedSong.setFileName(PathInspector.getBaseName(file.getPathString()));
        parsedSong.setTrack(file.getTrackNumber());
        parsedSong.setSuggestedTrack(index + 1);
        parsedSong.setTitle(file.getTitle());
        Path path = file.toPath();
        MetaDataParser parser = metaDataParserFactory.getParser(path);
        if (parser != null) {
            parsedSong.setSuggestedTitle(parser.guessTitle(path));
        }
        parsedSong.setArtist(file.getArtist());
        parsedSong.setAlbum(file.getAlbumName());
        parsedSong.setYear(file.getYear());
        parsedSong.setGenre(file.getGenre());
        return parsedSong;
    }

    /**
     * Contains information about a single song.
     */
    public static class ParsedSong {
        private int id;
        private String fileName;
        private Integer suggestedTrack;
        private Integer track;
        private String suggestedTitle;
        private String title;
        private String artist;
        private String album;
        private Integer year;
        private String genre;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public Integer getSuggestedTrack() {
            return suggestedTrack;
        }

        public void setSuggestedTrack(Integer suggestedTrack) {
            this.suggestedTrack = suggestedTrack;
        }

        public Integer getTrack() {
            return track;
        }

        public void setTrack(Integer track) {
            this.track = track;
        }

        public String getSuggestedTitle() {
            return suggestedTitle;
        }

        public void setSuggestedTitle(String suggestedTitle) {
            this.suggestedTitle = suggestedTitle;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getArtist() {
            return artist;
        }

        public void setArtist(String artist) {
            this.artist = artist;
        }

        public String getAlbum() {
            return album;
        }

        public void setAlbum(String album) {
            this.album = album;
        }

        public Integer getYear() {
            return year;
        }

        public void setYear(Integer year) {
            this.year = year;
        }

        public String getGenre() {
            return genre;
        }

        public void setGenre(String genre) {
            this.genre = genre;
        }
    }
}

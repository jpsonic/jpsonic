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

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletionException;

import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.infrastructure.filesystem.PathInspector;
import com.tesshu.jpsonic.infrastructure.scanner.WritableMediaFileService;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.metadata.MetaData;
import com.tesshu.jpsonic.service.metadata.MetaDataParser;
import com.tesshu.jpsonic.service.metadata.MetaDataParserFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Provides AJAX-enabled services for editing tags in music files. This class is
 * used by the DWR framework (http://getahead.ltd.uk/dwr/).
 *
 * @author Sindre Mehus
 */
@Service("ajaxTagService")
public class TagService {

    private static final Logger LOG = LoggerFactory.getLogger(TagService.class);

    private final MetaDataParserFactory metaDataParserFactory;
    private final MediaFileService mediaFileService;
    private final WritableMediaFileService writableMediaFileService;
    private final ScannerStateProvider scannerStateProvider;

    public TagService(MetaDataParserFactory metaDataParserFactory,
            MediaFileService mediaFileService, WritableMediaFileService writableMediaFileService,
            ScannerStateProvider scannerStateProvider) {
        super();
        this.metaDataParserFactory = metaDataParserFactory;
        this.mediaFileService = mediaFileService;
        this.writableMediaFileService = writableMediaFileService;
        this.scannerStateProvider = scannerStateProvider;
    }

    boolean equals(String a, String b) {
        return Objects.equals(a, b);
    }

    /**
     * Updated tags for a given music file.
     *
     * @param id        The ID of the music file.
     * @param trackStr  The track number.
     * @param artistStr The artist name.
     * @param albumStr  The album name.
     * @param titleStr  The song title.
     * @param yearStr   The release year.
     * @param genreStr  The musical genre.
     *
     * @return "UPDATED" if the new tags were updated, "SKIPPED" if no update was
     *         necessary. Otherwise the error message is returned.
     */
    @SuppressWarnings("PMD.UseObjectForClearerAPI") // Because it's ajax API
    public String updateTags(int id, String trackStr, String artistStr, String albumStr,
            String titleStr, String yearStr, String genreStr) {

        MediaFile mediaFile = mediaFileService.getMediaFileStrict(id);
        if (mediaFile == null || scannerStateProvider.isScanning()) {
            return "SKIPPED";
        }

        Path path = mediaFile.toPath();
        MetaDataParser parser = metaDataParserFactory.getParser(path);
        if (parser == null || !parser.isEditingSupported(path)) {
            return "Tag editing of " + PathInspector.getExtension(mediaFile.getPathString())
                    + " files is not supported.";
        }

        String artist = StringUtils.trimToNull(artistStr);
        String album = StringUtils.trimToNull(albumStr);
        String title = StringUtils.trimToNull(titleStr);
        String genre = StringUtils.trimToNull(genreStr);
        String track = StringUtils.trimToNull(trackStr);
        Integer trackNumber = getTrackNumber(track);
        String year = StringUtils.trimToNull(yearStr);
        Integer yearNumber = getYearNumber(year);
        if (equals(artist, mediaFile.getArtist()) && equals(album, mediaFile.getAlbumName())
                && equals(title, mediaFile.getTitle())
                && Objects.equals(yearNumber, mediaFile.getYear())
                && equals(genre, mediaFile.getGenre())
                && Objects.equals(trackNumber, mediaFile.getTrackNumber())) {
            return "SKIPPED";
        }

        MetaData newMetaData = parser.getMetaData(path);

        // Note: album artist is intentionally set, as it is not user-changeable.
        newMetaData.setArtist(artist);
        newMetaData.setAlbumName(album);
        newMetaData.setTitle(title);
        newMetaData.setYear(yearNumber);
        newMetaData.setGenre(genre);
        newMetaData.setTrackNumber(trackNumber);

        try {
            parser.setMetaData(mediaFile, newMetaData);
        } catch (CompletionException | IllegalArgumentException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to update tags for " + id, e);
            }
            return e.getMessage();
        }
        writableMediaFileService.updateTags(mediaFile);
        return "UPDATED";
    }

    private Integer getTrackNumber(String track) {
        Integer trackNumber = null;
        if (track != null) {
            try {
                trackNumber = Integer.valueOf(track);
            } catch (NumberFormatException x) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Illegal track number: " + track, x);
                }
            }
        }
        return trackNumber;
    }

    private Integer getYearNumber(String year) {
        Integer yearNumber = null;
        if (year != null) {
            try {
                yearNumber = Integer.valueOf(year);
            } catch (NumberFormatException x) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Illegal year: " + year, x);
                }
            }
        }
        return yearNumber;
    }
}

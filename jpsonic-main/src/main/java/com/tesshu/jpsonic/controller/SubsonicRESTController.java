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

import static com.tesshu.jpsonic.feature.auth.rest.RESTRequestParameterProcessingFilter.decrypt;
import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import javax.xml.datatype.XMLGregorianCalendar;

import com.tesshu.jpsonic.SuppressFBWarnings;
import com.tesshu.jpsonic.SuppressLint;
import com.tesshu.jpsonic.ajax.LyricsInfo;
import com.tesshu.jpsonic.ajax.LyricsService;
import com.tesshu.jpsonic.controller.Attributes.Request;
import com.tesshu.jpsonic.controller.form.UserSettingsCommand;
import com.tesshu.jpsonic.domain.model.Artist;
import com.tesshu.jpsonic.domain.model.Genre;
import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.model.MusicFolderContent;
import com.tesshu.jpsonic.domain.model.MusicIndex;
import com.tesshu.jpsonic.domain.model.TranscodingDefinition.BitRateLimit;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterProvider;
import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicIndexProvider;
import com.tesshu.jpsonic.domain.provider.resource.ServerLocaleProvider;
import com.tesshu.jpsonic.domain.type.CoverArtType;
import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.feature.i18n.AirsonicLocaleResolver;
import com.tesshu.jpsonic.feature.stream.DownloadController;
import com.tesshu.jpsonic.feature.stream.StreamController;
import com.tesshu.jpsonic.infrastructure.filesystem.MediaTypeDetector;
import com.tesshu.jpsonic.infrastructure.scanner.HttpSearchCriteriaDirector;
import com.tesshu.jpsonic.infrastructure.scanner.IndexType;
import com.tesshu.jpsonic.infrastructure.scanner.WritableMediaFileService;
import com.tesshu.jpsonic.infrastructure.search.LegacySearch;
import com.tesshu.jpsonic.infrastructure.search.criteria.HttpSearchCriteria;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.AlbumNotes;
import com.tesshu.jpsonic.persistence.api.entity.ArtistBio;
import com.tesshu.jpsonic.persistence.api.entity.Bookmark;
import com.tesshu.jpsonic.persistence.api.entity.InternetRadio;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.persistence.api.repository.AlbumDao;
import com.tesshu.jpsonic.persistence.api.repository.ArtistDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.api.repository.PlayQueueDao;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.persistence.param.ShuffleSelectionParam;
import com.tesshu.jpsonic.persistence.result.SavedPlayQueue;
import com.tesshu.jpsonic.service.AudioScrobblerService;
import com.tesshu.jpsonic.service.BookmarkService;
import com.tesshu.jpsonic.service.InternetRadioService;
import com.tesshu.jpsonic.service.LastFmService;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.MediaScannerService;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.PlaylistService;
import com.tesshu.jpsonic.service.PodcastService;
import com.tesshu.jpsonic.service.RatingService;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.StatusService;
import com.tesshu.jpsonic.service.StatusService.PlayStatus;
import com.tesshu.jpsonic.service.TranscodingService;
import com.tesshu.jpsonic.service.UserService;
import com.tesshu.jpsonic.util.PlayerUtils;
import com.tesshu.jpsonic.util.StringUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.AlbumID3;
import org.subsonic.restapi.AlbumInfo;
import org.subsonic.restapi.AlbumList;
import org.subsonic.restapi.AlbumList2;
import org.subsonic.restapi.AlbumWithSongsID3;
import org.subsonic.restapi.ArtistID3;
import org.subsonic.restapi.ArtistInfo;
import org.subsonic.restapi.ArtistInfo2;
import org.subsonic.restapi.ArtistWithAlbumsID3;
import org.subsonic.restapi.ArtistsID3;
import org.subsonic.restapi.Bookmarks;
import org.subsonic.restapi.Child;
import org.subsonic.restapi.Directory;
import org.subsonic.restapi.Genres;
import org.subsonic.restapi.Index;
import org.subsonic.restapi.IndexID3;
import org.subsonic.restapi.Indexes;
import org.subsonic.restapi.InternetRadioStation;
import org.subsonic.restapi.InternetRadioStations;
import org.subsonic.restapi.License;
import org.subsonic.restapi.Lyrics;
import org.subsonic.restapi.MediaType;
import org.subsonic.restapi.MusicFolders;
import org.subsonic.restapi.NewestPodcasts;
import org.subsonic.restapi.NowPlaying;
import org.subsonic.restapi.NowPlayingEntry;
import org.subsonic.restapi.PlaylistWithSongs;
import org.subsonic.restapi.Playlists;
import org.subsonic.restapi.PodcastStatus;
import org.subsonic.restapi.Podcasts;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.ScanStatus;
import org.subsonic.restapi.SearchResult2;
import org.subsonic.restapi.SearchResult3;
import org.subsonic.restapi.Shares;
import org.subsonic.restapi.SimilarSongs;
import org.subsonic.restapi.SimilarSongs2;
import org.subsonic.restapi.Songs;
import org.subsonic.restapi.Starred;
import org.subsonic.restapi.Starred2;
import org.subsonic.restapi.TopSongs;
import org.subsonic.restapi.Users;
import org.subsonic.restapi.Videos;

/**
 * Multi-controller used for the REST API.
 * <p/>
 * For documentation, please refer to api.jsp.
 * <p/>
 * Note: Exceptions thrown from the methods are intercepted by RESTFilter.
 *
 * @author Sindre Mehus
 */
@SuppressWarnings({ "PMD.AvoidInstantiatingObjectsInLoops", "PMD.LinguisticNaming" })
/*
 * [AvoidInstantiatingObjectsInLoops] There are 21 loop instantiatings, but none
 * of them can be reused. This class has many loop instances because it is
 * responsible for conversion objects. [LinguisticNaming] This is a naming
 * convention specific to this REST class that writes directly to the HTTP
 * response without returning a return value.
 */
@SuppressFBWarnings(value = "SPRING_CSRF_UNRESTRICTED_REQUEST_MAPPING", justification = "Difficult to change as it affects existing apps(Protected by a token).")
@Controller
@RequestMapping(value = "/rest", method = { RequestMethod.GET, RequestMethod.POST })
public class SubsonicRESTController {

    private static final Logger LOG = LoggerFactory.getLogger(SubsonicRESTController.class);
    private static final String NOT_YET_IMPLEMENTED = "Not yet implemented";
    private static final String NO_LONGER_SUPPORTED = "No longer supported";
    private static final String MSG_PLAYLIST_NOT_FOUND = "Playlist not found: ";
    private static final String MSG_PLAYLIST_DENIED = "Permission denied for playlist: ";
    private static final String MSG_PODCAST_NOT_AUTHORIZED = " is not authorized to administrate podcasts.";
    private static final String MSG_SCANNING_NOW = "The feature cannot be used being scanning.";
    private static final String MSG_NO_USER = "No such user: ";

    private static final long LIMIT_OF_HISTORY_TO_BE_PRESENTED = 60;

    private final SettingsFacade settingsFacade;
    private final ServerLocaleProvider serverLocaleProvider;
    private final MusicFolderService musicFolderService;
    private final LibraryAccessPolicy libraryAccessPolicy;
    private final UserService userService;
    private final PlayerService playerService;
    private final MediaFileService mediaFileService;
    private final MediaFileProvider mediaFileProvider;
    private final WritableMediaFileService writableMediaFileService;
    private final LastFmService lastFmService;
    private final MusicFolderProvider musicFolderProvider;
    private final MusicIndexProvider musicIndexProvider;
    private final TranscodingService transcodingService;
    private final DownloadController downloadController;
    private final CoverArtController coverArtController;
    private final AvatarController avatarController;
    private final UserSettingsController userSettingsController;
    private final TopController topController;
    private final StatusService statusService;
    private final StreamController streamController;
    private final HLSController hlsController;
    private final ShareService shareService;
    private final PlaylistService playlistService;
    private final LyricsService lyricsService;
    private final AudioScrobblerService audioScrobblerService;
    private final PodcastService podcastService;
    private final RatingService ratingService;
    private final LegacySearch legacySearch;
    private final GenreMasterProvider genreMasterProvider;
    private final InternetRadioService internetRadioService;
    private final MediaFileDao mediaFileDao;
    private final ArtistDao artistDao;
    private final AlbumDao albumDao;
    private final BookmarkService bookmarkService;
    private final PlayQueueDao playQueueDao;
    private final MediaScannerService mediaScannerService;
    private final AirsonicLocaleResolver airsonicLocaleResolver;
    private final HttpSearchCriteriaDirector director;

    private final JAXBWriter jaxbWriter;

    public SubsonicRESTController(SettingsFacade settingsFacade,
            ServerLocaleProvider serverLocaleProvider, MusicFolderService musicFolderService,
            LibraryAccessPolicy libraryAccessPolicy, UserService userService,
            PlayerService playerService, MediaFileService mediaFileService,
            MediaFileProvider mediaFileProvider, WritableMediaFileService writableMediaFileService,
            LastFmService lastFmService, MusicFolderProvider musicFolderProvider,
            MusicIndexProvider musicIndexProvider, TranscodingService transcodingService,
            DownloadController downloadController, CoverArtController coverArtController,
            AvatarController avatarController, UserSettingsController userSettingsController,
            TopController topController, StatusService statusService,
            StreamController streamController, HLSController hlsController,
            ShareService shareService, PlaylistService playlistService, LyricsService lyricsService,
            AudioScrobblerService audioScrobblerService, PodcastService podcastService,
            RatingService ratingService, LegacySearch legacySearch,
            GenreMasterProvider genreMasterProvider, InternetRadioService internetRadioService,
            MediaFileDao mediaFileDao, ArtistDao artistDao, AlbumDao albumDao,
            BookmarkService bookmarkService, PlayQueueDao playQueueDao,
            MediaScannerService mediaScannerService, AirsonicLocaleResolver airsonicLocaleResolver,
            HttpSearchCriteriaDirector director) {
        super();
        this.settingsFacade = settingsFacade;
        this.serverLocaleProvider = serverLocaleProvider;
        this.musicFolderService = musicFolderService;
        this.libraryAccessPolicy = libraryAccessPolicy;
        this.userService = userService;
        this.playerService = playerService;
        this.mediaFileService = mediaFileService;
        this.mediaFileProvider = mediaFileProvider;
        this.writableMediaFileService = writableMediaFileService;
        this.lastFmService = lastFmService;
        this.musicFolderProvider = musicFolderProvider;
        this.musicIndexProvider = musicIndexProvider;
        this.transcodingService = transcodingService;
        this.downloadController = downloadController;
        this.coverArtController = coverArtController;
        this.avatarController = avatarController;
        this.userSettingsController = userSettingsController;
        this.topController = topController;
        this.statusService = statusService;
        this.streamController = streamController;
        this.hlsController = hlsController;
        this.shareService = shareService;
        this.playlistService = playlistService;
        this.lyricsService = lyricsService;
        this.audioScrobblerService = audioScrobblerService;
        this.podcastService = podcastService;
        this.ratingService = ratingService;
        this.legacySearch = legacySearch;
        this.genreMasterProvider = genreMasterProvider;
        this.internetRadioService = internetRadioService;
        this.mediaFileDao = mediaFileDao;
        this.artistDao = artistDao;
        this.albumDao = albumDao;
        this.bookmarkService = bookmarkService;
        this.playQueueDao = playQueueDao;
        this.mediaScannerService = mediaScannerService;
        this.airsonicLocaleResolver = airsonicLocaleResolver;
        this.director = director;
        jaxbWriter = new JAXBWriter(settingsFacade);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void handleMissingRequestParam(HttpServletRequest request, HttpServletResponse response,
            MissingServletRequestParameterException exception) {
        writeError(request, response, ErrorCode.MISSING_PARAMETER,
                "Required param (" + exception.getParameterName() + ") is missing");
    }

    @RequestMapping({ "/ping", "/ping.view" })
    public void ping(HttpServletRequest request, HttpServletResponse response) {
        Response res = createResponse();
        jaxbWriter.writeResponse(request, response, res);
    }

    /**
     * CAUTION : this method is required by mobile applications and must not be
     * removed.
     */
    @RequestMapping({ "/getLicense", "/getLicense.view" })
    public void getLicense(HttpServletRequest req, HttpServletResponse response) {
        License license = new License();
        license.setEmail("webmaster@tesshu.com");
        license.setValid(true);

        XMLGregorianCalendar farFuture = jaxbWriter
            .convertDate(ZonedDateTime.now().plusYears(100).toInstant());
        license.setLicenseExpires(farFuture);
        license.setTrialExpires(farFuture);

        Response res = createResponse();
        res.setLicense(license);
        HttpServletRequest request = wrapRequest(req);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getMusicFolders", "/getMusicFolders.view" })
    public void getMusicFolders(HttpServletRequest req, HttpServletResponse response) {
        HttpServletRequest request = wrapRequest(req);

        MusicFolders musicFolders = new MusicFolders();
        User user = userService.getCurrentUserStrict(request);
        for (com.tesshu.jpsonic.persistence.api.entity.MusicFolder musicFolder : musicFolderService
            .getMusicFoldersForUser(user.getUsername())) {
            org.subsonic.restapi.MusicFolder mf = new org.subsonic.restapi.MusicFolder();
            mf.setId(musicFolder.getId());
            mf.setName(musicFolder.getName());
            musicFolders.getMusicFolder().add(mf);
        }
        Response res = createResponse();
        res.setMusicFolders(musicFolders);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getIndexes", "/getIndexes.view" })
    public void getIndexes(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {

        HttpServletRequest request = wrapRequest(req);
        Response res = createResponse();
        long ifModifiedSince = ServletRequestUtils
            .getLongParameter(request, Attributes.Request.IF_MODIFIED_SINCE.value(), 0L);
        long lastModified = topController.getLastModified(request);
        if (lastModified <= ifModifiedSince) {
            jaxbWriter.writeResponse(request, response, res);
            return;
        }

        String username = userService.getCurrentUserStrict(request).getUsername();
        Indexes indexes = new Indexes();
        indexes.setLastModified(lastModified);
        indexes.setIgnoredArticles(settingsFacade.get(SKeys.general.index.ignoredArticles));

        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(username);
        Integer musicFolderId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.MUSIC_FOLDER_ID.value());
        if (musicFolderId != null) {
            for (com.tesshu.jpsonic.persistence.api.entity.MusicFolder musicFolder : musicFolders) {
                if (musicFolderId.equals(musicFolder.getId())) {
                    musicFolders = Collections.singletonList(musicFolder);
                    break;
                }
            }
        }

        List<MusicFolder> folders = musicFolders
            .stream()
            .map(com.tesshu.jpsonic.persistence.api.entity.MusicFolder::getId)
            .map(musicFolderProvider::requireMusicFolder)
            .toList();

        for (com.tesshu.jpsonic.domain.model.MediaFile shortcut : mediaFileProvider
            .findShortcuts(folders)) {
            indexes.getShortcut().add(createJaxbArtist(shortcut, username));
        }

        MusicFolderContent musicFolderContent = musicIndexProvider.findMusicFolderContent(folders);

        setRatingAndStarred(musicFolderContent, indexes, username);

        // Add children
        Player player = playerService.getPlayer(request, response);

        for (com.tesshu.jpsonic.domain.model.MediaFile mf : musicFolderContent.singleSongs()) {
            MediaFile singleSong = mediaFileService.getMediaFile(mf.id());
            indexes.getChild().add(createJaxbChild(player, singleSong, username));
        }

        res.setIndexes(indexes);
        jaxbWriter.writeResponse(request, response, res);
    }

    private void setRatingAndStarred(MusicFolderContent musicFolderContent, Indexes indexes,
            String username) {

        for (Map.Entry<MusicIndex, List<com.tesshu.jpsonic.domain.model.MediaFile>> entry : musicFolderContent
            .indexedArtists()
            .entrySet()) {
            Index index = new Index();
            indexes.getIndex().add(index);
            index.setName(entry.getKey().index());
            for (com.tesshu.jpsonic.domain.model.MediaFile mediaFile : entry.getValue()) {
                if (mediaFile.isDirectory()) {
                    org.subsonic.restapi.Artist a = new org.subsonic.restapi.Artist();
                    index.getArtist().add(a);
                    a.setId(String.valueOf(mediaFile.id()));
                    a.setName(mediaFile.name());
                    Instant starredDate = mediaFileDao
                        .getMediaFileStarredDate(mediaFile.id(), username);
                    a.setStarred(jaxbWriter.convertDate(starredDate));
                    if (mediaFile.isAlbum()) {
                        MediaFile album = mediaFileService.getMediaFile(mediaFile.id());
                        a.setAverageRating(ratingService.getAverageRating(album));
                        a.setUserRating(ratingService.getRatingForUser(username, album));
                    }
                }
            }
        }
    }

    @RequestMapping({ "/getGenres", "/getGenres.view" })
    public void getGenres(HttpServletRequest req, HttpServletResponse response) {
        HttpServletRequest request = wrapRequest(req);
        Genres genres = new Genres();

        for (Genre genre : genreMasterProvider.getLegacyGenres(false, 0, Integer.MAX_VALUE)) {
            org.subsonic.restapi.Genre g = new org.subsonic.restapi.Genre();
            genres.getGenre().add(g);
            g.setContent(genre.name());
            g.setAlbumCount(genre.albumCount());
            g.setSongCount(genre.songCount());
        }
        Response res = createResponse();
        res.setGenres(genres);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getSongsByGenre", "/getSongsByGenre.view" })
    public void getSongsByGenre(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        Player player = playerService.getPlayer(request, response);
        User user = userService.getCurrentUserStrict(request);

        Songs songs = new Songs();

        String genre = ServletRequestUtils
            .getRequiredStringParameter(request, Attributes.Request.GENRE.value());
        int offset = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.OFFSET.value(), 0);
        int count = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.COUNT.value(), 10);
        count = Math.max(0, Math.min(count, 500));
        Integer musicFolderId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.MUSIC_FOLDER_ID.value());
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername(), musicFolderId);

        MediaFile.MediaType[] types = { MediaFile.MediaType.MUSIC, MediaFile.MediaType.AUDIOBOOK,
                MediaFile.MediaType.PODCAST };
        for (MediaFile mediaFile : legacySearch
            .getSongsByGenres(genre, offset, count, musicFolders, types)) {
            songs.getSong().add(createJaxbChild(player, mediaFile, user.getUsername()));
        }
        Response res = createResponse();
        res.setSongsByGenre(songs);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getArtists", "/getArtists.view" })
    public void getArtists(HttpServletRequest req, HttpServletResponse response) {
        HttpServletRequest request = wrapRequest(req);
        User user = userService.getCurrentUserStrict(request);

        ArtistsID3 result = new ArtistsID3();
        result.setIgnoredArticles(settingsFacade.get(SKeys.general.index.ignoredArticles));
        List<MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername())
            .stream()
            .map(com.tesshu.jpsonic.persistence.api.entity.MusicFolder::getId)
            .map(musicFolderProvider::requireMusicFolder)
            .toList();

        SortedMap<MusicIndex, List<Artist>> indexedArtists = musicIndexProvider
            .findIndexedId3Artists(musicFolders);
        for (Map.Entry<MusicIndex, List<Artist>> entry : indexedArtists.entrySet()) {
            IndexID3 index = new IndexID3();
            index.setName(entry.getKey().index());
            for (Artist sortableArtist : entry.getValue()) {

                index
                    .getArtist()
                    .add(createJaxbArtist(new ArtistID3(), sortableArtist, user.getUsername()));
            }
            result.getIndex().add(index);
        }

        Response res = createResponse();
        res.setArtists(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getSimilarSongs", "/getSimilarSongs.view" })
    public void getSimilarSongs(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {

        HttpServletRequest request = wrapRequest(req);
        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, "Media file not found.");
            return;
        }

        User user = userService.getCurrentUserStrict(request);
        int count = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.COUNT.value(), 50);
        SimilarSongs result = new SimilarSongs();

        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername());
        List<MediaFile> similarSongs = lastFmService
            .getSimilarSongs(mediaFile, count, musicFolders);
        Player player = playerService.getPlayer(request, response);
        for (MediaFile similarSong : similarSongs) {
            result.getSong().add(createJaxbChild(player, similarSong, user.getUsername()));
        }

        Response res = createResponse();
        res.setSimilarSongs(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getSimilarSongs2", "/getSimilarSongs2.view" })
    public void getSimilarSongs2(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {

        HttpServletRequest request = wrapRequest(req);
        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        com.tesshu.jpsonic.persistence.api.entity.Artist artist = artistDao.getArtist(id);
        if (artist == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, "Artist not found.");
            return;
        }

        User user = userService.getCurrentUserStrict(request);
        int count = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.COUNT.value(), 50);
        SimilarSongs2 result = new SimilarSongs2();
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername());
        List<MediaFile> similarSongs = lastFmService.getSimilarSongs(artist, count, musicFolders);
        Player player = playerService.getPlayer(request, response);
        for (MediaFile similarSong : similarSongs) {
            result.getSong().add(createJaxbChild(player, similarSong, user.getUsername()));
        }

        Response res = createResponse();
        res.setSimilarSongs2(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getTopSongs", "/getTopSongs.view" })
    public void getTopSongs(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        User user = userService.getCurrentUserStrict(request);

        String artist = ServletRequestUtils
            .getRequiredStringParameter(request, Attributes.Request.ARTIST.value());
        int count = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.COUNT.value(), 50);

        TopSongs result = new TopSongs();

        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername());
        List<MediaFile> topSongs = lastFmService.getTopSongs(artist, count, musicFolders);
        Player player = playerService.getPlayer(request, response);
        for (MediaFile topSong : topSongs) {
            result.getSong().add(createJaxbChild(player, topSong, user.getUsername()));
        }

        Response res = createResponse();
        res.setTopSongs(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getArtistInfo", "/getArtistInfo.view" })
    public void getArtistInfo(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, "Media file not found.");
            return;
        }

        int count = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.COUNT.value(), 20);
        boolean includeNotPresent = ServletRequestUtils
            .getBooleanParameter(request, Attributes.Request.INCLUDE_NOT_PRESENT.value(), false);

        ArtistInfo result = new ArtistInfo();

        User user = userService.getCurrentUserStrict(request);
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername());
        List<MediaFile> similarArtists = lastFmService
            .getSimilarArtists(mediaFile, count, includeNotPresent, musicFolders);
        for (MediaFile similarArtist : similarArtists) {
            result.getSimilarArtist().add(createJaxbArtist(similarArtist, user.getUsername()));
        }

        UserSettings userSettings = userService.getUserSettings(user.getUsername());
        Locale locale = userSettings.isForceBio2Eng() ? Locale.ENGLISH
                : airsonicLocaleResolver.resolveLocale(request);
        ArtistBio artistBio = lastFmService.getArtistBio(mediaFile, locale);
        if (artistBio != null) {
            result.setBiography(artistBio.getBiography());
            result.setMusicBrainzId(artistBio.getMusicBrainzId());
            result.setLastFmUrl(artistBio.getLastFmUrl());
            result.setSmallImageUrl(artistBio.getSmallImageUrl());
            result.setMediumImageUrl(artistBio.getMediumImageUrl());
            result.setLargeImageUrl(artistBio.getLargeImageUrl());
        }

        Response res = createResponse();
        res.setArtistInfo(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getArtistInfo2", "/getArtistInfo2.view" })
    public void getArtistInfo2(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {

        HttpServletRequest request = wrapRequest(req);
        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        com.tesshu.jpsonic.persistence.api.entity.Artist artist = artistDao.getArtist(id);
        if (artist == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, "Artist not found.");
            return;
        }

        int count = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.COUNT.value(), 20);
        boolean includeNotPresent = ServletRequestUtils
            .getBooleanParameter(request, Attributes.Request.INCLUDE_NOT_PRESENT.value(), false);
        ArtistInfo2 result = new ArtistInfo2();
        User user = userService.getCurrentUserStrict(request);
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername());
        List<com.tesshu.jpsonic.persistence.api.entity.Artist> similarArtists = lastFmService
            .getSimilarArtists(artist, count, includeNotPresent, musicFolders);
        for (com.tesshu.jpsonic.persistence.api.entity.Artist similarArtist : similarArtists) {
            result
                .getSimilarArtist()
                .add(createJaxbArtist(new ArtistID3(), similarArtist, user.getUsername()));
        }

        UserSettings userSettings = userService.getUserSettings(user.getUsername());
        Locale locale = userSettings.isForceBio2Eng() ? Locale.ENGLISH
                : airsonicLocaleResolver.resolveLocale(request);
        ArtistBio artistBio = lastFmService.getArtistBio(artist, locale);
        if (artistBio != null) {
            result.setBiography(artistBio.getBiography());
            result.setMusicBrainzId(artistBio.getMusicBrainzId());
            result.setLastFmUrl(artistBio.getLastFmUrl());
            result.setSmallImageUrl(artistBio.getSmallImageUrl());
            result.setMediumImageUrl(artistBio.getMediumImageUrl());
            result.setLargeImageUrl(artistBio.getLargeImageUrl());
        }

        Response res = createResponse();
        res.setArtistInfo2(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    private <T extends ArtistID3> T createJaxbArtist(T jaxbArtist, Artist artist, String username) {
        jaxbArtist.setId(String.valueOf(artist.id()));
        jaxbArtist.setName(artist.name());
        jaxbArtist
            .setStarred(jaxbWriter
                .convertDate(mediaFileDao.getMediaFileStarredDate(artist.id(), username)));
        jaxbArtist.setAlbumCount(artist.albumCount());
        if (!artist.thumbUri().isEmpty()) {
            jaxbArtist.setCoverArt(CoverArtType.ARTIST.createKey(artist.id()));
        }
        return jaxbArtist;
    }

    private <T extends ArtistID3> T createJaxbArtist(T jaxbArtist,
            com.tesshu.jpsonic.persistence.api.entity.Artist artist, String username) {
        jaxbArtist.setId(String.valueOf(artist.getId()));
        jaxbArtist.setName(artist.getName());
        jaxbArtist
            .setStarred(jaxbWriter
                .convertDate(mediaFileDao.getMediaFileStarredDate(artist.getId(), username)));
        jaxbArtist.setAlbumCount(artist.getAlbumCount());
        if (artist.getCoverArtPath() != null) {
            jaxbArtist.setCoverArt(CoverArtType.ARTIST.createKey(artist.getId()));
        }
        return jaxbArtist;
    }

    private org.subsonic.restapi.Artist createJaxbArtist(
            com.tesshu.jpsonic.domain.model.MediaFile artist, String username) {
        org.subsonic.restapi.Artist result = new org.subsonic.restapi.Artist();
        result.setId(String.valueOf(artist.id()));
        result.setName(artist.artist());
        Instant starred = mediaFileDao.getMediaFileStarredDate(artist.id(), username);
        result.setStarred(jaxbWriter.convertDate(starred));
        return result;
    }

    private org.subsonic.restapi.Artist createJaxbArtist(MediaFile artist, String username) {
        org.subsonic.restapi.Artist result = new org.subsonic.restapi.Artist();
        result.setId(String.valueOf(artist.getId()));
        result.setName(artist.getArtist());
        Instant starred = mediaFileDao.getMediaFileStarredDate(artist.getId(), username);
        result.setStarred(jaxbWriter.convertDate(starred));
        return result;
    }

    @RequestMapping({ "/getArtist", "/getArtist.view" })
    public void getArtist(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {

        HttpServletRequest request = wrapRequest(req);
        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        com.tesshu.jpsonic.persistence.api.entity.Artist artist = artistDao.getArtist(id);
        if (artist == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, "Artist not found.");
            return;
        }

        User user = userService.getCurrentUserStrict(request);
        String username = user.getUsername();
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(username);
        ArtistWithAlbumsID3 result = createJaxbArtist(new ArtistWithAlbumsID3(), artist, username);
        for (Album album : albumDao
            .getAlbumsForArtist(0L, Integer.MAX_VALUE, artist.getName(), false, musicFolders)) {
            result.getAlbum().add(createJaxbAlbum(new AlbumID3(), album, username));
        }

        Response res = createResponse();
        res.setArtist(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    private <T extends AlbumID3> T createJaxbAlbum(T jaxbAlbum, Album album, String username) {
        jaxbAlbum.setId(String.valueOf(album.getId()));
        jaxbAlbum.setName(album.getName());
        if (album.getArtist() != null) {
            jaxbAlbum.setArtist(album.getArtist());
            com.tesshu.jpsonic.persistence.api.entity.Artist artist = artistDao
                .getArtist(album.getArtist());
            if (artist != null) {
                jaxbAlbum.setArtistId(String.valueOf(artist.getId()));
            }
        }
        if (album.getCoverArtPath() != null) {
            jaxbAlbum.setCoverArt(CoverArtType.ID3ALBUM.createKey(album.getId()));
        }
        jaxbAlbum.setSongCount(album.getSongCount());
        jaxbAlbum.setDuration(album.getDurationSeconds());
        jaxbAlbum.setCreated(jaxbWriter.convertDate(album.getCreated()));
        jaxbAlbum
            .setStarred(
                    jaxbWriter.convertDate(albumDao.getAlbumStarredDate(album.getId(), username)));
        jaxbAlbum.setYear(album.getYear());
        jaxbAlbum.setGenre(album.getGenre());
        return jaxbAlbum;
    }

    private <T extends org.subsonic.restapi.Playlist> T createJaxbPlaylist(T jaxbPlaylist,
            com.tesshu.jpsonic.persistence.api.entity.Playlist playlist) {
        jaxbPlaylist.setId(String.valueOf(playlist.getId()));
        jaxbPlaylist.setName(playlist.getName());
        jaxbPlaylist.setComment(playlist.getComment());
        jaxbPlaylist.setOwner(playlist.getUsername());
        jaxbPlaylist.setPublic(playlist.isShared());
        jaxbPlaylist.setSongCount(playlist.getFileCount());
        jaxbPlaylist.setDuration(playlist.getDurationSeconds());
        jaxbPlaylist.setCreated(jaxbWriter.convertDate(playlist.getCreated()));
        jaxbPlaylist.setChanged(jaxbWriter.convertDate(playlist.getChanged()));
        jaxbPlaylist.setCoverArt(CoverArtType.PLAYLIST.createKey(playlist.getId()));

        for (String username : playlistService.getPlaylistUsers(playlist.getId())) {
            jaxbPlaylist.getAllowedUser().add(username);
        }
        return jaxbPlaylist;
    }

    @RequestMapping({ "/getAlbum", "/getAlbum.view" })
    public void getAlbum(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        Album album = albumDao.getAlbum(id);
        if (album == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, "Album not found.");
            return;
        }

        Player player = playerService.getPlayer(request, response);
        String username = userService.getCurrentUsername(request);
        AlbumWithSongsID3 result = createJaxbAlbum(new AlbumWithSongsID3(), album, username);
        for (MediaFile mediaFile : mediaFileDao
            .getSongsForAlbum(0L, Integer.MAX_VALUE, album.getArtist(), album.getName())) {
            result.getSong().add(createJaxbChild(player, mediaFile, username));
        }

        Response res = createResponse();
        res.setAlbum(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getSong", "/getSong.view" })
    public void getSong(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {

        HttpServletRequest request = wrapRequest(req);
        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        MediaFile song = mediaFileDao.getMediaFile(id);
        if (song == null || song.isDirectory()) {
            writeError(request, response, ErrorCode.NOT_FOUND, "Song not found.");
            return;
        }

        String username = userService.getCurrentUsername(request);
        if (!libraryAccessPolicy.isFolderAccessAllowed(song, username)) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED, "Access denied");
            return;
        }

        Player player = playerService.getPlayer(request, response);
        Response res = createResponse();
        res.setSong(createJaxbChild(player, song, username));
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getMusicDirectory", "/getMusicDirectory.view" })
    public void getMusicDirectory(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        MediaFile dir = mediaFileService.getMediaFile(id);
        if (dir == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, "Directory not found");
            return;
        }

        String username = userService.getCurrentUsername(request);
        if (!libraryAccessPolicy.isFolderAccessAllowed(dir, username)) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED, "Access denied");
            return;
        }

        MediaFile parent = mediaFileService.getParentOf(dir);
        Directory directory = new Directory();
        directory.setId(String.valueOf(id));
        try {
            if (!mediaFileService.isRoot(parent)) {
                directory.setParent(String.valueOf(parent.getId()));
            }
        } catch (SecurityException e) {
            if (LOG.isTraceEnabled()) {
                LOG
                    .trace("Error in getMusicDirectory.",
                            new AssertionError("Errors with unclear cases.", e));
            }
        }
        directory.setName(dir.getName());
        directory
            .setStarred(jaxbWriter.convertDate(mediaFileDao.getMediaFileStarredDate(id, username)));
        directory.setPlayCount((long) dir.getPlayCount());

        if (dir.isAlbum()) {
            directory.setAverageRating(ratingService.getAverageRating(dir));
            directory.setUserRating(ratingService.getRatingForUser(username, dir));
        }

        Player player = playerService.getPlayer(request, response);
        for (MediaFile child : mediaFileService.getChildrenOf(dir, true, true)) {
            directory.getChild().add(createJaxbChild(player, child, username));
        }

        Response res = createResponse();
        res.setDirectory(directory);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/search", "/search.view" })
    @SuppressLint(value = "USER_CONTROLLED_SQL_RISK", justification = "False positive. Username is being used via SecurityContextHolderAwareRequestWrapper.")
    public void search(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException, IOException {
        HttpServletRequest request = wrapRequest(req);

        StringBuilder query = new StringBuilder();
        String any = request.getParameter(Attributes.Request.ANY.value());
        if (any != null) {
            query.append(any).append(' ');
        }
        String artist = request.getParameter(Attributes.Request.ARTIST.value());
        if (artist != null) {
            query.append(artist).append(' ');
        }
        String album = request.getParameter(Attributes.Request.ALBUM.value());
        if (album != null) {
            query.append(album).append(' ');
        }
        String title = request.getParameter(Attributes.Request.TITLE.value());
        if (title != null) {
            query.append(title);
        }

        int offset = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.OFFSET.value(), 0);
        int count = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.COUNT.value(), 20);
        User user = userService.getCurrentUserStrict(request);

        boolean includeComposer = settingsFacade.get(SKeys.general.search.searchComposer)
                || userService
                    .getUserSettings(user.getUsername())
                    .getMainVisibility()
                    .isComposerVisible();
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername());
        HttpSearchCriteria criteria = director
            .construct(query.toString().trim(), offset, count, includeComposer, musicFolders,
                    IndexType.SONG);

        com.tesshu.jpsonic.infrastructure.search.LegacySearchResult result = legacySearch
            .search(criteria);
        org.subsonic.restapi.SearchResult searchResult = new org.subsonic.restapi.SearchResult();
        searchResult.setOffset(result.getOffset());
        searchResult.setTotalHits(result.getTotalHits());

        Player player = playerService.getPlayer(request, response);
        for (MediaFile mediaFile : result.getMediaFiles()) {
            searchResult.getMatch().add(createJaxbChild(player, mediaFile, user.getUsername()));
        }
        Response res = createResponse();
        res.setSearchResult(searchResult);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/search2", "/search2.view" })
    @SuppressLint(value = "USER_CONTROLLED_SQL_RISK", justification = "False positive. Username is being used via SecurityContextHolderAwareRequestWrapper.")
    public void search2(HttpServletRequest req, HttpServletResponse response)
            throws IOException, ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        User user = userService.getCurrentUserStrict(request);
        String username = user.getUsername();
        Integer musicFolderId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.MUSIC_FOLDER_ID.value());

        SearchResult2 searchResult = new SearchResult2();

        String searchInput = StringUtils
            .trimToEmpty(request.getParameter(Attributes.Request.QUERY.value()));
        int offset = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.ARTIST_OFFSET.value(), 0);
        int count = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.ARTIST_COUNT.value(), 20);
        boolean includeComposer = settingsFacade.get(SKeys.general.search.searchComposer)
                || userService.getUserSettings(username).getMainVisibility().isComposerVisible();
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(username, musicFolderId);

        HttpSearchCriteria criteria = director
            .construct(searchInput, offset, count, includeComposer, musicFolders, IndexType.ARTIST);
        com.tesshu.jpsonic.infrastructure.search.LegacySearchResult artists = legacySearch
            .search(criteria);
        for (MediaFile mediaFile : artists.getMediaFiles()) {
            searchResult.getArtist().add(createJaxbArtist(mediaFile, username));
        }

        offset = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.ALBUM_OFFSET.value(), 0);
        count = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.ALBUM_COUNT.value(), 20);
        criteria = director
            .construct(searchInput, offset, count, includeComposer, musicFolders, IndexType.ALBUM);
        Player player = playerService.getPlayer(request, response);
        com.tesshu.jpsonic.infrastructure.search.LegacySearchResult albums = legacySearch
            .search(criteria);
        for (MediaFile mediaFile : albums.getMediaFiles()) {
            searchResult.getAlbum().add(createJaxbChild(player, mediaFile, username));
        }

        offset = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.SONG_OFFSET.value(), 0);
        count = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.SONG_COUNT.value(), 20);
        criteria = director
            .construct(searchInput, offset, count, includeComposer, musicFolders, IndexType.SONG);
        com.tesshu.jpsonic.infrastructure.search.LegacySearchResult songs = legacySearch
            .search(criteria);
        for (MediaFile mediaFile : songs.getMediaFiles()) {
            searchResult.getSong().add(createJaxbChild(player, mediaFile, username));
        }

        Response res = createResponse();
        res.setSearchResult2(searchResult);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/search3", "/search3.view" })
    @SuppressLint(value = "USER_CONTROLLED_SQL_RISK", justification = "False positive. Username is being used via SecurityContextHolderAwareRequestWrapper.")
    public void search3(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException, IOException {
        HttpServletRequest request = wrapRequest(req);
        User user = userService.getCurrentUserStrict(request);
        String username = user.getUsername();
        Integer musicFolderId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.MUSIC_FOLDER_ID.value());

        SearchResult3 searchResult = new SearchResult3();

        String searchInput = StringUtils
            .trimToEmpty(request.getParameter(Attributes.Request.QUERY.value()));
        int offset = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.ARTIST_OFFSET.value(), 0);
        int count = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.ARTIST_COUNT.value(), 20);
        boolean includeComposer = settingsFacade.get(SKeys.general.search.searchComposer)
                || userService.getUserSettings(username).getMainVisibility().isComposerVisible();
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(username, musicFolderId);

        HttpSearchCriteria criteria = director
            .construct(searchInput, offset, count, includeComposer, musicFolders,
                    IndexType.ARTIST_ID3);
        com.tesshu.jpsonic.infrastructure.search.LegacySearchResult result = legacySearch
            .search(criteria);
        for (com.tesshu.jpsonic.persistence.api.entity.Artist artist : result.getArtists()) {
            searchResult.getArtist().add(createJaxbArtist(new ArtistID3(), artist, username));
        }

        offset = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.ALBUM_OFFSET.value(), 0);
        count = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.ALBUM_COUNT.value(), 20);
        criteria = director
            .construct(searchInput, offset, count, includeComposer, musicFolders,
                    IndexType.ALBUM_ID3);
        result = legacySearch.search(criteria);
        for (Album album : result.getAlbums()) {
            searchResult.getAlbum().add(createJaxbAlbum(new AlbumID3(), album, username));
        }

        offset = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.SONG_OFFSET.value(), 0);
        count = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.SONG_COUNT.value(), 20);
        criteria = director
            .construct(searchInput, offset, count, includeComposer, musicFolders, IndexType.SONG);
        Player player = playerService.getPlayer(request, response);
        result = legacySearch.search(criteria);
        for (MediaFile song : result.getMediaFiles()) {
            searchResult.getSong().add(createJaxbChild(player, song, username));
        }

        Response res = createResponse();
        res.setSearchResult3(searchResult);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getPlaylists", "/getPlaylists.view" })
    public void getPlaylists(HttpServletRequest req, HttpServletResponse response) {
        HttpServletRequest request = wrapRequest(req);

        User user = userService.getCurrentUserStrict(request);
        String authenticatedUsername = user.getUsername();
        String requestedUsername = request.getParameter(Attributes.Request.USER_NAME.value());

        if (requestedUsername == null) {
            requestedUsername = authenticatedUsername;
        } else if (!user.isAdminRole()) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED, authenticatedUsername
                    + " is not authorized to get playlists for " + requestedUsername);
            return;
        }

        Playlists result = new Playlists();

        for (com.tesshu.jpsonic.persistence.api.entity.Playlist playlist : playlistService
            .getReadablePlaylistsForUser(requestedUsername)) {
            result
                .getPlaylist()
                .add(createJaxbPlaylist(new org.subsonic.restapi.Playlist(), playlist));
        }

        Response res = createResponse();
        res.setPlaylists(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getPlaylist", "/getPlaylist.view" })
    public void getPlaylist(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        com.tesshu.jpsonic.persistence.api.entity.Playlist playlist = playlistService
            .getPlaylist(id);
        if (playlist == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, MSG_PLAYLIST_NOT_FOUND + id);
            return;
        }

        String username = userService.getCurrentUsername(request);
        if (!playlistService.isReadAllowed(playlist, username)) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED, MSG_PLAYLIST_DENIED + id);
            return;
        }

        Player player = playerService.getPlayer(request, response);
        PlaylistWithSongs result = createJaxbPlaylist(new PlaylistWithSongs(), playlist);
        for (MediaFile mediaFile : playlistService.getFilesInPlaylist(id)) {
            if (libraryAccessPolicy.isFolderAccessAllowed(mediaFile, username)) {
                result.getEntry().add(createJaxbChild(player, mediaFile, username));
            }
        }

        Response res = createResponse();
        res.setPlaylist(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/jukeboxControl", "/jukeboxControl.view" })
    public ResponseEntity<String> jukeboxControl(HttpServletRequest req,
            HttpServletResponse response) {
        return ResponseEntity.status(HttpStatus.SC_GONE).body(NO_LONGER_SUPPORTED);
    }

    @RequestMapping({ "/createPlaylist", "/createPlaylist.view" })
    public void createPlaylist(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        Integer playlistId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.PLAYLIST_ID.value());
        String name = request.getParameter(Attributes.Request.NAME.value());
        if (playlistId == null && name == null) {
            writeError(request, response, ErrorCode.MISSING_PARAMETER,
                    "Playlist ID or name must be specified.");
            return;
        }

        com.tesshu.jpsonic.persistence.api.entity.Playlist playlist;
        String username = userService.getCurrentUsername(request);
        if (playlistId == null) {
            playlist = new com.tesshu.jpsonic.persistence.api.entity.Playlist();
            playlist.setName(name);
            Instant now = now();
            playlist.setCreated(now);
            playlist.setChanged(now);
            playlist.setShared(false);
            playlist.setUsername(username);
            playlistService.createPlaylist(playlist);
        } else {
            playlist = playlistService.getPlaylist(playlistId);
            if (playlist == null) {
                writeError(request, response, ErrorCode.NOT_FOUND,
                        MSG_PLAYLIST_NOT_FOUND + playlistId);
                return;
            }
            if (!playlistService.isWriteAllowed(playlist, username)) {
                writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                        MSG_PLAYLIST_DENIED + playlistId);
                return;
            }
        }

        List<MediaFile> songs = new ArrayList<>();
        for (int id : ServletRequestUtils
            .getIntParameters(request, Attributes.Request.SONG_ID.value())) {
            MediaFile song = mediaFileService.getMediaFile(id);
            if (song != null) {
                songs.add(song);
            }
        }
        playlistService.setFilesInPlaylist(playlist.getId(), songs);

        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/updatePlaylist", "/updatePlaylist.view" })
    public void updatePlaylist(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {

        HttpServletRequest request = wrapRequest(req);
        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.PLAYLIST_ID.value());
        com.tesshu.jpsonic.persistence.api.entity.Playlist playlist = playlistService
            .getPlaylist(id);
        if (playlist == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, MSG_PLAYLIST_NOT_FOUND + id);
            return;
        }

        String username = userService.getCurrentUsername(request);
        if (!playlistService.isWriteAllowed(playlist, username)) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED, MSG_PLAYLIST_DENIED + id);
            return;
        }

        String name = request.getParameter(Attributes.Request.NAME.value());
        if (name != null) {
            playlist.setName(name);
        }
        String comment = request.getParameter(Attributes.Request.COMMENT.value());
        if (comment != null) {
            playlist.setComment(comment);
        }
        Boolean shared = ServletRequestUtils
            .getBooleanParameter(request, Attributes.Request.PUBLIC.value());
        if (shared != null) {
            playlist.setShared(shared);
        }
        playlistService.updatePlaylist(playlist);

        List<MediaFile> songs = playlistService.getFilesInPlaylist(id);
        int[] toRemove = ServletRequestUtils
            .getIntParameters(request, Attributes.Request.SONG_INDEX_TO_REMOVE.value());
        int[] toAdd = ServletRequestUtils
            .getIntParameters(request, Attributes.Request.SONG_ID_TO_ADD.value());
        updatePlaylistSongs(id, toRemove, toAdd, songs);

        writeEmptyResponse(request, response);
    }

    private void updatePlaylistSongs(int playlistId, int[] toRemove, int[] toAdd,
            List<MediaFile> songs) {
        boolean songsChanged = false;

        SortedSet<Integer> tmp = new TreeSet<>();
        for (int songIndexToRemove : toRemove) {
            tmp.add(songIndexToRemove);
        }
        List<Integer> songIndexesToRemove = new ArrayList<>(tmp);
        Collections.reverse(songIndexesToRemove);
        for (int songIndexToRemove : songIndexesToRemove) {
            songs.remove(songIndexToRemove);
            songsChanged = true;
        }
        for (int songToAdd : toAdd) {
            MediaFile song = mediaFileService.getMediaFile(songToAdd);
            if (song != null) {
                songs.add(song);
                songsChanged = true;
            }
        }
        if (songsChanged) {
            playlistService.setFilesInPlaylist(playlistId, songs);
        }
    }

    @RequestMapping({ "/deletePlaylist", "/deletePlaylist.view" })
    public void deletePlaylist(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        com.tesshu.jpsonic.persistence.api.entity.Playlist playlist = playlistService
            .getPlaylist(id);
        if (playlist == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, MSG_PLAYLIST_NOT_FOUND + id);
            return;
        }

        String username = userService.getCurrentUsername(request);
        if (!playlistService.isWriteAllowed(playlist, username)) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED, MSG_PLAYLIST_DENIED + id);
            return;
        }
        playlistService.deletePlaylist(id);

        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/getAlbumList", "/getAlbumList.view" })
    public void getAlbumList(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException, ExecutionException {
        HttpServletRequest request = wrapRequest(req);
        User user = userService.getCurrentUserStrict(request);

        int size = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.SIZE.value(), 10);
        int offset = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.OFFSET.value(), 0);
        Integer musicFolderId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.MUSIC_FOLDER_ID.value());

        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername(), musicFolderId);

        size = Math.max(0, Math.min(size, 500));
        String type = ServletRequestUtils
            .getRequiredStringParameter(request, Attributes.Request.TYPE.value());

        List<MediaFile> albums;
        if ("highest".equals(type)) {
            albums = ratingService.getHighestRatedAlbums(offset, size, musicFolders);
        } else if ("frequent".equals(type)) {
            albums = mediaFileService.getMostFrequentlyPlayedAlbums(offset, size, musicFolders);
        } else if ("recent".equals(type)) {
            albums = mediaFileService.getMostRecentlyPlayedAlbums(offset, size, musicFolders);
        } else if ("newest".equals(type)) {
            albums = mediaFileService.getNewestAlbums(offset, size, musicFolders);
        } else if ("starred".equals(type)) {
            albums = mediaFileService
                .getStarredAlbums(offset, size, user.getUsername(), musicFolders);
        } else if ("alphabeticalByArtist".equals(type)) {
            albums = mediaFileService.getAlphabeticalAlbums(offset, size, true, musicFolders);
        } else if ("alphabeticalByName".equals(type)) {
            albums = mediaFileService.getAlphabeticalAlbums(offset, size, false, musicFolders);
        } else if ("byGenre".equals(type)) {
            albums = legacySearch
                .getAlbumsByGenres(
                        ServletRequestUtils
                            .getRequiredStringParameter(request, Attributes.Request.GENRE.value()),
                        offset, size, musicFolders);
        } else if ("byYear".equals(type)) {
            albums = mediaFileService
                .getAlbumsByYear(offset, size,
                        ServletRequestUtils
                            .getRequiredIntParameter(request, Attributes.Request.FROM_YEAR.value()),
                        ServletRequestUtils
                            .getRequiredIntParameter(request, Attributes.Request.TO_YEAR.value()),
                        musicFolders);
        } else if ("random".equals(type)) {
            albums = legacySearch.getRandomAlbums(size, musicFolders);
        } else {
            throw new ExecutionException(new IOException("Invalid list type: " + type));
        }

        Player player = playerService.getPlayer(request, response);
        AlbumList result = new AlbumList();
        for (MediaFile album : albums) {
            result.getAlbum().add(createJaxbChild(player, album, user.getUsername()));
        }

        Response res = createResponse();
        res.setAlbumList(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getAlbumList2", "/getAlbumList2.view" })
    public void getAlbumList2(HttpServletRequest req, HttpServletResponse response)
            throws ExecutionException, ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);

        int size = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.SIZE.value(), 10);
        int offset = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.OFFSET.value(), 0);
        size = Math.max(0, Math.min(size, 500));
        String type = ServletRequestUtils
            .getRequiredStringParameter(request, Attributes.Request.TYPE.value());
        User user = userService.getCurrentUserStrict(request);
        Integer musicFolderId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.MUSIC_FOLDER_ID.value());
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername(), musicFolderId);

        List<Album> albums;
        if ("frequent".equals(type)) {
            albums = albumDao.getMostFrequentlyPlayedAlbums(offset, size, musicFolders);
        } else if ("recent".equals(type)) {
            albums = albumDao.getMostRecentlyPlayedAlbums(offset, size, musicFolders);
        } else if ("newest".equals(type)) {
            albums = albumDao.getNewestAlbums(offset, size, musicFolders);
        } else if ("alphabeticalByArtist".equals(type)) {
            albums = albumDao.getAlphabeticalAlbums(offset, size, true, true, musicFolders);
        } else if ("alphabeticalByName".equals(type)) {
            albums = albumDao.getAlphabeticalAlbums(offset, size, false, true, musicFolders);
        } else if ("byGenre".equals(type)) {
            albums = legacySearch
                .getAlbumId3sByGenres(
                        ServletRequestUtils
                            .getRequiredStringParameter(request, Attributes.Request.GENRE.value()),
                        offset, size, musicFolders);
        } else if ("byYear".equals(type)) {
            albums = albumDao
                .getAlbumsByYear(offset, size,
                        ServletRequestUtils
                            .getRequiredIntParameter(request, Attributes.Request.FROM_YEAR.value()),
                        ServletRequestUtils
                            .getRequiredIntParameter(request, Attributes.Request.TO_YEAR.value()),
                        musicFolders);
        } else if ("starred".equals(type)) {
            albums = albumDao
                .getStarredAlbums(offset, size,
                        userService.getCurrentUserStrict(request).getUsername(), musicFolders);
        } else if ("random".equals(type)) {
            albums = legacySearch.getRandomAlbumsId3(size, musicFolders);
        } else {
            throw new ExecutionException(new IOException("Invalid list type: " + type));
        }
        AlbumList2 result = new AlbumList2();
        for (Album album : albums) {
            result.getAlbum().add(createJaxbAlbum(new AlbumID3(), album, user.getUsername()));
        }
        Response res = createResponse();
        res.setAlbumList2(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getRandomSongs", "/getRandomSongs.view" })
    public void getRandomSongs(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        Player player = playerService.getPlayer(request, response);
        User user = userService.getCurrentUserStrict(request);

        int size = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.SIZE.value(), 10);
        size = Math.max(0, Math.min(size, 500));
        List<String> genres = null;
        String genre = ServletRequestUtils
            .getStringParameter(request, Attributes.Request.GENRE.value());
        if (null != genre) {
            genres = Arrays.asList(genre);
        }
        Integer fromYear = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.FROM_YEAR.value());
        Integer toYear = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.TO_YEAR.value());
        Integer musicFolderId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.MUSIC_FOLDER_ID.value());
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername(), musicFolderId);
        ShuffleSelectionParam criteria = new ShuffleSelectionParam(size, genres, fromYear, toYear,
                musicFolders);

        Songs result = new Songs();
        for (MediaFile mediaFile : legacySearch.getRandomSongs(criteria)) {
            result.getSong().add(createJaxbChild(player, mediaFile, user.getUsername()));
        }
        Response res = createResponse();
        res.setRandomSongs(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getVideos", "/getVideos.view" })
    public void getVideos(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        Player player = playerService.getPlayer(request, response);
        User user = userService.getCurrentUserStrict(request);

        int size = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.SIZE.value(), Integer.MAX_VALUE);
        int offset = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.OFFSET.value(), 0);
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername());

        Videos result = new Videos();
        for (MediaFile mediaFile : mediaFileDao.getVideos(size, offset, musicFolders)) {
            result.getVideo().add(createJaxbChild(player, mediaFile, user.getUsername()));
        }
        Response res = createResponse();
        res.setVideos(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getNowPlaying", "/getNowPlaying.view" })
    public void getNowPlaying(HttpServletRequest req, HttpServletResponse response) {
        HttpServletRequest request = wrapRequest(req);
        NowPlaying result = new NowPlaying();

        for (PlayStatus status : statusService.getPlayStatuses()) {

            Player player = status.getPlayer();
            MediaFile mediaFile = status.getMediaFile();
            String username = player.getUsername();
            if (username == null) {
                continue;
            }

            UserSettings userSettings = userService.getUserSettings(username);
            if (!userSettings.isNowPlayingAllowed()) {
                continue;
            }

            long minutesAgo = status.getMinutesAgo();
            if (minutesAgo < LIMIT_OF_HISTORY_TO_BE_PRESENTED) {
                NowPlayingEntry entry = new NowPlayingEntry();
                entry.setUsername(username);
                entry.setPlayerId(player.getId());
                entry.setPlayerName(player.getName());
                entry.setMinutesAgo((int) minutesAgo);
                result.getEntry().add(createJaxbChild(entry, player, mediaFile, username));
            }
        }

        Response res = createResponse();
        res.setNowPlaying(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    private Child createJaxbChild(Player player, MediaFile mediaFile, String username) {
        return createJaxbChild(new Child(), player, mediaFile, username);
    }

    @SuppressWarnings({ "PMD.CognitiveComplexity", "PMD.NPathComplexity" })
    private <T extends Child> T createJaxbChild(T child, Player player, MediaFile mediaFile,
            String username) {
        MediaFile parent = mediaFileService.getParentOf(mediaFile);
        if (parent != null) {
            try {
                if (!mediaFileService.isRoot(parent)) {
                    child.setParent(String.valueOf(parent.getId()));
                }
            } catch (SecurityException e) {
                if (LOG.isTraceEnabled()) {
                    LOG
                        .trace("Error in getMusicDirectory",
                                new AssertionError("Errors with unclear cases.", e));
                }
            }
            child.setCoverArt(findCoverArt(mediaFile, parent));
        }
        child.setId(String.valueOf(mediaFile.getId()));
        child.setTitle(mediaFile.getName());
        child.setAlbum(mediaFile.getAlbumName());
        child.setArtist(mediaFile.getArtist());
        child.setIsDir(mediaFile.isDirectory());
        child.setYear(mediaFile.getYear());
        child.setGenre(mediaFile.getGenre());
        child.setCreated(jaxbWriter.convertDate(mediaFile.getCreated()));
        child
            .setStarred(jaxbWriter
                .convertDate(mediaFileDao.getMediaFileStarredDate(mediaFile.getId(), username)));
        child.setUserRating(ratingService.getRatingForUser(username, mediaFile));
        child.setAverageRating(ratingService.getAverageRating(mediaFile));
        child.setPlayCount((long) mediaFile.getPlayCount());

        if (mediaFile.isFile()) {
            child.setDuration(mediaFile.getDurationSeconds());
            child.setBitRate(mediaFile.getBitRate());
            child.setTrack(mediaFile.getTrackNumber());
            child.setDiscNumber(mediaFile.getDiscNumber());
            child.setSize(mediaFile.getFileSize());
            String suffix = mediaFile.getFormat();
            child.setSuffix(suffix);
            child.setContentType(MediaTypeDetector.getMimeType(suffix));
            child.setIsVideo(mediaFile.isVideo());
            child.setPath(getRelativePath(mediaFile, serverLocaleProvider, musicFolderService));

            String albumArtist = mediaFile.getAlbumArtist();
            String albumName = mediaFile.getAlbumName();
            Album album = (albumArtist != null && albumName != null)
                    ? albumDao.getAlbum(albumArtist, albumName)
                    : null;
            if (album != null) {
                child.setAlbumId(String.valueOf(album.getId()));
            }

            String artistName = mediaFile.getArtist();
            com.tesshu.jpsonic.persistence.api.entity.Artist artist = (artistName != null)
                    ? artistDao.getArtist(artistName)
                    : null;
            if (artist != null) {
                child.setArtistId(String.valueOf(artist.getId()));
            }

            child.setType(getMedyaType(mediaFile.getMediaType()));
            if (mediaFile.getMediaType() == MediaFile.MediaType.VIDEO) {
                child.setOriginalWidth(mediaFile.getWidth());
                child.setOriginalHeight(mediaFile.getHeight());
            }

            if (transcodingService.isTranscodingRequired(mediaFile, player)) {
                String transcodedSuffix = transcodingService.getSuffix(player, mediaFile, null);
                child.setTranscodedSuffix(transcodedSuffix);
                child.setTranscodedContentType(MediaTypeDetector.getMimeType(transcodedSuffix));
            }
        }
        return child;
    }

    private MediaType getMedyaType(MediaFile.MediaType mediaType) {
        return switch (mediaType) {
        case MUSIC -> MediaType.MUSIC;
        case PODCAST -> MediaType.PODCAST;
        case AUDIOBOOK -> MediaType.AUDIOBOOK;
        case VIDEO -> MediaType.VIDEO;
        default -> null;
        };
    }

    private String findCoverArt(MediaFile mediaFile, MediaFile parent) {
        MediaFile dir = mediaFile.isDirectory() ? mediaFile : parent;
        if (dir != null && dir.getCoverArtPathString() != null) {
            return String.valueOf(dir.getId());
        }
        return null;
    }

    public static String getRelativePath(MediaFile musicFile,
            ServerLocaleProvider serverLocaleProvider, MusicFolderService musicFolderService) {

        String filePath = musicFile.getPathString();

        // Convert slashes.
        filePath = filePath.replace('\\', '/');

        String filePathLower = filePath.toLowerCase(serverLocaleProvider.getLocale());

        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getAllMusicFolders(false, true);
        StringBuilder builder = new StringBuilder();
        for (com.tesshu.jpsonic.persistence.api.entity.MusicFolder musicFolder : musicFolders) {
            String folderPath = musicFolder.getPathString().replace('\\', '/');
            String folderPathLower = folderPath.toLowerCase(serverLocaleProvider.getLocale());
            if (!folderPathLower.endsWith("/")) {
                builder.setLength(0);
                folderPathLower = builder.append(folderPathLower).append('/').toString();
            }
            if (filePathLower.startsWith(folderPathLower)) {
                String relativePath = filePath.substring(folderPath.length());
                return !relativePath.isEmpty() && relativePath.charAt(0) == '/'
                        ? relativePath.substring(1)
                        : relativePath;
            }
        }

        return null;
    }

    @RequestMapping({ "/download", "/download.view" })
    public void download(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException, IOException {
        HttpServletRequest request = wrapRequest(req);
        User user = userService.getCurrentUserStrict(request);
        if (!user.isDownloadRole()) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    user.getUsername() + " is not authorized to download files.");
            return;
        }

        long ifModifiedSince = request.getDateHeader("If-Modified-Since");
        long lastModified = downloadController.getLastModified(request);

        if (ifModifiedSince != -1 && lastModified != -1 && lastModified <= ifModifiedSince) {
            response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        if (lastModified != -1) {
            response.setDateHeader("Last-Modified", lastModified);
        }

        downloadController.handleRequest(request, response);
    }

    @RequestMapping({ "/stream", "/stream.view" })
    public void stream(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException, IOException {
        HttpServletRequest request = wrapRequest(req);
        User user = userService.getCurrentUserStrict(request);
        if (!user.isStreamRole()) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    user.getUsername() + " is not authorized to play files.");
            return;
        }
        req.setAttribute(Request.IS_REST.value(), "true");
        streamController.handleRequest(request, response);
    }

    @RequestMapping({ "/hls", "/hls.view" })
    public void hls(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException, IOException {
        HttpServletRequest request = wrapRequest(req);
        User user = userService.getCurrentUserStrict(request);
        if (!user.isStreamRole()) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    user.getUsername() + " is not authorized to play files.");
            return;
        }
        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        MediaFile video = mediaFileDao.getMediaFile(id);
        if (video == null || video.isDirectory()) {
            writeError(request, response, ErrorCode.NOT_FOUND, "Video not found.");
            return;
        }
        if (!libraryAccessPolicy.isFolderAccessAllowed(video, user.getUsername())) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED, "Access denied");
            return;
        }
        hlsController.handleRequest(request, response);
    }

    @RequestMapping({ "/scrobble", "/scrobble.view" })
    public void scrobble(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        int[] ids = ServletRequestUtils
            .getRequiredIntParameters(request, Attributes.Request.ID.value());
        long[] times = ServletRequestUtils.getLongParameters(request, "time");
        if (times.length > 0 && times.length != ids.length) {
            writeError(request, response, ErrorCode.GENERIC,
                    "Wrong number of timestamps: " + times.length);
            return;
        }

        Player player = playerService.getPlayer(request, response);
        boolean submission = ServletRequestUtils
            .getBooleanParameter(request, Attributes.Request.SUBMISSION.value(), true);
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            MediaFile file = mediaFileService.getMediaFile(id);
            if (file == null) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("File to scrobble not found: " + id);
                }
                continue;
            }
            Instant time = times.length == 0 ? now() : Instant.ofEpochMilli(times[i]);
            statusService.addRemotePlay(new PlayStatus(file, player, time));
            writableMediaFileService.incrementPlayCount(file);
            if (userService.getUserSettings(player.getUsername()).isLastFmEnabled()) {
                audioScrobblerService.register(file, player.getUsername(), submission, time);
            }
        }

        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/star", "/star.view" })
    public void star(HttpServletRequest request, HttpServletResponse response) {
        starOrUnstar(request, response, true);
    }

    @RequestMapping({ "/unstar", "/unstar.view" })
    public void unstar(HttpServletRequest request, HttpServletResponse response) {
        starOrUnstar(request, response, false);
    }

    @SuppressWarnings("PMD.CognitiveComplexity") // #1020 Move to support class or service
    private void starOrUnstar(HttpServletRequest req, HttpServletResponse response, boolean star) {
        HttpServletRequest request = wrapRequest(req);

        String username = userService.getCurrentUserStrict(request).getUsername();
        for (int id : ServletRequestUtils
            .getIntParameters(request, Attributes.Request.ID.value())) {
            MediaFile mediaFile = mediaFileDao.getMediaFile(id);
            if (mediaFile == null) {
                writeError(request, response, ErrorCode.NOT_FOUND, "Media file not found: " + id);
                return;
            }
            if (star) {
                mediaFileDao.starMediaFile(id, username);
            } else {
                mediaFileDao.unstarMediaFile(id, username);
            }
        }
        for (int albumId : ServletRequestUtils.getIntParameters(request, "albumId")) {
            Album album = albumDao.getAlbum(albumId);
            if (album == null) {
                writeError(request, response, ErrorCode.NOT_FOUND, "Album not found: " + albumId);
                return;
            }
            if (star) {
                albumDao.starAlbum(albumId, username);
            } else {
                albumDao.unstarAlbum(albumId, username);
            }
        }
        for (int artistId : ServletRequestUtils.getIntParameters(request, "artistId")) {
            com.tesshu.jpsonic.persistence.api.entity.Artist artist = artistDao.getArtist(artistId);
            if (artist == null) {
                writeError(request, response, ErrorCode.NOT_FOUND, "Artist not found: " + artistId);
                return;
            }
            if (star) {
                artistDao.starArtist(artistId, username);
            } else {
                artistDao.unstarArtist(artistId, username);
            }
        }

        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/getStarred", "/getStarred.view" })
    public void getStarred(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        Player player = playerService.getPlayer(request, response);
        User user = userService.getCurrentUserStrict(request);
        String username = user.getUsername();
        Integer musicFolderId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.MUSIC_FOLDER_ID.value());
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(username, musicFolderId);

        Starred result = new Starred();
        for (MediaFile artist : mediaFileDao
            .getStarredDirectories(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getArtist().add(createJaxbArtist(artist, username));
        }
        for (MediaFile album : mediaFileDao
            .getStarredAlbums(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getAlbum().add(createJaxbChild(player, album, username));
        }
        for (MediaFile song : mediaFileDao
            .getStarredFiles(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getSong().add(createJaxbChild(player, song, username));
        }
        Response res = createResponse();
        res.setStarred(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getStarred2", "/getStarred2.view" })
    public void getStarred2(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        Player player = playerService.getPlayer(request, response);
        User user = userService.getCurrentUserStrict(request);
        String username = user.getUsername();
        Integer musicFolderId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.MUSIC_FOLDER_ID.value());
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(username, musicFolderId);

        Starred2 result = new Starred2();
        for (com.tesshu.jpsonic.persistence.api.entity.Artist artist : artistDao
            .getStarredArtists(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getArtist().add(createJaxbArtist(new ArtistID3(), artist, username));
        }
        for (Album album : albumDao
            .getStarredAlbums(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getAlbum().add(createJaxbAlbum(new AlbumID3(), album, username));
        }
        for (MediaFile song : mediaFileDao
            .getStarredFiles(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getSong().add(createJaxbChild(player, song, username));
        }
        Response res = createResponse();
        res.setStarred2(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getPodcasts", "/getPodcasts.view" })
    public void getPodcasts(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        Player player = playerService.getPlayer(request, response);
        String username = userService.getCurrentUsername(request);
        boolean includeEpisodes = ServletRequestUtils
            .getBooleanParameter(request, Attributes.Request.INCLUDE_EPISODES.value(), true);
        Integer channelId = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.ID.value());

        Podcasts result = new Podcasts();

        for (com.tesshu.jpsonic.persistence.api.entity.PodcastChannel channel : podcastService
            .getAllChannels()) {
            if (channelId == null || channelId.equals(channel.getId())) {

                org.subsonic.restapi.PodcastChannel c = new org.subsonic.restapi.PodcastChannel();
                result.getChannel().add(c);

                c.setId(String.valueOf(channel.getId()));
                c.setUrl(channel.getUrl());
                c.setStatus(PodcastStatus.valueOf(channel.getStatus().name()));
                c.setTitle(channel.getTitle());
                c.setDescription(channel.getDescription());
                c.setCoverArt(CoverArtType.PODCAST.createKey(channel.getId()));
                c.setOriginalImageUrl(channel.getImageUrl());
                c.setErrorMessage(channel.getErrorMessage());

                if (includeEpisodes) {
                    List<com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode> episodes = podcastService
                        .getEpisodes(channel.getId());
                    for (com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode episode : episodes) {
                        c.getEpisode().add(createJaxbPodcastEpisode(player, username, episode));
                    }
                }
            }
        }
        Response res = createResponse();
        res.setPodcasts(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getNewestPodcasts", "/getNewestPodcasts.view" })
    public void getNewestPodcasts(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        Player player = playerService.getPlayer(request, response);
        String username = userService.getCurrentUsername(request);

        int count = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.COUNT.value(), 20);
        NewestPodcasts result = new NewestPodcasts();

        for (com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode episode : podcastService
            .getNewestEpisodes(count)) {
            result.getEpisode().add(createJaxbPodcastEpisode(player, username, episode));
        }

        Response res = createResponse();
        res.setNewestPodcasts(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    private org.subsonic.restapi.PodcastEpisode createJaxbPodcastEpisode(Player player,
            String username, com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode episode) {
        org.subsonic.restapi.PodcastEpisode e = new org.subsonic.restapi.PodcastEpisode();

        String path = episode.getPath();
        if (path != null) {
            MediaFile mediaFile = mediaFileService.getMediaFile(path);
            if (mediaFile != null) {
                e = createJaxbChild(new org.subsonic.restapi.PodcastEpisode(), player, mediaFile,
                        username);
                e.setStreamId(String.valueOf(mediaFile.getId()));
            }
        }

        e.setId(String.valueOf(episode.getId())); // Overwrites the previous "id" attribute.
        e.setChannelId(String.valueOf(episode.getChannelId()));
        e.setStatus(PodcastStatus.valueOf(episode.getStatus().name()));
        e.setTitle(episode.getTitle());
        e.setDescription(episode.getDescription());
        e.setPublishDate(jaxbWriter.convertDate(episode.getPublishDate()));
        return e;
    }

    @RequestMapping({ "/refreshPodcasts", "/refreshPodcasts.view" })
    public void refreshPodcasts(HttpServletRequest req, HttpServletResponse response) {
        HttpServletRequest request = wrapRequest(req);

        if (mediaScannerService.isScanning()) {
            writeError(request, response, ErrorCode.GENERIC, MSG_SCANNING_NOW);
            return;
        }

        User user = userService.getCurrentUserStrict(request);
        if (!user.isPodcastRole()) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    user.getUsername() + MSG_PODCAST_NOT_AUTHORIZED);
            return;
        }
        podcastService.refreshAllChannels(true);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/createPodcastChannel", "/createPodcastChannel.view" })
    public void createPodcastChannel(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);

        if (mediaScannerService.isScanning()) {
            writeError(request, response, ErrorCode.GENERIC, MSG_SCANNING_NOW);
            return;
        }

        User user = userService.getCurrentUserStrict(request);
        if (!user.isPodcastRole()) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    user.getUsername() + MSG_PODCAST_NOT_AUTHORIZED);
            return;
        }

        String url = ServletRequestUtils
            .getRequiredStringParameter(request, Attributes.Request.URL.value());
        podcastService.createChannel(url);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/deletePodcastChannel", "/deletePodcastChannel.view" })
    public void deletePodcastChannel(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        User user = userService.getCurrentUserStrict(request);
        if (!user.isPodcastRole()) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    user.getUsername() + MSG_PODCAST_NOT_AUTHORIZED);
            return;
        }

        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        podcastService.deleteChannel(id);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/deletePodcastEpisode", "/deletePodcastEpisode.view" })
    public void deletePodcastEpisode(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        User user = userService.getCurrentUserStrict(request);
        if (!user.isPodcastRole()) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    user.getUsername() + MSG_PODCAST_NOT_AUTHORIZED);
            return;
        }

        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        podcastService.deleteEpisode(id, true);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/downloadPodcastEpisode", "/downloadPodcastEpisode.view" })
    public void downloadPodcastEpisode(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);

        if (mediaScannerService.isScanning()) {
            writeError(request, response, ErrorCode.GENERIC, MSG_SCANNING_NOW);
            return;
        }

        User user = userService.getCurrentUserStrict(request);
        if (!user.isPodcastRole()) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    user.getUsername() + MSG_PODCAST_NOT_AUTHORIZED);
            return;
        }

        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        com.tesshu.jpsonic.persistence.api.entity.PodcastEpisode episode = podcastService
            .getEpisode(id, true);
        if (episode == null) {
            writeError(request, response, ErrorCode.NOT_FOUND,
                    "Podcast episode " + id + " not found.");
            return;
        }

        podcastService.downloadEpisode(episode);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/getInternetRadioStations", "/getInternetRadioStations.view" })
    public void getInternetRadioStations(HttpServletRequest req, HttpServletResponse response) {
        HttpServletRequest request = wrapRequest(req);

        InternetRadioStations result = new InternetRadioStations();
        for (InternetRadio radio : internetRadioService.getAllInternetRadios()) {
            InternetRadioStation i = new InternetRadioStation();
            i.setId(String.valueOf(radio.getId()));
            i.setName(radio.getName());
            i.setStreamUrl(radio.getStreamUrl());
            i.setHomePageUrl(radio.getHomepageUrl());
            result.getInternetRadioStation().add(i);
        }
        Response res = createResponse();
        res.setInternetRadioStations(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getBookmarks", "/getBookmarks.view" })
    public void getBookmarks(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        Player player = playerService.getPlayer(request, response);
        User user = userService.getCurrentUserStrict(request);

        Bookmarks result = new Bookmarks();
        for (Bookmark bookmark : bookmarkService.getBookmarks(user.getUsername())) {
            org.subsonic.restapi.Bookmark b = new org.subsonic.restapi.Bookmark();
            result.getBookmark().add(b);
            b.setPosition(bookmark.getPositionMillis());
            b.setUsername(bookmark.getUsername());
            b.setComment(bookmark.getComment());
            b.setCreated(jaxbWriter.convertDate(bookmark.getCreated()));
            b.setChanged(jaxbWriter.convertDate(bookmark.getChanged()));

            MediaFile mediaFile = mediaFileService.getMediaFile(bookmark.getMediaFileId());
            if (mediaFile != null) {
                b.setEntry(createJaxbChild(player, mediaFile, user.getUsername()));
            }
        }

        Response res = createResponse();
        res.setBookmarks(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/createBookmark", "/createBookmark.view" })
    public void createBookmark(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        String username = userService.getCurrentUsername(request);
        int mediaFileId = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        long position = ServletRequestUtils
            .getRequiredLongParameter(request, Attributes.Request.POSITION.value());
        String comment = request.getParameter(Attributes.Request.COMMENT.value());

        Instant now = now();
        Bookmark bookmark = new Bookmark(0, mediaFileId, position, username, comment, now, now);
        bookmarkService.createOrUpdateBookmark(bookmark);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/deleteBookmark", "/deleteBookmark.view" })
    public void deleteBookmark(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);

        String username = userService.getCurrentUsername(request);
        int mediaFileId = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        bookmarkService.deleteBookmark(username, mediaFileId);

        writeEmptyResponse(request, response);
    }

    @SuppressWarnings("UnnecessarilyFullyQualified")
    @RequestMapping({ "/getPlayQueue", "/getPlayQueue.view" })
    public void getPlayQueue(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        String username = userService.getCurrentUsername(request);
        SavedPlayQueue playQueue = playQueueDao.getPlayQueue(username);
        if (playQueue == null) {
            writeEmptyResponse(request, response);
            return;
        }

        org.subsonic.restapi.PlayQueue restPlayQueue = new org.subsonic.restapi.PlayQueue();
        restPlayQueue.setUsername(playQueue.getUsername());
        restPlayQueue.setCurrent(playQueue.getCurrentMediaFileId());
        restPlayQueue.setPosition(playQueue.getPositionMillis());
        restPlayQueue.setChanged(jaxbWriter.convertDate(playQueue.getChanged()));
        restPlayQueue.setChangedBy(playQueue.getChangedBy());

        Player player = playerService.getPlayer(request, response);
        for (Integer mediaFileId : playQueue.getMediaFileIds()) {
            MediaFile mediaFile = mediaFileService.getMediaFile(mediaFileId);
            if (mediaFile != null) {
                restPlayQueue.getEntry().add(createJaxbChild(player, mediaFile, username));
            }
        }

        Response res = createResponse();
        res.setPlayQueue(restPlayQueue);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/savePlayQueue", "/savePlayQueue.view" })
    public void savePlayQueue(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        List<Integer> mediaFileIds = PlayerUtils
            .toIntegerList(
                    ServletRequestUtils.getIntParameters(request, Attributes.Request.ID.value()));
        Integer current = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.CURRENT.value());
        if (!mediaFileIds.contains(current)) {
            writeError(request, response, ErrorCode.GENERIC,
                    "Current track is not included in play queue");
            return;
        }

        String username = userService.getCurrentUsername(request);
        Long position = ServletRequestUtils
            .getLongParameter(request, Attributes.Request.POSITION.value());
        Instant changed = now();
        String changedBy = ServletRequestUtils
            .getRequiredStringParameter(request, Attributes.Request.C.value());

        SavedPlayQueue playQueue = new SavedPlayQueue(null, username, mediaFileIds, current,
                position, changed, changedBy);
        playQueueDao.savePlayQueue(playQueue);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/getShares", "/getShares.view" })
    public void getShares(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        Player player = playerService.getPlayer(request, response);
        User user = userService.getCurrentUserStrict(request);
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername());

        Shares result = new Shares();
        for (com.tesshu.jpsonic.persistence.api.entity.Share share : shareService
            .getSharesForUser(user)) {
            org.subsonic.restapi.Share s = createJaxbShare(request, share);
            result.getShare().add(s);

            for (MediaFile mediaFile : shareService.getSharedFiles(share.getId(), musicFolders)) {
                s.getEntry().add(createJaxbChild(player, mediaFile, user.getUsername()));
            }
        }
        Response res = createResponse();
        res.setShares(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/createShare", "/createShare.view" })
    public void createShare(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        User user = userService.getCurrentUserStrict(request);
        if (!user.isShareRole()) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    user.getUsername() + " is not authorized to share media.");
            return;
        }

        List<MediaFile> files = new ArrayList<>();
        for (int id : ServletRequestUtils
            .getRequiredIntParameters(request, Attributes.Request.ID.value())) {
            files.add(mediaFileService.getMediaFile(id));
        }

        com.tesshu.jpsonic.persistence.api.entity.Share share = shareService
            .createShare(request, files);
        share.setDescription(request.getParameter(Attributes.Request.DESCRIPTION.value()));
        long expires = ServletRequestUtils
            .getLongParameter(request, Attributes.Request.EXPIRES.value(), 0L);
        if (expires != 0) {
            share.setExpires(Instant.ofEpochMilli(expires));
        }
        shareService.updateShare(share);

        Shares result = new Shares();
        org.subsonic.restapi.Share s = createJaxbShare(request, share);
        result.getShare().add(s);

        Player player = playerService.getPlayer(request, response);
        String username = userService.getCurrentUsername(request);
        if (username == null) {
            writeError(request, response, ErrorCode.NOT_AUTHENTICATED, "Wrong username.");
        }
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(username);
        for (MediaFile mediaFile : shareService.getSharedFiles(share.getId(), musicFolders)) {
            s.getEntry().add(createJaxbChild(player, mediaFile, username));
        }

        Response res = createResponse();
        res.setShares(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/deleteShare", "/deleteShare.view" })
    public void deleteShare(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {

        HttpServletRequest request = wrapRequest(req);
        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        com.tesshu.jpsonic.persistence.api.entity.Share share = shareService.getShareById(id);
        if (share == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, "Shared media not found.");
            return;
        }

        User user = userService.getCurrentUserStrict(request);
        if (!user.isAdminRole() && !share.getUsername().equals(user.getUsername())) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    "Not authorized to delete shared media.");
            return;
        }

        shareService.deleteShare(id);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/updateShare", "/updateShare.view" })
    public void updateShare(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {

        HttpServletRequest request = wrapRequest(req);
        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        com.tesshu.jpsonic.persistence.api.entity.Share share = shareService.getShareById(id);
        if (share == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, "Shared media not found.");
            return;
        }

        User user = userService.getCurrentUserStrict(request);
        if (!user.isAdminRole() && !share.getUsername().equals(user.getUsername())) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    "Not authorized to modify shared media.");
            return;
        }

        share.setDescription(request.getParameter(Attributes.Request.DESCRIPTION.value()));
        String expiresString = request.getParameter(Attributes.Request.EXPIRES.value());
        if (expiresString != null) {
            long expires = Long.parseLong(expiresString);
            share.setExpires(expires == 0L ? null : Instant.ofEpochMilli(expires));
        }
        shareService.updateShare(share);
        writeEmptyResponse(request, response);
    }

    private org.subsonic.restapi.Share createJaxbShare(HttpServletRequest request,
            com.tesshu.jpsonic.persistence.api.entity.Share share) {
        org.subsonic.restapi.Share result = new org.subsonic.restapi.Share();
        result.setId(String.valueOf(share.getId()));
        result.setUrl(shareService.getShareUrl(request, share));
        result.setUsername(share.getUsername());
        result.setCreated(jaxbWriter.convertDate(share.getCreated()));
        result.setVisitCount(share.getVisitCount());
        result.setDescription(share.getDescription());
        result.setExpires(jaxbWriter.convertDate(share.getExpires()));
        result.setLastVisited(jaxbWriter.convertDate(share.getLastVisited()));
        return result;
    }

    @RequestMapping({ "/getCoverArt", "/getCoverArt.view" })
    public void getCoverArt(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException, IOException {
        HttpServletRequest request = wrapRequest(req);
        coverArtController.handleRequest(request, response);
    }

    @RequestMapping({ "/getAvatar", "/getAvatar.view" })
    public void getAvatar(HttpServletRequest req, HttpServletResponse response) throws IOException {
        HttpServletRequest request = wrapRequest(req);
        avatarController.handleRequest(request, response);
    }

    @RequestMapping({ "/changePassword", "/changePassword.view" })
    public void changePassword(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {

        HttpServletRequest request = wrapRequest(req);
        User authUser = userService.getCurrentUserStrict(request);
        String username = ServletRequestUtils
            .getRequiredStringParameter(request, Attributes.Request.USER_NAME.value());
        boolean allowed = authUser.isAdminRole()
                || username.equals(authUser.getUsername()) && authUser.isSettingsRole();
        if (!allowed) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED, authUser.getUsername()
                    + " is not authorized to change password for " + username);
            return;
        }

        User user = userService.getUserByName(username);
        if (user == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, MSG_NO_USER + username);
            return;
        }
        String newPass = decrypt(ServletRequestUtils
            .getRequiredStringParameter(request, Attributes.Request.PASSWORD.value()));
        userService.updatePassword(user, newPass, user.isLdapAuthenticated());
        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/getUser", "/getUser.view" })
    public void getUser(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);

        String username = ServletRequestUtils
            .getRequiredStringParameter(request, Attributes.Request.USER_NAME.value());

        User currentUser = userService.getCurrentUserStrict(request);
        if (!username.equals(currentUser.getUsername()) && !currentUser.isAdminRole()) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED, currentUser.getUsername()
                    + " is not authorized to get details for other users.");
            return;
        }

        User requestedUser = userService.getUserByName(username);
        if (requestedUser == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, MSG_NO_USER + username);
            return;
        }

        Response res = createResponse();
        res.setUser(createJaxbUser(requestedUser));
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getUsers", "/getUsers.view" })
    public void getUsers(HttpServletRequest req, HttpServletResponse response) {
        HttpServletRequest request = wrapRequest(req);

        User currentUser = userService.getCurrentUserStrict(request);
        if (!currentUser.isAdminRole()) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED, currentUser.getUsername()
                    + " is not authorized to get details for other users.");
            return;
        }

        Users result = new Users();
        for (User user : userService.getAllUsers()) {
            result.getUser().add(createJaxbUser(user));
        }

        Response res = createResponse();
        res.setUsers(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    private org.subsonic.restapi.User createJaxbUser(User user) {
        UserSettings userSettings = userService.getUserSettings(user.getUsername());

        org.subsonic.restapi.User result = new org.subsonic.restapi.User();
        result.setUsername(user.getUsername());
        result.setEmail(user.getEmail());
        result.setScrobblingEnabled(userSettings.isLastFmEnabled());
        result.setAdminRole(user.isAdminRole());
        result.setSettingsRole(user.isSettingsRole());
        result.setDownloadRole(user.isDownloadRole());
        result.setUploadRole(user.isUploadRole());
        result.setPlaylistRole(true); // Since 1.8.0
        result.setCoverArtRole(user.isCoverArtRole());
        result.setCommentRole(user.isCommentRole());
        result.setPodcastRole(user.isPodcastRole());
        result.setStreamRole(user.isStreamRole());
        result.setJukeboxRole(false);
        result.setShareRole(user.isShareRole());
        // currently this role isn't supported by airsonic
        result.setVideoConversionRole(false);
        // Useless
        result.setAvatarLastChanged(null);

        BitRateLimit bitRateLimit = userSettings.getBitRateLimit();
        if (bitRateLimit != null && bitRateLimit != BitRateLimit.OFF) {
            result.setMaxBitRate(bitRateLimit.getMaxBitRate());
        }

        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> musicFolders = musicFolderService
            .getMusicFoldersForUser(user.getUsername());
        for (com.tesshu.jpsonic.persistence.api.entity.MusicFolder musicFolder : musicFolders) {
            result.getFolder().add(musicFolder.getId());
        }
        return result;
    }

    @RequestMapping({ "/createUser", "/createUser.view" })
    public void createUser(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        User user = userService.getCurrentUserStrict(request);
        if (!user.isAdminRole()) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    user.getUsername() + " is not authorized to create new users.");
            return;
        }

        UserSettingsCommand command = new UserSettingsCommand();
        command
            .setUsername(ServletRequestUtils
                .getRequiredStringParameter(request, Attributes.Request.USER_NAME.value()));
        command
            .setPassword(decrypt(ServletRequestUtils
                .getRequiredStringParameter(request, Attributes.Request.PASSWORD.value())));
        command
            .setEmail(ServletRequestUtils
                .getRequiredStringParameter(request, Attributes.Request.EMAIL.value()));
        command
            .setLdapAuthenticated(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.LDAP_AUTHENTICATED.value(),
                        false));
        command
            .setAdminRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.ADMIN_ROLE.value(), false));
        command
            .setCommentRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.COMMENT_ROLE.value(), false));
        command
            .setCoverArtRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.COVER_ART_ROLE.value(), false));
        command
            .setDownloadRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.DOWNLOAD_ROLE.value(), false));
        command
            .setStreamRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.STREAM_ROLE.value(), true));
        command
            .setUploadRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.UPLOAD_ROLE.value(), false));
        command
            .setPodcastRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.PODCAST_ROLE.value(), false));
        command
            .setSettingsRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.SETTINGS_ROLE.value(), true));
        command
            .setShareRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.SHARE_ROLE.value(), false));
        command.setBitRateLimit(BitRateLimit.OFF);

        int[] folderIds = ServletRequestUtils
            .getIntParameters(request, Attributes.Request.MUSIC_FOLDER_ID.value());
        if (folderIds.length == 0) {
            folderIds = PlayerUtils
                .toIntArray(com.tesshu.jpsonic.persistence.api.entity.MusicFolder
                    .toIdList(musicFolderService.getAllMusicFolders()));
        }
        command.setAllowedMusicFolderIds(folderIds);

        userSettingsController.createUser(command);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/updateUser", "/updateUser.view" })
    public void updateUser(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {

        HttpServletRequest request = wrapRequest(req);
        User user = userService.getCurrentUserStrict(request);
        if (!user.isAdminRole()) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    user.getUsername() + " is not authorized to update users.");
            return;
        }

        String username = ServletRequestUtils
            .getRequiredStringParameter(request, Attributes.Request.USER_NAME.value());
        User u = userService.getUserByName(username);
        if (u == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, MSG_NO_USER + username);
            return;
        } else if (user.getUsername().equals(username)) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    "Not allowed to change own user");
            return;
        } else if (userService.isAdmin(username)) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    "Not allowed to change admin user");
            return;
        }

        UserSettingsCommand command = new UserSettingsCommand();
        command.setUsername(username);
        command
            .setEmail(ServletRequestUtils
                .getStringParameter(request, Attributes.Request.EMAIL.value(), u.getEmail()));
        command
            .setLdapAuthenticated(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.LDAP_AUTHENTICATED.value(),
                        u.isLdapAuthenticated()));
        command
            .setAdminRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.ADMIN_ROLE.value(),
                        u.isAdminRole()));
        command
            .setCommentRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.COMMENT_ROLE.value(),
                        u.isCommentRole()));
        command
            .setCoverArtRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.COVER_ART_ROLE.value(),
                        u.isCoverArtRole()));
        command
            .setDownloadRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.DOWNLOAD_ROLE.value(),
                        u.isDownloadRole()));
        command
            .setStreamRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.STREAM_ROLE.value(),
                        u.isDownloadRole()));
        command
            .setUploadRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.UPLOAD_ROLE.value(),
                        u.isUploadRole()));
        command
            .setPodcastRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.PODCAST_ROLE.value(),
                        u.isPodcastRole()));
        command
            .setSettingsRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.SETTINGS_ROLE.value(),
                        u.isSettingsRole()));
        command
            .setShareRole(ServletRequestUtils
                .getBooleanParameter(request, Attributes.Request.SHARE_ROLE.value(),
                        u.isShareRole()));

        UserSettings s = userService.getUserSettings(username);
        int maxBitRate = ServletRequestUtils
            .getIntParameter(request, Attributes.Request.MAX_BIT_RATE.value(),
                    s.getBitRateLimit().getMaxBitRate());
        BitRateLimit bitRateLimit = BitRateLimit.fromMaxBitRate(maxBitRate);
        if (bitRateLimit != null) {
            command.setBitRateLimit(bitRateLimit);
        }

        if (hasParameter(request, Attributes.Request.PASSWORD.value())) {
            command
                .setPassword(decrypt(ServletRequestUtils
                    .getRequiredStringParameter(request, Attributes.Request.PASSWORD.value())));
            command.setPasswordChange(true);
        }

        int[] folderIds = ServletRequestUtils
            .getIntParameters(request, Attributes.Request.MUSIC_FOLDER_ID.value());
        if (folderIds.length == 0) {
            folderIds = PlayerUtils
                .toIntArray(com.tesshu.jpsonic.persistence.api.entity.MusicFolder
                    .toIdList(musicFolderService.getMusicFoldersForUser(username)));
        }
        command.setAllowedMusicFolderIds(folderIds);

        userSettingsController.updateUser(command);
        writeEmptyResponse(request, response);
    }

    private boolean hasParameter(HttpServletRequest request, String name) {
        return request.getParameter(name) != null;
    }

    @RequestMapping({ "/deleteUser", "/deleteUser.view" })
    public void deleteUser(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        User user = userService.getCurrentUserStrict(request);
        if (!user.isAdminRole()) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    user.getUsername() + " is not authorized to delete users.");
            return;
        }

        String username = ServletRequestUtils
            .getRequiredStringParameter(request, Attributes.Request.USER_NAME.value());
        User u = userService.getUserByName(username);

        if (u == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, MSG_NO_USER + username);
            return;
        } else if (user.getUsername().equals(username)) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    "Not allowed to delete own user");
            return;
        } else if (userService.isAdmin(username)) {
            writeError(request, response, ErrorCode.NOT_AUTHORIZED,
                    "Not allowed to delete admin user");
            return;
        }

        userService.deleteUser(username);

        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/getChatMessages", "/getChatMessages.view" })
    public ResponseEntity<String> getChatMessages(HttpServletRequest req,
            HttpServletResponse response) {
        return ResponseEntity.status(HttpStatus.SC_GONE).body(NO_LONGER_SUPPORTED);
    }

    @RequestMapping({ "/addChatMessage", "/addChatMessage.view" })
    public ResponseEntity<String> addChatMessage(HttpServletRequest req,
            HttpServletResponse response) {
        return ResponseEntity.status(HttpStatus.SC_GONE).body(NO_LONGER_SUPPORTED);
    }

    @RequestMapping({ "/getLyrics", "/getLyrics.view" })
    public void getLyrics(HttpServletRequest req, HttpServletResponse response) {
        HttpServletRequest request = wrapRequest(req);
        String artist = request.getParameter(Attributes.Request.ARTIST.value());
        String title = request.getParameter(Attributes.Request.TITLE.value());
        LyricsInfo lyrics = lyricsService.getLyrics(artist, title);

        Lyrics result = new Lyrics();
        result.setArtist(lyrics.getArtist());
        result.setTitle(lyrics.getTitle());
        result.setContent(lyrics.getLyrics());

        Response res = createResponse();
        res.setLyrics(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @SuppressWarnings("PMD.NullAssignment") // (rating) Intentional allocation to register null
    @RequestMapping({ "/setRating", "/setRating.view" })
    public void setRating(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);
        Integer rating = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.RATING.value());
        if (rating == 0) {
            rating = null;
        }

        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());
        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            writeError(request, response, ErrorCode.NOT_FOUND, "File not found: " + id);
            return;
        }

        String username = userService.getCurrentUsername(request);
        ratingService.setRatingForUser(username, mediaFile, rating);

        writeEmptyResponse(request, response);
    }

    @RequestMapping({ "/getAlbumInfo", "/getAlbumInfo.view" })
    public void getAlbumInfo(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);

        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());

        MediaFile mediaFile = this.mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            writeError(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND,
                    "Media file not found.");
            return;
        }
        AlbumNotes albumNotes = this.lastFmService.getAlbumNotes(mediaFile);

        AlbumInfo result = getAlbumInfoInternal(albumNotes);
        Response res = createResponse();
        res.setAlbumInfo(result);
        this.jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({ "/getAlbumInfo2", "/getAlbumInfo2.view" })
    public void getAlbumInfo2(HttpServletRequest req, HttpServletResponse response)
            throws ServletRequestBindingException {
        HttpServletRequest request = wrapRequest(req);

        int id = ServletRequestUtils
            .getRequiredIntParameter(request, Attributes.Request.ID.value());

        Album album = this.albumDao.getAlbum(id);
        if (album == null) {
            writeError(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND,
                    "Album not found.");
            return;
        }
        AlbumNotes albumNotes = this.lastFmService.getAlbumNotes(album);

        AlbumInfo result = getAlbumInfoInternal(albumNotes);
        Response res = createResponse();
        res.setAlbumInfo(result);
        this.jaxbWriter.writeResponse(request, response, res);
    }

    private AlbumInfo getAlbumInfoInternal(AlbumNotes albumNotes) {
        AlbumInfo result = new AlbumInfo();
        if (albumNotes != null) {
            result.setNotes(albumNotes.getNotes());
            result.setMusicBrainzId(albumNotes.getMusicBrainzId());
            result.setLastFmUrl(albumNotes.getLastFmUrl());
            result.setSmallImageUrl(albumNotes.getSmallImageUrl());
            result.setMediumImageUrl(albumNotes.getMediumImageUrl());
            result.setLargeImageUrl(albumNotes.getLargeImageUrl());
        }
        return result;
    }

    @RequestMapping({ "/getVideoInfo", "/getVideoInfo.view" })
    public ResponseEntity<String> getVideoInfo() {
        return ResponseEntity.status(HttpStatus.SC_NOT_IMPLEMENTED).body(NOT_YET_IMPLEMENTED);
    }

    @RequestMapping({ "/getCaptions", "/getCaptions.view" })
    public ResponseEntity<String> getCaptions() {
        return ResponseEntity.status(HttpStatus.SC_NOT_IMPLEMENTED).body(NOT_YET_IMPLEMENTED);
    }

    @RequestMapping({ "/startScan", "/startScan.view" })
    public void startScan(HttpServletRequest req, HttpServletResponse response) {
        HttpServletRequest request = wrapRequest(req);
        mediaScannerService.scanLibrary();
        getScanStatus(request, response);
    }

    @RequestMapping({ "/getScanStatus", "/getScanStatus.view" })
    public void getScanStatus(HttpServletRequest req, HttpServletResponse response) {
        ScanStatus scanStatus = new ScanStatus();
        scanStatus.setScanning(this.mediaScannerService.isScanning());
        scanStatus.setCount(this.mediaScannerService.getScanCount());

        Response res = createResponse();
        res.setScanStatus(scanStatus);
        HttpServletRequest request = wrapRequest(req);
        this.jaxbWriter.writeResponse(request, response, res);
    }

    private HttpServletRequest wrapRequest(final HttpServletRequest request) {
        final String playerId = createPlayerIfNecessary(request);
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                // Returns the correct player to be used in PlayerService.getPlayer()
                if (Attributes.Request.PLAYER.value().equals(name)) {
                    return playerId;
                }

                // Support old style ID parameters.
                if (Attributes.Request.ID.value().equals(name)) {
                    return mapId(request.getParameter(Attributes.Request.ID.value()));
                }

                return super.getParameter(name);
            }
        };
    }

    protected final String mapId(String id) {
        CoverArtType coverArtType = CoverArtType.detect(id);
        if (id == null || coverArtType == CoverArtType.ARTIST
                || coverArtType == CoverArtType.ID3ALBUM || StringUtils.isNumeric(id)) {
            return id;
        }
        try {
            String path = StringUtil.utf8HexDecode(id);
            MediaFile mediaFile = mediaFileService.getMediaFile(path);
            if (mediaFile == null) {
                return id;
            }
            return String.valueOf(mediaFile.getId());
        } catch (DecoderException e) {
            return id;
        }
    }

    private Response createResponse() {
        return jaxbWriter.createResponse(true);
    }

    private void writeEmptyResponse(HttpServletRequest request, HttpServletResponse response) {
        jaxbWriter.writeResponse(request, response, createResponse());
    }

    public void writeError(HttpServletRequest request, HttpServletResponse response, ErrorCode code,
            String message) {
        jaxbWriter.writeErrorResponse(request, response, code, message);
    }

    private String createPlayerIfNecessary(HttpServletRequest request) {
        String username = request.getRemoteUser();
        String clientId = request.getParameter(Attributes.Request.C.value());

        List<Player> players = playerService.getPlayersForUserAndClientId(username, clientId);

        // If not found, create it.
        if (players.isEmpty()) {
            Player player = new Player();
            player.setIpAddress(request.getRemoteAddr());
            player.setUsername(username);
            player.setClientId(clientId);
            player.setName(clientId);
            playerService.createPlayer(player);
            players = playerService.getPlayersForUserAndClientId(username, clientId);
        }

        // Return the player ID.
        return players.isEmpty() ? null : String.valueOf(players.get(0).getId());
    }

    public enum ErrorCode {

        GENERIC(0, "A generic error."), MISSING_PARAMETER(10, "Required parameter is missing."),
        PROTOCOL_MISMATCH_CLIENT_TOO_OLD(20,
                "Incompatible Jpsonic REST protocol version. Client must upgrade."),
        PROTOCOL_MISMATCH_SERVER_TOO_OLD(30,
                "Incompatible Jpsonic REST protocol version. Server must upgrade."),
        NOT_AUTHENTICATED(40, "Wrong username or password."),
        NOT_AUTHORIZED(50, "User is not authorized for the given operation."),
        NOT_FOUND(70, "Requested data was not found.");

        private final int code;
        private final String message;

        ErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}

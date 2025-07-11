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
 * (C) 2014 Sindre Mehus
 * (C) 2016 Airsonic Authors
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.dao.ArtistDao;
import com.tesshu.jpsonic.dao.MediaFileDao;
import com.tesshu.jpsonic.domain.AlbumNotes;
import com.tesshu.jpsonic.domain.ArtistBio;
import com.tesshu.jpsonic.domain.LastFmCoverArt;
import com.tesshu.jpsonic.domain.MediaFile;
import com.tesshu.jpsonic.domain.MusicFolder;
import de.umass.lastfm.Album;
import de.umass.lastfm.Artist;
import de.umass.lastfm.CallException;
import de.umass.lastfm.Caller;
import de.umass.lastfm.ImageSize;
import de.umass.lastfm.Track;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Provides services from the Last.fm REST API.
 *
 * @author Sindre Mehus
 */
@Service
public class LastFmService {

    private static final String LAST_FM_KEY = "ece4499898a9440896dfdce5dab26bbf";
    private static final long CACHE_TIME_TO_LIVE_MILLIS = 6 * 30 * 24 * 3600 * 1000L; // 6 months
    private static final Logger LOG = LoggerFactory.getLogger(LastFmService.class);

    private final MediaFileDao mediaFileDao;
    private final MediaFileService mediaFileService;
    private final ArtistDao artistDao;

    public LastFmService(MediaFileDao mediaFileDao, MediaFileService mediaFileService,
            ArtistDao artistDao) {
        super();
        this.mediaFileDao = mediaFileDao;
        this.mediaFileService = mediaFileService;
        this.artistDao = artistDao;
    }

    @PostConstruct
    public void init() {
        Caller caller = Caller.getInstance();
        caller.setUserAgent("Airsonic");

        Path cacheDir = Path.of(SettingsService.getJpsonicHome().toString(), "lastfmcache");
        caller.setCache(new LastFmCache(cacheDir, CACHE_TIME_TO_LIVE_MILLIS));
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // (MediaFile) Not reusable
    private List<MediaFile> fillUpWithNonPresentArtists(List<MediaFile> result,
            Collection<Artist> similarArtists, int count, List<MusicFolder> musicFolders) {
        for (Iterator<Artist> i = similarArtists.iterator(); i.hasNext()
                && result.size() != count;) {
            Artist lastFmArtist = i.next();
            MediaFile similarArtist = mediaFileDao
                .getArtistByName(lastFmArtist.getName(), musicFolders);
            if (similarArtist == null) {
                MediaFile notPresentArtist = new MediaFile();
                notPresentArtist.setId(-1);
                notPresentArtist.setArtist(lastFmArtist.getName());
                result.add(notPresentArtist);
            }
        }
        return result;
    }

    /**
     * Returns similar artists, using last.fm REST API.
     *
     * @param mediaFile         The media file (song, album or artist).
     * @param count             Max number of similar artists to return.
     * @param includeNotPresent Whether to include artists that are not present in
     *                          the media library.
     * @param musicFolders      Only return artists present in these folders.
     *
     * @return Similar artists, ordered by presence then similarity.
     */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // (MediaFile) Not reusable
    public List<MediaFile> getSimilarArtists(MediaFile mediaFile, int count,
            boolean includeNotPresent, List<MusicFolder> musicFolders) {
        List<MediaFile> result = new ArrayList<>();
        if (mediaFile == null) {
            return result;
        }

        String artistName = getArtistName(mediaFile);
        Collection<Artist> similarArtists;
        try {
            similarArtists = Artist.getSimilar(getCanonicalArtistName(artistName), LAST_FM_KEY);
        } catch (CallException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to find similar artists for " + artistName, e);
            }
            return result;
        }

        // First select artists that are present.
        for (Artist lastFmArtist : similarArtists) {
            MediaFile similarArtist = mediaFileDao
                .getArtistByName(lastFmArtist.getName(), musicFolders);
            if (similarArtist != null) {
                result.add(similarArtist);
            }
            if (result.size() == count) {
                return result;
            }
        }

        if (!includeNotPresent) {
            return result;
        }

        // Then fill up with non-present artists
        return fillUpWithNonPresentArtists(result, similarArtists, count, musicFolders);
    }

    /**
     * Returns similar artists, using last.fm REST API.
     *
     * @param artist            The artist.
     * @param count             Max number of similar artists to return.
     * @param includeNotPresent Whether to include artists that are not present in
     *                          the media library.
     * @param musicFolders      Only return songs from artists in these folders.
     *
     * @return Similar artists, ordered by presence then similarity.
     */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // (Artist) Not reusable
    public List<com.tesshu.jpsonic.domain.Artist> getSimilarArtists(
            com.tesshu.jpsonic.domain.Artist artist, int count, boolean includeNotPresent,
            List<MusicFolder> musicFolders) {
        List<com.tesshu.jpsonic.domain.Artist> result = new ArrayList<>();

        Collection<Artist> similarArtists;
        try {
            // First select artists that are present.
            similarArtists = Artist
                .getSimilar(getCanonicalArtistName(artist.getName()), LAST_FM_KEY);
        } catch (CallException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to find similar artists for " + artist.getName(), e);
            }
            return result;
        }

        for (Artist lastFmArtist : similarArtists) {
            com.tesshu.jpsonic.domain.Artist similarArtist = artistDao
                .getArtist(lastFmArtist.getName(), musicFolders);
            if (similarArtist != null) {
                result.add(similarArtist);
                if (result.size() == count) {
                    return result;
                }
            }
        }

        if (!includeNotPresent) {
            return result;
        }

        // Then fill up with non-present artists
        for (Iterator<Artist> i = similarArtists.iterator(); i.hasNext()
                && result.size() != count;) {
            Artist lastFmArtist = i.next();
            com.tesshu.jpsonic.domain.Artist similarArtist = artistDao
                .getArtist(lastFmArtist.getName());
            if (similarArtist == null) {
                com.tesshu.jpsonic.domain.Artist notPresentArtist = new com.tesshu.jpsonic.domain.Artist();
                notPresentArtist.setId(-1);
                notPresentArtist.setName(lastFmArtist.getName());
                result.add(notPresentArtist);
            }
        }
        return result;
    }

    /**
     * Returns songs from similar artists, using last.fm REST API. Typically used
     * for artist radio features.
     *
     * @param artist       The artist.
     * @param count        Max number of songs to return.
     * @param musicFolders Only return songs from artists in these folders.
     *
     * @return Songs from similar artists;
     */
    public List<MediaFile> getSimilarSongs(com.tesshu.jpsonic.domain.Artist artist, int count,
            List<MusicFolder> musicFolders) {

        List<MediaFile> similarSongs = new ArrayList<>(
                mediaFileDao.getSongsByArtist(artist.getName(), 0, 1000));
        for (com.tesshu.jpsonic.domain.Artist similarArtist : getSimilarArtists(artist, 100, false,
                musicFolders)) {
            similarSongs.addAll(mediaFileDao.getSongsByArtist(similarArtist.getName(), 0, 1000));
        }
        Collections.shuffle(similarSongs);
        return similarSongs.subList(0, Math.min(count, similarSongs.size()));
    }

    /**
     * Returns songs from similar artists, using last.fm REST API. Typically used
     * for artist radio features.
     *
     * @param mediaFile    The media file (song, album or artist).
     * @param count        Max number of songs to return.
     * @param musicFolders Only return songs from artists present in these folders.
     *
     * @return Songs from similar artists;
     */
    public List<MediaFile> getSimilarSongs(MediaFile mediaFile, int count,
            List<MusicFolder> musicFolders) {
        List<MediaFile> similarSongs = new ArrayList<>();

        String artistName = getArtistName(mediaFile);
        MediaFile artist = mediaFileDao.getArtistByName(artistName, musicFolders);
        if (artist != null) {
            similarSongs.addAll(mediaFileService.getRandomSongsForParent(artist, count));
        }

        for (MediaFile similarArtist : getSimilarArtists(mediaFile, 100, false, musicFolders)) {
            similarSongs.addAll(mediaFileService.getRandomSongsForParent(similarArtist, count));
        }
        Collections.shuffle(similarSongs);
        return similarSongs.subList(0, Math.min(count, similarSongs.size()));
    }

    /**
     * Returns artist bio and images.
     *
     * @param mediaFile The media file (song, album or artist).
     *
     * @return Artist bio.
     */
    public @Nullable ArtistBio getArtistBio(MediaFile mediaFile, Locale locale) {
        return getArtistBio(getCanonicalArtistName(getArtistName(mediaFile)), locale);
    }

    /**
     * Returns artist bio and images.
     *
     * @param artist The artist.
     *
     * @return Artist bio.
     */
    public ArtistBio getArtistBio(com.tesshu.jpsonic.domain.Artist artist, Locale locale) {
        return getArtistBio(getCanonicalArtistName(artist.getName()), locale);
    }

    private @Nullable ArtistBio getArtistBio(String artistName, Locale locale) {
        if (artistName == null) {
            return null;
        }

        Artist info;
        try {
            info = Artist.getInfo(artistName, locale, null /* username */, LAST_FM_KEY);
        } catch (CallException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to find artist bio for " + artistName, e);
            }
            return null;
        }

        if (info == null) {
            return null;
        }

        return new ArtistBio(processWikiText(info.getWikiSummary()), info.getMbid(), info.getUrl(),
                info.getImageURL(ImageSize.MEDIUM), info.getImageURL(ImageSize.LARGE),
                info.getImageURL(ImageSize.MEGA));
    }

    /**
     * Returns top songs for the given artist, using last.fm REST API.
     *
     * @param artist       The artist.
     * @param count        Max number of songs to return.
     * @param musicFolders Only return songs present in these folders.
     *
     * @return Top songs for artist.
     */
    public List<MediaFile> getTopSongs(MediaFile artist, int count,
            List<MusicFolder> musicFolders) {
        return getTopSongs(artist.getName(), count, musicFolders);
    }

    /**
     * Returns top songs for the given artist, using last.fm REST API.
     *
     * @param artistName   The artist name.
     * @param count        Max number of songs to return.
     * @param musicFolders Only return songs present in these folders.
     *
     * @return Top songs for artist.
     */
    public List<MediaFile> getTopSongs(String artistName, int count,
            List<MusicFolder> musicFolders) {
        List<MediaFile> result = new ArrayList<>();

        if (StringUtils.isBlank(artistName) || count <= 0) {
            return result;
        }

        for (Track topTrack : Artist.getTopTracks(artistName, LAST_FM_KEY)) {
            MediaFile song = mediaFileDao
                .getSongByArtistAndTitle(artistName, topTrack.getName(), musicFolders);
            if (song != null) {
                result.add(song);
                if (result.size() == count) {
                    return result;
                }
            }
        }
        return result;
    }

    /**
     * Returns album notes and images.
     *
     * @param mediaFile The media file (song or album).
     *
     * @return Album notes.
     */
    public AlbumNotes getAlbumNotes(MediaFile mediaFile) {
        return getAlbumNotes(getCanonicalArtistName(getArtistName(mediaFile)),
                mediaFile.getAlbumName());
    }

    /**
     * Returns album notes and images.
     *
     * @param album The album.
     *
     * @return Album notes.
     */
    public AlbumNotes getAlbumNotes(com.tesshu.jpsonic.domain.Album album) {
        return getAlbumNotes(getCanonicalArtistName(album.getArtist()), album.getName());
    }

    /**
     * Returns album notes and images.
     *
     * @param artist The artist name.
     * @param album  The album name.
     *
     * @return Album notes.
     */
    private AlbumNotes getAlbumNotes(String artist, String album) {
        if (artist == null || album == null) {
            return null;
        }

        Album info;
        try {
            info = Album.getInfo(artist, album, LAST_FM_KEY);
        } catch (CallException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to find album notes for " + artist + " - " + album, e);
            }
            return null;
        }

        if (info == null) {
            return null;
        }

        return new AlbumNotes(processWikiText(info.getWikiSummary()), info.getMbid(), info.getUrl(),
                info.getImageURL(ImageSize.MEDIUM), info.getImageURL(ImageSize.LARGE),
                info.getImageURL(ImageSize.MEGA));
    }

    public List<LastFmCoverArt> searchCoverArt(String artist, String album) {
        if (artist == null && album == null) {
            return Collections.emptyList();
        }
        StringBuilder query = new StringBuilder();
        if (artist != null) {
            query.append(artist).append(' ');
        }
        if (album != null) {
            query.append(album);
        }

        Collection<Album> matches;
        try {
            matches = Album.search(query.toString(), LAST_FM_KEY);
        } catch (CallException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to search for cover art for " + artist + " - " + album, e);
            }
            return Collections.emptyList();
        }

        return matches
            .stream()
            .map(this::convert)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private LastFmCoverArt convert(Album album) {
        String imageUrl = null;
        List<ImageSize> imageSizes = Arrays.asList(ImageSize.values());
        Collections.reverse(imageSizes);
        for (ImageSize imageSize : imageSizes) {
            imageUrl = StringUtils.trimToNull(album.getImageURL(imageSize));
            if (imageUrl != null) {
                break;
            }
        }

        return imageUrl == null ? null
                : new LastFmCoverArt(imageUrl, album.getArtist(), album.getName());
    }

    private String getCanonicalArtistName(String artistName) {
        if (artistName == null) {
            return null;
        }

        Artist info;
        try {
            info = Artist.getInfo(artistName, LAST_FM_KEY);
        } catch (CallException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to find artist bio for " + artistName, e);
            }
            return null;
        }

        if (info == null) {
            return null;
        }

        String biography = processWikiText(info.getWikiSummary());
        String redirectedArtistName = getRedirectedArtist(biography);
        return redirectedArtistName == null ? artistName : redirectedArtistName;
    }

    private String getRedirectedArtist(String biography) {
        /*
         * This is mistagged for <a target='_blank'
         * href="http://www.last.fm/music/The+Boomtown+Rats" class="bbcode_artist">The
         * Boomtown Rats</a>; it would help Last.fm if you could correct your tags. <a
         * target='_blank'
         * href="http://www.last.fm/music/+noredirect/Boomtown+Rats">Boomtown Rats on
         * Last.fm</a>.<p> -- or --<p> Fix your tags to <a target='_blank'
         * href="http://www.last.fm/music/The+Chemical+Brothers"
         * class="bbcode_artist">The Chemical Brothers</a> <a target='_blank'
         * href="http://www.last.fm/music/+noredirect/Chemical+Brothers">Chemical
         * Brothers on Last.fm</a>.
         */

        if (biography == null) {
            return null;
        }
        Pattern pattern = Pattern
            .compile(
                    "((This is mistagged for)|(Fix your tags to)).*class=\"bbcode_artist\">(.*?)</a>");
        Matcher matcher = pattern.matcher(biography);
        if (matcher.find()) {
            return matcher.group(4);
        }
        return null;
    }

    private String processWikiText(final String text) {
        /*
         * System of a Down is an Armenian American <a
         * href="http://www.last.fm/tag/alternative%20metal" class="bbcode_tag"
         * rel="tag">alternative metal</a> band, formed in 1994 in Los Angeles,
         * California, USA. All four members are of Armenian descent, and are widely
         * known for their outspoken views expressed in many of their songs confronting
         * the Armenian Genocide of 1915 by the Ottoman Empire and the ongoing War on
         * Terror by the US government. The band consists of <a
         * href="http://www.last.fm/music/Serj+Tankian" class="bbcode_artist">Serj
         * Tankian</a> (vocals), Daron Malakian (vocals, guitar), Shavo Odadjian (bass,
         * vocals) and John Dolmayan (drums). <a
         * href="http://www.last.fm/music/System+of+a+Down">Read more about System of a
         * Down on Last.fm</a>. User-contributed text is available under the Creative
         * Commons By-SA License and may also be available under the GNU FDL.
         */
        String result = text;
        result = result.replaceAll("User-contributed text.*", "");
        result = result.replaceAll("<a ", "<a target='_blank' ");
        result = result.replace("\n", " ");
        result = StringUtils.trimToNull(result);

        if (result != null && result.startsWith("This is an incorrect tag")) {
            return null;
        }

        return result;
    }

    private String getArtistName(MediaFile mediaFile) {
        String artistName = mediaFile.getName();
        if (mediaFile.isAlbum() || mediaFile.isFile()) {
            artistName = mediaFile.getAlbumArtist() == null ? mediaFile.getArtist()
                    : mediaFile.getAlbumArtist();
        }
        return artistName;
    }
}

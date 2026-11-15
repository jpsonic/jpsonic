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

package com.tesshu.jpsonic.persistence.core.repository;

import static com.tesshu.jpsonic.persistence.base.DaoUtils.nullableInstantOf;
import static com.tesshu.jpsonic.persistence.base.DaoUtils.questionMarks;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.tesshu.jpsonic.domain.model.TranscodingDefinition.BitRateLimit;
import com.tesshu.jpsonic.domain.system.AlbumListType;
import com.tesshu.jpsonic.domain.system.AvatarScheme;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.util.StringUtil;
import org.apache.commons.codec.DecoderException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides user-related database services.
 *
 * @author Sindre Mehus
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals") // Only DAO is allowed to exclude this rule #827
@Repository
@Transactional
public class UserDao {

    private static final Logger LOG = LoggerFactory.getLogger(UserDao.class);
    private static final String USER_COLUMNS = """
            username, password, email, ldap_authenticated, \
            bytes_streamed, bytes_downloaded, bytes_uploaded
            """;
    private static final String USER_SETTINGS_COLUMNS = """
            username, locale, theme_id, final_version_notification, beta_version_notification,
            song_notification, main_track_number, main_artist, main_album, main_genre,
            main_year, main_bit_rate, main_duration, main_format, main_file_size,
            playlist_track_number, playlist_artist, playlist_album, playlist_genre,
            playlist_year, playlist_bit_rate, playlist_duration, playlist_format,
            playlist_file_size, last_fm_enabled, last_fm_username, last_fm_password,
            listenbrainz_enabled, listenbrainz_token, transcode_scheme, show_now_playing,
            selected_music_folder_id, party_mode_enabled, now_playing_allowed,
            avatar_scheme, system_avatar_id, changed, show_artist_info, auto_hide_play_queue,
            view_as_list, default_album_list, queue_following_songs, show_side_bar,
            list_reload_delay, keyboard_shortcuts_enabled, pagination_size,
            main_composer, playlist_composer, close_drawer, close_play_queue,
            alternative_drawer, assign_accesskey_to_number, open_detail_index,
            open_detail_setting, open_detail_star, show_index,
            simple_display, show_sibling, show_rate, show_album_search, show_last_play,
            show_download, show_tag, show_comment, show_share, show_change_coverart,
            show_top_songs, show_similar, show_album_actions, breadcrumb_index,
            put_menu_in_drawer, font_scheme_name, show_outline_help, force_bio2eng,
            voice_input_enabled, show_current_song_info, speech_lang_scheme_name, ietf,
            font_family, font_size, show_scanned_count\s
            """;
    private static final int ROLE_ID_ADMIN = 1;
    private static final int ROLE_ID_DOWNLOAD = 2;
    private static final int ROLE_ID_UPLOAD = 3;
    private static final int ROLE_ID_PLAYLIST = 4;
    private static final int ROLE_ID_COVER_ART = 5;
    private static final int ROLE_ID_COMMENT = 6;
    private static final int ROLE_ID_PODCAST = 7;
    private static final int ROLE_ID_STREAM = 8;
    private static final int ROLE_ID_SETTINGS = 9;
    private static final int ROLE_ID_JUKEBOX = 10;
    private static final int ROLE_ID_SHARE = 11;
    private static final int SINGLE_USER = 1;

    private final TemplateWrapper template;
    private final String userTableQuote;
    private final UserRowMapper userRowMapper;
    private final UserSettingsRowMapper userSettingsRowMapper;
    private final DomainUserSettingsRowMapper domainUserSettingsRowMapper;

    public UserDao(TemplateWrapper templateWrapper, String userTableQuote) {
        template = templateWrapper;
        this.userTableQuote = userTableQuote;
        userRowMapper = new UserRowMapper();
        userSettingsRowMapper = new UserSettingsRowMapper();
        domainUserSettingsRowMapper = new DomainUserSettingsRowMapper();
    }

    /**
     * Returns the user with the given username.
     *
     * @param username      The username used when logging in.
     * @param caseSensitive If false, perform a case-insensitive search
     *
     * @return The user, or <code>null</code> if not found.
     */
    public @Nullable User getUserByName(String username, boolean caseSensitive) {
        String sql;
        if (caseSensitive) {
            sql = "select " + USER_COLUMNS + " from " + getUserTable() + " where username=?";
        } else {
            sql = "select " + USER_COLUMNS + " from " + getUserTable()
                    + " where UPPER(username)=UPPER(?)";
        }
        List<User> users = template.query(sql, userRowMapper, username);
        User user = null;
        if (users.size() == SINGLE_USER) {
            user = users.iterator().next();
        } else if (users.size() > SINGLE_USER) {
            throw new IllegalArgumentException("Too many matching users");
        }
        if (user != null) {
            readRoles(user);
        }
        return user;
    }

    /**
     * Returns the user with the given email address.
     *
     * @param email The email address.
     *
     * @return The user, or <code>null</code> if not found.
     */
    public User getUserByEmail(String email) {
        String sql = "select " + USER_COLUMNS + " from " + getUserTable() + " where email=?";
        User user = template.queryOne(sql, userRowMapper, email);
        if (user != null) {
            readRoles(user);
        }
        return user;
    }

    /**
     * Returns all users.
     *
     * @return Possibly empty array of all users.
     */
    public List<User> getAllUsers() {
        String sql = "select " + USER_COLUMNS + " from " + getUserTable();
        List<User> users = template.query(sql, userRowMapper);
        users.forEach(this::readRoles);
        return users;
    }

    /**
     * Creates a new user.
     *
     * @param user The user to create.
     */
    public void createUser(User user) {
        String sql = "insert into " + getUserTable() + " (" + USER_COLUMNS + ") values ("
                + questionMarks(USER_COLUMNS) + ')';
        template
            .update(sql, user.getUsername(), encrypt(user.getPassword()), user.getEmail(),
                    user.isLdapAuthenticated(), user.getBytesStreamed(), user.getBytesDownloaded(),
                    user.getBytesUploaded());
        writeRoles(user);
    }

    /**
     * Deletes the user with the given username.
     *
     * @param username The username.
     */
    public void deleteUser(String username) {
        template.update("delete from user_role where username=?", username);
        template.update("delete from player where username=?", username);
        template.update("delete from " + getUserTable() + " where username=?", username);
    }

    /**
     * Updates the given user.
     *
     * @param user The user to update.
     */
    public void updateUser(User user) {
        String sql = "update " + getUserTable()
                + " set password=?, email=?, ldap_authenticated=?, bytes_streamed=?, bytes_downloaded=?, bytes_uploaded=? "
                + "where username=?";
        template
            .update(sql, encrypt(user.getPassword()), user.getEmail(), user.isLdapAuthenticated(),
                    user.getBytesStreamed(), user.getBytesDownloaded(), user.getBytesUploaded(),
                    user.getUsername());
        writeRoles(user);
    }

    public void updatePassword(User user, String newPass, boolean ldapAuthenticated) {
        String sql = "update " + getUserTable()
                + " set password=?, ldap_authenticated=? where username=?";
        template.update(sql, newPass, ldapAuthenticated, user.getUsername());
    }

    public void incrementUserByteCounts(long bytesStreamedIncrement, long bytesDownloadedIncrement,
            long bytesUploadedIncrement, String username) {
        String sql = """
                update user
                set bytes_streamed = bytes_streamed + ?,
                    bytes_downloaded = bytes_downloaded + ?,
                    bytes_uploaded = bytes_uploaded + ?
                where username = ?
                """;
        template
            .update(sql, bytesStreamedIncrement, bytesDownloadedIncrement, bytesUploadedIncrement,
                    username);
    }

    /**
     * Returns the name of the roles for the given user.
     *
     * @param username The user name.
     *
     * @return Roles the user is granted.
     */
    public List<String> getRolesForUser(String username) {
        String sql = "select r.name from role r, user_role ur where ur.username=? and ur.role_id=r.id";
        return template.getJdbcTemplate().queryForList(sql, String.class, username);
    }

    /**
     * Returns settings for the given user.
     *
     * @param username The username.
     *
     * @return User-specific settings, or <code>null</code> if no such settings
     *         exist.
     */
    public UserSettings getUserSettings(String username) {
        String sql = "select " + USER_SETTINGS_COLUMNS + " from user_settings where username=?";
        return template.queryOne(sql, userSettingsRowMapper, username);
    }

    public com.tesshu.jpsonic.domain.model.UserSettings getDomainUserSettings(String username) {
        String sql = "select username, transcode_scheme from user_settings where username=?";
        return template.queryOne(sql, domainUserSettingsRowMapper, username);
    }

    /**
     * Updates settings for the given username, creating it if necessary.
     *
     * @param settings The user-specific settings.
     */
    public void updateUserSettings(UserSettings settings) {
        template.update("delete from user_settings where username=?", settings.getUsername());

        String sql = "insert into user_settings (" + USER_SETTINGS_COLUMNS + ") values ("
                + questionMarks(USER_SETTINGS_COLUMNS) + ')';
        String locale = settings.getLocale() == null ? null : settings.getLocale().toString();
        UserSettings.Visibility main = settings.getMainVisibility();
        UserSettings.Visibility playlist = settings.getPlaylistVisibility();
        template
            .update(sql, settings.getUsername(), locale, settings.getThemeId(),
                    settings.isFinalVersionNotificationEnabled(),
                    settings.isBetaVersionNotificationEnabled(),
                    settings.isSongNotificationEnabled(), main.isTrackNumberVisible(),
                    main.isArtistVisible(), main.isAlbumVisible(), main.isGenreVisible(),
                    main.isYearVisible(), main.isBitRateVisible(), main.isDurationVisible(),
                    main.isFormatVisible(), main.isFileSizeVisible(),
                    playlist.isTrackNumberVisible(), playlist.isArtistVisible(),
                    playlist.isAlbumVisible(), playlist.isGenreVisible(), playlist.isYearVisible(),
                    playlist.isBitRateVisible(), playlist.isDurationVisible(),
                    playlist.isFormatVisible(), playlist.isFileSizeVisible(),
                    settings.isLastFmEnabled(), settings.getLastFmUsername(),
                    encrypt(settings.getLastFmPassword()), settings.isListenBrainzEnabled(),
                    settings.getListenBrainzToken(), settings.getBitRateLimit().name(), false,
                    settings.getSelectedMusicFolderId(), settings.isPartyModeEnabled(),
                    settings.isNowPlayingAllowed(), settings.getAvatarScheme().name(),
                    settings.getSystemAvatarId(), settings.getChanged(),
                    settings.isShowArtistInfoEnabled(), settings.isAutoHidePlayQueue(),
                    settings.isViewAsList(), settings.getDefaultAlbumList().getId(),
                    settings.isQueueFollowingSongs(), settings.isCloseDrawer(),
                    60 /* Unused listReloadDelay */, settings.isKeyboardShortcutsEnabled(),
                    settings.getPaginationSize(), main.isComposerVisible(),
                    playlist.isComposerVisible(), settings.isCloseDrawer(),
                    settings.isClosePlayQueue(), settings.isAlternativeDrawer(),
                    settings.isAssignAccesskeyToNumber(), settings.isOpenDetailIndex(),
                    settings.isOpenDetailSetting(), settings.isOpenDetailStar(),
                    settings.isShowIndex(), settings.isSimpleDisplay(), settings.isShowSibling(),
                    settings.isShowRate(), settings.isShowAlbumSearch(), settings.isShowLastPlay(),
                    settings.isShowDownload(), settings.isShowTag(), settings.isShowComment(),
                    settings.isShowShare(), settings.isShowChangeCoverArt(),
                    settings.isShowTopSongs(), settings.isShowSimilar(),
                    settings.isShowAlbumActions(), settings.isBreadcrumbIndex(),
                    settings.isPutMenuInDrawer(), settings.getFontSchemeName(),
                    settings.isShowOutlineHelp(), settings.isForceBio2Eng(),
                    settings.isVoiceInputEnabled(), settings.isShowCurrentSongInfo(),
                    settings.getSpeechLangSchemeName(), settings.getIetf(),
                    settings.getFontFamily(), settings.getFontSize(),
                    settings.isShowScannedCount());
    }

    private static String encrypt(String s) {
        if (s == null) {
            return null;
        }
        return "enc:" + StringUtil.utf8HexEncode(s);
    }

    @Nullable
    protected static final String decrypt(String s) {
        if (s == null) {
            return null;
        }
        if (!s.startsWith("enc:")) {
            return s;
        }
        try {
            return StringUtil.utf8HexDecode(s.substring(4));
        } catch (DecoderException e) {
            return s;
        }
    }

    private void readRoles(User user) {
        String sql = "select role_id from user_role where username=?";
        List<?> roles = template
            .getJdbcTemplate()
            .queryForList(sql, Integer.class, user.getUsername());
        roles.forEach(role -> {
            switch ((Integer) role) {
            case ROLE_ID_ADMIN -> user.setAdminRole(true);
            case ROLE_ID_DOWNLOAD -> user.setDownloadRole(true);
            case ROLE_ID_UPLOAD -> user.setUploadRole(true);
            case ROLE_ID_PLAYLIST -> user.setPlaylistRole(true);
            case ROLE_ID_COVER_ART -> user.setCoverArtRole(true);
            case ROLE_ID_COMMENT -> user.setCommentRole(true);
            case ROLE_ID_PODCAST -> user.setPodcastRole(true);
            case ROLE_ID_STREAM -> user.setStreamRole(true);
            case ROLE_ID_SETTINGS -> user.setSettingsRole(true);
            case ROLE_ID_JUKEBOX -> LOG.trace("Discontinued role: {}", role);
            case ROLE_ID_SHARE -> user.setShareRole(true);
            default -> LOG.warn("Unknown role: {}", role);
            }
        });
    }

    @SuppressWarnings("PMD.NPathComplexity") // It's not particularly difficult, so you can leave it
                                             // as it is.
    private void writeRoles(User user) {
        template.update("delete from user_role where username=?", user.getUsername());
        if (user.isAdminRole()) {
            updateRole(user.getUsername(), ROLE_ID_ADMIN);
        }
        if (user.isDownloadRole()) {
            updateRole(user.getUsername(), ROLE_ID_DOWNLOAD);
        }
        if (user.isUploadRole()) {
            updateRole(user.getUsername(), ROLE_ID_UPLOAD);
        }
        if (user.isPlaylistRole()) {
            updateRole(user.getUsername(), ROLE_ID_PLAYLIST);
        }
        if (user.isCoverArtRole()) {
            updateRole(user.getUsername(), ROLE_ID_COVER_ART);
        }
        if (user.isCommentRole()) {
            updateRole(user.getUsername(), ROLE_ID_COMMENT);
        }
        if (user.isPodcastRole()) {
            updateRole(user.getUsername(), ROLE_ID_PODCAST);
        }
        if (user.isStreamRole()) {
            updateRole(user.getUsername(), ROLE_ID_STREAM);
        }
        if (user.isSettingsRole()) {
            updateRole(user.getUsername(), ROLE_ID_SETTINGS);
        }
        if (user.isShareRole()) {
            updateRole(user.getUsername(), ROLE_ID_SHARE);
        }
    }

    private void updateRole(String username, Integer role) {
        template.update("insert into user_role (username, role_id) values(?, ?)", username, role);
    }

    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new User(rs.getString(1), decrypt(rs.getString(2)), rs.getString(3),
                    rs.getBoolean(4), rs.getLong(5), rs.getLong(6), rs.getLong(7));
        }
    }

    @SuppressWarnings("PMD.AssignmentInOperand")
    private static class DomainUserSettingsRowMapper
            implements RowMapper<com.tesshu.jpsonic.domain.model.UserSettings> {
        @Override
        public com.tesshu.jpsonic.domain.model.UserSettings mapRow(ResultSet rs, int rowNum)
                throws SQLException {
            return new com.tesshu.jpsonic.domain.model.UserSettings(rs.getString(1),
                    BitRateLimit.of(rs.getString(2)));
        }
    }

    @SuppressWarnings("PMD.AssignmentInOperand")
    // Just use a simple int instead of LongAdder here.
    private static class UserSettingsRowMapper implements RowMapper<UserSettings> {
        @Override
        public UserSettings mapRow(ResultSet rs, int rowNum) throws SQLException {
            int col = 1;
            UserSettings settings = new UserSettings(rs.getString(col++));
            settings
                .setLocale(com.tesshu.jpsonic.domain.language.StringUtil
                    .parseLocale(rs.getString(col++)));
            settings.setThemeId(rs.getString(col++));
            settings.setFinalVersionNotificationEnabled(rs.getBoolean(col++));
            col++; // BetaVersionNotificationEnable
            settings.setSongNotificationEnabled(rs.getBoolean(col++));

            settings.getMainVisibility().setTrackNumberVisible(rs.getBoolean(col++));
            settings.getMainVisibility().setArtistVisible(rs.getBoolean(col++));
            settings.getMainVisibility().setAlbumVisible(rs.getBoolean(col++));
            settings.getMainVisibility().setGenreVisible(rs.getBoolean(col++));
            settings.getMainVisibility().setYearVisible(rs.getBoolean(col++));
            settings.getMainVisibility().setBitRateVisible(rs.getBoolean(col++));
            settings.getMainVisibility().setDurationVisible(rs.getBoolean(col++));
            settings.getMainVisibility().setFormatVisible(rs.getBoolean(col++));
            settings.getMainVisibility().setFileSizeVisible(rs.getBoolean(col++));

            settings.getPlaylistVisibility().setTrackNumberVisible(rs.getBoolean(col++));
            settings.getPlaylistVisibility().setArtistVisible(rs.getBoolean(col++));
            settings.getPlaylistVisibility().setAlbumVisible(rs.getBoolean(col++));
            settings.getPlaylistVisibility().setGenreVisible(rs.getBoolean(col++));
            settings.getPlaylistVisibility().setYearVisible(rs.getBoolean(col++));
            settings.getPlaylistVisibility().setBitRateVisible(rs.getBoolean(col++));
            settings.getPlaylistVisibility().setDurationVisible(rs.getBoolean(col++));
            settings.getPlaylistVisibility().setFormatVisible(rs.getBoolean(col++));
            settings.getPlaylistVisibility().setFileSizeVisible(rs.getBoolean(col++));

            settings.setLastFmEnabled(rs.getBoolean(col++));
            settings.setLastFmUsername(rs.getString(col++));
            settings.setLastFmPassword(decrypt(rs.getString(col++)));

            settings.setListenBrainzEnabled(rs.getBoolean(col++));
            settings.setListenBrainzToken(rs.getString(col++));

            settings.setBitRateLimit(BitRateLimit.of(rs.getString(col++)));
            col++; // showNowPlayingEnabled (Unused)
            settings.setSelectedMusicFolderId(rs.getInt(col++));
            settings.setPartyModeEnabled(rs.getBoolean(col++));
            settings.setNowPlayingAllowed(rs.getBoolean(col++));
            settings.setAvatarScheme(AvatarScheme.valueOf(rs.getString(col++)));
            settings.setSystemAvatarId((Integer) rs.getObject(col++));
            settings.setChanged(nullableInstantOf(rs.getTimestamp(col++)));
            settings.setShowArtistInfoEnabled(rs.getBoolean(col++));
            settings.setAutoHidePlayQueue(rs.getBoolean(col++));
            settings.setViewAsList(rs.getBoolean(col++));
            settings.setDefaultAlbumList(AlbumListType.fromId(rs.getString(col++)));
            settings.setQueueFollowingSongs(rs.getBoolean(col++));
            settings.setCloseDrawer(rs.getBoolean(col++));
            col++; // listReloadDelay (Unused)
            settings.setKeyboardShortcutsEnabled(rs.getBoolean(col++));
            settings.setPaginationSize(rs.getInt(col++));
            settings.getMainVisibility().setComposerVisible(rs.getBoolean(col++));
            settings.getPlaylistVisibility().setComposerVisible(rs.getBoolean(col++));
            settings.setCloseDrawer(rs.getBoolean(col++));
            settings.setClosePlayQueue(rs.getBoolean(col++));
            settings.setAlternativeDrawer(rs.getBoolean(col++));
            settings.setAssignAccesskeyToNumber(rs.getBoolean(col++));
            settings.setOpenDetailIndex(rs.getBoolean(col++));
            settings.setOpenDetailSetting(rs.getBoolean(col++));
            settings.setOpenDetailStar(rs.getBoolean(col++));
            settings.setShowIndex(rs.getBoolean(col++));
            settings.setSimpleDisplay(rs.getBoolean(col++));
            settings.setShowSibling(rs.getBoolean(col++));
            settings.setShowRate(rs.getBoolean(col++));
            settings.setShowAlbumSearch(rs.getBoolean(col++));
            settings.setShowLastPlay(rs.getBoolean(col++));
            settings.setShowDownload(rs.getBoolean(col++));
            settings.setShowTag(rs.getBoolean(col++));
            settings.setShowComment(rs.getBoolean(col++));
            settings.setShowShare(rs.getBoolean(col++));
            settings.setShowChangeCoverArt(rs.getBoolean(col++));
            settings.setShowTopSongs(rs.getBoolean(col++));
            settings.setShowSimilar(rs.getBoolean(col++));
            settings.setShowAlbumActions(rs.getBoolean(col++));
            settings.setBreadcrumbIndex(rs.getBoolean(col++));
            settings.setPutMenuInDrawer(rs.getBoolean(col++));
            settings.setFontSchemeName(rs.getString(col++));
            settings.setShowOutlineHelp(rs.getBoolean(col++));
            settings.setForceBio2Eng(rs.getBoolean(col++));
            settings.setVoiceInputEnabled(rs.getBoolean(col++));
            settings.setShowCurrentSongInfo(rs.getBoolean(col++));
            settings.setSpeechLangSchemeName(rs.getString(col++));
            settings.setIetf(rs.getString(col++));
            settings.setFontFamily(rs.getString(col++));
            settings.setFontSize(rs.getInt(col++));
            settings.setShowScannedCount(rs.getBoolean(col));
            return settings;
        }
    }

    public String getUserTable() {
        return userTableQuote + "user" + userTableQuote;
    }
}

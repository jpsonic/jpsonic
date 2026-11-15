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

import static com.jsoftbiz.utils.OS.OS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.tesshu.jpsonic.SuppressFBWarnings;
import com.tesshu.jpsonic.domain.language.StringUtil;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider.DirectoryInfo;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider.LocaleInfo;
import com.tesshu.jpsonic.infrastructure.db.DatabaseConfiguration.ProfileNameConstants;
import com.tesshu.jpsonic.infrastructure.filesystem.PathInspector;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.base.DaoHelper;
import com.tesshu.jpsonic.persistence.core.entity.MediaLibraryStatistics;
import com.tesshu.jpsonic.persistence.core.repository.StaticsDao;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.UserService;
import com.tesshu.jpsonic.service.metadata.FFmpeg;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for the help page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/internalhelp", "/internalhelp.view" })
public class InternalHelpController {

    private static final Logger LOG = LoggerFactory.getLogger(InternalHelpController.class);

    private final MusicFolderService musicFolderService;
    private final UserService userService;
    private final DaoHelper daoHelper;
    private final Environment environment;
    private final StaticsDao staticsDao;
    private final FFmpeg ffmpeg;

    public InternalHelpController(UserService userService, MusicFolderService musicFolderService,
            DaoHelper daoHelper, Environment environment, StaticsDao staticsDao, FFmpeg ffmpeg) {
        super();
        this.userService = userService;
        this.musicFolderService = musicFolderService;
        this.daoHelper = daoHelper;
        this.environment = environment;
        this.staticsDao = staticsDao;
        this.ffmpeg = ffmpeg;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request) {
        Map<String, Object> map = LegacyMap.of();

        map.put("brand", EnvironmentProvider.getInstance().getBrand());
        map
            .put("admin",
                    userService.isAdmin(userService.getCurrentUserStrict(request).getUsername()));

        // Gather internal information
        gatherPlatfomInfo(request, map);
        gatherDatabaseInfo(map);
        gatherFilesystemInfo(map);
        gatherTranscodingInfo(map);
        gatherLocaleInfo(map);
        map.put("indexLuceneVersion", Version.getPackageImplementationVersion());
        gatherStats(map);

        return new ModelAndView("internalhelp", "model", map);
    }

    private void gatherPlatfomInfo(HttpServletRequest request, Map<String, Object> map) {
        map.put("platformName", OS.getPlatformName());
        map.put("osName", OS.getName());
        map.put("osVersion", OS.getVersion());
        map.put("osArc", OS.getArch());
        map.put("javaVersion", EnvironmentProvider.getInstance().getJavaVersion());
        long totalMemory = Runtime.getRuntime().totalMemory();
        map.put("totalMemory", totalMemory);
        map.put("usedMemory", totalMemory - Runtime.getRuntime().freeMemory());
        map.put("gc", EnvironmentProvider.getInstance().guessGCName());
        map.put("applicationServer", request.getSession().getServletContext().getServerInfo());
    }

    private void gatherStats(Map<String, Object> map) {
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();
        List<MediaLibraryStatistics> stats = staticsDao.getRecentMediaLibraryStatistics();
        List<StatsVO> result = new ArrayList<>();
        folders.forEach(folder -> {
            stats
                .stream()
                .filter(stat -> stat.getFolderId() == folder.getId())
                .findFirst()
                .ifPresent(stat -> {
                    StatsVO vo = new StatsVO(
                            LocalDateTime.ofInstant(stat.getExecuted(), ZoneId.systemDefault()),
                            folder.getName(), stat.getArtistCount(), stat.getAlbumCount(),
                            stat.getSongCount(), stat.getVideoCount(),
                            com.tesshu.jpsonic.util.StringUtil
                                .formatDurationHMMSS(stat.getTotalDuration()),
                            PathInspector.byteCountToDisplaySize(stat.getTotalSize()));
                    result.add(vo);
                });
        });
        map.put("stats", result);
    }

    /**
     * Returns true if a locale string (e.g. en_US.UTF-8) appears to support UTF-8
     * correctly.
     * <p>
     * Some systems use non-standard locales (e.g. en_US.utf8 instead of
     * en_US.UTF-8) to specify Unicode support, which are usually supported by the
     * Glibc.
     * <p>
     * See:
     * https://superuser.com/questions/999133/differences-between-en-us-utf8-and-en-us-utf-8
     */
    boolean doesLocaleSupportUtf8(String locale) {
        return locale != null
                && StringUtil.containsIgnoreCase(locale.replaceAll("\\W", ""), "utf8");
    }

    private void gatherLocaleInfo(Map<String, Object> map) {
        LocaleInfo info = EnvironmentProvider.getInstance().getLocaleInfo();

        map.put("localeDefault", info.localeDefault());
        map.put("localeUserLanguage", info.localeUserLanguage());
        map.put("localeUserCountry", info.localeUserCountry());
        map.put("localeFileEncoding", info.localeFileEncoding());
        map.put("localeSunJnuEncoding", info.localeSunJnuEncoding());
        map.put("localeSunIoUnicodeEncoding", info.localeSunIoUnicodeEncoding());
        map.put("localeLang", info.localeLang());
        map.put("localeLcAll", info.localeLcAll());
        map.put("localeDefaultCharset", info.localeDefaultCharset());
        map.put("localeDefaultZoneOffset", info.localeDefaultZoneOffset());

        map.put("localeFileEncodingSupportsUtf8", doesLocaleSupportUtf8(info.localeFileEncoding()));
        map.put("localeLangSupportsUtf8", doesLocaleSupportUtf8(info.localeLang()));
        map.put("localeLcAllSupportsUtf8", doesLocaleSupportUtf8(info.localeLcAll()));
        map
            .put("localeDefaultCharsetSupportsUtf8",
                    doesLocaleSupportUtf8(info.localeDefaultCharset()));
    }

    @SuppressFBWarnings(value = { "CRLF_INJECTION_LOGS",
            "SQL_INJECTION_SPRING_JDBC" }, justification = "False positive. find-sec-bugs#614/find-sec-bugs#385")
    private void gatherDatabaseInfo(Map<String, Object> map) {
        try (Connection conn = daoHelper.getDataSource().getConnection()) {
            // Driver name/version
            map.put("dbDriverName", conn.getMetaData().getDriverName());
            map.put("dbDriverVersion", conn.getMetaData().getDriverVersion());
            map.put("dbServerVersion", conn.getMetaData().getDatabaseProductVersion());
        } catch (SQLException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to gather information", e);
            }
        }
        putDatabaseLegacyInfoTo(map);
    }

    private void putDatabaseLegacyInfoTo(Map<String, Object> map) {
        if (environment.acceptsProfiles(Profiles.of(ProfileNameConstants.HOST))) {
            map.put("dbIsLegacy", true);
            DirectoryInfo info = EnvironmentProvider.getInstance().getDatabaseDirectoryInfo();

            map
                .put("dbDirectorySizeBytes",
                        Files.exists(EnvironmentProvider.getInstance().getLocalDatabaseDirectory())
                                ? info.sizeBytes()
                                : 0);
            map.put("dbDirectorySize", PathInspector.byteCountToDisplaySize(info.sizeBytes()));
            Path dbLogFile = EnvironmentProvider.getInstance().getDatabaseLogFilePath();
            try {
                map.put("dbLogSizeBytes", Files.exists(dbLogFile) ? Files.size(dbLogFile) : 0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            map
                .put("dbLogSize",
                        PathInspector.byteCountToDisplaySize((long) map.get("dbLogSizeBytes")));
        } else {
            map.put("dbIsLegacy", false);
        }
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // (FileStatistics) Not reusable
    private void gatherFilesystemInfo(Map<String, Object> map) {
        DirectoryInfo info = EnvironmentProvider.getInstance().getJpsonicHomeInfo();
        map.put("fsHomeDirectorySizeBytes", info.sizeBytes());
        map.put("fsHomeDirectorySize", PathInspector.byteCountToDisplaySize(info.sizeBytes()));
        map.put("fsHomeUsableSpaceBytes", info.usableSpaceBytes());
        map.put("fsHomeUsableSpace", PathInspector.byteCountToDisplaySize(info.usableSpaceBytes()));
        map.put("fsHomeTotalSpaceBytes", info.totalSpaceBytes());
        map.put("fsHomeTotalSpace", PathInspector.byteCountToDisplaySize(info.totalSpaceBytes()));

        SortedMap<String, FileStatistics> fsMusicFolderStatistics = new TreeMap<>();
        for (MusicFolder folder : musicFolderService.getAllMusicFolders()) {
            FileStatistics stat = new FileStatistics();
            stat.setFromPath(folder.toPath());
            stat.setName(folder.getName());
            fsMusicFolderStatistics.put(folder.getName(), stat);
        }
        map.put("fsMusicFolderStatistics", fsMusicFolderStatistics);
    }

    String formatFFmpegVersion(String version) {
        String fmt;
        try (StringReader reader = new StringReader(version);
                BufferedReader br = new BufferedReader(reader);) {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                if (line.startsWith("ffmpeg version")) {
                    sb
                        .append(line.substring(0, line.indexOf("Copyright") - 1))
                        .append("\n\s\s\s\s\s\s\s\s")
                        .append(line.substring(line.indexOf("Copyright")))
                        .append('\n');
                } else if (line.startsWith("built with")) {
                    sb.append("\s\s\s\s\s\s\s\s").append(line).append('\n');
                } else if (line.startsWith("configuration:")) {
                    sb.append('\n');
                    Stream.of(line.split(" ")).forEach(s -> {
                        if (s.startsWith("configuration:")) {
                            sb.append(s);
                        } else {
                            sb.append("\s\s\s\s").append(s);
                        }
                        sb.append('\n');
                    });
                    sb.append('\n');
                } else {
                    sb.append(line).append('\n');
                }
                line = br.readLine();
            }
            fmt = sb.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return fmt;
    }

    private void gatherTranscodingInfo(Map<String, Object> map) {
        EnvironmentProvider.TranscodeStatus status = EnvironmentProvider
            .getInstance()
            .getTranscodeStatus();

        status.ffmpegPath().ifPresent(p -> map.put("ffmpegPath", p.toString()));
        map.put("ffmpegReadable", status.ffmpegReadable());
        map.put("ffmpegExecutable", status.ffmpegExecutable());

        status.ffprobePath().ifPresent(p -> map.put("ffprobePath", p.toString()));
        map.put("ffprobeReadable", status.ffprobeReadable());
        map.put("ffprobeExecutable", status.ffprobeExecutable());

        String version = "Unknown";
        if (status.ffmpegExecutable()) {
            version = ffmpeg.getVersion();
        }
        map.put("ffmpegVersion", formatFFmpegVersion(version));
    }

    public static class FileStatistics {
        private String name;
        private String path;
        private String freeFilesystemSizeBytes;
        private String totalFilesystemSizeBytes;
        private boolean readable;
        private boolean writable;
        private boolean executable;

        public String getName() {
            return name;
        }

        public String getFreeFilesystemSizeBytes() {
            return freeFilesystemSizeBytes;
        }

        public boolean isReadable() {
            return readable;
        }

        public boolean isWritable() {
            return writable;
        }

        public boolean isExecutable() {
            return executable;
        }

        public String getTotalFilesystemSizeBytes() {
            return totalFilesystemSizeBytes;
        }

        public String getPath() {
            return path;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setFreeFilesystemSizeBytes(String freeFilesystemSizeBytes) {
            this.freeFilesystemSizeBytes = freeFilesystemSizeBytes;
        }

        public void setReadable(boolean readable) {
            this.readable = readable;
        }

        public void setWritable(boolean writable) {
            this.writable = writable;
        }

        public void setExecutable(boolean executable) {
            this.executable = executable;
        }

        public void setTotalFilesystemSizeBytes(String totalFilesystemSizeBytes) {
            this.totalFilesystemSizeBytes = totalFilesystemSizeBytes;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public void setFromPath(Path path) {
            Path fileName = path.getFileName();
            if (fileName != null) {
                this.setName(fileName.toString());
            }
            this.setPath(path.toString());
            try {
                FileStore store = Files.getFileStore(path);
                this
                    .setFreeFilesystemSizeBytes(
                            PathInspector.byteCountToDisplaySize(store.getUsableSpace()));
                this
                    .setTotalFilesystemSizeBytes(
                            PathInspector.byteCountToDisplaySize(store.getTotalSpace()));
            } catch (IOException e) {
                if (LOG.isWarnEnabled()) {
                    LOG
                        .warn("Could not get directory size because path cannot be accessed.: "
                                + path, e.getMessage());
                }
            }
            this.setReadable(Files.isReadable(path));
            this.setWritable(Files.isWritable(path));
            this.setExecutable(Files.isExecutable(path));
        }
    }

    public static class StatsVO {

        private final LocalDateTime executed;
        private final String folderName;
        private final int artistCount;
        private final int albumCount;
        private final int songCount;
        private final int videoCount;
        private final String size;
        private final String duration;

        public StatsVO(LocalDateTime executed, String folderName, int artistCount, int albumCount,
                int songCount, int videoCount, String size, String duration) {
            super();
            this.executed = executed;
            this.folderName = folderName;
            this.artistCount = artistCount;
            this.albumCount = albumCount;
            this.songCount = songCount;
            this.videoCount = videoCount;
            this.size = size;
            this.duration = duration;
        }

        public LocalDateTime getExecuted() {
            return executed;
        }

        public String getFolderName() {
            return folderName;
        }

        public int getArtistCount() {
            return artistCount;
        }

        public int getAlbumCount() {
            return albumCount;
        }

        public int getSongCount() {
            return songCount;
        }

        public int getVideoCount() {
            return videoCount;
        }

        public String getSize() {
            return size;
        }

        public String getDuration() {
            return duration;
        }
    }
}

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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import com.tesshu.jpsonic.SuppressFBWarnings;
import com.tesshu.jpsonic.SuppressLint;
import com.tesshu.jpsonic.dao.DaoHelper;
import com.tesshu.jpsonic.dao.MediaFileDao;
import com.tesshu.jpsonic.dao.MusicFolderDao;
import com.tesshu.jpsonic.dao.StaticsDao;
import com.tesshu.jpsonic.domain.MediaLibraryStatistics;
import com.tesshu.jpsonic.domain.MusicFolder;
import com.tesshu.jpsonic.service.SecurityService;
import com.tesshu.jpsonic.service.SettingsService;
import com.tesshu.jpsonic.service.TranscodingService;
import com.tesshu.jpsonic.service.VersionService;
import com.tesshu.jpsonic.service.search.AnalyzerFactory;
import com.tesshu.jpsonic.service.search.IndexManager;
import com.tesshu.jpsonic.service.search.IndexType;
import com.tesshu.jpsonic.spring.DatabaseConfiguration.ProfileNameConstants;
import com.tesshu.jpsonic.util.FileUtil;
import com.tesshu.jpsonic.util.LegacyMap;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
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
    private static final int LOG_LINES_TO_SHOW = 50;
    private static final String TABLE_TYPE_TABLE = "table";

    private final VersionService versionService;
    private final SettingsService settingsService;
    private final SecurityService securityService;
    private final IndexManager indexManager;
    private final DaoHelper daoHelper;
    private final AnalyzerFactory analyzerFactory;
    private final MusicFolderDao musicFolderDao;
    private final MediaFileDao mediaFileDao;
    private final TranscodingService transcodingService;
    private final Environment environment;
    private final StaticsDao staticsDao;

    public InternalHelpController(VersionService versionService, SettingsService settingsService,
            SecurityService securityService, IndexManager indexManager, DaoHelper daoHelper,
            AnalyzerFactory analyzerFactory, MusicFolderDao musicFolderDao, MediaFileDao mediaFileDao,
            TranscodingService transcodingService, Environment environment, StaticsDao staticsDao) {
        super();
        this.versionService = versionService;
        this.settingsService = settingsService;
        this.securityService = securityService;
        this.indexManager = indexManager;
        this.daoHelper = daoHelper;
        this.analyzerFactory = analyzerFactory;
        this.musicFolderDao = musicFolderDao;
        this.mediaFileDao = mediaFileDao;
        this.transcodingService = transcodingService;
        this.environment = environment;
        this.staticsDao = staticsDao;
    }

    @GetMapping
    @SuppressLint(value = "CROSS_SITE_SCRIPTING", justification = "False positive. VersionService reads static local files.")
    protected ModelAndView handleRequestInternal(HttpServletRequest request) {
        Map<String, Object> map = LegacyMap.of();

        map.put("admin", securityService.isAdmin(securityService.getCurrentUserStrict(request).getUsername()));
        map.put("showStatus", settingsService.isShowStatus());

        if (versionService.isNewFinalVersionAvailable()) {
            map.put("newVersionAvailable", true);
            map.put("latestVersion", versionService.getLatestFinalVersion());
        } else if (versionService.isNewBetaVersionAvailable()) {
            map.put("newVersionAvailable", true);
            map.put("latestVersion", versionService.getLatestBetaVersion());
        }

        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();

        String serverInfo = request.getSession().getServletContext().getServerInfo() + ", java "
                + System.getProperty("java.version") + ", " + System.getProperty("os.name");

        map.put("user", securityService.getCurrentUserStrict(request));
        map.put("brand", SettingsService.getBrand());
        map.put("localVersion", versionService.getLocalVersion());
        map.put("buildDate", versionService.getLocalBuildDate());
        map.put("buildNumber", versionService.getLocalBuildNumber());
        map.put("serverInfo", serverInfo);
        map.put("usedMemory", totalMemory - freeMemory);
        map.put("totalMemory", totalMemory);
        Path logFile = SettingsService.getLogFile();
        List<String> latestLogEntries = getLatestLogEntries(logFile);
        map.put("logEntries", latestLogEntries);
        map.put("logFile", logFile);

        // Gather internal information
        gatherScanInfo(map);
        gatherIndexInfo(map);
        gatherDatabaseInfo(map);
        gatherFilesystemInfo(map);
        gatherTranscodingInfo(map);
        gatherLocaleInfo(map);

        return new ModelAndView("internalhelp", "model", map);
    }

    private void gatherScanInfo(Map<String, Object> map) {
        // Airsonic scan statistics
        MediaLibraryStatistics stats = staticsDao.getRecentMediaLibraryStatistics();
        if (stats != null) {
            map.put("statAlbumCount", stats.getAlbumCount());
            map.put("statArtistCount", stats.getArtistCount());
            map.put("statSongCount", stats.getSongCount());
            map.put("statLastScanDate", stats.getExecuted().atZone(ZoneId.systemDefault()).toLocalDateTime());
            map.put("statTotalDurationSeconds", stats.getTotalDuration());
            map.put("statTotalLengthBytes", FileUtil.byteCountToDisplaySize(stats.getTotalSize()));
        }
    }

    @SuppressWarnings({ "PMD.CloseResource", "PMD.AvoidInstantiatingObjectsInLoops" })
    /*
     * [CloseResource] False positive. SearcherManager inherits Closeable but ensures each searcher is closed only once
     * all threads have finished using it. Use release instead of close for reuse. No explicit close is done here.
     * [AvoidInstantiatingObjectsInLoops] (IndexStatistics) Not reusable
     */
    private void gatherIndexInfo(Map<String, Object> map) {
        SortedMap<String, IndexStatistics> indexStats = new TreeMap<>();
        for (IndexType indexType : IndexType.values()) {
            IndexStatistics stat = new IndexStatistics();
            IndexSearcher searcher = indexManager.getSearcher(indexType);
            stat.setName(indexType.name());
            indexStats.put(indexType.name(), stat);
            if (searcher == null) {
                stat.setCount(0);
                stat.setDeletedCount(0);
            } else {
                IndexReader reader = searcher.getIndexReader();
                stat.setCount(reader.numDocs());
                stat.setDeletedCount(reader.numDeletedDocs());
                indexManager.release(indexType, searcher);
            }
        }
        map.put("indexStatistics", indexStats);

        try (Analyzer analyzer = analyzerFactory.getAnalyzer()) {
            map.put("indexLuceneVersion", analyzer.getVersion().toString());
        }
    }

    /**
     * Returns true if a locale string (e.g. en_US.UTF-8) appears to support UTF-8 correctly.
     *
     * Some systems use non-standard locales (e.g. en_US.utf8 instead of en_US.UTF-8) to specify Unicode support, which
     * are usually supported by the Glibc.
     *
     * See: https://superuser.com/questions/999133/differences-between-en-us-utf8-and-en-us-utf-8
     */
    private boolean doesLocaleSupportUtf8(String locale) {
        if (locale == null) {
            return false;
        } else {
            return StringUtils.containsIgnoreCase(locale.replaceAll("\\W", ""), "utf8");
        }
    }

    private void gatherLocaleInfo(Map<String, Object> map) {
        map.put("localeDefault", Locale.getDefault());
        map.put("localeUserLanguage", System.getProperty("user.language"));
        map.put("localeUserCountry", System.getProperty("user.country"));
        map.put("localeFileEncoding", System.getProperty("file.encoding"));
        map.put("localeSunJnuEncoding", System.getProperty("sun.jnu.encoding"));
        map.put("localeSunIoUnicodeEncoding", System.getProperty("sun.io.unicode.encoding"));
        map.put("localeLang", System.getenv("LANG"));
        map.put("localeLcAll", System.getenv("LC_ALL"));
        map.put("localeDefaultCharset", Charset.defaultCharset().toString());
        map.put("localeDefaultZoneOffset", java.time.ZoneOffset.systemDefault());

        map.put("localeFileEncodingSupportsUtf8", doesLocaleSupportUtf8(System.getProperty("file.encoding")));
        map.put("localeLangSupportsUtf8", doesLocaleSupportUtf8(System.getenv("LANG")));
        map.put("localeLcAllSupportsUtf8", doesLocaleSupportUtf8(System.getenv("LC_ALL")));
        map.put("localeDefaultCharsetSupportsUtf8", doesLocaleSupportUtf8(Charset.defaultCharset().toString()));
    }

    @SuppressFBWarnings(value = { "CRLF_INJECTION_LOGS",
            "SQL_INJECTION_SPRING_JDBC" }, justification = "False positive. find-sec-bugs#614/find-sec-bugs#385")
    private void gatherDatabaseInfo(Map<String, Object> map) {

        try (Connection conn = daoHelper.getDataSource().getConnection()) {

            // Driver name/version
            map.put("dbDriverName", conn.getMetaData().getDriverName());
            map.put("dbDriverVersion", conn.getMetaData().getDriverVersion());
            map.put("dbServerVersion", conn.getMetaData().getDatabaseProductVersion());

            // Gather information for existing database tables
            try (ResultSet resultSet = conn.getMetaData().getTables(null, null, "%", null)) {

                SortedMap<String, Long> dbTableCount = new TreeMap<>();
                while (resultSet.next()) {
                    String tableSchema = resultSet.getString("TABLE_SCHEM");
                    String tableName = resultSet.getString("TABLE_NAME");
                    String tableType = resultSet.getString("TABLE_TYPE");
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Got database table {}, schema {}, type {}", tableName, tableSchema, tableType);
                    }
                    if (!TABLE_TYPE_TABLE.equalsIgnoreCase(tableType)) {
                        continue; // Table type
                    }
                    // MariaDB has "null" schemas, while other databases use "public".
                    if (tableSchema != null && !"public".equalsIgnoreCase(tableSchema)) {
                        continue; // Table schema
                    }
                    Long tableCount = daoHelper.getJdbcTemplate()
                            .queryForObject(String.format("SELECT count(*) FROM %s", tableName), Long.class);
                    dbTableCount.put(tableName, tableCount);
                }
                map.put("dbTableCount", dbTableCount);
            }
        } catch (SQLException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to gather information", e);
            }
        }

        putDatabaseLegacyInfoTo(map);
        putDatabaseTableInfoTo(map);
    }

    private void putDatabaseLegacyInfoTo(Map<String, Object> map) {
        if (environment.acceptsProfiles(Profiles.of(ProfileNameConstants.LEGACY))) {
            map.put("dbIsLegacy", true);
            Path dbDirectory = Path.of(SettingsService.getJpsonicHome().toString(), "db");
            map.put("dbDirectorySizeBytes", Files.exists(dbDirectory) ? FileUtil.sizeOfDirectory(dbDirectory) : 0);
            map.put("dbDirectorySize", FileUtil.byteCountToDisplaySize((long) map.get("dbDirectorySizeBytes")));
            Path dbLogFile = Path.of(dbDirectory.toString(), "airsonic.log");
            try {
                map.put("dbLogSizeBytes", Files.exists(dbLogFile) ? Files.size(dbLogFile) : 0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            map.put("dbLogSize", FileUtil.byteCountToDisplaySize((long) map.get("dbLogSizeBytes")));
        } else {
            map.put("dbIsLegacy", false);
        }

    }

    private void putDatabaseTableInfoTo(Map<String, Object> map) {
        map.put("dbMediaFileMusicNonPresentCount", daoHelper.getJdbcTemplate()
                .queryForObject("SELECT count(*) FROM media_file WHERE NOT present AND type = 'MUSIC'", Long.class));
        map.put("dbMediaFilePodcastNonPresentCount", daoHelper.getJdbcTemplate()
                .queryForObject("SELECT count(*) FROM media_file WHERE NOT present AND type = 'PODCAST'", Long.class));
        map.put("dbMediaFileDirectoryNonPresentCount", daoHelper.getJdbcTemplate().queryForObject(
                "SELECT count(*) FROM media_file WHERE NOT present AND type = 'DIRECTORY'", Long.class));
        map.put("dbMediaFileAlbumNonPresentCount", daoHelper.getJdbcTemplate()
                .queryForObject("SELECT count(*) FROM media_file wheRE NOT present AND type = 'ALBUM'", Long.class));

        map.put("dbMediaFileMusicPresentCount", daoHelper.getJdbcTemplate()
                .queryForObject("SELECT count(*) FROM media_file WHERE present AND type = 'MUSIC'", Long.class));
        map.put("dbMediaFilePodcastPresentCount", daoHelper.getJdbcTemplate()
                .queryForObject("SELECT count(*) FROM media_file WHERE present AND type = 'PODCAST'", Long.class));
        map.put("dbMediaFileDirectoryPresentCount", daoHelper.getJdbcTemplate()
                .queryForObject("SELECT count(*) FROM media_file WHERE present AND type = 'DIRECTORY'", Long.class));
        map.put("dbMediaFileAlbumPresentCount", daoHelper.getJdbcTemplate()
                .queryForObject("SELECT count(*) FROM media_file WHERE present AND type = 'ALBUM'", Long.class));

        map.put("dbMediaFileDistinctAlbumCount", daoHelper.getJdbcTemplate()
                .queryForObject("SELECT count(DISTINCT album) FROM media_file WHERE present", Long.class));
        map.put("dbMediaFileDistinctArtistCount", daoHelper.getJdbcTemplate()
                .queryForObject("SELECT count(DISTINCT artist) FROM media_file WHERE present", Long.class));
        map.put("dbMediaFileDistinctAlbumArtistCount", daoHelper.getJdbcTemplate()
                .queryForObject("SELECT count(DISTINCT album_artist) FROM media_file WHERE present", Long.class));

        map.put("dbMediaFilesInNonPresentMusicFoldersCount",
                mediaFileDao.getFilesInNonPresentMusicFoldersCount(Arrays.asList(settingsService.getPodcastFolder())));
        map.put("dbMediaFilesInNonPresentMusicFoldersSample",
                mediaFileDao.getFilesInNonPresentMusicFolders(10, Arrays.asList(settingsService.getPodcastFolder())));

        map.put("dbMediaFilesWithMusicFolderMismatchCount", mediaFileDao.getFilesWithMusicFolderMismatchCount());
        map.put("dbMediaFilesWithMusicFolderMismatchSample", mediaFileDao.getFilesWithMusicFolderMismatch(10));
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // (FileStatistics) Not reusable
    private void gatherFilesystemInfo(Map<String, Object> map) {
        long directorySize = FileUtil.sizeOfDirectory(SettingsService.getJpsonicHome());
        map.put("fsHomeDirectorySizeBytes", directorySize);
        map.put("fsHomeDirectorySize", FileUtil.byteCountToDisplaySize(directorySize));
        try {
            FileStore store = Files.getFileStore(SettingsService.getJpsonicHome());
            long usableSpace = store.getUsableSpace();
            map.put("fsHomeUsableSpaceBytes", usableSpace);
            map.put("fsHomeUsableSpace", FileUtil.byteCountToDisplaySize(usableSpace));
            long totalSpace = store.getTotalSpace();
            map.put("fsHomeTotalSpaceBytes", totalSpace);
            map.put("fsHomeTotalSpace", FileUtil.byteCountToDisplaySize(totalSpace));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SortedMap<String, FileStatistics> fsMusicFolderStatistics = new TreeMap<>();
        for (MusicFolder folder : musicFolderDao.getAllMusicFolders()) {
            FileStatistics stat = new FileStatistics();
            stat.setFromPath(folder.toPath());
            stat.setName(folder.getName());
            fsMusicFolderStatistics.put(folder.getName(), stat);
        }
        map.put("fsMusicFolderStatistics", fsMusicFolderStatistics);
    }

    private void gatherTranscodingInfo(Map<String, Object> map) {
        map.put("fsFfprobeInfo", gatherStatisticsForTranscodingExecutable("ffprobe"));
        map.put("fsFfmpegInfo", gatherStatisticsForTranscodingExecutable("ffmpeg"));
    }

    private static List<String> getLatestLogEntries(Path logFile) {
        List<String> result = new ArrayList<>(LOG_LINES_TO_SHOW);
        try (ReversedLinesFileReader reader = new ReversedLinesFileReader(logFile, Charset.defaultCharset())) {
            for (String current = reader.readLine(); current != null; current = reader.readLine()) {
                if (result.size() >= LOG_LINES_TO_SHOW) {
                    break;
                }
                result.add(0, current);
            }
            return result;
        } catch (IOException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Could not open log file " + logFile, e);
            }
            return result;
        }
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // (File) Not reusable
    private Path lookForExecutable(String executableName) {
        for (String path : System.getenv("PATH").split(java.io.File.pathSeparator, -1)) {
            Path file = Path.of(path, executableName);
            if (Files.exists(file)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found {} in {}", executableName, path);
                }
                return file;
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Looking for {} in {} (not found)", executableName, path);
                }
            }
        }
        return null;
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // (File) Not reusable
    private Path lookForTranscodingExecutable(String executableName) {
        for (String name : Arrays.asList(executableName, String.format("%s.exe", executableName))) {
            Path executableLocation = Path.of(transcodingService.getTranscodeDirectory().toString(), name);
            if (Files.exists(executableLocation)) {
                return executableLocation;
            }
            executableLocation = lookForExecutable(executableName);
            if (executableLocation != null && Files.exists(executableLocation)) {
                return executableLocation;
            }
        }
        return null;
    }

    private FileStatistics gatherStatisticsForTranscodingExecutable(String executableName) {
        FileStatistics executableStatistics = null;
        Path executableLocation = lookForTranscodingExecutable(executableName);
        if (executableLocation != null) {
            executableStatistics = new FileStatistics();
            executableStatistics.setFromPath(executableLocation);
        }
        return executableStatistics;
    }

    public static class IndexStatistics {
        private String name;
        private int count;
        private int deletedCount;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public int getDeletedCount() {
            return deletedCount;
        }

        public void setDeletedCount(int deletedCount) {
            this.deletedCount = deletedCount;
        }
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
                this.setFreeFilesystemSizeBytes(FileUtil.byteCountToDisplaySize(store.getUsableSpace()));
                this.setTotalFilesystemSizeBytes(FileUtil.byteCountToDisplaySize(store.getTotalSpace()));
            } catch (IOException e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Could not get directory size because path cannot be accessed.: " + path, e.getMessage());
                }
            }
            this.setReadable(Files.isReadable(path));
            this.setWritable(Files.isWritable(path));
            this.setExecutable(Files.isExecutable(path));
        }
    }
}

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

package com.tesshu.jpsonic.command;

import java.io.File;
import java.util.Date;
import java.util.List;

import com.tesshu.jpsonic.controller.MusicFolderSettingsController;
import com.tesshu.jpsonic.domain.FileModifiedCheckScheme;
import com.tesshu.jpsonic.domain.MusicFolder;
import org.apache.commons.lang.StringUtils;

/**
 * Command used in {@link MusicFolderSettingsController}.
 *
 * @author Sindre Mehus
 */
public class MusicFolderSettingsCommand extends SettingsPageCommons {

    // Specify folder
    private List<MusicFolderInfo> musicFolders;
    private MusicFolderInfo newMusicFolder;

    // Run a scan
    private boolean fullScanNext;
    private boolean scanning;
    private String interval;
    private String hour;
    private boolean showRefresh;

    // Exclusion settings
    private String excludePatternString;
    private boolean ignoreSymLinks;

    // Other operations
    private boolean fastCache;
    private FileModifiedCheckScheme fileModifiedCheckScheme;
    private boolean ignoreFileTimestamps;
    private boolean ignoreFileTimestampsForEachAlbum;

    public List<MusicFolderInfo> getMusicFolders() {
        return musicFolders;
    }

    public void setMusicFolders(List<MusicFolderInfo> musicFolders) {
        this.musicFolders = musicFolders;
    }

    public MusicFolderInfo getNewMusicFolder() {
        return newMusicFolder;
    }

    public void setNewMusicFolder(MusicFolderInfo newMusicFolder) {
        this.newMusicFolder = newMusicFolder;
    }

    public boolean isFullScanNext() {
        return fullScanNext;
    }

    public void setFullScanNext(boolean fullScanNext) {
        this.fullScanNext = fullScanNext;
    }

    public boolean isScanning() {
        return scanning;
    }

    public void setScanning(boolean scanning) {
        this.scanning = scanning;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public String getHour() {
        return hour;
    }

    public void setHour(String hour) {
        this.hour = hour;
    }

    public boolean isShowRefresh() {
        return showRefresh;
    }

    public void setShowRefresh(boolean showIndexRefresh) {
        this.showRefresh = showIndexRefresh;
    }

    public String getExcludePatternString() {
        return excludePatternString;
    }

    public void setExcludePatternString(String excludePatternString) {
        this.excludePatternString = excludePatternString;
    }

    public boolean isIgnoreSymLinks() {
        return ignoreSymLinks;
    }

    public void setIgnoreSymLinks(boolean ignoreSymLinks) {
        this.ignoreSymLinks = ignoreSymLinks;
    }

    public boolean isFastCache() {
        return fastCache;
    }

    public void setFastCache(boolean fastCache) {
        this.fastCache = fastCache;
    }

    public FileModifiedCheckScheme getFileModifiedCheckScheme() {
        return fileModifiedCheckScheme;
    }

    public void setFileModifiedCheckScheme(FileModifiedCheckScheme fileModifiedCheckScheme) {
        this.fileModifiedCheckScheme = fileModifiedCheckScheme;
    }

    public boolean isIgnoreFileTimestamps() {
        return ignoreFileTimestamps;
    }

    public void setIgnoreFileTimestamps(boolean ignoreFileTimes) {
        this.ignoreFileTimestamps = ignoreFileTimes;
    }

    public boolean isIgnoreFileTimestampsForEachAlbum() {
        return ignoreFileTimestampsForEachAlbum;
    }

    public void setIgnoreFileTimestampsForEachAlbum(boolean ignoreFileTimestampsForEachAlbum) {
        this.ignoreFileTimestampsForEachAlbum = ignoreFileTimestampsForEachAlbum;
    }

    public static class MusicFolderInfo {

        private Integer id;
        private String path;
        private String name;
        private boolean enabled;
        private boolean delete;
        private boolean existing;

        public MusicFolderInfo(MusicFolder musicFolder) {
            id = musicFolder.getId();
            path = musicFolder.getPath().getPath();
            name = musicFolder.getName();
            enabled = musicFolder.isEnabled();
            existing = musicFolder.getPath().exists() && musicFolder.getPath().isDirectory();
        }

        public MusicFolderInfo() {
            enabled = true;
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isDelete() {
            return delete;
        }

        public void setDelete(boolean delete) {
            this.delete = delete;
        }

        public MusicFolder toMusicFolder() {
            String path = StringUtils.trimToNull(this.path);
            if (path == null) {
                return null;
            }
            File file = new File(path);
            String name = StringUtils.trimToNull(this.name);
            if (name == null) {
                name = file.getName();
            }
            return new MusicFolder(id, new File(path), name, enabled, new Date());
        }

        public boolean isExisting() {
            return existing;
        }

    }
}
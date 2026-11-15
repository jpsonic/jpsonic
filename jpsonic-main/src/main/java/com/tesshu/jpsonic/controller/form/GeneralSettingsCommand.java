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

package com.tesshu.jpsonic.controller.form;

import java.util.List;

import com.tesshu.jpsonic.controller.GeneralSettingsController;
import com.tesshu.jpsonic.domain.system.IndexScheme;
import com.tesshu.jpsonic.feature.theme.Theme;

/**
 * Command used in {@link GeneralSettingsController}.
 *
 * @author Sindre Mehus
 */
public class GeneralSettingsCommand extends SettingsPageCommons {

    // Language and theme
    private List<Theme> themes;
    private String themeIndex = "0";
    private List<String> locales;
    private String localeIndex = "0";
    private IndexScheme indexScheme;

    // Index settings
    private String defaultIndexString;
    private String simpleIndexString;
    private String index;
    private String ignoredArticles;
    private boolean ignoreFullWidth;
    private boolean deleteDiacritic;

    // Sort settings
    private boolean sortAlbumsByYear;
    private boolean sortGenresByAlphabet;
    private boolean prohibitSortVarious;
    private boolean defaultSortAlbumsByYear;
    private boolean defaultSortGenresByAlphabet;
    private boolean defaultProhibitSortVarious;

    // Search settings
    private boolean searchComposer;
    private boolean outputSearchQuery;

    // Suppressed legacy features
    private boolean useJsonp;
    private boolean useCast;
    private boolean usePartyMode;

    // Extensions and shortcuts
    private String musicFileTypes;
    private String videoFileTypes;
    private String coverArtFileTypes;
    private String excludedCoverArts;
    private String playlistFolder;
    private String shortcuts;
    private String defaultMusicFileTypes;
    private String defaultVideoFileTypes;
    private String defaultCoverArtFileTypes;
    private String defaultExcludedCoverArts;
    private String defaultPlaylistFolder;
    private String defaultShortcuts;

    // Welcom message
    private boolean gettingStartedEnabled;
    private String welcomeTitle;
    private String welcomeSubtitle;
    private String welcomeMessage;
    private String loginMessage;

    public List<Theme> getThemes() {
        return themes;
    }

    public void setThemes(List<Theme> themes) {
        this.themes = themes;
    }

    public String getThemeIndex() {
        return themeIndex;
    }

    public void setThemeIndex(String themeIndex) {
        this.themeIndex = themeIndex;
    }

    public List<String> getLocales() {
        return locales;
    }

    public void setLocales(List<String> locales) {
        this.locales = locales;
    }

    public String getLocaleIndex() {
        return localeIndex;
    }

    public void setLocaleIndex(String localeIndex) {
        this.localeIndex = localeIndex;
    }

    public IndexScheme getIndexScheme() {
        return indexScheme;
    }

    public void setIndexScheme(IndexScheme indexScheme) {
        this.indexScheme = indexScheme;
    }

    public String getDefaultIndexString() {
        return defaultIndexString;
    }

    public void setDefaultIndexString(String defaultIndexString) {
        this.defaultIndexString = defaultIndexString;
    }

    public String getSimpleIndexString() {
        return simpleIndexString;
    }

    public void setSimpleIndexString(String simpleIndexString) {
        this.simpleIndexString = simpleIndexString;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getIgnoredArticles() {
        return ignoredArticles;
    }

    public void setIgnoredArticles(String ignoredArticles) {
        this.ignoredArticles = ignoredArticles;
    }

    public boolean isIgnoreFullWidth() {
        return ignoreFullWidth;
    }

    public void setIgnoreFullWidth(boolean ignoreFullWidth) {
        this.ignoreFullWidth = ignoreFullWidth;
    }

    public boolean isDeleteDiacritic() {
        return deleteDiacritic;
    }

    public void setDeleteDiacritic(boolean deleteDiacritic) {
        this.deleteDiacritic = deleteDiacritic;
    }

    public boolean isSortAlbumsByYear() {
        return sortAlbumsByYear;
    }

    public void setSortAlbumsByYear(boolean sortAlbumsByYear) {
        this.sortAlbumsByYear = sortAlbumsByYear;
    }

    public boolean isSortGenresByAlphabet() {
        return sortGenresByAlphabet;
    }

    public void setSortGenresByAlphabet(boolean sortGenresByAlphabet) {
        this.sortGenresByAlphabet = sortGenresByAlphabet;
    }

    public boolean isProhibitSortVarious() {
        return prohibitSortVarious;
    }

    public void setProhibitSortVarious(boolean prohibitSortVarious) {
        this.prohibitSortVarious = prohibitSortVarious;
    }

    public boolean isDefaultSortAlbumsByYear() {
        return defaultSortAlbumsByYear;
    }

    public void setDefaultSortAlbumsByYear(boolean defaultSortAlbumsByYear) {
        this.defaultSortAlbumsByYear = defaultSortAlbumsByYear;
    }

    public boolean isDefaultSortGenresByAlphabet() {
        return defaultSortGenresByAlphabet;
    }

    public void setDefaultSortGenresByAlphabet(boolean defaultSortGenresByAlphabet) {
        this.defaultSortGenresByAlphabet = defaultSortGenresByAlphabet;
    }

    public boolean isDefaultProhibitSortVarious() {
        return defaultProhibitSortVarious;
    }

    public void setDefaultProhibitSortVarious(boolean defaultProhibitSortVarious) {
        this.defaultProhibitSortVarious = defaultProhibitSortVarious;
    }

    public boolean isSearchComposer() {
        return searchComposer;
    }

    public void setSearchComposer(boolean searchComposer) {
        this.searchComposer = searchComposer;
    }

    public boolean isOutputSearchQuery() {
        return outputSearchQuery;
    }

    public void setOutputSearchQuery(boolean outputSearchQuery) {
        this.outputSearchQuery = outputSearchQuery;
    }

    public boolean isUseJsonp() {
        return useJsonp;
    }

    public void setUseJsonp(boolean useJsonp) {
        this.useJsonp = useJsonp;
    }

    public boolean isUseCast() {
        return useCast;
    }

    public void setUseCast(boolean useCast) {
        this.useCast = useCast;
    }

    public boolean isUsePartyMode() {
        return usePartyMode;
    }

    public void setUsePartyMode(boolean usePartyMode) {
        this.usePartyMode = usePartyMode;
    }

    public String getMusicFileTypes() {
        return musicFileTypes;
    }

    public void setMusicFileTypes(String musicFileTypes) {
        this.musicFileTypes = musicFileTypes;
    }

    public String getVideoFileTypes() {
        return videoFileTypes;
    }

    public void setVideoFileTypes(String videoFileTypes) {
        this.videoFileTypes = videoFileTypes;
    }

    public String getCoverArtFileTypes() {
        return coverArtFileTypes;
    }

    public void setCoverArtFileTypes(String coverArtFileTypes) {
        this.coverArtFileTypes = coverArtFileTypes;
    }

    public String getExcludedCoverArts() {
        return excludedCoverArts;
    }

    public void setExcludedCoverArts(String excludedCoverArts) {
        this.excludedCoverArts = excludedCoverArts;
    }

    public String getPlaylistFolder() {
        return playlistFolder;
    }

    public void setPlaylistFolder(String playlistFolder) {
        this.playlistFolder = playlistFolder;
    }

    public String getShortcuts() {
        return shortcuts;
    }

    public void setShortcuts(String shortcuts) {
        this.shortcuts = shortcuts;
    }

    public String getDefaultMusicFileTypes() {
        return defaultMusicFileTypes;
    }

    public void setDefaultMusicFileTypes(String defaultMusicFileTypes) {
        this.defaultMusicFileTypes = defaultMusicFileTypes;
    }

    public String getDefaultVideoFileTypes() {
        return defaultVideoFileTypes;
    }

    public void setDefaultVideoFileTypes(String defaultVideoFileTypes) {
        this.defaultVideoFileTypes = defaultVideoFileTypes;
    }

    public String getDefaultCoverArtFileTypes() {
        return defaultCoverArtFileTypes;
    }

    public void setDefaultCoverArtFileTypes(String defaultCoverArtFileTypes) {
        this.defaultCoverArtFileTypes = defaultCoverArtFileTypes;
    }

    public String getDefaultExcludedCoverArts() {
        return defaultExcludedCoverArts;
    }

    public void setDefaultExcludedCoverArts(String defaultExcludedCoverArts) {
        this.defaultExcludedCoverArts = defaultExcludedCoverArts;
    }

    public String getDefaultPlaylistFolder() {
        return defaultPlaylistFolder;
    }

    public void setDefaultPlaylistFolder(String defaultPlaylistFolder) {
        this.defaultPlaylistFolder = defaultPlaylistFolder;
    }

    public String getDefaultShortcuts() {
        return defaultShortcuts;
    }

    public void setDefaultShortcuts(String defaultShortcuts) {
        this.defaultShortcuts = defaultShortcuts;
    }

    public boolean isGettingStartedEnabled() {
        return gettingStartedEnabled;
    }

    public void setGettingStartedEnabled(boolean gettingStartedEnabled) {
        this.gettingStartedEnabled = gettingStartedEnabled;
    }

    public String getWelcomeTitle() {
        return welcomeTitle;
    }

    public void setWelcomeTitle(String welcomeTitle) {
        this.welcomeTitle = welcomeTitle;
    }

    public String getWelcomeSubtitle() {
        return welcomeSubtitle;
    }

    public void setWelcomeSubtitle(String welcomeSubtitle) {
        this.welcomeSubtitle = welcomeSubtitle;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }

    public String getLoginMessage() {
        return loginMessage;
    }

    public void setLoginMessage(String loginMessage) {
        this.loginMessage = loginMessage;
    }
}

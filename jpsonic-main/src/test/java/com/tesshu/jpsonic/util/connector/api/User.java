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
 * (C) 2024 tesshucom
 */

package com.tesshu.jpsonic.util.connector.api;

import java.util.ArrayList;
import java.util.List;

import javax.xml.datatype.XMLGregorianCalendar;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "User", propOrder = { "folder" })
@SuppressWarnings("PMD.ShortClassName") // XJC Naming Conventions
public class User {

    @XmlElement(type = Integer.class)
    protected List<Integer> folder;
    @XmlAttribute(name = "username", required = true)
    protected String username;
    @XmlAttribute(name = "email")
    protected String email;
    @XmlAttribute(name = "scrobblingEnabled", required = true)
    protected boolean scrobblingEnabled;
    @XmlAttribute(name = "maxBitRate")
    protected Integer maxBitRate;
    @XmlAttribute(name = "adminRole", required = true)
    protected boolean adminRole;
    @XmlAttribute(name = "settingsRole", required = true)
    protected boolean settingsRole;
    @XmlAttribute(name = "downloadRole", required = true)
    protected boolean downloadRole;
    @XmlAttribute(name = "uploadRole", required = true)
    protected boolean uploadRole;
    @XmlAttribute(name = "playlistRole", required = true)
    protected boolean playlistRole;
    @XmlAttribute(name = "coverArtRole", required = true)
    protected boolean coverArtRole;
    @XmlAttribute(name = "commentRole", required = true)
    protected boolean commentRole;
    @XmlAttribute(name = "podcastRole", required = true)
    protected boolean podcastRole;
    @XmlAttribute(name = "streamRole", required = true)
    protected boolean streamRole;
    @XmlAttribute(name = "jukeboxRole", required = true)
    protected boolean jukeboxRole;
    @XmlAttribute(name = "shareRole", required = true)
    protected boolean shareRole;
    @XmlAttribute(name = "videoConversionRole", required = true)
    protected boolean videoConversionRole;
    @XmlAttribute(name = "avatarLastChanged")
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar avatarLastChanged;

    public List<Integer> getFolder() {
        if (folder == null) {
            folder = new ArrayList<>();
        }
        return this.folder;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String value) {
        this.username = value;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String value) {
        this.email = value;
    }

    public boolean isScrobblingEnabled() {
        return scrobblingEnabled;
    }

    public void setScrobblingEnabled(boolean value) {
        this.scrobblingEnabled = value;
    }

    public Integer getMaxBitRate() {
        return maxBitRate;
    }

    public void setMaxBitRate(Integer value) {
        this.maxBitRate = value;
    }

    public boolean isAdminRole() {
        return adminRole;
    }

    public void setAdminRole(boolean value) {
        this.adminRole = value;
    }

    public boolean isSettingsRole() {
        return settingsRole;
    }

    public void setSettingsRole(boolean value) {
        this.settingsRole = value;
    }

    public boolean isDownloadRole() {
        return downloadRole;
    }

    public void setDownloadRole(boolean value) {
        this.downloadRole = value;
    }

    public boolean isUploadRole() {
        return uploadRole;
    }

    public void setUploadRole(boolean value) {
        this.uploadRole = value;
    }

    public boolean isPlaylistRole() {
        return playlistRole;
    }

    public void setPlaylistRole(boolean value) {
        this.playlistRole = value;
    }

    public boolean isCoverArtRole() {
        return coverArtRole;
    }

    public void setCoverArtRole(boolean value) {
        this.coverArtRole = value;
    }

    public boolean isCommentRole() {
        return commentRole;
    }

    public void setCommentRole(boolean value) {
        this.commentRole = value;
    }

    public boolean isPodcastRole() {
        return podcastRole;
    }

    public void setPodcastRole(boolean value) {
        this.podcastRole = value;
    }

    public boolean isStreamRole() {
        return streamRole;
    }

    public void setStreamRole(boolean value) {
        this.streamRole = value;
    }

    public boolean isJukeboxRole() {
        return jukeboxRole;
    }

    public void setJukeboxRole(boolean value) {
        this.jukeboxRole = value;
    }

    public boolean isShareRole() {
        return shareRole;
    }

    public void setShareRole(boolean value) {
        this.shareRole = value;
    }

    public boolean isVideoConversionRole() {
        return videoConversionRole;
    }

    public void setVideoConversionRole(boolean value) {
        this.videoConversionRole = value;
    }

    public XMLGregorianCalendar getAvatarLastChanged() {
        return avatarLastChanged;
    }

    public void setAvatarLastChanged(XMLGregorianCalendar value) {
        this.avatarLastChanged = value;
    }
}

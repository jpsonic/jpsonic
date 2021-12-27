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

package com.tesshu.jpsonic.domain;

import java.io.File;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.base.Objects;

/**
 * Represents a top level directory in which music or other media is stored.
 *
 * @author Sindre Mehus
 */
@SuppressWarnings("serial")
public class MusicFolder implements Serializable {

    private final Integer id;
    private File path;
    private String name;
    private boolean enabled;
    private Date changed;

    /**
     * Creates a new music folder.
     *
     * @param id
     *            The system-generated ID.
     * @param path
     *            The path of the music folder.
     * @param name
     *            The user-defined name.
     * @param enabled
     *            Whether the folder is enabled.
     * @param changed
     *            When the corresponding database entry was last changed.
     */
    public MusicFolder(Integer id, File path, String name, boolean enabled, Date changed) {
        this.id = id;
        this.path = path;
        this.name = name;
        this.enabled = enabled;
        this.changed = changed;
    }

    /**
     * Creates a new music folder.
     *
     * @param path
     *            The path of the music folder.
     * @param name
     *            The user-defined name.
     * @param enabled
     *            Whether the folder is enabled.
     * @param changed
     *            When the corresponding database entry was last changed.
     */
    public MusicFolder(File path, String name, boolean enabled, Date changed) {
        this(null, path, name, enabled, changed);
    }

    /**
     * Returns the system-generated ID.
     *
     * @return The system-generated ID.
     */
    public Integer getId() {
        return id;
    }

    /**
     * Returns the path of the music folder.
     *
     * @return The path of the music folder.
     */
    public File getPath() {
        return path;
    }

    /**
     * Sets the path of the music folder.
     *
     * @param path
     *            The path of the music folder.
     */
    public void setPath(File path) {
        this.path = path;
    }

    /**
     * Returns the user-defined name.
     *
     * @return The user-defined name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the user-defined name.
     *
     * @param name
     *            The user-defined name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns whether the folder is enabled.
     *
     * @return Whether the folder is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the folder is enabled.
     *
     * @param enabled
     *            Whether the folder is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns when the corresponding database entry was last changed.
     *
     * @return When the corresponding database entry was last changed.
     */
    public Date getChanged() {
        return changed;
    }

    /**
     * Sets when the corresponding database entry was last changed.
     *
     * @param changed
     *            When the corresponding database entry was last changed.
     */
    public void setChanged(Date changed) {
        this.changed = changed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return Objects.equal(id, ((MusicFolder) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public static List<Integer> toIdList(List<MusicFolder> from) {
        return from.stream().map(toId()).collect(Collectors.toList());
    }

    public static List<String> toPathList(List<MusicFolder> from) {
        return from.stream().map(toPath()).collect(Collectors.toList());
    }

    public static Function<MusicFolder, Integer> toId() {
        return MusicFolder::getId;
    }

    public static Function<MusicFolder, String> toPath() {
        return from -> from.getPath().getPath();
    }
}
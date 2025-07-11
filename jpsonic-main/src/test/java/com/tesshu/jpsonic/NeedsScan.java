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

package com.tesshu.jpsonic;

import java.util.List;

import com.tesshu.jpsonic.domain.MusicFolder;

/**
 * Test case interface for scanning MusicFolder.
 */
public interface NeedsScan {

    /**
     * MusicFolder used by test class.
     *
     * @return MusicFolder used by test class
     */
    default List<MusicFolder> getMusicFolders() {
        return MusicFolderTestDataUtils.getTestMusicFolders();
    }

    /**
     * Whether the data input has been completed.
     *
     * @return Static AtomicBoolean indicating whether the data injection has been
     *         completed
     */
    boolean isDataBasePopulated();

    /**
     * Whether the data input has been completed.
     *
     * @return Static AtomicBoolean indicating whether the data injection has been
     *         completed
     */
    boolean isDataBaseReady();

    /**
     * Populate the database. Used when a subclass has a single test method. Can be
     * used even if there are multiple inner classes by @Nested.
     */
    void populateDatabase();

    /**
     * Populate the database only once. If you have multiple inner classes, we can
     * only use it if the class that uses this method is executed first.
     */
    void populateDatabaseOnlyOnce();

}

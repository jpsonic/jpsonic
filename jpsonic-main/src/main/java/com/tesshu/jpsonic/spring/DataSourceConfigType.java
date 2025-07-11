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

package com.tesshu.jpsonic.spring;

public enum DataSourceConfigType {
    JNDI("jndi"), URL("url"), HOST("host"), EMBED("embed"), LEGACY("legacy");

    private final String name;

    DataSourceConfigType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static DataSourceConfigType of(String name) {
        if (name == null) {
            return HOST;
        } else if (HOST.name().compareToIgnoreCase(name) == 0
                || LEGACY.name().compareToIgnoreCase(name) == 0) {
            return HOST;
        } else if (URL.name().compareToIgnoreCase(name) == 0
                || EMBED.name().compareToIgnoreCase(name) == 0) {
            return URL;
        } else if (JNDI.name().compareToIgnoreCase(name) == 0) {
            return JNDI;
        }
        return HOST;
    }
}

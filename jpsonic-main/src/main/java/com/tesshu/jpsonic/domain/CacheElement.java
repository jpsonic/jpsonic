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

public class CacheElement {

    private final long id;
    private final int type;
    private final String key;
    private final Object value;
    private final long created;

    public CacheElement(int type, String key, Object value, long created) {
        this.type = type;
        this.key = key;
        this.value = value;
        this.created = created;

        id = createId(type, key);
    }

    public static long createId(int type, String key) {
        return ((long) type << 32) | Math.abs((long) key.hashCode());
    }

    public long getId() {
        return id;
    }

    public int getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    public long getCreated() {
        return created;
    }
}

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

import com.tesshu.jpsonic.spring.DataSourceConfigType;
import org.checkerframework.checker.nullness.qual.NonNull;

public class DatabaseSettingsCommand extends SettingsPageCommons {

    @NonNull
    private DataSourceConfigType configType;

    private String embedDriver;
    private String embedUrl;
    private String embedUsername;
    private String embedPassword;
    private String jndiName;
    private int mysqlVarcharMaxlength;
    private String usertableQuote;

    public DataSourceConfigType getConfigType() {
        return configType;
    }

    public void setConfigType(DataSourceConfigType configType) {
        this.configType = configType;
    }

    public String getEmbedDriver() {
        return embedDriver;
    }

    public void setEmbedDriver(String embedDriver) {
        this.embedDriver = embedDriver;
    }

    public String getEmbedUrl() {
        return embedUrl;
    }

    public void setEmbedUrl(String embedUrl) {
        this.embedUrl = embedUrl;
    }

    public String getEmbedUsername() {
        return embedUsername;
    }

    public void setEmbedUsername(String embedUsername) {
        this.embedUsername = embedUsername;
    }

    public String getEmbedPassword() {
        return embedPassword;
    }

    public void setEmbedPassword(String embedPassword) {
        this.embedPassword = embedPassword;
    }

    public String getJNDIName() {
        return jndiName;
    }

    public void setJNDIName(String jndiName) {
        this.jndiName = jndiName;
    }

    public int getMysqlVarcharMaxlength() {
        return mysqlVarcharMaxlength;
    }

    public void setMysqlVarcharMaxlength(int mysqlVarcharMaxlength) {
        this.mysqlVarcharMaxlength = mysqlVarcharMaxlength;
    }

    public String getUsertableQuote() {
        return usertableQuote;
    }

    public void setUsertableQuote(String usertableQuote) {
        this.usertableQuote = usertableQuote;
    }
}

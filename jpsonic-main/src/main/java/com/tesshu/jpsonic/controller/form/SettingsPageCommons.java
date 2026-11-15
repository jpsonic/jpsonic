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
 * (C) 2021 tesshucom
 */

package com.tesshu.jpsonic.controller.form;

/**
 * Properties used for similar purposes on all Setting pages view. The settings
 * page defaults to as few display items as possible. These settings themselves
 * can be changed by the user.
 */
public class SettingsPageCommons {

    /**
     * Whether to display Radio in the menu.
     */
    private boolean useRadio;

    private boolean useDatabaseSettings;

    /**
     * Number of items in share. If 0, Share does not need to be displayed in the
     * menu.
     */
    private int shareCount;

    /**
     * Whether to display verbose help
     */
    private boolean showOutlineHelp;

    /**
     * Whether to open all summaries on the settings page by default.
     */
    private boolean openDetailSetting;

    /**
     * Whether to display a toast message after redirecting.
     */
    private boolean showToast;

    /**
     * Whether it is currently scanning
     */
    private boolean scanning;

    public final boolean isUseRadio() {
        return useRadio;
    }

    public final void setUseRadio(boolean useRadio) {
        this.useRadio = useRadio;
    }

    public boolean isUseDatabaseSettings() {
        return useDatabaseSettings;
    }

    public void setUseDatabaseSettings(boolean useDatabaseSettings) {
        this.useDatabaseSettings = useDatabaseSettings;
    }

    public final int getShareCount() {
        return shareCount;
    }

    public final void setShareCount(int shareCount) {
        this.shareCount = shareCount;
    }

    public final boolean isShowOutlineHelp() {
        return showOutlineHelp;
    }

    public final void setShowOutlineHelp(boolean showOutlineHelp) {
        this.showOutlineHelp = showOutlineHelp;
    }

    public final boolean isOpenDetailSetting() {
        return openDetailSetting;
    }

    public final void setOpenDetailSetting(boolean openDetailSetting) {
        this.openDetailSetting = openDetailSetting;
    }

    public final boolean isShowToast() {
        return showToast;
    }

    public final void setShowToast(boolean showToast) {
        this.showToast = showToast;
    }

    public boolean isScanning() {
        return scanning;
    }

    public void setScanning(boolean scanning) {
        this.scanning = scanning;
    }
}

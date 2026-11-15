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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.tesshu.jpsonic.controller.form.AdvancedSettingsCommand;
import com.tesshu.jpsonic.domain.model.AuthKey;
import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.domain.system.IndexScheme;
import com.tesshu.jpsonic.feature.auth.rememberme.KeyRotationPeriod;
import com.tesshu.jpsonic.feature.auth.rememberme.KeyRotationType;
import com.tesshu.jpsonic.feature.auth.rememberme.RMSKeys;
import com.tesshu.jpsonic.feature.auth.rememberme.RememberMeKeyManager;
import com.tesshu.jpsonic.feature.auth.rememberme.RememberMeStagingApplier;
import com.tesshu.jpsonic.feature.auth.rememberme.TokenValidityPeriod;
import com.tesshu.jpsonic.feature.upnp.UPnPSKeys;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Controller for the page used to administrate advanced settings.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/advancedSettings", "/advancedSettings.view" })
public class AdvancedSettingsController {

    private static final Logger LOG = LoggerFactory.getLogger(AdvancedSettingsController.class);

    private final SettingsFacade settingsFacade;
    private final UserService userService;
    private final RememberMeKeyManager rememberMeKeyManager;
    private final ShareService shareService;
    private final OutlineHelpSelector outlineHelpSelector;
    private final ScannerStateProvider scannerStateProvider;

    public AdvancedSettingsController(SettingsFacade settingsFacade, UserService userService,
            RememberMeKeyManager rememberMeKeyManager, ShareService shareService,
            OutlineHelpSelector outlineHelpSelector, ScannerStateProvider scannerStateProvider) {
        super();
        this.settingsFacade = settingsFacade;
        this.userService = userService;
        this.rememberMeKeyManager = rememberMeKeyManager;
        this.shareService = shareService;
        this.outlineHelpSelector = outlineHelpSelector;
        this.scannerStateProvider = scannerStateProvider;
    }

    @GetMapping
    protected String get(HttpServletRequest request, Model model) {
        AdvancedSettingsCommand command = new AdvancedSettingsCommand();

        command.setRememberMeEnable(settingsFacade.get(RMSKeys.enable));
        command
            .setRememberMeKeyRotationType(
                    KeyRotationType.of(settingsFacade.get(RMSKeys.rotationType)));
        command
            .setRememberMeKeyRotationPeriod(
                    KeyRotationPeriod.of(settingsFacade.get(RMSKeys.rotationPeriod)));
        command
            .setRememberMeTokenValidityPeriod(
                    TokenValidityPeriod.of(settingsFacade.get(RMSKeys.tokenValidityPeriod)));
        command.setSlidingExpirationEnabled(settingsFacade.get(RMSKeys.slidingExpirationEnable));

        AuthKey rememberMeKey = rememberMeKeyManager.getAuthKey();
        LocalDateTime lastUpdate = rememberMeKey
            .lastUpdate()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
        command
            .setRememberMeLastUpdate(
                    lastUpdate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // Bandwidth control
        command
            .setDownloadLimit(String
                .valueOf(settingsFacade.get(SKeys.advanced.bandwidth.downloadBitrateLimit)));
        command
            .setUploadLimit(String
                .valueOf(settingsFacade.get(SKeys.advanced.bandwidth.uploadBitrateLimit)));
        command
            .setBufferSize(String.valueOf(settingsFacade.get(SKeys.advanced.bandwidth.bufferSize)));
        command
            .setForceCompatibilityMode(
                    settingsFacade.get(UPnPSKeys.advanced.forceCompatibilityMode));

        // Email notification
        command.setSmtpFrom(settingsFacade.get(SKeys.advanced.smtp.from));
        command.setSmtpServer(settingsFacade.get(SKeys.advanced.smtp.server));
        command.setSmtpPort(settingsFacade.get(SKeys.advanced.smtp.port));
        command.setSmtpEncryption(settingsFacade.get(SKeys.advanced.smtp.encryption));
        command.setSmtpUser(settingsFacade.get(SKeys.advanced.smtp.user));

        // LDAP authentication
        command.setLdapEnabled(settingsFacade.get(SKeys.advanced.ldap.enabled));
        command.setLdapUrl(settingsFacade.get(SKeys.advanced.ldap.url));
        command.setLdapSearchFilter(settingsFacade.get(SKeys.advanced.ldap.searchFilter));
        command.setLdapManagerDn(settingsFacade.get(SKeys.advanced.ldap.managerDn));
        command.setLdapAutoShadowing(settingsFacade.get(SKeys.advanced.ldap.autoShadowing));
        command.setBrand(EnvironmentProvider.getInstance().getBrand());

        // Account recovery assistant
        command.setCaptchaEnabled(settingsFacade.get(SKeys.advanced.captcha.enabled));
        command.setRecaptchaSiteKey(settingsFacade.get(SKeys.advanced.captcha.siteKey));

        // Scan log
        command.setUseScanLog(settingsFacade.get(SKeys.advanced.scanLog.useScanLog));
        command.setScanLogRetention(settingsFacade.get(SKeys.advanced.scanLog.scanLogRetention));
        command.setUseScanEvents(settingsFacade.get(SKeys.advanced.scanLog.useScanEvents));
        command.setMeasureMemory(settingsFacade.get(SKeys.advanced.scanLog.measureMemory));

        // Danger Zone
        command
            .setIndexScheme(
                    IndexScheme.valueOf(settingsFacade.get(SKeys.advanced.index.indexSchemeName)));
        command
            .setForceInternalValueInsteadOfTags(
                    settingsFacade.get(SKeys.advanced.index.forceInternalValueInsteadOfTags));
        command.setSortAlphanum(settingsFacade.get(SKeys.advanced.sort.alphanum));
        command.setSortStrict(settingsFacade.get(SKeys.advanced.sort.strict));
        command.setDefaultSortAlphanum(SKeys.advanced.sort.alphanum.defaultValue());
        command.setDefaultSortStrict(SKeys.advanced.sort.strict.defaultValue());

        // for view page control
        command.setUseRadio(settingsFacade.get(SKeys.general.legacy.useRadio));
        command
            .setUseDatabaseSettings(settingsFacade.get(SKeys.general.legacy.useDatabaseSettings));
        command.setShareCount(shareService.getAllShares().size());
        User user = userService.getCurrentUserStrict(request);
        command
            .setShowOutlineHelp(outlineHelpSelector.isShowOutlineHelp(request, user.getUsername()));
        UserSettings userSettings = userService.getUserSettings(user.getUsername());
        command.setOpenDetailSetting(userSettings.isOpenDetailSetting());
        command.setScanning(scannerStateProvider.isScanning());

        model.addAttribute(Attributes.Model.Command.VALUE, command);
        return "advancedSettings";
    }

    @PostMapping("/rotate")
    protected String postRotate() {
        rememberMeKeyManager.rotate();
        return "redirect:/advancedSettings.view";
    }

    @PostMapping
    protected ModelAndView post(
            @ModelAttribute(Attributes.Model.Command.VALUE) AdvancedSettingsCommand command,
            RedirectAttributes redirectAttributes) {

        // RememberMe
        new RememberMeStagingApplier().apply(command, settingsFacade);

        // Bandwidth control
        try {
            settingsFacade
                .staging(SKeys.advanced.bandwidth.downloadBitrateLimit,
                        Long.parseLong(command.getDownloadLimit()));
            settingsFacade
                .staging(SKeys.advanced.bandwidth.uploadBitrateLimit,
                        Long.parseLong(command.getUploadLimit()));
            settingsFacade
                .staging(SKeys.advanced.bandwidth.bufferSize,
                        Integer.parseInt(command.getBufferSize()));
        } catch (NumberFormatException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error in parse of bitrateLimit or bufferSize.", e);
            }
        }
        settingsFacade
            .staging(UPnPSKeys.advanced.forceCompatibilityMode, command.isForceCompatibilityMode());

        // Email notification
        settingsFacade.staging(SKeys.advanced.smtp.from, command.getSmtpFrom());
        settingsFacade.staging(SKeys.advanced.smtp.server, command.getSmtpServer());
        settingsFacade.staging(SKeys.advanced.smtp.port, command.getSmtpPort());
        settingsFacade.staging(SKeys.advanced.smtp.encryption, command.getSmtpEncryption());
        settingsFacade.staging(SKeys.advanced.smtp.user, command.getSmtpUser());
        settingsFacade
            .stagingEncodedString(SKeys.advanced.smtp.password, command.getSmtpPassword());

        // LDAP authentication
        settingsFacade.staging(SKeys.advanced.ldap.enabled, command.isLdapEnabled());
        settingsFacade.staging(SKeys.advanced.ldap.url, command.getLdapUrl());
        settingsFacade.staging(SKeys.advanced.ldap.searchFilter, command.getLdapSearchFilter());
        settingsFacade.staging(SKeys.advanced.ldap.managerDn, command.getLdapManagerDn());
        settingsFacade
            .stagingEncodedString(SKeys.advanced.ldap.managerPassword,
                    command.getLdapManagerPassword());
        settingsFacade.staging(SKeys.advanced.ldap.autoShadowing, command.isLdapAutoShadowing());

        // Account recovery assistant
        settingsFacade.staging(SKeys.advanced.captcha.enabled, command.isCaptchaEnabled());
        settingsFacade.staging(SKeys.advanced.captcha.siteKey, command.getRecaptchaSiteKey());
        if (StringUtils.isNotEmpty(command.getRecaptchaSecretKey())) {
            settingsFacade
                .staging(SKeys.advanced.captcha.secretKey, command.getRecaptchaSecretKey());
        }

        // Scan log
        if (!scannerStateProvider.isScanning()) {
            setScanLog(command);
        }

        // Danger Zone
        if (!scannerStateProvider.isScanning()) {
            setDangerZone(command);
        }

        settingsFacade.commitAll();

        // for view page control
        redirectAttributes.addFlashAttribute(Attributes.Redirect.RELOAD_FLAG.value(), false);
        redirectAttributes.addFlashAttribute(Attributes.Redirect.TOAST_FLAG.value(), true);

        return new ModelAndView(new RedirectView(ViewName.ADVANCED_SETTINGS.value()));
    }

    @SuppressWarnings("unchecked")
    void setScanLog(AdvancedSettingsCommand command) {
        settingsFacade.staging(SKeys.advanced.scanLog.useScanLog, command.isUseScanLog());
        if (command.isUseScanLog()) {
            settingsFacade
                .staging(SKeys.advanced.scanLog.scanLogRetention, command.getScanLogRetention());
            settingsFacade.staging(SKeys.advanced.scanLog.useScanEvents, command.isUseScanEvents());
            settingsFacade.staging(SKeys.advanced.scanLog.measureMemory, command.isMeasureMemory());
        } else {
            settingsFacade.stagingDefault(SKeys.advanced.scanLog.scanLogRetention);
            settingsFacade
                .stagingDefault(SKeys.advanced.scanLog.useScanEvents,
                        SKeys.advanced.scanLog.measureMemory);
        }
    }

    private void setDangerZone(AdvancedSettingsCommand command) {

        IndexScheme scheme = command.getIndexScheme();
        settingsFacade.staging(SKeys.advanced.index.indexSchemeName, scheme.name());

        if (scheme == IndexScheme.NATIVE_JAPANESE) {
            settingsFacade.staging(SKeys.advanced.index.forceInternalValueInsteadOfTags, false);
            settingsFacade.staging(SKeys.advanced.index.deleteDiacritic, true);
            settingsFacade.staging(SKeys.advanced.index.ignoreFullWidth, true);
        } else if (scheme == IndexScheme.ROMANIZED_JAPANESE) {
            settingsFacade
                .staging(SKeys.advanced.index.forceInternalValueInsteadOfTags,
                        command.isForceInternalValueInsteadOfTags());
            settingsFacade.staging(SKeys.advanced.index.deleteDiacritic, false);
            settingsFacade.staging(SKeys.advanced.index.ignoreFullWidth, true);
        } else {
            settingsFacade.staging(SKeys.advanced.index.forceInternalValueInsteadOfTags, false);
            settingsFacade.staging(SKeys.advanced.index.deleteDiacritic, false);
            settingsFacade.staging(SKeys.advanced.index.ignoreFullWidth, false);
        }

        settingsFacade.staging(SKeys.advanced.sort.alphanum, command.isSortAlphanum());
        settingsFacade.staging(SKeys.advanced.sort.strict, command.isSortStrict());
    }
}

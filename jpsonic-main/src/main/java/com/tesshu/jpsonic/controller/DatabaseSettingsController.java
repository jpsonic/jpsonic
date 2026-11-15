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

import com.tesshu.jpsonic.controller.form.DatabaseSettingsCommand;
import com.tesshu.jpsonic.infrastructure.db.DataSourceConfigType;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.DBSKeys;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping({ "/databaseSettings", "/databaseSettings.view" })
public class DatabaseSettingsController {

    private final SettingsFacade settingsFacade;
    private final UserService userService;
    private final ShareService shareService;
    private final OutlineHelpSelector outlineHelpSelector;

    public DatabaseSettingsController(SettingsFacade settingsFacade, UserService userService,
            ShareService shareService, OutlineHelpSelector outlineHelpSelector) {
        super();
        this.settingsFacade = settingsFacade;
        this.userService = userService;
        this.shareService = shareService;
        this.outlineHelpSelector = outlineHelpSelector;
    }

    @GetMapping
    protected String get() {
        return "databaseSettings";
    }

    @ModelAttribute
    protected void formBackingObject(HttpServletRequest request, Model model) {
        DatabaseSettingsCommand command = new DatabaseSettingsCommand();
        command
            .setConfigType(DataSourceConfigType.of(settingsFacade.get(DBSKeys.databaseConfigType)));
        command.setEmbedDriver(settingsFacade.get(DBSKeys.embedDriver));
        command.setEmbedUrl(settingsFacade.get(DBSKeys.embedUrl));
        command.setEmbedUsername(settingsFacade.get(DBSKeys.embedUsername));
        command.setEmbedPassword(settingsFacade.get(DBSKeys.embedPassword));
        command.setJNDIName(settingsFacade.get(DBSKeys.jndiName));
        command.setMysqlVarcharMaxlength(settingsFacade.get(DBSKeys.mysqlVarcharMaxlength));
        command.setUsertableQuote(settingsFacade.get(DBSKeys.usertableQuote));

        // for view page control
        command.setUseRadio(settingsFacade.get(SKeys.general.legacy.useRadio));
        command
            .setUseDatabaseSettings(settingsFacade.get(SKeys.general.legacy.useDatabaseSettings));
        command.setShareCount(shareService.getAllShares().size());
        User user = userService.getCurrentUserStrict(request);
        command
            .setShowOutlineHelp(outlineHelpSelector.isShowOutlineHelp(request, user.getUsername()));
        model.addAttribute(Attributes.Model.Command.VALUE, command);
    }

    @PostMapping
    protected RedirectView onSubmit(
            @ModelAttribute(Attributes.Model.Command.VALUE) @Validated DatabaseSettingsCommand command,
            BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            resetDatabaseToDefault();
            settingsFacade.staging(DBSKeys.databaseConfigType, command.getConfigType().name());

            if (command.getConfigType() == DataSourceConfigType.URL) {
                settingsFacade.staging(DBSKeys.embedDriver, command.getEmbedDriver());
                settingsFacade.staging(DBSKeys.embedUrl, command.getEmbedUrl());
                settingsFacade.staging(DBSKeys.embedPassword, command.getEmbedPassword());
                settingsFacade.staging(DBSKeys.embedUsername, command.getEmbedUsername());
            } else if (command.getConfigType() == DataSourceConfigType.JNDI) {
                settingsFacade.staging(DBSKeys.jndiName, command.getJNDIName());
            }

            if (command.getConfigType() != DataSourceConfigType.HOST) {
                settingsFacade
                    .staging(DBSKeys.mysqlVarcharMaxlength, command.getMysqlVarcharMaxlength());
                settingsFacade.staging(DBSKeys.usertableQuote, command.getUsertableQuote());
            }
            redirectAttributes.addFlashAttribute(Attributes.Redirect.TOAST_FLAG.value(), true);
            settingsFacade.commitAll();
        }
        return new RedirectView(ViewName.DATABASE_SETTINGS.value());
    }

    void resetDatabaseToDefault() {
        settingsFacade
            .stagingDefault(DBSKeys.databaseConfigType, DBSKeys.embedDriver, DBSKeys.embedPassword,
                    DBSKeys.embedUrl, DBSKeys.embedUsername, DBSKeys.jndiName,
                    DBSKeys.usertableQuote);
        settingsFacade.stagingDefault(DBSKeys.mysqlVarcharMaxlength);
    }
}

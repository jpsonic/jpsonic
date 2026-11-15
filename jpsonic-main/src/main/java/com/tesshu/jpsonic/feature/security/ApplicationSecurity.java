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
 * (C) 2026 tesshucom
 */

package com.tesshu.jpsonic.feature.security;

import java.util.EnumSet;

import com.tesshu.jpsonic.feature.auth.core.CustomUserDetailsContextMapper;
import com.tesshu.jpsonic.feature.auth.core.PlainTextPasswordEncoder;
import com.tesshu.jpsonic.feature.auth.jwt.JWTAuthenticationProvider;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SystemSKeys;
import com.tesshu.jpsonic.service.JWTSecurityService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.SessionTrackingMode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.authentication.configuration.GlobalAuthenticationConfigurerAdapter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;

@SuppressWarnings("PMD.SignatureDeclareThrowsException")
@DependsOn("liquibase")
@Configuration
@ConditionalOnProperty(name = "jpsonic.feature.security", havingValue = "true", matchIfMissing = true)
@EnableMethodSecurity(securedEnabled = true)
public class ApplicationSecurity extends GlobalAuthenticationConfigurerAdapter {

    @Bean
    public ServletContextInitializer servletContextInitializer() {
        return context -> context.setSessionTrackingModes(EnumSet.of(SessionTrackingMode.COOKIE));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new PlainTextPasswordEncoder();
    }

    @EnableWebSecurity
    public static class AuthenticationManagerConfig {

        @Autowired
        public void configure(SettingsFacade settingsFacade, UserService userService,
                AuthenticationManagerBuilder auth,
                CustomUserDetailsContextMapper customUserDetailsContextMapper) throws Exception {
            if (settingsFacade.get(SKeys.advanced.ldap.enabled)) {
                auth
                    .ldapAuthentication()
                    .contextSource()
                    .managerDn(settingsFacade.get(SKeys.advanced.ldap.managerDn))
                    .managerPassword(
                            settingsFacade.getDecodedString(SKeys.advanced.ldap.managerPassword))
                    .url(settingsFacade.get(SKeys.advanced.ldap.url))
                    .and()
                    .userSearchFilter(settingsFacade.get(SKeys.advanced.ldap.searchFilter))
                    .userDetailsContextMapper(customUserDetailsContextMapper);
            }
            auth.userDetailsService(userService);

            String jwtKey = settingsFacade.get(SystemSKeys.deprecatedSecrets.jwtKey);
            if (StringUtils.isBlank(jwtKey)) {
                LoggerFactory.getLogger(ApplicationSecurity.class).warn("Generating new jwt key");
                jwtKey = JWTSecurityService.generateKey();
                settingsFacade.commit(SystemSKeys.deprecatedSecrets.jwtKey, jwtKey);
            }
            auth.authenticationProvider(new JWTAuthenticationProvider(jwtKey));
        }

        @Bean
        public AuthenticationManager authenticationManager(
                AuthenticationConfiguration authenticationConfiguration) throws Exception {
            return authenticationConfiguration.getAuthenticationManager();
        }
    }

    @Order(1)
    @Import(UpnpSecurityConfig.class)
    public static class Config1 {
    }

    @EnableWebSecurity
    @Order(2)
    @Import(ExtSecurityConfig.class)
    public static class Config2 {
    }

    @EnableWebSecurity
    @Order(3)
    @Import(ApiSecurityConfig.class)
    public static class Config3 {
    }
}

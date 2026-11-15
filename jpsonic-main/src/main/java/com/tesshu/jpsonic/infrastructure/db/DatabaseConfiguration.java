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

package com.tesshu.jpsonic.infrastructure.db;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.infrastructure.core.LifecyclePhase;
import com.tesshu.jpsonic.persistence.base.DaoHelper;
import com.tesshu.jpsonic.persistence.base.GenericDaoHelper;
import com.tesshu.jpsonic.persistence.base.LegacyHsqlDaoHelper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class DatabaseConfiguration {

    public static final long DS_CONNECTION_TIMEOUT = 60_000;
    public static final int DS_MINIMUM_IDLE = 0;
    public static final int DS_MAXIMUM_POOLSIZE = 8;
    public static final long DS_MAX_LIFE_TIME = 1_800_000;
    public static final long DS_IDLE_TIMEOUT = 300_000;
    public static final long DS_KEEP_ALIVE_TIME = 0;

    private final Environment environment;

    public static class ProfileNameConstants {

        public static final String HOST = "host";
        public static final String URL = "url";
        public static final String JNDI = "jndi";

        private ProfileNameConstants() {
        }
    }

    public DatabaseConfiguration(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public DataSourceTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Primary
    @Bean
    @DependsOn("liquibase")
    public DaoHelper legacyDaoHelper(DataSource dataSource) {
        return environment.acceptsProfiles(Profiles.of(ProfileNameConstants.HOST))
                ? new SmartLifecycleLegacyDaoHelper(new LegacyHsqlDaoHelper(dataSource))
                : new GenericDaoHelper(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    @DependsOn("liquibase")
    public DaoHelper daoHelper(DataSource dataSource) {
        return new GenericDaoHelper(dataSource);
    }

    private HikariConfig createConfig(String driver, String url, String user, String pass) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driver);
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(pass);
        config.setConnectionTimeout(DS_CONNECTION_TIMEOUT);
        config.setMinimumIdle(DS_MINIMUM_IDLE);
        config.setMaximumPoolSize(DS_MAXIMUM_POOLSIZE);
        config.setMaxLifetime(DS_MAX_LIFE_TIME);
        config.setIdleTimeout(DS_IDLE_TIMEOUT);
        config.setKeepaliveTime(DS_KEEP_ALIVE_TIME);
        return config;
    }

    @Bean
    @Profile(ProfileNameConstants.HOST)
    public DataSource legacyDataSource() {
        return new HikariDataSource(createConfig("org.hsqldb.jdbc.JDBCDriver",
                EnvironmentProvider.getInstance().buildDefaultJDBCUrl(),
                EnvironmentProvider.getInstance().getDefaultJDBCUsername(),
                EnvironmentProvider.getInstance().getDefaultJDBCPassword()));
    }

    @SuppressWarnings("PMD.UseObjectForClearerAPI") // Because it's spring API
    @Bean
    @Profile(ProfileNameConstants.URL)
    public DataSource embedDataSource(@Value("${DatabaseConfigEmbedDriver}") String driver,
            @Value("${DatabaseConfigEmbedUrl}") String url,
            @Value("${DatabaseConfigEmbedUsername}") String username,
            @Value("${DatabaseConfigEmbedPassword}") String password) {
        return new HikariDataSource(createConfig(driver, url, username, password));
    }

    @Bean
    @Profile(ProfileNameConstants.JNDI)
    public DataSource jndiDataSource(@Value("${DatabaseConfigJNDIName}") String jndiName) {
        JndiDataSourceLookup jndiLookup = new JndiDataSourceLookup();
        return jndiLookup.getDataSource(jndiName);
    }

    @Bean
    public Path rollbackFile() {
        return EnvironmentProvider.getInstance().getRollbackFilePath();
    }

    @Bean
    public String userTableQuote(@Value("${DatabaseUsertableQuote:}") String value) {
        return value;
    }

    @Bean
    public SpringLiquibase liquibase(DataSource dataSource,
            @Value("${DatabaseMysqlMaxlength:512}") String mysqlVarcharLimit,
            String userTableQuote) {
        SpringLiquibase springLiquibase = new AirsonicSpringLiquibase();
        springLiquibase.setDataSource(dataSource);
        springLiquibase.setChangeLog("classpath:liquibase/db-changelog.xml");
        springLiquibase.setRollbackFile(rollbackFile().toFile());
        Map<String, String> parameters = LegacyMap
            .of("defaultMusicFolder", EnvironmentProvider.getInstance().getDefaultMusicFolder(),
                    "mysqlVarcharLimit", mysqlVarcharLimit, "userTableQuote", userTableQuote);
        springLiquibase.setChangeLogParameters(parameters);
        return springLiquibase;
    }

    public static class SmartLifecycleLegacyDaoHelper implements DaoHelper, SmartLifecycle {

        private final LegacyHsqlDaoHelper delegate;
        private final AtomicBoolean running = new AtomicBoolean(false);

        public SmartLifecycleLegacyDaoHelper(LegacyHsqlDaoHelper delegate) {
            this.delegate = delegate;
        }

        @Override
        public void start() {
            running.set(true);
        }

        @Override
        public void stop() {
            delegate.shutdownHsqldbDatabase();
            running.set(false);
        }

        @Override
        public void stop(Runnable callback) {
            stop();
            callback.run();
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }

        @Override
        public int getPhase() {
            return LifecyclePhase.DATABASE.value;
        }

        @Override
        public JdbcTemplate getJdbcTemplate() {
            return delegate.getJdbcTemplate();
        }

        @Override
        public NamedParameterJdbcTemplate getNamedParameterJdbcTemplate() {
            return delegate.getNamedParameterJdbcTemplate();
        }

        @Override
        public DataSource getDataSource() {
            return delegate.getDataSource();
        }

        @Override
        public void checkpoint() {
            delegate.checkpoint();
        }
    }
}

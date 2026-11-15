/*
 * This file is part of Jpsonic.
 *
 * Jpsonic is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Jpsonic is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 *
 * (C) 2021 tesshucom
 */

package com.tesshu.jpsonic.service;

import static com.tesshu.jpsonic.util.PlayerUtils.now;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
public class PodcastScheduleConfiguration implements SchedulingConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(PodcastScheduleConfiguration.class);

    private final TaskScheduler taskScheduler;
    private final SettingsFacade settingsFacade;
    private final PodcastService podcastService;
    private final ScannerStateProvider scannerStateProvider;

    public PodcastScheduleConfiguration(TaskScheduler taskScheduler, SettingsFacade settingsFacade,
            PodcastService podcastService, ScannerStateProvider scannerStateProvider) {
        super();
        this.taskScheduler = taskScheduler;
        this.settingsFacade = settingsFacade;
        this.podcastService = podcastService;
        this.scannerStateProvider = scannerStateProvider;
    }

    /*
     * The first execution is 5 minutes after the server starts.
     */
    static final Instant createFirstTime() {
        return now().plus(5, ChronoUnit.MINUTES);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar scheduledTaskRegistrar) {

        scheduledTaskRegistrar.setScheduler(taskScheduler);

        scheduledTaskRegistrar.addTriggerTask(() -> {
            if (scannerStateProvider.isScanning()) {
                LOG.info("Is scanning. Automatic podcast updates will not be performed.");
            } else {
                LOG.info("Auto Podcast update will be performed.");
                podcastService.refreshAllChannels(true);
            }
        }, new PodcastUpdateTrigger(settingsFacade, scannerStateProvider));
    }

    static class PodcastUpdateTrigger implements Trigger {

        private final SettingsFacade settingsFacade;
        private final ScannerStateProvider scannerStateProvider;

        PodcastUpdateTrigger(SettingsFacade settingsFacade,
                ScannerStateProvider scannerStateProvider) {
            super();
            this.settingsFacade = settingsFacade;
            this.scannerStateProvider = scannerStateProvider;
        }

        @Override
        public Date nextExecutionTime(TriggerContext triggerContext) {

            Instant nextTime = nextExecution(triggerContext);
            String nextTimeString = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(nextTime);

            String msg;
            if (this.scannerStateProvider.isScanning()) {
                msg = "Auto Podcast update has been rescheduled because being scanning. (Next {})";
            } else {
                msg = "Auto Podcast update every "
                        + settingsFacade.get(SKeys.podcast.updateInterval)
                        + " hours was scheduled. (Next {})";
            }

            if (LOG.isInfoEnabled()) {
                LOG.info(msg, nextTimeString);
            }

            return Date.from(nextTime);
        }

        @Override
        public Instant nextExecution(TriggerContext triggerContext) {
            int hoursBetween = settingsFacade.get(SKeys.podcast.updateInterval);
            if (hoursBetween == -1) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Automatic Podcast update disabled.");
                }
                return null;
            }
            Instant lastTime = triggerContext.lastCompletion();
            boolean isReschedule = lastTime == null || this.scannerStateProvider.isScanning();
            return isReschedule ? createFirstTime() : lastTime.plus(hoursBetween, ChronoUnit.HOURS);
        }
    }
}

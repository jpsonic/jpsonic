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
 * (C) 2023 tesshucom
 */

package com.tesshu.jpsonic.controller;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.core.EnvironmentProvider;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent.ScanEventType;
import com.tesshu.jpsonic.persistence.core.entity.ScanLog;
import com.tesshu.jpsonic.persistence.core.entity.ScanLog.ScanLogType;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.persistence.core.entity.UserSettings;
import com.tesshu.jpsonic.persistence.core.repository.StaticsDao;
import com.tesshu.jpsonic.service.UserService;
import com.tesshu.jpsonic.util.StringUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping({ "/scanlog", "/scanlog.view" })
public class ScanLogController {

    private static final DateTimeFormatter DATE_AND_OPTIONAL_MILLI_TIME = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]");

    private final UserService userService;
    private final ScannerStateProvider scannerStateProvider;
    private final StaticsDao staticsDao;
    private final OutlineHelpSelector outlineHelpSelector;

    public ScanLogController(UserService userService, ScannerStateProvider scannerStateProvider,
            StaticsDao staticsDao, OutlineHelpSelector outlineHelpSelector) {
        super();
        this.userService = userService;
        this.scannerStateProvider = scannerStateProvider;
        this.staticsDao = staticsDao;
        this.outlineHelpSelector = outlineHelpSelector;
    }

    @GetMapping
    protected ModelAndView get(HttpServletRequest request,
            @RequestParam(value = Attributes.Request.NameConstants.START_DATE, required = false) String reqStartDate,
            @RequestParam(value = Attributes.Request.NameConstants.SHOW_SCANNED_COUNT, required = false) String reqShowCount) {

        Map<String, Object> model = LegacyMap.of();

        model.put("brand", EnvironmentProvider.getInstance().getBrand());
        model
            .put("admin",
                    userService.isAdmin(userService.getCurrentUserStrict(request).getUsername()));

        model.put("scanning", scannerStateProvider.isScanning());

        List<ScanLogVO> scanLogs = staticsDao
            .getScanLog(ScanLogType.SCAN_ALL)
            .stream()
            .map(ScanLogVO::new)
            .collect(Collectors.toList());

        if (!scanLogs.isEmpty()) {
            LocalDateTime lastStartDate = scanLogs.get(0).getStartDate();
            scanLogs.forEach(scanLog -> setStatus(lastStartDate, scanLog));
        }
        model.put("scanLogs", scanLogs);

        @Nullable
        LocalDateTime selectedStartDate = isEmpty(reqStartDate)
                ? scanLogs.isEmpty() ? null : scanLogs.get(0).getStartDate()
                : LocalDateTime.parse(reqStartDate);
        if (selectedStartDate != null) {
            model.put("startDate", selectedStartDate);
        }

        User user = userService.getCurrentUserStrict(request);
        model
            .put("showOutlineHelp",
                    outlineHelpSelector.isShowOutlineHelp(request, user.getUsername()));

        UserSettings userSettings = userService.getUserSettings(user.getUsername());
        if (userSettings.isShowScannedCount() != Boolean.parseBoolean(reqShowCount)) {
            userSettings.setShowScannedCount(Boolean.parseBoolean(reqShowCount));
            userService.updateUserSettings(userSettings);
        }
        boolean showScannedCount = userSettings.isShowScannedCount();
        model.put("showScannedCount", showScannedCount);

        if (selectedStartDate != null) {
            List<ScanEventVO> scanEvents = createScanEvents(selectedStartDate, showScannedCount);
            model.put("scanEvents", scanEvents);

            if (!scanEvents.isEmpty()) {
                String scanEventsDuration = getDurationString(selectedStartDate,
                        scanEvents.get(scanEvents.size() - 1).getExecuted());
                model.put("scanEventsDuration", scanEventsDuration);
            }
        }

        return new ModelAndView("scanLog", "model", model);
    }

    private List<ScanEventVO> createScanEvents(@NonNull LocalDateTime selectedStartDate,
            boolean showScannedCount) {
        @SuppressWarnings("deprecation")
        List<ScanEventVO> scanEvents = staticsDao
            .getScanEvents(selectedStartDate.atZone(ZoneOffset.systemDefault()).toInstant())
            .stream()
            .filter(scanEvent -> showScannedCount
                    || scanEvent.getType() != ScanEventType.SCANNED_COUNT
                            && scanEvent.getType() != ScanEventType.PARSED_COUNT)
            .map(ScanEventVO::new)
            .collect(Collectors.toList());
        setDurations(selectedStartDate, scanEvents);
        return scanEvents;
    }

    @SuppressWarnings("deprecation")
    private void setStatus(@NonNull LocalDateTime lastStartDate, ScanLogVO scanLog) {
        final ScanEventType lastEventType = staticsDao
            .getLastScanEventType(
                    scanLog.getStartDate().atZone(ZoneOffset.systemDefault()).toInstant());
        switch (lastEventType) {
        case SUCCESS:
        case FAILED:
        case DESTROYED:
        case CANCELED:
        case FINISHED:
            scanLog.setStatus(lastEventType.name());
            break;
        default:
            if (lastStartDate.equals(scanLog.getStartDate()) && scannerStateProvider.isScanning()) {
                scanLog.setStatus("SCANNING");
            } else {
                scanLog.setStatus(ScanEventType.UNKNOWN.name());
            }
            break;
        }
    }

    private void setDurations(@NonNull LocalDateTime startDate, List<ScanEventVO> scanEvents) {
        if (scanEvents.isEmpty()) {
            return;
        }
        scanEvents
            .get(0)
            .setDuration(getDurationString(startDate, scanEvents.get(0).getExecuted()));
        for (int i = 0; i < scanEvents.size(); i++) {
            if (scanEvents.get(i).getDuration() != null) {
                continue;
            }
            scanEvents
                .get(i)
                .setDuration(getDurationString(scanEvents.get(i - 1).getExecuted(),
                        scanEvents.get(i).getExecuted()));
        }
    }

    private String getDurationString(LocalDateTime from, LocalDateTime to) {
        Duration duration = Duration.between(from, to);
        return StringUtil.formatDurationHMMSS(duration.toSeconds()) + "."
                + String.format("%03d", duration.toMillis() % 1_000);
    }

    public static class ScanLogVO {

        private final LocalDateTime startDate;
        private final String startDateStr;
        private final ScanLogType type;
        private String status;

        ScanLogVO(@NonNull ScanLog scanLog) {
            super();
            this.startDate = LocalDateTime
                .ofInstant(scanLog.getStartDate(), ZoneId.systemDefault());
            this.startDateStr = DATE_AND_OPTIONAL_MILLI_TIME.format(startDate);
            this.type = scanLog.getType();
        }

        public LocalDateTime getStartDate() {
            return startDate;
        }

        public String getStartDateStr() {
            return startDateStr;
        }

        public ScanLogType getType() {
            return type;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class ScanEventVO {

        private final LocalDateTime executed;
        private final String executedStr;
        private final ScanEventType type;
        private final long maxMemory;
        private final long totalMemory;
        private final long usedMemory;
        private final String comment;
        private String duration;

        public ScanEventVO(@NonNull ScanEvent scanEvent) {
            super();
            this.executed = LocalDateTime
                .ofInstant(scanEvent.getExecuted(), ZoneId.systemDefault());
            this.executedStr = DATE_AND_OPTIONAL_MILLI_TIME.format(executed);
            this.type = scanEvent.getType();
            this.maxMemory = scanEvent.getMaxMemory();
            this.totalMemory = scanEvent.getTotalMemory();
            this.usedMemory = scanEvent.getTotalMemory() - scanEvent.getFreeMemory();
            this.comment = scanEvent.getComment();
        }

        public LocalDateTime getExecuted() {
            return executed;
        }

        public String getExecutedStr() {
            return executedStr;
        }

        public ScanEventType getType() {
            return type;
        }

        public long getMaxMemory() {
            return maxMemory;
        }

        public long getTotalMemory() {
            return totalMemory;
        }

        public long getUsedMemory() {
            return usedMemory;
        }

        public String getComment() {
            return comment;
        }

        public String getDuration() {
            return duration;
        }

        public void setDuration(String duration) {
            this.duration = duration;
        }
    }
}

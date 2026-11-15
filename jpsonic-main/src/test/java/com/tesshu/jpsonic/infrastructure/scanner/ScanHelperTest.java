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
 * (C) 2025 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.scanner;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.tesshu.jpsonic.domain.provider.resource.ServerLocaleProvider;
import com.tesshu.jpsonic.infrastructure.comparator.ComparatorsFacade;
import com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators;
import com.tesshu.jpsonic.infrastructure.language.MetadataReadingProcessor;
import com.tesshu.jpsonic.infrastructure.locale.ServerLocaleManager;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent.ScanEventType;
import com.tesshu.jpsonic.persistence.core.repository.StaticsDao;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class ScanHelperTest {

    private SettingsFacade settingsFacade;
    private ScanHelper scanHelper;
    private StaticsDao staticsDao;

    @BeforeEach
    void setup() {
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps,
                    SKeys.musicFolder.scan.ignoreFileTimestamps.defaultValue())
            .withString(SKeys.podcast.folder, SKeys.podcast.folder.defaultValue())
            .withBoolean(SKeys.advanced.sort.strict, SKeys.advanced.sort.strict.defaultValue())
            .withBoolean(SKeys.advanced.scanLog.useScanLog,
                    SKeys.advanced.scanLog.useScanLog.defaultValue())
            .withInt(SKeys.advanced.scanLog.scanLogRetention,
                    SKeys.advanced.scanLog.scanLogRetention.defaultValue())
            .withBoolean(SKeys.advanced.scanLog.useScanEvents,
                    SKeys.advanced.scanLog.useScanEvents.defaultValue())
            .withBoolean(SKeys.advanced.scanLog.measureMemory,
                    SKeys.advanced.scanLog.measureMemory.defaultValue())
            .build();
        init();
    }

    @Ignore
    void init() {

        final ScannerStateServiceImpl scannerStateService = mock(ScannerStateServiceImpl.class);
        staticsDao = mock(StaticsDao.class);
        final MediaFileDao mediaFileDao = mock(MediaFileDao.class);
        final Indexer indexer = mock(Indexer.class);
        final WritableMediaFileService wmfs = mock(WritableMediaFileService.class);
        scanHelper = new ScanHelper(scannerStateService, settingsFacade, staticsDao, mediaFileDao,
                indexer, wmfs);
    }

    @Test
    void testCreateScanEvent() {
        Instant startDate = now();
        ScanContext context = new ScanContext(startDate,
                settingsFacade.get(SKeys.musicFolder.scan.ignoreFileTimestamps),
                settingsFacade.get(SKeys.podcast.folder),
                settingsFacade.get(SKeys.advanced.sort.strict),
                settingsFacade.get(SKeys.advanced.scanLog.useScanLog),
                settingsFacade.get(SKeys.advanced.scanLog.scanLogRetention),
                SKeys.advanced.scanLog.scanLogRetention.defaultValue(),
                settingsFacade.get(SKeys.advanced.scanLog.useScanEvents),
                settingsFacade.get(SKeys.advanced.scanLog.measureMemory));

        scanHelper.createScanEvent(context, ScanEventType.SUCCESS, null);
        Mockito.verify(staticsDao, Mockito.times(1)).createScanEvent(Mockito.any(ScanEvent.class));
        scanHelper.createScanEvent(context, ScanEventType.CANCELED, null);
        Mockito.verify(staticsDao, Mockito.times(2)).createScanEvent(Mockito.any(ScanEvent.class));
        scanHelper.createScanEvent(context, ScanEventType.DESTROYED, null);
        Mockito.verify(staticsDao, Mockito.times(3)).createScanEvent(Mockito.any(ScanEvent.class));
        Mockito.clearInvocations(staticsDao);
        scanHelper.createScanEvent(context, ScanEventType.PARSE_FILE_STRUCTURE, null);
        Mockito.verify(staticsDao, Mockito.never()).createScanEvent(Mockito.any(ScanEvent.class));

        // Log all events if useScanEvents=true
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps,
                    SKeys.musicFolder.scan.ignoreFileTimestamps.defaultValue())
            .withString(SKeys.podcast.folder, SKeys.podcast.folder.defaultValue())
            .withBoolean(SKeys.advanced.sort.strict, SKeys.advanced.sort.strict.defaultValue())
            .withBoolean(SKeys.advanced.scanLog.useScanLog,
                    SKeys.advanced.scanLog.useScanLog.defaultValue())
            .withInt(SKeys.advanced.scanLog.scanLogRetention,
                    SKeys.advanced.scanLog.scanLogRetention.defaultValue())
            .withBoolean(SKeys.advanced.scanLog.useScanEvents, true)
            .withBoolean(SKeys.advanced.scanLog.measureMemory,
                    SKeys.advanced.scanLog.measureMemory.defaultValue())
            .build();
        init();
        context = new ScanContext(startDate,
                settingsFacade.get(SKeys.musicFolder.scan.ignoreFileTimestamps),
                settingsFacade.get(SKeys.podcast.folder),
                settingsFacade.get(SKeys.advanced.sort.strict),
                settingsFacade.get(SKeys.advanced.scanLog.useScanLog),
                settingsFacade.get(SKeys.advanced.scanLog.scanLogRetention),
                SKeys.advanced.scanLog.scanLogRetention.defaultValue(),
                settingsFacade.get(SKeys.advanced.scanLog.useScanEvents),
                settingsFacade.get(SKeys.advanced.scanLog.measureMemory));
        Mockito.clearInvocations(staticsDao);

        scanHelper.createScanEvent(context, ScanEventType.SUCCESS, null);
        Mockito.verify(staticsDao, Mockito.times(1)).createScanEvent(Mockito.any(ScanEvent.class));
        scanHelper.createScanEvent(context, ScanEventType.CANCELED, null);
        Mockito.verify(staticsDao, Mockito.times(2)).createScanEvent(Mockito.any(ScanEvent.class));
        scanHelper.createScanEvent(context, ScanEventType.DESTROYED, null);
        Mockito.verify(staticsDao, Mockito.times(3)).createScanEvent(Mockito.any(ScanEvent.class));
        scanHelper.createScanEvent(context, ScanEventType.PARSE_FILE_STRUCTURE, null);
        Mockito.verify(staticsDao, Mockito.times(4)).createScanEvent(Mockito.any(ScanEvent.class));

        // No memory metering
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps,
                    SKeys.musicFolder.scan.ignoreFileTimestamps.defaultValue())
            .withString(SKeys.podcast.folder, SKeys.podcast.folder.defaultValue())
            .withBoolean(SKeys.advanced.sort.strict, SKeys.advanced.sort.strict.defaultValue())
            .withBoolean(SKeys.advanced.scanLog.useScanLog,
                    SKeys.advanced.scanLog.useScanLog.defaultValue())
            .withInt(SKeys.advanced.scanLog.scanLogRetention,
                    SKeys.advanced.scanLog.scanLogRetention.defaultValue())
            .withBoolean(SKeys.advanced.scanLog.useScanEvents, true)
            .withBoolean(SKeys.advanced.scanLog.measureMemory, false)
            .build();
        init();
        context = new ScanContext(startDate,
                settingsFacade.get(SKeys.musicFolder.scan.ignoreFileTimestamps),
                settingsFacade.get(SKeys.podcast.folder),
                settingsFacade.get(SKeys.advanced.sort.strict),
                settingsFacade.get(SKeys.advanced.scanLog.useScanLog),
                settingsFacade.get(SKeys.advanced.scanLog.scanLogRetention),
                SKeys.advanced.scanLog.scanLogRetention.defaultValue(),
                settingsFacade.get(SKeys.advanced.scanLog.useScanEvents),
                settingsFacade.get(SKeys.advanced.scanLog.measureMemory));
        Mockito.clearInvocations(staticsDao);
        ArgumentCaptor<ScanEvent> eventCap = ArgumentCaptor.forClass(ScanEvent.class);
        Mockito.doNothing().when(staticsDao).createScanEvent(eventCap.capture());
        scanHelper.createScanEvent(context, ScanEventType.PARSE_FILE_STRUCTURE, null);
        assertEquals(-1, eventCap.getValue().getFreeMemory());

        // With memory metering
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps,
                    SKeys.musicFolder.scan.ignoreFileTimestamps.defaultValue())
            .withString(SKeys.podcast.folder, SKeys.podcast.folder.defaultValue())
            .withBoolean(SKeys.advanced.sort.strict, SKeys.advanced.sort.strict.defaultValue())
            .withBoolean(SKeys.advanced.scanLog.useScanLog,
                    SKeys.advanced.scanLog.useScanLog.defaultValue())
            .withInt(SKeys.advanced.scanLog.scanLogRetention,
                    SKeys.advanced.scanLog.scanLogRetention.defaultValue())
            .withBoolean(SKeys.advanced.scanLog.useScanEvents, true)
            .withBoolean(SKeys.advanced.scanLog.measureMemory, true)
            .build();
        init();
        context = new ScanContext(startDate,
                settingsFacade.get(SKeys.musicFolder.scan.ignoreFileTimestamps),
                settingsFacade.get(SKeys.podcast.folder),
                settingsFacade.get(SKeys.advanced.sort.strict),
                settingsFacade.get(SKeys.advanced.scanLog.useScanLog),
                settingsFacade.get(SKeys.advanced.scanLog.scanLogRetention),
                SKeys.advanced.scanLog.scanLogRetention.defaultValue(),
                settingsFacade.get(SKeys.advanced.scanLog.useScanEvents),
                settingsFacade.get(SKeys.advanced.scanLog.measureMemory));
        Mockito.clearInvocations(staticsDao);
        eventCap = ArgumentCaptor.forClass(ScanEvent.class);
        Mockito.doNothing().when(staticsDao).createScanEvent(eventCap.capture());
        scanHelper.createScanEvent(context, ScanEventType.PARSE_FILE_STRUCTURE, null);
        assertNotEquals(-1, eventCap.getValue().getFreeMemory());
    }

    @Test
    void testInvokeUpdateOrder() {

        MediaFile m1 = new MediaFile();
        m1.setPathString("path1");
        m1.setPresent(true);
        m1.setOrder(1);
        MediaFile m2 = new MediaFile();
        m2.setPathString("path2");
        m2.setPresent(true);
        m2.setOrder(2);
        MediaFile m3 = new MediaFile();
        m3.setPathString("path3");
        m3.setPresent(true);
        m3.setOrder(3);

        SettingsFacade settingsFacade = SettingsFacadeBuilder.create().build();
        ServerLocaleProvider serverLocaleProvider = new ServerLocaleManager(settingsFacade);
        MetadataReadingProcessor proc = mock(MetadataReadingProcessor.class);
        ComparatorsFacade comparatorsFacade = new ComparatorsFacade(settingsFacade,
                serverLocaleProvider, proc);
        JpsonicComparators comparators = new JpsonicComparators(settingsFacade, comparatorsFacade);
        WritableMediaFileService wmfs = mock(WritableMediaFileService.class);

        ArgumentCaptor<MediaFile> captor = ArgumentCaptor.forClass(MediaFile.class);
        Mockito.when(wmfs.updateOrder(captor.capture())).thenReturn(1);

        int count = scanHelper
            .invokeUpdateOrder(Arrays.asList(m2, m3, m1), comparators.songsDefault(),
                    wmfs::updateOrder);
        assertEquals(3, count);

        List<MediaFile> result = captor.getAllValues();
        assertEquals(3, result.size());
        assertEquals("path1", result.get(0).getPathString());
        assertEquals("path2", result.get(1).getPathString());
        assertEquals("path3", result.get(2).getPathString());

        captor = ArgumentCaptor.forClass(MediaFile.class);
        Mockito.when(wmfs.updateOrder(captor.capture())).thenReturn(1);
        scanHelper.invokeUpdateOrder(result, comparators.songsDefault(), wmfs::updateOrder);
        assertEquals(0, captor.getAllValues().size());

        m1.setOrder(3);
        m2.setOrder(2);
        m3.setOrder(1);
        count = scanHelper
            .invokeUpdateOrder(Arrays.asList(m1, m2, m3), comparators.songsDefault(),
                    wmfs::updateOrder);
        assertEquals(2, count);
        result = captor.getAllValues();
        assertEquals(2, result.size());
        assertEquals("path1", result.get(0).getPathString());
        assertEquals(1, result.get(0).getOrder());
        assertEquals("path3", result.get(1).getPathString());
        assertEquals(3, result.get(1).getOrder());

        captor = ArgumentCaptor.forClass(MediaFile.class);
        Mockito.when(wmfs.updateOrder(captor.capture())).thenReturn(1);
        scanHelper.invokeUpdateOrder(result, comparators.songsDefault(), wmfs::updateOrder);
        result = captor.getAllValues();
        assertEquals(1, result.size());
        assertEquals("path3", result.get(0).getPathString());
        assertEquals(2, result.get(0).getOrder());

        captor = ArgumentCaptor.forClass(MediaFile.class);
        Mockito.when(wmfs.updateOrder(captor.capture())).thenReturn(1);
        scanHelper.invokeUpdateOrder(result, comparators.songsDefault(), wmfs::updateOrder);
        result = captor.getAllValues();
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getOrder());

        captor = ArgumentCaptor.forClass(MediaFile.class);
        Mockito.when(wmfs.updateOrder(captor.capture())).thenReturn(1);
        scanHelper.invokeUpdateOrder(result, comparators.songsDefault(), wmfs::updateOrder);
        result = captor.getAllValues();
        assertEquals(0, result.size());
    }
}

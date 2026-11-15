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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.ArtistDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.service.MediaFileCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class PreScanProcedureTest {

    @Nested
    class CheckMudicFoldersTest {

        private SettingsFacade settingsFacade;
        private MusicFolderServiceImpl musicFolderService;
        private PreScanProcedure preScanProc;

        @BeforeEach
        void setup() {
            settingsFacade = SettingsFacadeBuilder.create().buildWithDefault();
            musicFolderService = mock(MusicFolderServiceImpl.class);
            final IndexManager indexManager = mock(IndexManager.class);
            final MediaFileDao mediaFileDao = mock(MediaFileDao.class);
            final ArtistDao artistDao = mock(ArtistDao.class);
            final MediaFileCache mediaFileCache = mock(MediaFileCache.class);
            final ScanHelper scanHelper = mock(ScanHelper.class);
            preScanProc = new PreScanProcedure(musicFolderService, indexManager, mediaFileDao,
                    artistDao, mediaFileCache, scanHelper);
        }

        @Test
        void testExistenceCheck() throws URISyntaxException {
            MusicFolder existingFolder = new MusicFolder(1,
                    Path
                        .of(PreScanProcedureTest.class.getResource("/MEDIAS/Music").toURI())
                        .toString(),
                    "Existing", true, now(), 1, false);
            List<MusicFolder> folders = Arrays.asList(existingFolder);
            Mockito.when(musicFolderService.getAllMusicFolders(false, true)).thenReturn(folders);
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
            preScanProc.checkMusicFolders(context);
            Mockito
                .verify(musicFolderService, Mockito.never())
                .updateMusicFolder(startDate, existingFolder);

            MusicFolder notExistingFolder = new MusicFolder(2,
                    existingFolder.getPathString() + "99", "Not existing", true, now(), 2, false);
            folders = Arrays.asList(existingFolder, notExistingFolder);
            Mockito.when(musicFolderService.getAllMusicFolders(false, true)).thenReturn(folders);
            preScanProc.checkMusicFolders(context);
            Mockito
                .verify(musicFolderService, Mockito.never())
                .updateMusicFolder(startDate, existingFolder);
            Mockito
                .verify(musicFolderService, Mockito.times(1))
                .updateMusicFolder(startDate, notExistingFolder);
            Mockito.clearInvocations(musicFolderService);

            MusicFolder existingFile = new MusicFolder(3,
                    Path
                        .of(PreScanProcedureTest.class.getResource("/MEDIAS/piano.mp3").toURI())
                        .toString(),
                    "Existing file", true, now(), 3, false);
            folders = Arrays.asList(existingFolder, notExistingFolder, existingFile);
            Mockito.when(musicFolderService.getAllMusicFolders(false, true)).thenReturn(folders);
            preScanProc.checkMusicFolders(context);
            Mockito
                .verify(musicFolderService, Mockito.never())
                .updateMusicFolder(startDate, existingFolder);
            Mockito
                .verify(musicFolderService, Mockito.times(1))
                .updateMusicFolder(startDate, notExistingFolder);
            Mockito
                .verify(musicFolderService, Mockito.times(1))
                .updateMusicFolder(startDate, existingFile);
        }

        @Test
        void testOrderCheck() throws URISyntaxException {
            MusicFolder orderedFolder = new MusicFolder(1,
                    Path
                        .of(PreScanProcedureTest.class.getResource("/MEDIAS/Music").toURI())
                        .toString(),
                    "Ordered", true, now(), 1, false);
            List<MusicFolder> folders = Arrays.asList(orderedFolder);
            Mockito.when(musicFolderService.getAllMusicFolders(false, true)).thenReturn(folders);
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

            preScanProc.checkMusicFolders(context);
            Mockito
                .verify(musicFolderService, Mockito.never())
                .updateMusicFolder(startDate, orderedFolder);
            Mockito.clearInvocations(musicFolderService);

            MusicFolder notOrderedFolder1 = new MusicFolder(2,
                    Path
                        .of(PreScanProcedureTest.class.getResource("/MEDIAS/Music2").toURI())
                        .toString(),
                    "Music2", true, now(), -1, false);
            MusicFolder notOrderedFolder2 = new MusicFolder(3,
                    Path
                        .of(PreScanProcedureTest.class.getResource("/MEDIAS/Music3").toURI())
                        .toString(),
                    "Music3", true, now(), -1, false);
            folders = Arrays.asList(orderedFolder, notOrderedFolder1, notOrderedFolder2);
            Mockito.when(musicFolderService.getAllMusicFolders(false, true)).thenReturn(folders);
            ArgumentCaptor<MusicFolder> folderCaptor = ArgumentCaptor.forClass(MusicFolder.class);
            Mockito
                .doNothing()
                .when(musicFolderService)
                .updateMusicFolder(Mockito.any(Instant.class), folderCaptor.capture());
            preScanProc.checkMusicFolders(context);
            assertEquals(3, folderCaptor.getAllValues().size());
            assertEquals(1, folderCaptor.getAllValues().get(0).getFolderOrder());
            assertEquals(2, folderCaptor.getAllValues().get(1).getFolderOrder());
            assertEquals(3, folderCaptor.getAllValues().get(2).getFolderOrder());
        }
    }
}

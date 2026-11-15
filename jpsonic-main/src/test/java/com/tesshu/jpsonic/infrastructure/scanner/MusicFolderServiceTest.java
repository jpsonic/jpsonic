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

package com.tesshu.jpsonic.infrastructure.scanner;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Documented;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.MusicFolderDao;
import com.tesshu.jpsonic.persistence.core.repository.StaticsDao;
import com.tesshu.jpsonic.util.PlayerUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class MusicFolderServiceTest {

    private MusicFolderDao musicFolderDao;
    private MusicFolderServiceImpl musicFolderService;

    private static final String USER_NAME = "user";

    @BeforeEach
    void setup() throws ExecutionException, URISyntaxException {
        musicFolderDao = mock(MusicFolderDao.class);
        ScannerStateServiceImpl scannerStateService = mock(ScannerStateServiceImpl.class);
        Mockito.when(scannerStateService.tryScanningLock()).thenReturn(true);
        musicFolderService = new MusicFolderServiceImpl(musicFolderDao, mock(StaticsDao.class),
                scannerStateService, mock(ApplicationEventPublisher.class));

        MusicFolder m1 = new MusicFolder(1, "/dummy/path", "Disabled&NonExisting", false, null, 0,
                false);
        MusicFolder m2 = new MusicFolder(2, "/dummy/path", "Enabled&NonExisting", true, null, 1,
                false);
        Path existingPath1 = Path
            .of(MusicFolderServiceTest.class.getResource("/MEDIAS/Music").toURI());
        assertTrue(Files.exists(existingPath1));
        MusicFolder m3 = new MusicFolder(3, existingPath1.toString(), "Disabled&Existing", false,
                null, 3, false);
        Path existingPath2 = Path
            .of(MusicFolderServiceTest.class.getResource("/MEDIAS/Music2").toURI());
        assertTrue(Files.exists(existingPath2));
        MusicFolder m4 = new MusicFolder(4, existingPath2.toString(), "Enabled&Existing", true,
                null, 4, false);
        List<MusicFolder> folders = Arrays.asList(m1, m2, m3, m4);
        Mockito.when(musicFolderDao.getAllMusicFolders()).thenReturn(folders);
    }

    @Documented
    private @interface GetAllMusicFoldersDecisions {
        @interface Conditions {
            @interface IncludeDisabled {
                @interface True {
                }

                @interface False {
                }
            }

            @interface IncludeNonExisting {
                @interface True {
                }

                @interface False {
                }
            }
        }
    }

    @Test
    @GetAllMusicFoldersDecisions.Conditions.IncludeDisabled.False
    @GetAllMusicFoldersDecisions.Conditions.IncludeNonExisting.True
    // Same as GetAllMusicFoldersDisabledExistingTest#c03 if redundantFolderCheck =
    // false, otherwise same as c01
    void testGetAllMusicFolders() {
        // Files.exists is never called
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders();
        assertEquals(2, folders.size());
        assertEquals("Enabled&NonExisting", folders.get(0).getName());
        assertEquals("Enabled&Existing", folders.get(1).getName());
    }

    @Nested
    class GetAllMusicFoldersDisabledExistingTest {

        @Test
        @GetAllMusicFoldersDecisions.Conditions.IncludeDisabled.False
        @GetAllMusicFoldersDecisions.Conditions.IncludeNonExisting.False
        // Files.exists will be called 2 times
        void c01() {
            List<MusicFolder> folders = musicFolderService.getAllMusicFolders(false, false);
            assertEquals(1, folders.size());
            assertEquals("Enabled&Existing", folders.get(0).getName());
        }

        @Test
        @GetAllMusicFoldersDecisions.Conditions.IncludeDisabled.True
        @GetAllMusicFoldersDecisions.Conditions.IncludeNonExisting.False
        // Files.exists will be called 4 times. However this pattern is not used in the
        // implementation.
        void c02() {
            List<MusicFolder> folders = musicFolderService.getAllMusicFolders(true, false);
            assertEquals(2, folders.size());
            assertEquals("Disabled&Existing", folders.get(0).getName());
            assertEquals("Enabled&Existing", folders.get(1).getName());
        }

        @Test
        @GetAllMusicFoldersDecisions.Conditions.IncludeDisabled.False
        @GetAllMusicFoldersDecisions.Conditions.IncludeNonExisting.True
        // Files.exists is never called
        void c03() {
            List<MusicFolder> folders = musicFolderService.getAllMusicFolders(false, true);
            assertEquals(2, folders.size());
            assertEquals("Enabled&NonExisting", folders.get(0).getName());
            assertEquals("Enabled&Existing", folders.get(1).getName());
        }

        @Test
        @GetAllMusicFoldersDecisions.Conditions.IncludeDisabled.True
        @GetAllMusicFoldersDecisions.Conditions.IncludeNonExisting.True
        // Files.exists is never called
        void c04() {
            List<MusicFolder> folders = musicFolderService.getAllMusicFolders(true, true);
            assertEquals(4, folders.size());
            assertEquals("Disabled&NonExisting", folders.get(0).getName());
            assertEquals("Enabled&NonExisting", folders.get(1).getName());
            assertEquals("Disabled&Existing", folders.get(2).getName());
            assertEquals("Enabled&Existing", folders.get(3).getName());
        }

        @Test
        // Files.exists will be called 4 times
        void testCached() {
            List<MusicFolder> result = musicFolderDao.getAllMusicFolders();
            Mockito.doReturn(result).when(musicFolderDao).getAllMusicFolders();
            Mockito.clearInvocations(musicFolderDao);
            List<MusicFolder> folders = musicFolderService.getAllMusicFolders(false, false);
            assertEquals(1, folders.size());
            folders = musicFolderService.getAllMusicFolders(false, false);
            assertEquals(1, folders.size());
            Mockito.verify(musicFolderDao, Mockito.times(1)).getAllMusicFolders();
        }
    }

    @Nested
    @GetAllMusicFoldersDecisions.Conditions.IncludeDisabled.False
    @GetAllMusicFoldersDecisions.Conditions.IncludeNonExisting.True
    // Same as GetAllMusicFoldersDisabledExistingTest#c03 if redundantFolderCheck =
    // false, otherwise same as c01
    class GetMusicFoldersForUserUsernameTest {

        @BeforeEach
        void setup() {
            List<MusicFolder> enabledExisting = musicFolderDao
                .getAllMusicFolders()
                .stream()
                .filter(m -> m.getId() == 4)
                .collect(Collectors.toList());
            Mockito
                .when(musicFolderDao.getMusicFoldersForUser(USER_NAME))
                .thenReturn(enabledExisting);
        }

        @Test
        // Files.exists will be called 2 times (Never called if redundantFolderCheck =
        // false)
        void testEnabledExisting() {
            List<MusicFolder> folders = musicFolderService.getMusicFoldersForUser(USER_NAME);
            assertEquals(1, folders.size());
            assertEquals("Enabled&Existing", folders.get(0).getName());
        }

        @Test
        // Files.exists will be called 2 times (Never called if redundantFolderCheck =
        // false)
        void testCached() {
            List<MusicFolder> folders = musicFolderService.getMusicFoldersForUser(USER_NAME);
            assertEquals(1, folders.size());
            assertEquals("Enabled&Existing", folders.get(0).getName());
            folders = musicFolderService.getMusicFoldersForUser(USER_NAME);
            assertEquals(1, folders.size());
            assertEquals("Enabled&Existing", folders.get(0).getName());
            Mockito.verify(musicFolderDao, Mockito.times(1)).getMusicFoldersForUser(USER_NAME);
        }
    }

    @Nested
    @GetAllMusicFoldersDecisions.Conditions.IncludeDisabled.False
    @GetAllMusicFoldersDecisions.Conditions.IncludeNonExisting.True
    // Same as GetAllMusicFoldersDisabledExistingTest#c03 if redundantFolderCheck =
    // false, otherwise same as c01
    class GetMusicFoldersForUserUsernameFolderIdTest {

        @BeforeEach
        void setup() {
            List<MusicFolder> enabledExisting = musicFolderDao
                .getAllMusicFolders()
                .stream()
                .filter(m -> m.getId() == 4)
                .collect(Collectors.toList());
            Mockito
                .when(musicFolderDao.getMusicFoldersForUser(USER_NAME))
                .thenReturn(enabledExisting);
        }

        @Test
        // Files.exists will be called 2 times (Never called if redundantFolderCheck =
        // false)
        void testAllowed() {
            List<MusicFolder> folders = musicFolderService.getMusicFoldersForUser(USER_NAME, null);
            assertEquals(1, folders.size());
            assertEquals("Enabled&Existing", folders.get(0).getName());
        }

        @Test
        // Files.exists will be called 4 times
        void testDisabledNonExisting() {
            List<MusicFolder> folders = musicFolderService.getMusicFoldersForUser(USER_NAME, 1);
            assertEquals(0, folders.size());
        }

        @Test
        // Files.exists will be called 4 times (Never called if redundantFolderCheck =
        // false)
        void testEnabledNonExisting() {
            List<MusicFolder> folders = musicFolderService.getMusicFoldersForUser(USER_NAME, 2);
            assertEquals(0, folders.size());
        }

        @Test
        // Files.exists will be called 4 times (Never called if redundantFolderCheck =
        // false)
        void testDisabledExisting() {
            List<MusicFolder> folders = musicFolderService.getMusicFoldersForUser(USER_NAME, 3);
            assertEquals(0, folders.size());
        }

        @Test
        // Files.exists will be called 4 times (Never called if redundantFolderCheck =
        // false)
        void testEnabledExisting() {
            List<MusicFolder> folders = musicFolderService.getMusicFoldersForUser(USER_NAME, 4);
            assertEquals(1, folders.size());
            assertEquals("Enabled&Existing", folders.get(0).getName());
        }
    }

    @Test
    void testSetMusicFoldersForUser() {
        int folderId = 0;
        musicFolderService.setMusicFoldersForUser(USER_NAME, Arrays.asList(folderId));
        Mockito
            .verify(musicFolderDao, Mockito.times(1))
            .setMusicFoldersForUser(USER_NAME, Arrays.asList(folderId));
    }

    @Nested
    @GetAllMusicFoldersDecisions.Conditions.IncludeDisabled.False
    @GetAllMusicFoldersDecisions.Conditions.IncludeNonExisting.False
    // Same as GetAllMusicFoldersDisabledExistingTest#c03 if redundantFolderCheck =
    // false, otherwise same as c01
    class GetMusicFolderByIdTest {

        @Test
        // Files.exists will be called 2 times (Never called if redundantFolderCheck =
        // false)
        void testDisabledNonExisting() {
            assertNull(musicFolderService.getMusicFolderById(1));
        }

        @Test
        // Files.exists will be called 2 times (Never called if redundantFolderCheck =
        // false)
        void testEnabledNonExisting() {
            MusicFolder folder = musicFolderService.getMusicFolderById(2);
            assertEquals("Enabled&NonExisting", folder.getName());
        }

        @Test
        // Files.exists will be called 2 times (Never called if redundantFolderCheck =
        // false)
        void testDisabledExisting() {
            assertNull(musicFolderService.getMusicFolderById(3));
        }

        @Test
        // Files.exists will be called 2 times (Never called if redundantFolderCheck =
        // false)
        void testEnabledExisting() {
            MusicFolder folder = musicFolderService.getMusicFolderById(4);
            assertEquals("Enabled&Existing", folder.getName());
        }
    }

    @Test
    void testCreateMusicFolder() {
        musicFolderService.createMusicFolder(PlayerUtils.now(), null);
        Mockito
            .verify(musicFolderDao, Mockito.times(1))
            .createMusicFolder(Mockito.nullable(MusicFolder.class));
    }

    @Test
    void testDeleteMusicFolder() {
        musicFolderService.deleteMusicFolder(PlayerUtils.now(), -1);
        Mockito
            .verify(musicFolderDao, Mockito.times(1))
            .deleteMusicFolder(Mockito.nullable(Integer.class));
    }

    @Test
    void testUpdateMusicFolder() {
        musicFolderService.updateMusicFolder(PlayerUtils.now(), null);
        Mockito
            .verify(musicFolderDao, Mockito.times(1))
            .updateMusicFolder(Mockito.nullable(MusicFolder.class));
    }

    @Test
    void testclearMusicFolderCache() {
        List<MusicFolder> result = musicFolderDao.getAllMusicFolders();
        Mockito.doReturn(result).when(musicFolderDao).getAllMusicFolders();
        Mockito.clearInvocations(musicFolderDao);
        List<MusicFolder> folders = musicFolderService.getAllMusicFolders(false, false);
        assertEquals(1, folders.size());

        musicFolderService.clearMusicFolderCache();

        folders = musicFolderService.getAllMusicFolders(false, false);
        assertEquals(1, folders.size());

        Mockito.verify(musicFolderDao, Mockito.times(2)).getAllMusicFolders();
    }
}

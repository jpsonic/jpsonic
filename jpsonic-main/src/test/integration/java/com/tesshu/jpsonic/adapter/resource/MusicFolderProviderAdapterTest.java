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

package com.tesshu.jpsonic.adapter.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.MusicFolderTestDataUtils;
import com.tesshu.jpsonic.domain.model.MediaFile;
import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.infrastructure.scanner.MusicFolderServiceImpl;
import com.tesshu.jpsonic.persistence.NeedsDB;
import com.tesshu.jpsonic.persistence.api.repository.MusicFolderDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@NeedsDB
public class MusicFolderProviderAdapterTest extends AbstractNeedsScan {

    @Autowired
    private MusicFolderProvider musicFolderProvider;
    @Autowired
    private MusicFolderServiceImpl musicFolderService;
    @Autowired
    private MediaFileProvider mediaFileProvider;
    @Autowired
    private MusicFolderDao musicFolderDao;

    public List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> getMusicFolders() {
        return MusicFolderTestDataUtils.getTestMusicFolders();
    }    

    @BeforeEach
    void setup() {
        
        if(!isDataBasePopulated()) {
            musicFolderDao
            .getAllMusicFolders()
            .stream()
            .map(com.tesshu.jpsonic.persistence.api.entity.MusicFolder::getId)
            .forEach(musicFolderDao::deleteMusicFolder);
        }
        
        populateDatabaseOnlyOnce();
    }

    @Test
    public void testGetGuestFolders() {
        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> folderEntities = musicFolderDao
            .getAllMusicFolders();
        assertEquals(2, folderEntities.size());

        folderEntities = musicFolderService
            .getMusicFoldersForUser(MusicFolderServiceImpl.USERNAME_GUEST);
        assertEquals(2, folderEntities.size());
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        assertEquals(2, folders.size());

        // cashed
        folders = musicFolderProvider.getGuestFolders();
        assertEquals(2, folders.size());

        com.tesshu.jpsonic.persistence.api.entity.MusicFolder folder = folderEntities
            .stream()
            .filter(f -> "Music".equals(f.getName()))
            .findFirst()
            .get();
        com.tesshu.jpsonic.persistence.api.entity.MusicFolder folder2 = folderEntities
            .stream()
            .filter(f -> "Music2".equals(f.getName()))
            .findFirst()
            .get();

        musicFolderService
            .setMusicFoldersForUser(MusicFolderServiceImpl.USERNAME_GUEST, List.of(folder.getId()));

        folderEntities = musicFolderService
            .getMusicFoldersForUser(MusicFolderServiceImpl.USERNAME_GUEST);
        assertEquals(1, folderEntities.size());
        folders = musicFolderProvider.getGuestFolders();
        assertEquals(1, folders.size());

        musicFolderService
            .setMusicFoldersForUser(MusicFolderServiceImpl.USERNAME_GUEST,
                    List.of(folder.getId(), folder2.getId()));

        folderEntities = musicFolderService
            .getMusicFoldersForUser(MusicFolderServiceImpl.USERNAME_GUEST);
        assertEquals(2, folderEntities.size());
        folders = musicFolderProvider.getGuestFolders();
        assertEquals(2, folders.size());

        folder2.setEnabled(false);
        musicFolderService.updateMusicFolder(Instant.now(), folder2);

        folderEntities = musicFolderService
            .getMusicFoldersForUser(MusicFolderServiceImpl.USERNAME_GUEST);
        assertEquals(1, folderEntities.size());
        folders = musicFolderProvider.getGuestFolders();
        assertEquals(1, folders.size());

        folder2.setEnabled(true);
        musicFolderService.updateMusicFolder(Instant.now(), folder2);

        folderEntities = musicFolderService
            .getMusicFoldersForUser(MusicFolderServiceImpl.USERNAME_GUEST);
        assertEquals(2, folderEntities.size());
        folders = musicFolderProvider.getGuestFolders();
        assertEquals(2, folders.size());
    }
    

    @Test
    public void testRequireMusicFolder() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        assertEquals(2, folders.size());
        MusicFolder folder = folders
            .stream()
            .filter(f -> "Music".equals(f.name()))
            .findFirst()
            .get();
        assertEquals(folder, musicFolderProvider.requireMusicFolder(folder.id()));

        MediaFile mediaFile = mediaFileProvider.requireMediaFile(folder);
        assertEquals(MediaFile.Type.DIRECTORY, mediaFile.type());
        assertEquals("Music", mediaFile.name());
        assertTrue(mediaFile.parentId().isEmpty());
    }
}

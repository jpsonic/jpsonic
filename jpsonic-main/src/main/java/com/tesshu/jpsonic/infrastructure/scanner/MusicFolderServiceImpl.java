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
 * (C) 2021 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.scanner;

import java.nio.file.Files;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import com.tesshu.jpsonic.domain.event.MusicFolderChangedEvent;
import com.tesshu.jpsonic.infrastructure.concurrent.ReadWriteLockSupport;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.MusicFolderDao;
import com.tesshu.jpsonic.persistence.core.entity.ScanEvent.ScanEventType;
import com.tesshu.jpsonic.persistence.core.repository.StaticsDao;
import com.tesshu.jpsonic.service.MusicFolderService;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for CRUD operations and cache management of music
 * folders.
 * <p>
 * Provides access to folder listings, per-user folder assignments, and
 * in-memory caching. All cache access is managed in a thread-safe manner using
 * {@link java.util.concurrent.locks.ReentrantReadWriteLock}.
 *
 * <h3>Main Responsibilities</h3>
 * <ul>
 * <li>{@link #getAllMusicFolders(boolean, boolean)} Retrieves all registered
 * music folders. Invalid or nonexistent paths can be optionally excluded.</li>
 * <li>{@link #getMusicFoldersForUser(String)} Retrieves folders assigned to a
 * specific user.</li>
 * <li>{@link #createMusicFolder(Instant, MusicFolder)} /
 * {@link #deleteMusicFolder(Instant, int)} Creates or deletes a folder.
 * Operations are protected by scan-state locks and are logged as scan events.
 * </li>
 * <li>{@link #setMusicFoldersForUser(String, List)} Updates the folder
 * assignments for a given user.</li>
 * </ul>
 *
 * <h3>Notes</h3>
 * <ul>
 * <li>To prevent conflicts during scanning, folder creation and deletion are
 * guarded by scan-state locking.</li>
 * <li>When a folder is created, the internal {@code folderOrder} is
 * reconstructed to maintain order consistency.</li>
 * <li>Folder operations are logged as events.</li>
 * </ul>
 *
 * @see MusicFolderDao
 * @see ScannerStateServiceImpl
 */
@Service("musicFolderService")
@DependsOn("scannerStateService")
public class MusicFolderServiceImpl implements MusicFolderService, ReadWriteLockSupport {

    public static final String USERNAME_GUEST = "guest";

    private final ConcurrentMap<String, List<MusicFolder>> cachedUserFolders;
    private List<MusicFolder> cachedMusicFolders;
    private List<com.tesshu.jpsonic.domain.model.MusicFolder> cachedDomainGuestFolders;

    private final MusicFolderDao musicFolderDao;
    private final StaticsDao staticsDao;
    private final ScannerStateServiceImpl scannerStateService;
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private final ApplicationEventPublisher eventPublisher;

    public MusicFolderServiceImpl(MusicFolderDao musicFolderDao, StaticsDao staticsDao,
            ScannerStateServiceImpl scannerStateService, ApplicationEventPublisher eventPublisher) {
        this.musicFolderDao = musicFolderDao;
        this.staticsDao = staticsDao;
        this.scannerStateService = scannerStateService;
        this.eventPublisher = eventPublisher;
        cachedUserFolders = new ConcurrentHashMap<>();
    }

    @Override
    public List<MusicFolder> getAllMusicFolders() {
        return getAllMusicFolders(false, true);
    }

    @Override
    public List<MusicFolder> getAllMusicFolders(boolean includeDisabled,
            boolean includeNonExisting) {
        readLock(cacheLock);
        try {
            if (cachedMusicFolders == null) {
                cachedMusicFolders = musicFolderDao.getAllMusicFolders();
            }
            return cachedMusicFolders
                .stream()
                .filter(folder -> includeDisabled || folder.isEnabled())
                .filter(folder -> includeNonExisting || Files.exists(folder.toPath()))
                .collect(Collectors.toList());
        } finally {
            readUnlock(cacheLock);
        }
    }

    public List<com.tesshu.jpsonic.domain.model.MusicFolder> getDomainGuestFolders() {
        readLock(cacheLock);
        try {
            if (cachedDomainGuestFolders == null) {
                cachedDomainGuestFolders = musicFolderDao
                    .getDomainMusicFoldersForUser(USERNAME_GUEST)
                    .stream()
                    .filter(com.tesshu.jpsonic.domain.model.MusicFolder::enabled)
                    .toList();
            }
            return cachedDomainGuestFolders;
        } finally {
            readUnlock(cacheLock);
        }
    }

    @Override
    public List<MusicFolder> getMusicFoldersForUser(@NonNull String username) {
        readLock(cacheLock);
        try {
            List<MusicFolder> result = cachedUserFolders.get(username);
            if (result == null) {
                writeLock(cacheLock);
                try {
                    result = musicFolderDao.getMusicFoldersForUser(username);
                    result.retainAll(getAllMusicFolders());
                    cachedUserFolders.put(username, result);
                } finally {
                    writeUnlock(cacheLock);
                }
            }
            return result;
        } finally {
            readUnlock(cacheLock);
        }
    }

    @Override
    public List<MusicFolder> getMusicFoldersForUser(@NonNull String username,
            @Nullable Integer selectedMusicFolderId) {
        List<MusicFolder> allowed = getMusicFoldersForUser(username);
        if (selectedMusicFolderId == null) {
            return allowed;
        }
        MusicFolder selected = getMusicFolderById(selectedMusicFolderId);
        return allowed.contains(selected) ? Collections.singletonList(selected)
                : Collections.emptyList();
    }

    @Override
    public void setMusicFoldersForUser(@NonNull String username, List<Integer> musicFolderIds) {
        musicFolderDao.setMusicFoldersForUser(username, musicFolderIds);
        writeLock(cacheLock);
        try {
            cachedUserFolders.remove(username);
            if (USERNAME_GUEST.equals(username)) {
                clearMusicFolderCache();
            }
        } finally {
            writeUnlock(cacheLock);
        }
    }

    @Override
    public @Nullable MusicFolder getMusicFolderById(int id) {
        return getAllMusicFolders()
            .stream()
            .filter(folder -> folder.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    public void createMusicFolder(@NonNull Instant executed, @NonNull MusicFolder musicFolder) {
        if (scannerStateService.tryScanningLock()) {
            musicFolderDao.createMusicFolder(musicFolder);
            List<MusicFolder> folders = getAllMusicFolders(false, false);
            for (int i = 0; i < folders.size(); i++) {
                MusicFolder folder = folders.get(i);
                folder.setFolderOrder(i);
                musicFolderDao.updateMusicFolder(folder);
            }
            staticsDao.createFolderLog(executed, ScanEventType.FOLDER_CREATE);
            clearMusicFolderCache();
            scannerStateService.unlockScanning();
        }
    }

    public void deleteMusicFolder(@NonNull Instant executed, int id) {
        if (scannerStateService.tryScanningLock()) {
            musicFolderDao.deleteMusicFolder(id);
            staticsDao.createFolderLog(executed, ScanEventType.FOLDER_DELETE);
            clearMusicFolderCache();
            scannerStateService.unlockScanning();
        }
    }

    public void updateMusicFolder(@NonNull Instant executed, @NonNull MusicFolder musicFolder) {
        if (scannerStateService.tryScanningLock()) {
            musicFolderDao.updateMusicFolder(musicFolder);
            staticsDao.createFolderLog(executed, ScanEventType.FOLDER_UPDATE);
            clearMusicFolderCache();
            scannerStateService.unlockScanning();
        }
    }

    public void updateMusicFolderOrder(@NonNull Instant executed, int folderId) {
        if (scannerStateService.tryScanningLock()) {
            List<MusicFolder> folders = getAllMusicFolders(false, false);
            int position = -1;
            for (int i = 0; i < folders.size(); i++) {
                if (folderId == folders.get(i).getId()) {
                    position = i;
                    break;
                }
            }
            if (0 < position) {
                Collections.swap(folders, position - 1, position);
                for (int i = 0; i < folders.size(); i++) {
                    MusicFolder folder = folders.get(i);
                    folder.setFolderOrder(i);
                    musicFolderDao.updateMusicFolder(folder);
                }
                staticsDao.createFolderLog(executed, ScanEventType.FOLDER_UPDATE);
                clearMusicFolderCache();
            }
            scannerStateService.unlockScanning();
        }
    }

    @SuppressWarnings("PMD.NullAssignment")
    public void clearMusicFolderCache() {
        writeLock(cacheLock);
        try {
            cachedUserFolders.clear();
            cachedMusicFolders = null;
            cachedDomainGuestFolders = null;
        } finally {
            eventPublisher.publishEvent(new MusicFolderChangedEvent());
            writeUnlock(cacheLock);
        }
    }
}

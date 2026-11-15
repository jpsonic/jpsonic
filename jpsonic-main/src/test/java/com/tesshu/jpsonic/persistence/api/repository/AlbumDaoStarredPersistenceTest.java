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

package com.tesshu.jpsonic.persistence.api.repository;

import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.TestCaseUtils;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.Album;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/*
 * Make sure that scanning ignoring timestamps will not delete starred albums.
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class AlbumDaoStarredPersistenceTest extends AbstractNeedsScan {

    static final String ADMIN_NAME = "admin";

    @Autowired
    private AlbumDao albumDao;
    @Autowired
    private SettingsFacade settingsFacade;

    private final List<MusicFolder> folders = List
        .of(new MusicFolder(1, resolveBaseMediaPath("Music"), "Music", true, now(), 0, false));

    @Override
    public List<MusicFolder> getMusicFolders() {
        return folders;
    }

    @BeforeEach
    void setup() throws IOException {
        populateDatabase();
    }

    @Test
    void testStarredPersistence() throws IOException, InterruptedException {

        // Checking registered data
        List<Album> albums = albumDao
            .getAlphabeticalAlbums(0, Integer.MAX_VALUE, false, true, folders);
        assertEquals(4, albums.size());
        assertEquals("_ID3_ALBUM_ Bach: Goldberg Variations, Canons [Disc 1]",
                albums.get(0).getName());
        assertEquals("_ID3_ALBUM_ Ravel - Chamber Music With Voice", albums.get(1).getName());
        assertEquals("_ID3_ALBUM_ Sackcloth 'n' Ashes", albums.get(2).getName());
        assertEquals("Complete Piano Works", albums.get(3).getName());

        // Give a Star
        albumDao.starAlbum(albums.get(0).getId(), ADMIN_NAME);
        Thread.sleep(200);
        albumDao.starAlbum(albums.get(2).getId(), ADMIN_NAME);
        List<Album> starred = albumDao.getStarredAlbums(0, Integer.MAX_VALUE, ADMIN_NAME, folders);
        assertEquals(2, starred.size());

        // Note that the order is 'created desc'
        assertEquals("_ID3_ALBUM_ Sackcloth 'n' Ashes", starred.get(0).getName());
        assertEquals("_ID3_ALBUM_ Bach: Goldberg Variations, Canons [Disc 1]",
                starred.get(1).getName());

        // Run a scan
        TestCaseUtils.execScan(mediaScannerService);

        // Of course the result remains the same
        starred = albumDao.getStarredAlbums(0, Integer.MAX_VALUE, ADMIN_NAME, folders);
        assertEquals(2, starred.size());
        assertEquals("_ID3_ALBUM_ Sackcloth 'n' Ashes", starred.get(0).getName());
        assertEquals("_ID3_ALBUM_ Bach: Goldberg Variations, Canons [Disc 1]",
                starred.get(1).getName());

        // Scan with IgnoreFileTimestamps enabled
        settingsFacade.commit(SKeys.musicFolder.scan.ignoreFileTimestamps, true);
        TestCaseUtils.execScan(mediaScannerService);

        // The result remains the same
        starred = albumDao.getStarredAlbums(0, Integer.MAX_VALUE, ADMIN_NAME, folders);
        assertEquals(2, starred.size());
        assertEquals("_ID3_ALBUM_ Sackcloth 'n' Ashes", starred.get(0).getName());
        assertEquals("_ID3_ALBUM_ Bach: Goldberg Variations, Canons [Disc 1]",
                starred.get(1).getName());
    }
}

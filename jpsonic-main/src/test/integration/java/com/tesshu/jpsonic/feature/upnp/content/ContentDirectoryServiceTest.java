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

package com.tesshu.jpsonic.feature.upnp.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.feature.upnp.UPnPSKeys;
import com.tesshu.jpsonic.infrastructure.search.SearchSKeys;
import com.tesshu.jpsonic.infrastructure.search.UPnPSearchMethod;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jupnp.support.contentdirectory.ContentDirectoryException;
import org.jupnp.support.model.BrowseResult;
import org.springframework.beans.factory.annotation.Autowired;

class ContentDirectoryServiceTest extends AbstractNeedsScan {

    @Autowired
    private ContentDirectoryService contentDirectoryService;
    @Autowired
    private SettingsFacade settings;
    private List<MusicFolder> musicFolders;

    @Override
    public List<MusicFolder> getMusicFolders() {
        return musicFolders;
    }

    @BeforeEach
    void setup() throws URISyntaxException, InterruptedException {
        String uri = Path
            .of(ContentDirectoryServiceTest.class
                .getResource("/MEDIAS/Sort/Pagination/Artists")
                .toURI())
            .toString();
        musicFolders = Arrays.asList(new MusicFolder(1, uri, "Artists", true, Instant.now(), 1, false));
        settings.commit(UPnPSKeys.basic.baseLanUrl, "https://192.168.1.1:4040");
        populateDatabaseOnlyOnce();
    }

    @Test
    void testSearch() throws ContentDirectoryException {

        // File Structure
        String query = """
                (upnp:class = "object.container.person.musicArtist" \
                and dc:title contains "はるなつあきふゆ")\
                """;
        BrowseResult result = contentDirectoryService
            .search(null, query, null, 0, Integer.MAX_VALUE, null);
        assertEquals(1, result.getCount().getValue());
        assertEquals(1, result.getTotalMatches().getValue());

        query = """
                (upnp:class = "object.container.album.musicAlbum") \
                and (dc:title contains "ALBUM" \
                        or dc:creator contains "ALBUM" \
                        or upnp:artist contains "ALBUM" )\
                """;
        result = contentDirectoryService.search(null, query, null, 0, 10, null);
        assertEquals(10, result.getCount().getValue());
        assertEquals(30, result.getTotalMatches().getValue());

        // Song
        query = """
                (upnp:class derivedfrom "object.item.audioItem" \
                        or upnp:class derivedfrom "object.item.videoItem ") \
                and (dc:title contains "empty" \
                        or dc:creator contains "empty" \
                        or upnp:artist contains "empty" \
                        or upnp:albumArtist contains "empty" \
                        or upnp:album contains "empty" \
                        or upnp:author contains "empty" \
                        or upnp:genre contains "empty" )\
                """;
        result = contentDirectoryService.search(null, query, null, 0, 30, null);
        assertEquals(30, result.getCount().getValue());
        assertEquals(61, result.getTotalMatches().getValue());

        // ID3
        // Like Subsonic and Airsonic, the correction is based on the Artist/Album/Song
        // three-level
        // management. Therefore, if a tag is missing, the same result as the File
        // Structure will be
        // returned, preventing missing results.
        settings.commit(SearchSKeys.search.upnpSearchMethod, UPnPSearchMethod.ID3.name());
        query = """
                (upnp:class = "object.container.person.musicArtist" \
                and dc:title contains "はるなつあきふゆ")\
                """;
        result = contentDirectoryService.search(null, query, null, 0, Integer.MAX_VALUE, null);
        assertEquals(1, result.getCount().getValue());
        assertEquals(1, result.getTotalMatches().getValue());

        query = """
                (upnp:class = "object.container.album.musicAlbum") \
                and (dc:title contains "ALBUM" \
                        or dc:creator contains "ALBUM" \
                        or upnp:artist contains "ALBUM" )\
                """;
        result = contentDirectoryService.search(null, query, null, 0, 10, null);
        assertEquals(10, result.getCount().getValue());
        assertEquals(30, result.getTotalMatches().getValue());
    }

}

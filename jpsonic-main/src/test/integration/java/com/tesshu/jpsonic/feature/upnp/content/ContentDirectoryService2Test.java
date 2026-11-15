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
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jupnp.support.contentdirectory.ContentDirectoryException;
import org.jupnp.support.model.BrowseResult;
import org.springframework.beans.factory.annotation.Autowired;

class ContentDirectoryService2Test extends AbstractNeedsScan {

    @Autowired
    private ContentDirectoryService contentDirectoryService;
    private List<MusicFolder> musicFolders;

    @Override
    public List<MusicFolder> getMusicFolders() {
        return musicFolders;
    }

    @BeforeEach
    void setup() throws URISyntaxException, InterruptedException {
        musicFolders = Arrays
            .asList(new MusicFolder(1,
                    Path
                        .of(ContentDirectoryService2Test.class.getResource("/MEDIAS/Music").toURI())
                        .toString(),
                    "Music", true, Instant.now(), 1, false));
        settingsFacade.commit(UPnPSKeys.basic.baseLanUrl, "https://192.168.1.1:4040");
        populateDatabaseOnlyOnce();
    }

    @Test
    void testSearch() throws ContentDirectoryException {

        // File Structure

        // Can't search by tag (That's the specification.)
        String query = """
                (upnp:class = "object.container.person.musicArtist" \
                and dc:title contains "_ID3_ARTIST_ Céline Frisch: Café Zimmermann")\
                """;
        BrowseResult result = contentDirectoryService
            .search(null, query, null, 0, Integer.MAX_VALUE, null);
        assertEquals(0, result.getCount().getValue());
        assertEquals(0, result.getTotalMatches().getValue());

        // As with Subsonic/Airsonic, if there is a tag, it will be corrected.
        // Therefore, it is
        // possible to search by tag. (That's the specification.)
        query = """
                (upnp:class = "object.container.album.musicAlbum") \
                and (dc:title contains "_ID3_ALBUM_ Sackcloth 'n' Ashes" \
                        or dc:creator contains "_ID3_ALBUM_ Sackcloth 'n' Ashes" \
                        or upnp:artist contains "_ID3_ALBUM_ Sackcloth 'n' Ashes" )\
                """;
        result = contentDirectoryService.search(null, query, null, 0, Integer.MAX_VALUE, null);
        assertEquals(1, result.getCount().getValue());
        assertEquals(1, result.getTotalMatches().getValue());

        // Song
        query = """
                (upnp:class derivedfrom "object.item.audioItem" \
                        or upnp:class derivedfrom "object.item.videoItem ") \
                and (dc:title contains "Sonata Violin & Cello II. Tres Vif" \
                        or dc:creator contains "Sonata Violin & Cello II. Tres Vif" \
                        or upnp:artist contains "Sonata Violin & Cello II. Tres Vif" \
                        or upnp:albumArtist contains "Sonata Violin & Cello II. Tres Vif" \
                        or upnp:album contains "Sonata Violin & Cello II. Tres Vif" \
                        or upnp:author contains "Sonata Violin & Cello II. Tres Vif" \
                        or upnp:genre contains "Sonata Violin & Cello II. Tres Vif" )\
                """;
        result = contentDirectoryService.search(null, query, null, 0, Integer.MAX_VALUE, null);
        assertEquals(1, result.getCount().getValue());
        assertEquals(1, result.getTotalMatches().getValue());

        // ID3
        // Unlike Subsonic and Airsonic, Jpsonic allows searching by tag even if your
        // music lib
        // does not have a three-layer structure.

        settingsFacade.commit(SearchSKeys.search.upnpSearchMethod, UPnPSearchMethod.ID3.name());

        query = """
                (upnp:class = "object.container.person.musicArtist" \
                and dc:title contains "_ID3_ARTIST_ Céline Frisch: Café Zimmermann")\
                """;
        result = contentDirectoryService.search(null, query, null, 0, Integer.MAX_VALUE, null);
        assertEquals(1, result.getCount().getValue());
        assertEquals(1, result.getTotalMatches().getValue());

        query = """
                (upnp:class = "object.container.album.musicAlbum") \
                and (dc:title contains "_ID3_ALBUM_ Sackcloth 'n' Ashes" \
                        or dc:creator contains "_ID3_ALBUM_ Sackcloth 'n' Ashes" \
                        or upnp:artist contains "_ID3_ALBUM_ Sackcloth 'n' Ashes" )\
                """;
        result = contentDirectoryService.search(null, query, null, 0, Integer.MAX_VALUE, null);
        assertEquals(1, result.getCount().getValue());
        assertEquals(1, result.getTotalMatches().getValue());
    }
}

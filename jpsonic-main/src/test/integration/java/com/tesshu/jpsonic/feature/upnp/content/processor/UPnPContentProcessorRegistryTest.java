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

package com.tesshu.jpsonic.feature.upnp.content.processor;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.stream.Stream;

import com.tesshu.jpsonic.feature.upnp.content.ProcId;
import com.tesshu.jpsonic.feature.upnp.content.SearchFilterProcessor;
import com.tesshu.jpsonic.feature.upnp.content.SearchResultProcessor;
import com.tesshu.jpsonic.feature.upnp.content.UPnPContentProcessorResolver;
import com.tesshu.jpsonic.infrastructure.core.NeedsHome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@NeedsHome
class UPnPContentProcessorRegistryTest {

    @Autowired
    private UPnPContentProcessorResolver uPnPContentProcessorResolver;

    @Test
    @SuppressWarnings("PMD.UnitTestShouldIncludeAssert")
    void testFindProcessor() {
        Stream
            .of(ProcId.values())
            .forEach(id -> assertNotNull(uPnPContentProcessorResolver.findProcessor(id)));
    }

    @Test
    void testFindSearchResultProcessor() {
        assertInstanceOf(SearchResultProcessor.class,
                uPnPContentProcessorResolver.findSearchResultProcessor(ProcId.MEDIA_FILE));
        assertInstanceOf(SearchResultProcessor.class,
                uPnPContentProcessorResolver.findSearchResultProcessor(ProcId.ALBUM_ID3));
        assertInstanceOf(SearchResultProcessor.class,
                uPnPContentProcessorResolver.findSearchResultProcessor(ProcId.ARTIST));
    }

    @Test
    void testFindSearchFilterProcessor() {
        assertInstanceOf(SearchFilterProcessor.class,
                uPnPContentProcessorResolver.findSearchFilterProcessor());
    }
}

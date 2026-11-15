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
 * (C) 2022 tesshucom
 */

package com.tesshu.jpsonic.ajax;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.tesshu.jpsonic.infrastructure.scanner.ScannerStateServiceImpl;
import com.tesshu.jpsonic.infrastructure.scanner.WritableMediaFileService;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.metadata.MetaDataParserFactory;
import com.tesshu.jpsonic.service.metadata.MusicParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class TagServiceTest {

    private MetaDataParserFactory metaDataParserFactory;
    private MediaFileService mediaFileService;
    private WritableMediaFileService writableMediaFileService;
    private ScannerStateServiceImpl scannerStateService;
    private TagService tagService;
    private MusicParser parser;

    @BeforeEach
    void setup() {
        metaDataParserFactory = mock(MetaDataParserFactory.class);
        mediaFileService = mock(MediaFileService.class);
        writableMediaFileService = mock(WritableMediaFileService.class);
        scannerStateService = mock(ScannerStateServiceImpl.class);
        tagService = new TagService(metaDataParserFactory, mediaFileService,
                writableMediaFileService, scannerStateService);
        parser = new MusicParser(mock(MusicFolderService.class));
    }

    @Test
    void testEquals() {
        assertTrue(tagService.equals(null, null));
        assertFalse(tagService.equals(null, "a"));
        assertFalse(tagService.equals("a", null));
        assertTrue(tagService.equals("a", "a"));
        assertFalse(tagService.equals("a", "A"));
        assertFalse(tagService.equals("a", "b"));
        assertFalse(tagService.equals("abc", "c"));
        assertFalse(tagService.equals("abc", "d"));
        assertFalse(tagService.equals("i", "İ"));

        String s = "a";
        assertTrue(tagService.equals(s, s));
        assertTrue(tagService.equals("", ""));
        assertFalse(tagService.equals("", "a"));
        assertFalse(tagService.equals("a", ""));
        assertTrue(tagService.equals("こんにちは", "こんにちは"));
        assertFalse(tagService.equals("こんにちは", "こんばんは"));
        assertFalse(tagService.equals("abcd", "abce"));
        assertFalse(tagService.equals("a\nb", "a b"));
    }

    @Test
    void testUpdateTags(@TempDir Path tempDir) throws URISyntaxException, IOException {
        Path org = Path
            .of(TagServiceTest.class
                .getResource("/MEDIAS/Metadata/v2.3+v1.1/MusicCenter2.1.0JP.mp3")
                .toURI());
        Path copy = Path.of(tempDir.toString(), org.getFileName().toString());

        try (OutputStream out = Files.newOutputStream(copy);) {
            Files.copy(org, out);
        }

        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(0);
        mediaFile.setPathString(copy.toString());
        mediaFile.setParentPathString(copy.getParent().toString());
        MediaFile parent = new MediaFile();
        parent.setId(1);
        parent.setPathString(copy.getParent().toString());

        Mockito.when(mediaFileService.getMediaFileStrict(mediaFile.getId())).thenReturn(mediaFile);
        Mockito.when(mediaFileService.getParent(mediaFile)).thenReturn(Optional.of(parent));
        Mockito
            .when(metaDataParserFactory.getParser(Path.of(mediaFile.getPathString())))
            .thenReturn(parser);

        Mockito.when(scannerStateService.isScanning()).thenReturn(false);
        String result = tagService.updateTags(0, "1", "artist", "album", "title", "2022", "genre");
        assertEquals("UPDATED", result);
        Mockito.verify(writableMediaFileService, Mockito.times(1)).updateTags(mediaFile);
        Mockito.clearInvocations(mediaFileService, writableMediaFileService);

        Mockito.when(scannerStateService.isScanning()).thenReturn(true);
        result = tagService.updateTags(0, "2", "artist2", "album2", "title2", "2023", "genre2");
        assertEquals("SKIPPED", result);
        Mockito.verify(writableMediaFileService, Mockito.never()).updateTags(mediaFile);
    }
}

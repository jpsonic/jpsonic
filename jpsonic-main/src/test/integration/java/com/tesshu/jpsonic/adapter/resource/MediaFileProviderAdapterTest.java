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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.Album;
import com.tesshu.jpsonic.domain.model.IndexWithCount;
import com.tesshu.jpsonic.domain.model.MediaFile;
import com.tesshu.jpsonic.domain.model.MediaFile.BitRate;
import com.tesshu.jpsonic.domain.model.MediaFile.DurationSeconds;
import com.tesshu.jpsonic.domain.model.MediaFile.Format;
import com.tesshu.jpsonic.domain.model.MediaFile.Type;
import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.model.MusicIndex;
import com.tesshu.jpsonic.domain.policy.RuntimeOrderPolicy;
import com.tesshu.jpsonic.domain.provider.resource.AlbumProvider;
import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.persistence.NeedsDB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@NeedsDB
class MediaFileProviderAdapterTest extends AbstractNeedsScan {

    @Autowired
    private MusicFolderProvider musicFolderProvider;
    @Autowired
    private MediaFileProvider mediaFileProvider;
    @Autowired
    private AlbumProvider albumProvider;

    @BeforeEach
    void setup() {
        populateDatabaseOnlyOnce();
    }

    @Test
    public void testCountAlbums() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        assertEquals(5, mediaFileProvider.countAlbums(folders));
    }

    @Test
    public void testCountChildrenListOfMusicFolderTypeArray() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        assertEquals(4, mediaFileProvider.countChildren(folders));
        assertEquals(2, mediaFileProvider.countChildren(folders, MediaFile.Type.DIRECTORY));
        assertEquals(2, mediaFileProvider.countChildren(folders, MediaFile.Type.ALBUM));
    }

    @Test
    public void testCountChildrenMediaFileTypeArray() {
        MediaFile mediaFile = mediaFileProvider.requireMediaFile(2);
        assertEquals(2, mediaFile.id());
        assertEquals("_DIR_ Ravel", mediaFile.name());
        assertEquals(2, mediaFileProvider.countChildren(mediaFile));
        assertEquals(2, mediaFileProvider.countChildren(mediaFile, MediaFile.Type.DIRECTORY));
        assertEquals(0, mediaFileProvider.countChildren(mediaFile, MediaFile.Type.ALBUM));
    }

    @Test
    public void testCountSongs() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        assertEquals(11, mediaFileProvider.countSongs(folders));
    }

    @Test
    public void testCountVideos() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        assertEquals(0, mediaFileProvider.countVideos(folders));
    }

    @Test
    public void testExistsAccessibleMediaFile() {
        String userName = "guest";
        
        final MediaFile mediaFile1 = mediaFileProvider.requireMediaFile(2);
        final MediaFile mediaFile2 = mediaFileProvider.requireMediaFile(6);
        final MediaFile mediaFile3 = mediaFileProvider.requireMediaFile(4);

        assertEquals(2, mediaFile1.id());
        assertEquals("_DIR_ Ravel", mediaFile1.name());
        assertEquals(MediaFile.Type.DIRECTORY, mediaFile1.type());

        assertEquals(6, mediaFile2.id());
        assertEquals("_DIR_ Ravel - Chamber Music With Voice", mediaFile2.name());
        assertEquals(Type.ALBUM, mediaFile2.type());

        assertEquals(4, mediaFile3.id());
        assertEquals("Bach: Goldberg Variations, BWV 988 - Aria", mediaFile3.name());
        assertEquals(Type.MUSIC, mediaFile3.type());

        assertTrue(mediaFileProvider.existsAccessibleMediaFile(userName, mediaFile1));
        assertTrue(mediaFileProvider.existsAccessibleMediaFile(userName, mediaFile2));
        assertTrue(mediaFileProvider.existsAccessibleMediaFile(userName, mediaFile3));

        List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> folders = musicFolderService
            .getMusicFoldersForUser(userName, null);

        musicFolderService.setMusicFoldersForUser(userName, Collections.emptyList());
        assertFalse(mediaFileProvider.existsAccessibleMediaFile(userName, mediaFile1));
        assertFalse(mediaFileProvider.existsAccessibleMediaFile(userName, mediaFile2));
        assertFalse(mediaFileProvider.existsAccessibleMediaFile(userName, mediaFile3));

        musicFolderService
            .setMusicFoldersForUser(userName,
                    com.tesshu.jpsonic.persistence.api.entity.MusicFolder.toIdList(folders));
        assertTrue(mediaFileProvider.existsAccessibleMediaFile(userName, mediaFile1));
        assertTrue(mediaFileProvider.existsAccessibleMediaFile(userName, mediaFile2));
        assertTrue(mediaFileProvider.existsAccessibleMediaFile(userName, mediaFile3));
   }

    @Test
    public void testFindAlbums() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        int count = mediaFileProvider.countAlbums(folders);
        assertEquals(5, count);
        List<MediaFile> files = 
        mediaFileProvider.findAlbums(folders, RuntimeOrderPolicy.AlbumSortOrder.DEFAULT, 0, Integer.MAX_VALUE);
        assertEquals(count, files.size());
        assertEquals(
                "_DIR_ Céline Frisch- Café Zimmermann - Bach- Goldberg Variations, Canons [Disc 1]",
                files.get(0).name());
        assertEquals("_DIR_ chrome hoof - 2004", files.get(1).name());
        assertEquals("_DIR_ Ravel - Chamber Music With Voice", files.get(2).name());
        assertEquals("_DIR_ Sackcloth 'n' Ashes", files.get(3).name());
        assertEquals("_DIR_ Ravel - Complete Piano Works", files.get(4).name());

        files = 
                mediaFileProvider.findAlbums(folders, RuntimeOrderPolicy.AlbumSortOrder.BY_ARTIST_AND_ALBUM, 0, Integer.MAX_VALUE);
        assertEquals(count, files.size());
        assertEquals(
                "_DIR_ Céline Frisch- Café Zimmermann - Bach- Goldberg Variations, Canons [Disc 1]",
                files.get(0).name());
        assertEquals("_DIR_ chrome hoof - 2004", files.get(1).name());
        assertEquals("_DIR_ Ravel - Chamber Music With Voice", files.get(2).name());
        assertEquals("_DIR_ Ravel - Complete Piano Works", files.get(3).name());
        assertEquals("_DIR_ Sackcloth 'n' Ashes", files.get(4).name());

        files = 
                mediaFileProvider.findAlbums(folders, RuntimeOrderPolicy.AlbumSortOrder.BY_ARTIST_AND_ALBUM, 1, 2);
        assertEquals("_DIR_ chrome hoof - 2004", files.get(0).name());
        assertEquals("_DIR_ Ravel - Chamber Music With Voice", files.get(1).name());
    }

    @Test
    public void testFindChildrenAlbumLongLongTypeArray() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        int count = albumProvider.countAlbums(folders);
        assertEquals(5, count);

        List<Album> albums = albumProvider
            .findAlbums(folders, RuntimeOrderPolicy.AlbumSortOrder.DEFAULT, 0, Integer.MAX_VALUE);
        assertEquals(count, albums.size());

        assertEquals(
                "_ID3_ALBUM_ Bach: Goldberg Variations, Canons [Disc 1]",
                albums.get(0).name());
        List<MediaFile> songs =
        mediaFileProvider.findChildren(folders, albums.get(0), 0, Integer.MAX_VALUE, MediaFile.Type.VIDEO);
        assertEquals(2, songs.size());

        assertEquals(
                "Bach: Goldberg Variations, BWV 988 - Aria",
                songs.get(0).title());
        assertEquals(
                "Bach: Goldberg Variations, BWV 988 - Variatio 1 A 1 Clav.",
                songs.get(1).title());

        assertEquals(1, songs.get(0).trackNumber().get());
        assertEquals(2, songs.get(1).trackNumber().get());
    }

    @Test
    public void testFindChildrenListOfMusicFolderLongLongTypeArray() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        int count = mediaFileProvider.countChildren(folders);
        List<MediaFile> files = mediaFileProvider.findChildren(folders, 0, Integer.MAX_VALUE);
        assertEquals(count, files.size());

        assertEquals("DIRECTORY:_DIR_ Ravel", files.get(0).type().name() + ":" + files.get(0).name());
        assertEquals("DIRECTORY:_DIR_ Sixteen Horsepower", files.get(1).type().name() + ":" + files.get(1).name());
        assertEquals("ALBUM:_DIR_ Céline Frisch- Café Zimmermann - Bach- Goldberg Variations, Canons [Disc 1]", files.get(2).type().name() + ":" + files.get(2).name());
        assertEquals("ALBUM:_DIR_ chrome hoof - 2004", files.get(3).type().name() + ":" + files.get(3).name());
        
        files = mediaFileProvider.findChildren(folders, 0, Integer.MAX_VALUE, MediaFile.Type.DIRECTORY);
        assertEquals("ALBUM:_DIR_ Céline Frisch- Café Zimmermann - Bach- Goldberg Variations, Canons [Disc 1]", files.get(0).type().name() + ":" + files.get(0).name());
        assertEquals("ALBUM:_DIR_ chrome hoof - 2004", files.get(1).type().name() + ":" + files.get(1).name());
    }

    @Test
    public void testFindChildrenListOfMusicFolderMediaFileOrderLongLongTypeArray() {
        final MediaFile mediaFile = mediaFileProvider.requireMediaFile(2);
        assertEquals(2, mediaFile.id());
        assertEquals("_DIR_ Ravel", mediaFile.name());
        assertEquals(MediaFile.Type.DIRECTORY, mediaFile.type());
        
        int count = mediaFileProvider.countChildren(mediaFile);
        List<MediaFile> files = mediaFileProvider
            .findChildren(mediaFile, RuntimeOrderPolicy.ChildOrder.DEFAULT, 0, Integer.MAX_VALUE);
        assertEquals(count, files.size());
        assertEquals("ALBUM:_DIR_ Ravel - Chamber Music With Voice", files.get(0).type().name() + ":" + files.get(0).name());
        assertEquals("ALBUM:_DIR_ Ravel - Complete Piano Works", files.get(1).type().name() + ":" + files.get(1).name());

        files = mediaFileProvider
                .findChildren(mediaFile, RuntimeOrderPolicy.ChildOrder.DEFAULT, 0, Integer.MAX_VALUE, Type.ALBUM);
        assertEquals(0, files.size());
    }

    @Test
    public void testFindChildrenListOfMusicFolderMusicIndexLongLongTypeArray() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        List<IndexWithCount> indexWithCounts = mediaFileProvider.findIndexWithCounts(folders);
        assertEquals(1, indexWithCounts.size());
        assertEquals("#", indexWithCounts.get(0).index());
        assertEquals(4, indexWithCounts.get(0).count());

        List<MediaFile> files = mediaFileProvider.findChildren(folders, new MusicIndex("#", Collections.emptyList()), 0, 0);
        assertEquals(indexWithCounts.get(0).count(), files.size());

        assertEquals("DIRECTORY:_DIR_ Ravel", files.get(0).type().name() + ":" + files.get(0).name());
        assertEquals("DIRECTORY:_DIR_ Sixteen Horsepower", files.get(1).type().name() + ":" + files.get(1).name());
        assertEquals("ALBUM:_DIR_ Céline Frisch- Café Zimmermann - Bach- Goldberg Variations, Canons [Disc 1]", files.get(2).type().name() + ":" + files.get(2).name());
        assertEquals("ALBUM:_DIR_ chrome hoof - 2004", files.get(3).type().name() + ":" + files.get(3).name());
    }

    @Test
    public void testFindIndexedDirectories() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        List<MediaFile> files = mediaFileProvider.findIndexedDirectories(folders);
        assertEquals(4, files.size());

        assertEquals("DIRECTORY:_DIR_ Ravel", files.get(0).type().name() + ":" + files.get(0).name());
        assertEquals("DIRECTORY:_DIR_ Sixteen Horsepower", files.get(1).type().name() + ":" + files.get(1).name());
        assertEquals("ALBUM:_DIR_ Céline Frisch- Café Zimmermann - Bach- Goldberg Variations, Canons [Disc 1]", files.get(2).type().name() + ":" + files.get(2).name());
        assertEquals("ALBUM:_DIR_ chrome hoof - 2004", files.get(3).type().name() + ":" + files.get(3).name());
    }

    @Test
    public void testFindIndexWithCounts() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        List<IndexWithCount> indexWithCounts = mediaFileProvider.findIndexWithCounts(folders);

        assertEquals(1, indexWithCounts.size());
        assertEquals("#", indexWithCounts.get(0).index());
        assertEquals(4, indexWithCounts.get(0).count());
    }

    @Test
    public void testFindMediaFile() {
        MediaFile mediaFile = mediaFileProvider.requireMediaFile(2);
        assertTrue(mediaFileProvider.findMediaFile(mediaFile.toPath()).isPresent());
        assertFalse(mediaFileProvider.findMediaFile(Path.of("/tmp/fake")).isPresent());
    }

    @Test
    public void testFindNewestAlbums() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        int count = mediaFileProvider.countAlbums(folders);
        List<MediaFile> files = mediaFileProvider.findNewestAlbums(folders, 0, Integer.MAX_VALUE);
        assertEquals(count, files.size());
    }

    @Test
    public void testFindSongs() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        int count = mediaFileProvider.countSongs(folders);
        assertEquals(11, count);
        List<MediaFile> songs = mediaFileProvider.findSongs(folders, 0, Integer.MAX_VALUE);
        assertEquals(count, songs.size());

        assertEquals("Bach: Goldberg Variations, BWV 988 - Aria", songs.get(0).name());
        assertEquals("Bach: Goldberg Variations, BWV 988 - Variatio 1 A 1 Clav.", songs.get(1).name());
        assertEquals("Eyes Like Dull Hazlenuts", songs.get(9).name());
        assertEquals("Telegraph Hill", songs.get(10).name());

        songs = mediaFileProvider.findSongs(folders, 1, count - 2);
        assertEquals("Bach: Goldberg Variations, BWV 988 - Variatio 1 A 1 Clav.", songs.get(0).name());
        assertEquals("Eyes Like Dull Hazlenuts", songs.get(8).name());
    }

    @Test
    public void testFindVideos() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        int count = mediaFileProvider.countVideos(folders);
        List<MediaFile> videos = mediaFileProvider.findVideos(folders, 0, Integer.MAX_VALUE);
        assertEquals(count, videos.size());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testRequireMediaFileInt() {
        final MediaFile mediaFile1 = mediaFileProvider.requireMediaFile(2);
        final MediaFile mediaFile2 = mediaFileProvider.requireMediaFile(6);
        final MediaFile mediaFile3 = mediaFileProvider.requireMediaFile(4);

        assertEquals(2, mediaFile1.id());
        assertEquals("_DIR_ Ravel", mediaFile1.name());
        assertEquals(MediaFile.Format.UNDEFINED, mediaFile1.format());
        assertEquals(MediaFile.Type.DIRECTORY, mediaFile1.type());
        assertEquals(MediaFile.BitRate.UNDEFINED, mediaFile1.bitRate());
        assertEquals(MediaFile.DurationSeconds.UNDEFINED, mediaFile1.durationSeconds());
        assertEquals(0, mediaFile1.fileSize());
        assertEquals("_DIR_ Ravel", mediaFile1.artist());
        assertNull(mediaFile1.album());
        assertNull(mediaFile1.title());

        assertEquals(6, mediaFile2.id());
        assertNull(mediaFile2.title());
        assertEquals("_DIR_ Ravel - Chamber Music With Voice", mediaFile2.name());
        assertEquals(Format.UNDEFINED, mediaFile2.format());
        assertEquals(Type.ALBUM, mediaFile2.type());
        assertEquals(BitRate.UNDEFINED, mediaFile2.bitRate());
        assertEquals(DurationSeconds.UNDEFINED, mediaFile2.durationSeconds());
        assertEquals(0, mediaFile2.fileSize());
        assertEquals("_ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble", mediaFile2.artist());
        assertEquals("_ID3_ALBUM_ Ravel - Chamber Music With Voice", mediaFile2.album());
        assertNull(mediaFile2.title());

        assertEquals(4, mediaFile3.id());
        assertEquals("Bach: Goldberg Variations, BWV 988 - Aria", mediaFile3.title());
        assertEquals("Bach: Goldberg Variations, BWV 988 - Aria", mediaFile3.name());
        assertEquals("flac", mediaFile3.format().value());
        assertEquals(Type.MUSIC, mediaFile3.type());
        assertEquals(756, mediaFile3.bitRate().value());
        assertEquals(4, mediaFile3.durationSeconds().value());
        assertEquals(358_406, mediaFile3.fileSize());
        assertEquals("_ID3_ARTIST_ Céline Frisch: Café Zimmermann", mediaFile3.artist());
        assertEquals("_ID3_ALBUM_ Bach: Goldberg Variations, Canons [Disc 1]", mediaFile3.album());
        assertEquals("Bach: Goldberg Variations, BWV 988 - Aria", mediaFile3.title());

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> mediaFileProvider.requireMediaFile(99999));
    }

    @Test
    public void testRequireMediaFileMusicFolder() {
        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();
        assertEquals(2, folders.size());
        MusicFolder musicFolder = folders.get(0);
        MediaFile mediaFile = mediaFileProvider.requireMediaFile(musicFolder);
        assertEquals(musicFolder.toPath(), mediaFile.toPath());
        MediaFile mediaFile2 = mediaFileProvider.requireMediaFile(mediaFile.id());
        assertEquals(mediaFile2.toPath(), mediaFile.toPath());
        
        MusicFolder fake = new MusicFolder(0, "/tmp/fake", null, isDataBaseReady(), null, 0, isDataBasePopulated());
        assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> mediaFileProvider.requireMediaFile(fake));
    }
}

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
 * (C) 2009 Sindre Mehus
 * (C) 2016 Airsonic Authors
 * (C) 2018 tesshucom
 */

package org.airsonic.player.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.tesshu.jpsonic.dao.JMediaFileDao;
import com.tesshu.jpsonic.dao.JPlaylistDao;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.dao.PlaylistDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.service.playlist.DefaultPlaylistImportHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class PlaylistServiceImportTest {

    PlaylistService playlistService;

    @InjectMocks
    DefaultPlaylistImportHandler defaultPlaylistImportHandler;

    @Mock
    MediaFileDao mediaFileDao;

    @Mock
    PlaylistDao playlistDao;

    @Mock
    MediaFileService mediaFileService;

    @Mock
    SettingsService settingsService;

    @Mock
    SecurityService securityService;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Captor
    ArgumentCaptor<Playlist> actual;

    @Captor
    ArgumentCaptor<List<MediaFile>> medias;

    @Before
    public void setup() {
        playlistService = new PlaylistService(new JMediaFileDao(mediaFileDao), new JPlaylistDao(playlistDao),
                securityService, settingsService, Collections.emptyList(),
                Lists.newArrayList(defaultPlaylistImportHandler));

    }

    @Test
    public void testImportFromM3U() throws Exception {
        final String username = "testUser";
        final String playlistName = "test-playlist";
        StringBuilder builder = new StringBuilder();
        builder.append("#EXTM3U\n");
        File mf1 = folder.newFile();
        FileUtils.touch(mf1);
        File mf2 = folder.newFile();
        FileUtils.touch(mf2);
        File mf3 = folder.newFile();
        FileUtils.touch(mf3);
        builder.append(mf1.getAbsolutePath()).append('\n').append(mf2.getAbsolutePath()).append('\n')
                .append(mf3.getAbsolutePath()).append('\n');
        doAnswer(new PersistPlayList(23)).when(playlistDao).createPlaylist(any());
        doAnswer(new MediaFileHasEverything()).when(mediaFileService).getMediaFile(any(File.class));
        InputStream inputStream = new ByteArrayInputStream(builder.toString().getBytes(StandardCharsets.UTF_8));
        String path = "/path/to/" + playlistName + ".m3u";
        playlistService.importPlaylist(username, playlistName, path, inputStream, null);
        verify(playlistDao).createPlaylist(actual.capture());
        verify(playlistDao).setFilesInPlaylist(eq(23), medias.capture());
        Playlist expected = new Playlist();
        expected.setUsername(username);
        expected.setName(playlistName);
        expected.setComment("Auto-imported from " + path);
        expected.setImportedFrom(path);
        expected.setShared(true);
        expected.setId(23);
        assertTrue(
                "\n" + ToStringBuilder.reflectionToString(actual.getValue()) + "\n\n did not equal \n\n"
                        + ToStringBuilder.reflectionToString(expected),
                EqualsBuilder.reflectionEquals(actual.getValue(), expected, "created", "changed"));
        List<MediaFile> mediaFiles = medias.getValue();
        assertEquals(3, mediaFiles.size());
    }

    @Test
    public void testImportFromPLS() throws Exception {
        final String username = "testUser";
        final String playlistName = "test-playlist";
        StringBuilder builder = new StringBuilder(40);
        builder.append("[playlist]\n");
        File mf1 = folder.newFile();
        FileUtils.touch(mf1);
        File mf2 = folder.newFile();
        FileUtils.touch(mf2);
        File mf3 = folder.newFile();
        FileUtils.touch(mf3);
        builder.append("File1=").append(mf1.getAbsolutePath()).append('\n').append("File2=")
                .append(mf2.getAbsolutePath()).append('\n').append("File3=").append(mf3.getAbsolutePath()).append('\n');
        doAnswer(new PersistPlayList(23)).when(playlistDao).createPlaylist(any());
        doAnswer(new MediaFileHasEverything()).when(mediaFileService).getMediaFile(any(File.class));
        InputStream inputStream = new ByteArrayInputStream(builder.toString().getBytes(StandardCharsets.UTF_8));
        String path = "/path/to/" + playlistName + ".pls";
        playlistService.importPlaylist(username, playlistName, path, inputStream, null);
        verify(playlistDao).createPlaylist(actual.capture());
        verify(playlistDao).setFilesInPlaylist(eq(23), medias.capture());
        Playlist expected = new Playlist();
        expected.setUsername(username);
        expected.setName(playlistName);
        expected.setComment("Auto-imported from " + path);
        expected.setImportedFrom(path);
        expected.setShared(true);
        expected.setId(23);
        assertTrue(
                "\n" + ToStringBuilder.reflectionToString(actual.getValue()) + "\n\n did not equal \n\n"
                        + ToStringBuilder.reflectionToString(expected),
                EqualsBuilder.reflectionEquals(actual.getValue(), expected, "created", "changed"));
        List<MediaFile> mediaFiles = medias.getValue();
        assertEquals(3, mediaFiles.size());
    }

    @Test
    public void testImportFromXSPF() throws Exception {
        final String username = "testUser";
        final String playlistName = "test-playlist";
        StringBuilder builder = new StringBuilder(300);
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<playlist version=\"1\" xmlns=\"http://xspf.org/ns/0/\">\n" + "    <trackList>\n");
        File mf1 = folder.newFile();
        FileUtils.touch(mf1);
        File mf2 = folder.newFile();
        FileUtils.touch(mf2);
        File mf3 = folder.newFile();
        FileUtils.touch(mf3);
        builder.append("<track><location>file://").append(mf1.getAbsolutePath()).append("</location></track>\n")
                .append("<track><location>file://").append(mf2.getAbsolutePath()).append("</location></track>\n")
                .append("<track><location>file://").append(mf3.getAbsolutePath()).append("</location></track>\n")
                .append("    </trackList>\n" + "</playlist>\n");
        doAnswer(new PersistPlayList(23)).when(playlistDao).createPlaylist(any());
        doAnswer(new MediaFileHasEverything()).when(mediaFileService).getMediaFile(any(File.class));
        InputStream inputStream = new ByteArrayInputStream(builder.toString().getBytes(StandardCharsets.UTF_8));
        String path = "/path/to/" + playlistName + ".xspf";
        playlistService.importPlaylist(username, playlistName, path, inputStream, null);
        verify(playlistDao).createPlaylist(actual.capture());
        verify(playlistDao).setFilesInPlaylist(eq(23), medias.capture());
        Playlist expected = new Playlist();
        expected.setUsername(username);
        expected.setName(playlistName);
        expected.setComment("Auto-imported from " + path);
        expected.setImportedFrom(path);
        expected.setShared(true);
        expected.setId(23);
        assertTrue(
                "\n" + ToStringBuilder.reflectionToString(actual.getValue()) + "\n\n did not equal \n\n"
                        + ToStringBuilder.reflectionToString(expected),
                EqualsBuilder.reflectionEquals(actual.getValue(), expected, "created", "changed"));
        List<MediaFile> mediaFiles = medias.getValue();
        assertEquals(3, mediaFiles.size());
    }

    private static class PersistPlayList implements Answer {
        private final int id;

        public PersistPlayList(int id) {
            this.id = id;
        }

        @Override
        public Object answer(InvocationOnMock invocationOnMock) {
            Playlist playlist = invocationOnMock.getArgument(0);
            playlist.setId(id);
            return null;
        }
    }

    private static class MediaFileHasEverything implements Answer {

        @Override
        public Object answer(InvocationOnMock invocationOnMock) {
            File file = invocationOnMock.getArgument(0);
            MediaFile mediaFile = new MediaFile();
            mediaFile.setPath(file.getPath());
            return mediaFile;
        }
    }
}

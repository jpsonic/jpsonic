package com.tesshu.jpsonic.service;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.tesshu.jpsonic.infrastructure.filesystem.FileOperations;
import com.tesshu.jpsonic.infrastructure.filesystem.ScanningExclusionPolicy;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.Playlist;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.persistence.api.repository.PlaylistDao;
import com.tesshu.jpsonic.persistence.core.repository.UserDao;
import com.tesshu.jpsonic.service.playlist.DefaultPlaylistExportHandler;
import com.tesshu.jpsonic.service.playlist.DefaultPlaylistImportHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.Ignore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "PMD.TooManyStaticImports" })
class PlaylistServiceTest {

    @Nested
    class ExportPlaylistTest {

        private SettingsFacade settingsFacade;
        private MediaFileDao mediaFileDao;
        private PlaylistService playlistService;

        @BeforeEach
        void setup() {
            settingsFacade = SettingsFacadeBuilder.create().build();
            init();
        }

        @Ignore
        protected void init() {
            mediaFileDao = mock(MediaFileDao.class);
            PlaylistDao jPlaylistDao = mock(PlaylistDao.class);
            playlistService = new PlaylistService(mediaFileDao, jPlaylistDao,
                    mock(UserService.class), settingsFacade,
                    new ScanningExclusionPolicy(settingsFacade),
                    Arrays.asList(new DefaultPlaylistExportHandler(mediaFileDao)),
                    Collections.emptyList(), null, null);
        }

        @Test
        void testExportToM3U() throws Exception {
            Mockito
                .when(mediaFileDao
                    .getFilesInPlaylist(ArgumentMatchers.eq(23), Mockito.anyLong(),
                            Mockito.anyLong()))
                .thenReturn(getPlaylistFiles());

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                playlistService.exportPlaylist(23, outputStream);
                /*
                 * Since there is no problem in operation, blank lines are excluded from
                 * verification. (The trailing newline is not output on the Windows-"server")
                 */
                String expected = IOUtils
                    .toString(getClass().getResourceAsStream("/PLAYLISTS/23.m3u"),
                            StandardCharsets.UTF_8)
                    .replaceAll("\\r", "")
                    .replaceAll("\\n+$", "");
                String actual = outputStream
                    .toString(StandardCharsets.UTF_8)
                    .replaceAll("\\r", "")
                    .replaceAll("\\n+$", "");
                assertEquals(expected, actual);
            }
        }

        private List<MediaFile> getPlaylistFiles() {

            MediaFile mf1 = new MediaFile();
            mf1.setId(142);
            mf1.setPathString("/some/path/to_album/to_artist/name - of - song.mp3");
            mf1.setPresent(true);
            List<MediaFile> mediaFiles = new ArrayList<>();
            mediaFiles.add(mf1);

            MediaFile mf2 = new MediaFile();
            mf2.setId(1235);
            mf2.setPathString("/some/path/to_album2/to_artist/another song.mp3");
            mf2.setPresent(true);
            mediaFiles.add(mf2);

            MediaFile mf3 = new MediaFile();
            mf3.setId(198_403);
            mf3.setPathString("/some/path/to_album2/to_artist/another song2.mp3");
            mf3.setPresent(false);
            mediaFiles.add(mf3);

            return mediaFiles;
        }
    }

    @Nested
    class ImportPlaylistTest {

        private PlaylistService playlistService;

        private PlaylistDao playlistDao;
        private MediaFileService mediaFileService;

        private ArgumentCaptor<Playlist> actual;
        private ArgumentCaptor<List<MediaFile>> medias;

        @TempDir
        private Path tempDir;

        @SuppressWarnings("unchecked")
        @BeforeEach
        void setup() {
            playlistDao = mock(PlaylistDao.class);
            mediaFileService = mock(MediaFileService.class);
            DefaultPlaylistImportHandler importHandler = new DefaultPlaylistImportHandler(
                    mediaFileService);
            SettingsFacade settingsFacade = SettingsFacadeBuilder.create().build();
            playlistService = new PlaylistService(mock(MediaFileDao.class), playlistDao,
                    mock(UserService.class), settingsFacade,
                    new ScanningExclusionPolicy(settingsFacade), Collections.emptyList(),
                    Arrays.asList(importHandler), null, null);
            actual = ArgumentCaptor.forClass(Playlist.class);
            medias = ArgumentCaptor.forClass(List.class);
        }

        @Test
        void testImportFromM3U() throws Exception {

            final String username = "testUser";
            final String playlistName = "test-playlist";
            StringBuilder builder = new StringBuilder();
            builder.append("#EXTM3U\n");
            Path mf1 = Path.of(tempDir.toString(), "EXTM3U-mf1");
            FileOperations.touch(mf1);
            Path mf2 = Path.of(tempDir.toString(), "EXTM3U-mf2");
            FileOperations.touch(mf2);
            Path mf3 = Path.of(tempDir.toString(), "EXTM3U-mf3");
            FileOperations.touch(mf3);
            builder
                .append(mf1.toUri().toString())
                .append('\n')
                .append(mf2.toUri().toString())
                .append('\n')
                .append(mf3.toUri().toString())
                .append('\n');

            doAnswer(new PersistPlayList(23))
                .when(playlistDao)
                .createPlaylist(ArgumentMatchers.any());
            String path = playlistName + ".m3u";
            MediaFile mediaFile = new MediaFile();
            mediaFile.setPathString(path);
            Mockito
                .when(mediaFileService.getMediaFile(Mockito.any(Path.class)))
                .thenReturn(new MediaFile());

            InputStream inputStream = new ByteArrayInputStream(
                    builder.toString().getBytes(StandardCharsets.UTF_8));

            playlistService.importPlaylist(username, playlistName, path, inputStream, null);

            verify(playlistDao).createPlaylist(actual.capture());
            verify(playlistDao).setFilesInPlaylist(ArgumentMatchers.eq(23), medias.capture());

            Playlist expected = new Playlist();
            expected.setUsername(username);
            expected.setName(playlistName);
            expected.setComment("Auto-imported from " + path);
            expected.setImportedFrom(path);
            expected.setShared(true);
            expected.setId(23);

            Assertions
                .assertTrue(
                        EqualsBuilder
                            .reflectionEquals(actual.getValue(), expected, "created", "changed"),
                        "\n" + ToStringBuilder.reflectionToString(actual.getValue())
                                + "\n\n did not equal \n\n"
                                + ToStringBuilder.reflectionToString(expected));
            List<MediaFile> mediaFiles = medias.getValue();
            assertEquals(3, mediaFiles.size());
        }

        @Test
        void testImportFromPLS() throws Exception {
            final String username = "testUser";
            final String playlistName = "test-playlist";
            Path mf1 = Path.of(tempDir.toString(), "PLS-mf1");
            FileOperations.touch(mf1);
            Path mf2 = Path.of(tempDir.toString(), "PLS-mf2");
            FileOperations.touch(mf2);
            Path mf3 = Path.of(tempDir.toString(), "PLS-mf3");
            FileOperations.touch(mf3);
            StringBuilder builder = new StringBuilder(40);
            builder
                .append("[playlist]\nFile1=")
                .append(mf1.toUri().toString())
                .append("\nFile2=")
                .append(mf2.toUri().toString())
                .append("\nFile3=")
                .append(mf3.toUri().toString())
                .append('\n');

            doAnswer(new PersistPlayList(23))
                .when(playlistDao)
                .createPlaylist(ArgumentMatchers.any());

            String path = Path.of("/path/to/" + playlistName + ".pls").toString();
            MediaFile mediaFile = new MediaFile();
            mediaFile.setPathString(path);
            Mockito
                .when(mediaFileService.getMediaFile(Mockito.any(Path.class)))
                .thenReturn(mediaFile);

            InputStream inputStream = new ByteArrayInputStream(
                    builder.toString().getBytes(StandardCharsets.UTF_8));

            playlistService.importPlaylist(username, playlistName, path, inputStream, null);

            verify(playlistDao).createPlaylist(actual.capture());
            verify(playlistDao).setFilesInPlaylist(ArgumentMatchers.eq(23), medias.capture());

            Playlist expected = new Playlist();
            expected.setUsername(username);
            expected.setName(playlistName);
            expected.setComment("Auto-imported from " + path);
            expected.setImportedFrom(path);
            expected.setShared(true);
            expected.setId(23);

            Assertions
                .assertTrue(EqualsBuilder
                    .reflectionEquals(actual.getValue(), expected, "created", "changed"));
            List<MediaFile> mediaFiles = medias.getValue();
            assertEquals(3, mediaFiles.size());
        }

        @Test
        void testImportFromXSPF() throws Exception {
            final String username = "testUser";
            final String playlistName = "test-playlist";
            Path mf1 = Path.of(tempDir.toString(), "XSPF-mf1");
            FileOperations.touch(mf1);
            Path mf2 = Path.of(tempDir.toString(), "XSPF-mf2");
            FileOperations.touch(mf2);
            Path mf3 = Path.of(tempDir.toString(), "XSPF-mf3");
            FileOperations.touch(mf3);
            StringBuilder builder = new StringBuilder(300);
            builder
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<playlist version=\"1\" xmlns=\"http://xspf.org/ns/0/\">\n<trackList>\n<track><location>")
                .append(mf1.toUri().toString())
                .append("</location></track>\n<track><location>")
                .append(mf2.toUri().toString())
                .append("</location></track>\n<track><location>")
                .append(mf3.toUri().toString())
                .append("</location></track>\n</trackList>\n</playlist>\n");

            doAnswer(new PersistPlayList(23))
                .when(playlistDao)
                .createPlaylist(ArgumentMatchers.any());
            String path = Path.of("/path/to/" + playlistName + ".xspf").toString();
            MediaFile mediaFile = new MediaFile();
            mediaFile.setPathString(path);
            Mockito
                .when(mediaFileService.getMediaFile(Mockito.any(Path.class)))
                .thenReturn(mediaFile);

            InputStream inputStream = new ByteArrayInputStream(
                    builder.toString().getBytes(StandardCharsets.UTF_8));

            playlistService.importPlaylist(username, playlistName, path, inputStream, null);

            verify(playlistDao).createPlaylist(actual.capture());
            verify(playlistDao).setFilesInPlaylist(ArgumentMatchers.eq(23), medias.capture());

            Playlist expected = new Playlist();
            expected.setUsername(username);
            expected.setName(playlistName);
            expected.setComment("Auto-imported from " + path);
            expected.setImportedFrom(path);
            expected.setShared(true);
            expected.setId(23);
            Assertions
                .assertTrue(EqualsBuilder
                    .reflectionEquals(actual.getValue(), expected, "created", "changed"));
            List<MediaFile> mediaFiles = medias.getValue();
            assertEquals(3, mediaFiles.size());
        }
    }

    private static class PersistPlayList implements Answer<Object> {
        private final int id;

        PersistPlayList(int id) {
            this.id = id;
        }

        @Override
        public Object answer(InvocationOnMock invocationOnMock) {
            Playlist playlist = invocationOnMock.getArgument(0);
            playlist.setId(id);
            return null;
        }
    }

    @Nested
    class ImportPlaylistsTest {

        private SettingsFacade settingsFacade;
        private PlaylistService playlistService;
        private PlaylistDao playlistDao;
        private MediaFileService mediaFileService;

        @TempDir
        private Path tempDir;

        @BeforeEach
        void setup() {
            settingsFacade = SettingsFacadeBuilder.create().build();
            init();
        }

        @Ignore
        void init() {
            playlistDao = mock(PlaylistDao.class);
            mediaFileService = mock(MediaFileService.class);
            UserService userService = new UserService(mock(UserDao.class),
                    mock(MusicFolderService.class));
            DefaultPlaylistImportHandler importHandler = new DefaultPlaylistImportHandler(
                    mediaFileService);
            playlistService = new PlaylistService(mock(MediaFileDao.class), playlistDao,
                    userService, settingsFacade, new ScanningExclusionPolicy(settingsFacade),
                    Collections.emptyList(), Arrays.asList(importHandler), null, null);
        }

        @Test
        void testImportPlaylists(@TempDir Path tempDir) throws IOException {

            final Instant current = now();
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.general.extension.playlistFolder, tempDir.toString())
                .build();
            init();

            Path mf1 = Path.of(tempDir.toString(), "XSPF-mf1");
            FileOperations.touch(mf1);
            Path mf2 = Path.of(tempDir.toString(), "XSPF-mf2");
            FileOperations.touch(mf2);
            Path mf3 = Path.of(tempDir.toString(), "XSPF-mf3");
            FileOperations.touch(mf3);
            StringBuilder builder = new StringBuilder(300);
            builder
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<playlist version=\"1\" xmlns=\"http://xspf.org/ns/0/\">\n<trackList>\n<track><location>")
                .append(mf1.toUri().toString())
                .append("</location></track>\n<track><location>")
                .append(mf2.toUri().toString())
                .append("</location></track>\n<track><location>")
                .append(mf3.toUri().toString())
                .append("</location></track>\n</trackList>\n</playlist>\n");

            Path playlistFile = Path.of(tempDir.toString(), "playlistFile");
            Files.write(playlistFile, builder.toString().getBytes(StandardCharsets.UTF_8));

            Playlist playlist = new Playlist();
            playlist.setImportedFrom(playlistFile.getFileName().toString());
            playlist.setChanged(current);
            Mockito.when(playlistDao.getAllPlaylists()).thenReturn(Arrays.asList(playlist));

            MediaFile mediaFile = new MediaFile();
            mediaFile.setId(0);
            mediaFile.setPathString(mf3.toString());
            Mockito.when(mediaFileService.getMediaFile(mf3)).thenReturn(mediaFile);

            // Assuming Synology environment
            Files.createDirectories(Path.of(tempDir.toString(), "@eaDir"));
            Files.createDirectories(Path.of(tempDir.toString(), "@tmp"));
            // Assuming Windows environment
            Files.createFile(Path.of(tempDir.toString(), "Thumbs.db"));

            ArgumentCaptor<Playlist> playlistCatCaptor = ArgumentCaptor.forClass(Playlist.class);
            Mockito.doNothing().when(playlistDao).createPlaylist(playlistCatCaptor.capture());

            playlistService.importPlaylists();

            List<Playlist> captored = playlistCatCaptor.getAllValues();
            captored.sort((p1, p2) -> p1.getName().compareTo(p2.getName()));
            assertEquals(3, playlistCatCaptor.getAllValues().size());
            assertEquals("XSPF-mf1", captored.get(0).getName());
            assertEquals("XSPF-mf2", captored.get(1).getName());
            assertEquals("XSPF-mf3", captored.get(2).getName());
        }
    }
}

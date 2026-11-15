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

package com.tesshu.jpsonic.feature.stream;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.annotation.Documented;
import java.util.Arrays;
import java.util.List;

import com.tesshu.jpsonic.controller.Attributes;
import com.tesshu.jpsonic.domain.system.PreferredFormatScheme;
import com.tesshu.jpsonic.feature.auth.jwt.JWTAuthenticationToken;
import com.tesshu.jpsonic.infrastructure.scanner.WritableMediaFileService;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.PlayQueue;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.PlaylistService;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.StatusService;
import com.tesshu.jpsonic.service.StatusService.TransferStatus;
import com.tesshu.jpsonic.service.TranscodingService;
import com.tesshu.jpsonic.service.TranscodingService.VideoTranscodingSettings;
import com.tesshu.jpsonic.service.UserService;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("PMD.TooManyStaticImports")
class StreamServiceTest {

    private StatusService statusService;
    private PlaylistService playlistService;
    private SettingsFacade settingsFacade;
    private UserService userService;
    private MediaFileService mediaFileService;
    private StreamService streamService;

    @BeforeEach
    void setup() {
        settingsFacade = SettingsFacadeBuilder.create().build();
        init();
    }

    @Ignore
    void init() {
        statusService = mock(StatusService.class);
        playlistService = mock(PlaylistService.class);
        userService = mock(UserService.class);
        mediaFileService = mock(MediaFileService.class);
        streamService = new StreamService(statusService, playlistService, userService,
                settingsFacade, mock(TranscodingService.class), null, mediaFileService,
                mock(WritableMediaFileService.class), null, null);
    }

    @Test
    @Order(1)
    void testSetUpPlayQueue() throws Exception {
        Player player = new Player();
        player.setId(1);
        final int playlistId = 10;

        MediaFile song1 = new MediaFile();
        song1.setPathString("song1");
        song1.setFileSize(1024L);
        MediaFile song2 = new MediaFile();
        song2.setPathString("song2");
        song2.setFileSize(1024L);
        List<MediaFile> songs = Arrays.asList(song1, song2);
        Mockito.when(playlistService.getFilesInPlaylist(playlistId)).thenReturn(songs);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        streamService.setUpPlayQueue(request, response, player, playlistId);
        assertEquals(songs, player.getPlayQueue().getFiles());
        assertEquals("2048", response.getHeader(HttpHeaders.CONTENT_LENGTH));
    }

    @Documented
    private @interface GetFormatDecision {
        @interface Conditions {

            @interface Param {
                @interface IsRest {
                    @interface False {
                    }

                    @interface True {
                    }

                    @interface Null {
                    }
                }

                @interface Format {
                    @interface Null {
                    }

                    @interface Aac {
                    }
                }
            }

            @interface Settings {
                @interface PreferredFormatSheme {
                    @interface Annoymous {
                    }

                    @interface OtherThanRequest {
                    }

                    @interface RequestOnly {
                    }
                }

                @interface PreferredFormat {
                    @interface Null {
                    }

                    @interface Mp3 {
                    }
                }
            }
        }

        @interface Results {
            @interface Null {
            }

            @interface Aac {
            }

            @interface Mp3 {
            }
        }
    }

    @Order(2)
    @Nested
    class GetFormatTest {

        String fmtMp3 = "mp3";
        String fmtAac = "aac";

        @GetFormatDecision.Conditions.Param.IsRest.True
        @GetFormatDecision.Conditions.Param.Format.Null
        @GetFormatDecision.Results.Null
        @Test
        void c1() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            Player player = new Player();
            assertNull(streamService.getFormat(request, player, true));
        }

        @GetFormatDecision.Conditions.Param.IsRest.True
        @GetFormatDecision.Conditions.Param.Format.Aac
        @GetFormatDecision.Results.Aac
        @Test
        void c2() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter(Attributes.Request.FORMAT.value(), fmtAac);
            Player player = new Player();
            assertEquals(fmtAac, streamService.getFormat(request, player, true));
        }

        @GetFormatDecision.Conditions.Param.IsRest.False
        @GetFormatDecision.Conditions.Param.Format.Null
        @GetFormatDecision.Conditions.Settings.PreferredFormatSheme.Annoymous
        @GetFormatDecision.Conditions.Settings.PreferredFormat.Null
        @GetFormatDecision.Results.Null
        @Test
        void c3() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.transcoding.preferredFormatShemeName,
                        PreferredFormatScheme.ANNOYMOUS.name())
                .build();
            init();

            MockHttpServletRequest request = new MockHttpServletRequest();
            Player player = new Player();
            player.setUsername(JWTAuthenticationToken.USERNAME_ANONYMOUS);
            assertNull(streamService.getFormat(request, player, false));
        }

        @GetFormatDecision.Conditions.Param.IsRest.False
        @GetFormatDecision.Conditions.Param.Format.Null
        @GetFormatDecision.Conditions.Settings.PreferredFormatSheme.Annoymous
        @GetFormatDecision.Conditions.Settings.PreferredFormat.Mp3
        @GetFormatDecision.Results.Mp3
        @Test
        void c4() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.transcoding.preferredFormatShemeName,
                        PreferredFormatScheme.ANNOYMOUS.name())
                .withString(SKeys.transcoding.preferredFormat, fmtMp3)
                .build();
            init();

            MockHttpServletRequest request = new MockHttpServletRequest();
            Player player = new Player();
            player.setUsername(JWTAuthenticationToken.USERNAME_ANONYMOUS);
            assertEquals(fmtMp3, streamService.getFormat(request, player, false));
        }

        @GetFormatDecision.Conditions.Param.IsRest.False
        @GetFormatDecision.Conditions.Param.Format.Aac
        @GetFormatDecision.Conditions.Settings.PreferredFormatSheme.Annoymous
        @GetFormatDecision.Conditions.Settings.PreferredFormat.Null
        @GetFormatDecision.Results.Aac
        @Test
        void c5() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.transcoding.preferredFormatShemeName,
                        PreferredFormatScheme.ANNOYMOUS.name())
                .withString(SKeys.transcoding.preferredFormat, fmtAac)
                .build();
            init();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter(Attributes.Request.FORMAT.value(), fmtAac);
            Player player = new Player();
            player.setUsername(JWTAuthenticationToken.USERNAME_ANONYMOUS);
            assertEquals(fmtAac, streamService.getFormat(request, player, false));
        }

        @GetFormatDecision.Conditions.Param.IsRest.False
        @GetFormatDecision.Conditions.Param.Format.Aac
        @GetFormatDecision.Conditions.Settings.PreferredFormatSheme.Annoymous
        @GetFormatDecision.Conditions.Settings.PreferredFormat.Mp3
        @GetFormatDecision.Results.Aac
        @Test
        void c6() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.transcoding.preferredFormatShemeName,
                        PreferredFormatScheme.ANNOYMOUS.name())
                .withString(SKeys.transcoding.preferredFormat, fmtMp3)
                .build();
            init();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter(Attributes.Request.FORMAT.value(), fmtAac);
            Player player = new Player();
            player.setUsername(JWTAuthenticationToken.USERNAME_ANONYMOUS);
            assertEquals(fmtAac, streamService.getFormat(request, player, false));
        }

        @GetFormatDecision.Conditions.Param.IsRest.False
        @GetFormatDecision.Conditions.Param.Format.Null
        @GetFormatDecision.Conditions.Settings.PreferredFormatSheme.OtherThanRequest
        @GetFormatDecision.Conditions.Settings.PreferredFormat.Null
        @GetFormatDecision.Results.Null
        @Test
        void c7() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.transcoding.preferredFormatShemeName,
                        PreferredFormatScheme.OTHER_THAN_REQUEST.name())
                .build();
            init();

            MockHttpServletRequest request = new MockHttpServletRequest();
            Player player = new Player();
            assertNull(streamService.getFormat(request, player, false));
        }

        @GetFormatDecision.Conditions.Param.IsRest.False
        @GetFormatDecision.Conditions.Param.Format.Null
        @GetFormatDecision.Conditions.Settings.PreferredFormatSheme.OtherThanRequest
        @GetFormatDecision.Conditions.Settings.PreferredFormat.Mp3
        @GetFormatDecision.Results.Mp3
        @Test
        void c8() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.transcoding.preferredFormatShemeName,
                        PreferredFormatScheme.OTHER_THAN_REQUEST.name())
                .withString(SKeys.transcoding.preferredFormat, fmtMp3)
                .build();
            init();

            MockHttpServletRequest request = new MockHttpServletRequest();
            Player player = new Player();
            assertEquals(fmtMp3, streamService.getFormat(request, player, false));
        }

        @GetFormatDecision.Conditions.Param.IsRest.False
        @GetFormatDecision.Conditions.Param.Format.Aac
        @GetFormatDecision.Conditions.Settings.PreferredFormatSheme.OtherThanRequest
        @GetFormatDecision.Conditions.Settings.PreferredFormat.Null
        @GetFormatDecision.Results.Aac
        @Test
        void c9() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.transcoding.preferredFormatShemeName,
                        PreferredFormatScheme.OTHER_THAN_REQUEST.name())
                .withString(SKeys.transcoding.preferredFormat, fmtAac)
                .build();
            init();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter(Attributes.Request.FORMAT.value(), fmtAac);
            Player player = new Player();
            assertEquals(fmtAac, streamService.getFormat(request, player, false));
        }

        @GetFormatDecision.Conditions.Param.IsRest.False
        @GetFormatDecision.Conditions.Param.Format.Aac
        @GetFormatDecision.Conditions.Settings.PreferredFormatSheme.OtherThanRequest
        @GetFormatDecision.Conditions.Settings.PreferredFormat.Mp3
        @GetFormatDecision.Results.Aac
        @Test
        void c10() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.transcoding.preferredFormatShemeName,
                        PreferredFormatScheme.OTHER_THAN_REQUEST.name())
                .withString(SKeys.transcoding.preferredFormat, fmtAac)
                .build();
            init();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter(Attributes.Request.FORMAT.value(), fmtAac);
            Player player = new Player();
            assertEquals(fmtAac, streamService.getFormat(request, player, false));
        }

        @GetFormatDecision.Conditions.Param.IsRest.True
        @GetFormatDecision.Conditions.Param.Format.Null
        @GetFormatDecision.Conditions.Settings.PreferredFormatSheme.OtherThanRequest
        @GetFormatDecision.Conditions.Settings.PreferredFormat.Mp3
        @GetFormatDecision.Results.Null
        @Test
        void c11() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.transcoding.preferredFormatShemeName,
                        PreferredFormatScheme.OTHER_THAN_REQUEST.name())
                .withString(SKeys.transcoding.preferredFormat, fmtMp3)
                .build();
            init();

            MockHttpServletRequest request = new MockHttpServletRequest();
            Player player = new Player();
            assertNull(streamService.getFormat(request, player, true));
        }

        @GetFormatDecision.Conditions.Param.IsRest.True
        @GetFormatDecision.Conditions.Param.Format.Null
        @GetFormatDecision.Conditions.Settings.PreferredFormatSheme.RequestOnly
        @GetFormatDecision.Conditions.Settings.PreferredFormat.Mp3
        @GetFormatDecision.Results.Null
        @Test
        void c12() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.transcoding.preferredFormatShemeName,
                        PreferredFormatScheme.REQUEST_ONLY.name())
                .withString(SKeys.transcoding.preferredFormat, fmtMp3)
                .build();
            init();

            MockHttpServletRequest request = new MockHttpServletRequest();
            Player player = new Player();
            assertNull(streamService.getFormat(request, player, true));
        }

        @GetFormatDecision.Conditions.Param.IsRest.Null
        @GetFormatDecision.Conditions.Param.Format.Null
        @GetFormatDecision.Conditions.Settings.PreferredFormatSheme.OtherThanRequest
        @GetFormatDecision.Conditions.Settings.PreferredFormat.Mp3
        @GetFormatDecision.Results.Null
        @Test
        void c13() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.transcoding.preferredFormatShemeName,
                        PreferredFormatScheme.OTHER_THAN_REQUEST.name())
                .withString(SKeys.transcoding.preferredFormat, fmtMp3)
                .build();
            init();

            MockHttpServletRequest request = new MockHttpServletRequest();
            Player player = new Player();
            assertEquals(fmtMp3, streamService.getFormat(request, player, null));
        }
    }

    @Order(3)
    @Test
    void testGetSingleFile() throws Exception {

        MediaFile song = new MediaFile();
        song.setPathString("song");

        Mockito.when(mediaFileService.getMediaFile(song.getId())).thenReturn(song);
        Mockito.when(mediaFileService.getMediaFile(song.getPathString())).thenReturn(song);

        MockHttpServletRequest request = new MockHttpServletRequest();
        assertNull(streamService.getSingleFile(request));

        request.setParameter(Attributes.Request.PATH.value(), song.getPathString());
        assertEquals(song, streamService.getSingleFile(request));

        request.removeAllParameters();
        request.setParameter(Attributes.Request.ID.value(), Integer.toString(song.getId()));
        assertEquals(song, streamService.getSingleFile(request));
    }

    @Order(4)
    @Test
    void testCreateVideoTranscodingSettings() throws Exception {
        MediaFile video = new MediaFile();
        video.setPathString("song");
        video.setWidth(300);
        video.setHeight(200);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(Attributes.Request.MAX_BIT_RATE.value(), "320");

        VideoTranscodingSettings settings = streamService
            .createVideoTranscodingSettings(video, request);
        assertEquals(300, settings.getWidth());
        assertEquals(200, settings.getHeight());
        assertEquals(2_147_483_647, settings.getDuration());
        assertEquals(0, settings.getTimeOffset());
        assertFalse(settings.isHls());

        video.setDurationSeconds(60);
        settings = streamService.createVideoTranscodingSettings(video, request);
        assertEquals(300, settings.getWidth());
        assertEquals(200, settings.getHeight());
        assertEquals(60, settings.getDuration());
        assertEquals(0, settings.getTimeOffset());
        assertFalse(settings.isHls());

        request.setParameter(Attributes.Request.SIZE.value(), "600x400");
        settings = streamService.createVideoTranscodingSettings(video, request);
        assertEquals(600, settings.getWidth());
        assertEquals(400, settings.getHeight());
        assertEquals(60, settings.getDuration());
        assertEquals(0, settings.getTimeOffset());
        assertFalse(settings.isHls());

        request.setParameter(Attributes.Request.DURATION.value(), "30");
        settings = streamService.createVideoTranscodingSettings(video, request);
        assertEquals(600, settings.getWidth());
        assertEquals(400, settings.getHeight());
        assertEquals(30, settings.getDuration());
        assertEquals(0, settings.getTimeOffset());
        assertFalse(settings.isHls());

        request.setParameter(Attributes.Request.TIME_OFFSET.value(), "15");
        settings = streamService.createVideoTranscodingSettings(video, request);
        assertEquals(600, settings.getWidth());
        assertEquals(400, settings.getHeight());
        assertEquals(30, settings.getDuration());
        assertEquals(15, settings.getTimeOffset());
        assertFalse(settings.isHls());

        request.setParameter(Attributes.Request.HLS.value(), "true");
        settings = streamService.createVideoTranscodingSettings(video, request);
        assertEquals(600, settings.getWidth());
        assertEquals(400, settings.getHeight());
        assertEquals(30, settings.getDuration());
        assertEquals(15, settings.getTimeOffset());
        assertTrue(settings.isHls());
    }

    @Order(5)
    @Test
    void testGetRequestedVideoSize() {
        assertNull(streamService.getRequestedVideoSize(null));
        assertNull(streamService.getRequestedVideoSize("fooxbar"));
        assertNull(streamService.getRequestedVideoSize("-1x1000"));
        assertNull(streamService.getRequestedVideoSize("1000x-1"));
        assertNull(streamService.getRequestedVideoSize("3000x1000"));
        assertNull(streamService.getRequestedVideoSize("1000x3000"));
        assertNotNull(streamService.getRequestedVideoSize("1000x1000"));
    }

    private boolean doTestGetSuitableVideoSize(Integer existingWidth, Integer existingHeight,
            Integer maxBitRate, int expectedWidth, int expectedHeight) {
        Dimension dimension = streamService
            .getSuitableVideoSize(existingWidth, existingHeight, maxBitRate);
        assertEquals(expectedWidth, dimension.width, "Wrong width.");
        assertEquals(expectedHeight, dimension.height, "Wrong height.");
        return true;
    }

    @Order(6)
    @Test
    void testGetSuitableVideoSize() throws Exception {

        // default
        assertTrue(doTestGetSuitableVideoSize(1280, 960, null, 400, 224));

        // 4:3 aspect rate
        assertTrue(doTestGetSuitableVideoSize(1280, 960, 200, 400, 300));
        assertTrue(doTestGetSuitableVideoSize(1280, 960, 300, 400, 300));
        assertTrue(doTestGetSuitableVideoSize(1280, 960, 400, 480, 360));
        assertTrue(doTestGetSuitableVideoSize(1280, 960, 500, 480, 360));
        assertTrue(doTestGetSuitableVideoSize(1280, 960, 600, 640, 480));
        assertTrue(doTestGetSuitableVideoSize(1280, 960, 700, 640, 480));
        assertTrue(doTestGetSuitableVideoSize(1280, 960, 800, 640, 480));
        assertTrue(doTestGetSuitableVideoSize(1280, 960, 900, 640, 480));
        assertTrue(doTestGetSuitableVideoSize(1280, 960, 1000, 640, 480));
        assertTrue(doTestGetSuitableVideoSize(1280, 960, 1100, 640, 480));
        assertTrue(doTestGetSuitableVideoSize(1280, 960, 1200, 640, 480));
        assertTrue(doTestGetSuitableVideoSize(1280, 960, 1500, 640, 480));
        assertTrue(doTestGetSuitableVideoSize(1280, 960, 1800, 960, 720));
        assertTrue(doTestGetSuitableVideoSize(1280, 960, 2000, 960, 720));

        // 16:9 aspect rate
        assertTrue(doTestGetSuitableVideoSize(1280, 720, 200, 400, 226));
        assertTrue(doTestGetSuitableVideoSize(1280, 720, 300, 400, 226));
        assertTrue(doTestGetSuitableVideoSize(1280, 720, 400, 480, 270));
        assertTrue(doTestGetSuitableVideoSize(1280, 720, 500, 480, 270));
        assertTrue(doTestGetSuitableVideoSize(1280, 720, 600, 640, 360));
        assertTrue(doTestGetSuitableVideoSize(1280, 720, 700, 640, 360));
        assertTrue(doTestGetSuitableVideoSize(1280, 720, 800, 640, 360));
        assertTrue(doTestGetSuitableVideoSize(1280, 720, 900, 640, 360));
        assertTrue(doTestGetSuitableVideoSize(1280, 720, 1000, 640, 360));
        assertTrue(doTestGetSuitableVideoSize(1280, 720, 1100, 640, 360));
        assertTrue(doTestGetSuitableVideoSize(1280, 720, 1200, 640, 360));
        assertTrue(doTestGetSuitableVideoSize(1280, 720, 1500, 640, 360));
        assertTrue(doTestGetSuitableVideoSize(1280, 720, 1800, 960, 540));
        assertTrue(doTestGetSuitableVideoSize(1280, 720, 2000, 960, 540));

        // Small original size.
        assertTrue(doTestGetSuitableVideoSize(100, 100, 1000, 100, 100));
        assertTrue(doTestGetSuitableVideoSize(100, 1000, 1000, 100, 1000));
        assertTrue(doTestGetSuitableVideoSize(1000, 100, 100, 1000, 100));

        // Unknown original size.
        assertTrue(doTestGetSuitableVideoSize(720, null, 200, 400, 226));
        assertTrue(doTestGetSuitableVideoSize(null, 540, 300, 400, 226));
        assertTrue(doTestGetSuitableVideoSize(null, null, 400, 480, 270));
        assertTrue(doTestGetSuitableVideoSize(720, null, 500, 480, 270));
        assertTrue(doTestGetSuitableVideoSize(null, 540, 600, 640, 360));
        assertTrue(doTestGetSuitableVideoSize(null, null, 700, 640, 360));
        assertTrue(doTestGetSuitableVideoSize(720, null, 1200, 640, 360));
        assertTrue(doTestGetSuitableVideoSize(null, 540, 1500, 640, 360));
        assertTrue(doTestGetSuitableVideoSize(null, null, 2000, 960, 540));

        // Odd original size.
        assertTrue(doTestGetSuitableVideoSize(203, 101, 1500, 204, 102));
        assertTrue(doTestGetSuitableVideoSize(464, 853, 1500, 464, 854));
    }

    @Order(7)
    @Test
    void testCloseAllStreamFor() throws Exception {

        TransferStatus status1 = new TransferStatus();
        assertTrue(status1.isActive());
        assertFalse(status1.isTerminated());
        TransferStatus status2 = new TransferStatus();
        status2.setActive(false);
        assertFalse(status2.isActive());
        assertFalse(status2.isTerminated());
        List<TransferStatus> statuses = Arrays.asList(status1, status2);
        Player player = new Player();
        Mockito.when(statusService.getStreamStatusesForPlayer(player)).thenReturn(statuses);

        streamService.closeAllStreamFor(player, true, false); // Do nothing
        assertTrue(status1.isActive());
        assertFalse(status1.isTerminated());
        assertFalse(status2.isActive());
        assertFalse(status2.isTerminated());

        streamService.closeAllStreamFor(player, false, true); // Do nothing
        assertTrue(status1.isActive());
        assertFalse(status1.isTerminated());
        assertFalse(status2.isActive());
        assertFalse(status2.isTerminated());

        streamService.closeAllStreamFor(player, false, false);
        assertTrue(status1.isActive());
        assertTrue(status1.isTerminated());
        assertFalse(status2.isActive());
        assertFalse(status2.isTerminated());
    }

    @Order(8)
    @Test
    void testCreateInputStream() throws Exception {
        Player player = new Player();
        PlayQueue playQueue = new PlayQueue();
        MediaFile song = new MediaFile();
        song.setPathString("path");
        playQueue.addFiles(false, song);
        player.setPlayQueue(playQueue);
        try (InputStream inputStream = streamService
            .createInputStream(player, null, null, null, null)) {
            assertNotNull(inputStream);
            assertInstanceOf(PlayQueueInputStream.class, inputStream);
        }
    }

    @Order(9)
    @Test
    void testSendDummyDelayed() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] bytes = { 0, 0, 0 };
        streamService.sendDummyDelayed(bytes, baos);
        assertTrue(Arrays.equals(new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }, bytes));
        assertTrue(Arrays
            .equals(new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }, baos.toByteArray()));
    }

    @Order(10)
    @Test
    void testSendDummy() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] bytes = { 0, 0, 0 };
        streamService.sendDummy(bytes, baos, bytes.length);
        assertTrue(Arrays.equals(new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }, bytes));
        assertTrue(Arrays
            .equals(new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }, baos.toByteArray()));
    }

    @Order(11)
    @Test
    void testRemoveStreamStatus() throws Exception {
        User user = new User(ServiceMockUtils.ADMIN_NAME, ServiceMockUtils.ADMIN_NAME, null);
        TransferStatus status = new TransferStatus();
        status.setBytesTransfered(100);
        streamService.removeStreamStatus(user, null);
        Mockito
            .verify(userService, Mockito.never())
            .incrementUserByteCounts(Mockito.any(User.class), Mockito.anyLong(), Mockito.anyLong(),
                    Mockito.anyLong());
        Mockito
            .verify(statusService, Mockito.never())
            .removeStreamStatus(Mockito.any(TransferStatus.class));

        Mockito.clearInvocations(userService, statusService);
        streamService.removeStreamStatus(user, status);
        Mockito
            .verify(userService, Mockito.times(1))
            .incrementUserByteCounts(Mockito.any(User.class), Mockito.anyLong(), Mockito.anyLong(),
                    Mockito.anyLong());
        Mockito
            .verify(statusService, Mockito.times(1))
            .removeStreamStatus(Mockito.any(TransferStatus.class));
    }
}

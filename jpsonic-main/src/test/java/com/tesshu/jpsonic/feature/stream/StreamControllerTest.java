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

package com.tesshu.jpsonic.feature.stream;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Documented;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import ch.qos.logback.classic.Level;
import com.tesshu.jpsonic.TestCaseUtils;
import com.tesshu.jpsonic.controller.Attributes;
import com.tesshu.jpsonic.domain.model.TranscodingDefinition.BitRateLimit;
import com.tesshu.jpsonic.feature.auth.jwt.JWTAuthenticationToken;
import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.feature.upnp.UPnPSubnet;
import com.tesshu.jpsonic.infrastructure.core.DisabledOnWindowsJdk21OrEarlier;
import com.tesshu.jpsonic.infrastructure.core.NeedsHome;
import com.tesshu.jpsonic.infrastructure.core.NeedsTranscode;
import com.tesshu.jpsonic.infrastructure.scanner.WritableMediaFileService;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.persistence.api.entity.PlayQueue;
import com.tesshu.jpsonic.persistence.api.entity.PlayQueue.Status;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.persistence.api.entity.Transcoding;
import com.tesshu.jpsonic.persistence.api.repository.TranscodingDao;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.StatusService;
import com.tesshu.jpsonic.service.StatusService.TransferStatus;
import com.tesshu.jpsonic.service.TranscodingService;
import com.tesshu.jpsonic.service.TranscodingService.Parameters;
import com.tesshu.jpsonic.service.TranscodingService.VideoTranscodingSettings;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@NeedsHome
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings({ "PMD.UnitTestShouldIncludeAssert", "PMD.AvoidDuplicateLiterals",
        "PMD.TooManyStaticImports" })
@DisabledOnWindowsJdk21OrEarlier // Flaky Test on Windows Server
class StreamControllerTest {

    private static final String TEST_URL = "/stream/test";
    private static final String TEST_PATH = "/var/dummy";

    private SettingsFacade settingsFacade;
    private LibraryAccessPolicy accessPolicy;
    private UserService userService;
    private PlayerService playerService;
    private StreamService streamService;
    private StatusService statusService;
    private TranscodingService transcodingService;

    /**
     * This instance does not launch writeStream method.
     */
    private StreamController streamController;
    private MockMvc mockMvc;

    private void initMocks(Player player, TranscodingService ts, StreamService ss) {
        this.transcodingService = ts;
        this.streamService = ss;

        settingsFacade = SettingsFacadeBuilder.create().build();
        userService = mock(UserService.class);
        accessPolicy = mock(LibraryAccessPolicy.class);

        User user = new User(player.getUsername(), player.getUsername(), "");
        when(userService.getUserByName(player.getUsername())).thenReturn(user);
        playerService = mock(PlayerService.class);
        when(playerService.getPlayer(any(), any(), anyBoolean(), anyBoolean())).thenReturn(player);

        statusService = mock(StatusService.class);
        TransferStatus transferStatus = mock(TransferStatus.class);
        when(transferStatus.getPlayer()).thenReturn(player);
        when(transferStatus.isTerminated()).thenReturn(true);
        when(transferStatus.isActive()).thenReturn(true);
        when(statusService.createStreamStatus(nullable(Player.class))).thenReturn(transferStatus);
        when(statusService.getStreamStatusesForPlayer(nullable(Player.class)))
            .thenReturn(Arrays.asList(transferStatus));

        streamController = new StreamController(settingsFacade, accessPolicy, userService,
                playerService, ts, statusService, ss);

        JWTAuthenticationToken token = new JWTAuthenticationToken(Collections.emptyList(),
                ServiceMockUtils.ADMIN_NAME, null);
        SecurityContextHolder.getContext().setAuthentication(token);

        this.mockMvc = MockMvcBuilders.standaloneSetup(streamController).build();
    }

    @BeforeEach
    void setup() throws ExecutionException {
        Player player = new Player();
        player.setId(100);
        PlayQueue playQueue = new PlayQueue();
        playQueue.setStatus(Status.STOPPED);
        player.setPlayQueue(playQueue);
        player.setUsername(ServiceMockUtils.ADMIN_NAME);
        TranscodingService transcodingService = mock(TranscodingService.class);
        Parameters parameters = new TranscodingService.Parameters(null, null);
        when(transcodingService
            .getParameters(nullable(MediaFile.class), nullable(Player.class),
                    nullable(Integer.class), nullable(String.class),
                    nullable(VideoTranscodingSettings.class)))
            .thenReturn(parameters);
        StreamService streamService = mock(StreamService.class);
        initMocks(player, transcodingService, streamService);
        TestCaseUtils.setLogLevel(StreamController.class, Level.TRACE);
    }

    @AfterEach
    void tearDown() {
        TestCaseUtils.setLogLevel(StreamController.class, Level.WARN);
    }

    @Order(0)
    @Test
    void testSendForbidden() throws Exception {

        // no stream role
        User user = new User(ServiceMockUtils.ADMIN_NAME, ServiceMockUtils.ADMIN_NAME, "");
        user.setStreamRole(false);
        when(userService.getUserByName(ServiceMockUtils.ADMIN_NAME)).thenReturn(user);
        mockMvc
            .perform(MockMvcRequestBuilders.get(TEST_URL))
            .andExpect(MockMvcResultMatchers.status().isOk());

        // no-jwt
        SecurityContextHolder.getContext().setAuthentication(null);
        mockMvc
            .perform(MockMvcRequestBuilders.get(TEST_URL))
            .andExpect(MockMvcResultMatchers.status().is4xxClientError())
            .andExpect(MockMvcResultMatchers.status().isForbidden())
            .andExpect(
                    MockMvcResultMatchers.status().reason("Streaming is forbidden for user admin"));
        verify(streamService, never())
            .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));

        // no-jwt with stream role
        user.setStreamRole(true);
        when(userService.getUserByName(ServiceMockUtils.ADMIN_NAME)).thenReturn(user);
        mockMvc
            .perform(MockMvcRequestBuilders.get(TEST_URL))
            .andExpect(MockMvcResultMatchers.status().isOk());
        verify(streamService, never())
            .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));

        // Unusual : Logging only
        user.setStreamRole(false);
        HttpServletResponse response = mock(MockHttpServletResponse.class);
        doThrow(IOException.class).when(response).sendError(anyInt(), anyString());
        streamController.handleRequest(new MockHttpServletRequest(), response);
        assertEquals(0, response.getStatus());
        verify(streamService, never())
            .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));
    }

    @Order(1)
    @Test
    void testGetMaxBitRate() throws Exception {
        MediaFile song = new MediaFile();
        song.setPathString(TEST_PATH);
        when(streamService.getSingleFile(any(HttpServletRequest.class))).thenReturn(song);

        ArgumentCaptor<Integer> maxBitRateCaptor = ArgumentCaptor.forClass(Integer.class);

        Parameters parameters = new TranscodingService.Parameters(null, null);
        when(transcodingService
            .getParameters(nullable(MediaFile.class), nullable(Player.class),
                    maxBitRateCaptor.capture(), nullable(String.class),
                    nullable(VideoTranscodingSettings.class)))
            .thenReturn(parameters);

        // Null
        mockMvc
            .perform(MockMvcRequestBuilders.get(TEST_URL))
            .andExpect(MockMvcResultMatchers.status().isOk());
        verify(transcodingService, times(1))
            .getParameters(nullable(MediaFile.class), nullable(Player.class),
                    nullable(Integer.class), nullable(String.class),
                    nullable(VideoTranscodingSettings.class));
        assertNull(maxBitRateCaptor.getValue());
        verify(streamService, times(1))
            .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));

        // 0
        clearInvocations(streamService);
        clearInvocations(transcodingService);
        mockMvc
            .perform(MockMvcRequestBuilders
                .get(TEST_URL)
                .param(Attributes.Request.MAX_BIT_RATE.value(), "0"))
            .andExpect(MockMvcResultMatchers.status().isOk());
        verify(transcodingService, times(1))
            .getParameters(nullable(MediaFile.class), nullable(Player.class),
                    nullable(Integer.class), nullable(String.class),
                    nullable(VideoTranscodingSettings.class));
        assertNull(maxBitRateCaptor.getValue());
        verify(streamService, times(1))
            .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));

        // 123
        clearInvocations(streamService);
        clearInvocations(transcodingService);
        mockMvc
            .perform(MockMvcRequestBuilders
                .get(TEST_URL)
                .param(Attributes.Request.MAX_BIT_RATE.value(), "123"))
            .andExpect(MockMvcResultMatchers.status().isOk());
        verify(transcodingService, times(1))
            .getParameters(nullable(MediaFile.class), nullable(Player.class),
                    nullable(Integer.class), nullable(String.class),
                    nullable(VideoTranscodingSettings.class));
        assertEquals(123, maxBitRateCaptor.getValue());
        verify(streamService, times(1))
            .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));
    }

    @Order(2)
    @Nested
    class PrepareResponseTest {

        @Order(0)
        @Test
        void testAuthentication() throws Exception {
            MediaFile song = new MediaFile();
            song.setPathString(TEST_PATH);
            when(streamService.getSingleFile(any(HttpServletRequest.class))).thenReturn(song);

            // no-jwt with stream role(Pass the first certification check)
            SecurityContextHolder.getContext().setAuthentication(null);
            User user = new User(ServiceMockUtils.ADMIN_NAME, ServiceMockUtils.ADMIN_NAME, "");
            user.setStreamRole(true);
            when(userService.getUserByName(ServiceMockUtils.ADMIN_NAME)).thenReturn(user);

            // No folder access permission
            when(accessPolicy.isFolderAccessAllowed(any(MediaFile.class), any(String.class)))
                .thenReturn(false);
            mockMvc
                .perform(MockMvcRequestBuilders.get(TEST_URL))
                .andExpect(MockMvcResultMatchers.status().is4xxClientError())
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers
                    .status()
                    .reason("Access to file 0 is forbidden for user admin"));
            verify(streamService, never())
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));

            // With folder access permission
            when(accessPolicy.isFolderAccessAllowed(any(MediaFile.class), any(String.class)))
                .thenReturn(true);
            mockMvc
                .perform(MockMvcRequestBuilders.get(TEST_URL))
                .andExpect(MockMvcResultMatchers.status().isOk());
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));
        }

        @Order(1)
        @Test
        void testVideoTranscoding() throws Exception {
            MediaFile song = new MediaFile();
            song.setPathString(TEST_PATH);
            when(streamService.getSingleFile(any(HttpServletRequest.class))).thenReturn(song);

            ArgumentCaptor<VideoTranscodingSettings> vtsCaptor = ArgumentCaptor
                .forClass(VideoTranscodingSettings.class);
            when(streamService
                .createInputStream(nullable(Player.class), nullable(TransferStatus.class),
                        nullable(Integer.class), nullable(String.class), vtsCaptor.capture()))
                .thenReturn(IOUtils.toInputStream("test", Charset.defaultCharset()));

            // not video
            song.setMediaType(MediaType.MUSIC);
            mockMvc
                .perform(MockMvcRequestBuilders.get(TEST_URL))
                .andExpect(MockMvcResultMatchers.status().isOk());
            verify(streamService, times(1))
                .createInputStream(nullable(Player.class), nullable(TransferStatus.class),
                        nullable(Integer.class), nullable(String.class),
                        nullable(VideoTranscodingSettings.class));
            verify(streamService, never())
                .createVideoTranscodingSettings(nullable(MediaFile.class),
                        nullable(HttpServletRequest.class));
            assertNull(vtsCaptor.getValue());
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));

            // video
            clearInvocations(streamService);
            song.setMediaType(MediaType.VIDEO);
            mockMvc
                .perform(MockMvcRequestBuilders.get(TEST_URL))
                .andExpect(MockMvcResultMatchers.status().isOk());
            verify(streamService, times(1))
                .createInputStream(nullable(Player.class), nullable(TransferStatus.class),
                        nullable(Integer.class), nullable(String.class),
                        nullable(VideoTranscodingSettings.class));
            verify(streamService, times(1))
                .createVideoTranscodingSettings(nullable(MediaFile.class),
                        nullable(HttpServletRequest.class));
            assertNull(vtsCaptor.getValue());
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));

            // hls
            clearInvocations(streamService);
            song.setMediaType(MediaType.MUSIC);
            mockMvc
                .perform(MockMvcRequestBuilders
                    .get(TEST_URL)
                    .param(Attributes.Request.HLS.value(), "true"))
                .andExpect(MockMvcResultMatchers.status().isOk());
            verify(streamService, times(1))
                .createInputStream(nullable(Player.class), nullable(TransferStatus.class),
                        nullable(Integer.class), nullable(String.class),
                        nullable(VideoTranscodingSettings.class));
            verify(streamService, times(1))
                .createVideoTranscodingSettings(nullable(MediaFile.class),
                        nullable(HttpServletRequest.class));
            assertNull(vtsCaptor.getValue());
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));
        }
    }

    @Documented
    private @interface ApplyRangeDecision {
        @interface Conditions {

            // @see TranscodingService#getParameters
            // @see TranscodingService#isRangeAllowed
            @interface IsRangeNotAllowed {
                @interface False {
                }

                @interface True {
                }
            }

            @interface MediaType {
                @interface Video {
                }

                @interface NotVideo {
                }
            }

            @interface CreateRange {
                @interface RequestHeader {
                    @interface Range {
                        @interface Null {
                        }

                        @interface NotNull {
                        }
                    }
                }

                @interface RequestParam {
                    @interface OffsetSeconds {
                        @interface Null {
                        }

                        @interface NotNull {
                        }

                        @interface Invalid {
                        }

                    }
                }

                @interface MediaFile {
                    @interface WithoutDurationOrSize {
                    }
                }

                @interface Result {
                    @interface Null {
                    }

                    @interface NotNull {
                    }
                }
            }
        }

        @interface Result {
            @interface Status {
                @interface Ok200 {
                }

                @interface Partial206 {
                }
            }

            @interface Header {
                @interface AcceptRanges {
                    @interface None {
                    }

                    @interface NotExist {
                    }

                    @interface Bytes {
                    }
                }
            }

        }
    }

    @Order(3)
    @Nested
    class ApplyRangeTest {

        @ApplyRangeDecision.Conditions.IsRangeNotAllowed.False
        @ApplyRangeDecision.Result.Status.Ok200
        @ApplyRangeDecision.Result.Header.AcceptRanges.None
        @Test
        void na00() throws Exception {
            MediaFile song = new MediaFile();
            song.setPathString(TEST_PATH);
            song.setMediaType(MediaType.MUSIC);
            when(streamService.getSingleFile(any(HttpServletRequest.class))).thenReturn(song);
            mockMvc
                .perform(MockMvcRequestBuilders.get(TEST_URL))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.ACCEPT_RANGES, "none"))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_LENGTH));
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));
        }

        @ApplyRangeDecision.Conditions.MediaType.Video
        @ApplyRangeDecision.Result.Status.Ok200
        @ApplyRangeDecision.Result.Header.AcceptRanges.None
        @Test
        void na01() throws Exception {
            MediaFile song = new MediaFile();
            song.setPathString(TEST_PATH);
            song.setMediaType(MediaType.VIDEO);
            when(streamService.getSingleFile(any(HttpServletRequest.class))).thenReturn(song);
            mockMvc
                .perform(MockMvcRequestBuilders.get(TEST_URL))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.ACCEPT_RANGES, "none"))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_LENGTH));
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));
        }

        @ApplyRangeDecision.Conditions.MediaType.NotVideo
        @ApplyRangeDecision.Conditions.IsRangeNotAllowed.True
        @ApplyRangeDecision.Conditions.CreateRange.RequestHeader.Range.Null
        @ApplyRangeDecision.Conditions.CreateRange.RequestParam.OffsetSeconds.Null
        @ApplyRangeDecision.Conditions.CreateRange.Result.Null
        @ApplyRangeDecision.Result.Status.Ok200
        @ApplyRangeDecision.Result.Header.AcceptRanges.NotExist
        @Test
        void cr00() throws Exception {
            MediaFile song = new MediaFile();
            song.setPathString(TEST_PATH);
            song.setMediaType(MediaType.MUSIC);
            song.setFileSize(3_200L);
            when(streamService.getSingleFile(any(HttpServletRequest.class))).thenReturn(song);

            Parameters parameters = new TranscodingService.Parameters(song, null);
            parameters.setMaxBitRate(320);
            parameters.setRangeAllowed(true);
            parameters.setExpectedLength(song.getFileSize());
            when(transcodingService
                .getParameters(nullable(MediaFile.class), nullable(Player.class),
                        nullable(Integer.class), nullable(String.class),
                        nullable(VideoTranscodingSettings.class)))
                .thenReturn(parameters);

            mockMvc
                .perform(MockMvcRequestBuilders.get(TEST_URL))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.ACCEPT_RANGES))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(
                        MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_LENGTH, "3200"));
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));

            song.setFileSize(Long.valueOf(Integer.MAX_VALUE + 1));
            parameters.setExpectedLength(song.getFileSize());
            clearInvocations(streamService);
            mockMvc
                .perform(MockMvcRequestBuilders.get(TEST_URL))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.ACCEPT_RANGES))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(MockMvcResultMatchers
                    .header()
                    .string(HttpHeaders.CONTENT_LENGTH, "-2147483648"));
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));
        }

        @ApplyRangeDecision.Conditions.MediaType.NotVideo
        @ApplyRangeDecision.Conditions.IsRangeNotAllowed.True
        @ApplyRangeDecision.Conditions.CreateRange.RequestHeader.Range.NotNull
        @ApplyRangeDecision.Conditions.CreateRange.Result.NotNull
        @ApplyRangeDecision.Result.Status.Partial206
        @ApplyRangeDecision.Result.Header.AcceptRanges.Bytes
        @Test
        void cr01() throws Exception {
            MediaFile song = new MediaFile();
            song.setPathString(TEST_PATH);
            song.setMediaType(MediaType.MUSIC);
            song.setDurationSeconds(10);
            song.setFileSize(3_200L);
            when(streamService.getSingleFile(any(HttpServletRequest.class))).thenReturn(song);

            Parameters parameters = new TranscodingService.Parameters(song, null);
            parameters.setMaxBitRate(320);
            parameters.setRangeAllowed(true);
            parameters.setExpectedLength(song.getFileSize());
            when(transcodingService
                .getParameters(nullable(MediaFile.class), nullable(Player.class),
                        nullable(Integer.class), nullable(String.class),
                        nullable(VideoTranscodingSettings.class)))
                .thenReturn(parameters);

            mockMvc
                .perform(MockMvcRequestBuilders.get(TEST_URL).header("Range", "bytes=320-639"))
                .andExpect(MockMvcResultMatchers.status().isPartialContent())
                .andExpect(
                        MockMvcResultMatchers.header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(MockMvcResultMatchers
                    .header()
                    .string(HttpHeaders.CONTENT_RANGE, "bytes 320-639/3200"))
                .andExpect(
                        MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_LENGTH, "320"));
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));

            clearInvocations(streamService);
            mockMvc
                .perform(MockMvcRequestBuilders.get(TEST_URL).header("Range", "bytes=320-"))
                .andExpect(MockMvcResultMatchers.status().isPartialContent())
                .andExpect(
                        MockMvcResultMatchers.header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(MockMvcResultMatchers
                    .header()
                    .string(HttpHeaders.CONTENT_RANGE, "bytes 320-3199/3200"))
                .andExpect(
                        MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_LENGTH, "2880"));
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));
        }

        @ApplyRangeDecision.Conditions.MediaType.NotVideo
        @ApplyRangeDecision.Conditions.IsRangeNotAllowed.True
        @ApplyRangeDecision.Conditions.CreateRange.RequestHeader.Range.Null
        @ApplyRangeDecision.Conditions.CreateRange.RequestParam.OffsetSeconds.NotNull
        @ApplyRangeDecision.Conditions.CreateRange.Result.NotNull
        @ApplyRangeDecision.Result.Status.Partial206
        @ApplyRangeDecision.Result.Header.AcceptRanges.Bytes
        @Test
        void cr02() throws Exception {
            MediaFile song = new MediaFile();
            song.setPathString(TEST_PATH);
            song.setMediaType(MediaType.MUSIC);
            song.setDurationSeconds(10);
            song.setFileSize(3_300L);
            when(streamService.getSingleFile(any(HttpServletRequest.class))).thenReturn(song);

            Parameters parameters = new TranscodingService.Parameters(song, null);
            parameters.setMaxBitRate(320);
            parameters.setRangeAllowed(true);
            parameters.setExpectedLength(song.getFileSize());
            when(transcodingService
                .getParameters(nullable(MediaFile.class), nullable(Player.class),
                        nullable(Integer.class), nullable(String.class),
                        nullable(VideoTranscodingSettings.class)))
                .thenReturn(parameters);

            mockMvc
                .perform(MockMvcRequestBuilders
                    .get(TEST_URL)
                    .param(Attributes.Request.OFFSET_SECONDS.value(), "1"))
                .andExpect(MockMvcResultMatchers.status().isPartialContent())
                .andExpect(
                        MockMvcResultMatchers.header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(MockMvcResultMatchers
                    .header()
                    .string(HttpHeaders.CONTENT_RANGE, "bytes 330-3299/3300"))
                .andExpect(
                        MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_LENGTH, "2970"));
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));
        }

        @ApplyRangeDecision.Conditions.MediaType.NotVideo
        @ApplyRangeDecision.Conditions.IsRangeNotAllowed.True
        @ApplyRangeDecision.Conditions.CreateRange.RequestHeader.Range.Null
        @ApplyRangeDecision.Conditions.CreateRange.RequestParam.OffsetSeconds.Invalid
        @ApplyRangeDecision.Conditions.CreateRange.Result.NotNull
        @ApplyRangeDecision.Result.Status.Ok200
        @ApplyRangeDecision.Result.Header.AcceptRanges.NotExist
        @Test
        void cr03() throws Exception {
            MediaFile song = new MediaFile();
            song.setPathString(TEST_PATH);
            song.setMediaType(MediaType.MUSIC);
            song.setDurationSeconds(10);
            song.setFileSize(3_300L);
            when(streamService.getSingleFile(any(HttpServletRequest.class))).thenReturn(song);

            Parameters parameters = new TranscodingService.Parameters(song, null);
            parameters.setMaxBitRate(320);
            parameters.setRangeAllowed(true);
            parameters.setExpectedLength(song.getFileSize());
            when(transcodingService
                .getParameters(nullable(MediaFile.class), nullable(Player.class),
                        nullable(Integer.class), nullable(String.class),
                        nullable(VideoTranscodingSettings.class)))
                .thenReturn(parameters);

            mockMvc
                .perform(MockMvcRequestBuilders
                    .get(TEST_URL)
                    .param(Attributes.Request.OFFSET_SECONDS.value(), "Invalid offset test!"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.ACCEPT_RANGES))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(
                        MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_LENGTH, "3300"));
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));
        }

        @ApplyRangeDecision.Conditions.MediaType.NotVideo
        @ApplyRangeDecision.Conditions.IsRangeNotAllowed.True
        @ApplyRangeDecision.Conditions.CreateRange.RequestHeader.Range.Null
        @ApplyRangeDecision.Conditions.CreateRange.RequestParam.OffsetSeconds.NotNull
        @ApplyRangeDecision.Conditions.CreateRange.MediaFile.WithoutDurationOrSize
        @ApplyRangeDecision.Result.Status.Ok200
        @ApplyRangeDecision.Result.Header.AcceptRanges.NotExist
        @Test
        void cr04() throws Exception {
            MediaFile song = new MediaFile();
            song.setPathString(TEST_PATH);
            song.setMediaType(MediaType.MUSIC);
            song.setDurationSeconds(null);
            song.setFileSize(3_300L);
            when(streamService.getSingleFile(any(HttpServletRequest.class))).thenReturn(song);

            Parameters parameters = new TranscodingService.Parameters(song, null);
            parameters.setMaxBitRate(320);
            parameters.setRangeAllowed(true);
            parameters.setExpectedLength(song.getFileSize());
            when(transcodingService
                .getParameters(nullable(MediaFile.class), nullable(Player.class),
                        nullable(Integer.class), nullable(String.class),
                        nullable(VideoTranscodingSettings.class)))
                .thenReturn(parameters);

            mockMvc
                .perform(MockMvcRequestBuilders
                    .get(TEST_URL)
                    .param(Attributes.Request.OFFSET_SECONDS.value(), "1"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.ACCEPT_RANGES))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(
                        MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_LENGTH, "3300"));
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));

            song.setDurationSeconds(10);
            song.setFileSize(null); // Assumed unreachble code
            parameters.setExpectedLength(song.getFileSize());
            clearInvocations(streamService);
            assertThrows(ServletException.class,
                    () -> mockMvc
                        .perform(MockMvcRequestBuilders
                            .get(TEST_URL)
                            .param(Attributes.Request.OFFSET_SECONDS.value(), "1")));
            verify(streamService, never())
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));
        }
    }

    @Documented
    private @interface HeaderDecision {
        @interface Conditions {
            @interface User {
                @interface NotAnonymous {
                }

                @interface Anonymous {
                }
            }

            @interface MediaFile {
                @interface File {
                    @interface Flac {
                    }
                }

                @interface BitRate955 {
                }
            }

            @interface Player {
                @interface ValidTranscoding {
                    @interface Exist {
                    }

                    @interface NotExist {
                    }
                }

                @interface BitRateLimit {
                    @interface OFF {
                    }

                    @interface MaxBitRate320 {
                    }
                }
            }

            @interface Param {
                @interface MaxBitRate320 {
                }

                @interface FormatMp3 {
                }
            }

            @interface SettingService {
                @interface PreferredFormat {
                    @interface Null {
                    }

                    @interface Mp3 {
                    }
                }
            }
        }

        @interface Result {
            @interface ContentType {
                @interface AudioFlac {
                }

                @interface AudioMpeg {
                }
            }
        }
    }

    @Order(4)
    @Nested
    @NeedsTranscode
    class ContentTypeAndDurationTest {

        private MediaFile song;
        private Player player;

        private void initMocksWithTranscoding(boolean isSetTranscodingsAll, boolean isAnonymous)
                throws URISyntaxException {
            song = new MediaFile();
            song.setId(0);
            song
                .setPathString(Path
                    .of(StreamControllerTest.class
                        .getResource(
                                "/MEDIAS/Music/_DIR_ Céline Frisch- Café Zimmermann - Bach- Goldberg Variations, Canons [Disc 1]/01 - Bach- Goldberg Variations, BWV 988 - Aria.flac")
                        .toURI())
                    .toString());
            song.setMediaType(MediaType.MUSIC);
            song.setFormat("flac");
            song.setFileSize(358_406L);
            song.setBitRate(955);
            song.setDurationSeconds(3);
            MediaFileService mediaFileService = mock(MediaFileService.class);
            when(mediaFileService.getMediaFile(song.getId())).thenReturn(song);

            player = new Player();
            player.setId(101);
            player.setBitRateLimit(BitRateLimit.OFF);
            PlayQueue playQueue = new PlayQueue();
            playQueue.setStatus(Status.STOPPED);
            playQueue.addFiles(false, song);
            player.setPlayQueue(playQueue);
            player
                .setUsername(isAnonymous ? JWTAuthenticationToken.USERNAME_ANONYMOUS
                        : ServiceMockUtils.ADMIN_NAME);
            UPnPSubnet subnet = mock(UPnPSubnet.class);
            if (isAnonymous) {
                User user = new User(JWTAuthenticationToken.USERNAME_ANONYMOUS,
                        JWTAuthenticationToken.USERNAME_ANONYMOUS, "");
                when(userService.getUserByName(JWTAuthenticationToken.USERNAME_ANONYMOUS))
                    .thenReturn(user);
                when(subnet.isInUPnPRange(nullable(String.class))).thenReturn(true);
                when(playerService.getGuestPlayer(nullable(HttpServletRequest.class)))
                    .thenReturn(player);
            }

            TranscodingDao transcodingDao = mock(TranscodingDao.class);
            List<Transcoding> allTranscodings = isSetTranscodingsAll
                    ? transcodingDao.getAllTranscodings()
                    : Collections.emptyList();
            when(transcodingDao.getTranscodingsForPlayer(anyInt())).thenReturn(allTranscodings);

            TranscodingService ts = new TranscodingService(settingsFacade, userService, subnet,
                    transcodingDao, playerService, null);
            StreamService ss = new StreamService(statusService, null, userService, settingsFacade,
                    ts, null, mediaFileService, mock(WritableMediaFileService.class), null, null);
            initMocks(player, ts, ss);
        }

        @HeaderDecision.Conditions.User.NotAnonymous
        @HeaderDecision.Conditions.MediaFile.File.Flac
        @HeaderDecision.Conditions.MediaFile.BitRate955
        @HeaderDecision.Conditions.Player.ValidTranscoding.NotExist
        @HeaderDecision.Conditions.Player.BitRateLimit.OFF
        @HeaderDecision.Result.ContentType.AudioFlac
        @Test
        void c1() throws Exception {
            initMocksWithTranscoding(false, false);
            mockMvc
                .perform(MockMvcRequestBuilders
                    .get("/stream")
                    .param(Attributes.Request.ID.value(), Integer.toString(song.getId())))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(
                        MockMvcResultMatchers.header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.ACCEPT_RANGES))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(
                        MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_LENGTH, "358406"))
                .andExpect(MockMvcResultMatchers.header().string("Content-Type", "audio/flac"))
                .andExpect(MockMvcResultMatchers.header().string("X-Content-Duration", "3.0"));
        }

        @HeaderDecision.Conditions.User.NotAnonymous
        @HeaderDecision.Conditions.MediaFile.File.Flac
        @HeaderDecision.Conditions.MediaFile.BitRate955
        @HeaderDecision.Conditions.Player.ValidTranscoding.Exist
        @HeaderDecision.Conditions.Player.BitRateLimit.OFF
        @HeaderDecision.Conditions.Param.MaxBitRate320
        @HeaderDecision.Result.ContentType.AudioMpeg
        @Test
        void c2() throws Exception {
            initMocksWithTranscoding(true, false);
            mockMvc
                .perform(MockMvcRequestBuilders
                    .get("/stream")
                    .param(Attributes.Request.ID.value(), Integer.toString(song.getId()))
                    .param(Attributes.Request.MAX_BIT_RATE.value(),
                            Integer.toString(BitRateLimit.MAX_320.getMaxBitRate())))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(
                        MockMvcResultMatchers.header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.ACCEPT_RANGES, "none"))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_LENGTH))
                .andExpect(MockMvcResultMatchers.header().string("Content-Type", "audio/mpeg"))
                .andExpect(MockMvcResultMatchers.header().string("X-Content-Duration", "3.0"));
        }

        @HeaderDecision.Conditions.User.NotAnonymous
        @HeaderDecision.Conditions.MediaFile.File.Flac
        @HeaderDecision.Conditions.MediaFile.BitRate955
        @HeaderDecision.Conditions.Player.ValidTranscoding.Exist
        @HeaderDecision.Conditions.Player.BitRateLimit.MaxBitRate320
        @HeaderDecision.Result.ContentType.AudioMpeg
        @Test
        void c3() throws Exception {
            initMocksWithTranscoding(true, false);
            player.setBitRateLimit(BitRateLimit.MAX_320);
            mockMvc
                .perform(MockMvcRequestBuilders
                    .get("/stream")
                    .param(Attributes.Request.ID.value(), Integer.toString(song.getId())))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(
                        MockMvcResultMatchers.header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.ACCEPT_RANGES, "none"))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_LENGTH))
                .andExpect(MockMvcResultMatchers.header().string("Content-Type", "audio/mpeg"))
                .andExpect(MockMvcResultMatchers.header().string("X-Content-Duration", "3.0"));
        }

        @HeaderDecision.Conditions.User.NotAnonymous
        @HeaderDecision.Conditions.MediaFile.File.Flac
        @HeaderDecision.Conditions.MediaFile.BitRate955
        @HeaderDecision.Conditions.Player.ValidTranscoding.Exist
        @HeaderDecision.Conditions.Player.BitRateLimit.OFF
        @HeaderDecision.Conditions.Param.FormatMp3
        @HeaderDecision.Result.ContentType.AudioMpeg
        @Test
        void c4() throws Exception {
            initMocksWithTranscoding(true, false);
            mockMvc
                .perform(MockMvcRequestBuilders
                    .get("/stream")
                    .param(Attributes.Request.ID.value(), Integer.toString(song.getId()))
                    .param("format", "mp3"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(
                        MockMvcResultMatchers.header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.ACCEPT_RANGES, "none"))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_LENGTH))
                .andExpect(MockMvcResultMatchers.header().string("Content-Type", "audio/mpeg"))
                .andExpect(MockMvcResultMatchers.header().string("X-Content-Duration", "3.0"));
        }

        @HeaderDecision.Conditions.User.NotAnonymous
        @HeaderDecision.Conditions.MediaFile.File.Flac
        @HeaderDecision.Conditions.MediaFile.BitRate955
        @HeaderDecision.Conditions.Player.ValidTranscoding.Exist
        @HeaderDecision.Conditions.Player.BitRateLimit.OFF
        @HeaderDecision.Conditions.SettingService.PreferredFormat.Null
        @HeaderDecision.Result.ContentType.AudioFlac
        @Test
        void c5() throws Exception {
            initMocksWithTranscoding(true, false);
            mockMvc
                .perform(MockMvcRequestBuilders
                    .get("/stream")
                    .param(Attributes.Request.ID.value(), Integer.toString(song.getId())))
                .andExpect(
                        MockMvcResultMatchers.header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.ACCEPT_RANGES))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(
                        MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_LENGTH, "358406"))
                .andExpect(MockMvcResultMatchers.header().string("Content-Type", "audio/flac"))
                .andExpect(MockMvcResultMatchers.header().string("X-Content-Duration", "3.0"));
        }

        @HeaderDecision.Conditions.User.Anonymous
        @HeaderDecision.Conditions.MediaFile.File.Flac
        @HeaderDecision.Conditions.MediaFile.BitRate955
        @HeaderDecision.Conditions.Player.ValidTranscoding.Exist
        @HeaderDecision.Conditions.Player.BitRateLimit.OFF
        @HeaderDecision.Conditions.SettingService.PreferredFormat.Mp3
        @HeaderDecision.Result.ContentType.AudioMpeg
        @Test
        void c6() throws Exception {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.transcoding.preferredFormat, "mp3")
                .build();
            initMocksWithTranscoding(true, false);
            mockMvc
                .perform(MockMvcRequestBuilders
                    .get("/stream")
                    .param(Attributes.Request.ID.value(), Integer.toString(song.getId())))
                .andExpect(
                        MockMvcResultMatchers.header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.ACCEPT_RANGES))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(
                        MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_LENGTH, "358406"))
                .andExpect(MockMvcResultMatchers.header().string("Content-Type", "audio/flac"))
                .andExpect(MockMvcResultMatchers.header().string("X-Content-Duration", "3.0"));
        }

        @HeaderDecision.Conditions.User.Anonymous
        @HeaderDecision.Conditions.MediaFile.File.Flac
        @HeaderDecision.Conditions.MediaFile.BitRate955
        @HeaderDecision.Conditions.Player.ValidTranscoding.NotExist
        @HeaderDecision.Conditions.Player.BitRateLimit.OFF
        @HeaderDecision.Result.ContentType.AudioFlac
        @Test
        void c1a() throws Exception {
            initMocksWithTranscoding(false, true);
            mockMvc
                .perform(MockMvcRequestBuilders
                    .get("/stream")
                    .param(Attributes.Request.ID.value(), Integer.toString(song.getId())))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(
                        MockMvcResultMatchers.header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.ACCEPT_RANGES))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(
                        MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_LENGTH, "358406"))
                .andExpect(MockMvcResultMatchers.header().string("Content-Type", "audio/flac"))
                .andExpect(MockMvcResultMatchers.header().string("X-Content-Duration", "3.0"));
        }

        @HeaderDecision.Conditions.User.Anonymous
        @HeaderDecision.Conditions.MediaFile.File.Flac
        @HeaderDecision.Conditions.MediaFile.BitRate955
        @HeaderDecision.Conditions.Player.ValidTranscoding.Exist
        @HeaderDecision.Conditions.Player.BitRateLimit.OFF
        @HeaderDecision.Conditions.Param.MaxBitRate320
        @HeaderDecision.Result.ContentType.AudioMpeg
        @Test
        void c2a() throws Exception {
            initMocksWithTranscoding(true, true);
            mockMvc
                .perform(MockMvcRequestBuilders
                    .get("/stream")
                    .param(Attributes.Request.ID.value(), Integer.toString(song.getId()))
                    .param(Attributes.Request.MAX_BIT_RATE.value(),
                            Integer.toString(BitRateLimit.MAX_320.getMaxBitRate())))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(
                        MockMvcResultMatchers.header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.ACCEPT_RANGES, "none"))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_LENGTH))
                .andExpect(MockMvcResultMatchers.header().string("Content-Type", "audio/mpeg"))
                .andExpect(MockMvcResultMatchers.header().string("X-Content-Duration", "3.0"));
        }

        @HeaderDecision.Conditions.User.Anonymous
        @HeaderDecision.Conditions.MediaFile.File.Flac
        @HeaderDecision.Conditions.MediaFile.BitRate955
        @HeaderDecision.Conditions.Player.ValidTranscoding.Exist
        @HeaderDecision.Conditions.Player.BitRateLimit.MaxBitRate320
        @HeaderDecision.Result.ContentType.AudioMpeg
        @Test
        void c3a() throws Exception {
            initMocksWithTranscoding(true, true);
            player.setBitRateLimit(BitRateLimit.MAX_320);
            mockMvc
                .perform(MockMvcRequestBuilders
                    .get("/stream")
                    .param(Attributes.Request.ID.value(), Integer.toString(song.getId())))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(
                        MockMvcResultMatchers.header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.ACCEPT_RANGES, "none"))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_LENGTH))
                .andExpect(MockMvcResultMatchers.header().string("Content-Type", "audio/mpeg"))
                .andExpect(MockMvcResultMatchers.header().string("X-Content-Duration", "3.0"));
        }

        @HeaderDecision.Conditions.User.Anonymous
        @HeaderDecision.Conditions.MediaFile.File.Flac
        @HeaderDecision.Conditions.MediaFile.BitRate955
        @HeaderDecision.Conditions.Player.ValidTranscoding.Exist
        @HeaderDecision.Conditions.Player.BitRateLimit.OFF
        @HeaderDecision.Conditions.Param.FormatMp3
        @HeaderDecision.Result.ContentType.AudioMpeg
        @Test
        void c4a() throws Exception {
            initMocksWithTranscoding(true, true);
            mockMvc
                .perform(MockMvcRequestBuilders
                    .get("/stream")
                    .param(Attributes.Request.ID.value(), Integer.toString(song.getId()))
                    .param("format", "mp3"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(
                        MockMvcResultMatchers.header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.ACCEPT_RANGES, "none"))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_LENGTH))
                .andExpect(MockMvcResultMatchers.header().string("Content-Type", "audio/mpeg"))
                .andExpect(MockMvcResultMatchers.header().string("X-Content-Duration", "3.0"));
        }

        @HeaderDecision.Conditions.User.Anonymous
        @HeaderDecision.Conditions.MediaFile.File.Flac
        @HeaderDecision.Conditions.MediaFile.BitRate955
        @HeaderDecision.Conditions.Player.ValidTranscoding.Exist
        @HeaderDecision.Conditions.Player.BitRateLimit.OFF
        @HeaderDecision.Conditions.SettingService.PreferredFormat.Null
        @HeaderDecision.Result.ContentType.AudioFlac
        @Test
        void c5a() throws Exception {
            initMocksWithTranscoding(true, true);
            mockMvc
                .perform(MockMvcRequestBuilders
                    .get("/stream")
                    .param(Attributes.Request.ID.value(), Integer.toString(song.getId())))
                .andExpect(
                        MockMvcResultMatchers.header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.ACCEPT_RANGES))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(
                        MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_LENGTH, "358406"))
                .andExpect(MockMvcResultMatchers.header().string("Content-Type", "audio/flac"))
                .andExpect(MockMvcResultMatchers.header().string("X-Content-Duration", "3.0"));
        }

        @HeaderDecision.Conditions.User.Anonymous
        @HeaderDecision.Conditions.MediaFile.File.Flac
        @HeaderDecision.Conditions.MediaFile.BitRate955
        @HeaderDecision.Conditions.Player.ValidTranscoding.Exist
        @HeaderDecision.Conditions.Player.BitRateLimit.OFF
        @HeaderDecision.Conditions.SettingService.PreferredFormat.Mp3
        @HeaderDecision.Result.ContentType.AudioMpeg
        @Test
        void c6a() throws Exception {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withString(SKeys.transcoding.preferredFormat, "mp3")
                .build();
            initMocksWithTranscoding(true, true);
            mockMvc
                .perform(MockMvcRequestBuilders
                    .get("/stream")
                    .param(Attributes.Request.ID.value(), Integer.toString(song.getId())))
                .andExpect(
                        MockMvcResultMatchers.header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.ACCEPT_RANGES, "none"))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.CONTENT_LENGTH))
                .andExpect(MockMvcResultMatchers.header().string("Content-Type", "audio/mpeg"))
                .andExpect(MockMvcResultMatchers.header().string("X-Content-Duration", "3.0"));
        }

        @Test
        void testHls() throws Exception {
            MediaFile song = new MediaFile();
            song.setPathString(TEST_PATH);
            when(streamService.getSingleFile(any(HttpServletRequest.class))).thenReturn(song);

            // hls
            clearInvocations(streamService);
            song.setMediaType(MediaType.MUSIC);
            mockMvc
                .perform(MockMvcRequestBuilders
                    .get(TEST_URL)
                    .param(Attributes.Request.HLS.value(), "true"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().string("Content-Type", "video/mp2t"));
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));

            // not hls and null duration (Assumed unreachble code)
            song.setMediaType(MediaType.MUSIC);
            clearInvocations(streamService);
            mockMvc
                .perform(MockMvcRequestBuilders.get(TEST_URL))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers
                    .header()
                    .string("Content-Type", "application/octet-stream"))
                .andExpect(MockMvcResultMatchers.header().doesNotExist("X-Content-Duration"));
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));

            // not hls and duration
            song.setMediaType(MediaType.MUSIC);
            song.setDurationSeconds(10);
            clearInvocations(streamService);
            mockMvc
                .perform(MockMvcRequestBuilders.get(TEST_URL))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers
                    .header()
                    .string("Content-Type", "application/octet-stream"))
                .andExpect(MockMvcResultMatchers.header().string("X-Content-Duration", "10.0"));
            verify(streamService, times(1))
                .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));
        }
    }

    @Order(5)
    @Test
    void testWriteVerboseLog() throws Exception {
        MediaFile song = new MediaFile();
        song.setPathString(TEST_PATH);
        song.setDurationSeconds(10);
        song.setFileSize(3_300L);
        when(streamService.getSingleFile(any(HttpServletRequest.class))).thenReturn(song);

        mockMvc
            .perform(MockMvcRequestBuilders.head(TEST_URL))
            .andExpect(MockMvcResultMatchers.status().isOk());
        verify(streamService, never())
            .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));

        mockMvc
            .perform(MockMvcRequestBuilders.get(TEST_URL))
            .andExpect(MockMvcResultMatchers.status().isOk());
        verify(streamService, times(1))
            .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));
    }

    @Documented
    private @interface WriteStreamDecision {
        @interface Conditions {
            @interface IsAliveStream {
                @interface False {
                }

                @interface True {
                }
            }

            @interface IsPodcast {
                @interface False {
                }

                @interface True {
                }
            }

            @interface IsSingleFile {
                @interface False {
                }

                @interface True {
                }
            }

            @interface CheckRequired {
                @interface False {
                }

                @interface True {
                }
            }

            @interface PlayQueueStatus {
                @interface Stopped {
                }

                @interface Playing {
                }
            }

        }

        @interface Result {
            @interface Outsize0 {
            }

            @interface Outsize8192 {
            }
        }
    }

    @Order(6)
    @Nested
    class WriteStreamTest {

        @WriteStreamDecision.Conditions.IsAliveStream.False
        @WriteStreamDecision.Conditions.CheckRequired.False
        @WriteStreamDecision.Result.Outsize0
        @Test
        void c00() throws Exception {
            String dummy = "a".repeat(8192);
            InputStream in = new ByteArrayInputStream(dummy.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            streamController.writeStream(null, in, out, null, false, false);
            assertEquals(0, out.size());
        }

        @WriteStreamDecision.Conditions.IsAliveStream.True
        @WriteStreamDecision.Conditions.IsPodcast.False
        @WriteStreamDecision.Conditions.IsSingleFile.True
        @WriteStreamDecision.Conditions.CheckRequired.True
        @WriteStreamDecision.Conditions.PlayQueueStatus.Stopped
        @WriteStreamDecision.Result.Outsize0
        @Test
        void c01() throws Exception {
            Player player = new Player();
            player.setId(100);
            TransferStatus transferStatus = new TransferStatus();
            transferStatus.setActive(true);
            when(statusService.getStreamStatusesForPlayer(player))
                .thenReturn(Arrays.asList(transferStatus));

            PlayQueue playQueue = new PlayQueue();
            playQueue.setStatus(Status.STOPPED);
            player.setPlayQueue(playQueue);
            player.setUsername(ServiceMockUtils.ADMIN_NAME);

            String dummy = "a".repeat(8192);
            InputStream in = new ByteArrayInputStream(dummy.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            streamController.writeStream(player, in, out, null, false, true);
            assertEquals(0, out.size());
            verify(streamService, never())
                .sendDummyDelayed(any(byte[].class), any(OutputStream.class));
        }

        @WriteStreamDecision.Conditions.IsAliveStream.True
        @WriteStreamDecision.Conditions.IsPodcast.True
        @WriteStreamDecision.Conditions.IsSingleFile.False
        @WriteStreamDecision.Conditions.CheckRequired.True
        @WriteStreamDecision.Conditions.PlayQueueStatus.Stopped
        @WriteStreamDecision.Result.Outsize0
        @Test
        void c02() throws Exception {
            Player player = new Player();
            player.setId(100);
            TransferStatus transferStatus = new TransferStatus();
            transferStatus.setActive(true);
            when(statusService.getStreamStatusesForPlayer(player))
                .thenReturn(Arrays.asList(transferStatus));

            PlayQueue playQueue = new PlayQueue();
            playQueue.setStatus(Status.STOPPED);
            player.setPlayQueue(playQueue);
            player.setUsername(ServiceMockUtils.ADMIN_NAME);

            String dummy = "a".repeat(8192);
            InputStream in = new ByteArrayInputStream(dummy.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            streamController.writeStream(player, in, out, null, true, false);
            assertEquals(0, out.size());
            verify(streamService, never())
                .sendDummyDelayed(any(byte[].class), any(OutputStream.class));
        }

        @WriteStreamDecision.Conditions.IsAliveStream.True
        @WriteStreamDecision.Conditions.IsPodcast.False
        @WriteStreamDecision.Conditions.IsSingleFile.False
        @WriteStreamDecision.Conditions.CheckRequired.True
        @WriteStreamDecision.Conditions.PlayQueueStatus.Stopped
        @WriteStreamDecision.Result.Outsize0
        @Test
        void c03() throws Exception {
            Player player = new Player();
            player.setId(100);
            TransferStatus transferStatus = new TransferStatus();
            transferStatus.setActive(true);
            PlayQueue playQueue = new PlayQueue();
            playQueue.setStatus(Status.STOPPED);
            player.setPlayQueue(playQueue);
            player.setUsername(ServiceMockUtils.ADMIN_NAME);
            initMocks(player, transcodingService, streamService);

            when(statusService.getStreamStatusesForPlayer(player))
                .thenReturn(Arrays.asList(transferStatus));
            doThrow(new UnsupportedOperationException("To skip verification of sendDummyDelayed"))
                .when(streamService)
                .sendDummyDelayed(any(byte[].class), any(OutputStream.class));
            assertTrue(streamController.isAliveStream(player));

            String dummy = "a".repeat(8192);
            InputStream in = new ByteArrayInputStream(dummy.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertThrows(UnsupportedOperationException.class, () -> {
                streamController.writeStream(player, in, out, null, false, false);
            });
            assertEquals(0, out.size());
            verify(streamService, times(1))
                .sendDummyDelayed(any(byte[].class), any(OutputStream.class));
        }

        @WriteStreamDecision.Conditions.IsAliveStream.True
        @WriteStreamDecision.Conditions.CheckRequired.True
        @WriteStreamDecision.Conditions.PlayQueueStatus.Playing
        @WriteStreamDecision.Result.Outsize8192
        @Test
        void c04() throws Exception {
            Player player = new Player();
            player.setId(100);
            TransferStatus transferStatus = new TransferStatus();
            transferStatus.setActive(true);
            PlayQueue playQueue = new PlayQueue();
            playQueue.setStatus(Status.PLAYING);
            player.setPlayQueue(playQueue);
            player.setUsername(ServiceMockUtils.ADMIN_NAME);
            initMocks(player, transcodingService, streamService);

            when(statusService.getStreamStatusesForPlayer(player))
                .thenReturn(Arrays.asList(transferStatus));
            doThrow(new UnsupportedOperationException("To skip verification of sendDummyDelayed"))
                .when(streamService)
                .sendDummyDelayed(any(byte[].class), any(OutputStream.class));
            assertTrue(streamController.isAliveStream(player));

            String dummy = "a".repeat(8192);
            InputStream in = new ByteArrayInputStream(dummy.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertThrows(UnsupportedOperationException.class, () -> {
                streamController.writeStream(player, in, out, null, false, false);
            });
            assertEquals(8192, out.size());
            verify(streamService, times(1))
                .sendDummyDelayed(any(byte[].class), any(OutputStream.class));
        }
    }

    @Documented
    private @interface IsAliveStreamDecision {
        @interface Conditions {
            @interface Destroy {
                @interface False {
                }

                @interface True {
                }
            }

            @interface TransferStatusisActive {
                @interface False {
                }

                @interface True {
                }
            }
        }

        @interface Result {
            @interface False {
            }

            @interface True {
            }
        }
    }

    @Order(7)
    @Nested
    class IsAliveStreamTest {

        @IsAliveStreamDecision.Conditions.Destroy.False
        @IsAliveStreamDecision.Conditions.TransferStatusisActive.False
        @IsAliveStreamDecision.Result.False
        @Test
        void c00() throws Exception {
            Player player = new Player();
            player.setId(100);
            assertFalse(streamController.isAliveStream(player));
        }

        @IsAliveStreamDecision.Conditions.Destroy.False
        @IsAliveStreamDecision.Conditions.TransferStatusisActive.True
        @IsAliveStreamDecision.Result.True
        @Test
        void c01() throws Exception {
            Player player = new Player();
            player.setId(100);
            TransferStatus transferStatus = new TransferStatus();
            transferStatus.setActive(true);
            when(statusService.getStreamStatusesForPlayer(player))
                .thenReturn(Arrays.asList(transferStatus));
            assertTrue(streamController.isAliveStream(player));
        }

        @IsAliveStreamDecision.Conditions.Destroy.True
        @IsAliveStreamDecision.Result.False
        @Test
        void c02() throws Exception {
            Player player = new Player();
            player.setId(100);
            assertFalse(streamController.isAliveStream(player));
        }
    }

    @Order(8)
    @Test
    void testWriteErrorLog() throws Exception {
        MediaFile song = new MediaFile();
        song.setPathString(TEST_PATH);
        song.setDurationSeconds(10);
        song.setFileSize(3_300L);
        when(streamService.getSingleFile(any(HttpServletRequest.class))).thenReturn(song);

        SettingsFacade spy = spy(settingsFacade);
        doAnswer(invocation -> {
            throw new IOException("testWriteErrorLog1");
        }).when(spy).get(SKeys.advanced.bandwidth.bufferSize);
        mockMvc
            .perform(MockMvcRequestBuilders.get(TEST_URL))
            .andExpect(MockMvcResultMatchers.status().isOk());
        verify(streamService, times(1))
            .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));

        doAnswer(invocation -> {
            throw new org.eclipse.jetty.io.EofException("testWriteErrorLog2");
        }).when(spy).get(SKeys.advanced.bandwidth.bufferSize);
        clearInvocations(streamService);
        mockMvc
            .perform(MockMvcRequestBuilders.get(TEST_URL))
            .andExpect(MockMvcResultMatchers.status().isOk());
        verify(streamService, times(1))
            .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));

        doAnswer(invocation -> {
            throw new org.apache.catalina.connector.ClientAbortException("testWriteErrorLog3");
        }).when(spy).get(SKeys.advanced.bandwidth.bufferSize);
        clearInvocations(streamService);
        mockMvc
            .perform(MockMvcRequestBuilders.get(TEST_URL))
            .andExpect(MockMvcResultMatchers.status().isOk());
        verify(streamService, times(1))
            .removeStreamStatus(nullable(User.class), nullable(TransferStatus.class));
    }

    @Order(9)
    @Test
    void testGet() throws Exception {
        // Playlist case only (Because path coverage are covered by other than this
        // case)
        int playlistId = 99;
        ArgumentCaptor<Integer> idCaptor = ArgumentCaptor.forClass(Integer.class);
        doNothing()
            .when(streamService)
            .setUpPlayQueue(any(HttpServletRequest.class), any(HttpServletResponse.class),
                    any(Player.class), idCaptor.capture());

        mockMvc
            .perform(MockMvcRequestBuilders
                .get(TEST_URL)
                .param(Attributes.Request.PLAYLIST.value(), Integer.toString(playlistId)))
            .andExpect(MockMvcResultMatchers.status().isOk());

        verify(streamService)
            .setUpPlayQueue(any(HttpServletRequest.class), any(HttpServletResponse.class),
                    any(Player.class), any(Integer.class));
        assertEquals(playlistId, idCaptor.getValue());
    }
}

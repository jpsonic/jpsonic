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

package com.tesshu.jpsonic.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URISyntaxException;
import java.nio.file.Path;

import com.tesshu.jpsonic.controller.InternalHelpController.FileStatistics;
import com.tesshu.jpsonic.infrastructure.core.DisabledOnWindowsJdk21OrEarlier;
import com.tesshu.jpsonic.infrastructure.core.NeedsHome;
import com.tesshu.jpsonic.infrastructure.core.NeedsTranscode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@NeedsHome
@NeedsTranscode
@SuppressWarnings({ "PMD.UnitTestShouldIncludeAssert", "PMD.TooManyStaticImports" }) // pmd/pmd/issues/1084
class InternalHelpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisabledOnWindowsJdk21OrEarlier // Flaky Test on Windows Server
    void testOkForAdmins() throws Exception {
        // Boot 4 migration: @WithMockUser is no longer reapplied across requests.
        mockMvc
            .perform(get("/internalhelp")
                .with(SecurityMockMvcRequestPostProcessors
                    .user("admin")
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk());
    }

    @Test
    void testNotOkForUsers() throws Exception {
        // Boot 4 migration: @WithMockUser is no longer reapplied across requests.
        mockMvc
            .perform(get("/internalhelp")
                .with(SecurityMockMvcRequestPostProcessors
                    .user("user")
                    .authorities(new SimpleGrantedAuthority("USER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void fmtTest() {

        String version = """
                ffmpeg version 6.1.2 Copyright (c) 2000-2024 the FFmpeg developers
                built with gcc 14.2.0 (Alpine 14.2.0)
                configuration: --prefix=/usr --disable-librtmp \
                --disable-lzma --disable-static --disable-stripping \
                --enable-avfilter --enable-gpl --enable-ladspa --enable-libaom \
                --enable-libass --enable-libbluray --enable-libdav1d \
                --enable-libdrm --enable-libfontconfig --enable-libfreetype \
                --enable-libfribidi --enable-libharfbuzz --enable-libmp3lame \
                --enable-libopenmpt --enable-libopus --enable-libplacebo \
                --enable-libpulse --enable-librav1e --enable-librist \
                --enable-libsoxr --enable-libsrt --enable-libssh --enable-libtheora \
                --enable-libv4l2 --enable-libvidstab --enable-libvorbis --enable-libvpx \
                --enable-libwebp --enable-libx264 --enable-libx265 --enable-libxcb \
                --enable-libxml2 --enable-libxvid --enable-libzimg --enable-libzmq \
                --enable-lto=auto --enable-lv2 --enable-openssl --enable-pic \
                --enable-postproc --enable-pthreads --enable-shared --enable-vaapi \
                --enable-vdpau --enable-version3 --enable-vulkan --optflags=-O3 \
                --enable-libjxl --enable-libsvtav1 --enable-libvpl
                libavutil      58. 29.100 / 58. 29.100
                libavcodec     60. 31.102 / 60. 31.102
                libavformat    60. 16.100 / 60. 16.100
                libavdevice    60.  3.100 / 60.  3.100
                libavfilter     9. 12.100 /  9. 12.100
                libswscale      7.  5.100 /  7.  5.100
                libswresample   4. 12.100 /  4. 12.100
                libpostproc    57.  3.100 / 57.  3.100
                """;
        InternalHelpController controller = new InternalHelpController(null, null, null, null, null,
                null);

        assertEquals("""
                ffmpeg version 6.1.2
                        Copyright (c) 2000-2024 the FFmpeg developers
                        built with gcc 14.2.0 (Alpine 14.2.0)

                configuration:
                    --prefix=/usr
                    --disable-librtmp
                    --disable-lzma
                    --disable-static
                    --disable-stripping
                    --enable-avfilter
                    --enable-gpl
                    --enable-ladspa
                    --enable-libaom
                    --enable-libass
                    --enable-libbluray
                    --enable-libdav1d
                    --enable-libdrm
                    --enable-libfontconfig
                    --enable-libfreetype
                    --enable-libfribidi
                    --enable-libharfbuzz
                    --enable-libmp3lame
                    --enable-libopenmpt
                    --enable-libopus
                    --enable-libplacebo
                    --enable-libpulse
                    --enable-librav1e
                    --enable-librist
                    --enable-libsoxr
                    --enable-libsrt
                    --enable-libssh
                    --enable-libtheora
                    --enable-libv4l2
                    --enable-libvidstab
                    --enable-libvorbis
                    --enable-libvpx
                    --enable-libwebp
                    --enable-libx264
                    --enable-libx265
                    --enable-libxcb
                    --enable-libxml2
                    --enable-libxvid
                    --enable-libzimg
                    --enable-libzmq
                    --enable-lto=auto
                    --enable-lv2
                    --enable-openssl
                    --enable-pic
                    --enable-postproc
                    --enable-pthreads
                    --enable-shared
                    --enable-vaapi
                    --enable-vdpau
                    --enable-version3
                    --enable-vulkan
                    --optflags=-O3
                    --enable-libjxl
                    --enable-libsvtav1
                    --enable-libvpl

                libavutil      58. 29.100 / 58. 29.100
                libavcodec     60. 31.102 / 60. 31.102
                libavformat    60. 16.100 / 60. 16.100
                libavdevice    60.  3.100 / 60.  3.100
                libavfilter     9. 12.100 /  9. 12.100
                libswscale      7.  5.100 /  7.  5.100
                libswresample   4. 12.100 /  4. 12.100
                libpostproc    57.  3.100 / 57.  3.100
                """, controller.formatFFmpegVersion(version));
    }

    @Nested
    class DoesLocaleSupportUtf8Test {

        private final InternalHelpController controller = new InternalHelpController(null, null,
                null, null, null, null);

        @Test
        void testNull() {
            assertFalse(controller.doesLocaleSupportUtf8(null));
        }

        @Test
        void testContainsUtf8() {
            assertTrue(controller.doesLocaleSupportUtf8("utf8"));
            assertTrue(controller.doesLocaleSupportUtf8("utf-8"));
        }

        @Test
        void testNotContainsUtf8() {
            assertFalse(controller.doesLocaleSupportUtf8("MS932"));
        }
    }

    @Nested
    class FileStatisticsTest {
        @Test
        void testSetFromPath() throws URISyntaxException {
            Path path = Path
                .of(InternalHelpControllerTest.class.getResource("/MEDIAS/Music").toURI());
            FileStatistics fileStatistics = new FileStatistics();
            fileStatistics.setFromPath(path);
            assertEquals("Music", fileStatistics.getName());
            assertNotNull(fileStatistics.getFreeFilesystemSizeBytes());
            assertTrue(fileStatistics.isReadable());
            assertTrue(fileStatistics.isWritable());
            assertTrue(fileStatistics.isExecutable());
            assertNotNull(fileStatistics.getTotalFilesystemSizeBytes());
            assertEquals(path.toString(), fileStatistics.getPath());
        }
    }
}

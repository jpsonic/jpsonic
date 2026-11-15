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

package com.tesshu.jpsonic.controller;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.infrastructure.core.NeedsHome;
import com.tesshu.jpsonic.infrastructure.scanner.MusicFolderServiceImpl;
import com.tesshu.jpsonic.infrastructure.scanner.ScannerStateServiceImpl;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.persistence.api.repository.MusicFolderDao;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.StatusService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.ModelAndView;

@SpringBootTest
@ActiveProfiles("test")
@Execution(ExecutionMode.CONCURRENT)
@NeedsHome
class UploadControllerTest {

    @Autowired
    private UploadController uploadController;

    @Autowired
    private MusicFolderDao musicFolderDao;
    @Autowired
    private MusicFolderServiceImpl musicFolderService;

    private static final String FILE_NAME = "piano.mp3";
    private static final String FILE_PATH = "/MEDIAS/piano.mp3";
    private static final String FILE_CONTENT_TYPE = "audio/mpeg";
    private static final String ZIP_NAME = "piano.zip";
    private static final String ZIP_PATH = "/MEDIAS/piano.zip";
    private static final String ZIP_CONTENT_TYPE = "application/zip";
    private static final String BOUNDARY = "265001916915724";
    private static final String SEPA = "\r\n";

    // @Test Currently it is not possible to run two tests in a row
    @WithMockUser(username = "admin")
    @Test
    @SuppressWarnings("unchecked")
    void testHandleRequestInternalWithFile(@TempDir Path tempDirPath)
            throws IOException, URISyntaxException {

        MusicFolder musicFolder = new MusicFolder(0, tempDirPath.toString(), "Incoming1", true,
                now(), 1, false);
        musicFolderDao.createMusicFolder(musicFolder);
        musicFolderService.clearMusicFolderCache();

        URL url = UploadController.class.getResource(FILE_PATH);

        MockMultipartHttpServletRequest req = new MockMultipartHttpServletRequest();

        byte[] data = IOUtils.toByteArray(Files.newInputStream(Path.of(url.toURI())));
        req
            .setContent(
                    createFileContent(tempDirPath.toString(), FILE_NAME, FILE_CONTENT_TYPE, data));
        req.setContentType("multipart/form-data; boundary=" + BOUNDARY);

        MockMultipartFile file = new MockMultipartFile("file", FILE_NAME, FILE_CONTENT_TYPE, data);
        req.addFile(file);
        req.setMethod(HttpMethod.POST.name());

        ModelAndView result = uploadController
            .handleRequestInternal(req, new MockHttpServletResponse());
        assertEquals("upload", result.getViewName());
        Map<String, Object> model = (Map<String, Object>) result.getModel().get("model");
        assertNotNull(model);
        assertEquals(FILE_NAME,
                ((List<Path>) model.get("uploadedFiles")).get(0).getFileName().toString());
        assertEquals(0, ((List<Path>) model.get("unzippedFiles")).size());
    }

    @WithMockUser(username = "admin")
    @Test
    @SuppressWarnings("unchecked")
    void testHandleRequestInternalWithZip(@TempDir Path tempDirPath) throws Exception {

        MusicFolder musicFolder = new MusicFolder(10, tempDirPath.toString(), "Incoming2", true,
                now(), 1, false);
        musicFolderDao.createMusicFolder(musicFolder);
        musicFolderService.clearMusicFolderCache();

        URL url = UploadController.class.getResource(ZIP_PATH);

        MockMultipartHttpServletRequest req = new MockMultipartHttpServletRequest();

        byte[] data = IOUtils.toByteArray(Files.newInputStream(Path.of(url.toURI())));
        byte[] fileContent = createFileContent(tempDirPath.toString(), ZIP_NAME, ZIP_CONTENT_TYPE,
                data);

        String zipField = "--" + BOUNDARY + SEPA + " Content-Disposition: form-data; name=\""
                + UploadController.FIELD_NAME_UNZIP + "\";" + "Content-type: text/plain" + SEPA
                + " value=\"true\"" + SEPA + SEPA;
        String zipValue = "true" + SEPA;
        req
            .setContent(ArrayUtils
                .addAll((zipField + zipValue).getBytes(StandardCharsets.UTF_8), fileContent));
        req.setContentType("multipart/form-data; boundary=" + BOUNDARY);

        MockMultipartFile file = new MockMultipartFile("file", ZIP_NAME, ZIP_CONTENT_TYPE, data);
        req.addFile(file);
        req.setMethod(HttpMethod.POST.name());

        ModelAndView result = uploadController
            .handleRequestInternal(req, new MockHttpServletResponse());
        assertEquals("upload", result.getViewName());
        Map<String, Object> model = (Map<String, Object>) result.getModel().get("model");
        assertNotNull(model);
        assertEquals(FILE_NAME,
                ((List<Path>) model.get("unzippedFiles")).get(0).getFileName().toString());
    }

    byte[] createFileContent(String tempDir, String fileName, String contentType,
            byte[] fileValue) {
        String dirField = "--" + BOUNDARY + SEPA + " Content-Disposition: form-data; name=\""
                + UploadController.FIELD_NAME_DIR + "\";" + "Content-type: text/plain" + SEPA
                + " value=\"" + tempDir + "\"" + SEPA + SEPA;
        String dirValue = tempDir + SEPA;
        String fileField = "--" + BOUNDARY + SEPA
                + " Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\""
                + SEPA + "Content-type: " + contentType + SEPA + SEPA;
        String end = SEPA + "--" + BOUNDARY + "--";
        return ArrayUtils
            .addAll((dirField + dirValue + fileField).getBytes(StandardCharsets.UTF_8),
                    ArrayUtils.addAll(fileValue, end.getBytes(StandardCharsets.UTF_8)));
    }

    @Nested
    class ExceptionTest {

        private ScannerStateServiceImpl scannerStateService;

        @BeforeEach
        void setup() throws ExecutionException {
            scannerStateService = mock(ScannerStateServiceImpl.class);
            SettingsFacade settingsFacade = SettingsFacadeBuilder.create().build();
            uploadController = new UploadController(mock(LibraryAccessPolicy.class),
                    mock(UserService.class), mock(PlayerService.class), mock(StatusService.class),
                    settingsFacade, scannerStateService);
        }

        @Test
        void testIsScanning() {

            Mockito.when(scannerStateService.isScanning()).thenReturn(true);
            ModelAndView result = uploadController
                .handleRequestInternal(mock(HttpServletRequest.class),
                        mock(HttpServletResponse.class));
            @SuppressWarnings("unchecked")
            Map<String, Object> model = (Map<String, Object>) result.getModel().get("model");
            assertEquals("Currently scanning. Please try again after a while.",
                    ((Exception) model.get("exception")).getMessage());
        }
    }
}

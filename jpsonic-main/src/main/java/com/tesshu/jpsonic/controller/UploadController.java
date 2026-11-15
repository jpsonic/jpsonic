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

import static com.tesshu.jpsonic.controller.Attributes.Request.NameConstants.EXEPTION;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.tesshu.jpsonic.SuppressLint;
import com.tesshu.jpsonic.domain.language.StringUtil;
import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.infrastructure.collection.util.LegacyMap;
import com.tesshu.jpsonic.infrastructure.concurrent.ConcurrentUtils;
import com.tesshu.jpsonic.infrastructure.filesystem.FileOperations;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.core.entity.User;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.StatusService;
import com.tesshu.jpsonic.service.StatusService.TransferStatus;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.core.ProgressListener;
import org.apache.commons.fileupload2.jakarta.JakartaServletDiskFileUpload;
import org.apache.commons.fileupload2.jakarta.JakartaServletFileUpload;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller which receives uploaded files.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/upload", "/upload.view" })
public class UploadController {

    private static final Logger LOG = LoggerFactory.getLogger(UploadController.class);
    public static final String FIELD_NAME_DIR = "dir";
    public static final String FIELD_NAME_UNZIP = "unzip";
    private static final String NOT_ALLOWED_MSG = "The root path is other than the Musicfolder, or the file already exists: ";

    private final LibraryAccessPolicy libraryAccessPolicy;
    private final UserService userService;
    private final PlayerService playerService;
    private final StatusService statusService;
    private final SettingsFacade settingsFacade;
    private final ScannerStateProvider scannerStateProvider;

    public UploadController(LibraryAccessPolicy libraryAccessPolicy, UserService userService,
            PlayerService playerService, StatusService statusService, SettingsFacade settingsFacade,
            ScannerStateProvider scannerStateProvider) {
        super();
        this.libraryAccessPolicy = libraryAccessPolicy;
        this.userService = userService;
        this.playerService = playerService;
        this.statusService = statusService;
        this.settingsFacade = settingsFacade;
        this.scannerStateProvider = scannerStateProvider;
    }

    @PostMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request,
            HttpServletResponse response) {

        Map<String, Object> model = LegacyMap.of();
        if (scannerStateProvider.isScanning()) {
            model
                .put(EXEPTION, new IllegalArgumentException(
                        "Currently scanning. Please try again after a while."));
            return new ModelAndView("upload", "model", model);
        }
        if (!JakartaServletFileUpload.isMultipartContent(request)) {
            model.put(EXEPTION, new IOException("Illegal request."));
            return new ModelAndView("upload", "model", model);
        }

        TransferStatus status = null;
        UnzipResult result = null;
        try {

            status = statusService
                .createUploadStatus(playerService.getPlayer(request, response, false, false));
            status.setBytesTotal(request.getContentLength());

            request.getSession().setAttribute(Attributes.Session.UPLOAD_STATUS.value(), status);

            List<DiskFileItem> items = getUploadItems(request, status);
            Path dir = getDir(items);
            boolean unzip = isUnzip(items);
            result = doUnzip(items, dir, unzip);

        } catch (IOException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Uploading failed. {}", e.getMessage());
            }
            model.put(EXEPTION, e);
        } catch (ExecutionException e) {
            ConcurrentUtils.handleCauseUnchecked(e);
            if (LOG.isWarnEnabled()) {
                LOG.warn("Uploading failed. {}", e.getMessage());
            }
            model.put(EXEPTION, e);
        } finally {
            afterUpload(request, status);
        }

        if (result != null) {
            model.put("uploadedFiles", result.getUploadedFiles());
            model.put("unzippedFiles", result.getUnzippedFiles());
        }

        return new ModelAndView("upload", "model", model);
    }

    private @NonNull Path getDir(List<DiskFileItem> items) throws IOException {
        for (DiskFileItem item : items) {
            if (item.isFormField() && FIELD_NAME_DIR.equals(item.getFieldName())) {
                return Path.of(item.getString());
            }
        }
        throw new IOException("Missing 'dir' parameter.");
    }

    private boolean isUnzip(List<DiskFileItem> items) {
        for (DiskFileItem item : items) {
            if (item.isFormField() && FIELD_NAME_UNZIP.equals(item.getFieldName())) {
                return true;
            }
        }
        return false;
    }

    private List<DiskFileItem> getUploadItems(HttpServletRequest request, TransferStatus status)
            throws FileUploadException {
        DiskFileItemFactory factory = DiskFileItemFactory.builder().get();
        JakartaServletDiskFileUpload upload = new JakartaServletDiskFileUpload(factory);
        upload.setProgressListener(new UploadListenerImpl(status, statusService, settingsFacade));
        return upload.parseRequest(request);
    }

    private void afterUpload(HttpServletRequest request, TransferStatus status) {
        if (status != null) {
            statusService.removeUploadStatus(status);
            request.getSession().removeAttribute(Attributes.Session.UPLOAD_STATUS.value());
            User user = userService.getCurrentUserStrict(request);
            userService.incrementUserByteCounts(user, 0L, 0L, status.getBytesTransfered());
        }
    }

    private static class UnzipResult {

        private final List<Path> uploadedFiles;
        private final List<Path> unzippedFiles;

        UnzipResult(List<Path> uploadedFiles, List<Path> unzippedFiles) {
            super();
            this.uploadedFiles = uploadedFiles;
            this.unzippedFiles = unzippedFiles;
        }

        List<Path> getUploadedFiles() {
            return uploadedFiles;
        }

        List<Path> getUnzippedFiles() {
            return unzippedFiles;
        }
    }

    private UnzipResult doUnzip(List<DiskFileItem> items, Path dir, boolean unzip)
            throws ExecutionException {
        List<Path> uploadedFiles = new ArrayList<>();
        List<Path> unzippedFiles = new ArrayList<>();

        if (!Files.exists(dir) && FileOperations.createDirectories(dir) == null
                && LOG.isWarnEnabled()) {
            LOG.warn("The directory '{}' could not be created.", dir);
        }

        // Look for file items.
        for (DiskFileItem item : items) {
            if (!item.isFormField() && !StringUtils.isAllBlank(item.getName())) {

                Path targetFile = Path.of(dir.toString(), item.getName());
                uploadedFiles = upload(item, targetFile);

                if (LOG.isInfoEnabled()) {
                    LOG.info("Uploaded " + targetFile);
                }

                if (unzip && StringUtil.endsWithIgnoreCase(targetFile.toString(), ".zip")) {
                    unzippedFiles = unzip(targetFile);
                }
            }
        }
        return new UnzipResult(uploadedFiles, unzippedFiles);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException") // apache-commons/FileItem#write
    private List<Path> upload(DiskFileItem targetItem, Path targetFile) throws ExecutionException {

        if (!libraryAccessPolicy.isUploadAllowed(targetFile)) {
            throw new ExecutionException(new GeneralSecurityException(
                    NOT_ALLOWED_MSG + StringEscapeUtils.escapeHtml4(targetFile.toString())));
        }

        List<Path> uploadedFiles = new ArrayList<>();
        try {
            targetItem.write(targetFile);
            uploadedFiles.add(targetFile);
        } catch (Exception e) {
            throw new ExecutionException("Unable to write item.", e);
        }
        return uploadedFiles;
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // (File, IOException) Not reusable
    @SuppressLint(value = "RESOURCE_LEAK", justification = "False positive. facebook/infer#619")
    private List<Path> unzip(Path file) throws ExecutionException {
        if (LOG.isInfoEnabled()) {
            LOG.info("Unzipping " + file);
        }
        List<Path> unzippedFiles = Collections.emptyList();
        try (ZipFile zipFile = new ZipFile(file.toString())) {

            Enumeration<?> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                Path parent = file.getParent();
                if (parent == null) {
                    throw new ExecutionException(new IOException(
                            "Bad zip filename: " + StringEscapeUtils.escapeHtml4(file.toString())));
                }
                ZipEntry entry = (ZipEntry) entries.nextElement();
                Path entryFile = Path.of(parent.toString(), entry.getName());
                if (!entryFile.normalize().toString().startsWith(parent.toString())) {
                    throw new ExecutionException(new IOException("Bad zip filename: "
                            + StringEscapeUtils.escapeHtml4(entryFile.toString())));
                }
                if (!entry.isDirectory()) {
                    unzippedFiles = unzip(zipFile, entry, entryFile);
                }
            }
            zipFile.close();
            FileOperations.deleteIfExists(file);

        } catch (IOException e) {
            throw new ExecutionException("Can't unzip.", e);
        }
        return unzippedFiles;
    }

    private List<Path> unzip(ZipFile zipFile, ZipEntry entry, Path entryFile)
            throws ExecutionException {

        if (!libraryAccessPolicy.isUploadAllowed(entryFile)) {
            throw new ExecutionException(new GeneralSecurityException(
                    NOT_ALLOWED_MSG + StringEscapeUtils.escapeHtml4(entryFile.toString())));
        }

        Path parent = entryFile.getParent();
        if (parent == null || !Files.exists(parent)
                && FileOperations.createDirectories(parent) == null && LOG.isWarnEnabled()) {
            LOG.warn("The directory '{}' could not be created.", parent);
        }

        List<Path> unzippedFiles = new ArrayList<>();
        try (OutputStream outputStream = Files.newOutputStream(entryFile);
                InputStream inputStream = zipFile.getInputStream(entry)) {
            byte[] buf = new byte[8192];
            while (true) {
                int n = inputStream.read(buf);
                if (n == -1) {
                    break;
                }
                outputStream.write(buf, 0, n);
            }
            if (LOG.isInfoEnabled()) {
                LOG.info("Unzipped " + entryFile);
            }
            unzippedFiles.add(entryFile);
        } catch (IOException e) {
            throw new ExecutionException(e);
        }
        return unzippedFiles;
    }

    /**
     * Receives callbacks as the file upload progresses.
     */
    private static class UploadListenerImpl implements ProgressListener {

        private final TransferStatus status;
        private final StatusService statusService;
        private final SettingsFacade settingsFacade;
        private final long startTime;

        private static final Logger LOG = LoggerFactory.getLogger(UploadListenerImpl.class);

        UploadListenerImpl(TransferStatus status, StatusService statusService,
                SettingsFacade settingsFacade) {
            this.status = status;
            this.statusService = statusService;
            this.settingsFacade = settingsFacade;
            this.startTime = Instant.now().toEpochMilli();
        }

        @Override
        public void update(long pBytesRead, long pContentLength, int pItems) {
            long bitCount = pBytesRead * 8L;
            float elapsedMillis = Math.max(1, Instant.now().toEpochMilli() - startTime);
            float elapsedSeconds = elapsedMillis / 1000.0F;
            long maxBitsPerSecond = getBitrateLimit();

            status.setBytesTransfered(pBytesRead);

            try {
                doSleep(maxBitsPerSecond, bitCount, elapsedSeconds);
            } catch (InterruptedException e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Failed to sleep.", e);
                }
            }
        }

        private void doSleep(long maxBitsPerSecond, long bitCount, float elapsedSeconds)
                throws InterruptedException {
            if (maxBitsPerSecond > 0) {
                float sleepMillis = bitCount * 1000.0F / (maxBitsPerSecond - elapsedSeconds)
                        * 1000.0F;
                if (sleepMillis > 0) {
                    Thread.sleep((long) sleepMillis);
                }
            }
        }

        private long getBitrateLimit() {
            return 1024L * this.settingsFacade.get(SKeys.advanced.bandwidth.uploadBitrateLimit)
                    / Math.max(1, this.statusService.getAllUploadStatuses().size());
        }
    }
}

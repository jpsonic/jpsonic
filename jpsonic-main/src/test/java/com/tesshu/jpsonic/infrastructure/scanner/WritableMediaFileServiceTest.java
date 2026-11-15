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

package com.tesshu.jpsonic.infrastructure.scanner;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static com.tesshu.jpsonic.util.PlayerUtils.FAR_PAST;
import static com.tesshu.jpsonic.util.PlayerUtils.now;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Documented;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators;
import com.tesshu.jpsonic.infrastructure.filesystem.ScanningExclusionPolicy;
import com.tesshu.jpsonic.infrastructure.language.MetadataReadingProcessor;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.repository.AlbumDao;
import com.tesshu.jpsonic.persistence.api.repository.MediaFileDao;
import com.tesshu.jpsonic.service.MediaFileCache;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.metadata.MetaData;
import com.tesshu.jpsonic.service.metadata.MusicParser;
import com.tesshu.jpsonic.service.metadata.VideoParser;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals" })
class WritableMediaFileServiceTest {

    private MediaFileDao mediaFileDao;
    private SettingsFacade settingsFacade;
    private LibraryAccessPolicy libraryAccessPolicy;
    private WritableMediaFileService writableMediaFileService;

    @BeforeEach
    void setup() {
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.general.extension.videoFileTypes, null)
            .withString(SKeys.general.extension.musicFileTypes, "mp3")
            .withString(SKeys.general.extension.excludedCoverArt, null)
            .build();
        init();
    }

    @Ignore
    void init() {

        libraryAccessPolicy = mock(LibraryAccessPolicy.class);
        mediaFileDao = mock(MediaFileDao.class);
        MusicParser musicParser = new MusicParser(mock(MusicFolderService.class));
        MediaFileCache mediaFileCache = mock(MediaFileCache.class);
        MediaFileService mediaFileService = new MediaFileService(settingsFacade,
                new ScanningExclusionPolicy(settingsFacade), mock(MusicFolderService.class),
                libraryAccessPolicy, mediaFileCache, mediaFileDao, mock(JpsonicComparators.class));
        AlbumDao albumDao = mock(AlbumDao.class);
        MetadataReadingProcessor proc = mock(MetadataReadingProcessor.class);
        writableMediaFileService = new WritableMediaFileService(mediaFileDao,
                mock(ScannerStateProvider.class), mediaFileService, albumDao, mediaFileCache,
                musicParser, mock(VideoParser.class), settingsFacade, libraryAccessPolicy,
                new ScanningExclusionPolicy(settingsFacade), proc, mock(Indexer.class),
                new MusicIndexProviderImpl(null, null, settingsFacade, proc));

        Mockito.when(libraryAccessPolicy.isReadAllowed(Mockito.any(Path.class))).thenReturn(true);
    }

    private Path createPath(String path) throws URISyntaxException {
        return Path.of(WritableMediaFileServiceTest.class.getResource(path).toURI());
    }

    @Test
    void testGetLastModified() throws URISyntaxException, IOException {
        // Defaulte (Same as legacy). File modification date.
        Path path = createPath("/MEDIAS/Music2/_DIR_ chrome hoof - 2004/10 telegraph hill.mp3");
        // For scan flows in Scheme.LAST_MODIFIED, lastModified = the last modified date
        assertEquals(Files.getLastModifiedTime(path).toInstant().truncatedTo(ChronoUnit.MILLIS),
                writableMediaFileService.getLastModified(path));
    }

    @Documented
    private @interface CheckLastModifiedDecision {

        @interface Conditions {

            @interface IgnoreFileTimestamps {
                @interface True {
                }

                @interface False {

                }
            }

            @interface MediaFile {
                @interface Version {
                    @interface GtEqDaoVersion {
                    }

                    @interface LtDaoVersion {
                    }
                }

                @interface Changed {
                    @interface GtEqLastModified {
                    }

                    @interface LtLastModified {
                    }

                }

                @interface LastScanned {
                    @interface EqZeroDate {
                    }

                    @interface NeZeroDate {
                    }
                }
            }
        }

        @interface Result {
            @interface CreateOrUpdate {
                @interface True {
                }

                @interface False {
                }
            }
        }

    }

    @Nested
    class CheckLastModifiedTest {

        private Path dir;
        private final Instant scanStart = now();
        private static final String INDEX_STRING = "A B C D E F G H I J K L M N O P Q R S T U V W X-Z(XYZ)";

        @BeforeEach
        void setup() throws URISyntaxException {
            dir = Path.of(WritableMediaFileServiceTest.class.getResource("/MEDIAS/Music").toURI());
        }

        private MediaFile createMediaFile(int version) {
            return new MediaFile() {
                @Override
                public int getVersion() {
                    return version;
                }
            };
        }

        @CheckLastModifiedDecision.Conditions.MediaFile.Version.LtDaoVersion
        @CheckLastModifiedDecision.Result.CreateOrUpdate.True
        @Test
        void c02() throws ExecutionException {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, false)
                .withString(SKeys.general.index.indexString, INDEX_STRING)
                .withString(SKeys.general.extension.videoFileTypes, null)
                .withString(SKeys.general.extension.musicFileTypes, "mp3")
                .withString(SKeys.general.extension.excludedCoverArt, null)
                .build();
            init();

            MediaFile mediaFile = createMediaFile(MediaFileDao.VERSION - 1);
            mediaFile.setPathString(dir.toString());
            Mockito
                .when(mediaFileDao.getMediaFile(mediaFile.getPathString()))
                .thenReturn(mediaFile);
            Mockito
                .when(mediaFileDao.updateMediaFile(mediaFile))
                .thenReturn(Optional.of(mediaFile));

            try {
                mediaFile.setChanged(Files.getLastModifiedTime(dir).toInstant());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            assertThat("mediaFile#version Lt MediaFileDao.VERSION", mediaFile.getVersion(),
                    lessThan(MediaFileDao.VERSION));

            assertEquals(mediaFile,
                    writableMediaFileService.checkLastModified(scanStart, mediaFile).get());
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .updateMediaFile(Mockito.any(MediaFile.class));
        }

        @CheckLastModifiedDecision.Conditions.MediaFile.Version.GtEqDaoVersion
        @CheckLastModifiedDecision.Conditions.IgnoreFileTimestamps.True
        @CheckLastModifiedDecision.Conditions.MediaFile.Changed.GtEqLastModified
        @CheckLastModifiedDecision.Conditions.MediaFile.LastScanned.EqZeroDate
        @CheckLastModifiedDecision.Result.CreateOrUpdate.True
        @Test
        void c03() throws ExecutionException, IOException {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, true)
                .withString(SKeys.general.index.indexString, INDEX_STRING)
                .withString(SKeys.general.extension.videoFileTypes, null)
                .withString(SKeys.general.extension.musicFileTypes, "mp3")
                .withString(SKeys.general.extension.excludedCoverArt, null)
                .build();
            init();

            MediaFile mediaFile = createMediaFile(MediaFileDao.VERSION);
            mediaFile.setPathString(dir.toString());
            try {
                mediaFile.setChanged(Files.getLastModifiedTime(dir).toInstant());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            mediaFile.setLastScanned(FAR_PAST);
            Mockito
                .when(mediaFileDao.getMediaFile(mediaFile.getPathString()))
                .thenReturn(mediaFile);
            Mockito
                .when(mediaFileDao.updateMediaFile(mediaFile))
                .thenReturn(Optional.of(mediaFile));

            assertEquals(mediaFile.getChanged(),
                    Files.getLastModifiedTime(mediaFile.toPath()).toInstant());
            assertEquals(FAR_PAST, mediaFile.getLastScanned());
            assertEquals(mediaFile,
                    writableMediaFileService.checkLastModified(scanStart, mediaFile).get());
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .updateMediaFile(Mockito.any(MediaFile.class));
            Mockito.clearInvocations(mediaFileDao);

            try {
                mediaFile
                    .setChanged(
                            Files.getLastModifiedTime(dir).toInstant().plus(1, ChronoUnit.DAYS));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            assertThat("Changed Gt LastModified", mediaFile.getChanged().toEpochMilli(),
                    greaterThan(Files.getLastModifiedTime(mediaFile.toPath()).toMillis()));
            assertEquals(FAR_PAST, mediaFile.getLastScanned());
            assertEquals(mediaFile,
                    writableMediaFileService.checkLastModified(scanStart, mediaFile).get());
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .updateMediaFile(Mockito.any(MediaFile.class));
        }

        @CheckLastModifiedDecision.Conditions.MediaFile.Version.GtEqDaoVersion
        @CheckLastModifiedDecision.Conditions.IgnoreFileTimestamps.True
        @CheckLastModifiedDecision.Conditions.MediaFile.Changed.GtEqLastModified
        @CheckLastModifiedDecision.Conditions.MediaFile.LastScanned.NeZeroDate
        @CheckLastModifiedDecision.Result.CreateOrUpdate.False
        @Test
        void c04() throws ExecutionException, IOException {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, true)
                .withString(SKeys.general.index.indexString, INDEX_STRING)
                .withString(SKeys.general.extension.videoFileTypes, null)
                .withString(SKeys.general.extension.musicFileTypes, "mp3")
                .withString(SKeys.general.extension.excludedCoverArt, null)
                .build();
            init();

            MediaFile mediaFile = createMediaFile(MediaFileDao.VERSION);
            mediaFile.setPathString(dir.toString());
            try {
                mediaFile.setChanged(Files.getLastModifiedTime(dir).toInstant());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            mediaFile.setLastScanned(mediaFile.getChanged().plus(1, ChronoUnit.DAYS));

            assertEquals(mediaFile.getChanged(),
                    Files.getLastModifiedTime(mediaFile.toPath()).toInstant());
            assertNotEquals(FAR_PAST, mediaFile.getLastScanned());
            assertTrue(writableMediaFileService.checkLastModified(scanStart, mediaFile).isEmpty());
            Mockito
                .verify(mediaFileDao, Mockito.never())
                .updateMediaFile(Mockito.any(MediaFile.class));

            try {
                mediaFile
                    .setChanged(
                            Files.getLastModifiedTime(dir).toInstant().plus(1, ChronoUnit.DAYS));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            assertThat("Changed Gt LastModified", mediaFile.getChanged().toEpochMilli(),
                    greaterThan(Files.getLastModifiedTime(mediaFile.toPath()).toMillis()));
            assertNotEquals(FAR_PAST, mediaFile.getLastScanned());
            assertTrue(writableMediaFileService.checkLastModified(scanStart, mediaFile).isEmpty());
            Mockito
                .verify(mediaFileDao, Mockito.never())
                .updateMediaFile(Mockito.any(MediaFile.class));
        }

        @CheckLastModifiedDecision.Conditions.MediaFile.Version.GtEqDaoVersion
        @CheckLastModifiedDecision.Conditions.IgnoreFileTimestamps.True
        @CheckLastModifiedDecision.Conditions.MediaFile.Changed.LtLastModified
        @CheckLastModifiedDecision.Conditions.MediaFile.LastScanned.EqZeroDate
        @CheckLastModifiedDecision.Result.CreateOrUpdate.True
        @Test
        void c05() throws ExecutionException, IOException {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, true)
                .withString(SKeys.general.index.indexString, INDEX_STRING)
                .withString(SKeys.general.extension.videoFileTypes, null)
                .withString(SKeys.general.extension.musicFileTypes, "mp3")
                .withString(SKeys.general.extension.excludedCoverArt, null)
                .build();
            init();

            MediaFile mediaFile = createMediaFile(MediaFileDao.VERSION);
            mediaFile.setPathString(dir.toString());
            try {
                mediaFile
                    .setChanged(
                            Files.getLastModifiedTime(dir).toInstant().minus(1, ChronoUnit.DAYS));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            mediaFile.setLastScanned(FAR_PAST);
            Mockito
                .when(mediaFileDao.getMediaFile(mediaFile.getPathString()))
                .thenReturn(mediaFile);
            Mockito
                .when(mediaFileDao.updateMediaFile(mediaFile))
                .thenReturn(Optional.of(mediaFile));

            assertThat("Changed Lt LastModified", mediaFile.getChanged().toEpochMilli(),
                    lessThan(Files.getLastModifiedTime(mediaFile.toPath()).toMillis()));
            assertEquals(FAR_PAST, mediaFile.getLastScanned());
            assertEquals(mediaFile,
                    writableMediaFileService.checkLastModified(scanStart, mediaFile).get());
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .updateMediaFile(Mockito.any(MediaFile.class));
        }

        @CheckLastModifiedDecision.Conditions.MediaFile.Version.GtEqDaoVersion
        @CheckLastModifiedDecision.Conditions.IgnoreFileTimestamps.True
        @CheckLastModifiedDecision.Conditions.MediaFile.Changed.LtLastModified
        @CheckLastModifiedDecision.Conditions.MediaFile.LastScanned.NeZeroDate
        @CheckLastModifiedDecision.Result.CreateOrUpdate.False
        @Test
        void c06() throws ExecutionException, IOException {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, true)
                .withString(SKeys.general.index.indexString, INDEX_STRING)
                .withString(SKeys.general.extension.videoFileTypes, null)
                .withString(SKeys.general.extension.musicFileTypes, "mp3")
                .withString(SKeys.general.extension.excludedCoverArt, null)
                .build();
            init();

            MediaFile mediaFile = createMediaFile(MediaFileDao.VERSION);
            mediaFile.setPathString(dir.toString());
            try {
                mediaFile
                    .setChanged(
                            Files.getLastModifiedTime(dir).toInstant().minus(1, ChronoUnit.DAYS));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            mediaFile.setLastScanned(mediaFile.getChanged());

            assertThat("Changed Lt LastModified", mediaFile.getChanged().toEpochMilli(),
                    lessThan(Files.getLastModifiedTime(mediaFile.toPath()).toMillis()));
            assertNotEquals(FAR_PAST, mediaFile.getLastScanned());
            assertTrue(writableMediaFileService.checkLastModified(scanStart, mediaFile).isEmpty());
            Mockito
                .verify(mediaFileDao, Mockito.never())
                .updateMediaFile(Mockito.any(MediaFile.class));
        }

        @CheckLastModifiedDecision.Conditions.MediaFile.Version.GtEqDaoVersion
        @CheckLastModifiedDecision.Conditions.IgnoreFileTimestamps.False
        @CheckLastModifiedDecision.Conditions.MediaFile.Changed.GtEqLastModified
        @CheckLastModifiedDecision.Conditions.MediaFile.LastScanned.EqZeroDate
        @CheckLastModifiedDecision.Result.CreateOrUpdate.True
        @Test
        void c07() throws ExecutionException, IOException {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, false)
                .withString(SKeys.general.index.indexString, INDEX_STRING)
                .withString(SKeys.general.extension.videoFileTypes, null)
                .withString(SKeys.general.extension.musicFileTypes, "mp3")
                .withString(SKeys.general.extension.excludedCoverArt, null)
                .build();
            init();

            MediaFile mediaFile = createMediaFile(MediaFileDao.VERSION);
            mediaFile.setPathString(dir.toString());
            try {
                mediaFile.setChanged(Files.getLastModifiedTime(dir).toInstant());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            mediaFile.setLastScanned(FAR_PAST);
            Mockito
                .when(mediaFileDao.getMediaFile(mediaFile.getPathString()))
                .thenReturn(mediaFile);
            Mockito
                .when(mediaFileDao.updateMediaFile(mediaFile))
                .thenReturn(Optional.of(mediaFile));

            assertEquals(mediaFile.getChanged().toEpochMilli(),
                    Files.getLastModifiedTime(mediaFile.toPath()).toMillis());
            assertEquals(FAR_PAST, mediaFile.getLastScanned());
            assertEquals(mediaFile,
                    writableMediaFileService.checkLastModified(scanStart, mediaFile).get());
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .updateMediaFile(Mockito.any(MediaFile.class));
            Mockito.clearInvocations(mediaFileDao);

            try {
                mediaFile
                    .setChanged(
                            Files.getLastModifiedTime(dir).toInstant().plus(1, ChronoUnit.DAYS));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            assertThat("Changed Gt LastModified", mediaFile.getChanged().toEpochMilli(),
                    greaterThan(Files.getLastModifiedTime(mediaFile.toPath()).toMillis()));
            assertEquals(FAR_PAST, mediaFile.getLastScanned());
            assertEquals(mediaFile,
                    writableMediaFileService.checkLastModified(scanStart, mediaFile).get());
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .updateMediaFile(Mockito.any(MediaFile.class));
        }

        @CheckLastModifiedDecision.Conditions.MediaFile.Version.GtEqDaoVersion
        @CheckLastModifiedDecision.Conditions.IgnoreFileTimestamps.False
        @CheckLastModifiedDecision.Conditions.MediaFile.Changed.GtEqLastModified
        @CheckLastModifiedDecision.Conditions.MediaFile.LastScanned.NeZeroDate
        @CheckLastModifiedDecision.Result.CreateOrUpdate.False
        @Test
        void c08() throws ExecutionException, IOException {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, false)
                .withString(SKeys.general.index.indexString, INDEX_STRING)
                .withString(SKeys.general.extension.videoFileTypes, null)
                .withString(SKeys.general.extension.musicFileTypes, "mp3")
                .withString(SKeys.general.extension.excludedCoverArt, null)
                .build();
            init();

            MediaFile mediaFile = createMediaFile(MediaFileDao.VERSION);
            mediaFile.setPathString(dir.toString());
            try {
                mediaFile
                    .setChanged(Files
                        .getLastModifiedTime(dir)
                        .toInstant()
                        .truncatedTo(ChronoUnit.MILLIS));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            mediaFile.setLastScanned(mediaFile.getChanged());

            assertEquals(mediaFile.getChanged().toEpochMilli(),
                    Files.getLastModifiedTime(mediaFile.toPath()).toMillis());
            assertNotEquals(FAR_PAST, mediaFile.getLastScanned());
            assertTrue(writableMediaFileService.checkLastModified(scanStart, mediaFile).isEmpty());
            Mockito
                .verify(mediaFileDao, Mockito.never())
                .updateMediaFile(Mockito.any(MediaFile.class));

            try {
                mediaFile
                    .setChanged(Files
                        .getLastModifiedTime(dir)
                        .toInstant()
                        .plus(1, ChronoUnit.DAYS)
                        .truncatedTo(ChronoUnit.MILLIS));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            assertThat("Changed Gt LastModified", mediaFile.getChanged().toEpochMilli(),
                    greaterThan(Files.getLastModifiedTime(mediaFile.toPath()).toMillis()));
            assertNotEquals(FAR_PAST, mediaFile.getLastScanned());
            assertTrue(writableMediaFileService.checkLastModified(scanStart, mediaFile).isEmpty());
            Mockito
                .verify(mediaFileDao, Mockito.never())
                .updateMediaFile(Mockito.any(MediaFile.class));

        }

        @CheckLastModifiedDecision.Conditions.MediaFile.Version.GtEqDaoVersion
        @CheckLastModifiedDecision.Conditions.IgnoreFileTimestamps.False
        @CheckLastModifiedDecision.Conditions.MediaFile.Changed.LtLastModified
        @CheckLastModifiedDecision.Conditions.MediaFile.LastScanned.EqZeroDate
        @CheckLastModifiedDecision.Result.CreateOrUpdate.True
        @Test
        void c09() throws ExecutionException, IOException {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, true)
                .withString(SKeys.general.index.indexString, INDEX_STRING)
                .withString(SKeys.general.extension.videoFileTypes, null)
                .withString(SKeys.general.extension.musicFileTypes, "mp3")
                .withString(SKeys.general.extension.excludedCoverArt, null)
                .build();
            init();

            MediaFile mediaFile = createMediaFile(MediaFileDao.VERSION);
            mediaFile.setPathString(dir.toString());
            try {
                mediaFile
                    .setChanged(
                            Files.getLastModifiedTime(dir).toInstant().minus(1, ChronoUnit.DAYS));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            mediaFile.setLastScanned(FAR_PAST);
            Mockito
                .when(mediaFileDao.getMediaFile(mediaFile.getPathString()))
                .thenReturn(mediaFile);
            Mockito
                .when(mediaFileDao.updateMediaFile(mediaFile))
                .thenReturn(Optional.of(mediaFile));

            assertThat("Changed Lt LastModified", mediaFile.getChanged().toEpochMilli(),
                    lessThan(Files.getLastModifiedTime(mediaFile.toPath()).toMillis()));
            assertEquals(FAR_PAST, mediaFile.getLastScanned());
            assertEquals(mediaFile,
                    writableMediaFileService.checkLastModified(scanStart, mediaFile).get());
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .updateMediaFile(Mockito.any(MediaFile.class));
        }

        @CheckLastModifiedDecision.Conditions.MediaFile.Version.GtEqDaoVersion
        @CheckLastModifiedDecision.Conditions.IgnoreFileTimestamps.False
        @CheckLastModifiedDecision.Conditions.MediaFile.Changed.LtLastModified
        @CheckLastModifiedDecision.Conditions.MediaFile.LastScanned.NeZeroDate
        @CheckLastModifiedDecision.Result.CreateOrUpdate.True
        @Test
        void c10() throws ExecutionException, IOException {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, false)
                .withString(SKeys.general.index.indexString, INDEX_STRING)
                .withString(SKeys.general.extension.videoFileTypes, null)
                .withString(SKeys.general.extension.musicFileTypes, "mp3")
                .withString(SKeys.general.extension.excludedCoverArt, null)
                .build();
            init();

            MediaFile mediaFile = createMediaFile(MediaFileDao.VERSION);
            mediaFile.setPathString(dir.toString());
            try {
                mediaFile
                    .setChanged(
                            Files.getLastModifiedTime(dir).toInstant().minus(1, ChronoUnit.DAYS));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            mediaFile.setLastScanned(mediaFile.getChanged());
            Mockito
                .when(mediaFileDao.getMediaFile(mediaFile.getPathString()))
                .thenReturn(mediaFile);
            Mockito
                .when(mediaFileDao.updateMediaFile(mediaFile))
                .thenReturn(Optional.of(mediaFile));

            assertThat("Changed Lt LastModified", mediaFile.getChanged().toEpochMilli(),
                    lessThan(Files.getLastModifiedTime(mediaFile.toPath()).toMillis()));
            assertNotEquals(FAR_PAST, mediaFile.getLastScanned());
            assertEquals(mediaFile,
                    writableMediaFileService.checkLastModified(scanStart, mediaFile).get());
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .updateMediaFile(Mockito.any(MediaFile.class));
        }
    }

    // @Nested
    // class UpdateChildrenTest {
    //
    // @Test
    // void testUpdateChildren() throws URISyntaxException {
    //
    // assertTrue(mediaFileService.isSchemeLastModified());
    // Path dir = createPath("/MEDIAS/Music2/_DIR_ chrome hoof - 2004");
    // assertTrue(Files.isDirectory(dir));
    //
    // MediaFile album = mediaFileService.createMediaFile(dir);
    // assertEquals(FAR_PAST, album.getChildrenLastUpdated());
    // assertThat("Initial value had been assigned to 'changed'.",
    // album.getChanged().toEpochMilli(),
    // greaterThan(album.getChildrenLastUpdated().toEpochMilli()));
    // Mockito.clearInvocations(mediaFileDao);
    //
    // // Typical case where an update is performed
    // mediaFileService.updateChildren(album);
    // Mockito.verify(mediaFileDao,
    // Mockito.times(1)).getChildrenOf(Mockito.anyString());
    // Mockito.verify(mediaFileDao,
    // Mockito.times(3)).updateMediaFile(Mockito.any(MediaFile.class));
    // Mockito.verify(mediaFileDao,
    // Mockito.never()).deleteMediaFile(Mockito.anyString());
    // Mockito.clearInvocations(mediaFileDao);
    //
    // // Typical case where an update isn't performed
    // album.setChildrenLastUpdated(now()); // If it has already been executed, etc.
    // mediaFileService.updateChildren(album);
    // Mockito.verify(mediaFileDao,
    // Mockito.never()).getChildrenOf(Mockito.anyString());
    // Mockito.verify(mediaFileDao,
    // Mockito.never()).updateMediaFile(Mockito.any(MediaFile.class));
    // Mockito.verify(mediaFileDao,
    // Mockito.never()).deleteMediaFile(Mockito.anyString());
    //
    // Mockito.when(settingsService.getFileModifiedCheckSchemeName())
    // .thenReturn(FileModifiedCheckScheme.LAST_SCANNED.name());
    // assertFalse(mediaFileService.isSchemeLastModified());
    //
    // /*
    // * If Scheme is set to Last Scaned, Only updated if childrenLastUpdated is
    // zero (zero = initial value or
    // * immediately after reset)
    // */
    // mediaFileService.updateChildren(album);
    // Mockito.verify(mediaFileDao,
    // Mockito.never()).getChildrenOf(Mockito.anyString());
    // Mockito.verify(mediaFileDao,
    // Mockito.never()).updateMediaFile(Mockito.any(MediaFile.class));
    // Mockito.verify(mediaFileDao,
    // Mockito.never()).deleteMediaFile(Mockito.anyString());
    //
    // album.setChildrenLastUpdated(FAR_PAST);
    // mediaFileService.updateChildren(album);
    // Mockito.verify(mediaFileDao,
    // Mockito.times(1)).getChildrenOf(Mockito.anyString());
    // Mockito.verify(mediaFileDao,
    // Mockito.times(3)).updateMediaFile(Mockito.any(MediaFile.class));
    // Mockito.verify(mediaFileDao,
    // Mockito.never()).deleteMediaFile(Mockito.anyString());
    // }
    // }
    //

    @Nested
    class CreateMediaFileTest {

        @Test
        void testCreateMediaFile() throws URISyntaxException, IOException {
            Path path = createPath("/MEDIAS/Music2/_DIR_ chrome hoof - 2004/10 telegraph hill.mp3");
            assertFalse(Files.isDirectory(path));

            Instant scanStart = now();

            ArgumentCaptor<MediaFile> mediaFileCaptor = ArgumentCaptor.forClass(MediaFile.class);
            Mockito
                .when(mediaFileDao.createMediaFile(mediaFileCaptor.capture()))
                .thenReturn(new MediaFile());

            writableMediaFileService.createMediaFile(scanStart, path);

            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .createMediaFile(Mockito.any(MediaFile.class));
            MediaFile mediaFile = mediaFileCaptor.getValue();

            assertEquals(Files.getLastModifiedTime(mediaFile.toPath()).toMillis(),
                    mediaFile.getChanged().toEpochMilli());
            assertEquals(Files.getLastModifiedTime(mediaFile.toPath()).toMillis(),
                    mediaFile.getCreated().toEpochMilli());
            assertEquals(scanStart.toEpochMilli(), mediaFile.getLastScanned().toEpochMilli());
            assertEquals(FAR_PAST.toEpochMilli(),
                    mediaFile.getChildrenLastUpdated().toEpochMilli());
            assertEquals(0, mediaFile.getPlayCount());
            assertNull(mediaFile.getLastPlayed());
            assertNull(mediaFile.getComment());

            // Update case
            mediaFile.setPlayCount(100);
            Instant lastPlayed = now();
            mediaFile.setLastPlayed(lastPlayed);
            mediaFile.setComment("comment");
            Mockito
                .when(mediaFileDao.getMediaFile(mediaFile.getPathString()))
                .thenReturn(mediaFile);

            Mockito.clearInvocations(mediaFileDao);
            mediaFileCaptor = ArgumentCaptor.forClass(MediaFile.class);
            Mockito
                .when(mediaFileDao.updateMediaFile(mediaFileCaptor.capture()))
                .thenReturn(Optional.empty());

            Instant statsUpdated = now();
            writableMediaFileService.refreshMediaFile(statsUpdated, mediaFile);
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .updateMediaFile(Mockito.any(MediaFile.class));
            mediaFile = mediaFileCaptor.getValue();

            assertEquals(Files.getLastModifiedTime(mediaFile.toPath()).toMillis(),
                    mediaFile.getChanged().toEpochMilli());
            assertEquals(Files.getLastModifiedTime(mediaFile.toPath()).toMillis(),
                    mediaFile.getCreated().toEpochMilli());
            assertEquals(statsUpdated, mediaFile.getLastScanned());
            assertEquals(FAR_PAST, mediaFile.getChildrenLastUpdated());
            assertEquals(100, mediaFile.getPlayCount());
            assertEquals(lastPlayed.toEpochMilli(), mediaFile.getLastPlayed().toEpochMilli());
            assertEquals("comment", mediaFile.getComment());
        }

        @Test
        void testApplyFile() throws URISyntaxException {

            final Instant scanStart = now();

            // Newly created case
            ArgumentCaptor<MediaFile> mfCaptor = ArgumentCaptor.forClass(MediaFile.class);
            Mockito.doReturn(null).when(mediaFileDao).createMediaFile(mfCaptor.capture());

            Path path = createPath("/MEDIAS/Music2/_DIR_ chrome hoof - 2004/10 telegraph hill.mp3");
            assertFalse(Files.isDirectory(path));
            writableMediaFileService.createMediaFile(now(), path);
            MediaFile mediaFile = mfCaptor.getValue();

            assertThat("Because the parsed time is recorded.",
                    mediaFile.getLastScanned().toEpochMilli(),
                    greaterThanOrEqualTo(scanStart.toEpochMilli()));
            assertEquals(FAR_PAST.toEpochMilli(),
                    mediaFile.getChildrenLastUpdated().toEpochMilli());

            // Update case
            Mockito.when(mediaFileDao.createMediaFile(mediaFile)).thenReturn(mediaFile);
            mediaFile = writableMediaFileService.createMediaFile(now(), path).get();
            assertThat("Because the parsed time is set.", mediaFile.getLastScanned().toEpochMilli(),
                    greaterThanOrEqualTo(scanStart.toEpochMilli()));
            assertEquals(FAR_PAST.toEpochMilli(),
                    mediaFile.getChildrenLastUpdated().toEpochMilli());
        }

        @Test
        void testApplyDirWithoutChild() throws URISyntaxException {

            String indexStr = "A B C D E F G H I J K L M N O P Q R S T U V W X-Z(XYZ)";
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(SKeys.musicFolder.scan.ignoreFileTimestamps, true)
                .withString(SKeys.general.index.indexString, indexStr)
                .withString(SKeys.general.extension.videoFileTypes, null)
                .withString(SKeys.general.extension.musicFileTypes, "mp3")
                .withString(SKeys.general.extension.excludedCoverArt, null)
                .build();
            init();

            Path path = createPath("/MEDIAS/Music2");
            assertTrue(Files.isDirectory(path));
            final Instant scanStart = now();

            // Newly created case
            ArgumentCaptor<MediaFile> mfCaptor = ArgumentCaptor.forClass(MediaFile.class);
            Mockito.doReturn(null).when(mediaFileDao).createMediaFile(mfCaptor.capture());
            writableMediaFileService.createMediaFile(scanStart, path);
            MediaFile mediaFile = mfCaptor.getValue();
            assertThat("Because the parsed time is set.", scanStart.toEpochMilli(),
                    greaterThanOrEqualTo(mediaFile.getLastScanned().toEpochMilli()));
            assertEquals(FAR_PAST.toEpochMilli(),
                    mediaFile.getChildrenLastUpdated().toEpochMilli());

            // Update case
            Mockito.when(mediaFileDao.getMediaFile(path.toString())).thenReturn(mediaFile);
            Mockito.doReturn(null).when(mediaFileDao).createMediaFile(mfCaptor.capture());
            writableMediaFileService.createMediaFile(scanStart, path);
            mediaFile = mfCaptor.getValue();
            assertThat("Because the parsed time is set.", scanStart.toEpochMilli(),
                    greaterThanOrEqualTo(mediaFile.getLastScanned().toEpochMilli()));
            assertEquals(FAR_PAST.toEpochMilli(),
                    mediaFile.getChildrenLastUpdated().toEpochMilli());
        }

        @Test
        void testApplyDirWithChild() throws URISyntaxException {
            MusicParser musicParser = mock(MusicParser.class);
            Mockito
                .when(musicParser.getMetaData(Mockito.any(Path.class)))
                .thenReturn(new MetaData());
            Mockito
                .when(libraryAccessPolicy.isReadAllowed(Mockito.any(Path.class)))
                .thenReturn(true);

            Path dir = createPath("/MEDIAS/Music2/_DIR_ chrome hoof - 2004");
            assertTrue(Files.isDirectory(dir));

            Instant scanStart = now();

            final ArgumentCaptor<String> pathsCaptor = ArgumentCaptor.forClass(String.class);
            final ArgumentCaptor<MediaFile> mediaFileCaptor = ArgumentCaptor
                .forClass(MediaFile.class);

            writableMediaFileService.createMediaFile(scanStart, dir);

            // Because firstChild is NOT parsed(v111.6.0)
            Mockito.verify(musicParser, Mockito.never()).getMetaData(Mockito.any(Path.class));

            /*
             * Because firstChild is registered. Since firstChild is registered at this
             * time, firstChild will not be parsed when updateChildren is executed.
             */
            Mockito
                .verify(mediaFileDao, Mockito.times(1))
                .createMediaFile(mediaFileCaptor.capture());
            // [Windows] assertEquals("02 eyes like dull hazlenuts",
            // mediaFileCaptor.getValue().getName());
            // [Linux] assertEquals("10 telegraph hill",
            // mediaFileCaptor.getValue().getName());

            // never(v111.6.0)
            Mockito.verify(mediaFileDao, Mockito.never()).getMediaFile(pathsCaptor.capture());
        }
    }
}

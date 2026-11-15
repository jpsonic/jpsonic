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

package com.tesshu.jpsonic.feature.filesystem;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Arrays;

import com.tesshu.jpsonic.domain.model.MediaFile;
import com.tesshu.jpsonic.domain.provider.resource.MediaFileProvider;
import com.tesshu.jpsonic.infrastructure.filesystem.PathInspector;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.MusicFolder;
import com.tesshu.jpsonic.service.MusicFolderService;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LibraryAccessPolicyTest {

    private SettingsFacade settingsFacade;
    private LibraryAccessPolicy service;
    private PathInspector pathInspector;
    private MusicFolderService musicFolderService;
    private MediaFileProvider mediaFileProvider;

    @BeforeEach
    void setup() {
        settingsFacade = SettingsFacadeBuilder.create().build();
        pathInspector = new PathInspector();
        musicFolderService = mock(MusicFolderService.class);
        mediaFileProvider = mock(MediaFileProvider.class);
    }

    @Ignore
    void init() {
        service = new LibraryAccessPolicy(settingsFacade, pathInspector, musicFolderService,
                mediaFileProvider);
    }

    @Test
    void testIsWriteAllowed() {
        settingsFacade = SettingsFacadeBuilder
            .create()
            .withString(SKeys.podcast.folder, "")
            .build();
        init();
        assertThrows(IllegalArgumentException.class, () -> service.isWriteAllowed(null));
        assertThrows(IllegalArgumentException.class, () -> service.isWriteAllowed(Path.of("/")));
        assertFalse(service.isWriteAllowed(Path.of("")));
        when(musicFolderService.getAllMusicFolders(false, true))
            .thenReturn(Arrays.asList(new MusicFolder("/test", "test", true, null, false)));
        assertTrue(service.isWriteAllowed(Path.of("/test/cover.jpg")));
    }

    @ParameterizedTest(name = "podcast={0}, accessible={1} -> {2}")
    // spotless:off
    @CsvSource({
            // Podcast: access is always allowed
            "true,  true,  true",
            "true,  false, true",
            // Non-podcast: access depends on the provider
            "false, true,  true",
            "false, false, false"
    })
    // spotless:on
    void testCanAccessMediaFile(boolean inPodcastFolder, boolean accessible, boolean expected) {
        SettingsFacade settingsFacade = SettingsFacadeBuilder.create().buildWithDefault();

        PathInspector pathInspector = mock(PathInspector.class);
        MusicFolderService musicFolderService = mock(MusicFolderService.class);
        MediaFileProvider mediaFileProvider = mock(MediaFileProvider.class);

        Path path = Path.of("/podcast/episode.mp3");

        when(settingsFacade.get(SKeys.podcast.folder)).thenReturn("/podcast");
        when(pathInspector.isWithinHierarchy(path.toString(), "/podcast"))
            .thenReturn(inPodcastFolder);
        when(mediaFileProvider.existsAccessibleMediaFile(anyString(), any()))
            .thenReturn(accessible);

        LibraryAccessPolicy policy = new LibraryAccessPolicy(settingsFacade, pathInspector,
                musicFolderService, mediaFileProvider);

        MediaFile mediaFile = mock(MediaFile.class);
        when(mediaFile.toPath()).thenReturn(path);

        assertThat(policy.canAccessMediaFile("user", mediaFile)).isEqualTo(expected);
    }
}

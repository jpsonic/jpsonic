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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.feature.filesystem.LibraryAccessPolicy;
import com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile.MediaType;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.RatingService;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.ModelAndView;

class MainControllerTest {

    private LibraryAccessPolicy libraryAccessPolicy;
    private MockMvc mockMvc;

    private MediaFile root;
    private MediaFile artist;
    private MediaFile album;
    private MediaFile song;

    @BeforeEach
    void setup() throws ExecutionException, URISyntaxException {
        libraryAccessPolicy = mock(LibraryAccessPolicy.class);
        UserService userService = mock(UserService.class);
        MediaFileService mediaFileService = mock(MediaFileService.class);
        SettingsFacade settingsFacade = SettingsFacadeBuilder.create().build();
        MainController controller = new MainController(settingsFacade, libraryAccessPolicy,
                userService, mock(JpsonicComparators.class), mock(RatingService.class),
                mediaFileService, mock(ViewAsListSelector.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        root = new MediaFile();
        int rootId = 100;
        Path rootPath = Path.of(MainControllerTest.class.getResource("/MEDIAS").toURI());
        root.setId(rootId);
        root.setPathString(rootPath.toString());
        root.setMediaType(MediaType.DIRECTORY);
        Mockito.when(mediaFileService.getMediaFile(rootPath)).thenReturn(root);
        Mockito.when(mediaFileService.getMediaFile(rootId)).thenReturn(root);
        Mockito.when(mediaFileService.isRoot(root)).thenReturn(true);

        artist = new MediaFile();
        int artistId = 200;
        String artistPath = Path
            .of(MainControllerTest.class.getResource("/MEDIAS/Music").toURI())
            .toString();
        artist.setId(artistId);
        artist.setPathString(artistPath);
        artist.setMediaType(MediaType.DIRECTORY);

        Mockito.when(mediaFileService.getMediaFile(artistPath)).thenReturn(artist);
        Mockito.when(mediaFileService.getMediaFile(artistId)).thenReturn(artist);
        Mockito.when(mediaFileService.isRoot(artist)).thenReturn(false);
        Mockito
            .when(libraryAccessPolicy.isFolderAccessAllowed(artist, ServiceMockUtils.ADMIN_NAME))
            .thenReturn(true);
        Mockito
            .when(mediaFileService.getChildrenOf(artist, true, true))
            .thenReturn(Arrays.asList(album));
        Mockito.when(mediaFileService.getParentOf(artist)).thenReturn(root);

        album = new MediaFile();
        int albumId = 300;
        String albumPath = Path
            .of(MainControllerTest.class
                .getResource("/MEDIAS/Music/_DIR_ Ravel/_DIR_ Ravel - Chamber Music With Voice")
                .toURI())
            .toString();
        album.setId(albumId);
        album.setPathString(albumPath);
        album.setMediaType(MediaType.ALBUM);

        Mockito.when(mediaFileService.getMediaFile(albumPath)).thenReturn(album);
        Mockito.when(mediaFileService.getMediaFile(albumId)).thenReturn(album);
        Mockito.when(mediaFileService.isRoot(album)).thenReturn(false);
        Mockito
            .when(libraryAccessPolicy.isFolderAccessAllowed(album, ServiceMockUtils.ADMIN_NAME))
            .thenReturn(true);
        Mockito
            .when(mediaFileService.getChildrenOf(artist, true, true))
            .thenReturn(Arrays.asList(album));

        song = new MediaFile();
        int songId = 400;
        String songPath = Path
            .of(MainControllerTest.class
                .getResource(
                        "/MEDIAS/Music/_DIR_ Ravel/_DIR_ Ravel - Chamber Music With Voice/01 - Sonata Violin & Cello I. Allegro.ogg")
                .toURI())
            .toString();
        song.setId(songId);
        song.setPathString(songPath);
        song.setMediaType(MediaType.MUSIC);
        Mockito.when(mediaFileService.getMediaFile(songPath)).thenReturn(song);
        Mockito.when(mediaFileService.getMediaFile(songId)).thenReturn(song);
        Mockito.when(mediaFileService.isRoot(song)).thenReturn(false);
        Mockito.when(mediaFileService.getParentOf(song)).thenReturn(album);
        Mockito
            .when(mediaFileService.getChildrenOf(album, true, true))
            .thenReturn(Arrays.asList(song));
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    @SuppressWarnings("unchecked")
    void testGet() throws Exception {

        // with ID
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders
                .get("/" + ViewName.MAIN.value())
                .param(Attributes.Request.ID.value(), Integer.toString(album.getId())))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals("albumMain", modelAndView.getViewName());
        Map<String, Object> model = (Map<String, Object>) modelAndView
            .getModel()
            .get(Attributes.Model.VALUE);
        assertNotNull(model);

        // ... or path
        result = mockMvc
            .perform(MockMvcRequestBuilders
                .get("/" + ViewName.MAIN.value())
                .param(Attributes.Request.PATH.value(), album.getPathString()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        modelAndView = result.getModelAndView();
        assertEquals("albumMain", modelAndView.getViewName());
        model = (Map<String, Object>) modelAndView.getModel().get(Attributes.Model.VALUE);
        assertNotNull(model);

        // Returns parent if not directory.
        mockMvc
            .perform(MockMvcRequestBuilders
                .get("/" + ViewName.MAIN.value())
                .param(Attributes.Request.ID.value(), Integer.toString(song.getId())))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        modelAndView = result.getModelAndView();
        assertEquals("albumMain", modelAndView.getViewName());
        model = (Map<String, Object>) modelAndView.getModel().get(Attributes.Model.VALUE);
        assertNotNull(model);
    }

    /*
     * Verify the properties related to Children of the target media file
     */
    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    @SuppressWarnings("unchecked")
    void testGetForChildren() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders
                .get("/" + ViewName.MAIN.value())
                .param(Attributes.Request.ID.value(), Integer.toString(artist.getId())))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals("artistMain", modelAndView.getViewName());
        Map<String, Object> model = (Map<String, Object>) modelAndView
            .getModel()
            .get(Attributes.Model.VALUE);
        assertNotNull(model);

        assertEquals(0, ((List<MediaFile>) model.get("files")).size());
        List<MediaFile> subDirs = (List<MediaFile>) model.get("subDirs");
        assertEquals(1, subDirs.size());
        assertEquals(album, subDirs.get(0));
        assertEquals(root, (MediaFile) model.get("parent"));
        assertFalse((Boolean) model.get("navigateUpAllowed"));
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    @SuppressWarnings("PMD.UnitTestShouldIncludeAssert")
    void testGetFail() throws Exception {

        mockMvc
            .perform(MockMvcRequestBuilders
                .get("/" + ViewName.MAIN.value())
                .param(Attributes.Request.ID.value(), Integer.toString(-1)))
            .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
            .andExpect(MockMvcResultMatchers.redirectedUrl(ViewName.NOTFOUND.value()));

        Mockito
            .when(libraryAccessPolicy.isFolderAccessAllowed(album, ServiceMockUtils.ADMIN_NAME))
            .thenReturn(false);
        mockMvc
            .perform(MockMvcRequestBuilders
                .get("/" + ViewName.MAIN.value())
                .param(Attributes.Request.ID.value(), Integer.toString(album.getId())))
            .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
            .andExpect(MockMvcResultMatchers.redirectedUrl(ViewName.ACCESS_DENIED.value()));

        // Redirect to home if root directory.
        mockMvc
            .perform(MockMvcRequestBuilders
                .get("/" + ViewName.MAIN.value())
                .param(Attributes.Request.ID.value(), Integer.toString(root.getId())))
            .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
            .andExpect(MockMvcResultMatchers.redirectedUrl(ViewName.HOME.value() + "?"));
    }
}

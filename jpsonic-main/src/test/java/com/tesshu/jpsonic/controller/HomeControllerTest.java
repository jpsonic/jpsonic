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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.domain.model.Genre;
import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.model.MusicFolderContent;
import com.tesshu.jpsonic.domain.provider.master.GenreMasterProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicIndexProvider;
import com.tesshu.jpsonic.domain.provider.state.ScannerStateProvider;
import com.tesshu.jpsonic.domain.system.AlbumListType;
import com.tesshu.jpsonic.infrastructure.scanner.ScannerStateServiceImpl;
import com.tesshu.jpsonic.infrastructure.search.LegacySearch;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.MediaFile;
import com.tesshu.jpsonic.service.MediaFileService;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.RatingService;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.servlet.ModelAndView;

@SuppressWarnings("PMD.TooManyStaticImports")
class HomeControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setup() throws ExecutionException {
        SettingsFacade settingsFacade = SettingsFacadeBuilder.create().build();
        mockMvc = MockMvcBuilders
            .standaloneSetup(new HomeController(settingsFacade, mock(UserService.class),
                    mock(MusicFolderService.class), mock(ScannerStateServiceImpl.class),
                    mock(RatingService.class), mock(MediaFileService.class),
                    mock(LegacySearch.class), mock(GenreMasterProvider.class),
                    mock(MusicFolderProvider.class), mock(MusicIndexProvider.class)))
            .build();
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testHandleRequestInternal() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.HOME.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals("home", modelAndView.getViewName());

        @SuppressWarnings("unchecked")
        Map<String, Object> model = (Map<String, Object>) modelAndView.getModel().get("model");
        assertNotNull(model);
    }

    @Nested
    class GetTest {

        private RatingService ratingService;
        private MediaFileService mediaFileService;
        private LegacySearch legacySearch;
        private GenreMasterProvider genreMasterProvider;
        private MusicIndexProvider musicIndexProvider;
        private HomeController controller;

        @BeforeEach
        void setup() throws ExecutionException {
            ratingService = mock(RatingService.class);
            mediaFileService = mock(MediaFileService.class);
            legacySearch = mock(LegacySearch.class);
            genreMasterProvider = mock(GenreMasterProvider.class);
            musicIndexProvider = mock(MusicIndexProvider.class);
            SettingsFacade settingsFacade = SettingsFacadeBuilder.create().build();
            UserService userService = mock(UserService.class);
            ScannerStateProvider scannerStateProvider = mock(ScannerStateProvider.class);
            controller = new HomeController(settingsFacade, userService,
                    mock(MusicFolderService.class), scannerStateProvider, ratingService,
                    mediaFileService, legacySearch, genreMasterProvider,
                    mock(MusicFolderProvider.class), musicIndexProvider);
        }

        @Test
        void testHighest() throws ServletRequestBindingException {
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            Mockito
                .when(req.getParameter(Attributes.Request.LIST_TYPE.value()))
                .thenReturn(AlbumListType.HIGHEST.getId());
            controller.handleRequestInternal(req);
            Mockito
                .verify(ratingService, Mockito.times(1))
                .getHighestRatedAlbums(anyInt(), anyInt(), anyList());
        }

        @Test
        void testFrequent() throws ServletRequestBindingException {
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            Mockito
                .when(req.getParameter(Attributes.Request.LIST_TYPE.value()))
                .thenReturn(AlbumListType.FREQUENT.getId());
            controller.handleRequestInternal(req);
            Mockito
                .verify(mediaFileService, Mockito.times(1))
                .getMostFrequentlyPlayedAlbums(anyInt(), anyInt(), anyList());
        }

        @Test
        void testRecent() throws ServletRequestBindingException {
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            Mockito
                .when(req.getParameter(Attributes.Request.LIST_TYPE.value()))
                .thenReturn(AlbumListType.RECENT.getId());

            MediaFile album = new MediaFile();
            album.setId(1);
            Mockito
                .when(mediaFileService.getMostRecentlyPlayedAlbums(anyInt(), anyInt(), anyList()))
                .thenReturn(Arrays.asList(album));

            controller.handleRequestInternal(req);
            Mockito
                .verify(mediaFileService, Mockito.times(1))
                .getMostRecentlyPlayedAlbums(anyInt(), anyInt(), anyList());
            Mockito.clearInvocations(mediaFileService);

            album.setLastPlayed(now());
            Mockito
                .when(mediaFileService.getMostRecentlyPlayedAlbums(anyInt(), anyInt(), anyList()))
                .thenReturn(Arrays.asList(album));
            controller.handleRequestInternal(req);
            Mockito
                .verify(mediaFileService, Mockito.times(1))
                .getMostRecentlyPlayedAlbums(anyInt(), anyInt(), anyList());
        }

        @Test
        void testNewest() throws ServletRequestBindingException {
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            Mockito
                .when(req.getParameter(Attributes.Request.LIST_TYPE.value()))
                .thenReturn(AlbumListType.NEWEST.getId());

            MediaFile album = new MediaFile();
            album.setId(1);
            album.setCreated(now());
            Mockito
                .when(mediaFileService.getNewestAlbums(anyInt(), anyInt(), anyList()))
                .thenReturn(Arrays.asList(album));

            controller.handleRequestInternal(req);
            Mockito
                .verify(mediaFileService, Mockito.times(1))
                .getNewestAlbums(anyInt(), anyInt(), anyList());
        }

        @Test
        void testStarred() throws ServletRequestBindingException {
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            Mockito
                .when(req.getParameter(Attributes.Request.LIST_TYPE.value()))
                .thenReturn(AlbumListType.STARRED.getId());
            controller.handleRequestInternal(req);
            Mockito
                .verify(mediaFileService, Mockito.times(1))
                .getStarredAlbums(anyInt(), anyInt(), anyString(), anyList());
        }

        @Test
        void testRandom() throws ServletRequestBindingException {
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            Mockito
                .when(req.getParameter(Attributes.Request.LIST_TYPE.value()))
                .thenReturn(AlbumListType.RANDOM.getId());
            controller.handleRequestInternal(req);
            Mockito.verify(legacySearch, Mockito.times(1)).getRandomAlbums(anyInt(), anyList());
        }

        @Test
        void testAlphabetical() throws ServletRequestBindingException {
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            Mockito
                .when(req.getParameter(Attributes.Request.LIST_TYPE.value()))
                .thenReturn(AlbumListType.ALPHABETICAL.getId());
            controller.handleRequestInternal(req);
            Mockito
                .verify(mediaFileService, Mockito.times(1))
                .getAlphabeticalAlbums(anyInt(), anyInt(), anyBoolean(), anyList());
        }

        @Test
        void testDecade() throws ServletRequestBindingException {
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            Mockito
                .when(req.getParameter(Attributes.Request.LIST_TYPE.value()))
                .thenReturn(AlbumListType.DECADE.getId());
            controller.handleRequestInternal(req);
            Mockito
                .verify(mediaFileService, Mockito.times(1))
                .getAlbumsByYear(anyInt(), anyInt(), anyInt(), anyInt(), anyList());
        }

        @Test
        void testGenre() throws ServletRequestBindingException {
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            Mockito
                .when(req.getParameter(Attributes.Request.LIST_TYPE.value()))
                .thenReturn(AlbumListType.GENRE.getId());
            List<Genre> genres = Arrays.asList(new Genre("pops", 0, 0));
            Mockito
                .when(genreMasterProvider.getLegacyGenres(true, 0, Integer.MAX_VALUE))
                .thenReturn(genres);
            controller.handleRequestInternal(req);
            Mockito
                .verify(legacySearch, Mockito.times(1))
                .getAlbumsByGenres(anyString(), anyLong(), anyLong(), anyList());
        }

        @Test
        void testIndex() throws ServletRequestBindingException {
            MockHttpServletRequest req = mock(MockHttpServletRequest.class);
            Mockito
                .when(req.getParameter(Attributes.Request.LIST_TYPE.value()))
                .thenReturn(AlbumListType.INDEX.getId());
            Mockito
                .when(musicIndexProvider
                    .findMusicFolderContent(anyList(),
                            ArgumentMatchers
                                .any(com.tesshu.jpsonic.domain.model.MediaFile.Type[].class)))
                .thenReturn(new MusicFolderContent(new TreeMap<>(), Collections.emptyList()));
            controller.handleRequestInternal(req);
            ArgumentCaptor<List<MusicFolder>> listCaptor = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<com.tesshu.jpsonic.domain.model.MediaFile.Type[]> varargsCaptor = ArgumentCaptor
                .forClass(com.tesshu.jpsonic.domain.model.MediaFile.Type[].class);
            Mockito
                .verify(musicIndexProvider, Mockito.times(1))
                .findMusicFolderContent(listCaptor.capture(), varargsCaptor.capture());
        }
    }
}

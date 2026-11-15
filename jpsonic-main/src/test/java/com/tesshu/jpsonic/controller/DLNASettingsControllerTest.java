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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.lang.annotation.Documented;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.tesshu.jpsonic.controller.form.DLNASettingsCommand;
import com.tesshu.jpsonic.domain.model.AuthKey;
import com.tesshu.jpsonic.domain.model.AuthKey.AuthKeyType;
import com.tesshu.jpsonic.domain.model.MenuItem.ViewType;
import com.tesshu.jpsonic.domain.model.TranscodingDefinition.BitRateLimit;
import com.tesshu.jpsonic.domain.provider.resource.ServerLocaleProvider;
import com.tesshu.jpsonic.domain.system.MenuItemId;
import com.tesshu.jpsonic.domain.type.GenreMasterSort;
import com.tesshu.jpsonic.feature.crypt.upnp.KeyRotationType;
import com.tesshu.jpsonic.feature.crypt.upnp.UpnpKeyManager;
import com.tesshu.jpsonic.feature.upnp.UPnPSKeys;
import com.tesshu.jpsonic.feature.upnp.UPnPService;
import com.tesshu.jpsonic.infrastructure.locale.ServerLocaleManager;
import com.tesshu.jpsonic.infrastructure.menu.MenuItemData;
import com.tesshu.jpsonic.infrastructure.menu.MenuItemManager;
import com.tesshu.jpsonic.infrastructure.menu.MenuItemManager.MenuItemWithDefaultName;
import com.tesshu.jpsonic.infrastructure.search.UPnPSearchMethod;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import com.tesshu.jpsonic.persistence.api.entity.Player;
import com.tesshu.jpsonic.persistence.core.repository.MenuItemDaoImpl;
import com.tesshu.jpsonic.service.MusicFolderService;
import com.tesshu.jpsonic.service.PlayerService;
import com.tesshu.jpsonic.service.ServiceMockUtils;
import com.tesshu.jpsonic.service.ShareService;
import com.tesshu.jpsonic.service.TranscodingService;
import com.tesshu.jpsonic.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Ignore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals" }) // pmd/pmd#4616
class DLNASettingsControllerTest {

    private SettingsFacade settingsFacade;
    private ServerLocaleProvider serverLocaleProvider;
    private MusicFolderService musicFolderService;
    private PlayerService playerService;
    private UPnPService upnpService;
    private UpnpKeyManager upnpKeyManager;
    private DLNASettingsController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() throws ExecutionException {
        settingsFacade = SettingsFacadeBuilder.create().buildWithDefault();
        init();
    }

    @Ignore
    void init() {
        serverLocaleProvider = new ServerLocaleManager(settingsFacade);
        musicFolderService = mock(MusicFolderService.class);
        playerService = mock(PlayerService.class);
        upnpService = mock(UPnPService.class);
        upnpKeyManager = mock(UpnpKeyManager.class);
        when(upnpKeyManager.getKey()).thenReturn("dummyKey");
        AuthKey authKey = new AuthKey(AuthKeyType.UPNP, "dummyKey", Instant.now());
        when(upnpKeyManager.getAuthKey()).thenReturn(authKey);

        controller = new DLNASettingsController(settingsFacade, musicFolderService,
                mock(UserService.class), playerService, mock(TranscodingService.class), upnpService,
                mock(ShareService.class), mock(MenuItemManager.class), upnpKeyManager,
                mock(OutlineHelpSelector.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testGet() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.DLNA_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals("dlnaSettings", modelAndView.getViewName());

        DLNASettingsCommand command = (DLNASettingsCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);
    }

    @WithMockUser(username = ServiceMockUtils.ADMIN_NAME)
    @Test
    void testPost() throws Exception {
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/" + ViewName.DLNA_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
        assertNotNull(result);
        ModelAndView modelAndView = result.getModelAndView();
        assertEquals("dlnaSettings", modelAndView.getViewName());

        DLNASettingsCommand command = (DLNASettingsCommand) modelAndView
            .getModelMap()
            .get(Attributes.Model.Command.VALUE);
        assertNotNull(command);

        result = mockMvc
            .perform(MockMvcRequestBuilders
                .post("/" + ViewName.DLNA_SETTINGS.value())
                .flashAttr("model", command))
            .andExpect(MockMvcResultMatchers.status().isFound())
            .andExpect(MockMvcResultMatchers.redirectedUrl(ViewName.DLNA_SETTINGS.value()))
            .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
            .andReturn();
        assertNotNull(result);
    }

    /*
     * Register OFF to scheme if all Transcoding is disabled. If not, register the
     * input value.
     */
    @Test
    void testTranscoding() throws Exception {
        DLNASettingsCommand command = new DLNASettingsCommand();
        command.setActiveTranscodingIds(0);
        command.setBitRateLimit(BitRateLimit.MAX_1411);
        command.setTopMenuItems(Collections.emptyList());
        command.setSubMenuItems(Collections.emptyList());
        command.setAlbumGenreSort(GenreMasterSort.FREQUENCY);
        command.setSongGenreSort(GenreMasterSort.FREQUENCY);
        command.setSearchMethod(UPnPSearchMethod.FILE_STRUCTURE);
        command.setDlnaKeyRotationType(KeyRotationType.FIXED);
        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        controller.post(command, Mockito.mock(RedirectAttributes.class));
        Mockito.verify(playerService, Mockito.times(1)).updatePlayer(playerCaptor.capture());
        assertEquals(BitRateLimit.MAX_1411, playerCaptor.getValue().getBitRateLimit());
    }

    @Nested
    class SubMenuItemRowInfosTest {

        @Test
        void testEmpty() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(UPnPSKeys.basic.enabled, false)
                .build();
            init();
            musicFolderService = mock(MusicFolderService.class);
            upnpService = mock(UPnPService.class);
            controller = new DLNASettingsController(settingsFacade, musicFolderService,
                    mock(UserService.class), mock(PlayerService.class),
                    mock(TranscodingService.class), upnpService, mock(ShareService.class),
                    mock(MenuItemManager.class), upnpKeyManager, mock(OutlineHelpSelector.class));
            mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
            Model model = new ExtendedModelMap();
            controller
                .formBackingObject(Mockito.mock(HttpServletRequest.class), model, Optional.empty(),
                        Optional.empty());

            DLNASettingsCommand command = (DLNASettingsCommand) model
                .getAttribute(Attributes.Model.Command.VALUE);
            assertEquals(0, command.getSubMenuItems().size());
            assertEquals(0, command.getSubMenuItemRowInfos().size());
        }

        @Test
        void testInfos() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(UPnPSKeys.basic.enabled, false)
                .build();
            init();
            musicFolderService = mock(MusicFolderService.class);

            MenuItemManager menuItemManager = mock(MenuItemManager.class);
            upnpService = mock(UPnPService.class);
            controller = new DLNASettingsController(settingsFacade, musicFolderService,
                    mock(UserService.class), mock(PlayerService.class),
                    mock(TranscodingService.class), upnpService, mock(ShareService.class),
                    menuItemManager, upnpKeyManager, mock(OutlineHelpSelector.class));
            mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

            // Create dummy data
            List<MenuItemWithDefaultName> topMenuItems = new ArrayList<>();
            topMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP, MenuItemId.FOLDER,
                        MenuItemId.ROOT, "", true, 1), "top1"));
            topMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP, MenuItemId.ARTIST,
                        MenuItemId.ROOT, "", true, 2), "top2"));
            topMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP, MenuItemId.ALBUM,
                        MenuItemId.ROOT, "", false, 3), "top3"));
            when(menuItemManager.getTopMenuItems(ViewType.UPNP)).thenReturn(topMenuItems);

            List<MenuItemWithDefaultName> subMenuItems = new ArrayList<>();
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.MEDIA_FILE, MenuItemId.FOLDER, "", true, 1), "sub1"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP, MenuItemId.INDEX,
                        MenuItemId.FOLDER, "", true, 2), "sub2"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.ALBUM_ARTIST, MenuItemId.ARTIST, "", false, 3), "sub3"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.INDEX_ID3, MenuItemId.ARTIST, "", false, 4), "sub4"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.ALBUM_ID3, MenuItemId.ALBUM, "", false, 5), "sub5"));
            when(menuItemManager.getSubMenuItems(ViewType.UPNP)).thenReturn(subMenuItems);
            menuItemManager.getSubMenuItems(ViewType.UPNP);

            // Exec
            Model model = new ExtendedModelMap();
            controller
                .formBackingObject(Mockito.mock(HttpServletRequest.class), model, Optional.empty(),
                        Optional.empty());

            DLNASettingsCommand command = (DLNASettingsCommand) model
                .getAttribute(Attributes.Model.Command.VALUE);
            assertEquals(5, command.getSubMenuItems().size());
            assertEquals(3, command.getSubMenuItemRowInfos().size());

            assertEquals(MenuItemId.MEDIA_FILE,
                    command.getSubMenuItemRowInfos().get(MenuItemId.FOLDER).firstChild().getId());
            assertEquals(2, command.getSubMenuItemRowInfos().get(MenuItemId.FOLDER).count());
            assertEquals(MenuItemId.ALBUM_ARTIST,
                    command.getSubMenuItemRowInfos().get(MenuItemId.ARTIST).firstChild().getId());
            assertEquals(2, command.getSubMenuItemRowInfos().get(MenuItemId.ARTIST).count());
            assertEquals(MenuItemId.ALBUM_ID3,
                    command.getSubMenuItemRowInfos().get(MenuItemId.ALBUM).firstChild().getId());
            assertEquals(1, command.getSubMenuItemRowInfos().get(MenuItemId.ALBUM).count());
        }
    }

    @Nested
    class RandomMaxTest {

        private DLNASettingsCommand command;

        @BeforeEach
        void setup() {
            settingsFacade = SettingsFacadeBuilder.create().build();
            init();
        }

        @Ignore
        void init() {
            controller = new DLNASettingsController(settingsFacade, mock(MusicFolderService.class),
                    mock(UserService.class), mock(PlayerService.class),
                    mock(TranscodingService.class), mock(UPnPService.class),
                    mock(ShareService.class), mock(MenuItemManager.class), upnpKeyManager,
                    mock(OutlineHelpSelector.class));
            command = new DLNASettingsCommand();
            command.setTopMenuItems(Collections.emptyList());
            command.setSubMenuItems(Collections.emptyList());
            command.setAlbumGenreSort(GenreMasterSort.FREQUENCY);
            command.setSongGenreSort(GenreMasterSort.FREQUENCY);
            command.setSearchMethod(UPnPSearchMethod.FILE_STRUCTURE);
            command.setDlnaKeyRotationType(KeyRotationType.FIXED);
        }

        @Test
        void testNull() {
            ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(int.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .captureInt(UPnPSKeys.options.randomMax, captor)
                .build();
            init();

            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(1, captor.getAllValues().size());
            assertEquals(50, captor.getValue());
        }

        @Test
        void testMinus() {
            ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(int.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .captureInt(UPnPSKeys.options.randomMax, captor)
                .build();
            init();

            command.setDlnaRandomMax(-1);
            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(1, captor.getAllValues().size());
            assertEquals(50, captor.getValue());
        }

        @Test
        void test0() {
            ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(int.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .captureInt(UPnPSKeys.options.randomMax, captor)
                .build();
            init();

            command.setDlnaRandomMax(0);
            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(1, captor.getAllValues().size());
            assertEquals(50, captor.getValue());
        }

        @Test
        void test1() {
            ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(int.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .captureInt(UPnPSKeys.options.randomMax, captor)
                .build();
            init();

            command.setDlnaRandomMax(1);
            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(1, captor.getAllValues().size());
            assertEquals(1, captor.getValue());
        }

        @Test
        void test49() {
            ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(int.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .captureInt(UPnPSKeys.options.randomMax, captor)
                .build();
            init();

            command.setDlnaRandomMax(49);
            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(1, captor.getAllValues().size());
            assertEquals(49, captor.getValue());
        }

        @Test
        void test50() {
            ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(int.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .captureInt(UPnPSKeys.options.randomMax, captor)
                .build();
            init();

            command.setDlnaRandomMax(50);
            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(1, captor.getAllValues().size());
            assertEquals(50, captor.getValue());
        }

        @Test
        void test51() {
            ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(int.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .captureInt(UPnPSKeys.options.randomMax, captor)
                .build();
            init();

            command.setDlnaRandomMax(51);
            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(1, captor.getAllValues().size());
            assertEquals(51, captor.getValue());
        }

        @Test
        void test1999() {
            ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(int.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .captureInt(UPnPSKeys.options.randomMax, captor)
                .build();
            init();

            command.setDlnaRandomMax(1999);
            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(1, captor.getAllValues().size());
            assertEquals(1999, captor.getValue());
        }

        @Test
        void test2000() {
            ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(int.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .captureInt(UPnPSKeys.options.randomMax, captor)
                .build();
            init();

            command.setDlnaRandomMax(2000);
            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(1, captor.getAllValues().size());
            assertEquals(1999, captor.getValue());
        }
    }

    @Nested
    class UpdateSubMenuItemsTest {

        @Test
        void testEmpty() {
            settingsFacade = SettingsFacadeBuilder.create().build();
            musicFolderService = mock(MusicFolderService.class);
            upnpService = mock(UPnPService.class);
            MenuItemDaoImpl menuItemDao = mock(MenuItemDaoImpl.class);
            MenuItemManager menuItemManager = new MenuItemManager(serverLocaleProvider, menuItemDao,
                    mock(MessageSource.class));
            controller = new DLNASettingsController(settingsFacade, musicFolderService,
                    mock(UserService.class), mock(PlayerService.class),
                    mock(TranscodingService.class), upnpService, mock(ShareService.class),
                    menuItemManager, upnpKeyManager, mock(OutlineHelpSelector.class));
            mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

            DLNASettingsCommand command = new DLNASettingsCommand();
            command.setTopMenuItems(Collections.emptyList());
            command.setSubMenuItems(Collections.emptyList());
            command.setAlbumGenreSort(GenreMasterSort.FREQUENCY);
            command.setSongGenreSort(GenreMasterSort.FREQUENCY);
            command.setSubMenuItemRowInfos(Collections.emptyMap());
            command.setSearchMethod(UPnPSearchMethod.FILE_STRUCTURE);
            command.setDlnaKeyRotationType(KeyRotationType.FIXED);

            ArgumentCaptor<MenuItemData> menuItemCaptor = ArgumentCaptor
                .forClass(MenuItemData.class);
            Mockito.doNothing().when(menuItemDao).updateMenuItem(menuItemCaptor.capture());

            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(0, menuItemCaptor.getAllValues().size());
        }

        @Test
        void testNoChange() {
            settingsFacade = SettingsFacadeBuilder.create().build();
            musicFolderService = mock(MusicFolderService.class);
            upnpService = mock(UPnPService.class);
            MenuItemDaoImpl menuItemDao = mock(MenuItemDaoImpl.class);
            MenuItemManager menuItemManager = new MenuItemManager(serverLocaleProvider, menuItemDao,
                    mock(MessageSource.class));
            controller = new DLNASettingsController(settingsFacade, musicFolderService,
                    mock(UserService.class), mock(PlayerService.class),
                    mock(TranscodingService.class), upnpService, mock(ShareService.class),
                    menuItemManager, upnpKeyManager, mock(OutlineHelpSelector.class));
            mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

            List<MenuItemWithDefaultName> subMenuItems = new ArrayList<>();
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.MEDIA_FILE, MenuItemId.FOLDER, "", true, 1), "sub1"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP, MenuItemId.INDEX,
                        MenuItemId.FOLDER, "", true, 2), "sub2"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.ALBUM_ARTIST, MenuItemId.ARTIST, "", false, 3), "sub3"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.INDEX_ID3, MenuItemId.ARTIST, "", false, 4), "sub4"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.ALBUM_ID3, MenuItemId.ALBUM, "", false, 5), "sub5"));
            subMenuItems
                .forEach(menuItem -> when(menuItemDao.getMenuItemData(menuItem.getId().value()))
                    .thenReturn(menuItem));

            DLNASettingsCommand command = new DLNASettingsCommand();
            command.setTopMenuItems(Collections.emptyList());
            command.setSubMenuItems(subMenuItems);
            command.setAlbumGenreSort(GenreMasterSort.FREQUENCY);
            command.setSongGenreSort(GenreMasterSort.FREQUENCY);
            command.setSearchMethod(UPnPSearchMethod.FILE_STRUCTURE);
            command.setDlnaKeyRotationType(KeyRotationType.FIXED);

            ArgumentCaptor<MenuItemData> menuItemCaptor = ArgumentCaptor
                .forClass(MenuItemData.class);
            Mockito.doNothing().when(menuItemDao).updateMenuItem(menuItemCaptor.capture());

            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(5, menuItemCaptor.getAllValues().size());
        }

        @Test
        void testUpdateEnabled() {
            settingsFacade = SettingsFacadeBuilder.create().build();
            musicFolderService = mock(MusicFolderService.class);
            upnpService = mock(UPnPService.class);
            MenuItemDaoImpl menuItemDao = mock(MenuItemDaoImpl.class);
            MenuItemManager menuItemManager = new MenuItemManager(serverLocaleProvider, menuItemDao,
                    mock(MessageSource.class));
            controller = new DLNASettingsController(settingsFacade, musicFolderService,
                    mock(UserService.class), mock(PlayerService.class),
                    mock(TranscodingService.class), upnpService, mock(ShareService.class),
                    menuItemManager, upnpKeyManager, mock(OutlineHelpSelector.class));
            mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

            // Create dummy data
            List<MenuItemWithDefaultName> subMenuItems = new ArrayList<>();
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.MEDIA_FILE, MenuItemId.FOLDER, "", true, 1), "sub1"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP, MenuItemId.INDEX,
                        MenuItemId.FOLDER, "", true, 2), "sub2"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.ALBUM_ARTIST, MenuItemId.ARTIST, "", false, 3), "sub3"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.INDEX_ID3, MenuItemId.ARTIST, "", false, 4), "sub4"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.ALBUM_ID3, MenuItemId.ALBUM, "", false, 5), "sub5"));
            subMenuItems.forEach(menuItem -> {
                if (menuItem.getId() == MenuItemId.MEDIA_FILE) {
                    when(menuItemDao.getMenuItemData(MenuItemId.MEDIA_FILE.value()))
                        .thenReturn(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                                MenuItemId.MEDIA_FILE, MenuItemId.FOLDER, "", false, 1), "sub1"));
                } else {
                    when(menuItemDao.getMenuItemData(menuItem.getId().value()))
                        .thenReturn(menuItem);
                }
            });

            DLNASettingsCommand command = new DLNASettingsCommand();
            command.setTopMenuItems(Collections.emptyList());
            command.setSubMenuItems(subMenuItems);
            command.setAlbumGenreSort(GenreMasterSort.FREQUENCY);
            command.setSongGenreSort(GenreMasterSort.FREQUENCY);
            command.setSearchMethod(UPnPSearchMethod.FILE_STRUCTURE);
            command.setDlnaKeyRotationType(KeyRotationType.FIXED);
            ArgumentCaptor<MenuItemData> menuItemCaptor = ArgumentCaptor
                .forClass(MenuItemData.class);
            Mockito.doNothing().when(menuItemDao).updateMenuItem(menuItemCaptor.capture());
            controller.post(command, Mockito.mock(RedirectAttributes.class));

            List<MenuItemData> results = menuItemCaptor.getAllValues();
            assertEquals(5, results.size());
            results
                .stream()
                .filter(menuItem -> menuItem.getId() == MenuItemId.MEDIA_FILE)
                .findFirst()
                .ifPresentOrElse(menuItem -> assertTrue(menuItem.isEnabled()), Assertions::fail);
        }

        @Test
        void testUpdateName() {
            settingsFacade = SettingsFacadeBuilder.create().build();
            musicFolderService = mock(MusicFolderService.class);
            upnpService = mock(UPnPService.class);
            MenuItemDaoImpl menuItemDao = mock(MenuItemDaoImpl.class);
            MenuItemManager menuItemManager = new MenuItemManager(serverLocaleProvider, menuItemDao,
                    mock(MessageSource.class));
            controller = new DLNASettingsController(settingsFacade, musicFolderService,
                    mock(UserService.class), mock(PlayerService.class),
                    mock(TranscodingService.class), upnpService, mock(ShareService.class),
                    menuItemManager, upnpKeyManager, mock(OutlineHelpSelector.class));
            mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

            // Create dummy data
            List<MenuItemWithDefaultName> subMenuItems = new ArrayList<>();
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.MEDIA_FILE, MenuItemId.FOLDER, "Changed Sub1", true, 1),
                        "sub1"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP, MenuItemId.INDEX,
                        MenuItemId.FOLDER, "", true, 2), "sub2"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.ALBUM_ARTIST, MenuItemId.ARTIST, "", false, 3), "sub3"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.INDEX_ID3, MenuItemId.ARTIST, "", false, 4), "sub4"));
            subMenuItems
                .add(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                        MenuItemId.ALBUM_ID3, MenuItemId.ALBUM, "", false, 5), "sub5"));
            subMenuItems.forEach(menuItem -> {
                if (menuItem.getId() == MenuItemId.MEDIA_FILE) {
                    when(menuItemManager.getMenuItem(MenuItemId.MEDIA_FILE))
                        .thenReturn(new MenuItemWithDefaultName(new MenuItemData(ViewType.UPNP,
                                MenuItemId.MEDIA_FILE, MenuItemId.FOLDER, "", false, 1), "sub1"));
                } else {
                    when(menuItemDao.getMenuItemData(menuItem.getId().value()))
                        .thenReturn(menuItem);
                }
            });

            DLNASettingsCommand command = new DLNASettingsCommand();
            command.setTopMenuItems(Collections.emptyList());
            command.setSubMenuItems(subMenuItems);
            command.setAlbumGenreSort(GenreMasterSort.FREQUENCY);
            command.setSongGenreSort(GenreMasterSort.FREQUENCY);
            command.setSearchMethod(UPnPSearchMethod.FILE_STRUCTURE);
            command.setDlnaKeyRotationType(KeyRotationType.FIXED);

            ArgumentCaptor<MenuItemData> menuItemCaptor = ArgumentCaptor
                .forClass(MenuItemData.class);
            Mockito.doNothing().when(menuItemDao).updateMenuItem(menuItemCaptor.capture());
            controller.post(command, Mockito.mock(RedirectAttributes.class));

            List<MenuItemData> results = menuItemCaptor.getAllValues();
            assertEquals(5, results.size());
            results
                .stream()
                .filter(menuItem -> menuItem.getId() == MenuItemId.MEDIA_FILE)
                .findFirst()
                .ifPresentOrElse(menuItem -> assertEquals("Changed Sub1", menuItem.getName()),
                        Assertions::fail);
        }
    }

    @Documented
    private @interface MediaServerEnabledDecision {
        @interface Conditions {
            @interface EnabledChanged {
                @interface False {
                }

                @interface True {
                }
            }

            @interface NameOrUrlChanged {
                @interface False {
                }

                @interface True {
                }
            }

            @interface EnabledFilteredIpChanged {
                @interface False {
                }

                @interface True {
                }
            }

            @interface FilteredIpChanged {
                @interface False {
                }

                @interface True {
                }
            }

            @interface Command {
                @interface DlnaEnabled {
                    @interface False {
                    }

                    @interface True {
                    }
                }
            }

        }

        @interface Results {
            @interface Never {
            }

            @interface MediaServerEnabled {
                @interface False {
                }

                @interface True {
                }
            }

        }

    }

    @Nested
    class MediaServerEnabledTest {

        private static final String DLNA_SERVER_NAME = "jpsonic";
        private static final String DLNA_BASE_LAN_URL = "url";
        private static final String DLNA_FILTERED_IP = UPnPSKeys.advanced.filteredIp.defaultValue();

        @MediaServerEnabledDecision.Conditions.EnabledChanged.False
        @MediaServerEnabledDecision.Conditions.NameOrUrlChanged.False
        @MediaServerEnabledDecision.Conditions.EnabledFilteredIpChanged.False
        @MediaServerEnabledDecision.Conditions.FilteredIpChanged.False
        @MediaServerEnabledDecision.Results.Never
        // Never (Nothing has changed)
        @Test
        void m01() {
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(UPnPSKeys.basic.enabled, false)
                .withString(UPnPSKeys.basic.serverName, DLNA_SERVER_NAME)
                .withString(UPnPSKeys.basic.baseLanUrl, DLNA_BASE_LAN_URL)
                .build();
            init();

            DLNASettingsCommand command = new DLNASettingsCommand();
            command.setDlnaEnabled(false);
            command.setDlnaServerName(DLNA_SERVER_NAME);
            command.setDlnaBaseLANURL(DLNA_BASE_LAN_URL);
            command.setTopMenuItems(Collections.emptyList());
            command.setSubMenuItems(Collections.emptyList());
            command.setAlbumGenreSort(GenreMasterSort.FREQUENCY);
            command.setSongGenreSort(GenreMasterSort.FREQUENCY);
            command.setSearchMethod(UPnPSearchMethod.FILE_STRUCTURE);
            command.setDlnaKeyRotationType(KeyRotationType.FIXED);

            controller.post(command, Mockito.mock(RedirectAttributes.class));
            Mockito.verify(upnpService, Mockito.never()).setEnabled(Mockito.any(boolean.class));
        }

        @MediaServerEnabledDecision.Conditions.Command.DlnaEnabled.True
        @MediaServerEnabledDecision.Conditions.EnabledChanged.True
        @MediaServerEnabledDecision.Conditions.NameOrUrlChanged.False
        @MediaServerEnabledDecision.Conditions.EnabledFilteredIpChanged.False
        @MediaServerEnabledDecision.Conditions.FilteredIpChanged.False
        @MediaServerEnabledDecision.Results.MediaServerEnabled.True
        @Test
        // Boot
        void m02() {
            ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(boolean.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(UPnPSKeys.basic.enabled, false)
                .withString(UPnPSKeys.basic.serverName, DLNA_SERVER_NAME)
                .withString(UPnPSKeys.basic.baseLanUrl, DLNA_BASE_LAN_URL)
                .captureBoolean(UPnPSKeys.basic.enabled, captor)
                .build();
            init();

            DLNASettingsCommand command = new DLNASettingsCommand();
            command.setDlnaEnabled(true);
            command.setDlnaServerName(DLNA_SERVER_NAME);
            command.setDlnaBaseLANURL(DLNA_BASE_LAN_URL);
            command.setTopMenuItems(Collections.emptyList());
            command.setSubMenuItems(Collections.emptyList());
            command.setAlbumGenreSort(GenreMasterSort.FREQUENCY);
            command.setSongGenreSort(GenreMasterSort.FREQUENCY);
            command.setSearchMethod(UPnPSearchMethod.FILE_STRUCTURE);
            command.setDlnaKeyRotationType(KeyRotationType.FIXED);

            controller.post(command, Mockito.mock(RedirectAttributes.class));

            assertEquals(1, captor.getAllValues().size());
            assertTrue(captor.getValue());
        }

        @MediaServerEnabledDecision.Conditions.Command.DlnaEnabled.False
        @MediaServerEnabledDecision.Conditions.EnabledChanged.True
        @MediaServerEnabledDecision.Conditions.NameOrUrlChanged.False
        @MediaServerEnabledDecision.Conditions.EnabledFilteredIpChanged.False
        @MediaServerEnabledDecision.Conditions.FilteredIpChanged.False
        @MediaServerEnabledDecision.Results.MediaServerEnabled.False
        @Test
        // Shutdown
        void m03() {
            ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(boolean.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(UPnPSKeys.basic.enabled, true)
                .withString(UPnPSKeys.basic.serverName, DLNA_SERVER_NAME)
                .withString(UPnPSKeys.basic.baseLanUrl, DLNA_BASE_LAN_URL)
                .captureBoolean(UPnPSKeys.basic.enabled, captor)
                .build();
            init();

            DLNASettingsCommand command = new DLNASettingsCommand();
            command.setDlnaEnabled(false);
            command.setDlnaServerName(DLNA_SERVER_NAME);
            command.setDlnaBaseLANURL(DLNA_BASE_LAN_URL);
            command.setTopMenuItems(Collections.emptyList());
            command.setSubMenuItems(Collections.emptyList());
            command.setAlbumGenreSort(GenreMasterSort.FREQUENCY);
            command.setSongGenreSort(GenreMasterSort.FREQUENCY);
            command.setSearchMethod(UPnPSearchMethod.FILE_STRUCTURE);
            command.setDlnaKeyRotationType(KeyRotationType.FIXED);

            controller.post(command, Mockito.mock(RedirectAttributes.class));

            assertEquals(1, captor.getAllValues().size());
            assertFalse(captor.getValue());
        }

        @MediaServerEnabledDecision.Conditions.Command.DlnaEnabled.False
        @MediaServerEnabledDecision.Conditions.EnabledChanged.False
        @MediaServerEnabledDecision.Conditions.NameOrUrlChanged.True
        @MediaServerEnabledDecision.Conditions.EnabledFilteredIpChanged.False
        @MediaServerEnabledDecision.Conditions.FilteredIpChanged.False
        @MediaServerEnabledDecision.Results.Never
        // Never (Do nothing if you change the name and URL while DLNA is stopped)
        @Test
        void m04() {
            ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(boolean.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(UPnPSKeys.basic.enabled, true)
                .withString(UPnPSKeys.basic.serverName, DLNA_SERVER_NAME)
                .withString(UPnPSKeys.basic.baseLanUrl, DLNA_BASE_LAN_URL)
                .captureBoolean(UPnPSKeys.basic.enabled, captor)
                .build();
            init();

            DLNASettingsCommand command = new DLNASettingsCommand();
            command.setDlnaEnabled(false);
            command.setDlnaServerName("changedDlnaServerName");
            command.setDlnaBaseLANURL(DLNA_BASE_LAN_URL);
            command.setTopMenuItems(Collections.emptyList());
            command.setSubMenuItems(Collections.emptyList());
            command.setAlbumGenreSort(GenreMasterSort.FREQUENCY);
            command.setSongGenreSort(GenreMasterSort.FREQUENCY);
            command.setSearchMethod(UPnPSearchMethod.FILE_STRUCTURE);

            assertEquals(0, captor.getAllValues().size());
        }

        @MediaServerEnabledDecision.Conditions.Command.DlnaEnabled.True
        @MediaServerEnabledDecision.Conditions.EnabledChanged.False
        @MediaServerEnabledDecision.Conditions.NameOrUrlChanged.True
        @MediaServerEnabledDecision.Results.MediaServerEnabled.False
        @MediaServerEnabledDecision.Conditions.EnabledFilteredIpChanged.False
        @MediaServerEnabledDecision.Conditions.FilteredIpChanged.False
        @MediaServerEnabledDecision.Results.MediaServerEnabled.True
        // Reboot
        @Test
        void m05() {
            ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(boolean.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(UPnPSKeys.basic.enabled, true)
                .withString(UPnPSKeys.basic.serverName, DLNA_SERVER_NAME)
                .withString(UPnPSKeys.basic.baseLanUrl, DLNA_BASE_LAN_URL)
                .captureBoolean(UPnPSKeys.basic.enabled, captor)
                .build();
            init();

            DLNASettingsCommand command = new DLNASettingsCommand();
            command.setDlnaEnabled(true);
            command.setDlnaServerName(DLNA_SERVER_NAME);
            command.setDlnaBaseLANURL("changedDlnaBaseLANURL");
            command.setTopMenuItems(Collections.emptyList());
            command.setSubMenuItems(Collections.emptyList());
            command.setAlbumGenreSort(GenreMasterSort.FREQUENCY);
            command.setSongGenreSort(GenreMasterSort.FREQUENCY);
            command.setSearchMethod(UPnPSearchMethod.FILE_STRUCTURE);
            command.setDlnaKeyRotationType(KeyRotationType.FIXED);

            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(1, captor.getAllValues().size());
            assertTrue(captor.getAllValues().get(0));
        }

        @MediaServerEnabledDecision.Conditions.Command.DlnaEnabled.False
        @MediaServerEnabledDecision.Conditions.EnabledChanged.False
        @MediaServerEnabledDecision.Conditions.NameOrUrlChanged.False
        @MediaServerEnabledDecision.Conditions.EnabledFilteredIpChanged.True
        @MediaServerEnabledDecision.Conditions.FilteredIpChanged.False
        @MediaServerEnabledDecision.Results.Never
        // Never (Do nothing if you change the enabledFilteredIp while DLNA is stopped)
        @Test
        void m06() {
            ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(boolean.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(UPnPSKeys.basic.enabled, false)
                .withString(UPnPSKeys.basic.serverName, DLNA_SERVER_NAME)
                .withString(UPnPSKeys.basic.baseLanUrl, DLNA_BASE_LAN_URL)
                .withBoolean(UPnPSKeys.advanced.enabledFilteredIp, true)
                .withString(UPnPSKeys.advanced.filteredIp, DLNA_FILTERED_IP)
                .captureBoolean(UPnPSKeys.basic.enabled, captor)
                .build();
            init();

            DLNASettingsCommand command = new DLNASettingsCommand();
            command.setDlnaEnabled(false);
            command.setDlnaServerName(DLNA_SERVER_NAME);
            command.setDlnaBaseLANURL(DLNA_BASE_LAN_URL);
            command.setTopMenuItems(Collections.emptyList());
            command.setSubMenuItems(Collections.emptyList());
            command.setAlbumGenreSort(GenreMasterSort.FREQUENCY);
            command.setSongGenreSort(GenreMasterSort.FREQUENCY);
            command.setDlnaEnabledFilteredIp(false);
            command.setDlnaFilteredIp(DLNA_FILTERED_IP);
            command.setSearchMethod(UPnPSearchMethod.FILE_STRUCTURE);
            command.setDlnaKeyRotationType(KeyRotationType.FIXED);

            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(1, captor.getAllValues().size());
            assertFalse(captor.getAllValues().get(0));
        }

        @MediaServerEnabledDecision.Conditions.Command.DlnaEnabled.True
        @MediaServerEnabledDecision.Conditions.EnabledChanged.False
        @MediaServerEnabledDecision.Conditions.NameOrUrlChanged.False
        @MediaServerEnabledDecision.Results.MediaServerEnabled.False
        @MediaServerEnabledDecision.Conditions.EnabledFilteredIpChanged.True
        @MediaServerEnabledDecision.Conditions.FilteredIpChanged.False
        @MediaServerEnabledDecision.Results.MediaServerEnabled.True
        // Reboot
        @Test
        void m07() {
            ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(boolean.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(UPnPSKeys.basic.enabled, true)
                .withString(UPnPSKeys.basic.serverName, DLNA_SERVER_NAME)
                .withString(UPnPSKeys.basic.baseLanUrl, DLNA_BASE_LAN_URL)
                .withBoolean(UPnPSKeys.advanced.enabledFilteredIp, true)
                .withString(UPnPSKeys.advanced.filteredIp, DLNA_FILTERED_IP)
                .captureBoolean(UPnPSKeys.basic.enabled, captor)
                .build();
            init();

            DLNASettingsCommand command = new DLNASettingsCommand();
            command.setDlnaEnabled(true);
            command.setDlnaServerName(DLNA_SERVER_NAME);
            command.setDlnaBaseLANURL(DLNA_BASE_LAN_URL);
            command.setTopMenuItems(Collections.emptyList());
            command.setSubMenuItems(Collections.emptyList());
            command.setAlbumGenreSort(GenreMasterSort.FREQUENCY);
            command.setSongGenreSort(GenreMasterSort.FREQUENCY);
            command.setDlnaEnabledFilteredIp(false);
            command.setDlnaFilteredIp(DLNA_FILTERED_IP);
            command.setSearchMethod(UPnPSearchMethod.FILE_STRUCTURE);
            command.setDlnaKeyRotationType(KeyRotationType.FIXED);

            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(1, captor.getAllValues().size());
            assertTrue(captor.getAllValues().get(0));
        }

        @MediaServerEnabledDecision.Conditions.Command.DlnaEnabled.False
        @MediaServerEnabledDecision.Conditions.EnabledChanged.False
        @MediaServerEnabledDecision.Conditions.NameOrUrlChanged.False
        @MediaServerEnabledDecision.Conditions.EnabledFilteredIpChanged.False
        @MediaServerEnabledDecision.Conditions.FilteredIpChanged.True
        @MediaServerEnabledDecision.Results.Never
        // Never (Do nothing if you change the filteredIp while DLNA is stopped)
        @Test
        void m08() {
            ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(boolean.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(UPnPSKeys.basic.enabled, false)
                .withString(UPnPSKeys.basic.serverName, DLNA_SERVER_NAME)
                .withString(UPnPSKeys.basic.baseLanUrl, DLNA_BASE_LAN_URL)
                .withBoolean(UPnPSKeys.advanced.enabledFilteredIp, true)
                .withString(UPnPSKeys.advanced.filteredIp, DLNA_FILTERED_IP)
                .captureBoolean(UPnPSKeys.basic.enabled, captor)
                .build();
            init();

            DLNASettingsCommand command = new DLNASettingsCommand();
            command.setDlnaEnabled(false);
            command.setDlnaServerName(DLNA_SERVER_NAME);
            command.setDlnaBaseLANURL(DLNA_BASE_LAN_URL);
            command.setTopMenuItems(Collections.emptyList());
            command.setSubMenuItems(Collections.emptyList());
            command.setAlbumGenreSort(GenreMasterSort.FREQUENCY);
            command.setSongGenreSort(GenreMasterSort.FREQUENCY);
            command.setDlnaEnabledFilteredIp(true);
            command.setDlnaFilteredIp("123.456.7.8");
            command.setSearchMethod(UPnPSearchMethod.FILE_STRUCTURE);
            command.setDlnaKeyRotationType(KeyRotationType.FIXED);

            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(1, captor.getAllValues().size());
            assertFalse(captor.getAllValues().get(0));

        }

        @MediaServerEnabledDecision.Conditions.Command.DlnaEnabled.True
        @MediaServerEnabledDecision.Conditions.EnabledChanged.False
        @MediaServerEnabledDecision.Conditions.NameOrUrlChanged.False
        @MediaServerEnabledDecision.Results.MediaServerEnabled.False
        @MediaServerEnabledDecision.Conditions.EnabledFilteredIpChanged.False
        @MediaServerEnabledDecision.Conditions.FilteredIpChanged.True
        @MediaServerEnabledDecision.Results.MediaServerEnabled.True
        // Reboot
        @Test
        void m09() {
            ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(boolean.class);
            settingsFacade = SettingsFacadeBuilder
                .create()
                .withBoolean(UPnPSKeys.basic.enabled, true)
                .withString(UPnPSKeys.basic.serverName, DLNA_SERVER_NAME)
                .withString(UPnPSKeys.basic.baseLanUrl, DLNA_BASE_LAN_URL)
                .withBoolean(UPnPSKeys.advanced.enabledFilteredIp, true)
                .withString(UPnPSKeys.advanced.filteredIp, DLNA_FILTERED_IP)
                .captureBoolean(UPnPSKeys.basic.enabled, captor)
                .build();
            init();

            DLNASettingsCommand command = new DLNASettingsCommand();
            command.setDlnaEnabled(true);
            command.setDlnaServerName(DLNA_SERVER_NAME);
            command.setDlnaBaseLANURL(DLNA_BASE_LAN_URL);
            command.setTopMenuItems(Collections.emptyList());
            command.setSubMenuItems(Collections.emptyList());
            command.setAlbumGenreSort(GenreMasterSort.FREQUENCY);
            command.setSongGenreSort(GenreMasterSort.FREQUENCY);
            command.setDlnaEnabledFilteredIp(true);
            command.setDlnaFilteredIp("123.456.7.8");
            command.setSearchMethod(UPnPSearchMethod.FILE_STRUCTURE);
            command.setDlnaKeyRotationType(KeyRotationType.FIXED);

            controller.post(command, Mockito.mock(RedirectAttributes.class));
            assertEquals(1, captor.getAllValues().size());
            assertTrue(captor.getAllValues().get(0));
        }
    }
}

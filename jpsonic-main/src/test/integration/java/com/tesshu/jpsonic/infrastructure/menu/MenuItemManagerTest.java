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
 * (C) 2023 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.menu;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;

import com.tesshu.jpsonic.domain.model.MenuItem.ViewType;
import com.tesshu.jpsonic.domain.system.MenuItemId;
import com.tesshu.jpsonic.infrastructure.locale.ServerLocaleManager;
import com.tesshu.jpsonic.infrastructure.menu.MenuItemManager.MenuItemWithDefaultName;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.persistence.NeedsDB;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import com.tesshu.jpsonic.persistence.core.repository.MenuItemDaoImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@NeedsDB
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class MenuItemManagerTest {

    @Autowired
    private ServerLocaleManager serverLocaleManager;
    @Autowired
    private SettingsFacade settingsFacade;
    @Autowired
    MenuItemDaoImpl menuItemDao;
    @Autowired
    private MenuItemManager menuItemManager;
    @Autowired
    private TemplateWrapper templateWrapper;

    @BeforeEach
    void setup() throws URISyntaxException {
        Locale otherThanEnJp = serverLocaleManager.getAvailableLocales().get(5);
        assertEquals("ca", otherThanEnJp.getLanguage());
        serverLocaleManager.stagingLocale(otherThanEnJp);
        settingsFacade.commitAll();
    }

    @Test
    void testGetTopMenuItemCount() {
        assertEquals(8, menuItemManager.countTopMenuItems(ViewType.UPNP));
    }

    @Test
    void testGetTopMenuItems() {
        List<com.tesshu.jpsonic.domain.model.MenuItem> menuItems = menuItemManager
            .findTopMenuItems(ViewType.UPNP, false, 0, Integer.MAX_VALUE);
        assertEquals(menuItemManager.countTopMenuItems(ViewType.UPNP), menuItems.size());
        assertEquals("Folder", menuItems.get(0).name());
        assertEquals("Album Artist", menuItems.get(1).name());
        assertEquals("Album", menuItems.get(2).name());
        assertEquals("Genre", menuItems.get(3).name());
        assertEquals("Podcast", menuItems.get(4).name());
        assertEquals("Playlists", menuItems.get(5).name());
        assertEquals("Recently", menuItems.get(6).name());
        assertEquals("Shuffle", menuItems.get(7).name());
    }

    @Test
    void testGetTopMenuItemsWithType() {
        List<MenuItemWithDefaultName> menuItems = menuItemManager.getTopMenuItems(ViewType.UPNP);
        assertEquals(menuItemManager.countTopMenuItems(ViewType.UPNP), menuItems.size());
        assertEquals("Folder", menuItems.get(0).getName());
        assertEquals("Album Artist", menuItems.get(1).getName());
        assertEquals("Album", menuItems.get(2).getName());
        assertEquals("Genre", menuItems.get(3).getName());
        assertEquals("Podcast", menuItems.get(4).getName());
        assertEquals("Playlists", menuItems.get(5).getName());
        assertEquals("Recently", menuItems.get(6).getName());
        assertEquals("Shuffle", menuItems.get(7).getName());

        assertEquals("Folder", menuItems.get(0).getDefaultName());
        assertEquals("Album Artist", menuItems.get(1).getDefaultName());
        assertEquals("Album", menuItems.get(2).getDefaultName());
        assertEquals("Genre", menuItems.get(3).getDefaultName());
        assertEquals("Podcast", menuItems.get(4).getDefaultName());
        assertEquals("Playlists", menuItems.get(5).getDefaultName());
        assertEquals("Recently", menuItems.get(6).getDefaultName());
        assertEquals("Shuffle", menuItems.get(7).getDefaultName());
    }

    @Test
    void testGetChildlenOf() {
        List<com.tesshu.jpsonic.domain.model.MenuItem> menuItems = menuItemManager
            .findChildlen(ViewType.UPNP, MenuItemId.FOLDER, false, 0, Integer.MAX_VALUE);
        assertEquals(3, menuItems.size());
        assertEquals("Simple List", menuItems.get(0).name());
        assertEquals("By Folder", menuItems.get(1).name());
        assertEquals("With Index", menuItems.get(2).name());

        menuItems = menuItemManager
            .findChildlen(ViewType.UPNP, MenuItemId.ARTIST, false, 0, Integer.MAX_VALUE);
        assertEquals(3, menuItems.size());
        assertEquals("Simple List", menuItems.get(0).name());
        assertEquals("By Folder", menuItems.get(1).name());
        assertEquals("With Index", menuItems.get(2).name());

        menuItems = menuItemManager
            .findChildlen(ViewType.UPNP, MenuItemId.ALBUM, false, 0, Integer.MAX_VALUE);
        assertEquals(4, menuItems.size());
        assertEquals("Simple List(ID3)", menuItems.get(0).name());
        assertEquals("By Folder(ID3)", menuItems.get(1).name());
        assertEquals("Simple List(FileStructure)", menuItems.get(2).name());
        assertEquals("By Folder(FileStructure)", menuItems.get(3).name());

        menuItems = menuItemManager
            .findChildlen(ViewType.UPNP, MenuItemId.GENRE, false, 0, Integer.MAX_VALUE);
        assertEquals(6, menuItems.size());
        assertEquals("By Album", menuItems.get(0).name());
        assertEquals("By Folder&Album", menuItems.get(1).name());
        assertEquals("By Music", menuItems.get(2).name());
        assertEquals("By Folder&Music", menuItems.get(3).name());
        assertEquals("Audiobook", menuItems.get(4).name());
        assertEquals("Subsonic Style", menuItems.get(5).name());

        menuItems = menuItemManager
            .findChildlen(ViewType.UPNP, MenuItemId.PODCAST, false, 0, Integer.MAX_VALUE);
        assertEquals(1, menuItems.size());
        assertEquals("Channels List", menuItems.get(0).name());

        menuItems = menuItemManager
            .findChildlen(ViewType.UPNP, MenuItemId.PLAYLISTS, false, 0, Integer.MAX_VALUE);
        assertEquals(1, menuItems.size());
        assertEquals("Simple List", menuItems.get(0).name());

        menuItems = menuItemManager
            .findChildlen(ViewType.UPNP, MenuItemId.RECENTLY, false, 0, Integer.MAX_VALUE);
        assertEquals(4, menuItems.size());
        assertEquals("Added Albums", menuItems.get(0).name());
        assertEquals("Added Albums By Folder", menuItems.get(1).name());
        assertEquals("Tagged Albums", menuItems.get(2).name());
        assertEquals("Tagged Albums By Folder", menuItems.get(3).name());

        menuItems = menuItemManager
            .findChildlen(ViewType.UPNP, MenuItemId.SHUFFLE, false, 0, Integer.MAX_VALUE);
        assertEquals(6, menuItems.size());
        assertEquals("Music", menuItems.get(0).name());
        assertEquals("Music By Artist", menuItems.get(1).name());
        assertEquals("Music By Folder&Artist", menuItems.get(2).name());
        assertEquals("Music By Genre", menuItems.get(3).name());
        assertEquals("Music By Folder&Genre", menuItems.get(4).name());
        assertEquals("Album", menuItems.get(5).name());
    }

    @Nested
    class EnsureUPnPSubMenuEnabledTest {

        @Test
        void testDoNothing() {
            int topMenuItemCount = menuItemManager.countTopMenuItems(ViewType.UPNP);
            int enabledSubMenuCount = (int) menuItemManager
                .getSubMenuItems(ViewType.UPNP)
                .stream()
                .filter(MenuItemData::isEnabled)
                .count();
            assertEquals(topMenuItemCount, enabledSubMenuCount);
            menuItemManager.ensureUPnPSubMenuEnabled();
            enabledSubMenuCount = (int) menuItemManager
                .getSubMenuItems(ViewType.UPNP)
                .stream()
                .filter(MenuItemData::isEnabled)
                .count();
            assertEquals(topMenuItemCount, enabledSubMenuCount);
        }

        @Test
        void testEnsureUPnPSubMenuEnabled() {
            int topMenuItemCount = menuItemManager.countTopMenuItems(ViewType.UPNP);
            int enabledSubMenuCount = (int) menuItemManager
                .getSubMenuItems(ViewType.UPNP)
                .stream()
                .filter(MenuItemData::isEnabled)
                .count();
            assertEquals(topMenuItemCount, enabledSubMenuCount);

            menuItemManager.getSubMenuItems(ViewType.UPNP).stream().forEach(menuItem -> {
                menuItem.setEnabled(false);
                menuItemManager.updateMenuItem(menuItem);
            });

            enabledSubMenuCount = (int) menuItemManager
                .getSubMenuItems(ViewType.UPNP)
                .stream()
                .filter(MenuItemData::isEnabled)
                .count();
            assertEquals(0, enabledSubMenuCount);

            menuItemManager.ensureUPnPSubMenuEnabled();
            enabledSubMenuCount = (int) menuItemManager
                .getSubMenuItems(ViewType.UPNP)
                .stream()
                .filter(MenuItemData::isEnabled)
                .count();
            assertEquals(topMenuItemCount, enabledSubMenuCount);
        }
    }

    @Test
    void testUpdateMenuItems() {
        List<MenuItemWithDefaultName> topMenuItems = menuItemManager.getTopMenuItems(ViewType.UPNP);
        topMenuItems
            .stream()
            .filter(menuItem -> menuItem.getId() == MenuItemId.FOLDER)
            .findFirst()
            .ifPresentOrElse(menuItem -> assertTrue(menuItem.isEnabled()), Assertions::fail);
        int enabledSubMenuCount = (int) menuItemManager
            .getSubMenuItems(ViewType.UPNP)
            .stream()
            .filter(MenuItemData::isEnabled)
            .count();
        assertEquals(topMenuItems.size(), enabledSubMenuCount);
        topMenuItems
            .stream()
            .filter(menuItem -> menuItem.getId() == MenuItemId.FOLDER)
            .findFirst()
            .ifPresent(menuItem -> menuItem.setEnabled(false));

        List<MenuItemWithDefaultName> subMenuItems = menuItemManager.getSubMenuItems(ViewType.UPNP);
        subMenuItems.stream().forEach(menuItem -> menuItem.setEnabled(false));

        menuItemManager
            .updateMenuItems(Stream.concat(topMenuItems.stream(), subMenuItems.stream()));

        topMenuItems = menuItemManager.getTopMenuItems(ViewType.UPNP);
        topMenuItems
            .stream()
            .filter(menuItem -> menuItem.getId() == MenuItemId.FOLDER)
            .findFirst()
            .ifPresentOrElse(menuItem -> assertFalse(menuItem.isEnabled()), Assertions::fail);
        enabledSubMenuCount = (int) menuItemManager
            .getSubMenuItems(ViewType.UPNP)
            .stream()
            .filter(MenuItemData::isEnabled)
            .count();
        assertEquals(topMenuItems.size(), enabledSubMenuCount);

        // tearDown
        topMenuItems
            .stream()
            .filter(menuItem -> menuItem.getId() == MenuItemId.FOLDER)
            .findFirst()
            .ifPresentOrElse(menuItem -> {
                menuItem.setEnabled(true);
                menuItemManager.updateMenuItem(menuItem);
            }, Assertions::fail);
    }

    @Nested
    class UpdateMenuItemOrderTest {

        @Test
        void testUpdateMenuItemOrder() {
            List<MenuItemWithDefaultName> topMenuItems = menuItemManager
                .getTopMenuItems(ViewType.UPNP);
            assertEquals(MenuItemId.FOLDER, topMenuItems.get(0).getId());
            assertEquals(MenuItemId.ARTIST, topMenuItems.get(1).getId());
            assertEquals(MenuItemId.ALBUM, topMenuItems.get(2).getId());
            assertEquals(MenuItemId.GENRE, topMenuItems.get(3).getId());
            assertEquals(MenuItemId.PODCAST, topMenuItems.get(4).getId());
            assertEquals(MenuItemId.PLAYLISTS, topMenuItems.get(5).getId());
            assertEquals(MenuItemId.RECENTLY, topMenuItems.get(6).getId());
            assertEquals(MenuItemId.SHUFFLE, topMenuItems.get(7).getId());

            menuItemManager.updateMenuItemOrder(ViewType.UPNP, MenuItemId.ARTIST.value());

            topMenuItems = menuItemManager.getTopMenuItems(ViewType.UPNP);
            assertEquals(MenuItemId.ARTIST, topMenuItems.get(0).getId());
            assertEquals(MenuItemId.FOLDER, topMenuItems.get(1).getId());
            assertEquals(MenuItemId.ALBUM, topMenuItems.get(2).getId());
            assertEquals(MenuItemId.GENRE, topMenuItems.get(3).getId());
            assertEquals(MenuItemId.PODCAST, topMenuItems.get(4).getId());
            assertEquals(MenuItemId.PLAYLISTS, topMenuItems.get(5).getId());
            assertEquals(MenuItemId.RECENTLY, topMenuItems.get(6).getId());
            assertEquals(MenuItemId.SHUFFLE, topMenuItems.get(7).getId());

            menuItemManager.resetMenuItem(ViewType.UPNP, MenuItemManager.ResetMode.TOP_MENU);
        }

        @Test
        void testFirstItem() {
            List<MenuItemWithDefaultName> topMenuItems = menuItemManager
                .getTopMenuItems(ViewType.UPNP);
            assertEquals(MenuItemId.FOLDER, topMenuItems.get(0).getId());
            assertEquals(MenuItemId.ARTIST, topMenuItems.get(1).getId());
            assertEquals(MenuItemId.ALBUM, topMenuItems.get(2).getId());
            assertEquals(MenuItemId.GENRE, topMenuItems.get(3).getId());
            assertEquals(MenuItemId.PODCAST, topMenuItems.get(4).getId());
            assertEquals(MenuItemId.PLAYLISTS, topMenuItems.get(5).getId());
            assertEquals(MenuItemId.RECENTLY, topMenuItems.get(6).getId());
            assertEquals(MenuItemId.SHUFFLE, topMenuItems.get(7).getId());

            menuItemManager.updateMenuItemOrder(ViewType.UPNP, MenuItemId.FOLDER.value());

            topMenuItems = menuItemManager.getTopMenuItems(ViewType.UPNP);
            assertEquals(MenuItemId.FOLDER, topMenuItems.get(0).getId());
            assertEquals(MenuItemId.ARTIST, topMenuItems.get(1).getId());
            assertEquals(MenuItemId.ALBUM, topMenuItems.get(2).getId());
            assertEquals(MenuItemId.GENRE, topMenuItems.get(3).getId());
            assertEquals(MenuItemId.PODCAST, topMenuItems.get(4).getId());
            assertEquals(MenuItemId.PLAYLISTS, topMenuItems.get(5).getId());
            assertEquals(MenuItemId.RECENTLY, topMenuItems.get(6).getId());
            assertEquals(MenuItemId.SHUFFLE, topMenuItems.get(7).getId());
        }
    }

    @Test
    void testResetMenuItem() {
        Function<List<MenuItemData>, Boolean> validateDefaultSubMenuItems = (subMenuItems) -> {
            assertEquals(MenuItemId.MEDIA_FILE, subMenuItems.get(0).getId());
            assertEquals(MenuItemId.MEDIA_FILE_BY_FOLDER, subMenuItems.get(1).getId());
            assertEquals(MenuItemId.INDEX, subMenuItems.get(2).getId());
            assertEquals(MenuItemId.ALBUM_ARTIST, subMenuItems.get(3).getId());
            assertEquals(MenuItemId.ALBUM_ARTIST_BY_FOLDER, subMenuItems.get(4).getId());
            assertEquals(MenuItemId.INDEX_ID3, subMenuItems.get(5).getId());
            assertEquals(MenuItemId.ALBUM_ID3, subMenuItems.get(6).getId());
            assertEquals(MenuItemId.ALBUM_ID3_BY_FOLDER, subMenuItems.get(7).getId());
            assertEquals(MenuItemId.ALBUM_FILE_STRUCTURE, subMenuItems.get(8).getId());
            assertEquals(MenuItemId.ALBUM_FILE_STRUCTURE_BY_FOLDER, subMenuItems.get(9).getId());
            assertEquals(MenuItemId.ALBUM_ID3_BY_GENRE, subMenuItems.get(10).getId());
            assertEquals(MenuItemId.ALBUM_ID3_BY_FOLDER_GENRE, subMenuItems.get(11).getId());
            assertEquals(MenuItemId.SONG_BY_GENRE, subMenuItems.get(12).getId());
            assertEquals(MenuItemId.SONG_BY_FOLDER_GENRE, subMenuItems.get(13).getId());
            assertEquals(MenuItemId.AUDIOBOOK_BY_GENRE, subMenuItems.get(14).getId());
            assertEquals(MenuItemId.ALBUM_BY_GENRE, subMenuItems.get(15).getId());
            assertEquals(MenuItemId.PODCAST_DEFALT, subMenuItems.get(16).getId());
            assertEquals(MenuItemId.PLAYLISTS_DEFALT, subMenuItems.get(17).getId());
            assertEquals(MenuItemId.RECENTLY_ADDED_ALBUM, subMenuItems.get(18).getId());
            assertEquals(MenuItemId.RECENTLY_ADDED_ALBUM_BY_FOLDER, subMenuItems.get(19).getId());
            assertEquals(MenuItemId.RECENTLY_TAGGED_ALBUM, subMenuItems.get(20).getId());
            assertEquals(MenuItemId.RECENTLY_TAGGED_ALBUM_BY_FOLDER, subMenuItems.get(21).getId());
            assertEquals(MenuItemId.RANDOM_SONG, subMenuItems.get(22).getId());
            assertEquals(MenuItemId.RANDOM_SONG_BY_ARTIST, subMenuItems.get(23).getId());
            assertEquals(MenuItemId.RANDOM_SONG_BY_FOLDER_ARTIST, subMenuItems.get(24).getId());
            assertEquals(MenuItemId.RANDOM_SONG_BY_GENRE, subMenuItems.get(25).getId());
            assertEquals(MenuItemId.RANDOM_SONG_BY_FOLDER_GENRE, subMenuItems.get(26).getId());
            assertEquals(MenuItemId.RANDOM_ALBUM, subMenuItems.get(27).getId());
            subMenuItems.forEach(menuItem -> {
                assertTrue(menuItem.getName().isBlank());
                boolean enabled = switch (menuItem.getId()) {
                case MEDIA_FILE, ALBUM_ARTIST, ALBUM_ID3, ALBUM_ID3_BY_GENRE, PODCAST_DEFALT,
                        PLAYLISTS_DEFALT, RECENTLY_ADDED_ALBUM, RANDOM_SONG ->
                    true;
                default -> false;
                };
                assertEquals(enabled, menuItem.isEnabled());
            });
            return true;
        };

        List<MenuItemData> subMenuItems = menuItemDao.getSubMenuItems(ViewType.UPNP);
        assertTrue(validateDefaultSubMenuItems.apply(subMenuItems));

        subMenuItems.forEach(menuItem -> {
            menuItem.setEnabled(!menuItem.isEnabled());
            menuItem.setName("dummy");
            menuItemDao.updateMenuItem(menuItem);
        });

        menuItemManager.resetMenuItem(ViewType.UPNP, MenuItemManager.ResetMode.SUB_MENU);
        subMenuItems = menuItemDao.getSubMenuItems(ViewType.UPNP);
        assertTrue(validateDefaultSubMenuItems.apply(subMenuItems));
    }

    /**
     * This case will not occur in the latest version of Jpsonic. This case would
     * occur if a new menu was added to the latest version of the database and then
     * the Jpsonic was launched with an older version. (When viewed from a previous
     * version of Jpsonic, there are menus with unknown IDs.)
     */
    @Nested
    class UnknownMenuTest {

        @Test
        void testUnknownTopMenu() {

            assertEquals(8, menuItemDao.getTopMenuIds(ViewType.UPNP).size());
            assertEquals(8, menuItemManager.countTopMenuItems(ViewType.UPNP));

            assertEquals(8,
                    menuItemDao.getTopMenuItems(ViewType.UPNP, false, 0, Integer.MAX_VALUE).size());
            assertEquals(8,
                    menuItemManager
                        .findTopMenuItems(ViewType.UPNP, false, 0, Integer.MAX_VALUE)
                        .size());
            assertEquals(8, menuItemManager.getTopMenuItems(ViewType.UPNP).size());

            // Add a dummy sub menu
            templateWrapper.update("""
                    insert into menu_item
                    (view_type, id, parent, name, enabled, menu_item_order)
                    values(?, ?, ?, ?, ?, ?);
                    """, ViewType.UPNP.value(), -1, MenuItemId.ROOT.value(), "dummy", true, 99);

            // Menus with unknown IDs are excluded in the Service layer.
            assertEquals(9, menuItemDao.getTopMenuIds(ViewType.UPNP).size());
            assertEquals(8, menuItemManager.countTopMenuItems(ViewType.UPNP));

            assertEquals(9,
                    menuItemDao.getTopMenuItems(ViewType.UPNP, false, 0, Integer.MAX_VALUE).size());
            assertEquals(8,
                    menuItemManager
                        .findTopMenuItems(ViewType.UPNP, false, 0, Integer.MAX_VALUE)
                        .size());
            assertEquals(8, menuItemManager.getTopMenuItems(ViewType.UPNP).size());

            templateWrapper.update("""
                    delete from menu_item where id < 0
                    """);

            assertEquals(8, menuItemDao.getTopMenuIds(ViewType.UPNP).size());
            assertEquals(8,
                    menuItemDao.getTopMenuItems(ViewType.UPNP, false, 0, Integer.MAX_VALUE).size());
        }

        @Test
        void testUnknownSubMenu() {

            assertEquals(1, menuItemDao.getChildIds(ViewType.UPNP, MenuItemId.GENRE).size());
            assertEquals(1, menuItemManager.countChildlen(ViewType.UPNP, MenuItemId.GENRE));

            int genreSize = menuItemDao
                .findChildlen(ViewType.UPNP, MenuItemId.GENRE, false, 0, Integer.MAX_VALUE)
                .size();
            assertEquals(6, genreSize);
            assertEquals(genreSize,
                    menuItemManager
                        .findChildlen(ViewType.UPNP, MenuItemId.GENRE, false, 0, Integer.MAX_VALUE)
                        .size());

            int subMenuItemsSize = menuItemDao.getSubMenuItems(ViewType.UPNP).size();
            assertEquals(28, subMenuItemsSize);
            assertEquals(subMenuItemsSize, menuItemManager.getSubMenuItems(ViewType.UPNP).size());

            // Add a dummy sub menu
            templateWrapper.update("""
                    insert into menu_item
                    (view_type, id, parent, name, enabled, menu_item_order)
                    values(?, ?, ?, ?, ?, ?);
                    """, ViewType.UPNP.value(), -1, MenuItemId.GENRE.value(), "", true, 99);

            // Menus with unknown IDs are excluded in the Service layer.

            assertEquals(2, menuItemDao.getChildIds(ViewType.UPNP, MenuItemId.GENRE).size());
            assertEquals(1, menuItemManager.countChildlen(ViewType.UPNP, MenuItemId.GENRE));

            assertEquals(7,
                    menuItemDao
                        .findChildlen(ViewType.UPNP, MenuItemId.GENRE, false, 0, Integer.MAX_VALUE)
                        .size());
            assertEquals(6,
                    menuItemManager
                        .findChildlen(ViewType.UPNP, MenuItemId.GENRE, false, 0, Integer.MAX_VALUE)
                        .size());

            assertEquals(subMenuItemsSize + 1, menuItemDao.getSubMenuItems(ViewType.UPNP).size());
            assertEquals(subMenuItemsSize, menuItemManager.getSubMenuItems(ViewType.UPNP).size());

            templateWrapper.update("""
                    delete from menu_item where id < 0
                    """);
            assertEquals(1, menuItemDao.getChildIds(ViewType.UPNP, MenuItemId.GENRE).size());
            assertEquals(6,
                    menuItemDao
                        .findChildlen(ViewType.UPNP, MenuItemId.GENRE, false, 0, Integer.MAX_VALUE)
                        .size());
            assertEquals(subMenuItemsSize, menuItemDao.getSubMenuItems(ViewType.UPNP).size());
        }

        /*
         * #2660 This does not happen only in release versions, it is a glitch that can
         * occur when sharing release and development branches. An exception occurs if a
         * definition already exists for MenuItemId, the record exists in the DB, and
         * the name is not defined in the message resource.
         */
        @Test
        void testUnknownMessage() {
            // Add a dummy menu
            templateWrapper.update("""
                    insert into menu_item
                    (view_type, id, parent, name, enabled, menu_item_order)
                    values(?, ?, ?, ?, ?, ?);
                    """, ViewType.UPNP.value(), 130, MenuItemId.ROOT.value(), "dummy", true, 99);
            for (MenuItemWithDefaultName menuItem : menuItemManager
                .getTopMenuItems(ViewType.UPNP)) {
                assertNotNull(menuItem.getDefaultName());
            }
            menuItemManager.ensureUPnPSubMenuEnabled();
            templateWrapper.update("""
                    delete from menu_item where id = 130
                    """);
        }
    }
}

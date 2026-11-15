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

package com.tesshu.jpsonic.persistence.core.repository;

import static com.tesshu.jpsonic.persistence.base.DaoUtils.prefix;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import com.tesshu.jpsonic.domain.model.MenuItem;
import com.tesshu.jpsonic.domain.model.MenuItem.ViewType;
import com.tesshu.jpsonic.domain.system.MenuItemId;
import com.tesshu.jpsonic.infrastructure.menu.MenuItemData;
import com.tesshu.jpsonic.infrastructure.persistence.api.MenuItemDao;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository("menuItemDao")
@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "PMD.FieldDeclarationsShouldBeAtStartOfClass" })
public class MenuItemDaoImpl implements MenuItemDao {

    private static final String QUERY_COLUMNS = """
            view_type, id, parent, name, enabled, menu_item_order\s
            """;

    private final TemplateWrapper template;
    private final RowMapper<MenuItemData> rowMapper = (ResultSet rs, int num) -> new MenuItemData(
            ViewType.of(rs.getInt(1)), MenuItemId.of(rs.getInt(2)), MenuItemId.of(rs.getInt(3)),
            rs.getString(4), rs.getBoolean(5), rs.getInt(6));
    private final RowMapper<MenuItemId> idRowMapper = (ResultSet rs, int num) -> MenuItemId
        .of(rs.getInt(1));

    public MenuItemDaoImpl(TemplateWrapper templateWrapper) {
        template = templateWrapper;
    }

    @Override
    public List<MenuItemId> getTopMenuIds(ViewType viewType) {
        return template.query("""
                select id from menu_item
                where view_type=? and parent=? and enabled=?
                """, idRowMapper, viewType.value(), MenuItemId.ROOT.value(), true);
    }

    @Override
    public List<MenuItemData> getTopMenuItems(ViewType viewType, boolean enabledOnly, long offset,
            long count) {
        Map<String, Object> args = Map
            .of("type", viewType.value(), "parentId", MenuItemId.ROOT.value(), "enabledOnly",
                    enabledOnly, "count", count, "offset", offset);
        return template.namedQuery("select " + QUERY_COLUMNS + """
                from menu_item
                where view_type=:type and parent=:parentId %s
                order by enabled desc, menu_item_order, id
                limit :count offset :offset
                """.formatted(enabledOnly ? "and enabled=:enabledOnly" : ""), rowMapper, args);
    }

    @Override
    public List<MenuItemId> getChildIds(ViewType viewType, MenuItemId id) {
        return template.query("""
                select id from menu_item
                where view_type=? and parent=? and enabled=?
                """, idRowMapper, viewType.value(), id.value(), true);
    }

    @Override
    public List<MenuItemData> findChildlen(ViewType viewType, MenuItemId id, boolean enabledOnly,
            long offset, long count) {
        Map<String, Object> args = Map
            .of("type", viewType.value(), "parentId", id.value(), "enabledOnly", enabledOnly,
                    "count", count, "offset", offset);
        return template.namedQuery("select " + QUERY_COLUMNS + """
                from menu_item
                where view_type=:type and parent=:parentId %s
                order by menu_item_order, id
                limit :count offset :offset
                """.formatted(enabledOnly ? "and enabled=:enabledOnly" : ""), rowMapper, args);
    }

    @Override
    public void updateMenuItem(MenuItemData menuItem) {
        String sql = """
                update menu_item
                set view_type=?, parent=?, name=?, enabled=?, menu_item_order=?
                where id=?
                """;
        template
            .update(sql, menuItem.getViewType().value(), menuItem.getParent().value(),
                    menuItem.getName(), menuItem.isEnabled(), menuItem.getMenuItemOrder(),
                    menuItem.getId().value());
    }

    @Override
    public List<MenuItemData> getSubMenuItems(ViewType viewType) {
        Map<String, Object> args = Map
            .of("type", viewType.value(), "rootId", MenuItemId.ROOT.value());
        return template
            .namedQuery(
                    "select " + prefix(QUERY_COLUMNS, "sub")
                            + """
                                    from menu_item sub
                                    join menu_item top
                                    on sub.parent = top.id
                                    where sub.view_type=:type and sub.parent <> :rootId
                                    order by top.enabled desc, top.menu_item_order, sub.enabled desc, sub.menu_item_order, top.id, sub.id
                                    """,
                    rowMapper, args);
    }

    @Override
    public MenuItemData getMenuItemData(int id) {
        return template.queryOne("select " + QUERY_COLUMNS + """
                from menu_item
                where id=?
                """, rowMapper, id);
    }

    // ############################################################################
    // Jpsonic domain

    private final RowMapper<MenuItem> domainRowMapper = (ResultSet rs, int num) -> new MenuItem(
            ViewType.of(rs.getInt(1)), MenuItemId.of(rs.getInt(2)), MenuItemId.of(rs.getInt(3)),
            rs.getString(4), rs.getBoolean(5), rs.getInt(6));

    @Override
    public MenuItem getMenuItem(int id) {
        return template.queryOne("select " + QUERY_COLUMNS + """
                from menu_item
                where id=?
                """, domainRowMapper, id);
    }

}

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

package com.tesshu.jpsonic.feature.upnp.content.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.Artist;
import com.tesshu.jpsonic.domain.model.MusicFolder;
import com.tesshu.jpsonic.domain.model.MusicIndex;
import com.tesshu.jpsonic.domain.provider.resource.ArtistProvider;
import com.tesshu.jpsonic.domain.provider.resource.MusicFolderProvider;
import com.tesshu.jpsonic.infrastructure.settings.SKeys;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.GenreContainer;
import org.springframework.beans.factory.annotation.Autowired;

class IndexId3ProcTest extends AbstractNeedsScan {

    private static final List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> MUSIC_FOLDERS = Arrays
        .asList(new com.tesshu.jpsonic.persistence.api.entity.MusicFolder(1,
                resolveBaseMediaPath("Sort/Compare"), "Artists", true, Instant.now(), 1, false));

    @Autowired
    private ArtistProvider artistProvider;
    @Autowired
    private IndexId3Proc proc;
    @Autowired
    private MusicFolderProvider musicFolderProvider;
    @Autowired
    private SettingsFacade settingsFacade;

    @Override
    public List<com.tesshu.jpsonic.persistence.api.entity.MusicFolder> getMusicFolders() {
        return MUSIC_FOLDERS;
    }

    @BeforeEach
    void setup() {
        String simpleIndex = """
                A B C D E F G H I J K L M N O P Q R S T U V W X-Z(XYZ) \
                \u3042(\u30A2\u30A4\u30A6\u30A8\u30AA) \
                \u304B(\u30AB\u30AD\u30AF\u30B1\u30B3) \
                \u3055(\u30B5\u30B7\u30B9\u30BB\u30BD) \
                \u305F(\u30BF\u30C1\u30C4\u30C6\u30C8) \
                \u306A(\u30CA\u30CB\u30CC\u30CD\u30CE) \
                \u306F(\u30CF\u30D2\u30D5\u30D8\u30DB) \
                \u307E(\u30DE\u30DF\u30E0\u30E1\u30E2) \
                \u3084(\u30E4\u30E6\u30E8) \
                \u3089(\u30E9\u30EA\u30EB\u30EC\u30ED) \
                \u308F(\u30EF\u30F2\u30F3)
                """; // JP Index
        settingsFacade.commit(SKeys.general.index.indexString, simpleIndex);
        populateDatabaseOnlyOnce();
    }

    @Test
    void testGetProcId() {
        assertEquals("indexId3", proc.getProcId().getValue());
    }

    @Test
    void testCreateContainer() {
        MusicIndex index = proc.getDirectChild("A");
        Container container = proc.createContainer(index);
        assertInstanceOf(GenreContainer.class, container);
        assertEquals("indexId3/A", container.getId());
        assertEquals("indexId3", container.getParentID());
        assertEquals("A", container.getTitle());
        assertEquals(3, container.getChildCount());
    }

    @Test
    void testGetDirectChildren() {
        List<MusicIndex> indexes = proc.getDirectChildren(0, Integer.MAX_VALUE);
        assertEquals(9, indexes.size());
        assertEquals("A", indexes.get(0).index());
        assertEquals("B", indexes.get(1).index());
        assertEquals("C", indexes.get(2).index());
        assertEquals("D", indexes.get(3).index());
        assertEquals("E", indexes.get(4).index());
        assertEquals("あ", indexes.get(5).index());
        assertEquals("さ", indexes.get(6).index());
        assertEquals("は", indexes.get(7).index());
        assertEquals("#", indexes.get(8).index());

        indexes = proc.getDirectChildren(0, 5);
        assertEquals("A", indexes.get(0).index());
        assertEquals("B", indexes.get(1).index());
        assertEquals("C", indexes.get(2).index());
        assertEquals("D", indexes.get(3).index());
        assertEquals("E", indexes.get(4).index());

        indexes = proc.getDirectChildren(5, 4);
        assertEquals("あ", indexes.get(0).index());
        assertEquals("さ", indexes.get(1).index());
        assertEquals("は", indexes.get(2).index());
        assertEquals("#", indexes.get(3).index());
    }

    @Test
    void testGetDirectChildrenCount() {

        List<MusicFolder> folders = musicFolderProvider.getGuestFolders();

        assertEquals(32, artistProvider.countArtists(folders));

        List<Artist> artists = artistProvider.findArtists(folders, 0, Integer.MAX_VALUE);
        assertEquals(32, artists.size());

        // #
        assertEquals("10", artists.get(0).name());
        assertEquals("20", artists.get(1).name());
        assertEquals("50", artists.get(2).name());
        assertEquals("60", artists.get(3).name());
        assertEquals("70", artists.get(4).name());
        assertEquals("98", artists.get(5).name());
        assertEquals("99", artists.get(6).name());

        // A
        assertEquals("abcde", artists.get(7).name());
        assertEquals("abcいうえおあ", artists.get(8).name());
        assertEquals("abc亜伊鵜絵尾", artists.get(9).name());

        // B
        assertEquals("ＢＣＤＥＡ", artists.get(10).name());

        // C
        assertEquals("ĆḊÉÁḂ", artists.get(11).name());

        // D
        assertEquals("DEABC", artists.get(12).name());

        // E
        assertEquals("the eabcd", artists.get(13).name());
        assertEquals("episode 1", artists.get(14).name());
        assertEquals("episode 2", artists.get(15).name());
        assertEquals("episode 19", artists.get(16).name());

        // あいうえお
        assertEquals("亜伊鵜絵尾", artists.get(17).name());
        assertEquals("αβγ", artists.get(18).name());
        assertEquals("いうえおあ", artists.get(19).name());
        assertEquals("ゥェォァィ", artists.get(20).name());
        assertEquals("ｴｵｱｲｳ", artists.get(21).name());
        assertEquals("ｪｫｧｨｩ", artists.get(22).name());
        assertEquals("ぉぁぃぅぇ", artists.get(23).name());
        assertEquals("オアイウエ", artists.get(24).name());

        // さしすせそ
        assertEquals("春夏秋冬", artists.get(25).name());

        // はひふへほ
        assertEquals("貼られる", artists.get(26).name());
        assertEquals("パラレル", artists.get(27).name());
        assertEquals("馬力", artists.get(28).name());
        assertEquals("張り切る", artists.get(29).name());
        assertEquals("はるなつあきふゆ", artists.get(30).name());

        // #
        assertEquals("♂くんつ", artists.get(31).name());

        assertEquals(9, proc.getDirectChildrenCount());
    }

    @Test
    void testGetDirectChild() {
        assertEquals("A", proc.getDirectChild("A").index());
        assertEquals("B", proc.getDirectChild("B").index());
        assertEquals("C", proc.getDirectChild("C").index());
        assertEquals("D", proc.getDirectChild("D").index());
        assertEquals("E", proc.getDirectChild("E").index());
        assertEquals("あ", proc.getDirectChild("あ").index());
        assertEquals("さ", proc.getDirectChild("さ").index());
        assertEquals("は", proc.getDirectChild("は").index());
        assertEquals("#", proc.getDirectChild("#").index());
    }

    @Test
    void testGetChildren() {
        MusicIndex index = proc.getDirectChild("A");
        List<Artist> artists = proc.getChildren(index, 0, Integer.MAX_VALUE);
        assertEquals(3, artists.size());
        assertEquals("abcde", artists.get(0).name());
        assertEquals("abcいうえおあ", artists.get(1).name());
        assertEquals("abc亜伊鵜絵尾", artists.get(2).name());
    }

    @Test
    void testGetChildSizeOf() {
        MusicIndex index = proc.getDirectChild("A");
        assertEquals(3, proc.getChildSizeOf(index));
    }

    @Test
    void testAddChild() {
        DIDLContent content = new DIDLContent();
        assertEquals(0, content.getContainers().size());

        MusicIndex index = proc.getDirectChild("A");
        proc
            .getChildren(index, 0, Integer.MAX_VALUE)
            .stream()
            .forEach(artist -> proc.addChild(content, artist));
        assertEquals(3, content.getContainers().size());
    }
}

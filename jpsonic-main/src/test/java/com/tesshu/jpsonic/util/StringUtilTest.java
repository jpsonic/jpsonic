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

package com.tesshu.jpsonic.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.Test;

/**
 * Unit test of {@link StringUtil}.
 *
 * @author Sindre Mehus
 */
@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "PMD.TooManyStaticImports" })
class StringUtilTest {

    @Test
    void testToHtml() {
        assertNull(StringEscapeUtils.escapeHtml4(null));
        assertEquals("", StringEscapeUtils.escapeHtml4(""));
        assertEquals(" ", StringEscapeUtils.escapeHtml4(" "));
        assertEquals("q &amp; a", StringEscapeUtils.escapeHtml4("q & a"));
        assertEquals("q &amp; a &lt;&gt; b", StringEscapeUtils.escapeHtml4("q & a <> b"));
    }

    @Test
    void testFormatDurationMSS() {
        assertEquals("0:00", StringUtil.formatDurationMSS(0), "Error in formatDurationMSS().");
        assertEquals("0:05", StringUtil.formatDurationMSS(5), "Error in formatDurationMSS().");
        assertEquals("0:10", StringUtil.formatDurationMSS(10), "Error in formatDurationMSS().");
        assertEquals("0:59", StringUtil.formatDurationMSS(59), "Error in formatDurationMSS().");
        assertEquals("1:00", StringUtil.formatDurationMSS(60), "Error in formatDurationMSS().");
        assertEquals("1:01", StringUtil.formatDurationMSS(61), "Error in formatDurationMSS().");
        assertEquals("1:10", StringUtil.formatDurationMSS(70), "Error in formatDurationMSS().");
        assertEquals("10:00", StringUtil.formatDurationMSS(600), "Error in formatDurationMSS().");
        assertEquals("45:50", StringUtil.formatDurationMSS(2750), "Error in formatDurationMSS().");
        assertEquals("83:45", StringUtil.formatDurationMSS(5025), "Error in formatDurationMSS().");
        assertEquals("121:40", StringUtil.formatDurationMSS(7300), "Error in formatDurationMSS().");
    }

    @Test
    void testFormatDurationHMMSS() {
        assertEquals("0:00:00", StringUtil.formatDurationHMMSS(0),
                "Error in formatDurationHMMSS().");
        assertEquals("0:00:05", StringUtil.formatDurationHMMSS(5),
                "Error in formatDurationHMMSS().");
        assertEquals("0:00:10", StringUtil.formatDurationHMMSS(10),
                "Error in formatDurationHMMSS().");
        assertEquals("0:00:59", StringUtil.formatDurationHMMSS(59),
                "Error in formatDurationHMMSS().");
        assertEquals("0:01:00", StringUtil.formatDurationHMMSS(60),
                "Error in formatDurationHMMSS().");
        assertEquals("0:01:01", StringUtil.formatDurationHMMSS(61),
                "Error in formatDurationHMMSS().");
        assertEquals("0:01:10", StringUtil.formatDurationHMMSS(70),
                "Error in formatDurationHMMSS().");
        assertEquals("0:10:00", StringUtil.formatDurationHMMSS(600),
                "Error in formatDurationHMMSS().");
        assertEquals("0:45:50", StringUtil.formatDurationHMMSS(2750),
                "Error in formatDurationHMMSS().");
        assertEquals("1:23:45", StringUtil.formatDurationHMMSS(5025),
                "Error in formatDurationHMMSS().");
        assertEquals("2:01:40", StringUtil.formatDurationHMMSS(7300),
                "Error in formatDurationHMMSS().");
    }

    @Test
    void testFormatDuration() {
        assertEquals("0:00", StringUtil.formatDuration(0), "Error in formatDuration().");
        assertEquals("0:05", StringUtil.formatDuration(5), "Error in formatDuration().");
        assertEquals("0:10", StringUtil.formatDuration(10), "Error in formatDuration().");
        assertEquals("0:59", StringUtil.formatDuration(59), "Error in formatDuration().");
        assertEquals("1:00", StringUtil.formatDuration(60), "Error in formatDuration().");
        assertEquals("1:01", StringUtil.formatDuration(61), "Error in formatDuration().");
        assertEquals("1:10", StringUtil.formatDuration(70), "Error in formatDuration().");
        assertEquals("10:00", StringUtil.formatDuration(600), "Error in formatDuration().");
        assertEquals("45:50", StringUtil.formatDuration(2750), "Error in formatDuration().");
        assertEquals("1:23:45", StringUtil.formatDuration(5025), "Error in formatDuration().");
        assertEquals("2:01:40", StringUtil.formatDuration(7300), "Error in formatDuration().");
    }

    @Test
    void testSplit() {
        doTestSplit("u2 rem \"greatest hits\"", "u2", "rem", "greatest hits");
        doTestSplit("u2", "u2");
        doTestSplit("u2 rem", "u2", "rem");
        doTestSplit(" u2  \t rem ", "u2", "rem");
        doTestSplit("u2 \"rem\"", "u2", "rem");
        doTestSplit("u2 \"rem", "u2", "\"rem");
        doTestSplit("\"", "\"");

        assertEquals(0, StringUtil.split("").size());
        assertEquals(0, StringUtil.split(" ").size());
        assertEquals(0, StringUtil.split(null).size());
    }

    private void doTestSplit(String input, String... expected) {
        List<String> actual = StringUtil.split(input);
        assertEquals(expected.length, actual.size(), "Wrong number of elements.");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual.get(i), "Wrong criteria.");
        }
    }

    @Test
    void testParseInts() {
        doTestParseInts("123", 123);
        doTestParseInts("1 2 3", 1, 2, 3);
        doTestParseInts("10  20 \t\n 30", 10, 20, 30);

        assertSame(StringUtil.parseInts(null).length, 0, "Error in parseInts().");
        assertSame(StringUtil.parseInts("").length, 0, "Error in parseInts().");
        assertSame(StringUtil.parseInts(" ").length, 0, "Error in parseInts().");
        assertSame(StringUtil.parseInts("  ").length, 0, "Error in parseInts().");
    }

    private void doTestParseInts(String s, int... expected) {
        assertEquals(Arrays.toString(expected), Arrays.toString(StringUtil.parseInts(s)),
                "Error in parseInts().");
    }
}

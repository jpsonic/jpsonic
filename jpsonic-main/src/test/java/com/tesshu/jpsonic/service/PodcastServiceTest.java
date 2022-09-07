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

package com.tesshu.jpsonic.service;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PodcastServiceTest {

    private PodcastService podcastService;

    @BeforeEach
    public void setup() throws ExecutionException {
        podcastService = new PodcastService(null, null, null, null, null, null, null);
    }

    private ZonedDateTime toJST(String date) {
        // Parse and return in Japan Standard Time
        // "Japan" is a value for testing, actually systemDefault is used in Jpsonic.
        return ZonedDateTime.ofInstant(podcastService.parseDate(date), ZoneId.of("Japan"));
    }

    @Test
    void testParseDate() {

        assertNull(podcastService.parseDate(null));
        assertNull(podcastService.parseDate("Fri, 09 Sep 2022 08:49:03"));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        assertEquals("2022-09-09 17:41:00", fmt.format(toJST("Fri, 09 Sep 2022 08:41:00 +0000")));
        assertEquals("2022-09-09 16:42:00", fmt.format(toJST("Fri, 09 Sep 2022 08:42:00 +0100")));
        assertEquals("2022-09-09 15:43:00", fmt.format(toJST("Fri, 09 Sep 2022 08:43:00 +0200")));
        assertEquals("2022-09-09 17:44:00", fmt.format(toJST("09 Sep 2022 08:44:00 +0000")));
        assertEquals("2022-09-09 17:45:00", fmt.format(toJST("Fri, 09 Sep 2022 08:45 +0000")));
        assertEquals("2022-09-09 08:46:00", fmt.format(toJST("Fri, 09 Sep 2022 08:46:00 JST")));
        assertEquals("2022-09-09 08:47:01", fmt.format(toJST("Fri, 09 Sep 2022 08:47:01 ROK")));
        assertEquals("2022-09-09 22:48:02", fmt.format(toJST("Fri, 09 Sep 2022 08:48:02 CDT")));
        assertEquals("2022-09-09 21:49:03", fmt.format(toJST("Fri, 09 Sep 2022 08:49:03 EST")));
    }
}

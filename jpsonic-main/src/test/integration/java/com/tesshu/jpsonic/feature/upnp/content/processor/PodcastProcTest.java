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

package com.tesshu.jpsonic.feature.upnp.content.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.tesshu.jpsonic.AbstractNeedsScan;
import com.tesshu.jpsonic.domain.model.PodcastChannel;
import com.tesshu.jpsonic.domain.model.PodcastEpisode;
import com.tesshu.jpsonic.feature.upnp.content.ProcId;
import com.tesshu.jpsonic.persistence.base.DaoHelper;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings({ "PMD.TooManyStaticImports", "PMD.AvoidDuplicateLiterals" })
class PodcastProcTest extends AbstractNeedsScan {

    @Autowired
    private TemplateWrapper template;
        @Autowired
        private DaoHelper daoHelper;
        @Autowired
        private PodcastProc proc;

        @BeforeEach
        void setup() {
            
            int c = template.queryForInt("select count(*) from PODCAST_CHANNEL", 0);
            if (c > 0) {
                return;
            }
            daoHelper.getJdbcTemplate().update("""
                    INSERT INTO PODCAST_CHANNEL VALUES(0,'https://www.nhk.or.jp/s-media/news/podcast/list/v1/all.xml',
                    'NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9',
                    '\u3053\u3061\u3089\u306e\u30dd\u30c3\u30c9\u30ad\u30e3\u30b9\u30c8\u306f...',
                    'COMPLETED',NULL,'https://www.nhk.or.jp/s-media/img/18439M2W42_thumbnail.jpg');
                    INSERT INTO PODCAST_EPISODE VALUES(0,0,
                    'https://www.nhk.or.jp/s-media/news/podcast/audio/ab2c9c8290cf1a1b98a157c0b791bd8d_64k.mp3',
                    '/jpsonic/podcasts/NHK\u30e9\u65e5.mp3',
                    '\u30cb\u30e5\u30fc\u30b9 2026\u5e7409\u670823\u65e5\u5348\u524d10\u664200\u5206 2026\u5e749\u670823\u65e5'
                    ,NULL,'2026-09-23 10:05:00.000000',
                    '00:04:57',2376720,2376720,'COMPLETED',NULL);
                    INSERT INTO PODCAST_EPISODE VALUES(1,0,
                    'https://www.nhk.or.jp/s-media/news/podcast/audio/9f54ecf8ce24dde4193fff51bdf4fe6b_64k.mp3',
                    '/jpsonic/podcasts/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9 - 2026-09-23 - 1.mp3',
                    '\u30cb\u30e5\u30fc\u30b9 2026\u5e7409\u670823\u65e5\u5348\u524d09\u664200\u5206 2026\u5e749\u670823\u65e5',
                    NULL,'2026-09-23 09:05:00.000000','00:04:57',2376720,2376720,'COMPLETED',NULL);
                    INSERT INTO PODCAST_EPISODE VALUES(2,0,
                    'https://www.nhk.or.jp/s-media/news/podcast/audio/7b703408d4a5809c33bd872de4d377fd_64k.mp3',
                    '/jpsonic/podcasts/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9 - 2026-09-23 - 2.mp3',
                    '\u30cb\u30e5\u30fc\u30b9 2026\u5e7409\u670823\u65e5\u5348\u524d08\u664200\u5206 2026\u5e749\u670823\u65e5',
                    NULL,'2026-09-23 08:05:00.000000','00:04:57',2376720,2376720,'COMPLETED',NULL);
                    INSERT INTO PODCAST_EPISODE VALUES(3,0,
                    'https://www.nhk.or.jp/s-media/news/podcast/audio/f99cc8b522eb3780a3682a47b7d8d8b4_64k.mp3',
                    '/jpsonic/podcasts/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9 - 2026-09-23 - 3.mp3',
                    '\u30de\u30a4\u3042\u3055! \u5348\u524d7\u6642\u306eNHK\u30cb\u30e5\u30fc\u30b9 2026\u5e749\u670823\u65e5',
                    NULL,'2026-09-23 07:15:00.000000','00:14:30',6960528,6960528,'COMPLETED',NULL);
                    INSERT INTO PODCAST_EPISODE VALUES(4,0,
                    'https://www.nhk.or.jp/s-media/news/podcast/audio/000cb9831897fee694f9f3eb336c3dd2_64k.mp3',
                    '/jpsonic/podcasts/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9 - 2026-09-23 - 4.mp3',
                    '\u30de\u30a4\u3042\u3055! \u5348\u524d6\u6642\u306eNHK\u30cb\u30e5\u30fc\u30b9 2026\u5e749\u670823\u65e5',
                    NULL,'2026-09-23 06:14:00.000000',
                    '00:13:00',6240528,6240528,'COMPLETED',NULL);
                    INSERT INTO PODCAST_EPISODE VALUES(5,0,
                    'https://www.nhk.or.jp/s-media/news/podcast/audio/e80e78f6fdebfa9e3810ac7563cccfdb_64k.mp3',
                    '/jpsonic/podcasts/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9 - 2026-09-23 - 5.mp3',
                    '\u30de\u30a4\u3042\u3055! \u5348\u524d5\u6642\u306eNHK\u30cb\u30e5\u30fc\u30b9 2026\u5e749\u670823\u65e5',
                    NULL,'2026-09-23 05:16:00.000000',
                    '00:13:19',6392966,6392966,'COMPLETED',NULL);
                    INSERT INTO PODCAST_EPISODE VALUES(6,0,
                    'https://www.nhk.or.jp/s-media/news/podcast/audio/dbdaf3a1b8277895448989e8a9c591a7_64k.mp3',
                    '/jpsonic/podcasts/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9 - 2026-09-23 - 6.mp3',
                    '\u30cb\u30e5\u30fc\u30b9 2026\u5e7409\u670823\u65e5\u5348\u524d04\u664200\u5206 2026\u5e749\u670823\u65e5',
                    NULL,'2026-09-23 04:05:00.000000',
                    '00:04:57',2376720,2376720,'COMPLETED',NULL);
                    INSERT INTO PODCAST_EPISODE VALUES(7,0,
                    'https://www.nhk.or.jp/s-media/news/podcast/audio/52aa1ab90a70224001263ae64bd5f5af_64k.mp3',
                    '/jpsonic/podcasts/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9 - 2026-09-23 - 7.mp3',
                    '\u30cb\u30e5\u30fc\u30b9 2026\u5e7409\u670823\u65e5\u5348\u524d03\u664200\u5206 2026\u5e749\u670823\u65e5',
                    NULL,'2026-09-23 03:05:00.000000',
                    '00:04:57',2376720,2376720,'COMPLETED',NULL);
                    INSERT INTO PODCAST_EPISODE VALUES(8,0,
                    'https://www.nhk.or.jp/s-media/news/podcast/audio/c38ebb92762c10c24ce850dcf420de53_64k.mp3',
                    '/jpsonic/podcasts/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9 - 2026-09-23 - 8.mp3',
                    '\u65e5\u7c73\u9996\u8133\u4f1a\u8ac7\u95a2\u9023\u30cb\u30e5\u30fc\u30b9',NULL,'2026-09-23 02:47:00.000000',
                    '00:13:01',6254342,6254342,'COMPLETED',NULL);
                    INSERT INTO PODCAST_EPISODE VALUES(9,0,
                    'https://www.nhk.or.jp/s-media/news/podcast/audio/d602f4895517f2076af0b90ae8eec1ba_64k.mp3',
                    '/jpsonic/podcasts/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9/NHK\u30e9\u30b8\u30aa\u30cb\u30e5\u30fc\u30b9 - 2026-09-23 - 9.mp3',
                    '\u30cb\u30e5\u30fc\u30b9 2026\u5e7409\u670823\u65e5\u5348\u524d02\u664200\u5206 2026\u5e749\u670823\u65e5',
                    NULL,'2026-09-23 02:05:00.000000',
                    '00:04:57',2376720,2376720,'COMPLETED',NULL);
               
                    """);
        }

        @Test
        public void testGetProcId() {
           assertEquals(ProcId.PODCAST, proc.getProcId());
        }

        @Test
        public void testGetDirectChildren() {
           List<PodcastChannel> channels = proc.getDirectChildren(0, Integer.MAX_VALUE);
           assertEquals(1, channels.size());
        }

        @Test
        public void testGetDirectChildrenCount() {
            assertEquals(1, proc.getDirectChildrenCount());
        }

        @Test
        public void testGetDirectChild() {
            List<PodcastChannel> channels = proc.getDirectChildren(0, Integer.MAX_VALUE);
            PodcastChannel podcastChannel = proc.getDirectChild(Integer.toString(channels.get(0).id()));
            assertEquals("NHKラジオニュース", podcastChannel.title());
        }

        @Test
        public void testGetChildren() {
            List<PodcastChannel> channels = proc.getDirectChildren(0, Integer.MAX_VALUE);
            PodcastChannel podcastChannel = proc.getDirectChild(Integer.toString(channels.get(0).id()));
            assertEquals("NHKラジオニュース", podcastChannel.title());
            assertEquals(10, proc.getChildren(podcastChannel, 0, Integer.MAX_VALUE).size());

            List<PodcastEpisode> partialEpisodes =  proc.getChildren(podcastChannel, 9, 3);
            assertEquals(1, partialEpisodes.size());
            assertEquals("ニュース 2026年09月23日午前02時00分 2026年9月23日", partialEpisodes.get(0).title());
        }

        @Test
        public void testGetChildSizeOf() {
            List<PodcastChannel> channels = proc.getDirectChildren(0, Integer.MAX_VALUE);
            PodcastChannel podcastChannel = proc.getDirectChild(Integer.toString(channels.get(0).id()));
            assertEquals("NHKラジオニュース", podcastChannel.title());
            assertEquals(10, proc.getChildSizeOf(podcastChannel));
        }
}

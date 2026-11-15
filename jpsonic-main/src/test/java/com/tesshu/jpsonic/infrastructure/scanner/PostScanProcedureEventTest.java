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

package com.tesshu.jpsonic.infrastructure.scanner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.tesshu.jpsonic.domain.event.MusicFolderChangedEvent;
import com.tesshu.jpsonic.domain.event.ScanFinishedEvent;
import com.tesshu.jpsonic.persistence.base.TemplateWrapper;
import com.tesshu.jpsonic.persistence.core.repository.StaticsDao;
import com.tesshu.jpsonic.service.MediaFileCache;
import com.tesshu.jpsonic.service.PlaylistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(PostScanProcedureEventTest.PostScanProcedureEventTestConfiguration.class)
class PostScanProcedureEventTest {

    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private TestListener testListener;

    @Test
    void testPublish() {
        PostScanProcedure postScanProcedure = new PostScanProcedure(
                mock(MusicFolderServiceImpl.class), mock(IndexManager.class),
                mock(PlaylistService.class), mock(TemplateWrapper.class), mock(StaticsDao.class),
                mock(SortProcedureService.class), mock(MediaFileCache.class),
                mock(ScanHelper.class), publisher);

        assertFalse(testListener.isScanFinished());
        postScanProcedure.afterScan(mock(ScanContext.class));
        assertTrue(testListener.isScanFinished());
    }

    @Configuration
    static class PostScanProcedureEventTestConfiguration {

        @Bean
        TestListener testListener() {
            return new TestListener();
        }
    }

    @SuppressWarnings("PMD.TestClassWithoutTestCases")
    static class TestListener {

        private boolean called;

        boolean isScanFinished() {
            return called;
        }

        @EventListener
        void onMusicFolderChanged(MusicFolderChangedEvent e) {
            called = false;
        }

        @EventListener
        void onScanFinishedEvent(ScanFinishedEvent e) {
            called = true;
        }
    }
}

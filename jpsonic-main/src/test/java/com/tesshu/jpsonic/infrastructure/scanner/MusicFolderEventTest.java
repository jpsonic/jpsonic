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
import com.tesshu.jpsonic.persistence.api.repository.MusicFolderDao;
import com.tesshu.jpsonic.persistence.core.repository.StaticsDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(MusicFolderEventTest.MusicFolderEventTestConfiguration.class)
class MusicFolderEventTest {

    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private TestListener testListener;

    @Test
    void testPublish() {
        MusicFolderServiceImpl folderService = new MusicFolderServiceImpl(
                mock(MusicFolderDao.class), mock(StaticsDao.class),
                mock(ScannerStateServiceImpl.class), publisher);

        assertFalse(testListener.isClearMusicFolderCacheCalled());
        folderService.clearMusicFolderCache();
        assertTrue(testListener.isClearMusicFolderCacheCalled());
    }

    @Configuration
    static class MusicFolderEventTestConfiguration {

        @Bean
        TestListener testListener() {
            return new TestListener();
        }
    }

    @SuppressWarnings("PMD.TestClassWithoutTestCases")
    static class TestListener {

        private boolean called;

        boolean isClearMusicFolderCacheCalled() {
            return called;
        }

        @EventListener
        void onMusicFolderChanged(MusicFolderChangedEvent e) {
            called = true;
        }
    }
}

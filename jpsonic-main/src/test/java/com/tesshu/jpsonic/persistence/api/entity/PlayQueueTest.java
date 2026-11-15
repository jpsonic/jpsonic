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

package com.tesshu.jpsonic.persistence.api.entity;

import static com.tesshu.jpsonic.service.ServiceMockUtils.mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.tesshu.jpsonic.infrastructure.comparator.ComparatorsFacade;
import com.tesshu.jpsonic.infrastructure.comparator.JpsonicComparators;
import com.tesshu.jpsonic.infrastructure.language.MetadataReadingProcessor;
import com.tesshu.jpsonic.infrastructure.locale.ServerLocaleManager;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacade;
import com.tesshu.jpsonic.infrastructure.settings.SettingsFacadeBuilder;
import org.apache.commons.lang3.exception.UncheckedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Unit test of {@link PlayQueue}.
 *
 * @author Sindre Mehus
 */
@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "PMD.TestClassWithoutTestCases",
        "PMD.TooManyStaticImports" })
class PlayQueueTest {

    private JpsonicComparators jpsonicComparators;

    @BeforeEach
    void setup() {
        SettingsFacade settingsFacade = SettingsFacadeBuilder.create().build();
        ServerLocaleManager serverLocaleManager = new ServerLocaleManager(settingsFacade);
        ComparatorsFacade comparatorsFacade = new ComparatorsFacade(settingsFacade,
                serverLocaleManager, mock(MetadataReadingProcessor.class));
        jpsonicComparators = new JpsonicComparators(settingsFacade, comparatorsFacade);
    }

    @Test
    void testEmpty() {
        PlayQueue playQueue = new PlayQueue();
        assertEquals(0, playQueue.size());
        Assertions.assertTrue(playQueue.isEmpty());
        assertEquals(0, playQueue.getFiles().size());
        assertNull(playQueue.getCurrentFile());
    }

    @Test
    void testStatus() {
        PlayQueue playQueue = new PlayQueue();
        assertEquals(PlayQueue.Status.PLAYING, playQueue.getStatus());

        playQueue.setStatus(PlayQueue.Status.STOPPED);
        assertEquals(PlayQueue.Status.STOPPED, playQueue.getStatus());

        playQueue.addFiles(true, new TestMediaFile());
        assertEquals(PlayQueue.Status.PLAYING, playQueue.getStatus());

        playQueue.clear();
        assertEquals(PlayQueue.Status.PLAYING, playQueue.getStatus());
    }

    @Test
    void testMoveUp() {
        PlayQueue playQueue = createPlaylist(0, "A", "B", "C", "D");
        playQueue.moveUp(0);
        assertPlaylistEquals(playQueue, 0, "A", "B", "C", "D");

        playQueue = createPlaylist(0, "A", "B", "C", "D");
        playQueue.moveUp(9999);
        assertPlaylistEquals(playQueue, 0, "A", "B", "C", "D");

        playQueue = createPlaylist(1, "A", "B", "C", "D");
        playQueue.moveUp(1);
        assertPlaylistEquals(playQueue, 0, "B", "A", "C", "D");

        playQueue = createPlaylist(3, "A", "B", "C", "D");
        playQueue.moveUp(3);
        assertPlaylistEquals(playQueue, 2, "A", "B", "D", "C");
    }

    @Test
    void testMoveDown() {
        PlayQueue playQueue = createPlaylist(0, "A", "B", "C", "D");
        playQueue.moveDown(0);
        assertPlaylistEquals(playQueue, 1, "B", "A", "C", "D");

        playQueue = createPlaylist(0, "A", "B", "C", "D");
        playQueue.moveDown(9999);
        assertPlaylistEquals(playQueue, 0, "A", "B", "C", "D");

        playQueue = createPlaylist(1, "A", "B", "C", "D");
        playQueue.moveDown(1);
        assertPlaylistEquals(playQueue, 2, "A", "C", "B", "D");

        playQueue = createPlaylist(3, "A", "B", "C", "D");
        playQueue.moveDown(3);
        assertPlaylistEquals(playQueue, 3, "A", "B", "C", "D");
    }

    @Test
    void testRemove() {
        PlayQueue playQueue = createPlaylist(0, "A", "B", "C", "D");
        playQueue.removeFileAt(0);
        assertPlaylistEquals(playQueue, 0, "B", "C", "D");

        playQueue = createPlaylist(1, "A", "B", "C", "D");
        playQueue.removeFileAt(0);
        assertPlaylistEquals(playQueue, 0, "B", "C", "D");

        playQueue = createPlaylist(0, "A", "B", "C", "D");
        playQueue.removeFileAt(3);
        assertPlaylistEquals(playQueue, 0, "A", "B", "C");

        playQueue = createPlaylist(1, "A", "B", "C", "D");
        playQueue.removeFileAt(1);
        assertPlaylistEquals(playQueue, 1, "A", "C", "D");

        playQueue = createPlaylist(3, "A", "B", "C", "D");
        playQueue.removeFileAt(3);
        assertPlaylistEquals(playQueue, 2, "A", "B", "C");

        playQueue = createPlaylist(0, "A");
        playQueue.removeFileAt(0);
        assertPlaylistEquals(playQueue, -1);
    }

    @Test
    void testNext() {
        PlayQueue playQueue = createPlaylist(0, "A", "B", "C");
        assertFalse(playQueue.isRepeatEnabled());
        playQueue.next();
        assertPlaylistEquals(playQueue, 1, "A", "B", "C");
        playQueue.next();
        assertPlaylistEquals(playQueue, 2, "A", "B", "C");
        playQueue.next();
        assertPlaylistEquals(playQueue, -1, "A", "B", "C");

        playQueue = createPlaylist(0, "A", "B", "C");
        playQueue.setRepeatEnabled(true);
        Assertions.assertTrue(playQueue.isRepeatEnabled());
        playQueue.next();
        assertPlaylistEquals(playQueue, 1, "A", "B", "C");
        playQueue.next();
        assertPlaylistEquals(playQueue, 2, "A", "B", "C");
        playQueue.next();
        assertPlaylistEquals(playQueue, 0, "A", "B", "C");
    }

    @Test
    void testPlayAfterEndReached() {
        PlayQueue playQueue = createPlaylist(2, "A", "B", "C");
        playQueue.setStatus(PlayQueue.Status.PLAYING);
        playQueue.next();
        assertNull(playQueue.getCurrentFile());
        assertEquals(PlayQueue.Status.STOPPED, playQueue.getStatus());

        playQueue.setStatus(PlayQueue.Status.PLAYING);
        assertEquals(PlayQueue.Status.PLAYING, playQueue.getStatus());
        assertEquals(0, playQueue.getIndex());
        assertEquals("A", playQueue.getCurrentFile().getName());
    }

    @Test
    void testPlayLast() {
        PlayQueue playQueue = createPlaylist(1, "A", "B", "C");

        playQueue.addFiles(true, new TestMediaFile("D"));
        assertPlaylistEquals(playQueue, 1, "A", "B", "C", "D");

        playQueue.addFiles(false, new TestMediaFile("E"));
        assertPlaylistEquals(playQueue, 0, "E");
    }

    @Test
    void testAddFilesAt() {
        PlayQueue playQueue = createPlaylist(0);

        playQueue
            .addFilesAt(Arrays
                .asList(new TestMediaFile("A"), new TestMediaFile("B"), new TestMediaFile("C")), 0);
        assertPlaylistEquals(playQueue, 0, "A", "B", "C");

        playQueue.addFilesAt(Arrays.asList(new TestMediaFile("D"), new TestMediaFile("E")), 1);
        assertPlaylistEquals(playQueue, 0, "A", "D", "E", "B", "C");

        playQueue.addFilesAt(Arrays.asList(new TestMediaFile("F")), 0);
        assertPlaylistEquals(playQueue, 0, "F", "A", "D", "E", "B", "C");

    }

    @Test
    @SuppressWarnings("PMD.UnusedLocalVariable")
    void testLock() throws Exception {

        int threadsCount = 1_000;

        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setWaitForTasksToCompleteOnShutdown(true); // To handle Stream
        executor.setAwaitTerminationMillis(1_000);
        executor.setQueueCapacity(threadsCount);
        executor.setCorePoolSize(threadsCount);
        executor.setMaxPoolSize(threadsCount);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setDaemon(true);
        executor.initialize();

        MetricRegistry metrics = new MetricRegistry();
        List<Future<Integer>> futures = new ArrayList<>();
        Timer globalTimer = metrics
            .timer(MetricRegistry.name(PlayQueueTest.class, "PlayQueue#setIndex"));

        String[] songs = new String[threadsCount];
        for (int i = 0; i < songs.length; i++) {
            songs[i] = String.valueOf(i);
        }

        PlayQueue playQueue = createPlaylist(0, songs);
        Random random = new Random();

        for (int i = 0; i < threadsCount; i++) {
            futures.add(executor.submit(() -> {
                try (Timer.Context globalTimerContext = globalTimer.time()) {
                    playQueue.setIndex(random.nextInt(threadsCount));
                } catch (IllegalArgumentException e) {
                    throw new UncheckedException(e);
                }
                return 1;
            }));
        }

        assertEquals(threadsCount, futures.stream().mapToInt(future -> {
            try {
                assertNotNull(future.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new UncheckedException(e);
            }
            return 1;
        }).sum());
        executor.shutdown();

        ConsoleReporter.Builder builder = ConsoleReporter
            .forRegistry(metrics)
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS);
        try (ConsoleReporter reporter = builder.build()) {
            // to be none
        }
    }

    @Test
    void testUndo() {
        PlayQueue playQueue = createPlaylist(0, "A", "B", "C");
        playQueue.setIndex(2);
        playQueue.undo();
        assertPlaylistEquals(playQueue, 0, "A", "B", "C");

        playQueue.removeFileAt(2);
        playQueue.undo();
        assertPlaylistEquals(playQueue, 0, "A", "B", "C");

        playQueue.clear();
        playQueue.undo();
        assertPlaylistEquals(playQueue, 0, "A", "B", "C");

        playQueue.addFiles(true, new TestMediaFile());
        playQueue.undo();
        assertPlaylistEquals(playQueue, 0, "A", "B", "C");

        playQueue.moveDown(1);
        playQueue.undo();
        assertPlaylistEquals(playQueue, 0, "A", "B", "C");

        playQueue.moveUp(1);
        playQueue.undo();
        assertPlaylistEquals(playQueue, 0, "A", "B", "C");
    }

    @Test
    void testOrder() {
        PlayQueue playQueue = new PlayQueue();
        playQueue.addFiles(true, new TestMediaFile(2, "Artist A", "Album B"));
        playQueue.addFiles(true, new TestMediaFile(1, "Artist C", "Album C"));
        playQueue.addFiles(true, new TestMediaFile(3, "Artist B", "Album A"));
        playQueue.addFiles(true, new TestMediaFile(null, "Artist D", "Album D"));
        playQueue.setIndex(2);
        assertEquals(3, playQueue.getCurrentFile().getTrackNumber(), "Error in sort.");

        // Order by track.
        playQueue.sort(jpsonicComparators.mediaFileOrderBy(JpsonicComparators.OrderBy.TRACK));
        assertNull(playQueue.getFile(0).getTrackNumber(), "Error in sort().");
        assertEquals(1, playQueue.getFile(1).getTrackNumber(), "Error in sort.");
        assertEquals(2, playQueue.getFile(2).getTrackNumber(), "Error in sort.");
        assertEquals(3, playQueue.getFile(3).getTrackNumber(), "Error in sort.");
        assertEquals(3, playQueue.getCurrentFile().getTrackNumber());

        // Order by artist.
        playQueue.sort(jpsonicComparators.mediaFileOrderBy(JpsonicComparators.OrderBy.ARTIST));
        assertEquals("Artist A", playQueue.getFile(0).getArtist(), "Error in sort.");
        assertEquals("Artist B", playQueue.getFile(1).getArtist(), "Error in sort.");
        assertEquals("Artist C", playQueue.getFile(2).getArtist(), "Error in sort.");
        assertEquals("Artist D", playQueue.getFile(3).getArtist(), "Error in sort.");
        assertEquals(3, playQueue.getCurrentFile().getTrackNumber(), "Error in sort.");

        // Order by album.
        playQueue.sort(jpsonicComparators.mediaFileOrderBy(JpsonicComparators.OrderBy.ALBUM));
        assertEquals("Album A", playQueue.getFile(0).getAlbumName(), "Error in sort.");
        assertEquals("Album B", playQueue.getFile(1).getAlbumName(), "Error in sort.");
        assertEquals("Album C", playQueue.getFile(2).getAlbumName(), "Error in sort.");
        assertEquals("Album D", playQueue.getFile(3).getAlbumName(), "Error in sort.");
        assertEquals(3, playQueue.getCurrentFile().getTrackNumber(), "Error in sort.");
    }

    private void assertPlaylistEquals(PlayQueue playQueue, int index, String... songs) {
        assertEquals(songs.length, playQueue.size());
        for (int i = 0; i < songs.length; i++) {
            assertEquals(songs[i], playQueue.getFiles().get(i).getName());
        }

        if (index == -1) {
            assertNull(playQueue.getCurrentFile());
        } else {
            assertEquals(songs[index], playQueue.getCurrentFile().getName());
        }
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // (TestMediaFile) Not reusable.
    private PlayQueue createPlaylist(int index, String... songs) {
        PlayQueue playQueue = new PlayQueue();
        for (String song : songs) {
            playQueue.addFiles(true, new TestMediaFile(song));
        }
        playQueue.setIndex(index);
        return playQueue;
    }

    private static class TestMediaFile extends MediaFile {

        private String name;
        private Integer track;
        private String album;
        private String artist;

        TestMediaFile() {
            super();
        }

        TestMediaFile(String name) {
            super();
            this.name = name;
        }

        TestMediaFile(Integer track, String artist, String album) {
            super();
            this.track = track;
            this.album = album;
            this.artist = artist;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public Integer getTrackNumber() {
            return track;
        }

        @Override
        public String getArtist() {
            return artist;
        }

        @Override
        public String getArtistReading() {
            return artist;
        }

        @Override
        public String getAlbumName() {
            return album;
        }

        @Override
        public String getAlbumReading() {
            return album;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, track, album, artist);
        }
    }
}

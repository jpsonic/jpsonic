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
 * (C) 2025 tesshucom
 */

package com.tesshu.jpsonic.infrastructure.scanner;

import com.tesshu.jpsonic.infrastructure.core.LifecyclePhase;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Adapter that bridges the lifecycle of PodcastServiceImpl to Spring. Uses
 * SmartLifecycle to call init() on application start and markDestroy() on
 * shutdown.
 */
@Component
public class PodcastDisposer implements SmartLifecycle {

    // NOTE: The lifecycle of PodcastService may be merged into SCAN in the future.
    // Currently, this Disposer is kept for SmartLifecycle integration.
    private final PodcastServiceImpl podcastService;

    public PodcastDisposer(PodcastServiceImpl podcastService) {
        this.podcastService = podcastService;
    }

    @Override
    public void start() {
        // no-op
    }

    @Override
    public void stop() {
        podcastService.markDestroy();
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return podcastService.isRunning();
    }

    @Override
    public int getPhase() {
        return LifecyclePhase.PODCAST.getValue();
    }
}

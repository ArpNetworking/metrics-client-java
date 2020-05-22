/*
 * Copyright 2017 Inscope Metrics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.inscopemetrics.client.impl;

import io.inscopemetrics.client.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of nanosecond {@link StopWatch}. This class is thread safe but
 * does not provide synchronized access across threads.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TsdNanoStopWatch implements StopWatch {

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(TsdNanoStopWatch.class);

    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final long startedAtNanoSeconds;
    private long elapsedNanoSeconds;
    private final Logger logger;

    /**
     * Create a new stop watch that is immediately started.
     */
    public TsdNanoStopWatch() {
        startedAtNanoSeconds = System.nanoTime();
        logger = DEFAULT_LOGGER;
    }

    TsdNanoStopWatch(final Logger logger) {
        startedAtNanoSeconds = System.nanoTime();
        this.logger = logger;
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public void stop() {
        if (!isRunning.getAndSet(false)) {
            logger.warn("Ignore call to stop; stopwatch already stopped");
            return;
        }
        elapsedNanoSeconds = System.nanoTime() - startedAtNanoSeconds;
    }

    @Override
    public long getElapsedTime() {
        if (isRunning.get()) {
            logger.warn("Invalid call to getElapsedTime; stopwatch not stopped");
            return 0;
        }
        return elapsedNanoSeconds;
    }

    @Override
    public TimeUnit getUnit() {
        return TimeUnit.NANOSECONDS;
    }

    @Override
    public String toString() {
        return String.format(
                "TsdNanoStopWatch{IsRunning=%s, StartNanos=%d, Elapsed=%s}",
                isRunning,
                startedAtNanoSeconds,
                elapsedNanoSeconds);
    }
}

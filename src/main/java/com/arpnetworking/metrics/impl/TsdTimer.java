/*
 * Copyright 2014 Groupon.com
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
package com.arpnetworking.metrics.impl;

import com.arpnetworking.metrics.Quantity;
import com.arpnetworking.metrics.StopWatch;
import com.arpnetworking.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link Timer}. This class is thread safe but does not
 * provide synchronized access across threads.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
/* package private */ final class TsdTimer implements Timer, Quantity {
    /**
     * Package private static factory. All {@link TsdTimer} instances should
     * be created through the {@link TsdMetrics} instance.
     *
     * @param name The name of the timer.
     * {@link TsdTimer} which could cause garbage collection delays.
     */
    /* package private */ static TsdTimer newInstance(final String name) {
        return new TsdTimer(name, DEFAULT_LOGGER);
    }

    @Override
    public void stop() {
        close();
    }

    @Override
    public void close() {
        final boolean stopped = _state.compareAndSet(State.RUNNING, State.STOPPED);

        if (stopped) {
            // The previous state was running for this thread, so we are the
            // ones that should stop the StopWatch instance.
            _stopWatch.stop();
        } else {
            // One of these hints is guaranteed to be logged since the state can
            // only transition from running to stopped or aborted and if this
            // thread did not make that transition then another already did and
            // the updated state should be available for logging.
            if (State.ABORTED.equals(_state.get())) {
                _logger.warn(String.format("Timer closed/stopped after aborted; timer=%s", this));
            } else /* if (State.STOPPED.equals(_state.get())) */ {
                _logger.warn(String.format("Timer closed/stopped multiple times; timer=%s", this));
            }
        }
    }

    @Override
    public void abort() {
        final boolean aborted = _state.compareAndSet(State.RUNNING, State.ABORTED);

        if (!aborted) {
            // One of these hints is guaranteed to be logged since the state can
            // only transition from running to stopped or aborted and if this
            // thread did not make that transition then another already did and
            // the updated state should be available for logging.
            if (State.ABORTED.equals(_state.get())) {
                _logger.warn(String.format("Timer aborted multiple times; timer=%s", this));
            } else /* if (State.STOPPED.equals(_state.get())) */ {
                _logger.warn(String.format("Timer aborted after closed/stopped; timer=%s", this));
            }
        }
    }

    @Override
    public Number getValue() {
        if (State.RUNNING.equals(_state.get())) {
            _logger.warn(String.format("Timer access before it is closed/stopped; timer=%s", this));
            return 0.0;
        }
        if (State.ABORTED.equals(_state.get())) {
            _logger.warn(String.format("Invalid aborted timer value access; timer=%s", this));
            return 0.0;
        }
        return Utility.convertTimeUnit(
                _stopWatch.getElapsedTime(),
                _stopWatch.getUnit(),
                TimeUnit.SECONDS);
    }

    @Override
    public boolean isRunning() {
        return State.RUNNING.equals(_state.get());
    }

    @Override
    public boolean isAborted() {
        return State.ABORTED.equals(_state.get());
    }

    @Override
    public String toString() {
        return String.format(
                "TsdTimer{Name=%s, StopWatch=%s, State=%s}",
                _name,
                _stopWatch,
                _state);
    }

    // NOTE: Package private for testing
    TsdTimer(final String name, final Logger logger) {
        _name = name;
        _logger = logger;
        _stopWatch = StopWatch.start();
        _state = new AtomicReference<>(State.RUNNING);
    }

    private final String _name;
    private final AtomicReference<State> _state;
    private final StopWatch _stopWatch;
    private final Logger _logger;

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(TsdTimer.class);

    private enum State {
        RUNNING,
        STOPPED,
        ABORTED
    }
}

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
package com.arpnetworking.metrics.impl;

import com.arpnetworking.metrics.Quantity;
import com.arpnetworking.metrics.StopWatch;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple thread-safe implementation of nanosecond {@link StopWatch}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TsdStopWatch implements StopWatch {

    @Override
    public boolean isRunning() {
        return _isRunning.get();
    }

    @Override
    public void stop() {
        if (!_isRunning.getAndSet(false)) {
            throw new IllegalStateException("StopWatch is not running.");
        }
        _elapsedNanoSeconds = TsdQuantity.newInstance(System.nanoTime() - _startedAtNanoSeconds);
    }

    @Override
    public Quantity getElapsedTime() {
        if (_isRunning.get()) {
            throw new IllegalStateException("StopWatch is still running.");
        }
        return _elapsedNanoSeconds;
    }

    @Override
    public TimeUnit getUnit() {
        return TimeUnit.NANOSECONDS;
    }

    @Override
    public String toString() {
        return String.format(
                "TsdStopWatch{IsRunning=%s, StartNanos=%d, Elapsed=%s}",
                _isRunning,
                _startedAtNanoSeconds,
                _elapsedNanoSeconds);
    }

    /**
     * Create a new stop watch that is immediately started.
     */
    public TsdStopWatch() {
        _startedAtNanoSeconds = System.nanoTime();
    }

    private final AtomicBoolean _isRunning = new AtomicBoolean(true);
    private long _startedAtNanoSeconds;
    private Quantity _elapsedNanoSeconds;
}

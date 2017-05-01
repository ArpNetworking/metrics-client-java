/**
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
import com.arpnetworking.metrics.Timer;
import com.arpnetworking.metrics.Unit;
import com.arpnetworking.metrics.Units;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of <code>Timer</code>. This class is thread safe.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
/* package private */ final class TsdTimer implements Timer, Quantity {
    /**
     * Package private constructor. All <code>TsdTimer</code> instances should
     * be created through the <code>TsdMetrics</code> instance.
     *
     * @param name The name of the timer.
     * @param isOpen Reference to state of containing <code>TsdMetrics</code>
     * instance. This is provided as a separate parameter to avoid creating a
     * cyclical dependency between <code>TsdMetrics</code> and
     * <code>TsdTimer</code> which could cause garbage collection delays.
     */
    /* package private */static TsdTimer newInstance(final String name, final AtomicBoolean isOpen) {
        return new TsdTimer(name, isOpen, DEFAULT_LOGGER);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        final boolean wasRunning = _isRunning.getAndSet(false);
        final boolean wasAborted = _isAborted.get();
        if (!_isOpen.get()) {
            _logger.warn(String.format("Timer closed/stopped after metrics instance closed; timer=%s", this));
        }
        if (wasAborted) {
            _logger.warn(String.format("Timer closed/stopped after aborted; timer=%s", this));
        } else if (!wasRunning) {
            _logger.warn(String.format("Timer closed/stopped multiple times; timer=%s", this));
        } else {
            _elapsedTime = System.nanoTime() - _startTime;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void abort() {
        final boolean wasAborted = _isAborted.getAndSet(true);
        final boolean wasRunning = _isRunning.getAndSet(false);
        if (!_isOpen.get()) {
            _logger.warn(String.format("Timer aborted after metrics instance closed; timer=%s", this));
        }
        if (wasAborted) {
            _logger.warn(String.format("Timer aborted multiple times; timer=%s", this));
        } else if (!wasRunning) {
            _logger.warn(String.format("Timer aborted after closed/stopped; timer=%s", this));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Number getValue() {
        if (_isRunning.get()) {
            _logger.warn(String.format("Timer access before it is closed/stopped; timer=%s", this));
        }
        return _elapsedTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Unit getUnit() {
        return Units.NANOSECOND;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRunning() {
        return _isRunning.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAborted() {
        return _isAborted.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format(
                "TsdTimer{Name=%s, StartTime=%s, ElapsedTime=%s, IsRunning=%s, IsAborted=%s, IsOpen=%s}",
                _name,
                _startTime,
                _elapsedTime,
                _isRunning,
                _isAborted,
                _isOpen);
    }

    // NOTE: Package private for testing
    TsdTimer(final String name, final AtomicBoolean isOpen, final Logger logger) {
        _name = name;
        _isOpen = isOpen;
        _logger = logger;
        _startTime = System.nanoTime();
        _isRunning = new AtomicBoolean(true);
        _isAborted = new AtomicBoolean(false);
    }

    private final String _name;
    private final AtomicBoolean _isOpen;
    private final AtomicBoolean _isRunning;
    private final AtomicBoolean _isAborted;
    private final long _startTime;
    private long _elapsedTime = 0;
    private final Logger _logger;

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(TsdTimer.class);
}

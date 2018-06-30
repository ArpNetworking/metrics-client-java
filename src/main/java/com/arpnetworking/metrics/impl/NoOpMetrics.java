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

import com.arpnetworking.metrics.Counter;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.Timer;
import com.arpnetworking.metrics.Unit;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/**
 * Implementation of <code>Metrics</code> that provides safe interactions
 * but does not actually publish any metrics. This is useful for merging
 * codepaths where in one clients provide a <code>Metrics</code> instance
 * and in another where they do not without having to resort to the use
 * of <code>null</code> or <code>Optional</code>.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class NoOpMetrics implements Metrics {

    @Override
    public Counter createCounter(final String name) {
        return new NoOpCounter();
    }

    @Override
    public void incrementCounter(final String name) {
        // Do nothing
    }

    @Override
    public void incrementCounter(final String name, final long value) {
        // Do nothing
    }

    @Override
    public void decrementCounter(final String name) {
        // Do nothing
    }

    @Override
    public void decrementCounter(final String name, final long value) {
        // Do nothing
    }

    @Override
    public void resetCounter(final String name) {
        // Do nothing
    }

    @Override
    public Timer createTimer(final String name) {
        return new NoOpTimer();
    }

    @Override
    public void startTimer(final String name) {
        // Do nothing
    }

    @Override
    public void stopTimer(final String name) {
        // Do nothing
    }

    /**
     * @deprecated The use of TimeUnit directly in signatures is deprecated Use
     * {@link Metrics#setTimer(String, long, Unit)} instead.
     */
    @Override
    @Deprecated
    public void setTimer(final String name, final long duration, @Nullable final TimeUnit unit) {
        // Do nothing
    }

    @Override
    public void setTimer(final String name, final long duration, @Nullable final Unit unit) {
        // Do nothing
    }

    @Override
    public void setGauge(final String name, final double value) {
        // Do nothing
    }

    @Override
    public void setGauge(final String name, final double value, @Nullable final Unit unit) {
        // Do nothing
    }

    @Override
    public void setGauge(final String name, final long value) {
        // Do nothing
    }

    @Override
    public void setGauge(final String name, final long value, @Nullable final Unit unit) {
        // Do nothing
    }

    @Override
    public void addAnnotation(final String key, final String value) {
        // Do nothing
    }

    @Override
    public void addAnnotations(final Map<String, String> map) {
        // Do nothing
    }

    @Override
    public boolean isOpen() {
        return _isOpen.get();
    }

    @Override
    public void close() {
        if (_isOpen.getAndSet(false)) {
            _finalTimestamp = _clock.instant();
        }
    }

    @Override
    @Nullable
    public Instant getOpenTime() {
        return _initialTimestamp;
    }

    @Override
    @Nullable
    public Instant getCloseTime() {
        return _finalTimestamp;
    }

    @Override
    public String toString() {
        return "NoOpMetrics";
    }

    /**
     * Public constructor.
     */
    public NoOpMetrics() {
        this(Clock.systemUTC());
    }

    /* package private */ NoOpMetrics(final Clock clock) {
        _clock = clock;
        _initialTimestamp = _clock.instant();
    }

    private final AtomicBoolean _isOpen = new AtomicBoolean(true);
    private final Clock _clock;
    private final Instant _initialTimestamp;
    private Instant _finalTimestamp = null;
}

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

import io.inscopemetrics.client.Counter;
import io.inscopemetrics.client.Metrics;
import io.inscopemetrics.client.Timer;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/**
 * Implementation of {@link Metrics} that provides safe interactions
 * but does not actually publish any metrics. This is useful for merging
 * codepaths where in one clients provide a {@link Metrics} instance
 * and in another where they do not without having to resort to the use
 * of {@code null} or {@code Optional}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class NoOpMetrics implements Metrics {

    private final AtomicBoolean isOpen = new AtomicBoolean(true);
    private final Clock clock;
    private final Instant initialTimestamp;
    private Instant finalTimestamp = null;

    /**
     * Public constructor.
     */
    public NoOpMetrics() {
        this(Clock.systemUTC());
    }

    NoOpMetrics(final Clock clock) {
        this.clock = clock;
        initialTimestamp = this.clock.instant();
    }

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

    @Override
    public void setTimer(final String name, final long duration, @Nullable final TimeUnit unit) {
        // Do nothing
    }

    @Override
    public void setGauge(final String name, final double value) {
        // Do nothing
    }

    @Override
    public void setGauge(final String name, final long value) {
        // Do nothing
    }

    @Override
    public void addDimension(final String key, final String value) {
        // Do nothing
    }

    @Override
    public void addDimensions(final Map<String, String> map) {
        // Do nothing
    }

    @Override
    public boolean isOpen() {
        return isOpen.get();
    }

    @Override
    public void close() {
        if (isOpen.getAndSet(false)) {
            finalTimestamp = clock.instant();
        }
    }

    @Override
    @Nullable
    public Instant getOpenTime() {
        return initialTimestamp;
    }

    @Override
    @Nullable
    public Instant getCloseTime() {
        return finalTimestamp;
    }

    @Override
    public String toString() {
        return "NoOpMetrics";
    }
}

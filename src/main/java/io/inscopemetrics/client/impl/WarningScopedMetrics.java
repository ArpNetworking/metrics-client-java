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

import com.arpnetworking.commons.slf4j.RateLimitedLogger;
import io.inscopemetrics.client.Counter;
import io.inscopemetrics.client.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Implementation of {@link io.inscopemetrics.client.ScopedMetrics} that
 * provides safe interactions but does not actually publish any metrics and
 * warns periodically of any usage with the reason provided on construction.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class WarningScopedMetrics extends NoOpScopedMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(WarningScopedMetrics.class);

    private final String message;
    private final Logger usageLogger;

    /**
     * Public constructor.
     *
     * @param message The message to emit periodically about usage.
     */
    public WarningScopedMetrics(final String message) {
        this(
                Clock.systemUTC(),
                new RateLimitedLogger("InvalidUsage", LOGGER, Duration.ofMinutes(1)),
                message);
    }

    WarningScopedMetrics(final Clock clock, final Logger usageLogger, final String message) {
        super(clock);
        this.message = message;
        this.usageLogger = usageLogger;
    }

    @Override
    public Counter createCounter(final String name) {
        emitWarning();
        return super.createCounter(name);
    }

    @Override
    public void incrementCounter(final String name) {
        emitWarning();
        super.incrementCounter(name);
    }

    @Override
    public void incrementCounter(final String name, final long value) {
        emitWarning();
        super.incrementCounter(name, value);
    }

    @Override
    public void decrementCounter(final String name) {
        emitWarning();
        super.decrementCounter(name);
    }

    @Override
    public void decrementCounter(final String name, final long value) {
        emitWarning();
        super.decrementCounter(name, value);
    }

    @Override
    public void resetCounter(final String name) {
        emitWarning();
        super.resetCounter(name);
    }

    @Override
    public Timer createTimer(final String name) {
        emitWarning();
        return super.createTimer(name);
    }

    @Override
    public void startTimer(final String name) {
        emitWarning();
        super.startTimer(name);
    }

    @Override
    public void stopTimer(final String name) {
        emitWarning();
        super.stopTimer(name);
    }

    @Override
    public void recordCounter(final String name, final long value) {
        emitWarning();
        super.recordCounter(name, value);
    }

    @Override
    public void recordTimer(final String name, final long duration, @Nullable final TimeUnit unit) {
        emitWarning();
        super.recordTimer(name, duration, unit);
    }

    @Override
    public void recordGauge(final String name, final double value) {
        emitWarning();
        super.recordGauge(name, value);
    }

    @Override
    public void recordGauge(final String name, final long value) {
        emitWarning();
        super.recordGauge(name, value);
    }

    @Override
    public void addDimension(final String key, final String value) {
        emitWarning();
        super.addDimension(key, value);
    }

    @Override
    public void addDimensions(final Map<String, String> map) {
        emitWarning();
        super.addDimensions(map);
    }

    @Override
    public boolean isOpen() {
        emitWarning();
        return super.isOpen();
    }

    @Override
    public void close() {
        emitWarning();
        super.close();
    }

    @Override
    @Nullable
    public Instant getOpenTime() {
        emitWarning();
        return super.getOpenTime();
    }

    @Override
    @Nullable
    public Instant getCloseTime() {
        emitWarning();
        return super.getCloseTime();
    }

    @Override
    public String toString() {
        return "WarningScopedMetrics";
    }

    private void emitWarning() {
        usageLogger.warn(String.format("Invalid use of scoped metrics: %s", message));
    }
}

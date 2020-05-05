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

import com.arpnetworking.metrics.Counter;
import com.arpnetworking.metrics.Quantity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Implementation of {@link Counter}. This class is thread safe but does not
 * provide synchronized access across threads.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
/* package private */ final class TsdCounter implements Counter, Quantity {
    /**
     * Package private static factory. All {@link TsdCounter} instances should
     * be created through the {@link TsdMetrics} instance.
     *
     * @param name The name of the counter.
     * @param isOpen {@link Supplier} to the state of the underlying {@link com.arpnetworking.metrics.Metrics} instance.
     */
    /* package private */ static TsdCounter newInstance(final String name, final Supplier<Boolean> isOpen) {
        return new TsdCounter(name, isOpen, DEFAULT_LOGGER);
    }

    @Override
    public void increment() {
        increment(1L);
    }

    @Override
    public void decrement() {
        increment(-1L);
    }

    @Override
    public void increment(final long value) {
        // Note that this check is not guaranteed to catch incorrect use of the
        // Counter object, but it represents a best effort to help users
        // identify race conditions in their instrumentation.
        if (!_isOpen.get()) {
            _logger.warn(String.format("Attempt to modify counter after underlying Metrics object closed; %s", this));
            return;
        }
        _value.addAndGet(value);
    }

    @Override
    public void decrement(final long value) {
        increment(-1L * value);
    }

    @Override
    public String toString() {
        return String.format(
                "TsdCounter{id=%s, Name=%s, Value=%s}",
                Integer.toHexString(System.identityHashCode(this)),
                _name,
                _value);
    }

    @Override
    public Number getValue() {
        return _value.get();
    }

    // NOTE: Package private for testing.
    /* package private */TsdCounter(final String name, final Supplier<Boolean> isOpen, final Logger logger) {
        _name = name;
        _value = new AtomicLong(0L);
        _isOpen = isOpen;
        _logger = logger;
    }

    private final String _name;
    private final AtomicLong _value;
    private final Supplier<Boolean> _isOpen;
    private final Logger _logger;

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(TsdCounter.class);
}

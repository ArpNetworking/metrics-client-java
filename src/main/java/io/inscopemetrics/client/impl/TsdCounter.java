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
package io.inscopemetrics.client.impl;

import io.inscopemetrics.client.Counter;
import io.inscopemetrics.client.Quantity;
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
public class TsdCounter implements Counter, Quantity {

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(TsdCounter.class);

    private final String name;
    private final AtomicLong value;
    private final Supplier<Boolean> isOpen;
    private final Logger logger;

    /**
     * Protected constructor.
     *
     * @param name The time of the counter.
     * @param isOpen Used to determine if the containing instance is still open.
     * @param logger The {@link Logger} to use.
     */
    protected TsdCounter(final String name, final Supplier<Boolean> isOpen, final Logger logger) {
        this.name = name;
        value = new AtomicLong(0L);
        this.isOpen = isOpen;
        this.logger = logger;
    }

    /**
     * Public static factory. All {@link TsdCounter} instances should
     * be created through the {@link TsdMetrics} instance.
     *
     * @param name The name of the counter.
     * @param isOpen {@link Supplier} to the state of the underlying {@link io.inscopemetrics.client.Metrics} instance.
     * @return New instance of {@link TsdCounter}.
     */
    public static TsdCounter newInstance(final String name, final Supplier<Boolean> isOpen) {
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
    public void increment(final long addend) {
        // Note that this check is not guaranteed to catch incorrect use of the
        // Counter object, but it represents a best effort to help users
        // identify race conditions in their instrumentation.
        if (!isOpen.get()) {
            logger.warn(String.format("Attempt to modify counter after underlying Metrics object closed; %s", this));
            return;
        }
        this.value.addAndGet(addend);
    }

    @Override
    public void decrement(final long subtrahend) {
        increment(-1L * subtrahend);
    }

    @Override
    public String toString() {
        return String.format(
                "TsdCounter{id=%s, Name=%s, Value=%s}",
                Integer.toHexString(System.identityHashCode(this)),
                name,
                value);
    }

    @Override
    public Number getValue() {
        return value.get();
    }
}

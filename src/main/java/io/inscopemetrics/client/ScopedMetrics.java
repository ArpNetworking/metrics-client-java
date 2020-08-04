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
package io.inscopemetrics.client;

import java.time.Instant;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Interface for logging metrics: timers, counters and gauges. Clients should
 * create one instance of an implementing class for each unit of work.
 *
 * At the end of the unit of work the client should invoke {@link ScopedMetrics#close()}
 * on that instance. After the {@link ScopedMetrics#close()} method is invoked
 * the instance cannot be used to record further metrics and should be
 * discarded.
 *
 * The constructs of a counter, timer and gauge are provided only as constructs
 * to aide with instrumentation. The domain of metric names should be
 * considered shared across these constructs. Specifically, samples recorded
 * against a timer named "metric_name" and a counter named "metric_name"
 * records those measurements against the <u>same</u> metric.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public interface ScopedMetrics extends Metrics, AutoCloseable {

    /**
     * Create and initialize a counter sample. It is valid to create multiple
     * {@link Counter} instances with the same name, even concurrently,
     * each will record a unique sample for the metric of the specified name.
     *
     * @param name The name of the metric.
     * @return {@link Counter} instance for recording a counter sample.
     */
    Counter createCounter(String name);

    /**
     * Increment the specified counter sample by 1. If no counter sample exists
     * then one is initialized to zero. To create a new counter sample invoke
     * {@link ScopedMetrics#resetCounter(String)}.
     *
     * @param name The name of the metric.
     */
    void incrementCounter(String name);

    /**
     * Increment the specified counter sample by the specified amount. If no
     * counter sample exists then one is initialized to zero. To create a new
     * sample invoke {@link ScopedMetrics#resetCounter(String)}.
     *
     * @param name The name of the metric.
     * @param value The amount to increment by.
     */
    void incrementCounter(String name, long value);

    /**
     * Decrement the specified counter sample by 1. If no counter sample exists
     * then one is initialized to zero. To create a new sample invoke
     * {@link ScopedMetrics#resetCounter(String)}.
     *
     * @param name The name of the metric.
     */
    void decrementCounter(String name);

    /**
     * Decrement the specified counter sample by the specified amount. If no
     * counter sample exists then one is initialized to zero. To create a new
     * sample invoke {@link ScopedMetrics#resetCounter(String)}.
     *
     * @param name The name of the metric.
     * @param value The amount to decrement by.
     */
    void decrementCounter(String name, long value);

    /**
     * Create a new sample for the counter with a value of zero. This most
     * commonly used to either record a zero-count sample for a particular
     * counter or to record multiple samples for the same counter in one unit
     * of work.
     *
     * @param name The name of the metric.
     */
    void resetCounter(String name);

    /**
     * Create and start a timer. It is valid to create multiple {@link Timer}
     * instances with the same name, even concurrently, each will record a
     * unique sample for the metric of the specified name.
     *
     * <b>All timers are output in seconds.</b>
     *
     * @param name The name of the metric.
     * @return {@link Timer} instance for recording a timing sample.
     */
    Timer createTimer(String name);

    /**
     * Start measurement of a sample for the specified timer. This pattern
     * can only be used to take a single measurement for a specific metric.
     * Use {@link ScopedMetrics#createTimer(String)} to make multiple
     * concurrent measurements for the same metric.
     *
     * <b>All timers are output in seconds.</b>
     *
     * @param name The name of the metric.
     */
    void startTimer(String name);

    /**
     * Stop measurement of a sample for the specified timer. Use {@link ScopedMetrics#createTimer(String)}
     * to make multiple concurrent measurements.
     *
     * <b>All timers are output in seconds.</b>
     *
     * @param name The name of the metric.
     */
    void stopTimer(String name);

    /**
     * Add key-value pair that describes the captured metrics or context.
     * Statistics will be computed along all key-value pair-set combinations.
     * Adding the same dimension key overwrites any previously added value
     * for that key or any value for that key provided by {@link MetricsFactory}.
     *
     * @param key The name of the dimension.
     * @param value The value of the dimension.
     */
    void addDimension(String key, String value);

    /**
     * Add key-value pairs that describe the captured metrics or context.
     * Statistics will be computed along all key-value pair-set combinations.
     * Adding the same dimension key overwrites any previously added value
     * for that key or any value for that key provided by {@link MetricsFactory}.
     *
     * @param map The {@link Map} of dimension names to dimension values.
     */
    void addDimensions(Map<String, String> map);

    /**
     * Accessor to determine if this {@link ScopedMetrics} instance is open or
     * closed. Once closed an instance will not record new data.
     *
     * @return True if and only if this {@link ScopedMetrics} instance is open.
     */
    boolean isOpen();

    /**
     * Close the metrics object. This attempts to complete publication of
     * metrics to the underlying data store. Once the metrics object is closed,
     * no further metrics can be recorded.
     */
    @Override
    void close();

    /**
     * Returns {@link Instant} this {@link ScopedMetrics} instance was
     * opened. Commonly {@link ScopedMetrics} instances are opened on creation;
     * however, that is not required. If this instance has not been opened the
     * returned {@link Instant} will be null.
     *
     * @return The {@link Instant} this {@link ScopedMetrics} instance was
     * opened.
     */
    @Nullable
    Instant getOpenTime();

    /**
     * Returns {@link Instant} this {@link ScopedMetrics} instance was
     * closed. If this instance has not been closed the returned
     * {@link Instant} will be null.
     *
     * @return The {@link Instant} this {@link ScopedMetrics} instance was
     * closed.
     */
    @Nullable
    Instant getCloseTime();
}

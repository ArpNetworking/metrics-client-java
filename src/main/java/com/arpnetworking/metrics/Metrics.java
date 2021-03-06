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
package com.arpnetworking.metrics;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Interface for logging metrics: timers, counters and gauges. Clients should
 * create one instance of an implementing class for each unit of work. At the
 * end of the unit of work the client should invoke {@link Metrics#close()} on that
 * instance. After the {@link Metrics#close()} method is invoked the instance
 * cannot be used to record further metrics and should be discarded.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public interface Metrics extends AutoCloseable {

    /**
     * Create and initialize a counter sample. It is valid to create multiple
     * {@link Counter} instances with the same name, even concurrently,
     * each will record a unique sample for the counter of the specified name.
     *
     * @param name The name of the counter.
     * @return {@link Counter} instance for recording a counter sample.
     */
    Counter createCounter(String name);

    /**
     * Increment the specified counter by 1. All counters are initialized to
     * zero. Creates a sample if one does not exist. To create a new sample
     * invoke {@link Metrics#resetCounter(String)}.
     *
     * @param name The name of the counter.
     */
    void incrementCounter(String name);

    /**
     * Increment the specified counter by the specified amount. All counters are
     * initialized to zero. Creates a sample if one does not exist. To create a new
     * sample invoke {@link Metrics#resetCounter(String)}.
     *
     * @param name The name of the counter.
     * @param value The amount to increment by.
     */
    void incrementCounter(String name, long value);

    /**
     * Decrement the specified counter by 1. All counters are initialized to
     * zero. Creates a sample if one does not exist. To create a new sample
     * invoke {@link Metrics#resetCounter(String)}.
     *
     * @param name The name of the counter.
     */
    void decrementCounter(String name);

    /**
     * Decrement the specified counter by the specified amount. All counters are
     * initialized to zero. Creates a sample if one does not exist. To create a new
     * sample invoke {@link Metrics#resetCounter(String)}.
     *
     * @param name The name of the counter.
     * @param value The amount to decrement by.
     */
    void decrementCounter(String name, long value);

    /**
     * Create a new sample for the counter with value zero. This most commonly used
     * to either record a zero-count for a particular counter or to record multiple
     * samples of the same counter in one unit of work. If clients wish to record set
     * count metrics then all counters should be reset before conditionally invoking
     * increment and/or decrement.
     *
     * @param name The name of the counter.
     */
    void resetCounter(String name);

    /**
     * Create and start a timer. It is valid to create multiple {@link Timer}
     * instances with the same name, even concurrently, each will record a
     * unique sample for the timer of the specified name.
     *
     * @param name The name of the timer.
     * @return {@link Timer} instance for recording a timing sample.
     */
    Timer createTimer(String name);

    /**
     * Start measurement of a sample for the specified timer. Use {@link Metrics#createTimer(String)}
     * to make multiple concurrent measurements.
     *
     * @param name The name of the timer.
     */
    void startTimer(String name);

    /**
     * Stop measurement of a sample for the specified timer. Use {@link Metrics#createTimer(String)}
     * to make multiple concurrent measurements.
     *
     * @param name The name of the timer.
     */
    void stopTimer(String name);

    /**
     * Set the timer to the specified value. This is most commonly used to
     * record timers from external sources that are not directly integrated with
     * metrics. All timers are internally represented in seconds.
     *
     * @param name The name of the timer.
     * @param duration The duration of the timer.
     * @param unit The time unit of the timer.
     */
    void setTimer(String name, long duration, @Nullable TimeUnit unit);

    /**
     * Set the specified gauge reading.
     *
     * @param name The name of the gauge.
     * @param value The reading on the gauge
     */
    void setGauge(String name, double value);

    /**
     * Set the specified gauge reading.
     *
     * @param name The name of the gauge.
     * @param value The reading on the gauge
     */
    void setGauge(String name, long value);

    /**
     * Add an attribute that describes the captured metrics or context.
     *
     * @param key The name of the attribute.
     * @param value The value of the attribute.
     */
    void addAnnotation(String key, String value);

    /**
     * Add attributes that describe the captured metrics or context.
     *
     * @param map The {@link Map} of attribute names to attribute values.
     */
    void addAnnotations(Map<String, String> map);

    /**
     * Accessor to determine if this {@link Metrics} instance is open or
     * closed. Once closed an instance will not record new data.
     *
     * @return True if and only if this {@link Metrics} instance is open.
     */
    boolean isOpen();

    /**
     * Close the metrics object. This should complete publication of metrics to
     * the underlying data store. Once the metrics object is closed, no further
     * metrics can be recorded.
     */
    @Override
    void close();

    /**
     * Returns {@link Instant} this {@link Metrics} instance was
     * opened. Commonly {@link Metrics} instances are opened on creation;
     * however, that is not required. If this instance has not been opened the
     * returned {@link Instant} will be null.
     *
     * @return The {@link Instant} this {@link Metrics} instance was
     * opened.
     */
    @Nullable
    Instant getOpenTime();

    /**
     * Returns {@link Instant} this {@link Metrics} instance was
     * closed. If this instance has not been closed the returned
     * {@link Instant} will be null.
     *
     * @return The {@link Instant} this {@link Metrics} instance was
     * closed.
     */
    @Nullable
    Instant getCloseTime();
}

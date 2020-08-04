/*
 * Copyright 2020 Inscope Metrics
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

import java.util.concurrent.TimeUnit;

/**
 * Interface for logging metrics: timers, counters and gauges.
 *
 * The constructs of a counter, timer and gauge are provided only as constructs
 * to aide with instrumentation. The domain of metric names should be
 * considered shared across these constructs. Specifically, samples recorded
 * against a timer named "metric_name" and a counter named "metric_name"
 * records those measurements against the <u>same</u> metric.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public interface Metrics {

    /**
     * Record a sample for the specified counter. This is most commonly used to
     * record counters from external sources that are not directly integrated
     * with metrics.
     *
     * @param name The name of the metric.
     * @param value The value of the counter sample.
     */
    void recordCounter(String name, long value);

    /**
     * Record a sample for the specified timer. This is most commonly used to
     * record timers from external sources that are not directly integrated with
     * metrics.
     *
     * <b>All timers are output in seconds.</b>
     *
     * @param name The name of the metric.
     * @param duration The duration of the timer.
     * @param unit The time unit of the timer.
     */
    void recordTimer(String name, long duration, TimeUnit unit);

    /**
     * Record a sample for the specified metric.
     *
     * @param name The name of the metric.
     * @param value The reading on the gauge.
     */
    void recordGauge(String name, double value);

    /**
     * Record a sample for the specified metric.
     *
     * @param name The name of the metric.
     * @param value The reading on the gauge.
     */
    void recordGauge(String name, long value);
}

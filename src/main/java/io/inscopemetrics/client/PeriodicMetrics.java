/*
 * Copyright 2017 Inscope Metrics, Inc
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

import java.util.function.Consumer;

/**
 * Interface for registering periodic logging of metrics: timers, counters and
 * gauges. Typically, clients should create only one instance per polling
 * interval. Each interval the registered callbacks are invoked to record
 * samples for metrics against the provided {@link Metrics} instance.
 *
 * Scheduling the collection of {@link PeriodicMetrics} can either by done
 * by {@link MetricsFactory} or directly by clients.
 *
 * Additionally, metrics may be recorded directly against {@link PeriodicMetrics}
 * using the record methods from {@link Metrics}. However, it is important to
 * understand that the samples are emitted in the next period and there is no
 * logical grouping (scope) across calls to group measurements together.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public interface PeriodicMetrics extends Metrics, AutoCloseable {
    /**
     * Register a callback to be polled periodically for recording metric
     * samples. The callback will be executed on a periodic basis once
     * registered. The provided instance of {@link Metrics} should only
     * only be used inside the registered {@link Consumer} and should not be
     * retained beyond the execution of the callback.
     *
     * @param callback Callback to invoke periodically to record metric samples
     */
    void registerPolledMetric(Consumer<Metrics> callback);

    /**
     * Close the metrics object. This attempts to complete publication of
     * metrics to the underlying sink. Once the metrics object is closed,
     * no further metrics can be recorded.
     *
     * In the case of periodic metrics, this means that the scheduled execution
     * must first be terminated and any recording of measurements directly
     * against the instance stopped. Once closed any recorded data will be lost.
     */
    @Override
    void close();
}

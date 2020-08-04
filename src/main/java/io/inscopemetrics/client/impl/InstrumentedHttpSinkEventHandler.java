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
package io.inscopemetrics.client.impl;

import io.inscopemetrics.client.Event;
import io.inscopemetrics.client.MetricsFactory;
import io.inscopemetrics.client.ScopedMetrics;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Implementation of {@link HttpSinkEventHandler} which emits metrics
 * periodically about the performance of the {@link HttpSink}.
 *
 * TODO(ville): Convert to using PeriodicMetrics from the incubator project.
 * TODO(ville): Add queue length metric by periodically polling the sink.
 * TODO(ville): Support retries (attempt probably needs to be renamed).
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class InstrumentedHttpSinkEventHandler implements HttpSinkEventHandler {

    private static final long DELAY_IN_MILLISECONDS = 1000L;

    private final Supplier<Optional<MetricsFactory>> metricsFactorySupplier;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                // NOTE: There is no ability to shutdown the event handler so
                // we mark the work thread as a daemon thread. This will be
                // resolved once we migrate this implementation to periodic
                // metrics.
                final Thread thread = new Thread(runnable, "MetricsSinkHttpInstrumention");
                thread.setDaemon(true);
                return thread;
            });

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private volatile ScopedMetrics metrics;

    /**
     * Public constructor.
     *
     * @param metricsFactorySupplier {@code Supplier} that provides
     * {@code Optional} {@link MetricsFactory} instance. Decouples the
     * circular reference between this event handler and the metrics factory.
     */
    public InstrumentedHttpSinkEventHandler(final Supplier<Optional<MetricsFactory>> metricsFactorySupplier) {
        this.metricsFactorySupplier = metricsFactorySupplier;
        metrics = this.metricsFactorySupplier.get().map(MetricsFactory::createScopedMetrics).orElse(null);
        executorService.scheduleAtFixedRate(
                new PeriodicUnitOfWork(),
                DELAY_IN_MILLISECONDS,
                DELAY_IN_MILLISECONDS,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void attemptComplete(
            final long records,
            final long bytes,
            final boolean success,
            final long elapasedTime,
            final TimeUnit elapsedTimeUnit) {
        try {
            readWriteLock.readLock().lock();
            if (metrics != null) {
                metrics.incrementCounter("metrics_client/http_sink/records", records);
                metrics.incrementCounter("metrics_client/http_sink/bytes", bytes);
                metrics.resetCounter("metrics_client/http_sink/success_rate");
                if (success) {
                    metrics.incrementCounter("metrics_client/http_sink/success_rate");
                }
                metrics.recordTimer(
                        "metrics_client/http_sink/latency",
                        elapasedTime,
                        elapsedTimeUnit);
            }
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public void droppedEvent(final Event event) {
        try {
            readWriteLock.readLock().lock();
            if (metrics != null) {
                metrics.incrementCounter("metrics_client/http_sink/dropped");
            }
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    private final class PeriodicUnitOfWork implements Runnable {
        @Override
        public void run() {
            if (metrics != null) {
                try {
                    readWriteLock.writeLock().lock();
                    metrics.close();
                    metrics = null;
                    metrics = metricsFactorySupplier.get().map(MetricsFactory::createScopedMetrics).orElse(null);
                    if (metrics != null) {
                        metrics.resetCounter("metrics_client/http_sink/records");
                        metrics.resetCounter("metrics_client/http_sink/bytes");
                        metrics.resetCounter("metrics_client/http_sink/dropped");
                    }
                } finally {
                    readWriteLock.writeLock().unlock();
                }
            } else {
                metrics = metricsFactorySupplier.get().map(MetricsFactory::createScopedMetrics).orElse(null);
            }
        }
    }
}

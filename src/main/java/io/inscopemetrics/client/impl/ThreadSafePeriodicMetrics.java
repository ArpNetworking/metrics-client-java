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

import io.inscopemetrics.client.Metrics;
import io.inscopemetrics.client.MetricsFactory;
import io.inscopemetrics.client.PeriodicMetrics;
import io.inscopemetrics.client.ScopedMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Implementation of a {@link PeriodicMetrics} which executes registered
 * callbacks to record metric samples against {@link ThreadSafeScopedMetrics}
 * on each execution. By default callbacks are executed synchronously by the
 * thread which is executing this {@link PeriodicMetrics}. However, an
 * alternate {@link Executor} may be supplied through the builder.
 *
 * Instances should be created and scheduled through {@link MetricsFactory}.
 * However, it is possible to create instances through {@link Builder} for
 * explicit scheduled execution by the caller. This is not recommended.
 *
 * In frameworks which provide single threaded execution guarantees, such as
 * Akka and Vert.x, users should instead use {@link LockFreePeriodicMetrics}
 * obtained through {@link io.inscopemetrics.client.impl.LockFreePeriodicMetrics.Builder}.
 *
 * This class is thread safe.
 *
 * <b>IMPORTANT</b>: This class must be scheduled with an executor in order
 * for metrics to be recorded.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public class ThreadSafePeriodicMetrics implements PeriodicMetrics, Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadSafePeriodicMetrics.class);

    private final MetricsFactory metricsFactory;
    private final Executor pollingExecutor;
    private final ReadWriteLock openLock = new ReentrantReadWriteLock(false);
    private final Set<Consumer<Metrics>> callbacks = ConcurrentHashMap.newKeySet();
    private final WarningScopedMetrics warningScopedMetrics;
    private Supplier<ScopedMetrics> metricsSupplier;
    private ScopedMetrics currentMetrics;

    private ThreadSafePeriodicMetrics(final Builder builder) {
        metricsFactory = builder.metricsFactory;
        pollingExecutor = builder.pollingExecutor;
        warningScopedMetrics = builder.warningScopedMetrics;
        metricsSupplier = this::createNewScopedMetrics;
        currentMetrics = createNewScopedMetrics();
    }

    @Override
    public void registerPolledMetric(final Consumer<Metrics> consumer) {
        callbacks.add(consumer);
    }

    @Override
    public void recordCounter(final String name, final long value) {
        openLock.readLock().lock();
        try {
            currentMetrics.recordCounter(name, value);
        } finally {
            openLock.readLock().unlock();
        }
    }

    @Override
    public void recordTimer(final String name, final long duration, final TimeUnit unit) {
        openLock.readLock().lock();
        try {
            currentMetrics.recordTimer(name, duration, unit);
        } finally {
            openLock.readLock().unlock();
        }
    }

    @Override
    public void recordGauge(final String name, final double value) {
        openLock.readLock().lock();
        try {
            currentMetrics.recordGauge(name, value);
        } finally {
            openLock.readLock().unlock();
        }
    }

    @Override
    public void recordGauge(final String name, final long value) {
        openLock.readLock().lock();
        try {
            currentMetrics.recordGauge(name, value);
        } finally {
            openLock.readLock().unlock();
        }
    }

    @Override
    public void run() {
        openLock.writeLock().lock();
        try {
            final List<CompletableFuture<?>> futures = new ArrayList<>();
            for (final Consumer<Metrics> callback : callbacks) {
                futures.add(CompletableFuture.runAsync(() -> callback.accept(currentMetrics), pollingExecutor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).get();
        } catch (final ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            final ScopedMetrics previousMetric = currentMetrics;
            currentMetrics = metricsSupplier.get();
            previousMetric.close();
            openLock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        openLock.writeLock().lock();
        try {
            metricsSupplier = this::closedMetricsWithWarningSink;
            final ScopedMetrics previousMetric = currentMetrics;
            currentMetrics = metricsSupplier.get();
            previousMetric.close();
        } finally {
            openLock.writeLock().unlock();
        }
    }

    @Override
    public String toString() {
        return String.format("ThreadSafePeriodicMetrics{callback_count=%d}", callbacks.size());
    }

    private ScopedMetrics createNewScopedMetrics() {
        return metricsFactory.createScopedMetrics();
    }

    private ScopedMetrics closedMetricsWithWarningSink() {
        return warningScopedMetrics;
    }

    /**
     * Builder for {@link ThreadSafePeriodicMetrics}.
     *
     * This class does not throw exceptions if it is used improperly. An
     * example of improper use would be if the constraints on a field are
     * not satisfied. To prevent breaking the client application no
     * exception is thrown; instead a warning is logged using the SLF4J
     * {@link LoggerFactory} for this class.
     *
     * Further, the constructed {@link ThreadSafePeriodicMetrics} will operate
     * normally except that instead of publishing metrics to the sinks it
     * will log a warning each time {@link ThreadSafePeriodicMetrics#run()} is
     * invoked on the {@link ThreadSafePeriodicMetrics} instance.
     *
     * This class is <b>NOT</b> thread safe.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
     */
    public static class Builder implements com.arpnetworking.commons.builder.Builder<ThreadSafePeriodicMetrics> {

        private static final WarningScopedMetrics WARNING_SCOPED_METRICS = new WarningScopedMetrics(
                "Periodic metrics recorded against after close; data lost");
        private static final Executor DEFAULT_POLLING_EXECUTOR = DirectExecutor.getInstance();

        private Executor pollingExecutor = DEFAULT_POLLING_EXECUTOR;
        private MetricsFactory metricsFactory;
        private final Logger logger;
        private final WarningScopedMetrics warningScopedMetrics;

        /**
         * Public constructor.
         */
        public Builder() {
            this(LOGGER, WARNING_SCOPED_METRICS);
        }

        // NOTE: Package private for testing
        Builder(final Logger logger, final WarningScopedMetrics warningScopedMetrics) {
            this.logger = logger;
            this.warningScopedMetrics = warningScopedMetrics;
        }

        @Override
        public ThreadSafePeriodicMetrics build() {
            // IMPORTANT: This builder returns the concrete type ThreadSafePeriodicMetrics
            // instead of the interface type PeriodicMetrics to support users who need to
            // self-schedule since the PeriodicMetrics interface does not itself extend
            // Runnable since that is not its primary purpose.

            // Defaults
            if (metricsFactory == null) {
                metricsFactory = new TsdMetricsFactory.Builder()
                        .setSinks(Collections.singletonList(
                                new WarningSink.Builder()
                                        .setReasons(Collections.singletonList(
                                                "MetricsFactory cannot be null"))
                                        .build()
                        ))
                        .build();
                logger.warn(String.format("Defaulted null metrics factory; metricsFactory=%s", metricsFactory));
            }

            if (pollingExecutor == null) {
                pollingExecutor = DEFAULT_POLLING_EXECUTOR;
                logger.warn(String.format("Defaulted null polling executor; pollingExecutor=%s", pollingExecutor));
            }

            return new ThreadSafePeriodicMetrics(this);
        }

        /**
         * Sets the {@link MetricsFactory}. Required. Cannot be null.
         *
         * @param value The {@link MetricsFactory} instance.
         * @return This instance of {@link Builder}.
         */
        public Builder setMetricsFactory(final MetricsFactory value) {
            metricsFactory = value;
            return this;
        }

        /**
         * Sets the {@link Executor} used to execute the registered callbacks.
         * Optional. Default is {@link DirectExecutor}. Cannot be null.
         *
         * @param value The {@link Executor} instance.
         * @return This instance of {@link Builder}.
         */
        public Builder setPollingExecutor(final Executor value) {
            pollingExecutor = value;
            return this;
        }
    }
}

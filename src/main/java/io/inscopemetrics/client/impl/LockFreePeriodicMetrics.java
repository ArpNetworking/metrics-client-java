/*
 * Copyright 2020 Dropbox
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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Implementation of a {@link PeriodicMetrics} which executes registered
 * callbacks to record metric samples against {@link LockFreeScopedMetrics}
 * on each execution. Callbacks are always executed by the thread which is
 * executing this {@link PeriodicMetrics}.
 *
 * Instances should be created through {@link MetricsFactory} and scheduled
 * for periodic execution by the creator.
 *
 * This class is <b>NOT</b> thread safe; in fact, it is intended for use only
 * in contexts where the framework guarantees single threaded execution. For
 * example, Akka or Vert.x. In support of such an execution model this class
 * is implemented under the assumption of single threaded execution and as
 * a result does not use any locks.
 *
 * Most use cases will be better served with {@link ThreadSafePeriodicMetrics}.
 *
 * <b>IMPORTANT</b>: This class must be executed periodically with single
 * thread guarantees to all access in order for metrics to be recorded.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class LockFreePeriodicMetrics implements PeriodicMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(LockFreePeriodicMetrics.class);

    private final MetricsFactory metricsFactory;
    private final Set<Consumer<Metrics>> callbacks = new HashSet<>();
    private final WarningScopedMetrics warningScopedMetrics;
    private ScopedMetrics currentMetrics;
    private Supplier<ScopedMetrics> metricsSupplier;

    private LockFreePeriodicMetrics(final Builder builder) {
        metricsFactory = builder.metricsFactory;
        warningScopedMetrics = builder.warningScopedMetrics;
        metricsSupplier = this::createNewScopedMetrics;
        currentMetrics = metricsSupplier.get();
    }

    @Override
    public void registerPolledMetric(final Consumer<Metrics> consumer) {
        callbacks.add(consumer);
    }

    @Override
    public void recordCounter(final String name, final long value) {
        currentMetrics.recordCounter(name, value);
    }

    @Override
    public void recordTimer(final String name, final long duration, final TimeUnit unit) {
        currentMetrics.recordTimer(name, duration, unit);
    }

    @Override
    public void recordGauge(final String name, final double value) {
        currentMetrics.recordGauge(name, value);
    }

    @Override
    public void recordGauge(final String name, final long value) {
        currentMetrics.recordGauge(name, value);
    }

    @Override
    public void close() {
        currentMetrics.close();
        metricsSupplier = this::closedMetricsWithWarningSink;
        currentMetrics = metricsSupplier.get();
    }

    /**
     * Record metric samples from all registered callbacks to a managed
     * instance of {@link LockFreeScopedMetrics}.
     */
    public void recordPeriodicMetrics() {
        for (final Consumer<Metrics> callback : callbacks) {
            callback.accept(currentMetrics);
        }
        currentMetrics.close();
        currentMetrics = metricsSupplier.get();
    }

    @Override
    public String toString() {
        return String.format("LockFreePeriodicMetrics{callback_count=%d}", callbacks.size());
    }

    private ScopedMetrics createNewScopedMetrics() {
        return metricsFactory.createLockFreeScopedMetrics();
    }

    private ScopedMetrics closedMetricsWithWarningSink() {
        return warningScopedMetrics;
    }

    /**
     * Builder for {@link LockFreePeriodicMetrics}.
     *
     * This class does not throw exceptions if it is used improperly. An
     * example of improper use would be if the constraints on a field are
     * not satisfied. To prevent breaking the client application no
     * exception is thrown; instead a warning is logged using the SLF4J
     * {@link LoggerFactory} for this class.
     *
     * Further, the constructed {@link LockFreePeriodicMetrics} will operate
     * normally except that instead of publishing metrics to the sinks it
     * will log a warning each time {@link LockFreePeriodicMetrics#recordPeriodicMetrics()}
     * is invoked on the {@link LockFreePeriodicMetrics} instance.
     *
     * This class is <b>NOT</b> thread safe.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
     */
    public static class Builder implements com.arpnetworking.commons.builder.Builder<LockFreePeriodicMetrics> {

        private static final WarningScopedMetrics WARNING_SCOPED_METRICS = new WarningScopedMetrics(
                "Periodic metrics recorded against after close; data lost");

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
        public LockFreePeriodicMetrics build() {
            // IMPORTANT: This builder returns the concrete type LockFreePeriodicMetrics
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

            return new LockFreePeriodicMetrics(this);
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
    }
}

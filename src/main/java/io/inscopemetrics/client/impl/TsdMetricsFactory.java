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

import com.arpnetworking.commons.uuidfactory.SplittableRandomUuidFactory;
import com.arpnetworking.commons.uuidfactory.UuidFactory;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.inscopemetrics.client.Metrics;
import io.inscopemetrics.client.MetricsFactory;
import io.inscopemetrics.client.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Default implementation of {@link MetricsFactory} for creating
 * {@link Metrics} instances for publication of time series data (TSD).
 *
 * For more information about the semantics of this class and its methods
 * please refer to the {@link MetricsFactory} interface documentation.
 *
 * The simplest way to create an instance of this class is to use the
 * {@link TsdMetricsFactory#newInstance(Map, Map)} static factory method.
 * This method will use default settings where possible.
 *
 * {@code
 * final MetricsFactory metricsFactory = TsdMetricsFactory.newInstance(
 *     Collections.singletonMap("service", "my-service-name"),
 *     Collections.singletonMap(
 *         "host",
 *         BackgroundCachingHostResolver.getInstance());
 * }
 *
 * To customize the factory instance use the nested {@link Builder} class:
 *
 * {@code
 * final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
 *     .setDefaultDimensions(Collections.singletonMap("service", "my-service-name"))
 *     .setDefaultComputedDimensions(
 *         Collections.singletonMap(
 *             "host",
 *             BackgroundCachingHostResolver.getInstance())
 *     .setSinks(Collections.singletonList(
 *         new HttpSink.Builder().build()));
 *     .build();
 * }
 *
 * The above will write metrics to http://localhost:7090/metrics/v3/application.
 * This is the default port and path of the Metrics Aggregator Daemon (MAD). It
 * is sometimes desirable to customize this path; for example, when running MAD
 * under Docker:
 *
 * {@code
 * final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
 *     .setDefaultDimensions(Collections.singletonMap("service", "my-service-name"))
 *     .setDefaultComputedDimensions(
 *         Collections.singletonMap(
 *             "host",
 *             BackgroundCachingHostResolver.getInstance())
 *     .setSinks(Collections.singletonList(
 *         new HttpSink.Builder()
 *             .setUri(URI.create("http://192.168.0.1:1234/metrics/v3/application"))
 *             .build()));
 *     .build();
 * }
 *
 * Alternatively, metrics may be written to a file:
 *
 * {@code
 * final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
 *     .setDefaultDimensions(Collections.singletonMap("service", "my-service-name"))
 *     .setDefaultComputedDimensions(
 *         Collections.singletonMap(
 *             "host",
 *             BackgroundCachingHostResolver.getInstance())
 *     .setSinks(Collections.singletonList(
 *         new FileLogSink.Builder().build()));
 *     .build();
 * }
 *
 * The above will write metrics to query.log in the current directory. It is
 * advised that at least the directory be set when using the FileLogSink:
 *
 * {@code
 * final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
 *     .setDefaultDimensions(Collections.singletonMap("service", "my-service-name"))
 *     .setDefaultComputedDimensions(
 *         Collections.singletonMap(
 *             "host",
 *             BackgroundCachingHostResolver.getInstance())
 *     .setSinks(Collections.singletonList(
 *         new FileLogSink.Builder()
 *             .setDirectory("/usr/local/var/my-app/logs")
 *             .build()));
 *     .build();
 * }
 *
 * The above will write metrics to /usr/local/var/my-app/logs in query.log.
 * Additionally, you can customize the base file name and extension for your
 * application. However, if you are using MAD remember to configure it to
 * match:
 *
 * {@code
 * final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
 *     .setDefaultDimensions(Collections.singletonMap("service", "my-service-name"))
 *     .setDefaultComputedDimensions(
 *         Collections.singletonMap(
 *             "host",
 *             BackgroundCachingHostResolver.getInstance())
 *     .setSinks(Collections.singletonList(
 *         new StenoLogSink.Builder()
 *             .setDirectory("/usr/local/var/my-app/logs")
 *             .setName("tsd")
 *             .setExtension(".txt")
 *             .build()));
 *     .build();
 * }
 *
 * The above will write metrics to /usr/local/var/my-app/logs in tsd.txt. The
 * extension is configured separately as the files are rolled over every hour
 * inserting a date-time between the name and extension like:
 *
 * query-log.YYYY-MM-DD-HH.log
 *
 * All the examples apply equally to {@link MetricsFactory#create()} and
 * {@link MetricsFactory#createLockFree()}.
 *
 * This class is thread safe.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class TsdMetricsFactory implements MetricsFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TsdMetricsFactory.class);

    private final List<Sink> sinks;
    private final Supplier<UUID> uuidFactory;
    private final Map<String, String> defaultDimensions;
    private final Map<String, Supplier<String>> defaultComputedDimensions;
    private final Logger logger;

    /**
     * Protected constructor.
     *
     * @param builder Instance of {@link Builder}.
     */
    protected TsdMetricsFactory(final Builder builder) {
        this(builder, LOGGER);
    }

    TsdMetricsFactory(final Builder builder, final Logger logger) {
        sinks = Collections.unmodifiableList(new ArrayList<>(builder.sinks));
        uuidFactory = builder.uuidFactory;
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        defaultDimensions = Collections.unmodifiableMap(new HashMap<>(builder.defaultDimensions));
        defaultComputedDimensions = Collections.unmodifiableMap(new HashMap<>(builder.defaultComputedDimensions));
        // CHECKSTYLE.ON: IllegalInstantiation
        this.logger = logger;
    }

    /**
     * Static factory. Construct an instance of {@link TsdMetricsFactory}
     * using the first available default {@link Sink} with no default
     * static or computed dimensions.
     *
     * @return Instance of {@link TsdMetricsFactory}.
     */
    public static MetricsFactory newInstance() {
        return newInstance(Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * Static factory. Construct an instance of {@link TsdMetricsFactory}
     * using the first available default {@link Sink}.
     *
     * The value of a computed default dimension will override the value of a
     * fixed default dimension unless the computed dimension value {@link Supplier}
     * returns {@code null} in which case it is ignored.
     *
     * @param defaultDimensions The dimensions to add to every {@link Metrics} instance.
     * @param defaultComputedDimensions The dimensions to add to every {@link Metrics}
     * instance with the value evaluated at creation time from a {@link Supplier}.
     * @return Instance of {@link TsdMetricsFactory}.
     */
    public static MetricsFactory newInstance(
            final Map<String, String> defaultDimensions,
            final Map<String, Supplier<String>> defaultComputedDimensions
    ) {
        return new Builder()
                .setDefaultDimensions(defaultDimensions)
                .setDefaultComputedDimensions(defaultComputedDimensions)
                .build();
    }

    @Override
    public Metrics create() {
        final UUID uuid = uuidFactory.get();
        Metrics metrics;
        try {
            metrics = new TsdMetrics(
                    uuid,
                    sinks);
            configureMetrics(metrics);

            // CHECKSTYLE.OFF: IllegalCatch - Suppliers do not throw checked exceptions
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatch
            logger.warn("Unable to construct TsdMetrics, metrics disabled", e);
            metrics = new TsdMetrics(
                    uuid,
                    Collections.singletonList(
                            new WarningSink.Builder()
                                    .setReasons(Collections.singletonList(e.getMessage()))
                                    .build()));
        }
        return metrics;
    }

    @Override
    public Metrics createLockFree() {
        final UUID uuid = uuidFactory.get();
        Metrics metrics;
        try {
            metrics = new LockFreeMetrics(
                    uuid,
                    sinks);
            configureMetrics(metrics);

            // CHECKSTYLE.OFF: IllegalCatch - Suppliers do not throw checked exceptions
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatch
            logger.warn("Unable to construct LockFreeMetrics, metrics disabled", e);
            metrics = new LockFreeMetrics(
                    uuid,
                    Collections.singletonList(
                            new WarningSink.Builder()
                                    .setReasons(Collections.singletonList(e.getMessage()))
                                    .build()));
        }
        return metrics;
    }

    @Override
    public String toString() {
        return String.format(
                "TsdMetricsFactory{Sinks=%s, DefaultDimensions=%s, DefaultComputedDimensions=%s}",
                sinks,
                defaultDimensions,
                defaultComputedDimensions);
    }

    void configureMetrics(final Metrics metrics) {
        metrics.addDimensions(defaultDimensions);
        for (final Map.Entry<String, Supplier<String>> entry : defaultComputedDimensions.entrySet()) {
            final String value = entry.getValue().get();
            if (value != null) {
                metrics.addDimension(entry.getKey(), value);
            }
        }
    }

    List<Sink> getSinks() {
        return Collections.unmodifiableList(sinks);
    }

    Map<String, String> getDefaultDimensions() {
        return Collections.unmodifiableMap(defaultDimensions);
    }

    Map<String, Supplier<String>> getDefaultComputedDimensions() {
        return Collections.unmodifiableMap(defaultComputedDimensions);
    }

    Supplier<UUID> getUuidFactory() {
        return uuidFactory;
    }

    static @Nullable List<Sink> createDefaultSinks(final List<String> defaultSinkClassNames) {
        for (final String sinkClassName : defaultSinkClassNames) {
            final Optional<Class<? extends Sink>> sinkClass = getSinkClass(sinkClassName);
            if (sinkClass.isPresent()) {
                final Optional<Sink> sink = createSink(sinkClass.get());
                if (sink.isPresent()) {
                    return Collections.singletonList(sink.get());
                }
            }
        }

        return Collections.unmodifiableList(
                Collections.singletonList(
                        new WarningSink.Builder()
                                .setReasons(Collections.singletonList("No default sink found."))
                                .build()));
    }

    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    static Optional<Sink> createSink(final Class<? extends Sink> sinkClass) {
        try {
            final Class<?> sinkBuilderClass = Class.forName(sinkClass.getName() + "$Builder");
            final Object sinkBuilder = sinkBuilderClass.newInstance();
            final Method buildMethod = sinkBuilderClass.getMethod("build");
            return Optional.of((Sink) buildMethod.invoke(sinkBuilder));
            // CHECKSTYLE.OFF: IllegalCatch - Much cleaner than catching the half-dozen checked exceptions
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.warn(
                    String.format(
                            "Unable to load sink; sinkClass=%s",
                            sinkClass),
                    e);
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    static Optional<Class<? extends Sink>> getSinkClass(final String name) {
        try {
            return Optional.of((Class<? extends Sink>) Class.forName(name));
        } catch (final ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Builder for {@link TsdMetricsFactory}.
     *
     * This class does not throw exceptions if it is used improperly. An
     * example of improper use would be if the constraints on a field are
     * not satisfied. To prevent breaking the client application no
     * exception is thrown; instead a warning is logged using the SLF4J
     * {@link LoggerFactory} for this class.
     *
     * Further, the constructed {@link TsdMetricsFactory} will operate
     * normally except that instead of publishing metrics to the sinks it
     * will log a warning each time {@link Metrics#close()} is invoked on the
     * {@link Metrics} instance.
     *
     * This class is thread safe.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    public static class Builder implements com.arpnetworking.commons.builder.Builder<MetricsFactory> {

        private static final List<Sink> EMPTY_SINKS = Collections.emptyList();
        private static final Map<String, String> EMPTY_DEFAULT_DIMENSIONS = Collections.emptyMap();
        private static final Map<String, Supplier<String>> EMPTY_DEFAULT_COMPUTED_DIMENSIONS = Collections.emptyMap();

        private static final List<Sink> DEFAULT_SINKS = Collections.singletonList(new HttpSink.Builder().build());
        private static final Map<String, String> DEFAULT_DEFAULT_DIMENSIONS = EMPTY_DEFAULT_DIMENSIONS;
        private static final Map<String, Supplier<String>> DEFAULT_DEFAULT_COMPUTED_DIMENSIONS = EMPTY_DEFAULT_COMPUTED_DIMENSIONS;
        private static final Supplier<UUID> DEFAULT_UUID_FACTORY = new SplittableRandomUuidFactory();

        private final Logger logger;

        private List<Sink> sinks = DEFAULT_SINKS;
        private Supplier<UUID> uuidFactory = DEFAULT_UUID_FACTORY;
        private Map<String, String> defaultDimensions = DEFAULT_DEFAULT_DIMENSIONS;
        private Map<String, Supplier<String>> defaultComputedDimensions = DEFAULT_DEFAULT_COMPUTED_DIMENSIONS;

        /**
         * Public constructor.
         */
        public Builder() {
            this(LOGGER);
        }

        // NOTE: Package private for testing
        Builder(@Nullable final Logger logger) {
            this.logger = logger;
        }

        /**
         * Create an instance of {@link MetricsFactory}.
         *
         * @return Instance of {@link MetricsFactory}.
         */
        @Override
        public MetricsFactory build() {
            // Defaults
            if (sinks == null) {
                sinks = EMPTY_SINKS;
                logger.info(String.format(
                        "Defaulted null sinks; sinks=%s",
                        sinks));
            }
            if (defaultDimensions == null) {
                defaultDimensions = EMPTY_DEFAULT_DIMENSIONS;
                logger.info(String.format(
                        "Defaulted null default dimensions; defaultDimensions=%s",
                        defaultDimensions));
            }
            if (defaultComputedDimensions == null) {
                defaultComputedDimensions = EMPTY_DEFAULT_COMPUTED_DIMENSIONS;
                logger.info(String.format(
                        "Defaulted null default computed dimensions; defaultComputedDimensions=%s",
                        defaultComputedDimensions));
            }
            if (uuidFactory == null) {
                uuidFactory = DEFAULT_UUID_FACTORY;
                logger.info(String.format(
                        "Defaulted null uuid factory; uuidFactory=%s",
                        uuidFactory));
            }

            return new TsdMetricsFactory(this);
        }

        /**
         * Set the sinks to publish to. Cannot be null. Optional. Defaults to
         * the first available {@link Sink} on the classpath from an ordered
         * list of predefine {@link Sink} implementations.
         *
         * @param value The sinks to publish to.
         * @return This {@link Builder} instance.
         */
        public Builder setSinks(@Nullable final List<Sink> value) {
            sinks = value;
            return this;
        }

        /**
         * Set the dimensions to add to each {@link Metrics} instance. Cannot
         * be null. Optional. Defaults to an empty map (no default dimensions).
         *
         * @param value The default dimensions.
         * @return This {@link Builder} instance.
         */
        public Builder setDefaultDimensions(@Nullable final Map<String, String> value) {
            defaultDimensions = value;
            return this;
        }

        /**
         * Set the computed dimensions to add to each {@link Metrics} instance where
         * the value of the dimension is evaluated at {@link Metrics} creation time.
         * Cannot be null. Optional. Defaults to an empty map (no default computed
         * dimensions).
         *
         * @param value The default computed dimensions.
         * @return This {@link Builder} instance.
         */
        public Builder setDefaultComputedDimensions(@Nullable final Map<String, Supplier<String>> value) {
            defaultComputedDimensions = value;
            return this;
        }

        /**
         * Set the UuidFactory to be used to create UUIDs assigned to instances
         * of {@link Metrics} created by this {@link MetricsFactory}.
         * Cannot be null. Optional. Defaults to using the Java native
         * {@link java.util.UUID#randomUUID()}.
         *
         * @param uuidFactory The {@link UuidFactory} instance.
         * @return This {@link Builder} instance.
         */
        public Builder setUuidFactory(@Nullable final UuidFactory uuidFactory) {
            this.uuidFactory = uuidFactory;
            return this;
        }
    }
}

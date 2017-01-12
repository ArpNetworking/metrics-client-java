/**
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

import com.arpnetworking.commons.hostresolver.BackgroundCachingHostResolver;
import com.arpnetworking.commons.hostresolver.HostResolver;
import com.arpnetworking.commons.uuidfactory.SplittableRandomUuidFactory;
import com.arpnetworking.commons.uuidfactory.UuidFactory;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.Sink;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Default implementation of <code>MetricsFactory</code> for creating
 * <code>Metrics</code> instances for publication of time series data (TSD).
 *
 * For more information about the semantics of this class and its methods
 * please refer to the <code>MetricsFactory</code> interface documentation.
 *
 * The simplest way to create an instance of this class is to use the
 * <code>newInstance</code> static factory method. This method will use
 * default settings where possible.
 *
 * {@code
 * final MetricsFactory metricsFactory = TsdMetricsFactory.newInstance(
 *     "MyService",
 *     "MyService-US-Prod");
 * }
 *
 * To customize the factory instance use the nested <code>Builder</code> class:
 *
 * {@code
 * final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
 *     .setServiceName("MyService")
 *     .setClusterName("MyService-US-Prod")
 *     .setSinks(Collections.singletonList(
 *         new ApacheHttpSink.Builder().build()));
 *     .build();
 * }
 *
 * The above will write metrics to http://localhost:7090/metrics/v1/application.
 * This is the default port and path of the Metrics Aggregator Daemon (MAD). It
 * is sometimes desirable to customize this path; for example, when running MAD
 * under Docker:
 *
 * {@code
 * final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
 *     .setServiceName("MyService")
 *     .setClusterName("MyService-US-Prod")
 *     .setSinks(Collections.singletonList(
 *         new ApacheHttpSink.Builder()
 *             .setUri(URI.create("http://192.168.0.1:1234/metrics/v1/application"))
 *             .build()));
 *     .build();
 * }
 *
 * Alternatively, metrics may be written to a file:
 *
 * {@code
 * final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
 *     .setServiceName("MyService")
 *     .setClusterName("MyService-US-Prod")
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
 *     .setServiceName("MyService")
 *     .setClusterName("MyService-US-Prod")
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
 *     .setServiceName("MyService")
 *     .setClusterName("MyService-US-Prod")
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
 * This class is thread safe.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class TsdMetricsFactory implements MetricsFactory {

    /**
     * Static factory. Construct an instance of <code>TsdMetricsFactory</code>
     * using the first available default <code>Sink</code>.
     *
     * @param serviceName The name of the service/application publishing metrics.
     * @param clusterName The name of the cluster (e.g. instance) publishing metrics.
     * @return Instance of <code>TsdMetricsFactory</code>.
     */
    public static MetricsFactory newInstance(
            final String serviceName,
            final String clusterName) {
        return new Builder()
                .setClusterName(clusterName)
                .setServiceName(serviceName)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Metrics create() {
        final UUID uuid = _uuidFactory.get();
        try {
            return new TsdMetrics(
                    uuid,
                    _serviceName,
                    _clusterName,
                    _hostResolver.get(),
                    _sinks);
            // CHECKSTYLE.OFF: IllegalCatch - Suppliers do not throw checked exceptions
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatch
            final List<String> failures = Collections.singletonList("Unable to determine hostname");
            _logger.warn(
                    String.format(
                            "Unable to construct TsdMetrics, metrics disabled; failures=%s",
                            failures),
                    e);
            return new TsdMetrics(
                    uuid,
                    _serviceName,
                    _clusterName,
                    DEFAULT_HOST_NAME,
                    Collections.singletonList(
                            new WarningSink.Builder()
                                    .setReasons(failures)
                                    .build()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format(
                "TsdMetricsFactory{Sinks=%s, ServiceName=%s, ClusterName=%s, HostResolver=%s}",
                _sinks,
                _serviceName,
                _clusterName,
                _hostResolver);
    }

    /* package private */ List<Sink> getSinks() {
        return Collections.unmodifiableList(_sinks);
    }

    /* package private */ String getServiceName() {
        return _serviceName;
    }

    /* package private */ Supplier<String> getHostResolver() {
        return _hostResolver;
    }

    /* package private */ Supplier<UUID> getUuidFactory() {
        return _uuidFactory;
    }

    /* package private */ String getClusterName() {
        return _clusterName;
    }

    /* package private */ static @Nullable List<Sink> createDefaultSinks(final List<String> defaultSinkClassNames) {
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

    @SuppressWarnings("unchecked")
    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    /* package private */ static Optional<Sink> createSink(final Class<? extends Sink> sinkClass) {
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
    /* package private */ static Optional<Class<? extends Sink>> getSinkClass(final String name) {
        try {
            return Optional.of((Class<? extends Sink>) Class.forName(name));
        } catch (final ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Protected constructor.
     *
     * @param builder Instance of <code>Builder</code>.
     */
    protected TsdMetricsFactory(final Builder builder) {
        this(builder, LOGGER);
    }

    /**
     * Protected constructor.
     *
     * @param builder Instance of <code>Builder</code>.
     */
    /* package private */ TsdMetricsFactory(final Builder builder, final Logger logger) {
        _sinks = Collections.unmodifiableList(new ArrayList<>(builder._sinks));
        _uuidFactory = builder._uuidFactory;
        _serviceName = builder._serviceName;
        _clusterName = builder._clusterName;
        _hostResolver = builder._hostResolver;
        _logger = logger;
    }

    private final List<Sink> _sinks;
    private final Supplier<UUID> _uuidFactory;
    private final String _serviceName;
    private final String _clusterName;
    private final Supplier<String> _hostResolver;
    private final Logger _logger;

    private static final String DEFAULT_SERVICE_NAME = "<SERVICE_NAME>";
    private static final String DEFAULT_CLUSTER_NAME = "<CLUSTER_NAME>";
    private static final String DEFAULT_HOST_NAME = "<HOST_NAME>";
    private static final List<String> DEFAULT_SINK_CLASS_NAMES;
    private static final Logger LOGGER = LoggerFactory.getLogger(TsdMetricsFactory.class);

    static {
        final List<String> sinkClassNames = new ArrayList<>();
        sinkClassNames.add("com.arpnetworking.metrics.impl.ApacheHttpSink");
        sinkClassNames.add("com.arpnetworking.metrics.impl.FileSink");
        DEFAULT_SINK_CLASS_NAMES = Collections.unmodifiableList(sinkClassNames);
    }

    /**
     * Builder for <code>TsdMetricsFactory</code>.
     *
     * This class does not throw exceptions if it is used improperly. An
     * example of improper use would be if the constraints on a field are
     * not satisfied. To prevent breaking the client application no
     * exception is thrown; instead a warning is logged using the SLF4J
     * <code>LoggerFactory</code> for this class.
     *
     * Further, the constructed <code>TsdMetricsFactory</code> will operate
     * normally except that instead of publishing metrics to the sinks it
     * will log a warning each time <code>close()</code> is invoked on the
     * <code>Metrics</code> instance.
     *
     * This class is thread safe.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
     */
    public static class Builder implements com.arpnetworking.commons.builder.Builder<MetricsFactory> {

        /**
         * Public constructor.
         */
        public Builder() {
            this(DEFAULT_HOST_RESOLVER, LOGGER);
        }

        /**
         * Public constructor.
         *
         * @param hostResolver The <code>HostResolver</code> instance to use
         * to determine the default host name.
         */
        public Builder(final HostResolver hostResolver) {
            this(hostResolver, LOGGER);
        }

        // NOTE: Package private for testing
        /* package private */ Builder(@Nullable final Supplier<String> hostResolver, @Nullable final Logger logger) {
            _hostResolver = hostResolver;
            _logger = logger;
        }

        /**
         * Create an instance of <code>MetricsFactory</code>.
         *
         * @return Instance of <code>MetricsFactory</code>.
         */
        @Override
        public MetricsFactory build() {
            final List<String> failures = new ArrayList<>();

            // Defaults
            if (_sinks == null) {
                _sinks = DEFAULT_SINKS;
                _logger.info(String.format("Defaulted null sinks; sinks=%s", _sinks));
            }
            if (_hostResolver == null) {
                _hostResolver = DEFAULT_HOST_RESOLVER;
                _logger.info(String.format("Defaulted null host resolver; resolver=%s", _hostResolver));
            }

            // Validate
            if (_serviceName == null) {
                _serviceName = DEFAULT_SERVICE_NAME;
                failures.add("ServiceName cannot be null");
            }
            if (_clusterName == null) {
                _clusterName = DEFAULT_CLUSTER_NAME;
                failures.add("ClusterName cannot be null");
            }

            // Apply fallback
            if (!failures.isEmpty()) {
                _logger.warn(String.format(
                        "Unable to construct TsdMetricsFactory, metrics disabled; failures=%s",
                        failures));
                _sinks = Collections.singletonList(
                        new WarningSink.Builder()
                                .setReasons(failures)
                                .build());
            }

            return new TsdMetricsFactory(this);
        }

        /**
         * Set the sinks to publish to. Cannot be null.
         *
         * @param value The sinks to publish to.
         * @return This <code>Builder</code> instance.
         */
        public Builder setSinks(@Nullable final List<Sink> value) {
            _sinks = value;
            return this;
        }

        /**
         * Set the UuidFactory to be used to create UUIDs assigned to instances
         * of <code>Metrics</code> created by this <code>MetricsFactory</code>.
         * Cannot be null. Optional. Defaults to using the Java native 
         * <code>java.util.UUID.randomUUID()</code>.
         *
         * @param uuidFactory The <code>UuidFactory</code> instance.
         * @return This <code>Builder</code> instance.
         */
        public Builder setUuidFactory(@Nullable final UuidFactory uuidFactory) {
            _uuidFactory = uuidFactory;
            return this;
        }

        /**
         * Set the service name to publish as. Cannot be null.
         *
         * @param value The service name to publish as.
         * @return This <code>Builder</code> instance.
         */
        public Builder setServiceName(@Nullable final String value) {
            _serviceName = value;
            return this;
        }

        /**
         * Set the cluster name to publish as. Cannot be null.
         *
         * @param value The cluster name to publish as.
         * @return This <code>Builder</code> instance.
         */
        public Builder setClusterName(@Nullable final String value) {
            _clusterName = value;
            return this;
        }

        /**
         * Set the host name to publish as. Cannot be null. Optional. Default
         * is the host name provided by the provided <code>HostProvider</code>
         * or its default instance if one was not specified. If the
         * <code>HostProvider</code> fails to provide a host name the builder
         * will produce a fake instance of <code>Metrics</code> on create. This
         * is to ensure the library remains exception neutral.
         *
         * @param value The host name to publish as.
         * @return This <code>Builder</code> instance.
         */
        public Builder setHostName(@Nullable final String value) {
            _hostResolver = () -> value;
            return this;
        }

        private final Logger _logger;

        private List<Sink> _sinks = DEFAULT_SINKS;
        private Supplier<UUID> _uuidFactory = DEFAULT_UUID_FACTORY;
        private String _serviceName;
        private String _clusterName;
        private Supplier<String> _hostResolver;

        private static final List<Sink> DEFAULT_SINKS = createDefaultSinks(DEFAULT_SINK_CLASS_NAMES);
        private static final Supplier<String> DEFAULT_HOST_RESOLVER = new BackgroundCachingHostResolver(Duration.ofMinutes(1));
        private static final Supplier<UUID> DEFAULT_UUID_FACTORY = new SplittableRandomUuidFactory();
    }
}

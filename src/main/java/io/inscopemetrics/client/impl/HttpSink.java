/*
 * Copyright 2016 Inscope Metrics, Inc
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

import com.arpnetworking.commons.java.util.function.SingletonSupplier;
import com.arpnetworking.commons.slf4j.RateLimitedLogger;
import com.google.protobuf.ByteString;
import io.inscopemetrics.client.AggregatedData;
import io.inscopemetrics.client.Event;
import io.inscopemetrics.client.Quantity;
import io.inscopemetrics.client.Sink;
import io.inscopemetrics.client.StopWatch;
import io.inscopemetrics.client.protocol.ClientV3;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Http sink using the Protobuf format for metrics and the Apache HTTP library.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class HttpSink implements Sink {

    private static final Header CONTENT_TYPE_HEADER = new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
    private static final int UUID_LENGTH_BYTES = 16;
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpSink.class);

    private final URI uri;
    private final int bufferSize;
    private final int maxBatchSize;
    private final Duration emptyQueueInterval;
    private final Optional<HttpSinkEventHandler> eventHandler;
    private final RateLimitedLogger eventsDroppedLogger;
    private final Deque<Event> events = new ConcurrentLinkedDeque<>();
    private final AtomicInteger eventsCount = new AtomicInteger(0);
    private final AtomicBoolean isRunning = new AtomicBoolean(true);

    /**
     * Protected constructor.
     *
     * @param builder The {@link Builder} to build from.
     */
    protected HttpSink(final Builder builder) {
        this(builder, LOGGER);
    }

    HttpSink(final Builder builder, final Logger logger) {
        this(
                builder,
                new SingletonSupplier<>(() -> {
                    final SingletonSupplier<PoolingHttpClientConnectionManager> clientManagerSupplier = new SingletonSupplier<>(() -> {
                        final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
                        connectionManager.setDefaultMaxPerRoute(builder.parallelism);
                        connectionManager.setMaxTotal(builder.parallelism);
                        return connectionManager;
                    });

                    return HttpClients.custom()
                            .setConnectionManager(clientManagerSupplier.get())
                            .build();
                }),
                logger);
    }

    HttpSink(
            final Builder builder,
            final Supplier<CloseableHttpClient> httpClientSupplier,
            final Logger logger) {
        uri = builder.uri;
        bufferSize = builder.bufferSize;
        maxBatchSize = builder.maxBatchSize;
        emptyQueueInterval = builder.emptyQueueInterval;
        eventHandler = Optional.ofNullable(builder.eventHandler);
        eventsDroppedLogger = new RateLimitedLogger(
                "EventsDroppedLogger",
                logger,
                builder.eventsDroppedLoggingInterval);

        final ExecutorService executor = Executors.newFixedThreadPool(
                builder.parallelism,
                runnable -> new Thread(runnable, "MetricsSinkApacheHttpWorker"));

        Runtime.getRuntime().addShutdownHook(new ShutdownHookThread(this.isRunning, executor));

        // Use a single shared worker instance across the pool; see getSharedHttpClient
        final HttpDispatch httpDispatchWorker = new HttpDispatch(
                httpClientSupplier,
                builder.eventsDroppedLoggingInterval,
                builder.unsupportedDataLoggingInterval,
                logger,
                eventHandler);
        for (int i = 0; i < builder.parallelism; ++i) {
            executor.execute(httpDispatchWorker);
        }
    }

    @Override
    public void record(final Event event) {
        events.push(event);
        final int eventCount = eventsCount.incrementAndGet();
        if (eventCount > bufferSize) {
            eventsDroppedLogger.getLogger().warn(
                    "Event queue is full; dropping event(s)");
            final Event droppedEvent = this.events.pollFirst();
            eventHandler.ifPresent(eh -> eh.droppedEvent(droppedEvent));
            eventsCount.decrementAndGet();
        }
    }

    void stop() {
        isRunning.set(false);
    }

    static void safeSleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            // Do nothing
        }
    }

    private class HttpDispatch implements Runnable {

        private static final int MANTISSA_BITS = 52;
        private static final int EXPONENT_BITS = 11;
        private static final long BASE_MASK = (1L << (MANTISSA_BITS + EXPONENT_BITS)) >> EXPONENT_BITS;

        private final Supplier<CloseableHttpClient> httpClientSupplier;
        private final RateLimitedLogger dispatchErrorLogger;
        private final RateLimitedLogger unsupportedDataLogger;
        private final Optional<HttpSinkEventHandler> eventHandler;

        HttpDispatch(
                final Supplier<CloseableHttpClient> httpClientSupplier,
                final Duration dispatchErrorLoggingInterval,
                final Duration unsupportedDataLoggingInterval,
                final Logger logger,
                final Optional<HttpSinkEventHandler> eventHandler) {
            this.httpClientSupplier = httpClientSupplier;
            dispatchErrorLogger = new RateLimitedLogger(
                    "DispatchErrorLogger",
                    logger,
                    dispatchErrorLoggingInterval);
            unsupportedDataLogger = new RateLimitedLogger(
                    "UnsupportedDataLogger",
                    logger,
                    unsupportedDataLoggingInterval);
            this.eventHandler = eventHandler;
        }

        @Override
        public void run() {
            while (isRunning.get()) {
                try {
                    // Collect a set of events to send
                    // NOTE: This also builds the serialization types in order to spend more time
                    // allowing more records to arrive in the batch
                    int collected = 0;
                    final ClientV3.Request.Builder requestBuilder = ClientV3.Request.newBuilder();
                    do {
                        @Nullable final Event event = events.pollFirst();
                        if (event == null) {
                            break;
                        }

                        eventsCount.decrementAndGet();
                        collected++;

                        requestBuilder.addRecords(serializeEvent(event));
                    } while (collected < maxBatchSize);

                    if (collected > 0) {
                        dispatchRequest(httpClientSupplier.get(), requestBuilder.build());
                    }

                    // Sleep if the queue is empty
                    if (collected == 0) {
                        safeSleep(emptyQueueInterval.toMillis());
                    }
                    // CHECKSTYLE.OFF: IllegalCatch - Ensure exception neutrality
                } catch (final RuntimeException e) {
                    // CHECKSTYLE.ON: IllegalCatch
                    dispatchErrorLogger.getLogger().error("MetricsSinkApacheHttpWorker failure", e);
                }
            }
        }

        private void dispatchRequest(final CloseableHttpClient httpClient, final ClientV3.Request request) {
            // TODO(ville): We need to add retries.
            // Requests that fail should either go into a different retry queue
            // or else be requeued at the front of the existing queue (unless
            // it's full). The data will need to be wrapped with an attempt
            // count.
            final ByteArrayEntity entity = new ByteArrayEntity(request.toByteArray());
            final HttpPost post = new HttpPost(uri);
            post.setHeader(CONTENT_TYPE_HEADER);
            post.setEntity(entity);

            final StopWatch stopWatch = StopWatch.start();
            boolean success = false;

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                final int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == HttpStatus.SC_NOT_FOUND) {
                    // While the server may be running and accepting HTTP
                    // requests, the specific endpoint may not have been loaded
                    // yet. Therefore, this case should not be treated any
                    // differently than the server not being available. However,
                    // it could also mean that the server does not support the
                    // particular endpoint in use (e.g. version mismatch).
                    throw new RuntimeException("Endpoint not available");
                }
                // CHECKSTYLE.OFF: MagicNumber - Http codes are three digits
                if ((statusCode / 100) != 2) {
                    // CHECKSTYLE.ON: MagicNumber
                    dispatchErrorLogger.getLogger().error(
                            String.format(
                                    "Received failure response when sending metrics to HTTP endpoint; uri=%s, status=%s",
                                    uri,
                                    statusCode));
                } else {
                    success = true;
                }
                // CHECKSTYLE.OFF: IllegalCatch - Prevent leaking exceptions; it makes testing more complex
            } catch (final RuntimeException | IOException e) {
                // CHECKSTYLE.ON: IllegalCatch
                dispatchErrorLogger.getLogger().error(
                        String.format(
                                "Encountered failure when sending metrics to HTTP endpoint; uri=%s",
                                uri),
                        e);
            } finally {
                stopWatch.stop();
                if (eventHandler.isPresent()) {
                    eventHandler.get().attemptComplete(
                            request.getRecordsCount(),
                            entity.getContentLength(),
                            success,
                            stopWatch.getElapsedTime(),
                            stopWatch.getUnit());
                }
            }
        }

        private ClientV3.Record serializeEvent(final Event event) {
            final ClientV3.Record.Builder builder = ClientV3.Record.newBuilder();

            // Serialize metric samples first and opportunistically include any
            // matching metric aggregated data. Then serialize any metric
            // aggregated data for metrics that don't have any sample data.

            // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
            final Set<String> processedMetricName = new HashSet<>();
            // CHECKSTYLE.ON: IllegalInstantiation

            for (final Map.Entry<String, List<Quantity>> entry : event.getSamples().entrySet()) {
                builder.addData(ClientV3.MetricDataEntry.newBuilder()
                        .setNumericalData(buildNumericalData(entry.getValue(), event.getAggregatedData().get(entry.getKey())))
                        .setName(createIdentifier(entry.getKey()))
                        .build());
                processedMetricName.add(entry.getKey());
            }

            for (final Map.Entry<String, AggregatedData> entry : event.getAggregatedData().entrySet()) {
                if (!processedMetricName.contains(entry.getKey())) {
                    builder.addData(ClientV3.MetricDataEntry.newBuilder()
                            .setNumericalData(buildNumericalData(Collections.emptyList(), entry.getValue()))
                            .setName(createIdentifier(entry.getKey()))
                            .build());
                }
            }

            // Serialize all dimensions
            for (final Map.Entry<String, String> entry : event.getDimensions().entrySet()) {
                builder.addDimensions(
                        ClientV3.DimensionEntry.newBuilder()
                                .setName(createIdentifier(entry.getKey()))
                                .setValue(createIdentifier(entry.getValue()))
                                .build());
            }

            // Serialize the start and end dates
            builder.setStartMillisSinceEpoch(event.getStartTime().toEpochMilli());
            builder.setEndMillisSinceEpoch(event.getEndTime().toEpochMilli());

            // Serialize the event identifier
            final UUID uuid = event.getId();
            final ByteBuffer buffer = ByteBuffer.wrap(new byte[UUID_LENGTH_BYTES]);
            buffer.putLong(uuid.getMostSignificantBits());
            buffer.putLong(uuid.getLeastSignificantBits());
            buffer.rewind();
            builder.setId(ByteString.copyFrom(buffer));

            return builder.build();
        }

        private ClientV3.Identifier createIdentifier(final String value) {
            return ClientV3.Identifier.newBuilder()
                    .setStringValue(value)
                    .build();
        }

        private ClientV3.NumericalData buildNumericalData(final List<Quantity> samples, @Nullable final AggregatedData aggregatedData) {
            final ClientV3.NumericalData.Builder builder = ClientV3.NumericalData.newBuilder();
            builder.addAllSamples(samples.stream().map(q -> q.getValue().doubleValue()).collect(Collectors.toList()));
            if (aggregatedData instanceof AugmentedHistogram) {
                final AugmentedHistogram augmentedHistogram = (AugmentedHistogram) aggregatedData;
                final int precision = augmentedHistogram.getPrecision();
                final long packMask = (1 << (precision + EXPONENT_BITS + 1)) - 1;
                builder.setStatistics(
                        ClientV3.AugmentedHistogram.newBuilder()
                                .setPrecision(augmentedHistogram.getPrecision())
                                .setMin(augmentedHistogram.getMin())
                                .setMax(augmentedHistogram.getMax())
                                .setSum(augmentedHistogram.getSum())
                                .addAllEntries(
                                        augmentedHistogram.getHistogram()
                                                .entrySet()
                                                .stream()
                                                .map(entry -> {
                                                    final long truncatedKeyAsLong = Double.doubleToRawLongBits(entry.getKey());
                                                    final long shiftedKey = truncatedKeyAsLong >> (MANTISSA_BITS - precision);
                                                    final long packedKey = shiftedKey & packMask;
                                                    return ClientV3.SparseHistogramEntry.newBuilder()
                                                            .setBucket(packedKey)
                                                            .setCount(entry.getValue())
                                                            .build();
                                                })
                                                ::iterator)
                                .build()
                );
            } else if (aggregatedData != null) {
                unsupportedDataLogger.getLogger().error(
                        String.format(
                                "Unsupported aggregated data type; class=%s",
                                aggregatedData.getClass().getName()));
            }
            return builder.build();
        }
    }

    // TODO(ville): Replace this with a proper close/shutdown pattern.
    private static final class ShutdownHookThread extends Thread {

        private final AtomicBoolean isRunning;
        private final ExecutorService executor;

        ShutdownHookThread(
                final AtomicBoolean isRunning,
                final ExecutorService executor) {
            this.isRunning = isRunning;
            this.executor = executor;
        }

        @Override
        public void run() {
            isRunning.set(false);
            executor.shutdown();
        }
    }

    /**
     * Builder for {@link HttpSink}.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    public static class Builder {

        private static final Integer DEFAULT_BUFFER_SIZE = 10000;
        private static final URI DEFAULT_URI = URI.create("http://localhost:7090/metrics/v3/application");
        private static final Integer DEFAULT_PARALLELISM = 2;
        private static final Integer DEFAULT_MAX_BATCH_SIZE = 500;
        private static final Duration DEFAULT_EMPTY_QUEUE_INTERVAL = Duration.ofMillis(500);
        private static final Duration DEFAULT_EVENTS_DROPPED_LOGGING_INTERVAL = Duration.ofMinutes(1);
        private static final Duration DEFAULT_DISPATCH_ERROR_LOGGING_INTERVAL = Duration.ofMinutes(1);
        private static final Duration DEFAULT_UNSUPPORTED_DATA_LOGGING_INTERVAL = Duration.ofMinutes(1);

        private Integer bufferSize = DEFAULT_BUFFER_SIZE;
        private URI uri = DEFAULT_URI;
        private Integer parallelism = DEFAULT_PARALLELISM;
        private Integer maxBatchSize = DEFAULT_MAX_BATCH_SIZE;
        private Duration emptyQueueInterval = DEFAULT_EMPTY_QUEUE_INTERVAL;
        private Duration eventsDroppedLoggingInterval = DEFAULT_EVENTS_DROPPED_LOGGING_INTERVAL;
        private Duration dispatchErrorLoggingInterval = DEFAULT_DISPATCH_ERROR_LOGGING_INTERVAL;
        private Duration unsupportedDataLoggingInterval = DEFAULT_UNSUPPORTED_DATA_LOGGING_INTERVAL;
        private @Nullable HttpSinkEventHandler eventHandler;

        /**
         * Set the buffer size in number of events. Optional; default is 10,000.
         *
         * @param value The number of events to buffer.
         * @return This {@link Builder} instance.
         */
        public Builder setBufferSize(@Nullable final Integer value) {
            bufferSize = value;
            return this;
        }

        /**
         * Set the URI of the HTTP endpoint. Optional; default is
         * {@code http://localhost:7090/metrics/v3/application}.
         *
         * @param value The uri of the HTTP endpoint.
         * @return This {@link Builder} instance.
         */
        public Builder setUri(@Nullable final URI value) {
            uri = value;
            return this;
        }

        /**
         * Set the parallelism (threads and connections) that the sink will
         * use. Optional; default is 5.
         *
         * @param value The parallelism; threads and connections.
         * @return This {@link Builder} instance.
         */
        public Builder setParallelism(@Nullable final Integer value) {
            parallelism = value;
            return this;
        }

        /**
         * Set the empty queue interval. Each worker thread will independently
         * sleep this long if the event queue is empty. Optional; default is
         * 500 milliseconds.
         *
         * @param value The empty queue interval.
         * @return This {@link Builder} instance.
         */
        public Builder setEmptyQueueInterval(@Nullable final Duration value) {
            emptyQueueInterval = value;
            return this;
        }

        /**
         * Set the maximum batch size (records to send in each request).
         * Optional; default is 500.
         *
         * @param value The maximum batch size.
         * @return This {@link Builder} instance.
         */
        public Builder setMaxBatchSize(@Nullable final Integer value) {
            maxBatchSize = value;
            return this;
        }

        /**
         * Set the events dropped logging interval. Any event drop notices will
         * be logged at most once per interval. Optional; default is 1 minute.
         *
         * @param value The logging interval.
         * @return This {@link Builder} instance.
         */
        public Builder setEventsDroppedLoggingInteval(@Nullable final Duration value) {
            eventsDroppedLoggingInterval = value;
            return this;
        }

        /**
         * Set the dispatch error logging interval. Any dispatch errors will be
         * logged at most once per interval. Optional; default is 1 minute.
         *
         * @param value The logging interval.
         * @return This {@link Builder} instance.
         */
        public Builder setDispatchErrorLoggingInterval(@Nullable final Duration value) {
            dispatchErrorLoggingInterval = value;
            return this;
        }

        /**
         * Set the unsupported data logging interval. Any unsupported data
         * errors will be logged at most once per interval. Optional; default
         * is 1 minute.
         *
         * @param value The logging interval.
         * @return This {@link Builder} instance.
         */
        public Builder setUnsupportedDataLoggingInterval(@Nullable final Duration value) {
            unsupportedDataLoggingInterval = value;
            return this;
        }

        /**
         * Set the event handler. Optional; default is {@code null}.
         *
         * @param value The event handler.
         * @return This {@link Builder} instance.
         */
        public Builder setEventHandler(@Nullable final HttpSinkEventHandler value) {
            eventHandler = value;
            return this;
        }

        /**
         * Create an instance of {@link Sink}.
         *
         * @return Instance of {@link Sink}.
         */
        public Sink build() {
            // Defaults
            applyDefaults();

            // Validate
            final List<String> failures = new ArrayList<>();
            validate(failures);

            // Fallback
            if (!failures.isEmpty()) {
                LOGGER.warn(String.format(
                        "Unable to construct %s, sink disabled; failures=%s",
                        this.getClass().getEnclosingClass().getSimpleName(),
                        failures));
                return new WarningSink.Builder()
                        .setReasons(failures)
                        .build();
            }

            return new HttpSink(this);
        }

        private void applyDefaults() {
            if (bufferSize == null) {
                bufferSize = DEFAULT_BUFFER_SIZE;
                LOGGER.info(String.format(
                        "Defaulted null buffer size; bufferSize=%s",
                        bufferSize));
            }
            if (uri == null) {
                uri = DEFAULT_URI;
                LOGGER.info(String.format(
                        "Defaulted null uri; uri=%s",
                        uri));
            }
            if (parallelism == null) {
                parallelism = DEFAULT_PARALLELISM;
                LOGGER.info(String.format(
                        "Defaulted null parallelism; parallelism=%s",
                        parallelism));
            }
            if (maxBatchSize == null) {
                maxBatchSize = DEFAULT_MAX_BATCH_SIZE;
                LOGGER.info(String.format(
                        "Defaulted null max batch size; maxBatchSize=%s",
                        maxBatchSize));
            }
            if (emptyQueueInterval == null) {
                emptyQueueInterval = DEFAULT_EMPTY_QUEUE_INTERVAL;
                LOGGER.info(String.format(
                        "Defaulted null empty queue interval; emptyQueueInterval=%s",
                        emptyQueueInterval));
            }
            if (eventsDroppedLoggingInterval == null) {
                eventsDroppedLoggingInterval = DEFAULT_EVENTS_DROPPED_LOGGING_INTERVAL;
                LOGGER.info(String.format(
                        "Defaulted null dispatch error logging interval; eventsDroppedLoggingInterval=%s",
                        eventsDroppedLoggingInterval));
            }
            if (dispatchErrorLoggingInterval == null) {
                dispatchErrorLoggingInterval = DEFAULT_DISPATCH_ERROR_LOGGING_INTERVAL;
                LOGGER.info(String.format(
                        "Defaulted null dispatch error logging interval; dispatchErrorLoggingInterval=%s",
                        dispatchErrorLoggingInterval));
            }
            if (unsupportedDataLoggingInterval == null) {
                unsupportedDataLoggingInterval = DEFAULT_UNSUPPORTED_DATA_LOGGING_INTERVAL;
                LOGGER.info(String.format(
                        "Defaulted null unsupported data logging interval; unsupportedDataLoggingInterval=%s",
                        unsupportedDataLoggingInterval));
            }
        }

        private void validate(final List<String> failures) {
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                failures.add(String.format("URI must be an http(s) URI; uri=%s", uri));
            }
        }
    }
}

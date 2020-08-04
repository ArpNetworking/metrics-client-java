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
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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

    private final ExecutorService executor;
    private final URI uri;
    private final int bufferSize;
    private final int maxBatchSize;
    private final Duration closeTimeout;
    private final Optional<HttpSinkEventHandler> eventHandler;
    private final RateLimitedLogger eventsDroppedLogger;
    private final Logger logger;
    private final Lock queueLock = new ReentrantLock(false);
    private final Condition eventAdded = queueLock.newCondition();
    private final Deque<Event> queue;
    private boolean isRunning = true;

    /**
     * Protected constructor.
     *
     * @param builder The {@link Builder} to build from.
     */
    protected HttpSink(final Builder builder) {
        logger = builder.logger;
        uri = builder.uri;
        bufferSize = builder.bufferSize;
        maxBatchSize = builder.maxBatchSize;
        closeTimeout = builder.closeTimeout;
        eventHandler = Optional.ofNullable(builder.eventHandler);
        eventsDroppedLogger = new RateLimitedLogger(
                "EventsDroppedLogger",
                logger,
                builder.eventsDroppedLoggingInterval);

        executor = Executors.newFixedThreadPool(
                builder.parallelism,
                runnable -> {
                    final Thread thread = new Thread(runnable, "metrics-http-sink");
                    thread.setDaemon(true);
                    return thread;
                });
        queue = new ArrayDeque<>(builder.bufferSize);

        // Use a single shared worker instance across the pool; see getSharedHttpClient
        final HttpDispatch httpDispatchWorker = new HttpDispatch(
                builder.httpClientSupplier,
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
        queueLock.lock();
        try {
            if (!isRunning) {
                eventsDroppedLogger.warn(
                        "Sink closed; dropping event");
                eventHandler.ifPresent(eh -> eh.droppedEvent(event));
                return;
            }
            queue.push(event);
            eventAdded.signal();
            if (queue.size() > bufferSize) {
                eventsDroppedLogger.warn(
                        "Event queue is full; dropping event(s)");
                final Event droppedEvent = queue.pollFirst();
                eventHandler.ifPresent(eh -> eh.droppedEvent(droppedEvent));
            }
        } finally {
            queueLock.unlock();
        }
    }

    @Override
    public void close() throws InterruptedException {
        queueLock.lock();
        try {
            // There is no explicit flush because the dispatch workers will
            // only terminate once the queue is empty.
            //
            // NOTE: This doesn't work perfectly at the moment because there
            // are no request timeouts and the Apache request is not interruptable.
            // Consequently, the executor threads are currently marked as
            // daemon threads so when close times out, fails to interrupt and
            // completes, the application can still terminate.
            isRunning = false;
            eventAdded.signalAll();
        } finally {
            queueLock.unlock();
        }

        try {
            executor.shutdown();
            if (!executor.awaitTermination(closeTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();

                queueLock.lock();
                try {
                    for (final Event droppedEvent : queue) {
                        eventHandler.ifPresent(eh -> eh.droppedEvent(droppedEvent));
                    }
                    logger.warn(
                            String.format(
                                    "Close timed out; dropping %d event(s)",
                                    queue.size()));
                    queue.clear();
                } finally {
                    queueLock.unlock();
                }
            }
        } catch (final InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    @Override
    public String toString() {
        return String.format("HttpSink{endpoint=%s}", uri.toString());
    }

    URI getUri() {
        return uri;
    }

    int getBufferSize() {
        return bufferSize;
    }

    int getMaxBatchSize() {
        return maxBatchSize;
    }

    boolean isOpen() {
        queueLock.lock();
        try {
            return isRunning;
        } finally {
            queueLock.unlock();
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
            final List<Event> eventBatch = new ArrayList<>(maxBatchSize);
            while (isRunning || !queue.isEmpty()) {
                try {
                    // Create a batch of events
                    batchEvents(eventBatch);

                    if (!eventBatch.isEmpty()) {
                        // Serialize and dispatch the batch of events
                        final ClientV3.Request.Builder requestBuilder = ClientV3.Request.newBuilder();
                        for (final Event event : eventBatch) {
                            requestBuilder.addRecords(serializeEvent(event));
                        }
                        dispatchRequest(httpClientSupplier.get(), requestBuilder.build());
                    }

                    // CHECKSTYLE.OFF: IllegalCatch - Ensure exception neutrality
                } catch (final InterruptedException | RuntimeException e) {
                    // CHECKSTYLE.ON: IllegalCatch
                    // TODO(ville): Should we just terminate on interruption?
                    dispatchErrorLogger.error("MetricsHttpSinkWorker failure", e);
                }
            }
        }

        private void batchEvents(final List<Event> eventBatch) throws InterruptedException {
            eventBatch.clear();
            queueLock.lock();
            try {
                // Wait for data
                // NOTE: If we need to batch more aggressively in order to
                // scale the interaction between the client and aggregator
                // then we should instead wait until at least N nanoseconds
                // or until there is max batch size records.
                while (isRunning && queue.isEmpty()) {
                    eventAdded.await();
                }

                // Collect a batch of events to send
                do {
                    @Nullable final Event event = queue.pollFirst();
                    if (event == null) {
                        break;
                    }
                    eventBatch.add(event);
                } while (eventBatch.size() < maxBatchSize);

                // Wake up other dispatcher if there is still work in the queue
                // NOTE: Avoid the herd by waking up just a single worker on each
                // add, but if after creating a batch there is still more work in
                // the queue then ensure another worker is awake too (which then
                // does the same thing).
                if (!queue.isEmpty()) {
                    eventAdded.signal();
                }
            } finally {
                queueLock.unlock();
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

            // TODO(ville): Implement request timeout.
            // The Apache client does not support end-to-end request-reply timeouts
            // and these must be added by the user. The timeouts we currently
            // configure are the connect timeout and the socket timeout, which is
            // the time between bytes received on the socket.
            //
            // Once we have request timeouts we can remove the daemon thread flag
            // on the dispatcher pool _IF_ we ensure that the close timeout is
            // greater than both the connect and request timeout (combined?).
            //
            // This is because to have a proper timeout, we need to cancel the
            // HTTP request, which is effectively what is necessary for shutdown
            // with non-daemon threads since the request is not interruptable
            // via shutdownNow() (e.g. Thread.interrupt()).
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                final int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == HttpStatus.SC_NOT_FOUND) {
                    // TODO(ville): Fix this after we've fixed MAD.
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
                    dispatchErrorLogger.error(
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
                dispatchErrorLogger.error(
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
                unsupportedDataLogger.error(
                        String.format(
                                "Unsupported aggregated data type; class=%s",
                                aggregatedData.getClass().getName()));
            }
            return builder.build();
        }
    }

    /**
     * Builder for {@link HttpSink}.
     *
     * This class is <b>NOT</b> thread safe.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    public static class Builder {

        private static final String DEFAULT_SCHEME = "http";
        private static final String DEFAULT_HOST = "localhost";
        private static final Integer DEFAULT_PORT = 7090;
        private static final String DEFAULT_PATH = "/metrics/v3/application";
        private static final Integer DEFAULT_BUFFER_SIZE = 10000;
        private static final Integer DEFAULT_PARALLELISM = 2;
        private static final Integer DEFAULT_MAX_BATCH_SIZE = 500;
        private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
        private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(3);
        private static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(3);
        private static final Duration DEFAULT_EVENTS_DROPPED_LOGGING_INTERVAL = Duration.ofMinutes(1);
        private static final Duration DEFAULT_DISPATCH_ERROR_LOGGING_INTERVAL = Duration.ofMinutes(1);
        private static final Duration DEFAULT_UNSUPPORTED_DATA_LOGGING_INTERVAL = Duration.ofMinutes(1);

        private static final URI DEFAULT_URI = URI.create(String.format(
                "%s://%s:%d%s",
                DEFAULT_SCHEME,
                DEFAULT_HOST,
                DEFAULT_PORT,
                DEFAULT_PATH));

        private String scheme = DEFAULT_SCHEME;
        private String host = DEFAULT_HOST;
        private Integer port = DEFAULT_PORT;
        private String path = DEFAULT_PATH;
        private Integer bufferSize = DEFAULT_BUFFER_SIZE;
        private Integer parallelism = DEFAULT_PARALLELISM;
        private Integer maxBatchSize = DEFAULT_MAX_BATCH_SIZE;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private Duration closeTimeout = DEFAULT_CLOSE_TIMEOUT;
        private Duration eventsDroppedLoggingInterval = DEFAULT_EVENTS_DROPPED_LOGGING_INTERVAL;
        private Duration dispatchErrorLoggingInterval = DEFAULT_DISPATCH_ERROR_LOGGING_INTERVAL;
        private Duration unsupportedDataLoggingInterval = DEFAULT_UNSUPPORTED_DATA_LOGGING_INTERVAL;
        private @Nullable HttpSinkEventHandler eventHandler;

        private URI uri;
        private Supplier<CloseableHttpClient> httpClientSupplier;
        private final Logger logger;

        /**
         * Public constructor.
         */
        public Builder() {
            this(LOGGER, null);
        }

        Builder(final Logger logger) {
            this(logger, null);
        }

        Builder(final Logger logger, @Nullable final Supplier<CloseableHttpClient> httpClientSupplier) {
            this.logger = logger;
            this.httpClientSupplier = httpClientSupplier;
        }

        /**
         * Set the scheme of the HTTP endpoint. Optional; default is
         * {@code http}. Must be {@code http} or {@code https}.
         *
         * @param value The scheme of the HTTP endpoint.
         * @return This {@link Builder} instance.
         */
        public Builder setScheme(@Nullable final String value) {
            scheme = value;
            return this;
        }

        /**
         * Set the host of the HTTP endpoint. Optional; default is
         * {@code localhost}.
         *
         * @param value The host of the HTTP endpoint.
         * @return This {@link Builder} instance.
         */
        public Builder setHost(@Nullable final String value) {
            host = value;
            return this;
        }

        /**
         * Set the port of the HTTP endpoint. Optional; default is
         * {@code 7090}.
         *
         * @param value The port of the HTTP endpoint.
         * @return This {@link Builder} instance.
         */
        public Builder setPort(@Nullable final Integer value) {
            port = value;
            return this;
        }

        /**
         * Set the path of the HTTP endpoint. Optional; default is
         * {@code metrics/v3/application}.
         *
         * @param value The path of the HTTP endpoint.
         * @return This {@link Builder} instance.
         */
        public Builder setPath(@Nullable final String value) {
            path = value;
            return this;
        }

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
         * Set the connect timeout. Optional; default is 10 seconds.
         *
         * @param value The connect timeout.
         * @return This {@link Builder} instance.
         */
        public Builder setConnectTimeout(@Nullable final Duration value) {
            connectTimeout = value;
            return this;
        }

        /**
         * Set the request timeout. Optional; default is 3 seconds.
         *
         * @param value The request timeout.
         * @return This {@link Builder} instance.
         */
        public Builder setRequestTimeout(@Nullable final Duration value) {
            requestTimeout = value;
            return this;
        }

        /**
         * Set the close timeout. Optional; default is 3 seconds.
         *
         * @param value The close timeout.
         * @return This {@link Builder} instance.
         */
        public Builder setCloseTimeout(@Nullable final Duration value) {
            closeTimeout = value;
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

            // Create the URI
            try {
                uri = new URIBuilder()
                        .setScheme(scheme)
                        .setHost(host)
                        .setPort(port)
                        .setPath(path)
                        .build();
            } catch (final URISyntaxException e) {
                uri = DEFAULT_URI;
            }

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

            // Http client supplier
            if (httpClientSupplier == null) {
                httpClientSupplier = new SingletonSupplier<>(() -> {
                    final SingletonSupplier<PoolingHttpClientConnectionManager> clientManagerSupplier = new SingletonSupplier<>(() -> {
                        final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
                        connectionManager.setDefaultMaxPerRoute(parallelism);
                        connectionManager.setMaxTotal(parallelism);
                        return connectionManager;
                    });

                    final RequestConfig requestConfig = RequestConfig.custom()
                            .setConnectTimeout((int) connectTimeout.toMillis())
                            .setSocketTimeout((int) requestTimeout.toMillis())
                            .build();

                    return HttpClients.custom()
                            .setConnectionManager(clientManagerSupplier.get())
                            .setDefaultRequestConfig(requestConfig)
                            .build();
                });
            }

            return new HttpSink(this);
        }

        private <T> T defaultNull(@Nullable final T member, final T fallback, final String name, final String field) {
            if (member == null) {
                LOGGER.info(String.format(
                        "Defaulted null %s; %s=%s",
                        name,
                        field,
                        fallback));
                return fallback;
            }
            return member;
        }

        private void applyDefaults() {
            scheme = defaultNull(
                    scheme,
                    DEFAULT_SCHEME,
                    "scheme",
                    "scheme");
            host = defaultNull(
                    host,
                    DEFAULT_HOST,
                    "host",
                    "host");
            port = defaultNull(
                    port,
                    DEFAULT_PORT,
                    "port",
                    "port");
            path = defaultNull(
                    path,
                    DEFAULT_PATH,
                    "path",
                    "path");
            bufferSize = defaultNull(
                    bufferSize,
                    DEFAULT_BUFFER_SIZE,
                    "buffer size",
                    "bufferSize");
            parallelism = defaultNull(
                    parallelism,
                    DEFAULT_PARALLELISM,
                    "parallelism",
                    "parallelism");
            maxBatchSize = defaultNull(
                    maxBatchSize,
                    DEFAULT_MAX_BATCH_SIZE,
                    "max bastch size",
                    "maxBatchSize");
            connectTimeout = defaultNull(
                    connectTimeout,
                    DEFAULT_CONNECT_TIMEOUT,
                    "connect timeout",
                    "connectTimeout");
            requestTimeout = defaultNull(
                    requestTimeout,
                    DEFAULT_REQUEST_TIMEOUT,
                    "request timeout",
                    "requestTimeout");
            closeTimeout = defaultNull(
                    closeTimeout,
                    DEFAULT_CLOSE_TIMEOUT,
                    "close timeout",
                    "closeTimeout");
            eventsDroppedLoggingInterval = defaultNull(
                    eventsDroppedLoggingInterval,
                    DEFAULT_EVENTS_DROPPED_LOGGING_INTERVAL,
                    "events dropped logging interval",
                    "eventsDroppedLoggingInterval");
            dispatchErrorLoggingInterval = defaultNull(
                    dispatchErrorLoggingInterval,
                    DEFAULT_DISPATCH_ERROR_LOGGING_INTERVAL,
                    "dispatch error logging interval",
                    "dispatchErrorLoggingInterval");
            unsupportedDataLoggingInterval = defaultNull(
                    unsupportedDataLoggingInterval,
                    DEFAULT_UNSUPPORTED_DATA_LOGGING_INTERVAL,
                    "unsupported data logging interval",
                    "unsupportedDataLoggingInterval");

            if (maxBatchSize > bufferSize) {
                maxBatchSize = bufferSize;
                LOGGER.info(String.format(
                        "Defaulted max batch size > buffer size); maxBatchSize=%s",
                        maxBatchSize));
            }
        }

        private void validate(final List<String> failures) {
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                failures.add(String.format("Scheme must be http or https; scheme=%s", scheme));
            }
        }
    }
}

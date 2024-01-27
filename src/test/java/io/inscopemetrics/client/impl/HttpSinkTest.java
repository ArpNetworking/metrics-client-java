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

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.ValueMatcher;
import io.inscopemetrics.client.AggregatedData;
import io.inscopemetrics.client.Event;
import io.inscopemetrics.client.Quantity;
import io.inscopemetrics.client.Sink;
import io.inscopemetrics.client.protocol.ClientV3;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for {@link HttpSink}.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class HttpSinkTest {

    private static final String PATH = "/wiremock/test/path";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(
            WireMockConfiguration.wireMockConfig()
                    .dynamicPort()
                    .asynchronousResponseEnabled(true));

    private final UUID id = UUID.randomUUID();
    private final Instant start = Instant.now().minusMillis(812);
    private final Instant end = Instant.now();

    @Test
    public void testBuilderWithDefaults() {
        final Sink sink = new HttpSink.Builder().build();
        assertNotNull(sink);
        assertEquals(HttpSink.class, sink.getClass());
    }

    @Test
    public void testBuilderWithNulls() {
        final HttpSink.Builder builder = new HttpSink.Builder();
        builder.setBufferSize(null);
        builder.setParallelism(null);
        builder.setScheme(null);
        builder.setHost(null);
        builder.setPort(null);
        builder.setPath(null);
        builder.setMaxBatchSize(null);
        builder.setConnectTimeout(null);
        builder.setRequestTimeout(null);
        builder.setCloseTimeout(null);
        builder.setDispatchErrorLoggingInterval(null);
        builder.setEventsDroppedLoggingInteval(null);
        builder.setUnsupportedDataLoggingInterval(null);
        final Sink sink = builder.build();
        assertNotNull(sink);
        assertEquals(HttpSink.class, sink.getClass());
    }

    @Test
    public void testBuilderWithHttpsUri() {
        final HttpSink.Builder builder = new HttpSink.Builder()
                .setScheme("https")
                .setHost("secure.example.com");
        final Sink sink = builder.build();
        assertNotNull(sink);
        assertEquals(HttpSink.class, sink.getClass());
    }

    @Test
    public void testBuilderWithNotHttpUri() {
        final HttpSink.Builder builder = new HttpSink.Builder()
                .setScheme("ftp")
                .setHost("ftp.example.com");
        final Sink sink = builder.build();
        assertNotNull(sink);
        assertNotEquals(HttpSink.class, sink.getClass());
    }

    @Test
    public void testBuilderWithInvalidUri() {
        final HttpSink.Builder builder = new HttpSink.Builder()
                .setHost("no spaces allowed");
        final Sink sink = builder.build();
        assertNotNull(sink);
        assertEquals(HttpSink.class, sink.getClass());
        final HttpSink httpSink = (HttpSink) sink;
        assertEquals("http://localhost:7090/metrics/v3/application", httpSink.getUri().toString());
    }

    @Test
    public void testBuilderBatchLargerThanBuffer() {
        final HttpSink.Builder builder = new HttpSink.Builder()
                .setBufferSize(10)
                .setMaxBatchSize(100);
        final Sink sink = builder.build();
        assertNotNull(sink);
        assertEquals(HttpSink.class, sink.getClass());
        final HttpSink httpSink = (HttpSink) sink;
        assertEquals(10, httpSink.getBufferSize());
        assertEquals(10, httpSink.getMaxBatchSize());
    }

    // CHECKSTYLE.OFF: MethodLength
    @Test
    public void testPostSuccess() throws InterruptedException {
        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            // Dimensions
                            assertEquals(4, r.getDimensionsCount());
                            assertDimension(r.getDimensionsList(), "foo", "bar");
                            assertDimension(r.getDimensionsList(), "_host", "some.host.com");
                            assertDimension(r.getDimensionsList(), "_service", "myservice");
                            assertDimension(r.getDimensionsList(), "_cluster", "mycluster");

                            // Samples
                            assertEquals(6, r.getDataOrBuilderList().size());
                            assertMetricData(
                                    r.getDataList(),
                                    "timerLong",
                                    123L);
                            assertMetricData(
                                    r.getDataList(),
                                    "timerInt",
                                    123);
                            assertMetricData(
                                    r.getDataList(),
                                    "timerShort",
                                    (short) 123);
                            assertMetricData(
                                    r.getDataList(),
                                    "timerByte",
                                    (byte) 123);
                            assertMetricData(
                                    r.getDataList(),
                                    "counter",
                                    8d);
                            assertMetricData(
                                    r.getDataList(),
                                    "gauge",
                                    10d);
                        }))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)));

        final AtomicBoolean assertionResult = new AtomicBoolean(false);
        final Semaphore semaphore = new Semaphore(0);
        final Sink sink = new HttpSink.Builder()
                .setPort(wireMockRule.port())
                .setPath(PATH)
                .setEventHandler(
                        new AttemptCompletedAssertionHandler(
                                assertionResult,
                                1,
                                289,
                                true,
                                new CompletionHandler(semaphore)))
                .build();

        final Map<String, String> dimensions = new LinkedHashMap<>();
        dimensions.put("foo", "bar");
        dimensions.put("_host", "some.host.com");
        dimensions.put("_service", "myservice");
        dimensions.put("_cluster", "mycluster");

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                dimensions,
                createQuantityMap(
                        "timerLong", TsdQuantity.newInstance(123L),
                        "timerInt", TsdQuantity.newInstance(123),
                        "timerShort", TsdQuantity.newInstance((short) 123),
                        "timerByte", TsdQuantity.newInstance((byte) 123),
                        "counter", TsdQuantity.newInstance(8d),
                        "gauge", TsdQuantity.newInstance(10d)),
                Collections.emptyMap());

        sink.record(event);
        semaphore.acquire();

        // Ensure expected handler was invoked
        assertTrue(assertionResult.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(1, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
    }
    // CHECKSTYLE.ON: MethodLength

    @Test
    public void testAugmentedHistogram() throws InterruptedException {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<Double, Long> histogram = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        histogram.put(1.0, 1L);
        histogram.put(2.0, 2L);
        histogram.put(3.0, 3L);

        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            // Dimensions
                            assertEquals(0, r.getDimensionsCount());

                            // Samples
                            assertEquals(2, r.getDataOrBuilderList().size());
                            assertMetricData(
                                    r.getDataList(),
                                    "timerLong",
                                    123L);
                            assertMetricData(
                                    r.getDataList(),
                                    "augmentedHistogram",
                                    ClientV3.AugmentedHistogram.newBuilder()
                                            .setSum(14.0)
                                            .setMin(1.0)
                                            .setMax(3.0)
                                            .setPrecision(7)
                                            .addEntries(ClientV3.SparseHistogramEntry.newBuilder()
                                                    .setCount(1)
                                                    .setBucket(packHistogramKey(1.0, 7))
                                                    .build())
                                            .addEntries(ClientV3.SparseHistogramEntry.newBuilder()
                                                    .setCount(2)
                                                    .setBucket(packHistogramKey(2.0, 7))
                                                    .build())
                                            .addEntries(ClientV3.SparseHistogramEntry.newBuilder()
                                                    .setCount(3)
                                                    .setBucket(packHistogramKey(3.0, 7))
                                                    .build())
                                            .build());
                        }))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)));

        final AtomicBoolean assertionResult = new AtomicBoolean(false);
        final Semaphore semaphore = new Semaphore(0);
        final org.slf4j.Logger logger = mock(org.slf4j.Logger.class);
        final Sink sink = new HttpSink.Builder(logger)
                .setPort(wireMockRule.port())
                .setPath(PATH)
                .setEventHandler(
                        new AttemptCompletedAssertionHandler(
                                assertionResult,
                                1,
                                143,
                                true,
                                new CompletionHandler(semaphore)))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),
                createQuantityMap("timerLong", TsdQuantity.newInstance(123L)),
                Collections.singletonMap(
                        "augmentedHistogram",
                        new AugmentedHistogram.Builder()
                                .setSum(14.0)
                                .setMinimum(1.0)
                                .setMaximum(3.0)
                                .setPrecision(7)
                                .setHistogram(histogram)
                                .build()));

        sink.record(event);
        semaphore.acquire();

        // Ensure expected handler was invoked
        assertTrue(assertionResult.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(1, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
    }

    @Test
    public void testAugmentedHistogramAndSamplesMerged() throws InterruptedException {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<Double, Long> histogram = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        histogram.put(1.0, 1L);
        histogram.put(2.0, 2L);
        histogram.put(3.0, 3L);

        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            // Dimensions
                            assertEquals(0, r.getDimensionsCount());

                            // Samples
                            assertEquals(1, r.getDataOrBuilderList().size());
                            assertMetricData(
                                    r.getDataList(),
                                    "mergedData",
                                    Collections.singletonList(123.0),
                                    ClientV3.AugmentedHistogram.newBuilder()
                                            .setSum(14.0)
                                            .setMin(1.0)
                                            .setMax(3.0)
                                            .setPrecision(7)
                                            .addEntries(ClientV3.SparseHistogramEntry.newBuilder()
                                                    .setCount(1)
                                                    .setBucket(packHistogramKey(1.0, 7))
                                                    .build())
                                            .addEntries(ClientV3.SparseHistogramEntry.newBuilder()
                                                    .setCount(2)
                                                    .setBucket(packHistogramKey(2.0, 7))
                                                    .build())
                                            .addEntries(ClientV3.SparseHistogramEntry.newBuilder()
                                                    .setCount(3)
                                                    .setBucket(packHistogramKey(3.0, 7))
                                                    .build())
                                            .build());
                        }))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)));

        final AtomicBoolean assertionResult = new AtomicBoolean(false);
        final Semaphore semaphore = new Semaphore(0);
        final org.slf4j.Logger logger = mock(org.slf4j.Logger.class);
        final Sink sink = new HttpSink.Builder()
                .setPort(wireMockRule.port())
                .setPath(PATH)
                .setEventHandler(
                        new AttemptCompletedAssertionHandler(
                                assertionResult,
                                1,
                                117,
                                true,
                                new CompletionHandler(semaphore)))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),
                createQuantityMap("mergedData", TsdQuantity.newInstance(123.0)),
                Collections.singletonMap(
                        "mergedData",
                        new AugmentedHistogram.Builder()
                                .setSum(14.0)
                                .setMinimum(1.0)
                                .setMaximum(3.0)
                                .setPrecision(7)
                                .setHistogram(histogram)
                                .build()));

        sink.record(event);
        semaphore.acquire();

        // Ensure expected handler was invoked
        assertTrue(assertionResult.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(1, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
    }

    @Test
    public void testUnsupportedAggregateData() throws InterruptedException {
        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            // Dimensions
                            assertEquals(0, r.getDimensionsCount());

                            // Samples
                            assertEquals(2, r.getDataOrBuilderList().size());
                            assertMetricData(
                                    r.getDataList(),
                                    "timerLong",
                                    123L);
                            // NOTE: The other metric is the empty record unsupportedAggregatedDataTypeMetric
                        }))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)));

        final AtomicBoolean assertionResult = new AtomicBoolean(false);
        final Semaphore semaphore = new Semaphore(0);
        final org.slf4j.Logger logger = mock(org.slf4j.Logger.class);
        final Sink sink = new HttpSink.Builder(logger)
                .setPort(wireMockRule.port())
                .setPath(PATH)
                .setEventHandler(
                        new AttemptCompletedAssertionHandler(
                                assertionResult,
                                1,
                                104,
                                true,
                                new CompletionHandler(semaphore)))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),
                createQuantityMap("timerLong", TsdQuantity.newInstance(123L)),
                Collections.singletonMap(
                        "unsupportedAggregatedDataTypeMetric",
                        new TestUnsupportedAggregatedData()));

        sink.record(event);
        semaphore.acquire();

        // Ensure expected handler was invoked
        assertTrue(assertionResult.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(1, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Verify that
        verify(logger).error(
                startsWith(
                        String.format(
                                "Unsupported aggregated data type; class=%s",
                                TestUnsupportedAggregatedData.class.getName())));
    }

    @Test
    public void testNoUnits() throws InterruptedException {
        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            // Dimensions
                            assertEquals(0, r.getDimensionsCount());

                            // Samples
                            assertMetricData(
                                    r.getDataList(),
                                    "timer",
                                    7d);
                            assertMetricData(
                                    r.getDataList(),
                                    "counter",
                                    8d);
                            assertMetricData(
                                    r.getDataList(),
                                    "gauge",
                                    9d);
                        }))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)));

        final Semaphore semaphore = new Semaphore(0);
        final Sink sink = new HttpSink.Builder()
                .setPort(wireMockRule.port())
                .setPath(PATH)
                .setEventHandler(new CompletionHandler(semaphore))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),
                createQuantityMap(
                        "timer", TsdQuantity.newInstance(7d),
                        "counter", TsdQuantity.newInstance(8d),
                        "gauge", TsdQuantity.newInstance(9d)),
                Collections.emptyMap());

        sink.record(event);
        semaphore.acquire();

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(1, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
    }

    @Test
    public void testBatchesRequests() throws InterruptedException {
        final AtomicBoolean initialRequest = new AtomicBoolean(true);
        final Semaphore clientSemaphore = new Semaphore(0);
        final Semaphore serverSemaphore = new Semaphore(0);
        final Semaphore completionSemaphore = new Semaphore(0);

        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            if (initialRequest.get()) {
                                initialRequest.set(false);
                                try {
                                    clientSemaphore.release();
                                    serverSemaphore.acquire();
                                } catch (final InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            } else {
                                // Dimensions
                                assertEquals(0, r.getDimensionsCount());

                                // Samples
                                assertMetricData(
                                        r.getDataList(),
                                        "timer",
                                        7d);
                                assertMetricData(
                                        r.getDataList(),
                                        "counter",
                                        8d);
                                assertMetricData(
                                        r.getDataList(),
                                        "gauge",
                                        9d);
                            }
                        }))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)));

        final AtomicBoolean blockerRequestResult = new AtomicBoolean(false);
        final AtomicBoolean batchRequestResult = new AtomicBoolean(false);
        final Sink sink = new HttpSink.Builder()
                .setPort(wireMockRule.port())
                .setPath(PATH)
                .setMaxBatchSize(10)
                .setParallelism(1)
                .setEventHandler(
                        new AttemptCompletedAssertionHandler(
                                Arrays.asList(
                                    new AssertionResult(
                                            blockerRequestResult,
                                            1,
                                            105,
                                            true),
                                    new AssertionResult(
                                            batchRequestResult,
                                            3,
                                            315,
                                            true)),
                                new CompletionHandler(completionSemaphore)))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),
                createQuantityMap(
                        "timer", TsdQuantity.newInstance(7d),
                        "counter", TsdQuantity.newInstance(8d),
                        "gauge", TsdQuantity.newInstance(9d)),
                Collections.emptyMap());

        // Send initial "blocker" event to allow back-log to accumulate
        sink.record(event);
        clientSemaphore.acquire();

        // Actual batch of requests
        for (int x = 0; x < 3; x++) {
            sink.record(event);
        }

        // Allow the server to continue processing
        serverSemaphore.release();

        // Ensure expected handler was invoked
        completionSemaphore.acquire(2);
        assertTrue(blockerRequestResult.get());
        assertTrue(batchRequestResult.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(2, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
    }

    @Test
    public void testBatchesRequestsRespectsMax() throws InterruptedException {
        final AtomicBoolean initialRequest = new AtomicBoolean(true);
        final Semaphore clientSemaphore = new Semaphore(0);
        final Semaphore serverSemaphore = new Semaphore(0);
        final Semaphore completionSemaphore = new Semaphore(0);

        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            if (initialRequest.get()) {
                                initialRequest.set(false);
                                try {
                                    clientSemaphore.release();
                                    serverSemaphore.acquire();
                                } catch (final InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            } else {
                                // Dimensions
                                assertEquals(0, r.getDimensionsCount());

                                // Samples
                                assertMetricData(
                                        r.getDataList(),
                                        "timer",
                                        7d);
                                assertMetricData(
                                        r.getDataList(),
                                        "counter",
                                        8d);
                                assertMetricData(
                                        r.getDataList(),
                                        "gauge",
                                        9d);
                            }
                        }))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)));

        final AtomicBoolean blockerRequestResult = new AtomicBoolean(false);
        final AtomicBoolean batch1RequestResult = new AtomicBoolean(false);
        final AtomicBoolean batch2RequestResult = new AtomicBoolean(false);
        final AtomicBoolean batch3RequestResult = new AtomicBoolean(false);
        final Sink sink = new HttpSink.Builder()
                .setPort(wireMockRule.port())
                .setPath(PATH)
                .setMaxBatchSize(2)
                .setParallelism(1)
                .setEventHandler(
                        new AttemptCompletedAssertionHandler(
                                Arrays.asList(
                                        new AssertionResult(
                                                blockerRequestResult,
                                                1,
                                                105,
                                                true),
                                        new AssertionResult(
                                                batch1RequestResult,
                                                2,
                                                210,
                                                true),
                                        new AssertionResult(
                                                batch2RequestResult,
                                                2,
                                                210,
                                                true),
                                        new AssertionResult(
                                                batch3RequestResult,
                                                1,
                                                105,
                                                true)),
                                new CompletionHandler(completionSemaphore)))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),
                createQuantityMap(
                        "timer", TsdQuantity.newInstance(7d),
                        "counter", TsdQuantity.newInstance(8d),
                        "gauge", TsdQuantity.newInstance(9d)),
                Collections.emptyMap());

        // Send initial "blocker" event to allow back-log to accumulate
        sink.record(event);
        clientSemaphore.acquire();

        // Actual batch of requests
        for (int x = 0; x < 5; x++) {
            sink.record(event);
        }

        // Allow the server to continue processing
        serverSemaphore.release();

        // Ensure expected handler was invoked
        completionSemaphore.acquire(4);
        assertTrue(blockerRequestResult.get());
        assertTrue(batch1RequestResult.get());
        assertTrue(batch2RequestResult.get());
        assertTrue(batch3RequestResult.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(4, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
    }

    @Test
    public void testRespectsBufferMax() throws InterruptedException {
        final AtomicBoolean initialRequest = new AtomicBoolean(true);
        final Semaphore clientSemaphore = new Semaphore(0);
        final Semaphore serverSemaphore = new Semaphore(0);
        final Semaphore completionSemaphore = new Semaphore(0);

        final AtomicInteger droppedEvents = new AtomicInteger(0);
        final AtomicInteger recordsReceived = new AtomicInteger(0);

        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            recordsReceived.incrementAndGet();

                            if (initialRequest.get()) {
                                initialRequest.set(false);
                                try {
                                    clientSemaphore.release();
                                    serverSemaphore.acquire();
                                } catch (final InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            } else {
                                // Dimensions
                                assertEquals(0, r.getDimensionsCount());

                                // Samples
                                assertMetricData(
                                        r.getDataList(),
                                        "timer",
                                        7d);
                                assertMetricData(
                                        r.getDataList(),
                                        "counter",
                                        8d);
                                assertMetricData(
                                        r.getDataList(),
                                        "gauge",
                                        9d);
                            }
                        }))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)));

        final Sink sink = new HttpSink.Builder()
                .setPort(wireMockRule.port())
                .setPath(PATH)
                .setMaxBatchSize(2)
                .setParallelism(1)
                .setBufferSize(5)
                .setEventHandler(
                        new EventDroppedAssertionHandler(
                                droppedEvents,
                                new CompletionHandler(completionSemaphore)))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),
                createQuantityMap(
                        "timer", TsdQuantity.newInstance(7d),
                        "counter", TsdQuantity.newInstance(8d),
                        "gauge", TsdQuantity.newInstance(9d)),
                Collections.emptyMap());

        // Send initial "blocker" event to allow back-log to accumulate
        sink.record(event);
        clientSemaphore.acquire();

        // Add the actual events to analyze
        for (int x = 0; x < 10; x++) {
            sink.record(event);
        }

        // Allow the server to continue processing
        serverSemaphore.release();

        // Ensure expected handler was invoked
        completionSemaphore.acquire(4);

        // Ensure expected handler was invoked
        assertEquals(5, droppedEvents.get());

        // Assert number of records received
        assertEquals(6, recordsReceived.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(4, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
    }

    @Test
    public void testEndpointNotAvailable() throws InterruptedException {
        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            // Dimensions
                            assertEquals(0, r.getDimensionsCount());

                            // Samples
                            assertEquals(0, r.getDataList().size());
                        }))
                        .willReturn(WireMock.aResponse()
                                .withStatus(404)));

        final AtomicBoolean assertionResult = new AtomicBoolean(false);
        final Semaphore semaphore = new Semaphore(0);
        final org.slf4j.Logger logger = mock(org.slf4j.Logger.class);
        final Sink sink = new HttpSink.Builder(logger)
                .setPort(wireMockRule.port())
                .setPath(PATH)
                .setEventHandler(
                        new AttemptCompletedAssertionHandler(
                                assertionResult,
                                1,
                                34,
                                false,
                                new CompletionHandler(
                                        semaphore)))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),  // dimensions
                Collections.emptyMap(),  // samples
                Collections.emptyMap()); // aggregates

        sink.record(event);
        semaphore.acquire();

        // Ensure expected handler was invoked
        assertTrue(assertionResult.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(1, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Assert that an IOException was captured
        verify(logger).error(
                startsWith("Encountered failure when sending metrics to HTTP endpoint; uri="),
                any(RuntimeException.class));
    }

    @Test
    public void testPostFailure() throws InterruptedException {
        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            // Dimensions
                            assertEquals(0, r.getDimensionsCount());

                            // Samples
                            assertEquals(0, r.getDataList().size());
                        }))
                        .willReturn(WireMock.aResponse()
                                .withStatus(400)));

        final AtomicBoolean assertionResult = new AtomicBoolean(false);
        final Semaphore semaphore = new Semaphore(0);
        final org.slf4j.Logger logger = mock(org.slf4j.Logger.class);
        final Sink sink = new HttpSink.Builder(logger)
                .setPort(wireMockRule.port())
                .setPath(PATH)
                .setEventHandler(
                        new AttemptCompletedAssertionHandler(
                                assertionResult,
                                1,
                                34,
                                false,
                                new CompletionHandler(
                                        semaphore)))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),  // dimensions
                Collections.emptyMap(),  // samples
                Collections.emptyMap()); // aggregates

        sink.record(event);
        semaphore.acquire();

        // Ensure expected handler was invoked
        assertTrue(assertionResult.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(1, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Assert that an IOException was captured
        verify(logger).error(
                startsWith("Received failure response when sending metrics to HTTP endpoint; uri="));
    }

    @Test
    public void testPostBadHost() throws InterruptedException {
        final org.slf4j.Logger logger = mock(org.slf4j.Logger.class);
        final Semaphore semaphore = new Semaphore(0);
        final Sink sink = new HttpSink.Builder(logger)
                .setHost("nohost.example.com")
                .setPath(PATH)
                .setEventHandler(new CompletionHandler(semaphore))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),  // dimensions
                Collections.emptyMap(),  // samples
                Collections.emptyMap()); // aggregates

        sink.record(event);
        semaphore.acquire();

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that no data was sent
        wireMockRule.verify(0, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Assert that an IOException was captured
        verify(logger).error(
                startsWith("Encountered failure when sending metrics to HTTP endpoint; uri="),
                any(IOException.class));
    }

    @Test
    public void testHttpClientExecuteException() throws InterruptedException {
        final CloseableHttpClient httpClient = mock(
                CloseableHttpClient.class,
                invocationOnMock -> {
                    throw new NullPointerException("Throw by default");
                });

        final org.slf4j.Logger logger = mock(org.slf4j.Logger.class);
        final Semaphore semaphore = new Semaphore(0);
        final Sink sink = new HttpSink.Builder(logger, () -> httpClient)
                .setHost("nohost.example.com")
                .setPath(PATH)
                .setEventHandler(new CompletionHandler(semaphore))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),  // dimensions
                Collections.emptyMap(),  // samples
                Collections.emptyMap()); // aggregates

        sink.record(event);
        semaphore.acquire();

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that no data was sent
        wireMockRule.verify(0, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Assert that the runtime exception was captured
        verify(logger).error(
                startsWith("Encountered failure when sending metrics to HTTP endpoint; uri="),
                any(NullPointerException.class));
    }

    @Test
    public void testHttpClientResponseException() throws InterruptedException, IOException {
        final CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        final CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
        doReturn(httpResponse).when(httpClient).execute(any(HttpPost.class));
        doThrow(new NullPointerException("Throw by default")).when(httpResponse).getStatusLine();

        final org.slf4j.Logger logger = mock(org.slf4j.Logger.class);
        final Semaphore semaphore = new Semaphore(0);
        final Sink sink = new HttpSink.Builder(logger, () -> httpClient)
                .setHost("nohost.example.com")
                .setPath(PATH)
                .setEventHandler(new CompletionHandler(semaphore))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),  // dimensions
                Collections.emptyMap(),  // samples
                Collections.emptyMap()); // aggregates

        sink.record(event);
        semaphore.acquire();

        // Verify mocks
        verify(httpClient).execute(any(HttpPost.class));
        verifyNoMoreInteractions(httpClient);
        verify(httpResponse).getStatusLine();
        verify(httpResponse).close();
        verifyNoMoreInteractions(httpResponse);

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that no data was sent
        wireMockRule.verify(0, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Assert that the runtime exception was captured
        verify(logger).error(
                startsWith("Encountered failure when sending metrics to HTTP endpoint; uri="),
                any(NullPointerException.class));
    }

    @Test
    public void testHttpClientResponseCloseException() throws InterruptedException, IOException {
        final CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        final CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
        doReturn(httpResponse).when(httpClient).execute(any(HttpPost.class));
        doThrow(new NullPointerException("Throw by default")).when(httpResponse).getStatusLine();
        doThrow(new IllegalStateException("Throw by default")).when(httpResponse).close();

        final org.slf4j.Logger logger = mock(org.slf4j.Logger.class);
        final Semaphore semaphore = new Semaphore(0);
        final Sink sink = new HttpSink.Builder(logger, () -> httpClient)
                .setHost("nohost.example.com")
                .setPath(PATH)
                .setEventHandler(new CompletionHandler(semaphore))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),  // dimensions
                Collections.emptyMap(),  // samples
                Collections.emptyMap()); // aggregates

        sink.record(event);
        semaphore.acquire();

        // Verify mocks
        verify(httpClient).execute(any(HttpPost.class));
        verifyNoMoreInteractions(httpClient);
        verify(httpResponse).getStatusLine();
        verify(httpResponse).close();
        verifyNoMoreInteractions(httpResponse);

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that no data was sent
        wireMockRule.verify(0, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Assert that the runtime exception was captured
        verify(logger).error(
                startsWith("Encountered failure when sending metrics to HTTP endpoint; uri="),
                any(NullPointerException.class));
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void testHttpClientSupplierException() throws InterruptedException, IOException {
        final org.slf4j.Logger logger = mock(org.slf4j.Logger.class);
        final Semaphore semaphore = new Semaphore(0);
        final HttpSinkEventHandler handler = mock(HttpSinkEventHandler.class);
        final Sink sink = new HttpSink.Builder(
                logger,
                () -> {
                    semaphore.release();
                    throw new IllegalStateException("Test Exception");
                })
                .setHost("nohost.example.com")
                .setPath(PATH)
                .setEventHandler(handler)
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),  // dimensions
                Collections.emptyMap(),  // samples
                Collections.emptyMap()); // aggregates

        sink.record(event);
        semaphore.acquire();

        // Assert that the runtime exception was captured
        verify(logger, timeout(1000)).error(
                startsWith("MetricsHttpSinkWorker failure"),
                any(IllegalStateException.class));

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that no data was sent
        wireMockRule.verify(0, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Verify no handler was invoked
        verify(handler, never()).attemptComplete(
                anyLong(),
                anyLong(),
                anyBoolean(),
                anyLong(),
                any(TimeUnit.class));
    }

    @Test
    public void testClose() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        final Semaphore serverSemaphore = new Semaphore(0);
        final Semaphore clientSemaphore = new Semaphore(0);
        final HttpSinkEventHandler handler = mock(HttpSinkEventHandler.class);

        wireMockRule.stubFor(WireMock.requestMatching(new RequestValueMatcher(record -> {
                    try {
                        serverSemaphore.release();
                        clientSemaphore.acquire();
                    } catch (final InterruptedException e) {
                        // Do nothing
                    }
                }))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)));

        @SuppressWarnings("unchecked")
        final HttpSink sink = (HttpSink) new HttpSink.Builder()
                .setPort(wireMockRule.port())
                .setPath(PATH)
                .setParallelism(1)
                .setEventHandler(
                        new CompletionHandler(
                                semaphore,
                                handler))
                .setRequestTimeout(Duration.ofSeconds(2))
                .setCloseTimeout(Duration.ofSeconds(5))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),
                createQuantityMap("timer", TsdQuantity.newInstance(123)),
                Collections.emptyMap());

        sink.record(event);
        serverSemaphore.acquire();

        final Thread thread = new Thread(() -> {
                while (sink.isOpen()) {
                    try {
                        Thread.sleep(50);
                    } catch (final InterruptedException e) {
                        // Ignore
                    }
                }
                clientSemaphore.release();
            }, "testClose.helper");
        thread.start();

        sink.close();

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // The in-flight message was allowed to complete successfully
        wireMockRule.verify(1, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
        verify(handler).attemptComplete(
                eq(1L),
                anyLong(),
                eq(true),
                anyLong(),
                any(TimeUnit.class));
    }

    @Test
    public void testRecordAfterClose() throws InterruptedException {
        wireMockRule.stubFor(WireMock.post(WireMock.urlEqualTo(PATH))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)));

        final Semaphore semaphore = new Semaphore(0);
        final HttpSinkEventHandler handler = mock(HttpSinkEventHandler.class);
        @SuppressWarnings("unchecked")
        final Sink sink = new HttpSink.Builder()
                .setPort(wireMockRule.port())
                .setPath(PATH)
                .setEventHandler(new CompletionHandler(semaphore, handler))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),
                createQuantityMap("timer", TsdQuantity.newInstance(123)),
                Collections.emptyMap());

        sink.close();
        sink.record(event);
        assertFalse(semaphore.tryAcquire(1, TimeUnit.SECONDS));

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was not received (implying it was not sent)
        wireMockRule.verify(0, requestPattern);
        assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
        verify(handler).droppedEvent(any(Event.class));
    }

    @Test
    public void testCloseForcefully() throws Exception {
        final AtomicInteger droppedEvents = new AtomicInteger(0);
        final Semaphore serverSemaphore = new Semaphore(0);
        final HttpSinkEventHandler handler = mock(HttpSinkEventHandler.class);

        wireMockRule.stubFor(WireMock.requestMatching(new RequestValueMatcher(record -> {
                    try {
                        serverSemaphore.release();
                        Thread.sleep(5000);
                        // Note: this request is intended to timeout
                    } catch (final InterruptedException e) {
                        // Do nothing
                    }
                }))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)));

        @SuppressWarnings("unchecked")
        final HttpSink sink = (HttpSink) new HttpSink.Builder()
                .setPort(wireMockRule.port())
                .setPath(PATH)
                .setParallelism(1)
                .setEventHandler(
                        new EventDroppedAssertionHandler(
                                droppedEvents,
                                new CompletionHandler(
                                        serverSemaphore,
                                        handler)))
                .setRequestTimeout(Duration.ofSeconds(1))
                .setCloseTimeout(Duration.ofMillis(100))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),
                createQuantityMap("timer", TsdQuantity.newInstance(123)),
                Collections.emptyMap());

        sink.record(event);
        serverSemaphore.acquire();
        sink.record(event);
        sink.close();

        // Normally after close the application terminates; here we wait for
        // the result to verify that the request fails.
        assertTrue(serverSemaphore.tryAcquire(2, TimeUnit.SECONDS));

        // Assert that the first request's result was failed on the client side
        verify(handler).attemptComplete(
                eq(1L),
                anyLong(),
                eq(false),
                anyLong(),
                any(TimeUnit.class));

        // Assert the second event was dropped
        assertEquals(1, droppedEvents.get());
    }

    @Test
    public void testCloseInterrupted() throws Exception {
        final AtomicInteger droppedEvents = new AtomicInteger(0);
        final Semaphore serverSemaphore = new Semaphore(0);
        final HttpSinkEventHandler handler = mock(HttpSinkEventHandler.class);

        wireMockRule.stubFor(WireMock.requestMatching(new RequestValueMatcher(record -> {
                    try {
                        serverSemaphore.release();
                        Thread.sleep(5000);
                        // Note: this request is intended to timeout
                    } catch (final InterruptedException e) {
                        // Do nothing
                    }
                }))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)));

        @SuppressWarnings("unchecked")
        final HttpSink sink = (HttpSink) new HttpSink.Builder()
                .setPort(wireMockRule.port())
                .setPath(PATH)
                .setParallelism(1)
                .setEventHandler(
                        new EventDroppedAssertionHandler(
                                droppedEvents,
                                new CompletionHandler(
                                        serverSemaphore,
                                        handler)))
                .setRequestTimeout(Duration.ofSeconds(2))
                .setCloseTimeout(Duration.ofSeconds(5))
                .build();

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                Collections.emptyMap(),
                createQuantityMap("timer", TsdQuantity.newInstance(123)),
                Collections.emptyMap());

        final AtomicBoolean closeInterrupted = new AtomicBoolean(false);
        final Thread thread = new Thread(() -> {
            try {
                sink.close();
            } catch (final InterruptedException e) {
                closeInterrupted.set(true);
            }
        }, "testCloseInterrupted.closer");

        // Submit a request, wait for it to block and then submit a second
        sink.record(event);
        serverSemaphore.acquire();
        sink.record(event);

        // Start async shutdown
        thread.start();

        // Wait for the first request to timeout
        serverSemaphore.acquire();

        // Interrupt and join the shutdown thread
        thread.interrupt();
        thread.join();

        assertTrue(closeInterrupted.get());

        // Normally after close the application terminates; here we wait for
        // the result to verify that the request fails.
        assertTrue(serverSemaphore.tryAcquire(3, TimeUnit.SECONDS));

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that the first request's result was failed on the client side. The interruption
        // voided all data guarantees and there is neither a record of the second request being
        // dropped nor is there a record of it being completed. Also, the server may or may not
        // have received the second request. This is why it's important to shutdown cleanly. However,
        // I suspect not many applications do this. We may want to consider a design that is more
        // resilient to being forced to close.
        verify(handler).attemptComplete(
                eq(1L),
                anyLong(),
                eq(false),
                anyLong(),
                any(TimeUnit.class));
        assertEquals(0, droppedEvents.get());
    }

    @Test
    public void testNoEventHandler() throws InterruptedException {
        wireMockRule.stubFor(WireMock.post(WireMock.urlEqualTo(PATH))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)));

        final Sink sink = new HttpSink.Builder()
                .setPort(wireMockRule.port())
                .setPath(PATH)
                .build();

        final Map<String, String> dimensions = new LinkedHashMap<>();
        dimensions.put("foo", "bar");
        dimensions.put("_host", "some.host.com");
        dimensions.put("_service", "myservice");
        dimensions.put("_cluster", "mycluster");

        final TsdEvent event = new TsdEvent(
                id,
                start,
                end,
                dimensions,
                createQuantityMap(
                        "timer", TsdQuantity.newInstance(123),
                        "counter", TsdQuantity.newInstance(8),
                        "gauge", TsdQuantity.newInstance(10)),
                Collections.emptyMap());

        sink.record(event);
    }

    private static Map<String, List<Quantity>> createQuantityMap(final Object... arguments) {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<String, List<Quantity>> map = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        List<Quantity> samples = null;
        for (final Object argument : arguments) {
            if (argument instanceof String) {
                samples = new ArrayList<>();
                map.put((String) argument, samples);
            } else if (argument instanceof Quantity) {
                if (samples == null) {
                    throw new RuntimeException("First argument must be metric name");
                }
                samples.add((Quantity) argument);
            } else {
                throw new RuntimeException("Unsupported argument type: " + argument.getClass());
            }
        }
        return map;
    }

    private static void assertMetricData(
            final List<ClientV3.MetricDataEntry> metricData,
            final String name,
            final List<Double> samples,
            final ClientV3.AugmentedHistogram augmentedHistogram) {
        assertTrue(
                String.format(
                        "Missing metric data: name=%s, samples=%s, augmentedHistogram=%s",
                        name,
                        samples,
                        augmentedHistogram),
                metricData.contains(
                        ClientV3.MetricDataEntry.newBuilder()
                                .setNumericalData(
                                        ClientV3.NumericalData.newBuilder()
                                                .addAllSamples(samples)
                                                .setStatistics(augmentedHistogram)
                                                .build())
                                .setName(createIdentifier(name))
                                .build()));
    }

    private static void assertMetricData(
            final List<ClientV3.MetricDataEntry> metricData,
            final String name,
            final ClientV3.AugmentedHistogram augmentedHistogram) {
        assertTrue(
                String.format(
                        "Missing metric data: name=%s, augmentedHistogram=%s",
                        name,
                        augmentedHistogram),
                metricData.contains(
                        ClientV3.MetricDataEntry.newBuilder()
                                .setNumericalData(
                                        ClientV3.NumericalData.newBuilder()
                                                .setStatistics(augmentedHistogram)
                                                .build())
                                .setName(createIdentifier(name))
                                .build()));
    }

    private static void assertMetricData(
            final List<ClientV3.MetricDataEntry> metricData,
            final String name,
            final Number sample) {
        assertTrue(
                String.format(
                        "Missing metric data: name=%s, sample=%s",
                        name,
                        sample),
                metricData.contains(
                        ClientV3.MetricDataEntry.newBuilder()
                                .setNumericalData(
                                        ClientV3.NumericalData.newBuilder()
                                                .addSamples(sample.doubleValue())
                                                .build())
                                .setName(createIdentifier(name))
                                .build()));
    }

    private static void assertDimension(
            final List<ClientV3.DimensionEntry> dimensions,
            final String name,
            final String value) {
        assertTrue(dimensions.contains(
                ClientV3.DimensionEntry.newBuilder()
                        .setName(createIdentifier(name))
                        .setValue(createIdentifier(value))
                        .build()));
    }

    private static ClientV3.Identifier createIdentifier(final String value) {
        return ClientV3.Identifier.newBuilder()
                .setStringValue(value)
                .build();
    }

    private static long packHistogramKey(final double value, final int precision) {
        final long truncateMask = 0xfff0000000000000L >> precision;
        final long packMask = (1 << (precision + 52 + 1)) - 1;

        final long truncatedKey = Double.doubleToRawLongBits(value) & truncateMask;
        final long shiftedKey = truncatedKey >> (52 - precision);
        return shiftedKey & packMask;
    }

    private static final class RespectsMaxBufferEventHandler implements HttpSinkEventHandler {
        private final Semaphore semaphoreA;
        private final Semaphore semaphoreB;
        private final Semaphore semaphoreC;
        private final AtomicInteger droppedEvents;

        RespectsMaxBufferEventHandler(
                final Semaphore semaphoreA,
                final Semaphore semaphoreB,
                final Semaphore semaphoreC,
                final AtomicInteger droppedEvents) {
            this.semaphoreA = semaphoreA;
            this.semaphoreB = semaphoreB;
            this.semaphoreC = semaphoreC;
            this.droppedEvents = droppedEvents;
        }

        @Override
        public void attemptComplete(
                final long records,
                final long bytes,
                final boolean success,
                final long elapasedTime,
                final TimeUnit elapsedTimeUnit) {
            if (semaphoreA.availablePermits() == 0) {
                // Initial synchronization request (leave a an extra permit to mark its completion)
                semaphoreA.release(2);
                try {
                    semaphoreB.acquire();
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                // Actual buffered requests
                semaphoreC.release();
            }
        }

        @Override
        public void droppedEvent(final Event event) {
            droppedEvents.incrementAndGet();
        }
    }

    private static final class CompletionHandler implements HttpSinkEventHandler {

        private final Semaphore semaphore;
        private final Optional<HttpSinkEventHandler> nextHandler;

        CompletionHandler(final Semaphore semaphore) {
            this.semaphore = semaphore;
            nextHandler = Optional.empty();
        }

        CompletionHandler(
                final Semaphore semaphore,
                final HttpSinkEventHandler nextHandler) {
            this.semaphore = semaphore;
            this.nextHandler = Optional.of(nextHandler);
        }

        @Override
        public void attemptComplete(
                final long records,
                final long bytes,
                final boolean success,
                final long elapasedTime,
                final TimeUnit elapsedTimeUnit) {
            semaphore.release();
            nextHandler.ifPresent(h -> h.attemptComplete(records, bytes, success, elapasedTime, elapsedTimeUnit));
        }

        @Override
        public void droppedEvent(final Event event) {
            nextHandler.ifPresent(h -> h.droppedEvent(event));
        }
    }

    private static final class AssertionResult {
        private final AtomicBoolean assertionResult;
        private final long expectedRecords;
        private final long expectedBytes;
        private final boolean expectedSuccess;

        AssertionResult(
                final AtomicBoolean assertionResult,
                final long expectedRecords,
                final long expectedBytes,
                final boolean expectedSuccess) {
            this.assertionResult = assertionResult;
            this.expectedRecords = expectedRecords;
            this.expectedBytes = expectedBytes;
            this.expectedSuccess = expectedSuccess;
        }
    }

    private static final class AttemptCompletedAssertionHandler implements HttpSinkEventHandler {

        private final AtomicInteger counter = new AtomicInteger(0);
        private final List<AssertionResult> assertions;
        private final Optional<HttpSinkEventHandler> nextHandler;

        AttemptCompletedAssertionHandler(
                final AtomicBoolean assertionResult,
                final long expectedRecords,
                final long expectedBytes,
                final boolean expectedSuccess) {
            assertions = new ArrayList<>();
            assertions.add(new AssertionResult(
                    assertionResult,
                    expectedRecords,
                    expectedBytes,
                    expectedSuccess));
            nextHandler = Optional.empty();
        }

        AttemptCompletedAssertionHandler(
                final AtomicBoolean assertionResult,
                final long expectedRecords,
                final long expectedBytes,
                final boolean expectedSuccess,
                final HttpSinkEventHandler nextHandler) {
            assertions = new ArrayList<>();
            assertions.add(new AssertionResult(
                    assertionResult,
                    expectedRecords,
                    expectedBytes,
                    expectedSuccess));
            this.nextHandler = Optional.of(nextHandler);
        }

        AttemptCompletedAssertionHandler(final List<AssertionResult> assertions) {
            this.assertions = assertions;
            this.nextHandler = Optional.empty();
        }

        AttemptCompletedAssertionHandler(
                final List<AssertionResult> assertions,
                final HttpSinkEventHandler nextHandler) {
            this.assertions = assertions;
            this.nextHandler = Optional.of(nextHandler);
        }

        @Override
        public synchronized void attemptComplete(
                final long records,
                final long bytes,
                final boolean success,
                final long elapasedTime,
                final TimeUnit elapsedTimeUnit) {
            final AssertionResult assertionResult = assertions.get(counter.getAndIncrement());
            try {
                assertEquals(assertionResult.expectedRecords, records);
                assertEquals(assertionResult.expectedBytes, bytes);
                assertEquals(assertionResult.expectedSuccess, success);
                assertionResult.assertionResult.set(true);
                // CHECKSTYLE.OFF: IllegalCatch - JUnit throws assertions which derive from Throwable
            } catch (final Throwable t) {
                // CHECKSTYLE.ON: IllegalCatch
                assertionResult.assertionResult.set(false);
            }
            nextHandler.ifPresent(h -> h.attemptComplete(records, bytes, success, elapasedTime, elapsedTimeUnit));
        }

        @Override
        public void droppedEvent(final Event event) {
            nextHandler.ifPresent(h -> h.droppedEvent(event));
        }
    }

    private static final class EventDroppedAssertionHandler implements HttpSinkEventHandler {

        private final AtomicInteger droppedCount;
        private final Optional<HttpSinkEventHandler> nextHandler;

        EventDroppedAssertionHandler(final AtomicInteger droppedCount) {
            this.droppedCount = droppedCount;
            nextHandler = Optional.empty();
        }

        EventDroppedAssertionHandler(
                final AtomicInteger droppedCount,
                final HttpSinkEventHandler nextHandler) {
            this.droppedCount = droppedCount;
            this.nextHandler = Optional.of(nextHandler);
        }

        @Override
        public void attemptComplete(
                final long records,
                final long bytes,
                final boolean success,
                final long elapasedTime,
                final TimeUnit elapsedTimeUnit) {
            nextHandler.ifPresent(h -> h.attemptComplete(records, bytes, success, elapasedTime, elapsedTimeUnit));
        }

        @Override
        public void droppedEvent(final Event event) {
            droppedCount.incrementAndGet();
            nextHandler.ifPresent(h -> h.droppedEvent(event));
        }
    }

    private static class RequestValueMatcher implements ValueMatcher<Request> {

        private final Consumer<ClientV3.Record> validator;

        RequestValueMatcher(final Consumer<ClientV3.Record> validator) {
            this.validator = validator;
        }

        @Override
        public MatchResult match(final Request httpRequest) {
            if (!httpRequest.getMethod().equals(RequestMethod.POST)) {
                System.out.println(String.format(
                        "Request method does not match; expect=%s, actual=%s",
                        RequestMethod.POST,
                        httpRequest.getMethod()));
                return MatchResult.noMatch();
            }
            if (!httpRequest.getUrl().equals(PATH)) {
                System.out.println(String.format(
                        "Request uri does not match; expect=%s, actual=%s",
                        PATH,
                        httpRequest.getUrl()));
                return MatchResult.noMatch();
            }
            try {
                final ClientV3.Request request = ClientV3.Request.parseFrom(httpRequest.getBody());
                request.getRecordsList().forEach(validator::accept);
                return MatchResult.of(true);
                // CHECKSTYLE.OFF: IllegalCatch - JUnit throws assertions which derive from Throwable
            } catch (final Throwable t) {
                // CHECKSTYLE.ON: IllegalCatch
                System.out.println("Error parsing body: " + t);
                t.printStackTrace();
                return MatchResult.noMatch();
            }
        }
    }

    private static final class TestUnsupportedAggregatedData implements AggregatedData {
        // Intentionally empty
    }
}

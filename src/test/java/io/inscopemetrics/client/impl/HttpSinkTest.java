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
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

/**
 * Tests for {@link HttpSink}.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class HttpSinkTest {

    private static final String PATH = "/metrics/v1/application";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(
            WireMockConfiguration.wireMockConfig().dynamicPort());

    private final UUID id = UUID.randomUUID();
    private final Instant start = Instant.now().minusMillis(812);
    private final Instant end = Instant.now();

    @Test
    public void testBuilderWithDefaults() {
        final Sink sink = new HttpSink.Builder().build();
        Assert.assertNotNull(sink);
        Assert.assertEquals(HttpSink.class, sink.getClass());
    }

    @Test
    public void testBuilderWithNulls() {
        final HttpSink.Builder builder = new HttpSink.Builder();
        builder.setBufferSize(null);
        builder.setParallelism(null);
        builder.setUri(null);
        builder.setMaxBatchSize(null);
        builder.setEmptyQueueInterval(null);
        builder.setDispatchErrorLoggingInterval(null);
        builder.setEventsDroppedLoggingInteval(null);
        builder.setUnsupportedDataLoggingInterval(null);
        builder.setEmptyQueueInterval(null);
        final Sink sink = builder.build();
        Assert.assertNotNull(sink);
        Assert.assertEquals(HttpSink.class, sink.getClass());
    }

    @Test
    public void testBuilderWithHttpsUri() {
        final HttpSink.Builder builder = new HttpSink.Builder();
        builder.setUri(URI.create("https://secure.example.com"));
        final Sink sink = builder.build();
        Assert.assertNotNull(sink);
        Assert.assertEquals(HttpSink.class, sink.getClass());
    }

    @Test
    public void testBuilderWithNotHttpUri() {
        final HttpSink.Builder builder = new HttpSink.Builder();
        builder.setUri(URI.create("ftp://ftp.example.com"));
        final Sink sink = builder.build();
        Assert.assertNotNull(sink);
        Assert.assertNotEquals(HttpSink.class, sink.getClass());
    }

    // CHECKSTYLE.OFF: MethodLength
    @Test
    public void testPostSuccess() throws InterruptedException {
        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            // Dimensions
                            Assert.assertEquals(4, r.getDimensionsCount());
                            assertDimension(r.getDimensionsList(), "foo", "bar");
                            assertDimension(r.getDimensionsList(), "_host", "some.host.com");
                            assertDimension(r.getDimensionsList(), "_service", "myservice");
                            assertDimension(r.getDimensionsList(), "_cluster", "mycluster");

                            // Samples
                            Assert.assertEquals(6, r.getDataOrBuilderList().size());
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
                .setUri(URI.create("http://localhost:" + wireMockRule.port() + PATH))
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
        Assert.assertTrue(assertionResult.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(1, requestPattern);
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
    }
    // CHECKSTYLE.ON: MethodLength

    @Test
    public void testAugmentedHistogram() throws InterruptedException {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<Double, Integer> histogram = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        histogram.put(1.0, 1);
        histogram.put(2.0, 2);
        histogram.put(3.0, 3);

        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            // Dimensions
                            Assert.assertEquals(0, r.getDimensionsCount());

                            // Samples
                            Assert.assertEquals(2, r.getDataOrBuilderList().size());
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
        final org.slf4j.Logger logger = Mockito.mock(org.slf4j.Logger.class);
        final Sink sink = new HttpSink(
                new HttpSink.Builder()
                        .setUri(URI.create("http://localhost:" + wireMockRule.port() + PATH))
                        .setEventHandler(
                                new AttemptCompletedAssertionHandler(
                                        assertionResult,
                                        1,
                                        143,
                                        true,
                                        new CompletionHandler(semaphore))),
                logger);

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
        Assert.assertTrue(assertionResult.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(1, requestPattern);
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
    }

    @Test
    public void testAugmentedHistogramAndSamplesMerged() throws InterruptedException {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<Double, Integer> histogram = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        histogram.put(1.0, 1);
        histogram.put(2.0, 2);
        histogram.put(3.0, 3);

        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            // Dimensions
                            Assert.assertEquals(0, r.getDimensionsCount());

                            // Samples
                            Assert.assertEquals(1, r.getDataOrBuilderList().size());
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
        final org.slf4j.Logger logger = Mockito.mock(org.slf4j.Logger.class);
        final Sink sink = new HttpSink(
                new HttpSink.Builder()
                        .setUri(URI.create("http://localhost:" + wireMockRule.port() + PATH))
                        .setEventHandler(
                                new AttemptCompletedAssertionHandler(
                                        assertionResult,
                                        1,
                                        117,
                                        true,
                                        new CompletionHandler(semaphore))),
                logger);

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
        Assert.assertTrue(assertionResult.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(1, requestPattern);
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
    }

    @Test
    public void testUnsupportedAggregateData() throws InterruptedException {
        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            // Dimensions
                            Assert.assertEquals(0, r.getDimensionsCount());

                            // Samples
                            Assert.assertEquals(2, r.getDataOrBuilderList().size());
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
        final org.slf4j.Logger logger = Mockito.mock(org.slf4j.Logger.class);
        final Sink sink = new HttpSink(
                new HttpSink.Builder()
                        .setUri(URI.create("http://localhost:" + wireMockRule.port() + PATH))
                        .setEventHandler(
                                new AttemptCompletedAssertionHandler(
                                        assertionResult,
                                        1,
                                        104,
                                        true,
                                        new CompletionHandler(semaphore))),
                logger);

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
        Assert.assertTrue(assertionResult.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(1, requestPattern);
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Verify that
        Mockito.verify(logger).error(
                Mockito.startsWith(
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
                            Assert.assertEquals(0, r.getDimensionsCount());

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
                .setUri(URI.create("http://localhost:" + wireMockRule.port() + PATH))
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
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
    }

    @Test
    public void testBatchesRequests() throws InterruptedException {
        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            // Dimensions
                            Assert.assertEquals(0, r.getDimensionsCount());

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

        final AtomicBoolean assertionResult = new AtomicBoolean(false);
        final Semaphore semaphore = new Semaphore(0);
        final Sink sink = new HttpSink.Builder()
                .setUri(URI.create("http://localhost:" + wireMockRule.port() + PATH))
                .setMaxBatchSize(10)
                .setParallelism(1)
                .setEmptyQueueInterval(Duration.ofMillis(1000))
                .setEventHandler(
                        new AttemptCompletedAssertionHandler(
                                assertionResult,
                                3,
                                315,
                                true,
                                new CompletionHandler(
                                        semaphore)))
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

        for (int x = 0; x < 3; x++) {
            sink.record(event);
        }
        semaphore.acquire();

        // Ensure expected handler was invoked
        Assert.assertTrue(assertionResult.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(1, requestPattern);
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
    }

    @Test
    public void testBatchesRequestsRespectsMax() throws InterruptedException {
        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            // Dimensions
                            Assert.assertEquals(0, r.getDimensionsCount());

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

        final Semaphore semaphore = new Semaphore(-2);
        final Sink sink = new HttpSink.Builder()
                .setUri(URI.create("http://localhost:" + wireMockRule.port() + PATH))
                .setMaxBatchSize(2)
                .setParallelism(1)
                .setEmptyQueueInterval(Duration.ofMillis(1000))
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

        for (int x = 0; x < 5; x++) {
            sink.record(event);
        }
        semaphore.acquire();

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(3, requestPattern);
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
    }

    @Test
    public void testRespectsBufferMax() throws InterruptedException {
        final AtomicInteger droppedEvents = new AtomicInteger(0);
        final Semaphore semaphoreA = new Semaphore(0);
        final Semaphore semaphoreB = new Semaphore(0);
        final Semaphore semaphoreC = new Semaphore(-2);
        final AtomicInteger recordsReceived = new AtomicInteger(0);

        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            recordsReceived.incrementAndGet();

                            // Dimensions
                            Assert.assertEquals(0, r.getDimensionsCount());

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

        final Sink sink = new HttpSink.Builder()
                .setUri(URI.create("http://localhost:" + wireMockRule.port() + PATH))
                .setMaxBatchSize(2)
                .setParallelism(1)
                .setBufferSize(5)
                .setEmptyQueueInterval(Duration.ofMillis(1000))
                .setEventHandler(new RespectsMaxBufferEventHandler(semaphoreA, semaphoreB, semaphoreC, droppedEvents))
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

        // Add one event to be used as a synchronization point
        sink.record(event);
        semaphoreA.acquire();

        // Add the actual events to analyze
        for (int x = 0; x < 10; x++) {
            sink.record(event);
        }
        semaphoreB.release();
        semaphoreC.acquire();

        // Ensure expected handler was invoked
        Assert.assertEquals(5, droppedEvents.get());

        // Assert number of records received
        Assert.assertEquals(6, recordsReceived.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(4, requestPattern);
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
    }

    @Test
    public void testEndpointNotAvailable() throws InterruptedException {
        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            // Dimensions
                            Assert.assertEquals(0, r.getDimensionsCount());

                            // Samples
                            Assert.assertEquals(0, r.getDataList().size());
                        }))
                        .willReturn(WireMock.aResponse()
                                .withStatus(404)));

        final AtomicBoolean assertionResult = new AtomicBoolean(false);
        final Semaphore semaphore = new Semaphore(0);
        final org.slf4j.Logger logger = Mockito.mock(org.slf4j.Logger.class);
        final Sink sink = new HttpSink(
                new HttpSink.Builder()
                        .setUri(URI.create("http://localhost:" + wireMockRule.port() + PATH))
                        .setEventHandler(
                                new AttemptCompletedAssertionHandler(
                                        assertionResult,
                                        1,
                                        34,
                                        false,
                                        new CompletionHandler(
                                                semaphore))),
                logger);

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
        Assert.assertTrue(assertionResult.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(1, requestPattern);
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Assert that an IOException was captured
        Mockito.verify(logger).error(
                Mockito.startsWith("Encountered failure when sending metrics to HTTP endpoint; uri="),
                Mockito.any(RuntimeException.class));
    }

    @Test
    public void testPostFailure() throws InterruptedException {
        wireMockRule.stubFor(
                WireMock.requestMatching(new RequestValueMatcher(
                        r -> {
                            // Dimensions
                            Assert.assertEquals(0, r.getDimensionsCount());

                            // Samples
                            Assert.assertEquals(0, r.getDataList().size());
                        }))
                        .willReturn(WireMock.aResponse()
                                .withStatus(400)));

        final AtomicBoolean assertionResult = new AtomicBoolean(false);
        final Semaphore semaphore = new Semaphore(0);
        final org.slf4j.Logger logger = Mockito.mock(org.slf4j.Logger.class);
        final Sink sink = new HttpSink(
                new HttpSink.Builder()
                        .setUri(URI.create("http://localhost:" + wireMockRule.port() + PATH))
                        .setEventHandler(
                                new AttemptCompletedAssertionHandler(
                                        assertionResult,
                                        1,
                                        34,
                                        false,
                                        new CompletionHandler(
                                                semaphore))),
                logger);

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
        Assert.assertTrue(assertionResult.get());

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(1, requestPattern);
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Assert that an IOException was captured
        Mockito.verify(logger).error(
                Mockito.startsWith("Received failure response when sending metrics to HTTP endpoint; uri="));
    }

    @Test
    public void testPostBadHost() throws InterruptedException {
        final org.slf4j.Logger logger = Mockito.mock(org.slf4j.Logger.class);
        final Semaphore semaphore = new Semaphore(0);
        final Sink sink = new HttpSink(
                new HttpSink.Builder()
                        .setUri(URI.create("http://nohost.example.com" + PATH))
                        .setEventHandler(new CompletionHandler(semaphore)),
                logger);

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
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Assert that an IOException was captured
        Mockito.verify(logger).error(
                Mockito.startsWith("Encountered failure when sending metrics to HTTP endpoint; uri="),
                Mockito.any(IOException.class));
    }

    @Test
    public void testHttpClientExecuteException() throws InterruptedException {
        final CloseableHttpClient httpClient = Mockito.mock(
                CloseableHttpClient.class,
                invocationOnMock -> {
                    throw new NullPointerException("Throw by default");
                });

        final org.slf4j.Logger logger = Mockito.mock(org.slf4j.Logger.class);
        final Semaphore semaphore = new Semaphore(0);
        final Sink sink = new HttpSink(
                new HttpSink.Builder()
                        .setUri(URI.create("http://nohost.example.com" + PATH))
                        .setEventHandler(new CompletionHandler(semaphore)),
                () -> httpClient,
                logger);

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
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Assert that the runtime exception was captured
        Mockito.verify(logger).error(
                Mockito.startsWith("Encountered failure when sending metrics to HTTP endpoint; uri="),
                Mockito.any(NullPointerException.class));
    }

    @Test
    public void testHttpClientResponseException() throws InterruptedException, IOException {
        final CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        final CloseableHttpResponse httpResponse = Mockito.mock(CloseableHttpResponse.class);
        Mockito.doReturn(httpResponse).when(httpClient).execute(Mockito.any(HttpPost.class));
        Mockito.doThrow(new NullPointerException("Throw by default")).when(httpResponse).getStatusLine();

        final org.slf4j.Logger logger = Mockito.mock(org.slf4j.Logger.class);
        final Semaphore semaphore = new Semaphore(0);
        final Sink sink = new HttpSink(
                new HttpSink.Builder()
                        .setUri(URI.create("http://nohost.example.com" + PATH))
                        .setEventHandler(new CompletionHandler(semaphore)),
                () -> httpClient,
                logger);

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
        Mockito.verify(httpClient).execute(Mockito.any(HttpPost.class));
        Mockito.verifyNoMoreInteractions(httpClient);
        Mockito.verify(httpResponse).getStatusLine();
        Mockito.verify(httpResponse).close();
        Mockito.verifyNoMoreInteractions(httpResponse);

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that no data was sent
        wireMockRule.verify(0, requestPattern);
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Assert that the runtime exception was captured
        Mockito.verify(logger).error(
                Mockito.startsWith("Encountered failure when sending metrics to HTTP endpoint; uri="),
                Mockito.any(NullPointerException.class));
    }

    @Test
    public void testHttpClientResponseCloseException() throws InterruptedException, IOException {
        final CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        final CloseableHttpResponse httpResponse = Mockito.mock(CloseableHttpResponse.class);
        Mockito.doReturn(httpResponse).when(httpClient).execute(Mockito.any(HttpPost.class));
        Mockito.doThrow(new NullPointerException("Throw by default")).when(httpResponse).getStatusLine();
        Mockito.doThrow(new IllegalStateException("Throw by default")).when(httpResponse).close();

        final org.slf4j.Logger logger = Mockito.mock(org.slf4j.Logger.class);
        final Semaphore semaphore = new Semaphore(0);
        final Sink sink = new HttpSink(
                new HttpSink.Builder()
                        .setUri(URI.create("http://nohost.example.com" + PATH))
                        .setEventHandler(new CompletionHandler(semaphore)),
                () -> httpClient,
                logger);

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
        Mockito.verify(httpClient).execute(Mockito.any(HttpPost.class));
        Mockito.verifyNoMoreInteractions(httpClient);
        Mockito.verify(httpResponse).getStatusLine();
        Mockito.verify(httpResponse).close();
        Mockito.verifyNoMoreInteractions(httpResponse);

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that no data was sent
        wireMockRule.verify(0, requestPattern);
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Assert that the runtime exception was captured
        Mockito.verify(logger).error(
                Mockito.startsWith("Encountered failure when sending metrics to HTTP endpoint; uri="),
                Mockito.any(NullPointerException.class));
        Mockito.verifyNoMoreInteractions(logger);
    }

    @Test
    public void testHttpClientSupplierException() throws InterruptedException, IOException {
        final org.slf4j.Logger logger = Mockito.mock(org.slf4j.Logger.class);
        final Semaphore semaphore = new Semaphore(0);
        final HttpSinkEventHandler handler = Mockito.mock(HttpSinkEventHandler.class);
        final Sink sink = new HttpSink(
                new HttpSink.Builder()
                        .setUri(URI.create("http://nohost.example.com" + PATH))
                        .setEventHandler(handler),
                () -> {
                    semaphore.release();
                    throw new IllegalStateException("Test Exception");
                },
                logger);

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
        Mockito.verify(logger, Mockito.timeout(1000)).error(
                Mockito.startsWith("MetricsSinkApacheHttpWorker failure"),
                Mockito.any(IllegalStateException.class));

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that no data was sent
        wireMockRule.verify(0, requestPattern);
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());

        // Verify no handler was invoked
        Mockito.verify(handler, Mockito.never()).attemptComplete(
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.anyBoolean(),
                Mockito.anyLong(),
                Mockito.any(TimeUnit.class));
    }

    @Test
    public void testStop() throws InterruptedException {
        wireMockRule.stubFor(WireMock.post(WireMock.urlEqualTo(PATH))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)));

        final Semaphore semaphore = new Semaphore(0);
        @SuppressWarnings("unchecked")
        final HttpSink sink = (HttpSink) new HttpSink.Builder()
                .setUri(URI.create("http://localhost:" + wireMockRule.port() + PATH))
                .setEventHandler(new CompletionHandler(semaphore))
                .build();

        final Map<String, String> dimensions = new LinkedHashMap<>();
        dimensions.put("foo", "bar");
        dimensions.put("_start", Instant.now().minusMillis(812).atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT));
        dimensions.put("_end", Instant.now().atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT));
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

        sink.stop();
        Thread.sleep(1000);
        sink.record(event);
        Assert.assertFalse(semaphore.tryAcquire(1, TimeUnit.SECONDS));

        // Request matcher
        final RequestPatternBuilder requestPattern = WireMock.postRequestedFor(WireMock.urlEqualTo(PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/octet-stream"));

        // Assert that data was sent
        wireMockRule.verify(0, requestPattern);
        Assert.assertTrue(wireMockRule.findUnmatchedRequests().getRequests().isEmpty());
    }

    @Test
    public void testNoEventHandler() throws InterruptedException {
        wireMockRule.stubFor(WireMock.post(WireMock.urlEqualTo(PATH))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)));

        final Sink sink = new HttpSink.Builder()
                .setUri(URI.create("http://localhost:" + wireMockRule.port() + PATH))
                .build();

        final Map<String, String> dimensions = new LinkedHashMap<>();
        dimensions.put("foo", "bar");
        dimensions.put("_start", Instant.now().minusMillis(812).atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT));
        dimensions.put("_end", Instant.now().atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT));
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

    @Test
    public void testSafeSleep() throws InterruptedException {
        final AtomicBoolean value = new AtomicBoolean(false);
        final Thread thread = new Thread(() -> {
            // Value will not be set if safe sleep throws or is not interrupted.
            HttpSink.safeSleep(500);
            value.set(true);
        });

        thread.start();
        Thread.sleep(100);
        thread.interrupt();
        thread.join(600);

        Assert.assertFalse(thread.isAlive());
        Assert.assertTrue(value.get());
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
        Assert.assertTrue(
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
        Assert.assertTrue(
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
        Assert.assertTrue(
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
        Assert.assertTrue(dimensions.contains(
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

    private static final class AttemptCompletedAssertionHandler implements HttpSinkEventHandler {

        private final AtomicBoolean assertionResult;
        private final long expectedRecords;
        private final long expectedBytes;
        private final boolean expectedSuccess;
        private final Optional<HttpSinkEventHandler> nextHandler;

        AttemptCompletedAssertionHandler(
                final AtomicBoolean assertionResult,
                final long expectedRecords,
                final long expectedBytes,
                final boolean expectedSuccess) {
            this.assertionResult = assertionResult;
            this.expectedRecords = expectedRecords;
            this.expectedBytes = expectedBytes;
            this.expectedSuccess = expectedSuccess;
            nextHandler = Optional.empty();
        }

        AttemptCompletedAssertionHandler(
                final AtomicBoolean assertionResult,
                final long expectedRecords,
                final long expectedBytes,
                final boolean expectedSuccess,
                final HttpSinkEventHandler nextHandler) {
            this.assertionResult = assertionResult;
            this.expectedRecords = expectedRecords;
            this.expectedBytes = expectedBytes;
            this.expectedSuccess = expectedSuccess;
            this.nextHandler = Optional.of(nextHandler);
        }

        @Override
        public void attemptComplete(
                final long records,
                final long bytes,
                final boolean success,
                final long elapasedTime,
                final TimeUnit elapsedTimeUnit) {
            try {
                Assert.assertEquals(expectedRecords, records);
                Assert.assertEquals(expectedBytes, bytes);
                Assert.assertEquals(expectedSuccess, success);
                assertionResult.set(true);
                // CHECKSTYLE.OFF: IllegalCatch - JUnit throws assertions which derive from Throwable
            } catch (final Throwable t) {
                // CHECKSTYLE.ON: IllegalCatch
                assertionResult.set(false);
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

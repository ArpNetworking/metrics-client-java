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

import io.inscopemetrics.client.AggregatedData;
import io.inscopemetrics.client.Counter;
import io.inscopemetrics.client.Event;
import io.inscopemetrics.client.Metrics;
import io.inscopemetrics.client.Sink;
import io.inscopemetrics.client.Timer;
import io.inscopemetrics.client.test.MetricMatcher;
import io.inscopemetrics.client.test.QuantityMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.hamcrest.MockitoHamcrest;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TsdMetrics}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
@RunWith(Parameterized.class)
public final class TsdMetricsCommonTest {

    private MetricsProxy metricsProxy;

    public TsdMetricsCommonTest(final MetricsProxy metricsCreator) {
        metricsProxy = metricsCreator;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> getParameters() {
        return Arrays.asList(
                new Object[][] {
                        {new TsdMetricsProxy()},
                        {new LockFreeMetricsProxy()}
                });
    }

    @Test
    public void testEmptySingleSink() {
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final UUID id = UUID.randomUUID();
        final Instant before = Instant.now();
        final Metrics metrics = metricsProxy.createMetrics(id, sink);
        metrics.close();
        final Instant after = Instant.now();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertEquals(id, actualEvent.getId());
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertTrue(actualEvent.getSamples().isEmpty());
        assertTimestamps(before, after, actualEvent);
    }

    @Test
    public void testEmptyMultipleSinks() {
        final Sink sink1 = mock(Sink.class, "TsdMetricsCommonTest.testEmptyMultipleSinks.sink1");
        final Sink sink2 = mock(Sink.class, "TsdMetricsCommonTest.testEmptyMultipleSinks.sink2");
        final UUID id = UUID.randomUUID();
        final Instant before = Instant.now();
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(id, sink1, sink2);
        metrics.close();
        final Instant after = Instant.now();

        final ArgumentCaptor<Event> eventCapture1 = ArgumentCaptor.forClass(Event.class);
        verify(sink1).record(eventCapture1.capture());
        final Event actualEvent1 = eventCapture1.getValue();
        assertEquals(id, actualEvent1.getId());
        assertThat(
                actualEvent1.getDimensions(),
                standardDimensionsMatcher());
        assertTrue(actualEvent1.getSamples().isEmpty());
        assertTimestamps(before, after, actualEvent1);

        final ArgumentCaptor<Event> eventCapture2 = ArgumentCaptor.forClass(Event.class);
        verify(sink2).record(eventCapture2.capture());
        final Event actualEvent2 = eventCapture2.getValue();
        assertEquals(id, actualEvent2.getId());
        assertThat(
                actualEvent2.getDimensions(),
                standardDimensionsMatcher());
        assertTrue(actualEvent2.getSamples().isEmpty());
        assertTimestamps(before, after, actualEvent2);

        assertEquals(actualEvent1, actualEvent2);
    }

    @Test
    public void testCounterOnly() {
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);
        metrics.incrementCounter("counter");
        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match(
                        "counter",
                        QuantityMatcher.match(1)));
    }

    @Test
    public void testTimerOnly() {
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);
        metrics.setTimer("timer", 1L, TimeUnit.MILLISECONDS);
        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match(
                        "timer",
                        QuantityMatcher.match(0.001, 0.00001)));
    }

    @Test
    public void testGaugeOnly() {
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);
        metrics.setGauge("gauge", 1.23);
        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match(
                        "gauge",
                        QuantityMatcher.match(1.23, 0.001)));
    }

    @Test
    public void testAggregatedDataOnly() {
        final Sink sink = mock(Sink.class);
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<Double, Long> histogram = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        double sum = 0.0;
        for (long i = 1; i < 10; ++i) {
            histogram.put(AugmentedHistogramTest.toKey((double) i), i);
            sum += i * i;
        }
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);
         metricsProxy.recordAggregatedData(
                 metrics,
                 "aggregatedMetric",
                 new AugmentedHistogram.Builder()
                         .setHistogram(histogram)
                         .setPrecision(7)
                         .setMinimum(1.0)
                         .setMaximum(10.0)
                         .setSum(sum)
                         .build());
        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertTrue(actualEvent.getSamples().isEmpty());
        assertEquals(
                actualEvent.getAggregatedData(),
                Collections.singletonMap(
                        "aggregatedMetric",
                        new AugmentedHistogram.Builder()
                            .setHistogram(histogram)
                            .setPrecision(7)
                            .setMinimum(1.0)
                            .setMaximum(10.0)
                            .setSum(sum)
                            .build()));
    }

    @Test
    public void testAggregatedDataDuplicate() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<Double, Long> histogram = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        double sum = 0.0;
        for (long i = 1; i < 10; ++i) {
            histogram.put(AugmentedHistogramTest.toKey((double) i), i);
            sum += i * i;
        }
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
         metricsProxy.recordAggregatedData(
                 metrics,
                 "aggregatedMetric",
                 new AugmentedHistogram.Builder()
                         .setHistogram(histogram)
                         .setPrecision(7)
                         .setMinimum(1.0)
                         .setMaximum(10.0)
                         .setSum(sum)
                         .build());
         metricsProxy.recordAggregatedData(
                 metrics,
                 "aggregatedMetric",
                 new AugmentedHistogram.Builder()
                         .setHistogram(histogram)
                         .setPrecision(7)
                         .setMinimum(2.0)
                         .setMaximum(20.0)
                         .setSum(sum)
                         .build());
        metrics.close();

        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertTrue(actualEvent.getSamples().isEmpty());
        assertEquals(
                actualEvent.getAggregatedData(),
                Collections.singletonMap(
                        "aggregatedMetric",
                        new AugmentedHistogram.Builder()
                                .setHistogram(histogram)
                                .setPrecision(7)
                                .setMinimum(1.0)
                                .setMaximum(10.0)
                                .setSum(sum)
                                .build()));
    }

    @Test
    public void testTimerCounterGauge() {
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);
        metrics.incrementCounter("counter");
        metrics.setTimer("timer", 1L, TimeUnit.MILLISECONDS);
        metrics.setGauge("gauge", 1.23);
        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match(
                        "timer",
                        QuantityMatcher.match(0.001, 0.00001),
                        "counter",
                        QuantityMatcher.match(1),
                        "gauge",
                        QuantityMatcher.match(1.23, 0.001)));
    }

    @Test
    public void testTimerCounterGaugeSameName() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.incrementCounter("badName");
        metrics.setTimer("badName", 1L, TimeUnit.MILLISECONDS);
        metrics.setGauge("badName", 1.23);
        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match(
                        "badName",
                        QuantityMatcher.match(0.001, 0.00001),
                        QuantityMatcher.match(1),
                        QuantityMatcher.match(1.23, 0.001)));

        verify(logger, atLeastOnce()).warn(
                startsWith("Metric recorded as two or more of counter, timer, gauge; name=badName"));
    }

    @Test
    public void testIsOpen() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        assertTrue(metrics.isOpen());
        metrics.close();
        assertFalse(metrics.isOpen());
    }

    @Test
    public void testCreateCounterNotOpen() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.close();
        final Counter counter = metrics.createCounter("counter-closed");
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
        assertNotNull(counter);
    }

    @Test
    public void testIncrementCounterNotOpen() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.close();
        metrics.incrementCounter("counter-closed");
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testResetCounterNotOpen() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.close();
        metrics.resetCounter("counter-closed");
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testSetGaugeDoubleNotOpen() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.close();
        metrics.setGauge("gauge-closed", 1.23);
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testSetGaugeLongNotOpen() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.close();
        metrics.setGauge("gauge-closed", 10L);
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testCreateTimerNotOpen() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.close();
        final Timer timer = metrics.createTimer("timer-closed");
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
        assertNotNull(timer);
    }

    @Test
    public void testSetTimerNotOpenTimeUnit() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.close();
        metrics.setTimer("timer-closed", 1L, TimeUnit.MILLISECONDS);
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testStartTimerNotOpen() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.close();
        metrics.startTimer("timer-closed");
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testStopTimerNotOpen() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.close();
        metrics.stopTimer("timer-closed");
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testAddDimensionNotOpen() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.close();
        metrics.addDimension("key", "value");
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testAddDimensionsNotOpen() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.close();
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<String, String> annotations = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        annotations.put("key1", "value1");
        annotations.put("key2", "value2");
        metrics.addDimensions(annotations);
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testCloseNotOpen() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.close();
        verifyNoInteractions(logger);
        metrics.close();
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testAggregatedDataNotOpen() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<Double, Long> histogram = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        double sum = 0.0;
        for (long i = 1; i < 10; ++i) {
            histogram.put(AugmentedHistogramTest.toKey((double) i), i);
            sum += i * i;
        }
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.close();
         metricsProxy.recordAggregatedData(
                 metrics,
                 "aggregatedMetric",
                 new AugmentedHistogram.Builder()
                         .setHistogram(histogram)
                         .setPrecision(7)
                         .setMinimum(1.0)
                         .setMaximum(10.0)
                         .setSum(sum)
                         .build());

        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testCloseSinkThrows() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        doThrow(new NullPointerException("Test exception")).when(sink).record(any(Event.class));
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.close();
        verify(sink).record(any(Event.class));
        verifyNoMoreInteractions(sink);
        verify(logger).warn(
                startsWith("Metrics sink failed to record; sink="),
                any(NullPointerException.class));
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void testStartTimerAlreadyStarted() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.startTimer("timer-already-started");
        metrics.startTimer("timer-already-started");
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testStopTimerNotStarted() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.stopTimer("timer-not-started");
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testStopTimerAlreadyStopped() {
        final org.slf4j.Logger logger = metricsProxy.createSlf4jLoggerMock();
        final Sink sink = mock(Sink.class);
        final Metrics metrics = metricsProxy.createMetrics(logger, sink);
        metrics.startTimer("timer-already-stopped");
        metrics.stopTimer("timer-already-stopped");
        verifyNoInteractions(logger);
        metrics.stopTimer("timer-already-stopped");
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testCloseTryWithResource() {
        final Sink sink = mock(Sink.class);
        try (Metrics metrics = metricsProxy.createMetrics(sink)) {
            metrics.incrementCounter("testCloseTryWithResource");
        }

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match(
                        "testCloseTryWithResource",
                        QuantityMatcher.match(1)));
    }

    @Test
    public void testTimerMetrics() throws InterruptedException {
        final Sink sink = mock(Sink.class);
        final Instant earliestStartDate = Instant.now();
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);

        metrics.setTimer("timerA", 100L, TimeUnit.MILLISECONDS);
        metrics.startTimer("timerB");
        metrics.stopTimer("timerB");
        metrics.startTimer("timerC");
        metrics.stopTimer("timerC");
        metrics.startTimer("timerC");
        metrics.stopTimer("timerC");
        metrics.startTimer("timerD");
        metrics.stopTimer("timerD");
        metrics.setTimer("timerD", 1L, TimeUnit.MILLISECONDS);

        Thread.sleep(10);
        metrics.close();
        final Instant latestEndDate = Instant.now();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertTimestamps(earliestStartDate, latestEndDate, actualEvent);
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match(
                        "timerA",
                        QuantityMatcher.match(0.1, 0.001),
                        "timerB",
                        QuantityMatcher.match(Matchers.any(Number.class)),
                        "timerC",
                        QuantityMatcher.match(Matchers.any(Number.class)),
                        QuantityMatcher.match(Matchers.any(Number.class)),
                        "timerD",
                        QuantityMatcher.match(Matchers.any(Number.class)),
                        QuantityMatcher.match(0.001, 0.00001)));
    }

    @Test
    public void testCounterMetrics() throws InterruptedException {
        final Sink sink = mock(Sink.class);
        final Instant earliestStartDate = Instant.now();
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);

        metrics.incrementCounter("counterA");
        metrics.incrementCounter("counterB", 2L);
        metrics.decrementCounter("counterC");
        metrics.decrementCounter("counterD", 2L);
        metrics.resetCounter("counterE");
        metrics.resetCounter("counterF");
        metrics.resetCounter("counterF");
        metrics.incrementCounter("counterF");
        metrics.resetCounter("counterF");
        metrics.incrementCounter("counterF");
        metrics.incrementCounter("counterF");

        Thread.sleep(10);
        metrics.close();
        final Instant latestEndDate = Instant.now();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertTimestamps(earliestStartDate, latestEndDate, actualEvent);
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match(
                        "counterA",
                        QuantityMatcher.match(1),
                        "counterB",
                        QuantityMatcher.match(2),
                        "counterC",
                        QuantityMatcher.match(-1),
                        "counterD",
                        QuantityMatcher.match(-2),
                        "counterE",
                        QuantityMatcher.match(0),
                        "counterF",
                        QuantityMatcher.match(0),
                        QuantityMatcher.match(1),
                        QuantityMatcher.match(2)));
    }

    @Test
    public void testGaugeMetrics() throws InterruptedException {
        final Sink sink = mock(Sink.class);
        final Instant earliestStartDate = Instant.now();
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);

        metrics.setGauge("gaugeA", 10L);
        metrics.setGauge("gaugeB", 1.23);
        metrics.setGauge("gaugeC", 10L);
        metrics.setGauge("gaugeC", 20L);
        metrics.setGauge("gaugeD", 2.07);
        metrics.setGauge("gaugeD", 1.23);

        Thread.sleep(10);
        metrics.close();
        final Instant latestEndDate = Instant.now();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertTimestamps(earliestStartDate, latestEndDate, actualEvent);
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match(
                        "gaugeA",
                        QuantityMatcher.match(10),
                        "gaugeB",
                        QuantityMatcher.match(1.23, 0.001),
                        "gaugeC",
                        QuantityMatcher.match(10),
                        QuantityMatcher.match(20),
                        "gaugeD",
                        QuantityMatcher.match(2.07, 0.001),
                        QuantityMatcher.match(1.23, 0.001)));
    }

    @Test
    public void testAddDimensionMetrics() throws InterruptedException {
        final Sink sink = mock(Sink.class);
        final Instant earliestStartDate = Instant.now();
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);

        metrics.addDimension("foo", "bar");
        metrics.addDimension("dup", "cat");
        metrics.addDimension("dup", "dog");

        Thread.sleep(10);
        metrics.close();
        final Instant latestEndDate = Instant.now();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher(
                        Matchers.hasEntry("foo", "bar"),
                        Matchers.hasEntry("dup", "dog")));
        assertTimestamps(earliestStartDate, latestEndDate, actualEvent);
        assertTrue(actualEvent.getSamples().isEmpty());
    }

    @Test
    public void testAddDimensionsMetrics() throws InterruptedException {
        final Sink sink = mock(Sink.class);
        final Instant earliestStartDate = Instant.now();
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);

        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<String, String> dimensions = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        dimensions.put("foo", "bar");
        dimensions.put("dup", "dog");
        metrics.addDimensions(dimensions);

        Thread.sleep(10);
        metrics.close();
        final Instant latestEndDate = Instant.now();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher(
                        Matchers.hasEntry("foo", "bar"),
                        Matchers.hasEntry("dup", "dog")));
        assertTimestamps(earliestStartDate, latestEndDate, actualEvent);
        assertTrue(actualEvent.getSamples().isEmpty());
    }

    @Test
    public void testTimerUnits() {
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);

        metrics.setTimer("withTimeUnit", 11L, TimeUnit.NANOSECONDS);
        metrics.setTimer("withTimeUnit", 12L, TimeUnit.MICROSECONDS);
        metrics.setTimer("withTimeUnit", 13L, TimeUnit.MILLISECONDS);
        metrics.setTimer("withTimeUnit", 14L, TimeUnit.SECONDS);
        metrics.setTimer("withTimeUnit", 15L, TimeUnit.MINUTES);
        metrics.setTimer("withTimeUnit", 16L, TimeUnit.HOURS);
        metrics.setTimer("withTimeUnit", 17L, TimeUnit.DAYS);

        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match(
                        "withTimeUnit",
                        QuantityMatcher.match(0.000000011, 0.0000000001),
                        QuantityMatcher.match(0.000012, 0.0000000001),
                        QuantityMatcher.match(0.013, 0.0000000001),
                        QuantityMatcher.match(14.0, 0.0000000001),
                        QuantityMatcher.match(900.0, 0.0000000001),
                        QuantityMatcher.match(57600.0, 0.0000000001),
                        QuantityMatcher.match(1468800.0, 0.0000000001)));
    }

    @Test
    public void testTimerObjects() throws InterruptedException {
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);
        @SuppressWarnings("resource")
        final Timer timerObjectA = metrics.createTimer("timerObjectA");
        @SuppressWarnings("resource")
        final Timer timerObjectB1 = metrics.createTimer("timerObjectB");
        @SuppressWarnings("resource")
        final Timer timerObjectB2 = metrics.createTimer("timerObjectB");

        Thread.sleep(1);

        timerObjectA.close();
        timerObjectB2.close();

        Thread.sleep(1);

        timerObjectB1.close();
        metrics.close();

        // Important: The samples for timerObjectB are recorded in the order the
        // two timer objects are instantiated and not the order in which they
        // are stopped/closed.

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match(
                        "timerObjectA",
                        QuantityMatcher.match(Matchers.greaterThanOrEqualTo(0.001)),
                        "timerObjectB",
                        QuantityMatcher.match(Matchers.greaterThanOrEqualTo(0.002)),
                        QuantityMatcher.match(Matchers.greaterThanOrEqualTo(0.001))));
    }

    @Test
    public void testSkipUnclosedTimerSample() {
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);
        metrics.createTimer("timerObjectA");
        metrics.setTimer("timerObjectA", 1, TimeUnit.SECONDS);

        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match(
                        "timerObjectA",
                        QuantityMatcher.match(1.0, 0.001)));
    }

    @Test
    public void testTimerWithoutClosedSample() {
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);
        metrics.createTimer("timerObjectB");
        metrics.setTimer("timerObjectA", 1, TimeUnit.SECONDS);

        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match(
                        "timerObjectA",
                        QuantityMatcher.match(1.0, 0.001),
                        "timerObjectB"));
    }

    @Test
    public void testOnlyTimersWithoutClosedSample() {
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);
        metrics.createTimer("timerObjectB");

        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match("timerObjectB"));
    }

    @Test
    public void testSkipAbortedTimerSample() {
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);
        final Timer timer = metrics.createTimer("timerObjectA");
        metrics.setTimer("timerObjectA", 1, TimeUnit.SECONDS);
        timer.abort();

        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match(
                        "timerObjectA",
                        QuantityMatcher.match(1.0, 0.001)));
    }

    @Test
    public void testTimerWithoutUnabortedSample() {
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);
        final Timer timer = metrics.createTimer("timerObjectB");
        metrics.setTimer("timerObjectA", 1, TimeUnit.SECONDS);
        timer.abort();

        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match(
                        "timerObjectA",
                        QuantityMatcher.match(1.0, 0.001),
                        "timerObjectB"));
    }

    @Test
    public void testOnlyTimersWithoutUnabortedSample() {
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(sink);
        final Timer timer = metrics.createTimer("timerObjectB");
        timer.abort();

        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        assertThat(
                actualEvent.getDimensions(),
                standardDimensionsMatcher());
        assertThat(
                actualEvent.getSamples(),
                MetricMatcher.match("timerObjectB"));
    }

    @Test
    public void testGetOpenAndCloseTime() {
        final Clock clock = mock(Clock.class);
        final Instant start = Instant.now();
        final Instant end = start.plusMillis(1000);
        when(clock.instant()).thenReturn(start, end);

        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsProxy.createMetrics(clock, metricsProxy.createSlf4jLoggerMock(), UUID.randomUUID(), sink);
        assertEquals(start, metrics.getOpenTime());
        assertNull(metrics.getCloseTime());

        metrics.close();
        assertEquals(end, metrics.getCloseTime());
    }

    @Test
    public void testToString() {
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final String asString = metricsProxy.createMetrics(sink).toString();
        assertNotNull(asString);
        assertFalse(asString.isEmpty());
    }

    private void assertTimestamps(
            final Instant earliestStartDate,
            final Instant latestEndDate,
            final Event event) {

        final Instant actualStart = event.getStartTime();
        assertFalse(earliestStartDate.isAfter(actualStart));

        final Instant actualEnd = event.getEndTime();
        assertFalse(latestEndDate.isBefore(actualEnd));
    }

    @SuppressWarnings(value = {"unchecked", "rawtypes"})
    private static Matcher<Map<String, String>> standardDimensionsMatcher(
            final Matcher... additionalMatchers) {
        final List matchers = new ArrayList();
        matchers.add(Matchers.hasEntry("host", "MyHost"));
        matchers.add(Matchers.hasEntry("service", "MyService"));
        matchers.add(Matchers.hasEntry("cluster", "MyCluster"));
        matchers.addAll(Arrays.asList(additionalMatchers));
        return Matchers.allOf(matchers);
    }

    private interface MetricsProxy {
        default Metrics createMetrics(final Sink... sinks) {
            return createMetrics(createSlf4jLoggerMock(), sinks);
        }

        default Metrics createMetrics(final UUID id, final Sink... sinks) {
            return createMetrics(Clock.systemUTC(), createSlf4jLoggerMock(), id, sinks);
        }

        default Metrics createMetrics(final org.slf4j.Logger logger, final Sink... sinks) {
            return createMetrics(Clock.systemUTC(), logger, UUID.randomUUID(), sinks);
        }

        default org.slf4j.Logger createSlf4jLoggerMock() {
            return mock(org.slf4j.Logger.class);
        }

        void recordAggregatedData(Metrics metrics, String metricName, AggregatedData data);

        Metrics createMetrics(
                Clock clock,
                org.slf4j.Logger logger,
                UUID id,
                Sink... sinks);
    }

    private static final class TsdMetricsProxy implements MetricsProxy {

        @Override
        public Metrics createMetrics(
                final Clock clock,
                final org.slf4j.Logger logger,
                final UUID id,
                final Sink... sinks) {
            final Metrics metrics = new TsdMetrics(
                    id,
                    Arrays.asList(sinks),
                    clock,
                    logger);
            metrics.addDimension("host", "MyHost");
            metrics.addDimension("service", "MyService");
            metrics.addDimension("cluster", "MyCluster");
            return metrics;
        }

        @Override
        public void recordAggregatedData(final Metrics metrics, final String metricName, final AggregatedData data) {
            assertTrue(metrics instanceof TsdMetrics);
            ((TsdMetrics) metrics).recordAggregatedData(metricName, data);
        }
    }

    private static final class LockFreeMetricsProxy implements MetricsProxy {

        @Override
        public Metrics createMetrics(
                final Clock clock,
                final org.slf4j.Logger logger,
                final UUID id,
                final Sink... sinks) {
            final Metrics metrics = new LockFreeMetrics(
                    id,
                    Arrays.asList(sinks),
                    clock,
                    logger);
            metrics.addDimension("host", "MyHost");
            metrics.addDimension("service", "MyService");
            metrics.addDimension("cluster", "MyCluster");
            return metrics;
        }

        @Override
        public void recordAggregatedData(final Metrics metrics, final String metricName, final AggregatedData data) {
            assertTrue(metrics instanceof LockFreeMetrics);
            ((LockFreeMetrics) metrics).recordAggregatedData(metricName, data);
        }
    }
}

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

import com.arpnetworking.metrics.Counter;
import com.arpnetworking.metrics.Event;
import com.arpnetworking.metrics.Sink;
import com.arpnetworking.metrics.Timer;
import com.arpnetworking.metrics.Units;
import com.arpnetworking.metrics.test.MetricMatcher;
import com.arpnetworking.metrics.test.QuantityMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.hamcrest.MockitoHamcrest;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Tests for <code>TsdMetrics</code>.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@SuppressWarnings("deprecation")  // We still want to test deprecated methods
public class TsdMetricsTest {

    @Test
    public void testEmptySingleSink() {
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);
        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher());
        Assert.assertTrue(actualEvent.getTimerSamples().isEmpty());
        Assert.assertTrue(actualEvent.getCounterSamples().isEmpty());
        Assert.assertTrue(actualEvent.getGaugeSamples().isEmpty());
    }

    @Test
    public void testEmptyMultipleSinks() {
        final Sink sink1 = Mockito.mock(Sink.class, "TsdMetricsTest.testEmptyMultipleSinks.sink1");
        final Sink sink2 = Mockito.mock(Sink.class, "TsdMetricsTest.testEmptyMultipleSinks.sink2");
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink1, sink2);
        metrics.close();

        final ArgumentCaptor<Event> eventCapture1 = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink1).record(eventCapture1.capture());
        final Event actualEvent1 = eventCapture1.getValue();
        Assert.assertThat(
                actualEvent1.getAnnotations(),
                standardAnnotationsMatcher());
        Assert.assertTrue(actualEvent1.getTimerSamples().isEmpty());
        Assert.assertTrue(actualEvent1.getCounterSamples().isEmpty());
        Assert.assertTrue(actualEvent1.getGaugeSamples().isEmpty());

        final ArgumentCaptor<Event> eventCapture2 = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink2).record(eventCapture2.capture());
        final Event actualEvent2 = eventCapture2.getValue();
        Assert.assertThat(
                actualEvent2.getAnnotations(),
                standardAnnotationsMatcher());
        Assert.assertTrue(actualEvent2.getTimerSamples().isEmpty());
        Assert.assertTrue(actualEvent2.getCounterSamples().isEmpty());
        Assert.assertTrue(actualEvent2.getGaugeSamples().isEmpty());

        Assert.assertEquals(actualEvent1, actualEvent2);
    }

    @Test
    public void testCounterOnly() {
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);
        metrics.incrementCounter("counter");
        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher());
        Assert.assertTrue(actualEvent.getTimerSamples().isEmpty());
        Assert.assertThat(
                actualEvent.getCounterSamples(),
                MetricMatcher.match(
                        "counter",
                        QuantityMatcher.match(1)));
        Assert.assertTrue(actualEvent.getGaugeSamples().isEmpty());
    }

    @Test
    public void testTimerOnly() {
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);
        metrics.setTimer("timer", 1L, TimeUnit.MILLISECONDS);
        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher());
        Assert.assertThat(
                actualEvent.getTimerSamples(),
                MetricMatcher.match(
                        "timer",
                        QuantityMatcher.match(1, Units.MILLISECOND)));
        Assert.assertTrue(actualEvent.getCounterSamples().isEmpty());
        Assert.assertTrue(actualEvent.getGaugeSamples().isEmpty());
    }

    @Test
    public void testGaugeOnly() {
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);
        metrics.setGauge("gauge", 1.23);
        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher());
        Assert.assertTrue(actualEvent.getTimerSamples().isEmpty());
        Assert.assertTrue(actualEvent.getCounterSamples().isEmpty());
        Assert.assertThat(
                actualEvent.getGaugeSamples(),
                MetricMatcher.match(
                        "gauge",
                        QuantityMatcher.match(1.23, null)));
    }

    @Test
    public void testTimerCounterGauge() {
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);
        metrics.incrementCounter("counter");
        metrics.setTimer("timer", 1L, TimeUnit.MILLISECONDS);
        metrics.setGauge("gauge", 1.23);
        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher());
        Assert.assertThat(
                actualEvent.getTimerSamples(),
                MetricMatcher.match(
                        "timer",
                        QuantityMatcher.match(1L, Units.MILLISECOND)));
        Assert.assertThat(
                actualEvent.getCounterSamples(),
                MetricMatcher.match(
                        "counter",
                        QuantityMatcher.match(1)));
        Assert.assertThat(
                actualEvent.getGaugeSamples(),
                MetricMatcher.match(
                        "gauge",
                        QuantityMatcher.match(1.23, null)));
    }

    @Test
    public void testIsOpen() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        Assert.assertTrue(metrics.isOpen());
        metrics.close();
        Assert.assertFalse(metrics.isOpen());
    }

    @Test
    public void testCreateCounterNotOpen() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.close();
        final Counter counter = metrics.createCounter("counter-closed");
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
        Assert.assertNotNull(counter);
    }

    @Test
    public void testIncrementCounterNotOpen() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.close();
        metrics.incrementCounter("counter-closed");
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testResetCounterNotOpen() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.close();
        metrics.resetCounter("counter-closed");
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testSetGaugeDoubleNotOpen() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.close();
        metrics.setGauge("gauge-closed", 1.23);
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testSetGaugeLongNotOpen() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.close();
        metrics.setGauge("gauge-closed", 10L);
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testCreateTimerNotOpen() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.close();
        final Timer timer = metrics.createTimer("timer-closed");
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
        Assert.assertNotNull(timer);
    }

    @Test
    public void testSetTimerNotOpenTimeUnit() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.close();
        metrics.setTimer("timer-closed", 1L, TimeUnit.MILLISECONDS);
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testStartTimerNotOpen() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.close();
        metrics.startTimer("timer-closed");
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testStopTimerNotOpen() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.close();
        metrics.stopTimer("timer-closed");
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testAddAnnotationNotOpen() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.close();
        metrics.addAnnotation("key", "value");
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testAddAnnotationsNotOpen() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.close();
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<String, String> annotations = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        annotations.put("key1", "value1");
        annotations.put("key2", "value2");
        metrics.addAnnotations(annotations);
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testCloseNotOpen() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.close();
        Mockito.verifyZeroInteractions(logger);
        metrics.close();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testCloseSinkThrows() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        Mockito.doThrow(new NullPointerException("Test exception")).when(sink).record(Mockito.any(Event.class));
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.close();
        Mockito.verify(sink).record(Mockito.any(Event.class));
        Mockito.verifyNoMoreInteractions(sink);
        Mockito.verify(logger).warn(
                Mockito.startsWith("Metrics sink failed to record; sink="),
                Mockito.any(NullPointerException.class));
        Mockito.verifyNoMoreInteractions(logger);
    }

    @Test
    public void testStartTimerAlreadyStarted() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.startTimer("timer-already-started");
        metrics.startTimer("timer-already-started");
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testStopTimerNotStarted() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.stopTimer("timer-not-started");
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testStopTimerAlreadyStopped() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final Sink sink = Mockito.mock(Sink.class);
        final TsdMetrics metrics = createTsdMetrics(logger, sink);
        metrics.startTimer("timer-already-stopped");
        metrics.stopTimer("timer-already-stopped");
        Mockito.verifyZeroInteractions(logger);
        metrics.stopTimer("timer-already-stopped");
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testCloseTryWithResource() {
        final Sink sink = Mockito.mock(Sink.class);
        try (TsdMetrics metrics = createTsdMetrics(sink)) {
            metrics.incrementCounter("testCloseTryWithResource");
        }

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher());
        Assert.assertTrue(actualEvent.getTimerSamples().isEmpty());
        Assert.assertThat(
                actualEvent.getCounterSamples(),
                MetricMatcher.match(
                        "testCloseTryWithResource",
                        QuantityMatcher.match(1)));
        Assert.assertTrue(actualEvent.getGaugeSamples().isEmpty());
    }

    @Test
    public void testTimerMetrics() throws ParseException, InterruptedException {
        final Sink sink = Mockito.mock(Sink.class);
        final Date earliestStartDate = new Date();
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);

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
        final Date latestEndDate = new Date();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher());
        assertTimestamps(earliestStartDate, latestEndDate, actualEvent.getAnnotations());
        Assert.assertThat(
                actualEvent.getTimerSamples(),
                MetricMatcher.match(
                        "timerA",
                        QuantityMatcher.match(100, Units.MILLISECOND),
                        "timerB",
                        QuantityMatcher.match(Matchers.any(Number.class), Units.NANOSECOND),
                        "timerC",
                        QuantityMatcher.match(Matchers.any(Number.class), Units.NANOSECOND),
                        QuantityMatcher.match(Matchers.any(Number.class), Units.NANOSECOND),
                        "timerD",
                        QuantityMatcher.match(Matchers.any(Number.class), Units.NANOSECOND),
                        QuantityMatcher.match(1, Units.MILLISECOND)));
        Assert.assertTrue(actualEvent.getCounterSamples().isEmpty());
        Assert.assertTrue(actualEvent.getGaugeSamples().isEmpty());
    }

    @Test
    public void testCounterMetrics() throws ParseException, InterruptedException {
        final Sink sink = Mockito.mock(Sink.class);
        final Date earliestStartDate = new Date();
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);

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
        final Date latestEndDate = new Date();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher());
        assertTimestamps(earliestStartDate, latestEndDate, actualEvent.getAnnotations());
        Assert.assertTrue(actualEvent.getTimerSamples().isEmpty());
        Assert.assertThat(
                actualEvent.getCounterSamples(),
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
        Assert.assertTrue(actualEvent.getGaugeSamples().isEmpty());
    }

    @Test
    public void testGaugeMetrics() throws ParseException, InterruptedException {
        final Sink sink = Mockito.mock(Sink.class);
        final Date earliestStartDate = new Date();
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);

        metrics.setGauge("gaugeA", 10L);
        metrics.setGauge("gaugeB", 1.23);
        metrics.setGauge("gaugeC", 10L);
        metrics.setGauge("gaugeC", 20L);
        metrics.setGauge("gaugeD", 2.07);
        metrics.setGauge("gaugeD", 1.23);

        Thread.sleep(10);
        metrics.close();
        final Date latestEndDate = new Date();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher());
        assertTimestamps(earliestStartDate, latestEndDate, actualEvent.getAnnotations());
        Assert.assertTrue(actualEvent.getTimerSamples().isEmpty());
        Assert.assertTrue(actualEvent.getCounterSamples().isEmpty());
        Assert.assertThat(
                actualEvent.getGaugeSamples(),
                MetricMatcher.match(
                        "gaugeA",
                        QuantityMatcher.match(10),
                        "gaugeB",
                        QuantityMatcher.match(1.23),
                        "gaugeC",
                        QuantityMatcher.match(10),
                        QuantityMatcher.match(20),
                        "gaugeD",
                        QuantityMatcher.match(2.07),
                        QuantityMatcher.match(1.23)));
    }

    @Test
    public void testAddAnnotationMetrics() throws ParseException, InterruptedException {
        final Sink sink = Mockito.mock(Sink.class);
        final Date earliestStartDate = new Date();
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);

        metrics.addAnnotation("foo", "bar");
        metrics.addAnnotation("dup", "cat");
        metrics.addAnnotation("dup", "dog");

        Thread.sleep(10);
        metrics.close();
        final Date latestEndDate = new Date();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher(
                        Matchers.hasEntry("foo", "bar"),
                        Matchers.hasEntry("dup", "dog")));
        assertTimestamps(earliestStartDate, latestEndDate, actualEvent.getAnnotations());
        Assert.assertTrue(actualEvent.getTimerSamples().isEmpty());
        Assert.assertTrue(actualEvent.getCounterSamples().isEmpty());
        Assert.assertTrue(actualEvent.getGaugeSamples().isEmpty());
    }

    @Test
    public void testAddAnnotationsMetrics() throws ParseException, InterruptedException {
        final Sink sink = Mockito.mock(Sink.class);
        final Date earliestStartDate = new Date();
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);

        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<String, String> annotations = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        annotations.put("foo", "bar");
        annotations.put("dup", "dog");
        metrics.addAnnotations(annotations);

        Thread.sleep(10);
        metrics.close();
        final Date latestEndDate = new Date();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher(
                        Matchers.hasEntry("foo", "bar"),
                        Matchers.hasEntry("dup", "dog")));
        assertTimestamps(earliestStartDate, latestEndDate, actualEvent.getAnnotations());
        Assert.assertTrue(actualEvent.getTimerSamples().isEmpty());
        Assert.assertTrue(actualEvent.getCounterSamples().isEmpty());
        Assert.assertTrue(actualEvent.getGaugeSamples().isEmpty());
    }

    @Test
    public void testUnits() {
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);

        metrics.setGauge("bySize", 21L, Units.BYTE);
        metrics.setGauge("bySize", 22L, Units.KILOBYTE);
        metrics.setGauge("bySize", 23L, Units.MEGABYTE);
        metrics.setGauge("bySize", 24L, Units.GIGABYTE);

        // You should never do this but the library cannot prevent it because
        // values are combined across instances, processes and hosts:
        metrics.setGauge("mixedUnit", 1.23, Units.BYTE);
        metrics.setGauge("mixedUnit", 2.07, Units.SECOND);

        metrics.setTimer("withTimeUnit", 11L, TimeUnit.NANOSECONDS);
        metrics.setTimer("withTimeUnit", 12L, TimeUnit.MICROSECONDS);
        metrics.setTimer("withTimeUnit", 13L, TimeUnit.MILLISECONDS);
        metrics.setTimer("withTimeUnit", 14L, TimeUnit.SECONDS);
        metrics.setTimer("withTimeUnit", 15L, TimeUnit.MINUTES);
        metrics.setTimer("withTimeUnit", 16L, TimeUnit.HOURS);
        metrics.setTimer("withTimeUnit", 17L, TimeUnit.DAYS);

        metrics.setTimer("withTsdUnit", 1L, Units.NANOSECOND);
        metrics.setTimer("withTsdUnit", 2L, Units.MICROSECOND);
        metrics.setTimer("withTsdUnit", 3L, Units.MILLISECOND);
        metrics.setTimer("withTsdUnit", 4L, Units.SECOND);
        metrics.setTimer("withTsdUnit", 5L, Units.MINUTE);
        metrics.setTimer("withTsdUnit", 6L, Units.HOUR);
        metrics.setTimer("withTsdUnit", 7L, Units.DAY);

        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher());
        Assert.assertThat(
                actualEvent.getTimerSamples(),
                MetricMatcher.match(
                        "withTimeUnit",
                        QuantityMatcher.match(11, Units.NANOSECOND),
                        QuantityMatcher.match(12, Units.MICROSECOND),
                        QuantityMatcher.match(13, Units.MILLISECOND),
                        QuantityMatcher.match(14, Units.SECOND),
                        QuantityMatcher.match(15, Units.MINUTE),
                        QuantityMatcher.match(16, Units.HOUR),
                        QuantityMatcher.match(17, Units.DAY),
                        "withTsdUnit",
                        QuantityMatcher.match(1, Units.NANOSECOND),
                        QuantityMatcher.match(2, Units.MICROSECOND),
                        QuantityMatcher.match(3, Units.MILLISECOND),
                        QuantityMatcher.match(4, Units.SECOND),
                        QuantityMatcher.match(5, Units.MINUTE),
                        QuantityMatcher.match(6, Units.HOUR),
                        QuantityMatcher.match(7, Units.DAY)));
        Assert.assertTrue(actualEvent.getCounterSamples().isEmpty());
        Assert.assertThat(
                actualEvent.getGaugeSamples(),
                MetricMatcher.match(
                        "bySize",
                        QuantityMatcher.match(21, Units.BYTE),
                        QuantityMatcher.match(22, Units.KILOBYTE),
                        QuantityMatcher.match(23, Units.MEGABYTE),
                        QuantityMatcher.match(24, Units.GIGABYTE),
                        "mixedUnit",
                        QuantityMatcher.match(1.23, Units.BYTE),
                        QuantityMatcher.match(2.07, Units.SECOND)));
    }

    @Test
    public void testTimerObjects() throws InterruptedException {
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);
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
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher());
        Assert.assertThat(
                actualEvent.getTimerSamples(),
                MetricMatcher.match(
                        "timerObjectA",
                        QuantityMatcher.match(Matchers.greaterThanOrEqualTo(1L), Units.NANOSECOND),
                        "timerObjectB",
                        QuantityMatcher.match(Matchers.greaterThanOrEqualTo(2L), Units.NANOSECOND),
                        QuantityMatcher.match(Matchers.greaterThanOrEqualTo(1L), Units.NANOSECOND)));
        Assert.assertTrue(actualEvent.getCounterSamples().isEmpty());
        Assert.assertTrue(actualEvent.getGaugeSamples().isEmpty());
    }

    @Test
    public void testSkipUnclosedTimerSample() {
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);
        metrics.createTimer("timerObjectA");
        metrics.setTimer("timerObjectA", 1, TimeUnit.SECONDS);

        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher());
        Assert.assertThat(
                actualEvent.getTimerSamples(),
                MetricMatcher.match(
                        "timerObjectA",
                        QuantityMatcher.match(1, Units.SECOND)));
        Assert.assertTrue(actualEvent.getCounterSamples().isEmpty());
        Assert.assertTrue(actualEvent.getGaugeSamples().isEmpty());
    }

    @Test
    public void testTimerWithoutClosedSample() {
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);
        metrics.createTimer("timerObjectB");
        metrics.setTimer("timerObjectA", 1, TimeUnit.SECONDS);

        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher());
        Assert.assertThat(
                actualEvent.getTimerSamples(),
                MetricMatcher.match(
                        "timerObjectA",
                        QuantityMatcher.match(1, Units.SECOND),
                        "timerObjectB"));
        Assert.assertTrue(actualEvent.getGaugeSamples().isEmpty());
    }

    @Test
    public void testOnlyTimersWithoutClosedSample() {
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);
        metrics.createTimer("timerObjectB");

        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                Matchers.allOf(
                        Matchers.hasKey("_start"),
                        Matchers.hasKey("_end")));
        Assert.assertThat(
                actualEvent.getTimerSamples(),
                MetricMatcher.match("timerObjectB"));
        Assert.assertTrue(actualEvent.getGaugeSamples().isEmpty());
    }

    @Test
    public void testSkipAbortedTimerSample() {
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);
        final Timer timer = metrics.createTimer("timerObjectA");
        metrics.setTimer("timerObjectA", 1, TimeUnit.SECONDS);
        timer.abort();

        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher());
        Assert.assertThat(
                actualEvent.getTimerSamples(),
                MetricMatcher.match(
                        "timerObjectA",
                        QuantityMatcher.match(1, Units.SECOND)));
        Assert.assertTrue(actualEvent.getCounterSamples().isEmpty());
        Assert.assertTrue(actualEvent.getGaugeSamples().isEmpty());
    }

    @Test
    public void testTimerWithoutUnabortedSample() {
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);
        final Timer timer = metrics.createTimer("timerObjectB");
        metrics.setTimer("timerObjectA", 1, TimeUnit.SECONDS);
        timer.abort();

        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                standardAnnotationsMatcher());
        Assert.assertThat(
                actualEvent.getTimerSamples(),
                MetricMatcher.match(
                        "timerObjectA",
                        QuantityMatcher.match(1, Units.SECOND),
                        "timerObjectB"));
        Assert.assertTrue(actualEvent.getGaugeSamples().isEmpty());
    }

    @Test
    public void testOnlyTimersWithoutUnabortedSample() {
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);
        final Timer timer = metrics.createTimer("timerObjectB");
        timer.abort();

        metrics.close();

        final ArgumentCaptor<Event> eventCapture = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(sink).record(eventCapture.capture());
        final Event actualEvent = eventCapture.getValue();
        Assert.assertThat(
                actualEvent.getAnnotations(),
                Matchers.allOf(
                        Matchers.hasKey("_start"),
                        Matchers.hasKey("_end")));
        Assert.assertThat(
                actualEvent.getTimerSamples(),
                MetricMatcher.match("timerObjectB"));
        Assert.assertTrue(actualEvent.getGaugeSamples().isEmpty());
    }

    @Test
    public void testGetOpenAndCloseTime() {
        final Clock clock = Mockito.mock(Clock.class);
        final Instant start = Instant.now();
        final Instant end = start.plusMillis(1000);
        Mockito.when(clock.instant()).thenReturn(start, end);

        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(clock, createSlf4jLoggerMock(), sink);
        Assert.assertEquals(start, metrics.getOpenTime());
        Assert.assertNull(metrics.getCloseTime());

        metrics.close();
        Assert.assertEquals(end, metrics.getCloseTime());
    }

    @Test
    public void testGetOrCreate() {
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final TsdMetrics metrics = createTsdMetrics(sink);

        final ConcurrentMap<String, String> map = new ConcurrentHashMap<>();
        Assert.assertEquals("bar", metrics.getOrCreate(map, "foo", "bar"));
        Assert.assertEquals("bar", metrics.getOrCreate(map, "foo", "who"));
    }

    @Test
    public void testToString() {
        final Sink sink = Mockito.mock(Sink.class);
        @SuppressWarnings("resource")
        final String asString = createTsdMetrics(sink).toString();
        Assert.assertNotNull(asString);
        Assert.assertFalse(asString.isEmpty());
    }

    private TsdMetrics createTsdMetrics(final Sink... sinks) {
        return createTsdMetrics(createSlf4jLoggerMock(), sinks);
    }

    private TsdMetrics createTsdMetrics(final org.slf4j.Logger logger, final Sink... sinks) {
        return createTsdMetrics(Clock.systemUTC(), logger, sinks);
    }

    private TsdMetrics createTsdMetrics(final Clock clock, final org.slf4j.Logger logger, final Sink... sinks) {
        return new TsdMetrics(
                UUID.randomUUID(),
                "MyService",
                "MyCluster",
                "MyHost",
                Arrays.asList(sinks),
                clock,
                logger);
    }

    private org.slf4j.Logger createSlf4jLoggerMock() {
        return Mockito.mock(org.slf4j.Logger.class);
    }

    private void assertTimestamps(
            final Date earliestStartDate,
            final Date latestEndDate,
            final Map<String, String> annotations)
            throws ParseException {

        Assert.assertTrue(annotations.containsKey("_start"));
        final Date actualStart = _iso8601Format.parse(annotations.get("_start"));
        Assert.assertTrue(earliestStartDate.getTime() <= actualStart.getTime());
        Assert.assertTrue(latestEndDate.getTime() >= actualStart.getTime());

        Assert.assertTrue(annotations.containsKey("_end"));
        final Date actualEnd = _iso8601Format.parse(annotations.get("_end"));
        Assert.assertTrue(latestEndDate.getTime() >= actualEnd.getTime());
        Assert.assertTrue(earliestStartDate.getTime() <= actualEnd.getTime());
    }

    @SuppressWarnings(value = {"unchecked", "rawtypes"})
    private static Matcher<Map<String, String>> standardAnnotationsMatcher(
            final Matcher... additionalMatchers) {
        final List matchers = new ArrayList();
        matchers.add(Matchers.hasKey("_id"));
        matchers.add(Matchers.hasEntry("_host", "MyHost"));
        matchers.add(Matchers.hasEntry("_service", "MyService"));
        matchers.add(Matchers.hasEntry("_cluster", "MyCluster"));
        matchers.add(Matchers.hasKey("_start"));
        matchers.add(Matchers.hasKey("_end"));
        matchers.addAll(Arrays.asList(additionalMatchers));
        return Matchers.allOf(matchers);
    }

    // NOTE: SimpleDateFormat is not thread safe thus it is non-static
    private final SimpleDateFormat _iso8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXX");
}

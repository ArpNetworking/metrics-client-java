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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.inscopemetrics.client.ScopedMetrics;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests for {@link WarningScopedMetrics}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class WarningScopedMetricsTest {

    private static final String TEST_MESSAGE = "Test Message";

    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public final MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Mock
    private Logger logger;

    private ScopedMetrics metrics;

    @Before
    public void setUp() {
        metrics = new WarningScopedMetrics(Clock.systemUTC(), logger, TEST_MESSAGE);
    }

    @Test
    public void testCreateCounter() {
        assertNotNull(metrics.createCounter("aCounter"));
        verify(logger).warn(contains(TEST_MESSAGE));
        assertTrue(metrics.createCounter("aCounter") instanceof NoOpCounter);
        verify(logger, times(2)).warn(contains(TEST_MESSAGE));
        assertNotSame(metrics.createCounter("aCounter"), metrics.createCounter("aCounter"));
        verify(logger, times(4)).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testIncrementCounter() {
        metrics.incrementCounter("aCounter");
        verify(logger).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testIncrementCounterByValue() {
        metrics.incrementCounter("aCounter", 2);
        verify(logger).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testDecrementCounter() {
        metrics.decrementCounter("aCounter");
        verify(logger).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testDecrementCounterByValue() {
        metrics.decrementCounter("aCounter", 2);
        verify(logger).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testResetCounter() {
        metrics.resetCounter("aCounter");
        verify(logger).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testCreateTimer() {
        assertNotNull(metrics.createTimer("aTimer"));
        verify(logger).warn(contains(TEST_MESSAGE));
        assertTrue(metrics.createTimer("aTimer") instanceof NoOpTimer);
        verify(logger, times(2)).warn(contains(TEST_MESSAGE));
        assertNotSame(metrics.createTimer("aTimer"), metrics.createTimer("aTimer"));
        verify(logger, times(4)).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testStartTimer() {
        metrics.startTimer("aTimer");
        verify(logger).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testStopTimer() {
        metrics.stopTimer("aTimer");
        verify(logger).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testRecordCounter() {
        metrics.recordCounter("aCounter", 1);
        verify(logger).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testRecordTimerTimeUnit() {
        metrics.recordTimer("aTimer", 1, TimeUnit.SECONDS);
        verify(logger).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testRecordGaugeDouble() {
        metrics.recordGauge("aGauge", 1.23d);
        verify(logger).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testRecordGaugeLong() {
        metrics.recordGauge("aGauge", 123L);
        verify(logger).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testAddDimension() {
        metrics.addDimension("foo", "bar");
        verify(logger).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testAddDimensions() {
        metrics.addDimensions(Collections.singletonMap("foo", "bar"));
        verify(logger).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testClose() {
        assertTrue(metrics.isOpen());
        verify(logger).warn(contains(TEST_MESSAGE));
        metrics.close();
        verify(logger, times(2)).warn(contains(TEST_MESSAGE));
        assertFalse(metrics.isOpen());
        verify(logger, times(3)).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testTimestamps() throws InterruptedException {
        final Instant now = Clock.systemUTC().instant();
        final ScopedMetrics scopedMetrics = new WarningScopedMetrics(Clock.systemUTC(), logger, TEST_MESSAGE);
        assertNotNull(scopedMetrics.getOpenTime());
        assertNull(scopedMetrics.getCloseTime());
        assertTrue(scopedMetrics.getOpenTime().compareTo(now) >= 0);
        Thread.sleep(100);
        scopedMetrics.close();
        final Instant afterClose = Clock.systemUTC().instant();
        assertFalse(scopedMetrics.isOpen());
        assertNotNull(scopedMetrics.getCloseTime());
        final Instant closeTime = scopedMetrics.getCloseTime();
        assertTrue(closeTime.compareTo(now) > 0);
        assertTrue(closeTime.compareTo(afterClose) <= 0);
        verify(logger, times(7)).warn(contains(TEST_MESSAGE));
    }

    @Test
    public void testToString() {
        final String asString = metrics.toString();
        assertNotNull(asString);
        assertFalse(asString.isEmpty());
        assertThat(asString, Matchers.containsString("WarningScopedMetrics"));
        verifyNoInteractions(logger);
    }
}

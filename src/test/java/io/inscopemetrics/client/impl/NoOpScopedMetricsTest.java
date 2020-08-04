/*
 * Copyright 2017 Inscope Metrics, Inc.
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

import io.inscopemetrics.client.ScopedMetrics;
import org.hamcrest.Matchers;
import org.junit.Test;

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

/**
 * Tests for {@link NoOpScopedMetrics}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class NoOpScopedMetricsTest {

    private final Instant now = Clock.systemUTC().instant();
    private final ScopedMetrics metrics = new NoOpScopedMetrics();

    @Test
    public void testCreateCounter() {
        assertNotNull(metrics.createCounter("aCounter"));
        assertTrue(metrics.createCounter("aCounter") instanceof NoOpCounter);
        assertNotSame(metrics.createCounter("aCounter"), metrics.createCounter("aCounter"));
    }

    @Test
    public void testIncrementCounter() {
        metrics.incrementCounter("aCounter");
        // Does not throw.
    }

    @Test
    public void testIncrementCounterByValue() {
        metrics.incrementCounter("aCounter", 2);
        // Does not throw.
    }

    @Test
    public void testDecrementCounter() {
        metrics.decrementCounter("aCounter");
        // Does not throw.
    }

    @Test
    public void testDecrementCounterByValue() {
        metrics.decrementCounter("aCounter", 2);
        // Does not throw.
    }

    @Test
    public void testResetCounter() {
        metrics.resetCounter("aCounter");
        // Does not throw.
    }

    @Test
    public void testCreateTimer() {
        assertNotNull(metrics.createTimer("aTimer"));
        assertTrue(metrics.createTimer("aTimer") instanceof NoOpTimer);
        assertNotSame(metrics.createTimer("aTimer"), metrics.createTimer("aTimer"));
    }

    @Test
    public void testStartTimer() {
        metrics.startTimer("aTimer");
        // Does not throw.
    }

    @Test
    public void testStopTimer() {
        metrics.stopTimer("aTimer");
        // Does not throw.
    }

    @Test
    public void testRecordCounter() {
        metrics.recordCounter("aCounter", 1);
        // Does not throw.
    }

    @Test
    public void testRecordTimerTimeUnit() {
        metrics.recordTimer("aTimer", 1, TimeUnit.SECONDS);
        // Does not throw.
    }

    @Test
    public void testRecordGaugeDouble() {
        metrics.recordGauge("aGauge", 1.23d);
        // Does not throw.
    }

    @Test
    public void testRecordGaugeLong() {
        metrics.recordGauge("aGauge", 123L);
        // Does not throw.
    }

    @Test
    public void testAddDimension() {
        metrics.addDimension("foo", "bar");
        // Does not throw.
    }

    @Test
    public void testAddDimensions() {
        metrics.addDimensions(Collections.singletonMap("foo", "bar"));
        // Does not throw.
    }

    @Test
    public void testClose() {
        assertTrue(metrics.isOpen());
        metrics.close();
        assertFalse(metrics.isOpen());
    }

    @Test
    public void testDoubleClose() throws InterruptedException {
        assertTrue(metrics.isOpen());
        metrics.close();
        assertFalse(metrics.isOpen());
        assertTrue(metrics.getCloseTime().compareTo(now) >= 0);
        Thread.sleep(10);
        final Instant later = Clock.systemUTC().instant();
        metrics.close();
        assertTrue(metrics.getCloseTime().compareTo(now) >= 0);
        assertTrue(metrics.getCloseTime().compareTo(later) < 0);
    }

    @Test
    public void testTimestamps() {
        assertNotNull(metrics.getOpenTime());
        assertNull(metrics.getCloseTime());
        assertTrue(metrics.getOpenTime().compareTo(now) >= 0);
        metrics.close();
        assertFalse(metrics.isOpen());
        assertNotNull(metrics.getCloseTime());
        assertTrue(metrics.getCloseTime().compareTo(metrics.getOpenTime()) >= 0);
    }

    @Test
    public void testToString() {
        final String asString = metrics.toString();
        assertNotNull(asString);
        assertFalse(asString.isEmpty());
        assertThat(asString, Matchers.containsString("NoOpScopedMetrics"));
    }
}

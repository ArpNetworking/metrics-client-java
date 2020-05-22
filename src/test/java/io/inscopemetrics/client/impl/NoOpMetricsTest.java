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

import io.inscopemetrics.client.Metrics;
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
 * Tests for {@link NoOpMetrics}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class NoOpMetricsTest {

    @Test
    public void testCreateCounter() {
        final Metrics metrics = new NoOpMetrics();
        assertNotNull(metrics.createCounter("aCounter"));
        assertTrue(metrics.createCounter("aCounter") instanceof NoOpCounter);
        assertNotSame(metrics.createCounter("aCounter"), metrics.createCounter("aCounter"));
    }

    @Test
    public void testIncrementCounter() {
        final Metrics metrics = new NoOpMetrics();
        metrics.incrementCounter("aCounter");
        // Does not throw.
    }

    @Test
    public void testIncrementCounterByValue() {
        final Metrics metrics = new NoOpMetrics();
        metrics.incrementCounter("aCounter", 2);
        // Does not throw.
    }

    @Test
    public void testDecrementCounter() {
        final Metrics metrics = new NoOpMetrics();
        metrics.decrementCounter("aCounter");
        // Does not throw.
    }

    @Test
    public void testDecrementCounterByValue() {
        final Metrics metrics = new NoOpMetrics();
        metrics.decrementCounter("aCounter", 2);
        // Does not throw.
    }

    @Test
    public void testResetCounter() {
        final Metrics metrics = new NoOpMetrics();
        metrics.resetCounter("aCounter");
        // Does not throw.
    }

    @Test
    public void testCreateTimer() {
        final Metrics metrics = new NoOpMetrics();
        assertNotNull(metrics.createTimer("aTimer"));
        assertTrue(metrics.createTimer("aTimer") instanceof NoOpTimer);
        assertNotSame(metrics.createTimer("aTimer"), metrics.createTimer("aTimer"));
    }

    @Test
    public void testStartTimer() {
        final Metrics metrics = new NoOpMetrics();
        metrics.startTimer("aTimer");
        // Does not throw.
    }

    @Test
    public void testStopTimer() {
        final Metrics metrics = new NoOpMetrics();
        metrics.stopTimer("aTimer");
        // Does not throw.
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testSetTimerTimeUnit() {
        final Metrics metrics = new NoOpMetrics();
        metrics.setTimer("aTimer", 1, TimeUnit.SECONDS);
        // Does not throw.
    }

    @Test
    public void testSetGaugeDouble() {
        final Metrics metrics = new NoOpMetrics();
        metrics.setGauge("aGauge", 1.23d);
        // Does not throw.
    }

    @Test
    public void testSetGaugeLong() {
        final Metrics metrics = new NoOpMetrics();
        metrics.setGauge("aGauge", 123L);
        // Does not throw.
    }

    @Test
    public void testAddDimension() {
        final Metrics metrics = new NoOpMetrics();
        metrics.addDimension("foo", "bar");
        // Does not throw.
    }

    @Test
    public void testAddDimensions() {
        final Metrics metrics = new NoOpMetrics();
        metrics.addDimensions(Collections.singletonMap("foo", "bar"));
        // Does not throw.
    }

    @Test
    public void testClose() {
        final Metrics metrics = new NoOpMetrics();
        assertTrue(metrics.isOpen());
        metrics.close();
        assertFalse(metrics.isOpen());
    }

    @Test
    public void testDoubleClose() throws InterruptedException {
        final Instant now = Clock.systemUTC().instant();
        final Metrics metrics = new NoOpMetrics();
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
        final Instant now = Clock.systemUTC().instant();
        final Metrics metrics = new NoOpMetrics();
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
        final String asString = new NoOpMetrics().toString();
        assertNotNull(asString);
        assertFalse(asString.isEmpty());
        assertThat(asString, Matchers.containsString("NoOpMetrics"));
    }
}

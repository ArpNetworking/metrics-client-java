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
package com.arpnetworking.metrics.impl;

import com.arpnetworking.metrics.StopWatch;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.hamcrest.MockitoHamcrest;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for {@link TsdTimer}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class TsdTimerTest {

    @Test
    public void testClose() throws InterruptedException {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final long minimumTimeInMilliseconds = 100;
        final long startTime = System.nanoTime();
        final TsdTimer timer;
        try (TsdTimer resourceTimer = TsdTimer.newInstance("timerName", isOpen)) {
            timer = resourceTimer;
            Thread.sleep(minimumTimeInMilliseconds);
            resourceTimer.stop();
        }
        final long elapsedTimeInNanoseconds = System.nanoTime() - startTime;
        Assert.assertThat(
                (Long) timer.getValue(),
                Matchers.greaterThanOrEqualTo(TimeUnit.NANOSECONDS.convert(
                        minimumTimeInMilliseconds,
                        TimeUnit.MILLISECONDS)));
        Assert.assertThat((Long) timer.getValue(), Matchers.lessThanOrEqualTo(elapsedTimeInNanoseconds));
    }

    @Test
    public void testStop() throws InterruptedException {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final long minimumTimeInMilliseconds = 100;
        final long startTime = System.nanoTime();
        try (TsdTimer timer = TsdTimer.newInstance("timerName", isOpen)) {
            Thread.sleep(minimumTimeInMilliseconds);
            timer.stop();

            final long elapsedTimeInNanoseconds = System.nanoTime() - startTime;
            Assert.assertThat(
                    (Long) timer.getValue(),
                    Matchers.greaterThanOrEqualTo(TimeUnit.NANOSECONDS.convert(
                            minimumTimeInMilliseconds,
                            TimeUnit.MILLISECONDS)));
            Assert.assertThat((Long) timer.getValue(), Matchers.lessThanOrEqualTo(elapsedTimeInNanoseconds));
        }
    }

    @Test
    public void testAlreadyStopped() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final Logger logger = createSlf4jLoggerMock();
        @SuppressWarnings("resource")
        final TsdTimer timer = new TsdTimer("timerName", isOpen, logger);
        timer.close();
        Mockito.verifyZeroInteractions(logger);
        timer.close();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testAlreadyStoppedRaceCondition() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final Logger logger = createSlf4jLoggerMock();
        final StopWatch stopWatch = Mockito.mock(StopWatch.class);
        Mockito.doReturn(true).when(stopWatch).isRunning();
        Mockito.doThrow(new IllegalStateException("Stopwatch already stopped")).when(stopWatch).stop();
        @SuppressWarnings("resource")
        final TsdTimer timer = new TsdTimer("timerName", isOpen, stopWatch, logger);
        timer.close();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testAlreadyAborted() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final Logger logger = createSlf4jLoggerMock();
        @SuppressWarnings("resource")
        final TsdTimer timer = new TsdTimer("timerName", isOpen, logger);
        timer.abort();
        Mockito.verifyZeroInteractions(logger);
        timer.abort();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testStopAfterAbort() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final Logger logger = createSlf4jLoggerMock();
        @SuppressWarnings("resource")
        final TsdTimer timer = new TsdTimer("timerName", isOpen, logger);
        timer.abort();
        Mockito.verifyZeroInteractions(logger);
        timer.close();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testAbortAfterStop() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final Logger logger = createSlf4jLoggerMock();
        @SuppressWarnings("resource")
        final TsdTimer timer = new TsdTimer("timerName", isOpen, logger);
        timer.close();
        Mockito.verifyZeroInteractions(logger);
        timer.abort();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testStopAfterMetricsClosed() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        @SuppressWarnings("resource")
        final TsdTimer timer = new TsdTimer("timerName", isOpen, logger);
        isOpen.set(false);
        Mockito.verify(logger, Mockito.never()).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
        timer.close();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testAbortAfterMetricsClosed() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        @SuppressWarnings("resource")
        final TsdTimer timer = new TsdTimer("timerName", isOpen, logger);
        isOpen.set(false);
        Mockito.verify(logger, Mockito.never()).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
        timer.abort();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testGetElapsedAfterStop() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        @SuppressWarnings("resource")
        final TsdTimer timer = new TsdTimer("timerName", isOpen, logger);
        Mockito.verify(logger, Mockito.never()).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
        timer.getValue();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
        timer.getUnit();
        Mockito.verify(logger, Mockito.times(2)).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testIsRunning() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final TsdTimer timer = TsdTimer.newInstance("timerName", isOpen);
        Assert.assertTrue(timer.isRunning());
        timer.stop();
        Assert.assertFalse(timer.isRunning());
    }

    @Test
    public void testIsAborted() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final TsdTimer timer = TsdTimer.newInstance("timerName", isOpen);
        Assert.assertFalse(timer.isAborted());
        timer.abort();
        Assert.assertTrue(timer.isAborted());
    }

    @Test
    public void testToString() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final String asString = TsdTimer.newInstance("timerName", isOpen).toString();
        Assert.assertNotNull(asString);
        Assert.assertFalse(asString.isEmpty());
        Assert.assertThat(asString, Matchers.containsString("timerName"));
    }

    @Test
    public void testConstructor() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final TsdTimer timer = TsdTimer.newInstance("timerName", isOpen);
        Assert.assertTrue(timer.isRunning());
    }

    private Logger createSlf4jLoggerMock() {
        return Mockito.mock(org.slf4j.Logger.class);
    }
}

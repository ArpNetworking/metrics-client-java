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

import org.hamcrest.Matchers;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.hamcrest.MockitoHamcrest;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link TsdTimer}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TsdTimerTest {

    @Test
    public void testClose() throws InterruptedException {
        final long minimumTimeInMilliseconds = 100;
        final long startTime = System.nanoTime();
        final TsdTimer timer;
        try (TsdTimer resourceTimer = TsdTimer.newInstance("timerName")) {
            timer = resourceTimer;
            Thread.sleep(minimumTimeInMilliseconds);
            resourceTimer.stop();
        }
        final double elapsedTimeInNanoseconds = System.nanoTime() - startTime;
        assertThat(
                (Double) timer.getValue(),
                Matchers.greaterThanOrEqualTo(Utility.convertTimeUnit(
                        minimumTimeInMilliseconds,
                        TimeUnit.MILLISECONDS,
                        TimeUnit.SECONDS)));
        assertThat((Double) timer.getValue(), Matchers.lessThanOrEqualTo(elapsedTimeInNanoseconds));
    }

    @Test
    public void testStop() throws InterruptedException {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final long minimumTimeInMilliseconds = 100;
        final long startTime = System.nanoTime();
        try (TsdTimer timer = TsdTimer.newInstance("timerName")) {
            Thread.sleep(minimumTimeInMilliseconds);
            timer.stop();

            final double elapsedTimeInNanoseconds = System.nanoTime() - startTime;
            assertThat(
                    (Double) timer.getValue(),
                    Matchers.greaterThanOrEqualTo(Utility.convertTimeUnit(
                            minimumTimeInMilliseconds,
                            TimeUnit.MILLISECONDS,
                            TimeUnit.SECONDS)));
            assertThat((Double) timer.getValue(), Matchers.lessThanOrEqualTo(elapsedTimeInNanoseconds));
        }
    }

    @Test
    public void testAlreadyStopped() {
        final Logger logger = createSlf4jLoggerMock();
        @SuppressWarnings("resource")
        final TsdTimer timer = new TsdTimer("timerName", logger);
        timer.close();
        Mockito.verifyZeroInteractions(logger);
        timer.close();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testAlreadyAborted() {
        final Logger logger = createSlf4jLoggerMock();
        @SuppressWarnings("resource")
        final TsdTimer timer = new TsdTimer("timerName", logger);
        timer.abort();
        Mockito.verifyZeroInteractions(logger);
        timer.abort();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testStopAfterAbort() {
        final Logger logger = createSlf4jLoggerMock();
        @SuppressWarnings("resource")
        final TsdTimer timer = new TsdTimer("timerName", logger);
        timer.abort();
        Mockito.verifyZeroInteractions(logger);
        timer.close();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testAbortAfterStop() {
        final Logger logger = createSlf4jLoggerMock();
        @SuppressWarnings("resource")
        final TsdTimer timer = new TsdTimer("timerName", logger);
        timer.close();
        Mockito.verifyZeroInteractions(logger);
        timer.abort();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testGetElapsedBeforeStop() {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        @SuppressWarnings("resource")
        final TsdTimer timer = new TsdTimer("timerName", logger);
        Mockito.verify(logger, Mockito.never()).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
        timer.getValue();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testGetElapsedAfterAbort() {
        final Logger logger = createSlf4jLoggerMock();
        @SuppressWarnings("resource")
        final TsdTimer timer = new TsdTimer("timerName", logger);
        timer.abort();
        Mockito.verifyZeroInteractions(logger);
        timer.getValue();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testIsRunning() {
        final TsdTimer timer = TsdTimer.newInstance("timerName");
        assertTrue(timer.isRunning());
        timer.stop();
        assertFalse(timer.isRunning());
    }

    @Test
    public void testIsAborted() {
        final TsdTimer timer = TsdTimer.newInstance("timerName");
        assertFalse(timer.isAborted());
        timer.abort();
        assertTrue(timer.isAborted());
    }

    @Test
    public void testToString() {
        final String asString = TsdTimer.newInstance("timerName").toString();
        assertNotNull(asString);
        assertFalse(asString.isEmpty());
        assertThat(asString, Matchers.containsString("timerName"));
    }

    @Test
    public void testConstructor() {
        final TsdTimer timer = TsdTimer.newInstance("timerName");
        assertTrue(timer.isRunning());
    }

    private Logger createSlf4jLoggerMock() {
        return Mockito.mock(org.slf4j.Logger.class);
    }
}

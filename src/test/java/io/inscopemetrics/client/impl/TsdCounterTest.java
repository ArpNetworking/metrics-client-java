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
import org.mockito.hamcrest.MockitoHamcrest;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests for {@link TsdCounter}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TsdCounterTest {

    @Test
    public void testIncrement() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final TsdCounter counter = TsdCounter.newInstance("counterName", isOpen::get);
        assertEquals(0L, counter.getValue().longValue());
        counter.increment();
        assertEquals(1L, counter.getValue().longValue());
        counter.increment();
        assertEquals(2L, counter.getValue().longValue());
    }

    @Test
    public void testDecrement() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final TsdCounter counter = TsdCounter.newInstance("counterName", isOpen::get);
        assertEquals(0L, counter.getValue().longValue());
        counter.decrement();
        assertEquals(-1L, counter.getValue().longValue());
        counter.decrement();
        assertEquals(-2L, counter.getValue().longValue());
    }

    @Test
    public void testIncrementByValue() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final TsdCounter counter = TsdCounter.newInstance("counterName", isOpen::get);
        assertEquals(0L, counter.getValue().longValue());
        counter.increment(2);
        assertEquals(2L, counter.getValue().longValue());
        counter.increment(3);
        assertEquals(5L, counter.getValue().longValue());
    }

    @Test
    public void testDecrementByValue() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final TsdCounter counter = TsdCounter.newInstance("counterName", isOpen::get);
        assertEquals(0L, counter.getValue().longValue());
        counter.decrement(2);
        assertEquals(-2L, counter.getValue().longValue());
        counter.decrement(3);
        assertEquals(-5L, counter.getValue().longValue());
    }

    @Test
    public void testCombination() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final TsdCounter counter = TsdCounter.newInstance("counterName", isOpen::get);
        assertEquals(0L, counter.getValue().longValue());
        counter.increment();
        assertEquals(1L, counter.getValue().longValue());
        counter.decrement(3L);
        assertEquals(-2L, counter.getValue().longValue());
        counter.increment(4L);
        assertEquals(2L, counter.getValue().longValue());
        counter.decrement();
        assertEquals(1L, counter.getValue().longValue());
    }

    @Test
    public void testIncrementAfterClose() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final Logger logger = mock(Logger.class);
        final TsdCounter counter = new TsdCounter("counterName", isOpen::get, logger);
        counter.increment();
        isOpen.set(false);
        verifyNoInteractions(logger);
        counter.increment();
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testIncrementByValueAfterClose() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final Logger logger = mock(Logger.class);
        final TsdCounter counter = new TsdCounter("counterName", isOpen::get, logger);
        counter.increment(2L);
        isOpen.set(false);
        verifyNoInteractions(logger);
        counter.increment(2L);
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testDecrementAfterClose() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final Logger logger = mock(Logger.class);
        final TsdCounter counter = new TsdCounter("counterName", isOpen::get, logger);
        counter.decrement();
        isOpen.set(false);
        verifyNoInteractions(logger);
        counter.decrement();
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testDecrementByValueAfterClose() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final Logger logger = mock(Logger.class);
        final TsdCounter counter = new TsdCounter("counterName", isOpen::get, logger);
        counter.decrement(2L);
        isOpen.set(false);
        verifyNoInteractions(logger);
        counter.decrement(2L);
        verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testToString() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final String asString = TsdCounter.newInstance("counterName", isOpen::get).toString();
        assertNotNull(asString);
        assertFalse(asString.isEmpty());
        assertThat(asString, Matchers.containsString("counterName"));
    }
}

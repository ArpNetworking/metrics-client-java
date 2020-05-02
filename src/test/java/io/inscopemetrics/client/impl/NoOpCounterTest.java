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

import org.hamcrest.Matchers;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for {@link NoOpCounter}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class NoOpCounterTest {

    @Test
    public void testIncrement() {
        final NoOpCounter counter = new NoOpCounter();
        assertEquals(0L, counter.getValue().longValue());
        counter.increment();
        assertEquals(0L, counter.getValue().longValue());
    }

    @Test
    public void testDecrement() {
        final NoOpCounter counter = new NoOpCounter();
        assertEquals(0L, counter.getValue().longValue());
        counter.decrement();
        assertEquals(0L, counter.getValue().longValue());
    }

    @Test
    public void testIncrementByValue() {
        final NoOpCounter counter = new NoOpCounter();
        assertEquals(0L, counter.getValue().longValue());
        counter.increment(2);
        assertEquals(0L, counter.getValue().longValue());
    }

    @Test
    public void testDecrementByValue() {
        final NoOpCounter counter = new NoOpCounter();
        assertEquals(0L, counter.getValue().longValue());
        counter.decrement(2);
        assertEquals(0L, counter.getValue().longValue());
    }

    @Test
    public void testToString() {
        final String asString = new NoOpCounter().toString();
        assertNotNull(asString);
        assertFalse(asString.isEmpty());
        assertThat(asString, Matchers.containsString("NoOpCounter"));
    }
}

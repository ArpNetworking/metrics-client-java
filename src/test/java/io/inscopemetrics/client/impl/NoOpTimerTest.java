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
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link NoOpTimer}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class NoOpTimerTest {

    @Test
    public void testGetValue() {
        final NoOpTimer timer = new NoOpTimer();
        assertEquals(0, timer.getValue());
        timer.close();
        assertEquals(0, timer.getValue());
    }

    @Test
    public void testClose() {
        final NoOpTimer timer;
        try (NoOpTimer resourceTimer = new NoOpTimer()) {
            timer = resourceTimer;
            assertTrue(resourceTimer.isRunning());
        }
        assertFalse(timer.isRunning());
    }

    @Test
    public void testStop() {
        final NoOpTimer timer = new NoOpTimer();
        assertTrue(timer.isRunning());
        timer.stop();
        assertFalse(timer.isRunning());
    }

    @Test
    public void testAbort() {
        final NoOpTimer timer = new NoOpTimer();
        assertFalse(timer.isAborted());
        timer.abort();
        assertTrue(timer.isAborted());
    }

    @Test
    public void testToString() {
        final String asString = new NoOpTimer().toString();
        assertNotNull(asString);
        assertFalse(asString.isEmpty());
        assertThat(asString, Matchers.containsString("NoOpTimer"));
    }
}

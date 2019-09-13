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
package com.arpnetworking.metrics.impl;

import com.arpnetworking.metrics.Units;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for <code>NoOpTimer</code>.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class NoOpTimerTest {

    @Test
    public void testGetValue() throws InterruptedException {
        final NoOpTimer timer = new NoOpTimer();
        Assert.assertEquals(0, timer.getValue());
        timer.close();
        Assert.assertEquals(0, timer.getValue());
    }

    @Test
    public void testGetUnit() throws InterruptedException {
        final NoOpTimer timer = new NoOpTimer();
        Assert.assertEquals(Units.NANOSECOND, timer.getUnit());
        timer.close();
        Assert.assertEquals(Units.NANOSECOND, timer.getUnit());
    }

    @Test
    public void testClose() throws InterruptedException {
        final NoOpTimer timer;
        try (NoOpTimer resourceTimer = new NoOpTimer()) {
            timer = resourceTimer;
            Assert.assertTrue(resourceTimer.isRunning());
        }
        Assert.assertFalse(timer.isRunning());
    }

    @Test
    public void testStop() throws InterruptedException {
        final NoOpTimer timer = new NoOpTimer();
        Assert.assertTrue(timer.isRunning());
        timer.stop();
        Assert.assertFalse(timer.isRunning());
    }

    @Test
    public void testAbort() throws InterruptedException {
        final NoOpTimer timer = new NoOpTimer();
        Assert.assertFalse(timer.isAborted());
        timer.abort();
        Assert.assertTrue(timer.isAborted());
    }

    @Test
    public void testToString() {
        final String asString = new NoOpTimer().toString();
        Assert.assertNotNull(asString);
        Assert.assertFalse(asString.isEmpty());
        Assert.assertThat(asString, Matchers.containsString("NoOpTimer"));
    }
}

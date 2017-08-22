/**
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

import com.arpnetworking.metrics.StopWatch;
import com.arpnetworking.metrics.Units;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for <code>TsdStopWatch</code>.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class TsdStopWatchTest {

    @Test
    public void testIsRunning() {
        final StopWatch stopWatch = new TsdStopWatch();
        Assert.assertTrue(stopWatch.isRunning());
        stopWatch.stop();
        Assert.assertFalse(stopWatch.isRunning());
    }

    @Test
    public void testStopElapsedTime() throws InterruptedException {
        final long startTime = System.nanoTime();
        final StopWatch stopWatch = new TsdStopWatch();
        Thread.sleep(1000);
        stopWatch.stop();
        final long elapsedTime = System.nanoTime() - startTime;
        Assert.assertEquals(Units.NANOSECOND, stopWatch.getElapsedTime().getUnit());
        Assert.assertTrue(stopWatch.getElapsedTime().getValue().doubleValue() <= elapsedTime);
    }

    @Test
    public void testStopTwice() {
        final StopWatch stopWatch = new TsdStopWatch();
        stopWatch.stop();
        try {
            stopWatch.stop();
            Assert.fail("Expect exception not thrown");
        } catch (final IllegalStateException e) {
            // Expected exception
        }
    }

    @Test
    public void testElapsedBeforeStop() {
        final StopWatch stopWatch = new TsdStopWatch();
        try {
            stopWatch.getElapsedTime();
            Assert.fail("Expect exception not thrown");
        } catch (final IllegalStateException e) {
            // Expected exception
        }
    }

    @Test
    public void testToString() {
        final String asString = new TsdStopWatch().toString();
        Assert.assertNotNull(asString);
        Assert.assertFalse(asString.isEmpty());
    }
}

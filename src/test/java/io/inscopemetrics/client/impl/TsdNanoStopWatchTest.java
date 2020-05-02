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

import io.inscopemetrics.client.StopWatch;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.hamcrest.MockitoHamcrest;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link TsdNanoStopWatch}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TsdNanoStopWatchTest {

    @Test
    public void testIsRunning() {
        final StopWatch stopWatch = new TsdNanoStopWatch();
        assertTrue(stopWatch.isRunning());
        stopWatch.stop();
        assertFalse(stopWatch.isRunning());
    }

    @Test
    public void testStopElapsedTime() throws InterruptedException {
        final long startTime = System.nanoTime();
        final StopWatch stopWatch = new TsdNanoStopWatch();
        Thread.sleep(1000);
        stopWatch.stop();
        final long elapsedTime = System.nanoTime() - startTime;
        assertEquals(TimeUnit.NANOSECONDS, stopWatch.getUnit());
        assertTrue(stopWatch.getElapsedTime() <= elapsedTime);
    }

    @Test
    public void testStopTwiceNoEffect() throws InterruptedException {
        final StopWatch stopWatch = new TsdNanoStopWatch();
        stopWatch.stop();
        final long value = stopWatch.getElapsedTime();
        Thread.sleep(100);
        stopWatch.stop();
        assertEquals(value, stopWatch.getElapsedTime());
    }

    @Test
    public void testElapsedBeforeStop() {
        final org.slf4j.Logger logger = Mockito.mock(org.slf4j.Logger.class);
        final StopWatch stopWatch = new TsdNanoStopWatch(logger);
        stopWatch.getElapsedTime();
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(Matchers.any(String.class)));
    }

    @Test
    public void testToString() {
        final String asString = new TsdNanoStopWatch().toString();
        assertNotNull(asString);
        assertFalse(asString.isEmpty());
    }
}

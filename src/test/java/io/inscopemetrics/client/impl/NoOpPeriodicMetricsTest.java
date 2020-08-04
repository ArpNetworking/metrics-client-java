/*
 * Copyright 2020 Dropbox
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

import io.inscopemetrics.client.PeriodicMetrics;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for {@link NoOpPeriodicMetrics}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class NoOpPeriodicMetricsTest {

    private final PeriodicMetrics metrics = new NoOpPeriodicMetrics();

    @Test
    public void testRegisterPolledMetric() {
        metrics.registerPolledMetric(m -> { });
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
    public void testClose() {
        metrics.close();
        // Does not throw.
    }

    @Test
    public void testToString() {
        final String asString = metrics.toString();
        assertNotNull(asString);
        assertFalse(asString.isEmpty());
        assertThat(asString, Matchers.containsString("NoOpPeriodicMetrics"));
    }
}

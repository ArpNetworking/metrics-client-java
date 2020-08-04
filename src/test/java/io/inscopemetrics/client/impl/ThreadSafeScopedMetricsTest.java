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

import io.inscopemetrics.client.Sink;
import org.junit.Test;

import java.time.Clock;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Tests specific to {@link ThreadSafeScopedMetrics}. Tests shared with
 * {@link LockFreeScopedMetrics} are found in {@link ScopedMetricsCommonTest}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class ThreadSafeScopedMetricsTest {

    @Test
    public void testGetOrCreate() {
        final Sink sink = mock(Sink.class);
        @SuppressWarnings("resource")
        final ThreadSafeScopedMetrics metrics = createThreadSafeScopedMetrics(sink);

        final ConcurrentMap<String, String> map = new ConcurrentHashMap<>();
        assertEquals("bar", metrics.getOrCreate(map, "foo", "bar"));
        assertEquals("bar", metrics.getOrCreate(map, "foo", "who"));
    }

    private static ThreadSafeScopedMetrics createThreadSafeScopedMetrics(final Sink sink) {
        final ThreadSafeScopedMetrics metrics = new ThreadSafeScopedMetrics(
                UUID.randomUUID(),
                Collections.singletonList(sink),
                Clock.systemDefaultZone(),
                mock(org.slf4j.Logger.class));
        metrics.addDimension("host", "MyHost");
        metrics.addDimension("service", "MyService");
        metrics.addDimension("cluster", "MyCluster");
        return metrics;
    }
}

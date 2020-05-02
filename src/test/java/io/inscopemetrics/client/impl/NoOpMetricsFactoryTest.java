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

import io.inscopemetrics.client.Metrics;
import org.hamcrest.Matchers;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link NoOpMetricsFactory}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class NoOpMetricsFactoryTest {

    @Test
    public void testCreate() {
        final Metrics metrics = new NoOpMetricsFactory().create();
        assertNotNull(metrics);
        assertTrue(metrics instanceof NoOpMetrics);
    }

    @Test
    public void testCreateLockFree() {
        final Metrics metrics = new NoOpMetricsFactory().createLockFree();
        assertNotNull(metrics);
        assertTrue(metrics instanceof NoOpMetrics);
    }

    @Test
    public void testToString() {
        final String asString = new NoOpMetricsFactory().toString();
        assertNotNull(asString);
        assertFalse(asString.isEmpty());
        assertThat(asString, Matchers.containsString("NoOpMetricsFactory"));
    }
}

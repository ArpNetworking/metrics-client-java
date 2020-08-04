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

import io.inscopemetrics.client.MetricsFactory;
import io.inscopemetrics.client.PeriodicMetrics;
import io.inscopemetrics.client.ScopedMetrics;

import java.time.Duration;

/**
 * No operation implementation of {@link MetricsFactory}. This class is
 * thread safe.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class NoOpMetricsFactory implements MetricsFactory {

    private static final ScopedMetrics NO_OP_SCOPED_METRICS = new NoOpScopedMetrics();
    private static final PeriodicMetrics NO_OP_PERIODIC_METRICS = new NoOpPeriodicMetrics();

    @Override
    public ScopedMetrics createScopedMetrics() {
        return NO_OP_SCOPED_METRICS;
    }

    @Override
    public ScopedMetrics createLockFreeScopedMetrics() {
        return NO_OP_SCOPED_METRICS;
    }

    @Override
    public PeriodicMetrics schedulePeriodicMetrics(final Duration duration) {
        return NO_OP_PERIODIC_METRICS;
    }

    @Override
    public void close() {
        // Nothing to do
    }

    @Override
    public String toString() {
        return "NoOpMetricsFactory";
    }
}

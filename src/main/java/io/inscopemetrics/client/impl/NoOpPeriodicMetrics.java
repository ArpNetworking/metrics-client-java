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

import io.inscopemetrics.client.Metrics;
import io.inscopemetrics.client.PeriodicMetrics;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Implementation of {@link PeriodicMetrics} that provides safe interactions
 * but does not actually publish any metrics.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class NoOpPeriodicMetrics implements PeriodicMetrics {

    @Override
    public void registerPolledMetric(final Consumer<Metrics> callback) {
        // Do nothing
    }

    @Override
    public void recordCounter(final String name, final long value) {
        // Do nothing
    }

    @Override
    public void recordTimer(final String name, final long duration, final TimeUnit unit) {
        // Do nothing
    }

    @Override
    public void recordGauge(final String name, final double value) {
        // Do nothing
    }

    @Override
    public void recordGauge(final String name, final long value) {
        // Do nothing
    }

    @Override
    public void close() {
        // Nothing to do
    }

    @Override
    public String toString() {
        return "NoOpPeriodicMetrics";
    }
}

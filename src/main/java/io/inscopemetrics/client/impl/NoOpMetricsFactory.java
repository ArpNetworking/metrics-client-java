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
import io.inscopemetrics.client.MetricsFactory;

/**
 * No operation implementation of {@link MetricsFactory}. This class is
 * thread safe.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class NoOpMetricsFactory implements MetricsFactory {

    private static final Metrics NO_OP_METRICS = new NoOpMetrics();

    @Override
    public Metrics create() {
        return NO_OP_METRICS;
    }

    @Override
    public Metrics createLockFree() {
        return NO_OP_METRICS;
    }

    @Override
    public String toString() {
        return "NoOpMetricsFactory";
    }
}

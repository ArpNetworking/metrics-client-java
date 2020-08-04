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

import io.inscopemetrics.client.Quantity;
import io.inscopemetrics.client.Timer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * No operation implementation of {@link Timer}. This class is thread safe.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class NoOpTimer implements Timer, Quantity {

    private final AtomicBoolean isOpen = new AtomicBoolean(true);
    private final AtomicBoolean isAborted = new AtomicBoolean(false);

    /**
     * Public. All {@link NoOpTimer} instances should
     * be created through the {@link NoOpScopedMetrics} instance.
     */
    public NoOpTimer() {
        // Do nothing
    }

    @Override
    public void stop() {
        isOpen.set(false);
    }

    @Override
    public void close() {
        isOpen.set(false);
    }

    @Override
    public void abort() {
        isAborted.set(true);
    }

    @Override
    public Number getValue() {
        return 0;
    }

    @Override
    public boolean isRunning() {
        return isOpen.get();
    }

    @Override
    public boolean isAborted() {
        return isAborted.get();
    }

    @Override
    public String toString() {
        return "NoOpTimer";
    }
}

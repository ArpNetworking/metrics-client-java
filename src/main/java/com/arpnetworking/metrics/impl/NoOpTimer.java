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

import com.arpnetworking.metrics.Quantity;
import com.arpnetworking.metrics.Timer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * No operation implementation of {@link Timer}. This class is thread safe.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
/* package private */ final class NoOpTimer implements Timer, Quantity {
    /**
     * Package private constructor. All {@link NoOpTimer} instances should
     * be created through the {@link NoOpMetrics} instance.
     */
    /* package private */ NoOpTimer() {
        // Do nothing
    }

    @Override
    public void stop() {
        _isOpen.set(false);
    }

    @Override
    public void close() {
        _isOpen.set(false);
    }

    @Override
    public void abort() {
        _isAborted.set(true);
    }

    @Override
    public Number getValue() {
        return 0;
    }

    @Override
    public boolean isRunning() {
        return _isOpen.get();
    }

    @Override
    public boolean isAborted() {
        return _isAborted.get();
    }

    @Override
    public String toString() {
        return "NoOpTimer";
    }

    private final AtomicBoolean _isOpen = new AtomicBoolean(true);
    private final AtomicBoolean _isAborted = new AtomicBoolean(false);
}

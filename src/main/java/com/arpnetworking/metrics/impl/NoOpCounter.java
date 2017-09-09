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

import com.arpnetworking.metrics.Counter;
import com.arpnetworking.metrics.Quantity;
import com.arpnetworking.metrics.Unit;

import javax.annotation.Nullable;

/**
 * No operation implementation of <code>Counter</code>.  This class is thread safe.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
/* package private */ final class NoOpCounter implements Counter, Quantity {
    /**
     * Package private constructor. All <code>NoOpCounter</code> instances should
     * be created through the <code>NoOpMetrics</code> instance.
     */
    /* package private */ NoOpCounter() {
        // Do nothing
    }

    @Override
    public void increment() {
        // Do nothing
    }

    @Override
    public void decrement() {
        // Do nothing
    }

    @Override
    public void increment(final long value) {
        // Do nothing
    }

    @Override
    public void decrement(final long value) {
        // Do nothing
    }

    @Override
    public String toString() {
        return "NoOpCounter";
    }

    @Override
    public Number getValue() {
        return 0;
    }

    @Override
    @Nullable
    public Unit getUnit() {
        return null;
    }
}

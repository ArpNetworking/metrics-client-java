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
package com.arpnetworking.metrics.impl;

import com.arpnetworking.metrics.Quantity;

import java.util.Objects;

/**
 * Default implementation of {@link Quantity}. This class is thread safe.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
/* package private */ final class TsdQuantity implements Quantity {

    /**
     * Package private static factory. All {@link TsdCounter} instances
     * should be created through the {@link TsdMetrics} instance.
     *
     * @param value The value.
     * @return New instance of {@link TsdQuantity}.
     */
    /* package private */ static TsdQuantity newInstance(final Number value) {
        return new TsdQuantity(value);
    }

    @Override
    public Number getValue() {
        return _value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof TsdQuantity)) {
            return false;
        }

        final TsdQuantity otherQuantity = (TsdQuantity) other;
        return Objects.equals(_value, otherQuantity._value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_value);
    }

    @Override
    public String toString() {
        return String.format(
                "TsdQuantity{id=%s, Value=%s}",
                Integer.toHexString(System.identityHashCode(this)),
                _value);
    }

    private TsdQuantity(final Number value) {
        _value = value;
    }

    private final Number _value;
}

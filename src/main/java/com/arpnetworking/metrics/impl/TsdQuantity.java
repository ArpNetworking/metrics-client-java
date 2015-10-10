/**
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
import com.arpnetworking.metrics.Unit;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Default implementation of <code>Quantity</code>. This class is thread safe.
 *
 * @author Ville Koskela (vkoskela at groupon dot com)
 */
/* package private */ final class TsdQuantity implements Quantity {

    /**
     * Package private static factory. All <code>TsdCounter</code> instances
     * should be created through the <code>TsdMetrics</code> instance.
     *
     * @param value The value.
     * @param unit The units of the value.
     * @return New instance of <code>TsdQuantity</code>.
     */
    /* package private */ static TsdQuantity newInstance(final Number value, @Nullable final Unit unit) {
        return new TsdQuantity(value, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Number getValue() {
        return _value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Unit getUnit() {
        return _unit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof TsdQuantity)) {
            return false;
        }

        final TsdQuantity otherQuantity = (TsdQuantity) other;
        return Objects.equals(_value, otherQuantity._value)
                && Objects.equals(_unit, otherQuantity._unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(_value, _unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format(
                "TsdQuantity{id=%s, Value=%s, Unit=%s}",
                Integer.toHexString(System.identityHashCode(this)),
                _value,
                _unit);
    }

    private TsdQuantity(final Number value, @Nullable final Unit unit) {
        _value = value;
        _unit = unit;
    }

    private final Number _value;
    @Nullable private final Unit _unit;
}

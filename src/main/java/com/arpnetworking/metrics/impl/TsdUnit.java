/*
 * Copyright 2015 Groupon.com
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

import com.arpnetworking.metrics.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Default implementation of {@link Unit}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class TsdUnit implements Unit {

    public BaseUnit getBaseUnit() {
        return _baseUnit;
    }

    public BaseScale getBaseScale() {
        return _baseScale;
    }

    @Override
    public String getName() {
        return _baseScale.getName() + _baseUnit.getName();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof TsdUnit)) {
            return false;
        }

        final TsdUnit otherUnit = (TsdUnit) other;
        return Objects.equals(_baseUnit, otherUnit._baseUnit)
                && Objects.equals(_baseScale, otherUnit._baseScale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_baseUnit, _baseScale);
    }

    @Override
    public String toString() {
        return String.format(
                "TsdUnit{BaseUnit=%s, BaseScale=%s}",
                _baseUnit,
                _baseScale);
    }

    private TsdUnit(final Builder builder) {
        _baseUnit = builder._baseUnit;
        _baseScale = builder._baseScale;
    }

    private final BaseUnit _baseUnit;
    private final BaseScale _baseScale;

    private static final Logger LOGGER = LoggerFactory.getLogger(TsdUnit.class);

    /**
     * Builder for {@link TsdUnit}.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
     */
    public static final class Builder implements com.arpnetworking.commons.builder.Builder<Unit> {

        /**
         * Create an instance of {@link Unit}. The instance may be
         * {@code null} if no base unit is specified.
         *
         * @return Instance of {@link Unit}.
         */
        @Override
        public @Nullable Unit build() {
            if (_baseScale == null && _baseUnit == null) {
                return null;
            }
            if (_baseScale == null) {
                return _baseUnit;
            }
            if (_baseUnit == null) {
                // TODO(vkoskela): Support scalar only units.
                LOGGER.warn("TsdUnit cannot be constructed without base unit");
                return null;
            }
            return new TsdUnit(this);
        }

        /**
         * Set the base unit.
         *
         * @param value The base unit.
         * @return This {@link Builder} instance.
         */
        public Builder setBaseUnit(final BaseUnit value) {
            _baseUnit = value;
            return this;
        }

        /**
         * Set the base scale.
         *
         * @param value The base scale.
         * @return This {@link Builder} instance.
         */
        public Builder setScale(final BaseScale value) {
            _baseScale = value;
            return this;
        }

        private BaseUnit _baseUnit;
        private BaseScale _baseScale;
    }
}

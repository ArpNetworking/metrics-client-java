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

/**
 * Base units.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public enum BaseUnit implements Unit {

    // Time
    /**
     * Seconds.
     */
    SECOND("second"),
    /**
     * Minutes.
     */
    MINUTE("minute"),
    /**
     * Hours.
     */
    HOUR("hour"),
    /**
     * Days.
     */
    DAY("day"),
    /**
     * Weeks.
     */
    WEEK("week"),

    // Size
    /**
     * Bytes.
     */
    BIT("bit"),
    /**
     * Bytes.
     */
    BYTE("byte"),

    // Rotation
    /**
     * Rotations.
     */
    ROTATION("rotation"),
    /**
     * Degrees.
     */
    DEGREE("degree"),
    /**
     * Radians.
     */
    RADIAN("radian"),

    // Temperature
    /**
     * Celsius.
     */
    CELSIUS("celsius"),
    /**
     * Fahrenheit.
     */
    FAHRENHEIT("fahrenheit"),
    /**
     * Kelvin.
     */
    KELVIN("kelvin");

    @Override
    public String getName() {
        return _name;
    }

    BaseUnit(final String serializedName) {
        _name = serializedName;
    }

    private final String _name;
}

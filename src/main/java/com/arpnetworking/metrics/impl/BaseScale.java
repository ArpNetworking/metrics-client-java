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

/**
 * Base scales.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public enum BaseScale {

    // Base 10 scales
    /**
     * Yocto (10^-24).
     */
    YOCTO("yocto"),
    /**
     * Zepto (10^-21).
     */
    ZEPTO("zepto"),
    /**
     * Atto (10^-18).
     */
    ATTO("atto"),
    /**
     * Femto (10^-15).
     */
    FEMTO("femto"),
    /**
     * Pico (10^-12).
     */
    PICO("pico"),
    /**
     * Nano (10^-9).
     */
    NANO("nano"),
    /**
     * Micro (10^-6).
     */
    MICRO("micro"),
    /**
     * Milli (10^-3).
     */
    MILLI("milli"),
    /**
     * Centi (10^-2).
     */
    CENTI("centi"),
    /**
     * Deci (10^-1).
     */
    DECI("deci"),
    /**
     * Deca (10^1).
     */
    DECA("deca"),
    /**
     * Hecto (10^2).
     */
    HECTO("hecto"),
    /**
     * Kilo (10^3).
     */
    KILO("kilo"),
    /**
     * Mega (10^6).
     */
    MEGA("mega"),
    /**
     * Giga (10^9).
     */
    GIGA("giga"),
    /**
     * Tera (10^12).
     */
    TERA("tera"),
    /**
     * Peta (10^15).
     */
    PETA("peta"),
    /**
     * Exa (10^18).
     */
    EXA("exa"),
    /**
     * Zetta (10^21).
     */
    ZETTA("zetta"),
    /**
     * Yotta (10^24).
     */
    YOTTA("yotta"),

    // Base 2 scales
    /**
     * Kibi (2^10).
     */
    KIBI("kibi"),
    /**
     * Mebi (2^20).
     */
    MEBI("mebi"),
    /**
     * Gibi (2^30).
     */
    GIBI("gibi"),
    /**
     * Tebi (2^40).
     */
    TEBI("tebi"),
    /**
     * Pebi (2^50).
     */
    PEBI("pebi"),
    /**
     * Exbi (2^60).
     */
    EXBI("exbi"),
    /**
     * Zebi (2^70).
     */
    ZEBI("zebi"),
    /**
     * Yobi (2^80).
     */
    YOBI("yobi");

    /**
     * Retrieve the serialized name of the base scale. Individual sinks may
     * map these to suite their formats.
     *
     * @return The serialized name of the base scale.
     */
    public String getName() {
        return _name;
    }

    BaseScale(final String name) {
        _name = name;
    }

    private final String _name;
}

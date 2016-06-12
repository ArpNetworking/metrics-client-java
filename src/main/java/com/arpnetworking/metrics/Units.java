/**
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
package com.arpnetworking.metrics;

import com.arpnetworking.metrics.impl.BaseScale;
import com.arpnetworking.metrics.impl.BaseUnit;
import com.arpnetworking.metrics.impl.TsdCompoundUnit;
import com.arpnetworking.metrics.impl.TsdUnit;

/**
 * Units available for recording metrics. The units are used to aggregate values
 * of the same metric published in different units (e.g. bytes and kilobytes).
 * Publishing a metric with units from different domains will cause some of the
 * data to be discarded by the aggregator (e.g. bytes and seconds). This
 * includes discarding data when some data has a unit and some data does not
 * have any unit. This library cannot detect such inconsistencies since
 * aggregation can occur across Metric instances, processes and even hosts.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class Units {

    // Time
    /**
     * Nanoseconds.
     */
    public static final Unit NANOSECOND = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.SECOND)
            .setScale(BaseScale.NANO)
            .build();
    /**
     * Microseconds.
     */
    public static final Unit MICROSECOND = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.SECOND)
            .setScale(BaseScale.MICRO)
            .build();
    /**
     * Milliseconds.
     */
    public static final Unit MILLISECOND = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.SECOND)
            .setScale(BaseScale.MILLI)
            .build();
    /**
     * Seconds.
     */
    public static final Unit SECOND = BaseUnit.SECOND;
    /**
     * Minutes.
     */
    public static final Unit MINUTE = BaseUnit.MINUTE;
    /**
     * Hours.
     */
    public static final Unit HOUR = BaseUnit.HOUR;
    /**
     * Days.
     */
    public static final Unit DAY = BaseUnit.DAY;
    /**
     * Weeks.
     */
    public static final Unit WEEK = BaseUnit.WEEK;

    // Frequency
    /**
     * Per Nanosecond.
     */
    public static final Unit PER_NANOSECOND = new TsdCompoundUnit.Builder()
            .addDenominatorUnit(NANOSECOND)
            .build();
    /**
     * Per Microsecond.
     */
    public static final Unit PER_MICROSECOND = new TsdCompoundUnit.Builder()
            .addDenominatorUnit(MICROSECOND)
            .build();
    /**
     * Per Millisecond.
     */
    public static final Unit PER_MILLISECOND = new TsdCompoundUnit.Builder()
            .addDenominatorUnit(MILLISECOND)
            .build();
    /**
     * Per Second.
     */
    public static final Unit PER_SECOND = new TsdCompoundUnit.Builder()
            .addDenominatorUnit(BaseUnit.SECOND)
            .build();
    /**
     * Per Minute.
     */
    public static final Unit PER_MINUTE = new TsdCompoundUnit.Builder()
            .addDenominatorUnit(BaseUnit.MINUTE)
            .build();
    /**
     * Per Hour.
     */
    public static final Unit PER_HOUR = new TsdCompoundUnit.Builder()
            .addDenominatorUnit(BaseUnit.HOUR)
            .build();
    /**
     * Per Day.
     */
    public static final Unit PER_DAY = new TsdCompoundUnit.Builder()
            .addDenominatorUnit(BaseUnit.DAY)
            .build();
    /**
     * Per Week.
     */
    public static final Unit PER_WEEK = new TsdCompoundUnit.Builder()
            .addDenominatorUnit(BaseUnit.WEEK)
            .build();
    /**
     * Hertz.
     */
    public static final Unit HERTZ = new TsdCompoundUnit.Builder()
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Kilohertz.
     */
    public static final Unit KILOHERTZ = new TsdCompoundUnit.Builder()
            .addDenominatorUnit(
                    new TsdUnit.Builder()
                        .setBaseUnit(BaseUnit.SECOND)
                        .setScale(BaseScale.MILLI)
                        .build())
            .build();
    /**
     * Megahertz.
     */
    public static final Unit MEGAHERTZ = new TsdCompoundUnit.Builder()
            .addDenominatorUnit(
                    new TsdUnit.Builder()
                            .setBaseUnit(BaseUnit.SECOND)
                            .setScale(BaseScale.MICRO)
                            .build())
            .build();
    /**
     * Gigahertz.
     */
    public static final Unit GIGAHERTZ = new TsdCompoundUnit.Builder()
            .addDenominatorUnit(
                    new TsdUnit.Builder()
                            .setBaseUnit(BaseUnit.SECOND)
                            .setScale(BaseScale.NANO)
                            .build())
            .build();
    /**
     * Terahertz.
     */
    public static final Unit TERAHERTZ = new TsdCompoundUnit.Builder()
            .addDenominatorUnit(
                    new TsdUnit.Builder()
                            .setBaseUnit(BaseUnit.SECOND)
                            .setScale(BaseScale.PICO)
                            .build())
            .build();

    // Data
    /**
     * Bits.
     */
    public static final Unit BIT = BaseUnit.BIT;
    /**
     * Kibibits.
     */
    public static final Unit KIBIBIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.KIBI)
            .build();
    /**
     * Mebibits.
     */
    public static final Unit MEBIBIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.MEBI)
            .build();
    /**
     * Gibibits.
     */
    public static final Unit GIBIBIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.GIBI)
            .build();
    /**
     * Tebibits.
     */
    public static final Unit TEBIBIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.TEBI)
            .build();
    /**
     * Pebibits.
     */
    public static final Unit PEBIBIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.PEBI)
            .build();
    /**
     * Exbibits.
     */
    public static final Unit EXBIBIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.EXBI)
            .build();
    /**
     * Zebibits.
     */
    public static final Unit ZEBIBIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.ZEBI)
            .build();
    /**
     * Yobibits.
     */
    public static final Unit YOBIBIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.YOBI)
            .build();
    /**
     * Kilobits.
     */
    public static final Unit KILOBIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.KILO)
            .build();
    /**
     * Megabits.
     */
    public static final Unit MEGABIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.MEGA)
            .build();
    /**
     * Gigabits.
     */
    public static final Unit GIGABIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.GIGA)
            .build();
    /**
     * Terabits.
     */
    public static final Unit TERABIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.TERA)
            .build();
    /**
     * Petabits.
     */
    public static final Unit PETABIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.PETA)
            .build();
    /**
     * Exabits.
     */
    public static final Unit EXABIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.EXA)
            .build();
    /**
     * Zettabits.
     */
    public static final Unit ZETTABIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.ZETTA)
            .build();
    /**
     * Yottabits.
     */
    public static final Unit YOTTABIT = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BIT)
            .setScale(BaseScale.YOTTA)
            .build();
    /**
     * Bytes.
     */
    public static final Unit BYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .build();
    /**
     * Kibibytes.
     */
    public static final Unit KIBIBYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.KIBI)
            .build();
    /**
     * Mebibytes.
     */
    public static final Unit MEBIBYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.MEBI)
            .build();
    /**
     * Gibibytes.
     */
    public static final Unit GIBIBYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.GIBI)
            .build();
    /**
     * Tebibytes.
     */
    public static final Unit TEBIBYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.TEBI)
            .build();
    /**
     * Pebibytes.
     */
    public static final Unit PEBIBYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.PEBI)
            .build();
    /**
     * Exbibytes.
     */
    public static final Unit EXBIBYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.EXBI)
            .build();
    /**
     * Zebibytes.
     */
    public static final Unit ZEBIBYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.ZEBI)
            .build();
    /**
     * Yobibytes.
     */
    public static final Unit YOBIBYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.YOBI)
            .build();
    /**
     * Kilobytes.
     */
    public static final Unit KILOBYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.KILO)
            .build();
    /**
     * Megabytes.
     */
    public static final Unit MEGABYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.MEGA)
            .build();
    /**
     * Gigabytes.
     */
    public static final Unit GIGABYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.GIGA)
            .build();
    /**
     * Terabytes.
     */
    public static final Unit TERABYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.TERA)
            .build();
    /**
     * Petabytes.
     */
    public static final Unit PETABYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.PETA)
            .build();
    /**
     * Exabytes.
     */
    public static final Unit EXABYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.EXA)
            .build();
    /**
     * Zettabytes.
     */
    public static final Unit ZETTABYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.ZETTA)
            .build();
    /**
     * Yottabytes.
     */
    public static final Unit YOTTABYTE = new TsdUnit.Builder()
            .setBaseUnit(BaseUnit.BYTE)
            .setScale(BaseScale.YOTTA)
            .build();

    // Data Frequency
    /**
     * Bits per second.
     */
    public static final Unit BITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(BaseUnit.BIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Kibibits per second.
     */
    public static final Unit KIBIBITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(KIBIBIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Mebibits per second.
     */
    public static final Unit MEBIBITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(MEBIBIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Gibibits per second.
     */
    public static final Unit GIBIBITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(GIBIBIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Tebibits per second.
     */
    public static final Unit TEBIBITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(TEBIBIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Pebibits per second.
     */
    public static final Unit PEBIBITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(PEBIBIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Exbibits per second.
     */
    public static final Unit EXBIBITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(EXBIBIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Zebibits per second.
     */
    public static final Unit ZEBIBITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(ZEBIBIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Yobibits.
     */
    public static final Unit YOBIBITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(YOBIBIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Kilobits per second.
     */
    public static final Unit KILOBITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(KILOBIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Megabits per second.
     */
    public static final Unit MEGABITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(MEGABIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Gigabits per second.
     */
    public static final Unit GIGABITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(GIGABIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Terabits per second.
     */
    public static final Unit TERABITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(TERABIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Petabits per second.
     */
    public static final Unit PETABITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(PETABIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Exabits per second.
     */
    public static final Unit EXABITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(EXABIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Zettabits per second.
     */
    public static final Unit ZETTABITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(ZETTABIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Yottabits per second.
     */
    public static final Unit YOTTABITS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(YOTTABIT)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Bytes per second.
     */
    public static final Unit BYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(BYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Kibibytes per second.
     */
    public static final Unit KIBIBYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(KIBIBYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Mebibytes per second.
     */
    public static final Unit MEBIBYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(MEBIBYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Gibibytes per second.
     */
    public static final Unit GIBIBYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(GIBIBYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Tebibytes per second.
     */
    public static final Unit TEBIBYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(TEBIBYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Pebibytes per second.
     */
    public static final Unit PEBIBYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(PEBIBYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Exbibytes per second.
     */
    public static final Unit EXBIBYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(EXBIBYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Zebibytes per second.
     */
    public static final Unit ZEBIBYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(ZEBIBYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Yobibytes.
     */
    public static final Unit YOBIBYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(YOBIBYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Kilobytes per second.
     */
    public static final Unit KILOBYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(KILOBYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Megabytes per second.
     */
    public static final Unit MEGABYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(MEGABYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Gigabytes per second.
     */
    public static final Unit GIGABYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(GIGABYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Terabytes per second.
     */
    public static final Unit TERABYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(TERABYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Petabytes per second.
     */
    public static final Unit PETABYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(PETABYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Exabytes per second.
     */
    public static final Unit EXABYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(EXABYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Zettabytes per second.
     */
    public static final Unit ZETTABYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(ZETTABYTE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Yottabytes per second.
     */
    public static final Unit YOTTABYTES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(YOTTABYTE)
            .addDenominatorUnit(SECOND)
            .build();

    // Rotations
    /**
     * Rotations.
     */
    public static final Unit ROTATION = BaseUnit.ROTATION;
    /**
     * Radians.
     */
    public static final Unit RADIAN = BaseUnit.RADIAN;
    /**
     * Degrees.
     */
    public static final Unit DEGREE = BaseUnit.DEGREE;

    // Rotational Frequency
    /**
     * Rotations per second.
     */
    public static final Unit ROTATIONS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(ROTATION)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Rotations per minute.
     */
    public static final Unit ROTATIONS_PER_MINUTE = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(ROTATION)
            .addDenominatorUnit(MINUTE)
            .build();
    /**
     * Radians per second.
     */
    public static final Unit RADIANS_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(RADIAN)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Radians per minute.
     */
    public static final Unit RADIANS_PER_MINUTE = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(RADIAN)
            .addDenominatorUnit(MINUTE)
            .build();
    /**
     * Degrees per second.
     */
    public static final Unit DEGREES_PER_SECOND = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(DEGREE)
            .addDenominatorUnit(SECOND)
            .build();
    /**
     * Degrees per minute.
     */
    public static final Unit DEGREES_PER_MINUTE = new TsdCompoundUnit.Builder()
            .addNumeratorUnit(DEGREE)
            .addDenominatorUnit(MINUTE)
            .build();

    // Temperature
    /**
     * Kelvin.
     */
    public static final Unit KELVIN = BaseUnit.KELVIN;
    /**
     * Celsius.
     */
    public static final Unit CELSIUS = BaseUnit.CELSIUS;
    /**
     * Fahrenheit.
     */
    public static final Unit FAHRENHEIT = BaseUnit.FAHRENHEIT;

    private Units() {}
}

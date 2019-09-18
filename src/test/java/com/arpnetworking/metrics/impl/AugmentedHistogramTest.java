/*
 * Copyright 2019 Dropbox
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

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link AugmentedHistogram}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class AugmentedHistogramTest {

    @Test
    public void testAugmentedHistogram() {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<Double, Integer> histogram = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        for (int i = 1; i < 10; ++i) {
            histogram.put(toKey((double) i), i);
        }

        Assert.assertEquals(AUGMENTED_HISTOGRAM.getMax(), 10.0, 0.0001);
        Assert.assertEquals(AUGMENTED_HISTOGRAM.getMin(), 1.0, 0.0001);
        Assert.assertEquals(AUGMENTED_HISTOGRAM.getSum(), 285.0, 0.0001);
        Assert.assertEquals(AUGMENTED_HISTOGRAM.getPrecision(), 7);
        Assert.assertEquals(AUGMENTED_HISTOGRAM.getHistogram(), histogram);
    }

    @Test
    public void testInvalidAugmentedHistogram() {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<Double, Integer> histogram = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        double sum = 0.0;
        for (int i = 1; i < 10; ++i) {
            histogram.put(toKey((double) i), i);
            sum += i * i;
        }

        Assert.assertTrue(
                new AugmentedHistogram.Builder()
                        .setHistogram(null)
                        .setPrecision(7)
                        .setMinimum(1.0)
                        .setMaximum(10.0)
                        .setSum(sum)
                        .build() instanceof NoOpAggregatedData);
        Assert.assertTrue(
                new AugmentedHistogram.Builder()
                        .setHistogram(histogram)
                        .setPrecision(null)
                        .setMinimum(1.0)
                        .setMaximum(10.0)
                        .setSum(sum)
                        .build() instanceof NoOpAggregatedData);
        Assert.assertTrue(
                new AugmentedHistogram.Builder()
                        .setHistogram(histogram)
                        .setPrecision(7)
                        .setMinimum(null)
                        .setMaximum(10.0)
                        .setSum(sum)
                        .build() instanceof NoOpAggregatedData);
        Assert.assertTrue(
                new AugmentedHistogram.Builder()
                        .setHistogram(histogram)
                        .setPrecision(7)
                        .setMinimum(1.0)
                        .setMaximum(null)
                        .setSum(sum)
                        .build() instanceof NoOpAggregatedData);
        Assert.assertTrue(
                new AugmentedHistogram.Builder()
                        .setHistogram(histogram)
                        .setPrecision(7)
                        .setMinimum(1.0)
                        .setMaximum(10.0)
                        .setSum(null)
                        .build() instanceof NoOpAggregatedData);
        Assert.assertTrue(
                new AugmentedHistogram.Builder()
                        .setHistogram(histogram)
                        .setPrecision(7)
                        .setMinimum(9999.0) // Min is greater than max
                        .setMaximum(0.9999)
                        .setSum(sum)
                        .build() instanceof NoOpAggregatedData);
    }

    @Test
    public void testEquals() {
        Assert.assertNotSame(AUGMENTED_HISTOGRAM, AUGMENTED_HISTOGRAM_ALT);

        Assert.assertTrue(AUGMENTED_HISTOGRAM.equals(AUGMENTED_HISTOGRAM));
        Assert.assertFalse(AUGMENTED_HISTOGRAM.equals(null));
        Assert.assertFalse(AUGMENTED_HISTOGRAM.equals("This is a String"));

        Assert.assertTrue(AUGMENTED_HISTOGRAM.equals(AUGMENTED_HISTOGRAM_ALT));
        Assert.assertFalse(AUGMENTED_HISTOGRAM.equals(AUGMENTED_HISTOGRAM_MIN));
        Assert.assertFalse(AUGMENTED_HISTOGRAM.equals(AUGMENTED_HISTOGRAM_MAX));
        Assert.assertFalse(AUGMENTED_HISTOGRAM.equals(AUGMENTED_HISTOGRAM_SUM));
        // TODO(ville): Once we support non-default precision this needs to be flipped
        // (The assertion below will start failing when this happens; and can be removed once equals is fixed)
        Assert.assertTrue(AUGMENTED_HISTOGRAM.equals(AUGMENTED_HISTOGRAM_PRECISION));
        Assert.assertEquals(AUGMENTED_HISTOGRAM_PRECISION.getPrecision(), 7);
        Assert.assertFalse(AUGMENTED_HISTOGRAM.equals(AUGMENTED_HISTOGRAM_HISTOGRAM));
    }

    @Test
    public void testHashCode() {
        Assert.assertEquals(
                AUGMENTED_HISTOGRAM.hashCode(),
                AUGMENTED_HISTOGRAM.hashCode());
        Assert.assertNotSame(AUGMENTED_HISTOGRAM, AUGMENTED_HISTOGRAM_ALT);
        Assert.assertEquals(
                AUGMENTED_HISTOGRAM.hashCode(),
                AUGMENTED_HISTOGRAM_ALT.hashCode());
    }

    @Test
    public void testToString() {
        final String asString = AUGMENTED_HISTOGRAM.toString();
        Assert.assertNotNull(asString);
        Assert.assertFalse(asString.isEmpty());
    }

    /**
     * Convert a value to a histogram bucket key.
     *
     * @param value the value to convert
     * @return the key converted from the key
     */
    public static double toKey(final double value) {
        // In Metrics Aggregator Daemon (MAD) see:
        // src/main/java/com/arpnetworking/metrics/mad/model/statistics/HistogramStatistic.java
        //
        // This assumes a fixed 7-bit precision.
        final long mask = 0xffffe00000000000L;
        return Double.longBitsToDouble(Double.doubleToRawLongBits(value) & mask);
    }

    private static final AugmentedHistogram AUGMENTED_HISTOGRAM;
    private static final AugmentedHistogram AUGMENTED_HISTOGRAM_ALT;
    private static final AugmentedHistogram AUGMENTED_HISTOGRAM_MIN;
    private static final AugmentedHistogram AUGMENTED_HISTOGRAM_MAX;
    private static final AugmentedHistogram AUGMENTED_HISTOGRAM_SUM;
    private static final AugmentedHistogram AUGMENTED_HISTOGRAM_PRECISION;
    private static final AugmentedHistogram AUGMENTED_HISTOGRAM_HISTOGRAM;

    static {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<Double, Integer> histogramA = new HashMap<>();
        final Map<Double, Integer> histogramB = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        double sumA = 0.0;
        for (int i = 1; i < 10; ++i) {
            histogramA.put(toKey((double) i), i);
            sumA += i * i;

            histogramB.put(toKey((double) i), 10 - i);
        }
        AUGMENTED_HISTOGRAM = (AugmentedHistogram) new AugmentedHistogram.Builder()
                .setHistogram(histogramA)
                .setPrecision(7)
                .setMinimum(1.0)
                .setMaximum(10.0)
                .setSum(sumA)
                .build();

        AUGMENTED_HISTOGRAM_ALT = (AugmentedHistogram) new AugmentedHistogram.Builder()
                .setHistogram(histogramA)
                .setPrecision(7)
                .setMinimum(1.0)
                .setMaximum(10.0)
                .setSum(sumA)
                .build();

        AUGMENTED_HISTOGRAM_MIN = (AugmentedHistogram) new AugmentedHistogram.Builder()
                .setHistogram(histogramA)
                .setPrecision(7)
                .setMinimum(2.0) // Only field different
                .setMaximum(10.0)
                .setSum(sumA)
                .build();

        AUGMENTED_HISTOGRAM_MAX = (AugmentedHistogram) new AugmentedHistogram.Builder()
                .setHistogram(histogramA)
                .setPrecision(7)
                .setMinimum(1.0)
                .setMaximum(11.0) // Only field different
                .setSum(sumA)
                .build();

        AUGMENTED_HISTOGRAM_SUM = (AugmentedHistogram) new AugmentedHistogram.Builder()
                .setHistogram(histogramA)
                .setPrecision(7)
                .setMinimum(1.0)
                .setMaximum(10.0)
                .setSum(0.0) // Only field different
                .build();

        AUGMENTED_HISTOGRAM_PRECISION = (AugmentedHistogram) new AugmentedHistogram.Builder()
                .setHistogram(histogramA)
                .setPrecision(6) // Only field different
                .setMinimum(1.0)
                .setMaximum(10.0)
                .setSum(sumA)
                .build();

        AUGMENTED_HISTOGRAM_HISTOGRAM = (AugmentedHistogram) new AugmentedHistogram.Builder()
                .setHistogram(histogramB) // Only field different from A
                .setPrecision(7)
                .setMinimum(1.0)
                .setMaximum(10.0)
                .setSum(sumA)
                .build();
    }
}

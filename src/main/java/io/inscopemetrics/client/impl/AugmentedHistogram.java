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
package io.inscopemetrics.client.impl;

import io.inscopemetrics.client.AggregatedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Aggregated data for numerical measurements represented as a histogram
 * with supporting data capturing the minimum, maximum and sum of the
 * aggregated measurements.
 *
 * The histogram keys should be truncated from the sample value being
 * inserted. The amount of truncation is controlled by the precision.
 * Specifically, given {@code precision} and {@code sampleValue} the
 * key in the histogram is computed like this:
 *
 * {@code
 * final int MANTISSA_BITS = 52;
 * final int EXPONENT_BITS = 11;
 * final long BASE_MASK = (1L << (MANTISSA_BITS + EXPONENT_BITS)) >> EXPONENT_BITS;
 *
 * final long truncateMask = BASE_MASK >> precision;
 * final double histogramKey = Double.longBitsToDouble(Double.doubleToRawLongBits(sampleValue) & truncateMask)
 * }
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class AugmentedHistogram implements AggregatedData {

    private static final Logger LOGGER = LoggerFactory.getLogger(AugmentedHistogram.class);

    private final Map<Double, Integer> histogram;
    private final int precision;
    private final double min;
    private final double max;
    private final double sum;

    /**
     * Protected constructor via {@link Builder}.
     *
     * @param builder The {@link Builder} to build from.
     */
    protected AugmentedHistogram(final Builder builder) {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        histogram = new HashMap<>(builder.histogram);
        // CHECKSTYLE.ON: IllegalInstantiation
        precision = builder.precision;
        min = builder.min;
        max = builder.max;
        sum = builder.sum;
    }

    public Map<Double, Integer> getHistogram() {
        return Collections.unmodifiableMap(histogram);
    }

    public int getPrecision() {
        return precision;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getSum() {
        return sum;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AugmentedHistogram)) {
            return false;
        }
        final AugmentedHistogram otherAugmentedHistogram = (AugmentedHistogram) other;
        return Objects.equals(histogram, otherAugmentedHistogram.histogram)
                // TODO(ville): Enable precision comparison once it's allowed to vary.
                //&& precision == otherAugmentedHistogram.precision
                && Double.compare(min, otherAugmentedHistogram.min) == 0
                && Double.compare(max, otherAugmentedHistogram.max) == 0
                && Double.compare(sum, otherAugmentedHistogram.sum) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(histogram, precision, min, max, sum);
    }

    @Override
    public String toString() {
        return String.format(
                "AugmentedHistogram{Histogram=%s, Precision=%s, Min=%s, Max=%s, Sum=%s}",
                histogram,
                precision,
                min,
                max,
                sum);
    }

    /**
     * Builder implementation for {@link AggregatedData}.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    public static class Builder implements com.arpnetworking.commons.builder.Builder<AggregatedData> {

        private static final int DEFAULT_PRECISION = 7;

        private final Logger logger;

        private Map<Double, Integer> histogram;
        private Integer precision;
        private Double min;
        private Double max;
        private Double sum;

        /**
         * Public constructor.
         */
        public Builder() {
            this(LOGGER);
        }

        // NOTE: Package private for testing
        Builder(@Nullable final Logger logger) {
            this.logger = logger;
        }

        /**
         * Create an instance of {@link AugmentedHistogram}.
         *
         * @return Instance of {@link AugmentedHistogram}.
         */
        @Override
        public AggregatedData build() {
            final List<String> failures = new ArrayList<>();

            // Validate
            validate(failures);

            // Until the full stack supports variable precision histograms
            // we are locking the precision value to the existing implied default.
            if (precision != null && precision != DEFAULT_PRECISION) {
                logger.info(
                        String.format(
                                "Defaulted invalid precision (%s); precision=%s",
                                precision,
                                DEFAULT_PRECISION));
                precision = DEFAULT_PRECISION;
            }

            // Apply fallback
            if (!failures.isEmpty()) {
                logger.warn(String.format(
                        "Unable to construct AugmentedHistogram, metrics disabled; failures=%s",
                        failures));
                return new NoOpAggregatedData();
            }

            return new AugmentedHistogram(this);
        }

        /**
         * Set the histogram. Cannot be null.
         *
         * <u><b>IMPORTANT:</b></u>
         * The keys in the histogram must be computed with the specified precision
         * and match the key computation logic in the other components of the Inscope
         * Metrics software stack. Please refer to class Javadoc on {@link AugmentedHistogram}
         * for details on truncation process.
         *
         * @param value The histogram.
         * @return This {@link Builder} instance.
         */
        public Builder setHistogram(@Nullable final Map<Double, Integer> value) {
            histogram = value;
            return this;
        }

        /**
         * Set the precision. Cannot be null.
         *
         * @param value The precision.
         * @return This {@link Builder} instance.
         */
        public Builder setPrecision(@Nullable final Integer value) {
            precision = value;
            return this;
        }

        /**
         * Set the minimum. Cannot be null.
         *
         * @param value The minimum.
         * @return This {@link Builder} instance.
         */
        public Builder setMinimum(@Nullable final Double value) {
            min = value;
            return this;
        }

        /**
         * Set the maximum. Cannot be null.
         *
         * @param value The maximum.
         * @return This {@link Builder} instance.
         */
        public Builder setMaximum(@Nullable final Double value) {
            max = value;
            return this;
        }

        /**
         * Set the sum. Cannot be null.
         *
         * @param value The sum.
         * @return This {@link Builder} instance.
         */
        public Builder setSum(@Nullable final Double value) {
            sum = value;
            return this;
        }

        private void validate(final List<String> failures) {
            if (histogram == null) {
                failures.add("Histogram cannot be null");
            }
            if (precision == null) {
                failures.add("Precision cannot be null");
            }
            if (min == null) {
                failures.add("Minimum cannot be null");
            }
            if (max == null) {
                failures.add("Maximum cannot be null");
            }
            if (sum == null) {
                failures.add("Sum cannot be null");
            }
            if (min != null && max != null && min > max) {
                failures.add("Minimum cannot be greater than maximum.");
            }
        }
    }
}

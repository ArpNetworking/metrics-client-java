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

import com.arpnetworking.metrics.AggregatedData;
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
public final class AugmentedHistogram implements AggregatedData {

    public Map<Double, Long> getHistogram() {
        return Collections.unmodifiableMap(_histogram);
    }

    public int getPrecision() {
        return _precision;
    }

    public double getMin() {
        return _min;
    }

    public double getMax() {
        return _max;
    }

    public double getSum() {
        return _sum;
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
        return Objects.equals(_histogram, otherAugmentedHistogram._histogram)
                // TODO(ville): Enable precision comparison once it's allowed to vary.
                //&& _precision == otherAugmentedHistogram._precision
                && Double.compare(_min, otherAugmentedHistogram._min) == 0
                && Double.compare(_max, otherAugmentedHistogram._max) == 0
                && Double.compare(_sum, otherAugmentedHistogram._sum) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_histogram, _precision, _min, _max, _sum);
    }

    @Override
    public String toString() {
        return String.format(
                "AugmentedHistogram{Histogram=%s, Precision=%s, Min=%s, Max=%s, Sum=%s}",
                _histogram,
                _precision,
                _min,
                _max,
                _sum);
    }

    private AugmentedHistogram(final Builder builder) {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        _histogram = new HashMap<>(builder._histogram);
        // CHECKSTYLE.ON: IllegalInstantiation
        _precision = builder._precision;
        _min = builder._min;
        _max = builder._max;
        _sum = builder._sum;
    }

    private final Map<Double, Long> _histogram;
    private final int _precision;
    private final double _min;
    private final double _max;
    private final double _sum;

    private static final Logger LOGGER = LoggerFactory.getLogger(AugmentedHistogram.class);

    /**
     * Builder implementation for {@link AggregatedData}.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    public static class Builder implements com.arpnetworking.commons.builder.Builder<AggregatedData> {

        /**
         * Public constructor.
         */
        public Builder() {
            this(LOGGER);
        }

        // NOTE: Package private for testing
        /* package private */ Builder(@Nullable final Logger logger) {
            _logger = logger;
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
            if (_histogram == null) {
                failures.add("Histogram cannot be null");
            }
            if (_precision == null) {
                failures.add("Precision cannot be null");
            }
            if (_min == null) {
                failures.add("Minimum cannot be null");
            }
            if (_max == null) {
                failures.add("Maximum cannot be null");
            }
            if (_sum == null) {
                failures.add("Sum cannot be null");
            }
            if (_min != null && _max != null && _min > _max) {
                failures.add("Minimum cannot be greater than maximum.");
            }

            // Until the full stack supports variable precision histograms
            // we are locking the precision value to the existing implied default.
            if (_precision != null && _precision != 7) {
                _logger.info(
                        String.format(
                                "Defaulted invalid precision (%s); precision=%s",
                                _precision,
                                DEFAULT_PRECISION));
                _precision = DEFAULT_PRECISION;
            }

            // Apply fallback
            if (!failures.isEmpty()) {
                _logger.warn(String.format(
                        "Unable to construct AugmentedHistogram, metrics disabled; failures=%s",
                        failures));
                return NoOpAggregatedData.getInstance();
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
         * @return This <code>Builder</code> instance.
         */
        public Builder setHistogram(@Nullable final Map<Double, Long> value) {
            _histogram = value;
            return this;
        }

        /**
         * Set the precision. Cannot be null.
         *
         * @param value The precision.
         * @return This <code>Builder</code> instance.
         */
        public Builder setPrecision(@Nullable final Integer value) {
            _precision = value;
            return this;
        }

        /**
         * Set the minimum. Cannot be null.
         *
         * @param value The minimum.
         * @return This <code>Builder</code> instance.
         */
        public Builder setMinimum(@Nullable final Double value) {
            _min = value;
            return this;
        }

        /**
         * Set the maximum. Cannot be null.
         *
         * @param value The maximum.
         * @return This <code>Builder</code> instance.
         */
        public Builder setMaximum(@Nullable final Double value) {
            _max = value;
            return this;
        }

        /**
         * Set the sum. Cannot be null.
         *
         * @param value The sum.
         * @return This <code>Builder</code> instance.
         */
        public Builder setSum(@Nullable final Double value) {
            _sum = value;
            return this;
        }

        private final Logger _logger;

        private Map<Double, Long> _histogram;
        private Integer _precision;
        private Double _min;
        private Double _max;
        private Double _sum;

        private static final int DEFAULT_PRECISION = 7;
    }
}

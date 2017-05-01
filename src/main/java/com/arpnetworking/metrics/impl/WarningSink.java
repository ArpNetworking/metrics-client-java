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

import com.arpnetworking.metrics.Event;
import com.arpnetworking.metrics.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Implementation of <code>Sink</code> which emits a warning each time an
 * <code>Event</code> is to recorded.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
/* package private */ final class WarningSink implements Sink {

    @Override
    public void record(final Event event) {
        _logger.warn(String.format(
                "Unable to record event, metrics disabled; reasons=%s",
                _reasons));
    }

    @Override
    public String toString() {
        return String.format(
                "WarningSink{Reasons=%s}",
                _reasons);
    }

    private WarningSink(final Builder builder) {
        _reasons = builder._reasons;
        _logger = builder._logger;
    }

    private final List<String> _reasons;
    private final Logger _logger;


    /**
     * Builder for {@link WarningSink}.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
     */
    public static final class Builder implements com.arpnetworking.commons.builder.Builder<Sink> {

        /**
         * Set the list of reasons this warning sink was used. Required.
         * Cannot be empty.
         *
         * @param value The list of reasons.
         * @return This {@link Builder} instance.
         */
        public Builder setReasons(final List<String> value) {
            _reasons = value;
            return this;
        }

        /**
         * Set the logger to use. Optional.
         *
         * @param value The logger to use.
         * @return This {@link Builder} instance.
         */
        public Builder setLogger(@Nullable final Logger value) {
            _logger = value;
            return this;
        }

        @Override
        public Sink build() {
            // Defaults
            applyDefaults();

            // Validate
            final List<String> failures = new ArrayList<>();
            validate(failures);

            // Fallback
            if (!failures.isEmpty()) {
                final List<String> combinedReasons = new ArrayList<>(failures);
                if (_reasons != null) {
                    combinedReasons.addAll(_reasons);
                }
                _reasons = combinedReasons;
            }

            return new WarningSink(this);
        }

        private void applyDefaults() {
            if (_logger == null) {
                _logger = DEFAULT_LOGGER;
                DEFAULT_LOGGER.info(String.format(
                        "Defaulted null logger; logger=%s",
                        _logger));
            }
        }

        private void validate(final List<String> failures) {
            if (_reasons == null) {
                failures.add("Reasons must be a non-null list");
            } else if (_reasons.isEmpty()) {
                failures.add(String.format("Reasons must be a non-empty list; reasons=%s", _reasons));
            }
        }

        private List<String> _reasons;
        private Logger _logger;

        private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(WarningSink.class);
    }
}

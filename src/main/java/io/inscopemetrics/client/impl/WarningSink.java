/*
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
package io.inscopemetrics.client.impl;

import io.inscopemetrics.client.Event;
import io.inscopemetrics.client.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Implementation of {@link Sink} which emits a warning each time an
 * {@link Event} is to recorded.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class WarningSink implements Sink {

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(WarningSink.class);

    private final List<String> reasons;
    private final Logger logger;

    /**
     * Protected constructor via {@link Builder}.
     *
     * @param builder The {@link Builder} to build from.
     */
    protected WarningSink(final Builder builder) {
        reasons = new ArrayList<>(builder.reasons);
        logger = builder.logger;
    }

    @Override
    public void record(final Event event) {
        logger.warn(String.format(
                "Unable to record event, metrics disabled; reasons=%s",
                reasons));
    }

    @Override
    public void close() {
        // Nothing to do
    }

    @Override
    public String toString() {
        return String.format(
                "WarningSink{Reasons=%s}",
                reasons);
    }

    /**
     * Builder for {@link WarningSink}.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    public static class Builder implements com.arpnetworking.commons.builder.Builder<Sink> {

        private List<String> reasons;
        private Logger logger;

        /**
         * Set the list of reasons this warning sink was used. Required.
         * Cannot be empty.
         *
         * @param value The list of reasons.
         * @return This {@link Builder} instance.
         */
        public Builder setReasons(final List<String> value) {
            reasons = value;
            return this;
        }

        /**
         * Set the logger to use. Optional.
         *
         * @param value The logger to use.
         * @return This {@link Builder} instance.
         */
        public Builder setLogger(@Nullable final Logger value) {
            logger = value;
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
                if (reasons != null) {
                    combinedReasons.addAll(reasons);
                }
                reasons = combinedReasons;
            }

            return new WarningSink(this);
        }

        private void applyDefaults() {
            if (logger == null) {
                logger = DEFAULT_LOGGER;
                DEFAULT_LOGGER.info(String.format(
                        "Defaulted null logger; logger=%s",
                        logger));
            }
        }

        private void validate(final List<String> failures) {
            if (reasons == null) {
                failures.add("Reasons must be a non-null list");
            } else if (reasons.isEmpty()) {
                failures.add(String.format("Reasons must be a non-empty list; reasons=%s", reasons));
            }
        }
    }
}

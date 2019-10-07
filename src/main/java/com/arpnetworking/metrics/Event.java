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
package com.arpnetworking.metrics;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interface representing one span's metrics data.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public interface Event {

    /**
     * Accessor for event identifier.
     *
     * @return the identifier
     */
    UUID getId();

    /**
     * Accessor for start time.
     *
     * @return the start time
     */
    Instant getStartTime();

    /**
     * Accessor for end time.
     *
     * @return the end time
     */
    Instant getEndTime();

    /**
     * Accessor for dimensions.
     *
     * @return the dimensions
     */
    Map<String, String> getDimensions();

    /**
     * Accessor for samples.
     *
     * @return the samples by metric name
     */
    Map<String, List<Quantity>> getSamples();

    /**
     * Accessor for aggregated data.
     *
     * @return the aggregated data by metric name.
     */
    Map<String, AggregatedData> getAggregatedData();
}

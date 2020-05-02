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
package io.inscopemetrics.client.impl;

import io.inscopemetrics.client.AggregatedData;
import io.inscopemetrics.client.Event;
import io.inscopemetrics.client.Quantity;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link Event}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TsdEvent implements Event {

    private final UUID id;
    private final Instant startTime;
    private final Instant endTime;
    private final Map<String, String> dimensions;
    private final Map<String, List<Quantity>> samples;
    private final Map<String, AggregatedData> aggregatedData;

    /**
     * Protected constructor.
     *
     * NOTE: This method does <b>not</b> perform a deep copy of the provided
     * data structures. Callers are expected to <b>not</b> modify these data
     * structures after passing them to this constructor. This is acceptable
     * since this class is for internal implementation only.
     *
     * @param id the event identifier
     * @param startTime the start time
     * @param endTime the end time
     * @param dimensions the annotations
     * @param samples the samples
     * @param aggregatedData the aggregated data
     */
    protected TsdEvent(
            final UUID id,
            final Instant startTime,
            final Instant endTime,
            final Map<String, String> dimensions,
            final Map<String, List<Quantity>> samples,
            final Map<String, AggregatedData> aggregatedData) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.dimensions = dimensions;
        this.samples = samples;
        this.aggregatedData = aggregatedData;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public Instant getStartTime() {
        return startTime;
    }

    @Override
    public Instant getEndTime() {
        return endTime;
    }

    @Override
    public Map<String, String> getDimensions() {
        return Collections.unmodifiableMap(dimensions);
    }

    @Override
    public Map<String, List<Quantity>> getSamples() {
        return Collections.unmodifiableMap(samples);
    }

    @Override
    public Map<String, AggregatedData> getAggregatedData() {
        return Collections.unmodifiableMap(aggregatedData);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TsdEvent)) {
            return false;
        }
        final TsdEvent otherEvent = (TsdEvent) other;
        return Objects.equals(id, otherEvent.id)
                && Objects.equals(startTime, otherEvent.startTime)
                && Objects.equals(endTime, otherEvent.endTime)
                && Objects.equals(dimensions, otherEvent.dimensions)
                && Objects.equals(samples, otherEvent.samples)
                && Objects.equals(aggregatedData, otherEvent.aggregatedData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, startTime, endTime, dimensions, samples, aggregatedData);
    }

    @Override
    public String toString() {
        return String.format(
                "TsdEvent{Id=%s, StartTime=%s, EndTime=%s, Dimensions=%s, Samples=%s, AggregatedData=%s}",
                id,
                startTime,
                endTime,
                dimensions,
                samples,
                aggregatedData);
    }
}

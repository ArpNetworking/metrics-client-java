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

import com.arpnetworking.metrics.AggregatedData;
import com.arpnetworking.metrics.Event;
import com.arpnetworking.metrics.Quantity;

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
/* package private */ final class TsdEvent implements Event {

    /**
     * Public constructor.
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
    /* package private */ TsdEvent(
            final UUID id,
            final Instant startTime,
            final Instant endTime,
            final Map<String, String> dimensions,
            final Map<String, List<Quantity>> samples,
            final Map<String, AggregatedData> aggregatedData) {
        _id = id;
        _startTime = startTime;
        _endTime = endTime;
        _dimensions = dimensions;
        _samples = samples;
        _aggregatedData = aggregatedData;
    }

    @Override
    public UUID getId() {
        return _id;
    }

    @Override
    public Instant getStartTime() {
        return _startTime;
    }

    @Override
    public Instant getEndTime() {
        return _endTime;
    }

    @Override
    public Map<String, String> getDimensions() {
        return Collections.unmodifiableMap(_dimensions);
    }

    @Override
    public Map<String, List<Quantity>> getSamples() {
        return Collections.unmodifiableMap(_samples);
    }

    @Override
    public Map<String, AggregatedData> getAggregatedData() {
        return Collections.unmodifiableMap(_aggregatedData);
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
        return Objects.equals(_id, otherEvent._id)
                && Objects.equals(_startTime, otherEvent._startTime)
                && Objects.equals(_endTime, otherEvent._endTime)
                && Objects.equals(_dimensions, otherEvent._dimensions)
                && Objects.equals(_samples, otherEvent._samples)
                && Objects.equals(_aggregatedData, otherEvent._aggregatedData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_id, _startTime, _endTime, _dimensions, _samples, _aggregatedData);
    }

    @Override
    public String toString() {
        return String.format(
                "TsdEvent{Id=%s, StartTime=%s, EndTime=%s, Dimensions=%s, Samples=%s, AggregatedData=%s}",
                _id,
                _startTime,
                _endTime,
                _dimensions,
                _samples,
                _aggregatedData);
    }

    private final UUID _id;
    private final Instant _startTime;
    private final Instant _endTime;
    private final Map<String, String> _dimensions;
    private final Map<String, List<Quantity>> _samples;
    private final Map<String, AggregatedData> _aggregatedData;
}

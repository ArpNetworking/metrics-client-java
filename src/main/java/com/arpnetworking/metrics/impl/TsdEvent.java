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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default implementation of {@link Event}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TsdEvent implements Event {

    /**
     * Public constructor.
     *
     * NOTE: This method does <b>not</b> perform a deep copy of the provided
     * data structures. Callers are expected to <b>not</b> modify these data
     * structures after passing them to this constructor. This is acceptable
     * since this class is for sink implementations only which are expected
     * to honor this contract (e.g. by creating a copy of any end-user
     * provided data).
     *
     * @param annotations The annotations.
     * @param timerSamples The timer samples.
     * @param counterSamples The counter samples.
     * @param gaugeSamples The gauge samples.
     * @param aggregatedData The pre-aggregated data.
     */
    public TsdEvent(
            final Map<String, String> annotations,
            final Map<String, List<Quantity>> timerSamples,
            final Map<String, List<Quantity>> counterSamples,
            final Map<String, List<Quantity>> gaugeSamples,
            final Map<String, AggregatedData> aggregatedData) {
        _annotations = annotations;
        _timerSamples = timerSamples;
        _counterSamples = counterSamples;
        _gaugeSamples = gaugeSamples;
        _aggregatedData = aggregatedData;
    }

    @Override
    public Map<String, String> getAnnotations() {
        return Collections.unmodifiableMap(_annotations);
    }

    @Override
    public Map<String, List<Quantity>> getTimerSamples() {
        return Collections.unmodifiableMap(_timerSamples);
    }

    @Override
    public Map<String, List<Quantity>> getCounterSamples() {
        return Collections.unmodifiableMap(_counterSamples);
    }

    @Override
    public Map<String, List<Quantity>> getGaugeSamples() {
        return Collections.unmodifiableMap(_gaugeSamples);
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
        return Objects.equals(_annotations, otherEvent._annotations)
                && Objects.equals(_counterSamples, otherEvent._counterSamples)
                && Objects.equals(_timerSamples, otherEvent._timerSamples)
                && Objects.equals(_gaugeSamples, otherEvent._gaugeSamples)
                && Objects.equals(_aggregatedData, otherEvent._aggregatedData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_annotations, _counterSamples, _timerSamples, _gaugeSamples, _aggregatedData);
    }

    @Override
    public String toString() {
        return String.format(
                "TsdEvent{Annotations=%s, TimerSamples=%s, CounterSamples=%s, GaugeSamples=%s, AggregatedData=%s}",
                _annotations,
                _timerSamples,
                _counterSamples,
                _gaugeSamples,
                _aggregatedData);
    }

    private final Map<String, String> _annotations;
    private final Map<String, List<Quantity>> _timerSamples;
    private final Map<String, List<Quantity>> _counterSamples;
    private final Map<String, List<Quantity>> _gaugeSamples;
    private final Map<String, AggregatedData> _aggregatedData;
}

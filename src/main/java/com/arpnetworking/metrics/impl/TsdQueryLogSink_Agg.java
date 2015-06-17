/**
 * Copyright 2014 Groupon.com
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

import com.arpnetworking.metrics.Quantity;
import com.arpnetworking.metrics.Sink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extending the logic of <code>TsdQueryLogSink</code> to be able to signal aggregation
 * of some counters by using a signal character.
 *
 * Individual counters are preserved with the signal character removed
 *
 * Aggregate counters are named according to the characters preceding the first signal character
 *
 * Example  Counters In: GET^controller1, GET^controller2
 *          Counters Out: GETcontroller1, GETcontroller2, GET
 *
 * An optional signal character replacement will replace each instance of the signal
 * character in the name.  All characters after the last signal character are dropped.
 *
 * Example  Counters In: http_response_4^^00, http_response_4^^04
 *          Counters Out: http_response_400, http_response_404, http_response_4xx
 *
 * @author Ryan Ascheman (rascheman at groupon dot com)
 */
public class TsdQueryLogSink_Agg extends TsdQueryLogSink {

    protected TsdQueryLogSink_Agg(final Builder_Agg builder) {
        super(builder);
        _signalReplacement = builder._signalReplacement;
    }

    @Override
    public void record(
        final Map<String, String> annotations,
        final Map<String, List<Quantity>> timerSamples,
        final Map<String, List<Quantity>> counterSamples,
        final Map<String, List<Quantity>> gaugeSamples) {

        Map<String, List<Quantity>> aggCounterSamples = new HashMap<>();
        for (Map.Entry<String, List<Quantity>> entry : counterSamples.entrySet()) {
            int lastSignalIndex = entry.getKey().lastIndexOf(AGGREGATION_SIGNAL);
            if (lastSignalIndex > 0) {
                String aggKey = entry.getKey().substring(0, lastSignalIndex+1)
                                                .replaceAll(REPLACE_REGEX, _signalReplacement);

                if (!aggCounterSamples.containsKey(aggKey)) {
                    aggCounterSamples.put(aggKey, new ArrayList<>());
                }

                aggCounterSamples.get(aggKey).addAll(entry.getValue());
            }

            aggCounterSamples.put(entry.getKey().replaceAll(REPLACE_REGEX, ""), entry.getValue());
        }

        super.record(annotations, timerSamples, aggCounterSamples, gaugeSamples);
    }

    private final String _signalReplacement;

    private static final String AGGREGATION_SIGNAL = "^";
    private static final String REPLACE_REGEX = "\\^";

    public static class Builder_Agg extends TsdQueryLogSink.Builder {

        /**
         * Create an instance of <code>Sink</code>.
         *
         * @return Instance of <code>Sink</code>.
         */
        @Override
        public Sink build() {
            super.validateSinkParams();
            return new TsdQueryLogSink_Agg(this);
        }

        public Builder_Agg setSignalReplacement(final String value) {
            _signalReplacement = value;
            return this;
        }

        private String _signalReplacement = DEFAULT_SIGNAL_REPLACEMENT;

        private static final String DEFAULT_SIGNAL_REPLACEMENT = "";
    }
}

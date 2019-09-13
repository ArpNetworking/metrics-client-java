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

/**
 * Aggregated data instance which is ignored by the client.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class NoOpAggregatedData implements AggregatedData {

    /**
     * Static factory method for getting an instance of {@link NoOpAggregatedData}.
     *
     * @return an instance of {@link NoOpAggregatedData}
     */
    public static AggregatedData getInstance() {
        return INSTANCE;
    }

    private NoOpAggregatedData() {}

    private static final AggregatedData INSTANCE = new NoOpAggregatedData();
}

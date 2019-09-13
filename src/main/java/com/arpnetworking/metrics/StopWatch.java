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
package com.arpnetworking.metrics;

import com.arpnetworking.metrics.impl.TsdStopWatch;

import java.util.concurrent.TimeUnit;

/**
 * Interface for a thread safe stop watch. The stop watch starts on creation
 * and must be stopped to access the elapsed time. Once stopped the instance
 * must be discarded and cannot be started again. The design is minimal to
 * reduce contention at the cost of object creation because timers in this
 * library represent samples and do not reuse their underlying stop watches.
 * However, it is possible to build a stateful timer around this but you
 * will need to add stricter synchronization or else give up thread safety.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public interface StopWatch {

    /**
     * Create and start a new {@link StopWatch} instance.
     *
     * @return new started {@link StopWatch} instance.
     */
    static StopWatch start() {
        return new TsdStopWatch();
    }

    /**
     * Determine if the stop watch is currently accumulating time.
     *
     * @return {@code true} if and only if the the stop watch is accumulating time.
     */
    boolean isRunning();

    /**
     * Stop the stop watch.
     */
    void stop();

    /**
     * Retrieve the elapsed time.
     *
     * @return the elapsed time as a {@link Quantity}.
     */
    Quantity getElapsedTime();

    /**
     * Retrieve the elapsed time unit.
     *
     * @return the elapsed time unit as a {@link TimeUnit}
     */
    TimeUnit getUnit();
}

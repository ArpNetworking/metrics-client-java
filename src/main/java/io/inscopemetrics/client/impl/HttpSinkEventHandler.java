/*
 * Copyright 2017 Inscope Metrics, Inc
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

import java.util.concurrent.TimeUnit;

/**
 * Interface for callbacks from {@link HttpSink}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public interface HttpSinkEventHandler {

    /**
     * Callback invoked when a request to data has completed.
     *
     * @param records the number of records sent
     * @param bytes the number of bytes sent
     * @param success success or failure
     * @param elapasedTime the elapsed time
     * @param elapsedTimeUnit the elapsed time unit
     */
    void attemptComplete(
            long records,
            long bytes,
            boolean success,
            long elapasedTime,
            TimeUnit elapsedTimeUnit);

    /**
     * Callback invoked when an {@link Event} is dropped from the queue.
     *
     * @param event the {@link Event} dropped from the queue
     */
    void droppedEvent(Event event);
}

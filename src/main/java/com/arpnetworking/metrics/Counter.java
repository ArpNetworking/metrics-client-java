/*
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
package com.arpnetworking.metrics;

/**
 * Interface for counter. Instances are initialized to zero on creation. The
 * zero-value sample is recorded when the {@link Metrics} instance is
 * closed if no other actions are taken on the {@link Counter}.
 * 
 * Modifying the {@link Counter} instance's value modifies the single
 * sample value. When the associated {@link Metrics} instance is closed
 * whatever value the sample has is recorded. To create another sample you
 * create a new {@link Counter} instance with the same name.
 * 
 * Each counter instance is bound to a {@link Metrics} instance. After the
 * {@link Metrics} instance is closed any modifications to the
 * {@link Counter} instances bound to that {@link Metrics} instance
 * will be ignored. 
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public interface Counter extends Quantity {

    /**
     * Increment the counter sample by 1.
     */
    void increment();

    /**
     * Decrement the counter sample by 1.
     */
    void decrement();

    /**
     * Increment the counter sample by the specified value.
     * 
     * @param value The value to increment the counter by.
     */
    void increment(long value);

    /**
     * Decrement the counter sample by the specified value.
     * 
     * @param value The value to decrement the counter by.
     */
    void decrement(long value);
}

/**
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
package com.arpnetworking.metrics.impl;

import com.arpnetworking.metrics.UuidFactory;

import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Uses a ThreadLocal SplittableRandom to create UUIDs.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
 */
public class NativeRandomUuidFactory implements UuidFactory {
    /**
     * Uses a ThreadLocal SplittableRandom to create UUIDs.
     *
     * @return A UUID.
     */
    @Override
    public UUID create() {
        final SplittableRandom random = _localRandom.get();
        long gMost = random.nextLong();
        gMost &= 0xffffffffffff0fffL;
        gMost |= 0x0000000000004000L;

        long gLeast = random.nextLong();
        gLeast &= 0x3fffffffffffffffL;
        gLeast |= 0x8000000000000000L;
        return new UUID(gMost, gLeast);
    }

    private final SplittableRandom _random = new SplittableRandom();
    private final ThreadLocal<SplittableRandom> _localRandom = ThreadLocal.withInitial(_random::split);
}

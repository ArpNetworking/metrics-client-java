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

import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

/**
 * Tests for the {@link NativeRandomUuidFactory}.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
 */
public class NativeRandomUuidFactoryTest {
    @Test
    public void testCreate() throws Exception {
        final NativeRandomUuidFactory factory = new NativeRandomUuidFactory();
        final UUID uuid = factory.create();
        Assert.assertEquals(4, uuid.version());
        Assert.assertEquals(2, uuid.variant());
    }
}

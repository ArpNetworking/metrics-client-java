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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tests for {@link TsdEvent}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TsdEventTest {

    private Instant _now;

    @Before
    public void setUp() {
        _now = Instant.now();
    }

    @Test
    public void test() {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final UUID id = UUID.randomUUID();
        final Map<String, String> dimensions = new HashMap<>();
        dimensions.put("foo", "bar");
        final Map<String, List<Quantity>> samples = new HashMap<>();
        samples.put("timer", Collections.singletonList(
                TsdQuantity.newInstance(1)));
        samples.put("counter", Collections.singletonList(
                TsdQuantity.newInstance(1.23)));
        samples.put("gauge", Collections.singletonList(
                TsdQuantity.newInstance(2.46)));
        final Map<String, AggregatedData> aggregatedData = new HashMap<>();
        aggregatedData.put("aggregatedMetric", NoOpAggregatedData.getInstance());
        // CHECKSTYLE.ON: IllegalInstantiation

        final Event event = new TsdEvent(
                id,
                _now,
                _now.plusSeconds(60),
                dimensions,
                samples,
                aggregatedData);

        Assert.assertEquals(id, event.getId());
        Assert.assertEquals(_now, event.getStartTime());
        Assert.assertEquals(_now.plusSeconds(60), event.getEndTime());
        Assert.assertEquals(dimensions, event.getDimensions());
        Assert.assertEquals(samples, event.getSamples());
        Assert.assertEquals(aggregatedData, event.getAggregatedData());
    }

    @Test
    public void testEquals() {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final UUID id = UUID.randomUUID();
        final Map<String, String> dimensions = new HashMap<>();
        dimensions.put("foo", "bar");
        final Map<String, List<Quantity>> samples = new HashMap<>();
        samples.put("timer", Collections.singletonList(TsdQuantity.newInstance(1)));
        samples.put("counter", Collections.singletonList(TsdQuantity.newInstance(1.23)));
        samples.put("gauge", Collections.singletonList(TsdQuantity.newInstance(2.46)));
        final Map<String, AggregatedData> aggregatedData = new HashMap<>();
        aggregatedData.put("aggregatedMetric", NoOpAggregatedData.getInstance());
        // CHECKSTYLE.ON: IllegalInstantiation

        final Event event = new TsdEvent(
                id,
                _now,
                _now.plusSeconds(60),
                dimensions,
                samples,
                aggregatedData);

        Assert.assertTrue(event.equals(event));

        Assert.assertFalse(event.equals(null));
        Assert.assertFalse(event.equals("This is a String"));

        final Event event2 = new TsdEvent(
                id,
                _now,
                _now.plusSeconds(60),
                dimensions,
                samples,
                aggregatedData);

        Assert.assertTrue(event.equals(event2));
    }

    @Test
    public void testNotEquals() {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final UUID id = UUID.randomUUID();
        final Map<String, String> dimensions = new HashMap<>();
        dimensions.put("foo", "bar");
        final Map<String, List<Quantity>> samples = new HashMap<>();
        samples.put("timer", Collections.singletonList(TsdQuantity.newInstance(1)));
        samples.put("counter", Collections.singletonList(TsdQuantity.newInstance(1.23)));
        samples.put("gauge", Collections.singletonList(TsdQuantity.newInstance(2.46)));
        final Map<String, AggregatedData> aggregatedData = new HashMap<>();
        aggregatedData.put("aggregatedMetric", NoOpAggregatedData.getInstance());
        // CHECKSTYLE.ON: IllegalInstantiation

        final Event event = new TsdEvent(
                id,
                _now,
                _now.plusSeconds(60),
                dimensions,
                samples,
                aggregatedData);

        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final UUID differentId = UUID.randomUUID();
        final Map<String, String> differentDimensions = new HashMap<>();
        differentDimensions.put("foo2", "bar");
        final Map<String, List<Quantity>> differentSamples = new HashMap<>();
        differentSamples.put("timer2", Collections.singletonList(TsdQuantity.newInstance(1)));
        differentSamples.put("counter2", Collections.singletonList(TsdQuantity.newInstance(1.23)));
        differentSamples.put("gauge2", Collections.singletonList(TsdQuantity.newInstance(2.46)));
        final Map<String, AggregatedData> differentAggregatedData = new HashMap<>();
        differentAggregatedData.put("aggregatedMetric2", NoOpAggregatedData.getInstance());
        // CHECKSTYLE.ON: IllegalInstantiation

        final Event differentEvent1 = new TsdEvent(
                differentId,
                _now.minusSeconds(60),
                _now.plusSeconds(60),
                dimensions,
                samples,
                aggregatedData);

        final Event differentEvent2 = new TsdEvent(
                id,
                _now.minusSeconds(60),
                _now.plusSeconds(60),
                dimensions,
                samples,
                aggregatedData);

        final Event differentEvent3 = new TsdEvent(
                id,
                _now,
                _now.plusSeconds(2),
                dimensions,
                samples,
                aggregatedData);

        final Event differentEvent4 = new TsdEvent(
                id,
                _now,
                _now.plusSeconds(60),
                differentDimensions,
                samples,
                aggregatedData);

        final Event differentEvent5 = new TsdEvent(
                id,
                _now,
                _now.plusSeconds(60),
                dimensions,
                differentSamples,
                aggregatedData);

        final Event differentEvent6 = new TsdEvent(
                id,
                _now,
                _now.plusSeconds(60),
                dimensions,
                samples,
                differentAggregatedData);

        Assert.assertFalse(event.equals(differentEvent1));
        Assert.assertFalse(event.equals(differentEvent2));
        Assert.assertFalse(event.equals(differentEvent3));
        Assert.assertFalse(event.equals(differentEvent4));
        Assert.assertFalse(event.equals(differentEvent5));
        Assert.assertFalse(event.equals(differentEvent6));
    }

    @Test
    public void testHashCode() {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final UUID id = UUID.randomUUID();
        final Map<String, String> dimensions = new HashMap<>();
        dimensions.put("foo", "bar");
        final Map<String, List<Quantity>> samples = new HashMap<>();
        samples.put("timer", Collections.singletonList(
                TsdQuantity.newInstance(1)));
        samples.put("counter", Collections.singletonList(
                TsdQuantity.newInstance(1.23)));
        samples.put("gauge", Collections.singletonList(
                TsdQuantity.newInstance(2.46)));
        final Map<String, AggregatedData> aggregatedData = new HashMap<>();
        aggregatedData.put("aggregatedMetric", NoOpAggregatedData.getInstance());
        // CHECKSTYLE.ON: IllegalInstantiation

        Assert.assertEquals(
                new TsdEvent(
                        id,
                        _now,
                        _now.plusSeconds(60),
                        dimensions,
                        samples,
                        aggregatedData).hashCode(),
                new TsdEvent(
                        id,
                        _now,
                        _now.plusSeconds(60),
                        dimensions,
                        samples,
                        aggregatedData).hashCode());
    }

    @Test
    public void testToString() {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final UUID id = UUID.randomUUID();
        final Map<String, String> dimensions = new HashMap<>();
        dimensions.put("foo", "bar");
        final Map<String, List<Quantity>> samples = new HashMap<>();
        samples.put("timer", Collections.singletonList(
                TsdQuantity.newInstance(1)));
        samples.put("counter", Collections.singletonList(
                TsdQuantity.newInstance(1.23)));
        samples.put("gauge", Collections.singletonList(
                TsdQuantity.newInstance(2.46)));
        final Map<String, AggregatedData> aggregatedData = new HashMap<>();
        aggregatedData.put("aggregatedMetric", NoOpAggregatedData.getInstance());
        // CHECKSTYLE.ON: IllegalInstantiation

        final String asString = new TsdEvent(
                id,
                _now,
                _now.plusSeconds(60),
                dimensions,
                samples,
                aggregatedData).toString();

        Assert.assertNotNull(asString);
        Assert.assertFalse(asString.isEmpty());
    }
}

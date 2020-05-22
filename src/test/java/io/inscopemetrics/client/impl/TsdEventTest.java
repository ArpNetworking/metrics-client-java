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
package io.inscopemetrics.client.impl;

import io.inscopemetrics.client.AggregatedData;
import io.inscopemetrics.client.Event;
import io.inscopemetrics.client.Quantity;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link TsdEvent}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TsdEventTest {

    private Instant now;

    @Before
    public void setUp() {
        now = Instant.now();
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
        aggregatedData.put("aggregatedMetric", new NoOpAggregatedData());
        // CHECKSTYLE.ON: IllegalInstantiation

        final Event event = new TsdEvent(
                id,
                now,
                now.plusSeconds(60),
                dimensions,
                samples,
                aggregatedData);

        assertEquals(id, event.getId());
        assertEquals(now, event.getStartTime());
        assertEquals(now.plusSeconds(60), event.getEndTime());
        assertEquals(dimensions, event.getDimensions());
        assertEquals(samples, event.getSamples());
        assertEquals(aggregatedData, event.getAggregatedData());
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
        aggregatedData.put("aggregatedMetric", new NoOpAggregatedData());
        // CHECKSTYLE.ON: IllegalInstantiation

        final Event event = new TsdEvent(
                id,
                now,
                now.plusSeconds(60),
                dimensions,
                samples,
                aggregatedData);

        assertTrue(event.equals(event));

        assertFalse(event.equals(null));
        assertFalse(event.equals("This is a String"));

        final Event event2 = new TsdEvent(
                id,
                now,
                now.plusSeconds(60),
                dimensions,
                samples,
                aggregatedData);

        assertTrue(event.equals(event2));
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
        aggregatedData.put("aggregatedMetric", new NoOpAggregatedData());
        // CHECKSTYLE.ON: IllegalInstantiation

        final Event event = new TsdEvent(
                id,
                now,
                now.plusSeconds(60),
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
        differentAggregatedData.put("aggregatedMetric2", new NoOpAggregatedData());
        // CHECKSTYLE.ON: IllegalInstantiation

        final Event differentEvent1 = new TsdEvent(
                differentId,
                now.minusSeconds(60),
                now.plusSeconds(60),
                dimensions,
                samples,
                aggregatedData);

        final Event differentEvent2 = new TsdEvent(
                id,
                now.minusSeconds(60),
                now.plusSeconds(60),
                dimensions,
                samples,
                aggregatedData);

        final Event differentEvent3 = new TsdEvent(
                id,
                now,
                now.plusSeconds(2),
                dimensions,
                samples,
                aggregatedData);

        final Event differentEvent4 = new TsdEvent(
                id,
                now,
                now.plusSeconds(60),
                differentDimensions,
                samples,
                aggregatedData);

        final Event differentEvent5 = new TsdEvent(
                id,
                now,
                now.plusSeconds(60),
                dimensions,
                differentSamples,
                aggregatedData);

        final Event differentEvent6 = new TsdEvent(
                id,
                now,
                now.plusSeconds(60),
                dimensions,
                samples,
                differentAggregatedData);

        assertFalse(event.equals(differentEvent1));
        assertFalse(event.equals(differentEvent2));
        assertFalse(event.equals(differentEvent3));
        assertFalse(event.equals(differentEvent4));
        assertFalse(event.equals(differentEvent5));
        assertFalse(event.equals(differentEvent6));
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
        aggregatedData.put("aggregatedMetric", new NoOpAggregatedData());
        // CHECKSTYLE.ON: IllegalInstantiation

        assertEquals(
                new TsdEvent(
                        id,
                        now,
                        now.plusSeconds(60),
                        dimensions,
                        samples,
                        aggregatedData).hashCode(),
                new TsdEvent(
                        id,
                        now,
                        now.plusSeconds(60),
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
        aggregatedData.put("aggregatedMetric", new NoOpAggregatedData());
        // CHECKSTYLE.ON: IllegalInstantiation

        final String asString = new TsdEvent(
                id,
                now,
                now.plusSeconds(60),
                dimensions,
                samples,
                aggregatedData).toString();

        assertNotNull(asString);
        assertFalse(asString.isEmpty());
    }
}

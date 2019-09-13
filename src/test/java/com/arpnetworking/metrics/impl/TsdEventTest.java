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
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link TsdEvent}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TsdEventTest {

    @Test
    public void test() {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<String, String> annotations = new HashMap<>();
        annotations.put("foo", "bar");
        final Map<String, List<Quantity>> timerSamples = new HashMap<>();
        timerSamples.put("timer", Collections.singletonList(
                TsdQuantity.newInstance(1)));
        final Map<String, List<Quantity>> counterSamples = new HashMap<>();
        counterSamples.put("counter", Collections.singletonList(
                TsdQuantity.newInstance(1.23)));
        final Map<String, List<Quantity>> gaugeSamples = new HashMap<>();
        gaugeSamples.put("gauge", Collections.singletonList(
                TsdQuantity.newInstance(1.23)));
        final Map<String, List<AggregatedData>> aggregatedData = new HashMap<>();
        aggregatedData.put("aggregatedMetric", Collections.singletonList(NoOpAggregatedData.getInstance()));
        // CHECKSTYLE.ON: IllegalInstantiation
        final Event event = new TsdEvent(
                annotations,
                timerSamples,
                counterSamples,
                gaugeSamples,
                aggregatedData);

        Assert.assertEquals(annotations, event.getAnnotations());
        Assert.assertEquals(timerSamples, event.getTimerSamples());
        Assert.assertEquals(counterSamples, event.getCounterSamples());
        Assert.assertEquals(gaugeSamples, event.getGaugeSamples());
        Assert.assertEquals(aggregatedData, event.getAggregatedData());
    }

    @Test
    public void testEquals() {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<String, String> annotations = new HashMap<>();
        annotations.put("foo", "bar");
        final Map<String, List<Quantity>> timerSamples = new HashMap<>();
        timerSamples.put("timer", Collections.singletonList(
                TsdQuantity.newInstance(1)));
        final Map<String, List<Quantity>> counterSamples = new HashMap<>();
        counterSamples.put("counter", Collections.singletonList(
                TsdQuantity.newInstance(1.23)));
        final Map<String, List<Quantity>> gaugeSamples = new HashMap<>();
        gaugeSamples.put("gauge", Collections.singletonList(
                TsdQuantity.newInstance(1.23)));
        final Map<String, List<AggregatedData>> aggregatedData = new HashMap<>();
        aggregatedData.put("aggregatedMetric", Collections.singletonList(NoOpAggregatedData.getInstance()));
        // CHECKSTYLE.ON: IllegalInstantiation
        final Event event = new TsdEvent(
                annotations,
                timerSamples,
                counterSamples,
                gaugeSamples,
                aggregatedData);

        Assert.assertTrue(event.equals(event));

        Assert.assertFalse(event.equals(null));
        Assert.assertFalse(event.equals("This is a String"));

        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<String, String> differentAnnotations = new HashMap<>();
        annotations.put("foo2", "bar");
        final Map<String, List<Quantity>> differentTimerSamples = new HashMap<>();
        differentTimerSamples.put("timer2", Collections.singletonList(
                TsdQuantity.newInstance(1)));
        final Map<String, List<Quantity>> differentCounterSamples = new HashMap<>();
        differentCounterSamples.put("counter2", Collections.singletonList(
                TsdQuantity.newInstance(1.23)));
        final Map<String, List<Quantity>> differentGaugeSamples = new HashMap<>();
        differentGaugeSamples.put("gauge2", Collections.singletonList(
                TsdQuantity.newInstance(1.23)));
        final Map<String, List<AggregatedData>> differentAggregatedData = new HashMap<>();
        aggregatedData.put("aggregatedMetric2", Collections.singletonList(NoOpAggregatedData.getInstance()));
        // CHECKSTYLE.ON: IllegalInstantiation

        final Event differentEvent1 = new TsdEvent(
                differentAnnotations,
                timerSamples,
                counterSamples,
                gaugeSamples,
                aggregatedData);

        final Event differentEvent2 = new TsdEvent(
                annotations,
                differentTimerSamples,
                counterSamples,
                gaugeSamples,
                aggregatedData);

        final Event differentEvent3 = new TsdEvent(
                annotations,
                timerSamples,
                differentCounterSamples,
                gaugeSamples,
                aggregatedData);

        final Event differentEvent4 = new TsdEvent(
                annotations,
                timerSamples,
                counterSamples,
                differentGaugeSamples,
                aggregatedData);

        final Event differentEvent5 = new TsdEvent(
                annotations,
                timerSamples,
                counterSamples,
                gaugeSamples,
                differentAggregatedData);

        Assert.assertFalse(event.equals(differentEvent1));
        Assert.assertFalse(event.equals(differentEvent2));
        Assert.assertFalse(event.equals(differentEvent3));
        Assert.assertFalse(event.equals(differentEvent4));
        Assert.assertFalse(event.equals(differentEvent5));
    }

    @Test
    public void testHashCode() {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<String, String> annotations = new HashMap<>();
        annotations.put("foo", "bar");
        final Map<String, List<Quantity>> timerSamples = new HashMap<>();
        timerSamples.put("timer", Collections.singletonList(
                TsdQuantity.newInstance(1)));
        final Map<String, List<Quantity>> counterSamples = new HashMap<>();
        counterSamples.put("counter", Collections.singletonList(
                TsdQuantity.newInstance(1.23)));
        final Map<String, List<Quantity>> gaugeSamples = new HashMap<>();
        gaugeSamples.put("gauge", Collections.singletonList(
                TsdQuantity.newInstance(1.23)));
        final Map<String, List<AggregatedData>> aggregatedData = new HashMap<>();
        aggregatedData.put("aggregatedMetric", Collections.singletonList(NoOpAggregatedData.getInstance()));
        // CHECKSTYLE.ON: IllegalInstantiation

        Assert.assertEquals(
                new TsdEvent(
                        annotations,
                        timerSamples,
                        counterSamples,
                        gaugeSamples,
                        aggregatedData).hashCode(),
                new TsdEvent(
                        annotations,
                        timerSamples,
                        counterSamples,
                        gaugeSamples,
                        aggregatedData).hashCode());
    }

    @Test
    public void testToString() {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<String, String> annotations = new HashMap<>();
        annotations.put("foo", "bar");
        final Map<String, List<Quantity>> timerSamples = new HashMap<>();
        timerSamples.put("timer", Collections.singletonList(
                TsdQuantity.newInstance(1)));
        final Map<String, List<Quantity>> counterSamples = new HashMap<>();
        counterSamples.put("counter", Collections.singletonList(
                TsdQuantity.newInstance(1.23)));
        final Map<String, List<Quantity>> gaugeSamples = new HashMap<>();
        gaugeSamples.put("gauge", Collections.singletonList(
                TsdQuantity.newInstance(1.23)));
        final Map<String, List<AggregatedData>> aggregatedData = new HashMap<>();
        aggregatedData.put("aggregatedMetric", Collections.singletonList(NoOpAggregatedData.getInstance()));
        // CHECKSTYLE.ON: IllegalInstantiation
        final String asString = new TsdEvent(
                annotations,
                timerSamples,
                counterSamples,
                gaugeSamples,
                aggregatedData).toString();

        Assert.assertNotNull(asString);
        Assert.assertFalse(asString.isEmpty());
    }
}

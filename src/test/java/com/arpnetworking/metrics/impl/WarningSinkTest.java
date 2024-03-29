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

import com.arpnetworking.metrics.Event;
import com.arpnetworking.metrics.Sink;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.hamcrest.MockitoHamcrest;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link WarningSink}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class WarningSinkTest {

    @Test
    public void testWarningSink() {
        final List<String> reasons = new ArrayList<>();
        reasons.add("reason1");
        reasons.add("reason2");

        final Logger logger = Mockito.mock(Logger.class);
        final Event event = Mockito.mock(Event.class);
        final Sink sink = new WarningSink.Builder()
                .setReasons(reasons)
                .setLogger(logger)
                .build();

        final String asString = sink.toString();
        Assert.assertNotNull(asString);
        Assert.assertFalse(asString.isEmpty());
        Assert.assertTrue(asString.contains(reasons.get(0)));
        Assert.assertTrue(asString.contains(reasons.get(1)));

        sink.record(event);

        Mockito.verifyNoInteractions(event);
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(
                Matchers.containsString(reasons.toString())));
    }

    @Test
    @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
    public void testWarningSinkBuilderNullReasons() {
        final Logger logger = Mockito.mock(Logger.class);
        final Event event = Mockito.mock(Event.class);
        final Sink sink = new WarningSink.Builder()
                .setReasons(null)
                .setLogger(logger)
                .build();

        sink.record(event);

        Mockito.verifyNoInteractions(event);
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(
                Matchers.containsString("Reasons must be a non-null list")));
    }

    @Test
    public void testWarningSinkBuilderEmptyReasons() {
        final Logger logger = Mockito.mock(Logger.class);
        final Event event = Mockito.mock(Event.class);
        final Sink sink = new WarningSink.Builder()
                .setReasons(Collections.emptyList())
                .setLogger(logger)
                .build();

        sink.record(event);

        Mockito.verifyNoInteractions(event);
        Mockito.verify(logger).warn(MockitoHamcrest.argThat(
                Matchers.containsString("Reasons must be a non-empty list")));
    }
}

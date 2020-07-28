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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.inscopemetrics.client.Event;
import io.inscopemetrics.client.Sink;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.mockito.hamcrest.MockitoHamcrest;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

        final Logger logger = mock(Logger.class);
        final Event event = mock(Event.class);
        final Sink sink = new WarningSink.Builder()
                .setReasons(reasons)
                .setLogger(logger)
                .build();

        final String asString = sink.toString();
        assertNotNull(asString);
        assertFalse(asString.isEmpty());
        assertTrue(asString.contains(reasons.get(0)));
        assertTrue(asString.contains(reasons.get(1)));

        sink.record(event);

        verifyNoInteractions(event);
        verify(logger).warn(MockitoHamcrest.argThat(
                Matchers.containsString(reasons.toString())));
    }

    @Test
    @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
    public void testWarningSinkBuilderNullReasons() {
        final Logger logger = mock(Logger.class);
        final Event event = mock(Event.class);
        final Sink sink = new WarningSink.Builder()
                .setReasons(null)
                .setLogger(logger)
                .build();

        sink.record(event);

        verifyNoInteractions(event);
        verify(logger).warn(MockitoHamcrest.argThat(
                Matchers.containsString("Reasons must be a non-null list")));
    }

    @Test
    public void testWarningSinkBuilderEmptyReasons() {
        final Logger logger = mock(Logger.class);
        final Event event = mock(Event.class);
        final Sink sink = new WarningSink.Builder()
                .setReasons(Collections.emptyList())
                .setLogger(logger)
                .build();

        sink.record(event);

        verifyNoInteractions(event);
        verify(logger).warn(MockitoHamcrest.argThat(
                Matchers.containsString("Reasons must be a non-empty list")));
    }
}

/**
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
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for <code>WarningSink</code>.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class WarningSinkTest {

    @Test
    public void testWarningSink() {
        final List<String> reasons = new ArrayList<>();
        reasons.add("reason1");
        reasons.add("reason2");

        final Logger logger = Mockito.mock(Logger.class);
        final Event event = Mockito.mock(Event.class);
        final WarningSink sink = new WarningSink(reasons, logger);

        final String asString = sink.toString();
        Assert.assertNotNull(asString);
        Assert.assertFalse(asString.isEmpty());
        Assert.assertTrue(asString.contains(reasons.get(0)));
        Assert.assertTrue(asString.contains(reasons.get(1)));

        sink.record(event);

        Mockito.verifyZeroInteractions(event);
        Mockito.verify(logger).warn(Mockito.argThat(Matchers.containsString(reasons.toString())));
    }
}

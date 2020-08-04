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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.inscopemetrics.client.MetricsFactory;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;

import java.time.Clock;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the {@link ThreadSafePeriodicMetrics} class.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public class ThreadSafePeriodicMetricsTest {

    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public final MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Mock(answer = Answers.RETURNS_MOCKS)
    private MetricsFactory factory;

    @Test
    @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
    public void testWarnsOnNullExecutor() throws Exception {
        final Logger logger = Mockito.mock(Logger.class);
        new ThreadSafePeriodicMetrics.Builder(
                logger,
                new WarningScopedMetrics(
                        Clock.systemUTC(),
                        logger,
                        "ThreadSafePeriodicMetricsTest"))
                .setMetricsFactory(factory)
                .setPollingExecutor(null)
                .build();
        Mockito.verify(logger).warn(Mockito.anyString());
    }

    @Test
    public void testToString() {
        final String asString = new ThreadSafePeriodicMetrics.Builder()
                .setMetricsFactory(factory)
                .build()
                .toString();
        assertNotNull(asString);
        assertTrue(asString.contains("ThreadSafePeriodicMetrics"));
    }
}

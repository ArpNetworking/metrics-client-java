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
import io.inscopemetrics.client.PeriodicMetrics;
import io.inscopemetrics.client.ScopedMetrics;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;

import java.time.Clock;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@link LockFreePeriodicMetrics} class.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
@RunWith(Parameterized.class)
public class PeriodicMetricsCommonTest {

    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public final MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Mock
    private Logger logger;
    @Mock
    private ScopedMetrics scopedMetrics;
    @Mock(answer = Answers.RETURNS_MOCKS)
    private MetricsFactory factory;

    private WarningScopedMetrics warningScopedMetrics;
    private final MetricsProxy metricsProxy;

    public PeriodicMetricsCommonTest(final MetricsProxy metricsProxy) {
        this.metricsProxy = metricsProxy;
    }

    @Before
    public void setUp() {
        warningScopedMetrics = new WarningScopedMetrics(Clock.systemUTC(), logger, "PeriodicMetricsCommonTest");
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> getParameters() {
        return Arrays.asList(
                new Object[][] {
                        {new ThreadSafePeriodicMetricsProxy()},
                        {new LockFreePeriodicMetricsProxy()}
                });
    }

    @Test
    @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
    public void testWarnsOnNullFactory() throws Exception {
        metricsProxy.create(null, logger, warningScopedMetrics);
        verify(logger).warn(anyString());
    }

    @Test
    public void testCallsFactoryCreateForInitialMetricInstance() throws Exception {
        metricsProxy.create(factory);
        metricsProxy.verifyScopedCreation(factory);
    }

    @Test
    public void testRecordCounter() {
        metricsProxy.setupFactory(factory, scopedMetrics);
        final PeriodicMetrics periodicMetrics = metricsProxy.create(factory);

        final String name = "foo";
        final int value = 1;

        periodicMetrics.recordCounter(name, value);
        verify(scopedMetrics).recordCounter(name, 1);
    }

    @Test
    public void testRecordTimerUnit() throws Exception {
        metricsProxy.setupFactory(factory, scopedMetrics);
        final PeriodicMetrics periodicMetrics = metricsProxy.create(factory);

        final String name = "foo";
        final long value = 1;

        periodicMetrics.recordTimer(name, value, TimeUnit.MILLISECONDS);
        verify(scopedMetrics).recordTimer(name, value, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testRecordGaugeLong() throws Exception {
        metricsProxy.setupFactory(factory, scopedMetrics);
        final PeriodicMetrics periodicMetrics = metricsProxy.create(factory);

        final String name = "foo";
        final long value = 1;

        periodicMetrics.recordGauge(name, value);
        verify(scopedMetrics).recordGauge(name, value);
    }

    @Test
    public void testRecordGaugeDouble() {
        metricsProxy.setupFactory(factory, scopedMetrics);
        final PeriodicMetrics periodicMetrics = metricsProxy.create(factory);

        final String name = "foo";
        final double value = 1;

        periodicMetrics.recordGauge(name, value);
        verify(scopedMetrics).recordGauge(name, value);
    }

    @Test
    public void testCallsRegisteredMetrics() {
        final ScopedMetrics newScopedMetricsMock = mock(ScopedMetrics.class);
        metricsProxy.setupFactory(factory, scopedMetrics, newScopedMetricsMock);
        final PeriodicMetrics periodicMetrics = metricsProxy.create(factory);

        periodicMetrics.registerPolledMetric(metrics -> metrics.recordCounter("bar", 1));
        metricsProxy.execute(periodicMetrics);
        verify(scopedMetrics).recordCounter("bar", 1);
        verify(scopedMetrics).close();
        metricsProxy.execute(periodicMetrics);
        verifyNoMoreInteractions(scopedMetrics);
        verify(newScopedMetricsMock).recordCounter("bar", 1);
        verify(newScopedMetricsMock).close();
    }

    @Test
    public void testRegisterManyCallbacks() {
        final ScopedMetrics newScopedMetricsMock = mock(ScopedMetrics.class);
        metricsProxy.setupFactory(factory, scopedMetrics, newScopedMetricsMock);
        final PeriodicMetrics periodicMetrics = metricsProxy.create(factory);

        for (int x = 0; x < 1000; x++) {
            final Integer val = x;
            periodicMetrics.registerPolledMetric(metrics -> metrics.recordCounter("bar" + val, 1));
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testCallbackThrows() throws Throwable {
        metricsProxy.setupFactory(factory, scopedMetrics);
        final PeriodicMetrics periodicMetrics = metricsProxy.create(factory);
        periodicMetrics.registerPolledMetric(metrics -> { throw new IllegalStateException("This is a test"); });
        try {
            metricsProxy.execute(periodicMetrics);
        } catch (final RuntimeException e) {
            Throwable rethrowAs = e;
            if (e.getCause() instanceof ExecutionException) {
                rethrowAs = e.getCause().getCause();
            }
            throw rethrowAs;
        }
    }

    @Test
    public void testCloseFlushes() {
        metricsProxy.setupFactory(factory, scopedMetrics);
        final PeriodicMetrics periodicMetrics = metricsProxy.create(factory);
        periodicMetrics.recordGauge("foo", 1);
        verify(scopedMetrics).recordGauge("foo", 1);
        periodicMetrics.close();
        verify(scopedMetrics).close();
        verifyNoMoreInteractions(scopedMetrics);
    }

    @Test
    public void testNoRecordingAfterClose() {
        metricsProxy.setupFactory(factory, scopedMetrics);
        final PeriodicMetrics periodicMetrics = metricsProxy.create(factory);
        periodicMetrics.close();
        periodicMetrics.recordGauge("foo", 1);
        verify(scopedMetrics).close();
        periodicMetrics.close();
        verifyNoMoreInteractions(scopedMetrics);
    }

    @Test
    public void testWarnOnRecordAfterClose() {
        metricsProxy.setupFactory(factory, scopedMetrics);
        final PeriodicMetrics periodicMetrics = metricsProxy.create(factory, logger, warningScopedMetrics);
        periodicMetrics.close();
        verifyNoInteractions(logger);
        verify(scopedMetrics).close();

        periodicMetrics.recordGauge("foo", 1);
        verify(logger).warn(startsWith("Invalid use of scoped metrics:"));
        verifyNoMoreInteractions(scopedMetrics);
    }

    private interface MetricsProxy {

        PeriodicMetrics create(@Nullable MetricsFactory factory);

        PeriodicMetrics create(
                @Nullable MetricsFactory factory,
                Logger logger,
                WarningScopedMetrics warningScopedMetrics);

        void setupFactory(MetricsFactory factory, ScopedMetrics... scopedMetrics);

        void execute(PeriodicMetrics periodicMetrics);

        void verifyScopedCreation(MetricsFactory factory);
    }

    private static final class ThreadSafePeriodicMetricsProxy implements MetricsProxy {

        @Override
        @SuppressFBWarnings("NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE")
        public PeriodicMetrics create(@Nullable final MetricsFactory factory) {
            return new ThreadSafePeriodicMetrics.Builder()
                    .setMetricsFactory(factory)
                    .build();
        }

        @Override
        @SuppressFBWarnings("NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE")
        public PeriodicMetrics create(
                @Nullable final MetricsFactory factory,
                final Logger logger,
                final WarningScopedMetrics warningScopedMetrics) {
            return new ThreadSafePeriodicMetrics.Builder(logger, warningScopedMetrics)
                    .setMetricsFactory(factory)
                    .build();
        }

        @Override
        public void setupFactory(final MetricsFactory factory, final ScopedMetrics... scopedMetrics) {
            if (scopedMetrics.length == 1) {
                when(factory.createScopedMetrics()).thenReturn(scopedMetrics[0]);
            } else if (scopedMetrics.length > 1) {
                when(factory.createScopedMetrics()).thenReturn(
                        scopedMetrics[0],
                        Arrays.copyOfRange(scopedMetrics, 1, scopedMetrics.length));
            } else {
                throw new IllegalArgumentException("Must specify at least one ScopedMetrics");
            }
        }

        @Override
        public void execute(final PeriodicMetrics periodicMetrics) {
            if (!(periodicMetrics instanceof ThreadSafePeriodicMetrics)) {
                throw new IllegalArgumentException("Invalid periodic metrics type: " + periodicMetrics.getClass());
            }
            ((ThreadSafePeriodicMetrics) periodicMetrics).run();
        }

        @Override
        public void verifyScopedCreation(final MetricsFactory factory) {
            verify(factory).createScopedMetrics();
        }
    }

    private static final class LockFreePeriodicMetricsProxy implements MetricsProxy {

        @Override
        @SuppressFBWarnings("NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE")
        public PeriodicMetrics create(@Nullable final MetricsFactory factory) {
            return new LockFreePeriodicMetrics.Builder()
                    .setMetricsFactory(factory)
                    .build();
        }

        @Override
        @SuppressFBWarnings("NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE")
        public PeriodicMetrics create(
                @Nullable final MetricsFactory factory,
                final Logger logger,
                final WarningScopedMetrics warningScopedMetrics) {
            return new LockFreePeriodicMetrics.Builder(logger, warningScopedMetrics)
                    .setMetricsFactory(factory)
                    .build();
        }

        @Override
        public void setupFactory(final MetricsFactory factory, final ScopedMetrics... scopedMetrics) {
            if (scopedMetrics.length == 1) {
                when(factory.createLockFreeScopedMetrics()).thenReturn(scopedMetrics[0]);
            } else if (scopedMetrics.length > 1) {
                when(factory.createLockFreeScopedMetrics()).thenReturn(
                        scopedMetrics[0],
                        Arrays.copyOfRange(scopedMetrics, 1, scopedMetrics.length));
            } else {
                throw new IllegalArgumentException("Must specify at least one ScopedMetrics");
            }
        }

        @Override
        public void execute(final PeriodicMetrics periodicMetrics) {
            if (!(periodicMetrics instanceof LockFreePeriodicMetrics)) {
                throw new IllegalArgumentException("Invalid periodic metrics type: " + periodicMetrics.getClass());
            }
            ((LockFreePeriodicMetrics) periodicMetrics).recordPeriodicMetrics();
        }

        @Override
        public void verifyScopedCreation(final MetricsFactory factory) {
            verify(factory).createLockFreeScopedMetrics();
        }
    }
}

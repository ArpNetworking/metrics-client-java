/*
 * Copyright 2020 Dropbox
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
import io.inscopemetrics.client.ScopedMetrics;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for {@link PeriodicMetricsExecutor}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class PeriodicMetricsExecutorTest {

    // 7/1/2020 00:00:00.000 UTC
    private static final Instant JULY_FIRST = Instant.ofEpochMilli(1593561600000L);

    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public final MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Mock
    private Logger logger;
    @Mock
    private MetricsFactory metricsFactory;
    @Mock
    private ScopedMetrics scopedMetrics;

    @Test
    public void testBuilderDefaults() {
        final PeriodicMetricsExecutor executor = new PeriodicMetricsExecutor.Builder().build();
        assertDefaults(executor);
    }

    @Test
    public void testBuilderNullCloseTimeout() {
        final PeriodicMetricsExecutor executor = new PeriodicMetricsExecutor.Builder(Clock.systemUTC(), logger)
                .setCloseTimeout(null)
                .build();

        verify(logger).info(startsWith("Defaulted null close timeout; closeTimeout="));

        assertDefaults(executor);
    }

    @Test
    public void testBuilderNullOffsetPercent() {
        final PeriodicMetricsExecutor executor = new PeriodicMetricsExecutor.Builder(Clock.systemUTC(), logger)
                .setOffsetPercent(null)
                .build();

        verify(logger).info(startsWith("Defaulted null offset percent; offsetPercent="));

        assertDefaults(executor);
    }

    @Test
    public void testBuilderOffsetPercentTooSmall() {
        final PeriodicMetricsExecutor executor = new PeriodicMetricsExecutor.Builder(Clock.systemUTC(), logger)
                .setOffsetPercent(-1.0f)
                .build();

        verify(logger).info(startsWith("Adjusted invalid (<0.0) offset percent; offsetPercent="));

        assertEquals(0.0, executor.getOffsetPercent(), 0.001);
    }

    @Test
    public void testBuilderOffsetPercentTooLarge() {
        final PeriodicMetricsExecutor executor = new PeriodicMetricsExecutor.Builder(Clock.systemUTC(), logger)
                .setOffsetPercent(2.0f)
                .build();

        verify(logger).info(startsWith("Adjusted invalid (>1.0) offset percent; offsetPercent="));

        assertEquals(1.0, executor.getOffsetPercent(), 0.001);
    }

    @Test
    public void testBuilderNullExceededIntervalLoggingInterval() {
        final PeriodicMetricsExecutor executor = new PeriodicMetricsExecutor.Builder(Clock.systemUTC(), logger)
                .setTaskExceededIntervalLoggingInterval(null)
                .build();

        verify(logger).info(startsWith(
                "Defaulted null task exceeded interval logging interval; taskExceededIntervalLoggingInterval="));

        assertDefaults(executor);
    }

    @Test
    public void testTask() {
        final AtomicBoolean hasRun = new AtomicBoolean(false);
        final AtomicReference<PeriodicMetricsExecutor.Task> taskRescheduled = new AtomicReference<>(null);
        final Duration interval = Duration.ofSeconds(1);
        final Instant firstRun = Instant.now();
        final Instant secondRun = firstRun.plusSeconds(1);

        final PeriodicMetricsExecutor.Task task = new PeriodicMetricsExecutor.Task(
                () -> hasRun.set(true),
                interval,
                firstRun,
                taskRescheduled::set);

        assertEquals(interval, task.getInterval());
        assertEquals(firstRun, task.setNextRun(secondRun));
        assertEquals(secondRun, task.setNextRun(secondRun.plusSeconds(1)));

        task.run();

        assertTrue(hasRun.get());
        assertSame(task, taskRescheduled.get());

        final String taskAsString = task.toString();
        assertNotNull(taskAsString);
        assertTrue(taskAsString.contains("runnable="));
        assertTrue(taskAsString.contains("interval="));
    }

    @Test
    public void testComputeNextRunOffsetPercent() {
        // Schedule at beginning of edge
        assertEquals(
                JULY_FIRST.plusSeconds(1),
                PeriodicMetricsExecutor.computeNextRun(
                        JULY_FIRST.plusMillis(750),
                        Duration.ofSeconds(1),
                        0.0f));

        // Scheduled between edges
        assertEquals(
                JULY_FIRST.plusSeconds(1).plusMillis(500),
                PeriodicMetricsExecutor.computeNextRun(
                        JULY_FIRST.plusMillis(750),
                        Duration.ofSeconds(1),
                        0.5f));

        // Schedule at end edge
        // NOTE: Since it's at the end of an interval, it actually
        // gets scheduled at the end of the interval we happen to be in!
        assertEquals(
                JULY_FIRST.plusSeconds(1),
                PeriodicMetricsExecutor.computeNextRun(
                        JULY_FIRST.plusMillis(750),
                        Duration.ofSeconds(1),
                        1.0f));
    }

    @Test
    public void testComputeNextRunOnEdge() {
        assertEquals(
                JULY_FIRST.plusSeconds(1),
                PeriodicMetricsExecutor.computeNextRun(
                        JULY_FIRST,
                        Duration.ofSeconds(1),
                        0.0f));
    }

    @Test
    public void testDivisibleAlignment() {
        assertEquals(
                JULY_FIRST.plus(35, ChronoUnit.MINUTES),
                PeriodicMetricsExecutor.computeNextRun(
                        JULY_FIRST.plus(33, ChronoUnit.MINUTES)
                                .plus(32, ChronoUnit.SECONDS)
                                .plus(31, ChronoUnit.MILLIS),
                        Duration.ofMinutes(5),
                        0.0f));
    }

    @Test
    public void testIndivisibleAlignment() {
        // There are 3,794,120 whole 7-minute intervals since epoch to July
        // 1st, 2020, which takes us to July 1st, 2020 at 00:33:00 AM. So
        // the next 7-minute period is at 00:40:00 AM.

        assertEquals(
                JULY_FIRST.plus(40, ChronoUnit.MINUTES),
                PeriodicMetricsExecutor.computeNextRun(
                        JULY_FIRST.plus(35, ChronoUnit.MINUTES)
                                .plus(34, ChronoUnit.SECONDS)
                                .plus(33, ChronoUnit.MILLIS),
                        Duration.ofMinutes(7),
                        0.0f));
    }

    @Test
    public void testClose() throws Exception {
        doReturn(scopedMetrics).when(metricsFactory).createScopedMetrics();

        // Create an executor and periodic metrics with an initial scoped metrics
        final PeriodicMetricsExecutor executor = new PeriodicMetricsExecutor.Builder()
                .build();
        final ThreadSafePeriodicMetrics periodicMetrics = new ThreadSafePeriodicMetrics.Builder()
                .setMetricsFactory(metricsFactory)
                .build();
        verify(metricsFactory).createScopedMetrics();

        // Register a callback that should never be invoked (by interval or on close)
        periodicMetrics.registerPolledMetric(metrics -> metrics.recordGauge("foo", 1));
        executor.scheduleAtFixedRate(periodicMetrics, Duration.ofDays(365));

        // Close the executor
        executor.close();

        // Verify the original scope is closed
        verify(scopedMetrics).close();

        // Additional close should have no impact
        executor.close();

        // No more scopes should have been recorded and no metrics recorded against them
        verifyNoMoreInteractions(metricsFactory);
        verifyNoMoreInteractions(scopedMetrics);
    }

    @Test
    public void testCloseForcefully() throws Exception {
        final ScopedMetrics nextScopedMetrics = mock(ScopedMetrics.class);
        doReturn(scopedMetrics, nextScopedMetrics).when(metricsFactory).createScopedMetrics();

        final Semaphore semaphore = new Semaphore(0);

        // Create an executor and periodic metrics with an initial scoped metrics
        final PeriodicMetricsExecutor executor = new PeriodicMetricsExecutor.Builder()
                .setCloseTimeout(Duration.ofMillis(10))
                .build();
        final ThreadSafePeriodicMetrics periodicMetrics = new ThreadSafePeriodicMetrics.Builder()
                .setMetricsFactory(metricsFactory)
                .build();
        verify(metricsFactory).createScopedMetrics();

        // Create a "long" callback; the expectation is that it is interrupted
        periodicMetrics.registerPolledMetric(metrics -> {
            try {
                metrics.recordGauge("foo", 1);
                semaphore.release();
                Thread.sleep(1000);
                metrics.recordGauge("bar", 1);
            } catch (final InterruptedException e) {
                // Nothing to do
            }
        });
        executor.scheduleAtFixedRate(periodicMetrics, Duration.ofMillis(5));

        // Once we know the callback is blocked; try closing the executor
        semaphore.acquire();
        executor.close();

        // Only first metric should have been recorded and flushed
        verify(scopedMetrics).recordGauge("foo", 1);

        // The completion of the callback evaluation also meant that
        // the periodic metrics instance replaced its scoped metrics
        verify(metricsFactory, times(2)).createScopedMetrics();

        // Closed at the end of the callback processing
        verify(scopedMetrics).close();

        // Closed on close of the executor (and periodic metrics)
        verify(nextScopedMetrics).close();

        verifyNoMoreInteractions(metricsFactory);
        verifyNoMoreInteractions(scopedMetrics);
        verifyNoMoreInteractions(nextScopedMetrics);
    }

    @Test
    public void testCloseInterrupted() throws Exception {
        final ScopedMetrics nextScopedMetrics = mock(ScopedMetrics.class);
        doReturn(scopedMetrics, nextScopedMetrics).when(metricsFactory).createScopedMetrics();

        final Semaphore semaphore = new Semaphore(0);

        // Create an executor and periodic metrics with an initial scoped metrics
        final PeriodicMetricsExecutor executor = new PeriodicMetricsExecutor.Builder()
                .setCloseTimeout(Duration.ofMillis(1000))
                .build();
        final ThreadSafePeriodicMetrics periodicMetrics = new ThreadSafePeriodicMetrics.Builder()
                .setMetricsFactory(metricsFactory)
                .build();
        verify(metricsFactory).createScopedMetrics();

        // Create a "long" callback; the expectation is that it is interrupted
        periodicMetrics.registerPolledMetric(metrics -> {
            try {
                metrics.recordGauge("foo", 1);
                semaphore.release();
                Thread.sleep(1000);
                metrics.recordGauge("bar", 1);
            } catch (final InterruptedException e) {
                // Nothing to do
            }
        });
        executor.scheduleAtFixedRate(periodicMetrics, Duration.ofMillis(5));

        final AtomicBoolean closeInterrupted = new AtomicBoolean(false);
        final Thread thread = new Thread(() -> {
            try {
                semaphore.release();
                executor.close();
            } catch (final InterruptedException e) {
                closeInterrupted.set(true);
            }
        }, "testCloseInterrupted.closer");

        // Wait until the callback is blocking to start closing
        semaphore.acquire(1);
        thread.start();

        // Wait until the closer is closing to interrupt it
        semaphore.acquire(1);
        thread.interrupt();
        thread.join();

        // The close was interrupted
        assertTrue(closeInterrupted.get());

        // Only first metric should have been recorded and flushed
        verify(scopedMetrics).recordGauge("foo", 1);

        // The completion of the callback evaluation also meant that
        // the periodic metrics instance replaced its scoped metrics
        verify(metricsFactory, times(2)).createScopedMetrics();

        // Closed at the end of the callback processing
        verify(scopedMetrics).close();

        // Close interrupted; metrics not closed on close of the executor
        verifyNoInteractions(nextScopedMetrics);

        verifyNoMoreInteractions(metricsFactory);
        verifyNoMoreInteractions(scopedMetrics);
        verifyNoMoreInteractions(nextScopedMetrics);
    }

    @Test
    public void testScheduleAfterClose() throws InterruptedException {
        doReturn(scopedMetrics).when(metricsFactory).createScopedMetrics();
        final PeriodicMetricsExecutor executor = new PeriodicMetricsExecutor.Builder(Clock.systemUTC(), logger)
                .setCloseTimeout(Duration.ofMillis(1000))
                .build();
        executor.close();

        final ThreadSafePeriodicMetrics periodicMetrics = new ThreadSafePeriodicMetrics.Builder()
                .setMetricsFactory(metricsFactory)
                .build();
        verify(metricsFactory).createScopedMetrics();

        executor.scheduleAtFixedRate(periodicMetrics, Duration.ofSeconds(1));
        verify(logger).error("Scheduled periodic metrics for execution after executor was shutdown");

        periodicMetrics.close();
        verify(scopedMetrics).close();
        verifyNoMoreInteractions(scopedMetrics);
        verifyNoMoreInteractions(metricsFactory);
    }

    @Test
    public void testReschedule() throws InterruptedException {
        final ScopedMetrics nextScopedMetrics = mock(ScopedMetrics.class);
        doReturn(scopedMetrics, nextScopedMetrics).when(metricsFactory).createScopedMetrics();

        final PeriodicMetricsExecutor executor = new PeriodicMetricsExecutor.Builder(Clock.systemUTC(), logger)
                .setCloseTimeout(Duration.ofMillis(1000))
                .build();

        final ThreadSafePeriodicMetrics periodicMetrics = new ThreadSafePeriodicMetrics.Builder()
                .setMetricsFactory(metricsFactory)
                .build();
        verify(metricsFactory).createScopedMetrics();

        executor.scheduleAtFixedRate(periodicMetrics, Duration.ofSeconds(1));
        verify(scopedMetrics, timeout(1500)).close();
        verify(nextScopedMetrics, timeout(1500)).close();
        executor.close();
    }

    private void assertDefaults(final PeriodicMetricsExecutor executor) {
        assertEquals(0.0, executor.getOffsetPercent(), 0.001);
        assertEquals(Duration.ofSeconds(3), executor.getCloseTimeout());
        assertEquals(Duration.ofMinutes(1), executor.getTaskExceededIntervalLoggingInterval());
    }
}

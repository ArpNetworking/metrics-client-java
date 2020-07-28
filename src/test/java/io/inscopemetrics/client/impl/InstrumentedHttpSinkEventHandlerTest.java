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

import io.inscopemetrics.client.Event;
import io.inscopemetrics.client.Metrics;
import io.inscopemetrics.client.MetricsFactory;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for {@link InstrumentedHttpSinkEventHandler}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class InstrumentedHttpSinkEventHandlerTest {

    @Test
    public void testRecordCompletedAttempts() throws InterruptedException {
        final Semaphore semaphore = new Semaphore(-2);

        final MetricsFactory metricsFactory = mock(MetricsFactory.class);
        final Metrics metricsA = mock(Metrics.class, "A");
        final Metrics metricsB = mock(Metrics.class, "B");

        doAnswer(ignored -> {
            try {
                if (semaphore.availablePermits() == -2) {
                    return metricsA;
                } else {
                    return metricsB;
                }
            } finally {
                semaphore.release();
            }
        }).when(metricsFactory).create();

        final HttpSinkEventHandler eventHandler = new InstrumentedHttpSinkEventHandler(
                () -> Optional.of(metricsFactory));
        eventHandler.attemptComplete(1, 2, true, 123, TimeUnit.NANOSECONDS);
        eventHandler.attemptComplete(3, 4, false, 246, TimeUnit.NANOSECONDS);
        semaphore.acquire();

        verify(metricsFactory, times(3)).create();
        verify(metricsA).incrementCounter("metrics_client/http_sink/records", 1);
        verify(metricsA).incrementCounter("metrics_client/http_sink/records", 3);
        verify(metricsA).incrementCounter("metrics_client/http_sink/bytes", 2);
        verify(metricsA).incrementCounter("metrics_client/http_sink/bytes", 4);
        verify(metricsA, times(2)).resetCounter("metrics_client/http_sink/success_rate");
        verify(metricsA).incrementCounter("metrics_client/http_sink/success_rate");
        verify(metricsA).setTimer("metrics_client/http_sink/latency", 123, TimeUnit.NANOSECONDS);
        verify(metricsA).setTimer("metrics_client/http_sink/latency", 246, TimeUnit.NANOSECONDS);
        verify(metricsA).close();
        verifyNoMoreInteractions(metricsA);
    }

    @Test
    public void testRecordCompletedAttemptsMetricsThrows() throws InterruptedException {
        // NOTE: The metrics instance is exception safe by contract; this
        // unit test exists only to work around deficiencies in Jacoco.
        // See: https://github.com/jacoco/jacoco/issues/15
        final Semaphore semaphore = new Semaphore(0);

        final MetricsFactory metricsFactory = mock(MetricsFactory.class);
        final Metrics metrics = mock(Metrics.class);
        doAnswer(ignored -> {
                    semaphore.release();
                    return metrics;
                })
                .doAnswer(ignored -> {
                    semaphore.release();
                    return null;
                })
                .when(metricsFactory)
                .create();
        doThrow(new IllegalStateException("Test Exception"))
                .when(metrics)
                .incrementCounter(anyString(), anyLong());

        final HttpSinkEventHandler eventHandler = new InstrumentedHttpSinkEventHandler(
                () -> Optional.of(metricsFactory));

        semaphore.acquire();
        try {
            eventHandler.attemptComplete(3, 4, false, 246, TimeUnit.NANOSECONDS);
        } catch (final IllegalStateException e) {
            // Ignore
        }
        semaphore.acquire();

        // The following is used to force the write lock to be given up; since
        // the second metrics instance created by our factory is null this data
        // is not recorded.
        eventHandler.attemptComplete(-1, -1, true, 123, TimeUnit.NANOSECONDS);

        verify(metricsFactory, times(2)).create();
        verify(metrics).incrementCounter("metrics_client/http_sink/records", 3);
        verify(metrics).close();
        verifyNoMoreInteractions(metrics);
    }

    @Test
    public void testRecordDropped() throws InterruptedException {
        final Semaphore semaphore = new Semaphore(-2);

        final Event eventA = mock(Event.class, "A");
        final Event eventB = mock(Event.class, "B");
        final MetricsFactory metricsFactory = mock(MetricsFactory.class);
        final Metrics metricsA = mock(Metrics.class, "A");
        final Metrics metricsB = mock(Metrics.class, "B");

        doAnswer(ignored -> {
            try {
                if (semaphore.availablePermits() == -2) {
                    return metricsA;
                } else {
                    return metricsB;
                }
            } finally {
                semaphore.release();
            }
        }).when(metricsFactory).create();

        final HttpSinkEventHandler eventHandler = new InstrumentedHttpSinkEventHandler(
                () -> Optional.of(metricsFactory));
        eventHandler.droppedEvent(eventA);
        eventHandler.droppedEvent(eventB);
        semaphore.acquire();

        verify(metricsFactory, times(3)).create();
        verify(metricsA, times(2)).incrementCounter("metrics_client/http_sink/dropped");
        verify(metricsA).close();
        verifyNoMoreInteractions(metricsA);
    }

    @Test
    public void testRecordDroppedMetricsThrows() throws InterruptedException {
        // NOTE: The metrics instance is exception safe by contract; this
        // unit test exists only to work around deficiencies in Jacoco.
        // See: https://github.com/jacoco/jacoco/issues/15
        final Semaphore semaphore = new Semaphore(0);

        final Event eventA = mock(Event.class, "A");
        final Event eventB = mock(Event.class, "B");
        final MetricsFactory metricsFactory = mock(MetricsFactory.class);
        final Metrics metrics = mock(Metrics.class);
        doAnswer(ignored -> {
            semaphore.release();
            return metrics;
        })
                .doAnswer(ignored -> {
                    semaphore.release();
                    return null;
                })
                .when(metricsFactory)
                .create();
        doThrow(new IllegalStateException("Test Exception"))
                .when(metrics)
                .incrementCounter(Mockito.anyString());

        final HttpSinkEventHandler eventHandler = new InstrumentedHttpSinkEventHandler(
                () -> Optional.of(metricsFactory));

        semaphore.acquire();
        try {
            eventHandler.droppedEvent(eventA);
        } catch (final IllegalStateException e) {
            // Ignore
        }
        semaphore.acquire();

        // The following is used to force the write lock to be given up; since
        // the second metrics instance created by our factory is null this data
        // is not recorded.
        eventHandler.droppedEvent(eventB);

        verify(metricsFactory, times(2)).create();
        verify(metrics).incrementCounter("metrics_client/http_sink/dropped");
        verify(metrics).close();
        verifyNoMoreInteractions(metrics);
    }

    @Test
    public void testMetricsFactoryThrows() throws InterruptedException {
        // NOTE: The metrics factory instance is exception safe by contract;
        // this unit test exists only to work around deficiencies in Jacoco.
        // See: https://github.com/jacoco/jacoco/issues/15
        final Semaphore semaphore = new Semaphore(0);

        final MetricsFactory metricsFactory = mock(MetricsFactory.class);
        final Metrics metrics = mock(Metrics.class);
        doAnswer(ignored -> {
            semaphore.release();
            return metrics;
        })
                .doAnswer(ignored -> {
                    semaphore.release();
                    throw new IllegalStateException("Test Exception");
                })
                .when(metricsFactory)
                .create();

        final HttpSinkEventHandler eventHandler = new InstrumentedHttpSinkEventHandler(
                () -> Optional.of(metricsFactory));

        semaphore.acquire();
        eventHandler.attemptComplete(3, 4, false, 246, TimeUnit.NANOSECONDS);
        semaphore.acquire();

        // The following is used to force the write lock to be given up; since
        // the second metrics instance created by our factory is null this data
        // is not recorded.
        eventHandler.attemptComplete(-1, -1, true, 123, TimeUnit.NANOSECONDS);

        verify(metricsFactory, times(2)).create();
        verify(metrics).incrementCounter("metrics_client/http_sink/records", 3);
        verify(metrics).incrementCounter("metrics_client/http_sink/bytes", 4);
        verify(metrics, times(1)).resetCounter("metrics_client/http_sink/success_rate");
        verify(metrics).setTimer("metrics_client/http_sink/latency", 246, TimeUnit.NANOSECONDS);
        verify(metrics).close();
        verifyNoMoreInteractions(metrics);
    }

    @Test
    public void testSkipUntilMetricsFactoryAvailable() throws InterruptedException {
        final Semaphore semaphore = new Semaphore(-1);

        final MetricsFactory metricsFactory = mock(MetricsFactory.class);
        final Metrics metricsA = mock(Metrics.class, "A");

        doAnswer(ignored -> {
            semaphore.release();
            return metricsA;
        }).when(metricsFactory).create();

        final HttpSinkEventHandler eventHandler = new InstrumentedHttpSinkEventHandler(() -> {
            if (semaphore.availablePermits() == -1) {
                semaphore.release();
                return Optional.empty();
            }
            return Optional.of(metricsFactory);
        });
        eventHandler.attemptComplete(1, 2, true, 123, TimeUnit.NANOSECONDS);
        semaphore.acquire();
        eventHandler.attemptComplete(3, 4, false, 246, TimeUnit.NANOSECONDS);
        semaphore.acquire();

        verify(metricsFactory, times(2)).create();
        verify(metricsA).resetCounter("metrics_client/http_sink/records");
        verify(metricsA).resetCounter("metrics_client/http_sink/bytes");
        verify(metricsA).resetCounter("metrics_client/http_sink/dropped");
        verify(metricsA).incrementCounter("metrics_client/http_sink/records", 3);
        verify(metricsA).incrementCounter("metrics_client/http_sink/bytes", 4);
        verify(metricsA, times(1)).resetCounter("metrics_client/http_sink/success_rate");
        verify(metricsA).setTimer("metrics_client/http_sink/latency", 246, TimeUnit.NANOSECONDS);
        verify(metricsA).close();
        verifyNoMoreInteractions(metricsA);
    }
}

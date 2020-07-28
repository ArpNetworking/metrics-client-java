/*
 * Copyright 2014 Groupon.com
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

import com.arpnetworking.commons.hostresolver.HostResolver;
import com.arpnetworking.commons.uuidfactory.SplittableRandomUuidFactory;
import com.arpnetworking.commons.uuidfactory.UuidFactory;
import io.inscopemetrics.client.Event;
import io.inscopemetrics.client.Metrics;
import io.inscopemetrics.client.MetricsFactory;
import io.inscopemetrics.client.Sink;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TsdMetricsFactory}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public final class TsdMetricsFactoryTest {

    private static final Map<String, String> DEFAULT_DIMENSIONS;
    private static final Map<String, Supplier<String>> DEFAULT_COMPUTED_DIMENSIONS;

    static {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        DEFAULT_DIMENSIONS = new HashMap<>();
        DEFAULT_COMPUTED_DIMENSIONS = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation

        DEFAULT_DIMENSIONS.put("service", "MyService");
        DEFAULT_DIMENSIONS.put("cluster", "MyCluster");

        DEFAULT_COMPUTED_DIMENSIONS.put("host", () -> "MyHost");
    }

    @Mock
    private HostResolver mockHostResolver;
    @Mock
    private UuidFactory mockUuidFactory;

    @AfterClass
    public static void afterClass() {
        new File("./query.log").deleteOnExit();
    }

    @Test
    public void testNewInstance() {
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) TsdMetricsFactory.newInstance();

        assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        assertEquals(0, metricsFactory.getDefaultDimensions().size());
        assertEquals(0, metricsFactory.getDefaultComputedDimensions().size());
        assertEquals(1, metricsFactory.getSinks().size());
        assertTrue(metricsFactory.getSinks().get(0) instanceof HttpSink);

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    assertNotNull(metrics);
                    assertEquals(type, metrics.getClass());
                });
    }

    @Test
    public void testNewInstanceWithDimensions() {
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) TsdMetricsFactory.newInstance(
                DEFAULT_DIMENSIONS,
                DEFAULT_COMPUTED_DIMENSIONS);

        assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        assertEquals(2, metricsFactory.getDefaultDimensions().size());
        assertEquals("MyService", metricsFactory.getDefaultDimensions().get("service"));
        assertEquals("MyCluster", metricsFactory.getDefaultDimensions().get("cluster"));
        assertEquals(1, metricsFactory.getDefaultComputedDimensions().size());
        assertEquals("MyHost", metricsFactory.getDefaultComputedDimensions().get("host").get());
        assertEquals(1, metricsFactory.getSinks().size());
        assertTrue(metricsFactory.getSinks().get(0) instanceof HttpSink);

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    assertNotNull(metrics);
                    assertEquals(type, metrics.getClass());
                });
    }

    @Test
    public void testBuilderDefaults() {
        final Logger logger = mock(Logger.class);
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(logger).build();

        assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        assertEquals(0, metricsFactory.getDefaultDimensions().size());
        assertEquals(0, metricsFactory.getDefaultComputedDimensions().size());
        assertTrue(metricsFactory.getSinks().get(0) instanceof HttpSink);

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    assertNotNull(metrics);
                    assertEquals(type, metrics.getClass());
                });
    }

    @Test
    public void testBuilderNullSinks() {
        final Logger logger = mock(Logger.class);
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(logger)
                .setSinks(null)
                .build();

        verify(logger).info(startsWith("Defaulted null sinks; sinks="));

        assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        assertEquals(0, metricsFactory.getDefaultDimensions().size());
        assertEquals(0, metricsFactory.getDefaultComputedDimensions().size());
        assertTrue(metricsFactory.getSinks().isEmpty());

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    assertNotNull(metrics);
                    assertEquals(type, metrics.getClass());
                });
    }

    @Test
    public void testBuilderNullDefaultDimensions() {
        final Logger logger = mock(Logger.class);
        final Sink sink = mock(Sink.class);
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(logger)
                .setDefaultDimensions(null)
                .setSinks(Collections.singletonList(sink))
                .build();

        verify(logger).info(startsWith("Defaulted null default dimensions; defaultDimensions="));

        assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        assertEquals(0, metricsFactory.getDefaultDimensions().size());
        assertEquals(0, metricsFactory.getDefaultComputedDimensions().size());
        assertEquals(1, metricsFactory.getSinks().size());
        assertSame(sink, metricsFactory.getSinks().get(0));

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    reset(sink);
                    assertNotNull(metrics);
                    assertEquals(type, metrics.getClass());
                    metrics.close();
                    verify(sink).record(any(Event.class));
                });
    }

    @Test
    public void testBuilderNullDefaultComputedDimensions() {
        final Logger logger = mock(Logger.class);
        final Sink sink = mock(Sink.class);
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(logger)
                .setDefaultComputedDimensions(null)
                .setSinks(Collections.singletonList(sink))
                .build();

        verify(logger).info(startsWith("Defaulted null default computed dimensions; defaultComputedDimensions="));

        assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        assertEquals(0, metricsFactory.getDefaultDimensions().size());
        assertEquals(0, metricsFactory.getDefaultComputedDimensions().size());
        assertEquals(1, metricsFactory.getSinks().size());
        assertSame(sink, metricsFactory.getSinks().get(0));

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    reset(sink);
                    assertNotNull(metrics);
                    assertEquals(type, metrics.getClass());
                    metrics.close();
                    verify(sink).record(any(Event.class));
                });
    }

    @Test
    public void testBuilderComputedDimensionNull() {
        final Logger logger = mock(Logger.class);
        final Sink sink = mock(Sink.class);
        final Supplier<String> nullHostnameProvider = () -> null;

        final TsdMetricsFactory.Builder metricsFactoryBuilder = new TsdMetricsFactory.Builder(logger)
                .setDefaultComputedDimensions(Collections.singletonMap("host", nullHostnameProvider))
                .setSinks(Collections.singletonList(sink));

        final TsdMetricsFactory metricsFactory = new TsdMetricsFactory(metricsFactoryBuilder, logger);

        assertEquals(1, metricsFactory.getDefaultComputedDimensions().size());
        assertEquals(1, metricsFactory.getSinks().size());
        assertSame(sink, metricsFactory.getSinks().get(0));

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    @SuppressWarnings("resource")
                    final ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
                    reset(sink);
                    assertNotNull(metrics);
                    assertEquals(type, metrics.getClass());
                    metrics.close();
                    verify(sink).record(eventCaptor.capture());
                    assertFalse(eventCaptor.getValue().getDimensions().containsKey("host"));
                });
    }

    @Test
    public void testBuilderComputedDimensionFailure() {
        final Logger logger = mock(Logger.class);
        final Sink sink = mock(Sink.class);
        final Supplier<String> failingHostnameProvider = () -> {
            throw new RuntimeException("Test Exception");
        };

        final TsdMetricsFactory.Builder metricsFactoryBuilder = new TsdMetricsFactory.Builder(logger)
                .setDefaultComputedDimensions(Collections.singletonMap("host", failingHostnameProvider))
                .setSinks(Collections.singletonList(sink));

        final TsdMetricsFactory metricsFactory = new TsdMetricsFactory(metricsFactoryBuilder, logger);

        assertEquals(1, metricsFactory.getDefaultComputedDimensions().size());
        assertEquals(1, metricsFactory.getSinks().size());
        assertSame(sink, metricsFactory.getSinks().get(0));

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    verify(logger).warn(
                            eq(String.format("Unable to construct %s, metrics disabled", type.getSimpleName())),
                            any(RuntimeException.class));
                    assertNotNull(metrics);
                    assertEquals(type, metrics.getClass());
                    metrics.close();

                    verify(sink, never()).record(any(Event.class));
                    reset(sink);
                    reset(logger);
                });
    }

    @Test
    public void testBuilderSinks() {
        final Sink sink1 = mock(Sink.class, "TsdMetricsFactoryTest.testCreate.sink1");
        final Sink sink2 = mock(Sink.class, "TsdMetricsFactoryTest.testCreate.sink2");
        final List<Sink> sinks = new ArrayList<>();
        sinks.add(sink1);
        sinks.add(sink2);
        final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
                .setSinks(sinks)
                .build();
        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    reset(sink1);
                    reset(sink2);
                    assertNotNull(metrics);
                    assertEquals(type, metrics.getClass());
                    metrics.close();
                    verify(sink1).record(any(Event.class));
                    verify(sink2).record(any(Event.class));
                });
    }

    @Test
    public void testBuilderNoSinks() {
        final Logger logger = mock(Logger.class);
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(logger)
                .setSinks(Collections.emptyList())
                .build();
        assertEquals(0, metricsFactory.getSinks().size());

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> metrics.close());
    }

    @Test
    public void testCustomUuidFactory() {
        when(mockUuidFactory.get()).thenReturn(UUID.randomUUID(), UUID.randomUUID());

        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder()
                .setUuidFactory(mockUuidFactory)
                .build();

        final Metrics metrics = metricsFactory.create();
        assertNotNull(metrics);
        verify(mockUuidFactory, times(1)).get();
        final Metrics metrics2 = metricsFactory.create();
        assertNotNull(metrics2);
        verify(mockUuidFactory, times(2)).get();

        final Metrics metricsLF = metricsFactory.createLockFree();
        assertNotNull(metricsLF);
        verify(mockUuidFactory, times(3)).get();
        final Metrics metricsLF2 = metricsFactory.createLockFree();
        assertNotNull(metricsLF2);
        verify(mockUuidFactory, times(4)).get();
    }

    @Test
    public void testToString() {
        final String asString = new TsdMetricsFactory.Builder()
                .build()
                .toString();
        assertNotNull(asString);
        assertFalse(asString.isEmpty());
    }

    @Test
    public void testCreateDefaultSinksNone() {
        final List<Sink> sinks = TsdMetricsFactory.createDefaultSinks(Collections.emptyList());
        assertNotNull(sinks);
        assertEquals(1, sinks.size());
        assertTrue(sinks.iterator().next() instanceof WarningSink);
    }

    @Test
    public void testCreateDefaultSinksInvalid() {
        final List<Sink> sinks = TsdMetricsFactory.createDefaultSinks(
                Collections.singletonList("com.arpnetworking.metrics.impl.NonExistentSink"));
        assertNotNull(sinks);
        assertEquals(1, sinks.size());
        assertTrue(sinks.iterator().next() instanceof WarningSink);
    }

    @Test
    public void testCreateDefaultSinksValid() {
        final List<Sink> sinks = TsdMetricsFactory.createDefaultSinks(
                Collections.singletonList("io.inscopemetrics.client.impl.TsdMetricsFactoryTest$ValidDefaultSink"));
        assertNotNull(sinks);
        assertEquals(1, sinks.size());
        assertTrue(sinks.iterator().next() instanceof ValidDefaultSink);
    }

    @Test
    public void testCreateDefaultSinksMultipleUseFirst() {
        final List<Sink> sinks = TsdMetricsFactory.createDefaultSinks(
                Arrays.asList(
                        "io.inscopemetrics.client.impl.TsdMetricsFactoryTest$ValidDefaultSink",
                        "com.arpnetworking.metrics.impl.NonExistentSink"));
        assertNotNull(sinks);
        assertEquals(1, sinks.size());
        assertTrue(sinks.iterator().next() instanceof ValidDefaultSink);
    }

    @Test
    public void testCreateDefaultSinksMultipleSkipInvalid() {
        final List<Sink> sinks = TsdMetricsFactory.createDefaultSinks(
                Arrays.asList(
                        "com.arpnetworking.metrics.impl.NonExistentSink",
                        "io.inscopemetrics.client.impl.TsdMetricsFactoryTest$InvalidDefaultSink",
                        "io.inscopemetrics.client.impl.TsdMetricsFactoryTest$ValidDefaultSink"));
        assertNotNull(sinks);
        assertEquals(1, sinks.size());
        assertTrue(sinks.iterator().next() instanceof ValidDefaultSink);
    }

    @Test
    public void testCreateSinkSuccess() {
        final Optional<Sink> sink = TsdMetricsFactory.createSink(WarningSink.class);
        assertTrue(sink.isPresent());
        assertTrue(sink.get() instanceof WarningSink);
    }

    @Test
    public void testCreateSinkFailure() {
        final Optional<Sink> sink = TsdMetricsFactory.createSink(InvalidDefaultSink.class);
        assertFalse(sink.isPresent());
    }

    @Test
    public void testGetSinkExisting() {
        final Optional<Class<? extends Sink>> sinkClass = TsdMetricsFactory.getSinkClass(
                "io.inscopemetrics.client.impl.WarningSink");
        assertTrue(sinkClass.isPresent());
        assertEquals(WarningSink.class, sinkClass.get());
    }

    @Test
    public void testGetSinkDoesNotExist() {
        final Optional<Class<? extends Sink>> sinkClass = TsdMetricsFactory.getSinkClass(
                "com.arpnetworking.metrics.impl.NonExistentSink");
        assertFalse(sinkClass.isPresent());
    }

    private void testOnCreateMethods(final MetricsFactory factory, final BiConsumer<Metrics, Class<? extends Metrics>> test) {
        test.accept(factory.create(), TsdMetrics.class);
        test.accept(factory.createLockFree(), LockFreeMetrics.class);
    }

    /**
     * Invalid default sink. This sink is an invalid default sink because it
     * lacks a nested builder class. There are other reasons the sink would
     * be invalid as a default sink, but this is one of them.
     */
    public static final class InvalidDefaultSink implements Sink {

        @Override
        public void record(final Event event) {
            // Do nothing
        }
    }

    /**
     * Valid default sink.
     */
    public static final class ValidDefaultSink implements Sink {

        @Override
        public void record(final Event event) {
            // Do nothing
        }

        /**
         * Builder for {@link ValidDefaultSink}.
         */
        public static final class Builder implements com.arpnetworking.commons.builder.Builder<Sink> {

            @Override
            public Sink build() {
                return new ValidDefaultSink();
            }
        }
    }
}

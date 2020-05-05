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
package com.arpnetworking.metrics.impl;

import com.arpnetworking.commons.hostresolver.HostResolver;
import com.arpnetworking.commons.uuidfactory.SplittableRandomUuidFactory;
import com.arpnetworking.commons.uuidfactory.UuidFactory;
import com.arpnetworking.metrics.Event;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.Sink;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
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

/**
 * Tests for {@link TsdMetricsFactory}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TsdMetricsFactoryTest {

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @AfterClass
    public static void afterClass() {
        new File("./query.log").deleteOnExit();
    }

    @Test
    public void testNewInstance() {
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) TsdMetricsFactory.newInstance();

        Assert.assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        Assert.assertEquals(0, metricsFactory.getDefaultDimensions().size());
        Assert.assertEquals(0, metricsFactory.getDefaultComputedDimensions().size());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertTrue(metricsFactory.getSinks().get(0) instanceof WarningSink);

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    Assert.assertNotNull(metrics);
                    Assert.assertEquals(type, metrics.getClass());
                });
    }

    @Test
    public void testNewInstanceWithDimensions() {
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) TsdMetricsFactory.newInstance(
                DEFAULT_DIMENSIONS,
                DEFAULT_COMPUTED_DIMENSIONS);

        Assert.assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        Assert.assertEquals(2, metricsFactory.getDefaultDimensions().size());
        Assert.assertEquals("MyService", metricsFactory.getDefaultDimensions().get("service"));
        Assert.assertEquals("MyCluster", metricsFactory.getDefaultDimensions().get("cluster"));
        Assert.assertEquals(1, metricsFactory.getDefaultComputedDimensions().size());
        Assert.assertEquals("MyHost", metricsFactory.getDefaultComputedDimensions().get("host").get());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertTrue(metricsFactory.getSinks().get(0) instanceof WarningSink);

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    Assert.assertNotNull(metrics);
                    Assert.assertEquals(type, metrics.getClass());
                });
    }

    @Test
    public void testBuilderDefaults() {
        final Logger logger = Mockito.mock(Logger.class);
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(logger).build();

        Assert.assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        Assert.assertEquals(0, metricsFactory.getDefaultDimensions().size());
        Assert.assertEquals(0, metricsFactory.getDefaultComputedDimensions().size());
        Assert.assertTrue(metricsFactory.getSinks().get(0) instanceof WarningSink);

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    Assert.assertNotNull(metrics);
                    Assert.assertEquals(type, metrics.getClass());
                });
    }

    @Test
    public void testBuilderNullSinks() {
        final Logger logger = Mockito.mock(Logger.class);
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(logger)
                .setSinks(null)
                .build();

        Mockito.verify(logger).info(Mockito.startsWith("Defaulted null sinks; sinks="));

        Assert.assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        Assert.assertEquals(0, metricsFactory.getDefaultDimensions().size());
        Assert.assertEquals(0, metricsFactory.getDefaultComputedDimensions().size());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertTrue(metricsFactory.getSinks().get(0) instanceof WarningSink);

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    Assert.assertNotNull(metrics);
                    Assert.assertEquals(type, metrics.getClass());
                });
    }

    @Test
    public void testBuilderNullDefaultDimensions() {
        final Logger logger = Mockito.mock(Logger.class);
        final Sink sink = Mockito.mock(Sink.class);
        Mockito.doReturn("MyHost").when(_mockHostResolver).get();
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(logger)
                .setDefaultDimensions(null)
                .setSinks(Collections.singletonList(sink))
                .build();

        Mockito.verify(logger).info(Mockito.startsWith("Defaulted null default dimensions; defaultDimensions="));

        Assert.assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        Assert.assertEquals(0, metricsFactory.getDefaultDimensions().size());
        Assert.assertEquals(0, metricsFactory.getDefaultComputedDimensions().size());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertSame(sink, metricsFactory.getSinks().get(0));

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    Mockito.reset(sink);
                    Assert.assertNotNull(metrics);
                    Assert.assertEquals(type, metrics.getClass());
                    metrics.close();
                    Mockito.verify(sink).record(Mockito.any(Event.class));
                });
    }

    @Test
    public void testBuilderNullDefaultComputedDimensions() {
        final Logger logger = Mockito.mock(Logger.class);
        final Sink sink = Mockito.mock(Sink.class);
        Mockito.doReturn("MyHost").when(_mockHostResolver).get();
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(logger)
                .setDefaultComputedDimensions(null)
                .setSinks(Collections.singletonList(sink))
                .build();

        Mockito.verify(logger).info(Mockito.startsWith("Defaulted null default computed dimensions; defaultComputedDimensions="));

        Assert.assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        Assert.assertEquals(0, metricsFactory.getDefaultDimensions().size());
        Assert.assertEquals(0, metricsFactory.getDefaultComputedDimensions().size());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertSame(sink, metricsFactory.getSinks().get(0));

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    Mockito.reset(sink);
                    Assert.assertNotNull(metrics);
                    Assert.assertEquals(type, metrics.getClass());
                    metrics.close();
                    Mockito.verify(sink).record(Mockito.any(Event.class));
                });
    }

    @Test
    public void testBuilderComputedDimensionNull() {
        final Logger logger = Mockito.mock(Logger.class);
        final Sink sink = Mockito.mock(Sink.class);
        final Supplier<String> nullHostnameProvider = () -> null;

        final TsdMetricsFactory.Builder metricsFactoryBuilder = new TsdMetricsFactory.Builder(logger)
                .setDefaultComputedDimensions(Collections.singletonMap("host", nullHostnameProvider))
                .setSinks(Collections.singletonList(sink));

        final TsdMetricsFactory metricsFactory = new TsdMetricsFactory(metricsFactoryBuilder, logger);

        Assert.assertEquals(1, metricsFactory.getDefaultComputedDimensions().size());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertSame(sink, metricsFactory.getSinks().get(0));
        
        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    @SuppressWarnings("resource")
                    final ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
                    Mockito.reset(sink);
                    Assert.assertNotNull(metrics);
                    Assert.assertEquals(type, metrics.getClass());
                    metrics.close();
                    Mockito.verify(sink).record(eventCaptor.capture());
                    Assert.assertFalse(eventCaptor.getValue().getDimensions().containsKey("host"));
                });
    }

    @Test
    public void testBuilderComputedDimensionFailure() {
        final Logger logger = Mockito.mock(Logger.class);
        final Sink sink = Mockito.mock(Sink.class);
        final Supplier<String> failingHostnameProvider = () -> {
            throw new RuntimeException("Test Exception");
        };

        final TsdMetricsFactory.Builder metricsFactoryBuilder = new TsdMetricsFactory.Builder(logger)
                .setDefaultComputedDimensions(Collections.singletonMap("host", failingHostnameProvider))
                .setSinks(Collections.singletonList(sink));

        final TsdMetricsFactory metricsFactory = new TsdMetricsFactory(metricsFactoryBuilder, logger);

        Assert.assertEquals(1, metricsFactory.getDefaultComputedDimensions().size());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertSame(sink, metricsFactory.getSinks().get(0));

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    Mockito.verify(logger).warn(
                            Mockito.eq(String.format("Unable to construct %s, metrics disabled", type.getSimpleName())),
                            Mockito.any(RuntimeException.class));
                    Assert.assertNotNull(metrics);
                    Assert.assertEquals(type, metrics.getClass());
                    metrics.close();

                    Mockito.verify(sink, Mockito.never()).record(Mockito.any(Event.class));
                    Mockito.reset(sink);
                    Mockito.reset(logger);
                });
    }

    @Test
    public void testBuilderSinks() {
        final Sink sink1 = Mockito.mock(Sink.class, "TsdMetricsFactoryTest.testCreate.sink1");
        final Sink sink2 = Mockito.mock(Sink.class, "TsdMetricsFactoryTest.testCreate.sink2");
        final List<Sink> sinks = new ArrayList<>();
        sinks.add(sink1);
        sinks.add(sink2);
        final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
                .setSinks(sinks)
                .build();
        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> {
                    Mockito.reset(sink1);
                    Mockito.reset(sink2);
                    Assert.assertNotNull(metrics);
                    Assert.assertEquals(type, metrics.getClass());
                    metrics.close();
                    Mockito.verify(sink1).record(Mockito.any(Event.class));
                    Mockito.verify(sink2).record(Mockito.any(Event.class));
                });
    }

    @Test
    public void testBuilderNoSinks() {
        final Logger logger = Mockito.mock(Logger.class);
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(logger)
                .setSinks(Collections.emptyList())
                .build();
        Assert.assertEquals(0, metricsFactory.getSinks().size());

        testOnCreateMethods(
                metricsFactory,
                (metrics, type) -> metrics.close());
    }

    @Test
    public void testCustomUuidFactory() {
        Mockito.when(_mockUuidFactory.get()).thenReturn(UUID.randomUUID(), UUID.randomUUID());

        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder()
                .setUuidFactory(_mockUuidFactory)
                .build();

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Mockito.verify(_mockUuidFactory, Mockito.times(1)).get();
        final Metrics metrics2 = metricsFactory.create();
        Assert.assertNotNull(metrics2);
        Mockito.verify(_mockUuidFactory, Mockito.times(2)).get();

        final Metrics metricsLF = metricsFactory.createLockFree();
        Assert.assertNotNull(metricsLF);
        Mockito.verify(_mockUuidFactory, Mockito.times(3)).get();
        final Metrics metricsLF2 = metricsFactory.createLockFree();
        Assert.assertNotNull(metricsLF2);
        Mockito.verify(_mockUuidFactory, Mockito.times(4)).get();
    }

    @Test
    public void testToString() {
        final String asString = new TsdMetricsFactory.Builder()
                .build()
                .toString();
        Assert.assertNotNull(asString);
        Assert.assertFalse(asString.isEmpty());
    }

    @Test
    public void testCreateDefaultSinksNone() {
        final List<Sink> sinks = TsdMetricsFactory.createDefaultSinks(Collections.emptyList());
        Assert.assertNotNull(sinks);
        Assert.assertEquals(1, sinks.size());
        Assert.assertTrue(sinks.iterator().next() instanceof WarningSink);
    }

    @Test
    public void testCreateDefaultSinksInvalid() {
        final List<Sink> sinks = TsdMetricsFactory.createDefaultSinks(
                Collections.singletonList("com.arpnetworking.metrics.impl.NonExistentSink"));
        Assert.assertNotNull(sinks);
        Assert.assertEquals(1, sinks.size());
        Assert.assertTrue(sinks.iterator().next() instanceof WarningSink);
    }

    @Test
    public void testCreateDefaultSinksValid() {
        final List<Sink> sinks = TsdMetricsFactory.createDefaultSinks(
                Collections.singletonList("com.arpnetworking.metrics.impl.TsdMetricsFactoryTest$ValidDefaultSink"));
        Assert.assertNotNull(sinks);
        Assert.assertEquals(1, sinks.size());
        Assert.assertTrue(sinks.iterator().next() instanceof ValidDefaultSink);
    }

    @Test
    public void testCreateDefaultSinksMultipleUseFirst() {
        final List<Sink> sinks = TsdMetricsFactory.createDefaultSinks(
                Arrays.asList(
                        "com.arpnetworking.metrics.impl.TsdMetricsFactoryTest$ValidDefaultSink",
                        "com.arpnetworking.metrics.impl.NonExistentSink"));
        Assert.assertNotNull(sinks);
        Assert.assertEquals(1, sinks.size());
        Assert.assertTrue(sinks.iterator().next() instanceof ValidDefaultSink);
    }

    @Test
    public void testCreateDefaultSinksMultipleSkipInvalid() {
        final List<Sink> sinks = TsdMetricsFactory.createDefaultSinks(
                Arrays.asList(
                        "com.arpnetworking.metrics.impl.NonExistentSink",
                        "com.arpnetworking.metrics.impl.TsdMetricsFactoryTest$InvalidDefaultSink",
                        "com.arpnetworking.metrics.impl.TsdMetricsFactoryTest$ValidDefaultSink"));
        Assert.assertNotNull(sinks);
        Assert.assertEquals(1, sinks.size());
        Assert.assertTrue(sinks.iterator().next() instanceof ValidDefaultSink);
    }

    @Test
    public void testCreateSinkSuccess() {
        final Optional<Sink> sink = TsdMetricsFactory.createSink(WarningSink.class);
        Assert.assertTrue(sink.isPresent());
        Assert.assertTrue(sink.get() instanceof WarningSink);
    }

    @Test
    public void testCreateSinkFailure() {
        final Optional<Sink> sink = TsdMetricsFactory.createSink(InvalidDefaultSink.class);
        Assert.assertFalse(sink.isPresent());
    }

    @Test
    public void testGetSinkExisting() {
        final Optional<Class<? extends Sink>> sinkClass = TsdMetricsFactory.getSinkClass(
                "com.arpnetworking.metrics.impl.WarningSink");
        Assert.assertTrue(sinkClass.isPresent());
        Assert.assertTrue(WarningSink.class.equals(sinkClass.get()));
    }

    @Test
    public void testGetSinkDoesNotExist() {
        final Optional<Class<? extends Sink>> sinkClass = TsdMetricsFactory.getSinkClass(
                "com.arpnetworking.metrics.impl.NonExistentSink");
        Assert.assertFalse(sinkClass.isPresent());
    }

    private void testOnCreateMethods(final MetricsFactory factory, final BiConsumer<Metrics, Class<? extends Metrics>> test) {
        test.accept(factory.create(), TsdMetrics.class);
        test.accept(factory.createLockFree(), LockFreeMetrics.class);
    }

    @Mock
    private HostResolver _mockHostResolver;
    @Mock
    private UuidFactory _mockUuidFactory;

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

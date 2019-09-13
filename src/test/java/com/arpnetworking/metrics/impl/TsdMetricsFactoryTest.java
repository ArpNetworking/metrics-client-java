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

import com.arpnetworking.commons.hostresolver.BackgroundCachingHostResolver;
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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tests for {@link TsdMetricsFactory}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class TsdMetricsFactoryTest {

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @AfterClass
    public static void afterClass() {
        new File("./query.log").deleteOnExit();
    }

    @Test
    public void testNewInstance() throws UnknownHostException {
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) TsdMetricsFactory.newInstance(
                "MyService",
                "MyCluster");

        Assert.assertTrue(metricsFactory.getHostResolver() instanceof BackgroundCachingHostResolver);
        Assert.assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertTrue(metricsFactory.getSinks().get(0) instanceof WarningSink);

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics instanceof TsdMetrics);
    }

    @Test
    public void testBuilderDefaults() throws UnknownHostException {
        final Logger logger = Mockito.mock(Logger.class);
        Mockito.doReturn("MyHost").when(_mockHostResolver).get();
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(_mockHostResolver, logger)
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .build();

        Assert.assertSame(_mockHostResolver, metricsFactory.getHostResolver());
        Assert.assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        Assert.assertTrue(metricsFactory.getSinks().get(0) instanceof WarningSink);

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics instanceof TsdMetrics);
        Mockito.verify(_mockHostResolver).get();
    }

    @Test
    public void testBuilderNullSinks() throws UnknownHostException {
        final Logger logger = Mockito.mock(Logger.class);
        Mockito.doReturn("MyHost").when(_mockHostResolver).get();
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(_mockHostResolver, logger)
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .setSinks(null)
                .build();

        Assert.assertSame(_mockHostResolver, metricsFactory.getHostResolver());
        Assert.assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        Assert.assertTrue(metricsFactory.getSinks().get(0) instanceof WarningSink);

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics instanceof TsdMetrics);
        Mockito.verify(_mockHostResolver).get();
    }

    @Test
    public void testBuilderNullHostResolver() throws UnknownHostException {
        final Logger logger = Mockito.mock(Logger.class);
        final Sink sink = Mockito.mock(Sink.class);
        Mockito.doReturn("MyHost").when(_mockHostResolver).get();
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(null, logger)
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .setSinks(Collections.singletonList(sink))
                .build();

        Assert.assertTrue(metricsFactory.getHostResolver() instanceof BackgroundCachingHostResolver);
        Assert.assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertSame(sink, metricsFactory.getSinks().get(0));

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics instanceof TsdMetrics);
        metrics.close();
        Mockito.verify(sink).record(Mockito.any(Event.class));
    }

    @Test
    public void testBuilderHostResolverFailure() throws UnknownHostException {
        final Logger logger = Mockito.mock(Logger.class);
        final Sink sink = Mockito.mock(Sink.class);
        Mockito.doThrow(new RuntimeException()).when(_mockHostResolver).get();

        final TsdMetricsFactory.Builder metricsFactoryBuilder = new TsdMetricsFactory.Builder(_mockHostResolver, logger)
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .setSinks(Collections.singletonList(sink));

        final TsdMetricsFactory metricsFactory = new TsdMetricsFactory(metricsFactoryBuilder, logger);

        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertSame(sink, metricsFactory.getSinks().get(0));

        @SuppressWarnings("resource")
        final Metrics metrics = metricsFactory.create();
        Mockito.verify(logger).warn(
                Mockito.eq("Unable to construct TsdMetrics, metrics disabled; failures=[Unable to determine hostname]"),
                Mockito.any(RuntimeException.class));
        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics instanceof TsdMetrics);
        metrics.close();

        Mockito.verify(sink, Mockito.never()).record(Mockito.any(Event.class));
    }

    @Test
    public void testBuilderSinks() {
        final Sink sink1 = Mockito.mock(Sink.class, "TsdMetricsFactoryTest.testCreate.sink1");
        final Sink sink2 = Mockito.mock(Sink.class, "TsdMetricsFactoryTest.testCreate.sink2");
        final List<Sink> sinks = new ArrayList<>();
        sinks.add(sink1);
        sinks.add(sink2);
        final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .setSinks(sinks)
                .build();
        @SuppressWarnings("resource")
        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics instanceof TsdMetrics);
        metrics.close();
        Mockito.verify(sink1).record(Mockito.any(Event.class));
        Mockito.verify(sink2).record(Mockito.any(Event.class));
    }

    @Test
    public void testBuilderNoSinks() {
        final Logger logger = Mockito.mock(Logger.class);
        final HostResolver hostResolver = Mockito.mock(HostResolver.class);
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(hostResolver, logger)
                .setServiceName("MyService")
                .setHostName("MyHost")
                .build();
        Mockito.verify(logger).warn(Mockito.anyString());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertTrue(metricsFactory.getSinks().get(0) instanceof WarningSink);

        @SuppressWarnings("resource")
        final Metrics metrics = metricsFactory.create();
        metrics.close();
    }

    @Test
    public void testBuilderFailureClusterName() {
        final Logger logger = Mockito.mock(Logger.class);
        final HostResolver hostResolver = Mockito.mock(HostResolver.class);
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(hostResolver, logger)
                .setServiceName("MyService")
                .setHostName("MyHost")
                .build();
        Mockito.verify(logger).warn(Mockito.anyString());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertTrue(metricsFactory.getSinks().get(0) instanceof WarningSink);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics instanceof TsdMetrics);
        metrics.close();
     }

    @Test
    public void testBuilderFailureServiceName() {
        final Logger logger = Mockito.mock(Logger.class);
        final HostResolver hostResolver = Mockito.mock(HostResolver.class);
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(hostResolver, logger)
                .setClusterName("MyCluster")
                .setHostName("MyHost")
                .build();
        Mockito.verify(logger).warn(Mockito.anyString());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertTrue(metricsFactory.getSinks().get(0) instanceof WarningSink);
        @SuppressWarnings("resource")
        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics instanceof TsdMetrics);
        metrics.close();
    }

    @Test
    public void testBuilderHostResolver() throws UnknownHostException {
        Mockito.doReturn("foo.example.com").when(_mockHostResolver).get();
        final Sink sink = Mockito.mock(Sink.class);

        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(_mockHostResolver)
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .setSinks(Collections.singletonList(sink))
                .build();

        Assert.assertSame(_mockHostResolver, metricsFactory.getHostResolver());
        Assert.assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertSame(sink, metricsFactory.getSinks().get(0));

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics instanceof TsdMetrics);
        Mockito.verify(_mockHostResolver).get();

        final ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        metrics.close();
        Mockito.verify(sink).record(eventArgumentCaptor.capture());
        final Event event = eventArgumentCaptor.getValue();
        Assert.assertEquals("foo.example.com", event.getAnnotations().get("_host"));
    }

    @Test
    public void testBuilderHostNameOverride() throws UnknownHostException {
        Mockito.doReturn("foo.example.com").when(_mockHostResolver).get();
        final Sink sink = Mockito.mock(Sink.class);

        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(_mockHostResolver)
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .setHostName("bar.example.com")
                .setSinks(Collections.singletonList(sink))
                .build();

        Assert.assertNotSame(_mockHostResolver, metricsFactory.getHostResolver());
        Assert.assertTrue(metricsFactory.getUuidFactory() instanceof SplittableRandomUuidFactory);
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertSame(sink, metricsFactory.getSinks().get(0));

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics instanceof TsdMetrics);
        Mockito.verify(_mockHostResolver, Mockito.never()).get();

        final ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        metrics.close();
        Mockito.verify(sink).record(eventArgumentCaptor.capture());
        final Event event = eventArgumentCaptor.getValue();
        Assert.assertEquals("bar.example.com", event.getAnnotations().get("_host"));
    }

    @Test
    public void testCustomUuidFactory() throws Exception {
        Mockito.doReturn("MyHost").when(_mockHostResolver).get();
        Mockito.when(_mockUuidFactory.get()).thenReturn(UUID.randomUUID(), UUID.randomUUID());

        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(_mockHostResolver)
                .setUuidFactory(_mockUuidFactory)
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .build();

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Mockito.verify(_mockUuidFactory, Mockito.times(1)).get();
        final Metrics metrics2 = metricsFactory.create();
        Assert.assertNotNull(metrics2);
        Mockito.verify(_mockUuidFactory, Mockito.times(2)).get();
    }

    @Test
    public void testToString() {
        final String asString = new TsdMetricsFactory.Builder()
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .setHostName("MyHost")
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

    @Mock
    private HostResolver _mockHostResolver;
    @Mock
    private UuidFactory _mockUuidFactory;

    /**
     * Invalid default sink. This sink is an invalid default sink because it
     * lacks a nested builder class. There are other reasons the sink would
     * be invalid as a default sink, but this is one of them.
     */
    public static class InvalidDefaultSink implements Sink {

        @Override
        public void record(final Event event) {
            // Do nothing
        }
    }

    /**
     * Valid default sink.
     */
    public static class ValidDefaultSink implements Sink {

        @Override
        public void record(final Event event) {
            // Do nothing
        }

        /**
         * Builder for {@link ValidDefaultSink}.
         */
        public static class Builder implements com.arpnetworking.commons.builder.Builder<Sink> {

            @Override
            public Sink build() {
                return new ValidDefaultSink();
            }
        }
    }
}

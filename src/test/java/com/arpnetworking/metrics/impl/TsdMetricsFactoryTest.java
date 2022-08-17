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
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.After;
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
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TsdMetricsFactoryTest {

    @Before
    public void setUp() {
        _mocks = MockitoAnnotations.openMocks(this);
    }

    @After
    public void after() throws Exception {
        _mocks.close();
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

        MatcherAssert.assertThat(metricsFactory.getHostResolver(), Matchers.instanceOf(BackgroundCachingHostResolver.class));
        MatcherAssert.assertThat(metricsFactory.getUuidFactory(), Matchers.instanceOf(SplittableRandomUuidFactory.class));
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        MatcherAssert.assertThat(metricsFactory.getSinks().get(0), Matchers.instanceOf(WarningSink.class));

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        MatcherAssert.assertThat(metrics, Matchers.instanceOf(TsdMetrics.class));
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
        MatcherAssert.assertThat(metricsFactory.getUuidFactory(), Matchers.instanceOf(SplittableRandomUuidFactory.class));
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        MatcherAssert.assertThat(metricsFactory.getSinks().get(0), Matchers.instanceOf(WarningSink.class));

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        MatcherAssert.assertThat(metrics, Matchers.instanceOf(TsdMetrics.class));
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
        MatcherAssert.assertThat(metricsFactory.getUuidFactory(), Matchers.instanceOf(SplittableRandomUuidFactory.class));
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        MatcherAssert.assertThat(metricsFactory.getSinks().get(0), Matchers.instanceOf(WarningSink.class));

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        MatcherAssert.assertThat(metrics, Matchers.instanceOf(TsdMetrics.class));
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

        MatcherAssert.assertThat(metricsFactory.getHostResolver(), Matchers.instanceOf(BackgroundCachingHostResolver.class));
        MatcherAssert.assertThat(metricsFactory.getUuidFactory(), Matchers.instanceOf(SplittableRandomUuidFactory.class));
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertSame(sink, metricsFactory.getSinks().get(0));

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        MatcherAssert.assertThat(metrics, Matchers.instanceOf(TsdMetrics.class));
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
        MatcherAssert.assertThat(metrics, Matchers.instanceOf(TsdMetrics.class));
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
        MatcherAssert.assertThat(metrics, Matchers.instanceOf(TsdMetrics.class));
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
        MatcherAssert.assertThat(metricsFactory.getSinks().get(0), Matchers.instanceOf(WarningSink.class));

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
        MatcherAssert.assertThat(metricsFactory.getSinks().get(0), Matchers.instanceOf(WarningSink.class));
        @SuppressWarnings("resource")
        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        MatcherAssert.assertThat(metrics, Matchers.instanceOf(TsdMetrics.class));
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
        MatcherAssert.assertThat(metricsFactory.getSinks().get(0), Matchers.instanceOf(WarningSink.class));
        @SuppressWarnings("resource")
        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        MatcherAssert.assertThat(metrics, Matchers.instanceOf(TsdMetrics.class));
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
        MatcherAssert.assertThat(metricsFactory.getUuidFactory(), Matchers.instanceOf(SplittableRandomUuidFactory.class));
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertSame(sink, metricsFactory.getSinks().get(0));

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        MatcherAssert.assertThat(metrics, Matchers.instanceOf(TsdMetrics.class));
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
        MatcherAssert.assertThat(metricsFactory.getUuidFactory(), Matchers.instanceOf(SplittableRandomUuidFactory.class));
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertSame(sink, metricsFactory.getSinks().get(0));

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        MatcherAssert.assertThat(metrics, Matchers.instanceOf(TsdMetrics.class));
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
        MatcherAssert.assertThat(sinks.iterator().next(), Matchers.instanceOf(WarningSink.class));
    }

    @Test
    public void testCreateDefaultSinksInvalid() {
        final List<Sink> sinks = TsdMetricsFactory.createDefaultSinks(
                Collections.singletonList("com.arpnetworking.metrics.impl.NonExistentSink"));
        Assert.assertNotNull(sinks);
        Assert.assertEquals(1, sinks.size());
        MatcherAssert.assertThat(sinks.iterator().next(), Matchers.instanceOf(WarningSink.class));
    }

    @Test
    public void testCreateDefaultSinksValid() {
        final List<Sink> sinks = TsdMetricsFactory.createDefaultSinks(
                Collections.singletonList("com.arpnetworking.metrics.impl.TsdMetricsFactoryTest$ValidDefaultSink"));
        Assert.assertNotNull(sinks);
        Assert.assertEquals(1, sinks.size());
        MatcherAssert.assertThat(sinks.iterator().next(), Matchers.instanceOf(ValidDefaultSink.class));
    }

    @Test
    public void testCreateDefaultSinksMultipleUseFirst() {
        final List<Sink> sinks = TsdMetricsFactory.createDefaultSinks(
                Arrays.asList(
                        "com.arpnetworking.metrics.impl.TsdMetricsFactoryTest$ValidDefaultSink",
                        "com.arpnetworking.metrics.impl.NonExistentSink"));
        Assert.assertNotNull(sinks);
        Assert.assertEquals(1, sinks.size());
        MatcherAssert.assertThat(sinks.iterator().next(), Matchers.instanceOf(ValidDefaultSink.class));
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
        MatcherAssert.assertThat(sinks.iterator().next(), Matchers.instanceOf(ValidDefaultSink.class));
    }

    @Test
    public void testCreateSinkSuccess() {
        final Optional<Sink> sink = TsdMetricsFactory.createSink(WarningSink.class);
        Assert.assertTrue(sink.isPresent());
        MatcherAssert.assertThat(sink.get(), Matchers.instanceOf(WarningSink.class));
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
        Assert.assertEquals(WarningSink.class, sinkClass.get());
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
    private AutoCloseable _mocks;

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

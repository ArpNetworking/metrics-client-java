/**
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
import com.arpnetworking.metrics.Event;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.Sink;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;

import java.io.File;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests for <code>TsdMetricsFactory</code>.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class TsdMetricsFactoryTest {

    @AfterClass
    public static void afterClass() {
        new File("./query.log").deleteOnExit();
    }

    @Test
    public void testNewInstance() throws UnknownHostException {
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) TsdMetricsFactory.newInstance(
                "MyService",
                "MyCluster",
                new File("./"));

        Assert.assertTrue(metricsFactory.getHostResolver() instanceof BackgroundCachingHostResolver);
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertTrue(metricsFactory.getSinks().get(0) instanceof StenoLogSink
                || metricsFactory.getSinks().get(0) instanceof TsdLogSink);

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics instanceof TsdMetrics);
    }

    @Test
    public void testBuilderDefaults() throws UnknownHostException {
        final Logger logger = Mockito.mock(Logger.class);
        final HostResolver hostResolver = Mockito.mock(HostResolver.class);
        Mockito.doReturn("MyHost").when(hostResolver).getLocalHostName();
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(hostResolver, logger)
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .build();

        Assert.assertSame(hostResolver, metricsFactory.getHostResolver());
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertTrue(metricsFactory.getSinks().get(0) instanceof StenoLogSink
                || metricsFactory.getSinks().get(0) instanceof TsdLogSink);

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics instanceof TsdMetrics);
        Mockito.verify(hostResolver).getLocalHostName();
    }

    @Test
    public void testBuilderNullSinks() throws UnknownHostException {
        final Logger logger = Mockito.mock(Logger.class);
        final HostResolver hostResolver = Mockito.mock(HostResolver.class);
        Mockito.doReturn("MyHost").when(hostResolver).getLocalHostName();
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(hostResolver, logger)
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .setSinks(null)
                .build();

        Assert.assertSame(hostResolver, metricsFactory.getHostResolver());
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertTrue(metricsFactory.getSinks().get(0) instanceof StenoLogSink
                || metricsFactory.getSinks().get(0) instanceof TsdLogSink);

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics instanceof TsdMetrics);
        Mockito.verify(hostResolver).getLocalHostName();
    }

    @Test
    public void testBuilderNullHostResolver() throws UnknownHostException {
        final Logger logger = Mockito.mock(Logger.class);
        final Sink sink = Mockito.mock(Sink.class);
        final HostResolver hostResolver = Mockito.mock(HostResolver.class);
        Mockito.doReturn("MyHost").when(hostResolver).getLocalHostName();
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(null, logger)
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .setSinks(Collections.singletonList(sink))
                .build();

        Assert.assertTrue(metricsFactory.getHostResolver() instanceof BackgroundCachingHostResolver);
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
        final HostResolver hostResolver = Mockito.mock(HostResolver.class);
        Mockito.doThrow(new UnknownHostException()).when(hostResolver).getLocalHostName();

        final TsdMetricsFactory.Builder metricsFactoryBuilder = new TsdMetricsFactory.Builder(hostResolver, logger)
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .setSinks(Collections.singletonList(sink));

        final TsdMetricsFactory metricsFactory = new TsdMetricsFactory(metricsFactoryBuilder, logger);

        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertSame(sink, metricsFactory.getSinks().get(0));

        @SuppressWarnings("resource")
        final Metrics metrics = metricsFactory.create();
        Mockito.verify(logger).warn(Mockito.anyString(), Mockito.any(UnknownHostException.class));
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
        Mockito.verify(sink1).record(org.mockito.Matchers.any(Event.class));
        Mockito.verify(sink2).record(org.mockito.Matchers.any(Event.class));
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
        final HostResolver hostResolver = Mockito.mock(HostResolver.class);
        Mockito.doReturn("foo.example.com").when(hostResolver).getLocalHostName();
        final Sink sink = Mockito.mock(Sink.class);

        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(hostResolver)
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .setSinks(Collections.singletonList(sink))
                .build();

        Assert.assertSame(hostResolver, metricsFactory.getHostResolver());
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertSame(sink, metricsFactory.getSinks().get(0));

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics instanceof TsdMetrics);
        Mockito.verify(hostResolver).getLocalHostName();

        final ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        metrics.close();
        Mockito.verify(sink).record(eventArgumentCaptor.capture());
        final Event event = eventArgumentCaptor.getValue();
        Assert.assertEquals("foo.example.com", event.getAnnotations().get("_host"));
    }

    @Test
    public void testBuilderHostNameOverride() throws UnknownHostException {
        final HostResolver hostResolver = Mockito.mock(HostResolver.class);
        Mockito.doReturn("foo.example.com").when(hostResolver).getLocalHostName();
        final Sink sink = Mockito.mock(Sink.class);

        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(hostResolver)
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .setHostName("bar.example.com")
                .setSinks(Collections.singletonList(sink))
                .build();

        Assert.assertNotSame(hostResolver, metricsFactory.getHostResolver());
        Assert.assertEquals("MyService", metricsFactory.getServiceName());
        Assert.assertEquals("MyCluster", metricsFactory.getClusterName());
        Assert.assertEquals(1, metricsFactory.getSinks().size());
        Assert.assertSame(sink, metricsFactory.getSinks().get(0));

        final Metrics metrics = metricsFactory.create();
        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics instanceof TsdMetrics);
        Mockito.verify(hostResolver, Mockito.never()).getLocalHostName();

        final ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        metrics.close();
        Mockito.verify(sink).record(eventArgumentCaptor.capture());
        final Event event = eventArgumentCaptor.getValue();
        Assert.assertEquals("bar.example.com", event.getAnnotations().get("_host"));
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
}

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

import com.arpnetworking.commons.hostresolver.DefaultHostResolver;
import com.arpnetworking.commons.hostresolver.HostResolver;
import com.arpnetworking.metrics.Event;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.Sink;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

import java.io.File;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for <code>TsdMetricsFactory</code>.
 *
 * @author Ville Koskela (vkoskela at groupon dot com)
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

        Assert.assertEquals(DEFAULT_HOST_RESOLVER.getLocalHostName(), metricsFactory.getHostName());
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

        Assert.assertEquals("MyHost", metricsFactory.getHostName());
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

        Assert.assertEquals("MyHost", metricsFactory.getHostName());
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
    public void testBuilderOverrideHost() throws UnknownHostException {
        final Logger logger = Mockito.mock(Logger.class);
        final HostResolver hostResolver = Mockito.mock(HostResolver.class);
        Mockito.doReturn("MyHost").when(hostResolver).getLocalHostName();
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(hostResolver, logger)
                .setHostName("Foo")
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .build();

        Assert.assertEquals("Foo", metricsFactory.getHostName());
    }

    @Test
    public void testBuilderHostResolverFailure() throws UnknownHostException {
        final Logger logger = Mockito.mock(Logger.class);
        final HostResolver hostResolver = Mockito.mock(HostResolver.class);
        Mockito.doThrow(new UnknownHostException()).when(hostResolver).getLocalHostName();
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(hostResolver, logger)
                .setClusterName("MyCluster")
                .setServiceName("MyService")
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
    public void testBuilderFailureHostName() {
        final Logger logger = Mockito.mock(Logger.class);
        final HostResolver hostResolver = Mockito.mock(HostResolver.class);
        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(hostResolver, logger)
                .setServiceName("MyService")
                .setClusterName("MyCluster")
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
    public void testBuilderWithHostProvider() throws UnknownHostException {
        final HostResolver hostResolver = Mockito.mock(HostResolver.class);
        Mockito.doReturn("foo.example.com").when(hostResolver).getLocalHostName();

        final TsdMetricsFactory metricsFactory = (TsdMetricsFactory) new TsdMetricsFactory.Builder(hostResolver)
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .build();

        Assert.assertEquals("foo.example.com", metricsFactory.getHostName());
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

    private static final HostResolver DEFAULT_HOST_RESOLVER = new DefaultHostResolver();
}

/*
 * Copyright 2015 Groupon.com
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
package com.arpnetworking.metrics;

import com.arpnetworking.commons.hostresolver.BackgroundCachingHostResolver;
import com.arpnetworking.metrics.impl.TsdMetricsFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ensures code samples from README.md at least compile.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
@SuppressWarnings(value = {"resource", "try"})
public final class Samples {

    // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
    private void metricsFactoryStaticFactory() {
        // Begin sample:
        final Map<String, String> defaultDimensions = new HashMap<>();
        defaultDimensions.put("service", "MyServiceName");
        defaultDimensions.put("cluster", "MyService-US-Prod");
        final MetricsFactory metricsFactory = TsdMetricsFactory.newInstance(
                defaultDimensions,
                Collections.singletonMap("host", BackgroundCachingHostResolver.getInstance()));
    }

// TODO(ville): Implement me!
/*
    private void metricsFactoryBuilder() {
        // Begin sample:
        final Map<String, String> defaultDimensions = new HashMap<>();
        defaultDimensions.put("service", "MyServiceName");
        defaultDimensions.put("cluster", "MyService-US-Prod");
        final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
                .setDefaultDimensions(defaultDimensions)
                .setDefaultComputedDimensions(
                        Collections.singletonMap(
                                "host",
                                BackgroundCachingHostResolver.getInstance()))
                .setSinks(Collections.singletonList(
                        new TsdLogSink.Builder()
                                .setDirectory(new File("/var/logs"))
                                .setName("myservice-query")
                                .setExtension(".json")
                                .setMaxHistory(168)
                                .setCompress(false)
                                .build()))
                .build();
    }
 */
    // CHECKSTYLE.ON: IllegalInstantiation

    private void metrics() {
        final MetricsFactory metricsFactory = createMetricsFactory();

        // Begin sample:
        final Metrics metrics = metricsFactory.create();

        metrics.incrementCounter("foo");
        metrics.startTimer("bar");
        // Do something that is being timed
        metrics.stopTimer("bar");
        metrics.setGauge("temperature", 21.7);
        metrics.close();
    }

    private void counters() {
        final Metrics metrics = createMetrics();

        // Begin sample:
        for (String s : Arrays.asList("a", "b", "c", "d", "e")) {
            metrics.incrementCounter("strings");
            // Do something in a loop
        }

        metrics.resetCounter("strings");
        for (String s : Arrays.asList("a", "b", "c", "d", "e")) {
            metrics.incrementCounter("strings");
            // Do something in a loop
        }

        for (List<String> listOfString : Arrays.asList(
                Arrays.asList("a", "b", "c"),
                Arrays.asList("d", "e"),
                Arrays.asList("f"))) {
            metrics.resetCounter("strings");
            for (String s : listOfString) {
                metrics.incrementCounter("s");
                // Do something in a nested loop
            }
        }

        final Counter counter = metrics.createCounter("strings");
        for (String s : Arrays.asList("a", "b", "c", "d", "e")) {
            counter.increment();
            // Do something in a loop
        }
    }

    private void closeable() throws InterruptedException {
        final MetricsFactory metricsFactory = createMetricsFactory();

        // Begin sample:
        try (Metrics metrics = metricsFactory.create()) {
            try (Timer timer = metrics.createTimer("timer")) {
                // Time unsafe operation (e.g. this may throw)
                Thread.sleep(1000);
            }
        }
    }

    private MetricsFactory createMetricsFactory() {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<String, String> defaultDimensions = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        defaultDimensions.put("service", "MyServiceName");
        defaultDimensions.put("cluster", "MyService-US-Prod");
        return TsdMetricsFactory.newInstance(defaultDimensions, Collections.emptyMap());
    }

    private Metrics createMetrics(final MetricsFactory metricsFactory) {
        return metricsFactory.create();
    }

    private Metrics createMetrics() {
        return createMetrics(createMetricsFactory());
    }
}

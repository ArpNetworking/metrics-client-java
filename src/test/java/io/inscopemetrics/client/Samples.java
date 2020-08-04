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
package io.inscopemetrics.client;

import com.arpnetworking.commons.hostresolver.BackgroundCachingHostResolver;
import io.inscopemetrics.client.impl.ThreadSafePeriodicMetrics;
import io.inscopemetrics.client.impl.TsdMetricsFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private void scopedMetrics() {
        final MetricsFactory metricsFactory = createMetricsFactory();

        // Begin sample:
        final ScopedMetrics scopedMetrics = metricsFactory.createScopedMetrics();

        scopedMetrics.incrementCounter("foo");
        scopedMetrics.startTimer("bar");
        // Do something that is being timed
        scopedMetrics.stopTimer("bar");
        scopedMetrics.recordGauge("temperature", 21.7);
        scopedMetrics.close();
    }

    private void periodicMetrics() {
        final MetricsFactory metricsFactory = createMetricsFactory();

        // Begin sample:
        final PeriodicMetrics periodicMetrics = metricsFactory.schedulePeriodicMetrics(Duration.ofMinutes(5));
        periodicMetrics.recordGauge("foo", 1);
        periodicMetrics.registerPolledMetric(metrics -> metrics.recordCounter("bar", 1));
    }

    private void periodicMetricsBuilders() {
        final MetricsFactory metricsFactory = createMetricsFactory();

        // Begin sample:
        final ThreadSafePeriodicMetrics periodicMetrics = new ThreadSafePeriodicMetrics.Builder()
                .setMetricsFactory(metricsFactory)
                .build();
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(periodicMetrics, 0, 1, TimeUnit.MINUTES);
    }

    private void counters() {
        final ScopedMetrics metrics = createMetrics();

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
        try (ScopedMetrics metrics = metricsFactory.createScopedMetrics()) {
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

    private ScopedMetrics createMetrics(final MetricsFactory metricsFactory) {
        return metricsFactory.createScopedMetrics();
    }

    private ScopedMetrics createMetrics() {
        return createMetrics(createMetricsFactory());
    }
}

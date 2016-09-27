/**
 * Copyright 2016 Groupon.com
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

import com.arpnetworking.metrics.impl.TsdLogSink;
import com.arpnetworking.metrics.impl.TsdMetricsFactory;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.UUID;

/**
 * Class for testing the addition of size based rolling in logback.
 *
 * @author Ryan Ascheman (rascheman at groupon dot com)
 */
public class RollingTest {
    @Test
    public void rollOnSize() {
        final String directory = "./target/testMetrics/" + UUID.randomUUID().toString();
        final File file = new File(directory);
        Assert.assertTrue(file.mkdirs());

        final Sink logSink = new TsdLogSink.Builder()
                .setDirectory(file)
                .setName("testProject")
                .setMaxFileSize("10KB")
                .build();
        final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
                .setServiceName("testService")
                .setClusterName("testCluster")
                .setSinks(Collections.singletonList(logSink))
                .build();

        for (int i = 0; i < 100; ++i) {
            for (int j = 0; j < 100; ++j) {
                try (final Metrics metrics = metricsFactory.create()) {
                    metrics.incrementCounter("testCounter" + j);
                }
            }
        }

        Assert.assertNotNull(file.list());
        Assert.assertTrue(file.list().length > 1);
    }

    @Test
    public void rollOnTime() {
        final String directory = "./target/testMetrics/" + UUID.randomUUID().toString();
        final File file = new File(directory);
        Assert.assertTrue(file.mkdirs());

        final Sink logSink = new TsdLogSink.Builder()
                .setDirectory(file)
                .setName("testProject")
                .build();
        final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
                .setServiceName("testService")
                .setClusterName("testCluster")
                .setSinks(Collections.singletonList(logSink))
                .build();

        for (int i = 0; i < 100; ++i) {
            for (int j = 0; j < 100; ++j) {
                try (final Metrics metrics = metricsFactory.create()) {
                    metrics.incrementCounter("testCounter" + j);
                }
            }
        }

        Assert.assertNotNull(file.list());
        Assert.assertEquals(1, file.list().length);

        // Sleep
        // Assert > 1
    }

    /*  This test does not function correctly for logback 1.0.7 due to the following line of code which protects the most
    *   recent two time periods from being removed (last two days, last two hours, etc).
    *   <link>https://github.com/qos-ch/logback/blob/2dad0019ebaff91a183200dab54e1e4a94407d5a/
    *   logback-core/src/main/java/ch/qos/logback/core/rolling/helper/TimeBasedArchiveRemover.java#L107</link>
    *
    *   As of Sept 27 2016, this line has been removed and this test functions as expected when run using the master
    *   branch of logback.
    **/
//    @Test
    public void testMaxSize() throws InterruptedException {
        final String directory = "./target/testMetrics/" + UUID.randomUUID().toString();

        final File fileCapped = new File(directory + "_capped");
        Assert.assertTrue(fileCapped.mkdirs());
        final Sink logSinkCapped = new TsdLogSink.Builder()
                .setDirectory(fileCapped)
                .setName("testProject1")
                .setMaxFileSize("100KB")
                .setTotalSizeCap("20KB")
                .setMaxHistory(20)
                .setPrudent(true)
                .build();
        final MetricsFactory metricsFactoryCapped = new TsdMetricsFactory.Builder()
                .setServiceName("testService")
                .setClusterName("testCluster")
                .setSinks(Collections.singletonList(logSinkCapped))
                .build();

        final File fileUncapped = new File(directory + "_uncapped");
        Assert.assertTrue(fileUncapped.mkdirs());
        final Sink logSinkUncapped = new TsdLogSink.Builder()
                .setDirectory(fileUncapped)
                .setName("testProject2")
                .setMaxFileSize("100KB")
                .setMaxHistory(20)
                .setPrudent(true)
                .build();
        final MetricsFactory metricsFactoryUncapped = new TsdMetricsFactory.Builder()
                .setServiceName("testService")
                .setClusterName("testCluster")
                .setSinks(Collections.singletonList(logSinkUncapped))
                .build();

        for (int i = 0; i < 200; ++i) {
            try (final Metrics metricz = metricsFactoryCapped.create();
                 final Metrics metrics = metricsFactoryUncapped.create()) {
                for (int j = 0; j < 300; ++j) {
                    metricz.incrementCounter("testCounter" + j);
                    metrics.incrementCounter("testCounter" + j);
                }
            }
//            System.out.print("uncapped :" + fileUncapped.list().length);
//            System.out.println("  capped :" + fileCapped.list().length);
            // Give the async file rolling a chance to work
            Thread.sleep(50);
        }

        Assert.assertNotNull(fileCapped.list());
        Assert.assertNotNull(fileUncapped.list());
        Assert.assertTrue(fileUncapped.list().length > fileCapped.list().length);
    }
}

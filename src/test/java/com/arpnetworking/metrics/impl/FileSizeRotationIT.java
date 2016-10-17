/**
 * Copyright 2016 Inscope Metrics Inc.
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

import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collections;

/**
 * Integration test of file size based rotation.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class FileSizeRotationIT {

    @Test
    public void test() throws IOException, InterruptedException {
        final Path basePath = Paths.get("./target/integration-test/");
        Files.createDirectories(basePath);
        final Path path = Files.createTempDirectory(basePath, "FileSizeRotationIT");

        final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
                .setClusterName("MyCluster")
                .setServiceName("MyService")
                .setHostName("MyHost")
                .setSinks(
                        Collections.singletonList(
                                new TsdLogSink.Builder()
                                        .setDirectory(path.toFile())
                                        .setMaxFileSize("200")
                                        .setMaxHistory(4)
                                        .setCompress(false)
                                        .setImmediateFlush(true)
                                        .build()))
                .build();
        // This should create base file
        Metrics metrics = metricsFactory.create();
        metrics.setGauge("test", 1);
        metrics.close();
        Thread.sleep(SLEEP_IN_MILLIS);
        assertBaseFileExists(path);
        assertRotatedFileExists(path, 0, false);
        assertRotatedFileExists(path, 1, false);
        assertRotatedFileExists(path, 2, false);
        assertRotatedFileExists(path, 3, false);
        assertRotatedFileExists(path, 4, false);
        // This should create file #0
        metrics = metricsFactory.create();
        metrics.setGauge("test", 2);
        metrics.close();
        Thread.sleep(SLEEP_IN_MILLIS);
        assertBaseFileExists(path);
        assertRotatedFileExists(path, 0, true);
        assertRotatedFileExists(path, 1, false);
        assertRotatedFileExists(path, 2, false);
        assertRotatedFileExists(path, 3, false);
        assertRotatedFileExists(path, 4, false);
        // This should create file #1
        metrics = metricsFactory.create();
        metrics.setGauge("test", 3);
        metrics.close();
        Thread.sleep(SLEEP_IN_MILLIS);
        assertBaseFileExists(path);
        assertRotatedFileExists(path, 0, true);
        assertRotatedFileExists(path, 1, true);
        assertRotatedFileExists(path, 2, false);
        assertRotatedFileExists(path, 3, false);
        assertRotatedFileExists(path, 4, false);
        // This should create file #2
        metrics = metricsFactory.create();
        metrics.setGauge("test", 4);
        metrics.close();
        Thread.sleep(SLEEP_IN_MILLIS);
        assertBaseFileExists(path);
        assertRotatedFileExists(path, 0, true);
        assertRotatedFileExists(path, 1, true);
        assertRotatedFileExists(path, 2, true);
        assertRotatedFileExists(path, 3, false);
        assertRotatedFileExists(path, 4, false);
        // This should delete file #0 and create file #3
        metrics = metricsFactory.create();
        metrics.setGauge("test", 5);
        metrics.close();
        Thread.sleep(SLEEP_IN_MILLIS);
        assertBaseFileExists(path);
        assertRotatedFileExists(path, 0, false);
        assertRotatedFileExists(path, 1, true);
        assertRotatedFileExists(path, 2, true);
        assertRotatedFileExists(path, 3, true);
        assertRotatedFileExists(path, 4, false);
    }

    private void assertBaseFileExists(final Path path) throws IOException {
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**/query.log");
        Files.walk(path, 1).forEach(System.out::println);
        final long count = Files.walk(path, 1).filter(matcher::matches).count();
        Assert.assertEquals(1, count);
    }

    private void assertRotatedFileExists(final Path path, final int index, final boolean exists) throws IOException {
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher(String.format("glob:**/query.*.%s.log", index));
        final long count = Files.walk(path, 1).filter(matcher::matches).count();
        Assert.assertEquals(exists ? 1 : 0, count);
    }

    // NOTE: This must allow the mask to be decreased which happens after eight
    // times the default increase time of 100 ms; so 850 ms.
    private static final long SLEEP_IN_MILLIS = 850;
}

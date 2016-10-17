/**
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
package com.arpnetworking.metrics.impl;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import com.arpnetworking.logback.SizeAndRandomizedTimeBasedFNATP;
import com.arpnetworking.metrics.Event;
import com.arpnetworking.metrics.Sink;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Tests for <code>BaseFileSink</code>.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class BaseFileSinkTest {

    @Test
    public void testBuilderWithDefaults() throws IOException {
        final String expectedPath = "./target/BaseFileSinkTest/testBuilderWithDefaults/";
        final TestFileSink sink = (TestFileSink) new TestFileSink.Builder()
                .setDirectory(createDirectory(expectedPath))
                .build();

        final AsyncAppender asyncAppender = (AsyncAppender)
                sink.getMetricsLogger().getAppender("query-log-async");
        final RollingFileAppender<ILoggingEvent> rollingAppender = (RollingFileAppender<ILoggingEvent>)
                asyncAppender.getAppender("query-log");
        @SuppressWarnings("unchecked")
        final TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = (TimeBasedRollingPolicy<ILoggingEvent>)
                rollingAppender.getRollingPolicy();
        final PatternLayoutEncoder encoder = (PatternLayoutEncoder) rollingAppender.getEncoder();
        final SizeAndRandomizedTimeBasedFNATP<ILoggingEvent> triggerPolicy =
                (SizeAndRandomizedTimeBasedFNATP<ILoggingEvent>) rollingPolicy.getTimeBasedFileNamingAndTriggeringPolicy();

        Assert.assertEquals(10 * 60 * 1000, triggerPolicy.getMaxOffsetInMillis());
        Assert.assertEquals("100MB", triggerPolicy.getMaxFileSize());
        Assert.assertEquals(500, asyncAppender.getQueueSize());
        Assert.assertEquals(0, asyncAppender.getDiscardingThreshold());
        Assert.assertFalse(rollingAppender.isPrudent());
        Assert.assertEquals(24, rollingPolicy.getMaxHistory());
        Assert.assertTrue(rollingPolicy.getFileNamePattern().endsWith(".gz"));
        Assert.assertTrue(encoder.isImmediateFlush());
        Assert.assertEquals(expectedPath + "query.log", rollingAppender.getFile());
        Assert.assertEquals(expectedPath + "query.%d{yyyy-MM-dd-HH}.%i.log.gz", rollingPolicy.getFileNamePattern());
    }

    @Test
    public void testCustomBuilder() throws IOException {
        final String expectedPath = "./target/BaseFileSinkTest/testBuilderWithoutImmediateFlush/";
        final TestFileSink sink = (TestFileSink) new TestFileSink.Builder()
                .setDirectory(createDirectory(expectedPath))
                .setMaxHistory(48)
                .setMaxFileSize("2GB")
                .setImmediateFlush(Boolean.FALSE)
                .setCompress(Boolean.FALSE)
                .setName("foo")
                .setExtension(".bar")
                .setMaxQueueSize(1000)
                .setDropWhenQueueFull(true)
                .build();

        final AsyncAppender asyncAppender = (AsyncAppender)
                sink.getMetricsLogger().getAppender("query-log-async");
        final RollingFileAppender<ILoggingEvent> rollingAppender = (RollingFileAppender<ILoggingEvent>)
                asyncAppender.getAppender("query-log");
        @SuppressWarnings("unchecked")
        final TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = (TimeBasedRollingPolicy<ILoggingEvent>)
                rollingAppender.getRollingPolicy();
        final PatternLayoutEncoder encoder = (PatternLayoutEncoder) rollingAppender.getEncoder();
        final SizeAndRandomizedTimeBasedFNATP<ILoggingEvent> triggerPolicy =
                (SizeAndRandomizedTimeBasedFNATP<ILoggingEvent>) rollingPolicy.getTimeBasedFileNamingAndTriggeringPolicy();

        Assert.assertEquals(10 * 60 * 1000, triggerPolicy.getMaxOffsetInMillis());
        Assert.assertEquals("2GB", triggerPolicy.getMaxFileSize());
        Assert.assertEquals(1000, asyncAppender.getQueueSize());
        Assert.assertEquals(1000, asyncAppender.getDiscardingThreshold());
        Assert.assertEquals(48, rollingPolicy.getMaxHistory());
        Assert.assertFalse(encoder.isImmediateFlush());
        Assert.assertEquals(expectedPath + "foo.bar", rollingAppender.getFile());
        Assert.assertEquals(expectedPath + "foo.%d{yyyy-MM-dd-HH}.%i.bar", rollingPolicy.getFileNamePattern());
    }

    @Test
    public void testBuilderWithNull() throws IOException {
        final String expectedPath = "./";
        final TestFileSink sink = (TestFileSink) new TestFileSink.Builder()
                .setCompress(null)
                .setDirectory(null)
                .setExtension(null)
                .setImmediateFlush(null)
                .setMaxHistory(null)
                .setMaxFileSize(null)
                .setName(null)
                .setMaxQueueSize(null)
                .setDropWhenQueueFull(null)
                .build();

        final AsyncAppender asyncAppender = (AsyncAppender)
                sink.getMetricsLogger().getAppender("query-log-async");
        final RollingFileAppender<ILoggingEvent> rollingAppender = (RollingFileAppender<ILoggingEvent>)
                asyncAppender.getAppender("query-log");
        @SuppressWarnings("unchecked")
        final TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = (TimeBasedRollingPolicy<ILoggingEvent>)
                rollingAppender.getRollingPolicy();
        final PatternLayoutEncoder encoder = (PatternLayoutEncoder) rollingAppender.getEncoder();
        final SizeAndRandomizedTimeBasedFNATP<ILoggingEvent> triggerPolicy =
                (SizeAndRandomizedTimeBasedFNATP<ILoggingEvent>) rollingPolicy.getTimeBasedFileNamingAndTriggeringPolicy();

        Assert.assertEquals(10 * 60 * 1000, triggerPolicy.getMaxOffsetInMillis());
        Assert.assertEquals("100MB", triggerPolicy.getMaxFileSize());
        Assert.assertEquals(500, asyncAppender.getQueueSize());
        Assert.assertEquals(0, asyncAppender.getDiscardingThreshold());
        Assert.assertFalse(rollingAppender.isPrudent());
        Assert.assertEquals(24, rollingPolicy.getMaxHistory());
        Assert.assertTrue(encoder.isImmediateFlush());
        Assert.assertEquals(expectedPath + "query.log", rollingAppender.getFile());
        Assert.assertEquals(expectedPath + "query.%d{yyyy-MM-dd-HH}.%i.log.gz", rollingPolicy.getFileNamePattern());

        Files.deleteIfExists(new File("./query.log").toPath());
    }

    @Test
    public void testBuilderWithDirectoryNotExisting() throws IOException {
        final String expectedPath = "./target/BaseFileSinkTest/testBuilderWithDirectoryNotExisting";
        final Sink sink = new TestFileSink.Builder()
                .setDirectory(new File(expectedPath))
                .build();

        Assert.assertNotNull(sink);
        Assert.assertTrue(sink instanceof WarningSink);
    }

    @Test
    public void testBuilderWithDirectoryIsFile() throws IOException {
        final String expectedPath = "./target/BaseFileSinkTest/testBuilderWithDirectoryIsFile";
        Files.deleteIfExists(new File(expectedPath).toPath());
        Files.createDirectories(new File("./target/BaseFileSinkTest/").toPath());
        Files.createFile(new File(expectedPath).toPath());
        final Sink sink = new TestFileSink.Builder()
                .setDirectory(new File(expectedPath))
                .build();

        Assert.assertNotNull(sink);
        Assert.assertTrue(sink instanceof WarningSink);
    }

    @Test
    public void testBuilderEmptyDirectory() throws IOException {
        final String expectedPath = "";
        final Sink sink = new TestFileSink.Builder()
                .setDirectory(new File(expectedPath))
                .build();

        Assert.assertNotNull(sink);
        Assert.assertTrue(sink instanceof WarningSink);
    }

    @Test
    public void testShutdownHookThread() throws InterruptedException {
        final LoggerContext context = Mockito.mock(LoggerContext.class);
        final Thread shutdownThread = new TestFileSink.ShutdownHookThread(context);
        shutdownThread.start();
        shutdownThread.join();
        Mockito.verify(context).stop();
    }

    private static File createDirectory(final String path) throws IOException {
        final File directory = new File(path);
        Files.createDirectories(directory.toPath());
        return directory;
    }

    private static final class TestFileSink extends BaseFileSink {

        @Override
        public void record(final Event event) {
            getMetricsLogger().info(event.toString());
        }

        private static Encoder<ILoggingEvent> createEncoder(final boolean immediateFlush) {
            final PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setPattern("%msg%n");
            encoder.setImmediateFlush(immediateFlush);
            return encoder;
        }

        private TestFileSink(final Builder builder) {
            super(builder, createEncoder(builder._immediateFlush));
        }

        public static class Builder extends BaseFileSink.Builder<TestFileSink, Builder> {

            /**
             * {@inheritDoc}
             */
            @Override
            protected TestFileSink createSink() {
                return new TestFileSink(this);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            protected Builder self() {
                return this;
            }
        }
    }
}

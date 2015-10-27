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
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import com.arpnetworking.metrics.Sink;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for <code>Sink</code> implementations which write to files. This
 * implementation uses Logback as the underlying implementation to write events
 * to disk. It is designed not to interfere with Logback or SLF4J usage for
 * application logging.
 *
 * @author Ville Koskela (vkoskela at groupon dot com)
 */
/* package private */ abstract class BaseFileSink implements Sink {

    /**
     * Accessor for the <code>Logger</code> instance to write to.
     *
     * @return The <code>Logger</code> instance to write to.
     */
    protected Logger getMetricsLogger() {
        return _metricsLogger;
    }

    private TimeBasedRollingPolicy<ILoggingEvent> createRollingPolicy(
            final String extension,
            final String fileNameWithoutExtension,
            final int maxHistory,
            final boolean compress) {

        // TODO(vkoskela): Use LogbackSteno's random compression strategy [MAI-413]
        final TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setContext(_loggerContext);
        rollingPolicy.setMaxHistory(maxHistory);
        rollingPolicy.setCleanHistoryOnStart(true);
        if (compress) {
            rollingPolicy.setFileNamePattern(fileNameWithoutExtension + DATE_EXTENSION + extension + GZIP_EXTENSION);
        } else {
            rollingPolicy.setFileNamePattern(fileNameWithoutExtension + DATE_EXTENSION + extension);
        }

        return rollingPolicy;
    }

    private FileAppender<ILoggingEvent> createRollingAppender(
            final String fileName,
            final boolean prudent,
            final TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy,
            final Encoder<ILoggingEvent> encoder) {
        final RollingFileAppender<ILoggingEvent> rollingAppender = new RollingFileAppender<>();
        rollingAppender.setContext(_loggerContext);
        rollingAppender.setName("query-log");
        rollingAppender.setFile(fileName);
        // TODO(vkoskela): Implement prudent mode [MAI-415]
        //rollingAppender.setPrudent(prudent);
        rollingAppender.setAppend(true);
        rollingAppender.setRollingPolicy(rollingPolicy);
        rollingAppender.setEncoder(encoder);
        return rollingAppender;
    }

    private Appender<ILoggingEvent> createAsyncAppender(final Appender<ILoggingEvent> appender) {
        final AsyncAppender asyncAppender = new AsyncAppender();
        asyncAppender.setContext(_loggerContext);
        asyncAppender.setDiscardingThreshold(0);
        asyncAppender.setName("query-log-async");
        asyncAppender.setQueueSize(QUEUE_SIZE);
        asyncAppender.addAppender(appender);
        return asyncAppender;
    }

    /**
     * Protected constructor.
     *
     * @param builder Instance of <code>Builder</code>.
     */
    protected BaseFileSink(
            final Builder<? extends BaseFileSink, ? extends Builder<? extends Sink, ?>> builder,
            final Encoder<ILoggingEvent> encoder) {
        final String directory = builder._directory.getPath();
        final String name = builder._name;
        final String extension = builder._extension;
        final int maxHistory = builder._maxHistory.intValue();
        final boolean compress = builder._compress.booleanValue();
        final boolean prudent = builder._prudent.booleanValue();

        final StringBuilder fileNameBuilder = new StringBuilder(directory);
        fileNameBuilder.append(File.separator);
        fileNameBuilder.append(name);
        final String fileNameWithoutExtension = fileNameBuilder.toString();
        fileNameBuilder.append(extension);
        final String fileName = fileNameBuilder.toString();

        _loggerContext = new LoggerContext();
        encoder.setContext(_loggerContext);

        final TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = createRollingPolicy(
                extension,
                fileNameWithoutExtension,
                maxHistory,
                compress);
        final FileAppender<ILoggingEvent> rollingAppender = createRollingAppender(
                fileName,
                prudent,
                rollingPolicy,
                encoder);
        final Appender<ILoggingEvent> asyncAppender = createAsyncAppender(rollingAppender);

        rollingPolicy.setParent(rollingAppender);
        rollingPolicy.start();
        encoder.start();
        rollingAppender.start();
        asyncAppender.start();

        final Logger rootLogger = _loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);
        rootLogger.addAppender(asyncAppender);

        Runtime.getRuntime().addShutdownHook(new ShutdownHookThread(_loggerContext));

        _metricsLogger = _loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
    }

    private final LoggerContext _loggerContext;
    private final Logger _metricsLogger;

    private static final String DATE_EXTENSION = ".%d{yyyy-MM-dd-HH}";
    private static final String GZIP_EXTENSION = ".gz";
    private static final Integer QUEUE_SIZE = 500;

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(BaseFileSink.class);

    // NOTE: Package private for testing
    /* package private */ static final class ShutdownHookThread extends Thread {

        /* package private */ ShutdownHookThread(final LoggerContext context) {
            _context = context;
        }

        @Override
        public void run() {
            _context.stop();
        }

        private final LoggerContext _context;
    }

    /**
     * Builder for <code>BaseFileSink</code>.
     *
     * This class is thread safe.
     *
     * @author Ville Koskela (vkoskela at groupon dot com)
     */
    public abstract static class Builder<T extends Sink, B extends Builder<? extends Sink, ?>> {

        /**
         * Create an instance of <code>Sink</code>.
         *
         * @return Instance of <code>Sink</code>.
         */
        public Sink build() {
            // Defaults
            applyDefaults();

            // Validate
            final List<String> failures = new ArrayList<>();
            validate(failures);

            // Fallback
            if (!failures.isEmpty()) {
                LOGGER.warn(String.format(
                        "Unable to construct %s, metrics disabled; failures=%s",
                        this.getClass().getEnclosingClass().getSimpleName(),
                        failures));
                return new WarningSink(failures);
            }

            return createSink();
        }

        /**
         * Set the directory. Optional; default is the current working directory
         * of the application.
         *
         * @param value The value for directory.
         * @return This <code>Builder</code> instance.
         */
        public B setDirectory(final File value) {
            _directory = value;
            return self();
        }

        /**
         * Set the file name without extension. Optional; default is "query".
         * The file name without extension cannot be empty.
         *
         * @param value The value for name.
         * @return This <code>Builder</code> instance.
         */
        public B setName(final String value) {
            _name = value;
            return self();
        }

        /**
         * Set the file extension. Optional; default is ".log".
         *
         * @param value The value for extension.
         * @return This <code>Builder</code> instance.
         */
        public B setExtension(final String value) {
            _extension = value;
            return self();
        }

        /**
         * Set the max history to retain. Optional; default is 24.
         *
         * @param value The value for max history.
         * @return This <code>Builder</code> instance.
         */
        public B setMaxHistory(final Integer value) {
            _maxHistory = value;
            return self();
        }

        /**
         * Set whether files are compressed on roll. Optional; default is true.
         *
         * @param value Whether to compress on roll.
         * @return This <code>Builder</code> instance.
         */
        public B setCompress(final Boolean value) {
            _compress = value;
            return self();
        }

        /**
         * Set whether entries are flushed immediately. Entries are still
         * written asynchronously. Optional; default is true.
         *
         * @param value Whether to flush immediately.
         * @return This <code>Builder</code> instance.
         */
        public B setImmediateFlush(final Boolean value) {
            _immediateFlush = value;
            return self();
        }

        /**
         * Set whether logging should be prudent. Set this to true if multiple
         * <code>Sink</code> instances, even across JVMs, are writing to the
         * same file. Optional; default is false.
         *
         * @param value Whether to be prudent.
         * @return This <code>Builder</code> instance.
         */
        public B setPrudent(final Boolean value) {
            _prudent = value;
            return self();
        }

        /**
         * Protected method allows child builder classes to add additional
         * defaulting behavior to fields.
         */
        protected void applyDefaults() {
            if (_directory == null) {
                _directory = DEFAULT_DIRECTORY;
                LOGGER.info(String.format("Defaulted null directory; directory=%s", _directory));
            }
            if (_name == null) {
                _name = DEFAULT_NAME;
                LOGGER.info(String.format("Defaulted null name; name=%s", _name));
            }
            if (_extension == null) {
                _extension = DEFAULT_EXTENSION;
                LOGGER.info(String.format("Defaulted null extension; extension=%s", _extension));
            }
            if (_maxHistory == null) {
                _maxHistory = DEFAULT_MAX_HISTORY;
                LOGGER.info(String.format("Defaulted null max history; maxHistory=%s", _maxHistory));
            }
            if (_compress == null) {
                _compress = DEFAULT_COMPRESS;
                LOGGER.info(String.format("Defaulted null compress; compress=%s", _compress));
            }
            if (_immediateFlush == null) {
                _immediateFlush = DEFAULT_IMMEDIATE_FLUSH;
                LOGGER.info(String.format("Defaulted null immediate flush; immediateFlush=%s", _immediateFlush));
            }
            if (_prudent == null) {
                _prudent = DEFAULT_PRUDENT;
                LOGGER.info(String.format("Defaulted null prudent; prudent=%s", _prudent));
            }
        }

        /**
         * Protected method allows child builder classes to add additional
         * validation to fields.
         *
         * @param failures List of validation failures.
         */
        protected void validate(final List<String> failures) {
            if (!_directory.isDirectory()) {
                failures.add(String.format("Path is not a directory; path=%s", _directory));
            }
            if (!_directory.exists()) {
                failures.add(String.format("Path does not exist; path=%s", _directory));
            }
        }

        /**
         * Protected method delegates construction of the actual <code>Sink</code>
         * instance to the concrete builder child class.
         *
         * @return Instance of <code>T</code>.
         */
        protected abstract T createSink();

        /**
         * Protected method returns this builder with the correct top-level type.
         *
         * @return This builder with the correct top-level type.
         */
        protected abstract B self();

        protected File _directory = DEFAULT_DIRECTORY;
        protected String _name = DEFAULT_NAME;
        protected String _extension = DEFAULT_EXTENSION;
        protected Integer _maxHistory = DEFAULT_MAX_HISTORY;
        protected Boolean _compress = DEFAULT_COMPRESS;
        protected Boolean _immediateFlush = DEFAULT_IMMEDIATE_FLUSH;
        protected Boolean _prudent = DEFAULT_PRUDENT;

        private static final File DEFAULT_DIRECTORY = new File("./");
        private static final String DEFAULT_NAME = "query";
        private static final String DEFAULT_EXTENSION = ".log";
        private static final Integer DEFAULT_MAX_HISTORY = Integer.valueOf(24);
        private static final Boolean DEFAULT_COMPRESS = Boolean.TRUE;
        private static final Boolean DEFAULT_IMMEDIATE_FLUSH = Boolean.TRUE;
        private static final Boolean DEFAULT_PRUDENT = Boolean.FALSE;
    }
}

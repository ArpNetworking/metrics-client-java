/*
 * Copyright 2020 Dropbox
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
package io.inscopemetrics.client.impl;

import com.arpnetworking.commons.slf4j.RateLimitedLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Execute gathering and publication of measurements from periodic metrics
 * instances on specified intervals using a thread pool.
 *
 * This class is thread safe.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class PeriodicMetricsExecutor {

    private static final int DEFAULT_EXECUTOR_CORE_POOL_SIZE = 1;
    private static final int DEFAULT_EXECUTOR_KEEP_ALIVE_SECONDS = 60;
    private static final Supplier<ScheduledExecutorService> DEFAULT_WORKER_POOL_SUPPLIER;
    private static final Logger LOGGER = LoggerFactory.getLogger(PeriodicMetricsExecutor.class);

    static {
        DEFAULT_WORKER_POOL_SUPPLIER = () -> {
            final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                    DEFAULT_EXECUTOR_CORE_POOL_SIZE,
                    runnable -> new Thread(runnable, "periodic-metrics-worker"));
            executor.setKeepAliveTime(DEFAULT_EXECUTOR_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS);
            executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            return executor;
        };
    }

    private final Clock clock;
    private final Logger logger;
    private final Lock lock = new ReentrantLock(false);
    private final ScheduledExecutorService workerPool;
    private final List<ThreadSafePeriodicMetrics> registeredPeriodicMetrics;
    private final float offsetPercent;
    private final Duration closeTimeout;
    private final Duration taskExceededIntervalLoggingInterval;
    private final RateLimitedLogger taskExceededIntervalLogger;

    private PeriodicMetricsExecutor(final Builder builder) {
        workerPool = DEFAULT_WORKER_POOL_SUPPLIER.get();
        registeredPeriodicMetrics = new ArrayList<>();

        logger = builder.logger;
        clock = builder.clock;
        offsetPercent = builder.offsetPercent;
        closeTimeout = builder.closeTimeout;
        taskExceededIntervalLoggingInterval = builder.taskExceededIntervalLoggingInterval;

        taskExceededIntervalLogger = new RateLimitedLogger(
                "TaskExceededIntervalLogger",
                builder.logger,
                taskExceededIntervalLoggingInterval);
    }

    /**
     * Schedule the periodic metrics runnable for execution at the specified
     * interval clock edges. The first run is offset to occur at the next edge
     * of the specified interval. For example, if the interval is every 5 minutes,
     * and the job is submitted at 13:33:00 then the next execution will be
     * at 13:35:00. The following run would be at 13:40:00.
     *
     * All periods are anchored to epoch which guarantees that restarts do
     * not impact the execution interval. This is particularly important for
     * longer intervals and intervals which do not divide evenly into the
     * next larger time unit (e.g. 7 minutes).
     *
     * However, changes to the system clock either automatically or by the user
     * may result in consecutive executions closer together than requested
     * around the clock change. This is generally unavoidable with the
     * anchoring strategy described above.
     *
     * For example, at epoch T if the next 1 second interval is scheduled
     * for T+1000 (milliseconds) and at T+500 the clock is changed to T+2250
     * then when the task completes at T+2750 the interval to the previous
     * execution is correct at 1 effective second; however, the subsequent
     * execution will be scheduled to start at T+3000 resulting in an effective
     * interval of only 250 milliseconds.
     *
     * Additionally, changes to the system clock may result in false warnings
     * that periodic metric execution time exceeds the interval. In the example
     * above, the difference between the scheduled runs T+1000 and T+3000
     * exceeds the interval and so it would appear that the task took longer
     * to execute than the 1-second interval (while in reality time changed).
     *
     * Similarly, if the system is suspended and resumed the clock time
     * interval between executions cannot be guaranteed (and will be longer).
     *
     * These problems can be mitigated to a large extent using a short interval
     * scheduling thread which ticks towards the execution target time of the
     * next task. Unfortunately, in general it is impossible to both guarantee
     * the interval and ensure the execution on schedule edges. While this
     * implementation actually guarantees <u>neither</u>, it does guarantee
     * that:
     *
     * <ul>
     *     <li>The runtime interval from one execution to the next is never
     *     more than specified.</li>
     *     <li>No interval will have more than one execution.</li>
     * </ul>
     *
     * @param periodicMetrics the {@link ThreadSafePeriodicMetrics} instance
     * @param interval the base execution edge and interval
     */
    public void scheduleAtFixedRate(
            final ThreadSafePeriodicMetrics periodicMetrics,
            final Duration interval) {
        final Instant now = clock.instant();
        final Instant nextRun = computeNextRun(now, interval, offsetPercent);
        final Task task = new Task(periodicMetrics, interval, nextRun, this::reschedule);

        lock.lock();
        try {
            if (workerPool.isShutdown()) {
                logger.error("Scheduled periodic metrics for execution after executor was shutdown");
                return;
            }
            registeredPeriodicMetrics.add(periodicMetrics);
            workerPool.schedule(task, nextRun.toEpochMilli() - now.toEpochMilli(), TimeUnit.MILLISECONDS);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Shutdown the executor. Completes previously scheduled tasks but does not
     * permit scheduling of new tasks or rescheduling of existing tasks. All
     * scheduled periodic metrics instances are closed.
     *
     * @throws InterruptedException if the close is interrupted
     */
    public void close() throws InterruptedException {
        lock.lock();
        try {
            // Shutdown the worker pool; cleanly if possible
            // NOTE: This only allows currently running periodic tasks to
            // complete; any future executions are cancelled.
            workerPool.shutdown();
        } finally {
            lock.unlock();
        }

        try {
            if (!workerPool.awaitTermination(closeTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                workerPool.shutdownNow();
            }

            // Close all registered periodic metrics instances
            for (final ThreadSafePeriodicMetrics periodicMetrics : registeredPeriodicMetrics) {
                periodicMetrics.close();
            }
        } catch (final InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private void reschedule(final Task task) {
        final Instant now = clock.instant();
        final Duration interval = task.getInterval();
        final Instant nextRun = computeNextRun(now, interval, offsetPercent);

        // Update the next run on the task
        final Instant previousRun = task.setNextRun(nextRun);

        // Check if the task over ran its interval
        if (Duration.between(previousRun, nextRun).compareTo(interval) > 0) {
            // The previous run exceeded the interval; this will emit a
            // rate limited warning. Also note that in this case the nextRun
            // is the edge following completion of the task which means that
            // an edge was intentionally skipped (hence the warning).
            taskExceededIntervalLogger.warn(
                    String.format(
                            "Period metrics task execution time exceeded interval for: %s",
                            task));
        }

        // Synchronized scheduling of the task
        lock.lock();
        try {
            if (workerPool.isShutdown()) {
                // Silently stop rescheduling tasks
                return;
            }
            workerPool.schedule(task, nextRun.toEpochMilli() - now.toEpochMilli(), TimeUnit.MILLISECONDS);
        } finally {
            lock.unlock();
        }
    }

    static Instant computeNextRun(final Instant now, final Duration interval, final float offsetPercent) {
        final long epochMillis = now.toEpochMilli();
        final long intervalMillis = interval.toMillis();
        final long deltaMillis = epochMillis % intervalMillis;
        final long baseEpochMillis = epochMillis - deltaMillis;
        final long intervalOffsetMillis = (long) (intervalMillis * offsetPercent);

        final Instant nextRun = Instant.ofEpochMilli(baseEpochMillis + intervalOffsetMillis);
        if (nextRun.isAfter(now)) {
            return nextRun;
        }
        return nextRun.plusMillis(intervalMillis);
    }

    float getOffsetPercent() {
        return offsetPercent;
    }

    Duration getCloseTimeout() {
        return closeTimeout;
    }

    Duration getTaskExceededIntervalLoggingInterval() {
        return taskExceededIntervalLoggingInterval;
    }

    static class Task implements Runnable {

        private final Runnable runnable;
        private final Duration interval;
        private Instant nextRun;
        private final Consumer<Task> rescheduler;

        Task(
                final Runnable runnable,
                final Duration interval,
                final Instant nextRun,
                final Consumer<Task> rescheduler) {
            this.runnable = runnable;
            this.interval = interval;
            this.nextRun = nextRun;
            this.rescheduler = rescheduler;
        }

        public Duration getInterval() {
            return interval;
        }

        public Instant setNextRun(final Instant newNextRun) {
            final Instant previousRun = this.nextRun;
            this.nextRun = newNextRun;
            return previousRun;
        }

        @Override
        public void run() {
            runnable.run();
            rescheduler.accept(this);
        }

        @Override
        public String toString() {
            return String.format("{runnable=%s, interval=%s}", runnable, interval);
        }
    }

    /**
     * Builder for {@link PeriodicMetricsExecutor}.
     *
     * This class does not throw exceptions if it is used improperly. An
     * example of improper use would be if the constraints on a field are
     * not satisfied. To prevent breaking the client application no
     * exception is thrown; instead a warning is logged using the SLF4J
     * {@link LoggerFactory} for this class.
     *
     * This class is <b>NOT</b> thread safe.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    public static class Builder implements com.arpnetworking.commons.builder.Builder<PeriodicMetricsExecutor> {

        private static final float DEFAULT_OFFSET_PERCENT = 0.0f;
        private static final Duration DEFAULT_TASK_EXCEEDED_INTERVAL_LOGGING_INTERVAL = Duration.ofMinutes(1);
        private static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(3);

        private final Clock clock;
        private final Logger logger;

        private Float offsetPercent = DEFAULT_OFFSET_PERCENT;
        private Duration taskExceededIntervalLoggingInterval = DEFAULT_TASK_EXCEEDED_INTERVAL_LOGGING_INTERVAL;
        private Duration closeTimeout = DEFAULT_CLOSE_TIMEOUT;

        /**
         * Public constructor.
         */
        public Builder() {
            this(Clock.systemUTC(), LOGGER);
        }

        // NOTE: Package private for testing
        Builder(final Clock clock, final Logger logger) {
            this.clock = clock;
            this.logger = logger;
        }

        /**
         * Create an instance of {@link PeriodicMetricsExecutor}.
         *
         * @return Instance of {@link PeriodicMetricsExecutor}.
         */
        @Override
        public PeriodicMetricsExecutor build() {
            // Defaults
            if (offsetPercent == null) {
                offsetPercent = DEFAULT_OFFSET_PERCENT;
                logger.info(String.format(
                        "Defaulted null offset percent; offsetPercent=%s",
                        offsetPercent));
            }
            if (offsetPercent < 0.0) {
                offsetPercent = 0.0f;
                logger.info(String.format(
                        "Adjusted invalid (<0.0) offset percent; offsetPercent=%s",
                        offsetPercent));
            }
            if (offsetPercent > 1.0) {
                offsetPercent = 1.0f;
                logger.info(String.format(
                        "Adjusted invalid (>1.0) offset percent; offsetPercent=%s",
                        offsetPercent));
            }
            if (closeTimeout == null) {
                closeTimeout = DEFAULT_CLOSE_TIMEOUT;
                logger.info(String.format(
                        "Defaulted null close timeout; closeTimeout=%s",
                        closeTimeout));
            }
            if (taskExceededIntervalLoggingInterval == null) {
                taskExceededIntervalLoggingInterval = DEFAULT_TASK_EXCEEDED_INTERVAL_LOGGING_INTERVAL;
                logger.info(String.format(
                        "Defaulted null task exceeded interval logging interval; taskExceededIntervalLoggingInterval=%s",
                        taskExceededIntervalLoggingInterval));
            }

            return new PeriodicMetricsExecutor(this);
        }

        /**
         * Set the offset percent. Cannot be null. Optional. Must be [0.0, 1.0].
         * Defaults 0.0.
         *
         * @param value The offset percent.
         * @return This {@link Builder} instance.
         */
        public Builder setOffsetPercent(@Nullable final Float value) {
            offsetPercent = value;
            return this;
        }

        /**
         * Set the close timeout. Optional; default is 3 seconds.
         *
         * @param value The close timeout.
         * @return This {@link Builder} instance.
         */
        public Builder setCloseTimeout(@Nullable final Duration value) {
            closeTimeout = value;
            return this;
        }

        /**
         * Set the task exceeded interval logging interval. Any task exceeded
         * interval notices will be logged at most once per interval. Optional;
         * default is 1 minute.
         *
         * @param value The logging interval.
         * @return This {@link Builder} instance.
         */
        public Builder setTaskExceededIntervalLoggingInterval(@Nullable final Duration value) {
            taskExceededIntervalLoggingInterval = value;
            return this;
        }
    }
}

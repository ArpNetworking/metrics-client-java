/*
 * Copyright 2019 Dropbox
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

import io.inscopemetrics.client.AggregatedData;
import io.inscopemetrics.client.Counter;
import io.inscopemetrics.client.Event;
import io.inscopemetrics.client.Metrics;
import io.inscopemetrics.client.Quantity;
import io.inscopemetrics.client.Sink;
import io.inscopemetrics.client.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Default implementation of {@link Metrics} that publishes metrics as
 * time series data (TSD).
 *
 * This class does not throw exceptions if it is used improperly or if the
 * underlying IO subsystem fails to write the metrics. An example of improper
 * use would be if the user invokes stop on a timer without calling start. To
 * prevent breaking the client application no exception is thrown; instead a
 * warning is logged using the SLF4J {@link LoggerFactory} for this class.
 *
 * Another example would be if the disk is full or fails to record the metrics
 * when {@link LockFreeMetrics#close} is invoked the library will not throw an
 * exception. However, it will attempt to write a warning using the SLF4J
 * {@link LoggerFactory} for this class; although this is likely to fail
 * if the underlying hardware is experiencing problems.
 *
 * If clients desire an intrusive failure propagation strategy or prefer to
 * implement their own failure handling via callback we are open to implementing
 * such an alternative strategy. Please contact us with your feature request.
 *
 * For more information about the semantics of this class and its methods please
 * refer to the {@link Metrics} interface documentation. To create an
 * instance of this class use {@link TsdMetricsFactory}.
 *
 * This class is <b>NOT</b> thread safe; in fact, it is intended for use only
 * in contexts where the framework guarantees single threaded execution. For
 * example, Akka or Vert.x. In support of such an execution model this class
 * is implemented under the assumption of single threaded execution and as
 * a result does not use any locks.
 *
 * Most use cases will be better served with {@link TsdMetrics}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class LockFreeMetrics implements Metrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(LockFreeMetrics.class);
    private static final Utility.Predicate<Quantity> PREDICATE_STOPPED_TIMERS = new Utility.StoppedTimersPredicate();
    private static final Utility.Predicate<Quantity> PREDICATE_NON_ABORTED_TIMERS = new Utility.NonAbortedTimersPredicate();
    private static final List<Utility.Predicate<Quantity>> TIMER_PREDICATE_LIST;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    static {
        final List<Utility.Predicate<Quantity>> predicateList =  new ArrayList<>();
        predicateList.add(PREDICATE_STOPPED_TIMERS);
        predicateList.add(PREDICATE_NON_ABORTED_TIMERS);
        TIMER_PREDICATE_LIST = Collections.unmodifiableList(predicateList);
    }

    private boolean isOpen = true;
    private Instant finalTimestamp = null;

    private final List<Sink> sinks;
    private final Logger logger;
    private final Clock clock;
    private final UUID id;
    private final Instant initialTimestamp;

    // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
    private final Map<String, Counter> counters = new HashMap<>();
    private final Map<String, Timer> timers = new HashMap<>();
    private final Map<String, Deque<Quantity>> counterSamples = new HashMap<>();
    private final Map<String, Deque<Quantity>> timerSamples = new HashMap<>();
    private final Map<String, Deque<Quantity>> gaugeSamples = new HashMap<>();
    private final Map<String, AggregatedData> aggregatedData = new HashMap<>();
    private final Map<String, String> dimensions = new HashMap<>();
    // CHECKSTYLE.ON: IllegalInstantiation
    private final BiFunction<String, Counter, Counter> createCounterBiFunction = new CreateCounterFunction();

    /**
     * Protected constructor. Use an instance of TsdMetricsFactory to construct
     * {@link LockFreeMetrics} instances.
     *
     * @param uuid The unique identifier of this {@link Metrics} instance.
     * @param sinks The {@link Sink} instances to output data on {@link LockFreeMetrics#close()}.
     */
    protected LockFreeMetrics(
            final UUID uuid,
            final List<Sink> sinks) {
        this(uuid, sinks, Clock.systemUTC(), LOGGER);
    }

    // NOTE: Package private for testing.
    LockFreeMetrics(
            final UUID id,
            final List<Sink> sinks,
            final Clock clock,
            final Logger logger) {
        // NOTE: Immutability of sinks is guaranteed by TsdMetricsFactory
        this.sinks = sinks;
        this.logger = logger;
        this.clock = clock;
        initialTimestamp = this.clock.instant();
        this.id = id;
    }

    @Override
    public Counter createCounter(final String name) {
        return createCounterInternal(name);
    }

    @Override
    public void incrementCounter(final String name) {
        incrementCounter(name, 1L);
    }

    @Override
    public void decrementCounter(final String name) {
        incrementCounter(name, -1L);
    }

    @Override
    public void decrementCounter(final String name, final long value) {
        incrementCounter(name, -1L * value);
    }

    @Override
    public void incrementCounter(final String name, final long value) {
        if (!assertIsOpen()) {
            return;
        }

        final Counter counter = counters.compute(name, createCounterBiFunction);
        counter.increment(value);
    }

    @Override
    public void resetCounter(final String name) {
        if (!assertIsOpen()) {
            return;
        }
        counters.put(name, createCounterInternal(name));
    }

    @Override
    public Timer createTimer(final String name) {
        if (!assertIsOpen()) {
            // To prevent the calling code from throwing a NPE we just return a
            // timer object; note that the call to assertIsOpen has already
            // logged a warning about incorrect use of the class.
            return new NoOpTimer();
        }
        final Deque<Quantity> samples = getOrCreateDeque(timerSamples, name);
        final TsdTimer timer = TsdTimer.newInstance(name);
        samples.add(timer);
        return timer;
    }

    @Override
    public void startTimer(final String name) {
        if (!assertIsOpen()) {
            return;
        }
        // In this case the normal behavior is to insert; only if the library is
        // being incorrectly used will there be a running timer with the same
        // name.
        final TsdTimer timer = TsdTimer.newInstance(name);
        if (timers.putIfAbsent(name, timer) != null) {
            // This is in place of an exception; see class Javadoc
            // NOTE: If you are seeing this message and want multiple instances
            // of the same named timer then you should use {@code createTimer}
            logger.warn(String.format("Cannot start timer because timer already started; timerName=%s", name));
            return;
        }
        final Deque<Quantity> samples = getOrCreateDeque(timerSamples, name);
        samples.add(timer);
    }

    @Override
    public void stopTimer(final String name) {
        if (!assertIsOpen()) {
            return;
        }
        final Timer timer = timers.remove(name);
        if (timer == null) {
            // This is in place of an exception; see class Javadoc
            logger.warn(String.format("Cannot stop timer because timer was not started, or was already stopped; timerName=%s", name));
            return;
        }
        timer.stop();
    }

    @Override
    public void setTimer(final String name, final long duration, final TimeUnit unit) {
        if (!assertIsOpen()) {
            return;
        }
        final Deque<Quantity> samples = getOrCreateDeque(timerSamples, name);
        samples.add(TsdQuantity.newInstance(Utility.convertTimeUnit(duration, unit, TimeUnit.SECONDS)));
    }


    @Override
    public void setGauge(final String name, final double value) {
        if (!assertIsOpen()) {
            return;
        }
        final Deque<Quantity> list = getOrCreateDeque(gaugeSamples, name);
        list.add(TsdQuantity.newInstance(value));
    }

    @Override
    public void setGauge(final String name, final long value) {
        if (!assertIsOpen()) {
            return;
        }
        final Deque<Quantity> list = getOrCreateDeque(gaugeSamples, name);
        list.add(TsdQuantity.newInstance(value));
    }

    @Override
    public void addDimension(final String key, final String value) {
        if (!assertIsOpen()) {
            return;
        }
        dimensions.put(key, value);
    }

    @Override
    public void addDimensions(final Map<String, String> map) {
        if (!assertIsOpen()) {
            return;
        }
        for (final Map.Entry<String, String> entry : map.entrySet()) {
            dimensions.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public void close() {
        if (!assertIsOpen()) {
            return;
        }
        isOpen = false;

        finalTimestamp = clock.instant();

        final int maxMetricsCount = timerSamples.size() + counterSamples.size() + gaugeSamples.size();
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<String, List<Quantity>> mergedSamples = new HashMap<>((int) (maxMetricsCount / DEFAULT_LOAD_FACTOR), DEFAULT_LOAD_FACTOR);
        // CHECKSTYLE.ON: IllegalInstantiation
        mergeSamples(
                timerSamples,
                mergedSamples,
                TIMER_PREDICATE_LIST);
        mergeSamples(counterSamples, mergedSamples, Collections.emptyList());
        mergeSamples(gaugeSamples, mergedSamples, Collections.emptyList());

        final Map<String, List<Quantity>> unmodifiableMergedSamples = Collections.unmodifiableMap(
                mergedSamples.entrySet().stream().collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                e -> Collections.unmodifiableList(e.getValue()))));

        final Event event = new TsdEvent(
                id,
                initialTimestamp,
                finalTimestamp,
                Collections.unmodifiableMap(dimensions),
                unmodifiableMergedSamples,
                Collections.unmodifiableMap(aggregatedData));

        for (final Sink sink : sinks) {
            try {
                sink.record(event);
                // CHECKSTYLE.OFF: IllegalCatch - Prevent an exception leak; see class Javadoc
            } catch (final RuntimeException e) {
                // CHECKSTYLE.ON: IllegalCatch
                // This is in place of an exception; see class Javadoc
                logger.warn(String.format("Metrics sink failed to record; sink=%s", sink.getClass()), e);
            }
        }
    }

    @Override
    @Nullable
    public Instant getOpenTime() {
        return initialTimestamp;
    }

    @Override
    @Nullable
    public Instant getCloseTime() {
        return finalTimestamp;
    }

    @Override
    public String toString() {
        return String.format(
                "LockFreeMetrics{Sinks=%s, IsOpen=%b, Id=%s, InitialTimestamp=%s, FinalTimestamp=%s, Dimensions=%s}",
                sinks,
                isOpen,
                id,
                initialTimestamp,
                finalTimestamp,
                dimensions);

    }

    /**
     * Record aggregated data for a metric against this metrics instance. Only one aggregated
     * data instance can be recorded against each metric per {@link Metrics} instance (unit of work).
     * These should be produced by a client-side aggregator where each unit of work corresponds
     * to the aggregation period in which each metric for a set of dimensions has one aggregate
     * produced for it.
     *
     * <u><b>WARNING:</b></u> This method is not part of the {@link Metrics} interface
     * and therefore not officially supported by the client outside the scope of change
     * described by the semantic version of this package.
     *
     * @param name the name of the metric
     * @param newAggregatedData collection of aggregated data instances to record
     */
    public void recordAggregatedData(final String name, final AggregatedData newAggregatedData) {
        if (!assertIsOpen()) {
            return;
        }
        if (aggregatedData.putIfAbsent(name, newAggregatedData) != null) {
            logger.warn(
                    String.format(
                            "Multiple aggregated data instances recorded against the same metric: %s",
                            name));
        }
    }

    private Counter createCounterInternal(final String name) {
        if (!assertIsOpen()) {
            // To prevent the calling code from throwing a NPE we just return a
            // counter object; note that the call to assertIsOpen has already
            // logged a warning about incorrect use of the class.
            return new NoOpCounter();
        }

        // Create and register a new counter sample
        final Deque<Quantity> countersDeque = getOrCreateDeque(counterSamples, name);
        final TsdCounter newCounter = TsdCounter.newInstance(name, this::isOpen);
        countersDeque.add(newCounter);
        return newCounter;
    }

    private <T> Deque<T> getOrCreateDeque(
            final Map<String, Deque<T>> map,
            final String key) {
        return map.computeIfAbsent(key, k -> new LinkedList<>());
    }

    private boolean assertIsOpen() {
        if (!isOpen) {
            // This is in place of an exception; see class Javadoc
            logger.warn("LockFreeMetrics object was closed before an operation");
        }
        return isOpen;
    }

    private void mergeSamples(
            final Map<String, ? extends Collection<? extends Quantity>> samples,
            final Map<String, List<Quantity>> mergedSamples,
            final List<Utility.Predicate<Quantity>> predicates) {

        for (final Map.Entry<String, ? extends Collection<? extends Quantity>> entry : samples.entrySet()) {
            List<Quantity> quantities = mergedSamples.get(entry.getKey());
            if (quantities != null) {
                logger.warn(String.format(
                        "Metric recorded as two or more of counter, timer, gauge; name=%s",
                        entry.getKey()));
            } else {
                quantities = new ArrayList<>(entry.getValue().size());
                mergedSamples.put(entry.getKey(), quantities);
            }

            for (final Quantity quantity : entry.getValue()) {
                final List<Utility.Predicate<Quantity>> rejections = new ArrayList<>();
                for (final Utility.Predicate<Quantity> predicate : predicates) {
                    if (!predicate.apply(quantity)) {
                        rejections.add(predicate);
                    }
                }
                if (rejections.isEmpty()) {
                    quantities.add(quantity);
                } else {
                    logger.warn(String.format(
                            "Sample rejected; name=%s, sample=%s, rejections=%s",
                            entry.getKey(),
                            quantity,
                            rejections));
                }
            }
        }
    }

    private final class CreateCounterFunction implements BiFunction<String, Counter, Counter> {

        CreateCounterFunction() {}

        @Override
        public Counter apply(final String name, @Nullable final Counter existingValue) {
            if (existingValue == null) {
                final Counter newCounter = TsdCounter.newInstance(name, LockFreeMetrics.this::isOpen);
                final Deque<Quantity> countersDeque = getOrCreateDeque(counterSamples, name);
                countersDeque.add(newCounter);
                return newCounter;
            }
            return existingValue;
        }
    }
}

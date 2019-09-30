/*
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

import com.arpnetworking.metrics.AggregatedData;
import com.arpnetworking.metrics.Counter;
import com.arpnetworking.metrics.Event;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.Quantity;
import com.arpnetworking.metrics.Sink;
import com.arpnetworking.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * when {@link TsdMetrics#close} is invoked the library will not throw an exception.
 * However, it will attempt to write a warning using the SLF4J
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
 * This class is thread safe; however, it makes no effort to order
 * operations on the same data. For example, it is safe for two threads to
 * start and stop separate timers but if the threads start and stop the same
 * timer than it is up to the caller to ensure that start is called before
 * stop.
 *
 * The library does attempt to detect incorrect usage, for example modifying
 * metrics after closing and starting but never stopping a timer; however, in
 * a multithreaded environment it is not guaranteed that these warnings are
 * emitted. It is up to clients to ensure that multithreaded use of the same
 * {@link TsdMetrics} instance is correct.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class TsdMetrics implements Metrics {

    @Override
    public Counter createCounter(final String name) {
        return createCounterInternal(name);
    }

    @Override
    public void incrementCounter(final String name) {
        incrementCounter(name, 1L);
    }

    @Override
    public void incrementCounter(final String name, final long value) {
        if (!assertIsOpen()) {
            return;
        }

        final TsdCounter counter = _counters.compute(name, _createCounterBiFunction);
        counter.increment(value);
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
    public void resetCounter(final String name) {
        if (!assertIsOpen()) {
            return;
        }
        _counters.put(name, createCounterInternal(name));
    }

    @Override
    public Timer createTimer(final String name) {
        if (!assertIsOpen()) {
            // To prevent the calling code from throwing a NPE we just return a
            // timer object; note that the call to assertIsOpen has already
            // logged a warning about incorrect use of the class.
            return TsdTimer.newInstance(name, _isOpen);
        }
        final ConcurrentLinkedDeque<Quantity> samples = getOrCreateDeque(_timerSamples, name);
        final TsdTimer timer = TsdTimer.newInstance(name, _isOpen);
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
        final TsdTimer timer = TsdTimer.newInstance(name, _isOpen);
        if (_timers.putIfAbsent(name, timer) != null) {
            // This is in place of an exception; see class Javadoc
            _logger.warn(String.format("Cannot start timer because timer already started; timerName=%s", name));
            return;
        }
        final ConcurrentLinkedDeque<Quantity> samples = getOrCreateDeque(_timerSamples, name);
        samples.add(timer);
    }

    @Override
    public void stopTimer(final String name) {
        if (!assertIsOpen()) {
            return;
        }
        final Timer timer = _timers.remove(name);
        if (timer == null) {
            // This is in place of an exception; see class Javadoc
            _logger.warn(String.format("Cannot stop timer because timer was not started; timerName=%s", name));
            return;
        }
        timer.stop();
    }

    @Override
    public void setTimer(final String name, final long duration, final TimeUnit unit) {
        if (!assertIsOpen()) {
            return;
        }
        final ConcurrentLinkedDeque<Quantity> samples = getOrCreateDeque(_timerSamples, name);
        samples.add(TsdQuantity.newInstance(convertTimeUnit(duration, unit, TimeUnit.SECONDS)));
    }


    @Override
    public void setGauge(final String name, final double value) {
        if (!assertIsOpen()) {
            return;
        }
        final Deque<Quantity> list = getOrCreateDeque(_gaugeSamples, name);
        list.add(TsdQuantity.newInstance(value));
    }

    @Override
    public void setGauge(final String name, final long value) {
        if (!assertIsOpen()) {
            return;
        }
        final Deque<Quantity> list = getOrCreateDeque(_gaugeSamples, name);
        list.add(TsdQuantity.newInstance(value));
    }

    @Override
    public void addDimension(final String key, final String value) {
        if (!assertIsOpen()) {
            return;
        }
        _dimensions.put(key, value);
    }

    @Override
    public void addDimensions(final Map<String, String> map) {
        if (!assertIsOpen()) {
            return;
        }
        for (final Map.Entry<String, String> entry : map.entrySet()) {
            _dimensions.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean isOpen() {
        return _isOpen.get();
    }

    @Override
    public void close() {
        if (!assertIsOpen(_isOpen.getAndSet(false))) {
            return;
        }
        _finalTimestamp = _clock.instant();

        final int maxMetricsCount = _timerSamples.size() + _counterSamples.size() + _gaugeSamples.size();
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<String, List<Quantity>> clonedSamples = new HashMap<>((int) (maxMetricsCount / DEFAULT_LOAD_FACTOR), DEFAULT_LOAD_FACTOR);
        // CHECKSTYLE.ON: IllegalInstantiation
        cloneSamples(
                _timerSamples,
                clonedSamples,
                PREDICATE_STOPPED_TIMERS,
                PREDICATE_NON_ABORTED_TIMERS);
        cloneSamples(_counterSamples, clonedSamples);
        cloneSamples(_gaugeSamples, clonedSamples);

        final Event event = new TsdEvent(
                _initialTimestamp,
                _finalTimestamp,
                Collections.unmodifiableMap(_dimensions),
                clonedSamples.entrySet().stream().collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                e -> Collections.unmodifiableList(e.getValue()))),
                Collections.unmodifiableMap(_aggregatedData));

        for (final Sink sink : _sinks) {
            try {
                sink.record(event);
                // CHECKSTYLE.OFF: IllegalCatch - Prevent an exception leak; see class Javadoc
            } catch (final RuntimeException e) {
                // CHECKSTYLE.ON: IllegalCatch
                // This is in place of an exception; see class Javadoc
                _logger.warn(String.format("Metrics sink failed to record; sink=%s", sink.getClass()), e);
            }
        }
    }

    @Override
    @Nullable
    public Instant getOpenTime() {
        return _initialTimestamp;
    }

    @Override
    @Nullable
    public Instant getCloseTime() {
        return _finalTimestamp;
    }

    @Override
    public String toString() {
        return String.format(
                "TsdMetrics{Sinks=%s, IsOpen=%b, InitialTimestamp=%s, FinalTimestamp=%s, Dimensions=%s}",
                _sinks,
                _isOpen.get(),
                _initialTimestamp,
                _finalTimestamp,
                _dimensions);

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
     * @param aggregatedData collection of aggregated data instances to record
     */
    public void recordAggregatedData(final String name, final AggregatedData aggregatedData) {
        if (!assertIsOpen()) {
            return;
        }
        if (_aggregatedData.putIfAbsent(name, aggregatedData) != null) {
            _logger.warn(
                    String.format(
                            "Multiple aggregated data instances recorded against the same metric: %s",
                            name));
        }
    }

    // NOTE: Package private for testing
    /* package private */ <T> T getOrCreate(final ConcurrentMap<String, T> map, final String key, final T newValue) {
        final T currentValue = map.putIfAbsent(key, newValue);
        if (currentValue != null) {
            return currentValue;
        }
        return newValue;
    }

    private TsdCounter createCounterInternal(final String name) {
        if (!assertIsOpen()) {
            // To prevent the calling code from throwing a NPE we just return a
            // counter object; note that the call to assertIsOpen has already
            // logged a warning about incorrect use of the class.
            return TsdCounter.newInstance(name, _isOpen);
        }

        // Create and register a new counter sample
        final ConcurrentLinkedDeque<Quantity> counters = getOrCreateDeque(_counterSamples, name);
        final TsdCounter newCounter = TsdCounter.newInstance(name, _isOpen);
        counters.add(newCounter);
        return newCounter;
    }

    private <T> ConcurrentLinkedDeque<T> getOrCreateDeque(
            final ConcurrentMap<String, ConcurrentLinkedDeque<T>> map,
            final String key) {
        ConcurrentLinkedDeque<T> deque = map.get(key);
        if (deque == null) {
            final ConcurrentLinkedDeque<T> newDeque = new ConcurrentLinkedDeque<>();
            deque = getOrCreate(map, key, newDeque);
        }
        return deque;
    }

    private boolean assertIsOpen() {
        return assertIsOpen(_isOpen.get());
    }

    private boolean assertIsOpen(final boolean isOpen) {
        if (!isOpen) {
            // This is in place of an exception; see class Javadoc
            _logger.warn("Metrics object was closed during an operation; you may have a race condition");
        }
        return isOpen;
    }

    static double convertTimeUnit(final double valueFrom, final TimeUnit unitFrom, final TimeUnit unitTo) {
        final long conversionRateTo = unitTo.convert(1, unitFrom);
        if (conversionRateTo == 0) {
            return valueFrom / unitFrom.convert(1, unitTo);
        }
        return valueFrom * conversionRateTo;
    }

    @SafeVarargs
    // CHECKSTYLE.OFF: RedundantModifier - Final modifier required for @SafeVarargs
    private final void cloneSamples(
            final Map<String, ? extends Collection<? extends Quantity>> samples,
            final Map<String, List<Quantity>> clonedSamples,
            final Predicate<Quantity>... predicates) {
        // CHECKSTYLE.ON: RedundantModifier
        for (final Map.Entry<String, ? extends Collection<? extends Quantity>> entry : samples.entrySet()) {
            List<Quantity> quantities = clonedSamples.get(entry.getKey());
            if (quantities != null) {
                _logger.warn(String.format(
                        "Metric recorded as two or more of counter, timer, gauge; name=%s",
                        entry.getKey()));
            } else {
                quantities = new ArrayList<>(entry.getValue().size());
                clonedSamples.put(entry.getKey(), quantities);
            }

            for (final Quantity quantity : entry.getValue()) {
                final List<Predicate<Quantity>> rejections = new ArrayList<>();
                for (final Predicate<Quantity> predicate : predicates) {
                    if (!predicate.apply(quantity)) {
                        rejections.add(predicate);
                    }
                }
                if (rejections.isEmpty()) {
                    quantities.add(TsdQuantity.newInstance(quantity.getValue()));
                } else {
                    _logger.warn(String.format(
                            "Sample rejected; name=%s, sample=%s, rejections=%s",
                            entry.getKey(),
                            quantity,
                            rejections));
                }
            }
        }
    }

    // NOTE: Use an instance of TsdMetricsFactory to construct TsdMetrics instances.
    /* package private */ TsdMetrics(
            final UUID uuid, 
            final List<Sink> sinks) {
        this(uuid, sinks, Clock.systemUTC(), LOGGER);
    }

    // NOTE: Package private for testing.
    /* package private */ TsdMetrics(
            final UUID uuid,
            final List<Sink> sinks,
            final Clock clock,
            final Logger logger) {
        _sinks = sinks;
        _logger = logger;
        _clock = clock;
        _initialTimestamp = _clock.instant();
        _dimensions.put(ID_KEY, uuid.toString());
    }

    private final List<Sink> _sinks;
    private final Logger _logger;
    private final AtomicBoolean _isOpen = new AtomicBoolean(true);
    private final Clock _clock;
    private final Instant _initialTimestamp;
    private Instant _finalTimestamp = null;
    private final ConcurrentMap<String, TsdCounter> _counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TsdTimer> _timers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentLinkedDeque<Quantity>> _counterSamples = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentLinkedDeque<Quantity>> _timerSamples = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentLinkedDeque<Quantity>> _gaugeSamples = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AggregatedData> _aggregatedData = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> _dimensions = new ConcurrentHashMap<>();
    private final BiFunction<String, TsdCounter, TsdCounter> _createCounterBiFunction = new CreateCounterFunction();

    private static final Predicate<Quantity> PREDICATE_STOPPED_TIMERS = new StoppedTimersPredicate();
    private static final Predicate<Quantity> PREDICATE_NON_ABORTED_TIMERS = new NonAbortedTimersPredicate();
    private static final Logger LOGGER = LoggerFactory.getLogger(TsdMetrics.class);
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private static final String ID_KEY = "_id";

    private final class CreateCounterFunction implements BiFunction<String, TsdCounter, TsdCounter> {

        CreateCounterFunction() {}

        @Override
        public TsdCounter apply(final String name, @Nullable final TsdCounter existingValue) {
            if (existingValue == null) {
                final TsdCounter newCounter = TsdCounter.newInstance(name, _isOpen);
                final ConcurrentLinkedDeque<Quantity> counters = getOrCreateDeque(_counterSamples, name);
                counters.add(newCounter);
                return newCounter;
            }
            return existingValue;
        }
    }

    private interface Predicate<T> {

        boolean apply(T item);
    }

    private static final class StoppedTimersPredicate implements Predicate<Quantity> {

        @Override
        public boolean apply(final Quantity item) {
            if (item instanceof Timer) {
                final Timer timer = (Timer) item;
                return !timer.isRunning();
            }
            return true;
        }
    }

    private static final class NonAbortedTimersPredicate implements Predicate<Quantity> {

        @Override
        public boolean apply(final Quantity item) {
            if (item instanceof Timer) {
                final Timer timer = (Timer) item;
                return !timer.isAborted();
            }
            return true;
        }
    }
}

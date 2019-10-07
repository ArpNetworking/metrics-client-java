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
package com.arpnetworking.metrics.impl;

import com.arpnetworking.metrics.Quantity;
import com.arpnetworking.metrics.Timer;

import java.util.concurrent.TimeUnit;

/**
 * Shared utility code.
 *
 * @author Ville Koskela (ville at koskilabs dot com)
 */
/* package private */ final class Utility {

    /* package private */ static double convertTimeUnit(final double valueFrom, final TimeUnit unitFrom, final TimeUnit unitTo) {
        final long conversionRateTo = unitTo.convert(1, unitFrom);
        if (conversionRateTo == 0) {
            return valueFrom / unitFrom.convert(1, unitTo);
        }
        return valueFrom * conversionRateTo;
    }

    private Utility() {}

    /* package private */ interface Predicate<T> {
        boolean apply(T item);
    }

    /* package private */ static final class StoppedTimersPredicate implements Predicate<Quantity> {

        @Override
        public boolean apply(final Quantity item) {
            if (item instanceof Timer) {
                final Timer timer = (Timer) item;
                return !timer.isRunning();
            }
            return true;
        }
    }

    /* package private */ static final class NonAbortedTimersPredicate implements Predicate<Quantity> {

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

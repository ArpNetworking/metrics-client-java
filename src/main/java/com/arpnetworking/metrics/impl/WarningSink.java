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

import com.arpnetworking.metrics.Event;
import com.arpnetworking.metrics.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Implementation of <code>Sink</code> which emits a warning each time an
 * <code>Event</code> is to recorded.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
/* package private */ final class WarningSink implements Sink {

    /**
     * Public constructor.
     *
     * NOTE: This method does <b>not</b> perform a deep copy of the provided
     * data structures. Callers are expected to <b>not</b> modify these data
     * structures after passing them to this constructor. This is acceptable
     * since this class is for internal implementation only.
     *
     * @param reasons The reasons for the warning.
     */
    /* package private */ WarningSink(final List<String> reasons) {
        this(reasons, LOGGER);
    }

    /* package private */ WarningSink(final List<String> reasons, final Logger logger) {
        _reasons = reasons;
        _logger = logger;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void record(final Event event) {
        _logger.warn(String.format(
                "Unable to record event, metrics disabled; reasons=%s",
                _reasons));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format(
                "WarningSink{Reasons=%s}",
                _reasons);
    }

    private final List<String> _reasons;
    private final Logger _logger;

    private static final Logger LOGGER = LoggerFactory.getLogger(WarningSink.class);
}

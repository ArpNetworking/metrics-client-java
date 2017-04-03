/**
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
package com.arpnetworking.metrics;

/**
 * Interface for timer. Instances are started on creation and record state when
 * stopped or closed. All <code>Timer</code> implementations implement
 * <code>AutoCloseable</code> and can be used in try-with-resources statements.
 *
 * If you wish to discard the sample under measurement (e.g. in the event of an
 * application exception) simply invoke the <code>abort()</code> method.
 *
 * Each timer instance is bound to a <code>Metrics</code> instance. After the
 * <code>Metrics</code> instance is closed any timing data generated by
 * <code>Timer</code> instances bound to that <code>Metrics</code> instance will
 * be discarded.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public interface Timer extends AutoCloseable {

    /**
     * Abort the timer and do not record any timing data in the associated
     * <code>Metrics</code> instance.
     */
    void abort();

    /**
     * Stop the timer and record timing data in the associated
     * <code>Metrics</code> instance.
     */
    void stop();

    /**
     * Accessor to determine if this <code>Timer</code> instance is running or
     * stopped. Until stopped it does not produce a sample. Aborted timers are
     * considered stopped (or not running).
     *
     * @return True if and only if this <code>Timer</code> instance is running.
     */
    boolean isRunning();

    /**
     * Accessor to determine if this <code>Timer</code> instance has been
     * aborted. Aborted timers do not produce a sample at all. Once aborted the
     * timer is considered stopped (or not running).
     *
     * @return True if and only if this <code>Timer</code> instance is aborted.
     */
    boolean isAborted();

    @Override
    void close();
}

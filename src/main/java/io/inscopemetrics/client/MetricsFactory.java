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
package io.inscopemetrics.client;

import java.time.Duration;

/**
 * Interface for classes which create {@link ScopedMetrics} instances. Clients
 * should create a single instance of an implementing class for the entire
 * life of the application. Frameworks such as
 * <a href="http://projects.spring.io/spring-framework/">Spring/</a> and
 * <a href="https://code.google.com/p/google-guice/">Guice</a> may be used to inject the
 * {@link MetricsFactory} instance into various components within the
 * application.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public interface MetricsFactory {

    /**
     * Return a thread safe instance of {@link ScopedMetrics}.
     *
     * @return An instance of {@link ScopedMetrics}.
     */
    ScopedMetrics createScopedMetrics();

    /**
     * Return a lock-free instance of {@link ScopedMetrics}. This is not
     * appropriate in most use cases and is only intended for use in frameworks
     * which guarantee a single thread of execution in a particular context
     * such as Akka and Vert.x.
     *
     * @return A lock-free instance of {@link ScopedMetrics}.
     */
    ScopedMetrics createLockFreeScopedMetrics();

    /**
     * Return a thread safe and scheduled instance of {@link PeriodicMetrics}.
     *
     * To schedule your own instance use either:
     *
     * <ul>
     * <li>
     * {@link io.inscopemetrics.client.impl.ThreadSafePeriodicMetrics.Builder}
     * in cases where thread safety is required.
     * </li>
     * <li>
     * {@link io.inscopemetrics.client.impl.LockFreePeriodicMetrics.Builder}
     * in cases where the execution framework, such as Akka or Vert.x,
     * guarantees a single thread of execution.
     * </li>
     * </ul>
     *
     * @param interval The periodicity of the metrics collection.
     * @return A scheduled instance of {@link PeriodicMetrics}.
     */
    PeriodicMetrics schedulePeriodicMetrics(Duration interval);

    /**
     * Close the metrics factory. Users should first close any
     * {@link ScopedMetrics} instance and any {@link PeriodicMetrics}
     * instances not scheduled through this {@link MetricsFactory}.
     *
     * Closing the {@link MetricsFactory} stops periodic metrics
     * collection and closes all registered sinks.
     *
     * @throws InterruptedException if close is interrupted
     */
    void close() throws InterruptedException;
}

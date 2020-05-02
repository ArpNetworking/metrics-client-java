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

/**
 * Interface for classes which create {@link Metrics} instances. Clients
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
     * Return a thread safe instance of {@link Metrics}.
     *
     * @return An instance of {@link Metrics}.
     */
    Metrics create();

    /**
     * Return a lock-free instance of {@link Metrics}. This is not appropriate
     * in most use cases and is only intended for use in frameworks which
     * guarantee a single thread of execution in a particular context such as
     * Akka and Vert.x.
     *
     * @return A lock-free instance of {@link Metrics}.
     */
    Metrics createLockFree();
}

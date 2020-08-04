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

import java.util.concurrent.Executor;

/**
 * Execute tasks synchronously in the same thread that submits them. This
 * implementation avoids taking a dependency on Guava.
 *
 * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executor.html">Javadoc for Executor</a>
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public  class DirectExecutor implements Executor {

    private static DirectExecutor defaultInstance;

    private DirectExecutor() {}

    /**
     * Return the singleton instance of {@link DirectExecutor}.
     *
     * @return the singleton instance of {@link DirectExecutor}.
     */
    public static synchronized DirectExecutor getInstance() {
        if (defaultInstance == null) {
            defaultInstance = new DirectExecutor();
        }
        return defaultInstance;
    }

    @Override
    public void execute(final Runnable runnable) {
        runnable.run();
    }
}

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

import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link DirectExecutor}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class DirectExecutorTest {

    @Test
    public void testExecute() {
        final Executor executor = DirectExecutor.getInstance();
        final AtomicReference<Long> threadId = new AtomicReference<>(null);
        executor.execute(() -> threadId.set(Thread.currentThread().getId()));
        assertEquals(Thread.currentThread().getId(), threadId.get().longValue());
    }
}

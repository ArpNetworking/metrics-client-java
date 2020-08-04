/*
 * Copyright 2017 Inscope Metrics, Inc
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

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * A class to ease the acquisition and use of an atomic ref, protected by a ReadWriteLock.
 * The readLocked and writeLocked methods will perform the passed action after obtaining
 * a read lock and write lock, respectively.
 *
 * This class is thread safe.
 *
 * @param <T> The type of object protected by the locks.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public class ReadWriteLockedReference<T> {

    private final AtomicReference<T> reference;
    private final ReadWriteLock readWriteLock;

    /**
     * Public constructor.
     *
     * @param initialValue the initial value of the held reference
     */
    public ReadWriteLockedReference(final T initialValue) {
        reference = new AtomicReference<>(initialValue);
        readWriteLock = new ReentrantReadWriteLock(false);
    }

    /**
     * Perform an action while the read lock is acquired.
     *
     * @param method The action to perform
     */
    public void readLocked(final Consumer<T> method) {
        locking(method, readWriteLock.readLock());
    }

    /**
     * Perform an action while the write lock is acquired.
     *
     * @param method The action to perform
     */
    public void writeLocked(final Consumer<T> method) {
        locking(method, readWriteLock.writeLock());
    }

    /**
     * Replaces the held reference safely.
     *
     * @param newValue the new reference
     * @return the old reference
     */
    public T getAndSetReference(final T newValue) {
        readWriteLock.writeLock().lock();
        final T oldReference = reference.getAndSet(newValue);
        readWriteLock.writeLock().unlock();
        return oldReference;
    }

    private void locking(final Consumer<T> method, final Lock lock) {
        try {
            lock.lock();
            final T resolved = reference.get();
            method.accept(resolved);
        } finally {
            lock.unlock();
        }
    }
}

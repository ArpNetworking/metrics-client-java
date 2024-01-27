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

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Tests for the {@link ReadWriteLockedReference} class.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public class ReadWriteLockedReferenceTest {

    @Test
    public void testMultipleReadersOk() throws InterruptedException {
        final Semaphore waitForBoth = new Semaphore(0);
        final Semaphore releaseBoth = new Semaphore(0);
        final Object testObject = new Object();
        final ReadWriteLockedReference<Object> refLock = new ReadWriteLockedReference<>(testObject);

        final Runnable r = () -> refLock.readLocked(object -> {
            waitForBoth.release();
            try {
                releaseBoth.acquire();
            } catch (final InterruptedException ignored) {
            }
        });

        final Thread thread1 = new Thread(r);
        thread1.start();

        final Thread thread2 = new Thread(r);
        thread2.start();
        waitForBoth.acquire(2);
        releaseBoth.release(2);
    }

    @Test
    public void testMultipleWritersExcluded() throws InterruptedException {
        final Semaphore waitForEntry = new Semaphore(0);
        final Semaphore done = new Semaphore(0);

        final Object testObject = new Object();
        final ReadWriteLockedReference<Object> refLock = new ReadWriteLockedReference<>(testObject);

        final Runnable r = () -> {
            waitForEntry.release();
            refLock.writeLocked(object -> {
                done.release();
            });
        };

        refLock.writeLocked(object -> {
            final Thread thread1 = new Thread(r);
            thread1.start();

            try {
                waitForEntry.acquire();
                try {
                    final boolean acquired = done.tryAcquire(500, TimeUnit.MILLISECONDS);
                    if (acquired) {
                        Assert.fail("Write lock was not exclusive");
                    }

                } catch (final InterruptedException interrupted) {
                    Assert.fail("Interrupted");
                }
            } catch (final InterruptedException interrupted) {
                Assert.fail("Interrupted");
            }
        });

        done.acquire();
    }

    @Test
    public void testReleasesLockOnThrow() throws InterruptedException {
        final Object testObject = new Object();
        final ReadWriteLockedReference<Object> refLock = new ReadWriteLockedReference<>(testObject);

        final Semaphore waitForEntry = new Semaphore(0);

        final Runnable r = () -> {
            refLock.writeLocked(object -> {
                waitForEntry.release();
                throw new RuntimeException();
            });
        };

        final Thread thread1 = new Thread(r);
        thread1.start();

        waitForEntry.acquire();
        refLock.writeLocked(object -> { });
        // If we get here, success
    }

    @Test
    public void testGetAndSetAcquiresWriteLock() throws InterruptedException {
        final Semaphore waitForEntry = new Semaphore(0);

        final Object testObject = new Object();
        final Object newTestObject = new Object();
        final ReadWriteLockedReference<Object> refLock = new ReadWriteLockedReference<>(testObject);

        final Runnable r = () -> {
            refLock.readLocked(object -> {
                waitForEntry.release();
                try {
                    Thread.sleep(500);
                } catch (final InterruptedException interrupted) {
                    refLock.readLocked(o -> {
                        Assert.assertEquals(testObject, o);
                    });
                }
            });
        };

        final Thread thread1 = new Thread(r);
        thread1.start();

        try {
            waitForEntry.acquire();
            Thread.sleep(200);
            refLock.getAndSetReference(newTestObject);
        } catch (final InterruptedException interrupted) {
            Assert.fail("Interrupted");
        }

        refLock.readLocked(o -> Assert.assertEquals(newTestObject, o));
    }
}

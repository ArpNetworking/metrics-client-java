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
package io.inscopemetrics.client.impl;

import io.inscopemetrics.client.Quantity;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link TsdQuantity}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TsdQuantityTest {

    @Test
    public void testQuantity() {
        final Long expectedValue = 1L;
        final Quantity q = TsdQuantity.newInstance(expectedValue);
        assertEquals(expectedValue, q.getValue());
    }

    @Test
    public void testEquals() {
        final Quantity quantity = TsdQuantity.newInstance(1);
        assertTrue(quantity.equals(quantity));

        assertTrue(
                TsdQuantity.newInstance(1).equals(
                        TsdQuantity.newInstance(1)));

        assertFalse(quantity.equals(null));
        assertFalse(quantity.equals("This is a String"));

        final Quantity differentQuantity = TsdQuantity.newInstance(2);

        assertFalse(quantity.equals(differentQuantity));
    }

    @Test
    public void testHashCode() {
        assertEquals(
                TsdQuantity.newInstance(1).hashCode(),
                TsdQuantity.newInstance(1).hashCode());
    }

    @Test
    public void testToString() {
        final String asString = TsdQuantity.newInstance(1).toString();
        assertNotNull(asString);
        assertFalse(asString.isEmpty());
    }
}

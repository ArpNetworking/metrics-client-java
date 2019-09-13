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
package com.arpnetworking.metrics.impl;

import com.arpnetworking.metrics.Quantity;
import org.junit.Assert;
import org.junit.Test;

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
        Assert.assertEquals(expectedValue, q.getValue());
    }

    @Test
    public void testEquals() {
        final Quantity quantity = TsdQuantity.newInstance(1);
        Assert.assertTrue(quantity.equals(quantity));

        Assert.assertTrue(
                TsdQuantity.newInstance(1).equals(
                        TsdQuantity.newInstance(1)));

        Assert.assertFalse(quantity.equals(null));
        Assert.assertFalse(quantity.equals("This is a String"));

        final Quantity differentQuantity = TsdQuantity.newInstance(2);

        Assert.assertFalse(quantity.equals(differentQuantity));
    }

    @Test
    public void testHashCode() {
        Assert.assertEquals(
                TsdQuantity.newInstance(1).hashCode(),
                TsdQuantity.newInstance(1).hashCode());
    }

    @Test
    public void testToString() {
        final String asString = TsdQuantity.newInstance(1).toString();
        Assert.assertNotNull(asString);
        Assert.assertFalse(asString.isEmpty());
    }
}

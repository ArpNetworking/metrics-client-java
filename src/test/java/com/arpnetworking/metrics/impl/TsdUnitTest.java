/*
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

import com.arpnetworking.metrics.Unit;
import com.arpnetworking.metrics.Units;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for <code>TsdUnit</code>.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class TsdUnitTest {

    @Test
    public void testAccessors() {
        final TsdUnit unit = (TsdUnit) new TsdUnit.Builder()
                .setBaseUnit(BaseUnit.BIT)
                .setScale(BaseScale.KILO)
                .build();
        Assert.assertSame(BaseUnit.BIT, unit.getBaseUnit());
        Assert.assertSame(BaseScale.KILO, unit.getBaseScale());
    }

    @Test
    public void testBuild() {
        final Unit unit = new TsdUnit.Builder()
                .setBaseUnit(BaseUnit.BIT)
                .setScale(BaseScale.KILO)
                .build();
        Assert.assertEquals(BaseScale.KILO.getName() + BaseUnit.BIT.getName(), unit.getName());
    }

    @Test
    public void testBuildNull() {
        final Unit unit = new TsdUnit.Builder().build();
        Assert.assertNull(unit);
    }

    @Test
    public void testBuildBaseUnit() {
        final Unit unit = new TsdUnit.Builder()
                .setBaseUnit(BaseUnit.BIT)
                .build();
        Assert.assertEquals(
                BaseUnit.BIT,
                unit);
        Assert.assertEquals(Units.BIT.getName(), unit.getName());
    }

    @Test
    public void testEquality() {
        final Unit unit = new TsdUnit.Builder()
                .setBaseUnit(BaseUnit.BIT)
                .setScale(BaseScale.KILO)
                .build();
        Assert.assertTrue(unit.equals(unit));

        Assert.assertTrue(unit.equals(new TsdUnit.Builder()
                .setBaseUnit(BaseUnit.BIT)
                .setScale(BaseScale.KILO)
                .build()));

        Assert.assertFalse(unit.equals(null));
        Assert.assertFalse(unit.equals("This is a String"));

        Assert.assertFalse(unit.equals(new TsdUnit.Builder().build()));
        Assert.assertFalse(unit.equals(new TsdUnit.Builder()
                .setScale(BaseScale.KILO)
                .build()));
        Assert.assertFalse(unit.equals(new TsdUnit.Builder()
                .setBaseUnit(BaseUnit.BIT)
                .build()));
        Assert.assertFalse(unit.equals(new TsdUnit.Builder()
                .setBaseUnit(BaseUnit.BYTE)
                .setScale(BaseScale.KILO)
                .build()));
        Assert.assertFalse(unit.equals(new TsdUnit.Builder()
                .setBaseUnit(BaseUnit.BIT)
                .setScale(BaseScale.MEGA)
                .build()));
    }

    @Test
    public void testHashCode() {
        Assert.assertEquals(
                new TsdUnit.Builder()
                        .setBaseUnit(BaseUnit.BIT)
                        .setScale(BaseScale.KILO)
                        .build().hashCode(),
                new TsdUnit.Builder()
                        .setBaseUnit(BaseUnit.BIT)
                        .setScale(BaseScale.KILO)
                        .build().hashCode());
    }

    @Test
    public void testToString() {
        final AtomicBoolean isOpen = new AtomicBoolean(true);
        final String asString = new TsdUnit.Builder()
                .setBaseUnit(BaseUnit.BIT)
                .setScale(BaseScale.KILO)
                .build()
                .toString();
        Assert.assertNotNull(asString);
        Assert.assertFalse(asString.isEmpty());
        Assert.assertThat(asString, Matchers.containsString(BaseUnit.BIT.toString()));
        Assert.assertThat(asString, Matchers.containsString(BaseScale.KILO.toString()));
    }
}

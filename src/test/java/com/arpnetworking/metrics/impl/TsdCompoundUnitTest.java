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

import com.arpnetworking.metrics.CompoundUnit;
import com.arpnetworking.metrics.Unit;
import com.arpnetworking.metrics.Units;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link TsdCompoundUnit}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TsdCompoundUnitTest {

    @Test
    public void testBaseUnitCreation() {
        Unit unit;

        unit = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .build();
        Assert.assertNotNull(unit);
        Assert.assertTrue(unit instanceof BaseUnit);
        Assert.assertTrue(unit.equals(BaseUnit.BYTE));

        unit = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addNumeratorUnit(Units.SECOND)
                .addDenominatorUnit(Units.BYTE)
                .build();
        Assert.assertNotNull(unit);
        Assert.assertTrue(unit instanceof BaseUnit);
        Assert.assertTrue(unit.equals(BaseUnit.SECOND));

        unit = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addDenominatorUnit(new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.BYTE)
                        .addDenominatorUnit(Units.SECOND)
                        .build())
                .build();
        Assert.assertNotNull(unit);
        Assert.assertTrue(unit instanceof BaseUnit);
        Assert.assertTrue(unit.equals(BaseUnit.SECOND));

        unit = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.BYTE)
                        .addDenominatorUnit(Units.SECOND)
                        .build())
                .addDenominatorUnit(new TsdCompoundUnit.Builder()
                        .addDenominatorUnit(Units.SECOND)
                        .build())
                .build();
        Assert.assertNotNull(unit);
        Assert.assertTrue(unit instanceof BaseUnit);
        Assert.assertTrue(unit.equals(BaseUnit.BYTE));

        unit = new TsdCompoundUnit.Builder()
                .addDenominatorUnit(Units.SECOND)
                .build();
        Assert.assertNotNull(unit);
        Assert.assertFalse(unit instanceof BaseUnit);

        unit = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addDenominatorUnit(Units.BYTE)
                .addDenominatorUnit(Units.SECOND)
                .build();
        Assert.assertNotNull(unit);
        Assert.assertFalse(unit instanceof BaseUnit);

        unit = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.KILOBIT)
                .build();
        Assert.assertNotNull(unit);
        Assert.assertFalse(unit instanceof BaseUnit);
    }

    @Test
    public void testNullCreation() {
        Assert.assertNull(new TsdCompoundUnit.Builder().build());

        Assert.assertNull(
                new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.BYTE)
                        .addDenominatorUnit(Units.BYTE)
                        .build());

        Assert.assertNull(
                new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.KILOBYTE)
                        .addDenominatorUnit(Units.KILOBYTE)
                        .build());

        Assert.assertNull(
                new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(
                                new TsdCompoundUnit.Builder()
                                        .addNumeratorUnit(Units.BYTE)
                                        .addDenominatorUnit(Units.SECOND)
                                        .build())
                        .addDenominatorUnit(
                                new TsdCompoundUnit.Builder()
                                        .addNumeratorUnit(Units.BYTE)
                                        .addDenominatorUnit(Units.SECOND)
                                        .build())
                        .build());

        // This demonstrates how we do not *currently* reduce equivalent scales
        // to an instance of Unit.
        Assert.assertTrue(
                new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.KILOBYTE)
                        .addNumeratorUnit(Units.KILOBYTE)
                        .addDenominatorUnit(Units.MEGABYTE)
                        .addDenominatorUnit(Units.BYTE)
                        .build()
                instanceof CompoundUnit);

        // This demonstrates how we do not *currently* reduce equivalent units
        // to a scale-only instance of TsdUnit.
        Assert.assertTrue(
                new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.BYTE)
                        .addDenominatorUnit(Units.KILOBYTE)
                        .build()
                instanceof CompoundUnit);
    }

    @Test
    public void testCancelRedundantUnits() {
        CompoundUnit compoundUnit;
        BaseUnit baseUnit;

        baseUnit = (BaseUnit) new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addNumeratorUnit(Units.SECOND)
                .addDenominatorUnit(Units.BYTE)
                .build();
        Assert.assertNotNull(baseUnit);
        Assert.assertEquals(Units.SECOND, baseUnit);

        baseUnit = (BaseUnit) new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addNumeratorUnit(Units.SECOND)
                .addNumeratorUnit(Units.SECOND)
                .addDenominatorUnit(Units.BYTE)
                .addDenominatorUnit(Units.SECOND)
                .build();
        Assert.assertNotNull(baseUnit);
        Assert.assertEquals(Units.SECOND, baseUnit);

        compoundUnit = (CompoundUnit) new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addDenominatorUnit(Units.BYTE)
                .addDenominatorUnit(Units.SECOND)
                .build();
        Assert.assertNotNull(compoundUnit);
        Assert.assertTrue(compoundUnit.getNumeratorUnits().isEmpty());
        Assert.assertEquals(1, compoundUnit.getDenominatorUnits().size());
        Assert.assertEquals(Units.SECOND, compoundUnit.getDenominatorUnits().get(0));

        compoundUnit = (CompoundUnit) new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addNumeratorUnit(Units.SECOND)
                .addDenominatorUnit(Units.BYTE)
                .addDenominatorUnit(Units.SECOND)
                .addDenominatorUnit(Units.SECOND)
                .build();
        Assert.assertNotNull(compoundUnit);
        Assert.assertTrue(compoundUnit.getNumeratorUnits().isEmpty());
        Assert.assertEquals(1, compoundUnit.getDenominatorUnits().size());
        Assert.assertEquals(Units.SECOND, compoundUnit.getDenominatorUnits().get(0));
    }

    @Test
    public void testFlattenNestedCompoundUnits() {
        CompoundUnit unit;
        List<Unit> units;

        unit = (CompoundUnit) new TsdCompoundUnit.Builder()
                .addNumeratorUnit(new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.BYTE)
                        .addDenominatorUnit(Units.SECOND)
                        .build())
                .build();
        Assert.assertNotNull(unit);
        Assert.assertEquals(1, unit.getNumeratorUnits().size());
        Assert.assertEquals(Units.BYTE, unit.getNumeratorUnits().get(0));
        Assert.assertEquals(1, unit.getDenominatorUnits().size());
        Assert.assertEquals(Units.SECOND, unit.getDenominatorUnits().get(0));

        unit = (CompoundUnit) new TsdCompoundUnit.Builder()
                .addDenominatorUnit(new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.BYTE)
                        .addDenominatorUnit(Units.SECOND)
                        .build())
                .build();
        Assert.assertNotNull(unit);
        Assert.assertEquals(1, unit.getNumeratorUnits().size());
        Assert.assertEquals(Units.SECOND, unit.getNumeratorUnits().get(0));
        Assert.assertEquals(1, unit.getDenominatorUnits().size());
        Assert.assertEquals(Units.BYTE, unit.getDenominatorUnits().get(0));

        unit = (CompoundUnit) new TsdCompoundUnit.Builder()
                .addNumeratorUnit(new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.SECOND)
                        .addDenominatorUnit(Units.BYTE)
                        .build())
                .addDenominatorUnit(new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.BYTE)
                        .addDenominatorUnit(Units.SECOND)
                        .build())
                .build();
        Assert.assertNotNull(unit);
        Assert.assertEquals(2, unit.getNumeratorUnits().size());
        Assert.assertEquals(Units.SECOND, unit.getNumeratorUnits().get(0));
        Assert.assertEquals(Units.SECOND, unit.getNumeratorUnits().get(1));
        Assert.assertEquals(2, unit.getDenominatorUnits().size());
        Assert.assertEquals(Units.BYTE, unit.getDenominatorUnits().get(0));
        Assert.assertEquals(Units.BYTE, unit.getDenominatorUnits().get(1));

        unit = (CompoundUnit) new TsdCompoundUnit.Builder()
                .addNumeratorUnit(new MyCompoundUnitA())
                .addDenominatorUnit(new MyCompoundUnitB())
                .build();
        Assert.assertNotNull(unit);
        Assert.assertEquals(4, unit.getNumeratorUnits().size());
        units = new ArrayList<>(unit.getNumeratorUnits());
        Assert.assertTrue(units.remove(Units.MILLISECOND));
        Assert.assertTrue(units.remove(Units.SECOND));
        Assert.assertTrue(units.remove(Units.MINUTE));
        Assert.assertTrue(units.remove(Units.HOUR));
        Assert.assertEquals(4, unit.getDenominatorUnits().size());
        units = new ArrayList<>(unit.getDenominatorUnits());
        Assert.assertTrue(units.remove(Units.BIT));
        Assert.assertTrue(units.remove(Units.BYTE));
        Assert.assertTrue(units.remove(Units.KILOBIT));
        Assert.assertTrue(units.remove(Units.KILOBYTE));

        unit = (CompoundUnit) new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.CELSIUS)
                .addDenominatorUnit(new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.BYTE)
                        .addDenominatorUnit(new TsdCompoundUnit.Builder()
                                .addNumeratorUnit(Units.HERTZ)
                                .addNumeratorUnit(Units.CELSIUS)
                                .addDenominatorUnit(Units.SECOND)
                                .build())
                        .build())
                .build();
        Assert.assertNotNull(unit);
        Assert.assertEquals(2, unit.getNumeratorUnits().size());
        Assert.assertEquals(Units.CELSIUS, unit.getNumeratorUnits().get(0));
        Assert.assertEquals(Units.CELSIUS, unit.getNumeratorUnits().get(1));
        Assert.assertEquals(3, unit.getDenominatorUnits().size());
        units = new ArrayList<>(unit.getDenominatorUnits());
        Assert.assertTrue(units.remove(Units.BYTE));
        Assert.assertTrue(units.remove(Units.SECOND));
        Assert.assertTrue(units.remove(Units.SECOND));
    }

    @Test
    public void testFlattenRemoveUnit() {
        Unit unit;

        unit = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.BYTE)
                        .build())
                .build();
        Assert.assertNotNull(unit);
        Assert.assertEquals(Units.BYTE, unit);

        unit = new TsdCompoundUnit.Builder()
                .addDenominatorUnit(new TsdCompoundUnit.Builder()
                        .addDenominatorUnit(Units.SECOND)
                        .build())
                .build();
        Assert.assertNotNull(unit);
        Assert.assertEquals(Units.SECOND, unit);

        final CompoundUnit compoundUnit = (CompoundUnit) new TsdCompoundUnit.Builder()
                .addNumeratorUnit(new TsdCompoundUnit.Builder()
                        .addDenominatorUnit(Units.BYTE)
                        .build())
                .addDenominatorUnit(new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.BYTE)
                        .build())
                .build();
        Assert.assertNotNull(compoundUnit);
        Assert.assertTrue(compoundUnit.getNumeratorUnits().isEmpty());
        Assert.assertEquals(2, compoundUnit.getDenominatorUnits().size());
        Assert.assertEquals(Units.BYTE, compoundUnit.getDenominatorUnits().get(0));
        Assert.assertEquals(Units.BYTE, compoundUnit.getDenominatorUnits().get(1));
    }

    @Test
    public void testEquals() {
        final Unit unit = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addDenominatorUnit(Units.SECOND)
                .build();
        Assert.assertTrue(unit.equals(unit));

        Assert.assertFalse(new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .build()
                .equals(null));

        Assert.assertFalse(new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .build()
                .equals("Not a Unit"));

        Assert.assertTrue(new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .build()
                .equals(Units.BYTE));

        Assert.assertTrue(new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addNumeratorUnit(Units.SECOND)
                .build()
                .equals(new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.SECOND)
                        .addNumeratorUnit(Units.BYTE)
                        .build()));

        Assert.assertTrue(new TsdCompoundUnit.Builder()
                .addDenominatorUnit(Units.BYTE)
                .addDenominatorUnit(Units.SECOND)
                .build()
                .equals(new TsdCompoundUnit.Builder()
                        .addDenominatorUnit(Units.SECOND)
                        .addDenominatorUnit(Units.BYTE)
                        .build()));

        Assert.assertFalse(new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addNumeratorUnit(Units.BYTE)
                .build()
                .equals(new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.BYTE)
                        .build()));

        Assert.assertFalse(new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .build()
                .equals(new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.SECOND)
                        .build()));

        Assert.assertFalse(new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addDenominatorUnit(Units.SECOND)
                .build()
                .equals(new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.BYTE)
                        .addDenominatorUnit(Units.MINUTE)
                        .build()));

        Assert.assertFalse(new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addDenominatorUnit(Units.SECOND)
                .build()
                .equals(new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.KILOBYTE)
                        .addDenominatorUnit(Units.SECOND)
                        .build()));

        Assert.assertFalse(new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .build()
                .equals(new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.BYTE)
                        .addNumeratorUnit(Units.BYTE)
                        .build()));
    }

    @Test
    public void testHashCode() {
        Assert.assertEquals(
                new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.BYTE)
                        .addNumeratorUnit(Units.SECOND)
                        .build()
                        .hashCode(),
                new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.SECOND)
                        .addNumeratorUnit(Units.BYTE)
                        .build()
                        .hashCode());

        Assert.assertEquals(
                new TsdCompoundUnit.Builder()
                        .addDenominatorUnit(Units.BYTE)
                        .addDenominatorUnit(Units.SECOND)
                        .build()
                        .hashCode(),
                new TsdCompoundUnit.Builder()
                        .addDenominatorUnit(Units.SECOND)
                        .addDenominatorUnit(Units.BYTE)
                        .build()
                        .hashCode());
    }

    @Test
    public void testToString() {
        final String asString =
                new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(Units.BYTE)
                        .addNumeratorUnit(Units.SECOND)
                        .build()
                        .toString();
        Assert.assertNotNull(asString);
        Assert.assertFalse(asString.isEmpty());
    }

    @Test
    public void testGetName() {
        String name;

        name = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .build()
                .getName();
        Assert.assertNotNull(name);
        Assert.assertEquals("byte", name);

        name = new TsdCompoundUnit.Builder()
                .addDenominatorUnit(Units.SECOND)
                .build()
                .getName();
        Assert.assertNotNull(name);
        Assert.assertEquals("1/second", name);

        name = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addDenominatorUnit(Units.SECOND)
                .build()
                .getName();
        Assert.assertNotNull(name);
        Assert.assertEquals("byte/second", name);

        name = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addNumeratorUnit(Units.BYTE)
                .addDenominatorUnit(Units.SECOND)
                .build()
                .getName();
        Assert.assertNotNull(name);
        Assert.assertEquals("(byte*byte)/second", name);

        name = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addDenominatorUnit(Units.SECOND)
                .addDenominatorUnit(Units.SECOND)
                .build()
                .getName();
        Assert.assertNotNull(name);
        Assert.assertEquals("byte/(second*second)", name);

        name = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addNumeratorUnit(Units.SECOND)
                .build()
                .getName();
        Assert.assertNotNull(name);
        Assert.assertEquals("byte*second", name);

        name = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(new MyCompoundUnitA())
                .addDenominatorUnit(Units.SECOND)
                .build()
                .getName();
        Assert.assertNotNull(name);
        Assert.assertEquals("millisecond/(byte*kilobyte)", name);

        name = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addDenominatorUnit(new MyCompoundUnitA())
                .build()
                .getName();
        Assert.assertNotNull(name);
        Assert.assertEquals("(byte*byte*kilobyte)/(millisecond*second)", name);

        name = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(new MyCompoundUnitA())
                .addNumeratorUnit(Units.BYTE)
                .addDenominatorUnit(Units.SECOND)
                .build()
                .getName();
        Assert.assertNotNull(name);
        Assert.assertEquals("millisecond/kilobyte", name);

        final Unit unit = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.BYTE)
                .addDenominatorUnit(new MyCompoundUnitA())
                .addDenominatorUnit(Units.SECOND)
                .build();
        name = unit.getName();
        Assert.assertNotNull(name);
        Assert.assertEquals("(byte*byte*kilobyte)/(millisecond*second*second)", name);
        Assert.assertEquals(name, unit.getName());
    }

    @Test
    public void testSetUnits() {
        CompoundUnit compoundUnit;
        Unit unit;
        List<Unit> units;

        unit = new TsdCompoundUnit.Builder()
                .setNumeratorUnits(Collections.singletonList(Units.BYTE))
                .build();
        Assert.assertNotNull(unit);
        Assert.assertTrue(unit instanceof BaseUnit);
        Assert.assertTrue(unit.equals(BaseUnit.BYTE));

        unit = new TsdCompoundUnit.Builder()
                .addNumeratorUnit(Units.SECOND)
                .setNumeratorUnits(Collections.singletonList(Units.BYTE))
                .build();
        Assert.assertNotNull(unit);
        Assert.assertTrue(unit instanceof BaseUnit);
        Assert.assertTrue(unit.equals(BaseUnit.BYTE));

        compoundUnit = (CompoundUnit) new TsdCompoundUnit.Builder()
                .setNumeratorUnits(Collections.singletonList(Units.BYTE))
                .addNumeratorUnit(Units.SECOND)
                .build();
        Assert.assertNotNull(compoundUnit);
        Assert.assertEquals(2, compoundUnit.getNumeratorUnits().size());
        Assert.assertTrue(compoundUnit.getDenominatorUnits().isEmpty());
        units = new ArrayList<>(compoundUnit.getNumeratorUnits());
        Assert.assertTrue(units.remove(Units.BYTE));
        Assert.assertTrue(units.remove(Units.SECOND));

        compoundUnit = (CompoundUnit) new TsdCompoundUnit.Builder()
                .setDenominatorUnits(Collections.singletonList(Units.BYTE))
                .build();
        Assert.assertNotNull(compoundUnit);
        Assert.assertTrue(compoundUnit.getNumeratorUnits().isEmpty());
        Assert.assertEquals(1, compoundUnit.getDenominatorUnits().size());
        Assert.assertEquals(Units.BYTE, compoundUnit.getDenominatorUnits().get(0));

        compoundUnit = (CompoundUnit) new TsdCompoundUnit.Builder()
                .addDenominatorUnit(Units.SECOND)
                .setDenominatorUnits(Collections.singletonList(Units.BYTE))
                .build();
        Assert.assertNotNull(compoundUnit);
        Assert.assertTrue(compoundUnit.getNumeratorUnits().isEmpty());
        Assert.assertEquals(1, compoundUnit.getDenominatorUnits().size());
        Assert.assertEquals(Units.BYTE, compoundUnit.getDenominatorUnits().get(0));

        compoundUnit = (CompoundUnit) new TsdCompoundUnit.Builder()
                .setDenominatorUnits(Collections.singletonList(Units.BYTE))
                .addDenominatorUnit(Units.SECOND)
                .build();
        Assert.assertNotNull(compoundUnit);
        Assert.assertTrue(compoundUnit.getNumeratorUnits().isEmpty());
        Assert.assertEquals(2, compoundUnit.getDenominatorUnits().size());
        units = new ArrayList<>(compoundUnit.getDenominatorUnits());
        Assert.assertTrue(units.remove(Units.BYTE));
        Assert.assertTrue(units.remove(Units.SECOND));
    }

    private static final class MyCompoundUnitA implements CompoundUnit {
        @Override
        public List<Unit> getNumeratorUnits() {
            return Collections.singletonList(
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.SECOND)
                            .addDenominatorUnit(Units.BYTE)
                            .build());
        }

        @Override
        public List<Unit> getDenominatorUnits() {
            return Collections.singletonList(
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.KILOBYTE)
                            .addDenominatorUnit(Units.MILLISECOND)
                            .build());
        }

        @Override
        public String getName() {
            return "(second/byte)/(kilobyte/millisecond)";
        }
    }

    private static final class MyCompoundUnitB implements CompoundUnit {
        @Override
        public List<Unit> getNumeratorUnits() {
            return Collections.singletonList(
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.BIT)
                            .addDenominatorUnit(Units.MINUTE)
                            .build());
        }

        @Override
        public List<Unit> getDenominatorUnits() {
            return Collections.singletonList(
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.HOUR)
                            .addDenominatorUnit(Units.KILOBIT)
                            .build());
        }

        @Override
        public String getName() {
            return "(bit/minute)/(hour/kilobit)";
        }
    }
}

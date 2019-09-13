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
package com.arpnetworking.metrics;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Tests for <code>Units</code>.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class UnitsTest {

    @Test
    public void testConstructorIsPrivate() throws Exception {
        final Constructor<Units> constructor = Units.class.getDeclaredConstructor();
        Assert.assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        constructor.newInstance();
    }

    @Test
    public void testFromConversions() {
        Assert.assertEquals(Units.DAY, Units.from(TimeUnit.DAYS));
        Assert.assertEquals(Units.HOUR, Units.from(TimeUnit.HOURS));
        Assert.assertEquals(Units.MINUTE, Units.from(TimeUnit.MINUTES));
        Assert.assertEquals(Units.SECOND, Units.from(TimeUnit.SECONDS));
        Assert.assertEquals(Units.MILLISECOND, Units.from(TimeUnit.MILLISECONDS));
        Assert.assertEquals(Units.MICROSECOND, Units.from(TimeUnit.MICROSECONDS));
        Assert.assertEquals(Units.NANOSECOND, Units.from(TimeUnit.NANOSECONDS));
        Assert.assertNull(Units.from((TimeUnit) null));

        Assert.assertEquals(Optional.of(Units.DAY), Units.from(Optional.of(TimeUnit.DAYS)));
        Assert.assertEquals(Optional.of(Units.HOUR), Units.from(Optional.of(TimeUnit.HOURS)));
        Assert.assertEquals(Optional.of(Units.MINUTE), Units.from(Optional.of(TimeUnit.MINUTES)));
        Assert.assertEquals(Optional.of(Units.SECOND), Units.from(Optional.of(TimeUnit.SECONDS)));
        Assert.assertEquals(Optional.of(Units.MILLISECOND), Units.from(Optional.of(TimeUnit.MILLISECONDS)));
        Assert.assertEquals(Optional.of(Units.MICROSECOND), Units.from(Optional.of(TimeUnit.MICROSECONDS)));
        Assert.assertEquals(Optional.of(Units.NANOSECOND), Units.from(Optional.of(TimeUnit.NANOSECONDS)));
        Assert.assertEquals(Optional.empty(), Units.from(Optional.empty()));
    }
}

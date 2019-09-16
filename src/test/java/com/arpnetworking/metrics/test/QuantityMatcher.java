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
package com.arpnetworking.metrics.test;

import com.arpnetworking.metrics.Quantity;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeDiagnosingMatcher;

/**
 * Implementation of {@link Matcher} which matches a {@link Quantity}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class QuantityMatcher extends TypeSafeDiagnosingMatcher<Quantity> {

    /**
     * Create a new matcher for the expected {@link Quantity}.
     *
     * @param expected The expected {@link Quantity}.
     * @return new matcher for the expected metrics.
     */
    public static Matcher<Quantity> match(final Quantity expected) {
        return match(expected.getValue());
    }

    /**
     * Create a new matcher for the expected {@link Quantity}.
     *
     * @param expectedValue The expected value.
     * @return new matcher for the expected metrics.
     */
    public static Matcher<Quantity> match(final Number expectedValue) {
        if (expectedValue instanceof Double) {
            return new QuantityMatcher(
                    Matchers.closeTo(expectedValue.doubleValue(), 0.001));
        }
        return new QuantityMatcher(
                Matchers.equalTo(expectedValue));
    }

    /**
     * Create a new matcher for the expected {@link Quantity}.
     *
     * @param expectedValue The expected value.
     * @return new matcher for the expected metrics.
     */
    public static Matcher<Quantity> match(final long expectedValue) {
        return match(Long.valueOf(expectedValue));
    }

    /**
     * Create a new matcher for the expected {@link Quantity}.
     *
     * @param expectedValue The expected value.
     * @return new matcher for the expected metrics.
     */
    public static Matcher<Quantity> match(final double expectedValue) {
        return match(Double.valueOf(expectedValue));
    }

    /**
     * Create a new matcher for a {@link Quantity} with a matcher for the
     * value.
     *
     * @param valueMatcher The expected value matcher.
     * @return new matcher for the expected metrics.
     */
    public static Matcher<Quantity> match(final Matcher<? extends Number> valueMatcher) {
        return new QuantityMatcher(valueMatcher);
    }

    @Override
    public void describeTo(final Description description) {
        description.appendText(" was ")
                .appendValue(_valueMatcher);
    }

    @Override
    protected boolean matchesSafely(
            final Quantity item,
            final Description mismatchDescription) {
        boolean matches = true;
        if (!_valueMatcher.matches(item.getValue())) {
            mismatchDescription.appendText(String.format(
                    "value differs: expected=%s, actual=%s",
                    _valueMatcher,
                    item.getValue()));
            matches = false;
        }
        return matches;
    }

    private QuantityMatcher(final Matcher<? extends Number> valueMatcher) {
        _valueMatcher = valueMatcher;
    }

    private final Matcher<? extends Number> _valueMatcher;
}

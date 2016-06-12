/**
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

import java.util.List;

/**
 * This class is an example of a user defined compound unit which does not
 * simplify its numerator and denominator. It is used in testing to verify
 * that the sinks simplify even complex compound units.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class ComplexCompoundUnit implements CompoundUnit {

    /**
     * Public constructor.
     *
     * @param name The name of the complex compound unit.
     * @param numeratorUnits The <code>List</code> of numerator <code>Unit</code> instances.
     * @param denominatorUnits The <code>List</code> of denominator <code>Unit</code> instances.
     */
    public ComplexCompoundUnit(
            final String name,
            final List<Unit> numeratorUnits,
            final List<Unit> denominatorUnits) {
        _numeratorUnits = numeratorUnits;
        _denominatorUnits = denominatorUnits;
        _name = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Unit> getNumeratorUnits() {
        return _numeratorUnits;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Unit> getDenominatorUnits() {
        return _denominatorUnits;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return _name;
    }

    private final List<Unit> _numeratorUnits;
    private final List<Unit> _denominatorUnits;
    private final String _name;
}

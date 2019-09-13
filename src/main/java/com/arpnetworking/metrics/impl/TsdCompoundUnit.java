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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

/**
 * Default implementation of {@link CompoundUnit} a subtype of
 * {@link Unit} which is expressed with a {@link List} of
 * numerator and denominator {@link Unit} instances. It is
 * permissible to build a {@link TsdCompoundUnit} from other
 * {@link CompoundUnit} instances; however, this representation
 * will flatten on build. Consequently, any redundant units are removed
 * which could result in an empty compound unit (e.g. Unit.NO_UNIT).
 * Finally, two compound units are considered equal if their components
 * are equal regardless of order (e.g. foot,pounds equals pound,feet).
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class TsdCompoundUnit implements CompoundUnit {

    @Override
    public String getName() {
        return _name.updateAndGet(existingName -> {
            if (existingName != null) {
                return existingName;
            }
            final StringBuilder stringBuilder = new StringBuilder();
            final boolean numeratorParenthesis = _numeratorUnits.size() > 1 && !_denominatorUnits.isEmpty();
            final boolean denominatorParenthesis = _denominatorUnits.size() > 1;
            if (!_numeratorUnits.isEmpty()) {
                if (numeratorParenthesis) {
                    stringBuilder.append("(");
                }
                for (final Unit unit : _numeratorUnits) {
                    stringBuilder.append(unit.getName());
                    stringBuilder.append("*");
                }
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                if (numeratorParenthesis) {
                    stringBuilder.append(")");
                }
            } else {
                stringBuilder.append("1");
            }
            if (!_denominatorUnits.isEmpty()) {
                stringBuilder.append("/");
                if (denominatorParenthesis) {
                    stringBuilder.append("(");
                }
                for (final Unit unit : _denominatorUnits) {
                    stringBuilder.append(unit.getName());
                    stringBuilder.append("*");
                }
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                if (denominatorParenthesis) {
                    stringBuilder.append(")");
                }
            }
            return stringBuilder.toString();
        });
    }

    @Override
    public List<Unit> getNumeratorUnits() {
        return Collections.unmodifiableList(_numeratorUnits);
    }

    @Override
    public List<Unit> getDenominatorUnits() {
        return Collections.unmodifiableList(_denominatorUnits);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof TsdCompoundUnit)) {
            return false;
        }

        final TsdCompoundUnit otherCompoundUnit = (TsdCompoundUnit) other;
        return Objects.equals(_numeratorUnits, otherCompoundUnit._numeratorUnits)
                && Objects.equals(_denominatorUnits, otherCompoundUnit._denominatorUnits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_numeratorUnits, _denominatorUnits);
    }

    @Override
    public String toString() {
        return String.format(
                "TsdCompoundUnit{NumeratorUnits=%s, DenominatorUnits=%s}",
                getNumeratorUnits(),
                getDenominatorUnits());
    }

    private TsdCompoundUnit(final Builder builder) {
        _numeratorUnits = new ArrayList<>(builder._numeratorUnits);
        _denominatorUnits = new ArrayList<>(builder._denominatorUnits);
    }

    private final List<Unit> _numeratorUnits;
    private final List<Unit> _denominatorUnits;
    private final AtomicReference<String> _name = new AtomicReference<>();

    private static final Comparator<Unit> UNIT_COMPARATOR = new UnitNameComparator();

    /**
     * Builder for {@link TsdCompoundUnit}.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
     */
    public static class Builder implements com.arpnetworking.commons.builder.Builder<Unit> {

        /**
         * Create an instance of {@link Unit}. The instance may be
         * {@code null} if the {@link Unit} reduces to a constant
         * (e.g. Byte / Byte).
         *
         * @return Instance of {@link Unit}.
         */
        @Override
        public @Nullable Unit build() {
            // Defaults
            if (_numeratorUnits == null) {
                _numeratorUnits = new ArrayList<>();
            }
            if (_denominatorUnits == null) {
                _denominatorUnits = new ArrayList<>();
            }

            // = Simplify =
            // The use of TreeMap is to ensure CompoundUnit instances are
            // created in a consistent manner (e.g. bytes * seconds vs.
            // seconds * bytes). Alternatively, we could use a MultiMap as
            // the representation and ignore this issue; however, no
            // implementation of MultiMap exists in core Java libraries.
            final Map<Unit, Integer> numeratorUnitCount = new TreeMap<>(UNIT_COMPARATOR);
            final Map<Unit, Integer> denominatorUnitCount = new TreeMap<>(UNIT_COMPARATOR);

            // 1. Flatten any nested compound units
            flattenUnits(_numeratorUnits, numeratorUnitCount, denominatorUnitCount);
            flattenUnits(_denominatorUnits, denominatorUnitCount, numeratorUnitCount);

            // 2. Remove any redundant units (e.g. in both the numerator and
            // denominator)
            reduceCommonUnits(numeratorUnitCount, denominatorUnitCount);

            // 3. Flatten the unit counts
            _numeratorUnits.clear();
            for (final Map.Entry<Unit, Integer> unitCount : numeratorUnitCount.entrySet()) {
                for (int i = 0; i < unitCount.getValue().intValue(); ++i) {
                    _numeratorUnits.add(unitCount.getKey());
                }
            }
            _denominatorUnits.clear();
            for (final Map.Entry<Unit, Integer> unitCount : denominatorUnitCount.entrySet()) {
                for (int i = 0; i < unitCount.getValue().intValue(); ++i) {
                    _denominatorUnits.add(unitCount.getKey());
                }
            }

            // Return a no unit if possible
            if (_denominatorUnits.isEmpty() && _numeratorUnits.isEmpty()) {
                return null;
            }

            // Return a base unit if possible
            if (_denominatorUnits.isEmpty() && _numeratorUnits.size() == 1) {
                return _numeratorUnits.get(0);
            }

            // Return the simplified compound unit
            return new TsdCompoundUnit(this);
        }

        /**
         * Set the numerator units.
         *
         * @param value The numerator units.
         * @return This {@link Builder} instance.
         */
        public Builder setNumeratorUnits(final List<Unit> value) {
            _numeratorUnits = new ArrayList<>(value);
            return this;
        }

        /**
         * Add a numerator unit. Helper for merging units.
         *
         * @param values The numerator unit(s) to add.
         * @return This {@link Builder} instance.
         */
        public Builder addNumeratorUnit(final Unit... values) {
            if (_numeratorUnits == null) {
                _numeratorUnits = new ArrayList<>();
            }
            for (final Unit value : values) {
                _numeratorUnits.add(value);
            }
            return this;
        }

        /**
         * Set the denominator units.
         *
         * @param value The denominator units.
         * @return This {@link Builder} instance.
         */
        public Builder setDenominatorUnits(final List<Unit> value) {
            _denominatorUnits = new ArrayList<>(value);
            return this;
        }

        /**
         * Add a denominator unit. Helper for merging units.
         *
         * @param values The denominator unit(s) to add.
         * @return This {@link Builder} instance.
         */
        public Builder addDenominatorUnit(final Unit... values) {
            if (_denominatorUnits == null) {
                _denominatorUnits = new ArrayList<>();
            }
            for (final Unit value : values) {
                _denominatorUnits.add(value);
            }
            return this;
        }

        private void flattenUnits(
                final List<Unit> units,
                final Map<Unit, Integer> numeratorUnitCount,
                final Map<Unit, Integer> denominatorUnitCount) {
            for (final Unit unit : units) {
                if (unit instanceof CompoundUnit) {
                    final CompoundUnit compoundUnit = (CompoundUnit) unit;
                    splitCompoundUnit(compoundUnit, numeratorUnitCount, denominatorUnitCount);
                } else {
                    final Integer count = numeratorUnitCount.get(unit);
                    numeratorUnitCount.put(unit, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
                }
            }
        }

        private void reduceCommonUnits(
                final Map<Unit, Integer> numeratorUnitCount,
                final Map<Unit, Integer> denominatorUnitCount) {
            final Map<Unit, Integer> smallerMap;
            final Map<Unit, Integer> otherMap;
            if (numeratorUnitCount.size() < denominatorUnitCount.size()) {
                smallerMap = numeratorUnitCount;
                otherMap = denominatorUnitCount;
            } else {
                smallerMap = denominatorUnitCount;
                otherMap = numeratorUnitCount;
            }
            final Iterator<Map.Entry<Unit, Integer>> iterator = smallerMap.entrySet().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<Unit, Integer> entry = iterator.next();
                final Unit unit = entry.getKey();
                final int smallerCount = entry.getValue();
                @Nullable final Integer otherCountBoxed = otherMap.get(unit);
                final int otherCount = otherCountBoxed == null ? 0 : otherCountBoxed.intValue();
                if (smallerCount > otherCount) {
                    otherMap.remove(unit);
                    smallerMap.put(unit, Integer.valueOf(smallerCount - otherCount));
                } else if (otherCount > smallerCount) {
                    iterator.remove();
                    otherMap.put(unit, Integer.valueOf(otherCount - smallerCount));
                } else { // smallerCount == otherCount
                    iterator.remove();
                    otherMap.remove(unit);
                }
            }
        }

        private void splitCompoundUnit(
                final CompoundUnit compoundUnit,
                final Map<Unit, Integer> numeratorUnits,
                final Map<Unit, Integer> denominatorUnits) {

            for (final Unit unit : compoundUnit.getNumeratorUnits()) {
                if (unit instanceof CompoundUnit) {
                    final CompoundUnit numeratorCompoundUnit = (CompoundUnit) unit;
                    splitCompoundUnit(numeratorCompoundUnit, numeratorUnits, denominatorUnits);
                } else {
                    final Integer count = numeratorUnits.get(unit);
                    numeratorUnits.put(unit, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
                }
            }
            for (final Unit unit : compoundUnit.getDenominatorUnits()) {
                if (unit instanceof CompoundUnit) {
                    final CompoundUnit denominatorCompoundUnit = (CompoundUnit) unit;
                    splitCompoundUnit(denominatorCompoundUnit, denominatorUnits, numeratorUnits);
                } else {
                    final Integer count = denominatorUnits.get(unit);
                    denominatorUnits.put(unit, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
                }
            }
        }

        private List<Unit> _numeratorUnits;
        private List<Unit> _denominatorUnits;
    }

    private static final class UnitNameComparator implements Comparator<Unit>, Serializable {

        @Override
        public int compare(final Unit unit1, final Unit unit2) {
            return unit1.getName().compareTo(unit2.getName());
        }

        private static final long serialVersionUID = -5279368571532165819L;
    }
}

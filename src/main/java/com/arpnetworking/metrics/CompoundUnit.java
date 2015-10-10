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
 * Interface for a compound unit representation.
 *
 * @author Ville Koskela (vkoskela at groupon dot com)
 */
public interface CompoundUnit extends Unit {

    /**
     * Accessor for numerator units.
     *
     * @return List of numerator units.
     */
    List<Unit> getNumeratorUnits();

    /**
     * Accessor for denominator units.
     *
     * @return List of denominator units.
     */
    List<Unit> getDenominatorUnits();
}

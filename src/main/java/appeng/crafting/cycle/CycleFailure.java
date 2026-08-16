/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.crafting.cycle;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import appeng.api.stacks.AEKey;

/**
 * A structured rejection of a cycle plan request.
 *
 * @param code             rejection reason
 * @param missingInputs    all inputs that could not be covered (empty unless
 *                         {@link CycleFailureCode#INSUFFICIENT_INPUT})
 * @param netChange        planned signed net effect of the full cycle (may be empty on failure)
 * @param aggregateFirings total firing vector of the full cycle (may be empty on failure)
 */
public record CycleFailure(
        CycleFailureCode code,
        Map<AEKey, InputRequirement> missingInputs,
        Map<AEKey, BigInteger> netChange,
        Map<LoopFiring, BigInteger> aggregateFirings) {

    public CycleFailure {
        if (code == null) {
            throw new IllegalArgumentException("A cycle failure requires a reason code");
        }
        missingInputs = copyRequirements(missingInputs);
        netChange = copySigned(netChange);
        aggregateFirings = copyFirings(aggregateFirings);
    }

    /**
     * The input shortage that is most useful to display, if any: a single shortage is returned directly, otherwise
     * null.
     */
    public Map.Entry<AEKey, InputRequirement> primaryShortage() {
        if (missingInputs.size() != 1) {
            return null;
        }
        return missingInputs.entrySet().iterator().next();
    }

    private static Map<AEKey, InputRequirement> copyRequirements(Map<AEKey, InputRequirement> source) {
        LinkedHashMap<AEKey, InputRequirement> copied = new LinkedHashMap<>();
        source.forEach((key, requirement) -> {
            if (key == null || requirement == null) {
                throw new IllegalArgumentException("A cycle shortage must have a key and a requirement");
            }
            copied.put(key, requirement);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copySigned(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() == 0) {
                throw new IllegalArgumentException("A cycle net amount must be non-zero");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<LoopFiring, BigInteger> copyFirings(Map<LoopFiring, BigInteger> source) {
        LinkedHashMap<LoopFiring, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((firing, count) -> {
            if (firing == null || count == null || count.signum() <= 0) {
                throw new IllegalArgumentException("A cycle firing count must be positive");
            }
            copied.put(firing, count);
        });
        return Collections.unmodifiableMap(copied);
    }
}

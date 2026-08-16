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
 * One firing block of a crafting cycle: {@link #count()} identical executions of the same transition ({@link #inputs()}
 * consumed, {@link #outputs()} produced).
 *
 * <p>
 * All amounts use {@link BigInteger} so that arbitrarily large self-multiplying cycles cannot overflow.
 */
public record LoopFiring(Map<AEKey, BigInteger> inputs, Map<AEKey, BigInteger> outputs, BigInteger count) {

    public LoopFiring {
        inputs = copyPositive(inputs, "inputs");
        outputs = copyPositive(outputs, "outputs");
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("A cycle firing must consume at least one input");
        }
        if (count == null || count.signum() <= 0) {
            throw new IllegalArgumentException("A cycle firing count must be positive");
        }
    }

    /**
     * Exact signed effect of a single execution: {@code outputs - inputs}, zero entries removed.
     */
    public Map<AEKey, BigInteger> netChange() {
        LinkedHashMap<AEKey, BigInteger> net = new LinkedHashMap<>();
        inputs.forEach((key, amount) -> net.merge(key, amount.negate(), BigInteger::add));
        outputs.forEach((key, amount) -> net.merge(key, amount, BigInteger::add));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return Collections.unmodifiableMap(net);
    }

    private static Map<AEKey, BigInteger> copyPositive(Map<AEKey, BigInteger> source, String role) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("Cycle firing " + role + " must be positive");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}

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
import java.util.List;
import java.util.Map;

import appeng.api.stacks.AEKey;

/**
 * A closed-form deterministic production cycle and its exact compressed execution schedule.
 *
 * @param oneCycleOrder    stable firing blocks in one logical production cycle
 * @param repetitions      compact complete-cycle count
 * @param aggregateFirings total firing vector across all repetitions
 * @param minimumSeed      exact maximum prefix deficit across all repetitions
 * @param initialInputs    exact inventory that must be reserved
 * @param netChange        exact signed effect across all repetitions
 * @param schedule         executable compressed batch schedule (merged adjacent batches)
 */
public record CyclePlan(
        List<LoopFiring> oneCycleOrder,
        BigInteger repetitions,
        Map<LoopFiring, BigInteger> aggregateFirings,
        Map<AEKey, BigInteger> minimumSeed,
        Map<AEKey, BigInteger> initialInputs,
        Map<AEKey, BigInteger> netChange,
        List<LoopFiring> schedule) {

    /**
     * Copies all plan values and verifies the complete accounting and schedule conservation.
     */
    public CyclePlan {
        if (oneCycleOrder == null || oneCycleOrder.isEmpty() || repetitions == null || repetitions.signum() <= 0 ||
                aggregateFirings == null || minimumSeed == null || initialInputs == null || netChange == null ||
                schedule == null) {
            throw new IllegalArgumentException("A cycle plan requires complete positive accounting");
        }
        oneCycleOrder = List.copyOf(oneCycleOrder);
        aggregateFirings = copyPositiveFirings(aggregateFirings);
        minimumSeed = copyPositiveAmounts(minimumSeed);
        initialInputs = copyPositiveAmounts(initialInputs);
        netChange = copySignedNonZero(netChange);
        schedule = List.copyOf(schedule);

        // The aggregate firing vector must equal the one-cycle order repeated.
        LinkedHashMap<LoopFiring, BigInteger> expected = new LinkedHashMap<>();
        for (LoopFiring firing : oneCycleOrder) {
            expected.merge(firing, firing.count().multiply(repetitions), BigInteger::add);
        }
        if (!expected.equals(aggregateFirings)) {
            throw new IllegalArgumentException("A cycle plan firing vector must equal its repeated one-cycle order");
        }

        // The net change must equal the exact firing effects.
        LinkedHashMap<AEKey, BigInteger> calculatedNet = new LinkedHashMap<>();
        aggregateFirings.forEach((firing, count) -> firing.netChange().forEach(
                (key, amount) -> calculatedNet.merge(key, amount.multiply(count), BigInteger::add)));
        calculatedNet.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!calculatedNet.equals(netChange)) {
            throw new IllegalArgumentException("A cycle plan net change must equal its exact firing effects");
        }

        // Initial inputs must cover every minimum seed.
        for (Map.Entry<AEKey, BigInteger> seed : minimumSeed.entrySet()) {
            if (initialInputs.getOrDefault(seed.getKey(), BigInteger.ZERO).compareTo(seed.getValue()) < 0) {
                throw new IllegalArgumentException("A cycle plan initial input must include every minimum seed");
            }
        }

        // The schedule must conserve the exact final balances.
        LinkedHashMap<AEKey, BigInteger> finalBalances = simulateSchedule(initialInputs, schedule);
        LinkedHashMap<AEKey, BigInteger> calculatedFinal = new LinkedHashMap<>(initialInputs);
        netChange.forEach((key, amount) -> calculatedFinal.merge(key, amount, BigInteger::add));
        calculatedFinal.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!calculatedFinal.equals(finalBalances)) {
            throw new IllegalArgumentException("A cycle plan schedule must conserve its exact final balances");
        }
    }

    /**
     * Simulates the compressed schedule against the initial balances, returning the final non-zero balances.
     */
    public static LinkedHashMap<AEKey, BigInteger> simulateSchedule(
            Map<AEKey, BigInteger> initialBalances,
            List<LoopFiring> schedule) {
        LinkedHashMap<AEKey, BigInteger> balances = new LinkedHashMap<>();
        initialBalances.forEach((key, amount) -> {
            if (amount.signum() > 0) {
                balances.put(key, amount);
            }
        });
        for (LoopFiring batch : schedule) {
            // Required at the start of the batch for each consumed input (deficit grows with the batch count).
            for (Map.Entry<AEKey, BigInteger> input : batch.inputs().entrySet()) {
                BigInteger net = batch.netChange().getOrDefault(input.getKey(), BigInteger.ZERO);
                BigInteger required = net.signum() < 0
                        ? input.getValue().add(net.negate().multiply(batch.count().subtract(BigInteger.ONE)))
                        : input.getValue();
                if (balances.getOrDefault(input.getKey(), BigInteger.ZERO).compareTo(required) < 0) {
                    throw new IllegalArgumentException("A cycle plan schedule batch violates its balance bound");
                }
            }
            batch.netChange().forEach((key, amount) -> {
                BigInteger updated = balances.getOrDefault(key, BigInteger.ZERO)
                        .add(amount.multiply(batch.count()));
                if (updated.signum() < 0) {
                    throw new IllegalArgumentException("A cycle plan schedule produced a negative balance");
                }
                if (updated.signum() == 0) {
                    balances.remove(key);
                } else {
                    balances.put(key, updated);
                }
            });
        }
        return balances;
    }

    private static Map<LoopFiring, BigInteger> copyPositiveFirings(Map<LoopFiring, BigInteger> source) {
        LinkedHashMap<LoopFiring, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((firing, count) -> {
            if (firing == null || count == null || count.signum() <= 0) {
                throw new IllegalArgumentException("A cycle firing count must be positive");
            }
            copied.put(firing, count);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copyPositiveAmounts(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A cycle input amount must be positive");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copySignedNonZero(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() == 0) {
                throw new IllegalArgumentException("A cycle net amount must be non-zero");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}

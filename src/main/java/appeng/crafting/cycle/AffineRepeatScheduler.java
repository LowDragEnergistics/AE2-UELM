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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.stacks.AEKey;

/**
 * Builds an exact compressed schedule for repeated executions of one known productive cycle.
 *
 * <p>
 * Complete cycle rotations are grouped at affine balance breakpoints instead of being searched firing-by-firing: for
 * each rotation the maximum number of executable cycles is found by interval contraction on the per-input slope/margin,
 * so the schedule has logarithmic size in the number of repetitions. Ported from DataEnergistics'
 * {@code AffineTrinityDeterministicRepeatScheduler}.
 */
public final class AffineRepeatScheduler {

    /**
     * @param oneCycleOrder   exact firing blocks for one cycle
     * @param repetitions     positive number of complete cycles
     * @param initialBalances exact seed and externally consumed inputs for all repetitions
     * @param maxStates       positive compressed-state limit
     * @return executable compressed schedule (merged adjacent batches), or a failure result
     */
    public CycleScheduleResult schedule(List<LoopFiring> oneCycleOrder,
            BigInteger repetitions,
            Map<AEKey, BigInteger> initialBalances,
            int maxStates) {
        if (oneCycleOrder == null || oneCycleOrder.isEmpty() || repetitions == null || repetitions.signum() <= 0 ||
                initialBalances == null || maxStates <= 0) {
            throw new IllegalArgumentException("A cycle repeat schedule requires complete inputs");
        }
        List<LoopFiring> cycle = List.copyOf(oneCycleOrder);
        LinkedHashMap<AEKey, BigInteger> balances = copyBalances(initialBalances);
        ArrayList<LoopFiring> batches = new ArrayList<>();
        BigInteger remaining = repetitions;
        int statesVisited = 1;

        while (remaining.signum() > 0) {
            if (statesVisited >= maxStates) {
                return new CycleScheduleResult.Failure(CycleFailureCode.SEARCH_LIMIT);
            }

            CycleBatch selected = selectLargestExecutableRotation(cycle, remaining, balances);
            if (selected.cycles().signum() <= 0) {
                return new CycleScheduleResult.Failure(CycleFailureCode.NO_EXECUTABLE_ORDER);
            }
            applyRotation(cycle, selected, balances, batches);
            remaining = remaining.subtract(selected.cycles());
            statesVisited = Math.addExact(statesVisited, 1);
        }
        return new CycleScheduleResult.Success(List.copyOf(batches));
    }

    private static CycleBatch selectLargestExecutableRotation(
            List<LoopFiring> cycle,
            BigInteger remaining,
            Map<AEKey, BigInteger> balances) {
        CycleBatch selected = new CycleBatch(0, BigInteger.ZERO);
        for (int rotation = 0; rotation < cycle.size(); rotation++) {
            BigInteger cycles = maximumExecutableCycles(cycle, rotation, remaining, balances);
            if (cycles.compareTo(selected.cycles()) > 0) {
                selected = new CycleBatch(rotation, cycles);
            }
        }
        return selected;
    }

    private static BigInteger maximumExecutableCycles(
            List<LoopFiring> cycle,
            int rotation,
            BigInteger remaining,
            Map<AEKey, BigInteger> balances) {
        BigInteger lower = BigInteger.ONE;
        BigInteger upper = remaining;
        LinkedHashMap<AEKey, BigInteger> prefixPerCycle = new LinkedHashMap<>();
        for (int offset = 0; offset < cycle.size(); offset++) {
            LoopFiring firing = cycle.get((rotation + offset) % cycle.size());
            for (Map.Entry<AEKey, BigInteger> input : firing.inputs().entrySet()) {
                BigInteger delta = firing.netChange().getOrDefault(input.getKey(), BigInteger.ZERO);
                BigInteger constant = delta.signum() < 0 ? input.getValue().add(delta) : input.getValue();
                BigInteger requiredPerCycle = delta.signum() < 0
                        ? delta.negate().multiply(firing.count())
                        : BigInteger.ZERO;
                BigInteger prefix = prefixPerCycle.getOrDefault(input.getKey(), BigInteger.ZERO);
                BigInteger slope = requiredPerCycle.subtract(prefix);
                BigInteger margin = balances.getOrDefault(input.getKey(), BigInteger.ZERO).subtract(constant);
                if (slope.signum() > 0) {
                    if (margin.signum() < 0) {
                        return BigInteger.ZERO;
                    }
                    upper = upper.min(margin.divide(slope));
                } else if (slope.signum() == 0) {
                    if (margin.signum() < 0) {
                        return BigInteger.ZERO;
                    }
                } else if (margin.signum() < 0) {
                    lower = lower.max(ceilDivide(margin.negate(), slope.negate()));
                }
                if (upper.compareTo(lower) < 0) {
                    return BigInteger.ZERO;
                }
            }
            firing.netChange().forEach((key, amount) -> prefixPerCycle.merge(
                    key,
                    amount.multiply(firing.count()),
                    BigInteger::add));
        }
        return upper.compareTo(lower) >= 0 ? upper : BigInteger.ZERO;
    }

    private static void applyRotation(
            List<LoopFiring> cycle,
            CycleBatch selected,
            Map<AEKey, BigInteger> balances,
            List<LoopFiring> batches) {
        for (int offset = 0; offset < cycle.size(); offset++) {
            LoopFiring base = cycle.get((selected.rotation() + offset) % cycle.size());
            BigInteger count = base.count().multiply(selected.cycles());
            Map<AEKey, BigInteger> required = requiredAtStart(base, count);
            if (!hasInputs(balances, required)) {
                throw new IllegalStateException("An exact cycle batch violated its derived balance bound");
            }
            base.netChange().forEach((key, amount) -> {
                BigInteger updated = balances.getOrDefault(key, BigInteger.ZERO).add(amount.multiply(count));
                if (updated.signum() < 0) {
                    throw new IllegalStateException("An exact cycle batch produced a negative balance");
                }
                if (updated.signum() == 0) {
                    balances.remove(key);
                } else {
                    balances.put(key, updated);
                }
            });
            appendBatch(batches, new LoopFiring(base.inputs(), base.outputs(), count));
        }
    }

    private static Map<AEKey, BigInteger> requiredAtStart(LoopFiring firing, BigInteger count) {
        LinkedHashMap<AEKey, BigInteger> required = new LinkedHashMap<>();
        firing.inputs().forEach((key, input) -> {
            BigInteger net = firing.netChange().getOrDefault(key, BigInteger.ZERO);
            BigInteger amount = net.signum() < 0
                    ? input.add(net.negate().multiply(count.subtract(BigInteger.ONE)))
                    : input;
            required.put(key, amount);
        });
        return Collections.unmodifiableMap(required);
    }

    private static void appendBatch(List<LoopFiring> batches, LoopFiring added) {
        if (!batches.isEmpty()) {
            LoopFiring previous = batches.get(batches.size() - 1);
            if (previous.inputs().equals(added.inputs()) && previous.outputs().equals(added.outputs())) {
                batches.set(
                        batches.size() - 1,
                        new LoopFiring(previous.inputs(), previous.outputs(), previous.count().add(added.count())));
                return;
            }
        }
        batches.add(added);
    }

    private static boolean hasInputs(Map<AEKey, BigInteger> balances, Map<AEKey, BigInteger> required) {
        return required.entrySet().stream().allMatch(entry -> balances
                .getOrDefault(entry.getKey(), BigInteger.ZERO)
                .compareTo(entry.getValue()) >= 0);
    }

    private static LinkedHashMap<AEKey, BigInteger> copyBalances(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("Cycle repeat balances cannot be negative or null");
            }
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return copied;
    }

    private static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }

    private record CycleBatch(int rotation, BigInteger cycles) {
    }
}

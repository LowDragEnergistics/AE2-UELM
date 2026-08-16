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
import java.util.Set;

import appeng.api.stacks.AEKey;

/**
 * Solves a stable deterministic cycle by closed-form net effect and maximum prefix deficit.
 *
 * <p>
 * Given the ordered firings of one complete production cycle, the planner computes the per-cycle net change, the
 * minimum seed (maximum prefix deficit across the cycle), the repetition count needed to satisfy a demand, and verifies
 * that the inventory plus producible inputs can cover the seed. Ported from DataEnergistics'
 * {@code TrinityDeterministicCyclePlanner}.
 */
public final class DeterministicCyclePlanner {

    private final AffineRepeatScheduler scheduler;

    public DeterministicCyclePlanner() {
        this.scheduler = new AffineRepeatScheduler();
    }

    /**
     * @param oneCycleOrder     ordered firing blocks in one complete production cycle
     * @param target            requested productive key
     * @param requestedAmount   positive requested delivery
     * @param quantityMode      net-new or final-total semantics
     * @param available         non-negative inventory snapshot
     * @param producibleInputs  inputs that earlier graph components can supply after this cycle is selected
     * @param maxScheduleStates compressed scheduling state bound
     * @return exact compact cycle or structured rejection
     */
    public CyclePlanResult plan(
            List<LoopFiring> oneCycleOrder,
            AEKey target,
            BigInteger requestedAmount,
            CycleQuantityMode quantityMode,
            Map<AEKey, BigInteger> available,
            Set<AEKey> producibleInputs,
            int maxScheduleStates) {
        if (oneCycleOrder == null || oneCycleOrder.isEmpty() || target == null || quantityMode == null ||
                available == null || producibleInputs == null || requestedAmount == null ||
                requestedAmount.signum() <= 0 || maxScheduleStates <= 0) {
            throw new IllegalArgumentException("A cycle plan request is incomplete");
        }
        Map<AEKey, BigInteger> inventory = copyAvailable(available);
        CycleBalance oneCycle = cycleBalance(oneCycleOrder);
        BigInteger targetEffect = oneCycle.netChange().getOrDefault(target, BigInteger.ZERO);
        if (targetEffect.signum() <= 0) {
            return failure(CycleFailureCode.NO_PRODUCTIVE_CYCLE, Map.of(), Map.of(), Map.of());
        }

        BigInteger requiredNet = quantityMode == CycleQuantityMode.NET_NEW
                ? requestedAmount
                : requestedAmount.subtract(inventory.getOrDefault(target, BigInteger.ZERO)).max(BigInteger.ZERO);
        BigInteger repetitions = ceilDivide(requiredNet, targetEffect);
        if (quantityMode == CycleQuantityMode.FINAL_TOTAL) {
            repetitions = repetitions.max(BigInteger.ONE);
        }
        Map<AEKey, BigInteger> minimumSeed = repeatedMinimumSeed(oneCycle, repetitions);
        Map<AEKey, BigInteger> netChange = multiply(oneCycle.netChange(), repetitions);
        LinkedHashMap<AEKey, BigInteger> initialInputs = new LinkedHashMap<>(minimumSeed);
        if (quantityMode == CycleQuantityMode.FINAL_TOTAL) {
            BigInteger targetContribution = requestedAmount
                    .subtract(netChange.getOrDefault(target, BigInteger.ZERO))
                    .max(BigInteger.ZERO);
            if (targetContribution.signum() > 0) {
                initialInputs.merge(target, targetContribution, BigInteger::max);
            }
        }

        LinkedHashMap<LoopFiring, BigInteger> aggregateFirings = new LinkedHashMap<>();
        for (LoopFiring firing : oneCycleOrder) {
            aggregateFirings.merge(firing, firing.count().multiply(repetitions), BigInteger::add);
        }

        LinkedHashMap<AEKey, InputRequirement> shortages = new LinkedHashMap<>();
        for (Map.Entry<AEKey, BigInteger> input : initialInputs.entrySet()) {
            BigInteger required = input.getValue();
            BigInteger allocated = required.min(inventory.getOrDefault(input.getKey(), BigInteger.ZERO));
            BigInteger missing = required.subtract(allocated);
            if (missing.signum() > 0 && !producibleInputs.contains(input.getKey())) {
                shortages.put(input.getKey(), new InputRequirement(required, allocated, missing));
            }
        }
        if (!shortages.isEmpty()) {
            return failure(CycleFailureCode.INSUFFICIENT_INPUT, shortages, netChange, aggregateFirings);
        }

        CycleScheduleResult schedule = this.scheduler.schedule(
                oneCycleOrder,
                repetitions,
                initialInputs,
                maxScheduleStates);
        if (!schedule.successful()) {
            return failure(schedule.failureCode(), Map.of(), netChange, aggregateFirings);
        }
        return new CyclePlanResult.Success(new CyclePlan(
                oneCycleOrder,
                repetitions,
                aggregateFirings,
                minimumSeed,
                initialInputs,
                netChange,
                schedule.schedule()));
    }

    private static CycleBalance cycleBalance(List<LoopFiring> order) {
        LinkedHashMap<AEKey, BigInteger> balance = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> seed = new LinkedHashMap<>();
        for (LoopFiring firing : order) {
            for (Map.Entry<AEKey, BigInteger> input : firing.inputs().entrySet()) {
                BigInteger delta = firing.netChange().getOrDefault(input.getKey(), BigInteger.ZERO);
                BigInteger requiredBeforeBlock = input.getValue();
                if (delta.signum() < 0) {
                    requiredBeforeBlock = requiredBeforeBlock.add(
                            delta.negate().multiply(firing.count().subtract(BigInteger.ONE)));
                }
                BigInteger deficit = requiredBeforeBlock.subtract(
                        balance.getOrDefault(input.getKey(), BigInteger.ZERO));
                if (deficit.signum() > 0) {
                    seed.merge(input.getKey(), deficit, BigInteger::max);
                }
            }
            firing.netChange()
                    .forEach((key, amount) -> balance.merge(key, amount.multiply(firing.count()), BigInteger::add));
        }
        balance.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return new CycleBalance(
                Collections.unmodifiableMap(seed),
                Collections.unmodifiableMap(balance));
    }

    private static Map<AEKey, BigInteger> repeatedMinimumSeed(CycleBalance oneCycle,
            BigInteger repetitions) {
        LinkedHashMap<AEKey, BigInteger> seed = new LinkedHashMap<>(oneCycle.minimumSeed());
        oneCycle.netChange().forEach((key, effect) -> {
            if (effect.signum() < 0) {
                BigInteger repeatedDeficit = effect.negate().multiply(repetitions.subtract(BigInteger.ONE));
                seed.merge(key, repeatedDeficit, BigInteger::add);
            }
        });
        seed.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return Collections.unmodifiableMap(seed);
    }

    private static Map<AEKey, BigInteger> multiply(Map<AEKey, BigInteger> amounts,
            BigInteger multiplier) {
        LinkedHashMap<AEKey, BigInteger> multiplied = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> {
            BigInteger result = amount.multiply(multiplier);
            if (result.signum() != 0) {
                multiplied.put(key, result);
            }
        });
        return Collections.unmodifiableMap(multiplied);
    }

    private static Map<AEKey, BigInteger> copyAvailable(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("Cycle inventory cannot be negative or null");
            }
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(copied);
    }

    private static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
        if (numerator.signum() == 0) {
            return BigInteger.ZERO;
        }
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }

    private static CyclePlanResult failure(
            CycleFailureCode code,
            Map<AEKey, InputRequirement> shortages,
            Map<AEKey, BigInteger> netChange,
            Map<LoopFiring, BigInteger> aggregateFirings) {
        return new CyclePlanResult.Failure(new CycleFailure(code, shortages, netChange, aggregateFirings));
    }

    /**
     * Per-cycle minimum seed and net change.
     */
    private record CycleBalance(
            Map<AEKey, BigInteger> minimumSeed,
            Map<AEKey, BigInteger> netChange) {
    }
}

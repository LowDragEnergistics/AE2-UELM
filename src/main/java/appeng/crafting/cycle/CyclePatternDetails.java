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
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * A synthetic pattern that represents one complete self-loop production plan. The crafting service sees the loop's
 * target as craftable; when a request is pushed, the matrix re-plans from the actual seed amounts found in the input
 * holder and executes the loop.
 *
 * @param target       the requested productive key
 * @param definition   the item key used to identify this pattern
 * @param inputs       the seed (initial inputs) of one full plan
 * @param outputAmount the planned net output of one full plan
 */
public record CyclePatternDetails(
        AEKey target,
        AEItemKey definition,
        Map<AEKey, BigInteger> inputs,
        BigInteger outputAmount) implements IPatternDetails {

    /**
     * Creates the synthetic pattern for a successful cycle plan.
     */
    public static CyclePatternDetails forPlan(AEKey target, CyclePlan plan) {
        return new CyclePatternDetails(
                target,
                asItemKey(target),
                plan.initialInputs(),
                plan.netChange().getOrDefault(target, BigInteger.ZERO).max(BigInteger.ONE));
    }

    private static AEItemKey asItemKey(AEKey target) {
        if (target instanceof AEItemKey itemKey) {
            return itemKey;
        }
        throw new IllegalArgumentException("The self-loop matrix only supports item targets");
    }

    public CyclePatternDetails {
        if (target == null || definition == null || inputs == null || inputs.isEmpty() || outputAmount == null
                || outputAmount.signum() <= 0) {
            throw new IllegalArgumentException("A cycle pattern requires a target, inputs and a positive output");
        }
        inputs = Map.copyOf(inputs);
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        var inputs = new ArrayList<IInput>(this.inputs.size());
        this.inputs.forEach((key, amount) -> inputs.add(new CycleInput(key, amount)));
        return inputs.toArray(IInput[]::new);
    }

    @Override
    public GenericStack[] getOutputs() {
        return new GenericStack[] { new GenericStack(target, saturate(outputAmount)) };
    }

    @Override
    public boolean isInputOnly() {
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CyclePatternDetails that
                && target.equals(that.target) && definition.equals(that.definition);
    }

    @Override
    public int hashCode() {
        return target.hashCode() * 31 + definition.hashCode();
    }

    /**
     * One seed input slot.
     */
    record CycleInput(AEKey key, BigInteger amount) implements IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { new GenericStack(key, 1) };
        }

        @Override
        public long getMultiplier() {
            return saturate(amount);
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return input.equals(key);
        }

        @Nullable
        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }

    private static long saturate(BigInteger amount) {
        if (amount.signum() <= 0) {
            return 1;
        }
        return amount.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
}

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetails.IInput;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.TunnelPatternExpander;
import appeng.crafting.pattern.TunnelPatternItem;

/**
 * Converts processing patterns into cycle {@link LoopFiring}s for the self-loop matrix.
 *
 * <p>
 * Tunnel pattern references inside a pattern's inputs are expanded into their concrete contents via
 * {@link TunnelPatternExpander}, so a self-loop recipe may consume a tunnel pattern (whose contents can be changed
 * without touching the referencing pattern). A pattern whose tunnel references cannot be fully resolved (missing
 * target, tunnel cycle, malformed UUID, amount overflow) is rejected, since the matrix must know the exact real inputs
 * to compute a plan.
 */
public final class CyclePatterns {

    private CyclePatterns() {
    }

    /**
     * Converts a processing pattern to one firing of a cycle.
     *
     * @param pattern      a processing pattern with at least one output
     * @param tunnelLookup resolves an input-only pattern by UUID (ME storage / crafting service / own inventory)
     * @return the firing, or null if the pattern is input-only, degenerate, or its tunnel references cannot be resolved
     *         to concrete inputs
     */
    @Nullable
    public static LoopFiring fromPattern(IPatternDetails pattern,
            Function<UUID, IPatternDetails> tunnelLookup) {
        if (pattern == null || pattern.isInputOnly() || pattern.getOutputs().length == 0) {
            return null;
        }
        var expanded = TunnelPatternExpander.expandInputs(pattern.getInputs(), tunnelLookup, null);
        if (expanded == null) {
            // Tunnel cycle, malformed reference or amount overflow.
            return null;
        }
        // A tunnel reference whose target is missing or not input-only is kept as-is by the expander;
        // the matrix needs the exact real inputs, so such patterns are rejected.
        for (var input : expanded) {
            var first = input.getPossibleInputs()[0];
            if (first.what() instanceof AEItemKey itemKey && itemKey.getItem() instanceof TunnelPatternItem) {
                return null;
            }
        }
        var inputs = inputsOf(expanded);
        if (inputs.isEmpty()) {
            return null;
        }
        var outputs = outputsOf(pattern.getOutputs());
        if (outputs.isEmpty()) {
            return null;
        }
        return new LoopFiring(inputs, outputs, BigInteger.ONE);
    }

    /**
     * Builds the concrete consumption map of expanded inputs. Tunnel references that were kept unresolved (their target
     * is missing or not input-only) still carry a {@link TunnelPatternItem} as their possible input; such patterns are
     * rejected because the matrix cannot compute exact real inputs.
     */
    public static Map<AEKey, BigInteger> inputsOf(List<IInput> expandedInputs) {
        LinkedHashMap<AEKey, BigInteger> inputs = new LinkedHashMap<>();
        for (var input : expandedInputs) {
            var first = input.getPossibleInputs()[0];
            if (first.what() instanceof AEItemKey itemKey && itemKey.getItem() instanceof TunnelPatternItem) {
                throw new UnresolvedTunnelReferenceException(first.what());
            }
            BigInteger amount = BigInteger.valueOf(first.amount())
                    .multiply(BigInteger.valueOf(input.getMultiplier()));
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("A cycle input amount must be positive");
            }
            inputs.merge(first.what(), amount, BigInteger::add);
        }
        return Map.copyOf(inputs);
    }

    /**
     * Builds the output map of a pattern.
     */
    public static Map<AEKey, BigInteger> outputsOf(GenericStack[] outputs) {
        LinkedHashMap<AEKey, BigInteger> built = new LinkedHashMap<>();
        for (var output : outputs) {
            if (output == null || output.what() == null || output.amount() <= 0) {
                throw new IllegalArgumentException("A cycle pattern cannot contain an invalid output");
            }
            built.merge(output.what(), BigInteger.valueOf(output.amount()), BigInteger::add);
        }
        return Map.copyOf(built);
    }

    /**
     * Thrown when an expanded pattern input still is a tunnel pattern reference (unresolvable target).
     */
    public static final class UnresolvedTunnelReferenceException extends RuntimeException {
        public UnresolvedTunnelReferenceException(AEKey key) {
            super("Unresolved tunnel reference in cycle pattern input: " + key);
        }
    }
}

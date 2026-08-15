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

package appeng.crafting.pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetails.IInput;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

/**
 * Expands tunnel pattern references in pattern inputs at craft time.
 * <p>
 * A tunnel pattern reference is an input whose item is a {@link TunnelPatternItem}. It is replaced by the inputs of the
 * referenced input-only pattern (looked up by UUID), each multiplied by the number of referenced tunnel items. The
 * expansion is recursive, so a tunnel pattern can reference other tunnel patterns.
 */
public final class TunnelPatternExpander {

    private TunnelPatternExpander() {
    }

    /**
     * Expands tunnel pattern references in the given inputs.
     *
     * @param inputs         The inputs of a pattern, as decoded from its NBT.
     * @param lookup         Looks up an input-only pattern by UUID (usually the grid's crafting service).
     * @param parentPatterns The chain of patterns above the current one, used as an additional cycle guard.
     * @return The expanded inputs, or null if expansion failed (malformed tunnel reference, cycle, or amount overflow).
     *         Inputs that are not tunnel references are returned unchanged (their substitution semantics are
     *         preserved). A tunnel reference whose target is missing or not input-only is kept as-is, matching the
     *         reference implementation.
     */
    @Nullable
    public static List<IInput> expandInputs(IInput[] inputs, Function<UUID, IPatternDetails> lookup,
            Set<IPatternDetails> parentPatterns) {
        var expanded = new ArrayList<IInput>(inputs.length);
        var expansionStack = new HashSet<UUID>();
        for (var input : inputs) {
            if (!expand(input, 1, lookup, parentPatterns, expansionStack, expanded)) {
                return null;
            }
        }
        return expanded;
    }

    private static boolean expand(IInput input, long currentMultiplier, Function<UUID, IPatternDetails> lookup,
            Set<IPatternDetails> parentPatterns, Set<UUID> expansionStack, List<IInput> out) {
        var firstInput = input.getPossibleInputs()[0];
        var tunnelUuid = getTunnelUuid(firstInput);
        if (tunnelUuid == null) {
            // Not a tunnel reference: keep the input unchanged at the top level, or multiply it deeper in the chain.
            if (currentMultiplier == 1) {
                out.add(input);
            } else {
                long amount;
                try {
                    amount = Math.multiplyExact(firstInput.amount(), currentMultiplier);
                } catch (ArithmeticException ex) {
                    return false;
                }
                out.add(new AEProcessingPattern.Input(new GenericStack(firstInput.what(), amount)));
            }
            return true;
        }

        var target = lookup.apply(tunnelUuid);
        if (target == null || !target.isInputOnly()) {
            // Missing or non-input-only target: keep the original input (reference implementation behavior).
            out.add(input);
            return true;
        }

        if (parentPatterns != null && parentPatterns.contains(target)) {
            return false;
        }
        if (!expansionStack.add(tunnelUuid)) {
            return false;
        }

        long referenceMultiplier;
        try {
            referenceMultiplier = Math.multiplyExact(currentMultiplier,
                    Math.multiplyExact(firstInput.amount(), input.getMultiplier()));
        } catch (ArithmeticException ex) {
            expansionStack.remove(tunnelUuid);
            return false;
        }

        for (var targetInput : target.getInputs()) {
            if (!expand(targetInput, referenceMultiplier, lookup, parentPatterns, expansionStack, out)) {
                expansionStack.remove(tunnelUuid);
                return false;
            }
        }

        expansionStack.remove(tunnelUuid);
        return true;
    }

    @Nullable
    private static UUID getTunnelUuid(GenericStack stack) {
        if (!(stack.what() instanceof AEItemKey itemKey)) {
            return null;
        }
        if (!(itemKey.getItem() instanceof TunnelPatternItem)) {
            return null;
        }
        return TunnelPatternItem.getTunnelUuid(itemKey.toStack());
    }
}

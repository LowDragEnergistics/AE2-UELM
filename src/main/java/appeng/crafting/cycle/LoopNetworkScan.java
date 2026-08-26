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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;

/**
 * Scans an ME network's crafting providers for processing patterns and converts them into cycle firings, keeping the
 * provider binding needed to forward execution later.
 *
 * @param bindings one entry per usable processing pattern, in scan order
 */
public record LoopNetworkScan(List<PatternBinding> bindings) {

    /**
     * One usable pattern together with its firing form and the provider that offers it.
     */
    public record PatternBinding(IPatternDetails pattern, ICraftingProvider provider, LoopFiring firing) {
    }

    public LoopNetworkScan {
        bindings = List.copyOf(bindings);
    }

    /**
     * Scans all providers reachable through the given grid (machine block entities offering an
     * {@link ICraftingProvider} service). Patterns that cannot be converted to a firing (input-only, degenerate,
     * unresolvable tunnel references) are skipped.
     *
     * @param grid         the ME network
     * @param tunnelLookup resolves tunnel pattern references to their input-only pattern
     * @return the scan result
     */
    public static LoopNetworkScan scan(IGrid grid, Function<UUID, IPatternDetails> tunnelLookup) {
        var bindings = new ArrayList<PatternBinding>();
        for (var blockEntity : grid.getMachines(appeng.blockentity.crafting.PatternProviderBlockEntity.class)) {
            var logic = blockEntity.getLogic();
            for (var pattern : logic.getAvailablePatterns()) {
                var firing = CyclePatterns.fromPattern(pattern, tunnelLookup);
                if (firing != null) {
                    bindings.add(new PatternBinding(pattern, logic, firing));
                }
            }
        }
        return new LoopNetworkScan(bindings);
    }

    /**
     * @return the firings in scan order (same indices as the bindings)
     */
    public List<LoopFiring> firings() {
        var firings = new ArrayList<LoopFiring>(bindings.size());
        for (var binding : bindings) {
            firings.add(binding.firing());
        }
        return Collections.unmodifiableList(firings);
    }

    /**
     * @return the detected production cycles over the scanned firings
     */
    public List<LoopDetector.DetectedLoop> loops() {
        return LoopDetector.detect(firings());
    }

    /**
     * All firings of one detected loop, mapped back to their network bindings.
     */
    public List<PatternBinding> bindingsOf(LoopDetector.DetectedLoop loop) {
        var result = new ArrayList<PatternBinding>(loop.firingIndices().size());
        for (int index : loop.firingIndices()) {
            result.add(bindings.get(index));
        }
        return Collections.unmodifiableList(result);
    }
}

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

package appeng.blockentity.misc;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.crafting.cycle.CyclePatternDetails;
import appeng.crafting.cycle.CyclePlan;
import appeng.crafting.cycle.CyclePlanResult;
import appeng.crafting.cycle.CycleQuantityMode;
import appeng.crafting.cycle.DeterministicCyclePlanner;
import appeng.crafting.cycle.LoopExecutionTask;
import appeng.crafting.cycle.LoopFiring;
import appeng.crafting.cycle.LoopNetworkScan;

/**
 * The ME Self-Loop Matrix: a computing device attached to an ME network that automatically identifies self-loop
 * (self-multiplying) crafting recipes from the network's pattern providers and takes them over as an
 * {@link ICraftingProvider}.
 *
 * <p>
 * On a periodic scan it collects every processing pattern offered by the network's pattern providers, detects
 * production cycles (strongly connected firing groups), plans each productive cycle against the current network storage
 * and registers the cycle targets as craftable via synthetic patterns. When the crafting CPU pushes a request, the
 * matrix re-plans from the provided seed amounts and forwards the compressed schedule batches to the providers owning
 * the loop patterns; the network machines produce the goods as usual.
 */
public class SelfLoopMatrixBlockEntity extends AENetworkBlockEntity
        implements ServerTickingBlockEntity, ICraftingProvider {

    private static final int SCAN_INTERVAL = 20;
    private static final int MAX_SCHEDULE_STATES = 1024;
    private static final BigInteger MAX_REQUEST = BigInteger.valueOf(Long.MAX_VALUE);

    private int scanTimer = 0;
    private String lastScanFingerprint = "";
    private final List<Takeover> takeovers = new ArrayList<>();
    private final List<CyclePatternDetails> patterns = new ArrayList<>();
    private LoopExecutionTask activeTask;

    /**
     * One registered takeover: a detected loop, its productive target, the last successful plan and the network
     * bindings used to execute it.
     */
    record Takeover(
            List<LoopFiring> order,
            AEKey target,
            CyclePlan plan,
            List<LoopNetworkScan.PatternBinding> bindings) {
    }

    public SelfLoopMatrixBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
        this.getMainNode()
                .setIdlePowerUsage(1.0)
                .addService(ICraftingProvider.class, this);
    }

    @Override
    public void serverTick() {
        if (++scanTimer >= SCAN_INTERVAL) {
            scanTimer = 0;
            scanNetwork();
        }
        if (activeTask != null && activeTask.tick()) {
            if (activeTask.isComplete()) {
                activeTask = null;
            }
        }
    }

    /**
     * Scans the network for loop patterns and refreshes the synthetic craftable patterns when the provider catalog
     * changed.
     */
    private void scanNetwork() {
        var grid = getMainNode().getGrid();
        if (grid == null) {
            if (!this.patterns.isEmpty()) {
                this.takeovers.clear();
                this.patterns.clear();
                ICraftingProvider.requestUpdate(getMainNode());
            }
            return;
        }
        var scan = LoopNetworkScan.scan(grid, this::lookupTunnel);
        var fingerprint = scan.bindings().stream()
                .map(binding -> binding.pattern().getDefinition().toString())
                .reduce("", (a, b) -> a + '|' + b);
        if (fingerprint.equals(this.lastScanFingerprint) && !this.patterns.isEmpty()) {
            return;
        }
        this.lastScanFingerprint = fingerprint;

        var available = availableAmounts(grid);
        var newTakeovers = new ArrayList<Takeover>();
        var newPatterns = new ArrayList<CyclePatternDetails>();
        for (var loop : scan.loops()) {
            var loopBindings = scan.bindingsOf(loop);
            for (var target : loop.productiveTargets()) {
                var planned = maxPlan(loop.order(), target, available);
                if (planned == null) {
                    continue;
                }
                newTakeovers.add(new Takeover(loop.order(), target, planned, loopBindings));
                newPatterns.add(CyclePatternDetails.forPlan(target, planned));
            }
        }
        this.takeovers.clear();
        this.takeovers.addAll(newTakeovers);
        this.patterns.clear();
        this.patterns.addAll(newPatterns);
        ICraftingProvider.requestUpdate(getMainNode());
    }

    private IPatternDetails lookupTunnel(UUID uuid) {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            return grid.getCraftingService().getInputOnlyPattern(uuid);
        }
        return null;
    }

    /**
     * Finds the largest plan whose seed is covered by the available inventory, by exponential probing followed by
     * binary search. Returns the plan for the largest successful request (at least one cycle), or null if even one
     * cycle cannot be seeded.
     */
    static CyclePlan maxPlan(List<LoopFiring> order, AEKey target, Map<AEKey, BigInteger> available) {
        var planner = new DeterministicCyclePlanner();
        CyclePlanResult lastSuccess = planner.plan(
                order, target, BigInteger.ONE, CycleQuantityMode.NET_NEW, available, Set.of(), MAX_SCHEDULE_STATES);
        if (!lastSuccess.successful()) {
            return null;
        }
        CyclePlan best = lastSuccess.plan();
        BigInteger lo = BigInteger.ONE; // known-successful request
        BigInteger hi = null; // first known-failed request, or null if not yet probed
        BigInteger probe = BigInteger.ONE;
        while (true) {
            var next = probe.multiply(BigInteger.valueOf(2));
            if (next.compareTo(MAX_REQUEST) > 0) {
                break;
            }
            probe = next;
            var attempt = planner.plan(
                    order, target, probe, CycleQuantityMode.NET_NEW, available, Set.of(), MAX_SCHEDULE_STATES);
            if (attempt.successful()) {
                lo = probe;
                best = attempt.plan();
            } else {
                hi = probe;
                break;
            }
        }
        if (hi == null) {
            hi = MAX_REQUEST;
        }
        // Binary search (lo, hi) for the maximum successful request.
        while (hi.subtract(lo).compareTo(BigInteger.ONE) > 0) {
            var mid = lo.add(hi).divide(BigInteger.valueOf(2));
            var attempt = planner.plan(
                    order, target, mid, CycleQuantityMode.NET_NEW, available, Set.of(), MAX_SCHEDULE_STATES);
            if (attempt.successful()) {
                lo = mid;
                best = attempt.plan();
            } else {
                hi = mid;
            }
        }
        return best;
    }

    // ICraftingProvider

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return List.copyOf(this.patterns);
    }

    @Override
    public int getPatternPriority() {
        // Take over loop targets ahead of the raw loop patterns offered by providers.
        return 1000;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (activeTask != null && !activeTask.isComplete()) {
            return false;
        }
        var takeover = takeoverFor(patternDetails);
        if (takeover == null) {
            return false;
        }
        var available = new LinkedHashMap<AEKey, BigInteger>();
        if (inputHolder != null) {
            for (var holder : inputHolder) {
                holder.forEach(entry -> available.merge(
                        entry.getKey(), BigInteger.valueOf(entry.getLongValue()), BigInteger::add));
            }
        }
        // The seed amounts received decide how much of the cycle can actually run.
        var plan = maxPlan(takeover.order(), takeover.target(), available);
        if (plan == null) {
            return false;
        }
        this.activeTask = new LoopExecutionTask(plan, takeover.bindings());
        return true;
    }

    @Override
    public boolean isBusy() {
        return activeTask != null && !activeTask.isComplete();
    }

    @Override
    public Set<AEKey> getEmitableItems() {
        // Do not advertise emitable items: the CPU must expand the seed inputs so that a missing
        // seed is reported instead of silently promised.
        return Set.of();
    }

    private Takeover takeoverFor(IPatternDetails patternDetails) {
        if (patternDetails instanceof CyclePatternDetails cyclePattern) {
            for (var takeover : this.takeovers) {
                if (takeover.target().equals(cyclePattern.target())) {
                    return takeover;
                }
            }
        }
        return null;
    }

    private static Map<AEKey, BigInteger> availableAmounts(IGrid grid) {
        var amounts = new LinkedHashMap<AEKey, BigInteger>();
        grid.getStorageService().getCachedInventory().forEach(
                entry -> amounts.put(entry.getKey(), BigInteger.valueOf(entry.getLongValue())));
        return Map.copyOf(amounts);
    }
}

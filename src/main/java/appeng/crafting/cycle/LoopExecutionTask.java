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
import java.util.List;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

/**
 * Executes one accepted self-loop plan by forwarding its compressed schedule batches to the network providers that own
 * the corresponding patterns. One batch is forwarded per tick; if a provider is busy, the batch is retried on a later
 * tick. The actual products are produced by the network machines behind the providers and returned to the network
 * storage by them.
 */
public final class LoopExecutionTask {

    private final CyclePlan plan;
    private final List<LoopNetworkScan.PatternBinding> bindings;
    private final List<LoopFiring> batches;
    private int nextBatch;

    public LoopExecutionTask(CyclePlan plan, List<LoopNetworkScan.PatternBinding> bindings) {
        if (plan == null || bindings == null || bindings.isEmpty()) {
            throw new IllegalArgumentException("A loop execution task requires a plan and provider bindings");
        }
        this.plan = plan;
        this.bindings = List.copyOf(bindings);
        this.batches = plan.schedule();
    }

    /**
     * @return the plan this task executes (for tests and diagnostics)
     */
    public CyclePlan plan() {
        return this.plan;
    }

    /**
     * @return true once every schedule batch has been pushed to a provider
     */
    public boolean isComplete() {
        return this.nextBatch >= this.batches.size();
    }

    /**
     * Advances the task by at most one schedule batch per invocation.
     *
     * @return true if a batch was pushed successfully (or the task is complete), false if the provider rejected it and
     *         the batch should be retried later
     */
    public boolean tick() {
        if (isComplete()) {
            return true;
        }
        var batch = this.batches.get(this.nextBatch);
        var bindingIndex = this.bindings.size() == 1 ? 0 : indexOfFiring(batch);
        if (bindingIndex < 0) {
            // A batch firing is not part of the bound cycle: treat as terminal failure by completing.
            this.nextBatch = this.batches.size();
            return true;
        }
        var binding = this.bindings.get(bindingIndex);
        if (binding.provider().isBusy()) {
            return false;
        }
        var inputHolder = buildInputHolder(binding, batch.count());
        if (!binding.provider().pushPattern(binding.pattern(), inputHolder)) {
            return false;
        }
        this.nextBatch++;
        return true;
    }

    private int indexOfFiring(LoopFiring batch) {
        for (int i = 0; i < this.bindings.size(); i++) {
            if (this.bindings.get(i).firing().equals(batch)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Builds the per-input-slot request for one batch: each pattern input slot receives the batch count times its
     * per-craft amount.
     */
    private static KeyCounter[] buildInputHolder(LoopNetworkScan.PatternBinding binding, BigInteger count) {
        IPatternDetails pattern = binding.pattern();
        var inputHolder = new KeyCounter[pattern.getInputs().length];
        long multiplier = saturate(count);
        for (int i = 0; i < inputHolder.length; i++) {
            var input = pattern.getInputs()[i];
            var first = input.getPossibleInputs()[0];
            var amounts = new KeyCounter();
            amounts.add(first.what(), saturate(BigInteger.valueOf(first.amount())
                    .multiply(BigInteger.valueOf(input.getMultiplier()))
                    .multiply(BigInteger.valueOf(multiplier))));
            inputHolder[i] = amounts;
        }
        return inputHolder;
    }

    private static long saturate(BigInteger amount) {
        if (amount.signum() <= 0) {
            return 1;
        }
        return amount.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
}

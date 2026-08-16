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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.crafting.cycle.CyclePatterns;
import appeng.crafting.cycle.CyclePlanResult;
import appeng.crafting.cycle.CycleQuantityMode;
import appeng.crafting.cycle.DeterministicCyclePlanner;
import appeng.crafting.cycle.LoopFiring;
import appeng.crafting.pattern.ProcessingPatternItem;
import appeng.crafting.pattern.TunnelPatternItem;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

/**
 * The ME Self-Loop Matrix: a computing device attached to an ME network. It holds up to nine loop patterns (processing
 * patterns whose outputs feed back into the loop), a demand target and amount, and computes an exact production plan:
 * per-cycle net change, minimum seed, repetitions, compressed batch schedule and any input shortages.
 */
public class SelfLoopMatrixBlockEntity extends AENetworkBlockEntity
        implements InternalInventoryHost, ServerTickingBlockEntity {

    public static final int NUM_PATTERN_SLOTS = 9;
    public static final int TARGET_SLOT = 0;
    public static final int MAX_SCHEDULE_STATES = 1024;

    /**
     * Plan status synced to the menu: 0 = empty (no patterns/target), 1 = success, 2 = failure.
     */
    public static final int STATUS_EMPTY = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILURE = 2;

    private final AppEngInternalInventory patterns = new AppEngInternalInventory(this, NUM_PATTERN_SLOTS);
    private final AppEngInternalInventory target = new AppEngInternalInventory(this, 1);

    private long requestedAmount = 1;
    private CycleQuantityMode quantityMode = CycleQuantityMode.NET_NEW;

    private int planStatus = STATUS_EMPTY;
    private String planSummary = "";
    private boolean needsRecompute = true;

    public SelfLoopMatrixBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
        this.getMainNode().setIdlePowerUsage(1.0);
    }

    @Override
    public void serverTick() {
        if (needsRecompute) {
            needsRecompute = false;
            recompute();
        }
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        this.patterns.writeToNBT(data, "patterns");
        this.target.writeToNBT(data, "target");
        data.putLong("requestedAmount", this.requestedAmount);
        data.putByte("quantityMode", (byte) this.quantityMode.ordinal());
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        this.patterns.readFromNBT(data, "patterns");
        this.target.readFromNBT(data, "target");
        if (data.contains("requestedAmount")) {
            this.requestedAmount = data.getLong("requestedAmount");
        }
        if (data.contains("quantityMode")) {
            this.quantityMode = CycleQuantityMode.values()[data.getByte("quantityMode")];
        }
        this.needsRecompute = true;
    }

    @Override
    public void onChangeInventory(InternalInventory inv, int slot) {
        this.needsRecompute = true;
        this.saveChanges();
    }

    public AppEngInternalInventory getPatterns() {
        return this.patterns;
    }

    public AppEngInternalInventory getTarget() {
        return this.target;
    }

    public long getRequestedAmount() {
        return this.requestedAmount;
    }

    public void setRequestedAmount(long requestedAmount) {
        this.requestedAmount = Math.max(1, requestedAmount);
        this.needsRecompute = true;
        this.saveChanges();
    }

    public CycleQuantityMode getQuantityMode() {
        return this.quantityMode;
    }

    public void setQuantityMode(CycleQuantityMode quantityMode) {
        this.quantityMode = quantityMode;
        this.needsRecompute = true;
        this.saveChanges();
    }

    public int getPlanStatus() {
        return this.planStatus;
    }

    public String getPlanSummary() {
        return this.planSummary;
    }

    /**
     * The most recently computed firing order (for tests and display).
     */
    public List<LoopFiring> getCycleOrder() {
        return this.cycleOrder;
    }

    private List<LoopFiring> cycleOrder = List.of();

    /**
     * Computes the current plan from the pattern slots, demand and network storage.
     */
    public void recompute() {
        var grid = getMainNode().getGrid();

        var firings = new ArrayList<LoopFiring>();
        for (int i = 0; i < this.patterns.size(); i++) {
            var stack = this.patterns.getStackInSlot(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof ProcessingPatternItem)) {
                continue;
            }
            var pattern = PatternDetailsHelper.decodePattern(stack, getLevel());
            if (pattern == null) {
                continue;
            }
            var firing = CyclePatterns.fromPattern(pattern, this::lookupTunnel);
            if (firing != null) {
                firings.add(firing);
            }
        }
        this.cycleOrder = List.copyOf(firings);

        var targetStack = this.target.getStackInSlot(TARGET_SLOT);
        if (firings.isEmpty() || targetStack.isEmpty()) {
            this.planStatus = STATUS_EMPTY;
            this.planSummary = firings.isEmpty() ? "未放置循环样板" : "请放置目标物品";
            return;
        }
        var targetKey = AEItemKey.of(targetStack);
        if (targetKey == null) {
            this.planStatus = STATUS_EMPTY;
            this.planSummary = "目标物品无效";
            return;
        }

        var planner = new DeterministicCyclePlanner();
        var result = planner.plan(
                this.cycleOrder,
                targetKey,
                BigInteger.valueOf(this.requestedAmount),
                this.quantityMode,
                availableAmounts(grid),
                java.util.Set.of(),
                MAX_SCHEDULE_STATES);
        this.planStatus = result.successful() ? STATUS_SUCCESS : STATUS_FAILURE;
        this.planSummary = summarize(result, targetKey);
    }

    private IPatternDetails lookupTunnel(UUID uuid) {
        // Tunnel patterns stored in the matrix itself take precedence.
        for (int i = 0; i < this.patterns.size(); i++) {
            var stack = this.patterns.getStackInSlot(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof TunnelPatternItem)) {
                continue;
            }
            if (uuid.equals(TunnelPatternItem.getTunnelUuid(stack))) {
                return PatternDetailsHelper.decodePattern(stack, getLevel());
            }
        }
        // Fall back to the network's crafting index (ME storage).
        var grid = getMainNode().getGrid();
        if (grid != null) {
            return grid.getCraftingService().getInputOnlyPattern(uuid);
        }
        return null;
    }

    private static Map<AEKey, BigInteger> availableAmounts(IGrid grid) {
        if (grid == null) {
            return Map.of();
        }
        var amounts = new java.util.LinkedHashMap<AEKey, BigInteger>();
        grid.getStorageService().getCachedInventory().forEach(
                entry -> amounts.put(entry.getKey(), BigInteger.valueOf(entry.getLongValue())));
        return java.util.Map.copyOf(amounts);
    }

    private String summarize(CyclePlanResult result, AEKey targetKey) {
        var builder = new StringBuilder();
        if (result.successful()) {
            var plan = result.plan();
            builder.append("循环计划：成功\n");
            builder.append("目标：").append(targetKey.getDisplayName())
                    .append(" x").append(this.requestedAmount)
                    .append(this.quantityMode == CycleQuantityMode.FINAL_TOTAL ? "（最终总量）" : "（净新增）")
                    .append("\n");
            builder.append("重复次数：").append(plan.repetitions()).append("\n");
            builder.append("每周期净变化：").append(formatAmounts(planNetChangePerCycle())).append("\n");
            builder.append("最小种子：").append(formatAmounts(plan.minimumSeed())).append("\n");
            builder.append("初始输入：").append(formatAmounts(plan.initialInputs())).append("\n");
            builder.append("总净变化：").append(formatSigned(plan.netChange())).append("\n");
            builder.append("调度批次：").append(plan.schedule().size())
                    .append("（").append(statesLabel(plan.schedule().size())).append("）\n");
        } else {
            var failure = result.failure();
            builder.append("循环计划：失败\n");
            switch (failure.code()) {
                case NO_PRODUCTIVE_CYCLE -> builder.append("原因：该循环对目标无净产出\n");
                case INSUFFICIENT_INPUT -> {
                    builder.append("原因：输入不足\n");
                    failure.missingInputs().forEach((key, requirement) -> builder
                            .append("缺失：").append(key.getDisplayName())
                            .append(" 需").append(requirement.required())
                            .append("，库存").append(requirement.available())
                            .append("，差").append(requirement.missing()).append("\n"));
                }
                case NO_EXECUTABLE_ORDER -> builder.append("原因：无可执行调度顺序\n");
                case SEARCH_LIMIT -> builder.append("原因：调度状态上限\n");
            }
        }
        return builder.toString();
    }

    private Map<AEKey, BigInteger> planNetChangePerCycle() {
        var net = new java.util.LinkedHashMap<AEKey, BigInteger>();
        for (var firing : this.cycleOrder) {
            firing.netChange()
                    .forEach((key, amount) -> net.merge(key, amount.multiply(firing.count()), BigInteger::add));
        }
        return java.util.Map.copyOf(net);
    }

    private static String formatAmounts(Map<AEKey, BigInteger> amounts) {
        var builder = new StringBuilder();
        amounts.forEach((key, amount) -> builder.append(key.getDisplayName()).append(" ").append(amount).append("，"));
        return builder.isEmpty() ? "无" : builder.substring(0, builder.length() - 1);
    }

    private static String formatSigned(Map<AEKey, BigInteger> amounts) {
        var builder = new StringBuilder();
        amounts.forEach((key, amount) -> builder.append(key.getDisplayName())
                .append(amount.signum() > 0 ? " +" : " ").append(amount).append("，"));
        return builder.isEmpty() ? "无" : builder.substring(0, builder.length() - 1);
    }

    private static String statesLabel(int batches) {
        return batches + " 个批次";
    }

    @Override
    public boolean isClientSide() {
        return level == null || level.isClientSide();
    }
}

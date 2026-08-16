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

package appeng.menu.implementations;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.blockentity.misc.SelfLoopMatrixBlockEntity;
import appeng.crafting.cycle.CycleQuantityMode;
import appeng.crafting.pattern.ProcessingPatternItem;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;

/**
 * Menu for the ME Self-Loop Matrix: nine loop pattern slots, a demand target slot, an amount field, a quantity-mode
 * toggle and the computed plan summary.
 */
public class SelfLoopMatrixMenu extends AEBaseMenu {

    public static final String ACTION_SET_AMOUNT = "setAmount";
    public static final String ACTION_TOGGLE_QUANTITY_MODE = "toggleQuantityMode";

    public static final MenuType<SelfLoopMatrixMenu> TYPE = MenuTypeBuilder
            .create(SelfLoopMatrixMenu::new, SelfLoopMatrixBlockEntity.class)
            .build("self_loop_matrix");

    private final SelfLoopMatrixBlockEntity matrix;

    @GuiSync(1)
    public int planStatus = SelfLoopMatrixBlockEntity.STATUS_EMPTY;

    @GuiSync(2)
    public String planSummary = "";

    @GuiSync(3)
    public long requestedAmount = 1;

    @GuiSync(4)
    public int quantityMode;

    public SelfLoopMatrixMenu(int id, Inventory ip, SelfLoopMatrixBlockEntity matrix) {
        super(TYPE, id, ip, matrix);
        this.matrix = matrix;

        for (int i = 0; i < SelfLoopMatrixBlockEntity.NUM_PATTERN_SLOTS; i++) {
            this.addSlot(new AppEngSlot(matrix.getPatterns(), i) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof ProcessingPatternItem;
                }
            }, SlotSemantics.ENCODED_PATTERN);
        }
        this.addSlot(new AppEngSlot(matrix.getTarget(), SelfLoopMatrixBlockEntity.TARGET_SLOT),
                SlotSemantics.STORAGE);

        this.createPlayerInventorySlots(ip);

        this.registerClientAction(ACTION_SET_AMOUNT, Long.class, this::setRequestedAmount);
        this.registerClientAction(ACTION_TOGGLE_QUANTITY_MODE, this::toggleQuantityMode);
    }

    @Override
    public void broadcastChanges() {
        this.planStatus = this.matrix.getPlanStatus();
        this.planSummary = this.matrix.getPlanSummary();
        this.requestedAmount = this.matrix.getRequestedAmount();
        this.quantityMode = this.matrix.getQuantityMode().ordinal();
        super.broadcastChanges();
    }

    public void setRequestedAmount(long amount) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_AMOUNT, amount);
            return;
        }
        this.matrix.setRequestedAmount(amount);
    }

    public void toggleQuantityMode() {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_QUANTITY_MODE);
            return;
        }
        var next = this.matrix.getQuantityMode() == CycleQuantityMode.NET_NEW
                ? CycleQuantityMode.FINAL_TOTAL
                : CycleQuantityMode.NET_NEW;
        this.matrix.setQuantityMode(next);
    }

    public boolean isFinalTotal() {
        return this.quantityMode == CycleQuantityMode.FINAL_TOTAL.ordinal();
    }

    public String getPlanSummary() {
        return this.planSummary;
    }

    public int getPlanStatus() {
        return this.planStatus;
    }

    public long getRequestedAmount() {
        return this.requestedAmount;
    }
}

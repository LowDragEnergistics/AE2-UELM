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

package appeng.client.gui.implementations;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.NumberEntryType;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.menu.implementations.SelfLoopMatrixMenu;

/**
 * Screen for the ME Self-Loop Matrix computing device: loop pattern slots, a demand target slot, an amount field, a
 * quantity-mode toggle and the computed plan summary.
 */
public class SelfLoopMatrixScreen extends AEBaseScreen<SelfLoopMatrixMenu> {

    private final NumberEntryWidget amount;
    private final Button quantityModeButton;

    public SelfLoopMatrixScreen(SelfLoopMatrixMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);

        this.amount = widgets.addNumberEntryWidget("amount", NumberEntryType.UNITLESS);
        this.amount.setTextFieldStyle(style.getWidget("amountInput"));
        this.amount.setLongValue(menu.getRequestedAmount());
        this.amount.setOnChange(this::saveAmount);
        this.amount.setOnConfirm(this::onClose);

        this.quantityModeButton = new Button.Builder(Component.literal(""), this::toggleQuantityMode)
                .bounds(116, 71, 52, 14)
                .build();
        this.addRenderableWidget(this.quantityModeButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.quantityModeButton.setMessage(Component.literal(menu.isFinalTotal() ? "FINAL" : "NET_NEW"));
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        var lines = this.menu.getPlanSummary().split("\n");
        int y = 92;
        for (var line : lines) {
            if (y > 132) {
                break;
            }
            guiGraphics.drawString(this.font, line, offsetX + 8, offsetY + y, 0xFFFFFFFF, false);
            y += 9;
        }
    }

    private void saveAmount() {
        this.amount.getLongValue().ifPresent(menu::setRequestedAmount);
    }

    private void toggleQuantityMode(Button button) {
        this.menu.toggleQuantityMode();
    }
}

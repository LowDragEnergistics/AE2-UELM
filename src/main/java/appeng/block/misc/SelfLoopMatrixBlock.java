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

package appeng.block.misc;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.misc.SelfLoopMatrixBlockEntity;
import appeng.core.definitions.AEBlockEntities;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.SelfLoopMatrixMenu;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;

/**
 * The ME Self-Loop Matrix: a computing device attached to an ME network that computes exact production plans for
 * self-loop (self-multiplying) crafting recipes.
 */
public class SelfLoopMatrixBlock extends AEBaseEntityBlock<SelfLoopMatrixBlockEntity> {

    public SelfLoopMatrixBlock() {
        super(metalProps());
    }

    @Override
    public InteractionResult onActivated(Level level, BlockPos pos, Player player, InteractionHand hand,
            @Nullable ItemStack heldItem, BlockHitResult hit) {
        var be = this.getBlockEntity(level, pos);
        if (be != null) {

            if (!InteractionUtil.isInAlternateUseMode(player)) {
                if (!level.isClientSide()) {
                    MenuOpener.open(SelfLoopMatrixMenu.TYPE, player, MenuLocators.forBlockEntity(be));
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
        }

        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SelfLoopMatrixBlockEntity(AEBlockEntities.SELF_LOOP_MATRIX, pos, state);
    }
}

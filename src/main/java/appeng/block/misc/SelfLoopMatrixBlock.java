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

/**
 * The ME Self-Loop Matrix: a computing device attached to an ME network that automatically identifies self-loop
 * (self-multiplying) crafting recipes from the network's pattern providers and takes them over as an
 * {@link appeng.api.networking.crafting.ICraftingProvider}. It has no GUI.
 */
public class SelfLoopMatrixBlock extends AEBaseEntityBlock<SelfLoopMatrixBlockEntity> {

    public SelfLoopMatrixBlock() {
        super(metalProps());
    }

    @Override
    public InteractionResult onActivated(Level level, BlockPos pos, Player player, InteractionHand hand,
            @Nullable ItemStack heldItem, BlockHitResult hit) {
        // The matrix has no GUI: it acts purely as a network computing device.
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SelfLoopMatrixBlockEntity(AEBlockEntities.SELF_LOOP_MATRIX, pos, state);
    }
}

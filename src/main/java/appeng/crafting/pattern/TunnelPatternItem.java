/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.PatternInfo;
import appeng.api.stacks.GenericStack;

/**
 * An item that contains an encoded input-only "tunnel" pattern.
 * <p>
 * Tunnel patterns are used as inputs when encoding processing patterns. When a referencing pattern is crafted, the
 * tunnel pattern's inputs are inlined (multiplied by the referenced stack size) instead of the tunnel pattern item
 * itself. Tunnel patterns are indexed by their UUID, so their contents can be changed or renamed without updating the
 * patterns that reference them.
 */
public class TunnelPatternItem extends ProcessingPatternItem {

    public static final String TAG_TUNNEL = "tunnel";
    public static final String TAG_TUNNEL_UUID = "tunnelUuid";

    public TunnelPatternItem(Properties properties) {
        super(properties);
    }

    /**
     * @return true if the given stack is a tunnel pattern.
     */
    public static boolean isTunnelPattern(ItemStack stack) {
        return stack != null && stack.getItem() instanceof TunnelPatternItem;
    }

    /**
     * @return the UUID of this tunnel pattern, or null if the stack is not a valid tunnel pattern.
     */
    @Nullable
    public static UUID getTunnelUuid(ItemStack stack) {
        if (!isTunnelPattern(stack)) {
            return null;
        }
        var tag = stack.getTag();
        if (tag == null) {
            return null;
        }
        if (!tag.getBoolean(TAG_TUNNEL)) {
            return null;
        }
        String rawUuid = tag.getString(TAG_TUNNEL_UUID);
        if (rawUuid == null || rawUuid.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(rawUuid);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Writes the tunnel pattern marker and UUID to the given tag.
     */
    public static void writeTunnelUuid(CompoundTag tag, UUID uuid) {
        tag.putBoolean(TAG_TUNNEL, true);
        tag.putString(TAG_TUNNEL_UUID, uuid.toString());
    }

    /**
     * Encodes an input-only tunnel pattern with the given inputs and UUID.
     *
     * @throws IllegalArgumentException If sparseInputs contains only null/empty stacks.
     */
    public ItemStack encodeTunnelPattern(GenericStack[] sparseInputs, UUID uuid, PatternInfo info) {
        if (Arrays.stream(sparseInputs).noneMatch(Objects::nonNull)) {
            throw new IllegalArgumentException("At least one input must be non-null.");
        }

        var stack = new ItemStack(this);
        ProcessingPatternEncoding.encodeTunnelPattern(stack.getOrCreateTag(), sparseInputs, uuid, info);
        return stack;
    }
}

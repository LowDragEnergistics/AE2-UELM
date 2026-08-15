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

import java.util.Objects;
import java.util.UUID;

import com.google.common.base.Preconditions;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.crafting.PatternInfo;
import appeng.api.stacks.GenericStack;

/**
 * Helper functions to work with patterns, mostly related to (de)serialization.
 */
class ProcessingPatternEncoding {
    private static final String NBT_INPUTS = "in";
    private static final String NBT_OUTPUTS = "out";
    private static final String NBT_AUTHOR = "author";

    public static GenericStack[] getProcessingInputs(CompoundTag nbt) {
        return getMixedList(nbt, NBT_INPUTS, AEProcessingPattern.MAX_INPUT_SLOTS);
    }

    public static GenericStack[] getProcessingOutputs(CompoundTag nbt) {
        return getMixedList(nbt, NBT_OUTPUTS, AEProcessingPattern.MAX_OUTPUT_SLOTS);
    }

    public static String getAuthor(CompoundTag nbt) {
        Objects.requireNonNull(nbt, "Pattern must have a tag.");

        return nbt.getString(NBT_AUTHOR);
    }

    public static GenericStack[] getMixedList(CompoundTag nbt, String nbtKey, int maxSize) {
        Objects.requireNonNull(nbt, "Pattern must have a tag.");

        ListTag tag = nbt.getList(nbtKey, Tag.TAG_COMPOUND);
        Preconditions.checkArgument(tag.size() <= maxSize, "Cannot use more than " + maxSize + " ingredients");

        var result = new GenericStack[tag.size()];
        for (int x = 0; x < tag.size(); ++x) {
            var entry = tag.getCompound(x);
            if (entry.isEmpty()) {
                continue;
            }
            var stack = GenericStack.readTag(entry);
            if (stack == null) {
                throw new IllegalArgumentException("Pattern references missing stack: " + entry);
            }
            result[x] = stack;
        }
        return result;
    }

    @Deprecated
    public static void encodeProcessingPattern(CompoundTag tag, GenericStack[] sparseInputs,
            GenericStack[] sparseOutputs) {
        encodeProcessingPattern(tag, sparseInputs, sparseOutputs, PatternInfo.EMPTY);
    }

    public static void encodeProcessingPattern(CompoundTag tag, GenericStack[] sparseInputs,
            GenericStack[] sparseOutputs, PatternInfo info) {
        info = info == null ? PatternInfo.EMPTY : info;

        tag.put(NBT_INPUTS, encodeStackList(sparseInputs));
        tag.put(NBT_OUTPUTS, encodeStackList(sparseOutputs));
        tag.putString(NBT_AUTHOR, info.author());
    }

    /**
     * Encodes an input-only "tunnel" pattern: it has inputs but no outputs, and is identified by a UUID.
     */
    public static void encodeTunnelPattern(CompoundTag tag, GenericStack[] sparseInputs, UUID uuid, PatternInfo info) {
        info = info == null ? PatternInfo.EMPTY : info;

        tag.put(NBT_INPUTS, encodeStackList(sparseInputs));
        tag.put(NBT_OUTPUTS, new ListTag());
        tag.putString(NBT_AUTHOR, info.author());
        TunnelPatternItem.writeTunnelUuid(tag, uuid);
    }

    /**
     * @return true if the given tag belongs to an input-only "tunnel" pattern.
     */
    public static boolean isInputOnly(CompoundTag nbt) {
        Objects.requireNonNull(nbt, "Pattern must have a tag.");
        return nbt.getBoolean(TunnelPatternItem.TAG_TUNNEL);
    }

    /**
     * @return the tunnel pattern UUID stored in the given tag, or null if the tag is not a valid tunnel pattern.
     */
    @Nullable
    public static UUID getTunnelUuid(CompoundTag nbt) {
        if (!isInputOnly(nbt)) {
            return null;
        }
        String rawUuid = nbt.getString(TunnelPatternItem.TAG_TUNNEL_UUID);
        if (rawUuid.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(rawUuid);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static ListTag encodeStackList(GenericStack[] stacks) {
        ListTag tag = new ListTag();
        boolean foundStack = false;
        for (var stack : stacks) {
            tag.add(GenericStack.writeTag(stack));
            if (stack != null && stack.amount() > 0) {
                foundStack = true;
            }
        }
        Preconditions.checkArgument(foundStack, "List passed to pattern must contain at least one stack.");
        return tag;
    }

}

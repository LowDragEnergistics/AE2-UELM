package appeng.crafting.pattern;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetails.IInput;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;

/**
 * Test helpers for building tunnel patterns and tunnel references.
 */
public final class TunnelPatternTestHelper {

    private TunnelPatternTestHelper() {
    }

    /**
     * Builds an input-only pattern with the given UUID and inputs.
     */
    public static IPatternDetails inputOnly(UUID uuid, IInput... inputs) {
        return new IPatternDetails() {
            @Override
            public AEItemKey getDefinition() {
                throw new UnsupportedOperationException();
            }

            @Override
            public IInput[] getInputs() {
                return inputs;
            }

            @Override
            public GenericStack[] getOutputs() {
                return new GenericStack[0];
            }

            @Override
            public boolean isInputOnly() {
                return true;
            }

            @Override
            public UUID getInputOnlyUuid() {
                return uuid;
            }
        };
    }

    /**
     * An exact input with the given multiplier.
     */
    public static IInput input(long multiplier, GenericStack stack) {
        return new IInput() {
            @Override
            public GenericStack[] getPossibleInputs() {
                return new GenericStack[] { stack };
            }

            @Override
            public long getMultiplier() {
                return multiplier;
            }

            @Override
            public boolean isValid(AEKey input, net.minecraft.world.level.Level level) {
                return input.equals(stack.what());
            }

            @Override
            public AEKey getRemainingKey(AEKey template) {
                return null;
            }
        };
    }

    /**
     * A tunnel pattern item with the given UUID.
     */
    public static ItemStack referenceItem(UUID uuid) {
        var stack = new ItemStack(AEItems.TUNNEL_PATTERN);
        var tag = new CompoundTag();
        TunnelPatternItem.writeTunnelUuid(tag, uuid);
        stack.setTag(tag);
        return stack;
    }

    /**
     * A tunnel pattern reference as a pattern input, with the given count.
     */
    public static GenericStack reference(UUID uuid, long count) {
        return new GenericStack(AEItemKey.of(referenceItem(uuid)), count);
    }
}

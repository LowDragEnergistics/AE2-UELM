package appeng.crafting.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetails.IInput;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.util.BootstrapMinecraft;

/**
 * Pure-function tests for {@link TunnelPatternExpander}.
 */
@BootstrapMinecraft
class TunnelPatternExpanderTest {

    private static final GenericStack STICK = GenericStack.fromItemStack(new ItemStack(Items.STICK));
    private static final GenericStack TORCH = GenericStack.fromItemStack(new ItemStack(Items.TORCH));

    @Test
    void testNonTunnelInputsUnchanged() {
        var input = TunnelPatternTestHelper.input(1, STICK);
        var expanded = TunnelPatternExpander.expandInputs(new IInput[] { input }, uuid -> null, null);

        assertNotNull(expanded);
        assertEquals(1, expanded.size());
        assertSame(input, expanded.get(0));
    }

    @Test
    void testMultiLevelExpansion() {
        var t2 = UUID.randomUUID();
        var t1 = UUID.randomUUID();
        // T2: 2 sticks. T1: 1x T2.
        Map<UUID, IPatternDetails> tunnels = Map.of(
                t2, TunnelPatternTestHelper.inputOnly(t2, TunnelPatternTestHelper.input(2, STICK)),
                t1, TunnelPatternTestHelper.inputOnly(t1, ref(t2, 1, 1)));

        var expanded = TunnelPatternExpander.expandInputs(new IInput[] { ref(t1, 1, 1) }, tunnels::get, null);

        assertNotNull(expanded);
        assertEquals(1, expanded.size());
        assertEquals(AEItemKey.of(Items.STICK), expanded.get(0).getPossibleInputs()[0].what());
        assertEquals(2, consumedPerCraft(expanded.get(0)));
    }

    @Test
    void testMultiplierAppliedToTargetMultiplier() {
        var t = UUID.randomUUID();
        // T: 2 sticks per craft (multiplier 2). Reference 3x tunnel T.
        Map<UUID, IPatternDetails> tunnels = Map.of(t,
                TunnelPatternTestHelper.inputOnly(t, TunnelPatternTestHelper.input(2, STICK)));

        var expanded = TunnelPatternExpander.expandInputs(new IInput[] { ref(t, 1, 3) }, tunnels::get, null);

        assertNotNull(expanded);
        assertEquals(1, expanded.size());
        // 2 sticks per craft * 3 references = 6 sticks.
        assertEquals(6, consumedPerCraft(expanded.get(0)));
    }

    @Test
    void testCycleRejected() {
        var t = UUID.randomUUID();
        // T: 1x T (self-reference).
        Map<UUID, IPatternDetails> tunnels = Map.of(t,
                TunnelPatternTestHelper.inputOnly(t, ref(t, 1, 1)));

        assertNull(TunnelPatternExpander.expandInputs(new IInput[] { ref(t, 1, 1) }, tunnels::get, null));
    }

    @Test
    void testIndirectCycleRejected() {
        var t1 = UUID.randomUUID();
        var t2 = UUID.randomUUID();
        // T1 -> T2 -> T1.
        Map<UUID, IPatternDetails> tunnels = Map.of(
                t1, TunnelPatternTestHelper.inputOnly(t1, ref(t2, 1, 1)),
                t2, TunnelPatternTestHelper.inputOnly(t2, ref(t1, 1, 1)));

        assertNull(TunnelPatternExpander.expandInputs(new IInput[] { ref(t1, 1, 1) }, tunnels::get, null));
    }

    @Test
    void testOverflowRejected() {
        var t = UUID.randomUUID();
        // T: 2 sticks per craft; reference count Long.MAX_VALUE -> the multiplied amount overflows.
        Map<UUID, IPatternDetails> tunnels = Map.of(t,
                TunnelPatternTestHelper.inputOnly(t, TunnelPatternTestHelper.input(2, STICK)));

        assertNull(TunnelPatternExpander.expandInputs(new IInput[] { ref(t, Long.MAX_VALUE, 1) }, tunnels::get,
                null));
    }

    @Test
    void testMissingTargetKeepsInput() {
        var uuid = UUID.randomUUID();
        var input = ref(uuid, 1, 1);

        var expanded = TunnelPatternExpander.expandInputs(new IInput[] { input }, uuid2 -> null, null);

        assertNotNull(expanded);
        assertEquals(1, expanded.size());
        assertSame(input, expanded.get(0));
    }

    @Test
    void testNonInputOnlyTargetKeepsInput() {
        var uuid = UUID.randomUUID();
        // A target that exists but is not input-only: the reference is kept as-is.
        var notInputOnly = new IPatternDetails() {
            @Override
            public AEItemKey getDefinition() {
                throw new UnsupportedOperationException();
            }

            @Override
            public IInput[] getInputs() {
                return new IInput[] { TunnelPatternTestHelper.input(1, STICK) };
            }

            @Override
            public GenericStack[] getOutputs() {
                return new GenericStack[] { TORCH };
            }
        };
        var input = ref(uuid, 1, 1);

        var expanded = TunnelPatternExpander.expandInputs(new IInput[] { input }, uuid2 -> notInputOnly, null);

        assertNotNull(expanded);
        assertEquals(1, expanded.size());
        assertSame(input, expanded.get(0));
    }

    @Test
    void testMalformedTunnelReferenceFails() {
        // A tunnel pattern item with an invalid UUID is a malformed reference.
        var stack = new ItemStack(AEItems.TUNNEL_PATTERN);
        var tag = new CompoundTag();
        tag.putBoolean(TunnelPatternItem.TAG_TUNNEL, true);
        tag.putString(TunnelPatternItem.TAG_TUNNEL_UUID, "not-a-uuid");
        stack.setTag(tag);
        var input = TunnelPatternTestHelper.input(1, new GenericStack(AEItemKey.of(stack), 1));

        assertNull(TunnelPatternExpander.expandInputs(new IInput[] { input }, uuid -> null, null));
    }

    @Test
    void testParentPatternCycleGuard() {
        var t = UUID.randomUUID();
        Map<UUID, IPatternDetails> tunnels = Map.of(t,
                TunnelPatternTestHelper.inputOnly(t, TunnelPatternTestHelper.input(1, STICK)));
        var target = tunnels.get(t);

        // If the target is already in the parent pattern chain, expansion fails.
        assertNull(TunnelPatternExpander.expandInputs(new IInput[] { ref(t, 1, 1) }, tunnels::get,
                java.util.Set.of(target)));
    }

    private static IInput ref(UUID uuid, long count, long multiplier) {
        return TunnelPatternTestHelper.input(multiplier, TunnelPatternTestHelper.reference(uuid, count));
    }

    private static long consumedPerCraft(IInput input) {
        var first = input.getPossibleInputs()[0];
        return first.amount() * input.getMultiplier();
    }
}

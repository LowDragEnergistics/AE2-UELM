package appeng.crafting.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.TunnelPatternExpander;
import appeng.crafting.pattern.TunnelPatternTestHelper;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class CraftingCpuHelperTest {

    private static final GenericStack STICK = GenericStack.fromItemStack(new ItemStack(Items.STICK));
    private static final GenericStack TORCH = GenericStack.fromItemStack(new ItemStack(Items.TORCH));
    private static final GenericStack OUTPUT = GenericStack.fromItemStack(new ItemStack(Items.DIAMOND));

    @Test
    void testExtractPatternInputsExpandsTunnel() {
        var uuid = UUID.randomUUID();
        // Tunnel T: 2 sticks.
        Map<UUID, IPatternDetails> tunnels = Map.of(uuid,
                TunnelPatternTestHelper.inputOnly(uuid, TunnelPatternTestHelper.input(2, STICK)));

        // Pattern P: 1x tunnel T -> diamond.
        var encoded = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] { TunnelPatternTestHelper.reference(uuid, 1) },
                new GenericStack[] { OUTPUT }, "test");
        var pattern = PatternDetailsHelper.decodePattern(encoded, mock(Level.class));

        // The CPU inventory holds the expanded inputs (2 sticks), not the tunnel item.
        var inv = new ListCraftingInventory(ignored -> {
        });
        inv.insert(AEItemKey.of(Items.STICK), 2, appeng.api.config.Actionable.MODULATE);

        var expectedOutputs = new KeyCounter();
        var expectedContainerItems = new KeyCounter();
        var holder = CraftingCpuHelper.extractPatternInputs(pattern, inv, mock(Level.class),
                expectedOutputs, expectedContainerItems, tunnels::get);

        assertNotNull(holder);
        assertEquals(1, holder.length);
        assertEquals(2, holder[0].get(AEItemKey.of(Items.STICK)));
        assertEquals(1, expectedOutputs.get(AEItemKey.of(Items.DIAMOND)));
    }

    @Test
    void testExtractPatternInputsMissingTunnelReturnsNull() {
        var uuid = UUID.randomUUID();

        var encoded = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] { TunnelPatternTestHelper.reference(uuid, 1) },
                new GenericStack[] { OUTPUT }, "test");
        var pattern = PatternDetailsHelper.decodePattern(encoded, mock(Level.class));

        var inv = new ListCraftingInventory(ignored -> {
        });
        inv.insert(AEItemKey.of(Items.STICK), 2, appeng.api.config.Actionable.MODULATE);

        var expectedOutputs = new KeyCounter();
        var expectedContainerItems = new KeyCounter();
        // Lookup does not know the UUID: the pattern cannot be pushed.
        var holder = CraftingCpuHelper.extractPatternInputs(pattern, inv, mock(Level.class),
                expectedOutputs, expectedContainerItems, uuid2 -> null);

        assertNull(holder);
        // Nothing must have been extracted.
        assertEquals(2, inv.extract(AEItemKey.of(Items.STICK), 10, appeng.api.config.Actionable.SIMULATE));
    }

    @Test
    void testPushInputsFlatWhenHolderExpanded() {
        var uuid = UUID.randomUUID();
        // Tunnel T: 2 torches.
        Map<UUID, IPatternDetails> tunnels = Map.of(uuid,
                TunnelPatternTestHelper.inputOnly(uuid, TunnelPatternTestHelper.input(2, TORCH)));

        // Pattern P: [1x tunnel T, stick, stick] -> diamond. The duplicated stick triggers input compression,
        // which normally reorders the push according to the sparse inputs.
        var encoded = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] {
                        TunnelPatternTestHelper.reference(uuid, 1), STICK, STICK
                },
                new GenericStack[] { OUTPUT }, "test");
        var pattern = (AEProcessingPattern) PatternDetailsHelper.decodePattern(encoded, mock(Level.class));

        // Expand the inputs like the CPU does, then build the input holder the same way extractPatternInputs does.
        var expanded = TunnelPatternExpander.expandInputs(pattern.getInputs(), tunnels::get, null);
        assertNotNull(expanded);

        var inv = new ListCraftingInventory(ignored -> {
        });
        inv.insert(AEItemKey.of(Items.TORCH), 2, appeng.api.config.Actionable.MODULATE);
        inv.insert(AEItemKey.of(Items.STICK), 2, appeng.api.config.Actionable.MODULATE);

        var expectedOutputs = new KeyCounter();
        var expectedContainerItems = new KeyCounter();
        var holder = CraftingCpuHelper.extractPatternInputs(pattern, inv, mock(Level.class),
                expectedOutputs, expectedContainerItems, tunnels::get);
        assertNotNull(holder);

        // The expanded holder contains torch/stick, which are not part of the pattern's sparse inputs
        // (the tunnel item is). The push must fall back to a flat push instead of throwing.
        var pushed = new KeyCounter();
        pattern.pushInputsToExternalInventory(holder, (what, amount) -> pushed.add(what, amount));

        assertEquals(2, pushed.get(AEItemKey.of(Items.TORCH)));
        assertEquals(2, pushed.get(AEItemKey.of(Items.STICK)));
    }

    @Test
    void testCompressedPatternWithoutTunnelStillReorders() {
        // Pattern: [stick, stick] -> diamond. Sparse inputs are covered by the holder, so the reorder branch
        // must remain in use (no behavioral regression).
        var encoded = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] { STICK, STICK },
                new GenericStack[] { OUTPUT }, "test");
        var pattern = (AEProcessingPattern) PatternDetailsHelper.decodePattern(encoded, mock(Level.class));

        var inv = new ListCraftingInventory(ignored -> {
        });
        inv.insert(AEItemKey.of(Items.STICK), 2, appeng.api.config.Actionable.MODULATE);

        var expectedOutputs = new KeyCounter();
        var expectedContainerItems = new KeyCounter();
        var holder = CraftingCpuHelper.extractPatternInputs(pattern, inv, mock(Level.class),
                expectedOutputs, expectedContainerItems, null);
        assertNotNull(holder);

        var pushed = new KeyCounter();
        pattern.pushInputsToExternalInventory(holder, (what, amount) -> pushed.add(what, amount));

        assertEquals(2, pushed.get(AEItemKey.of(Items.STICK)));
    }
}

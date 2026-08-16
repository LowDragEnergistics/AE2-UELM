package appeng.blockentity.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlockEntities;
import appeng.util.BootstrapMinecraft;

/**
 * Behavior tests for the ME Self-Loop Matrix block entity: inventory handling, plan computation and tunnel-pattern
 * integration.
 */
@BootstrapMinecraft
class SelfLoopMatrixBlockEntityTest {

    private static SelfLoopMatrixBlockEntity newMatrix(Level level) {
        var be = AEBlockEntities.SELF_LOOP_MATRIX.create(new BlockPos(0, 0, 0), Blocks.AIR.defaultBlockState());
        assertNotNull(be);
        be.setLevel(level);
        return be;
    }

    private static ItemStack loopPattern() {
        // A -> 2 A (a self-multiplying loop).
        return PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] { new GenericStack(AEItemKey.of(Items.DIAMOND), 1) },
                new GenericStack[] { new GenericStack(AEItemKey.of(Items.DIAMOND), 2) },
                "tester");
    }

    @Test
    void testInventoryRoundTrip() {
        var level = mock(Level.class);
        var be = newMatrix(level);

        be.getPatterns().setItemDirect(0, loopPattern());
        be.getTarget().setItemDirect(SelfLoopMatrixBlockEntity.TARGET_SLOT, new ItemStack(Items.DIAMOND));
        be.setRequestedAmount(5);

        var tag = new CompoundTag();
        be.saveAdditional(tag);

        var restored = newMatrix(level);
        restored.loadTag(tag);
        assertTrue(ItemStack.isSameItemSameTags(be.getPatterns().getStackInSlot(0),
                restored.getPatterns().getStackInSlot(0)));
        assertTrue(ItemStack.isSameItemSameTags(be.getTarget().getStackInSlot(0),
                restored.getTarget().getStackInSlot(0)));
        assertEquals(5, restored.getRequestedAmount());
    }

    @Test
    void testRecomputeComputesCycleOrderAndSummary() {
        var level = mock(Level.class);
        var be = newMatrix(level);

        be.getPatterns().setItemDirect(0, loopPattern());
        be.getTarget().setItemDirect(SelfLoopMatrixBlockEntity.TARGET_SLOT, new ItemStack(Items.DIAMOND));

        be.recompute();

        // A -> 2 A: one firing in the cycle order.
        assertEquals(1, be.getCycleOrder().size());
        // Without a connected grid the storage is empty, so the 1 A seed is reported as missing.
        assertEquals(SelfLoopMatrixBlockEntity.STATUS_FAILURE, be.getPlanStatus());
        assertTrue(be.getPlanSummary().contains("输入不足"), be.getPlanSummary());
    }

    @Test
    void testRecomputeEmptyState() {
        var level = mock(Level.class);
        var be = newMatrix(level);

        be.recompute();
        assertEquals(SelfLoopMatrixBlockEntity.STATUS_EMPTY, be.getPlanStatus());
        assertTrue(be.getPlanSummary().contains("循环样板"), be.getPlanSummary());

        be.getPatterns().setItemDirect(0, loopPattern());
        be.recompute();
        assertEquals(SelfLoopMatrixBlockEntity.STATUS_EMPTY, be.getPlanStatus());
        assertTrue(be.getPlanSummary().contains("目标"), be.getPlanSummary());
    }

    @Test
    void testTunnelReferencePatternRejectedWhenUnresolved() {
        var level = mock(Level.class);
        var be = newMatrix(level);

        // A processing pattern whose input is an unresolvable tunnel reference cannot be used for
        // cycle computation and must be skipped.
        var uuid = java.util.UUID.randomUUID();
        var referencing = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] { appeng.crafting.pattern.TunnelPatternTestHelper.reference(uuid, 1) },
                new GenericStack[] { new GenericStack(AEItemKey.of(Items.TORCH), 2) },
                "tester");

        be.getPatterns().setItemDirect(0, referencing);
        be.getTarget().setItemDirect(SelfLoopMatrixBlockEntity.TARGET_SLOT, new ItemStack(Items.TORCH));

        be.recompute();

        // Unresolved tunnel reference -> no usable firing -> empty state.
        assertEquals(0, be.getCycleOrder().size());
        assertEquals(SelfLoopMatrixBlockEntity.STATUS_EMPTY, be.getPlanStatus());
    }
}

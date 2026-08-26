package appeng.blockentity.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.block.misc.SelfLoopMatrixBlock;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.cycle.CyclePlan;
import appeng.crafting.cycle.CycleQuantityMode;
import appeng.crafting.cycle.DeterministicCyclePlanner;
import appeng.crafting.cycle.LoopFiring;
import appeng.util.BootstrapMinecraft;

/**
 * Registration and takeover behavior of the GUI-less ME Self-Loop Matrix.
 */
@BootstrapMinecraft
class SelfLoopMatrixRegistrationTest {

    private static final AEItemKey A = AEItemKey.of(Items.DIAMOND);
    private static final AEItemKey B = AEItemKey.of(Items.EMERALD);

    @Test
    void testBlockRegistered() {
        assertTrue(AEBlocks.getBlocks().contains(AEBlocks.SELF_LOOP_MATRIX));
        assertInstanceOf(SelfLoopMatrixBlock.class, AEBlocks.SELF_LOOP_MATRIX.block());
    }

    @Test
    void testBlockEntityRegisteredAndProvidesCraftingService() {
        BlockEntityType<?> type = AEBlockEntities.SELF_LOOP_MATRIX;
        assertNotNull(type);
        var be = type.create(new BlockPos(0, 0, 0), Blocks.AIR.defaultBlockState());
        assertInstanceOf(SelfLoopMatrixBlockEntity.class, be);
        // The device is a crafting provider without a GUI.
        assertInstanceOf(ICraftingProvider.class, be);
    }

    @Test
    void testNoGuiLeftBehind() throws IOException {
        for (var lang : List.of("en_us", "zh_cn")) {
            String content;
            try (InputStream in = SelfLoopMatrixRegistrationTest.class
                    .getResourceAsStream("/assets/ae2/lang/" + lang + ".json")) {
                assertNotNull(in, lang);
                content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertTrue(content.contains("\"block.ae2.self_loop_matrix\""), "Missing block name in " + lang);
            // The GUI translation keys must be gone.
            assertFalse(content.contains("\"gui.ae2.SelfLoopMatrix\""), "Gui title key still present in " + lang);
        }
    }

    @Test
    void testMaxPlanScalesWithAvailableSeed() {
        var firing = new LoopFiring(
                Map.<AEKey, BigInteger>of(A, BigInteger.ONE, B, BigInteger.ONE),
                Map.<AEKey, BigInteger>of(A, BigInteger.valueOf(2)),
                BigInteger.ONE);

        // With only 1 A and 10 B available, at most 10 cycles can be seeded.
        var plan = SelfLoopMatrixBlockEntity.maxPlan(
                List.of(firing), A, Map.of(A, BigInteger.ONE, B, BigInteger.TEN));
        assertNotNull(plan);
        assertEquals(BigInteger.TEN, plan.repetitions());

        // Without the seed, no plan is possible.
        assertNull(SelfLoopMatrixBlockEntity.maxPlan(
                List.of(firing), A, Map.of(A, BigInteger.ZERO, B, BigInteger.TEN)));
    }

    @Test
    void testMaxPlanLargerSeedAllowsMoreCycles() {
        var firing = new LoopFiring(
                Map.<AEKey, BigInteger>of(A, BigInteger.ONE, B, BigInteger.ONE),
                Map.<AEKey, BigInteger>of(A, BigInteger.valueOf(2)),
                BigInteger.ONE);
        var plan = SelfLoopMatrixBlockEntity.maxPlan(
                List.of(firing), A, Map.of(A, BigInteger.ONE, B, BigInteger.valueOf(1000)));
        assertNotNull(plan);
        assertEquals(BigInteger.valueOf(1000), plan.repetitions());
    }

    @Test
    void testEmptyCyclePlanConservation() {
        // Sanity: the engine-level plan must conserve its balances for the takeover flow.
        var firing = new LoopFiring(
                Map.<AEKey, BigInteger>of(A, BigInteger.ONE, B, BigInteger.ONE),
                Map.<AEKey, BigInteger>of(A, BigInteger.valueOf(2)),
                BigInteger.ONE);
        var result = new DeterministicCyclePlanner().plan(
                List.of(firing), A, BigInteger.TEN, CycleQuantityMode.NET_NEW,
                Map.of(A, BigInteger.ONE, B, BigInteger.TEN),
                java.util.Set.of(), 128);
        assertTrue(result.successful());
        CyclePlan plan = result.plan();
        var finalBalances = CyclePlan.simulateSchedule(plan.initialInputs(), plan.schedule());
        assertEquals(Map.of(A, BigInteger.valueOf(11)), finalBalances);

        var counter = new KeyCounter();
        counter.add(A, 11);
        assertEquals(11, counter.get(A));
    }
}

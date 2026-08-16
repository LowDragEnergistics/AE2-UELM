package appeng.crafting.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.util.BootstrapMinecraft;

/**
 * Pure math tests for the deterministic cycle planner: single self-loops, multi-step cycles, quantity modes, rejections
 * and schedule conservation.
 */
@BootstrapMinecraft
class DeterministicCyclePlannerTest {

    private static final AEItemKey A = AEItemKey.of(Items.DIAMOND);
    private static final AEItemKey B = AEItemKey.of(Items.EMERALD);
    private static final AEItemKey C = AEItemKey.of(Items.REDSTONE);
    private static final AEItemKey W = AEItemKey.of(Items.LAPIS_LAZULI);

    private static BigInteger n(long v) {
        return BigInteger.valueOf(v);
    }

    // Small typed alias to keep call sites readable.
    private record AEKeyMap(AEItemKey key, long amount) {
        static Map<appeng.api.stacks.AEKey, BigInteger> of(AEItemKey key, long amount) {
            return java.util.Map.of(key, n(amount));
        }
    }

    /**
     * Single self-loop: 1 A + 1 B -> 2 A. Per cycle net: A +1, B -1. For 10 net A with no stock: repetitions 10, seed
     * {A:1, B:10}, net {A:+10, B:-10}, schedule conserves final {A:11}.
     */
    @Test
    void testSingleSelfLoopNetNew() {
        var firing = new LoopFiring(
                Map.<appeng.api.stacks.AEKey, BigInteger>of(A, n(1), B, n(1)),
                AEKeyMap.of(A, 2),
                BigInteger.ONE);
        var planner = new DeterministicCyclePlanner();

        var result = planner.plan(
                List.of(firing),
                A,
                n(10),
                CycleQuantityMode.NET_NEW,
                Map.of(A, n(1), B, n(10)),
                java.util.Set.of(),
                128);

        assertTrue(result.successful());
        var plan = result.plan();
        assertEquals(n(10), plan.repetitions());
        assertEquals(Map.of(A, n(1), B, n(10)), plan.minimumSeed());
        assertEquals(Map.of(A, n(1), B, n(10)), plan.initialInputs());
        assertEquals(Map.of(A, n(10), B, n(-10)), plan.netChange());
        assertEquals(Map.of(firing, n(10)), plan.aggregateFirings());
        assertNotNull(plan.schedule());
        assertTrue(plan.schedule().size() <= 3, "single self-loop should schedule in a few batches");
    }

    /**
     * FINAL_TOTAL semantics: 7 A are already stocked and 10 A are wanted as the final total, so only 3 must be produced
     * net; the 3 A contribution must be added to the initial inputs.
     */
    @Test
    void testSingleSelfLoopFinalTotal() {
        var firing = new LoopFiring(
                Map.<appeng.api.stacks.AEKey, BigInteger>of(A, n(1), B, n(1)),
                AEKeyMap.of(A, 2),
                BigInteger.ONE);
        var planner = new DeterministicCyclePlanner();

        var result = planner.plan(
                List.of(firing),
                A,
                n(10),
                CycleQuantityMode.FINAL_TOTAL,
                Map.of(A, n(3), B, n(7)),
                java.util.Set.of(),
                128);

        assertTrue(result.successful());
        var plan = result.plan();
        assertEquals(n(7), plan.repetitions());
        assertEquals(Map.of(A, n(3), B, n(7)), plan.initialInputs());
        // final total = initial + net = A:3+7=10, B:7-7=0
        var finalBalances = CyclePlan.simulateSchedule(plan.initialInputs(), plan.schedule());
        assertEquals(Map.of(A, n(10)), finalBalances);
    }

    /**
     * Two-step cycle: A -> B, then B + C -> 2 A. Per cycle net: A +1, C -1; B is working capital produced and consumed
     * inside the cycle, so it never needs an external seed. The seed must cover A for the first firing and C for the
     * whole loop (net consumed).
     */
    @Test
    void testTwoStepCycle() {
        var toB = new LoopFiring(AEKeyMap.of(A, 1), AEKeyMap.of(B, 1), BigInteger.ONE);
        var toA = new LoopFiring(Map.<appeng.api.stacks.AEKey, BigInteger>of(B, n(1), C, n(1)), AEKeyMap.of(A, 2),
                BigInteger.ONE);
        var planner = new DeterministicCyclePlanner();

        var result = planner.plan(
                List.of(toB, toA),
                A,
                n(5),
                CycleQuantityMode.NET_NEW,
                Map.of(A, n(1), C, n(5)),
                java.util.Set.of(),
                128);

        assertTrue(result.successful());
        var plan = result.plan();
        assertEquals(n(5), plan.repetitions());
        // Seed: A:1 (start of toB), C:5 (net consumed across 5 repetitions); B needs no external seed.
        assertEquals(Map.of(A, n(1), C, n(5)), plan.minimumSeed());
        assertEquals(Map.of(A, n(1), C, n(5)), plan.initialInputs());
        assertEquals(Map.of(A, n(5), C, n(-5)), plan.netChange());
        var finalBalances = CyclePlan.simulateSchedule(plan.initialInputs(), plan.schedule());
        assertEquals(Map.of(A, n(6)), finalBalances);
    }

    /**
     * The affine scheduler must group rotations: for the two-step cycle the batch sizes grow geometrically, so the
     * schedule has logarithmic size in the repetitions.
     */
    @Test
    void testScheduleIsLogarithmicInRepetitions() {
        var toB = new LoopFiring(AEKeyMap.of(A, 1), AEKeyMap.of(B, 1), BigInteger.ONE);
        var toA = new LoopFiring(Map.<appeng.api.stacks.AEKey, BigInteger>of(B, n(1), C, n(1)), AEKeyMap.of(A, 2),
                BigInteger.ONE);
        var planner = new DeterministicCyclePlanner();

        var result = planner.plan(
                List.of(toB, toA),
                A,
                BigInteger.TEN.pow(12),
                CycleQuantityMode.NET_NEW,
                Map.of(A, n(1), C, BigInteger.TEN.pow(12)),
                java.util.Set.of(),
                1024);

        assertTrue(result.successful());
        var plan = result.plan();
        assertEquals(BigInteger.TEN.pow(12), plan.repetitions());
        assertTrue(plan.schedule().size() < 128,
                "schedule should be logarithmic, got " + plan.schedule().size() + " batches");
    }

    /**
     * A cycle that does not produce the requested target net-positive must be rejected.
     */
    @Test
    void testNonProductiveCycleRejected() {
        // A + B -> A + B (no net production of A)
        var firing = new LoopFiring(
                Map.<appeng.api.stacks.AEKey, BigInteger>of(A, n(1), B, n(1)),
                Map.<appeng.api.stacks.AEKey, BigInteger>of(A, n(1), B, n(1)),
                BigInteger.ONE);
        var planner = new DeterministicCyclePlanner();

        var result = planner.plan(
                List.of(firing),
                A,
                n(10),
                CycleQuantityMode.NET_NEW,
                Map.of(),
                java.util.Set.of(),
                128);

        assertTrue(!result.successful());
        assertEquals(CycleFailureCode.NO_PRODUCTIVE_CYCLE, result.failure().code());
    }

    /**
     * A cycle consuming more of the target than it produces must be rejected as non-productive.
     */
    @Test
    void testShrinkingLoopRejected() {
        // 2 A -> A
        var firing = new LoopFiring(AEKeyMap.of(A, 2), AEKeyMap.of(A, 1), BigInteger.ONE);
        var planner = new DeterministicCyclePlanner();

        var result = planner.plan(
                List.of(firing),
                A,
                n(10),
                CycleQuantityMode.NET_NEW,
                Map.of(),
                java.util.Set.of(),
                128);

        assertTrue(!result.successful());
        assertEquals(CycleFailureCode.NO_PRODUCTIVE_CYCLE, result.failure().code());
    }

    /**
     * Missing external input: the cycle consumes B which is neither stocked nor producible.
     */
    @Test
    void testInsufficientExternalInput() {
        var firing = new LoopFiring(
                Map.<appeng.api.stacks.AEKey, BigInteger>of(A, n(1), B, n(1)),
                AEKeyMap.of(A, 2),
                BigInteger.ONE);
        var planner = new DeterministicCyclePlanner();

        var result = planner.plan(
                List.of(firing),
                A,
                n(10),
                CycleQuantityMode.NET_NEW,
                Map.of(A, n(1), B, n(5)),
                java.util.Set.of(),
                128);

        assertTrue(!result.successful());
        var failure = result.failure();
        assertEquals(CycleFailureCode.INSUFFICIENT_INPUT, failure.code());
        assertEquals(1, failure.missingInputs().size());
        var shortage = failure.primaryShortage();
        assertNotNull(shortage);
        assertEquals(B, shortage.getKey());
        assertEquals(n(10), shortage.getValue().required());
        assertEquals(n(5), shortage.getValue().available());
        assertEquals(n(5), shortage.getValue().missing());
    }

    /**
     * Missing target cycle seed: the target itself must be present to start the loop.
     */
    @Test
    void testInsufficientTargetSeed() {
        var firing = new LoopFiring(AEKeyMap.of(A, 1), AEKeyMap.of(A, 2), BigInteger.ONE);
        var planner = new DeterministicCyclePlanner();

        var result = planner.plan(
                List.of(firing),
                A,
                n(10),
                CycleQuantityMode.NET_NEW,
                Map.of(B, n(100)),
                java.util.Set.of(),
                128);

        assertTrue(!result.successful());
        var failure = result.failure();
        assertEquals(CycleFailureCode.INSUFFICIENT_INPUT, failure.code());
        assertNotNull(failure.primaryShortage());
        assertEquals(A, failure.primaryShortage().getKey());
    }

    /**
     * Missing working seed: W is neither the target nor net consumed (it is recycled by the cycle), but 1 W must still
     * be present to start the loop. Cycle: A + W -> B, B -> 2 A + W.
     */
    @Test
    void testInsufficientWorkingSeed() {
        var toB = new LoopFiring(Map.<appeng.api.stacks.AEKey, BigInteger>of(A, n(1), W, n(1)), AEKeyMap.of(B, 1),
                BigInteger.ONE);
        var toA = new LoopFiring(AEKeyMap.of(B, 1), Map.<appeng.api.stacks.AEKey, BigInteger>of(A, n(2), W, n(1)),
                BigInteger.ONE);
        var planner = new DeterministicCyclePlanner();

        var result = planner.plan(
                List.of(toB, toA),
                A,
                n(5),
                CycleQuantityMode.NET_NEW,
                Map.of(A, n(1)),
                java.util.Set.of(),
                128);

        assertTrue(!result.successful());
        assertEquals(CycleFailureCode.INSUFFICIENT_INPUT, result.failure().code());
        assertNotNull(result.failure().primaryShortage());
        assertEquals(W, result.failure().primaryShortage().getKey());
    }

    /**
     * Producible inputs are treated as covered even when absent from the inventory.
     */
    @Test
    void testProducibleInputsCovered() {
        var firing = new LoopFiring(AEKeyMap.of(A, 1), AEKeyMap.of(A, 2), BigInteger.ONE);
        var planner = new DeterministicCyclePlanner();

        var result = planner.plan(
                List.of(firing),
                A,
                n(10),
                CycleQuantityMode.NET_NEW,
                Map.of(A, n(1)),
                java.util.Set.of(B),
                128);

        assertTrue(result.successful());
    }

    /**
     * A huge repetition count must stay exact with BigInteger (no overflow).
     */
    @Test
    void testHugeRepetitionsExact() {
        var firing = new LoopFiring(
                Map.<appeng.api.stacks.AEKey, BigInteger>of(A, n(1), B, n(1)),
                AEKeyMap.of(A, 2),
                BigInteger.ONE);
        var planner = new DeterministicCyclePlanner();
        var huge = BigInteger.TEN.pow(60);

        var result = planner.plan(
                List.of(firing),
                A,
                huge,
                CycleQuantityMode.NET_NEW,
                Map.of(A, n(1), B, huge),
                java.util.Set.of(),
                4096);

        assertTrue(result.successful());
        var plan = result.plan();
        assertEquals(huge, plan.repetitions());
        assertEquals(huge, plan.netChange().get(A));
        assertEquals(n(1), plan.minimumSeed().get(A));
        assertEquals(huge, plan.minimumSeed().get(B));
    }

    /**
     * Firing/plan accounting must reject inconsistent data.
     */
    @Test
    void testAccountingValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new LoopFiring(Map.of(), Map.of(A, n(1)), BigInteger.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> new LoopFiring(AEKeyMap.of(A, 1), AEKeyMap.of(A, 2), BigInteger.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new InputRequirement(n(10), n(6), n(5)));
    }

    /**
     * Sanity: unused helper.
     */
    @Test
    void testItemKeysDistinct() {
        assertTrue(!A.equals(B) && !A.equals(C) && !B.equals(C));
        var stackA = new ItemStack(Items.DIAMOND);
        assertEquals(A, AEItemKey.of(stackA));
    }
}

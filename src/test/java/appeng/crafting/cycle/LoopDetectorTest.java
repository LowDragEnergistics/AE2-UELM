package appeng.crafting.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.util.BootstrapMinecraft;

/**
 * Self-loop identification tests: single self-loops, multi-step cycles, acyclic graphs and productive target detection.
 */
@BootstrapMinecraft
class LoopDetectorTest {

    private static final AEItemKey A = AEItemKey.of(Items.DIAMOND);
    private static final AEItemKey B = AEItemKey.of(Items.EMERALD);
    private static final AEItemKey C = AEItemKey.of(Items.REDSTONE);

    private static BigInteger n(long v) {
        return BigInteger.valueOf(v);
    }

    private record AE2Key(AEItemKey key, long amount) {
        static Map<AEKey, BigInteger> of(AEItemKey key, long amount) {
            return Map.<AEKey, BigInteger>of(key, n(amount));
        }
    }

    /**
     * A single firing whose output is also its input is a self-loop.
     */
    @Test
    void testSingleSelfLoopDetected() {
        // A + B -> 2 A
        var firing = new LoopFiring(
                Map.<AEKey, BigInteger>of(A, n(1), B, n(1)),
                AE2Key.of(A, 2),
                BigInteger.ONE);

        var loops = LoopDetector.detect(List.of(firing));

        assertEquals(1, loops.size());
        var loop = loops.get(0);
        assertEquals(List.of(0), loop.firingIndices());
        assertEquals(1, loop.order().size());
        assertEquals(List.of(A), loop.productiveTargets());
        assertEquals(Map.of(A, n(1), B, n(-1)), loop.netChange());
    }

    /**
     * A linear chain (A -> B -> C) has no cycle.
     */
    @Test
    void testAcyclicChainNoLoop() {
        var toB = new LoopFiring(AE2Key.of(A, 1), AE2Key.of(B, 1), BigInteger.ONE);
        var toC = new LoopFiring(AE2Key.of(B, 1), AE2Key.of(C, 1), BigInteger.ONE);

        assertEquals(0, LoopDetector.detect(List.of(toB, toC)).size());
    }

    /**
     * A -> B, B -> 2 A is a two-step production cycle.
     */
    @Test
    void testTwoStepCycleDetected() {
        var toB = new LoopFiring(AE2Key.of(A, 1), AE2Key.of(B, 1), BigInteger.ONE);
        var toA = new LoopFiring(AE2Key.of(B, 1), AE2Key.of(A, 2), BigInteger.ONE);

        var loops = LoopDetector.detect(List.of(toB, toA));

        assertEquals(1, loops.size());
        var loop = loops.get(0);
        assertEquals(2, loop.order().size());
        assertEquals(List.of(A), loop.productiveTargets());
        assertEquals(Map.of(A, n(1)), loop.netChange());
    }

    /**
     * A cycle embedded in a larger graph: only the strongly connected component is reported.
     */
    @Test
    void testCycleWithExternalChain() {
        var toB = new LoopFiring(AE2Key.of(A, 1), AE2Key.of(B, 1), BigInteger.ONE);
        var toA = new LoopFiring(AE2Key.of(B, 1), AE2Key.of(A, 2), BigInteger.ONE);
        var toC = new LoopFiring(AE2Key.of(A, 1), AE2Key.of(C, 1), BigInteger.ONE); // consumes A, not part of the cycle

        var loops = LoopDetector.detect(List.of(toB, toA, toC));

        assertEquals(1, loops.size());
        assertEquals(2, loops.get(0).order().size());
    }

    /**
     * Multiple independent cycles are all detected.
     */
    @Test
    void testMultipleCyclesDetected() {
        var loopA = new LoopFiring(AE2Key.of(A, 1), AE2Key.of(A, 2), BigInteger.ONE);
        var loopB = new LoopFiring(AE2Key.of(B, 1), AE2Key.of(B, 2), BigInteger.ONE);

        var loops = LoopDetector.detect(List.of(loopA, loopB));

        assertEquals(2, loops.size());
        assertTrue(loops.get(0).productiveTargets().contains(A) || loops.get(1).productiveTargets().contains(A));
        assertTrue(loops.get(0).productiveTargets().contains(B) || loops.get(1).productiveTargets().contains(B));
    }

    /**
     * A cycle that shrinks its target (2 A -> A) has no productive target.
     */
    @Test
    void testShrinkingLoopHasNoProductiveTarget() {
        var firing = new LoopFiring(AE2Key.of(A, 2), AE2Key.of(A, 1), BigInteger.ONE);

        var loops = LoopDetector.detect(List.of(firing));

        assertEquals(1, loops.size());
        assertTrue(loops.get(0).productiveTargets().isEmpty());
    }
}

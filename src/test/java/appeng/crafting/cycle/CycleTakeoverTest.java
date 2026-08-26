package appeng.crafting.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.util.BootstrapMinecraft;

/**
 * Tests for the synthetic cycle pattern ({@link CyclePatternDetails}) and the execution task
 * ({@link LoopExecutionTask}) that forwards schedule batches to network providers.
 */
@BootstrapMinecraft
class CycleTakeoverTest {

    private static final AEItemKey A = AEItemKey.of(Items.DIAMOND);
    private static final AEItemKey B = AEItemKey.of(Items.EMERALD);

    private static BigInteger n(long v) {
        return BigInteger.valueOf(v);
    }

    private static Map<AEKey, BigInteger> of(AEItemKey key, long amount) {
        return Map.of(key, n(amount));
    }

    /**
     * A successful plan for A + B -> 2 A: seed {A:1, B:10}, net {A:+10}.
     */
    private static CyclePlan samplePlan() {
        var firing = new LoopFiring(
                Map.<AEKey, BigInteger>of(A, n(1), B, n(1)),
                of(A, 2),
                BigInteger.ONE);
        var result = new DeterministicCyclePlanner().plan(
                List.of(firing), A, n(10), CycleQuantityMode.NET_NEW,
                Map.of(A, n(1), B, n(10)),
                java.util.Set.of(), 128);
        assertTrue(result.successful());
        return result.plan();
    }

    @Test
    void testCyclePatternDetailsExposeSeedAndOutput() {
        var plan = samplePlan();
        var pattern = CyclePatternDetails.forPlan(A, plan);

        assertEquals(A, pattern.getDefinition());
        assertEquals(2, pattern.getInputs().length);
        // Seed slots: A:1, B:10.
        long aSeed = 0;
        long bSeed = 0;
        for (var input : pattern.getInputs()) {
            var key = input.getPossibleInputs()[0].what();
            if (key.equals(A)) {
                aSeed = input.getMultiplier();
            } else if (key.equals(B)) {
                bSeed = input.getMultiplier();
            }
        }
        assertEquals(1, aSeed);
        assertEquals(10, bSeed);
        assertEquals(A, pattern.getPrimaryOutput().what());
        assertEquals(10, pattern.getPrimaryOutput().amount());
        assertFalse(pattern.isInputOnly());
        assertEquals(pattern, CyclePatternDetails.forPlan(A, plan));
    }

    @Test
    void testHugeOutputSatUratesToLong() {
        var firing = new LoopFiring(
                Map.<AEKey, BigInteger>of(A, n(1), B, n(1)),
                of(A, 2),
                BigInteger.ONE);
        var result = new DeterministicCyclePlanner().plan(
                List.of(firing), A, BigInteger.TEN.pow(40), CycleQuantityMode.NET_NEW,
                Map.of(A, n(1), B, BigInteger.TEN.pow(40)),
                java.util.Set.of(), 256);
        assertTrue(result.successful());

        var pattern = CyclePatternDetails.forPlan(A, result.plan());
        assertEquals(Long.MAX_VALUE, pattern.getPrimaryOutput().amount());
    }

    @Test
    void testExecutionTaskForwardsBatchesToProviders() {
        var plan = samplePlan();
        var binding = new LoopNetworkScan.PatternBinding(
                CyclePatternDetails.forPlan(A, plan),
                new RecordingProvider(),
                plan.oneCycleOrder().get(0));
        var task = new LoopExecutionTask(plan, List.of(binding));

        // The affine scheduler produces batches for the A+B->2A loop; each tick pushes one.
        int ticks = 0;
        while (!task.isComplete() && ticks < 64) {
            assertTrue(task.tick());
            ticks++;
        }
        assertTrue(task.isComplete());
    }

    @Test
    void testExecutionTaskRetriesWhenProviderBusy() {
        var plan = samplePlan();
        var provider = new RecordingProvider();
        var binding = new LoopNetworkScan.PatternBinding(CyclePatternDetails.forPlan(A, plan), provider,
                plan.oneCycleOrder().get(0));
        var task = new LoopExecutionTask(plan, List.of(binding));

        // Make the provider busy for one attempt.
        provider.busy = true;
        assertFalse(task.tick());
        provider.busy = false;
        assertTrue(task.tick());
        while (!task.isComplete()) {
            assertTrue(task.tick());
        }
        assertTrue(task.isComplete());
    }

    @Test
    void testExecutionTaskInputHolderMatchesPatternInputs() {
        var plan = samplePlan();
        var provider = new RecordingProvider();
        var pattern = CyclePatternDetails.forPlan(A, plan);
        var binding = new LoopNetworkScan.PatternBinding(pattern, provider, plan.oneCycleOrder().get(0));
        var task = new LoopExecutionTask(plan, List.of(binding));

        while (!task.isComplete()) {
            assertTrue(task.tick());
        }

        // Every pushed batch must carry one KeyCounter per pattern input slot.
        for (var holder : provider.received) {
            assertEquals(pattern.getInputs().length, holder.length);
            for (var counter : holder) {
                assertNotNull(counter);
            }
        }
    }

    /**
     * A fake provider that records pushed input holders.
     */
    private static final class RecordingProvider implements ICraftingProvider {
        boolean busy;
        final List<KeyCounter[]> received = new java.util.ArrayList<>();

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            received.add(inputHolder);
            return true;
        }

        @Override
        public boolean isBusy() {
            return busy;
        }
    }
}

package appeng.crafting.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.TunnelPatternTestHelper;
import appeng.util.BootstrapMinecraft;

/**
 * Tunnel-pattern compatibility for the self-loop matrix: a processing pattern whose inputs reference a tunnel pattern
 * must be converted to a firing that consumes the tunnel pattern's concrete contents.
 */
@BootstrapMinecraft
class CyclePatternsTest {

    private static final AEItemKey STICK = AEItemKey.of(Items.STICK);
    private static final AEItemKey TORCH = AEItemKey.of(Items.TORCH);
    private static final AEItemKey DIAMOND = AEItemKey.of(Items.DIAMOND);

    private static GenericStack stack(AEItemKey key, long amount) {
        return new GenericStack(key, amount);
    }

    /**
     * Encodes a processing pattern: 3x tunnel T + 1 torch -> 2 diamonds.
     */
    private static IPatternDetails referencingPattern(UUID uuid) {
        var stack = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] { TunnelPatternTestHelper.reference(uuid, 3), stack(TORCH, 1) },
                new GenericStack[] { stack(DIAMOND, 2) },
                "tester");
        return PatternDetailsHelper.decodePattern(stack, mock(Level.class));
    }

    /**
     * Encodes a tunnel pattern with the given concrete inputs.
     */
    private static IPatternDetails tunnelPattern(UUID uuid, GenericStack... inputs) {
        var stack = PatternDetailsHelper.encodeTunnelPattern(inputs, uuid, "tester");
        return PatternDetailsHelper.decodePattern(stack, mock(Level.class));
    }

    @Test
    void testTunnelReferenceExpandedToConcreteInputs() {
        var uuid = UUID.randomUUID();
        // T: 2 sticks per craft.
        var tunnel = tunnelPattern(uuid, stack(STICK, 2));
        var pattern = referencingPattern(uuid);

        var firing = CyclePatterns.fromPattern(pattern, id -> tunnel);

        assertNotNull(firing);
        // 2 sticks per craft * 3 references = 6 sticks, plus 1 torch.
        assertEquals(Map.of(STICK, BigInteger.valueOf(6), TORCH, BigInteger.ONE), firing.inputs());
        assertEquals(Map.of(DIAMOND, BigInteger.valueOf(2)), firing.outputs());
    }

    @Test
    void testTunnelContentsCanBeChangedWithoutEditingReferencingPattern() {
        var uuid = UUID.randomUUID();
        // Same referencing pattern, but the tunnel now provides 5 sticks per craft.
        var tunnel = tunnelPattern(uuid, stack(STICK, 5));
        var pattern = referencingPattern(uuid);

        var firing = CyclePatterns.fromPattern(pattern, id -> tunnel);

        assertNotNull(firing);
        assertEquals(STICK, firing.inputs().keySet().stream().filter(STICK::equals).findFirst().orElseThrow());
        assertEquals(BigInteger.valueOf(15), firing.inputs().get(STICK));
    }

    @Test
    void testMissingTunnelTargetRejected() {
        var uuid = UUID.randomUUID();
        var pattern = referencingPattern(uuid);

        // No lookup available: the reference is kept as-is and must be rejected for cycle computation.
        assertNull(CyclePatterns.fromPattern(pattern, id -> null));
    }

    @Test
    void testTunnelCycleRejected() {
        var t = UUID.randomUUID();
        var pattern = referencingPattern(t);

        // The tunnel references itself: expansion fails.
        var selfReferencing = tunnelPattern(t, TunnelPatternTestHelper.reference(t, 1));
        assertNull(CyclePatterns.fromPattern(pattern, id -> selfReferencing));
    }

    @Test
    void testInputOnlyPatternRejected() {
        var uuid = UUID.randomUUID();
        var inputOnly = tunnelPattern(uuid, stack(STICK, 1));

        assertNull(CyclePatterns.fromPattern(inputOnly, id -> null));
    }

    @Test
    void testTunnelInMultiStepLoop() {
        // T: 1 stick. Loop: (tunnel T + 1 torch -> 2 torches) forms a self-loop on torch:
        // firing consumes 1 stick + 1 torch and produces 2 torches. Per cycle net: torch +1, stick -1.
        var t = UUID.randomUUID();
        var tunnel = tunnelPattern(t, stack(STICK, 1));
        var loopPattern = referencingPattern2(t);
        var firing = CyclePatterns.fromPattern(loopPattern, id -> tunnel);
        assertNotNull(firing);

        var planner = new DeterministicCyclePlanner();
        var result = planner.plan(
                List.of(firing),
                TORCH,
                BigInteger.TEN,
                CycleQuantityMode.NET_NEW,
                Map.of(STICK, BigInteger.TEN, TORCH, BigInteger.ONE),
                java.util.Set.of(),
                128);

        assertTrue(result.successful());
        var plan = result.plan();
        assertEquals(BigInteger.TEN, plan.repetitions());
        // Seed: 1 stick (net consumed) + 1 torch for the first firing; stick seed grows with repetitions.
        assertEquals(Map.of(STICK, BigInteger.TEN, TORCH, BigInteger.ONE), plan.minimumSeed());
        var finalBalances = CyclePlan.simulateSchedule(plan.initialInputs(), plan.schedule());
        assertEquals(Map.of(TORCH, BigInteger.valueOf(11)), finalBalances);
    }

    /**
     * Encodes: 1x tunnel T + 1 torch -> 2 torches.
     */
    private static IPatternDetails referencingPattern2(UUID uuid) {
        var stack = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] { TunnelPatternTestHelper.reference(uuid, 1), stack(TORCH, 1) },
                new GenericStack[] { stack(TORCH, 2) },
                "tester");
        return PatternDetailsHelper.decodePattern(stack, mock(Level.class));
    }

    @Test
    void testInputsOfRejectsUnresolvedReference() {
        var uuid = UUID.randomUUID();
        var input = TunnelPatternTestHelper.input(3, TunnelPatternTestHelper.reference(uuid, 1));
        try {
            CyclePatterns.inputsOf(List.of(input));
            throw new AssertionError("expected UnresolvedTunnelReferenceException");
        } catch (CyclePatterns.UnresolvedTunnelReferenceException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}

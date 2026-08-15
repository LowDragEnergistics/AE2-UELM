package appeng.crafting.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.TunnelPatternItem;
import appeng.crafting.simulation.helpers.ProcessingPatternBuilder;
import appeng.crafting.simulation.helpers.SimulationEnv;
import appeng.util.BootstrapMinecraft;

/**
 * Simulation tests for tunnel pattern references: a processing pattern that uses a tunnel pattern item as an input
 * should, at craft time, consume the referenced input-only pattern's inputs instead (multiplied by the reference).
 */
@BootstrapMinecraft
public class TunnelSimulationTest {

    private static final Item ITEM_OUTPUT = Items.DIAMOND;
    private static final Item TUNNEL_INPUT = Items.STICK;
    private static final Item EXTRA_INPUT = Items.TORCH;

    @Test
    public void testTunnelInputInlined() {
        var env = new SimulationEnv();
        var uuid = UUID.randomUUID();

        // Tunnel pattern T: 2 sticks, no outputs.
        env.addInputOnlyPattern(tunnelPattern(uuid, 2, TUNNEL_INPUT));

        // Pattern P: 1x tunnel T -> 1 diamond.
        var p = env.addPattern(new ProcessingPatternBuilder(item(ITEM_OUTPUT))
                .addPreciseInput(1, tunnelReference(uuid, 1))
                .build());

        env.addStoredItem(item(TUNNEL_INPUT, 2));

        var plan = env.runSimulation(item(ITEM_OUTPUT), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(p, 1)
                .emittedMatch()
                .usedMatch(item(TUNNEL_INPUT, 2));
    }

    @Test
    public void testTunnelMultiplier() {
        var env = new SimulationEnv();
        var uuid = UUID.randomUUID();

        // Tunnel pattern T: 1 stick.
        env.addInputOnlyPattern(tunnelPattern(uuid, 1, TUNNEL_INPUT));

        // Pattern P: 3x tunnel T -> 1 diamond. Should consume 3 sticks.
        var p = env.addPattern(new ProcessingPatternBuilder(item(ITEM_OUTPUT))
                .addPreciseInput(3, tunnelReference(uuid, 1))
                .build());

        env.addStoredItem(item(TUNNEL_INPUT, 3));

        var plan = env.runSimulation(item(ITEM_OUTPUT), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(p, 1)
                .usedMatch(item(TUNNEL_INPUT, 3));
    }

    @Test
    public void testNestedTunnel() {
        var env = new SimulationEnv();
        var uuid1 = UUID.randomUUID();
        var uuid2 = UUID.randomUUID();

        // Tunnel T2: 2 sticks.
        env.addInputOnlyPattern(tunnelPattern(uuid2, 2, TUNNEL_INPUT));
        // Tunnel T1: 1x tunnel T2 (nested).
        env.addInputOnlyPattern(nestedTunnelPattern(uuid1, uuid2, 1));

        // Pattern P: 1x tunnel T1 -> 1 diamond. Should consume 2 sticks.
        var p = env.addPattern(new ProcessingPatternBuilder(item(ITEM_OUTPUT))
                .addPreciseInput(1, tunnelReference(uuid1, 1))
                .build());

        env.addStoredItem(item(TUNNEL_INPUT, 2));

        var plan = env.runSimulation(item(ITEM_OUTPUT), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(p, 1)
                .usedMatch(item(TUNNEL_INPUT, 2));
    }

    @Test
    public void testTunnelCycleFails() {
        var env = new SimulationEnv();
        var uuid = UUID.randomUUID();

        // Tunnel T: 1x tunnel T (self-reference -> cycle).
        env.addInputOnlyPattern(nestedTunnelPattern(uuid, uuid, 1));

        // Pattern P references the cyclic tunnel.
        env.addPattern(new ProcessingPatternBuilder(item(ITEM_OUTPUT))
                .addPreciseInput(1, tunnelReference(uuid, 1))
                .build());

        env.addStoredItem(item(TUNNEL_INPUT));

        var plan = env.runSimulation(item(ITEM_OUTPUT), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan).failed();
    }

    @Test
    public void testMissingTunnelTargetFails() {
        var env = new SimulationEnv();

        // Pattern P references a tunnel UUID that is not registered in the network.
        env.addPattern(new ProcessingPatternBuilder(item(ITEM_OUTPUT))
                .addPreciseInput(1, tunnelReference(UUID.randomUUID(), 1))
                .build());

        env.addStoredItem(item(TUNNEL_INPUT));

        var plan = env.runSimulation(item(ITEM_OUTPUT), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan).failed();
    }

    @Test
    public void testBrokenTunnelPatternFallsBackToOtherPattern() {
        var env = new SimulationEnv();
        var brokenUuid = UUID.randomUUID();
        var goodUuid = UUID.randomUUID();

        // A broken tunnel (self-cycle).
        env.addInputOnlyPattern(nestedTunnelPattern(brokenUuid, brokenUuid, 1));
        // A good tunnel: 1 stick.
        env.addInputOnlyPattern(tunnelPattern(goodUuid, 1, TUNNEL_INPUT));

        // Two patterns for the same output: one referencing the broken tunnel, one referencing the good tunnel.
        var broken = env.addPattern(new ProcessingPatternBuilder(item(ITEM_OUTPUT))
                .addPreciseInput(1, tunnelReference(brokenUuid, 1))
                .build());
        var good = env.addPattern(new ProcessingPatternBuilder(item(ITEM_OUTPUT))
                .addPreciseInput(1, tunnelReference(goodUuid, 1))
                .build());

        env.addStoredItem(item(TUNNEL_INPUT));

        // The simulation must fall back to the good pattern and succeed, consuming the stick.
        var plan = env.runSimulation(item(ITEM_OUTPUT), CalculationStrategy.REPORT_MISSING_ITEMS);
        assertThatPlan(plan)
                .succeeded()
                .patternsMatch(good, 1)
                .usedMatch(item(TUNNEL_INPUT, 1));

        // The broken pattern must not be reported as used.
        assertThat(plan.patternTimes()).doesNotContainKey(broken);
    }

    /**
     * Builds an input-only tunnel pattern with {@code count} of {@code input} as its single input.
     */
    private static IPatternDetails tunnelPattern(UUID uuid, long count, Item input) {
        return new TunnelPatternDetails(uuid,
                new IPatternDetails.IInput[] { preciseInput(count, item(input)) });
    }

    /**
     * Builds an input-only tunnel pattern whose single input references another tunnel pattern.
     */
    private static IPatternDetails nestedTunnelPattern(UUID uuid, UUID referencedUuid, long count) {
        return new TunnelPatternDetails(uuid,
                new IPatternDetails.IInput[] { preciseInput(count, tunnelReference(referencedUuid, 1)) });
    }

    private static IPatternDetails.IInput preciseInput(long multiplier, GenericStack possibleInput) {
        return new IPatternDetails.IInput() {
            @Override
            public GenericStack[] getPossibleInputs() {
                return new GenericStack[] { possibleInput };
            }

            @Override
            public long getMultiplier() {
                return multiplier;
            }

            @Override
            public boolean isValid(AEKey input, net.minecraft.world.level.Level level) {
                return input.equals(possibleInput.what());
            }

            @Override
            public AEKey getRemainingKey(AEKey template) {
                return null;
            }
        };
    }

    /**
     * An input-only pattern that can be registered via {@link SimulationEnv#addInputOnlyPattern}.
     */
    private static class TunnelPatternDetails implements IPatternDetails {
        private final UUID uuid;
        private final IInput[] inputs;

        private TunnelPatternDetails(UUID uuid, IInput[] inputs) {
            this.uuid = uuid;
            this.inputs = inputs;
        }

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
    }

    /**
     * A tunnel pattern item input (with the given UUID), used as a reference from a processing pattern.
     */
    private static GenericStack tunnelReference(UUID uuid, long count) {
        var stack = new ItemStack(AEItems.TUNNEL_PATTERN);
        var tag = new CompoundTag();
        TunnelPatternItem.writeTunnelUuid(tag, uuid);
        stack.setTag(tag);
        return new GenericStack(AEItemKey.of(stack), count);
    }

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
    }

    private static GenericStack item(Item item, long count) {
        return new GenericStack(AEItemKey.of(item), count);
    }

    private static PlanAssert assertThatPlan(ICraftingPlan plan) {
        return new PlanAssert(plan);
    }

    private static class PlanAssert {
        private final ICraftingPlan plan;

        private PlanAssert(ICraftingPlan plan) {
            this.plan = Objects.requireNonNull(plan);
        }

        public PlanAssert succeeded() {
            assertThat(plan.simulation()).isFalse();
            assertThat(plan.missingItems()).isEmpty();
            return this;
        }

        public PlanAssert failed() {
            assertThat(plan.simulation()).isTrue();
            assertThat(plan.missingItems()).isNotEmpty();
            return this;
        }

        public PlanAssert patternsMatch(IPatternDetails p1, long t1) {
            assertThat(plan.patternTimes()).isEqualTo(Map.of(p1, t1));
            return this;
        }

        public PlanAssert emittedMatch(GenericStack... expectedStacks) {
            return listMatches(plan.emittedItems(), expectedStacks);
        }

        public PlanAssert usedMatch(GenericStack... expectedStacks) {
            return listMatches(plan.usedItems(), expectedStacks);
        }

        private PlanAssert listMatches(KeyCounter actualList, GenericStack... expectedStacks) {
            var expectedList = new KeyCounter();
            for (var stack : expectedStacks) {
                expectedList.add(stack.what(), stack.amount());
            }
            assertThat(actualList.size()).isEqualTo(expectedList.size());
            for (var expected : expectedList) {
                var actual = actualList.get(expected.getKey());
                assertThat(actual).isEqualTo(expected.getLongValue());
            }
            return this;
        }
    }
}

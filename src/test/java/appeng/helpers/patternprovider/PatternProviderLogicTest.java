package appeng.helpers.patternprovider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.TunnelPatternTestHelper;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class PatternProviderLogicTest {

    private PatternProviderLogic createLogic() {
        var node = mock(IManagedGridNode.class);
        when(node.setFlags(any())).thenReturn(node);
        when(node.addService(any(), any())).thenReturn(node);
        // getNode() stays unstubbed (returns null), so ICraftingProvider.requestUpdate is a no-op.

        var host = mock(PatternProviderLogicHost.class);
        var be = mock(BlockEntity.class);
        when(host.getBlockEntity()).thenReturn(be);
        when(be.getLevel()).thenReturn(mock(Level.class));

        return new PatternProviderLogic(node, host, 9);
    }

    @Test
    void testTunnelPatternIsIgnored() {
        var logic = createLogic();

        // Placing only a tunnel pattern in a pattern provider must not make the provider supply anything.
        logic.getPatternInv().setItemDirect(0, TunnelPatternTestHelper.referenceItem(UUID.randomUUID()));

        assertThat(logic.getAvailablePatterns()).isEmpty();
    }

    @Test
    void testRegularPatternIsKept() {
        var logic = createLogic();

        var encoded = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] { GenericStack.fromItemStack(new ItemStack(Items.STICK)) },
                new GenericStack[] { GenericStack.fromItemStack(new ItemStack(Items.DIAMOND)) }, "test");
        logic.getPatternInv().setItemDirect(0, encoded);

        assertThat(logic.getAvailablePatterns()).hasSize(1);
    }

    @Test
    void testTunnelPatternDoesNotPollutePatternInputs() {
        var logic = createLogic();
        var uuid = UUID.randomUUID();

        // A tunnel pattern whose contents are sticks must not make the provider report sticks as its inputs.
        logic.getPatternInv().setItemDirect(0, TunnelPatternTestHelper.referenceItem(uuid));

        var encoded = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] { GenericStack.fromItemStack(new ItemStack(Items.DIAMOND)) },
                new GenericStack[] { GenericStack.fromItemStack(new ItemStack(Items.GRASS)) }, "test");
        logic.getPatternInv().setItemDirect(1, encoded);

        assertThat(logic.getAvailablePatterns()).hasSize(1);
    }
}

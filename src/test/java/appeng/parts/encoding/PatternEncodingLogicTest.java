package appeng.parts.encoding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class PatternEncodingLogicTest {

    private static final GenericStack STICK = new GenericStack(AEItemKey.of(Items.STICK), 4);

    private PatternEncodingLogic createLogic() {
        var host = new IPatternTerminalLogicHost() {
            private final PatternEncodingLogic logic = new PatternEncodingLogic(this);

            @Override
            public PatternEncodingLogic getLogic() {
                return logic;
            }

            @Override
            public Level getLevel() {
                return mock(Level.class);
            }

            @Override
            public void markForSave() {
            }
        };
        return host.getLogic();
    }

    @Test
    void testLoadTunnelPatternRoundtrip() {
        var logic = createLogic();
        var uuid = UUID.randomUUID();
        var tunnelPattern = PatternDetailsHelper.encodeTunnelPattern(new GenericStack[] { STICK }, uuid, "tester");

        // Placing a tunnel pattern into the encoded pattern slot loads its inputs and clears the outputs
        logic.getEncodedPatternInv().setItemDirect(0, tunnelPattern);

        assertEquals(EncodingMode.PROCESSING, logic.getMode());
        assertEquals(STICK, logic.getEncodedInputInv().getStack(0));
        assertNull(logic.getEncodedOutputInv().getStack(0));
    }
}

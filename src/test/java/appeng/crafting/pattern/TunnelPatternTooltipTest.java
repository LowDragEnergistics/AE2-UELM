package appeng.crafting.pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class TunnelPatternTooltipTest {

    private static final GenericStack STICK = GenericStack.fromItemStack(new ItemStack(Items.STICK));
    private static final GenericStack OUTPUT = GenericStack.fromItemStack(new ItemStack(Items.DIAMOND));

    @Test
    void testTunnelPatternTooltipShowsInfoAndUuid() {
        var uuid = UUID.randomUUID();
        var stack = PatternDetailsHelper.encodeTunnelPattern(new GenericStack[] { STICK }, uuid, "tester");

        var lines = hoverLines(stack);

        var keys = translatableKeys(lines);
        assertTrue(keys.contains("gui.ae2.TunnelPatternInfo1"));
        assertTrue(keys.contains("gui.ae2.TunnelPatternInfo2"));
        assertTrue(keys.contains("gui.ae2.TunnelPatternInfo3"));
        assertTrue(keys.contains("gui.ae2.TunnelPatternInfo4"));
        assertTrue(keys.contains("gui.ae2.TunnelPatternUuid"));

        // The UUID is passed as the formatted argument of the UUID line.
        assertTrue(lines.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents tc
                && "gui.ae2.TunnelPatternUuid".equals(tc.getKey())
                && Arrays.asList(tc.getArgs()).contains(uuid.toString())));
    }

    @Test
    void testProcessingPatternTooltipHasNoTunnelInfo() {
        var stack = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] { STICK }, new GenericStack[] { OUTPUT }, "tester");

        var keys = translatableKeys(hoverLines(stack));
        assertFalse(keys.stream().anyMatch(key -> key.contains("gui.ae2.TunnelPatternInfo")));
        assertFalse(keys.contains("gui.ae2.TunnelPatternUuid"));
    }

    @Test
    void testInvalidTunnelItemTooltipHasNoTunnelInfo() {
        // A tunnel item without a valid tunnel tag is invalid and must not show tunnel info.
        var stack = new ItemStack(PatternDetailsHelper.encodeTunnelPattern(
                new GenericStack[] { STICK }, UUID.randomUUID(), "tester").getItem());
        stack.setTag(new net.minecraft.nbt.CompoundTag());

        var keys = translatableKeys(hoverLines(stack));
        assertFalse(keys.stream().anyMatch(key -> key.contains("gui.ae2.TunnelPatternInfo")));
    }

    private static List<String> translatableKeys(List<Component> lines) {
        return lines.stream()
                .map(Component::getContents)
                .filter(TranslatableContents.class::isInstance)
                .map(TranslatableContents.class::cast)
                .map(TranslatableContents::getKey)
                .toList();
    }

    private static List<Component> hoverLines(ItemStack stack) {
        var item = (EncodedPatternItem) stack.getItem();
        var lines = new ArrayList<Component>();
        item.appendHoverText(stack, mock(Level.class), lines, TooltipFlag.Default.NORMAL);
        return lines;
    }
}

package appeng.crafting.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.crafting.PatternInfo;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class TunnelPatternEncodingTest {

    private static final GenericStack STICK = new GenericStack(AEItemKey.of(Items.STICK), 4);
    private static final GenericStack TORCH = new GenericStack(AEItemKey.of(Items.TORCH), 1);

    @Test
    void testEncodeTunnelPatternNbt() {
        var uuid = UUID.randomUUID();
        var tag = new CompoundTag();
        ProcessingPatternEncoding.encodeTunnelPattern(tag, new GenericStack[] { STICK, TORCH }, uuid,
                PatternInfo.EMPTY);

        assertTrue(ProcessingPatternEncoding.isInputOnly(tag));
        assertEquals(uuid, ProcessingPatternEncoding.getTunnelUuid(tag));
        assertEquals(2, ProcessingPatternEncoding.getProcessingInputs(tag).length);
        assertEquals(0, ProcessingPatternEncoding.getProcessingOutputs(tag).length);
    }

    @Test
    void testIsInputOnlyFalseForNormalPattern() {
        var tag = new CompoundTag();
        ProcessingPatternEncoding.encodeProcessingPattern(tag, new GenericStack[] { STICK },
                new GenericStack[] { TORCH }, PatternInfo.EMPTY);

        assertFalse(ProcessingPatternEncoding.isInputOnly(tag));
        assertNull(ProcessingPatternEncoding.getTunnelUuid(tag));
    }

    @Test
    void testGetTunnelUuidInvalid() {
        var tag = new CompoundTag();
        TunnelPatternItem.writeTunnelUuid(tag, UUID.randomUUID());
        tag.putString(TunnelPatternItem.TAG_TUNNEL_UUID, "not-a-uuid");

        assertNull(ProcessingPatternEncoding.getTunnelUuid(tag));
    }

    @Test
    void testGetTunnelUuidEmpty() {
        var tag = new CompoundTag();
        TunnelPatternItem.writeTunnelUuid(tag, UUID.randomUUID());
        tag.putString(TunnelPatternItem.TAG_TUNNEL_UUID, "");

        assertNull(ProcessingPatternEncoding.getTunnelUuid(tag));
    }

    @Test
    void testEncodeTunnelPatternItem() {
        var uuid = UUID.randomUUID();
        ItemStack stack = PatternDetailsHelper.encodeTunnelPattern(new GenericStack[] { STICK, TORCH }, uuid,
                "tester");

        assertTrue(TunnelPatternItem.isTunnelPattern(stack));
        assertEquals(uuid, TunnelPatternItem.getTunnelUuid(stack));

        // Until AETunnelPattern is added (M3), a tunnel pattern decodes as a processing pattern with inputs only.
        var decoded = PatternDetailsHelper.decodePattern(stack, mock(Level.class));
        assertInstanceOf(AEProcessingPattern.class, decoded);
        var processingPattern = (AEProcessingPattern) decoded;
        assertEquals(2, processingPattern.getSparseInputs().length);
        assertEquals(0, processingPattern.getSparseOutputs().length);
    }

    @Test
    void testEncodeTunnelPatternRequiresInput() {
        assertThrows(IllegalArgumentException.class,
                () -> PatternDetailsHelper.encodeTunnelPattern(new GenericStack[] { null, null }, UUID.randomUUID(),
                        PatternInfo.EMPTY));
    }

    @Test
    void testNormalProcessingPatternIsNotTunnel() {
        ItemStack stack = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] { STICK }, new GenericStack[] { TORCH }, "tester");

        assertFalse(TunnelPatternItem.isTunnelPattern(stack));
        assertNull(TunnelPatternItem.getTunnelUuid(stack));
    }
}

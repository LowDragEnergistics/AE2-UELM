package appeng.crafting.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import appeng.core.definitions.AEItems;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class TunnelPatternItemTest {

    @Test
    void testIsTunnelPattern() {
        assertFalse(TunnelPatternItem.isTunnelPattern(null));
        assertFalse(TunnelPatternItem.isTunnelPattern(ItemStack.EMPTY));
        assertFalse(TunnelPatternItem.isTunnelPattern(new ItemStack(AEItems.PROCESSING_PATTERN)));
        assertTrue(TunnelPatternItem.isTunnelPattern(new ItemStack(AEItems.TUNNEL_PATTERN)));
    }

    @Test
    void testGetTunnelUuidWithoutTag() {
        assertNull(TunnelPatternItem.getTunnelUuid(new ItemStack(AEItems.TUNNEL_PATTERN)));
    }

    @Test
    void testGetTunnelUuidNotMarkedAsTunnel() {
        var stack = new ItemStack(AEItems.TUNNEL_PATTERN);
        stack.getOrCreateTag().putString(TunnelPatternItem.TAG_TUNNEL_UUID, UUID.randomUUID().toString());
        assertNull(TunnelPatternItem.getTunnelUuid(stack));
    }

    @Test
    void testGetTunnelUuidEmptyString() {
        var stack = new ItemStack(AEItems.TUNNEL_PATTERN);
        stack.getOrCreateTag().putBoolean(TunnelPatternItem.TAG_TUNNEL, true);
        stack.getOrCreateTag().putString(TunnelPatternItem.TAG_TUNNEL_UUID, "");
        assertNull(TunnelPatternItem.getTunnelUuid(stack));
    }

    @Test
    void testGetTunnelUuidInvalidString() {
        var stack = new ItemStack(AEItems.TUNNEL_PATTERN);
        stack.getOrCreateTag().putBoolean(TunnelPatternItem.TAG_TUNNEL, true);
        stack.getOrCreateTag().putString(TunnelPatternItem.TAG_TUNNEL_UUID, "not-a-uuid");
        assertNull(TunnelPatternItem.getTunnelUuid(stack));
    }

    @Test
    void testNonTunnelItemReturnsNull() {
        var stack = new ItemStack(AEItems.PROCESSING_PATTERN);
        stack.getOrCreateTag().putBoolean(TunnelPatternItem.TAG_TUNNEL, true);
        stack.getOrCreateTag().putString(TunnelPatternItem.TAG_TUNNEL_UUID, UUID.randomUUID().toString());
        assertNull(TunnelPatternItem.getTunnelUuid(stack));
    }

    @Test
    void testWriteAndReadTunnelUuid() {
        var uuid = UUID.randomUUID();
        var tag = new CompoundTag();
        TunnelPatternItem.writeTunnelUuid(tag, uuid);

        assertTrue(tag.getBoolean(TunnelPatternItem.TAG_TUNNEL));
        assertEquals(uuid.toString(), tag.getString(TunnelPatternItem.TAG_TUNNEL_UUID));

        var stack = new ItemStack(AEItems.TUNNEL_PATTERN);
        stack.setTag(tag);
        assertEquals(uuid, TunnelPatternItem.getTunnelUuid(stack));
    }
}

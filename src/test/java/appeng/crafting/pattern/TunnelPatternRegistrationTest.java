package appeng.crafting.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import appeng.api.ids.AEItemIds;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEItems;
import appeng.util.BootstrapMinecraft;

@BootstrapMinecraft
class TunnelPatternRegistrationTest {

    @Test
    void testTunnelPatternRegistered() {
        assertTrue(AEItems.getItems().contains(AEItems.TUNNEL_PATTERN));
        assertEquals(AEItemIds.TUNNEL_PATTERN, AEItems.TUNNEL_PATTERN.id());
        assertInstanceOf(TunnelPatternItem.class, AEItems.TUNNEL_PATTERN.asItem());
    }

    @Test
    void testTunnelPatternStorableAsKey() {
        // The encoded tunnel pattern item must behave like any other item key in the ME storage data structures.
        var stack = TunnelPatternTestHelper.referenceItem(UUID.randomUUID());
        var key = AEItemKey.of(stack);

        var counter = new KeyCounter();
        counter.add(key, 1);
        assertEquals(1, counter.get(key));

        var roundTripped = key.toStack();
        assertEquals(stack.getItem(), roundTripped.getItem());
        assertEquals(stack.getCount(), roundTripped.getCount());
        assertEquals(stack.getTag(), roundTripped.getTag());
    }

    @Test
    void testTunnelLangKeysPresent() throws IOException {
        // Generated en_us plus the source en_gb/zh_cn files must contain the tunnel pattern keys.
        assertLangKeys("/assets/ae2/lang/en_us.json", "item.ae2.tunnel_pattern",
                "gui.ae2.TunnelPatternInfo1", "gui.ae2.TunnelPatternInfo4", "gui.ae2.TunnelPatternUuid");
        for (var lang : List.of("en_gb", "zh_cn")) {
            assertLangKeys("/assets/ae2/lang/" + lang + ".json", "item.ae2.tunnel_pattern",
                    "gui.ae2.TunnelPatternInfo1", "gui.ae2.TunnelPatternUuid");
        }
    }

    private static void assertLangKeys(String resource, String... keys) throws IOException {
        String content;
        try (InputStream in = TunnelPatternRegistrationTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (var key : keys) {
            assertTrue(content.contains("\"" + key + "\""), "Missing key " + key + " in " + resource);
        }
    }
}

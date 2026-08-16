package appeng.blockentity.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.inventory.MenuType;

import appeng.api.ids.AEBlockIds;
import appeng.block.misc.SelfLoopMatrixBlock;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEBlocks;
import appeng.menu.implementations.SelfLoopMatrixMenu;
import appeng.util.BootstrapMinecraft;

/**
 * Registration checks for the ME Self-Loop Matrix device.
 */
@BootstrapMinecraft
class SelfLoopMatrixRegistrationTest {

    @Test
    void testBlockRegistered() {
        assertTrue(AEBlocks.getBlocks().contains(AEBlocks.SELF_LOOP_MATRIX));
        assertEquals(AEBlockIds.SELF_LOOP_MATRIX, AEBlocks.SELF_LOOP_MATRIX.id());
        assertInstanceOf(SelfLoopMatrixBlock.class, AEBlocks.SELF_LOOP_MATRIX.block());
    }

    @Test
    void testBlockEntityRegistered() {
        assertNotNull(AEBlockEntities.SELF_LOOP_MATRIX);
        assertEquals("self_loop_matrix", AEBlockEntities.getBlockEntityTypes().keySet().stream()
                .filter(id -> id.getPath().equals("self_loop_matrix")).findFirst().orElseThrow().getPath());
    }

    @Test
    void testMenuTypeRegistered() {
        var type = SelfLoopMatrixMenu.TYPE;
        assertInstanceOf(MenuType.class, type);
    }

    @Test
    void testLangKeysPresent() throws IOException {
        // The generated en_us (from the datagen LocalizationProvider) and the source zh_cn must
        // contain the matrix keys. en_gb is a British-English override file without block names.
        for (var lang : List.of("en_us", "zh_cn")) {
            String content;
            try (InputStream in = SelfLoopMatrixRegistrationTest.class
                    .getResourceAsStream("/assets/ae2/lang/" + lang + ".json")) {
                assertNotNull(in, lang);
                content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertTrue(content.contains("\"block.ae2.self_loop_matrix\""), "Missing block name in " + lang);
            assertTrue(content.contains("\"gui.ae2.SelfLoopMatrix\""), "Missing gui title in " + lang);
        }
    }
}

package appeng.me.cells;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageCells;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.BaseActionSource;
import appeng.me.storage.NetworkStorage;
import appeng.util.BootstrapMinecraft;

/**
 * Verifies that tunnel patterns can be stored in ME storage (cells mounted on the network storage), like any other
 * encoded pattern.
 */
@BootstrapMinecraft
public class TunnelStorageTest {
    private static final IActionSource SRC = new BaseActionSource();

    @BeforeAll
    static void initCells() {
        CellTestUtil.initCells();
    }

    @Test
    void testTunnelPatternStorableInNetworkStorage() {
        var cell = StorageCells.getCellInventory(new ItemStack(AEItems.ITEM_CELL_1K.asItem()), null);
        Objects.requireNonNull(cell);

        var storage = new NetworkStorage();
        storage.mount(1, cell);

        var tunnel = PatternDetailsHelper.encodeTunnelPattern(
                new GenericStack[] { GenericStack.fromItemStack(new ItemStack(Items.STICK)) },
                UUID.randomUUID(), "test");

        assertThat(storage.insert(AEItemKey.of(tunnel), 1, Actionable.MODULATE, SRC)).isEqualTo(1);
        assertThat(storage.extract(AEItemKey.of(tunnel), 1, Actionable.MODULATE, SRC)).isEqualTo(1);
    }
}

package appeng.me.cells;

import net.minecraftforge.registries.ForgeRegistries;

import appeng.init.InitItems;
import appeng.init.internal.InitStorageCells;
import appeng.init.internal.InitUpgrades;

/**
 * Initializes the cell handlers/upgrades exactly once per JVM. {@link InitStorageCells#init()} is not idempotent (it
 * registers singleton handlers and throws on double registration), so test classes that need cells share this helper
 * instead of calling the init methods directly.
 */
public final class CellTestUtil {
    private static boolean initialized = false;

    private CellTestUtil() {
    }

    public static void initCells() {
        if (initialized) {
            return;
        }
        InitItems.init(ForgeRegistries.ITEMS);
        InitStorageCells.init();
        InitUpgrades.init();
        initialized = true;
    }
}

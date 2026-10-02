package com.barbwra.mlum.compat;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * Sell prices, read out of MlumShop without depending on it.
 *
 * <h2>Reflection, and why</h2>
 * <p>The two mods ship separately and either can be installed without the other, so a hard
 * dependency would make the bag refuse to load on a server that has no shop. The pattern is the
 * same one {@link TaczCompat} uses for firearms: look the class up once, cache the {@link Method},
 * and make every failure look like "there is no price" rather than like a crash. A pack without
 * MlumShop gets the panel's {@code ما ينباع عند التاجر} line and nothing else changes.</p>
 *
 * <h2>Resolved once, never retried</h2>
 * <p>{@link #resolve()} runs at most once per launch. Mods cannot appear mid-session, so a second
 * attempt could only ever produce the same answer - and retrying on every hover would put a
 * {@code Class.forName} miss on the render path, which is the one place it must not be.</p>
 */
public final class ShopCompat {

    private ShopCompat() {
    }

    public static final String MODID = "mshop";
    private static final String API = "com.barbwra.mshop.api.ShopPriceApi";

    /** Mirrors {@code ShopPriceApi.NOT_SELLABLE} so callers never need the other mod's constant. */
    public static final double NOT_SELLABLE = -1.0D;

    private static boolean resolved;
    private static Method sellPricePerItem;

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> api = Class.forName(API);
            sellPricePerItem = api.getMethod("sellPricePerItem", MinecraftServer.class, ItemStack.class);
            MlumInventory.LOGGER.info("[{}] MlumShop found - the details panel will show sell prices",
                    MlumInventory.MODID);
        } catch (ClassNotFoundException absent) {
            // The overwhelmingly common case on a pack without the shop. Not worth a warning.
            MlumInventory.LOGGER.debug("[{}] MlumShop not present - no sell prices",
                    MlumInventory.MODID);
        } catch (Exception mismatch) {
            // Present but the contract moved. This one IS worth shouting about: it means the two
            // mods have drifted and every price in the bag has silently gone blank.
            MlumInventory.LOGGER.warn("[{}] MlumShop is installed but {}.sellPricePerItem could not "
                    + "be bound - prices will not show", MlumInventory.MODID, API, mismatch);
        }
    }

    public static boolean available() {
        resolve();
        return sellPricePerItem != null;
    }

    /**
     * Money a trader pays for one of these, or {@link #NOT_SELLABLE}.
     *
     * <p>Server side: prices live in world save data. The bag calls this while building the packet
     * that feeds the details panel, so the client is handed a number rather than a lookup.</p>
     */
    public static double sellPricePerItem(MinecraftServer server, ItemStack stack) {
        resolve();
        if (sellPricePerItem == null || server == null || stack == null || stack.isEmpty()) {
            return NOT_SELLABLE;
        }
        try {
            Object result = sellPricePerItem.invoke(null, server, stack);
            return result instanceof Double price ? price : NOT_SELLABLE;
        } catch (Exception failed) {
            // One broken lookup must not take the bag down with it.
            MlumInventory.LOGGER.warn("[{}] MlumShop price lookup failed for '{}'",
                    MlumInventory.MODID, stack, failed);
            return NOT_SELLABLE;
        }
    }
}

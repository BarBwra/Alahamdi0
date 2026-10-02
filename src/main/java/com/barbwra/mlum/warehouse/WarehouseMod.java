package com.barbwra.mlum.warehouse;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.warehouse.catalog.Catalog;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * The warehouse subsystem's entry point, folded into MlumInventory.
 *
 * <p>This used to be its own {@code @Mod} class in a separate jar. It is no longer a mod - there is
 * exactly one {@code @Mod} in this project ({@link MlumInventory}) and this is a subsystem it owns.
 * The constants below delegate so that the several dozen warehouse classes referring to
 * {@code WarehouseMod.LOGGER}, {@code WarehouseMod.MODID} and {@code WarehouseMod.id(...)} keep
 * working unchanged.</p>
 *
 * <p>{@link #MODID} has to stay a compile-time constant, because
 * {@code @Mod.EventBusSubscriber(modid = WarehouseMod.MODID)} needs one. A {@code static final
 * String} initialised from another constant qualifies, so the annotations on the warehouse event
 * classes still resolve.</p>
 *
 * <p><b>The loop.</b> A player is granted a warehouse by console command, assembles contraband
 * crates on real-time production lines, stores them under a shelf-life clock, then hand-carries a
 * convoy across the map. Two minutes in, the run leaks and their live position is broadcast to
 * everyone; ten minutes in, the cargo is forfeit.</p>
 *
 * <p><b>The rule that shapes the code.</b> The client is a renderer and nothing else. Every intent
 * it sends is re-validated against the server's own model - ownership, rate limit, and then the
 * rule the action itself depends on. Nothing the client says is evidence of anything.</p>
 *
 * <p><b>Time is absolute.</b> Craft completions, leak and expiry deadlines, shelf life and market
 * samples are epoch milliseconds compared against wall-clock now. Nothing counts down in a tick
 * loop, so a restart or a two-hour outage cannot rewind or freeze a timer.</p>
 */
public final class WarehouseMod {

    private WarehouseMod() {
    }

    public static final String MODID = MlumInventory.MODID;
    public static final Logger LOGGER = MlumInventory.LOGGER;

    public static ResourceLocation id(String path) {
        return MlumInventory.id(path);
    }

    /**
     * Called from {@link MlumInventory}'s common setup, already on the main thread.
     *
     * <p>Registers the warehouse packet channel and loads the JSON catalog. Kept separate from
     * MlumInventory's own setup so the two subsystems fail independently - a broken recipes.json
     * must not stop the inventory screen from registering its packets.</p>
     */
    public static void setup() {
        com.barbwra.mlum.warehouse.net.ModNetwork.register();
        Catalog.get().loadOrCreateDefaults();
        LOGGER.info("[mlum/warehouse] Ready - {} recipes, {} ingredients.",
                Catalog.get().recipes().size(), Catalog.get().ingredients().size());
    }
}

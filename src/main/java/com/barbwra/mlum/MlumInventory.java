package com.barbwra.mlum;

import com.barbwra.mlum.bag.BagConfig;
import com.barbwra.mlum.menu.ModMenus;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.warehouse.WarehouseConfig;
import com.barbwra.mlum.warehouse.WarehouseMod;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * MlumInventory - a full-screen survival UI in place of the vanilla inventory.
 *
 * <p>Six menus share one frame (top bar with the tabs, wallet and zone; footer with the keys): the
 * bag, quests, level, skills, vehicles and faction. They are drawn by the mod's own UI layer in
 * {@code client.ui} - its own fonts, its own layout engine, one texture atlas - so they look the
 * same on every machine and resource pack. A/D moves between them from anywhere; chests and the
 * faction vault open in the bag, beside it.</p>
 *
 * <p>The server owns every rule: bag moves, money ({@code /mlum_inventory money}), skills, quests
 * (fed by Skript through {@code /mlum quest}), vehicles and factions. The client only draws what it
 * is told and asks for things.</p>
 */
@Mod(MlumInventory.MODID)
public class MlumInventory {

    public static final String MODID = "mlum";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MlumInventory() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModMenus.MENUS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        com.barbwra.mlum.downed.ModEntities.ENTITIES.register(modBus);
        if (net.minecraftforge.fml.ModList.get().isLoaded("tacz")) {
            // only names TACZ's event classes when TACZ is there to load them
            com.barbwra.mlum.downed.DownedTacz.register();
            com.barbwra.mlum.compat.TaczVehicleDamage.register();
        }
        modBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, MlumConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, MlumConfig.CLIENT_SPEC);

        /*
         * The warehouse subsystem's configs must be registered under explicit filenames.
         * Forge keys ModConfig by (modId, type), so a second unnamed SERVER config for "mlum"
         * would collide with the one above - the merge's only genuine incompatibility, and the
         * reason these are mlum-warehouse-*.toml rather than folding into mlum-server.toml.
         * Keeping them in separate files also means warehouse balance can be edited, broken and
         * reverted without touching the inventory UI's settings.
         */
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,
                WarehouseConfig.SERVER_SPEC, "mlum-warehouse-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,
                WarehouseConfig.CLIENT_SPEC, "mlum-warehouse-client.toml");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModNetwork.register();
            WarehouseMod.setup();
            /*
             * The bag config is read here rather than through ModLoadingContext because it is a
             * plain TOML file with item ids for keys, not a ForgeConfigSpec - see BagConfig for why
             * an open map cannot be expressed as a spec. Loading in commonSetup means the file
             * exists before any world does, and /mlum_inventory reload calls the same method.
             */
            BagConfig.load();
            LOGGER.info("[{}] MlumInventory ready.", MODID);
        });
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}

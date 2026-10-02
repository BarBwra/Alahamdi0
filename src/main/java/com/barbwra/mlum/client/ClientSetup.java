package com.barbwra.mlum.client;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.client.ui.mc.BagScreen;
import com.barbwra.mlum.client.hud.TaczGunHud;
import com.barbwra.mlum.client.hud.WeaponCard;
import com.barbwra.mlum.menu.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = MlumInventory.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // the bag, a chest beside the bag, a faction vault page beside the bag - one screen
            MenuScreens.register(ModMenus.MAIN.get(), BagScreen::new);
        });
    }

    /**
     * The gun packs' artwork can move on a resource reload - a different pack, or just F3+T - so
     * the firearm card's lookup cache is dropped whenever that happens.
     */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            TaczGunHud.clearCache();
            com.barbwra.mlum.client.gui.VehicleArt.clearCache();
            com.barbwra.mlum.client.ui.mc.McImage.clearCache();
            com.barbwra.mlum.client.ui.mc.McItems.clear();
        });
    }

    /**
     * The firearm card, anchored above the vanilla hotbar in the draw order.
     *
     * <p>Anchored to the hotbar rather than to the chat panel so it comes and goes in exactly the
     * situations the hotbar itself does.</p>
     */
    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "mlum_firearm_card",
                (gui, graphics, partialTick, width, height) -> {
                    if (MlumConfig.firearmCard()) {
                        WeaponCard.render(graphics, partialTick, width, height);
                    }
                });
        // the vehicle's lock state, over the vehicle mod's own seat list
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "mlum_vehicle_lock",
                (gui, graphics, partialTick, width, height) ->
                        com.barbwra.mlum.client.hud.VehicleLockHud.render(graphics, width, height));
    }
}

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
        // the reload skill is read on both sides; this is how the common half learns this client's level
        com.barbwra.mlum.skill.ReloadSkill.clientLevel = e -> e == net.minecraft.client.Minecraft.getInstance().player
                ? ClientSkills.levelOf(com.barbwra.mlum.skill.SkillEffects.ATTACHMENTS) : 0;
    }

    /**
     * The gun packs' artwork can move on a resource reload - a different pack, or just F3+T - so
     * the firearm card's lookup cache is dropped whenever that happens.
     */
    @SubscribeEvent
    public static void onRegisterRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.barbwra.mlum.downed.ModEntities.DOWNED_DUMMY.get(),
                com.barbwra.mlum.client.downed.DownedDummyRenderer::new);
        event.registerEntityRenderer(net.minecraft.world.entity.EntityType.ITEM,
                com.barbwra.mlum.client.feel.FlatItemRenderer::new);
    }

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
                    // the field HUD has its own weapon panel in the same corner
                    if (MlumConfig.firearmCard() && !MlumConfig.fieldHud()) {
                        WeaponCard.render(graphics, partialTick, width, height);
                    }
                });
        // the field HUD: wrist device, belt, weapon panel and compass - see FieldHud
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "mlum_field_hud",
                (gui, graphics, partialTick, width, height) ->
                        com.barbwra.mlum.client.hud.field.FieldHud.render(graphics, partialTick, width, height));
        // marks on the containers in view, under everything else on the HUD
        event.registerBelow(VanillaGuiOverlay.CROSSHAIR.id(), "mlum_loot_markers",
                (gui, graphics, partialTick, width, height) ->
                        com.barbwra.mlum.client.loot.LootMarkers.render(graphics, partialTick, width, height));
        // the search spinner, over the crosshair it sits under
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "mlum_loot_search",
                (gui, graphics, partialTick, width, height) ->
                        com.barbwra.mlum.client.loot.ClientLootSearch.render(graphics, partialTick, width, height));
        // the downed player's own screen, over everything
        event.registerAboveAll("mlum_downed",
                (gui, graphics, partialTick, width, height) ->
                        com.barbwra.mlum.client.downed.DownedHud.renderSelf(graphics, partialTick, width, height));
        // the options over a downed body, and faction members calling for help
        event.registerBelow(VanillaGuiOverlay.CROSSHAIR.id(), "mlum_downed_prompt",
                (gui, graphics, partialTick, width, height) ->
                        com.barbwra.mlum.client.downed.DownedHud.renderPrompt(graphics, partialTick, width, height));
        // the vehicle's lock state, over the vehicle mod's own seat list
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "mlum_vehicle_lock",
                (gui, graphics, partialTick, width, height) ->
                        com.barbwra.mlum.client.hud.VehicleLockHud.render(graphics, width, height));
    }
}

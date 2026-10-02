package com.barbwra.mlum.warehouse.events;

import com.barbwra.mlum.warehouse.WarehouseMod;
import com.barbwra.mlum.warehouse.client.ClientMissionData;
import com.barbwra.mlum.warehouse.client.hud.MissionHud;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side hooks: drawing the mission HUD and clearing it when the world goes away.
 *
 * <p>The HUD is drawn after the hotbar so it layers over the world but under chat and the debug
 * screen, and it is skipped entirely while the F1 interface toggle is off or a screen is open -
 * a delivery timer painted over an inventory would be worse than no timer.</p>
 */
@Mod.EventBusSubscriber(modid = WarehouseMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        if (com.barbwra.mlum.client.hud.HudVisibility.hidden()) {
            return;
        }
        MissionHud.render(event.getGuiGraphics(),
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight());
    }

    /**
     * Drops stale mission state on disconnect.
     *
     * <p>Without this, logging out mid-run and joining a different world would keep drawing the old
     * HUD - a countdown to a deadline on a server you are no longer connected to.</p>
     *
     * <p>This has to be {@link ClientPlayerNetworkEvent.LoggingOut} rather than the more obvious
     * {@code PlayerEvent.PlayerLoggedOutEvent}: the latter only ever fires on the logical server,
     * so on a dedicated server it would compile, register, and simply never run.</p>
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMissionData.clear();
    }
}

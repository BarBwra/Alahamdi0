package com.barbwra.mlum.camo;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client half of the ghillie suit.
 *
 * <p><b>Drawing.</b> Vanilla keeps drawing armour, held items and every render layer - the Curios
 * backpack on the back among them - on an invisible player. For a player hidden by their suit the
 * whole render is skipped instead, so nothing of them shows. That includes yourself in third
 * person: hidden means hidden.</p>
 *
 * <p><b>The count.</b> The server decides when you vanish; this runs the same rule locally only so
 * the HUD can show how long is left without waiting on a packet (see {@code FieldHud}).</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class GhillieClient {

    private GhillieClient() {
    }

    private static Ghillie.Settle settle;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        // the bag screen's own portrait is drawn regardless, so you can still see what you wear
        if (player.isInvisible() && Ghillie.suitOf(player) != null
                && !com.barbwra.mlum.client.downed.DownedClientEvents.portrait) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        settle = player == null ? null : Ghillie.step(player, settle, player.level().getGameTime());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        settle = null;
    }

    /** Wearing a full suit right now. */
    public static boolean wearing() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && Ghillie.suitOf(player) != null;
    }

    /** Hidden by the suit - what the server last said. */
    public static boolean hidden() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.isInvisible() && Ghillie.suitOf(player) != null;
    }

    /** 0..1 of the way there while crouched and still; 0 when not settling. */
    public static float progress() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0.0F : Ghillie.progress(settle, player.level().getGameTime());
    }

    public static boolean settling() {
        return settle != null;
    }
}

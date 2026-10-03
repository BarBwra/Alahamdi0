package com.barbwra.mlum.camo;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The other half of a hidden ghillie wearer: vanilla leaves armour and held items visible on an
 * invisible player, so for a player hidden by their suit nothing of them is drawn at all.
 *
 * <p>Only for other players. Your own body in third person still shows your armour the way an
 * invisibility potion does, so you can tell you are hidden without losing track of yourself.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class GhillieClient {

    private GhillieClient() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (player.isInvisible() && player != Minecraft.getInstance().player && Ghillie.suitOf(player) != null) {
            event.setCanceled(true);
        }
    }
}

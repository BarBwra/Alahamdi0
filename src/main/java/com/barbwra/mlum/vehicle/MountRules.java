package com.barbwra.mlum.vehicle;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * A summoned mount answers only to the player it was summoned for.
 *
 * <p>Two doors have to be closed, not one. Cancelling the mount event alone still lets another
 * player open the horse's inventory and take the saddle and armour off it, at which point owning
 * the animal stops meaning much - so the interaction is refused as well.</p>
 *
 * <p>Mounts summoned before this existed, and any horse tamed the ordinary way, carry no owner tag
 * and are deliberately left alone. Retroactively locking every horse on the server would strand
 * animals players already share.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class MountRules {

    private MountRules() {
    }

    /** Rate-limits the refusal message so holding right-click is not a chat flood. */
    private static long lastNotice;

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (!event.isMounting() || event.getLevel().isClientSide) {
            return;
        }
        if (!(event.getEntityMounting() instanceof ServerPlayer rider)) {
            return;
        }
        if (isForbidden(event.getEntityBeingMounted(), rider)) {
            event.setCanceled(true);
            refuse(rider);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (isForbidden(event.getTarget(), player)) {
            event.setCanceled(true);
            refuse(player);
        }
    }

    /** True when this entity belongs to somebody else. */
    private static boolean isForbidden(Entity target, Player actor) {
        UUID owner = MountSetup.ownerOf(target);
        return owner != null && !owner.equals(actor.getUUID());
    }

    private static void refuse(ServerPlayer player) {
        long now = System.currentTimeMillis();
        if (now - lastNotice < 1500L) {
            return;
        }
        lastNotice = now;
        player.sendSystemMessage(Component.literal("§c✖ §fهذا الحصان يخص لاعباً آخر."));
    }
}

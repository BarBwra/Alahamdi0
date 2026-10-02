package com.barbwra.mlum.vehicle;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Notices when a summoned vehicle is destroyed, so the garage stops believing it is still out.
 *
 * <h2>Removed is not the same as gone</h2>
 * <p>{@link EntityLeaveLevelEvent} fires for every entity that stops being in a level, and most of
 * those are still perfectly alive - a chunk unloaded, a player walked away, something changed
 * dimension. Acting on all of them would forget a vehicle that is parked two hundred blocks away
 * and orphan it in the world with no way to recall or store it.</p>
 *
 * <p>{@link Entity#getRemovalReason()} is what separates the two. Only {@code KILLED} and
 * {@code DISCARDED} mean the entity will not be coming back.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class VehicleDestruction {

    private VehicleDestruction() {
    }

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason == null || (reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED)) {
            return;
        }
        // The owner stamp is on the entity, so this costs one NBT read for the handful of entities
        // that carry it and nothing at all for the thousands that do not.
        UUID owner = VehicleOwnership.ownerOf(entity);
        if (owner == null || event.getLevel().isClientSide() || entity.getServer() == null) {
            return;
        }
        ServerPlayer player = entity.getServer().getPlayerList().getPlayer(owner);
        if (player == null) {
            return;
        }
        UUID active = VehicleGarage.activeId(player);
        if (active != null && active.equals(entity.getUUID())) {
            VehicleGarage.onDestroyed(player);
        }
    }
}

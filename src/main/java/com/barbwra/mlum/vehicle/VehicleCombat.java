package com.barbwra.mlum.vehicle;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.compat.SbwCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Notices when a player's vehicle is hurt, by anything, so it cannot be stored mid-fight.
 *
 * <p>A Superb Warfare vehicle is not a living entity and fires no hurt event, and it takes damage
 * from many places - bullets, rockets, its own shells, crashes. Rather than hook every one, each
 * summoned vehicle's health is looked at twice a second; any drop puts it under the same lock a
 * player gets for PvP ({@link CombatTracker#tagVehicle}).</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class VehicleCombat {

    private VehicleCombat() {
    }

    private static final Map<UUID, Float> LAST = new HashMap<>();
    private static int tick;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 10 != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (VehicleGarage.activeId(player) == null) {
                continue;
            }
            Entity vehicle = VehicleGarage.findActive(player);
            if (vehicle == null) {
                continue;
            }
            float health = SbwCompat.health(vehicle);
            if (health < 0.0F) {
                continue;
            }
            Float before = LAST.put(vehicle.getUUID(), health);
            if (before != null && health < before - 0.01F) {
                CombatTracker.tagVehicle(vehicle);
            }
        }
        if (tick % 1200 == 0) {
            LAST.clear();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID active = VehicleGarage.activeId(player);
            if (active != null) {
                LAST.remove(active);
            }
        }
    }
}

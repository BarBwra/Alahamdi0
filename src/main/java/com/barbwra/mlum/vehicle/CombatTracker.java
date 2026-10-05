package com.barbwra.mlum.vehicle;

import com.barbwra.mlum.MlumConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The PvP lock that stops a player summoning a vehicle mid-fight, and escaping on it.
 *
 * <p>Two ways in, because a server usually has both: the mod tags a player automatically whenever
 * they damage or are damaged by another player, and {@code /VehicleMenu <player> lock <seconds>}
 * lets Skript impose a tag for anything the mod cannot see - a raid, a boss event, a safe zone
 * rule.</p>
 *
 * <p>Keyed on UUID and measured in game ticks, so a relog does not clear a tag mid-fight while the
 * server keeps running. The map is small - only tagged players are in it - and entries are dropped
 * lazily as they are read.</p>
 */
public final class CombatTracker {

    private CombatTracker() {
    }

    /** UUID -> the game tick at which the tag expires. */
    private static final Map<UUID, Long> LOCKED = new ConcurrentHashMap<>();

    public static void tag(Player player) {
        tagFor(player, MlumConfig.combatLockSeconds());
    }

    public static void tagFor(Player player, int seconds) {
        if (player == null || player.level().isClientSide || seconds <= 0) {
            return;
        }
        long until = player.level().getGameTime() + (long) seconds * 20L;
        LOCKED.merge(player.getUUID(), until, Math::max);
    }

    public static void clear(Player player) {
        if (player != null) {
            LOCKED.remove(player.getUUID());
        }
    }

    public static boolean isLocked(ServerPlayer player) {
        return remainingTicks(player) > 0L;
    }

    /** Zero when free. Also the value the UI counts down from. */
    public static long remainingTicks(ServerPlayer player) {
        if (player == null) {
            return 0L;
        }
        Long until = LOCKED.get(player.getUUID());
        if (until == null) {
            return 0L;
        }
        long left = until - player.level().getGameTime();
        if (left <= 0L) {
            LOCKED.remove(player.getUUID());
            return 0L;
        }
        return left;
    }

    /* ---- vehicles: one that has taken damage cannot be put away for a while ---- */

    /** Vehicle UUID -> the game tick at which its tag expires. */
    private static final Map<UUID, Long> VEHICLES = new ConcurrentHashMap<>();

    public static void tagVehicle(net.minecraft.world.entity.Entity vehicle) {
        int seconds = MlumConfig.combatLockSeconds();
        if (vehicle == null || vehicle.level().isClientSide || seconds <= 0) {
            return;
        }
        VEHICLES.merge(vehicle.getUUID(), vehicle.level().getGameTime() + seconds * 20L, Math::max);
    }

    /** Ticks until this vehicle may be stored; zero when free. */
    public static long vehicleRemaining(net.minecraft.world.entity.Entity vehicle) {
        if (vehicle == null) {
            return 0L;
        }
        Long until = VEHICLES.get(vehicle.getUUID());
        if (until == null) {
            return 0L;
        }
        long left = until - vehicle.level().getGameTime();
        if (left <= 0L) {
            VEHICLES.remove(vehicle.getUUID());
            return 0L;
        }
        return left;
    }

    /** Called when the server stops, so a restart never inherits stale ticks. */
    public static void reset() {
        LOCKED.clear();
        VEHICLES.clear();
    }
}

package com.barbwra.mlum.zone;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.network.ModNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Safe areas: no player hurts another inside one, and nothing hostile stays in one.
 *
 * <p>Three independent guarantees, deliberately not sharing a mechanism, because each fails in a
 * different way and one covering for another is what makes a safe zone actually safe:</p>
 * <ol>
 *   <li><b>Damage is cancelled</b> at {@link LivingAttackEvent}, the earliest point, so no knockback,
 *       no hurt animation and no damage tick ever happen.</li>
 *   <li><b>Spawns are denied</b> inside the volume.</li>
 *   <li><b>Anything living that gets in anyway is pushed back out</b> on a timer - which covers mobs
 *       that walked in, were summoned in, or spawned before the zone was configured.</li>
 * </ol>
 *
 * <p><b>Either side being inside blocks the shot.</b> Protecting only the victim would let someone
 * stand in spawn and fire out with impunity; protecting only the attacker would let them be sniped
 * while standing in it. Both directions are refused.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class SafeZoneRules {

    private SafeZoneRules() {
    }

    /** Ticks between repulsion sweeps and boundary checks. Four times a second. */
    private static final int SWEEP_TICKS = 5;

    /** Last zone each player was standing in, so a crossing can be detected. Empty = outside. */
    private static final Map<UUID, String> LAST_ZONE = new HashMap<>();

    private static int tickCounter;

    /* ------------------------------------------------------------------ startup */

    /**
     * Reports what actually parsed, at boot, in the server log.
     *
     * <p>Without this a mistyped or misplaced zone is completely silent: the feature simply does
     * nothing and there is no way to tell a bad coordinate from a config the game never read. One
     * line naming every loaded zone turns "is it on?" into a question the log already answers.</p>
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        List<MlumConfig.SafeZone> zones = MlumConfig.safeZones();
        if (zones.isEmpty()) {
            MlumInventory.LOGGER.info("[{}] No safe areas configured. Add them under [safearea] "
                    + "in <world>/serverconfig/mlum-server.toml", MlumInventory.MODID);
            return;
        }
        MlumInventory.LOGGER.info("[{}] {} safe area(s) loaded:", MlumInventory.MODID, zones.size());
        for (MlumConfig.SafeZone zone : zones) {
            MlumInventory.LOGGER.info("[{}]   '{}' at {}, {}, {} radius {} height {}",
                    MlumInventory.MODID, zone.name(),
                    (int) zone.x(), (int) zone.y(), (int) zone.z(),
                    (int) zone.radius(), (int) zone.height());
        }
    }

    /* ------------------------------------------------------------------ damage */

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (!MlumConfig.safeZoneBlockPvp()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim) || victim.level().isClientSide) {
            return;
        }

        // Resolves an arrow or a bullet back to whoever fired it, so a shot from outside at
        // someone standing inside is still refused.
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player shooter) || shooter.getUUID().equals(victim.getUUID())) {
            return;
        }

        if (inZone(victim) || inZone(shooter)) {
            event.setCanceled(true);
        }
    }

    private static boolean inZone(Entity entity) {
        return MlumConfig.safeZoneAt(entity.getX(), entity.getY(), entity.getZ()) != null;
    }

    /* ------------------------------------------------------------------ spawns */

    @SubscribeEvent
    public static void onSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!MlumConfig.safeZoneRepelMobs()) {
            return;
        }
        if (MlumConfig.safeZoneAt(event.getX(), event.getY(), event.getZ()) != null) {
            event.setSpawnCancelled(true);
        }
    }

    /* ------------------------------------------------------------------- sweep */

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter < SWEEP_TICKS) {
            return;
        }
        tickCounter = 0;

        List<MlumConfig.SafeZone> zones = MlumConfig.safeZones();
        if (zones.isEmpty()) {
            return;
        }

        MinecraftServer server = event.getServer();
        notifyCrossings(server, zones);

        if (MlumConfig.safeZoneRepelMobs()) {
            for (ServerLevel level : server.getAllLevels()) {
                for (MlumConfig.SafeZone zone : zones) {
                    repel(level, zone);
                }
            }
        }
    }

    /**
     * Sends the popup to anyone who crossed a boundary since the last sweep.
     *
     * <p>Tracked by zone <i>name</i> rather than by a boolean, so walking directly from one zone
     * into an adjacent one reports the new zone instead of reporting nothing.</p>
     */
    private static void notifyCrossings(MinecraftServer server, List<MlumConfig.SafeZone> zones) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MlumConfig.SafeZone now = MlumConfig.safeZoneAt(player.getX(), player.getY(), player.getZ());
            String nowName = now == null ? "" : now.name();
            String was = LAST_ZONE.getOrDefault(player.getUUID(), "");

            if (nowName.equals(was)) {
                continue;
            }
            LAST_ZONE.put(player.getUUID(), nowName);

            if (!nowName.isEmpty()) {
                ModNetwork.sendZoneNotice(player, true, nowName);
            } else {
                ModNetwork.sendZoneNotice(player, false, was);
            }
        }
    }

    /**
     * Moves living non-players standing inside the zone to just outside its edge.
     *
     * <p><b>Nothing is ever deleted.</b> Killing what wanders in would quietly destroy a player's
     * tamed animals and any named mob a build relies on. Pushing preserves everything and still
     * empties the zone.</p>
     *
     * <p>Anything carrying a player is skipped, so riding a horse to spawn parks it at the edge
     * rather than teleporting the rider along with it.</p>
     */
    private static void repel(ServerLevel level, MlumConfig.SafeZone zone) {
        AABB box = new AABB(
                zone.x() - zone.radius(), zone.y() - zone.height(), zone.z() - zone.radius(),
                zone.x() + zone.radius(), zone.y() + zone.height(), zone.z() + zone.radius());

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity instanceof Player || carriesPlayer(entity)) {
                continue;
            }
            if (!zone.contains(entity.getX(), entity.getY(), entity.getZ())) {
                continue;   // inside the box but outside the cylinder
            }

            double[] edge = zone.pushOut(entity.getX(), entity.getZ());
            entity.teleportTo(edge[0], entity.getY(), edge[1]);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.2D));
            // Drops any pathing that was walking it back in.
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                mob.getNavigation().stop();
                mob.setTarget(null);
            }
        }
    }

    private static boolean carriesPlayer(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player) {
                return true;
            }
        }
        return false;
    }

    /* ---------------------------------------------------------------- cleanup */

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_ZONE.remove(event.getEntity().getUUID());
    }

    /**
     * Forgets everyone on login so the first sweep re-announces the zone they are standing in.
     *
     * <p>Without this, logging out inside a safe zone and back in would leave the client with no
     * popup and no indication it is protected.</p>
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        LAST_ZONE.remove(event.getEntity().getUUID());
    }
}

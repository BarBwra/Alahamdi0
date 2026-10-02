package com.barbwra.mlum.zone;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A player reading a menu is not a target.
 *
 * <h2>The rule</h2>
 * <p>While one of this mod's menus is open, a hostile mob may not <i>pick</i> that player - but only
 * if nothing is already on top of them. If a zombie is within {@code engagedRadius} at the moment it
 * tries, the guard stands aside and the fight carries on. That is the difference between protecting
 * someone who stepped away from the keyboard and handing everyone an invulnerability button: you
 * cannot open the bag to escape a fight you are already in, because the thing you are escaping is
 * exactly what switches the guard off.</p>
 *
 * <h2>Why the client has to say so</h2>
 * <p>The bag is a real container and the server can see it on {@code containerMenu}. The other five
 * tabs are plain screens that never touch the server, so it has no way to know they are up. The
 * client says so and keeps saying so; a flag that is not refreshed goes stale on its own, so a
 * crash, a kick or a lost connection cannot leave a player permanently unattackable.</p>
 *
 * <p><b>A modified client can lie about this.</b> It is worth stating plainly. The exploit is small
 * by construction - it cannot be used once anything is close enough to matter - but on a public
 * server, {@code menuGuard} in the config turns the whole thing off.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class MenuGuard {

    private MenuGuard() {
    }

    /** Ticks a client's "my menu is open" claim stays good for. The client re-sends well inside it. */
    private static final long STALE_TICKS = 100L;

    private static final Map<UUID, Long> OPEN = new ConcurrentHashMap<>();

    /** Called by the packet the client sends when a menu opens, closes, or is still open. */
    public static void set(ServerPlayer player, boolean open) {
        if (player == null) {
            return;
        }
        if (open) {
            OPEN.put(player.getUUID(), player.level().getGameTime());
        } else {
            OPEN.remove(player.getUUID());
        }
    }

    public static void forget(ServerPlayer player) {
        if (player != null) {
            OPEN.remove(player.getUUID());
        }
    }

    /** True when this player has a menu up, by their own word or by the container they are holding. */
    private static boolean inMenu(ServerPlayer player) {
        if (player.containerMenu instanceof com.barbwra.mlum.menu.MlumMenu) {
            return true;
        }
        Long at = OPEN.get(player.getUUID());
        return at != null && player.level().getGameTime() - at <= STALE_TICKS;
    }

    /** True when something hostile is already close enough that the player is plainly in a fight. */
    private static boolean engaged(ServerPlayer player, Mob asker) {
        int radius = MlumConfig.menuGuardRadius();
        if (radius <= 0) {
            return false;
        }
        AABB box = player.getBoundingBox().inflate(radius);
        double radiusSq = (double) radius * radius;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box,
                m -> m instanceof Enemy && m.isAlive())) {
            if (mob != asker && mob.distanceToSqr(player) <= radiusSq) {
                return true;
            }
        }
        // the one asking counts too - a zombie that walked up to them is an engagement
        return asker != null && asker.isAlive() && asker.distanceToSqr(player) <= radiusSq;
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!MlumConfig.menuGuard()) {
            return;
        }
        LivingEntity target = event.getNewTarget();
        if (!(target instanceof ServerPlayer player) || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (!(mob instanceof Enemy) || player.isSpectator() || player.isCreative()) {
            return;
        }
        if (!inMenu(player) || engaged(player, mob)) {
            return;
        }
        event.setCanceled(true);
    }

    /**
     * Also clears a target a mob already holds.
     *
     * <p>{@link LivingChangeTargetEvent} only fires when the target <i>changes</i>. A mob that locked
     * on before the menu opened would keep coming forever, because nothing ever asks again - so the
     * held target is dropped here as well, once the player is out of the engaged radius.</p>
     */
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END
                || !MlumConfig.menuGuard() || OPEN.isEmpty()) {
            return;
        }
        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isRemoved() || !inMenu(player) || engaged(player, null)) {
                continue;
            }
            AABB box = player.getBoundingBox().inflate(48.0D);
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box,
                    m -> m instanceof Enemy && m.isAlive())) {
                Entity held = mob.getTarget();
                if (held == player) {
                    mob.setTarget(null);
                }
            }
        }
    }
}

package com.barbwra.mlum.faction;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.level.XpBoost;
import com.barbwra.mlum.util.ArabicText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Where faction points come from.
 *
 * <p>Rates, as specified: a zombie is one point, or two for a VIP; killing a player is twenty-five;
 * being killed by a player costs the victim's faction ten, floored at zero by
 * {@link Faction#addPoints}.</p>
 *
 * <p><b>Kill credit is resolved the same way the XP system resolves it</b> - causing entity, then
 * projectile owner, then the game's own record. That third-hand walk is what makes a TACZ kill count
 * at all: the thing that dealt the blow is a bullet, and a bullet is not a player. Getting this
 * wrong is exactly the bug the Skript version of the XP system shipped with.</p>
 *
 * <p><b>Both halves of a player kill are handled in one event.</b> A single death awards the
 * killer's faction and charges the victim's, so the two can never disagree about whether a kill
 * happened - which they could if the penalty rode on a separate respawn or hurt event.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class FactionPoints {

    private FactionPoints() {
    }

    public static final int ZOMBIE = 1;
    public static final int ZOMBIE_VIP = 2;
    public static final int PLAYER_KILL = 25;
    public static final int DEATH_PENALTY = 10;

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || victim.getServer() == null) {
            return;
        }

        FactionData data = FactionData.get(victim.getServer());
        ServerPlayer killer = killerOf(victim, event.getSource());

        if (victim instanceof ServerPlayer victimPlayer) {
            // Only a player kill costs points. Dying to a zombie, a fall or the world is free -
            // otherwise a faction would bleed score for playing the game at all.
            if (killer == null || killer.getUUID().equals(victimPlayer.getUUID())) {
                return;
            }
            award(data, killer, PLAYER_KILL);
            charge(data, victimPlayer);
            return;
        }

        if (killer != null && victim instanceof Enemy) {
            award(data, killer, XpBoost.isVip(killer) ? ZOMBIE_VIP : ZOMBIE);
        }
    }

    /** Credits a kill to the killer's faction, doing nothing if they have none. */
    private static void award(FactionData data, ServerPlayer killer, int amount) {
        Faction faction = data.of(killer.getUUID());
        if (faction == null) {
            return;
        }
        int before = faction.level();
        faction.addPoints(killer.getUUID(), amount);
        data.setDirty();
        announceLevel(faction, before);
    }

    /** Charges the victim's faction, doing nothing if they have none. */
    private static void charge(FactionData data, ServerPlayer victim) {
        Faction faction = data.of(victim.getUUID());
        if (faction == null) {
            return;
        }
        faction.addPoints(null, -DEATH_PENALTY);
        data.setDirty();
    }

    /**
     * Placeholder for the level-up notice.
     *
     * <p>Kept as its own call so the point sources do not have to know how a level-up is announced,
     * and so adding the toast later touches one method rather than three.</p>
     */
    private static void announceLevel(Faction faction, int levelBefore) {
        if (faction.level() <= levelBefore) {
            return;
        }
        MlumInventory.LOGGER.info("Faction {} reached level {}", faction.name(), faction.level());
        // Everyone in the faction sees the new level and the widened vault immediately, rather than
        // the next time they happen to reopen the screen.
        for (UUID id : faction.members().keySet()) {
            ServerPlayer online = SERVER == null ? null : SERVER.getPlayerList().getPlayer(id);
            if (online != null) {
                online.sendSystemMessage(Component.literal(ArabicText.autoDisplay(
                                "منظمتك وصلت إلى المستوى " + faction.level()))
                        .withStyle(ChatFormatting.GOLD));
                FactionService.sync(online);
            }
        }
    }

    /**
     * The running server, captured on start.
     *
     * <p>{@link LivingDeathEvent} can fire for an entity whose {@code getServer()} is momentarily
     * unhelpful, and the announcement needs the player list rather than the dying entity's level.</p>
     */
    private static volatile MinecraftServer SERVER;

    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        SERVER = event.getServer();
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        SERVER = null;
    }

    /**
     * The player who should be credited with this death, or null.
     *
     * <ol>
     *   <li>The damage source's <i>causing</i> entity - already the shooter for a well-behaved
     *       projectile.</li>
     *   <li>The direct entity's owner, when the direct entity is a projectile that set no causing
     *       entity. This is the case that makes TACZ gunfire count.</li>
     *   <li>The game's own kill credit, which catches indirect kills such as a fall after a shot.</li>
     * </ol>
     */
    @Nullable
    private static ServerPlayer killerOf(LivingEntity victim, DamageSource source) {
        Entity causing = source.getEntity();
        if (causing instanceof ServerPlayer player) {
            return player;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        return victim.getKillCredit() instanceof ServerPlayer credited ? credited : null;
    }

    /** Keeps stored member names current, so the list renders for players who are offline. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            FactionData.get(player.getServer())
                    .refreshName(player.getUUID(), player.getGameProfile().getName());
            // The screen has no handshake of its own, so the first view rides in on login.
            FactionService.sync(player);
        }
    }

    /** Convenience for anything outside this class that needs a player's faction. */
    @Nullable
    public static Faction factionOf(@Nullable Player player) {
        if (player == null || player.getServer() == null) {
            return null;
        }
        return FactionData.get(player.getServer()).of(player.getUUID());
    }
}

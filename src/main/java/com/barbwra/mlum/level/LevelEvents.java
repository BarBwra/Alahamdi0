package com.barbwra.mlum.level;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * Where experience comes from, and the single place it is scaled.
 *
 * <p><b>The credit problem this exists to solve.</b> The Skript version of this awarded nothing for
 * a gun kill, because it asked what dealt the killing blow and got back a bullet. A projectile is
 * not a player, so the kill went uncredited. {@link #killerOf} walks the chain properly - the
 * damage source's <i>causing</i> entity first, then the projectile's owner, then the game's own
 * kill credit - so a headshot at forty blocks scores exactly like a melee kill.</p>
 *
 * <p><b>Vanilla experience is no longer suppressed.</b> The old version cancelled orb pickup
 * outright, because a furnace handing out experience would have desynced the mod's parallel point
 * total. There is no parallel total any more, so orbs, furnaces, breeding and bottles all work the
 * way the game intends - they simply arrive scaled like everything else.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class LevelEvents {

    private LevelEvents() {
    }

    /* ------------------------------------------------------------- the scaler */

    /**
     * Scales every experience gain, from any source, exactly once.
     *
     * <p>{@code XpChange} is fired by {@code Player.giveExperiencePoints}, which is the funnel every
     * source runs through - an orb, a furnace emptying, this mod's own kill award. Hooking it here
     * rather than at each award site is what guarantees the base rate and the 2x bonuses cannot be
     * bypassed by a source nobody remembered to update.</p>
     *
     * <p>Losses are left alone. A death penalty scaled by a bonus multiplier would mean the players
     * getting double experience also pay double for dying, which is the opposite of a reward.</p>
     */
    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        int amount = event.getAmount();
        if (amount <= 0 || event.getEntity().level().isClientSide) {
            return;
        }
        double factor = XpBoost.multiplierFor(event.getEntity());
        if (factor == 1.0D) {
            return;
        }
        // At least one point, so a harsh rate can never silently round a real reward to nothing.
        event.setAmount(Math.max(1, (int) Math.round(amount * factor)));
    }

    /**
     * Fires reward tiers and the level-up popup.
     *
     * <p>{@code LevelChange} covers every route a level can move: earning it, {@code /xp}, an
     * operator, or another mod. Because the level is now vanilla's own field rather than a mirror,
     * this sees all of them without a polling loop.</p>
     */
    @SubscribeEvent
    public static void onLevelChange(PlayerXpEvent.LevelChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevels() <= 0) {
            return;
        }
        // The event fires before the level is applied, so the new value is the sum.
        int newLevel = player.experienceLevel + event.getLevels();
        player.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                net.minecraft.sounds.SoundSource.MASTER, 0.6F, 1.2F);
        ModNetwork.sendLevelUp(player, newLevel);
        PlayerLevel.payRewards(player, newLevel);
    }

    /* ------------------------------------------------------------------ kills */

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        ServerPlayer killer = killerOf(victim, event.getSource());
        if (killer == null || killer.getUUID().equals(victim.getUUID())) {
            return;
        }

        if (victim instanceof Player) {
            // Losing experience for a player kill, not gaining it - the track rewards surviving the
            // world, and paying for murder would make farming other players the fastest route up.
            PlayerLevel.penalise(killer, MlumConfig.playerKillPenalty());
            return;
        }
        if (victim instanceof Enemy) {
            PlayerLevel.award(killer, MlumConfig.pointsPerMonster(), "monster kill");
        }
    }

    /**
     * The player who should be credited with this death, or null.
     *
     * <ol>
     *   <li>{@code getEntity()} - the <i>causing</i> entity. For a well-behaved projectile this is
     *       already the shooter, not the arrow.</li>
     *   <li>The direct entity's owner, when the direct entity is a projectile that did not set a
     *       causing entity. This is the case that fixes TACZ gunfire.</li>
     *   <li>{@code getKillCredit()} - the game's own record of who last hurt this entity, which
     *       catches indirect kills such as fall damage after a shot.</li>
     * </ol>
     */
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

    /* ------------------------------------------------------------------ mining */

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        Map<String, Integer> table = MlumConfig.minePoints();
        if (table.isEmpty()) {
            return;
        }

        BlockState state = event.getState();
        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) {
            return;
        }
        Integer points = table.get(id.toString());
        if (points != null && points > 0) {
            PlayerLevel.award(player, points, "mined " + id);
        }
    }

    /* ------------------------------------------------------------------- login */

    /** Pushes the reward track, which the client cannot know on its own. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerLevel.sync(player);
            // The mount screen has no open handshake of its own, so its state rides in on login
            // and is refreshed by every purchase.

        }
    }

    /**
     * Re-sends the track after a respawn.
     *
     * <p>Note there is no level restoration here any more. Vanilla decides what happens to
     * experience on death - {@code keepInventory}, the gamerule, whatever the pack sets - and the
     * mod no longer has an opinion it could impose by accident.</p>
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerLevel.sync(player);
        }
    }
}

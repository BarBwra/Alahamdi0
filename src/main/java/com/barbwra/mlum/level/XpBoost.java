package com.barbwra.mlum.level;

import com.barbwra.mlum.MlumConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * The experience multipliers: a permanent per-player one, and a temporary global one.
 *
 * <p>Both stack, and both multiply the same number - whatever a player was about to earn. That is
 * why there is only one place experience is ever scaled ({@link LevelEvents#onXpChange}); a bonus
 * applied at the award site instead would miss every source it did not know about.</p>
 *
 * <ul>
 *   <li><b>VIP</b> is a flag on the player, kept in the Forge persistent tag so it survives death,
 *       dimension changes and logout. Granted and revoked by command.</li>
 *   <li><b>Global</b> is a server-wide window with a deadline, held in memory. It is deliberately
 *       <i>not</i> persisted: a double-experience weekend that quietly survived a restart nobody
 *       remembered starting is worse than one an operator has to turn on again.</li>
 * </ul>
 */
public final class XpBoost {

    private XpBoost() {
    }

    private static final String KEY_VIP = "mlum:xp_vip";

    /** Wall-clock millis at which the global boost lapses. Zero means it is off. */
    private static long globalUntil;
    private static double globalFactor = 1.0D;

    private static CompoundTag root(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /* -------------------------------------------------------------------- vip */

    public static boolean isVip(Player player) {
        return root(player).getBoolean(KEY_VIP);
    }

    public static void setVip(Player player, boolean vip) {
        root(player).putBoolean(KEY_VIP, vip);
    }

    /* ----------------------------------------------------------------- global */

    /** Turns the global boost on for {@code seconds}. A non-positive duration turns it off. */
    public static void setGlobal(double factor, int seconds) {
        if (seconds <= 0 || factor <= 1.0D) {
            globalUntil = 0L;
            globalFactor = 1.0D;
            return;
        }
        globalFactor = factor;
        globalUntil = System.currentTimeMillis() + seconds * 1000L;
    }

    public static boolean globalActive() {
        return globalUntil > System.currentTimeMillis();
    }

    /** Seconds left on the global boost, or 0. */
    public static long globalSecondsLeft() {
        return globalActive() ? Math.max(0L, (globalUntil - System.currentTimeMillis()) / 1000L) : 0L;
    }

    public static double globalFactor() {
        return globalActive() ? globalFactor : 1.0D;
    }

    /* ------------------------------------------------------------ the product */

    /**
     * Everything that scales this player's experience, multiplied together.
     *
     * <p>The base rate is what makes levelling harder than vanilla - below 1.0 it slows the whole
     * curve down without touching the curve itself, so enchanting costs and every other system that
     * reads a level still behave exactly as players expect.</p>
     */
    public static double multiplierFor(Player player) {
        double factor = MlumConfig.xpRate();
        if (isVip(player)) {
            factor *= MlumConfig.vipXpFactor();
        }
        factor *= globalFactor();
        return factor;
    }

    /** Just the bonus part, for display - what the player is getting above the base rate. */
    public static double bonusFor(Player player) {
        double bonus = 1.0D;
        if (isVip(player)) {
            bonus *= MlumConfig.vipXpFactor();
        }
        bonus *= globalFactor();
        return bonus;
    }

    /** Dropped when a server stops, so a boost never leaks into the next run. */
    public static void clear() {
        globalUntil = 0L;
        globalFactor = 1.0D;
    }
}

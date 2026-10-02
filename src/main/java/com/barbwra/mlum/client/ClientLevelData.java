package com.barbwra.mlum.client;

import com.barbwra.mlum.network.S2CLevelState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The client's copy of the progression track, plus the animation state for a level-up.
 *
 * <p>The bar eases toward its target rather than snapping, and a level increase is detected here by
 * comparing against the previous packet - so the flourish fires on the exact frame the client
 * learns about it, without the server needing to send a separate "you levelled" message.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientLevelData {

    private ClientLevelData() {
    }

    private static int points;
    private static int level;
    private static int inLevel;
    private static int costOfNext;
    private static java.util.List<S2CLevelState.Tier> track = java.util.List.of();
    private static java.util.List<S2CLevelState.Rule> rules = java.util.List.of();

    /** Eased display value for the bar, 0..1. */
    private static float shownFraction;
    private static long levelUpAt;

    public static void accept(S2CLevelState state) {
        if (state.level() > level) {
            levelUpAt = System.currentTimeMillis();
            // A level up resets the bar to empty so it visibly refills, instead of jumping
            // backwards from full to a fraction with no explanation.
            shownFraction = 0.0F;
        }
        points = state.points();
        level = state.level();
        inLevel = state.inLevel();
        costOfNext = state.costOfNext();
        track = state.track();
        rules = state.rules();
    }

    /** How points are earned, as the server's config says - the level tab's rule list. */
    public static java.util.List<S2CLevelState.Rule> rules() {
        return rules;
    }

    /** The reward track, sorted by level. Empty until the first packet arrives. */
    public static java.util.List<S2CLevelState.Tier> track() {
        return track;
    }

    public static void clear() {
        points = 0;
        level = 0;
        inLevel = 0;
        costOfNext = 0;
        shownFraction = 0.0F;
        levelUpAt = 0L;
        lastSeenLevel = -1;
        rules = java.util.List.of();
        track = java.util.List.of();
        BAR.reset();
    }

    public static int points() {
        return points;
    }

    /*
     * Level and progress are read off the local player, not off the last packet.
     *
     * Since the rewrite there is no separate progression number to be told about - the level IS
     * vanilla's experienceLevel, and vanilla already keeps the client's copy in step through its
     * own experience packet. Reading it here means the bar cannot lag behind or disagree with what
     * /xp just did, which is exactly the class of bug the rewrite existed to remove. The packet is
     * still received, but only the reward track in it is used.
     */
    public static int level() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player == null ? level : player.experienceLevel;
    }

    public static int inLevel() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return inLevel;
        }
        return Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    public static int costOfNext() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player == null ? costOfNext : player.getXpNeededForNextLevel();
    }

    /**
     * How full the bar should be.
     *
     * <p><b>Reads the accessors, not the fields.</b> {@code inLevel} and {@code costOfNext} are the
     * last values a packet carried, and since progression moved onto vanilla experience the only
     * packets that arrive are on login and respawn - so a bar driven off the fields sat frozen at
     * whatever it was when the player joined, however much experience they earned afterwards. The
     * accessors read the live player, which is where the truth now lives.</p>
     */
    public static float target() {
        int cost = costOfNext();
        return cost <= 0 ? 0.0F : Math.min(1.0F, (float) inLevel() / cost);
    }

    /** Advanced once per frame by the HUD; eases toward {@link #target()}. */
    /**
     * The eased value to draw this frame.
     *
     * <p>Smoothed against wall-clock time rather than by a fixed step per call: the old version
     * moved 14% of the remaining distance every time it was asked, so the bar filled roughly eight
     * times faster at 240fps than at 30, and twice as fast again if anything read it twice in a
     * frame.</p>
     */
    public static float fraction() {
        // Level ups are noticed here rather than in the packet handler. Progression is vanilla
        // experience now, so no packet arrives when a level is crossed - the client simply sees its
        // own player's level change, which is the same instant the server did.
        int now = level();
        if (now != lastSeenLevel) {
            if (now > lastSeenLevel && lastSeenLevel >= 0) {
                levelUpAt = System.currentTimeMillis();
            }
            lastSeenLevel = now;
            BAR.reset();
        }
        shownFraction = BAR.update(target());
        return shownFraction;
    }

    private static int lastSeenLevel = -1;

    private static final com.barbwra.mlum.client.gui.Anim.Tween BAR =
            new com.barbwra.mlum.client.gui.Anim.Tween(0.12F);

    /** 0..1 through the level-up flourish, or 1 when it is over. */
    public static float levelUpProgress() {
        if (levelUpAt == 0L) {
            return 1.0F;
        }
        float t = (System.currentTimeMillis() - levelUpAt) / 900.0F;
        return t >= 1.0F ? 1.0F : t;
    }
}

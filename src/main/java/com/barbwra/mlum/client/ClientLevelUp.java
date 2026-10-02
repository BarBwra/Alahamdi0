package com.barbwra.mlum.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * How long ago the player levelled up, and to what.
 *
 * <p>Modelled on {@link ClientZoneNotice} deliberately: the safe-area popup already establishes
 * what an announcement looks like in this mod, and a level up arriving in a different shape would
 * read as a different system rather than the same game speaking.</p>
 *
 * <p>It holds a little longer than the zone notice does. A zone crossing is information you act on
 * immediately; a level is a reward, and a reward that vanishes as fast as a warning does not feel
 * like one.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientLevelUp {

    private ClientLevelUp() {
    }

    public static final long IN_MS = 280L;
    public static final long HOLD_MS = 3000L;
    public static final long OUT_MS = 460L;
    private static final long LIFE_MS = IN_MS + HOLD_MS + OUT_MS;

    private static int level;
    private static long shownAt;

    public static void show(int newLevel) {
        level = newLevel;
        shownAt = System.currentTimeMillis();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            // A rising pair rather than one note, so it reads as an achievement and not an alert.
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F, 0.6F));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.NOTE_BLOCK_CHIME.value(), 1.8F, 0.4F));
        }
    }

    public static void clear() {
        shownAt = 0L;
    }

    public static boolean isVisible() {
        return shownAt > 0L && System.currentTimeMillis() - shownAt < LIFE_MS;
    }

    public static int level() {
        return level;
    }

    /**
     * {@code [slide, alpha]} for the current frame.
     *
     * <p>Slide runs 0 to 1 on the way in, sits at 1, then returns to 0; alpha fades independently
     * so the card is never caught half way through a move at full opacity.</p>
     */
    public static float[] motion() {
        long age = System.currentTimeMillis() - shownAt;
        if (age < IN_MS) {
            float t = age / (float) IN_MS;
            // easeOutBack, so it lands with a small overshoot - the one flourish it gets
            float u = t - 1.0F;
            float eased = 1.0F + u * u * (2.2F * u + 1.2F);
            return new float[]{eased, Math.min(1.0F, t * 2.0F)};
        }
        if (age < IN_MS + HOLD_MS) {
            return new float[]{1.0F, 1.0F};
        }
        float t = (age - IN_MS - HOLD_MS) / (float) OUT_MS;
        float fade = 1.0F - Math.min(1.0F, t);
        return new float[]{1.0F, fade};
    }
}

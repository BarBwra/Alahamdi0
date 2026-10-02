package com.barbwra.mlum.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The client's copy of the last safe-area transition, and how long it has left on screen.
 *
 * <p>The whole popup lifetime lives here rather than on the server: the server sends one packet at
 * the moment of crossing and is then done, and the client owns the slide, the hold and the fade.
 * That keeps a purely cosmetic animation off the network entirely.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientZoneNotice {

    private ClientZoneNotice() {
    }

    /** Slide in, hold, slide out. Total life is the sum. */
    public static final long IN_MS = 260L;
    public static final long HOLD_MS = 2400L;
    public static final long OUT_MS = 420L;
    private static final long LIFE_MS = IN_MS + HOLD_MS + OUT_MS;

    private static boolean entered;
    private static String zoneName = "";
    private static long shownAt;

    public static void show(boolean isEntering, String name) {
        entered = isEntering;
        zoneName = name == null ? "" : name;
        shownAt = System.currentTimeMillis();

        // Two clearly different notes, so the meaning lands before the text is read.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.NOTE_BLOCK_CHIME.value(), isEntering ? 1.5F : 0.8F, 0.5F));
        }
    }

    public static void clear() {
        shownAt = 0L;
        // called on logout: the next world must not open with the last one's zone pill
        entered = false;
        zoneName = "";
    }

    public static boolean isVisible() {
        return shownAt > 0L && System.currentTimeMillis() - shownAt < LIFE_MS;
    }

    public static boolean entered() {
        return entered;
    }

    public static String zoneName() {
        return zoneName;
    }

    public static long age() {
        return System.currentTimeMillis() - shownAt;
    }

    /**
     * 0..1 horizontal slide, and 0..1 opacity, as one pair.
     *
     * <p>Returned together because they are driven by the same clock and every caller wants both.
     * index 0 is how far in the card has travelled, index 1 is its alpha.</p>
     */
    public static float[] motion() {
        long age = age();
        if (age < IN_MS) {
            float t = age / (float) IN_MS;
            float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            return new float[]{eased, Math.min(1.0F, t * 2.0F)};
        }
        if (age < IN_MS + HOLD_MS) {
            return new float[]{1.0F, 1.0F};
        }
        float t = (age - IN_MS - HOLD_MS) / (float) OUT_MS;
        return new float[]{1.0F - t * 0.35F, Math.max(0.0F, 1.0F - t)};
    }
}

package com.barbwra.mlum.client.gui;

import com.barbwra.mlum.MlumConfig;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Easing, timing and colour blending for every moving part of the UI.
 *
 * <p><b>Wall clock, not ticks.</b> Everything here is driven from {@link System#nanoTime()} rather
 * than from partial ticks, because the UI keeps animating while the game is paused on a single
 * player world and because a duration written in milliseconds is far easier to reason about than
 * one written in twentieths of a second.</p>
 *
 * <p><b>Off means settled, never frozen.</b> With {@code animations.enabled = false} every
 * progress query returns 1 and every {@link Tween} snaps straight to its target, so the UI draws
 * exactly as it would at the end of the animation. Nothing is left half way.</p>
 *
 * <p><b>Framerate independence.</b> {@link Tween} smooths exponentially against elapsed time, not
 * per frame, so a bar takes the same wall-clock time to fill at 30fps and at 240fps. A per-frame
 * {@code value += (target - value) * 0.2F} would be four times faster on the faster machine.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class Anim {

    private Anim() {
    }

    private static final long START_NANOS = System.nanoTime();

    /** Milliseconds since the class loaded. Monotonic, and immune to the system clock moving. */
    public static long now() {
        return (System.nanoTime() - START_NANOS) / 1_000_000L;
    }

    public static boolean enabled() {
        return MlumConfig.animationsEnabled();
    }

    /**
     * Linear 0..1 over {@code durationMs} starting at {@code startMs}, scaled by the configured
     * speed. Returns 1 immediately when animations are off.
     */
    public static float progress(long startMs, float durationMs) {
        if (!enabled() || durationMs <= 0.0F) {
            return 1.0F;
        }
        float speed = Math.max(0.05F, MlumConfig.animationSpeed());
        return Mth.clamp((now() - startMs) * speed / durationMs, 0.0F, 1.0F);
    }

    /** {@link #progress} with a stagger, so a row of panels can arrive one after another. */
    public static float progress(long startMs, float durationMs, float delayMs) {
        return progress(startMs + (long) (enabled() ? delayMs : 0.0F), durationMs);
    }

    /* ------------------------------------------------------------------ easing */

    /** Decelerating. The default for anything arriving on screen. */
    public static float easeOut(float t) {
        float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - u * u * u;
    }

    /** Decelerating, harder. For short distances that still need to feel deliberate. */
    public static float easeOutQuad(float t) {
        float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - u * u;
    }

    /** Overshoots slightly then settles - a selection ring landing on its cell. */
    public static float easeOutBack(float t) {
        float u = Mth.clamp(t, 0.0F, 1.0F) - 1.0F;
        return 1.0F + u * u * (2.70158F * u + 1.70158F);
    }

    /** Symmetric. For anything that moves from one resting place to another. */
    public static float easeInOut(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c < 0.5F ? 4.0F * c * c * c : 1.0F - (float) Math.pow(-2.0F * c + 2.0F, 3) / 2.0F;
    }

    /** A 0..1 sine over {@code periodMs}, for breathing glows. Sits at 1 when animations are off. */
    public static float pulse(float periodMs) {
        if (!enabled() || periodMs <= 0.0F) {
            return 1.0F;
        }
        double phase = (now() % (long) periodMs) / (double) periodMs;
        return (float) (0.5D + 0.5D * Math.sin(phase * Math.PI * 2.0D));
    }

    public static float lerp(float from, float to, float t) {
        return from + (to - from) * Mth.clamp(t, 0.0F, 1.0F);
    }

    /** Rounds through a float so a lerp between two pixel positions does not judder. */
    public static int lerpInt(int from, int to, float t) {
        return Math.round(lerp(from, to, t));
    }

    /* ------------------------------------------------------------------ colour */

    /** Blends two ARGB colours, alpha included. */
    public static int mix(int from, int to, float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        int a = Math.round(lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, c));
        int r = Math.round(lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, c));
        int g = Math.round(lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, c));
        int b = Math.round(lerp(from & 0xFF, to & 0xFF, c));
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** Scales an ARGB colour's alpha by {@code factor}, leaving the RGB alone. */
    public static int fade(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(factor, 0.0F, 1.0F));
        return a << 24 | (argb & 0x00FFFFFF);
    }

    /* ------------------------------------------------------------------- tween */

    /**
     * A float that chases a target over wall-clock time.
     *
     * <p>Used for the vitals bars and the HUD ammo counter, where the value arrives in jumps from
     * the server and should be seen to move rather than teleport.</p>
     */
    public static final class Tween {

        private float value;
        private long lastMs;
        private boolean seeded;

        /** Seconds to close roughly 63% of the remaining distance. Smaller is snappier. */
        private final float tau;

        public Tween(float tau) {
            this.tau = Math.max(0.01F, tau);
        }

        /** Feeds the current target and returns the eased value for this frame. */
        public float update(float target) {
            long now = now();
            if (!seeded) {
                seeded = true;
                value = target;
                lastMs = now;
                return value;
            }
            if (!enabled()) {
                value = target;
                lastMs = now;
                return value;
            }
            float dt = (now - lastMs) / 1000.0F * Math.max(0.05F, MlumConfig.animationSpeed());
            lastMs = now;
            // clamped so a stalled frame - a chunk load, a resource reload - cannot overshoot
            dt = Mth.clamp(dt, 0.0F, 0.25F);
            value += (target - value) * (1.0F - (float) Math.exp(-dt / tau));
            if (Math.abs(target - value) < 0.0005F) {
                value = target;
            }
            return value;
        }

        public float value() {
            return value;
        }

        /** Drops the eased value so the next update snaps. Call when the subject changes entirely. */
        public void reset() {
            seeded = false;
        }
    }
}

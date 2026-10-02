package com.barbwra.mlum.client.ui;

/** CSS timing functions, so animations curve exactly as they do in the design. */
public final class Ease {

    private Ease() {
    }

    /** {@code cubic-bezier(x1, y1, x2, y2)} at progress {@code t}. */
    public static float bezier(float x1, float y1, float x2, float y2, float t) {
        if (t <= 0.0F) {
            return 0.0F;
        }
        if (t >= 1.0F) {
            return 1.0F;
        }
        // solve x(u) = t for u by Newton, fall back to bisection
        float u = t;
        for (int i = 0; i < 8; i++) {
            float x = sample(x1, x2, u) - t;
            if (Math.abs(x) < 1e-5F) {
                return sample(y1, y2, u);
            }
            float d = slope(x1, x2, u);
            if (Math.abs(d) < 1e-6F) {
                break;
            }
            u -= x / d;
        }
        float lo = 0.0F;
        float hi = 1.0F;
        u = t;
        for (int i = 0; i < 30; i++) {
            float x = sample(x1, x2, u);
            if (Math.abs(x - t) < 1e-5F) {
                break;
            }
            if (x < t) {
                lo = u;
            } else {
                hi = u;
            }
            u = (lo + hi) / 2.0F;
        }
        return sample(y1, y2, u);
    }

    private static float sample(float a, float b, float u) {
        return ((1 - 3 * b + 3 * a) * u + (3 * b - 6 * a)) * u * u + 3 * a * u;
    }

    private static float slope(float a, float b, float u) {
        return 3 * (1 - 3 * b + 3 * a) * u * u + 2 * (3 * b - 6 * a) * u + 3 * a;
    }

    public static float easeOut(float t) {
        return bezier(0.0F, 0.0F, 0.58F, 1.0F, t);
    }

    public static float easeInOut(float t) {
        return bezier(0.42F, 0.0F, 0.58F, 1.0F, t);
    }

    public static float ease(float t) {
        return bezier(0.25F, 0.1F, 0.25F, 1.0F, t);
    }

    /** {@code 1 - (1 - t)^3}, the wallet's count-up. */
    public static float cubicOut(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    /** An {@code infinite alternate} animation's value: from -> to -> from over 2 x period. */
    public static float alternate(long nowMs, long periodMs, float from, float to) {
        long cycle = Math.floorMod(nowMs, periodMs * 2);
        float t = cycle < periodMs ? cycle / (float) periodMs : 2.0F - cycle / (float) periodMs;
        return from + (to - from) * easeInOut(t);
    }

    public static float clamp01(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }
}

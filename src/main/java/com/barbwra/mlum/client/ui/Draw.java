package com.barbwra.mlum.client.ui;

import com.barbwra.mlum.client.ui.text.Raster;
import com.barbwra.mlum.client.ui.text.Shaped;
import com.barbwra.mlum.client.ui.text.TextEngine;

/** Drawing in CSS pixels: every call snaps its edges to device pixels and hands off to a Canvas. */
public final class Draw {

    private Draw() {
    }

    public static void rect(Canvas c, float x, float y, float w, float h, int argb) {
        if ((argb >>> 24) == 0 || w <= 0.0F || h <= 0.0F) {
            return;
        }
        c.fill(Px.d(x), Px.d(y), Px.d(x + w), Px.d(y + h), argb);
    }

    /** Top-to-bottom multi-stop gradient. */
    public static void vgrad(Canvas c, float x, float y, float w, float h, float[] stops, int[] colors) {
        int x0 = Px.d(x);
        int x1 = Px.d(x + w);
        for (int i = 0; i + 1 < stops.length; i++) {
            int y0 = Px.d(y + h * stops[i]);
            int y1 = Px.d(y + h * stops[i + 1]);
            if (y1 > y0) {
                c.gradient(x0, y0, x1, y1, colors[i], colors[i], colors[i + 1], colors[i + 1]);
            }
        }
        // solid ends when the first stop is not at 0 or the last not at 1
        if (stops[0] > 0.0F) {
            c.fill(x0, Px.d(y), x1, Px.d(y + h * stops[0]), colors[0]);
        }
        if (stops[stops.length - 1] < 1.0F) {
            c.fill(x0, Px.d(y + h * stops[stops.length - 1]), x1, Px.d(y + h), colors[colors.length - 1]);
        }
    }

    /** Left-to-right multi-stop gradient. */
    public static void hgrad(Canvas c, float x, float y, float w, float h, float[] stops, int[] colors) {
        int y0 = Px.d(y);
        int y1 = Px.d(y + h);
        for (int i = 0; i + 1 < stops.length; i++) {
            int x0 = Px.d(x + w * stops[i]);
            int x1 = Px.d(x + w * stops[i + 1]);
            if (x1 > x0) {
                c.gradient(x0, y0, x1, y1, colors[i], colors[i + 1], colors[i + 1], colors[i]);
            }
        }
        if (stops[0] > 0.0F) {
            c.fill(Px.d(x), y0, Px.d(x + w * stops[0]), y1, colors[0]);
        }
        if (stops[stops.length - 1] < 1.0F) {
            c.fill(Px.d(x + w * stops[stops.length - 1]), y0, Px.d(x + w), y1, colors[colors.length - 1]);
        }
    }

    /** A solid or dashed border inside the device rectangle, side widths in device pixels. */
    public static void border(Canvas c, int x0, int y0, int x1, int y1, int t, int r, int b, int l,
                              int[] colors, boolean dashed, float cssWidth) {
        int ct = colors[0];
        int cr = colors[1];
        int cb = colors[2];
        int cl = colors[3];
        if (!dashed) {
            if (t > 0) {
                c.fill(x0, y0, x1, y0 + t, ct);
            }
            if (b > 0) {
                c.fill(x0, y1 - b, x1, y1, cb);
            }
            if (l > 0) {
                c.fill(x0, y0 + t, x0 + l, y1 - b, cl);
            }
            if (r > 0) {
                c.fill(x1 - r, y0 + t, x1, y1 - b, cr);
            }
            return;
        }
        // Chrome's dashes: 3x the width long, ~2x apart, a dash sitting on each corner
        float dash = 3.0F * cssWidth * Px.s;
        float gap = 2.0F * cssWidth * Px.s;
        if (t > 0) {
            dashes(c, x0, x1, y0, y0 + t, dash, gap, true, ct);
        }
        if (b > 0) {
            dashes(c, x0, x1, y1 - b, y1, dash, gap, true, cb);
        }
        if (l > 0) {
            dashes(c, y0, y1, x0, x0 + l, dash, gap, false, cl);
        }
        if (r > 0) {
            dashes(c, y0, y1, x1 - r, x1, dash, gap, false, cr);
        }
    }

    private static void dashes(Canvas c, int a0, int a1, int b0, int b1, float dash, float gap,
                               boolean horizontal, int argb) {
        float len = a1 - a0;
        if (len <= 0.0F) {
            return;
        }
        int n = Math.max(1, Math.round((len + gap) / (dash + gap)));
        float g = n > 1 ? (len - n * dash) / (n - 1) : 0.0F;
        for (int i = 0; i < n; i++) {
            int s = Math.round(a0 + i * (dash + g));
            int e = Math.min(a1, Math.round(a0 + i * (dash + g) + dash));
            if (n == 1) {
                e = a1;
            }
            if (e <= s) {
                continue;
            }
            if (horizontal) {
                c.fill(s, b0, e, b1, argb);
            } else {
                c.fill(b0, s, b1, e, argb);
            }
        }
    }

    /** One shaped line with its pen at {@code penX} and its baseline at {@code baseline}, CSS px. */
    public static void text(Canvas c, Shaped s, float penX, float baseline, int argb) {
        if (s == null || s.isEmpty() || (argb >>> 24) == 0) {
            return;
        }
        int px = Px.d(penX);
        int by = Px.d(baseline);
        if (s.fallback()) {
            c.fallbackText(s, px, by, argb);
            return;
        }
        Raster r = TextEngine.mask(s);
        blit(c, r, px, by, argb);
    }

    /** Text filled with a vertical gradient; the box runs {@code top}..{@code bottom} around the baseline. */
    public static void gradientText(Canvas c, Shaped s, float penX, float baseline, float top, float bottom,
                                    float[] stops, int[] colors) {
        if (s == null || s.isEmpty()) {
            return;
        }
        int px = Px.d(penX);
        int by = Px.d(baseline);
        if (s.fallback()) {
            c.fallbackText(s, px, by, colors[colors.length / 2]);
            return;
        }
        blit(c, TextEngine.gradient(s, top, bottom, stops, colors), px, by, 0xFFFFFFFF);
    }

    /** A blurred glow under text - {@code text-shadow: 0 0 blur c}. */
    public static void glowText(Canvas c, Shaped s, float penX, float baseline, float blur, int argb) {
        if (s == null || s.isEmpty() || s.fallback()) {
            return;
        }
        blit(c, TextEngine.glow(s, blur, argb), Px.d(penX), Px.d(baseline), 0xFFFFFFFF);
    }

    /** Text over a hard shadow. */
    public static void shadowText(Canvas c, Shaped s, float penX, float baseline, int argb, int shadow,
                                  float dx, float dy) {
        if (s == null || s.isEmpty()) {
            return;
        }
        int px = Px.d(penX);
        int by = Px.d(baseline);
        if (s.fallback()) {
            c.fallbackText(s, px + Math.round(dx * Px.s), by + Math.round(dy * Px.s), shadow);
            c.fallbackText(s, px, by, argb);
            return;
        }
        blit(c, TextEngine.shadowed(s, argb, shadow, dx, dy), px, by, 0xFFFFFFFF);
    }

    private static void blit(Canvas c, Raster r, int penX, int baseline, int tint) {
        if (r == null || r.isEmpty()) {
            return;
        }
        int x0 = penX - r.originX;
        int y0 = baseline - r.baseline;
        c.raster(r, 0.0F, 0.0F, 1.0F, 1.0F, x0, y0, x0 + r.width, y0 + r.height, tint);
    }

    /** A raster (sprite, glow) stretched over a CSS rectangle. */
    public static void raster(Canvas c, Raster r, float x, float y, float w, float h, int tint) {
        if (r == null || r.isEmpty()) {
            return;
        }
        c.raster(r, 0.0F, 0.0F, 1.0F, 1.0F, Px.d(x), Px.d(y), Px.d(x + w), Px.d(y + h), tint);
    }

    /**
     * A raster stretched over a CSS rectangle but only the part inside {@code clip} drawn - how a
     * background gradient larger than its box gets cut to the box without a scissor.
     */
    public static void rasterClipped(Canvas c, Raster r, float x, float y, float w, float h,
                                     float cx0, float cy0, float cx1, float cy1, int tint) {
        if (r == null || r.isEmpty() || w <= 0.0F || h <= 0.0F) {
            return;
        }
        float ix0 = Math.max(x, cx0);
        float iy0 = Math.max(y, cy0);
        float ix1 = Math.min(x + w, cx1);
        float iy1 = Math.min(y + h, cy1);
        if (ix1 <= ix0 || iy1 <= iy0) {
            return;
        }
        float u0 = (ix0 - x) / w;
        float v0 = (iy0 - y) / h;
        float u1 = (ix1 - x) / w;
        float v1 = (iy1 - y) / h;
        c.raster(r, u0, v0, u1, v1, Px.d(ix0), Px.d(iy0), Px.d(ix1), Px.d(iy1), tint);
    }

    /** Multiplies an ARGB colour's alpha. */
    public static int alpha(int argb, float a) {
        int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, a)));
        return (al << 24) | (argb & 0x00FFFFFF);
    }

    /** Colour with an explicit 0..1 alpha. */
    public static int rgba(int rgb, float a) {
        return (Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24) | (rgb & 0x00FFFFFF);
    }

    /** Linear blend of two ARGB colours. */
    public static int mix(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int brr = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (brr - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }
}

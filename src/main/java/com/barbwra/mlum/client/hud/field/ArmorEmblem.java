package com.barbwra.mlum.client.hud.field;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * The armour, drawn as a shield around the health number and broken into ten pieces.
 *
 * <p>Each piece is two armour points, so a full set of diamond reads as a whole shield and every
 * hit that costs armour knocks a piece out. A piece that has just gone flashes white and fades, so a
 * hit that cost armour is told apart from one that did not without anything to read.</p>
 *
 * <p>The outline is worked out once: every pixel of a shield shape that lies on its two-pixel rim,
 * tagged with which of the ten slices of the circle around the centre it falls in. Pixels right on a
 * slice boundary are dropped, which is what draws the small gaps between the pieces.</p>
 */
@OnlyIn(Dist.CLIENT)
final class ArmorEmblem {

    private static final int W = 21;
    private static final int H = 25;
    private static final int PIECES = 10;
    private static final long FLASH_MS = 420L;

    /** {u, v, piece} for the rim and {u, v} for the inside. */
    private final List<int[]> rim = new ArrayList<>();
    private final List<int[]> inside = new ArrayList<>();
    private final long[] lostAt = new long[PIECES];
    private int lit = PIECES;
    private int litBefore = -1;

    ArmorEmblem() {
        float cx = (W - 1) / 2.0F;
        float cy = H * 0.44F;
        double span = Math.PI * 2.0D / PIECES;
        for (int v = 0; v < H; v++) {
            for (int u = 0; u < W; u++) {
                if (!in(u, v)) {
                    continue;
                }
                boolean edge = !(in(u - 2, v) && in(u + 2, v) && in(u, v - 2) && in(u, v + 2));
                if (!edge) {
                    inside.add(new int[]{u, v});
                    continue;
                }
                double ang = Math.atan2(u - cx, -(v - cy));
                if (ang < 0) {
                    ang += Math.PI * 2.0D;
                }
                int piece = (int) Math.floor(ang / span);
                double off = ang - piece * span;
                if (off < 0.07D || off > span - 0.07D) {
                    continue;
                }
                rim.add(new int[]{u, v, Math.min(PIECES - 1, piece)});
            }
        }
        reset();
    }

    /** Flat top with rounded shoulders, straight sides, tapering to a point. */
    private static boolean in(int u, int v) {
        if (u < 0 || v < 0 || u >= W || v >= H) {
            return false;
        }
        float cx = (W - 1) / 2.0F;
        float dx = Math.abs(u - cx);
        if (v < 2) {
            return dx <= cx - (2 - v);
        }
        if (v < H * 0.55F) {
            return dx <= cx;
        }
        float t = (v - H * 0.55F) / (H * 0.45F);
        return dx <= cx * (1.0F - t) + 0.4F;
    }

    void reset() {
        for (int i = 0; i < PIECES; i++) {
            lostAt[i] = -10_000L;
        }
        litBefore = -1;
    }

    float width() {
        return W;
    }

    void track(int armor, long now) {
        lit = Math.max(0, Math.min(PIECES, Math.round(armor / 2.0F)));
        if (litBefore >= 0 && lit < litBefore) {
            for (int i = lit; i < litBefore; i++) {
                lostAt[i] = now;
            }
        }
        litBefore = lit;
    }

    void draw(HudPen pen, float x, float y, long now) {
        for (int[] p : inside) {
            pen.rect(x + p[0], y + p[1], 1, 1, 0xD1070906);
        }
        for (int[] p : rim) {
            int piece = p[2];
            int colour;
            if (piece < lit) {
                colour = FieldHud.STEEL;
            } else {
                float f = (now - lostAt[piece]) / (float) FLASH_MS;
                colour = f < 1.0F ? FieldHud.alpha(0xFFFFFFFF, 1.0F - f) : 0x1AFFFFFF;
            }
            pen.rect(x + p[0], y + p[1], 1, 1, colour);
        }
    }
}

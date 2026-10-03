package com.barbwra.mlum.client.hud.field;

import com.barbwra.mlum.client.ui.mc.McCanvas;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Round things, drawn with the only primitive the canvas has: the rectangle.
 *
 * <h2>How a smooth ring comes out of rectangles</h2>
 * <p>Every framebuffer pixel in the ring's box is given a coverage between 0 and 1 - how much of
 * it lies inside the band, and inside the arc's angle - and pixels next to each other on a row with
 * the same coverage are joined into one rectangle. The inside of the band is a few long runs per
 * row; only the edges break into single pixels, and those are what make the edge smooth. A ring
 * the size of the downed timer is a couple of thousand rectangles, one batch.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class Shapes {

    private Shapes() {
    }

    private static final double TAU = Math.PI * 2.0D;

    /**
     * An arc of a ring, clockwise from 12 o'clock.
     *
     * @param cx     centre, GUI pixels
     * @param radius to the middle of the band, GUI pixels
     * @param thick  band thickness, GUI pixels
     * @param from   where the arc starts, 0..1 of a turn
     * @param to     where it ends, 0..1 of a turn; from 0 to 1 is the whole ring
     */
    public static void arc(HudPen pen, float cx, float cy, float radius, float thick, float from, float to, int argb) {
        if (to <= from || (argb >>> 24) == 0) {
            return;
        }
        boolean full = to - from >= 0.9999F;
        float u = pen.u;
        float k = pen.zk();
        double dcx = pen.zx(cx) * u;
        double dcy = pen.zy(cy) * u;
        double rIn = (radius - thick / 2.0F) * k * u;
        double rOut = (radius + thick / 2.0F) * k * u;
        double a0 = from * TAU;
        double a1 = to * TAU;
        int x0 = (int) Math.floor(dcx - rOut - 1);
        int x1 = (int) Math.ceil(dcx + rOut + 1);
        int y0 = (int) Math.floor(dcy - rOut - 1);
        int y1 = (int) Math.ceil(dcy + rOut + 1);
        McCanvas canvas = pen.canvas();
        int baseAlpha = argb >>> 24;
        int rgb = argb & 0xFFFFFF;
        double outer = rOut + 1.0D;
        double inner = rIn - 1.0D;
        for (int y = y0; y < y1; y++) {
            double py = y + 0.5D - dcy;
            if (Math.abs(py) > outer) {
                continue;
            }
            // only the band is visited: the span of the outer circle on this row, minus the hole
            double half = Math.sqrt(outer * outer - py * py);
            int from0 = Math.max(x0, (int) Math.floor(dcx - half));
            int to0 = Math.min(x1, (int) Math.ceil(dcx + half));
            if (inner > 0.0D && Math.abs(py) < inner) {
                double hole = Math.sqrt(inner * inner - py * py);
                int holeL = (int) Math.ceil(dcx - hole);
                int holeR = (int) Math.floor(dcx + hole);
                row(canvas, y, from0, Math.min(to0, holeL), dcx, py, rIn, rOut, a0, a1, full, baseAlpha, rgb);
                row(canvas, y, Math.max(from0, holeR), to0, dcx, py, rIn, rOut, a0, a1, full, baseAlpha, rgb);
            } else {
                row(canvas, y, from0, to0, dcx, py, rIn, rOut, a0, a1, full, baseAlpha, rgb);
            }
        }
    }

    /** One row's stretch of pixels, joined into runs of equal coverage. */
    private static void row(McCanvas canvas, int y, int from, int to, double dcx, double py, double rIn, double rOut,
                            double a0, double a1, boolean full, int baseAlpha, int rgb) {
        int runStart = -1;
        int runLevel = 0;
        for (int x = from; x <= to; x++) {
            int level = 0;
            if (x < to) {
                double px = x + 0.5D - dcx;
                double r = Math.sqrt(px * px + py * py);
                double cover = Mth.clamp(Math.min(r - rIn, rOut - r) + 0.5D, 0.0D, 1.0D);
                if (cover > 0.0D && !full) {
                    // angle clockwise from straight up
                    double a = Math.atan2(px, -py);
                    if (a < 0) {
                        a += TAU;
                    }
                    double inside = Math.min(a - a0, a1 - a) * r;   // distance to the nearer end, in px
                    cover *= Mth.clamp(inside + 0.5D, 0.0D, 1.0D);
                }
                level = (int) Math.round(cover * 16.0D);
            }
            if (level != runLevel) {
                if (runLevel > 0) {
                    int a = baseAlpha * runLevel / 16;
                    canvas.fill(runStart, y, x, y + 1, (a << 24) | rgb);
                }
                runStart = x;
                runLevel = level;
            }
        }
    }

    /** A whole ring. */
    public static void ring(HudPen pen, float cx, float cy, float radius, float thick, int argb) {
        arc(pen, cx, cy, radius, thick, 0.0F, 1.0F, argb);
    }

    /** A filled disc - a ring whose band reaches the centre. */
    public static void disc(HudPen pen, float cx, float cy, float radius, int argb) {
        arc(pen, cx, cy, radius / 2.0F, radius, 0.0F, 1.0F, argb);
    }

    /**
     * A chunky pixel dot - a five by five block with its corners clipped - centred on the point,
     * snapped to whole framebuffer pixels so every cell is the same size.
     *
     * @param cell one cell, in framebuffer pixels
     */
    public static void pixelDot(HudPen pen, float cx, float cy, int cell, int argb) {
        McCanvas canvas = pen.canvas();
        int c = Math.max(1, cell);
        int x = Math.round(pen.zx(cx) * pen.u) - c * 5 / 2;
        int y = Math.round(pen.zy(cy) * pen.u) - c * 5 / 2;
        canvas.fill(x + c, y, x + c * 4, y + c * 5, argb);
        canvas.fill(x, y + c, x + c, y + c * 4, argb);
        canvas.fill(x + c * 4, y + c, x + c * 5, y + c * 4, argb);
    }
}

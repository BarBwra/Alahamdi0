package com.barbwra.mlum.warehouse.client.gui;

import com.barbwra.mlum.util.ArabicText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * Every shape in the terminal, drawn procedurally.
 *
 * <p>No textures, no atlas, no resource pack dependency. Three things carry the look:</p>
 * <ul>
 *   <li><b>Chamfered corners.</b> Panels are three overlapping rectangles so each corner loses 2px.
 *       Two extra fills, and the single biggest reason the UI stops reading as a stack of boxes.</li>
 *   <li><b>Vertical gradients</b> that interpolate alpha as well as colour, so a surface can be lit
 *       from the top and still let the world through.</li>
 *   <li><b>{@link #graph}</b>, a real multi-series line plot - the market is actual data, so it gets
 *       drawn as data rather than as decoration.</li>
 * </ul>
 *
 * <p>{@link #rtl} is the only correct way to draw the mod's Arabic. It shapes the letters into
 * their contextual forms and reorders the line, because Minecraft's font renderer does neither.</p>
 */
public final class GuiDraw {

    private GuiDraw() {
    }

    public static final int HEADER_H = 13;
    private static final int CHAMFER = 2;

    /* ------------------------------------------------------------------ text */

    /**
     * For raw {@code drawString} calls: shapes the letters <b>and</b> reorders them to visual order.
     *
     * <p>{@code Font.drawInBatch(String, ...)} decomposes the string straight to glyphs with no
     * bidi pass, so the caller has to do the reordering itself.</p>
     */
    public static String rtl(String text) {
        return ArabicText.autoDisplay(text);
    }

    /**
     * For text going into a {@link net.minecraft.network.chat.Component}: shapes the letters and
     * stops there.
     *
     * <p><b>This is not the same as {@link #rtl} and the difference is not cosmetic.</b> Anything
     * rendered from a Component - tooltips, chat, buttons built on Components - goes through
     * {@code Language.getVisualOrder}, which runs {@code FormattedBidiReorder} and reorders the
     * line itself. Handing that path an already-reversed string reverses it a second time, which is
     * exactly why the crate tooltips came out backwards while every other label on the screen was
     * correct: {@code drawString} needed the reorder, the tooltip did not.</p>
     *
     * <p>Presentation-form Arabic still carries bidi class R/AL, so Minecraft's own pass reorders
     * the shaped output correctly and keeps embedded numbers running left to right.</p>
     */
    public static String rtlShaped(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return ArabicText.isLogical(text) ? ArabicText.shape(text) : text;
    }

    /** Right-aligned Arabic, the natural alignment for this UI's own strings. */
    public static void drawRight(GuiGraphics g, Font font, String text, int right, int y, int color) {
        String shown = rtl(text);
        g.drawString(font, shown, right - font.width(shown), y, color, false);
    }

    public static void drawLeft(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, rtl(text), x, y, color, false);
    }

    public static void drawCentered(GuiGraphics g, Font font, String text, int cx, int y, int color) {
        String shown = rtl(text);
        g.drawString(font, shown, cx - font.width(shown) / 2, y, color, false);
    }

    /**
     * Text with a soft dark halo behind it.
     *
     * <p>Vanilla's drop shadow is a hard black offset copy - fine on an opaque slab, muddy on a
     * translucent one, because the world shows through both the glyph and its shadow. A halo in a
     * darkened version of the text's own colour keeps the glyph legible over anything behind the
     * panel without reading as a second, blurrier word.</p>
     *
     * <p>Four draw calls per string, so this is for headers, values and badges - not body text.</p>
     */
    private static void glow(GuiGraphics g, Font font, String shown, int x, int y, int color) {
        int halo = Theme.withAlpha(Theme.darken(color, 0.72F), 0xB0);
        g.drawString(font, shown, x + 1, y, halo, false);
        g.drawString(font, shown, x - 1, y, halo, false);
        g.drawString(font, shown, x, y + 1, halo, false);
        g.drawString(font, shown, x, y, color, false);
    }

    public static void glowRight(GuiGraphics g, Font font, String text, int right, int y, int color) {
        String shown = rtl(text);
        glow(g, font, shown, right - font.width(shown), y, color);
    }

    public static void glowLeft(GuiGraphics g, Font font, String text, int x, int y, int color) {
        glow(g, font, rtl(text), x, y, color);
    }

    public static void glowCentered(GuiGraphics g, Font font, String text, int cx, int y, int color) {
        String shown = rtl(text);
        glow(g, font, shown, cx - font.width(shown) / 2, y, color);
    }

    /**
     * A status chip: short label in a tinted, outlined pill.
     *
     * @return the width consumed, so callers can lay several out in a row
     */
    public static int badge(GuiGraphics g, Font font, String text, int x, int y, int color) {
        String shown = rtl(text);
        int w = font.width(shown) + 8;
        g.fill(x, y, x + w, y + 11, Theme.withAlpha(color, 0x30));
        outline(g, x, y, w, 11, Theme.withAlpha(color, 0xB0));
        g.drawString(font, shown, x + 4, y + 2, color, false);
        return w + 3;
    }

    /* ---------------------------------------------------------------- shapes */

    public static void panelBody(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        g.fillGradient(x + CHAMFER, y, x + w - CHAMFER, y + h, top, bottom);
        g.fillGradient(x, y + CHAMFER, x + CHAMFER, y + h - CHAMFER, top, bottom);
        g.fillGradient(x + w - CHAMFER, y + CHAMFER, x + w, y + h - CHAMFER, top, bottom);
    }

    public static void panelOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + CHAMFER, y, x + w - CHAMFER, y + 1, color);
        g.fill(x + CHAMFER, y + h - 1, x + w - CHAMFER, y + h, color);
        g.fill(x, y + CHAMFER, x + 1, y + h - CHAMFER, color);
        g.fill(x + w - 1, y + CHAMFER, x + w, y + h - CHAMFER, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, color);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Corner ticks. Two heavier marks on opposite corners, not four glowing brackets. */
    public static void brackets(GuiGraphics g, int x, int y, int w, int h, int color, int len) {
        g.fill(x + CHAMFER, y, x + CHAMFER + len, y + 2, color);
        g.fill(x, y + CHAMFER, x + 2, y + CHAMFER + len, color);
        g.fill(x + w - CHAMFER - len, y + h - 2, x + w - CHAMFER, y + h, color);
        g.fill(x + w - 2, y + h - CHAMFER - len, x + w, y + h - CHAMFER, color);
    }

    /* ---------------------------------------------------------------- panels */

    /**
     * Full-screen corner darkening.
     *
     * <p>Four stacked edge gradients rather than a single flat wash. A flat fill dims the middle of
     * the screen as much as the corners, which is the part the player is actually reading; this
     * leaves the centre almost untouched and pulls the periphery down so the panels separate from
     * the world without hiding it.</p>
     */
    public static void vignette(GuiGraphics g, int w, int h) {
        // Disabled by design: nothing may darken the live world behind a menu.
        if (w > 0) {
            return;
        }
        int edge = Theme.vignette();
        int clear = Theme.withAlpha(edge, 0);
        int band = Math.max(24, Math.min(w, h) / 3);

        g.fillGradient(0, 0, w, band, edge, clear);
        g.fillGradient(0, h - band, w, h, clear, edge);
        // Sides are drawn as horizontal gradients by filling narrow columns, since fillGradient
        // only interpolates vertically.
        for (int i = 0; i < band; i++) {
            int a = ((edge >>> 24) * (band - i) / band) & 0xFF;
            int col = Theme.withAlpha(edge, a);
            g.fill(i, 0, i + 1, h, col);
            g.fill(w - i - 1, 0, w - i, h, col);
        }
    }

    /** Faint horizontal scanlines, one per 3px. Sells "screen" without a texture. */
    public static void scanlines(GuiGraphics g, int x, int y, int w, int h) {
        if (!com.barbwra.mlum.warehouse.WarehouseConfig.scanlines()) {
            return;
        }
        for (int ly = y + 2; ly < y + h - 1; ly += 3) {
            g.fill(x + 1, ly, x + w - 1, ly + 1, 0x14000000);
        }
    }

    /** Frosted, untitled panel. */
    public static void glass(GuiGraphics g, int x, int y, int w, int h) {
        panelBody(g, x, y, w, h, Theme.glassTop(), Theme.glassBottom());
        scanlines(g, x, y, w, h);
        g.fill(x + CHAMFER, y + 1, x + w - CHAMFER, y + 2, Theme.BEVEL);
        panelOutline(g, x, y, w, h, Theme.BORDER);
        brackets(g, x, y, w, h, Theme.accent(110), 10);
    }

    /** Titled frosted panel. The title is right aligned because the mod's strings are Arabic. */
    public static void panel(GuiGraphics g, Font font, int x, int y, int w, int h, String title) {
        panelBody(g, x, y, w, h, Theme.glassTop(), Theme.glassBottom());
        scanlines(g, x, y + HEADER_H, w, h - HEADER_H);
        g.fillGradient(x + CHAMFER, y + 1, x + w - CHAMFER, y + HEADER_H, Theme.headerTop(), Theme.headerBottom());
        g.fill(x + CHAMFER, y + 1, x + w - CHAMFER, y + 2, Theme.BEVEL);

        int uy = y + HEADER_H;
        g.fill(x + CHAMFER, uy, x + w - CHAMFER, uy + 1, Theme.RULE);
        g.fill(x + CHAMFER + 6, uy + 1, x + w - CHAMFER - 6, uy + 2, Theme.accent(90));

        panelOutline(g, x, y, w, h, Theme.BORDER);
        brackets(g, x, y, w, h, Theme.accent(130), 10);

        if (title != null && !title.isEmpty()) {
            glowRight(g, font, title, x + w - 9, y + 3, Theme.TEXT);
            g.fill(x + w - 6, y + 3, x + w - 4, y + HEADER_H - 3, Theme.accent());
        }
    }

    /**
     * Diagonal hazard stripes, clipped to a box. Marks anything the player should hesitate over.
     */
    public static void hazard(GuiGraphics g, int x, int y, int w, int h, int color, int alpha) {
        g.enableScissor(x, y, x + w, y + h);
        int stripe = 6;
        for (int i = -h; i < w; i += stripe * 2) {
            for (int row = 0; row < h; row++) {
                int sx = x + i + row;
                g.fill(sx, y + row, sx + stripe, y + row + 1, Theme.withAlpha(color, alpha));
            }
        }
        g.disableScissor();
    }

    /* ------------------------------------------------------------------ bars */

    public static void thinBar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x, y, x + w, y + h, Theme.TRACK);
        int filled = Math.round(w * Mth.clamp(fraction, 0.0F, 1.0F));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, color);
            g.fill(x, y, x + filled, y + 1, Theme.withAlpha(color, 0x60));
        }
        outline(g, x - 1, y - 1, w + 2, h + 2, Theme.BORDER_SOFT);
    }

    /**
     * Segmented bar - discrete cells rather than one sliding fill.
     *
     * <p>Used for anything the player reads as a quantity (storage, lines) so it is countable at a
     * glance, where a continuous bar would only be comparable.</p>
     */
    public static void segmentBar(GuiGraphics g, int x, int y, int w, int h,
                                  int segments, float fraction, int color) {
        float exact = Mth.clamp(fraction, 0.0F, 1.0F) * segments;
        int full = (int) exact;
        for (int i = 0; i < segments; i++) {
            int sx = x + Math.round((float) w * i / segments);
            int ex = x + Math.round((float) w * (i + 1) / segments) - 1;
            if (ex <= sx) {
                ex = sx + 1;
            }
            g.fill(sx, y, ex, y + h, Theme.TRACK);
            if (i < full) {
                g.fill(sx, y, ex, y + h, color);
            }
        }
    }

    /**
     * A progress meter with a highlight band travelling along the filled section.
     *
     * <p>The motion is the point: an assembly line that is still running looks different from one
     * that is stalled or finished, without the player having to read a number. The band is derived
     * from wall-clock time rather than a stored counter, so it animates at framerate and costs
     * nothing to keep in sync.</p>
     */
    public static void meter(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x, y, x + w, y + h, Theme.TRACK);
        int filled = Math.round(w * Mth.clamp(fraction, 0.0F, 1.0F));

        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, Theme.withAlpha(color, 0xD0));
            g.fill(x, y, x + filled, y + 1, Theme.withAlpha(color, 0x50));

            // Sweep repeats every 1.8s across the filled span.
            float phase = (System.currentTimeMillis() % 1800L) / 1800.0F;
            int bandW = Math.max(6, filled / 5);
            int bandX = x + Math.round(phase * (filled + bandW)) - bandW;
            int from = Math.max(x, bandX);
            int to = Math.min(x + filled, bandX + bandW);
            if (to > from) {
                g.fillGradient(from, y, to, y + h, Theme.withAlpha(color, 0x00), Theme.withAlpha(color, 0x66));
            }
            // Leading edge, so the head of the bar is findable at a glance.
            if (filled < w) {
                g.fill(x + filled - 1, y, x + filled, y + h, Theme.accentBright());
            }
        }
        outline(g, x - 1, y - 1, w + 2, h + 2, Theme.BORDER_SOFT);
    }

    /**
     * A vertical fill indicator for a crate tile - how much shelf life is left.
     *
     * <p>Drawn as a rising column behind the tile's text rather than a separate bar, so a grid of
     * crates reads as a set of gauges at a glance and an about-to-spoil crate is visibly emptier
     * than its neighbours.</p>
     */
    public static void crateFill(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        int fill = Math.round(h * Mth.clamp(fraction, 0.0F, 1.0F));
        if (fill > 0) {
            g.fillGradient(x, y + h - fill, x + w, y + h,
                    Theme.withAlpha(color, 0x38), Theme.withAlpha(color, 0x12));
            g.fill(x, y + h - fill, x + w, y + h - fill + 1, Theme.withAlpha(color, 0x88));
        }
    }

    /** Filled/empty risk pips, for a route's danger rating. */
    public static void pips(GuiGraphics g, int x, int y, int filled, int total, int color) {
        for (int i = 0; i < total; i++) {
            int px = x + i * 6;
            if (i < filled) {
                g.fill(px, y, px + 4, y + 6, color);
            } else {
                outline(g, px, y, 4, 6, Theme.BORDER);
            }
        }
    }

    /* ----------------------------------------------------------------- graph */

    /**
     * Multi-series line plot, scissor-clipped to its own box.
     *
     * <p>Each series is drawn as connected 1px segments using Bresenham, because
     * {@code GuiGraphics} has no line primitive and a per-pixel fill loop over a 96-sample series
     * is still only a few hundred draw calls.</p>
     *
     * @param series one array per line, oldest sample first
     * @param colors one colour per series
     * @param min    bottom of the value axis
     * @param max    top of the value axis
     */
    public static void graph(GuiGraphics g, int x, int y, int w, int h,
                             float[][] series, int[] colors, float min, float max) {
        g.fill(x, y, x + w, y + h, Theme.TRACK);

        // Gridlines at the axis extremes and the neutral 1.0 midline.
        g.fill(x, y, x + w, y + 1, Theme.BORDER_SOFT);
        g.fill(x, y + h - 1, x + w, y + h, Theme.BORDER_SOFT);
        int mid = y + Math.round(h * (1.0F - (1.0F - min) / (max - min)));
        g.fill(x, mid, x + w, mid + 1, Theme.withAlpha(Theme.BORDER, 0x90));

        g.enableScissor(x, y, x + w, y + h);
        for (int s = 0; s < series.length; s++) {
            float[] data = series[s];
            if (data == null || data.length < 2) {
                continue;
            }
            int color = colors[Math.min(s, colors.length - 1)];
            int prevX = x;
            int prevY = valueToY(data[0], y, h, min, max);
            for (int i = 1; i < data.length; i++) {
                int px = x + Math.round((float) w * i / (data.length - 1));
                int py = valueToY(data[i], y, h, min, max);
                line(g, prevX, prevY, px, py, color);
                prevX = px;
                prevY = py;
            }
            // A dot on the latest sample, so the current value is findable without reading the key.
            g.fill(prevX - 1, prevY - 1, prevX + 2, prevY + 2, color);
        }
        g.disableScissor();

        outline(g, x, y, w, h, Theme.BORDER);
    }

    private static int valueToY(float value, int y, int h, float min, float max) {
        float t = Mth.clamp((value - min) / (max - min), 0.0F, 1.0F);
        return y + Math.round(h * (1.0F - t)) - 1;
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int guard = 0;
        while (guard++ < 4096) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) {
                return;
            }
            int e2 = err * 2;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /* ------------------------------------------------------------------ misc */

    public static String upper(String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }

    public static String trim(Font font, String text, int maxWidth) {
        if (text == null || maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    /** {@code 12:04} from milliseconds. Tabular by construction - always mm:ss. */
    public static String clock(long millis) {
        long total = Math.max(0L, millis) / 1000L;
        return String.format("%d:%02d", total / 60, total % 60);
    }

    /** {@code 4d 06h} or {@code 06h 12m}, whichever fits the magnitude. */
    public static String duration(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0) {
            return days + "d " + String.format("%02dh", hours);
        }
        if (hours > 0) {
            return String.format("%02dh %02dm", hours, minutes);
        }
        return String.format("%02dm", minutes);
    }
}

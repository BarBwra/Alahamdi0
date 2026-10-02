package com.barbwra.mlum.client.hud.field;

import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.mc.Atlas;
import com.barbwra.mlum.client.ui.mc.McCanvas;
import com.barbwra.mlum.client.ui.mc.UiBoot;
import com.barbwra.mlum.client.ui.text.Fonts;
import com.barbwra.mlum.client.ui.text.Raster;
import com.barbwra.mlum.client.ui.text.Shaped;
import com.barbwra.mlum.client.ui.text.TextEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The field HUD's pen: GUI pixels in, framebuffer pixels out, through the menus' own canvas.
 *
 * <h2>Why the menus' canvas and not GuiGraphics</h2>
 * <p>The HUD is set in the mod's two bundled faces - Handjet for every number, Noto Kufi for the
 * Arabic - and only the UI's own text engine can shape and rasterise those. Going through the same
 * {@link McCanvas} the menus paint with also means one batch for the whole HUD instead of a draw
 * call per rectangle, which is most of what keeps it cheap.</p>
 *
 * <h2>Units</h2>
 * <p>Everything the HUD code passes in is a GUI pixel, the same unit vanilla's hotbar is laid out
 * in, so the HUD grows and shrinks with the player's GUI Scale setting exactly like the vanilla HUD
 * it replaces. One GUI pixel is {@link #u} framebuffer pixels - a whole number, because vanilla's
 * GUI scale always is - so every 1px line lands on whole device pixels and stays sharp.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class HudPen {

    private final McCanvas canvas = new McCanvas();
    /** Framebuffer pixels per GUI pixel. */
    public float u = 1.0F;
    private long frames;

    public void begin(GuiGraphics graphics) {
        UiBoot.ensure();
        Minecraft mc = Minecraft.getInstance();
        int fw = mc.getWindow().getWidth();
        int fh = mc.getWindow().getHeight();
        // the same preamble a menu frame runs, so the text engine rasterises at this window's scale
        Px.fit(fw, fh);
        TextEngine.setScale(Px.s);
        Atlas.fitTo(fw);
        TextEngine.tick();
        Atlas.tick();
        u = (float) mc.getWindow().getGuiScale();
        canvas.begin(graphics, 0, 0);
    }

    public void end() {
        canvas.end();
        if (++frames % 600 == 0) {
            TextEngine.trim(1200);
        }
    }

    public McCanvas canvas() {
        return canvas;
    }

    private int d(float gui) {
        return Math.round(gui * u);
    }

    /* ------------------------------------------------------------------ shapes */

    public void rect(float x, float y, float w, float h, int argb) {
        if (w <= 0.0F || h <= 0.0F || (argb >>> 24) == 0) {
            return;
        }
        canvas.fill(d(x), d(y), d(x + w), d(y + h), argb);
    }

    /** One framebuffer pixel thick, for hairlines finer than a GUI pixel. */
    public void hairRow(float x, float y, float w, int argb) {
        int x0 = d(x);
        int y0 = d(y);
        canvas.fill(x0, y0, d(x + w), y0 + 1, argb);
    }

    /** Every other framebuffer row darkened, the glass of the device screens. */
    public void scanlines(float x, float y, float w, float h, int argb) {
        int x0 = d(x);
        int x1 = d(x + w);
        int y1 = d(y + h);
        for (int yy = d(y); yy < y1; yy += 2) {
            canvas.fill(x0, yy, x1, yy + 1, argb);
        }
    }

    public void vgrad(float x, float y, float w, float h, int top, int bottom) {
        canvas.gradient(d(x), d(y), d(x + w), d(y + h), top, top, bottom, bottom);
    }

    /** An item stack, an item icon or a texture, contained in the box. Wide boxes get gun art. */
    public void item(Object what, float x, float y, float w, float h, int tint) {
        canvas.item(what, d(x), d(y), d(x + w), d(y + h), tint);
    }

    /* ------------------------------------------------------------------ text */

    public static final int LEFT = 0;
    public static final int CENTER = 1;
    public static final int RIGHT = 2;

    /** Handjet, for numbers and Latin labels. */
    public Shaped pixel(String text, float size, int weight) {
        return TextEngine.shape(text, Fonts.pixel(weight), size * u / Px.s, 0.0F, false);
    }

    /** Noto Kufi, for Arabic. */
    public Shaped kufi(String text, float size, int weight) {
        return TextEngine.shape(text, Fonts.kufi(weight), size * u / Px.s, 0.0F, true);
    }

    /** Width in GUI pixels. */
    public float width(Shaped s) {
        return s == null ? 0.0F : s.width * Px.s / u;
    }

    public void text(Shaped s, float x, float baseline, int align, int argb) {
        if (s == null || s.isEmpty() || (argb >>> 24) == 0) {
            return;
        }
        float left = align == CENTER ? x - width(s) / 2.0F : align == RIGHT ? x - width(s) : x;
        int px = d(left);
        int by = d(baseline);
        if (s.fallback()) {
            canvas.fallbackText(s, px, by, argb);
            return;
        }
        blit(TextEngine.mask(s), px, by, argb);
    }

    /** Text with a one-pixel drop shadow under it, for numbers that sit over the world. */
    public void shadowed(Shaped s, float x, float baseline, int align, int argb) {
        int shadow = ((int) ((argb >>> 24) * 0.6F) << 24);
        text(s, x + 1.0F / u * Math.max(1.0F, u * 0.5F), baseline + 1.0F / u * Math.max(1.0F, u * 0.5F), align, shadow);
        text(s, x, baseline, align, argb);
    }

    /** A soft glow under the text, the phosphor look of the device screens. */
    public void glow(Shaped s, float x, float baseline, int align, float blurGui, int argb) {
        if (s == null || s.isEmpty() || s.fallback()) {
            return;
        }
        float left = align == CENTER ? x - width(s) / 2.0F : align == RIGHT ? x - width(s) : x;
        blit(TextEngine.glow(s, blurGui * u / Px.s, argb), d(left), d(baseline), 0xFFFFFFFF);
    }

    private void blit(Raster r, int penX, int baseline, int tint) {
        if (r == null || r.isEmpty()) {
            return;
        }
        int x0 = penX - r.originX;
        int y0 = baseline - r.baseline;
        canvas.raster(r, 0.0F, 0.0F, 1.0F, 1.0F, x0, y0, x0 + r.width, y0 + r.height, tint);
    }
}

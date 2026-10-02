package com.barbwra.mlum.client.ui.text;

import java.awt.font.GlyphVector;
import java.util.HashMap;

/**
 * One line of text, shaped the way Chrome shapes it for the design.
 *
 * <p>Everything a layout needs is in CSS pixels: {@link #width} (glyph advances each rounded to a
 * whole pixel, then letter-spacing added per glyph - which is exactly why "24,350" at 15px is 33px
 * wide in the browser and not 33.5), and the rounded {@link #ascent}/{@link #descent} that the
 * line-box maths runs on. The glyphs themselves are kept at device resolution for rasterising.</p>
 */
public final class Shaped {

    public final String text;
    /** Null only in fallback mode, when the fonts could not be loaded. */
    public final UiFont font;
    public final float size;
    public final float spacing;
    public final boolean rtl;
    public final int weight;
    public final boolean pixel;

    public final float width;
    public final int ascent;
    public final int descent;

    final float scale;
    final GlyphVector[] runs;
    final float[] runX;

    Raster mask;
    HashMap<String, Raster> styled;

    Shaped(String text, UiFont font, float size, float spacing, boolean rtl, int weight, boolean pixel,
           float width, int ascent, int descent, float scale, GlyphVector[] runs, float[] runX) {
        this.text = text;
        this.font = font;
        this.size = size;
        this.spacing = spacing;
        this.rtl = rtl;
        this.weight = weight;
        this.pixel = pixel;
        this.width = width;
        this.ascent = ascent;
        this.descent = descent;
        this.scale = scale;
        this.runs = runs;
        this.runX = runX;
    }

    /** True when there are no glyph outlines and the painter has to draw this with the game font. */
    public boolean fallback() {
        return runs == null;
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    void releaseRasters() {
        Rasters.release(mask);
        mask = null;
        if (styled != null) {
            for (Raster r : styled.values()) {
                Rasters.release(r);
            }
            styled = null;
        }
    }
}

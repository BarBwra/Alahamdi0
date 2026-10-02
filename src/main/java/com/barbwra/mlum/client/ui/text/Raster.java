package com.barbwra.mlum.client.ui.text;

/**
 * A block of ARGB pixels at device resolution: a line of text, a sprite, a glow.
 *
 * <p>The painter owns uploading it. {@link #handle} is where it keeps whatever it made from these
 * pixels (an atlas slot, a buffered image) so the pixels are only ever uploaded once, and
 * {@link Rasters#release} is how the cache tells it a raster is gone.</p>
 */
public final class Raster {

    private static long nextId = 1;

    public final long id;
    public final int width;
    public final int height;
    /** Non-premultiplied ARGB, row major. Kept until the painter has uploaded it. */
    public int[] argb;
    /**
     * True when the RGB is white and only the alpha carries the shape, so one upload can be drawn
     * in any colour by tinting. False for pre-coloured pixels (gradients, shadows, sprites).
     */
    public final boolean mask;
    /** Where the pen origin sits inside the raster: raster left edge = pen x - originX. */
    public final int originX;
    /** Where the baseline sits inside the raster: raster top = baseline y - baseline. */
    public final int baseline;

    /** Painter-private: whatever it built from these pixels. */
    public Object handle;
    public long lastUsed;
    /**
     * Bumped by whoever rewrites {@link #argb} in place (the transition's noise does, every frame),
     * so the painter knows its uploaded copy is stale and refreshes it where it already sits.
     */
    public int version;

    public Raster(int width, int height, int[] argb, boolean mask, int originX, int baseline) {
        this.id = nextId++;
        this.width = width;
        this.height = height;
        this.argb = argb;
        this.mask = mask;
        this.originX = originX;
        this.baseline = baseline;
    }

    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }
}

package com.barbwra.mlum.client.ui;

/**
 * CSS pixels to device pixels.
 *
 * <p>The design canvas is 1280x720 CSS pixels, scaled uniformly to the window; {@link #s} is how
 * many device pixels one CSS pixel covers (1.5 on a 1080p screen). Box edges are snapped one at a
 * time, so two boxes that touch in CSS still touch on screen. Border widths are floored to whole
 * device pixels with a minimum of one, which keeps hairlines crisp at 1.5x instead of smearing
 * them over two pixel rows.</p>
 */
public final class Px {

    public static float s = 1.0F;
    /** Canvas size in CSS pixels. At least 1280x720; the longer side grows to fill the window. */
    public static float W = 1280.0F;
    public static float H = 720.0F;

    private Px() {
    }

    /** Fits the design into a window of the given device size. */
    public static void fit(int deviceW, int deviceH) {
        s = Math.min(deviceW / 1280.0F, deviceH / 720.0F);
        if (s <= 0.0F) {
            s = 1.0F;
        }
        W = deviceW / s;
        H = deviceH / s;
    }

    public static int d(float css) {
        return Math.round(css * s);
    }

    /** Device width of a CSS border: floored, never below one pixel when it exists at all. */
    public static int border(float css) {
        if (css <= 0.0F) {
            return 0;
        }
        return Math.max(1, (int) Math.floor(css * s + 1e-3F));
    }

    public static float css(float device) {
        return device / s;
    }
}

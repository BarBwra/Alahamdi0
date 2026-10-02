package com.barbwra.mlum.client.ui.text;

import java.awt.Font;

/**
 * One face of one of the two UI families, exactly the files the HTML design loads.
 *
 * <p>Vertical metrics follow Chrome, which is what the design was measured in: ascent and descent
 * are the font's em values times the CSS size, each <b>rounded to a whole CSS pixel</b>. Both
 * families carry identical hhea and OS/2 typo values, so there is no table choice to get wrong.</p>
 */
public final class UiFont {

    public static final int KUFI = 0;
    public static final int PIXEL = 1;

    public final int family;
    public final int weight;
    /** Size 1, with kerning and ligatures on, the way a browser shapes by default. */
    final Font base;
    final float ascentEm;
    final float descentEm;
    /** Stable small integer, used in cache keys. */
    final int id;

    UiFont(int family, int weight, Font base, float ascentEm, float descentEm, int id) {
        this.family = family;
        this.weight = weight;
        this.base = base;
        this.ascentEm = ascentEm;
        this.descentEm = descentEm;
        this.id = id;
    }

    /** Ascent in whole CSS pixels at {@code size}. */
    public int ascent(float size) {
        return Math.round(ascentEm * size);
    }

    /** Descent in whole CSS pixels at {@code size}. */
    public int descent(float size) {
        return Math.round(descentEm * size);
    }

    public boolean isPixel() {
        return family == PIXEL;
    }

    @Override
    public String toString() {
        return (family == KUFI ? "kufi-" : "pixel-") + weight;
    }
}

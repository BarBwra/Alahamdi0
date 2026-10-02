package com.barbwra.mlum.client.ui.layout;

import com.barbwra.mlum.client.ui.text.UiFont;

/**
 * One inline run inside a {@link Node#TEXT} node: a piece of text in one style, or an atomic
 * inline box (an icon, a coin). Mirrors an HTML text node or inline element.
 */
public final class Span {

    public static final int PLAIN = 0;
    public static final int GRADIENT = 1;
    public static final int SHADOW = 2;

    public String text;
    public UiFont font;
    /** CSS px. */
    public float size;
    /** Line height in CSS px (a number line-height times this span's own size). */
    public float lh;
    public int color;
    /** letter-spacing in CSS px. */
    public float ls;
    public boolean rtl = true;

    /** An atomic inline box instead of text. */
    public Node box;
    /** CSS vertical-align as a length: positive raises. */
    public float valign;

    public int kind = PLAIN;
    public float[] gradStops;
    public int[] gradColors;
    /** Gradient box relative to the baseline, CSS px (top negative). */
    public float gradTop;
    public float gradBottom;
    public int shadowColor;
    public float shadowDx;
    public float shadowDy;
    /** text-shadow 0 0 {glowBlur} {glowColor}, drawn under the run; 0 = none. */
    public float glowBlur;
    public int glowColor;

    public Span() {
    }

    public static Span text(String text, UiFont font, float size, float lh, int color) {
        Span s = new Span();
        s.text = text;
        s.font = font;
        s.size = size;
        s.lh = lh;
        s.color = color;
        return s;
    }

    public static Span box(Node box, float valign) {
        Span s = new Span();
        s.box = box;
        s.valign = valign;
        return s;
    }

    public Span ltr() {
        rtl = false;
        return this;
    }

    public Span spacing(float css) {
        ls = css;
        return this;
    }

    public Span gradient(float top, float bottom, float[] stops, int[] colors) {
        kind = GRADIENT;
        gradTop = top;
        gradBottom = bottom;
        gradStops = stops;
        gradColors = colors;
        return this;
    }

    public Span glow(float blur, int argb) {
        glowBlur = blur;
        glowColor = argb;
        return this;
    }

    public Span shadow(int argb, float dx, float dy) {
        kind = SHADOW;
        shadowColor = argb;
        shadowDx = dx;
        shadowDy = dy;
        return this;
    }
}

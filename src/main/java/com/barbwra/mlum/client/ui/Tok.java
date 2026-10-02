package com.barbwra.mlum.client.ui;

/** The design's colour tokens, straight from its {@code :root}. ARGB. */
public final class Tok {

    private Tok() {
    }

    public static int rgba(int rgb, double a) {
        return ((int) Math.round(a * 255.0) << 24) | (rgb & 0xFFFFFF);
    }

    public static int rgb(int rgb) {
        return 0xFF000000 | rgb;
    }

    public static final int PAGE = rgb(0x0b0d0a);
    public static final int PANEL = rgba(0x0b0d0a, 0.86);
    public static final int PANEL_SOLID = rgb(0x11140f);
    public static final int SLOT = rgb(0x161a13);
    public static final int SLOT_HI = rgb(0x1e2319);
    public static final int CARD = rgba(0x161a13, 0.88);
    public static final int LINE = rgb(0x2b3026);
    public static final int LINE_SOFT = rgb(0x1f231b);
    public static final int BONE = rgb(0xece6d4);
    public static final int SOFT = rgb(0xd4ceba);
    public static final int MUTED = rgb(0xa19e8b);
    public static final int FAINT = rgb(0x6e6d5f);
    public static final int AMBER = rgb(0xf0a93b);
    public static final int AMBER_DIM = rgb(0x8a6124);
    public static final int AMBER_GLOW = rgba(0xf0a93b, 0.13);
    public static final int RUST = rgb(0xe0613f);
    public static final int SAGE = rgb(0x93c46f);
    public static final int STEEL = rgb(0x8fb0cc);
    public static final int FOOD = rgb(0xd0913e);
    public static final int CASH_HI = rgb(0xdcf7c6);
    public static final int CASH = rgb(0x8fd16a);
    public static final int CASH_LO = rgb(0x3f8a3a);
    public static final int CASH_LINE = rgb(0x29451f);

    public static final int INK = rgb(0x14110a);
    public static final int BTN_INK = rgb(0x16120a);
    public static final int SLOT_BORDER = rgb(0x272b22);

    /** common, uncommon, rare, epic, legendary, mythic. */
    public static final int[] RARITY = {
            rgb(0xb9b7a6), rgb(0x7cc25a), rgb(0x4ea8e8), rgb(0xb57ce8), rgb(0xf0a93b), rgb(0xff4d6a)
    };

    public static int rarity(int tier) {
        return RARITY[Math.max(0, Math.min(RARITY.length - 1, tier))];
    }

    /** {@code color-mix(in srgb, c p%, transparent)}: the colour at alpha p. */
    public static int mixTransparent(int c, double p) {
        return rgba(c, p);
    }

    /** {@code color-mix(in srgb, a p%, b)}. */
    public static int mix(int a, int b, double p) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = (int) Math.round(ar * p + br * (1 - p));
        int g = (int) Math.round(ag * p + bg * (1 - p));
        int bl = (int) Math.round(ab * p + bb * (1 - p));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}

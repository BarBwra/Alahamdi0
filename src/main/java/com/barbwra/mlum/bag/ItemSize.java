package com.barbwra.mlum.bag;

import javax.annotation.Nullable;

/**
 * How many cells an item occupies in the bag: {@code W} across by {@code H} down.
 *
 * <p><b>Width is capped at 9 because the grid is 9 wide.</b> A 10-wide item could never be placed
 * anywhere, so accepting one would mean an item that exists in the config and can never exist in
 * a bag - the config is clamped at parse time instead, and the clamp is logged.</p>
 *
 * <p>Height is deliberately <i>not</i> capped. The bag grows with the worn backpack, so the tallest
 * placeable item depends on runtime state that the config cannot see; {@link BagGrid} is the thing
 * that knows how many rows a section actually has, and it is the thing that rejects the placement.</p>
 */
public record ItemSize(int width, int height) {

    /** The default for anything the config does not mention. */
    public static final ItemSize ONE = new ItemSize(1, 1);

    public static final int MAX_WIDTH = 9;

    public ItemSize {
        width = Math.max(1, Math.min(MAX_WIDTH, width));
        height = Math.max(1, height);
    }

    public int cells() {
        return width * height;
    }

    public boolean isSingle() {
        return width == 1 && height == 1;
    }

    /** {@code "7x2"}. What the size badge on a multi-cell item shows, and what the config holds. */
    @Override
    public String toString() {
        return width + "x" + height;
    }

    /**
     * Parses a {@code "WxH"} config value.
     *
     * <p>Returns null rather than throwing on anything malformed. Every caller logs and falls back
     * to {@link #ONE}, because the spec is explicit that one bad line must never crash the mod -
     * an admin's typo should cost them one item's size, not the server.</p>
     */
    @Nullable
    public static ItemSize parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().toLowerCase(java.util.Locale.ROOT);
        int split = text.indexOf('x');
        if (split <= 0 || split >= text.length() - 1) {
            return null;
        }
        try {
            int w = Integer.parseInt(text.substring(0, split).trim());
            int h = Integer.parseInt(text.substring(split + 1).trim());
            if (w < 1 || h < 1) {
                return null;
            }
            return new ItemSize(w, h);
        } catch (NumberFormatException malformed) {
            return null;
        }
    }
}

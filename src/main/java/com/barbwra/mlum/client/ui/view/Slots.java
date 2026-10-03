package com.barbwra.mlum.client.ui.view;

import com.barbwra.mlum.client.ui.Canvas;
import com.barbwra.mlum.client.ui.Draw;
import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.Tok;
import com.barbwra.mlum.client.ui.layout.Layout;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.client.ui.text.Shaped;
import com.barbwra.mlum.client.ui.text.TextEngine;

/** Item cells: {@code .slot} in all its states, {@code .bitem} (multi-cell bag items), {@code .att}. */
public final class Slots {

    public static final int IDLE = 0;
    public static final int SEL = 1;
    public static final int ACTIVE = 2;
    public static final int HOT = 3;
    public static final int NOPE = 4;
    /** An attachment mount the selected or carried attachment would go on: lit and breathing. */
    public static final int FIT = 5;
    /**
     * An empty cell with the cursor on it.
     *
     * <p>{@link #slot} only ever reacted to hover when it held something, so an empty quick-access
     * cell gave no sign it was a place things go. This is the same amber square the bag grid shows,
     * for the same reason.</p>
     */
    public static final int OPEN = 5;

    private Slots() {
    }

    /**
     * {@code .slot}: a square cell, optionally holding an item.
     *
     * @param spr   the {@code .spr} box inside, CSS px (30 in a 44 cell, 34 gear, 46 details...)
     * @param hover {@code button.slot:hover} - only cells that hold an item react
     * @param qk    the quick-access key number in the corner, or null
     */
    public static Node slot(Item it, float size, float spr, boolean hover, int state, String qk) {
        Node n = Css.leaf(size, size);
        n.border(1.0F, Tok.SLOT_BORDER);
        boolean filled = it != null;
        boolean hovered = hover && filled;
        n.bg(hovered || state == OPEN ? Tok.SLOT_HI : Tok.SLOT);
        // border colour, in the design's cascade order
        int border = Tok.SLOT_BORDER;
        if (hovered) {
            border = Tok.AMBER_DIM;
        }
        if (state == OPEN) {
            border = Tok.AMBER;
        }
        if (state == SEL) {
            border = Tok.AMBER;
        } else if (state == ACTIVE) {
            border = Tok.rgb(0x8d8a78);
        }
        if (filled && it.rarity == 5 && !hovered) {
            border = Tok.mix(Tok.RARITY[5], Tok.SLOT_BORDER, 0.5);
        }
        if (state == HOT) {
            border = Tok.SAGE;
        } else if (state == NOPE) {
            border = Tok.RUST;
        }
        n.borderColor = border;
        final int st = state;
        n.under((c, nd) -> {
            if (filled && it.glows()) {
                Css.rarityGlow(c, nd, Tok.rarity(it.rarity), 0.24F, 0.68F);
            }
            switch (st) {
                case SEL -> Css.innerRing(c, nd, Tok.AMBER);
                case ACTIVE -> Css.innerRing(c, nd, Draw.rgba(0xECE6D4, 0.25F));
                case HOT -> Css.innerRing(c, nd, Tok.SAGE);
                case NOPE -> Css.innerRing(c, nd, Tok.RUST);
                case OPEN -> {
                    Draw.rect(c, nd.x, nd.y, nd.w, nd.h, Draw.rgba(0xF0A93B, 0.10F));
                    Css.innerRing(c, nd, Tok.AMBER);
                }
                default -> Css.slotInset(c, nd);
            }
        });
        n.over((c, nd) -> {
            if (filled) {
                float x = nd.x + (nd.w - spr) / 2.0F;
                float y = nd.y + (nd.h - spr) / 2.0F;
                drawItem(c, it, x, y, spr, spr);
            }
            if (qk != null) {
                // .qk.num - top 3px, right 4px, 12px faint
                text(c, qk, Css.P(600), 12, 0.24F, false, Tok.FAINT, nd.x + nd.w - nd.br - 4, nd.y + nd.bt + 3, true);
            }
            if (filled) {
                if (it.count > 1) {
                    qty(c, nd, String.valueOf(it.count));
                }
                if (it.durability >= 0.0F) {
                    durability(c, nd, it.durability);
                }
                Css.rarityLine(c, nd, Tok.rarity(it.rarity));
            }
        });
        return n;
    }

    /** Draws an item into a CSS box through the canvas. */
    public static void drawItem(Canvas c, Item it, float x, float y, float w, float h) {
        if (it == null || it.handle == null) {
            return;
        }
        c.item(it.handle, Px.d(x), Px.d(y), Px.d(x + w), Px.d(y + h), it.tint);
    }

    /** {@code .qty}: bottom 3px, right 3px, 600 15px/1 white with a hard black shadow. */
    public static void qty(Canvas c, Node nd, String count) {
        Shaped s = TextEngine.shape(count, Css.P(600), 15, 0.0F, false);
        float[] ab = Layout.inlineBox(Css.P(600), 15, 15);
        float right = nd.x + nd.w - nd.br - 3;
        float bottom = nd.y + nd.h - nd.bb - 3;
        float base = bottom - 15 + ab[0];
        Draw.shadowText(c, s, right - s.width, base, 0xFFFFFFFF, 0xFF000000, 1.0F, 1.0F);
    }

    /** {@code .dur}: a black 2px track 6px in from the sides and bottom; the fill runs from the right. */
    public static void durability(Canvas c, Node nd, float value) {
        float x = nd.x + nd.bl + 6;
        float w = nd.w - nd.bl - nd.br - 12;
        float y = nd.y + nd.h - nd.bb - 6 - 2;
        Draw.rect(c, x, y, w, 2, 0xFF000000);
        float fw = w * Math.max(0.0F, Math.min(1.0F, value));
        Draw.rect(c, x + w - fw, y, fw, 2, value < 0.5F ? Tok.AMBER : Tok.SAGE);
    }

    /**
     * One absolutely placed line of text. {@code right}/{@code top} are the box edges it hangs from;
     * the box is one line of {@code lh}-high text, so the baseline follows Chrome's line maths.
     */
    public static void text(Canvas c, String t, com.barbwra.mlum.client.ui.text.UiFont f, float size,
                            float spacing, boolean rtl, int color, float anchorX, float top, boolean fromRight) {
        Shaped s = TextEngine.shape(t, f, size, spacing, rtl);
        float[] ab = Layout.inlineBox(f, size, size);
        float x = fromRight ? anchorX - s.width : anchorX;
        Draw.text(c, s, x, top + ab[0], color);
    }

    /* ================================================================== bag items */

    /**
     * {@code .bitem}: an item filling its footprint in the bag grid. Size label top-left for
     * anything bigger than one cell; the picture fills the box less 12px, at most 64px tall unless
     * the item stands upright.
     */
    public static Node bagItem(Item it, float w, float h, boolean hover, boolean sel) {
        Node n = Css.leaf(w, h);
        n.border(1.0F, sel ? Tok.AMBER : hover ? Tok.AMBER_DIM : Tok.rgb(0x3a4030));
        n.bg(hover ? Tok.rgba(0x242a1d, 0.97) : Tok.rgba(0x1c2117, 0.95));
        n.under((c, nd) -> {
            if (it.glows()) {
                Css.rarityGlow(c, nd, Tok.rarity(it.rarity), 0.20F, 0.70F);
            }
        });
        n.over((c, nd) -> {
            float bw = nd.w - 12;
            float bh = nd.h - 12;
            if (!it.tall()) {
                bh = Math.min(bh, 64);
            }
            // the .spr box, centred by the grid; its content (the picture) is contained inside it
            float x = nd.x + (nd.w - bw) / 2.0F;
            float y = nd.y + (nd.h - bh) / 2.0F;
            drawItem(c, it, x, y, bw, bh);
            if (it.w * it.h > 1) {
                text(c, it.w + "x" + it.h, Css.P(600), 12, 0.0F, false, Tok.FAINT,
                        nd.x + nd.bl + 4, nd.y + nd.bt + 3, false);
            }
            if (it.count > 1) {
                qty(c, nd, String.valueOf(it.count));
            }
            if (it.durability >= 0.0F) {
                durability(c, nd, it.durability);
            }
            Css.rarityLine(c, nd, Tok.rarity(it.rarity));
        });
        return n;
    }

    /* ================================================================== mounts */

    /** {@code .att}: a 26px attachment mount, or its dashed empty state with a 2x ghost. */
    public static Node mount(Item it, String ghost, boolean hover, int state) {
        Node n = Css.leaf(26, 26).bg(Tok.SLOT);
        if (state == FIT) {
            float pulse = 0.5F + 0.5F * (float) Math.sin(com.barbwra.mlum.client.ui.mc.UiState.now() / 170.0D);
            n.bg(Tok.rgba(0xf0a93b, 0.10 + 0.10 * pulse)).border(1.0F, Tok.AMBER);
            n.under((c, nd) -> Draw.rasterClipped(c, com.barbwra.mlum.client.ui.Art.radial(0.9F),
                    nd.x - 6, nd.y - 6, nd.w + 12, nd.h + 12, nd.x - 6, nd.y - 6, nd.x + nd.w + 6, nd.y + nd.h + 6,
                    Draw.rgba(0xF0A93B, 0.25F + 0.25F * pulse)));
            n.over((c, nd) -> {
                if (it != null) {
                    drawItem(c, it, nd.x + 4, nd.y + 4, 18, 18);
                } else if (ghost != null) {
                    int[] d = com.barbwra.mlum.client.ui.Art.dims(ghost);
                    float gw = d[0] * 2;
                    float gh = d[1] * 2;
                    Draw.raster(c, com.barbwra.mlum.client.ui.Art.ghost(ghost), nd.x + (nd.w - gw) / 2.0F,
                            nd.y + (nd.h - gh) / 2.0F, gw, gh, Draw.rgba(0xF0A93B, 0.55F + 0.35F * pulse));
                }
                Css.innerRing(c, nd, Draw.rgba(0xF0A93B, 0.4F + 0.5F * pulse));
            });
            return n;
        }
        if (it != null) {
            n.border(1.0F, hover ? Tok.AMBER_DIM : Tok.rgb(0x2b3025));
            n.over((c, nd) -> {
                drawItem(c, it, nd.x + 4, nd.y + 4, 18, 18);
                Css.rarityLine(c, nd, Tok.rarity(it.rarity));
            });
        } else {
            int border = state == HOT ? Tok.SAGE : state == NOPE ? Tok.RUST : Tok.rgb(0x343a2e);
            n.border(1.0F, border).dashed();
            if (ghost != null) {
                int[] d = com.barbwra.mlum.client.ui.Art.dims(ghost);
                float gw = d[0] * 2;
                float gh = d[1] * 2;
                n.over((c, nd) -> Draw.raster(c, com.barbwra.mlum.client.ui.Art.ghost(ghost),
                        nd.x + (nd.w - gw) / 2.0F, nd.y + (nd.h - gh) / 2.0F, gw, gh, Draw.rgba(0xFFFFFF, 0.15F)));
            }
        }
        return n;
    }
}

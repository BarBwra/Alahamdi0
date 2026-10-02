package com.barbwra.mlum.client.ui.view;

import com.barbwra.mlum.client.ui.Art;
import com.barbwra.mlum.client.ui.Draw;
import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.Tok;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.client.ui.layout.Span;
import com.barbwra.mlum.client.ui.text.Fonts;
import com.barbwra.mlum.client.ui.text.Raster;
import com.barbwra.mlum.client.ui.text.UiFont;

import static com.barbwra.mlum.client.ui.layout.Node.AUTO;
import static com.barbwra.mlum.client.ui.layout.Node.CENTER;

/**
 * The design's CSS classes as node factories. Each method is named after the class it copies and
 * says which rules it carries, so a view reads like the HTML it was transcribed from.
 */
public final class Css {

    private Css() {
    }

    /* ================================================================== fonts */

    public static UiFont K(int weight) {
        return Fonts.kufi(weight);
    }

    public static UiFont P(int weight) {
        return Fonts.pixel(weight);
    }

    /** The screen's inherited line-height. */
    public static final float LH = 1.6F;

    /* ================================================================== nodes */

    public static Node block() {
        return new Node(Node.BLOCK);
    }

    public static Node row() {
        return new Node(Node.ROW);
    }

    public static Node col() {
        return new Node(Node.COL);
    }

    public static Node grid(float... cols) {
        return new Node(Node.GRID).cols(cols);
    }

    /**
     * A box with no children. A positive size is the CSS {@code width}/{@code height} (border box);
     * zero leaves that axis auto, so the box stretches the way an empty grid or flex item does.
     */
    public static Node leaf(float w, float h) {
        Node n = new Node(Node.LEAF);
        n.width = w > 0 ? w : AUTO;
        n.height = h > 0 ? h : AUTO;
        return n;
    }

    /** flex:1 - grow 1, shrink 1, basis 0. */
    public static Node flex1(Node n) {
        return n.grow(1.0F).shrink(1.0F).basis(0.0F);
    }

    /* ================================================================== text */

    /** A text block whose strut and only run share one style. {@code lh} is a line-height factor. */
    public static Node txt(String text, UiFont font, float size, float lh, int color) {
        Node n = new Node(Node.TEXT);
        n.font = font;
        n.size = size;
        n.lh = size * lh;
        n.color = color;
        n.span(Span.text(text, font, size, size * lh, color));
        return n;
    }

    /** A text block with a strut but no runs yet, for mixed inline content. */
    public static Node inline(UiFont font, float size, float lh, int color) {
        Node n = new Node(Node.TEXT);
        n.font = font;
        n.size = size;
        n.lh = size * lh;
        n.color = color;
        return n;
    }

    public static Span sp(String text, UiFont font, float size, float lh, int color) {
        return Span.text(text, font, size, size * lh, color);
    }

    /** {@code .num}: pixel 600, left to right, 0.02em tracking. */
    public static Span nsp(String text, float size, float lh, int color) {
        return Span.text(text, P(600), size, size * lh, color).ltr().spacing(0.02F * size);
    }

    /** A standalone {@code .num} element (its own strut is the pixel face). */
    public static Node num(String text, float size, float lh, int color) {
        Node n = new Node(Node.TEXT);
        n.font = P(600);
        n.size = size;
        n.lh = size * lh;
        n.color = color;
        n.rtl = false;
        n.span(nsp(text, size, lh, color));
        return n;
    }

    /** A pixel-font text element with its own tracking, weight and line height. */
    public static Node px(String text, int weight, float size, float lh, float spacingEm, int color) {
        Node n = new Node(Node.TEXT);
        n.font = P(weight);
        n.size = size;
        n.lh = size * lh;
        n.color = color;
        n.rtl = false;
        n.span(Span.text(text, P(weight), size, size * lh, color).ltr().spacing(spacingEm * size));
        return n;
    }

    /* ================================================================== art */

    /** {@code .px}: a one-colour icon in a w x h box. */
    public static Node icon(String name, float w, float h, int color) {
        return leaf(w, h).data(name).under((c, n) -> {
            Raster r = Art.mono((String) n.data);
            Draw.raster(c, r, n.x, n.y, n.w, n.h, color);
        });
    }

    /** {@code .spr}: a sprite centred in its box, contained, pixelated. */
    public static Node sprite(String name, float w, float h) {
        return leaf(w, h).data(name).under((c, n) -> drawSprite(c, (String) n.data, n.x, n.y, n.w, n.h, 0xFFFFFFFF));
    }

    /** background-size:contain, centred - what every {@code .spr} does. */
    public static void drawSprite(com.barbwra.mlum.client.ui.Canvas c, String name, float x, float y, float w,
                                  float h, int tint) {
        Raster r = Art.sprite(name);
        if (r.isEmpty()) {
            return;
        }
        float k = Math.min(w / r.width, h / r.height);
        float dw = r.width * k;
        float dh = r.height * k;
        Draw.raster(c, r, x + (w - dw) / 2.0F, y + (h - dh) / 2.0F, dw, dh, tint);
    }

    /** {@code .gh}: an empty socket's silhouette at a whole-number scale, 15% bone. */
    public static Node ghost(String name, int scale) {
        int[] d = Art.dims(name);
        return leaf(d[0] * scale, d[1] * scale).data(name).under((c, n) ->
                Draw.raster(c, Art.ghost((String) n.data), n.x, n.y, n.w, n.h, Draw.rgba(0xFFFFFF, 0.15F)));
    }

    /* ================================================================== boxes */

    /** A plain coloured box - bars, stripes, dots. */
    public static Node bar(float w, float h, int argb) {
        return leaf(w, h).bg(argb);
    }

    /**
     * {@code .panel}: panel fill, 1px line border, 12/14/14/14 padding, the faint top highlight
     * ({@code ::before}) and the two amber-dim corner brackets ({@code ::after}).
     */
    public static Node panel(Node n) {
        n.bg(Tok.PANEL).border(1.0F, Tok.LINE).pad(12, 14, 14, 14);
        return decoratePanel(n);
    }

    /** Both panel pseudo-elements are positioned, so they paint over the panel's in-flow content. */
    public static Node decoratePanel(Node n) {
        n.over(Css::panelDecor);
        return n;
    }

    /** The panel's {@code ::before} highlight and {@code ::after} brackets. */
    public static void panelDecor(com.barbwra.mlum.client.ui.Canvas c, Node n) {
        // ::before - 1px across the padding box, just under the top border
        float b = n.bt;
        Draw.rect(c, n.x + n.bl, n.y + b, n.w - n.bl - n.br, 1.0F, Draw.rgba(0xECE6D4, 0.035F));
        // ::after - inset:-1px, so the brackets sit on the outer edge of the border box
        int x0 = Px.d(n.x);
        int y0 = Px.d(n.y);
        int x1 = Px.d(n.x + n.w);
        int y1 = Px.d(n.y + n.h);
        int t = Px.border(1.0F);
        int len = Px.d(12.0F);
        int col = Tok.AMBER_DIM;
        c.fill(x1 - len, y0, x1, y0 + t, col);
        c.fill(x1 - t, y0, x1, y0 + len, col);
        c.fill(x0, y1 - t, x0 + len, y1, col);
        c.fill(x0, y1 - len, x0 + t, y1, col);
    }

    /** {@code .phead h3}: amber post, then the title. */
    public static Node h3(String title) {
        return row().align(CENTER).gap(8).add(
                bar(3, 13, Tok.AMBER),
                txt(title, K(600), 13.5F, 1.4F, Tok.BONE));
    }

    /**
     * {@code .phead}: title on the start side, an aside on the end side, a soft rule under both.
     */
    public static Node phead(String title, Node aside) {
        Node n = row().align(CENTER).justify(Node.BETWEEN).gap(10).minH(30);
        n.pad(0, 0, 9, 0).mar(0, 0, 12, 0).border(0, 0, 1, 0, Tok.LINE_SOFT);
        n.add(h3(title));
        if (aside != null) {
            n.add(aside);
        }
        return n;
    }

    /** {@code .phead .aside}: a gap-6 row of muted 11.5px text; numbers inside are 17px. */
    public static Node aside() {
        return row().align(CENTER).gap(6);
    }

    public static Node asideText(String t) {
        return txt(t, K(400), 11.5F, LH, Tok.MUTED);
    }

    public static Node asideNum(String t) {
        return num(t, 17, LH, Tok.MUTED);
    }

    /* ---- chips ---- */

    public static final int CHIP_RUST = 0;
    public static final int CHIP_SAGE = 1;
    public static final int CHIP_AMBER = 2;
    public static final int CHIP_MUTE = 3;

    /** {@code .chip}: 600 11px/1, 5/7/4 padding, 1px border in the text colour. */
    public static Node chip(String text, int color, int bg, int border) {
        Node n = row().align(CENTER).gap(5).pad(5, 7, 4, 7).border(1.0F, border).bg(bg);
        n.add(txt(text, K(600), 11, 1.0F, color));
        return n;
    }

    public static Node chip(String text, int kind) {
        return switch (kind) {
            case CHIP_RUST -> chip(text, Tok.RUST, Tok.rgba(0xe0613f, 0.1), Tok.RUST);
            case CHIP_SAGE -> chip(text, Tok.SAGE, Tok.rgba(0x93c46f, 0.1), Tok.SAGE);
            case CHIP_AMBER -> chip(text, Tok.AMBER, Tok.AMBER_GLOW, Tok.AMBER);
            default -> chip(text, Tok.MUTED, 0, Tok.LINE);
        };
    }

    /** {@code .chip.rc}: rarity coloured, 10% wash. */
    public static Node rarityChip(String text, int rarityColor) {
        return chip(text, rarityColor, Tok.mixTransparent(rarityColor, 0.10), rarityColor);
    }

    /** A chip whose content is a mixed row (text + number). */
    public static Node chipRow(int color, int bg, int border) {
        return row().align(CENTER).gap(5).pad(5, 7, 4, 7).border(1.0F, border).bg(bg);
    }

    /* ---- buttons ---- */

    public static final int BTN = 0;
    public static final int BTN_GHOST = 1;
    public static final int BTN_DANGER = 2;
    public static final int BTN_CONFIRM = 3;

    /** {@code .btn} / {@code .btn.sm}, filled, ghost, danger or confirm; disabled at 42%. */
    public static Node btn(String label, int style, boolean small, boolean disabled) {
        float h = small ? 30 : 40;
        float padX = small ? 13 : 20;
        float fs = small ? 12.5F : 14;
        int bg;
        int border;
        int fg;
        switch (style) {
            case BTN_GHOST -> {
                bg = 0;
                border = Tok.AMBER;
                fg = Tok.AMBER;
            }
            case BTN_DANGER -> {
                bg = Tok.RUST;
                border = Tok.RUST;
                fg = Tok.rgb(0x170b07);
            }
            case BTN_CONFIRM -> {
                bg = Tok.rgb(0xffcf7a);
                border = Tok.rgb(0xffcf7a);
                fg = Tok.BTN_INK;
            }
            default -> {
                bg = Tok.AMBER;
                border = Tok.AMBER;
                fg = Tok.BTN_INK;
            }
        }
        Node n = row().align(CENTER).gap(6).h(h).pad(0, padX, 0, padX).border(1.0F, border).bg(bg);
        n.add(txt(label, K(600), fs, 1.0F, fg));
        if (disabled) {
            n.opacity(0.42F);
        }
        return n;
    }

    /** A button whose label mixes text and a number ({@code ناقصك 1,500}). */
    public static Node btnRow(int style, boolean small, boolean disabled) {
        Node n = btn("", style, small, disabled);
        n.kids.clear();
        return n;
    }

    /** {@code .tool}: small outlined control, 600 11.5px/1, optional 12px icon. */
    public static Node tool(String label, String icon, float height, float padX) {
        Node n = row().align(CENTER).gap(6).h(height).pad(0, padX, 0, padX).border(1.0F, Tok.LINE)
                .bg(Draw.rgba(0xFFFFFF, 0.03F));
        if (icon != null) {
            n.add(icon(icon, 12, 12, Tok.MUTED));
        }
        if (label != null) {
            n.add(txt(label, K(600), 11.5F, 1.0F, Tok.MUTED));
        }
        return n;
    }

    /** {@code .keycap} (24px, 3px bottom) or {@code .keycap.sm} (18px, 2px bottom). */
    public static Node keycap(String key, boolean small, int color, int border) {
        float h = small ? 18 : 24;
        float fs = small ? 12 : 15;
        Node n = row().align(CENTER).justify(CENTER).h(h).minW(h).pad(0, small ? 5 : 6, 0, small ? 5 : 6)
                .border(1.0F, border).bg(Draw.rgba(0xFFFFFF, 0.03F));
        n.bb = small ? 2 : 3;
        n.add(px(key, 600, fs, 1.0F, 0.0F, color));
        return n;
    }

    /* ---- slots ---- */

    /**
     * {@code .slot}: slot fill, #272b22 border, and its two inset shadows - 2px of black at 50% on
     * the top and left inner edges, 1px of white at 3.5% on the bottom and right.
     */
    public static Node slot(float size) {
        return slotBox(leaf(size, size));
    }

    public static Node slotBox(Node n) {
        n.bg(Tok.SLOT).border(1.0F, Tok.SLOT_BORDER);
        n.under((c, nd) -> slotInset(c, nd));
        return n;
    }

    /** The two inset shadows, inside the border. */
    public static void slotInset(com.barbwra.mlum.client.ui.Canvas c, Node n) {
        int x0 = Px.d(n.x) + Px.border(n.bl);
        int y0 = Px.d(n.y) + Px.border(n.bt);
        int x1 = Px.d(n.x + n.w) - Px.border(n.br);
        int y1 = Px.d(n.y + n.h) - Px.border(n.bb);
        int two = Px.d(2);
        int one = Px.d(1);
        int dark = Draw.rgba(0x000000, 0.5F);
        int lite = Draw.rgba(0xFFFFFF, 0.035F);
        // inset 2px 2px: bands along the top and left
        c.fill(x0, y0, x1, y0 + two, dark);
        c.fill(x0, y0 + two, x0 + two, y1, dark);
        // inset -1px -1px: bands along the bottom and right
        c.fill(x0 + two, y1 - one, x1, y1, lite);
        c.fill(x1 - one, y0 + two, x1, y1 - one, lite);
    }

    /** An inner 1px ring - {@code box-shadow: inset 0 0 0 1px c}. */
    public static void innerRing(com.barbwra.mlum.client.ui.Canvas c, Node n, int argb) {
        int bw = Px.border(1.0F);
        int x0 = Px.d(n.x) + bw;
        int y0 = Px.d(n.y) + bw;
        int x1 = Px.d(n.x + n.w) - bw;
        int y1 = Px.d(n.y + n.h) - bw;
        int r = Px.border(1.0F);
        c.fill(x0, y0, x1, y0 + r, argb);
        c.fill(x0, y1 - r, x1, y1, argb);
        c.fill(x0, y0 + r, x0 + r, y1 - r, argb);
        c.fill(x1 - r, y0 + r, x1, y1 - r, argb);
    }

    /** {@code [data-r]::after}: the rarity line, inset 3px from the sides, 2px up from the bottom. */
    public static void rarityLine(com.barbwra.mlum.client.ui.Canvas c, Node n, int argb) {
        float x = n.x + n.bl + 3;
        float w = n.w - n.bl - n.br - 6;
        float y = n.y + n.h - n.bb - 2 - 2;
        Draw.rect(c, x, y, w, 2, argb);
    }

    /**
     * {@code radial-gradient(circle at 50% 55%, color-mix(rc a%, transparent), transparent e%)} over
     * the padding box, cut to it.
     */
    public static void rarityGlow(com.barbwra.mlum.client.ui.Canvas c, Node n, int rc, float alpha, float edge) {
        float px0 = n.x + n.bl;
        float py0 = n.y + n.bt;
        float pw = n.w - n.bl - n.br;
        float ph = n.h - n.bt - n.bb;
        float cx = px0 + pw * 0.5F;
        float cy = py0 + ph * 0.55F;
        // farthest-corner radius
        float dx = Math.max(cx - px0, px0 + pw - cx);
        float dy = Math.max(cy - py0, py0 + ph - cy);
        float r = (float) Math.sqrt(dx * dx + dy * dy);
        Raster glow = Art.radial(edge);
        Draw.rasterClipped(c, glow, cx - r, cy - r, r * 2, r * 2, px0, py0, px0 + pw, py0 + ph,
                Draw.rgba(rc, alpha));
    }

    /* ---- bars ---- */

    /** {@code .bar}: track with a fill from the start (right) edge. */
    public static Node meter(float height, float fraction, int track, int fill) {
        Node n = leaf(0, height).bg(track);
        n.minWidth = 0;
        final float f = Math.max(0.0F, Math.min(1.0F, fraction));
        n.under((c, nd) -> {
            float fw = nd.w * f;
            Draw.rect(c, nd.x + nd.w - fw, nd.y, fw, nd.h, fill);
        });
        return n;
    }

    public static float auto() {
        return AUTO;
    }
}

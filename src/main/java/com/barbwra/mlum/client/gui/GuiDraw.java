package com.barbwra.mlum.client.gui;

import com.barbwra.mlum.menu.MlumLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Every shape in the UI, drawn procedurally. No textures, no atlas, no resource pack to break.
 *
 * <h2>The four layers every surface is built from</h2>
 * <p>A panel here is never one fill. It is a stack, and the order matters because each layer only
 * works on top of the one below:</p>
 * <ol>
 *   <li><b>Plate</b> - a top-lit gradient with its corners notched off. The notch is two extra
 *       fills and it is the single biggest reason this stops looking like a stack of boxes.</li>
 *   <li><b>Grit</b> - {@link #scanlines} across the body, {@link #wear} along the edges. This is the
 *       whole difference between "dark UI" and "hardware that has been outdoors". It is also where
 *       a survival theme normally goes wrong, so both are kept to single-digit alpha: you should
 *       read the texture without ever being able to point at a speck.</li>
 *   <li><b>Structure</b> - the rim, the corner {@link #brackets}, the {@link #rivets}. Heavy, hard,
 *       and always fully opaque, because this is the layer that says "military" and a soft edge
 *       says the opposite.</li>
 *   <li><b>Information</b> - text and bars, drawn last, at full contrast, never textured. The grit
 *       lives strictly underneath the words. That rule is what keeps the screen legible.</li>
 * </ol>
 *
 * <h2>Everything is deterministic</h2>
 * <p>The weathering is hashed from the pixel position and a caller-supplied seed, never from a
 * random. A panel therefore wears the same way on every frame and on every machine - a nick that
 * flickers frame to frame reads instantly as a rendering bug rather than as damage.</p>
 */
public final class GuiDraw {

    private GuiDraw() {
    }

    public static final int HEADER_TEXT_DY = 3;

    /** How much each corner is cut. Two pixels reads as a chamfer; three reads as a mistake. */
    private static final int CHAMFER = 2;

    /* ================================================================== texture */

    /**
     * A stable 32-bit hash of a point. The weathering's entire source of variety.
     *
     * <p>Position-hashed rather than sequential, so a panel's wear does not shift when the panel
     * moves or when something above it in the draw order changes size.</p>
     */
    private static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    /**
     * Horizontal scanlines across a surface.
     *
     * <p>One dark line every three pixels. This is the cheapest thing on the screen that does the
     * most work: it costs one fill per three rows, and it is what stops a large panel reading as a
     * flat coloured rectangle without putting a single mark near the text.</p>
     */
    public static void scanlines(GuiGraphics g, int x, int y, int w, int h) {
        scanlines(g, x, y, w, h, Theme.SCANLINE);
    }

    public static void scanlines(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        for (int row = 1; row < h; row += 3) {
            g.fill(x, y + row, x + w, y + row + 1, color);
        }
    }

    /**
     * Chipped paint along the top and bottom edges of a surface.
     *
     * <p>Edges only, never the field. Speckle across the middle of a panel is noise the eye has to
     * filter out on the way to the text; damage concentrated where a real plate would take knocks
     * reads as wear and leaves the reading surface alone.</p>
     *
     * @param seed distinguishes one panel's damage from another's at the same coordinates
     */
    public static void wear(GuiGraphics g, int x, int y, int w, int h, int seed) {
        if (w < 12 || h < 6) {
            return;
        }
        for (int i = 4; i < w - 4; i += 7) {
            int top = hash(x + i, y, seed);
            if ((top & 7) < 3) {
                int len = 1 + ((top >> 4) & 1);
                g.fill(x + i, y + 1, x + i + len, y + 2, Theme.GRIT_DARK);
            }
            int bottom = hash(x + i, y + h, seed);
            if ((bottom & 7) < 3) {
                int len = 1 + ((bottom >> 4) & 1);
                g.fill(x + i, y + h - 2, x + i + len, y + h - 1, Theme.GRIT_DARK);
            }
            if ((top & 31) < 3) {
                g.fill(x + i, y + 2, x + i + 1, y + 3, Theme.GRIT_LIGHT);
            }
        }
    }

    /**
     * Sparse speckle over a small area - header bands and badges only.
     *
     * <p>Sampled on a 3px lattice rather than per pixel. Per-pixel noise over a full panel is tens
     * of thousands of fills a frame and looks like television static; a sparse lattice over a 13px
     * band is about forty and looks like a stencil that has been rained on.</p>
     */
    public static void grain(GuiGraphics g, int x, int y, int w, int h, int seed) {
        for (int gy = 0; gy < h; gy += 3) {
            for (int gx = (gy / 3 & 1); gx < w; gx += 3) {
                int n = hash(x + gx, y + gy, seed);
                if ((n & 15) == 0) {
                    g.fill(x + gx, y + gy, x + gx + 1, y + gy + 1, Theme.GRIT_DARK);
                }
            }
        }
    }

    /**
     * Diagonal warning tape.
     *
     * <p>Drawn a row at a time with the pattern shifted one pixel per row, which is a 45 degree
     * slope built entirely out of axis-aligned fills - the only kind {@code fill} can draw. Runs
     * are merged, so a 200x6 strip costs about fifty fills rather than twelve hundred.</p>
     */
    public static void hazard(GuiGraphics g, int x, int y, int w, int h, int lit, int dark) {
        int period = 8;
        for (int row = 0; row < h; row++) {
            int i = 0;
            while (i < w) {
                int band = Math.floorMod(i + row, period);
                boolean on = band < period / 2;
                int run = (on ? period / 2 : period) - band;
                int end = Math.min(w, i + run);
                g.fill(x + i, y + row, x + end, y + row + 1, on ? lit : dark);
                i = end;
            }
        }
    }

    /* ================================================================== structure */

    /** Notched filled rectangle with a vertical gradient. The base of every surface. */
    public static void panelBody(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        g.fillGradient(x + CHAMFER, y, x + w - CHAMFER, y + h, top, bottom);
        g.fillGradient(x, y + CHAMFER, x + CHAMFER, y + h - CHAMFER, top, bottom);
        g.fillGradient(x + w - CHAMFER, y + CHAMFER, x + w, y + h - CHAMFER, top, bottom);
    }

    /** Notched 1px outline matching {@link #panelBody}. */
    public static void panelOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + CHAMFER, y, x + w - CHAMFER, y + 1, color);
        g.fill(x + CHAMFER, y + h - 1, x + w - CHAMFER, y + h, color);
        g.fill(x, y + CHAMFER, x + 1, y + h - CHAMFER, color);
        g.fill(x + w - 1, y + CHAMFER, x + w, y + h - CHAMFER, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, color);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
    }

    /** Plain square 1px outline, for small cells where a notch would just be noise. */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /**
     * Corner brackets - all four, L-shaped, and heavy.
     *
     * <p>This replaces the two strips of "tape" the old theme used. Tape on opposite corners is a
     * label holding something down; four hard L-brackets are a case that has been <i>built</i> to
     * survive, which is the difference the rebrand is after. They are the loudest structural mark
     * on any surface, so the length is what carries emphasis - a panel gets long ones, a small card
     * gets short ones, and nothing else on screen is allowed to compete.</p>
     */
    public static void brackets(GuiGraphics g, int x, int y, int w, int h, int color, int len) {
        brackets(g, x, y, w, h, color, len, 1);
    }

    public static void brackets(GuiGraphics g, int x, int y, int w, int h,
                                int color, int len, int weight) {
        int t = Math.max(1, weight);
        int l = Math.max(2, Math.min(len, Math.min(w, h) / 2 - CHAMFER));

        // top left
        g.fill(x + CHAMFER, y, x + CHAMFER + l, y + t, color);
        g.fill(x, y + CHAMFER, x + t, y + CHAMFER + l, color);
        // top right
        g.fill(x + w - CHAMFER - l, y, x + w - CHAMFER, y + t, color);
        g.fill(x + w - t, y + CHAMFER, x + w, y + CHAMFER + l, color);
        // bottom left
        g.fill(x + CHAMFER, y + h - t, x + CHAMFER + l, y + h, color);
        g.fill(x, y + h - CHAMFER - l, x + t, y + h - CHAMFER, color);
        // bottom right
        g.fill(x + w - CHAMFER - l, y + h - t, x + w - CHAMFER, y + h, color);
        g.fill(x + w - t, y + h - CHAMFER - l, x + w, y + h - CHAMFER, color);
    }

    /**
     * Four bolts, inset from the corners.
     *
     * <p>Two pixels of body and one lit pixel on the top left of each - the smallest mark that
     * still reads as a raised head rather than as a stray dot.</p>
     */
    public static void rivets(GuiGraphics g, int x, int y, int w, int h, int inset) {
        rivet(g, x + inset, y + inset);
        rivet(g, x + w - inset - 2, y + inset);
        rivet(g, x + inset, y + h - inset - 2);
        rivet(g, x + w - inset - 2, y + h - inset - 2);
    }

    public static void rivet(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 2, y + 2, 0x99000000);
        g.fill(x, y, x + 1, y + 1, Theme.at(Theme.BONE, 0x4C));
    }

    /**
     * The lit top rim and shadowed underside of a raised plate.
     *
     * <p>One pixel each. Together they are the whole illusion that a card is sitting on top of the
     * panel rather than being painted into it, and they cost two fills.</p>
     */
    public static void emboss(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + CHAMFER, y + 1, x + w - CHAMFER, y + 2, Theme.EDGE);
        g.fill(x + CHAMFER, y + h - 2, x + w - CHAMFER, y + h - 1, Theme.SHADE);
    }

    /* ================================================================== panels */

    /**
     * The base surface: plate, grit, rim. Everything else in this file is built on it.
     *
     * @param seed the weathering seed - pass the panel's own x/y so each one wears differently
     */
    public static void plate(GuiGraphics g, int x, int y, int w, int h, int seed) {
        panelBody(g, x, y, w, h, Theme.panelTop(), Theme.panelBottom());
        scanlines(g, x + 1, y + 1, w - 2, h - 2);
        wear(g, x, y, w, h, seed);
        emboss(g, x, y, w, h);
        panelOutline(g, x, y, w, h, Theme.BORDER);
    }

    /** A <i>raised</i> plate - cards and tiles that sit on top of a panel. */
    public static void raised(GuiGraphics g, int x, int y, int w, int h, int seed) {
        panelBody(g, x, y, w, h, Theme.plateTop(), Theme.plateBottom());
        scanlines(g, x + 1, y + 1, w - 2, h - 2);
        wear(g, x, y, w, h, seed);
        emboss(g, x, y, w, h);
        panelOutline(g, x, y, w, h, Theme.BORDER_HI);
    }

    /** Bare panel with no header band. */
    public static void plainPanel(GuiGraphics g, int x, int y, int w, int h) {
        plate(g, x, y, w, h, x * 31 + y);
        brackets(g, x, y, w, h, Theme.accent(120), 10, 2);
        rivets(g, x, y, w, h, 4);
    }

    /** A flat panel: no notch, no brackets, one hairline. For dense strips and readouts. */
    public static void flatPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, Theme.plateTop(), Theme.plateBottom());
        scanlines(g, x + 1, y + 1, w - 2, h - 2);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, Theme.EDGE);
        outline(g, x, y, w, h, Theme.BORDER);
    }

    /**
     * Titled panel. The title is right aligned and the marker tab sits on the right edge, because
     * the mod's own strings are Arabic.
     */
    public static void panel(GuiGraphics g, Font font, int x, int y, int w, int h, String title) {
        panel(g, font, x, y, w, h, title, 1.0F);
    }

    /**
     * Titled panel that assembles itself rather than simply being there.
     *
     * <p>{@code reveal} runs 0 to 1 and drives only <b>decoration</b>: the brackets grow, the accent
     * rule wipes out from the left, and the title slides the last few pixels into place. The plate,
     * its rim and the header band are drawn in full from the first frame.</p>
     *
     * <p>That split is deliberate rather than shy. Slot positions are baked into the menu on both
     * sides and can never move, so a panel that slid or faded as a whole would separate visibly from
     * the cells sitting on top of it. Animating the structure alone keeps the two locked together
     * while still giving the screen a sense of coming online.</p>
     */
    public static void panel(GuiGraphics g, Font font, int x, int y, int w, int h,
                             String title, float reveal) {
        float r = Mth.clamp(reveal, 0.0F, 1.0F);
        int header = MlumLayout.HEADER_H;
        int seed = x * 31 + y;

        panelBody(g, x, y, w, h, Theme.panelTop(), Theme.panelBottom());
        scanlines(g, x + 1, y + header, w - 2, h - header - 1);

        /* ---- header band: its own plate, grained, with a hard rule under it ---- */
        g.fillGradient(x + CHAMFER, y + 1, x + w - CHAMFER, y + header,
                Theme.headerTop(), Theme.headerBottom());
        grain(g, x + CHAMFER, y + 1, w - CHAMFER * 2, header - 1, seed);
        g.fill(x + CHAMFER, y + 1, x + w - CHAMFER, y + 2, Theme.EDGE);

        wear(g, x, y, w, h, seed);

        int uy = y + header;
        g.fill(x + CHAMFER, uy, x + w - CHAMFER, uy + 1, Theme.RULE);
        // the accent rule wipes in from the left, and is dashed - a printed mark, not a glow
        int ruleLeft = x + CHAMFER + 6;
        int ruleFull = w - CHAMFER * 2 - 12;
        if (ruleFull > 0) {
            dashedRule(g, ruleLeft, uy + 2, Math.round(ruleFull * r), Theme.accent(110));
        }

        panelOutline(g, x, y, w, h, Theme.BORDER);
        brackets(g, x, y, w, h, Anim.fade(Theme.accent(170), r), Math.max(3, Math.round(12 * r)), 2);
        rivets(g, x, y, w, h, 4);

        if (title != null && !title.isEmpty()) {
            String shown = trim(font, title, w - 26);
            int slide = Math.round((1.0F - r) * 6.0F);
            int tx = x + w - 10 - font.width(shown) + slide;
            g.drawString(font, shown, tx, y + HEADER_TEXT_DY, Anim.fade(Theme.accent(), r), false);

            // the marker tab: a solid block on the right edge, notched at the bottom
            int tabX = x + w - 6;
            g.fill(tabX, y + 2, tabX + 3, y + header - 2, Anim.fade(Theme.accent(), r));
            g.fill(tabX, y + header - 3, tabX + 2, y + header - 2, Anim.fade(Theme.accentDeep(), r));
        }
    }

    /** A rule printed as dashes. Reads as stencilling; a solid line reads as a UI divider. */
    public static void dashedRule(GuiGraphics g, int x, int y, int w, int color) {
        for (int i = 0; i < w; i += 4) {
            g.fill(x + i, y, x + Math.min(w, i + 3), y + 1, color);
        }
    }

    /** Secondary text in a panel header. Left aligned, opposite the right aligned title. */
    public static void headerNote(GuiGraphics g, Font font, int x, int y, String note, int color) {
        if (note != null && !note.isEmpty()) {
            g.drawString(font, note, x + 8, y + HEADER_TEXT_DY, color, false);
        }
    }

    /* ================================================================== cells */

    /**
     * A storage socket. Recessed, notched at the corners, with a lit lip along the bottom.
     *
     * <p>{@code size} is 18 for the scanner grid and 28 for inventory and chest cells.</p>
     */
    public static void cell(GuiGraphics g, int x, int y, int size) {
        g.fillGradient(x + 1, y + 1, x + size - 1, y + size - 1, Theme.SLOT_TOP, Theme.SLOT_BOT);
        // the corner cut, which is what makes a grid of these read as a rack rather than a table
        g.fill(x + 1, y + 1, x + 3, y + 2, Theme.VOID);
        g.fill(x + size - 3, y + size - 2, x + size - 1, y + size - 1, Theme.VOID);
        outline(g, x, y, size, size, Theme.SLOT_BORDER);
        g.fill(x + 1, y + size - 2, x + size - 1, y + size - 1, Theme.SLOT_BEVEL);
    }

    public static void cell(GuiGraphics g, int x, int y) {
        cell(g, x, y, MlumLayout.SLOT);
    }

    /**
     * A slot rectangle in the flat style: recess, rim, and whatever the state adds.
     *
     * <p>The item inside is the only thing anyone is looking at, so the decoration here is
     * deliberately thinner than anywhere else on the screen. State is carried by the <b>rim and the
     * spine</b> rather than by a fill, which keeps the item's own colours readable.</p>
     *
     * @param hover 0..1 eased pointer weight
     * @param live  true for the thing the player is actually holding
     */
    public static void flatCell(GuiGraphics g, int x, int y, int w, int h, float hover, boolean live) {
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, Theme.SLOT_TOP, Theme.SLOT_BOT);

        if (live) {
            // a wash plus a spine down the leading edge - "this one is in your hands"
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, Theme.accent(28));
            g.fill(x, y, x + 2, y + h, Theme.accent());
            g.fill(x + 2, y, x + 3, y + h, Theme.accentDeep());
        }
        if (hover > 0.01F) {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, Anim.fade(0x26E6CFA1, hover));
        }
        int border = live
                ? Theme.accent()
                : Anim.mix(Theme.SLOT_BORDER, Theme.accent(200), hover);
        outline(g, x, y, w, h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, Theme.at(Theme.EDGE, live ? 0x40 : 0x18));
    }

    /**
     * A gear mounting point - the frame the firearm and equipment cells use.
     *
     * <p><b>Rail teeth are the whole idea.</b> Three short ticks along the top and bottom edges,
     * like the slots of a mounting rail. They cost six fills and they are what make a weapon cell
     * read as a place hardware is <i>clamped into</i> rather than as a slightly larger inventory
     * square - which is the one thing the old flat cell could never say.</p>
     *
     * @param armed true once the mount holds something, which is when it earns its accent rim
     * @param alarm true for a state that needs the rust channel instead - a dry magazine
     */
    public static void mount(GuiGraphics g, int x, int y, int w, int h,
                             float hover, boolean live, boolean armed, boolean alarm) {
        float hv = Mth.clamp(hover, 0.0F, 1.0F);

        panelBody(g, x, y, w, h, Theme.SLOT_TOP, Theme.SLOT_BOT);
        scanlines(g, x + 2, y + 2, w - 4, h - 4, 0x0E000000);

        int rim = alarm
                ? Anim.mix(Theme.at(Theme.RUST, 0xCC), Theme.EMBER, hv)
                : armed || live
                        ? Anim.mix(Theme.accent(armed ? 180 : 120), Theme.accent(), hv)
                        : Anim.mix(Theme.SLOT_BORDER, Theme.accent(190), hv);

        if (live) {
            g.fill(x + 2, y + 1, x + w - 2, y + h - 1, Theme.accent(26));
        }
        if (hv > 0.01F) {
            g.fill(x + 2, y + 1, x + w - 2, y + h - 1, Anim.fade(0x1EE6CFA1, hv));
        }

        panelOutline(g, x, y, w, h, rim);
        rails(g, x, y, w, h, rim);

        if (live) {
            // the spine: this is the weapon the key would draw
            g.fill(x, y + CHAMFER, x + 2, y + h - CHAMFER, Theme.accent());
        }
        if (armed || hv > 0.4F) {
            brackets(g, x, y, w, h, Theme.at(rim, hv > 0.4F ? 0xFF : 0xB4), 8, 2);
        }
    }

    /** The mounting rail's teeth: three ticks along the top edge and three along the bottom. */
    private static void rails(GuiGraphics g, int x, int y, int w, int h, int color) {
        int inner = w - 16;
        if (inner < 12) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            int tx = x + 8 + inner * i / 3 + inner / 8;
            g.fill(tx, y, tx + 3, y + 2, color);
            g.fill(tx, y + h - 2, tx + 3, y + h, color);
        }
    }

    /** Tiny caption. The only label style the layout uses outside of panel headers. */
    public static void caption(GuiGraphics g, Font font, String text, int x, int y, int color) {
        if (text != null && !text.isEmpty()) {
            g.drawString(font, text, x, y, color, false);
        }
    }

    /**
     * Letter-spaced text, for anything that should read as stencilled onto the hardware.
     *
     * <p>Minecraft's font has no tracking, so each glyph is drawn on its own with an extra pixel of
     * advance. Reserved for short labels - it is a per-character draw call, and at paragraph length
     * it would be both slow and unreadable.</p>
     */
    public static void stencil(GuiGraphics g, Font font, String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            g.drawString(font, ch, cursor, y, color, false);
            cursor += font.width(ch) + 1;
        }
    }

    /** The width {@link #stencil} will actually occupy. */
    public static int stencilWidth(Font font, String text) {
        return text == null || text.isEmpty() ? 0 : font.width(text) + text.length();
    }

    /**
     * A stamped key cap - the index badge beside a weapon or quick-access cell.
     *
     * <p>Filled solid with the accent when live, because "press this number" is an instruction and
     * an instruction should be the most legible thing in its corner.</p>
     */
    public static void keyCap(GuiGraphics g, Font font, int x, int y, int w, int h,
                              String label, boolean live) {
        if (live) {
            g.fill(x, y, x + w, y + h, Theme.accent());
            g.fill(x, y + h - 2, x + w, y + h, Theme.accentDeep());
        } else {
            g.fillGradient(x, y, x + w, y + h, Theme.plateTop(), Theme.plateBottom());
        }
        outline(g, x, y, w, h, live ? Theme.accentBright() : Theme.BORDER_HI);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2,
                live ? Theme.INK : Theme.TEXT_DIM, false);
    }

    /* ================================================================== badges */

    /**
     * A stack of rank chevrons, pointing up, widest at the bottom.
     *
     * <p>Drawn as stepped runs rather than as a real diagonal - {@code fill} has no other option -
     * which at this size is what a pixel chevron looks like anyway.</p>
     */
    public static void chevrons(GuiGraphics g, int cx, int y, int count, int color) {
        for (int c = 0; c < count; c++) {
            int top = y + c * 5;
            for (int i = 0; i < 5; i++) {
                g.fill(cx - 5 + i, top + 4 - i, cx - 3 + i, top + 5 - i, color);
                g.fill(cx + 3 - i, top + 4 - i, cx + 5 - i, top + 5 - i, color);
            }
        }
    }

    /**
     * A clearance plate - the header block on a progression or rank card.
     *
     * <p>Solid when earned so the number is punched out of the accent, hollow and dashed when it is
     * not. The two states have to be distinguishable at a glance across a whole scrolling track,
     * which a difference in text colour alone cannot do.</p>
     */
    public static void clearance(GuiGraphics g, Font font, int x, int y, int w, int h,
                                 String label, boolean earned, boolean live) {
        if (earned) {
            g.fillGradient(x, y, x + w, y + h, Theme.accent(), Theme.accentDeep());
            g.fill(x, y, x + w, y + 1, Theme.accentBright());
            grain(g, x + 1, y + 1, w - 2, h - 2, x + y);
            outline(g, x, y, w, h, Theme.accentBright());
        } else {
            g.fill(x, y, x + w, y + h, 0x50000000);
            outline(g, x, y, w, h, Theme.BORDER);
            dashedRule(g, x + 2, y + h - 2, w - 4, Theme.at(Theme.TEXT_MUTED, 0x80));
        }
        if (live) {
            outline(g, x - 1, y - 1, w + 2, h + 2, Theme.accentBright());
        }
        String shown = trim(font, label, w - 6);
        g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - 8) / 2,
                earned ? Theme.INK : Theme.TEXT_DIM, false);
    }

    /* ================================================================== bars */

    /**
     * Segmented vitals bar - discrete cells, like hearts, rather than one sliding fill.
     *
     * <p>Each lit cell carries a bright cap on its top row. That is what keeps twenty small blocks
     * reading as an instrument at a glance instead of as a striped rectangle.</p>
     */
    public static void segmentBar(GuiGraphics g, int x, int y, int w, int h,
                                  int segments, float fraction, int color, int highlight) {
        float clamped = Mth.clamp(fraction, 0.0F, 1.0F);
        float exact = clamped * segments;
        int full = (int) exact;
        float partial = exact - full;

        for (int i = 0; i < segments; i++) {
            int sx = x + Math.round((float) w * i / segments);
            int ex = x + Math.round((float) w * (i + 1) / segments) - 1;
            if (ex <= sx) {
                ex = sx + 1;
            }
            g.fill(sx, y, ex, y + h, Theme.TRACK);
            if (i < full) {
                g.fill(sx, y, ex, y + h, color);
                g.fill(sx, y, ex, y + 1, highlight);
            } else if (i == full && partial > 0.15F) {
                int px = sx + Math.max(1, Math.round((ex - sx) * partial));
                g.fill(sx, y, px, y + h, color);
                g.fill(sx, y, px, y + 1, highlight);
            }
        }
        outline(g, x - 1, y - 1, w + 2, h + 2, Theme.BORDER);
    }

    /**
     * The segments between {@code from} and {@code to}, with no track and no rim.
     *
     * <p>Drawn <i>after</i> {@link #segmentBar} to leave a paler trail where a value used to be. It
     * has to be a separate pass because {@code segmentBar} repaints the track under every segment,
     * so a second full bar behind it would simply be erased.</p>
     */
    public static void segmentTrail(GuiGraphics g, int x, int y, int w, int h,
                                    int segments, float from, float to, int color) {
        int first = Mth.floor(Mth.clamp(from, 0.0F, 1.0F) * segments);
        int last = Mth.ceil(Mth.clamp(to, 0.0F, 1.0F) * segments);

        for (int i = Math.max(0, first); i < Math.min(segments, last); i++) {
            int sx = x + Math.round((float) w * i / segments);
            int ex = x + Math.round((float) w * (i + 1) / segments) - 1;
            if (ex <= sx) {
                ex = sx + 1;
            }
            g.fill(sx, y, ex, y + h, color);
        }
    }

    /** Thin continuous bar, used for objective progress inside a list row. */
    public static void thinBar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x, y, x + w, y + h, Theme.TRACK);
        int filled = Math.round(w * Mth.clamp(fraction, 0.0F, 1.0F));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, color);
        }
    }

    /**
     * The heavy readout bar - a framed channel with a notched fill and a bright leading edge.
     *
     * <p>Used wherever a number matters enough to deserve real estate: experience, faction
     * progression, chest capacity. {@code rtl} grows it from the right, which is the direction the
     * Arabic screens read in.</p>
     */
    public static void meter(GuiGraphics g, int x, int y, int w, int h,
                             float fraction, int color, boolean rtl) {
        float f = Mth.clamp(fraction, 0.0F, 1.0F);

        g.fill(x, y, x + w, y + h, Theme.TRACK);
        scanlines(g, x, y, w, h, 0x24000000);

        int filled = Math.round(w * f);
        if (filled > 0) {
            int fx = rtl ? x + w - filled : x;
            g.fillGradient(fx, y, fx + filled, y + h, color, Anim.fade(color, 0.55F));
            g.fill(fx, y, fx + filled, y + 1, Theme.at(Theme.EDGE, 0x60));
            // the leading edge, bright, so the eye finds the current value instantly
            int lead = rtl ? fx : fx + filled - 2;
            g.fill(lead, y, lead + 2, y + h, Theme.accentBright());
        }

        // tick marks at the quarters - a bar without a scale is a shape, not a reading
        for (int i = 1; i < 4; i++) {
            int tx = x + w * i / 4;
            g.fill(tx, y, tx + 1, y + 2, Theme.at(Theme.BONE, 0x50));
            g.fill(tx, y + h - 2, tx + 1, y + h, Theme.at(Theme.BONE, 0x50));
        }
        outline(g, x - 1, y - 1, w + 2, h + 2, Theme.BORDER_HI);
    }

    public static void scrollbar(GuiGraphics g, int x, int y, int h, int visible, int total, int scroll) {
        if (total <= visible) {
            return;
        }
        g.fill(x, y, x + 3, y + h, Theme.TRACK);
        int thumb = Math.max(12, h * visible / total);
        int range = h - thumb;
        int maxScroll = Math.max(1, total - visible);
        int offset = Math.round((float) range * scroll / maxScroll);
        g.fillGradient(x, y + offset, x + 3, y + offset + thumb, Theme.accentBright(), Theme.accent());
        g.fill(x, y + offset, x + 3, y + offset + 1, Theme.accentBright());
    }

    /**
     * A divider with a marked centre.
     *
     * <p>Dashed rather than solid, with a short solid accent run in the middle. A plain 1px line
     * across a panel is the single most "generic dashboard" mark there is.</p>
     */
    public static void divider(GuiGraphics g, int x1, int y, int x2) {
        dashedRule(g, x1, y, x2 - x1, Theme.BORDER);
        int mid = (x1 + x2) / 2;
        g.fill(mid - 10, y, mid + 10, y + 1, Theme.accent(150));
        g.fill(mid - 1, y - 1, mid + 1, y + 2, Theme.accent(190));
    }

    /* ================================================================== character */

    /** Soft pool of light behind the model, brightest at the feet. */
    public static void spotlight(GuiGraphics g, int cx, int top, int bottom, int halfWidth) {
        for (int i = 0; i < 5; i++) {
            int inset = halfWidth * i / 6;
            int a = 5 + i * 4;
            g.fillGradient(cx - halfWidth + inset, top + i * 6, cx + halfWidth - inset, bottom,
                    Theme.accent(0), Theme.accent(a));
        }
    }

    /** Elliptical contact shadow so the model does not float. */
    public static void groundShadow(GuiGraphics g, int cx, int y, int halfWidth) {
        for (int i = 0; i < 4; i++) {
            int hw = halfWidth - i * (halfWidth / 6);
            g.fill(cx - hw, y + i, cx + hw, y + i + 1, (0x58 - i * 0x14) << 24);
        }
    }

    /**
     * Darkening around the four edges of the canvas.
     *
     * <p>Pushes the corners back so the eye lands in the middle of the screen, and it is the one
     * effect that makes a full-bleed 640x360 layout feel like something you are looking <i>into</i>
     * rather than a page.</p>
     */
    public static void vignette(GuiGraphics g, int w, int h, float strength) {
        // Disabled by design: nothing may darken the live world behind a menu.
        if (w > 0) {
            return;
        }
        int a = Math.round(Mth.clamp(strength, 0.0F, 1.0F) * 0x5A);
        if (a <= 0) {
            return;
        }
        int edge = a << 24;
        int clear = 0x00000000;
        g.fillGradient(0, 0, w, 26, edge, clear);
        g.fillGradient(0, h - 26, w, h, clear, edge);
        for (int i = 0; i < 26; i++) {
            int band = Math.round(a * (1.0F - i / 26.0F)) << 24;
            g.fill(i, 0, i + 1, h, band);
            g.fill(w - i - 1, 0, w - i, h, band);
        }
    }

    /* ================================================================== misc */

    /** Draws an item centred on a point at an arbitrary scale. */
    public static void bigItem(GuiGraphics g, ItemStack stack, int centerX, int centerY, float scale) {
        if (stack.isEmpty()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(centerX, centerY, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
    }

    /** Durability readout for a big equipment cell, since the 2x overlay hides vanilla's. */
    public static void durability(GuiGraphics g, ItemStack stack, int x, int y, int w) {
        if (stack.isEmpty() || !stack.isDamaged()) {
            return;
        }
        float left = 1.0F - (float) stack.getDamageValue() / stack.getMaxDamage();
        int color = 0xFF000000 | Mth.hsvToRgb(left / 3.0F, 1.0F, 1.0F);
        g.fill(x, y, x + w, y + 2, 0xC0000000);
        g.fill(x, y, x + Math.round(w * left), y + 2, color);
    }

    public static String upper(String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }

    /** Truncates with an ellipsis so long names never bleed out of their panel. */
    public static String trim(Font font, String text, int maxWidth) {
        if (text == null || maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int room = Math.max(0, maxWidth - font.width("..."));
        // Shaped Arabic is stored in visual order, so its first word is at the RIGHT end - cut the
        // left end instead, or a long name loses its beginning rather than its ending.
        if (com.barbwra.mlum.util.ArabicText.containsArabic(text)) {
            return "..." + font.plainSubstrByWidth(text, room, true);
        }
        return font.plainSubstrByWidth(text, room) + "...";
    }
}

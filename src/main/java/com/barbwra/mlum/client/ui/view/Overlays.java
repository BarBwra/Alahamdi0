package com.barbwra.mlum.client.ui.view;

import com.barbwra.mlum.client.ui.Art;
import com.barbwra.mlum.client.ui.Canvas;
import com.barbwra.mlum.client.ui.Draw;
import com.barbwra.mlum.client.ui.Ease;
import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.Tok;
import com.barbwra.mlum.client.ui.layout.Layout;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.client.ui.layout.Span;
import com.barbwra.mlum.client.ui.text.Shaped;
import com.barbwra.mlum.client.ui.text.TextEngine;

import java.util.ArrayList;
import java.util.List;

import static com.barbwra.mlum.client.ui.layout.Node.AUTO;
import static com.barbwra.mlum.client.ui.layout.Node.CENTER;
import static com.barbwra.mlum.client.ui.view.Css.K;
import static com.barbwra.mlum.client.ui.view.Css.LH;
import static com.barbwra.mlum.client.ui.view.Css.block;
import static com.barbwra.mlum.client.ui.view.Css.col;
import static com.barbwra.mlum.client.ui.view.Css.grid;
import static com.barbwra.mlum.client.ui.view.Css.inline;
import static com.barbwra.mlum.client.ui.view.Css.nsp;
import static com.barbwra.mlum.client.ui.view.Css.num;
import static com.barbwra.mlum.client.ui.view.Css.row;
import static com.barbwra.mlum.client.ui.view.Css.sp;
import static com.barbwra.mlum.client.ui.view.Css.txt;

/** Everything that floats over a tab: dialogs, the zombie alert, toasts, the static, the carried item. */
public final class Overlays {

    private Overlays() {
    }

    /* ================================================================== text input */

    /**
     * {@code .input}: a 40px dark field with the value (or a faint placeholder), the caret at its
     * end while focused, and the amber border that says so.
     */
    public static Node input(String id, String value, String placeholder, boolean focused, boolean caretOn) {
        return input(id, value, placeholder, focused, caretOn, 40);
    }

    /**
     * @param height 40 in a {@code .field} row; 29 inside a dialog, where the design's
     *               {@code flex:1} on {@code .input} collapses it to one line of text
     */
    public static Node input(String id, String value, String placeholder, boolean focused, boolean caretOn,
                             float height) {
        Node in = block().h(height).pad(1, 12, 1, 12).bg(Tok.rgb(0x0d100b)).border(1.0F, focused ? Tok.AMBER : Tok.LINE)
                .clip().hit("input:" + id);
        boolean empty = value == null || value.isEmpty();
        String shown = empty ? (placeholder == null ? "" : placeholder) : value;
        // an input's text is one line of normal line-height, centred in the content box
        float normal = K(400).ascent(13) + K(400).descent(13);
        Node t = txt(shown, K(400), 13, normal / 13.0F, empty ? Tok.FAINT : Tok.BONE);
        t.mt = (height - 4 - normal) / 2.0F;
        final boolean caret = focused && caretOn;
        final boolean hasValue = !empty;
        final String val = value == null ? "" : value;
        t.over((c, n) -> {
            if (!caret) {
                return;
            }
            // caret after the logical end of the text: the left edge of the run in RTL
            float x;
            if (!hasValue) {
                x = n.x + n.w - 1;
            } else {
                Shaped s = TextEngine.shape(val, K(400), 13, 0, true);
                boolean rtl = startsRtl(val);
                x = rtl ? n.x + n.w - s.width - 1 : n.x + n.w;
            }
            Draw.rect(c, x, n.y + 4, 1, n.h - 8, Tok.BONE);
        });
        in.add(t);
        return in;
    }

    static boolean startsRtl(String s) {
        for (int i = 0; i < s.length(); i++) {
            byte d = Character.getDirectionality(s.charAt(i));
            if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                return true;
            }
            if (d == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                return false;
            }
        }
        return true;
    }

    /* ================================================================== dialogs */

    /** {@code .modal}: the dimmed band between the bars, and a 440px dialog centred in it. */
    public static Node modal(String title, Node body, String hover) {
        Node m = row().align(CENTER).justify(CENTER).abs(0, 64, 0, 36).bg(Tok.rgba(0x050705, 0.72)).z(6)
                .hit("modal-bg").tag("modal");
        Node d = col().w(440).bg(Tok.PANEL_SOLID).border(1.0F, Tok.LINE).pad(14, 16, 16, 16).hit("dialog").tag("dialog");
        Node esc = Css.keycap("ESC", false, "close".equals(hover) ? Tok.BONE : Tok.MUTED, Tok.LINE);
        esc.height = 22;
        esc.minWidth = 34;
        esc.bb = 2;
        esc.kids.clear();
        esc.add(Css.px("ESC", 600, 12, 1.0F, 0, "close".equals(hover) ? Tok.BONE : Tok.MUTED));
        esc.hit("close");
        d.add(Css.phead(title, Css.aside().add(esc)));
        d.add(body);
        m.add(d);
        return m;
    }

    /** {@code .dlg-note}: a muted line, optionally with money. */
    public static Node note(String text) {
        Node n = row().align(CENTER).gap(6);
        n.add(txt(text, K(400), 12, LH, Tok.MUTED));
        return n;
    }

    public static Node moneyNote(String text, long money) {
        Node n = note(text);
        n.add(InvView.cash());
        n.add(num(Chrome.fmt(money), 16, LH, Tok.BONE));
        return n;
    }

    public static Node donateBody(String amount, boolean focused, boolean caret, long money, String error, String hover) {
        Node b = col().gap(12);
        b.add(moneyNote("فلوسك الحين", money));
        b.add(input("donate", amount, "", focused, caret, 29));
        Node quick = row().gap(6).wrap();
        String[][] q = {{"100", "100"}, {"1000", "1,000"}, {"5000", "5,000"}, {"all", null}};
        for (String[] e : q) {
            Node t = Css.tool(null, null, 28, 10).hit("amt:" + e[0]);
            if (e[1] != null) {
                t.add(num(e[1], 11.5F, 1.0F, ("amt:" + e[0]).equals(hover) ? Tok.BONE : Tok.MUTED));
            } else {
                t.add(txt("الكل", K(600), 11.5F, 1.0F, "amt:all".equals(hover) ? Tok.BONE : Tok.MUTED));
            }
            if (("amt:" + e[0]).equals(hover)) {
                t.borderColor = Tok.AMBER_DIM;
            }
            quick.add(t);
        }
        b.add(quick);
        if (error != null) {
            b.add(txt(error, K(400), 11.5F, LH, Tok.RUST));
        }
        Node foot = row().gap(8);
        foot.mt = 4;
        Node ok = Css.btn("تبرع", Css.BTN, false, false).hit("donate-ok");
        if ("donate-ok".equals(hover)) {
            QuestsView.brighten(ok);
        }
        foot.add(ok, Css.btn("إلغاء", Css.BTN_GHOST, false, false).hit("close"));
        b.add(foot);
        return b;
    }

    public static final class Player {
        public String id = "";
        public String name = "";
        public boolean sent;
        public String faction;
    }

    public static Node inviteBody(List<Player> players, boolean loading, String hover) {
        Node b = col().gap(12);
        b.add(note("اللاعبين المتصلين بدون منظمة"));
        Node list = col().gap(6).maxH(250).clip();
        if (loading) {
            list.add(txt("جاري التحميل…", K(400), 12, LH, Tok.MUTED));
        } else if (players.isEmpty()) {
            list.add(txt("ما فيه لاعبين متاحين الحين.", K(400), 12, LH, Tok.MUTED));
        }
        for (int i = 0; i < players.size() && i < 6; i++) {
            Player p = players.get(i);
            Node r = grid(-1, 0).gap(10).align(CENTER).pad(8, 10, 8, 10).bg(Tok.CARD).border(1.0F, Tok.LINE_SOFT);
            r.add(Css.px(p.name, 600, 18, 1.0F, 0.03F, p.faction != null ? Tok.MUTED : Tok.BONE));
            Node btn;
            if (p.faction != null) {
                btn = txt(p.faction, K(400), 11.5F, LH, Tok.FAINT);
            } else if (p.sent) {
                btn = Css.btn("أُرسلت", Css.BTN_GHOST, true, true);
            } else {
                btn = Css.btn("دعوة", Css.BTN, true, false).hit("invite-one", i);
                if ("invite-one".equals(hover)) {
                    QuestsView.brighten(btn);
                }
            }
            r.add(btn);
            list.add(r);
        }
        b.add(list);
        b.add(note("اللي تدعوه يشوف زر الانضمام في شاشته على طول."));
        return b;
    }

    /** A member's profile: stats, then promote / demote / kick when allowed. */
    public static Node profileBody(FactionView.Member mb, boolean canAct, boolean canPromote, boolean canDemote,
                                   String confirm, String hover) {
        Node b = col().gap(12);
        Node stats = grid(-1, -1).gap(8, 12);
        stats.add(stat("الدور", mb.role, false), stat("المستوى", mb.level > 0 ? String.valueOf(mb.level) : "-", true));
        stats.add(stat("النقاط", Chrome.fmt(mb.points), true), stat("التبرعات", Chrome.fmt(mb.donated), true));
        b.add(stats);
        if (mb.me) {
            b.add(note("هذا أنت."));
        } else if (!canAct) {
            b.add(note("ما عندك صلاحية على هذا العضو."));
        } else {
            Node acts = row().gap(8).wrap();
            acts.add(action("ترقية", "promote", canPromote, Css.BTN, confirm, hover));
            acts.add(action("تنزيل", "demote", canDemote, Css.BTN_GHOST, confirm, hover));
            acts.add(action("طرد", "kick", true, Css.BTN_DANGER, confirm, hover));
            b.add(acts);
        }
        return b;
    }

    static Node stat(String label, String value, boolean number) {
        Node s = row().align(Node.BASELINE).justify(Node.BETWEEN).pad(8, 10, 8, 10).bg(Tok.CARD).border(1.0F, Tok.LINE_SOFT);
        s.add(txt(label, K(400), 12, LH, Tok.MUTED));
        s.add(number ? num(value, 17, LH, Tok.BONE) : txt(value, K(600), 13, LH, Tok.BONE));
        return s;
    }

    /** A dialog button that asks once more before it acts. */
    static Node action(String label, String id, boolean enabled, int style, String confirm, String hover) {
        boolean confirming = id.equals(confirm);
        Node b = Css.btn(confirming ? "أكّد " + label : label, confirming ? Css.BTN_CONFIRM : style, true, !enabled);
        if (enabled) {
            b.hit(id);
            if (id.equals(hover)) {
                QuestsView.brighten(b);
            }
        }
        return b;
    }

    /** Pick the member who takes over. */
    public static Node transferBody(List<FactionView.Member> members, int scroll, String confirm, String hover) {
        Node b = col().gap(12);
        b.add(note("العضو اللي تختاره يصير القائد، وأنت تصير النائب."));
        Node list = col().gap(6);
        int shown = 0;
        for (int i = scroll; i < members.size() && shown < 6; i++) {
            FactionView.Member mb = members.get(i);
            if (mb.me) {
                continue;
            }
            shown++;
            Node r = grid(-1, 0).gap(10).align(CENTER).pad(8, 10, 8, 10).bg(Tok.CARD).border(1.0F, Tok.LINE_SOFT);
            r.add(Css.px(mb.name, 600, 18, 1.0F, 0.03F, Tok.BONE));
            String id = "transfer-to:" + i;
            boolean confirming = id.equals(confirm);
            Node btn = Css.btn(confirming ? "أكّد النقل" : "تسليم", confirming ? Css.BTN_CONFIRM : Css.BTN_GHOST, true, false)
                    .hit("transfer-to", i);
            r.add(btn);
            list.add(r);
        }
        if (shown == 0) {
            list.add(txt("ما فيه أعضاء غيرك.", K(400), 12, LH, Tok.MUTED));
        }
        b.add(list);
        return b;
    }

    /* ================================================================== zombie alert */

    public static final int DIR_BEHIND = 0;
    public static final int DIR_RIGHT = 1;
    public static final int DIR_LEFT = 2;

    /**
     * {@code .danger}: the edge the zombie is on turns red and pulses, and a chip says how far.
     */
    public static Node danger(int dir, int blocks, long nowMs, boolean lite) {
        Node d = block().abs(0, 64, 0, 36).z(3).tag("danger");
        /*
         * Toned down from the original. At 62% over a 110px band the red was reading as an error
         * state rather than as a hint, and it sat on top of the panels the player is trying to use.
         * A narrower, fainter, slower edge says the same thing without fighting the menu for
         * attention - the chip below is what actually carries the information.
         */
        float op = lite ? 0.35F : Ease.alternate(nowMs, 1500, 0.34F, 0.62F);
        int red = Tok.rgba(0xe0613f, 0.30);
        int clear = 0x00e0613f;
        Node edge;
        if (dir == DIR_RIGHT) {
            edge = Css.leaf(110, 0).abs(AUTO, 0, 0, 0).hgrad(new float[]{0, 1}, new int[]{clear, red});
        } else if (dir == DIR_LEFT) {
            edge = Css.leaf(110, 0).abs(0, 0, AUTO, 0).hgrad(new float[]{0, 1}, new int[]{red, clear});
        } else {
            edge = Css.leaf(0, 80).abs(0, AUTO, 0, 0).vgrad(new float[]{0, 1}, new int[]{clear, red});
        }
        edge.opacity(op);
        d.add(edge);
        Node chip = row().align(CENTER).gap(8).h(32).pad(0, 12, 0, 12).border(1.0F, Tok.RUST)
                .bg(Tok.rgba(0x220c08, 0.94)).abs(0, 10, AUTO, AUTO).pct(0.5F, 0, 0, 0).translate(-0.5F, 0);
        float dot = lite ? 0.85F : Ease.alternate(nowMs, 550, 0.3F, 0.85F);
        chip.add(Css.bar(8, 8, Tok.RUST).opacity(dot));
        chip.add(Css.icon("warn", 16, 16, Tok.RUST));
        chip.add(txt("زومبي قريب", K(700), 12.5F, LH, Tok.RUST));
        chip.add(num(String.valueOf(blocks), 17, LH, Tok.BONE));
        chip.add(txt("بلوك", K(400), 12.5F, LH, Tok.rgb(0xf4c3b4)));
        String[] words = {"وراك", "على يمينك", "على يسارك"};
        chip.add(txt("· " + words[Math.max(0, Math.min(2, dir))], K(400), 12.5F, LH, Tok.rgb(0xf4c3b4)));
        d.add(chip);
        return d;
    }

    /* ================================================================== toast */

    /** {@code .toast}: a message over the footer for 2.6s. Runs are text, bold text or numbers. */
    public static Node toast(List<Span> runs, boolean bad) {
        Node t = row().align(CENTER).gap(8).pad(9, 16, 9, 16).bg(Tok.PANEL_SOLID).border(1.0F, bad ? Tok.RUST : Tok.AMBER)
                .abs(0, AUTO, AUTO, 52).pct(0.5F, 0, 0, 0).translate(-0.5F, 0).z(9).tag("toast");
        Node line = inline(K(400), 13, LH, Tok.BONE);
        for (Span s : runs) {
            line.span(s);
        }
        t.add(line);
        return t;
    }

    /** Toast runs from a string where {@code {b}..{/b}} is bold and {@code {n}..{/n}} a number. */
    public static List<Span> runs(String text) {
        List<Span> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int b = text.indexOf("{b}", i);
            int n = text.indexOf("{n}", i);
            int next = b < 0 ? n : n < 0 ? b : Math.min(b, n);
            if (next < 0) {
                out.add(sp(text.substring(i), K(400), 13, LH, Tok.BONE));
                break;
            }
            if (next > i) {
                out.add(sp(text.substring(i, next), K(400), 13, LH, Tok.BONE));
            }
            boolean bold = next == b;
            int end = text.indexOf(bold ? "{/b}" : "{/n}", next);
            if (end < 0) {
                end = text.length();
            }
            String inner = text.substring(next + 3, end);
            out.add(bold ? sp(inner, K(700), 13, LH, Tok.BONE) : nsp(inner, 16, LH, Tok.AMBER));
            i = Math.min(text.length(), end + 4);
        }
        return out;
    }

    /* ================================================================== the static */

    /**
     * The old-TV static between menus, 320x155 noise stretched over the band between the bars.
     * {@code pixels} is refilled by {@link #noise} every frame of the transition.
     */
    public static void staticBand(Canvas c, com.barbwra.mlum.client.ui.text.Raster noise, float opacity) {
        if (opacity <= 0.001F) {
            return;
        }
        // 620px at the design's 720 - the whole band between the bars at any window shape
        Draw.raster(c, noise, 0, 64, Px.W, Px.H - 64 - 36, Draw.rgba(0xFFFFFF, opacity));
    }

    private static int seed = 99;

    private static float rand() {
        seed ^= seed << 13;
        seed ^= seed >>> 17;
        seed ^= seed << 5;
        return (seed & 0xFFFFFFFFL) / 4294967296.0F;
    }

    /** One frame of the design's noise: scanlines, a rolling bright band, the odd dropout row. */
    public static void noise(int[] argb, int w, int h, float t) {
        int band = (int) Math.floor(((t * 1.6F) % 1.0F) * (h + 40)) - 20;
        for (int y = 0; y < h; y++) {
            float rowK = (y & 1) != 0 ? 0.62F : 1.0F;
            int dy = y - band;
            if (dy > -12 && dy < 12) {
                rowK *= 1.45F - Math.abs(dy) / 30.0F;
            }
            float r0 = rand();
            if (r0 < 0.04F) {
                rowK *= 0.35F;
            } else if (r0 < 0.09F) {
                rowK *= 1.3F;
            }
            int o = y * w;
            for (int x = 0; x < w; x++) {
                float v = rand() * 235.0F * rowK;
                if (v > 255) {
                    v = 255;
                }
                argb[o + x] = 0xFF000000 | (((int) (v * 0.9F)) << 16) | (((int) (v * 0.97F)) << 8) | (int) v;
            }
        }
    }

    /* ================================================================== the carried item */

    /**
     * {@code #ghost}: the carried item at its full bag size under the cursor, dashed and half see
     * through, with its size under it for anything bigger than one cell.
     */
    public static Node ghost(Item it, float cx, float cy) {
        float w = it.w * InvView.PITCH - 4;
        float h = it.h * InvView.PITCH - 4;
        Node g = block().size(w, h).abs(Math.round(cx - w / 2), Math.round(cy - h / 2), AUTO, AUTO).z(8)
                .border(1.0F, Tok.BONE).dashed().bg(Draw.rgba(0xECE6D4, 0.08F)).opacity(0.55F).tag("ghost");
        g.over((c, n) -> {
            float bw = n.w - 12;
            float bh = n.h - 12;
            if (!it.tall()) {
                bh = Math.min(bh, 64);
            }
            Slots.drawItem(c, it, n.x + (n.w - bw) / 2, n.y + (n.h - bh) / 2, bw, bh);
            // How many are in hand. Without it a split is invisible - the ghost of two items and
            // the ghost of a stack of sixty look identical, and the cell it came from is hidden.
            if (it.count > 1) {
                Slots.qty(c, n, String.valueOf(it.count));
            }
        });
        if (it.w * it.h > 1) {
            Node sz = row().align(CENTER).pad(2, 6, 2, 6).bg(Draw.rgba(0x000000, 0.7F))
                    .abs(0, 4, AUTO, AUTO).pct(0.5F, 1.0F, 0, 0).translate(-0.5F, 0);
            sz.add(Css.px(it.w + "x" + it.h, 600, 14, 1.0F, 0, Tok.BONE));
            g.add(sz);
        }
        return g;
    }

    /** {@code .drop}: where the carried item would land, green when it fits, red when it does not. */
    public static void drop(Canvas c, Node bgrid, int row, int col, int w, int h, boolean ok) {
        int line = ok ? Tok.SAGE : Tok.RUST;
        cellBox(c, bgrid, row, col, w, h, line, 0.16F, 2);
    }

    /**
     * The amber square under an empty cell the cursor is over.
     *
     * <p>Its job is to say "this is a place where something goes" before the player is carrying
     * anything - the grid is a free-form board rather than a row of obvious slots, so without it an
     * empty cell and the gap between two of them look the same.</p>
     */
    public static void hoverCell(Canvas c, Node bgrid, int row, int col) {
        cellBox(c, bgrid, row, col, 1, 1, Tok.AMBER, 0.10F, 1);
    }

    /**
     * A tinted, outlined box over a footprint of the bag grid.
     *
     * <p>The grid is laid out right to left, so column 0 is the <i>right</i> edge - which is why the
     * x here is worked out from {@code bgrid.x + bgrid.w} backwards rather than forwards.</p>
     */
    private static void cellBox(Canvas c, Node bgrid, int row, int col, int w, int h,
                                int line, float fill, int weight) {
        float top = InvView.yOf(row);
        float height = InvView.yOf(row + h - 1) + InvView.CELLW - top;
        float width = w * InvView.PITCH - 4;
        float right = bgrid.x + bgrid.w - col * InvView.PITCH;
        float x = right - width;
        float y = bgrid.y + top;
        Draw.rect(c, x, y, width, height, Draw.rgba(line, fill));
        int x0 = Px.d(x);
        int y0 = Px.d(y);
        int x1 = Px.d(x + width);
        int y1 = Px.d(y + height);
        int b = Px.border(weight);
        Draw.border(c, x0, y0, x1, y1, b, b, b, b, new int[]{line, line, line, line}, false, weight);
    }

    /** Lays out and paints a floating node (ghost, toast) that is not part of the screen tree. */
    public static void paintFloating(Canvas c, Node n, com.barbwra.mlum.client.ui.Hits hits) {
        Node holder = block().size(Px.W, Px.H);
        holder.add(n);
        Layout.layout(holder, 0, 0, Px.W, Px.H);
        com.barbwra.mlum.client.ui.layout.Paint.paint(c, holder, hits);
    }
}

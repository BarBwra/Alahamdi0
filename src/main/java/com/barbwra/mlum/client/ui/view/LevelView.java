package com.barbwra.mlum.client.ui.view;

import com.barbwra.mlum.client.ui.Art;
import com.barbwra.mlum.client.ui.Canvas;
import com.barbwra.mlum.client.ui.Draw;
import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.Tok;
import com.barbwra.mlum.client.ui.layout.Layout;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.client.ui.text.Raster;
import com.barbwra.mlum.client.ui.text.Shaped;
import com.barbwra.mlum.client.ui.text.TextEngine;

import java.util.ArrayList;
import java.util.List;

import static com.barbwra.mlum.client.ui.layout.Node.AUTO;
import static com.barbwra.mlum.client.ui.layout.Node.CENTER;
import static com.barbwra.mlum.client.ui.view.Css.K;
import static com.barbwra.mlum.client.ui.view.Css.LH;
import static com.barbwra.mlum.client.ui.view.Css.P;
import static com.barbwra.mlum.client.ui.view.Css.block;
import static com.barbwra.mlum.client.ui.view.Css.col;
import static com.barbwra.mlum.client.ui.view.Css.grid;
import static com.barbwra.mlum.client.ui.view.Css.inline;
import static com.barbwra.mlum.client.ui.view.Css.nsp;
import static com.barbwra.mlum.client.ui.view.Css.num;
import static com.barbwra.mlum.client.ui.view.Css.panel;
import static com.barbwra.mlum.client.ui.view.Css.phead;
import static com.barbwra.mlum.client.ui.view.Css.row;
import static com.barbwra.mlum.client.ui.view.Css.sp;
import static com.barbwra.mlum.client.ui.view.Css.txt;

/** Level: the big number and progress with how points are earned, then the reward road. */
public final class LevelView {

    private LevelView() {
    }

    public static final int UP = 0;
    public static final int DOWN = 1;
    public static final int GOLD = 2;
    public static final int PLAIN = 3;

    public static final class Rule {
        public String value = "";
        public int kind;
        public String label = "";
        public boolean off;

        public Rule() {
        }

        public Rule(String value, int kind, String label, boolean off) {
            this.value = value;
            this.kind = kind;
            this.label = label;
            this.off = off;
        }
    }

    public static final class Milestone {
        public int level;
        public Item item;
        public String name = "";
    }

    public static final class Model {
        public int level;
        public long points;
        public int cur;
        public int need = 1;
        public final List<Rule> rules = new ArrayList<>();
        public final List<Milestone> road = new ArrayList<>();
        /** First visible milestone when the road is longer than eight. */
        public int roadStart;
        public String hover;
    }

    public static Node build(Model m) {
        Node main = Chrome.main();
        float contentH = Px.H - 64 - 36 - 32;
        Node lvl = col().gap(16).h(contentH).tag("lvl");
        lvl.add(hero(m));
        lvl.add(road(m));
        main.add(lvl);
        return main;
    }

    static Node hero(Model m) {
        Node p = panel(grid(240, -1)).gap(32).align(CENTER).pad(20, 26, 20, 26).tag("lvl-hero");
        Node left = col().align(CENTER).gap(8).border(0, 0, 0, 1, Tok.LINE_SOFT);
        left.pl = 30;
        left.add(txt("مستواك", K(400), 12, LH, Tok.MUTED));
        Node big = num(String.valueOf(m.level), 126, 0.8F, Tok.AMBER);
        big.spans.get(0).glow(30, Draw.rgba(0xF0A93B, 0.22F));
        left.add(big);
        Node pts = inline(K(400), 12, LH, Tok.MUTED);
        pts.span(nsp(Chrome.fmt(m.points), 18, LH, Tok.BONE));
        pts.span(sp(" نقطة", K(400), 12, LH, Tok.MUTED));
        left.add(pts);
        p.add(left);

        Node right = block();
        Node top = row().align(Node.BASELINE).justify(Node.BETWEEN);
        top.mb = 10;
        Node to = inline(K(400), 14, LH, Tok.SOFT);
        to.span(sp("للوصول إلى المستوى ", K(400), 14, LH, Tok.SOFT));
        to.span(nsp(String.valueOf(m.level + 1), 14, LH, Tok.SOFT));
        top.add(to);
        Node frac = inline(P(600), 22, LH, Tok.MUTED);
        frac.rtl = false;
        frac.span(nsp(String.valueOf(m.cur), 22, LH, Tok.BONE));
        frac.span(nsp(" / " + m.need, 22, LH, Tok.MUTED));
        top.add(frac);
        right.add(top);
        float f = m.need <= 0 ? 0 : m.cur / (float) m.need;
        right.add(Css.meter(12, f, Tok.rgb(0x1f231b), Tok.AMBER));
        Node left2 = inline(K(400), 12, LH, Tok.MUTED);
        left2.mt = 8;
        left2.span(sp("باقي ", K(400), 12, LH, Tok.MUTED));
        left2.span(nsp(String.valueOf(Math.max(0, m.need - m.cur)), 16, LH, Tok.AMBER));
        left2.span(sp(" نقطة", K(400), 12, LH, Tok.MUTED));
        right.add(left2);
        if (!m.rules.isEmpty()) {
            right.add(QuestsView.sectionLabel("كيف تجمع النقاط"));
            Node rules = row().gap(8).wrap();
            for (Rule r : m.rules) {
                rules.add(rule(r));
            }
            right.add(rules);
        }
        p.add(right);
        return p;
    }

    static Node rule(Rule r) {
        Node ru = row().align(CENTER).gap(8).h(34).pad(0, 10, 0, 12).border(1.0F, Tok.LINE).bg(Tok.CARD);
        if (r.off) {
            ru.dashed().opacity(0.55F);
        }
        int color = switch (r.kind) {
            case UP -> Tok.SAGE;
            case DOWN -> Tok.RUST;
            case GOLD -> Tok.AMBER;
            default -> Tok.SOFT;
        };
        Node v = num(r.value, 21, LH, color).minW(28);
        v.textAlign = CENTER;
        ru.add(v);
        ru.add(txt(r.label, K(400), 12, LH, Tok.SOFT));
        return ru;
    }

    static Node road(Model m) {
        Node p = panel(col()).tag("track-panel");
        Css.flex1(p);
        Node aside = Css.aside().add(Css.asideText("تشوف المكافآت المقفلة من الحين، عشان تعرف وش ينتظرك"));
        p.add(phead("طريق المكافآت", aside));
        int total = m.road.size();
        if (total == 0) {
            Node empty = txt("ما فيه مكافآت مضبوطة للمستويات.", K(400), 12.5F, LH, Tok.MUTED);
            empty.mt = 6;
            p.add(empty);
            return p;
        }
        int cols = Math.min(8, total);
        int start = Math.max(0, Math.min(total - cols, m.roadStart));
        // index of the first milestone not yet reached
        int ni = total;
        for (int i = 0; i < total; i++) {
            if (m.road.get(i).level > m.level) {
                ni = i;
                break;
            }
        }
        Node track = block().pad(48, 0, 0, 0).tag("track");
        Css.flex1(track);
        float colFrac = 1.0F / cols;
        float edge = colFrac / 2.0F;
        // .tbase and .tfill: from the first node's centre to the last's, and to "you are here"
        float pos = herePosition(m, ni, start, cols);
        track.add(Css.leaf(0, 2).bg(Tok.LINE).abs(0, 61, 0, AUTO).pct(edge, 0, edge, 0));
        if (pos > edge) {
            // .tfill: from the first node's centre (inset-inline-start 6.25%) to "you are here"
            Node fill = Css.leaf(0, 2).bg(Tok.SAGE).abs(AUTO, 61, 0, AUTO).pct(0, 0, edge, 0);
            fill.widthPct = Math.min(1.0F - 2 * edge, pos - edge);
            track.add(fill);
        }
        if (pos >= 0) {
            Node here = col().align(CENTER).abs(AUTO, 0, 0, AUTO).pct(0, 0, pos, 0).translate(0.5F, 0).z(2);
            here.add(txt("أنت هنا", K(400), 11, 1.2F, Tok.AMBER));
            here.add(num(String.valueOf(m.level), 19, 1.0F, Tok.AMBER));
            Node stem = Css.bar(2, 16, Tok.AMBER);
            stem.mt = 3;
            here.add(stem);
            track.add(here);
        }
        float[] tracks = new float[cols];
        java.util.Arrays.fill(tracks, -1);
        Node nodes = grid(tracks).tag("tnodes");
        for (int i = start; i < start + cols; i++) {
            nodes.add(node(m, i, ni));
        }
        track.add(nodes);
        p.add(track);
        return p;
    }

    /** Where "you are here" sits across the visible road, as a fraction from the right. */
    static float herePosition(Model m, int ni, int start, int cols) {
        int total = m.road.size();
        if (ni <= 0) {
            return -1;
        }
        if (ni >= total) {
            return (total - 1 - start + 0.5F) / cols;
        }
        int prev = m.road.get(ni - 1).level;
        int next = m.road.get(ni).level;
        float t = next == prev ? 0 : (m.level - prev) / (float) (next - prev);
        float idx = (ni - 1 - start) + 0.5F + t;
        if (idx < 0 || idx > cols) {
            return -1;
        }
        return idx / cols;
    }

    static Node node(Model m, int i, int ni) {
        Milestone ms = m.road.get(i);
        boolean done = ms.level <= m.level;
        boolean next = i == ni;
        Node tn = col().align(CENTER).gap(8);
        Node dia = Css.leaf(16, 16);
        dia.mt = 6;
        dia.under((c, n) -> diamond(c, n, done, next));
        tn.add(dia);
        Node lv = num("LV " + ms.level, 15, LH, done ? Tok.SAGE : next ? Tok.AMBER : Tok.FAINT);
        lv.mt = 4;
        tn.add(lv);
        Node slot = Slots.slot(ms.item, 62, 44, false, Slots.IDLE, null);
        if (next) {
            slot.borderColor = Tok.AMBER;
            Painter0.wrapUnder(slot, (c, n) -> ring(c, n, 3, Tok.AMBER_GLOW));
        }
        if (!done && !next) {
            // .tn.lock .slot .spr - dimmed, with the padlock tab on the corner
            if (ms.item != null) {
                ms.item.tint = 0xFFBFBFBF;
            }
            Painter0.wrapOver(slot, (c, n) -> lockTab(c, n));
        }
        slot.hit("road:" + i, i);
        tn.add(slot);
        Node name = txt(ms.name, K(400), 12, 1.5F, Tok.SOFT).wrapText().maxW(128);
        name.textAlign = CENTER;
        tn.add(name);
        Node st;
        if (done) {
            st = txt("مفتوحة", K(400), 11, LH, Tok.SAGE);
        } else if (next) {
            st = inline(K(400), 11, LH, Tok.AMBER);
            st.span(sp("باقي ", K(400), 11, LH, Tok.AMBER));
            st.span(nsp(String.valueOf(ms.level - m.level), 14, LH, Tok.AMBER));
            st.span(sp(" مستويات", K(400), 11, LH, Tok.AMBER));
        } else {
            st = txt("مقفلة", K(400), 11, LH, Tok.FAINT);
        }
        st.textAlign = CENTER;
        tn.add(st);
        return tn;
    }

    /** {@code .dia}: a 16px square turned 45 degrees, 2px border; ticked when reached. */
    static void diamond(Canvas c, Node n, boolean done, boolean next) {
        int border = done ? Tok.SAGE : next ? Tok.AMBER : Tok.LINE;
        int fill = done ? Tok.SAGE : next ? Tok.rgb(0x2a200f) : Tok.PANEL_SOLID;
        float cx = n.x + n.w / 2.0F;
        float cy = n.y + n.h / 2.0F;
        // a rotated square of side 16: half-diagonal 16/sqrt2 = 11.31
        diamondFill(c, cx, cy, 11.314F, border);
        diamondFill(c, cx, cy, 11.314F - 2.0F * 1.4142F, fill);
        if (done) {
            Draw.raster(c, Art.mono("check"), cx - 4, cy - 4, 8, 8, Tok.rgb(0x10140d));
        }
    }

    /** A filled diamond, row by row in device pixels. */
    static void diamondFill(Canvas c, float cx, float cy, float half, int argb) {
        int dcx = Math.round(cx * Px.s);
        int dcy = Math.round(cy * Px.s);
        int dh = Math.round(half * Px.s);
        for (int dy = -dh; dy < dh; dy++) {
            float yy = dy + 0.5F;
            int w = Math.round(dh - Math.abs(yy));
            if (w > 0) {
                c.fill(dcx - w, dcy + dy, dcx + w, dcy + dy + 1, argb);
            }
        }
    }

    /** {@code box-shadow: 0 0 0 Npx c}: an outer ring. */
    static void ring(Canvas c, Node n, float width, int argb) {
        int x0 = Px.d(n.x);
        int y0 = Px.d(n.y);
        int x1 = Px.d(n.x + n.w);
        int y1 = Px.d(n.y + n.h);
        int w = Px.d(width);
        c.fill(x0 - w, y0 - w, x1 + w, y0, argb);
        c.fill(x0 - w, y1, x1 + w, y1 + w, argb);
        c.fill(x0 - w, y0, x0, y1, argb);
        c.fill(x1, y0, x1 + w, y1, argb);
    }

    /** {@code .lk}: 20px padlock tab over the top-left corner. */
    static void lockTab(Canvas c, Node n) {
        float x = n.x + n.bl - 7;
        float y = n.y + n.bt - 7;
        Draw.rect(c, x, y, 20, 20, Tok.PANEL_SOLID);
        int x0 = Px.d(x);
        int y0 = Px.d(y);
        int x1 = Px.d(x + 20);
        int y1 = Px.d(y + 20);
        int b = Px.border(1);
        Draw.border(c, x0, y0, x1, y1, b, b, b, b, new int[]{Tok.LINE, Tok.LINE, Tok.LINE, Tok.LINE}, false, 1);
        Draw.raster(c, Art.mono("lock"), x + 6, y + 6, 8, 8, Tok.MUTED);
    }

    /** Helpers for stacking painters on a node that already has one. */
    static final class Painter0 {
        static void wrapUnder(Node n, com.barbwra.mlum.client.ui.layout.Painter p) {
            com.barbwra.mlum.client.ui.layout.Painter old = n.under;
            n.under = (c, nd) -> {
                p.paint(c, nd);
                if (old != null) {
                    old.paint(c, nd);
                }
            };
        }

        static void wrapOver(Node n, com.barbwra.mlum.client.ui.layout.Painter p) {
            com.barbwra.mlum.client.ui.layout.Painter old = n.over;
            n.over = (c, nd) -> {
                if (old != null) {
                    old.paint(c, nd);
                }
                p.paint(c, nd);
            };
        }
    }

}

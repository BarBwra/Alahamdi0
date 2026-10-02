package com.barbwra.mlum.client.ui.view;

import com.barbwra.mlum.client.ui.Art;
import com.barbwra.mlum.client.ui.Draw;
import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.Tok;
import com.barbwra.mlum.client.ui.layout.Node;

import java.util.ArrayList;
import java.util.List;

import static com.barbwra.mlum.client.ui.layout.Node.AUTO;
import static com.barbwra.mlum.client.ui.layout.Node.CENTER;
import static com.barbwra.mlum.client.ui.view.Css.K;
import static com.barbwra.mlum.client.ui.view.Css.LH;
import static com.barbwra.mlum.client.ui.view.Css.aside;
import static com.barbwra.mlum.client.ui.view.Css.asideNum;
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

/** Vehicles: the garage list on the right, the chosen vehicle's showcase on the left. */
public final class VehiclesView {

    private VehiclesView() {
    }

    public static final int STORED = 0;
    public static final int OUT = 1;
    public static final int WAIT = 2;

    public static final class Stat {
        public String label = "";
        public String value = "";
        /** 0..1 for a bar under the value, or -1. */
        public float bar = -1.0F;
        /** Seats to light out of {@link #seatsOf}, or -1. */
        public int seats = -1;
        public int seatsOf = 6;
    }

    public static final class Vehicle {
        public String id = "";
        /** Registry id of the entity, so the showcase can draw its real model. */
        public String entityId = "";
        public String name = "";
        public String subtitle = "";
        /** Picture: a texture in game, a sprite in the preview. */
        public Object image;
        public boolean armed;
        /** Limited (consumable) vehicles have a count; permanent ones do not. */
        public boolean limited;
        public int left;
        public int state = STORED;
        public int cooldown;
        public boolean available = true;
        public final List<Stat> stats = new ArrayList<>();
    }

    public static final class Model {
        public final List<Vehicle> vehicles = new ArrayList<>();
        public int selected;
        /** Why the action button is disabled, or null. */
        public String blocked;
        public boolean anotherOut;
        public String hover;
        public boolean busy;
    }

    public static Node build(Model m) {
        Node main = Chrome.main();
        float contentH = Px.H - 64 - 36 - 32;
        Node split = grid(456, -1).gap(22).rows(-1).h(contentH).tag("split");
        split.add(list(m));
        split.add(detail(m));
        main.add(split);
        return main;
    }

    static Node kindChip(Vehicle v) {
        return v.armed ? Css.chip("مسلحة", Css.CHIP_RUST) : Css.chip("مدنية", Css.CHIP_SAGE);
    }

    static Node limChip(Vehicle v) {
        if (!v.limited) {
            return Css.chip("دائمة", Css.CHIP_MUTE);
        }
        Node c = Css.chipRow(Tok.AMBER, Tok.AMBER_GLOW, Tok.AMBER);
        c.add(txt("محدودة · باقي", K(600), 11, 1.0F, Tok.AMBER));
        c.add(num(String.valueOf(v.left), 11, 1.0F, Tok.AMBER));
        return c;
    }

    /** {@code .vstate}: a 7px square then the state, faint / sage / amber. */
    static Node state(int kind, String text) {
        int color = kind == OUT ? Tok.SAGE : kind == WAIT ? Tok.AMBER : Tok.FAINT;
        Node s = row().align(CENTER).gap(6);
        s.add(Css.bar(7, 7, color));
        s.add(txt(text, K(400), 11, LH, color));
        return s;
    }

    static String stateText(Vehicle v) {
        return switch (v.state) {
            case OUT -> "في الخريطة";
            case WAIT -> "انتظر " + v.cooldown + " ث";
            default -> "مخزنة";
        };
    }

    static Node list(Model m) {
        Node p = panel(col()).minH(0).tag("vlist");
        p.add(phead("مركباتي", aside().add(asideNum(String.valueOf(m.vehicles.size())))));
        Node list = col().gap(6);
        for (int i = 0; i < m.vehicles.size(); i++) {
            Vehicle v = m.vehicles.get(i);
            String key = "v:" + i;
            boolean sel = i == m.selected;
            boolean hover = key.equals(m.hover);
            Node it = grid(76, -1, 0).gap(12).align(CENTER).pad(10, 12, 10, 12).bg(Tok.CARD)
                    .border(1.0F, sel ? Tok.AMBER : hover ? Tok.AMBER_DIM : Tok.LINE).hit(key, i);
            if (sel) {
                it.hgrad(new float[]{0, 1}, new int[]{Tok.CARD, Tok.AMBER_GLOW});
                it.bg(0);
            }
            Node thumb = Css.leaf(76, 32);
            final Object img = v.image;
            thumb.under((c, n) -> c.image(img, Px.d(n.x), Px.d(n.y), Px.d(n.x + n.w), Px.d(n.y + n.h), 0xFFFFFFFF));
            it.add(thumb);
            Node mid = block();
            mid.add(txt(v.name, K(600), 14, 1.5F, Tok.BONE));
            Node chips = row().gap(6).wrap();
            chips.mt = 4;
            chips.add(kindChip(v), limChip(v));
            mid.add(chips);
            it.add(mid);
            it.add(state(v.state, stateText(v)));
            list.add(it);
        }
        if (m.vehicles.isEmpty()) {
            list.add(txt("ما عندك مركبات للحين.", K(400), 12.5F, LH, Tok.MUTED));
        }
        p.add(list);
        Node legend = grid(-1).gap(6);
        legend.mt = AUTO;
        legend.pt = 14;
        legend.add(legendLine("دائمة:", " إذا انفجرت تقدر تستدعيها مرة ثانية."));
        legend.add(legendLine("محدودة:", " لها عدد، وإذا خلص العدد تنحذف من قائمتك."));
        legend.add(legendLine("وحدة بس:", " تقدر تطلع مركبة وحدة بالمرة، خزّنها قبل ما تطلع غيرها."));
        p.add(legend);
        return p;
    }

    static Node legendLine(String bold, String rest) {
        Node t = inline(K(400), 11.5F, 1.7F, Tok.MUTED).wrapText();
        t.span(sp(bold, K(600), 11.5F, 1.7F, Tok.SOFT));
        t.span(sp(rest, K(400), 11.5F, 1.7F, Tok.MUTED));
        return t;
    }

    static Node detail(Model m) {
        Node p = panel(col()).pad(0).minH(0).tag("vd");
        if (m.vehicles.isEmpty()) {
            Node body = block().pad(18, 24, 20, 24);
            body.add(txt("لما تحصل على مركبة بتطلع هنا.", K(400), 13, LH, Tok.MUTED));
            p.add(body);
            return p;
        }
        Vehicle v = m.vehicles.get(Math.max(0, Math.min(m.vehicles.size() - 1, m.selected)));
        /*
         * The showcase takes the panel. It was a fixed 240px band with the text below it filling
         * whatever was left, which on a tall window left a small vehicle floating over a lot of
         * empty rules - the vehicle is the thing worth looking at, so it grows and the text does not.
         */
        Node hero = row().align(CENTER).justify(CENTER).border(0, 0, 1, 0, Tok.LINE_SOFT).tag("vd-hero");
        hero.minH(300);
        Css.flex1(hero);
        hero.under((c, n) -> {
            // radial-gradient(ellipse 45% 55% at 50% 72%, rgba(240,169,59,.09), transparent 70%)
            float rx = n.w * 0.45F;
            float ry = n.h * 0.55F;
            float cx = n.x + n.w / 2;
            float cy = n.y + n.h * 0.72F;
            Draw.rasterClipped(c, Art.radial(0.70F), cx - rx, cy - ry, rx * 2, ry * 2, n.x, n.y, n.x + n.w, n.y + n.h,
                    Draw.rgba(0xF0A93B, 0.09F));
        });
        Node ground = Css.leaf(0, 1).abs(0, AUTO, 0, 36).pct(0.12F, 0, 0.12F, 0);
        ground.hgrad(new float[]{0, 0.5F, 1}, new int[]{0x003d4336, Tok.rgb(0x3d4336), 0x003d4336});
        hero.add(ground);
        Node big = Css.leaf(660, 280).z(1);
        final Object img = v.image;
        final String entity = v.entityId;
        big.under((c, n) -> {
            int bx0 = Px.d(n.x);
            int by0 = Px.d(n.y);
            int bx1 = Px.d(n.x + n.w);
            int by1 = Px.d(n.y + n.h);
            // The vehicle's own model when the mod that owns it is installed; the flat picture is
            // the fallback, so a pack without Superb Warfare still shows something.
            if (!c.entity(entity, bx0, by0, bx1, by1)) {
                c.image(img, bx0, by0, bx1, by1, 0xFFFFFFFF);
            }
        });
        hero.add(big);
        p.add(hero);

        Node body = col().gap(16).pad(18, 24, 20, 24);
        Node title = row().justify(Node.BETWEEN).align(Node.START).gap(16);
        Node names = block();
        names.add(txt(v.name, K(700), 28, 1.3F, Tok.BONE));
        Node sub = txt(v.subtitle, K(400), 13, LH, Tok.MUTED);
        sub.mt = 2;
        names.add(sub);
        title.add(names);
        Node chips = row().gap(6).wrap();
        chips.add(kindChip(v), limChip(v));
        title.add(chips);
        body.add(title);

        if (!v.stats.isEmpty()) {
            Node stats = grid(-1, -1).gap(28);
            for (Stat s : v.stats) {
                Node st = grid(-1, 0).gap(6, 12).align(Node.BASELINE);
                st.add(txt(s.label, K(400), 12, LH, Tok.MUTED));
                st.add(num(s.value, 30, 1.0F, Tok.BONE));
                if (s.bar >= 0) {
                    st.add(Css.meter(10, s.bar, Tok.rgb(0x1f231b), Tok.AMBER).spanCols(-1));
                } else if (s.seats >= 0) {
                    Node seats = row().gap(4).spanCols(-1);
                    for (int k = 0; k < s.seatsOf; k++) {
                        seats.add(Css.bar(26, 10, k < s.seats ? Tok.AMBER : Tok.rgb(0x23281f)));
                    }
                    st.add(seats);
                }
                stats.add(st);
            }
            body.add(stats);
        }

        if (v.limited) {
            Node warn = row().align(Node.START).gap(10).pad(10, 12, 10, 12)
                    .border(1.0F, Tok.rgba(0xe0613f, 0.45)).bg(Tok.rgba(0xe0613f, 0.08));
            Node ic = Css.icon("warn", 16, 16, Tok.rgb(0xf0b7a6));
            ic.mt = 4;
            warn.add(ic);
            Node t = inline(K(400), 12.5F, 1.8F, Tok.rgb(0xf0b7a6)).wrapText();
            t.span(sp("مركبة محدودة: إذا انفجرت تنقص من العدد، وإذا خلص العدد تنحذف نهائياً. باقي منها ", K(400), 12.5F, 1.8F,
                    Tok.rgb(0xf0b7a6)));
            t.span(nsp(String.valueOf(v.left), 12.5F, 1.8F, Tok.rgb(0xf0b7a6)));
            t.span(sp(".", K(400), 12.5F, 1.8F, Tok.rgb(0xf0b7a6)));
            warn.add(Css.flex1(t));
            body.add(warn);
        } else {
            Node note = row().align(Node.START).gap(10).pad(10, 12, 10, 12)
                    .border(1.0F, Tok.rgba(0x93c46f, 0.35)).bg(Tok.rgba(0x93c46f, 0.07));
            Node ic = Css.icon("check", 16, 16, Tok.rgb(0xcfe3bf));
            ic.mt = 4;
            note.add(ic);
            note.add(Css.flex1(txt("مركبة دائمة: إذا انفجرت تقدر تستدعيها مرة ثانية.", K(400), 12.5F, 1.8F,
                    Tok.rgb(0xcfe3bf)).wrapText()));
            body.add(note);
        }

        Node actions = row().align(CENTER).gap(16);
        actions.mt = AUTO;
        boolean hover = "veh".equals(m.hover);
        if (v.state == OUT) {
            Node b = Css.btn("تخزين المركبة", Css.BTN_GHOST, false, m.busy);
            if (!m.busy) {
                b.hit("veh", m.selected);
            }
            actions.add(b);
            actions.add(state(OUT, "المركبة في الخريطة الحين"));
        } else if (v.state == WAIT) {
            Node b = Css.btnRow(Css.BTN, false, true);
            b.add(txt("انتظر", K(600), 14, 1.0F, Tok.BTN_INK));
            b.add(num(String.valueOf(v.cooldown), 14, 1.0F, Tok.BTN_INK));
            b.add(txt("ثانية", K(600), 14, 1.0F, Tok.BTN_INK));
            actions.add(b);
            actions.add(state(WAIT, "تقدر تستدعيها بعد شوي"));
        } else {
            boolean disabled = m.blocked != null || m.busy;
            Node b = Css.btn("استدعاء المركبة", Css.BTN, false, disabled);
            if (!disabled) {
                b.hit("veh", m.selected);
                if (hover) {
                    QuestsView.brighten(b);
                }
            }
            actions.add(b);
            actions.add(state(STORED, m.blocked != null ? m.blocked : "المركبة مخزنة"));
        }
        body.add(actions);
        p.add(body);
        return p;
    }
}

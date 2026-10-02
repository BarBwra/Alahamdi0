package com.barbwra.mlum.client.ui.view;

import com.barbwra.mlum.client.ui.Draw;
import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.Tok;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.client.ui.text.TextEngine;

import java.util.ArrayList;
import java.util.List;

import static com.barbwra.mlum.client.ui.layout.Node.AUTO;
import static com.barbwra.mlum.client.ui.layout.Node.CENTER;
import static com.barbwra.mlum.client.ui.view.Css.K;
import static com.barbwra.mlum.client.ui.view.Css.LH;
import static com.barbwra.mlum.client.ui.view.Css.P;
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
import static com.barbwra.mlum.client.ui.view.Css.txt;

/** Quests: active and completed lists on the right, the selected quest in full on the left. */
public final class QuestsView {

    private QuestsView() {
    }

    public static final int MAIN = 0;
    public static final int DAILY = 1;
    public static final int SIDE = 2;

    public static final String[] TYPE_NAMES = {"رئيسية", "يومية", "جانبية"};
    public static final int[] TYPE_COLORS = {Tok.AMBER, Tok.STEEL, Tok.RARITY[0]};

    public static final class Reward {
        public Item item;
        public long count;
    }

    public static final class Quest {
        public int line;
        public int type;
        public String title = "";
        /** Paragraphs; the game's '|' separator becomes a line break. */
        public String description = "";
        public int progress;
        public int max = 1;
        public boolean done;
        public boolean claimed;
        public final List<Reward> rewards = new ArrayList<>();

        public float fraction() {
            return max <= 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F, progress / (float) max));
        }
    }

    public static final class Model {
        public final List<Quest> quests = new ArrayList<>();
        public int selected;
        public String hover;
        /** A claim is on its way to the server: the button waits instead of firing twice. */
        public boolean claiming;
    }

    public static Node build(Model m) {
        Node main = Chrome.main();
        float contentH = Px.H - 64 - 36 - 32;
        Node split = grid(456, -1).gap(22).rows(-1).h(contentH).tag("split");
        split.add(listPanel(m));
        split.add(detail(m));
        main.add(split);
        return main;
    }

    static Node listPanel(Model m) {
        Node p = panel(col()).minH(0).tag("qlist");
        List<Integer> act = new ArrayList<>();
        List<Integer> done = new ArrayList<>();
        for (int i = 0; i < m.quests.size(); i++) {
            (m.quests.get(i).done ? done : act).add(i);
        }
        p.add(phead("المهام النشطة", aside().add(asideNum(String.valueOf(act.size())))));
        Node list = col().gap(6);
        for (int i : act) {
            list.add(item(m, i));
        }
        p.add(list);
        Node head2 = phead("المهام المكتملة", aside().add(asideNum(String.valueOf(done.size()))));
        head2.mt = 18;
        p.add(head2);
        Node list2 = col().gap(6);
        for (int i : done) {
            list2.add(item(m, i));
        }
        p.add(list2);
        if (m.quests.isEmpty()) {
            Node empty = txt("ما عندك مهام الحين.", K(400), 12.5F, LH, Tok.MUTED);
            empty.mt = 6;
            p.add(empty);
        }
        Node legend = row().gap(16).wrap();
        legend.mt = AUTO;
        legend.pt = 14;
        for (int t = 0; t < 3; t++) {
            legend.add(legendItem(TYPE_COLORS[t], TYPE_NAMES[t]));
        }
        legend.add(legendItem(Tok.SAGE, "مكتملة"));
        p.add(legend);
        return p;
    }

    static Node legendItem(int color, String label) {
        Node s = row().align(CENTER).gap(6);
        s.add(Css.bar(8, 8, color));
        s.add(txt(label, K(400), 11, LH, Tok.MUTED));
        return s;
    }

    static Node item(Model m, int i) {
        Quest q = m.quests.get(i);
        String key = "q:" + i;
        boolean sel = i == m.selected;
        boolean hover = key.equals(m.hover);
        int qc = q.done ? Tok.SAGE : TYPE_COLORS[q.type];
        Node it = grid(3, -1, 0).gap(12).align(CENTER).pad(10, 12, 10, 12).bg(Tok.CARD)
                .border(1.0F, sel ? Tok.AMBER : hover ? Tok.AMBER_DIM : Tok.LINE).hit(key, i);
        if (sel) {
            // linear-gradient(to left, amber-glow, card)
            it.hgrad(new float[]{0, 1}, new int[]{Tok.CARD, Tok.AMBER_GLOW});
            it.bg(0);
        }
        it.add(Css.leaf(0, 0).bg(qc).self(Node.STRETCH));
        Node mid = block();
        String title = TextEngine.fit(q.title, K(600), 13.5F, 0, true, 300);
        mid.add(txt(title, K(600), 13.5F, 1.5F, q.done ? Tok.MUTED : Tok.BONE));
        Node meta = row().align(CENTER).gap(10);
        meta.mt = 4;
        meta.add(txt(TYPE_NAMES[q.type], K(400), 11, LH, Tok.MUTED));
        meta.add(mini(90, q.fraction(), qc));
        mid.add(meta);
        it.add(mid);
        Node end;
        if (q.done) {
            if (q.claimed) {
                end = row().align(CENTER).justify(CENTER).size(22, 22).bg(Tok.SAGE);
                end.add(Css.icon("check", 16, 16, Tok.rgb(0x10140d)));
            } else {
                end = Css.chip("جاهزة", Css.CHIP_AMBER);
            }
        } else {
            end = num(q.progress + " / " + q.max, 17, LH, Tok.BONE);
        }
        it.add(end);
        return it;
    }

    /** {@code .mini}: a 4px track with a fill from the right. */
    static Node mini(float w, float fraction, int color) {
        Node n = Css.leaf(w, 4).bg(Tok.rgb(0x23281f));
        final float f = Math.max(0, Math.min(1, fraction));
        n.under((c, nd) -> Draw.rect(c, nd.x + nd.w - nd.w * f, nd.y, nd.w * f, nd.h, color));
        return n;
    }

    static Node detail(Model m) {
        Node p = panel(block()).pad(22, 26, 22, 26).tag("qd");
        if (m.quests.isEmpty()) {
            p.add(txt("ما فيه مهمة مختارة.", K(400), 14, 2.0F, Tok.MUTED));
            return p;
        }
        Quest q = m.quests.get(Math.max(0, Math.min(m.quests.size() - 1, m.selected)));
        int tc = TYPE_COLORS[q.type];
        Node types = row().align(CENTER).gap(8);
        types.add(Css.chip("مهمة " + TYPE_NAMES[q.type], tc, 0, tc));
        types.add(q.done ? Css.chip("مكتملة", Css.CHIP_SAGE) : Css.chip("نشطة", Css.CHIP_MUTE));
        p.add(types);
        Node h2 = txt(q.title, K(700), 28, 1.45F, Tok.BONE).wrapText();
        h2.mt = 14;
        h2.mb = 8;
        p.add(h2);
        float ch = TextEngine.width("0", K(400), 14, 0, false);
        Node desc = block().maxW(60 * ch);
        for (String para : q.description.split("\\|")) {
            desc.add(txt(para.trim(), K(400), 14, 2.0F, Tok.SOFT).wrapText());
        }
        p.add(desc);
        p.add(sectionLabel("التقدم"));
        Node big = row().align(Node.BASELINE).justify(Node.BETWEEN).maxW(560);
        Node frac = inline(P(600), 40, 1.0F, Tok.BONE);
        frac.rtl = false;
        frac.span(nsp(q.progress + " ", 40, 1.0F, Tok.BONE));
        // letter-spacing is inherited as a length: 0.02em of the 40px parent
        frac.span(nsp("/ " + q.max, 24, 1.0F, Tok.MUTED).spacing(0.8F));
        big.add(frac);
        big.add(num(Math.round(q.fraction() * 100) + "%", 20, LH, Tok.MUTED));
        p.add(big);
        Node bar = Css.meter(10, q.fraction(), Tok.rgb(0x1f231b), q.done ? Tok.SAGE : tc).maxW(560);
        bar.mt = 10;
        p.add(bar);
        p.add(sectionLabel("المكافآت"));
        Node rewards = row().gap(10).wrap();
        for (Reward r : q.rewards) {
            Node rw = row().align(CENTER).gap(10).pad(6, 16, 6, 6).bg(Tok.CARD).border(1.0F, Tok.LINE_SOFT);
            rw.add(Slots.slot(r.item, 44, 30, false, Slots.IDLE, null));
            Node t = block();
            t.add(txt(r.item.name, K(400), 12.5F, LH, Tok.BONE));
            t.add(num("× " + Chrome.fmt(r.count), 17, LH, Tok.AMBER));
            rw.add(t);
            rewards.add(rw);
        }
        p.add(rewards);
        if (q.done) {
            Node claim = row().align(CENTER).gap(14);
            claim.mt = 22;
            if (q.claimed) {
                Node chip = Css.chipRow(Tok.SAGE, Tok.rgba(0x93c46f, 0.1), Tok.SAGE);
                chip.add(Css.icon("check", 8, 8, Tok.SAGE));
                chip.add(txt("استلمت المكافآت", K(600), 11, 1.0F, Tok.SAGE));
                claim.add(chip);
            } else {
                boolean hover = "claim".equals(m.hover);
                Node btn = Css.btn("استلم المكافآت", Css.BTN, false, m.claiming);
                if (hover && !m.claiming) {
                    brighten(btn);
                }
                if (!m.claiming) {
                    btn.hit("claim", q.line);
                }
                claim.add(btn);
                claim.add(txt("المكافآت تنحط في حقيبتك", K(400), 12, LH, Tok.MUTED));
            }
            p.add(claim);
        }
        return p;
    }

    /** {@code .btn:hover{filter:brightness(1.08)}} on a filled button. */
    public static void brighten(Node btn) {
        if ((btn.bg >>> 24) != 0) {
            btn.bg = bright(btn.bg);
        }
        btn.borderColor = bright(btn.borderColor);
    }

    static int bright(int argb) {
        int a = argb >>> 24;
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * 1.08F));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * 1.08F));
        int b = Math.min(255, Math.round((argb & 0xFF) * 1.08F));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** {@code .section-label}: muted caption with a soft rule running out of it. */
    public static Node sectionLabel(String text) {
        Node s = row().align(CENTER).gap(10);
        s.mt = 24;
        s.mb = 10;
        s.add(txt(text, K(400), 11.5F, LH, Tok.MUTED));
        s.add(Css.flex1(Css.leaf(0, 1).bg(Tok.LINE_SOFT)));
        return s;
    }
}

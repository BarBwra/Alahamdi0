package com.barbwra.mlum.client.ui.view;

import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.Tok;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.client.ui.layout.Span;

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
import static com.barbwra.mlum.client.ui.view.Css.row;
import static com.barbwra.mlum.client.ui.view.Css.sp;
import static com.barbwra.mlum.client.ui.view.Css.txt;

/** Skills: permanent perks bought with server money, three levels each, only a few may be maxed. */
public final class SkillsView {

    private SkillsView() {
    }

    public static final class Perk {
        public String id = "";
        public String name = "";
        public String description = "";
        /** One-colour icon name from {@code Art.MONO}. */
        public String icon = "bolt";
        /** What each level gives, as text with numbers marked {@code {n}}...{@code {/n}} or plain. */
        public final String[] effects = {"", "", ""};
        public final long[] prices = {0, 0, 0};
        public int level;
        public int maxLevel = 3;
        /** A placeholder for a perk that is planned but not built: drawn, never buyable. */
        public boolean soon;
    }

    public static final class Model {
        public final List<Perk> perks = new ArrayList<>();
        public int maxedCap = 3;
        public long money;
        /** What dropping a skill back to level 0 costs. */
        public long dropCost = 2000;
        /** Index of the perk whose buy button is asking for confirmation, or -1. */
        public int confirming = -1;
        /** Index of the perk whose drop button is asking for confirmation, or -1. */
        public int dropping = -1;
        /** A purchase is on its way to the server. */
        public int buying = -1;
        public boolean confirmStep = true;
        public String hover;
    }

    public static int maxed(Model m) {
        int n = 0;
        for (Perk p : m.perks) {
            if (!p.soon && p.level >= p.maxLevel) {
                n++;
            }
        }
        return n;
    }

    public static Node build(Model m) {
        Node main = Chrome.main();
        float contentH = Px.H - 64 - 36 - 32;
        Node sk = col().gap(14).h(contentH).tag("skills");
        sk.add(head(m));
        int count = Math.max(1, m.perks.size());
        int rowsN = (count + 3) / 4;
        float[] rowTracks = new float[rowsN];
        java.util.Arrays.fill(rowTracks, -1);
        Node g = grid(-1, -1, -1, -1).rows(rowTracks).gap(14).minH(0).tag("sk-grid");
        Css.flex1(g);
        for (int i = 0; i < m.perks.size(); i++) {
            g.add(perk(m, i));
        }
        sk.add(g);
        main.add(sk);
        return main;
    }

    static Node head(Model m) {
        Node p = panel(row()).align(CENTER).gap(26).pad(14, 20, 14, 20).tag("sk-head");
        Node t = block();
        Css.flex1(t);
        t.add(txt("مهارات الناجي", K(700), 19, 1.5F, Tok.BONE));
        Node desc = inline(K(400), 12.5F, 1.8F, Tok.MUTED).wrapText();
        desc.mt = 2;
        desc.span(sp("مهارات دائمة طول الموسم تشتريها بفلوس السيرفر. كل مهارة لها ", K(400), 12.5F, 1.8F, Tok.MUTED));
        desc.span(nsp("3", 12.5F, 1.8F, Tok.MUTED));
        desc.span(sp(" مستويات، وتقدر تكمّل ", K(400), 12.5F, 1.8F, Tok.MUTED));
        desc.span(nsp(String.valueOf(m.maxedCap), 12.5F, 1.8F, Tok.MUTED));
        desc.span(sp(" مهارات بس، فاختار أسلوبك.", K(400), 12.5F, 1.8F, Tok.MUTED));
        t.add(desc);
        p.add(t);
        Node cap = row().align(CENTER).gap(10);
        cap.add(txt("المهارات المكتملة", K(400), 12, LH, Tok.MUTED));
        Node caps = row().gap(4);
        int maxed = maxed(m);
        for (int k = 0; k < m.maxedCap; k++) {
            boolean on = k < maxed;
            caps.add(Css.leaf(16, 16).bg(on ? Tok.SAGE : Tok.rgb(0x161a13)).border(1.0F, on ? Tok.SAGE : Tok.LINE));
        }
        cap.add(caps);
        cap.add(num(maxed + " / " + m.maxedCap, 19, LH, Tok.BONE));
        p.add(cap);
        return p;
    }

    static Node perk(Model m, int i) {
        Perk pk = m.perks.get(i);
        if (pk.soon) {
            return soonCard(pk);
        }
        boolean isMax = pk.level >= pk.maxLevel;
        Node p = panel(col()).gap(10).pad(16).tag("perk");
        if (isMax) {
            p.borderColor = Tok.rgba(0x93c46f, 0.4);
        }
        Node top = row().align(CENTER).gap(12);
        Node pi = row().align(CENTER).justify(CENTER).size(44, 44).shrink(0).border(1.0F, isMax ? Tok.rgba(0x93c46f, 0.4) : Tok.LINE)
                .bg(Tok.SLOT);
        pi.add(Css.icon(pk.icon, 18, 18, isMax ? Tok.SAGE : Tok.AMBER));
        top.add(pi);
        Node names = block();
        names.add(txt(pk.name, K(700), 15, 1.4F, Tok.BONE));
        Node lv = inline(K(400), 11.5F, LH, Tok.MUTED);
        lv.span(sp("المستوى ", K(400), 11.5F, LH, Tok.MUTED));
        lv.span(nsp(String.valueOf(pk.level), 15, LH, Tok.BONE));
        lv.span(sp(" من ", K(400), 11.5F, LH, Tok.MUTED));
        lv.span(nsp(String.valueOf(pk.maxLevel), 15, LH, Tok.BONE));
        names.add(lv);
        top.add(names);
        p.add(top);
        p.add(txt(pk.description, K(400), 12, 1.8F, Tok.SOFT).wrapText());
        Node pips = grid(-1, -1, -1).gap(6);
        for (int k = 0; k < 3; k++) {
            boolean on = k < pk.level;
            Node pip = col().gap(0);
            Node bar = Css.leaf(0, 6).bg(on ? Tok.AMBER : Tok.rgb(0x23281f));
            bar.mb = 6;
            pip.add(bar);
            Node t = effect(pk.effects[k], on ? Tok.BONE : Tok.FAINT);
            t.textAlign = CENTER;
            pip.add(t);
            pips.add(pip);
        }
        p.add(pips);
        Node foot = row().align(CENTER).justify(Node.BETWEEN).gap(8).border(1, 0, 0, 0, Tok.LINE_SOFT);
        foot.mt = AUTO;
        foot.pt = 10;
        if (isMax) {
            foot.add(Css.chip("مكتملة", Css.CHIP_SAGE));
            foot.add(dropButton(m, i, pk));
        } else {
            long price = pk.prices[Math.min(2, Math.max(0, pk.level))];
            Node pr = row().align(CENTER).gap(6);
            pr.add(InvView.cash());
            pr.add(num(Chrome.fmt(price), 18, LH, Tok.BONE));
            foot.add(pr);
            boolean capped = pk.level == pk.maxLevel - 1 && maxed(m) >= m.maxedCap;
            boolean shortMoney = m.money < price;
            String key = "buy:" + i;
            boolean hover = key.equals(m.hover);
            Node btn;
            if (m.buying == i) {
                btn = Css.btn("جاري الشراء…", Css.BTN, true, true);
            } else if (capped) {
                btn = Css.btn("وصلت الحد", Css.BTN, true, true);
            } else if (shortMoney) {
                btn = Css.btnRow(Css.BTN_GHOST, true, true);
                btn.gap(6);
                btn.add(txt("ناقصك", K(600), 12.5F, 1.0F, Tok.AMBER));
                btn.add(num(Chrome.fmt(price - m.money), 12.5F, 1.0F, Tok.AMBER));
            } else if (m.confirmStep && m.confirming == i) {
                btn = Css.btn("أكّد الشراء", Css.BTN_CONFIRM, true, false).hit(key, i);
            } else {
                btn = Css.btn("شراء", Css.BTN, true, false).hit(key, i);
            }
            if (hover && btn.hit != null) {
                QuestsView.brighten(btn);
            }
            foot.add(btn);
        }
        p.add(foot);
        if (!isMax && pk.level > 0) {
            // a part-bought skill still needs a way out, and it does not fit in the footer row
            // beside the price - so it gets its own line under it
            Node extra = row().align(CENTER).justify(Node.END);
            extra.mt = 8;
            extra.add(dropButton(m, i, pk));
            p.add(extra);
        }
        return p;
    }

    /**
     * The way back out of a skill: back to level 0 for a flat fee.
     *
     * <p>Two clicks, like buying. This does not give anything back - it frees one of the three maxed
     * slots - so confirming it once is the difference between changing your build and losing
     * everything you put into it by brushing the mouse.</p>
     */
    static Node dropButton(Model m, int i, Perk pk) {
        if (pk.level <= 0) {
            return txt("", K(400), 13, LH, Tok.FAINT);
        }
        String key = "drop:" + i;
        boolean confirming = m.dropping == i;
        boolean afford = m.money >= m.dropCost;
        if (!afford && !confirming) {
            Node no = row().align(CENTER).gap(5);
            no.add(txt("الحذف", K(400), 11.5F, LH, Tok.FAINT));
            no.add(num(Chrome.fmt(m.dropCost), 13, LH, Tok.FAINT));
            return no;
        }
        Node b = Css.btn(confirming ? "أكّد الحذف" : "احذف · " + Chrome.fmt(m.dropCost),
                confirming ? Css.BTN_CONFIRM : Css.BTN_GHOST, true, false).hit(key, i);
        if (key.equals(m.hover)) {
            QuestsView.brighten(b);
        }
        return b;
    }

    /** A card for a perk that is announced but not built. Drawn dim, and nothing on it is clickable. */
    static Node soonCard(Perk pk) {
        Node p = panel(col()).gap(10).pad(16).tag("perk");
        p.borderColor = Tok.LINE_SOFT;
        Node top = row().align(CENTER).gap(12);
        Node pi = row().align(CENTER).justify(CENTER).size(44, 44).shrink(0)
                .border(1.0F, Tok.LINE_SOFT).bg(Tok.rgb(0x111409));
        pi.add(Css.icon(pk.icon, 18, 18, Tok.FAINT));
        top.add(pi);
        Node names = block();
        names.add(txt(pk.name, K(700), 15, 1.4F, Tok.MUTED));
        names.add(txt("مو متاحة بعد", K(400), 11.5F, LH, Tok.FAINT));
        top.add(names);
        p.add(top);
        p.add(txt(pk.description, K(400), 12, 1.8F, Tok.FAINT).wrapText());
        Node foot = row().align(CENTER).justify(Node.BETWEEN).gap(8).border(1, 0, 0, 0, Tok.LINE_SOFT);
        foot.mt = AUTO;
        foot.pt = 10;
        foot.add(Css.chip("قادم قريباً", Css.CHIP_MUTE));
        p.add(foot);
        p.opacity(0.75F);
        return p;
    }

    /** An effect label: {@code {n}} marks the number part, drawn in the pixel face at 14px. */
    static Node effect(String text, int color) {
        Node t = inline(K(400), 11, 1.3F, color);
        String s = text == null ? "" : text;
        int i = 0;
        while (i < s.length()) {
            int a = s.indexOf("{n}", i);
            if (a < 0) {
                t.span(sp(s.substring(i), K(400), 11, 1.3F, color));
                break;
            }
            if (a > i) {
                t.span(sp(s.substring(i, a), K(400), 11, 1.3F, color));
            }
            int b = s.indexOf("{/n}", a);
            if (b < 0) {
                b = s.length();
            }
            Span n = nsp(s.substring(a + 3, b), 14, 1.3F, color);
            t.span(n);
            i = Math.min(s.length(), b + 4);
        }
        return t;
    }
}

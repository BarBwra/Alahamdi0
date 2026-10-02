package com.barbwra.mlum.client.ui.view;

import com.barbwra.mlum.client.ClientRanks;
import com.barbwra.mlum.client.ui.Draw;
import com.barbwra.mlum.client.ui.Tok;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.rank.Rank;

import java.util.List;

import static com.barbwra.mlum.client.ui.layout.Node.AUTO;
import static com.barbwra.mlum.client.ui.layout.Node.CENTER;
import static com.barbwra.mlum.client.ui.view.Css.K;
import static com.barbwra.mlum.client.ui.view.Css.LH;
import static com.barbwra.mlum.client.ui.view.Css.block;
import static com.barbwra.mlum.client.ui.view.Css.col;
import static com.barbwra.mlum.client.ui.view.Css.grid;
import static com.barbwra.mlum.client.ui.view.Css.num;
import static com.barbwra.mlum.client.ui.view.Css.row;
import static com.barbwra.mlum.client.ui.view.Css.txt;

/**
 * The store: the rank ladder, the money packs, and one rank looked at closely.
 *
 * <h2>A window, not a till</h2>
 * <p>Nothing here has a Buy button, and that is the design rather than an omission. Ranks and money
 * packs are sold outside the game; what the player needs in here is to see what exists, what it
 * costs and what it does - and then where to go. A button that took payment would be a mod pretending
 * to be a shop.</p>
 *
 * <h2>Why the detail view splits the way it does</h2>
 * <p>The badge on one side and the words on the other, because they answer different questions and
 * the player is asking one at a time: "which one is this" is recognised, "what do I get" is read.
 * Stacking them would make the badge decoration above a wall of text.</p>
 */
public final class StoreView {

    private StoreView() {
    }

    /** Which list the store is showing. */
    public static final int RANKS = 0;
    public static final int MONEY = 1;

    public static final class Model {
        public int tab = RANKS;
        /** Index into the ladder being looked at closely, or -1 for the list. */
        public int detail = -1;
        public int held = -1;
        public String hover;
        public List<Rank> ranks = List.of();
        public List<ClientRanks.Pack> packs = List.of();
        public String note = "";
    }

    /* ================================================================== the dialog */

    public static Node build(Model m) {
        String title = m.detail >= 0 && m.detail < m.ranks.size()
                ? m.ranks.get(m.detail).name()
                : m.tab == MONEY ? "الفلوس" : "الرتب";
        Node body = m.detail >= 0 && m.detail < m.ranks.size()
                ? detail(m, m.ranks.get(m.detail))
                : list(m);
        return Overlays.modal(title, body, m.hover);
    }

    /* ---- the list ---- */

    static Node list(Model m) {
        Node b = col().gap(12);
        b.add(tabs(m));
        Node rows = col().gap(6).maxH(300).clip();
        if (m.tab == MONEY) {
            if (m.packs.isEmpty()) {
                rows.add(txt("ما فيه باقات فلوس معروضة حالياً.", K(400), 12, LH, Tok.MUTED));
            }
            for (int i = 0; i < m.packs.size(); i++) {
                rows.add(packRow(m, i));
            }
        } else {
            if (m.ranks.isEmpty()) {
                rows.add(txt("ما فيه رتب معرّفة في السيرفر.", K(400), 12, LH, Tok.MUTED));
            }
            for (int i = 0; i < m.ranks.size(); i++) {
                rows.add(rankRow(m, i));
            }
        }
        b.add(rows);
        if (m.note != null && !m.note.isEmpty()) {
            Node n = txt(m.note, K(400), 11.5F, LH, Tok.FAINT).wrapText();
            n.mt = 2;
            b.add(n);
        }
        return b;
    }

    /** The two headings. Money is hidden entirely when nothing is on offer. */
    static Node tabs(Model m) {
        Node t = row().gap(8);
        t.add(tab(m, RANKS, "الرتب"));
        if (!m.packs.isEmpty()) {
            t.add(tab(m, MONEY, "الفلوس"));
        }
        return t;
    }

    static Node tab(Model m, int which, String label) {
        boolean on = m.tab == which;
        String key = "store-tab:" + which;
        Node n = row().align(CENTER).h(28).pad(0, 12, 0, 12)
                .border(1.0F, on ? Tok.AMBER : Tok.LINE)
                .bg(on ? Tok.AMBER_GLOW : Tok.rgba(0x0a0c09, 0.6));
        n.add(txt(label, K(600), 12.5F, 1.0F, on ? Tok.AMBER : key.equals(m.hover) ? Tok.BONE : Tok.MUTED));
        n.hit(key, which);
        return n;
    }

    /**
     * One rank. The badge is a swatch in its own colour, so the row reads as the rank before the
     * name is read - which is how a rank is recognised in chat and on a nameplate too.
     */
    static Node rankRow(Model m, int i) {
        Rank r = m.ranks.get(i);
        String key = "rank-row:" + i;
        boolean hover = key.equals(m.hover);
        boolean mine = i == m.held;
        int color = 0xFF000000 | r.color();
        Node row = grid(34, -1, 0).gap(12).align(CENTER).pad(8, 12, 8, 12)
                .bg(mine ? Tok.rgba(r.color(), 0.10) : Tok.CARD)
                .border(1.0F, hover ? color : mine ? Tok.mix(color, Tok.LINE, 0.5) : Tok.LINE_SOFT)
                .hit(key, i);
        row.add(badge(r, 34));

        Node text = block().minW(0);
        Node top = Css.row().align(CENTER).gap(8);
        top.add(Css.px(r.name(), 700, 17, 1.0F, 0.04F, color));
        if (mine) {
            top.add(Css.chip("رتبتك", Css.CHIP_SAGE));
        }
        text.add(top);
        if (!r.blurb().isEmpty()) {
            Node blurb = txt(r.blurb(), K(400), 11.5F, LH, Tok.MUTED).wrapText();
            blurb.mt = 2;
            text.add(blurb);
        }
        row.add(text);

        Node right = col().align(Node.END).gap(2);
        if (!r.price().isEmpty()) {
            right.add(Css.px(r.price(), 600, 15, 1.0F, 0.02F, Tok.BONE));
        }
        right.add(txt("التفاصيل ›", K(400), 11, LH, hover ? Tok.AMBER : Tok.FAINT));
        row.add(right);
        return row;
    }

    static Node packRow(Model m, int i) {
        ClientRanks.Pack p = m.packs.get(i);
        Node row = grid(0, -1, 0).gap(12).align(CENTER).pad(8, 12, 8, 12)
                .bg(Tok.CARD).border(1.0F, Tok.LINE_SOFT);
        row.add(InvView.cash());
        Node text = block().minW(0);
        text.add(Css.px(p.label(), 600, 16, 1.0F, 0.03F, Tok.BONE));
        Node amount = Css.row().align(CENTER).gap(5);
        amount.mt = 2;
        amount.add(num(Chrome.fmt(p.amount()), 15, LH, Tok.CASH));
        amount.add(txt("في المحفظة", K(400), 11, LH, Tok.MUTED));
        text.add(amount);
        row.add(text);
        row.add(Css.px(p.price(), 600, 15, 1.0F, 0.02F, Tok.BONE));
        return row;
    }

    /* ---- one rank, closely ---- */

    static Node detail(Model m, Rank r) {
        int color = 0xFF000000 | r.color();
        Node b = col().gap(14);

        Node split = grid(180, -1).gap(18).align(Node.START);
        Node left = col().align(CENTER).gap(10);
        left.add(badge(r, 120));
        if (!r.price().isEmpty()) {
            Node price = Css.row().align(CENTER).pad(5, 12, 5, 12).border(1.0F, Tok.mix(color, Tok.LINE, 0.5))
                    .bg(Tok.rgba(r.color(), 0.08));
            price.add(Css.px(r.price(), 700, 17, 1.0F, 0.03F, color));
            left.add(price);
        }
        if (m.detail == m.held) {
            left.add(Css.chip("رتبتك الحالية", Css.CHIP_SAGE));
        }
        split.add(left);

        Node right = block().minW(0);
        if (!r.blurb().isEmpty()) {
            right.add(txt(r.blurb(), K(400), 13, 1.8F, Tok.SOFT).wrapText());
        }
        if (r.perks().isEmpty()) {
            Node none = txt("ما فيه تفاصيل مكتوبة لهذي الرتبة بعد.", K(400), 12, LH, Tok.FAINT);
            none.mt = 8;
            right.add(none);
        } else {
            Node perks = col().gap(7);
            perks.mt = 10;
            for (String perk : r.perks()) {
                perks.add(perkRow(perk, color));
            }
            right.add(perks);
        }
        split.add(right);
        b.add(split);

        Node foot = row().gap(8);
        foot.mt = 2;
        Node back = Css.btn("‹ رجوع", Css.BTN_GHOST, false, false).hit("store-back");
        if ("store-back".equals(m.hover)) {
            QuestsView.brighten(back);
        }
        foot.add(back);
        b.add(foot);
        return b;
    }

    /** One bullet: a square in the rank's colour, then the line. */
    static Node perkRow(String text, int color) {
        Node r = row().align(Node.START).gap(9);
        Node dot = Css.leaf(6, 6).bg(color);
        dot.mt = 5;
        r.add(dot);
        r.add(txt(text, K(400), 12.5F, 1.65F, Tok.BONE).wrapText().shrink(1).minW(0));
        return r;
    }

    /**
     * The rank's badge: its initials in its own colour, framed, with a soft glow behind.
     *
     * <p>Generated rather than a texture on purpose. A server adds and renames ranks in the config
     * and would otherwise need artwork commissioned for each one before it could be shown at all -
     * this way a rank exists the moment it is written down, and looks like it belongs.</p>
     */
    static Node badge(Rank r, float size) {
        int color = 0xFF000000 | r.color();
        Node b = Css.leaf(size, size).border(1.0F, Tok.mix(color, Tok.LINE, 0.6))
                .bg(Tok.rgba(r.color(), 0.10));
        final String mark = initials(r.name());
        final float fs = size * 0.42F;
        b.under((c, n) -> {
            // a radial bloom in the rank's colour, so higher ranks read as brighter
            float rx = n.w * 0.62F;
            float ry = n.h * 0.62F;
            Draw.rasterClipped(c, com.barbwra.mlum.client.ui.Art.radial(0.72F),
                    n.x + n.w / 2 - rx, n.y + n.h / 2 - ry, rx * 2, ry * 2,
                    n.x, n.y, n.x + n.w, n.y + n.h, Draw.rgba(r.color(), 0.22F));
        });
        b.over((c, n) -> {
            com.barbwra.mlum.client.ui.text.Shaped s =
                    com.barbwra.mlum.client.ui.text.TextEngine.shape(mark, Css.P(700), fs, fs * 0.04F, false);
            float[] ab = com.barbwra.mlum.client.ui.layout.Layout.inlineBox(Css.P(700), fs, fs);
            Draw.text(c, s, n.x + (n.w - s.width) / 2.0F, n.y + (n.h - fs) / 2.0F + ab[0], color);
            // a rule along the bottom, the same language the rarity line uses on an item
            Draw.rect(c, n.x + n.w * 0.2F, n.y + n.h - 2, n.w * 0.6F, 2, color);
        });
        return b;
    }

    /** "MVP+" stays "MVP+"; "Co-Owner" becomes "CO". Long names would not fit the square. */
    static String initials(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "?";
        }
        if (trimmed.length() <= 4) {
            return trimmed.toUpperCase(java.util.Locale.ROOT);
        }
        String[] words = trimmed.split("[\\s_\\-]+");
        if (words.length > 1) {
            return (words[0].charAt(0) + "" + words[1].charAt(0)).toUpperCase(java.util.Locale.ROOT);
        }
        return trimmed.substring(0, 2).toUpperCase(java.util.Locale.ROOT);
    }
}

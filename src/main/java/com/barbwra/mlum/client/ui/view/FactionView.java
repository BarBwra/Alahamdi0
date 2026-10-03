package com.barbwra.mlum.client.ui.view;

import com.barbwra.mlum.client.ui.Draw;
import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.Tok;
import com.barbwra.mlum.client.ui.layout.Layout;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.client.ui.layout.Span;

import java.util.ArrayList;
import java.util.List;

import static com.barbwra.mlum.client.ui.layout.Node.AUTO;
import static com.barbwra.mlum.client.ui.layout.Node.CENTER;
import static com.barbwra.mlum.client.ui.layout.Node.START;
import static com.barbwra.mlum.client.ui.view.Css.K;
import static com.barbwra.mlum.client.ui.view.Css.LH;
import static com.barbwra.mlum.client.ui.view.Css.P;
import static com.barbwra.mlum.client.ui.view.Css.aside;
import static com.barbwra.mlum.client.ui.view.Css.asideNum;
import static com.barbwra.mlum.client.ui.view.Css.asideText;
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

/** The faction tab: its five sub-pages, or the create / invites page for a player without one. */
public final class FactionView {

    private FactionView() {
    }

    public static final String[] SUB_IDS = {"main", "members", "levels", "board", "settings"};
    public static final String[] SUB_NAMES = {"الرئيسية", "الأعضاء", "المستويات", "المتصدرون", "الإعدادات"};

    public static final class Member {
        public String id = "";
        public String name = "";
        public String role = "";
        /** 0 leader, 1 deputy, 2 officer, 3 member, 4 guest. */
        public int rank = 3;
        public int level;
        public long points;
        public long donated;
        public boolean online;
        public boolean me;
    }

    public static final class Standing {
        public String name = "";
        public int level;
        public long points;
        public long bank;
        public int members;
        public boolean mine;
    }

    public static final class LevelCard {
        public int level;
        public int rows;
        public int pages;
        public long cost;
    }

    public static final class Invite {
        public String faction = "";
        public String from = "";
        public String when = "";
    }

    public static final class Model {
        public boolean inFaction;
        public int sub;
        public String name = "";
        public String leader = "";
        public String founded = "";
        public int level;
        public long points;
        public long floor;
        public long next;
        public boolean maxLevel;
        public long bank;
        public int rows;
        public int pages;
        public int rank;
        public int factionCount;
        public final List<Member> members = new ArrayList<>();
        public final List<Standing> board = new ArrayList<>();
        public final List<LevelCard> levels = new ArrayList<>();
        public final List<Invite> invites = new ArrayList<>();
        public long createCost;
        public long money;
        public boolean canInvite;
        public boolean leaderMe;
        /** Text fields, their contents and which one has focus. */
        public String createName = "";
        public String renameText = "";
        public String disbandText = "";
        public String focus;
        public boolean caretOn;
        public String error;
        public String errorField;
        public String hover;
        public int boardScroll;
        public int membersScroll;
    }

    public static Node build(Model m) {
        Node main = Chrome.main();
        float contentH = Px.H - 64 - 36 - 32;
        Node fac = col().gap(14).h(contentH).tag("fac");
        if (!m.inFaction) {
            fac.add(none(m, contentH));
        } else {
            Node subs = row().gap(6).tag("subtabs");
            for (int i = 0; i < SUB_IDS.length; i++) {
                subs.add(subtab(m, i));
            }
            fac.add(subs);
            Node body = block().minH(0).tag("fac-body");
            Css.flex1(body);
            Node page = switch (m.sub) {
                case 1 -> members(m);
                case 2 -> levels(m);
                case 3 -> board(m, true);
                case 4 -> settings(m);
                default -> overview(m);
            };
            body.add(page);
            fac.add(body);
        }
        main.add(fac);
        return main;
    }

    static Node subtab(Model m, int i) {
        boolean sel = i == m.sub;
        String key = "fsub:" + i;
        boolean hover = key.equals(m.hover);
        int color = sel ? Tok.AMBER : hover ? Tok.BONE : Tok.MUTED;
        Node t = row().align(CENTER).h(34).pad(0, 16, 0, 16).border(1.0F, sel ? Tok.AMBER : Tok.LINE)
                .bg(sel ? Tok.AMBER_GLOW : Tok.rgba(0x0a0c09, 0.7)).hit(key, i);
        t.add(txt(SUB_NAMES[i], K(600), 13, 1.0F, color));
        return t;
    }

    /* ================================================================== overview */

    static Node overview(Model m) {
        Node g = grid(-1, -1).rows(0, 0, -1).gap(14).tag("fmain");
        g.height = Px.H - 64 - 36 - 32 - 34 - 14;
        Node head = panel(grid(-1, 440)).gap(32).align(CENTER).pad(16, 22, 16, 22).spanCols(-1).tag("fhead");
        Node left = block();
        left.add(txt(m.name, K(700), 30, 1.4F, Tok.AMBER));
        Node chips = row().gap(6).wrap();
        chips.mt = 6;
        chips.add(Css.chip("القائد: " + m.leader, Css.CHIP_RUST));
        chips.add(Css.chip(m.founded, Css.CHIP_MUTE));
        int online = 0;
        for (Member mb : m.members) {
            if (mb.online) {
                online++;
            }
        }
        Node on = Css.chipRow(Tok.SAGE, Tok.rgba(0x93c46f, 0.1), Tok.SAGE);
        on.add(num(String.valueOf(online), 11, 1.0F, Tok.SAGE));
        on.add(txt("متصلين الحين", K(600), 11, 1.0F, Tok.SAGE));
        chips.add(on);
        left.add(chips);
        head.add(left);
        Node flv = block();
        Node top = row().align(Node.BASELINE).justify(Node.BETWEEN);
        top.mb = 10;
        Node from = inline(K(400), 14, LH, Tok.SOFT);
        if (m.maxLevel) {
            from.span(sp("أعلى مستوى ", K(400), 14, LH, Tok.SOFT));
            from.span(nsp(String.valueOf(m.level), 14, LH, Tok.SOFT));
        } else {
            from.span(sp("من المستوى ", K(400), 14, LH, Tok.SOFT));
            from.span(nsp(String.valueOf(m.level), 14, LH, Tok.SOFT));
            from.span(sp(" إلى ", K(400), 14, LH, Tok.SOFT));
            from.span(nsp(String.valueOf(m.level + 1), 14, LH, Tok.SOFT));
        }
        top.add(from);
        Node frac = inline(P(600), 22, LH, Tok.MUTED);
        frac.rtl = false;
        frac.span(nsp(Chrome.fmt(m.points), 22, LH, Tok.BONE));
        frac.span(nsp(" / " + Chrome.fmt(m.next), 22, LH, Tok.MUTED));
        top.add(frac);
        flv.add(top);
        float pct = m.next <= m.floor ? 1.0F : Math.max(0, (m.points - m.floor) / (float) (m.next - m.floor));
        flv.add(Css.meter(10, pct, Tok.rgb(0x1f231b), Tok.AMBER));
        Node left2 = inline(K(400), 12, LH, Tok.MUTED);
        left2.mt = 8;
        if (m.maxLevel) {
            left2.span(sp("وصلتوا أعلى مستوى", K(400), 12, LH, Tok.MUTED));
        } else {
            left2.span(sp("باقي ", K(400), 12, LH, Tok.MUTED));
            left2.span(nsp(Chrome.fmt(Math.max(0, m.next - m.points)), 16, LH, Tok.AMBER));
            left2.span(sp(" نقطة للمستوى الجاي", K(400), 12, LH, Tok.MUTED));
        }
        flv.add(left2);
        head.add(flv);
        g.add(head);

        Node tiles = grid(-1, -1, -1, -1).gap(14).spanCols(-1).tag("ftiles");
        tiles.add(tile("النقاط", Chrome.fmt(m.points), null, subText("مجموع نقاط كل الأعضاء", null), null));
        Node donate = Css.btn("تبرّع", Css.BTN, true, false).hit("donate");
        if ("donate".equals(m.hover)) {
            QuestsView.brighten(donate);
        }
        tiles.add(tile("الخزينة", Chrome.fmt(m.bank), "فلوس", subText("تبرعات الأعضاء", null), donate));
        Node diplomacy = Css.btn("الدبلوماسية", Css.BTN_GHOST, true, false).hit("diplomacy");
        if ("diplomacy".equals(m.hover)) {
            QuestsView.brighten(diplomacy);
        }
        tiles.add(tile("الأعضاء", String.valueOf(m.members.size()), "أعضاء", subText("متصلين الحين ", String.valueOf(online)), diplomacy));
        Node vault = inline(K(400), 11.5F, LH, Tok.FAINT);
        vault.span(sp("موزعة على ", K(400), 11.5F, LH, Tok.FAINT));
        vault.span(nsp(String.valueOf(m.pages), 14, LH, Tok.MUTED));
        vault.span(sp(" صفحات · ", K(400), 11.5F, LH, Tok.FAINT));
        Node openVault = Css.tool("افتح الخزنة", null, 24, 8).hit("open-vault");
        if ("open-vault".equals(m.hover)) {
            openVault.borderColor = Tok.AMBER_DIM;
            InvView.recolor(openVault, Tok.BONE);
        }
        vault.span(Span.box(openVault, 0));
        tiles.add(tile("المخزن", String.valueOf(m.rows), "صف", vault, null));
        g.add(tiles);

        Node contrib = panel(block()).tag("contrib");
        contrib.add(phead("أفضل المساهمين", aside().add(asideText("حسب النقاط"))));
        List<Member> top3 = new ArrayList<>(m.members);
        top3.sort((a, b) -> Long.compare(b.points, a.points));
        long maxp = top3.isEmpty() ? 1 : Math.max(1, top3.get(0).points);
        Node list = col().gap(6);
        for (int i = 0; i < Math.min(4, top3.size()); i++) {
            Member mb = top3.get(i);
            Node r = grid(26, -1, 140, 70).gap(12).align(CENTER).pad(8, 10, 8, 10).bg(Tok.CARD).border(1.0F, Tok.LINE_SOFT);
            Node rn = Css.px(String.valueOf(i + 1), 600, 18, 1.0F, 0, Tok.MUTED);
            rn.textAlign = CENTER;
            r.add(rn);
            Node who = row().align(CENTER).gap(8);
            who.add(Css.px(mb.name, 600, 18, 1.0F, 0.03F, Tok.BONE));
            if (mb.rank <= 1) {
                who.add(Css.chip(mb.role, mb.rank == 0 ? Css.CHIP_RUST : Css.CHIP_AMBER));
            }
            r.add(who);
            r.add(QuestsView.mini(0, mb.points / (float) maxp, Tok.AMBER));
            Node v = num(Chrome.fmt(mb.points), 17, LH, Tok.BONE);
            r.add(v);
            list.add(r);
        }
        contrib.add(list);
        g.add(contrib);

        Node rank = panel(col()).align(CENTER).justify(CENTER).gap(8).tag("rankcard");
        rank.add(txt("ترتيبكم في السيرفر", K(400), 12, LH, Tok.MUTED));
        Node big = num("#" + m.rank, 104, 0.8F, Tok.AMBER);
        big.spans.get(0).glow(30, Draw.rgba(0xF0A93B, 0.2F));
        rank.add(big);
        Node pp = inline(K(400), 13, LH, Tok.SOFT);
        pp.textAlign = CENTER;
        Standing second = null;
        Standing first = null;
        for (Standing s : m.board) {
            if (first == null) {
                first = s;
            } else if (second == null) {
                second = s;
            }
        }
        if (m.rank == 1 && second != null) {
            pp.span(sp("متقدمين على ", K(400), 13, LH, Tok.SOFT));
            pp.span(sp(second.name, K(700), 13, LH, Tok.SOFT));
            pp.span(sp(" بـ ", K(400), 13, LH, Tok.SOFT));
            pp.span(nsp(Chrome.fmt(Math.max(0, m.points - second.points)), 17, LH, Tok.SAGE));
            pp.span(sp(" نقطة", K(400), 13, LH, Tok.SOFT));
        } else if (m.rank > 1 && first != null) {
            pp.span(sp("ورا ", K(400), 13, LH, Tok.SOFT));
            pp.span(sp(first.name, K(700), 13, LH, Tok.SOFT));
            pp.span(sp(" بـ ", K(400), 13, LH, Tok.SOFT));
            pp.span(nsp(Chrome.fmt(Math.max(0, first.points - m.points)), 17, LH, Tok.RUST));
            pp.span(sp(" نقطة", K(400), 13, LH, Tok.SOFT));
        } else {
            pp.span(sp("أنتم المتصدرون", K(400), 13, LH, Tok.SOFT));
        }
        rank.add(pp);
        Node see = Css.btn("شوف المتصدرين", Css.BTN_GHOST, true, false).hit("fsub:3", 3);
        rank.add(see);
        g.add(rank);
        return g;
    }

    /** {@code .tile}: label (+ action), a big number with its unit, a faint line under it. */
    static Node tile(String label, String value, String unit, Node sub, Node action) {
        Node t = panel(col()).gap(4).pad(12, 16, 14, 16);
        Node top = row().align(CENTER).justify(Node.BETWEEN).minH(30);
        top.add(txt(label, K(400), 12, LH, Tok.MUTED));
        if (action != null) {
            top.add(action);
        }
        t.add(top);
        Node tv = inline(K(400), 13, LH, Tok.MUTED);
        tv.span(nsp(value, 36, 1.05F, Tok.BONE));
        if (unit != null) {
            // margin-inline-end:6px on the number
            tv.span(Span.box(Css.leaf(6, 0).w(6), 0));
            tv.span(sp(unit, K(400), 13, LH, Tok.MUTED));
        }
        t.add(tv);
        t.add(sub);
        return t;
    }

    static Node subText(String text, String number) {
        Node s = inline(K(400), 11.5F, LH, Tok.FAINT);
        s.span(sp(text, K(400), 11.5F, LH, Tok.FAINT));
        if (number != null) {
            s.span(nsp(number, 14, LH, Tok.MUTED));
        }
        return s;
    }

    /* ================================================================== tables */

    /** One table cell. */
    static final class Cell {
        final Node content;

        Cell(Node content) {
            this.content = content;
        }
    }

    /**
     * {@code table.t}: auto layout at 100% width - each column gets its widest cell, and the space
     * left over is shared in proportion to those widths, the way browsers size auto tables.
     */
    static Node table(float width, String[] heads, List<Node[]> rows, List<Boolean> me) {
        int cols = heads.length;
        List<Node[]> all = new ArrayList<>();
        Node[] headCells = new Node[cols];
        for (int c = 0; c < cols; c++) {
            // .t th{font:500 11.5px} - the shorthand resets line-height to normal (ascent + descent)
            float normal = K(500).ascent(11.5F) + K(500).descent(11.5F);
            headCells[c] = txt(heads[c], K(500), 11.5F, normal / 11.5F, Tok.MUTED);
        }
        all.add(headCells);
        all.addAll(rows);
        float[] max = new float[cols];
        for (Node[] r : all) {
            for (int c = 0; c < cols; c++) {
                max[c] = Math.max(max[c], Layout.prefW(r[c]) + 24);
            }
        }
        float sum = 0;
        for (float v : max) {
            sum += v;
        }
        float extra = Math.max(0, width - sum);
        float[] tracks = new float[cols];
        for (int c = 0; c < cols; c++) {
            tracks[c] = max[c] + (sum > 0 ? extra * max[c] / sum : 0);
        }
        Node t = col().w(width);
        Node hr = grid(tracks).border(0, 0, 1, 0, Tok.LINE);
        for (int c = 0; c < cols; c++) {
            // collapsed borders: the header row owns only half of the 1px line under it
            Node cell = block().pad(9, 12, 8.5F, 12);
            cell.add(headCells[c]);
            hr.add(cell);
        }
        t.add(hr);
        for (int i = 0; i < rows.size(); i++) {
            boolean isMe = me != null && i < me.size() && me.get(i);
            Node r = grid(tracks).align(CENTER).border(0, 0, 1, 0, Tok.LINE_SOFT);
            if (isMe) {
                r.bg(Tok.AMBER_GLOW);
            }
            for (int c = 0; c < cols; c++) {
                Node cell = row().align(CENTER).pad(11, 12, 11, 12);
                cell.add(rows.get(i)[c]);
                r.add(cell);
            }
            t.add(r);
        }
        return t;
    }

    static Node n(String v) {
        return Css.px(v, 600, 18, 1.0F, 0, Tok.BONE);
    }

    static Node td(String v) {
        return txt(v, K(400), 13, LH, Tok.BONE);
    }

    /* ================================================================== members */

    static float fpanelInner() {
        return Px.W - 48 - 30;
    }

    static Node members(Model m) {
        Node p = panel(block()).tag("fpanel");
        p.height = Px.H - 64 - 36 - 32 - 34 - 14;
        Node aside = aside();
        if (m.canInvite) {
            Node inv = Css.btn("دعوة لاعب", Css.BTN, true, false).hit("invite");
            if ("invite".equals(m.hover)) {
                QuestsView.brighten(inv);
            }
            aside.add(inv);
        }
        p.add(phead("الأعضاء", aside));
        List<Node[]> rows = new ArrayList<>();
        List<Boolean> me = new ArrayList<>();
        int shown = 0;
        for (int i = m.membersScroll; i < m.members.size() && shown < 9; i++, shown++) {
            Member mb = m.members.get(i);
            Node st = row().align(CENTER).gap(6);
            st.add(Css.bar(7, 7, mb.online ? Tok.SAGE : Tok.FAINT));
            st.add(txt(mb.online ? "متصل" : "غير متصل", K(400), 12, LH, mb.online ? Tok.SAGE : Tok.MUTED));
            Node name = Css.px(mb.name, 600, 18, 1.0F, 0.03F, Tok.BONE).hit("member:" + i, i);
            rows.add(new Node[]{name, td(mb.role), n(mb.level > 0 ? String.valueOf(mb.level) : "-"),
                    n(Chrome.fmt(mb.points)), n(Chrome.fmt(mb.donated)), st});
            me.add(mb.me);
        }
        Node table = table(fpanelInner(), new String[]{"الاسم", "الدور", "المستوى", "النقاط", "التبرعات", "الحالة"},
                rows, me);
        tagRows(table, "member:", m.membersScroll);
        p.add(table);
        Node hint = txt("اضغط على أي عضو عشان تشوف ملفه، وترقيه أو تنزله أو تطرده.", K(400), 11.5F, LH, Tok.FAINT);
        hint.mt = 12;
        p.add(hint);
        return p;
    }

    /** Makes every body row of a table clickable. */
    static void tagRows(Node table, String prefix, int offset) {
        for (int i = 1; i < table.kids.size(); i++) {
            table.kids.get(i).hit(prefix + (offset + i - 1), offset + i - 1);
        }
    }

    /* ================================================================== levels */

    static Node levels(Model m) {
        Node p = panel(block()).tag("fpanel");
        p.height = Px.H - 64 - 36 - 32 - 34 - 14;
        Node aside = aside();
        aside.add(asideText("كل مستوى يكلف"));
        aside.add(asideNum("10,000"));
        aside.add(asideText("نقطة ويضيف"));
        aside.add(asideNum("3"));
        aside.add(asideText("صفوف للخزنة"));
        p.add(phead("مستويات المنظمة", aside));
        Node g = grid(-1, -1, -1, -1, -1, -1).gap(12);
        for (LevelCard lc : m.levels) {
            int l = lc.level;
            String st = l < m.level ? "done" : l == m.level ? "cur" : l == m.level + 1 ? "next" : "lock";
            Node fl = col().gap(8).pad(14).border(1.0F, st.equals("cur") ? Tok.AMBER : Tok.LINE).bg(Tok.CARD);
            if (st.equals("cur")) {
                fl.vgrad(new float[]{0, 1}, new int[]{Tok.AMBER_GLOW, Tok.CARD});
                fl.bg(0);
            }
            if (st.equals("lock")) {
                fl.opacity(0.55F);
            }
            Node h = row().align(CENTER).justify(Node.BETWEEN).gap(6);
            Node lv = inline(K(400), 13, LH, Tok.SOFT);
            lv.span(sp("المستوى ", K(400), 13, LH, Tok.SOFT));
            lv.span(nsp(String.valueOf(l), 13, LH, Tok.SOFT));
            h.add(lv);
            Node chip = switch (st) {
                case "done" -> Css.chip("مفتوح", Css.CHIP_SAGE);
                case "cur" -> Css.chip("مستواكم", Css.CHIP_AMBER);
                case "next" -> Css.chip("الجاي", Css.CHIP_MUTE);
                default -> {
                    Node c = Css.chipRow(Tok.MUTED, 0, Tok.LINE);
                    c.add(Css.icon("lock", 8, 8, Tok.MUTED));
                    c.add(txt("مقفل", K(600), 11, 1.0F, Tok.MUTED));
                    yield c;
                }
            };
            h.add(chip);
            fl.add(h);
            Node big = block();
            big.mt = 6;
            big.add(num(String.valueOf(lc.rows), 40, 1.0F, st.equals("done") ? Tok.SAGE : Tok.BONE));
            big.add(txt("صف في الخزنة", K(400), 12, LH, Tok.MUTED));
            fl.add(big);
            Node meta = inline(K(400), 11.5F, LH, Tok.MUTED);
            meta.span(sp("الصفحات ", K(400), 11.5F, LH, Tok.MUTED));
            meta.span(nsp(String.valueOf(lc.pages), 11.5F, LH, Tok.MUTED));
            meta.span(sp(" · ", K(400), 11.5F, LH, Tok.MUTED));
            meta.span(nsp("+3", 11.5F, LH, Tok.MUTED));
            meta.span(sp(" صفوف", K(400), 11.5F, LH, Tok.MUTED));
            fl.add(meta);
            if (st.equals("next")) {
                float pct = m.next <= m.floor ? 0 : Math.max(0.01F, (m.points - m.floor) / (float) (m.next - m.floor));
                fl.add(Css.meter(5, pct, Tok.rgb(0x1f231b), Tok.AMBER));
                Node left = inline(K(400), 11.5F, LH, Tok.MUTED);
                left.span(sp("باقي ", K(400), 11.5F, LH, Tok.MUTED));
                left.span(nsp(Chrome.fmt(Math.max(0, m.next - m.points)), 11.5F, LH, Tok.MUTED));
                left.span(sp(" نقطة", K(400), 11.5F, LH, Tok.MUTED));
                fl.add(left);
            }
            Node cost = inline(K(400), 11.5F, LH, Tok.MUTED).border(1, 0, 0, 0, Tok.LINE_SOFT);
            cost.mt = AUTO;
            cost.pt = 10;
            cost.span(nsp(Chrome.fmt(lc.cost), 17, LH, Tok.SOFT));
            cost.span(sp(" نقطة", K(400), 11.5F, LH, Tok.MUTED));
            fl.add(cost);
            g.add(fl);
        }
        p.add(g);
        return p;
    }

    /* ================================================================== board */

    static Node board(Model m, boolean fullHeight) {
        Node p = panel(block()).tag("fpanel");
        if (fullHeight) {
            p.height = Px.H - 64 - 36 - 32 - 34 - 14;
        }
        p.add(phead("أقوى المنظمات", aside().add(asideText("الترتيب: المستوى، بعدين النقاط، بعدين الخزينة"))));
        List<Node[]> rows = new ArrayList<>();
        List<Boolean> me = new ArrayList<>();
        for (int i = m.boardScroll; i < m.board.size() && rows.size() < 9; i++) {
            Standing s = m.board.get(i);
            boolean mine = s.mine && m.inFaction;
            Node rk = Css.px(String.valueOf(i + 1), 600, 18, 1.0F, 0, i < 3 ? Tok.AMBER : Tok.BONE);
            Node name;
            if (mine) {
                name = row().align(CENTER).gap(4);
                name.add(td(s.name));
                name.add(Css.chip("منظمتك", Css.CHIP_AMBER));
            } else {
                name = td(s.name);
            }
            rows.add(new Node[]{rk, name, n(String.valueOf(s.level)), n(Chrome.fmt(s.points)), n(Chrome.fmt(s.bank)),
                    n(String.valueOf(s.members))});
            me.add(mine);
        }
        float width = fullHeight ? fpanelInner() : Px.W - 48 - 456 - 22 - 30;
        p.add(table(width, new String[]{"#", "المنظمة", "المستوى", "النقاط", "الخزينة", "الأعضاء"}, rows, me));
        if (m.board.isEmpty()) {
            Node e = txt("ما فيه منظمات للحين.", K(400), 12.5F, LH, Tok.MUTED);
            e.mt = 12;
            p.add(e);
        }
        return p;
    }

    /* ================================================================== settings */

    static Node settings(Model m) {
        Node s = col().gap(12).maxW(900).tag("fset");
        s.add(srow("اسم المنظمة", paraNums("من ", "3", " إلى ", "24", " حرف. الأعضاء والخزينة ما يتأثرون."),
                field(m, "rename", m.renameText, "", "حفظ الاسم", Css.BTN, !m.leaderMe || m.renameText.trim().length() < 3,
                        "save-name"), false, m, "rename"));
        Node transfer = row().gap(8);
        Node pick = Css.btn("اختار عضو…", Css.BTN_GHOST, false, !m.leaderMe);
        if (m.leaderMe) {
            pick.hit("transfer");
        }
        transfer.add(pick);
        s.add(srow("نقل القيادة", para("العضو اللي تختاره يصير القائد، وأنت تصير النائب."), transfer, false, m, null));
        Node leave = Css.btn("مغادرة", Css.BTN_GHOST, false, m.leaderMe);
        if (!m.leaderMe) {
            leave.hit("leave");
        }
        Node leaveBox = block().add(leave);
        s.add(srow("مغادرة المنظمة", para(m.leaderMe ? "القائد ما يقدر يغادر. انقل القيادة أول." : "تطلع من المنظمة وتقدر تنضم لغيرها."),
                leaveBox, false, m, null));
        boolean match = m.disbandText.trim().equals(m.name) && m.leaderMe;
        s.add(srow("حل المنظمة نهائياً", para("يطرد كل الأعضاء ويحذف النقاط والخزينة، وكل اللي في الخزنة يضيع. اكتب اسم المنظمة للتأكيد."),
                field(m, "disband", m.disbandText, m.name, "حل المنظمة", Css.BTN_DANGER, !match, "dissolve"), true, m, "disband"));
        return s;
    }

    static Node para(String t) {
        return txt(t, K(400), 12, 1.8F, Tok.MUTED).wrapText();
    }

    static Node paraNums(String a, String n1, String b, String n2, String c) {
        Node p = inline(K(400), 12, 1.8F, Tok.MUTED).wrapText();
        p.span(sp(a, K(400), 12, 1.8F, Tok.MUTED));
        p.span(nsp(n1, 12, 1.8F, Tok.MUTED));
        p.span(sp(b, K(400), 12, 1.8F, Tok.MUTED));
        p.span(nsp(n2, 12, 1.8F, Tok.MUTED));
        p.span(sp(c, K(400), 12, 1.8F, Tok.MUTED));
        return p;
    }

    static Node srow(String title, Node desc, Node control, boolean danger, Model m, String errField) {
        Node r = panel(grid(-1, 380)).gap(24).align(CENTER).pad(16, 20, 16, 20);
        if (danger) {
            r.borderColor = Tok.rgba(0xe0613f, 0.5);
        }
        Node left = block();
        Node h4 = txt(title, K(600), 14, 1.5F, danger ? Tok.RUST : Tok.BONE);
        h4.mb = 4;
        left.add(h4);
        left.add(desc);
        r.add(left);
        Node right = block();
        right.add(control);
        if (m.error != null && errField != null && errField.equals(m.errorField)) {
            Node err = txt(m.error, K(400), 11.5F, LH, Tok.RUST);
            err.mt = 6;
            right.add(err);
        }
        r.add(right);
        return r;
    }

    /** {@code .field}: a text input and its button side by side. */
    static Node field(Model m, String id, String value, String placeholder, String label, int style, boolean disabled,
                      String action) {
        Node f = row().gap(8);
        f.add(Css.flex1(input(m, id, value, placeholder).minW(0)));
        Node b = Css.btn(label, style, false, disabled);
        if (!disabled) {
            b.hit(action);
            if (action.equals(m.hover)) {
                QuestsView.brighten(b);
            }
        }
        f.add(b);
        return f;
    }

    /** {@code .input}: 40px, dark fill, amber border while focused, caret at the end. */
    public static Node input(Model m, String id, String value, String placeholder) {
        return Overlays.input(id, value, placeholder, id.equals(m.focus), m.caretOn);
    }

    /* ================================================================== no faction */

    static Node none(Model m, float contentH) {
        Node g = grid(456, -1).gap(22).align(START).rows(-1).h(contentH).tag("fnone");
        Node left = col().gap(14);
        Node create = panel(block()).tag("fcreate");
        create.add(phead("إنشاء منظمة", null));
        Node h2 = txt("ابدأ منظمتك", K(700), 22, 1.5F, Tok.BONE);
        h2.mb = 4;
        create.add(h2);
        Node p = txt("اختار اسم، وادعِ أصحابك، واجمعوا نقاط عشان تكبر الخزنة وتطلعون في المتصدرين.", K(400), 12.5F, 1.8F, Tok.MUTED)
                .wrapText();
        p.mb = 14;
        create.add(p);
        boolean cantAfford = m.money < m.createCost;
        create.add(field(m, "create", m.createName, "اسم المنظمة (3 إلى 24 حرف)", "إنشاء", Css.BTN,
                m.createName.trim().length() < 3 || cantAfford, "create"));
        if (m.error != null && "create".equals(m.errorField)) {
            Node err = txt(m.error, K(400), 11.5F, LH, Tok.RUST);
            err.mt = 6;
            create.add(err);
        }
        Node cost = row().align(CENTER).gap(8);
        cost.mt = 12;
        cost.add(txt("التكلفة", K(400), 12, LH, Tok.MUTED));
        cost.add(InvView.cash());
        cost.add(num(Chrome.fmt(m.createCost), 18, LH, cantAfford ? Tok.RUST : Tok.BONE));
        cost.add(txt(cantAfford ? "فلوسك ما تكفي" : "تنخصم بس إذا انشأت", K(400), 12, LH, Tok.FAINT));
        create.add(cost);
        left.add(create);

        Node inv = panel(block()).tag("finvites");
        inv.add(phead("دعوات وصلتك", aside().add(asideNum(String.valueOf(m.invites.size())))));
        Node list = col().gap(6);
        for (int i = 0; i < m.invites.size(); i++) {
            Invite in = m.invites.get(i);
            Node r = grid(-1, 0).gap(12).align(CENTER).pad(10, 12, 10, 12).bg(Tok.CARD).border(1.0F, Tok.LINE_SOFT);
            Node who = block();
            who.add(txt(in.faction, K(700), 14, LH, Tok.BONE));
            Node from = inline(K(400), 11.5F, LH, Tok.MUTED);
            from.span(sp("دعوة من ", K(400), 11.5F, LH, Tok.MUTED));
            from.span(Span.text(in.from, K(400), 11.5F, 11.5F * LH, Tok.MUTED));
            if (in.when != null && !in.when.isEmpty()) {
                from.span(sp(" · " + in.when, K(400), 11.5F, LH, Tok.MUTED));
            }
            who.add(from);
            r.add(who);
            Node acts = row().gap(6);
            Node join = Css.btn("انضمام", Css.BTN, true, false).hit("join", i);
            Node reject = Css.btn("رفض", Css.BTN_GHOST, true, false).hit("reject", i);
            if ("join".equals(m.hover)) {
                QuestsView.brighten(join);
            }
            acts.add(join, reject);
            r.add(acts);
            list.add(r);
        }
        if (m.invites.isEmpty()) {
            list.add(txt("ما وصلتك دعوات. لما أحد يدعوك بتطلع هنا.", K(400), 12, LH, Tok.MUTED));
        }
        inv.add(list);
        left.add(inv);
        g.add(left);
        Node b = board(m, false);
        b.height = contentH;
        g.add(b);
        return g;
    }
}

package com.barbwra.mlum.client.admin;

import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.Tok;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.client.ui.mc.UiText;
import com.barbwra.mlum.client.ui.view.Chrome;
import com.barbwra.mlum.client.ui.view.Css;
import com.barbwra.mlum.client.ui.view.Item;
import com.barbwra.mlum.client.ui.view.Overlays;
import com.barbwra.mlum.client.ui.view.Slots;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.barbwra.mlum.client.ui.layout.Node.CENTER;
import static com.barbwra.mlum.client.ui.view.Css.K;
import static com.barbwra.mlum.client.ui.view.Css.LH;
import static com.barbwra.mlum.client.ui.view.Css.block;
import static com.barbwra.mlum.client.ui.view.Css.col;
import static com.barbwra.mlum.client.ui.view.Css.grid;
import static com.barbwra.mlum.client.ui.view.Css.panel;
import static com.barbwra.mlum.client.ui.view.Css.row;
import static com.barbwra.mlum.client.ui.view.Css.txt;

/**
 * The admin panel, page by page, in the bag's own look.
 *
 * <pre>
 *  [ لوحة الإدارة · رتبتك ]  [اللاعبين] [الرتب] [العقوبات] [التذاكر] [التنبيهات] [الجدولة]   [مخفي]
 *  ┌──────── the list ────────┐  ┌──── what you can do with the one picked ────┐
 *  │ ...                      │  │ ...                                          │
 * </pre>
 *
 * <p>Every page is drawn from the last tag the server sent for it; nothing is guessed. Buttons
 * the player has no permission for are not drawn at all.</p>
 */
public final class AdminView {

    private AdminView() {
    }

    public static final String[] SUBS = {"players", "ranks", "punish", "tickets", "alerts", "schedule"};
    public static final String[] SUB_NAMES = {"اللاعبين", "الرتب", "العقوبات", "التذاكر", "التنبيهات", "الجدولة"};
    public static final String[] SUB_PERM = {"panel", "*op", "punish", "tickets", "alerts", "schedule"};
    static final String[] TYPES = {"warn", "mute", "jail", "kick", "ban"};
    static final String[] TYPE_NAMES = {"إنذار", "كتم", "سجن", "طرد", "باند"};
    static final String[] DAYS = {"ن", "ث", "ر", "خ", "ج", "س", "ح"};

    public static final class Model {
        public int sub;
        public String hover;
        public String focus;
        public boolean caret;
        public Map<String, String> fields;
        public UUID selected;
        public String rank;
        public String ptype = "warn";
        public int days = 0x7F;
        public int scroll;
        public String confirm;
        public String filter = "";
    }

    public static boolean canSee(int sub) {
        String need = SUB_PERM[sub];
        return need.equals("*op") ? ClientAdmin.op() : ClientAdmin.has(need);
    }

    public static Node build(Model m) {
        Node main = Chrome.main();
        float contentH = Px.H - 64 - 36 - 32;
        Node page = col().gap(14).h(contentH).tag("admin");
        page.add(head(m));
        Node body = switch (SUBS[m.sub]) {
            case "players" -> players(m);
            case "ranks" -> ranks(m);
            case "punish" -> punish(m);
            case "tickets" -> tickets(m);
            case "alerts" -> alerts(m);
            default -> schedule(m);
        };
        Css.flex1(body).minH(0);
        page.add(body);
        main.add(page);
        return main;
    }

    /* ================================================================== pieces */

    static Node head(Model m) {
        Node p = panel(row()).align(CENTER).gap(14).pad(10, 16, 10, 16);
        Node t = row().align(CENTER).gap(8);
        t.add(Css.bar(3, 16, Tok.AMBER));
        t.add(txt("لوحة الإدارة", K(700), 16, 1.4F, Tok.BONE));
        int rc = 0xFF000000 | ClientAdmin.rankColor();
        t.add(Css.chip(UiText.logical(ClientAdmin.rank()), rc, Tok.mixTransparent(rc, 0.10), rc));
        p.add(t);
        Node tabs = row().align(CENTER).gap(6);
        Css.flex1(tabs);
        for (int i = 0; i < SUBS.length; i++) {
            if (!canSee(i)) {
                continue;
            }
            boolean on = i == m.sub;
            boolean hov = ("asub:" + i).equals(m.hover);
            Node tab = row().align(CENTER).h(30).pad(0, 14, 0, 14)
                    .border(1.0F, on ? Tok.AMBER : hov ? Tok.AMBER_DIM : Tok.LINE)
                    .bg(on ? Tok.AMBER_GLOW : 0).hit("asub:" + i, i);
            tab.add(txt(SUB_NAMES[i], K(600), 12.5F, 1.0F, on ? Tok.AMBER : hov ? Tok.BONE : Tok.MUTED));
            if (SUBS[i].equals("tickets")) {
                int open = openTickets();
                if (open > 0) {
                    tab.add(Css.chip(String.valueOf(open), Css.CHIP_RUST));
                }
            }
            tabs.add(tab);
        }
        p.add(tabs);
        if (ClientAdmin.has("vanish")) {
            CompoundTag pl = ClientAdmin.page("players");
            boolean vanished = pl != null && pl.getBoolean("Vanished");
            p.add(button(vanished ? "مخفي · اضغط عشان تبين" : "وضع الخفاء", vanished ? Css.BTN : Css.BTN_GHOST, "vanish", m));
        }
        return p;
    }

    static int openTickets() {
        CompoundTag t = ClientAdmin.page("tickets");
        if (t == null) {
            return 0;
        }
        int n = 0;
        ListTag list = t.getList("Tickets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            if (!list.getCompound(i).getBoolean("Closed")) {
                n++;
            }
        }
        return n;
    }

    static Node button(String label, int style, String id, Model m) {
        Node b = Css.btn(label, style, true, false).hit(id);
        if (id.equals(m.hover) && style == Css.BTN_GHOST) {
            b.bg(Tok.AMBER_GLOW);
        }
        if (id.equals(m.confirm)) {
            b.kids.clear();
            b.add(txt("متأكد؟ اضغط مرة ثانية", K(600), 12.5F, 1.0F, style == Css.BTN_GHOST ? Tok.AMBER : Tok.BTN_INK));
        }
        return b;
    }

    static Node toggle(String label, boolean on, String id, Model m) {
        boolean hov = id.equals(m.hover);
        Node n = row().align(CENTER).gap(5).pad(5, 8, 4, 8)
                .border(1.0F, on ? Tok.SAGE : hov ? Tok.AMBER_DIM : Tok.LINE)
                .bg(on ? Tok.rgba(0x93c46f, 0.12) : 0).hit(id);
        n.add(txt(label, K(600), 11.5F, 1.0F, on ? Tok.SAGE : hov ? Tok.BONE : Tok.MUTED));
        return n;
    }

    static Node input(Model m, String id, String placeholder, float width) {
        Node in = Overlays.input(id, m.fields.getOrDefault(id, ""), placeholder, id.equals(m.focus), m.caret, 32);
        if (width > 0) {
            in.w(width);
        } else {
            Css.flex1(in);
        }
        return in;
    }

    static Node label(String text) {
        return txt(text, K(400), 11.5F, LH, Tok.MUTED);
    }

    static Node empty(String text) {
        Node n = txt(text, K(400), 12.5F, LH, Tok.FAINT);
        n.mt = 10;
        return n;
    }

    static String ago(long at) {
        long minutes = Math.max(0L, (System.currentTimeMillis() - at) / 60_000L);
        if (minutes < 1L) {
            return "الحين";
        }
        if (minutes < 60L) {
            return "قبل " + minutes + " د";
        }
        long hours = minutes / 60L;
        return hours < 24L ? "قبل " + hours + " س" : "قبل " + (hours / 24L) + " يوم";
    }

    static List<CompoundTag> list(String page, String key) {
        List<CompoundTag> out = new ArrayList<>();
        CompoundTag t = ClientAdmin.page(page);
        if (t != null) {
            ListTag l = t.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                out.add(l.getCompound(i));
            }
        }
        return out;
    }

    /** A window of {@code rows} rows starting at the scroll, for lists longer than the panel. */
    static <T> List<T> window(List<T> all, int scroll, int rows) {
        int from = Math.max(0, Math.min(scroll, Math.max(0, all.size() - rows)));
        return all.subList(from, Math.min(all.size(), from + rows));
    }

    /* ================================================================== players */

    static Node players(Model m) {
        Node g = grid(-1, 470).gap(14).align(Node.STRETCH);
        List<CompoundTag> players = list("players", "Players");
        Node left = panel(col()).gap(4).minH(0).clip();
        left.add(Css.phead("اللاعبين المتصلين", Css.aside().add(Css.asideNum(String.valueOf(players.size())))));
        if (players.isEmpty()) {
            left.add(empty("ما فيه أحد متصل غيرك"));
        }
        int base = Math.max(0, Math.min(m.scroll, Math.max(0, players.size() - 12)));
        List<CompoundTag> shown = window(players, m.scroll, 12);
        for (int i = 0; i < shown.size(); i++) {
            CompoundTag p = shown.get(i);
            UUID id = p.getUUID("Id");
            boolean sel = id.equals(m.selected);
            String key = "pl:" + (base + i);
            boolean hov = key.equals(m.hover);
            Node r = row().align(CENTER).gap(8).h(34).pad(0, 10, 0, 10)
                    .border(1.0F, sel ? Tok.AMBER : hov ? Tok.AMBER_DIM : Tok.LINE_SOFT)
                    .bg(sel ? Tok.AMBER_GLOW : Tok.CARD).hit(key, base + i);
            r.add(Css.px(p.getString("Name"), 600, 15, 1.0F, 0.03F, Tok.BONE));
            String rank = p.getString("Rank");
            if (!rank.isEmpty()) {
                int rc = 0xFF000000 | p.getInt("Color");
                r.add(Css.chip(UiText.logical(rank), rc, Tok.mixTransparent(rc, 0.10), rc));
            }
            if (p.getBoolean("Vanished")) {
                r.add(Css.chip("مخفي", Css.CHIP_MUTE));
            }
            if (p.getBoolean("Muted")) {
                r.add(Css.chip("مكتوم", Css.CHIP_AMBER));
            }
            if (p.getBoolean("Jailed")) {
                r.add(Css.chip("مسجون", Css.CHIP_RUST));
            }
            Node gap = Css.leaf(0, 1);
            Css.flex1(gap);
            r.add(gap);
            r.add(Css.num(p.getString("Pos"), 13, LH, Tok.FAINT));
            r.add(Css.num(p.getInt("Health") + "♥", 14, LH, Tok.RUST));
            r.add(Css.num(p.getInt("Ping") + "ms", 13, LH, Tok.MUTED));
            left.add(r);
        }
        g.add(left);
        g.add(playerActions(m, players));
        return g;
    }

    static Node playerActions(Model m, List<CompoundTag> players) {
        Node p = panel(col()).gap(10).minH(0).clip();
        CompoundTag sel = null;
        for (CompoundTag t : players) {
            if (t.getUUID("Id").equals(m.selected)) {
                sel = t;
            }
        }
        CompoundTag page = ClientAdmin.page("players");
        if (page != null && page.getBoolean("Watching")) {
            p.add(button("وقف المراقبة وارجع مكانك", Css.BTN, "unspectate", m));
        }
        if (sel == null) {
            p.add(Css.phead("اختر لاعب", null));
            p.add(empty("اضغط على لاعب من القائمة عشان تشوف وش تقدر تسوي معه"));
            return p;
        }
        p.add(Css.phead(sel.getString("Name"), Css.aside().add(Css.asideText(sel.getString("Dim")))));
        Node acts = row().gap(6).wrap();
        if (ClientAdmin.has("players.teleport")) {
            acts.add(button("انتقل له", Css.BTN_GHOST, "tp", m));
        }
        if (ClientAdmin.has("players.bring")) {
            acts.add(button("اسحبه عندك", Css.BTN_GHOST, "bring", m));
        }
        if (ClientAdmin.has("players.inventory")) {
            acts.add(button("شنطته", Css.BTN_GHOST, "inv", m));
        }
        if (ClientAdmin.has("players.spectate")) {
            acts.add(button("راقبه من عيونه", Css.BTN_GHOST, "spectate", m));
        }
        if (ClientAdmin.has("restore")) {
            acts.add(button("موتاته", Css.BTN_GHOST, "deaths", m));
        }
        p.add(acts);
        if (ClientAdmin.has("punish")) {
            p.add(punishForm(m, false));
        }
        CompoundTag deaths = ClientAdmin.page("deaths");
        if (ClientAdmin.has("restore") && deaths != null && deaths.hasUUID("Target") && deaths.getUUID("Target").equals(m.selected)) {
            p.add(Css.phead("آخر موتاته", null));
            ListTag l = deaths.getList("Deaths", Tag.TAG_COMPOUND);
            if (l.isEmpty()) {
                p.add(empty("ما مات ولا مرة"));
            }
            for (int i = 0; i < Math.min(4, l.size()); i++) {
                CompoundTag d = l.getCompound(i);
                Node row = row().align(CENTER).gap(6).pad(4, 6, 4, 6).border(1.0F, Tok.LINE_SOFT);
                Node info = col().gap(2);
                Css.flex1(info).minW(0);
                info.add(txt(UiText.logical(d.getString("Cause")), K(600), 11.5F, 1.4F, Tok.SOFT));
                info.add(txt(ago(d.getLong("At")) + " · " + d.getString("Place") + " · " + d.getInt("Count") + " غرض", K(400), 10.5F, 1.4F, Tok.FAINT));
                Node icons = row().gap(2);
                ListTag items = d.getList("Items", Tag.TAG_COMPOUND);
                for (int k = 0; k < Math.min(6, items.size()); k++) {
                    Item it = new Item();
                    it.handle = ItemStack.of(items.getCompound(k));
                    icons.add(Css.leaf(18, 18).under((c, n) -> Slots.drawItem(c, it, n.x, n.y, n.w, n.h)));
                }
                info.add(icons);
                row.add(info);
                if (d.getBoolean("Restored")) {
                    row.add(Css.chip("ترجعت", Css.CHIP_SAGE));
                } else {
                    row.add(button("رجّعها", Css.BTN, "restore:" + i, m));
                }
                p.add(row);
            }
        }
        return p;
    }

    /** The punishment form: type, reason, minutes. With {@code byName} it also asks who. */
    static Node punishForm(Model m, boolean byName) {
        Node f = col().gap(8).pad(10).border(1.0F, Tok.LINE_SOFT).bg(Tok.rgba(0x000000, 0.18));
        f.add(label(byName ? "عاقب لاعب بالاسم (متصل أو لا)" : "عقوبة"));
        if (byName) {
            f.add(input(m, "pname", "اسم اللاعب", 0));
        }
        Node types = row().gap(6).wrap();
        for (int i = 0; i < TYPES.length; i++) {
            if (ClientAdmin.has("punish." + TYPES[i])) {
                types.add(toggle(TYPE_NAMES[i], TYPES[i].equals(m.ptype), "ptype:" + TYPES[i], m));
            }
        }
        f.add(types);
        f.add(input(m, "reason", "السبب", 0));
        Node last = row().align(CENTER).gap(8);
        if (!m.ptype.equals("warn") && !m.ptype.equals("kick")) {
            last.add(input(m, "minutes", "المدة بالدقايق · فاضي = دائم", 0));
        } else {
            Node sp = Css.leaf(0, 1);
            Css.flex1(sp);
            last.add(sp);
        }
        last.add(button("نفّذ", Css.BTN_DANGER, byName ? "punish-name" : "punish", m));
        f.add(last);
        return f;
    }

    /* ================================================================== ranks */

    static Node ranks(Model m) {
        CompoundTag page = ClientAdmin.page("ranks");
        Node g = grid(300, -1, 300).gap(14).align(Node.STRETCH);
        List<CompoundTag> ranks = list("ranks", "Ranks");

        Node left = panel(col()).gap(6).minH(0).clip();
        left.add(Css.phead("الرتب الإدارية", Css.aside().add(Css.asideNum(String.valueOf(ranks.size())))));
        for (int i = 0; i < ranks.size(); i++) {
            CompoundTag r = ranks.get(i);
            String id = r.getString("Id");
            boolean sel = id.equals(m.rank);
            String key = "rk:" + i;
            int rc = 0xFF000000 | r.getInt("Color");
            Node row = row().align(CENTER).gap(8).h(34).pad(0, 10, 0, 10)
                    .border(1.0F, sel ? Tok.AMBER : key.equals(m.hover) ? Tok.AMBER_DIM : Tok.LINE_SOFT)
                    .bg(sel ? Tok.AMBER_GLOW : Tok.CARD).hit(key, i);
            row.add(Css.bar(4, 16, rc));
            row.add(txt(UiText.logical(r.getString("Name")), K(700), 13, 1.2F, Tok.BONE));
            row.add(Css.num(id, 12, LH, Tok.FAINT));
            left.add(row);
        }
        if (ranks.isEmpty()) {
            left.add(empty("ما فيه رتب لين الحين"));
        }
        Node create = col().gap(6).mar(10, 0, 0, 0);
        create.add(label("رتبة جديدة"));
        create.add(input(m, "rid", "معرّف بالإنجليزي، مثل mod", 0));
        create.add(input(m, "rname", "الاسم اللي يبين، مثل مشرف", 0));
        create.add(button("سوّ الرتبة", Css.BTN, "rank-create", m));
        left.add(create);
        g.add(left);

        CompoundTag sel = null;
        for (CompoundTag r : ranks) {
            if (r.getString("Id").equals(m.rank)) {
                sel = r;
            }
        }
        Node mid = panel(col()).gap(8).minH(0).clip();
        Node right = panel(col()).gap(6).minH(0).clip();
        if (sel == null || page == null) {
            mid.add(Css.phead("الصلاحيات", null));
            mid.add(empty("اختر رتبة من اليمين"));
            right.add(Css.phead("الأعضاء", null));
            g.add(mid, right);
            return g;
        }
        java.util.Set<String> perms = new java.util.HashSet<>();
        ListTag pl = sel.getList("Perms", Tag.TAG_STRING);
        for (int i = 0; i < pl.size(); i++) {
            perms.add(pl.getString(i));
        }
        Node aside = Css.aside();
        aside.add(button("احذف الرتبة", Css.BTN_GHOST, "rank-delete", m));
        mid.add(Css.phead("صلاحيات " + UiText.logical(sel.getString("Name")), aside));
        // the mod's own, grouped
        ListTag nodes = page.getList("Nodes", Tag.TAG_COMPOUND);
        String group = null;
        Node wrap = null;
        for (int i = 0; i < nodes.size(); i++) {
            CompoundTag n = nodes.getCompound(i);
            if (!n.getString("Group").equals(group)) {
                group = n.getString("Group");
                mid.add(label(group));
                wrap = row().gap(6).wrap();
                mid.add(wrap);
            }
            String node = n.getString("Id");
            boolean on = perms.contains(node) || perms.contains("*");
            wrap.add(toggle(n.getString("Label"), on, "perm:" + node, m));
        }
        // every command on the server
        Node ch = row().align(CENTER).gap(8);
        ch.add(label("الأوامر"));
        ch.add(input(m, "filter", "ابحث عن أمر", 0));
        mid.add(ch);
        Node cmds = row().gap(5).wrap();
        ListTag cl = page.getList("Commands", Tag.TAG_STRING);
        String f = m.fields.getOrDefault("filter", "").trim().toLowerCase(java.util.Locale.ROOT);
        int shown = 0;
        for (int i = 0; i < cl.size() && shown < 60; i++) {
            String c = cl.getString(i);
            if (!f.isEmpty() && !c.contains(f)) {
                continue;
            }
            shown++;
            boolean on = perms.contains("cmd." + c) || perms.contains("cmd.*") || perms.contains("*");
            cmds.add(toggle("/" + c, on, "cmd:" + c, m));
        }
        mid.add(cmds);
        // anything else, typed by hand
        Node custom = row().align(CENTER).gap(8);
        custom.add(input(m, "custom", "صلاحية بالاسم، مثل cmd.mlum.downed أو *", 0));
        custom.add(button("أضف", Css.BTN, "perm-add", m));
        mid.add(custom);
        Node extra = row().gap(5).wrap();
        for (String p : perms) {
            boolean known = p.startsWith("cmd.") && p.indexOf('.', 4) < 0;
            for (int i = 0; i < nodes.size() && !known; i++) {
                known = nodes.getCompound(i).getString("Id").equals(p);
            }
            if (!known) {
                extra.add(toggle(p + "  ×", true, "perm:" + p, m));
            }
        }
        mid.add(extra);
        g.add(mid);

        right.add(Css.phead("الأعضاء", null));
        List<CompoundTag> members = list("ranks", "Members");
        int idx = 0;
        for (CompoundTag mem : members) {
            if (!mem.getString("Rank").equals(m.rank)) {
                idx++;
                continue;
            }
            Node row = row().align(CENTER).gap(8).h(30).pad(0, 8, 0, 8).border(1.0F, Tok.LINE_SOFT);
            row.add(Css.leaf(6, 6).bg(mem.getBoolean("Online") ? Tok.SAGE : Tok.FAINT));
            Node name = Css.px(mem.getString("Name"), 600, 14, 1.0F, 0.03F, Tok.BONE);
            Css.flex1(name);
            row.add(name);
            row.add(button("شيله", Css.BTN_GHOST, "unassign:" + idx, m));
            right.add(row);
            idx++;
        }
        Node add = col().gap(6).mar(10, 0, 0, 0);
        add.add(label("أضف لاعب لهالرتبة"));
        add.add(input(m, "mname", "اسم اللاعب", 0));
        add.add(button("أضف", Css.BTN, "assign", m));
        right.add(add);
        g.add(right);
        return g;
    }

    /* ================================================================== punishments */

    static Node punish(Model m) {
        Node g = grid(-1, 400).gap(14).align(Node.STRETCH);
        List<CompoundTag> records = list("punish", "Records");
        Node left = panel(col()).gap(4).minH(0).clip();
        left.add(Css.phead("سجل العقوبات", Css.aside().add(Css.asideNum(String.valueOf(records.size())))));
        if (records.isEmpty()) {
            left.add(empty("ما فيه عقوبات"));
        }
        int base = Math.max(0, Math.min(m.scroll, Math.max(0, records.size() - 11)));
        List<CompoundTag> shown = window(records, m.scroll, 11);
        for (int i = 0; i < shown.size(); i++) {
            CompoundTag r = shown.get(i);
            String type = r.getString("Type");
            int t = java.util.Arrays.asList(TYPES).indexOf(type);
            Node row = row().align(CENTER).gap(8).pad(5, 8, 5, 8).border(1.0F, Tok.LINE_SOFT).bg(Tok.CARD);
            row.add(Css.chip(t < 0 ? type : TYPE_NAMES[t], type.equals("warn") ? Css.CHIP_AMBER : Css.CHIP_RUST));
            Node info = col().gap(1);
            Css.flex1(info).minW(0);
            info.add(Css.px(r.getString("Name"), 600, 14, 1.0F, 0.03F, Tok.BONE));
            info.add(txt(UiText.logical(r.getString("Reason")) + " · " + r.getString("By") + " · " + ago(r.getLong("At")),
                    K(400), 10.5F, 1.4F, Tok.FAINT));
            row.add(info);
            if (r.getBoolean("Active") && ClientAdmin.has("punish." + type)) {
                long until = r.getLong("Until");
                row.add(txt(until <= 0L ? "دائم" : "باقي " + AdminNotices.duration(until - System.currentTimeMillis()),
                        K(400), 11, LH, Tok.MUTED));
                row.add(button("شيلها", Css.BTN_GHOST, "lift:" + (base + i), m));
            }
            left.add(row);
        }
        g.add(left);
        Node right = panel(col()).gap(10).minH(0);
        right.add(Css.phead("عقوبة جديدة", null));
        right.add(punishForm(m, true));
        if (ClientAdmin.has("punish.jail")) {
            CompoundTag page = ClientAdmin.page("punish");
            boolean has = page != null && page.getBoolean("HasJail");
            Node jail = row().align(CENTER).gap(8);
            Node l = label(has ? "السجن محدد" : "ما حددت مكان السجن");
            Css.flex1(l);
            jail.add(l);
            jail.add(button("السجن صار هنا", Css.BTN_GHOST, "jail-set", m));
            right.add(jail);
        }
        g.add(right);
        return g;
    }

    /* ================================================================== tickets */

    static Node tickets(Model m) {
        List<CompoundTag> tickets = list("tickets", "Tickets");
        Node p = panel(col()).gap(6).minH(0).clip();
        p.add(Css.phead("تذاكر الدعم", Css.aside().add(Css.asideText("يكتبها اللاعب بـ /mlum ticket"))));
        if (tickets.isEmpty()) {
            p.add(empty("ما فيه تذاكر"));
        }
        int base = Math.max(0, Math.min(m.scroll, Math.max(0, tickets.size() - 10)));
        List<CompoundTag> shown = window(tickets, m.scroll, 10);
        for (int i = 0; i < shown.size(); i++) {
            CompoundTag t = shown.get(i);
            boolean closed = t.getBoolean("Closed");
            Node row = row().align(CENTER).gap(10).pad(6, 10, 6, 10).border(1.0F, closed ? Tok.LINE_SOFT : Tok.AMBER_DIM)
                    .bg(Tok.CARD).opacity(closed ? 0.55F : 1.0F);
            row.add(Css.num("#" + t.getInt("Id"), 16, LH, Tok.AMBER));
            Node info = col().gap(1);
            Css.flex1(info).minW(0);
            info.add(Css.px(t.getString("Name"), 600, 14, 1.0F, 0.03F, Tok.BONE));
            info.add(txt(UiText.logical(t.getString("Text")), K(400), 12, 1.6F, Tok.SOFT).wrapText());
            info.add(txt(ago(t.getLong("At")) + (closed ? " · قفلها " + t.getString("ClosedBy") : ""), K(400), 10.5F, 1.4F, Tok.FAINT));
            row.add(info);
            if (!closed) {
                row.add(button("روح لمكانه", Css.BTN_GHOST, "tk-tp:" + (base + i), m));
                row.add(button("انحلت", Css.BTN, "tk-close:" + (base + i), m));
            }
            p.add(row);
        }
        return p;
    }

    /* ================================================================== alerts */

    static Node alerts(Model m) {
        List<CompoundTag> alerts = list("alerts", "Alerts");
        Node p = panel(col()).gap(6).minH(0).clip();
        Node aside = Css.aside();
        if (!alerts.isEmpty()) {
            aside.add(button("امسح الكل", Css.BTN_GHOST, "alerts-clear", m));
        }
        p.add(Css.phead("تنبيهات التكرار والفلوس", aside));
        if (alerts.isEmpty()) {
            p.add(empty("ما فيه شي غريب"));
        }
        for (CompoundTag a : window(alerts, m.scroll, 12)) {
            Node row = row().align(CENTER).gap(10).pad(6, 10, 6, 10).border(1.0F, Tok.LINE_SOFT).bg(Tok.CARD);
            row.add(Css.leaf(4, 18).bg(Tok.RUST));
            row.add(Css.px(a.getString("Name"), 600, 14, 1.0F, 0.03F, Tok.BONE));
            Node text = txt(UiText.logical(a.getString("Text").replace("{n}", "").replace("{/n}", "")), K(400), 12, LH, Tok.SOFT);
            Css.flex1(text).minW(0);
            row.add(text);
            row.add(txt(ago(a.getLong("At")), K(400), 11, LH, Tok.FAINT));
            p.add(row);
        }
        return p;
    }

    /* ================================================================== schedule */

    static Node schedule(Model m) {
        CompoundTag page = ClientAdmin.page("schedule");
        Node g = grid(380, -1).gap(14).align(Node.STRETCH);
        Node left = panel(col()).gap(10);
        left.add(Css.phead("الريستارت", null));
        long at = page == null ? 0L : page.getLong("RestartAt");
        if (at > 0L) {
            long left0 = Math.max(0L, (at - System.currentTimeMillis()) / 1000L);
            left.add(txt("الريستارت الجاي بعد " + (left0 / 60) + ":" + String.format("%02d", left0 % 60), K(700), 14, LH, Tok.AMBER));
        } else {
            left.add(label("ما فيه ريستارت مخطط"));
        }
        if (ClientAdmin.has("restart")) {
            Node r = row().align(CENTER).gap(8);
            r.add(input(m, "rmin", "بعد كم دقيقة", 0));
            r.add(button("خطط ريستارت", Css.BTN, "restart-in", m));
            left.add(r);
            if (at > 0L) {
                left.add(button("الغ الريستارت", Css.BTN_GHOST, "restart-cancel", m));
            }
        }
        ListTag times = page == null ? new ListTag() : page.getList("Times", Tag.TAG_STRING);
        StringBuilder ts = new StringBuilder();
        for (int i = 0; i < times.size(); i++) {
            ts.append(i == 0 ? "" : " · ").append(times.getString(i));
        }
        left.add(label(times.isEmpty() ? "ما فيه أوقات يومية في الإعدادات (restartTimes)" : "يومياً: " + ts));
        g.add(left);

        Node right = panel(col()).gap(6).minH(0).clip();
        right.add(Css.phead("الأحداث المجدولة", Css.aside().add(Css.asideText("كل حدث أمر يشتغل لحاله"))));
        List<CompoundTag> jobs = list("schedule", "Jobs");
        if (jobs.isEmpty()) {
            right.add(empty("ما فيه أحداث مجدولة"));
        }
        for (int i = 0; i < jobs.size(); i++) {
            CompoundTag j = jobs.get(i);
            Node row = row().align(CENTER).gap(10).pad(6, 10, 6, 10).border(1.0F, Tok.LINE_SOFT).bg(Tok.CARD);
            row.add(Css.num(String.format("%02d:%02d", j.getInt("Hour"), j.getInt("Minute")), 18, LH, Tok.AMBER));
            Node info = col().gap(1);
            Css.flex1(info).minW(0);
            info.add(txt(UiText.logical(j.getString("Name")), K(700), 13, 1.3F, Tok.BONE));
            StringBuilder days = new StringBuilder();
            for (int d = 0; d < 7; d++) {
                if ((j.getInt("Days") & (1 << d)) != 0) {
                    days.append(days.length() == 0 ? "" : " ").append(DAYS[d]);
                }
            }
            info.add(txt(days + " · /" + j.getString("Command"), K(400), 10.5F, 1.4F, Tok.FAINT));
            row.add(info);
            if (ClientAdmin.has("schedule")) {
                row.add(button("شغّله الحين", Css.BTN_GHOST, "job-run:" + i, m));
                row.add(button("احذف", Css.BTN_GHOST, "job-del:" + i, m));
            }
            right.add(row);
        }
        if (ClientAdmin.has("schedule")) {
            Node form = col().gap(6).mar(10, 0, 0, 0).pad(10).border(1.0F, Tok.LINE_SOFT);
            form.add(label("حدث جديد"));
            Node r1 = row().align(CENTER).gap(8);
            r1.add(input(m, "jname", "الاسم، مثل قافلة الإمداد", 0));
            r1.add(input(m, "jhour", "الساعة", 70));
            r1.add(input(m, "jmin", "الدقيقة", 70));
            form.add(r1);
            form.add(input(m, "jcmd", "الأمر بدون /، مثل say بدأت القافلة", 0));
            Node r2 = row().align(CENTER).gap(6);
            for (int d = 0; d < 7; d++) {
                r2.add(toggle(DAYS[d], (m.days & (1 << d)) != 0, "day:" + d, m));
            }
            Node sp = Css.leaf(0, 1);
            Css.flex1(sp);
            r2.add(sp);
            r2.add(button("أضف", Css.BTN, "job-add", m));
            form.add(r2);
            right.add(form);
        }
        g.add(right);
        return g;
    }
}

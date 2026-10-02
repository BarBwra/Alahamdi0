package com.barbwra.mlum.client.ui.view;

import com.barbwra.mlum.client.ui.Art;
import com.barbwra.mlum.client.ui.Canvas;
import com.barbwra.mlum.client.ui.Draw;
import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.Tok;
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

/** The bag: bag grid and quick access, the survivor with gear and vitals, weapons or a chest, details. */
public final class InvView {

    private InvView() {
    }

    /* ================================================================== model */

    public static final class Entry {
        public Item item;
        public int row;
        public int col;
        /** In the worn backpack's rows rather than the base three. */
        public boolean pack;
    }

    public static final class Gun {
        public Item item;
        public int rounds = -1;
        public int reserve = -1;
        public final Item[] atts = new Item[6];
        /** Ghost silhouette per mount, e.g. "scope", or null when the gun has no such mount. */
        public final String[] ghosts = new String[6];
        public boolean selected;
    }

    public static final class Chest {
        public String title = "";
        public int rows = 3;
        public Item[] slots = new Item[27];
        public boolean paged;
        public int page;
        public int pages = 1;
    }

    public static final class Model {
        public final List<Entry> entries = new ArrayList<>();
        /** Backpack rows, 0..5. */
        public int packRows;
        public Item pack;
        public String packName = "";
        public int packAdd;
        public boolean packVip;
        public int used;
        public int cap;
        public final Item[] quick = new Item[7];
        public int activeQuick = -1;
        /** helmet, chest, legs, boots, offhand, backpack. */
        public final Item[] gear = new Item[6];
        public int level;
        public String name = "";
        public String faction;
        public int hpSeg;
        public int hp;
        public int armorSeg;
        public int armor;
        public int foodSeg;
        public int food;
        public final Gun[] guns = {new Gun(), new Gun()};
        public Chest chest;
        public Item inspect;
        /** Key of the selected thing, e.g. "bag:3", "qa:1", "gun:0". */
        public String sel;
        public String hover;
        /** Hover/drop verdicts while carrying: key -> HOT or NOPE. */
        public final java.util.Map<String, Integer> verdicts = new java.util.HashMap<>();
        public boolean sortButton = true;
        public boolean capBar = true;
        public boolean showPlayer = true;
        /** Index into entries being carried, hidden from the grid while it is. */
        public int heldEntry = -1;
        /**
         * How much of {@link #heldEntry} is still in its cell.
         *
         * <p>Picking up half a stack leaves the other half where it was, so the cell keeps drawing -
         * with the smaller number. Zero means the whole stack is in hand and the cell is empty.</p>
         */
        public int heldRemain;
        /** Where the carried item would land in the bag grid (row counts pack rows after base). */
        public int dropRow = -1;
        public int dropCol;
        public int dropW = 1;
        public int dropH = 1;
        public boolean dropOk;
        /** The empty cell under the cursor when nothing is being carried, or -1. */
        public int hoverRow = -1;
        public int hoverCol;
    }

    public static final String[] GEAR_LABELS = {"الخوذة", "السترة", "البنطلون", "الجزمة", "اليد الثانية", "الشنطة"};
    public static final String[] GEAR_GHOSTS = {"helmet", "vest", "pants", "boots", "shield", "bp_mil"};

    /* ================================================================== geometry */

    public static final float PITCH = 44;
    public static final float CELLW = 40;
    public static final float SEPH = 26;

    public static float yOf(int row) {
        return row * PITCH + (row >= 3 ? SEPH : 0);
    }

    /* ================================================================== view */

    /** {@code .main} holding {@code .inv}. */
    public static Node build(Model m) {
        Node main = Chrome.main();
        float contentH = Px.H - 64 - 36 - 32;
        Node inv = grid(456, -1, 424).gap(22).align(START).rows(-1).h(contentH).tag("inv");
        Node right = col().gap(14).add(bagPanel(m), quickPanel(m));
        Node centre = centre(m);
        centre.alignSelf = CENTER;
        Node left = col().gap(14);
        left.add(m.chest != null ? chestPanel(m) : weaponsPanel(m));
        left.add(detailsPanel(m));
        inv.add(right, centre, left);
        main.add(inv);
        return main;
    }

    /* ---- the bag ---- */

    static Node bagPanel(Model m) {
        Node p = panel(block()).tag("bag-panel");
        Node aside = aside();
        aside.add(asideNum(m.used + " / " + m.cap));
        aside.add(asideText("خانة"));
        if (m.sortButton) {
            boolean hover = "sort".equals(m.hover);
            Node sort = Css.tool("رتّب", "sort", 24, 8).hit("sort");
            if (hover) {
                sort.borderColor = Tok.AMBER_DIM;
                recolor(sort, Tok.BONE);
            }
            aside.add(sort);
        }
        Node head = phead("الحقيبة", aside);
        if (m.capBar) {
            float fill = m.cap <= 0 ? 0 : Math.min(1.0F, m.used / (float) m.cap);
            int color = fill >= 0.9F ? Tok.RUST : Tok.AMBER;
            Node cap = Css.leaf(0, 2).bg(Tok.LINE_SOFT).abs(0, AUTO, 0, -1);
            cap.under((c, n) -> Draw.rect(c, n.x + n.w - n.w * fill, n.y, n.w * fill, n.h, color));
            head.add(cap);
        }
        p.add(head);

        int rows = 3 + m.packRows;
        float gridH = yOf(rows - 1) + CELLW;
        Node bg = block().size(392, gridH).tag("bgrid").hit("bgrid");
        bg.ml = 0;
        bg.mr = AUTO;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) {
                bg.add(cell(r >= 3).abs(AUTO, yOf(r), c * PITCH, AUTO));
            }
        }
        if (m.packRows > 0) {
            bg.add(separator(m).abs(0, 3 * PITCH - 2, 0, AUTO));
        }
        if (m.hoverRow >= 0) {
            final int hr = m.hoverRow;
            final int hc = m.hoverCol;
            Node hov = Css.leaf(1, 1).abs(0, 0, AUTO, AUTO).z(2);
            hov.over((c, n) -> Overlays.hoverCell(c, n.parent(), hr, hc));
            bg.add(hov);
        }
        for (int i = 0; i < m.entries.size(); i++) {
            Entry e = m.entries.get(i);
            Item shown = e.item;
            if (i == m.heldEntry) {
                if (m.heldRemain <= 0) {
                    continue;
                }
                // half of it is on the cursor; the cell keeps the rest and says so
                shown = e.item.withCount(m.heldRemain);
            }
            int row = e.pack ? e.row + 3 : e.row;
            float top = yOf(row);
            float height = yOf(row + shown.h - 1) + CELLW - top;
            String key = "bag:" + i;
            Node it = Slots.bagItem(shown, shown.w * PITCH - 4, height, key.equals(m.hover), key.equals(m.sel));
            it.abs(AUTO, top, e.col * PITCH, AUTO).z(1).hit(key, i);
            bg.add(it);
        }
        if (m.dropRow >= 0) {
            final int dr = m.dropRow;
            final int dc = m.dropCol;
            final int dw = m.dropW;
            final int dh = m.dropH;
            final boolean ok = m.dropOk;
            Node drop = Css.leaf(1, 1).abs(0, 0, AUTO, AUTO).z(3);
            drop.over((c, n) -> Overlays.drop(c, n.parent(), dr, dc, dw, dh, ok));
            bg.add(drop);
        }
        p.add(bg);
        if (m.packRows == 0) {
            Node hint = row().align(CENTER).gap(7);
            hint.mt = 10;
            hint.add(Css.ghost("bp_mil", 1));
            hint.add(txt("لبس شنطة في خانة الشنطة عشان تزيد الخانات.", K(400), 11.5F, LH, Tok.MUTED));
            p.add(hint);
        }
        return p;
    }

    /** Recolours every text run and icon under a node - for hover states. */
    static void recolor(Node n, int argb) {
        for (Span s : n.spans) {
            s.color = argb;
        }
        n.color = argb;
        for (Node k : n.kids) {
            recolor(k, argb);
        }
    }

    static Node cell(boolean pack) {
        Node n = Css.leaf(40, 40);
        n.bg(pack ? Tok.rgb(0x191c14) : Tok.SLOT).border(1.0F, pack ? Tok.rgb(0x343a29) : Tok.SLOT_BORDER);
        n.under((c, nd) -> Css.slotInset(c, nd));
        return n;
    }

    /** {@code .bsep}: the worn backpack's label and the rule that divides base rows from pack rows. */
    static Node separator(Model m) {
        Node s = row().align(CENTER).gap(7).h(22);
        s.add(pictureOf(m.pack, 16, 16));
        s.add(txt(m.packName, K(400), 11.5F, LH, Tok.AMBER));
        s.add(num("+" + m.packAdd, 11.5F, LH, Tok.AMBER));
        if (m.packVip) {
            s.add(vip());
        }
        s.add(txt("· الأغراض هنا تنشال مع الشنطة", K(400), 11.5F, LH, Tok.MUTED));
        s.add(Css.flex1(Css.leaf(0, 1).bg(Tok.AMBER_DIM)));
        return s;
    }

    /** {@code .vip}: amber badge. */
    public static Node vip() {
        Node v = row().align(CENTER).pad(3, 5, 2, 5).bg(Tok.AMBER);
        v.add(Css.px("VIP", 700, 10, 1.0F, 0.06F, Tok.INK));
        return v;
    }

    /** A picture of an item in a fixed box (no cell around it). */
    static Node pictureOf(Item it, float w, float h) {
        return Css.leaf(w, h).under((c, n) -> Slots.drawItem(c, it, n.x, n.y, n.w, n.h));
    }

    static Node quickPanel(Model m) {
        Node p = panel(block()).tag("quick-panel");
        Node aside = aside();
        aside.add(asideText("المفاتيح"));
        aside.add(asideNum("3"));
        aside.add(asideText("إلى"));
        aside.add(asideNum("9"));
        aside.add(asideText("· كل غرض خانة وحدة"));
        p.add(phead("الوصول السريع", aside));
        Node g = grid(44, 44, 44, 44, 44, 44, 44).gap(4);
        for (int i = 0; i < 7; i++) {
            String key = "qa:" + i;
            int state = stateOf(m, key);
            if (state == Slots.IDLE && i == m.activeQuick) {
                state = Slots.ACTIVE;
            }
            // an empty cell under the cursor gets the same amber square the bag grid shows
            if (state == Slots.IDLE && m.quick[i] == null && key.equals(m.hover)) {
                state = Slots.OPEN;
            }
            g.add(Slots.slot(m.quick[i], 44, 30, key.equals(m.hover), state, String.valueOf(i + 3)).hit(key, i));
        }
        p.add(g);
        return p;
    }

    static int stateOf(Model m, String key) {
        Integer v = m.verdicts.get(key);
        if (v != null) {
            return v;
        }
        return key.equals(m.sel) ? Slots.SEL : Slots.IDLE;
    }

    /* ---- the survivor ---- */

    static Node centre(Model m) {
        Node c = col().align(CENTER).gap(14).tag("centre");
        Node fig = grid(56, 144, 56).gap(14).tag("fig");
        fig.add(gearColumn(m, new int[]{0, 2, 4}));
        Node body = Css.leaf(144, 288).tag("fig-body");
        body.under((cv, n) -> {
            // .fig-body::after - the shadow pooled under the feet
            shadowEllipse(cv, n.x + 6, n.y + n.h + 12 - 14, n.w - 12, 14);
        });
        if (m.showPlayer) {
            body.over((cv, n) -> cv.player(Px.d(n.x), Px.d(n.y), Px.d(n.x + n.w), Px.d(n.y + n.h), 0, 0));
        }
        body.hit("figure");
        fig.add(body);
        fig.add(gearColumn(m, new int[]{1, 3, 5}));
        c.add(fig);

        Node plate = row().align(CENTER).gap(10).tag("nameplate");
        Node lv = row().align(CENTER).pad(3, 6, 2, 6).bg(Tok.AMBER);
        lv.add(num(String.valueOf(m.level), 17, 1.0F, Tok.INK));
        plate.add(lv);
        plate.add(Css.px(m.name, 600, 24, 1.0F, 0.04F, Tok.BONE));
        if (m.faction != null && !m.faction.isEmpty()) {
            plate.add(Css.chip(m.faction, Css.CHIP_MUTE));
        }
        c.add(plate);

        Node vitals = grid(-1).gap(7).w(280).tag("vitals");
        vitals.add(vital("الصحة", m.hpSeg, m.hp, Tok.RUST));
        vitals.add(vital("الدرع", m.armorSeg, m.armor, Tok.STEEL));
        vitals.add(vital("الأكل", m.foodSeg, m.food, Tok.FOOD));
        c.add(vitals);
        return c;
    }

    /** radial-gradient(ellipse at center, rgba(0,0,0,.7), transparent 70%) over a box. */
    static void shadowEllipse(Canvas c, float x, float y, float w, float h) {
        // farthest-corner ellipse: radii w/sqrt2, h/sqrt2; the colour is gone at 70% of them
        float rx = (float) (w / Math.sqrt(2));
        float ry = (float) (h / Math.sqrt(2));
        Draw.rasterClipped(c, Art.radial(0.70F), x + w / 2 - rx, y + h / 2 - ry, rx * 2, ry * 2,
                x, y, x + w, y + h, Draw.rgba(0x000000, 0.7F));
    }

    static Node gearColumn(Model m, int[] gear) {
        Node col = block().h(304);
        float[] tops = {0, 118, 236};
        for (int k = 0; k < 3; k++) {
            int g = gear[k];
            Node eq = col().align(CENTER).gap(3).abs(0, tops[k], 0, AUTO);
            eq.add(gearSlot(m, g));
            eq.add(txt(GEAR_LABELS[g], K(400), 10.5F, 1.3F, Tok.MUTED));
            col.add(eq);
        }
        return col;
    }

    static Node gearSlot(Model m, int g) {
        String key = "eq:" + g;
        Item it = m.gear[g];
        int state = stateOf(m, key);
        // an empty gear socket under the cursor says so, like every other cell in the bag
        if (state == Slots.IDLE && it == null && key.equals(m.hover)) {
            state = Slots.OPEN;
        }
        Node s = Slots.slot(it, 52, 34, key.equals(m.hover), state, null).hit(key, g);
        // .eq .slot.bp outranks hover and selection; only the drop verdicts beat it
        if (g == 5 && it != null && state != Slots.HOT && state != Slots.NOPE && state != Slots.OPEN) {
            s.borderColor = Tok.AMBER_DIM;
        }
        if (it == null) {
            String ghost = GEAR_GHOSTS[g];
            int[] d = Art.dims(ghost);
            float gw = d[0] * 3;
            float gh = d[1] * 3;
            s.over = chain(s.over, (c, n) -> Draw.raster(c, Art.ghost(ghost), n.x + (n.w - gw) / 2, n.y + (n.h - gh) / 2,
                    gw, gh, Draw.rgba(0xFFFFFF, 0.15F)));
        }
        return s;
    }

    static com.barbwra.mlum.client.ui.layout.Painter chain(com.barbwra.mlum.client.ui.layout.Painter a,
                                                          com.barbwra.mlum.client.ui.layout.Painter b) {
        if (a == null) {
            return b;
        }
        return (c, n) -> {
            a.paint(c, n);
            b.paint(c, n);
        };
    }

    /** {@code .vital}: label, twenty segments, value - 44px / 1fr / 26px. */
    static Node vital(String label, int filled, int value, int color) {
        Node v = grid(44, -1, 26).gap(10).align(CENTER);
        v.add(txt(label, K(400), 11.5F, LH, Tok.MUTED));
        Node segs = grid(-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1)
                .gap(2).h(8);
        for (int k = 0; k < 20; k++) {
            segs.add(Css.leaf(0, 0).bg(k < filled ? color : Tok.rgb(0x1d2119)));
        }
        v.add(segs);
        Node n = num(String.valueOf(value), 16, LH, Tok.BONE);
        n.textAlign = Node.START;
        v.add(n);
        return v;
    }

    /* ---- weapons ---- */

    static Node weaponsPanel(Model m) {
        Node p = panel(block()).tag("weapons-panel");
        Node aside = aside();
        aside.add(asideText("المفاتيح"));
        aside.add(asideNum("1"));
        aside.add(asideText("و"));
        aside.add(asideNum("2"));
        p.add(phead("الأسلحة", aside));
        for (int i = 0; i < 2; i++) {
            Node card = m.guns[i].item != null ? gunCard(m, i) : gunEmpty(m, i);
            if (i == 1) {
                card.mt = 10;
            }
            p.add(card);
        }
        return p;
    }

    static final String[] ROLE = {"أساسي", "ثانوي"};

    static Node gunCard(Model m, int i) {
        Gun g = m.guns[i];
        String key = "gun:" + i;
        boolean sel = key.equals(m.sel) || g.selected;
        boolean hover = key.equals(m.hover);
        Integer verdict = m.verdicts.get(key);
        Node card = grid(40, -1, 88).gap(12).h(108).pad(10, 12, 10, 12).bg(Tok.CARD)
                .border(1.0F, verdict != null ? (verdict == Slots.HOT ? Tok.SAGE : Tok.RUST)
                        : sel ? Tok.AMBER : hover ? Tok.AMBER_DIM : Tok.LINE)
                .hit(key, i).tag("gun");
        int rc = Tok.rarity(g.item.rarity);
        card.under((c, n) -> Draw.rect(c, n.x + n.w - n.br - 2, n.y + n.bt + 10, 2, n.h - n.bt - n.bb - 20, rc));

        Node gk = col().align(CENTER).justify(CENTER).gap(4).border(0, 0, 0, 1, Tok.LINE_SOFT);
        gk.pl = 10;
        gk.add(num(String.valueOf(i + 1), 32, 0.9F, Tok.AMBER));
        gk.add(txt(ROLE[i], K(400), 10, LH, Tok.MUTED));
        card.add(gk);

        Node art = col().justify(Node.BETWEEN).minW(0);
        Node top = row().align(CENTER).gap(8);
        top.add(Css.px(g.item.name, 600, 20, 1.0F, 0.05F, Tok.BONE).shrink(1).minW(0));
        top.add(Css.rarityChip(g.item.rarityName(), rc));
        art.add(top);
        Node img = Css.leaf(0, 44).minW(0);
        img.under((c, n) -> Slots.drawItem(c, gunArt(g.item), n.x, n.y, n.w, n.h));
        art.add(img);
        Node ammo = row().align(CENTER).gap(6);
        if (g.rounds >= 0) {
            ammo.add(Css.sprite("ammo", 16, 16));
            ammo.add(num(g.rounds + " / " + Math.max(0, g.reserve), 17, LH, g.rounds == 0 ? Tok.RUST : Tok.BONE));
            ammo.add(txt("طلقة", K(400), 11, LH, Tok.MUTED));
        }
        art.add(ammo);
        card.add(art);
        card.add(mounts(m, i, g, false));
        return card;
    }

    /** The picture a gun card shows: its wide artwork when it has one. */
    static Item gunArt(Item it) {
        if (it.art == null) {
            return it;
        }
        Item a = new Item();
        a.handle = it.art;
        return a;
    }

    static Node gunEmpty(Model m, int i) {
        Gun g = m.guns[i];
        String key = "gun:" + i;
        Integer verdict = m.verdicts.get(key);
        // an empty firearm card lights amber on hover, like an empty cell anywhere else
        boolean open = verdict == null && key.equals(m.hover);
        Node card = grid(40, -1, 88).gap(12).h(108).pad(10, 12, 10, 12).bg(open ? Tok.SLOT_HI : Tok.CARD)
                .border(1.0F, verdict != null ? (verdict == Slots.HOT ? Tok.SAGE : Tok.RUST)
                        : open ? Tok.AMBER : Tok.LINE)
                .dashed().hit(key, i).tag("gun");
        Node gk = col().align(CENTER).justify(CENTER).gap(4).border(0, 0, 0, 1, Tok.LINE_SOFT);
        gk.pl = 10;
        gk.add(num(String.valueOf(i + 1), 32, 0.9F, Tok.FAINT));
        gk.add(txt(ROLE[i], K(400), 10, LH, Tok.MUTED));
        card.add(gk);
        Node art = col().justify(Node.BETWEEN).minW(0);
        Node top = row().align(CENTER).gap(8);
        top.add(txt("الخانة فاضية", K(400), 13, LH, Tok.MUTED));
        art.add(top);
        Node box = row().align(CENTER).justify(CENTER).h(44);
        box.add(Css.ghost("pistol", 4));
        art.add(box);
        Node ammo = row().align(CENTER).gap(6);
        ammo.add(txt("حط مسدس أو رشاش هنا", K(400), 11, LH, Tok.MUTED));
        art.add(ammo);
        card.add(art);
        card.add(mounts(m, i, g, true));
        return card;
    }

    static Node mounts(Model m, int gun, Gun g, boolean empty) {
        Node att = grid(26, 26, 26).gap(5).align(Node.START);
        att.justify = CENTER;
        for (int s = 0; s < 6; s++) {
            String key = "att:" + gun + ":" + s;
            Item it = empty ? null : g.atts[s];
            String ghost = g.ghosts[s] != null ? g.ghosts[s] : DEFAULT_GHOSTS[s];
            Integer v = m.verdicts.get(key);
            att.add(Slots.mount(it, ghost, key.equals(m.hover), v == null ? Slots.IDLE : v).hit(key, gun * 6 + s));
        }
        // align-content:center - the 2x26+5 block centred in the card's height
        Node wrap = col().justify(CENTER);
        wrap.add(att);
        return wrap;
    }

    public static final String[] DEFAULT_GHOSTS = {"scope", "muzzle", "laser", "grip", "mag", "stock"};

    /* ---- chest ---- */

    static Node chestPanel(Model m) {
        Chest ch = m.chest;
        Node p = panel(block()).tag("chest-panel");
        Node aside;
        if (ch.paged) {
            aside = aside();
            aside.add(pagerKey("›", "pg:-1", ch.page <= 0, m));
            aside.add(asideText("الصفحة"));
            aside.add(asideNum(String.valueOf(ch.page + 1)));
            aside.add(asideText("من"));
            aside.add(asideNum(String.valueOf(ch.pages)));
            aside.add(pagerKey("‹", "pg:1", ch.page >= ch.pages - 1, m));
        } else {
            int used = 0;
            for (Item it : ch.slots) {
                if (it != null) {
                    used++;
                }
            }
            aside = aside();
            aside.add(asideNum(used + " / " + ch.rows * 9));
            aside.add(asideText("خانة"));
            aside.add(Css.btn("خذ الكل", Css.BTN, true, false).hit("lootall"));
        }
        p.add(phead(ch.title, aside));
        Node g = grid(40, 40, 40, 40, 40, 40, 40, 40, 40).gap(4);
        for (int i = 0; i < ch.rows * 9; i++) {
            String key = "box:" + i;
            Item it = i < ch.slots.length ? ch.slots[i] : null;
            g.add(Slots.slot(it, 40, 28, key.equals(m.hover), stateOf(m, key), null).hit(key, i));
        }
        p.add(g);
        Node hint = txt("كل غرض في الصندوق ياخذ خانة وحدة، مهما كان حجمه في الحقيبة.", K(400), 11.5F, LH, Tok.FAINT);
        hint.mt = 10;
        p.add(hint);
        return p;
    }

    static Node pagerKey(String glyph, String id, boolean disabled, Model m) {
        Node k = Css.keycap(glyph, false, id.equals(m.hover) && !disabled ? Tok.BONE : Tok.MUTED, Tok.LINE);
        k.height = 22;
        k.minWidth = 22;
        k.kids.clear();
        k.add(Css.px(glyph, 600, 15, 1.0F, 0.0F, id.equals(m.hover) && !disabled ? Tok.BONE : Tok.MUTED));
        if (disabled) {
            k.opacity(0.35F);
        } else {
            k.hit(id);
        }
        return k;
    }

    /* ---- details ---- */

    static Node detailsPanel(Model m) {
        Node p = panel(block()).minH(150).tag("details-panel");
        Node aside = aside();
        aside.add(Css.keycap("Shift", true, Tok.MUTED, Tok.LINE));
        aside.add(asideText("+ نقر للنقل السريع"));
        p.add(phead("تفاصيل الغرض", aside));
        Item it = m.inspect;
        if (it == null) {
            Node e = txt("مرّر الماوس على أي غرض عشان تشوف تفاصيله.", K(400), 12.5F, LH, Tok.MUTED);
            e.mt = 14;
            e.mb = 14;
            p.add(e);
            return p;
        }
        int rc = Tok.rarity(it.rarity);
        Node body = grid(64, -1).gap(14).align(START);
        Node slot = Slots.slot(it, 64, 46, false, Slots.IDLE, null);
        body.add(slot);
        Node info = block();
        info.add(txt(it.name, K(700), 16, 1.4F, rc));
        Node meta = row().gap(6).wrap();
        meta.mt = 5;
        meta.add(Css.rarityChip(it.rarityName(), rc));
        if (it.category != null && !it.category.isEmpty()) {
            meta.add(Css.chip(it.category, Css.CHIP_MUTE));
        }
        if (it.extra != null && !it.extra.isEmpty()) {
            meta.add(Css.chip(it.extra, Css.CHIP_RUST));
        }
        info.add(meta);
        if (it.description != null && !it.description.isEmpty()) {
            Node d = txt(it.description, K(400), 12.5F, 1.85F, Tok.SOFT).wrapText();
            d.mt = 8;
            info.add(d);
        }
        Node price = row().align(CENTER).gap(6).wrap();
        price.mt = 8;
        if (it.price >= 0) {
            price.add(txt("سعر البيع", K(400), 12, LH, Tok.MUTED));
            price.add(cash());
            price.add(num(Chrome.fmt(it.price), 17, LH, Tok.AMBER));
            if (it.count > 1) {
                Node all = inline(K(400), 12, LH, Tok.FAINT);
                all.span(sp("للحبة · الكل ", K(400), 12, LH, Tok.FAINT));
                all.span(nsp(Chrome.fmt(it.price * it.count), 17, LH, Tok.AMBER));
                price.add(all);
            }
        } else {
            price.add(txt("ما ينباع عند التاجر", K(400), 12, LH, Tok.FAINT));
        }
        info.add(price);
        Node size = row().align(CENTER).gap(6).wrap();
        size.mt = 8;
        size.add(txt("الحجم في الحقيبة", K(400), 12, LH, Tok.MUTED));
        size.add(num(it.w + "x" + it.h, 17, LH, Tok.AMBER));
        size.add(txt("خانة", K(400), 12, LH, Tok.FAINT));
        info.add(size);
        body.add(info);
        p.add(body);
        return p;
    }

    /** {@code .cash}: the 20x14 banknote. */
    public static Node cash() {
        return Css.leaf(20, 14).under((c, n) -> Draw.raster(c, Art.sprite("cash"), n.x, n.y, n.w, n.h, 0xFFFFFFFF));
    }
}

package com.barbwra.mlum.client.ui.view;

import static com.barbwra.mlum.client.ui.view.Css.K;
import static com.barbwra.mlum.client.ui.view.Css.LH;
import static com.barbwra.mlum.client.ui.view.Css.block;
import static com.barbwra.mlum.client.ui.view.Css.col;
import static com.barbwra.mlum.client.ui.view.Css.grid;
import static com.barbwra.mlum.client.ui.view.Css.row;
import static com.barbwra.mlum.client.ui.view.Css.txt;

import com.barbwra.mlum.client.ui.Canvas;
import com.barbwra.mlum.client.ui.Draw;
import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.Tok;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.client.ui.mc.UiState;

/**
 * The faction vault: a strongroom door, not a chest.
 *
 * <pre>
 *   ┌ brass trim ───────────────────────────────────────┐
 *   │  خزنة                                    ( dial )  │  header: name in amber, combination dial
 *   │  اسم المنظمة                    ● مفتوحة          │
 *   │  [1] [2] [3] [4]                                   │  page drawers
 *   │ ▮┌──────────── interior ───────────────┐▮          │  bolts either side
 *   │ ▮│  9 x rows cells                     │▮          │
 *   │  └─────────────────────────────────────┘           │
 *   └ rivets ────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>Opening.</b> The first time a vault is opened the dial spins, the bolts draw back, and the
 * two leaves of an inner door slide apart over the cells (about a second). Turning a page skips the
 * door and only spins the dial - the vault is already open.</p>
 *
 * <p>Everything is drawn here with rectangles; there is no texture to ship or lose. The cells are
 * the bag's own slot nodes with the {@code box:} keys, so every click and drag is unchanged.</p>
 */
public final class VaultView {

    private VaultView() {
    }

    /** When the door animation started, or -1 for none. Set by the screen on open. */
    public static long doorAt = -1L;
    /** When the dial last started a spin (opening or a page turn). */
    public static long dialAt = -1L;
    /** The page-turn shutter: closing when a turn is asked for, opening on the new page. */
    private static long shutAt = -1L;
    private static long openAt = -1L;
    private static int shutDir = 1;

    /** A turn was clicked: slide the shutter across before the page is swapped. */
    public static void closeShutter(int dir) {
        shutDir = dir;
        shutAt = UiState.now();
        openAt = -1L;
    }

    /** The new page is up: slide the shutter away. */
    public static void openShutter() {
        shutAt = -1L;
        openAt = UiState.now();
    }

    private static final long DOOR_MS = 1250L;
    private static final long DIAL_MS = 620L;

    private static final int STEEL_HI = 0xFF23271F;
    private static final int STEEL = 0xFF171A15;
    private static final int STEEL_LO = 0xFF0D0F0B;
    private static final int FRAME = 0xFF3A3F35;
    private static final int BRASS = 0xFFB8862E;
    private static final int BRASS_DIM = 0xFF6E5120;

    /* ================================================================== layout */

    private static final float CELL = 56.0F;
    private static final float CELL_GAP = 6.0F;

    /**
     * The whole vault screen: no tab bar, the vault large on the left and the bag beside it on the
     * right - the vault is what this screen is for, the bag is only where things come from.
     */
    static Node screen(InvView.Model m) {
        Node main = block().abs(24, 22, 24, 22).tag("main");
        Node g = grid(456, -1).gap(26).align(Node.START);
        Node bag = col().gap(14);
        bag.add(InvView.bagPanel(m), InvView.quickPanel(m));
        g.add(bag, panel(m));
        main.add(g);
        return main;
    }

    static Node panel(InvView.Model m) {
        InvView.Chest ch = m.chest;
        long now = UiState.now();
        Node p = block().tag("vault-panel").pad(0);

        /* ---- header: the name, and the dial ---- */
        Node head = row().align(Node.CENTER).justify(Node.BETWEEN).pad(20, 26, 8, 26);
        Node names = col().gap(0);
        names.add(Css.px("SECURE VAULT · " + serial(ch.owner), 600, 10.0F, 1.2F, 0.18F, Tok.FAINT));
        names.add(txt("خزنة المنظمة", K(700), 26.0F, 1.35F, Tok.BONE));
        names.add(txt(ch.owner.isEmpty() ? "—" : ch.owner, K(600), 15.0F, 1.4F, Tok.AMBER));
        head.add(names);

        Node dialBox = row().align(Node.CENTER).gap(14);
        Node status = col().gap(2).align(Node.CENTER);
        boolean locked = doorAt >= 0 && now - doorAt < DOOR_MS * 45 / 100;
        Node led = Css.leaf(10, 10);
        led.under((c, n) -> {
            boolean shut = doorAt >= 0 && UiState.now() - doorAt < DOOR_MS * 45 / 100;
            int col = shut ? Tok.RUST : Tok.SAGE;
            disc(c, n.cx(), n.cy(), 7.0F, Draw.rgba(col & 0xFFFFFF, 0.18F));
            disc(c, n.cx(), n.cy(), 4.0F, col);
            disc(c, n.cx() - 1.0F, n.cy() - 1.0F, 1.3F, 0x90FFFFFF);
        });
        status.add(led);
        status.add(txt(locked ? "مقفلة" : "مفتوحة", K(600), 11.0F, 1.3F, locked ? Tok.RUST : Tok.SAGE));
        dialBox.add(status);
        Node dial = Css.leaf(72, 72);
        final int seed = ch.owner.hashCode();
        dial.under((c, n) -> paintDial(c, n.cx(), n.cy(), 33.0F, dialAngle(seed)));
        dialBox.add(dial);
        head.add(dialBox);
        p.add(head);

        /* ---- the money: rent on one side, the faction bank on the other ---- */
        Node money = row().align(Node.CENTER).justify(Node.BETWEEN).pad(0, 26, 10, 26);
        Node rent = row().align(Node.CENTER).gap(8);
        if (ch.pages > 1) {
            boolean paid = ch.rentLeft > 0;
            rent.add(txt(paid ? "الإيجار" : "الإيجار منتهي", K(600), 12.0F, 1.4F, paid ? Tok.MUTED : Tok.RUST));
            if (paid) {
                rent.add(txt(left(ch.rentLeft), K(700), 12.0F, 1.4F, Tok.SAGE));
            } else {
                rent.add(txt("· الصفحات من 2 مقفلة وأغراضها محفوظة", K(400), 11.0F, 1.4F, Tok.FAINT));
            }
            Node pay = Css.btn(paid ? "مدّد الإيجار" : "ادفع الإيجار", paid ? Css.BTN_GHOST : Css.BTN, true, false).hit("vrent.open");
            rent.add(pay);
        } else {
            rent.add(txt("صفحة وحدة مجانية · ارفعوا مستوى المنظمة عشان صفحات أكثر", K(400), 11.0F, 1.4F, Tok.FAINT));
        }
        money.add(rent);
        Node bank = row().align(Node.CENTER).gap(6);
        bank.add(txt("فلوس المنظمة", K(400), 11.5F, 1.4F, Tok.MUTED));
        bank.add(Css.num("$" + String.format(java.util.Locale.ROOT, "%,d", ch.bank), 17, 1.2F, Tok.CASH));
        money.add(bank);
        p.add(money);

        /* ---- page drawers ---- */
        Node tabs = row().align(Node.CENTER).justify(Node.CENTER).gap(8).pad(2, 26, 14, 26);
        for (int i = 0; i < Math.max(1, ch.pages); i++) {
            tabs.add(drawer(i, i == ch.page, i > 0 && ch.rentLeft <= 0 && !ch.op, m));
        }
        p.add(tabs);

        /* ---- the interior, with the big turn buttons either side ---- */
        float innerH = ch.rows * CELL + (ch.rows - 1) * CELL_GAP + 10;
        Node body = row().align(Node.CENTER).justify(Node.CENTER).gap(12);
        body.add(arrow(false, ch.page <= 0, innerH, m));
        Node interior = block().pad(5).tag("vault-interior");
        float[] cols = new float[9];
        java.util.Arrays.fill(cols, CELL);
        Node g = grid(cols).gap(CELL_GAP);
        for (int i = 0; i < ch.rows * 9; i++) {
            String key = "box:" + i;
            Item it = i < ch.slots.length ? ch.slots[i] : null;
            g.add(Slots.slot(it, CELL, 38, key.equals(m.hover), InvView.stateOf(m, key), null).hit(key, i));
        }
        interior.add(g);
        interior.under(VaultView::paintInterior);
        interior.over((c, n) -> {
            paintDoor(c, n);
            paintShutter(c, n);
        });
        body.add(interior);
        body.add(arrow(true, ch.page >= ch.pages - 1, innerH, m));
        p.add(body);

        /* ---- footer ---- */
        Node foot = row().align(Node.CENTER).justify(Node.BETWEEN).pad(14, 26, 16, 26);
        foot.add(txt("A و D يقلبون الصفحات · كل غرض ياخذ خانة وحدة · كل شي هنا للمنظمة كلها", K(400), 11.0F, LH, Tok.FAINT));
        foot.add(Css.px("MLUM", 700, 10.0F, 1.2F, 0.3F, BRASS_DIM));
        p.add(foot);

        p.under((c, n) -> paintBody(c, n, interior));
        p.over((c, n) -> paintFrame(c, n, interior));
        return p;
    }

    /** "12 يوم و 5 ساعات" - how long the rent has left. */
    private static String left(long ms) {
        long hours = ms / 3_600_000L;
        long days = hours / 24;
        hours %= 24;
        if (days > 0) {
            return "باقي " + days + " يوم" + (hours > 0 ? " و " + hours + " ساعة" : "");
        }
        long minutes = Math.max(1, ms / 60_000L % 60);
        return "باقي " + (hours > 0 ? hours + " ساعة و " : "") + minutes + " دقيقة";
    }

    /**
     * A tall steel turn button beside the interior. In this right-to-left screen the previous page
     * is on the right and the next one on the left.
     */
    private static Node arrow(boolean next, boolean disabled, float height, InvView.Model m) {
        String id = next ? "pg:1" : "pg:-1";
        boolean hot = id.equals(m.hover) && !disabled;
        Node a = col().align(Node.CENTER).justify(Node.CENTER).gap(10).size(50, height);
        Node chev = Css.leaf(22, 38);
        chev.under((c, n) -> chevron(c, n.x, n.y, n.w, n.h, !next, disabled ? 0x40A19E8B : hot ? Tok.AMBER : Tok.BONE));
        a.add(chev);
        a.add(txt(next ? "التالية" : "السابقة", K(600), 10.5F, 1.2F, disabled ? Tok.FAINT : hot ? Tok.AMBER : Tok.MUTED));
        a.under((c, n) -> {
            Draw.vgrad(c, n.x, n.y, n.w, n.h, new float[]{0.0F, 0.5F, 1.0F},
                    new int[]{hot ? 0xFF2F3429 : 0xFF23271F, hot ? 0xFF22261E : 0xFF181B15, 0xFF0F110D});
            outline(c, n.x, n.y, n.w, n.h, hot ? BRASS : FRAME);
            Draw.rect(c, n.x + 1, n.y + 1, n.w - 2, 1.0F, 0x14FFFFFF);
            rivet(c, n.cx(), n.y + 8);
            rivet(c, n.cx(), n.y + n.h - 8);
        });
        if (disabled) {
            a.opacity(0.45F);
        } else {
            a.hit(id);
        }
        return a;
    }

    /** A solid chevron out of rows, pointing right ({@code right}) or left. */
    private static void chevron(Canvas c, float x, float y, float w, float h, boolean right, int argb) {
        float thick = w * 0.42F;
        for (float dy = 0; dy < h; dy += 0.5F) {
            float t = Math.abs(dy - h / 2.0F) / (h / 2.0F);
            float tip = (1.0F - t) * (w - thick);
            float px = right ? x + tip : x + w - tip - thick;
            Draw.rect(c, px, y + dy, thick, 0.5F, argb);
        }
    }

    private static Node drawer(int index, boolean current, boolean locked, InvView.Model m) {
        String id = "vpg:" + index;
        boolean hot = id.equals(m.hover) && !current;
        Node d = row().align(Node.CENTER).justify(Node.CENTER).size(44, 30);
        if (locked) {
            Node lock = Css.leaf(10, 13);
            lock.under((c, n) -> padlock(c, n.x, n.y, hot ? Tok.AMBER : Tok.FAINT));
            d.add(lock);
            d.gap(4);
        }
        d.add(Css.num(String.valueOf(index + 1), 16, 1.0F, current ? Tok.INK : locked ? Tok.FAINT : (hot ? Tok.BONE : Tok.MUTED)));
        d.under((c, n) -> {
            if (current) {
                Draw.vgrad(c, n.x, n.y, n.w, n.h, new float[]{0.0F, 1.0F}, new int[]{0xFFE2A94A, BRASS});
                Draw.rect(c, n.x, n.y, n.w, 1.0F, 0x70FFFFFF);
            } else {
                Draw.vgrad(c, n.x, n.y, n.w, n.h, new float[]{0.0F, 1.0F},
                        new int[]{hot ? 0xFF2E3329 : locked ? 0xFF1A1C17 : 0xFF22261F, 0xFF121510});
                Draw.rect(c, n.x, n.y, n.w, 1.0F, 0x14FFFFFF);
            }
            outline(c, n.x, n.y, n.w, n.h, current ? 0xFF7A5718 : locked ? 0xFF4A2E22 : FRAME);
            Draw.rect(c, n.x + n.w / 2 - 6, n.y + n.h - 4, 12, 1.5F, current ? 0x80000000 : 0x40FFFFFF);
        });
        if (!current) {
            d.hit(id, index);
        }
        return d;
    }

    private static void padlock(Canvas c, float x, float y, int argb) {
        // the shackle, then the body
        outline(c, x + 2, y, 6, 6, argb);
        Draw.rect(c, x, y + 5, 10, 8, argb);
        Draw.rect(c, x + 4.5F, y + 7.5F, 1.0F, 3.0F, 0xFF121510);
    }

    /** A short serial from the faction's name, so each vault reads as its own. */
    private static String serial(String owner) {
        int h = Math.abs(owner.hashCode() % 9000) + 1000;
        return "MV-" + h;
    }

    /* ================================================================== the rent dialog */

    /**
     * Paying the rent: what it is for, what the faction has, and the plans - longer ones cheaper
     * per day. Only the leader and deputy can pay; everyone else sees it and why they cannot.
     */
    public static Node rentModal(InvView.Chest ch, String hover) {
        Node body = col().gap(12);
        int extra = Math.max(0, ch.pages - 1);
        long daily = (long) extra * com.barbwra.mlum.faction.FactionLevel.RENT_PER_PAGE_DAY;
        body.add(txt("كل صفحة بعد الأولى إيجارها " + money(com.barbwra.mlum.faction.FactionLevel.RENT_PER_PAGE_DAY)
                + " باليوم · عندكم " + extra + " صفحات = " + money(daily) + " باليوم", K(400), 12.0F, LH, Tok.SOFT).wrapText());
        body.add(txt(ch.rentLeft > 0 ? "الإيجار الحالي: " + left(ch.rentLeft) + " · أي مدة تدفعونها تنضاف عليه"
                : "الإيجار منتهي · الصفحات مقفلة لين تدفعون، وأغراضها ما تنمسح", K(400), 11.5F, LH,
                ch.rentLeft > 0 ? Tok.SAGE : Tok.RUST).wrapText());
        Node bank = row().align(Node.CENTER).gap(6);
        bank.add(txt("فلوس المنظمة", K(400), 12.0F, LH, Tok.MUTED));
        bank.add(Css.num(money(ch.bank), 17, 1.2F, Tok.CASH));
        body.add(bank);

        Node plans = grid(-1, -1).gap(10);
        int[] days = com.barbwra.mlum.faction.FactionLevel.RENT_DAYS;
        int[] off = com.barbwra.mlum.faction.FactionLevel.RENT_OFF;
        for (int i = 0; i < days.length; i++) {
            long cost = com.barbwra.mlum.faction.FactionLevel.rentCost(ch.level, i);
            long full = daily * days[i];
            boolean can = ch.canPay && cost > 0 && ch.bank >= cost;
            String id = "vrent:" + i;
            boolean hot = can && id.equals(hover);
            Node card = col().gap(4).pad(10, 12, 10, 12).bg(hot ? Tok.rgba(0xf0a93b, 0.10) : Tok.CARD)
                    .border(1.0F, hot ? Tok.AMBER : off[i] > 0 ? Tok.AMBER_DIM : Tok.LINE);
            Node top = row().align(Node.CENTER).justify(Node.BETWEEN);
            top.add(txt(days[i] == 1 ? "يوم واحد" : days[i] + (days[i] <= 10 ? " أيام" : " يوم"), K(700), 14.0F, 1.3F, Tok.BONE));
            if (off[i] > 0) {
                top.add(Css.chip("خصم " + off[i] + "%", Css.CHIP_AMBER));
            }
            card.add(top);
            card.add(Css.num(money(cost), 20, 1.1F, can ? Tok.CASH : Tok.FAINT));
            if (off[i] > 0) {
                card.add(txt("بدل " + money(full), K(400), 10.5F, 1.3F, Tok.FAINT));
            } else {
                card.add(txt(money(daily) + " × " + days[i], K(400), 10.5F, 1.3F, Tok.FAINT));
            }
            if (can) {
                card.hit(id);
            } else {
                card.opacity(0.6F);
            }
            plans.add(card);
        }
        body.add(plans);
        if (!ch.canPay) {
            body.add(txt("الدفع من فلوس المنظمة للقائد والنائب بس", K(600), 11.5F, LH, Tok.RUST));
        }
        return Overlays.modal("إيجار صفحات الخزنة", body, hover);
    }

    private static String money(long v) {
        return "$" + String.format(java.util.Locale.ROOT, "%,d", v);
    }

    /* ================================================================== painting */

    private static void paintBody(Canvas c, Node n, Node interior) {
        // brushed steel: a dark ramp and fine horizontal grain
        Draw.vgrad(c, n.x, n.y, n.w, n.h, new float[]{0.0F, 0.35F, 1.0F}, new int[]{0xF51F231C, 0xF5161914, 0xF50C0E0B});
        for (float y = n.y + 3; y < n.y + n.h - 3; y += 3.0F) {
            Draw.rect(c, n.x + 3, y, n.w - 6, 1.0F, ((int) y & 4) == 0 ? 0x07FFFFFF : 0x05000000);
        }
        // a soft amber light falling on the header
        Draw.rasterClipped(c, com.barbwra.mlum.client.ui.Art.radial(0.9F), n.x + n.w * 0.45F, n.y - 90, n.w * 0.7F, 200,
                n.x, n.y, n.x + n.w, n.y + n.h, Draw.rgba(0xF0A93B, 0.07F));
        // bolts either side of the interior, drawn back while the door unlocks
        float back = boltBack();
        for (int i = 0; i < 3; i++) {
            float by = interior.y + interior.h * (0.2F + 0.3F * i) - 9.0F;
            bolt(c, interior.x - 6.0F - back, by, 6.0F, 18.0F, true);
            bolt(c, interior.x + interior.w + back, by, 6.0F, 18.0F, false);
        }
    }

    private static void paintFrame(Canvas c, Node n, Node interior) {
        // outer frame, then an inner bevel
        outline(c, n.x, n.y, n.w, n.h, FRAME);
        outline(c, n.x + 2, n.y + 2, n.w - 4, n.h - 4, 0xFF050604);
        Draw.rect(c, n.x + 3, n.y + 3, n.w - 6, 1.0F, 0x12FFFFFF);
        // brass trim across the top and a thinner one at the foot
        Draw.vgrad(c, n.x, n.y, n.w, 4.0F, new float[]{0.0F, 1.0F}, new int[]{0xFFE0AB4E, BRASS_DIM});
        Draw.rect(c, n.x, n.y + n.h - 2, n.w, 2.0F, BRASS_DIM);
        // rivets: the corners, and along the top and bottom edges
        float inset = 9.0F;
        for (float x = n.x + inset; x <= n.x + n.w - inset + 0.1F; x += (n.w - inset * 2) / 6.0F) {
            rivet(c, x, n.y + inset + 2);
            rivet(c, x, n.y + n.h - inset);
        }
        rivet(c, n.x + inset, interior.y + interior.h / 2);
        rivet(c, n.x + n.w - inset, interior.y + interior.h / 2);
        // the two hinges on the door's right edge
        for (int i = 0; i < 2; i++) {
            float hy = n.y + n.h * (0.22F + 0.5F * i);
            Draw.hgrad(c, n.x + n.w - 3, hy, 5.0F, 34.0F, new float[]{0.0F, 1.0F}, new int[]{0xFF4A4F44, 0xFF1B1E18});
            Draw.rect(c, n.x + n.w - 3, hy + 16, 5.0F, 1.0F, 0xFF0A0B09);
        }
    }

    private static void paintInterior(Canvas c, Node n) {
        Draw.rect(c, n.x, n.y, n.w, n.h, 0xF5060705);
        // the recess: shadow falling in from the top and sides
        Draw.vgrad(c, n.x, n.y, n.w, 10.0F, new float[]{0.0F, 1.0F}, new int[]{0xB0000000, 0x00000000});
        Draw.hgrad(c, n.x, n.y, 8.0F, n.h, new float[]{0.0F, 1.0F}, new int[]{0x90000000, 0x00000000});
        Draw.hgrad(c, n.x + n.w - 8.0F, n.y, 8.0F, n.h, new float[]{0.0F, 1.0F}, new int[]{0x00000000, 0x90000000});
        outline(c, n.x - 1, n.y - 1, n.w + 2, n.h + 2, 0xFF2A2E26);
        Draw.rect(c, n.x - 1, n.y + n.h + 1, n.w + 2, 1.0F, 0x10FFFFFF);
        // a dim warm light from above, like a lamp inside the vault
        Draw.rasterClipped(c, com.barbwra.mlum.client.ui.Art.radial(0.85F), n.x + n.w * 0.2F, n.y - n.h * 0.5F, n.w * 0.6F, n.h,
                n.x, n.y, n.x + n.w, n.y + n.h, Draw.rgba(0xF0A93B, 0.05F));
    }

    /** The inner door: two leaves closed over the cells, then sliding apart. */
    private static void paintDoor(Canvas c, Node n) {
        if (doorAt < 0) {
            return;
        }
        float t = (UiState.now() - doorAt) / (float) DOOR_MS;
        if (t >= 1.0F) {
            doorAt = -1L;
            return;
        }
        float open = ease(clamp((t - 0.48F) / 0.52F));
        float half = n.w / 2.0F;
        c.pushClip(Px.d(n.x), Px.d(n.y), Px.d(n.x + n.w), Px.d(n.y + n.h));
        try {
            leaf(c, n.x - open * half, n.y, half, n.h, true);
            leaf(c, n.x + half + open * half, n.y, half, n.h, false);
            if (open <= 0.0F) {
                // the seam glows while the lock works
                float pulse = 0.5F + 0.5F * (float) Math.sin(t * 40.0F);
                Draw.rect(c, n.x + half - 1, n.y, 2.0F, n.h, Draw.rgba(0xF0A93B, 0.25F + 0.35F * pulse));
            }
        } finally {
            c.popClip();
        }
    }

    /** How much of the interior the shutter covers right now, 0..1. */
    private static float cover() {
        long now = UiState.now();
        if (openAt >= 0) {
            float t = (now - openAt) / 380.0F;
            if (t >= 1.0F) {
                openAt = -1L;
                return 0.0F;
            }
            return 1.0F - ease(t);
        }
        if (shutAt >= 0) {
            if (now - shutAt > 900L) {
                // the turn was refused and no new page came: give the view back
                shutAt = -1L;
                return 0.0F;
            }
            return ease(clamp((now - shutAt) / 160.0F));
        }
        return 0.0F;
    }

    /**
     * Steel slats sliding across the cells while the page changes - in from the side the turn came
     * from, away to the other - so the swap underneath is never seen.
     */
    private static void paintShutter(Canvas c, Node n) {
        float f = cover();
        if (f <= 0.0F) {
            return;
        }
        boolean closing = shutAt >= 0;
        float w = n.w * f;
        // next (dir > 0) comes from the left in this right-to-left screen
        boolean fromLeft = shutDir > 0 == closing;
        float x = fromLeft ? n.x : n.x + n.w - w;
        c.pushClip(Px.d(n.x), Px.d(n.y), Px.d(n.x + n.w), Px.d(n.y + n.h));
        try {
            Draw.rect(c, x, n.y, w, n.h, 0xFF151813);
            for (float sx = x; sx < x + w; sx += 14.0F) {
                float sw = Math.min(14.0F, x + w - sx);
                Draw.hgrad(c, sx, n.y, sw, n.h, new float[]{0.0F, 0.5F, 1.0F}, new int[]{0xFF2A2E27, 0xFF1D201A, 0xFF121410});
                Draw.rect(c, sx, n.y, 1.0F, n.h, 0x18FFFFFF);
            }
            // a brass leading edge
            float edge = fromLeft ? x + w - 3 : x;
            Draw.rect(c, edge, n.y, 3.0F, n.h, BRASS);
        } finally {
            c.popClip();
        }
    }

    private static void leaf(Canvas c, float x, float y, float w, float h, boolean left) {
        Draw.vgrad(c, x, y, w, h, new float[]{0.0F, 1.0F}, new int[]{0xFF2A2E27, 0xFF151813});
        for (float gy = y + 2; gy < y + h; gy += 3.0F) {
            Draw.rect(c, x, gy, w, 1.0F, 0x06FFFFFF);
        }
        outline(c, x, y, w, h, 0xFF0A0B09);
        // reinforcing bars
        for (int i = 1; i <= 2; i++) {
            float by = y + h * i / 3.0F - 3.0F;
            Draw.rect(c, x + 6, by, w - 12, 6.0F, 0xFF1C1F19);
            Draw.rect(c, x + 6, by, w - 12, 1.0F, 0x18FFFFFF);
            Draw.rect(c, x + 6, by + 5, w - 12, 1.0F, 0x60000000);
        }
        // rivets in the corners
        rivet(c, x + 7, y + 7);
        rivet(c, x + w - 7, y + 7);
        rivet(c, x + 7, y + h - 7);
        rivet(c, x + w - 7, y + h - 7);
        // the handle on the meeting edge
        float hx = left ? x + w - 12 : x + 8;
        Draw.vgrad(c, hx, y + h / 2 - 18, 4.0F, 36.0F, new float[]{0.0F, 1.0F}, new int[]{0xFFE0AB4E, BRASS_DIM});
        Draw.rect(c, hx, y + h / 2 - 18, 1.0F, 36.0F, 0x50FFFFFF);
        // the meeting edge itself
        Draw.rect(c, left ? x + w - 1 : x, y, 1.0F, h, left ? 0x30FFFFFF : 0xFF000000);
    }

    private static void paintDial(Canvas c, float cx, float cy, float r, float angle) {
        disc(c, cx, cy + 2.0F, r + 1.0F, 0x70000000);
        disc(c, cx, cy, r, 0xFF4A4F44);
        disc(c, cx, cy, r - 1.5F, 0xFF121410);
        disc(c, cx, cy, r - 3.0F, 0xFF262A23);
        // the graduations, turning with the dial
        for (int i = 0; i < 40; i++) {
            double a = Math.toRadians(angle + i * 9.0F);
            boolean major = i % 5 == 0;
            float rr = r - (major ? 6.5F : 5.5F);
            float s = major ? 2.0F : 1.0F;
            int col = i == 0 ? Tok.AMBER : (major ? 0xFFB9B5A2 : 0xFF6E6D5F);
            Draw.rect(c, cx + (float) Math.sin(a) * rr - s / 2, cy - (float) Math.cos(a) * rr - s / 2, s, s, col);
        }
        // the knob and its grip
        disc(c, cx, cy, r * 0.46F, 0xFF0E100C);
        disc(c, cx, cy, r * 0.42F, 0xFF3C4137);
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(angle + i * 22.5F);
            Draw.rect(c, cx + (float) Math.sin(a) * r * 0.40F - 0.75F, cy - (float) Math.cos(a) * r * 0.40F - 0.75F, 1.5F, 1.5F,
                    0xFF16190F);
        }
        disc(c, cx, cy, r * 0.28F, 0xFF2D3129);
        disc(c, cx - r * 0.08F, cy - r * 0.1F, r * 0.14F, 0x26FFFFFF);
        // the fixed index mark at the top
        Draw.rect(c, cx - 1.5F, cy - r - 5.0F, 3.0F, 6.0F, Tok.AMBER);
        Draw.rect(c, cx - 0.5F, cy - r - 5.0F, 1.0F, 6.0F, 0x60FFFFFF);
    }

    /* ================================================================== pieces */

    private static void bolt(Canvas c, float x, float y, float w, float h, boolean left) {
        Draw.vgrad(c, x, y, w, h, new float[]{0.0F, 0.45F, 1.0F}, new int[]{0xFF5C6156, 0xFF3A3E35, 0xFF1E211B});
        outline(c, x, y, w, h, 0xFF0A0B09);
        Draw.rect(c, left ? x + w - 2 : x, y + 1, 2.0F, h - 2, 0x40000000);
    }

    private static void rivet(Canvas c, float x, float y) {
        disc(c, x, y + 0.6F, 2.4F, 0x90000000);
        disc(c, x, y, 2.2F, 0xFF4E5348);
        disc(c, x - 0.6F, y - 0.6F, 1.0F, 0x50FFFFFF);
    }

    private static void outline(Canvas c, float x, float y, float w, float h, int argb) {
        Draw.rect(c, x, y, w, 1.0F, argb);
        Draw.rect(c, x, y + h - 1, w, 1.0F, argb);
        Draw.rect(c, x, y + 1, 1.0F, h - 2, argb);
        Draw.rect(c, x + w - 1, y + 1, 1.0F, h - 2, argb);
    }

    /** A filled circle out of half-pixel rows. */
    static void disc(Canvas c, float cx, float cy, float r, int argb) {
        if (r <= 0.0F) {
            return;
        }
        for (float dy = -r; dy < r; dy += 0.5F) {
            float mid = dy + 0.25F;
            float half = (float) Math.sqrt(Math.max(0.0F, r * r - mid * mid));
            if (half > 0.1F) {
                Draw.rect(c, cx - half, cy + dy, half * 2.0F, 0.5F, argb);
            }
        }
    }

    /* ================================================================== timing */

    /** The dial's resting angle is the faction's own; a spin adds two turns and settles back on it. */
    private static float dialAngle(int seed) {
        float rest = Math.floorMod(seed, 40) * 9.0F;
        if (dialAt < 0) {
            return rest;
        }
        float t = clamp((UiState.now() - dialAt) / (float) DIAL_MS);
        if (t >= 1.0F) {
            dialAt = -1L;
            return rest;
        }
        // fast, then a little back the other way, like a real combination
        float spin = t < 0.75F ? ease(t / 0.75F) * 720.0F : 720.0F - (float) Math.sin((t - 0.75F) / 0.25F * Math.PI) * 27.0F;
        return rest + spin;
    }

    /** How far the bolts are drawn back: out while locked, sliding home as the door opens. */
    private static float boltBack() {
        if (doorAt < 0) {
            return 4.0F;
        }
        float t = (UiState.now() - doorAt) / (float) DOOR_MS;
        return 4.0F * ease(clamp((t - 0.22F) / 0.25F));
    }

    private static float clamp(float v) {
        return v < 0.0F ? 0.0F : Math.min(1.0F, v);
    }

    private static float ease(float t) {
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0D) / 2.0F;
    }
}

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

    private static final long DOOR_MS = 1250L;
    private static final long DIAL_MS = 620L;

    private static final int STEEL_HI = 0xFF23271F;
    private static final int STEEL = 0xFF171A15;
    private static final int STEEL_LO = 0xFF0D0F0B;
    private static final int FRAME = 0xFF3A3F35;
    private static final int BRASS = 0xFFB8862E;
    private static final int BRASS_DIM = 0xFF6E5120;

    /* ================================================================== layout */

    static Node panel(InvView.Model m) {
        InvView.Chest ch = m.chest;
        long now = UiState.now();
        Node p = block().tag("vault-panel").pad(0);

        /* ---- header: the name, and the dial ---- */
        Node head = row().align(Node.CENTER).justify(Node.BETWEEN).pad(18, 22, 8, 22);
        Node names = col().gap(0);
        names.add(Css.px("SECURE VAULT · " + serial(ch.owner), 600, 9.5F, 1.2F, 0.18F, Tok.FAINT));
        names.add(txt("خزنة المنظمة", K(700), 22.0F, 1.35F, Tok.BONE));
        names.add(txt(ch.owner.isEmpty() ? "—" : ch.owner, K(600), 13.5F, 1.4F, Tok.AMBER));
        head.add(names);

        Node dialBox = row().align(Node.CENTER).gap(12);
        Node status = col().gap(2).align(Node.CENTER);
        boolean locked = doorAt >= 0 && now - doorAt < DOOR_MS * 45 / 100;
        Node led = Css.leaf(9, 9);
        led.under((c, n) -> {
            boolean shut = doorAt >= 0 && UiState.now() - doorAt < DOOR_MS * 45 / 100;
            int col = shut ? Tok.RUST : Tok.SAGE;
            disc(c, n.cx(), n.cy(), 6.5F, Draw.rgba(col & 0xFFFFFF, 0.18F));
            disc(c, n.cx(), n.cy(), 3.5F, col);
            disc(c, n.cx() - 1.0F, n.cy() - 1.0F, 1.2F, 0x90FFFFFF);
        });
        status.add(led);
        status.add(txt(locked ? "مقفلة" : "مفتوحة", K(600), 10.5F, 1.3F, locked ? Tok.RUST : Tok.SAGE));
        dialBox.add(status);
        Node dial = Css.leaf(62, 62);
        final int seed = ch.owner.hashCode();
        dial.under((c, n) -> paintDial(c, n.cx(), n.cy(), 29.0F, dialAngle(seed)));
        dialBox.add(dial);
        head.add(dialBox);
        p.add(head);

        /* ---- page drawers ---- */
        Node tabs = row().align(Node.CENTER).gap(6).pad(2, 22, 12, 22);
        for (int i = 0; i < Math.max(1, ch.pages); i++) {
            tabs.add(drawer(i, i == ch.page, m));
        }
        Node grow = Css.leaf(1, 1).grow(1);
        tabs.add(grow);
        int used = 0;
        for (int i = 0; i < ch.rows * 9 && i < ch.slots.length; i++) {
            if (ch.slots[i] != null) {
                used++;
            }
        }
        tabs.add(Css.asideNum(used + " / " + ch.rows * 9));
        tabs.add(Css.asideText("خانة"));
        p.add(tabs);

        /* ---- the interior ---- */
        Node interior = block().mar(0, 10, 0, 10).pad(5).tag("vault-interior");
        Node g = grid(40, 40, 40, 40, 40, 40, 40, 40, 40).gap(4);
        for (int i = 0; i < ch.rows * 9; i++) {
            String key = "box:" + i;
            Item it = i < ch.slots.length ? ch.slots[i] : null;
            g.add(Slots.slot(it, 40, 28, key.equals(m.hover), InvView.stateOf(m, key), null).hit(key, i));
        }
        interior.add(g);
        interior.under(VaultView::paintInterior);
        interior.over(VaultView::paintDoor);
        p.add(interior);

        /* ---- footer ---- */
        Node foot = row().align(Node.CENTER).justify(Node.BETWEEN).pad(10, 22, 14, 22);
        foot.add(txt("كل غرض ياخذ خانة وحدة · كل شي هنا للمنظمة كلها", K(400), 11.0F, LH, Tok.FAINT));
        foot.add(Css.px("MLUM", 700, 10.0F, 1.2F, 0.3F, BRASS_DIM));
        p.add(foot);

        p.under((c, n) -> paintBody(c, n, interior));
        p.over((c, n) -> paintFrame(c, n, interior));
        return p;
    }

    private static Node drawer(int index, boolean current, InvView.Model m) {
        String id = "vpg:" + index;
        boolean hot = id.equals(m.hover) && !current;
        Node d = row().align(Node.CENTER).justify(Node.CENTER).size(34, 24);
        d.add(Css.num(String.valueOf(index + 1), 14, 1.0F, current ? Tok.INK : (hot ? Tok.BONE : Tok.MUTED)));
        d.under((c, n) -> {
            if (current) {
                Draw.vgrad(c, n.x, n.y, n.w, n.h, new float[]{0.0F, 1.0F}, new int[]{0xFFE2A94A, BRASS});
                Draw.rect(c, n.x, n.y, n.w, 1.0F, 0x70FFFFFF);
            } else {
                Draw.vgrad(c, n.x, n.y, n.w, n.h, new float[]{0.0F, 1.0F}, new int[]{hot ? 0xFF2E3329 : 0xFF22261F, 0xFF121510});
                Draw.rect(c, n.x, n.y, n.w, 1.0F, 0x14FFFFFF);
            }
            outline(c, n.x, n.y, n.w, n.h, current ? 0xFF7A5718 : FRAME);
            // the drawer pull
            Draw.rect(c, n.x + n.w / 2 - 5, n.y + n.h - 4, 10, 1.5F, current ? 0x80000000 : 0x40FFFFFF);
        });
        if (!current) {
            d.hit(id, index);
        }
        return d;
    }

    /** A short serial from the faction's name, so each vault reads as its own. */
    private static String serial(String owner) {
        int h = Math.abs(owner.hashCode() % 9000) + 1000;
        return "MV-" + h;
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
            bolt(c, interior.x - 9.0F - back, by, 9.0F, 18.0F, true);
            bolt(c, interior.x + interior.w + back, by, 9.0F, 18.0F, false);
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
            return 5.0F;
        }
        float t = (UiState.now() - doorAt) / (float) DOOR_MS;
        return 5.0F * ease(clamp((t - 0.22F) / 0.25F));
    }

    private static float clamp(float v) {
        return v < 0.0F ? 0.0F : Math.min(1.0F, v);
    }

    private static float ease(float t) {
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0D) / 2.0F;
    }
}

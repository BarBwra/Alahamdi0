package com.barbwra.mlum.client.ui.view;

import com.barbwra.mlum.client.ui.Art;
import com.barbwra.mlum.client.ui.Canvas;
import com.barbwra.mlum.client.ui.Draw;
import com.barbwra.mlum.client.ui.Ease;
import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.Tok;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.client.ui.layout.Span;

import static com.barbwra.mlum.client.ui.layout.Node.AUTO;
import static com.barbwra.mlum.client.ui.layout.Node.CENTER;
import static com.barbwra.mlum.client.ui.view.Css.K;
import static com.barbwra.mlum.client.ui.view.Css.LH;
import static com.barbwra.mlum.client.ui.view.Css.P;
import static com.barbwra.mlum.client.ui.view.Css.chip;
import static com.barbwra.mlum.client.ui.view.Css.icon;
import static com.barbwra.mlum.client.ui.view.Css.keycap;
import static com.barbwra.mlum.client.ui.view.Css.row;
import static com.barbwra.mlum.client.ui.view.Css.txt;

/** The frame every tab shares: scrim, top bar (tabs, wallet, zone), footer. */
public final class Chrome {

    private Chrome() {
    }

    public static final String[] TAB_IDS = {"inventory", "quests", "level", "skills", "vehicles", "faction"};
    public static final String[] TAB_NAMES = {"الحقيبة", "المهام", "المستوى", "المهارات", "المركبات", "المنظمة"};
    public static final String[] TAB_ICONS = {"bag", "quest", "rank", "bolt", "car", "flag"};

    /** Everything the top bar shows. */
    public static final class Bar {
        public int selected;
        public int questBadge = -1;
        public boolean skillsNew;
        /** Wallet as currently displayed (counts up to the real value). */
        public long shown;
        /** Which key cap is flashing: 'a', 'd' or 0. */
        public char keyHit;
        public String zone;
        public String zoneTag;
        public int zoneTagKind = Css.CHIP_RUST;
        /** The rank beside the wallet: its name as drawn, and the colour the config gave it. */
        public String rank = "";
        public int rankColor = Tok.MUTED;
        public boolean plainWallet;
        public boolean lite;
        /** Wallet animation clocks, ms. */
        public long nowMs;
        public long deltaAtMs = -1;
        public long delta;
        /** Hovered hit id, for hover colours. */
        public String hover;
    }

    /* ================================================================== scrim */

    /**
     * The shade between the world and the menu. The bag keeps a window open in the middle so the
     * player still sees what is behind them; every other tab is a flat 78%.
     */
    public static void scrim(Canvas c, boolean inventory, float strength) {
        int x0 = 0;
        int y0 = 0;
        int x1 = Px.d(Px.W);
        int y1 = Px.d(Px.H);
        if (inventory) {
            float[] stops = {0.0F, 0.30F, 0.50F, 0.70F, 1.0F};
            float[] alphas = {0.88F, 0.76F, 0.30F, 0.76F, 0.88F};
            int[] colors = new int[5];
            for (int i = 0; i < 5; i++) {
                colors[i] = Draw.rgba(0x050705, Math.min(0.97F, alphas[i] * strength));
            }
            Draw.hgrad(c, 0, 0, Px.W, Px.H, stops, colors);
        } else {
            c.fill(x0, y0, x1, y1, Draw.rgba(0x050705, Math.min(0.97F, 0.78F * strength)));
        }
    }

    /** {@code .main}: between the bars, 24px in from the sides, 16px top and bottom padding. */
    public static Node main() {
        return Css.block().abs(24, 64, 24, 36).pad(16, 0, 16, 0).tag("main");
    }

    /* ================================================================== top bar */

    public static Node topBar(Bar m) {
        Node bar = row().align(Node.STRETCH).gap(10).h(64).tag("topbar");
        bar.pad(0, 18, 0, 20).border(0, 0, 1, 0, Tok.LINE);
        bar.vgrad(new float[]{0, 1}, new int[]{Draw.rgba(0x040604, 0.94F), Draw.rgba(0x040604, 0.72F)});
        bar.abs(0, 0, 0, AUTO);

        bar.add(navKey("d", m).self(CENTER));
        Node tabs = row().align(Node.STRETCH).gap(2);
        for (int i = 0; i < TAB_IDS.length; i++) {
            tabs.add(tab(i, m));
        }
        bar.add(tabs);
        bar.add(navKey("a", m).self(CENTER));

        Node status = row().align(CENTER).gap(10).tag("status");
        status.mr = 14;
        status.add(wallet(m));
        // The zone used to live here. It said the same two things all day and was never acted on;
        // the rank is a thing the player owns, and the button behind it opens the store.
        status.add(rankPill(m));
        bar.add(status);
        return bar;
    }

    /* ---- the rank ---- */

    /**
     * {@code .pill}, in the rank's own colour, and a button.
     *
     * <p>Drawn from the colour the config gives each rank rather than from a palette token, because
     * the whole point of a rank is that it is recognisable at a glance across the server - so the
     * server operator picks it, not the theme.</p>
     */
    private static Node rankPill(Bar m) {
        boolean hover = "rank".equals(m.hover);
        int color = m.rankColor;
        String name = m.rank == null || m.rank.isEmpty() ? "—" : m.rank;
        Node p = row().align(CENTER).gap(8).h(34).pad(0, 12, 0, 12)
                .border(1.0F, hover ? color : Tok.mix(color, Tok.LINE, 0.55))
                .bg(Tok.rgba(color & 0xFFFFFF, hover ? 0.16 : 0.08))
                .hit("rank").tag("pill");
        p.add(icon("rank", 14, 14, color));
        p.add(Css.px(name, 700, 15, 1.0F, 0.05F, color));
        return p;
    }

    private static Node navKey(String key, Bar m) {
        boolean hit = m.keyHit == key.charAt(0);
        boolean hover = ("nav:" + key).equals(m.hover);
        int color = hit ? Tok.AMBER : hover ? Tok.BONE : Tok.MUTED;
        int border = hit ? Tok.AMBER : Tok.LINE;
        return keycap(key.toUpperCase(), false, color, border).hit("nav:" + key);
    }

    private static Node tab(int i, Bar m) {
        boolean sel = i == m.selected;
        boolean hover = ("tab:" + TAB_IDS[i]).equals(m.hover);
        int color = sel ? Tok.AMBER : hover ? Tok.BONE : Tok.MUTED;
        Node t = row().align(CENTER).gap(9).pad(0, 15, 0, 15).hit("tab:" + TAB_IDS[i], i);
        t.add(icon(TAB_ICONS[i], 16, 16, color));
        t.add(txt(TAB_NAMES[i], K(600), 15, 1.0F, color));
        if (i == 1 && m.questBadge >= 0) {
            Node badge = row().align(CENTER).justify(CENTER).minW(18).h(18).pad(0, 4, 0, 4).bg(Tok.AMBER);
            badge.add(Css.px(String.valueOf(m.questBadge), 700, 14, 1.0F, 0.0F, Tok.INK));
            t.add(badge);
        }
        if (i == 3 && m.skillsNew) {
            Node nw = row().align(CENTER).pad(4, 5, 3, 5).bg(Tok.AMBER);
            nw.add(txt("جديد", K(600), 10, 1.0F, Tok.INK));
            t.add(nw);
        }
        if (sel) {
            t.under((c, n) -> {
                // linear-gradient(to top, amber-glow, transparent 75%)
                Draw.vgrad(c, n.x, n.y, n.w, n.h, new float[]{0.0F, 0.25F, 1.0F},
                        new int[]{0x00F0A93B, 0x00F0A93B, Tok.AMBER_GLOW});
            });
            t.over((c, n) -> Draw.rect(c, n.x + 10, n.y + n.h - 2 + 1, n.w - 20, 2, Tok.AMBER));
        }
        return t;
    }

    /* ---- the wallet ---- */

    public static int tier(long v) {
        return v >= 1_000_000L ? 4 : v >= 100_000L ? 3 : v >= 10_000L ? 2 : v >= 1_000L ? 1 : 0;
    }

    public static String fmt(long v) {
        return String.format(java.util.Locale.US, "%,d", v);
    }

    private static Node wallet(Bar m) {
        int tier = m.plainWallet ? 0 : tier(m.shown);
        boolean plain = m.plainWallet;
        Node w = row().align(CENTER).gap(9).h(36).pad(0, 12, 0, 14);
        if (plain) {
            w.border(1.0F, Tok.LINE).bg(Tok.rgba(0x0a0c09, 0.75));
        } else {
            w.border(1.0F, tier == 4 ? Tok.rgb(0x4e8a3a) : Tok.CASH_LINE);
            w.vgrad(new float[]{0, 1}, new int[]{Tok.rgb(0x10190c), Tok.rgb(0x0a1007)});
        }
        w.hit("wallet").tag("wallet");

        Node coin = Css.leaf(24, 22).tag("coin").under((c, n) -> Draw.raster(c, Art.sprite("bill"), n.x, n.y, n.w, n.h, 0xFFFFFFFF));
        w.add(coin);

        String amount = fmt(m.shown);
        Node amt = new Node(Node.TEXT);
        float fs = plain ? 19 : 22;
        int weight = plain ? 600 : 700;
        amt.font = P(weight);
        amt.size = fs;
        amt.lh = fs;
        amt.rtl = false;
        Span s = Span.text(amount, P(weight), fs, fs, Tok.BONE).ltr().spacing(0.03F * fs);
        if (tier == 1) {
            s.color = Tok.rgb(0x9bd67a);
        } else if (tier >= 2) {
            // background-clip:text over the element's own 22px box
            float[] ab = com.barbwra.mlum.client.ui.layout.Layout.inlineBox(P(weight), fs, fs);
            s.gradient(-ab[0], ab[1], new float[]{0.0F, 0.48F, 1.0F},
                    new int[]{Tok.CASH_HI, Tok.CASH, Tok.CASH_LO});
        }
        amt.span(s);
        amt.tag("wamt");
        w.add(amt);

        final int t = tier;
        final boolean lite = m.lite;
        final long now = m.nowMs;
        w.over((c, n) -> walletOverlay(c, n, t, plain, lite, now));
        if (m.deltaAtMs >= 0 && m.delta != 0) {
            long age = m.nowMs - m.deltaAtMs;
            if (age >= 0 && age < 1250) {
                w.add(walletDelta(m.delta, age));
            }
        }
        return w;
    }

    /** ::before highlight, the shine sweep (t3+), the t4 ring and sparks. */
    private static void walletOverlay(Canvas c, Node n, int tier, boolean plain, boolean lite, long now) {
        if (plain) {
            return;
        }
        // ::before: 1px of pale green along the top of the padding box
        Draw.rect(c, n.x + n.bl, n.y + n.bt, n.w - n.bl - n.br, 1, Draw.rgba(0xBEFFAA, 0.12F));
        if (tier == 4) {
            // box-shadow: inset 0 0 0 1px rgba(147,196,111,.2)
            Css.innerRing(c, n, Draw.rgba(0x93C46F, 0.2F));
        }
        if (tier >= 3 && !lite) {
            // @keyframes shine: 5s loop, crosses in the first 14%, hidden the rest
            float t = (now % 5000L) / 5000.0F;
            if (t < 0.14F) {
                float px0 = n.x + n.bl;
                float pw = n.w - n.bl - n.br;
                float left = -24 + (pw + 2 + 24) * (t / 0.14F);
                float x = px0 + left;
                float y = n.y + n.bt;
                float h = n.h - n.bt - n.bb;
                // linear-gradient(90deg, transparent, rgba(215,255,205,.22), transparent), 22px wide
                float cx0 = Math.max(x, px0);
                float cx1 = Math.min(x + 22, px0 + pw);
                if (cx1 > cx0) {
                    c.pushClip(Px.d(px0), Px.d(y), Px.d(px0 + pw), Px.d(y + h));
                    Draw.hgrad(c, x, y, 22, h, new float[]{0.0F, 0.5F, 1.0F},
                            new int[]{0x00D7FFCD, Draw.rgba(0xD7FFCD, 0.22F), 0x00D7FFCD});
                    c.popClip();
                }
            }
        }
        if (tier == 4 && !lite) {
            // two sparks twinkling 2.4s apart by half a period
            // .spark.a {top:-5px; left:-5px}, .spark.b {bottom:-5px; right:34px} - padding box
            spark(c, n.x + n.bl - 5, n.y + n.bt - 5, twinkle(now, 0));
            spark(c, n.x + n.w - n.br - 34 - 10, n.y + n.h - n.bb - 5, twinkle(now, 1200));
        }
    }

    /** @keyframes twinkle, 2.4s ease-in-out: 0 until 45%, 1 until 55%, back to 0. */
    private static float twinkle(long now, long delay) {
        float p = Math.floorMod(now - delay, 2400L) / 2400.0F;
        if (p < 0.45F) {
            return Ease.easeInOut(p / 0.45F);
        }
        if (p <= 0.55F) {
            return 1.0F;
        }
        return 1.0F - Ease.easeInOut((p - 0.55F) / 0.45F);
    }

    private static void spark(Canvas c, float x, float y, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        Draw.raster(c, Art.sprite("spark"), x, y, 10, 10, Draw.rgba(0xFFFFFF, alpha));
    }

    /** {@code .wdelta}: the change floating down under the wallet for 1.25s. */
    private static Node walletDelta(long delta, long age) {
        float t = age / 1250.0F;
        // @keyframes dfloat with ease-out per segment: opacity 0 -> 1 by 14% -> 0; y -4 -> 12
        float alpha = t < 0.14F ? Ease.easeOut(t / 0.14F) : 1.0F - Ease.easeOut((t - 0.14F) / 0.86F);
        float dy = -4.0F + 16.0F * Ease.easeOut(t);
        String txt = (delta > 0 ? "+" : "\u2212") + fmt(Math.abs(delta));
        int color = delta > 0 ? Tok.rgb(0xa6e37f) : Tok.RUST;
        Node d = Css.px(txt, 600, 16, 1.0F, 0.02F, color);
        // top: calc(100% + 4px); left: 50%; transform: translateX(-50%)
        d.abs(0, 4 + dy, AUTO, AUTO).pct(0.5F, 1.0F, 0, 0).translate(-0.5F, 0);
        d.opacity = Math.max(0.0F, Math.min(1.0F, alpha));
        return d;
    }

    /* ---- the zone ---- */

    private static Node zonePill(Bar m) {
        Node p = row().align(CENTER).gap(8).h(34).pad(0, 12, 0, 12).border(1.0F, Tok.LINE)
                .bg(Tok.rgba(0x0a0c09, 0.75)).tag("pill");
        p.add(icon("pin", 16, 16, Tok.MUTED));
        p.add(txt(m.zone, K(400), 12.5F, LH, Tok.BONE));
        if (m.zoneTag != null && !m.zoneTag.isEmpty()) {
            p.add(chip(m.zoneTag, m.zoneTagKind).tag("pill-chip"));
        }
        return p;
    }

    /* ================================================================== footer */

    public static Node footer(boolean credit) {
        return footer(credit, false);
    }

    /** @param bagKeys the bag's own gestures, which mean nothing on the other tabs */
    public static Node footer(boolean credit, boolean bagKeys) {
        Node f = row().align(CENTER).gap(20).h(36).pad(0, 24, 0, 24).border(1, 0, 0, 0, Tok.LINE_SOFT)
                .bg(Draw.rgba(0x040604, 0.88F));
        f.abs(0, AUTO, 0, 0).tag("footer");
        f.add(hint(new String[]{"ESC"}, "إغلاق"));
        f.add(hint(new String[]{"A", "D"}, "تبديل القوائم"));
        f.add(hint(new String[]{"Shift"}, "+ نقر: نقل سريع"));
        if (bagKeys) {
            f.add(hint(new String[]{"Q"}, "رمي الغرض"));
            // no key cap: the cap font is the pixel face, which has no Arabic glyphs
            f.add(hint(new String[]{}, "كلك يمين: حبة حبة · دبل كلك: يلمّ الكل"));
        }
        if (credit) {
            Node cr = Css.px("Created by BarBwra", 600, 15, 1.0F, 0.06F, Tok.AMBER_DIM);
            cr.ml = AUTO;
            cr.tag("credit");
            f.add(cr);
        }
        return f;
    }

    /**
     * {@code .footer .hint}. The design's later {@code .hint} rule (meant for the faction screen)
     * also matches these, so they carry its 12px top margin and faint colour - which is exactly how
     * the page looks, so it is copied as is.
     */
    private static Node hint(String[] keys, String label) {
        Node h = row().align(CENTER).gap(7).tag("hint");
        h.mt = 12;
        for (String k : keys) {
            h.add(keycap(k, true, Tok.MUTED, Tok.LINE));
        }
        h.add(txt(label, K(400), 11.5F, LH, Tok.FAINT));
        return h;
    }
}

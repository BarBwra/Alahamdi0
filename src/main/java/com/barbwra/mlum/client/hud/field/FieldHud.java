package com.barbwra.mlum.client.hud.field;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.client.ClientLevelData;
import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.client.hud.HudVisibility;
import com.barbwra.mlum.client.hud.LevelUpToast;
import com.barbwra.mlum.client.hud.ZoneToast;
import com.barbwra.mlum.client.ui.mc.UiText;
import com.barbwra.mlum.client.ui.text.Shaped;
import com.barbwra.mlum.compat.SbwCompat;
import com.barbwra.mlum.compat.TaczAttachments;
import com.barbwra.mlum.compat.TaczCompat;
import com.barbwra.mlum.util.ChargeTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The field HUD: everything the vanilla HUD showed, redrawn as a survivor's kit.
 *
 * <pre>
 *                         [ SW ''|''' W '''|'' NW ]
 *                                 [ 229 ]
 *
 *  +--LV 7 · ---------------\                            /-----------M4A1-+
 *  | [face] [~~ heartbeat ~~]  (shield)    M4A1        | AUTO    [ gun   ] |
 *  |                           ( 100  )               | 28/120  [ art   ] |
 *  \ food =============== -------+  [][][][##][][][][]  +|||||||||||||||||||+
 * </pre>
 *
 * <p><b>Two devices and a belt.</b> Bottom left is a wrist unit: your own face, a heartbeat written
 * across a small screen the way a hospital monitor writes it, faster and redder the less health is
 * left, the health number inside a shield whose ten pieces are the armour - the armour protects the
 * health, so the health sits inside it - and the food bar underneath. Its top edge is the level bar.
 * Bottom right is its mirror for whatever is in hand. The belt between them is the hotbar: all nine
 * cells side by side, always, the selected one marked - nothing slides or grows.</p>
 *
 * <p>{@code fieldHudScale} in the client config sizes the whole thing; each part grows from its own
 * corner of the screen.</p>
 *
 * <p><b>One look for both screens.</b> They are monochrome in the HUD's own colour, the heartbeat
 * and the gun alike, so the two units read as one kit rather than two widgets that happen to share
 * a screen. Colour is spent only where it means something: red when health or ammunition is low,
 * wheat for food, steel for armour, yellow for stored charge.</p>
 *
 * <p>Laid out in GUI pixels, so GUI Scale resizes it the way it resizes the vanilla HUD. Every frame
 * is built from live player state - nothing is cached that could fall out of step with the game.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class FieldHud {

    private FieldHud() {
    }

    private static final HudPen PEN = new HudPen();

    static final int BONE = 0xFFECE6D4;
    static final int MUTED = 0xFFA19E8B;
    static final int FAINT = 0xFF6B6A5C;
    static final int RUST = 0xFFD9623F;
    static final int WHEAT = 0xFFD2A95E;
    static final int STEEL = 0xFF9DB4C4;
    static final int VOLT = 0xFFE8C547;
    static final int INK = 0xFF1A1D17;

    /* the wrist device and the weapon panel - the panel deliberately the smaller of the two */
    private static final int WRIST_W = 136;
    private static final int WRIST_H = 42;
    private static final int SLAB_W = 100;
    private static final int SLAB_H = 38;
    private static final int MARGIN = 6;

    private static final Ecg ECG = new Ecg(62);
    private static final ArmorEmblem EMBLEM = new ArmorEmblem();

    private static long lastFrame = -1L;
    private static float lastHealth = -1.0F;
    private static long hitAt = -10_000L;

    public static void reset() {
        lastFrame = -1L;
        lastHealth = -1.0F;
        EMBLEM.reset();
    }

    /* ================================================================== frame */

    public static void render(GuiGraphics graphics, float partialTick, int width, int height) {
        if (!MlumConfig.fieldHud() || HudVisibility.hidden()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        long now = Anim.now();
        float dt = lastFrame < 0 ? 0.0F : Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;

        float health = player.getHealth();
        float max = Math.max(1.0F, player.getMaxHealth());
        if (lastHealth >= 0.0F && health < lastHealth - 0.01F) {
            hitAt = now;
        }
        lastHealth = health;
        float hp = Mth.clamp(health / max, 0.0F, 1.0F);
        ECG.advance(dt, 64.0F + (1.0F - hp) * 140.0F);
        EMBLEM.track(Math.min(20, player.getArmorValue()), now);

        Inventory inv = player.getInventory();

        int accent = MlumConfig.fieldHudAccent();
        int shake = now - hitAt < 160L && Anim.enabled() ? ((now / 40L) % 2 == 0 ? 1 : -1) : 0;
        boolean survival = mc.gameMode != null && mc.gameMode.canHurtPlayer();
        boolean riding = SbwCompat.isRiding(player);
        boolean down = com.barbwra.mlum.client.downed.ClientDowned.selfDowned();

        float k = MlumConfig.fieldHudScale();
        HudPen pen = PEN;
        pen.begin(graphics);
        try {
            pen.zoom(width / 2.0F, 0.0F, k);
            compass(pen, player, partialTick, width, accent);
            if (survival) {
                pen.zoom(MARGIN, height - MARGIN, k);
                wrist(pen, player, MARGIN + shake, height - MARGIN - WRIST_H, hp, accent, now);
            }
            if (com.barbwra.mlum.camo.GhillieClient.wearing() && !down) {
                // above the wrist device; with no wrist (creative) it takes the wrist's corner
                pen.zoom(MARGIN, height - MARGIN, k);
                float base = survival ? height - MARGIN - WRIST_H - 4 : height - MARGIN;
                camoNotice(pen, MARGIN, base - noteHeight(), accent, now);
            }
            if (!down) {
                // flat on your back the belt and the gun are out of reach; only the wrist stays.
                // On a narrow screen the belt would run into the two panels, so it sits above them
                float half = ((CELL + GAP) * 9 / 2.0F + 20.0F) * k;
                boolean room = width / 2.0F - half > MARGIN + WRIST_W * k + 4
                        && width / 2.0F + half < width - MARGIN - SLAB_W * k - 4;
                float floor = room ? height : height - MARGIN - Math.max(WRIST_H, SLAB_H) * k - 4.0F;
                pen.zoom(width / 2.0F, floor, k);
                belt(pen, inv, width, floor, accent);
            }
            if (!riding && !down) {
                pen.zoom(width - MARGIN, height - MARGIN, k);
                slab(pen, player, inv.getSelected(), width - MARGIN - SLAB_W - shake, height - MARGIN - SLAB_H, accent, now);
            }
        } finally {
            pen.unzoom();
            pen.end();
        }

        // these used to hang off the vanilla hotbar's draw, which the field HUD switches off
        ZoneToast.render(graphics, width, height);
        LevelUpToast.render(graphics, width, height);
    }

    /* ================================================================== shared pieces */

    static int alpha(int argb, float a) {
        int base = argb >>> 24;
        int na = Mth.clamp(Math.round(base * a), 0, 255);
        return (na << 24) | (argb & 0xFFFFFF);
    }

    static float blink(long now, boolean on) {
        return on && Anim.enabled() ? 0.5F + 0.5F * Math.abs(Mth.sin(now / 170.0F)) : 1.0F;
    }

    /**
     * The shape language: a panel with cut corners, filled with a dark vertical gradient, edged in a
     * faint hairline of the HUD colour, with a one-pixel drop under it.
     */
    static void chamfer(HudPen pen, float x, float y, int w, int h, int tl, int tr, int br, int bl, int accent) {
        for (int pass = 0; pass < 3; pass++) {
            for (int r = 0; r < h; r++) {
                int l = Math.max(Math.max(tl - r, bl - (h - 1 - r)), 0);
                int rr = Math.max(Math.max(tr - r, br - (h - 1 - r)), 0);
                float rowW = w - l - rr;
                if (pass == 0) {
                    pen.rect(x + l + 1, y + r + 1, rowW, 1, 0x61000000);
                } else if (pass == 1) {
                    int c = Anim.mix(0xFF181C16, 0xFF0B0E0A, r / (float) Math.max(1, h - 1));
                    pen.rect(x + l, y + r, rowW, 1, alpha(c, 0.86F));
                } else {
                    int edge = alpha(accent, 0.2F);
                    pen.rect(x + l, y + r, 1, 1, edge);
                    pen.rect(x + w - rr - 1, y + r, 1, 1, edge);
                    if (r == 0) {
                        pen.rect(x + l, y, rowW, 1, edge);
                    } else if (r == h - 1) {
                        pen.rect(x + l, y + r, rowW, 1, alpha(accent, 0.14F));
                    } else if (r == 1) {
                        pen.rect(x + l + 1, y + 1, rowW - 2, 1, 0x0DFFFFFF);
                    }
                }
            }
        }
    }

    /** A recessed monochrome screen. Content goes on next, then {@link #glass}. */
    static void screen(HudPen pen, float x, float y, float w, float h, int accent) {
        pen.rect(x - 1, y - 1, w + 2, h + 2, 0xE6020302);
        pen.rect(x, y, w, h, 0xF5080B07);
        pen.rect(x, y, w, h, alpha(accent, 0.035F));
        int dot = alpha(accent, 0.55F);
        for (float gx = x + 3; gx < x + w; gx += 6) {
            for (float gy = y + 3; gy < y + h; gy += 5) {
                pen.rect(gx, gy, 0.5F, 0.5F, dot);
            }
        }
    }

    static void glass(HudPen pen, float x, float y, float w, float h) {
        pen.scanlines(x, y, w, h, 0x38000000);
        pen.rect(x, y, w, 1, 0x0DFFFFFF);
    }

    /** A pixel-art map: each char is a pixel, looked up in {@code colours} by its index in {@code keys}. */
    static void sprite(HudPen pen, String[] map, String keys, int[] colours, float x, float y, float s, float a) {
        for (int r = 0; r < map.length; r++) {
            String row = map[r];
            for (int c = 0; c < row.length(); c++) {
                int k = keys.indexOf(row.charAt(c));
                if (k >= 0) {
                    pen.rect(x + c * s, y + r * s, s, s, alpha(colours[k], a));
                }
            }
        }
    }

    static final String[] HEART = {" ## ## ", "#######", "#######", " ##### ", "  ###  ", "   #   "};
    static final String[] FOOD = {" ### ", "#####", "mmmmm", "#####"};

    /* ================================================================== the wrist device */

    private static void wrist(HudPen pen, LocalPlayer player, float x, float y, float hp, int accent, long now) {
        chamfer(pen, x, y, WRIST_W, WRIST_H, 0, 4, 0, 2, accent);

        // the top edge is the level bar
        float xp = Mth.clamp(ClientLevelData.fraction(), 0.0F, 1.0F);
        float flourish = ClientLevelData.levelUpProgress();
        int xpColour = flourish < 1.0F ? Anim.mix(0xFFFFFFFF, accent, flourish) : accent;
        float trackW = WRIST_W - 8;
        pen.rect(x + 2, y, trackW, 1, alpha(accent, 0.16F));
        pen.rect(x + 2, y, trackW * xp, 1, alpha(xpColour, 0.95F));
        Shaped lv = pen.pixel("LV " + ClientLevelData.level(), 5.0F, 700);
        pen.text(lv, x + 4, y + 6.5F, HudPen.LEFT, flourish < 1.0F ? xpColour : FAINT);
        pen.rect(x + 5 + pen.width(lv), y + 3.5F, 1.5F, 1.5F, alpha(accent, 0.4F + 0.6F * ECG.beat(now)));

        // your own face, in a recessed frame; it flashes red for a moment when you are hit
        float fx = x + 4;
        float fy = y + 9;
        float fs = 20;
        pen.rect(fx - 1, fy - 1, fs + 2, fs + 2, 0xE6020302);
        float hit = Mth.clamp(1.0F - (now - hitAt) / 260.0F, 0.0F, 1.0F);
        pen.face(player.getSkinTextureLocation(), fx, fy, fs, Anim.mix(0xFFFFFFFF, 0xFFFF6A50, hit));
        pen.rect(fx, fy, fs, 1, 0x14FFFFFF);
        if (hp <= 0.3F) {
            pen.rect(fx, fy, fs, fs, alpha(RUST, 0.18F * blink(now, true)));
        }

        float sx = x + 28;
        float sy = y + 9;
        float sw = 62;
        float sh = 20;
        int colour = hp <= 0.3F ? RUST : hp <= 0.6F ? WHEAT : accent;
        boolean low = hp <= 0.3F;
        screen(pen, sx, sy, sw, sh, accent);
        ECG.draw(pen, sx, sy, sh, colour);
        glass(pen, sx, sy, sw, sh);

        // the health inside its armour
        float mid = x + WRIST_W - 22;
        EMBLEM.draw(pen, mid - EMBLEM.width() / 2.0F, y + 6, now);
        sprite(pen, HEART, "#", new int[]{RUST}, mid - 2.1F, y + 11.5F, 0.6F, blink(now, low));
        Shaped num = pen.pixel(String.valueOf(Math.round(hp * 100.0F)), 9.0F, 700);
        float a = blink(now, low);
        pen.glow(num, mid, y + 25.5F, HudPen.CENTER, 1.5F, alpha(colour, 0.6F * a));
        pen.text(num, mid, y + 25.5F, HudPen.CENTER, alpha(colour, a));

        // food underneath the heartbeat
        float food = Mth.clamp(player.getFoodData().getFoodLevel() / 20.0F, 0.0F, 1.0F);
        foodBar(pen, fx, y + 35, sx + sw - fx, food, now);
    }

    private static void foodBar(HudPen pen, float x, float y, float w, float food, long now) {
        sprite(pen, FOOD, "#m", new int[]{WHEAT, 0xFF8A5A2B}, x, y - 1, 1.0F, 1.0F);
        float bx = x + 7;
        float bw = w - 7;
        boolean low = food < 0.25F;
        pen.rect(bx, y, bw, 2, 0x1AFFFFFF);
        pen.rect(bx, y, Math.round(bw * food), 2, alpha(low ? RUST : WHEAT, blink(now, low)));
        for (int q = 1; q < 4; q++) {
            pen.rect(bx + Math.round(bw * q / 4.0F), y, 1, 2, 0xE60B0E0A);
        }
    }

    /* ================================================================== the ghillie notice */

    private static final int NOTE_H = 15;
    private static final int TIMER_H = 30;

    private static float noteHeight() {
        boolean counting = com.barbwra.mlum.camo.GhillieClient.settling() && !com.barbwra.mlum.camo.GhillieClient.hidden();
        return counting ? TIMER_H : NOTE_H;
    }

    /**
     * Above the wrist device, for as long as a full ghillie suit is worn.
     *
     * <ul>
     *   <li>not crouched: one muted line saying how to vanish;</li>
     *   <li>crouched and still: the timer - a ring running down round the seconds left, and what
     *       breaks it (moving, letting go of Shift);</li>
     *   <li>hidden: a steady lamp and the word.</li>
     * </ul>
     */
    private static void camoNotice(HudPen pen, float x, float y, int accent, long now) {
        boolean hidden = com.barbwra.mlum.camo.GhillieClient.hidden();
        boolean settling = com.barbwra.mlum.camo.GhillieClient.settling();
        float progress = com.barbwra.mlum.camo.GhillieClient.progress();
        int seconds = com.barbwra.mlum.MlumConfig.hideSeconds();
        if (settling && !hidden) {
            chamfer(pen, x, y, WRIST_W, TIMER_H, 0, 3, 0, 0, WHEAT);
            float cx = x + 15.0F;
            float cy = y + TIMER_H / 2.0F;
            float left = 1.0F - progress;
            Shapes.disc(pen, cx, cy, 11.5F, 0x59000000);
            Shapes.ring(pen, cx, cy, 9.5F, 2.0F, 0x24FFFFFF);
            Shapes.arc(pen, cx, cy, 9.5F, 2.0F, 0.0F, left, left < 0.2F ? accent : WHEAT);
            int remain = Math.max(1, (int) Math.ceil(left * seconds));
            pen.text(pen.pixel(String.valueOf(remain), 11.0F, 700), cx, cy + 4.0F, HudPen.CENTER, BONE);
            pen.text(pen.kufi("تختفي بعد " + remain + " ث", 5.5F, 700), x + 31, y + 12.0F, HudPen.LEFT, BONE);
            pen.text(pen.kufi("لا تتحرك ولا تفك Shift", 4.6F, 600), x + 31, y + 21.0F, HudPen.LEFT, MUTED);
            float bw = WRIST_W - 34;
            pen.rect(x + 31, y + TIMER_H - 4, bw, 1, 0x26FFFFFF);
            pen.rect(x + 31, y + TIMER_H - 4, bw * progress, 1, WHEAT);
            return;
        }
        int edge = hidden ? accent : FAINT;
        chamfer(pen, x, y, WRIST_W, NOTE_H, 0, 3, 0, 0, edge);
        float lamp = hidden ? 0.75F + 0.25F * Math.abs(Mth.sin(now / 600.0F)) : 0.35F;
        pen.rect(x + 4, y + 5.5F, 4, 4, alpha(edge, lamp));
        String text = hidden ? "مختفي · أي حركة تكشفك" : "Shift واثبت " + seconds + " ثواني عشان تختفي";
        pen.text(pen.kufi(text, 5.0F, 600), x + 12, y + 10.0F, HudPen.LEFT, hidden ? accent : MUTED);
    }

    /** The notice alone, for when the field HUD is switched off. */
    public static void ghillieOnly(GuiGraphics graphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || HudVisibility.hidden() || !com.barbwra.mlum.camo.GhillieClient.wearing()
                || com.barbwra.mlum.client.downed.ClientDowned.selfDowned()) {
            return;
        }
        float k = MlumConfig.fieldHudScale();
        HudPen pen = PEN;
        pen.begin(graphics);
        try {
            pen.zoom(MARGIN, height - MARGIN, k);
            camoNotice(pen, MARGIN, height - MARGIN - 26 - noteHeight(), MlumConfig.fieldHudAccent(), Anim.now());
        } finally {
            pen.unzoom();
            pen.end();
        }
    }

    /* ================================================================== the belt */

    private static final float CELL = 17.0F;
    private static final float GAP = 1.5F;

    /** All nine cells in a row, always; the selected one lit. Nothing moves when you scroll. */
    private static void belt(HudPen pen, Inventory inv, int width, float height, int accent) {
        int sel = inv.selected;
        float stride = CELL + GAP;
        float total = stride * 9 - GAP;
        float cx = width / 2.0F;
        float cy = height - 4.0F - CELL / 2.0F;
        float x0 = cx - total / 2.0F + CELL / 2.0F;
        for (int i = 0; i < 9; i++) {
            float x = x0 + i * stride;
            cell(pen, x, cy, CELL, inv.getItem(i), i == sel, accent, 1.0F);
            pen.text(pen.pixel(String.valueOf(i + 1), 4.0F, 600), x - CELL / 2 + 1.5F, cy - CELL / 2 + 4.2F,
                    HudPen.LEFT, alpha(i == sel ? accent : FAINT, 0.9F));
        }

        ItemStack off = inv.offhand.get(0);
        if (!off.isEmpty()) {
            cell(pen, cx - total / 2.0F - 6.0F - 7.0F, cy + 1.5F, 14, off, false, accent, 0.9F);
        }

        ItemStack held = inv.getItem(sel);
        String name = held.isEmpty() ? "" : UiText.logical(held.getHoverName().getString());
        if (!name.isEmpty()) {
            boolean latin = isLatin(name);
            Shaped s = latin ? pen.pixel(name, 7.0F, 700) : pen.kufi(name, 5.5F, 600);
            pen.shadowed(s, cx, cy - CELL / 2 - 5.0F, HudPen.CENTER, alpha(BONE, 0.95F));
        }
    }

    private static void cell(HudPen pen, float cx, float cy, float size, ItemStack stack, boolean selected,
                             int accent, float a) {
        float s = Math.round(size);
        float x = Math.round(cx - s / 2.0F);
        float y = Math.round(cy - s / 2.0F);
        int fill = alpha(selected ? 0xFF1A1F17 : 0xFF0F120D, (selected ? 0.88F : 0.72F) * a);
        pen.rect(x + 1, y, s - 2, s, fill);
        pen.rect(x, y + 1, 1, s - 2, fill);
        pen.rect(x + s - 1, y + 1, 1, s - 2, fill);

        boolean charged = !stack.isEmpty() && ChargeTag.has(stack) && ChargeTag.BATTERY.equals(ChargeTag.kind(stack));
        boolean onWhite = false;
        if (charged) {
            // a battery: the cell itself is the gauge, white from the bottom up
            float frac = ChargeTag.fraction(stack);
            pen.rect(x + 1, y + s * (1.0F - frac), s - 2, s * frac, alpha(0xFFF4F1E8, 0.92F * a));
            onWhite = frac > 0.3F;
        }
        if (selected) {
            pen.rect(x + 2, y + s - 1, s - 4, 1, alpha(accent, a));
            pen.rect(x + 4, y + s, s - 8, 1, alpha(accent, 0.35F * a));
            pen.rect(x + 1, y, 3, 1, alpha(accent, 0.8F * a));
            pen.rect(x + s - 4, y, 3, 1, alpha(accent, 0.8F * a));
        } else {
            pen.rect(x + 1, y, s - 2, 1, alpha(0xFFFFFFFF, 0.05F * a));
        }
        if (stack.isEmpty()) {
            return;
        }
        float inset = s >= 16 ? 2 : 1;
        pen.item(stack, x + inset, y + inset - (charged ? 1 : 0), s - inset * 2, s - inset * 2, alpha(0xFFFFFFFF, a));

        if (stack.isBarVisible() && !charged) {
            float bw = (s - 4) * stack.getBarWidth() / 13.0F;
            pen.rect(x + 2, y + s - 3, s - 4, 1, alpha(0xFF000000, a));
            pen.rect(x + 2, y + s - 3, bw, 1, alpha(0xFF000000 | stack.getBarColor(), a));
        }

        String tag = charged ? ChargeTag.charge(stack) + "/" + ChargeTag.max(stack)
                : stack.getCount() > 1 ? String.valueOf(stack.getCount()) : "";
        if (!tag.isEmpty() && s >= (charged ? 17 : 12)) {
            Shaped t = pen.pixel(tag, s >= 17 ? 5.5F : 4.5F, 700);
            if (onWhite) {
                pen.text(t, x + s - 1.5F, y + s - 1.5F, HudPen.RIGHT, alpha(INK, a));
            } else {
                pen.shadowed(t, x + s - 1.0F, y + s - 1.0F, HudPen.RIGHT, alpha(BONE, a));
            }
        }
    }

    /* ================================================================== the weapon panel */

    private static void slab(HudPen pen, LocalPlayer player, ItemStack stack, float x, float y, int accent, long now) {
        chamfer(pen, x, y, SLAB_W, SLAB_H, 4, 0, 2, 0, accent);
        pen.rect(x + 10, y, 7, 1, alpha(accent, 0.9F));

        float sw = 54;
        float sh = 17;
        float sx = x + SLAB_W - 4 - sw;
        float sy = y + 9;
        boolean gun = !stack.isEmpty() && TaczCompat.isGun(stack);
        boolean charged = !stack.isEmpty() && ChargeTag.has(stack);

        screen(pen, sx, sy, sw, sh, accent);
        if (!stack.isEmpty()) {
            if (gun) {
                // a wide box: the canvas draws TACZ's own flat gun artwork, tinted to the screen
                pen.item(stack, sx + 3, sy + 2, sw - 6, sh - 4, alpha(accent, 0.95F));
            } else {
                float box = sh - 3;
                pen.item(stack, sx + sw / 2 - box / 2, sy + 1.5F, box, box, 0xFFFFFFFF);
            }
        }
        glass(pen, sx, sy, sw, sh);

        String name = stack.isEmpty() ? "" : UiText.logical(stack.getHoverName().getString());
        if (!name.isEmpty()) {
            Shaped n = isLatin(name) ? pen.pixel(name, 5.0F, 700) : pen.kufi(name, 4.2F, 600);
            pen.text(n, sx + sw, y + 6.5F, HudPen.RIGHT, BONE);
        }

        float lx = x + 4;
        float span = SLAB_W - 8;
        if (gun) {
            int loaded = Math.max(0, TaczCompat.loadedRounds(stack));
            int mag = TaczAttachments.magazineSize(stack);
            if (mag <= 0) {
                mag = Math.max(loaded, 1);
            }
            boolean low = loaded <= Math.max(1, mag / 4);
            String mode = TaczCompat.fireMode(stack).toUpperCase(java.util.Locale.ROOT);
            if (!mode.isEmpty()) {
                Shaped m = pen.pixel(mode, 4.5F, 700);
                pen.text(m, lx, y + 13, HudPen.LEFT, accent);
                pen.rect(lx, y + 14, pen.width(m), 0.5F, alpha(accent, 0.7F));
            }
            Shaped num = pen.pixel(String.format("%02d", Math.min(999, loaded)), 15.0F, 700);
            pen.shadowed(num, lx, y + 27, HudPen.LEFT, alpha(low ? RUST : BONE, blink(now, loaded == 0)));
            int reserve = TaczCompat.reserveInInventory(player, stack);
            if (reserve >= 0) {
                pen.text(pen.pixel("/" + Math.min(9999, reserve), 5.5F, 600), lx + pen.width(num) + 0.5F, y + 27,
                        HudPen.LEFT, MUTED);
            }
            int shown = Math.min(mag, 60);
            float step = span / shown;
            int lit = Math.round(loaded * shown / (float) mag);
            for (int i = 0; i < shown; i++) {
                boolean on = i < lit;
                pen.rect(lx + i * step, y + 31, Math.max(0.5F, step - 0.5F), 3.5F,
                        on ? alpha(low ? RUST : BONE, 0.9F) : 0x1AFFFFFF);
            }
        } else if (charged) {
            int charge = ChargeTag.charge(stack);
            int max = ChargeTag.max(stack);
            boolean defib = ChargeTag.DEFIB.equals(ChargeTag.kind(stack));
            pen.text(pen.kufi(defib ? "طاقة" : "شحن", 4.2F, 600), lx + 34, y + 13.5F, HudPen.RIGHT, MUTED);
            Shaped num = pen.pixel(String.valueOf(charge), defib ? 15.0F : 12.0F, 700);
            pen.glow(num, lx, y + 27, HudPen.LEFT, 1.5F, alpha(VOLT, 0.5F));
            pen.text(num, lx, y + 27, HudPen.LEFT, VOLT);
            if (!defib) {
                pen.text(pen.pixel("/" + max, 5.0F, 600), lx + pen.width(num) + 0.5F, y + 27, HudPen.LEFT, MUTED);
            }
            int per = Math.max(1, com.barbwra.mlum.MlumConfig.defibCost());
            if (defib && max >= per) {
                int cells = max / per;
                int full = charge / per;
                float cw = (span - (cells - 1)) / cells;
                for (int i = 0; i < cells; i++) {
                    pen.rect(lx + i * (cw + 1), y + 31, cw, 3.5F, i < full ? VOLT : 0x1AFFFFFF);
                }
                pen.text(pen.kufi(full + " إنعاش", 4.0F, 600), sx - 2, y + 6.5F, HudPen.RIGHT, MUTED);
            } else {
                pen.rect(lx, y + 31, span, 3.5F, 0x1AFFFFFF);
                pen.rect(lx, y + 31, span * ChargeTag.fraction(stack), 3.5F, alpha(0xFFF4F1E8, 0.92F));
            }
        } else if (!stack.isEmpty()) {
            if (stack.getCount() > 1) {
                pen.shadowed(pen.pixel("×" + stack.getCount(), 13.0F, 700), lx, y + 26, HudPen.LEFT, BONE);
            }
        } else {
            pen.text(pen.kufi("يدك فاضية", 5.0F, 600), lx + 34, y + 22, HudPen.RIGHT, FAINT);
        }
    }

    /* ================================================================== the compass */

    private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private static void compass(HudPen pen, LocalPlayer player, float partialTick, int width, int accent) {
        float heading = ((player.getViewYRot(partialTick) + 180.0F) % 360.0F + 360.0F) % 360.0F;
        float cx = width / 2.0F;
        float w = 140.0F;
        float x0 = cx - w / 2.0F;
        float y = 5.0F;
        for (float xx = x0; xx < x0 + w; xx++) {
            float edge = 1.0F - Math.abs(xx - cx) / (w / 2.0F);
            pen.rect(xx, y + 7, 1, 1, alpha(0xFFFFFFFF, 0.22F * (float) Math.pow(Math.max(0.0F, edge), 0.6F)));
        }
        int first = (int) Math.floor((heading - 90.0F) / 5.0F) * 5;
        for (int deg = first; deg <= heading + 90.0F; deg += 5) {
            float px = cx + (deg - heading) * (w / 180.0F);
            if (px < x0 + 2 || px > x0 + w - 2) {
                continue;
            }
            int rd = ((deg % 360) + 360) % 360;
            float edge = (float) Math.pow(Math.max(0.0F, 1.0F - Math.abs(px - cx) / (w / 2.0F)), 0.7F);
            if (rd % 45 == 0) {
                boolean cardinal = rd % 90 == 0;
                Shaped s = pen.pixel(POINTS[rd / 45], cardinal ? 6.5F : 5.0F, 700);
                pen.shadowed(s, px, y + 5, HudPen.CENTER, alpha(cardinal ? BONE : MUTED, edge));
            } else {
                boolean major = rd % 15 == 0;
                pen.rect(Math.round(px), y + (major ? 3 : 4), 1, major ? 4 : 3, alpha(0xFFFFFFFF, 0.5F * edge));
            }
        }
        chamfer(pen, cx - 10, y + 9, 20, 9, 0, 0, 2, 2, accent);
        pen.rect(cx - 1, y + 7, 3, 1, accent);
        pen.text(pen.pixel(String.format("%03d", Math.round(heading) % 360), 6.0F, 700), cx, y + 16, HudPen.CENTER, accent);
    }

    static boolean isLatin(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }
}

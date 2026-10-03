package com.barbwra.mlum.client.dealer;

import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.client.screens.ScreenKit;
import com.barbwra.mlum.client.ui.mc.EntityPreview;
import com.barbwra.mlum.client.ui.mc.UiSounds;
import com.barbwra.mlum.client.ui.text.Shaped;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The showroom.
 *
 * <pre>
 *  ┌──────────────────────────────────────────────┬──────────────────┐
 *  │ MLUM MOTORS                                  │ المركبات المعروضة │
 *  │                                              │ رصيدك  · مستواك   │
 *  │               ( the vehicle, turning         │ [الكل][قسم][قسم]  │
 *  │                 on a lit turntable )         │ ┃ name     $price │
 *  │                                              │   level · limited │
 *  │  ┌ card: name, section, price, what it needs, buy ┐            │
 *  └──────────────────────────────────────────────┴──────────────────┘
 * </pre>
 *
 * <p>The vehicle is the real model, drawn in this screen only - the buyer can turn it with the mouse
 * and zoom with the wheel, but it is not in the world, so no one else sees it and it cannot be
 * driven off. Buying asks for a second click within three seconds.</p>
 *
 * <p>Anyone allowed to edit gets add / edit / delete buttons in the same screen; the form takes the
 * place of the list, so the vehicle being entered is previewed live behind it as its id is typed.</p>
 */
@OnlyIn(Dist.CLIENT)
public class DealerScreen extends Screen {

    private static final int CASH = 0xFF8FD16A;
    private static final int CASH_DIM = 0xFF5E7A4C;

    private final ScreenKit kit = new ScreenKit();
    private final List<float[]> zones = new ArrayList<>();
    private final List<String> zoneIds = new ArrayList<>();

    private int filter;
    private int selected = -1;
    private int scroll;
    private int seen = -1;

    private float yaw = 215.0F;
    private float shownYaw = 215.0F;
    private float zoom = 1.0F;
    private boolean dragging;
    private long lastTouch;
    private long lastFrame;

    private int confirmBuy = -1;
    private long confirmAt;
    private int confirmDelete = -1;
    private long deleteAt;

    private String toast;
    private boolean toastOk;
    private long toastAt;

    private Form form;
    private Field focus;

    /* geometry of the last frame, for clicks and the wheel */
    private float listX;
    private float listTop;
    private float listBottom;
    private float stageX1;
    private int rowsShown;

    public DealerScreen() {
        super(Component.literal("Dealership"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        ClientDealer.send("close", new CompoundTag());
        super.removed();
    }

    /* ================================================================== the model */

    private List<ClientDealer.Listing> visible() {
        List<ClientDealer.Listing> out = new ArrayList<>();
        for (ClientDealer.Listing l : ClientDealer.LISTINGS) {
            if (filter == 0 || l.category() == filter) {
                out.add(l);
            }
        }
        return out;
    }

    private ClientDealer.Listing listing(int id) {
        for (ClientDealer.Listing l : ClientDealer.LISTINGS) {
            if (l.id() == id) {
                return l;
            }
        }
        return null;
    }

    private void settle() {
        if (filter != 0 && ClientDealer.category(filter) == null) {
            filter = 0;
        }
        List<ClientDealer.Listing> v = visible();
        boolean present = false;
        for (ClientDealer.Listing l : v) {
            present |= l.id() == selected;
        }
        if (!present) {
            selected = v.isEmpty() ? -1 : v.get(0).id();
        }
    }

    private boolean owns(ClientDealer.Listing l) {
        ClientDealer.Owned o = ClientDealer.OWNED.get(l.entity());
        return o != null && !o.consumable() && !l.limited();
    }

    private static String money(long v) {
        return "$" + String.format(Locale.ROOT, "%,d", v);
    }

    private static boolean arabic(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) {
                return true;
            }
        }
        return false;
    }

    public void result(boolean ok, String message) {
        toast = message;
        toastOk = ok;
        toastAt = System.currentTimeMillis();
        if (ok) {
            UiSounds.coin();
            form = null;
            focus = null;
        } else {
            UiSounds.tick(false);
        }
    }

    /* ================================================================== frame */

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (seen != ClientDealer.version) {
            seen = ClientDealer.version;
            settle();
        }
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        if (!dragging && now - lastTouch > 2500L) {
            yaw += dt * 14.0F;
        }
        shownYaw += (yaw - shownYaw) * Math.min(1.0F, dt * 10.0F);

        zones.clear();
        zoneIds.clear();
        float pad = 8.0F;
        float listW = Math.min(196.0F, width * 0.38F);
        listX = width - pad - listW;
        stageX1 = listX - pad;
        float stageX0 = pad;
        float stageCx = (stageX0 + stageX1) / 2.0F;
        float cardH = 64.0F;
        float cardY = height - pad - cardH;
        float floorY = cardY - 30.0F;
        float stageTop = pad + 30.0F;
        float stageW = stageX1 - stageX0;
        float size = Math.min(stageW * 0.62F, (floorY - stageTop) * 1.25F) * zoom;

        kit.begin(g);
        try {
            backdrop(kit.pen, stageCx, floorY, stageW, stageX1);
        } finally {
            kit.end();
        }

        String entity = form != null && form.vehicle ? form.get("entity").trim() : null;
        ClientDealer.Listing sel = listing(selected);
        if (entity == null && sel != null) {
            entity = sel.entity();
        }
        boolean drawn = entity != null && !entity.isEmpty() && ResourceLocation.tryParse(entity) != null
                && EntityPreview.showroom(g, entity, stageCx, floorY, size, shownYaw, 14.0F);

        kit.begin(g);
        try {
            HudPen pen = kit.pen;
            // the house name, top left
            Shaped mark = pen.pixel("MLUM MOTORS", 13.0F, 700);
            pen.glow(mark, stageX0 + 4, pad + 14, HudPen.LEFT, 2.5F, ScreenKit.alpha(ScreenKit.AMBER, 0.35F));
            pen.text(mark, stageX0 + 4, pad + 14, HudPen.LEFT, ScreenKit.BONE);
            pen.text(pen.kufi("معرض المركبات", 5.5F, 600), stageX0 + 4, pad + 24, HudPen.LEFT, ScreenKit.MUTED);

            if (!drawn) {
                String why = entity == null || entity.isEmpty()
                        ? (ClientDealer.LISTINGS.isEmpty() ? "المعرض فاضي حالياً" : "اختر مركبة من القائمة")
                        : "ما فيه معاينة لهذي المركبة";
                pen.text(pen.kufi(why, 7.0F, 600), stageCx, (stageTop + floorY) / 2.0F, HudPen.CENTER, ScreenKit.FAINT);
            } else {
                pen.text(pen.kufi("اسحب بالماوس عشان تلف المركبة · العجلة للتقريب", 4.6F, 600), stageCx, cardY - 6.0F,
                        HudPen.CENTER, ScreenKit.alpha(ScreenKit.FAINT, 0.9F));
            }

            if (form != null) {
                drawForm(pen, mouseX, mouseY, pad, listW, now);
            } else {
                drawList(pen, mouseX, mouseY, pad, listW);
                drawCard(pen, sel, stageX0, cardY, stageW, cardH, mouseX, mouseY, now);
            }
            drawToast(pen, stageCx, stageTop + 4, now);
        } finally {
            kit.end();
        }
    }

    /* ================================================================== the room */

    private void backdrop(HudPen pen, float cx, float floorY, float stageW, float stageX1) {
        pen.vgrad(0, 0, width, height, 0xFF13160F, 0xFF040504);
        // the back wall: tall panels, lit faintly from above
        for (float x = 4; x < stageX1; x += 34.0F) {
            pen.vgrad(x, 0, 0.5F, floorY - 14, 0x14FFFFFF, 0x02FFFFFF);
        }
        // where the wall meets the floor
        pen.vgrad(0, floorY - 22, stageX1 + 8, 22, 0x00000000, 0x55000000);
        pen.rect(0, floorY - 14, stageX1 + 8, 0.5F, 0x16FFFFFF);
        // the spotlight: a cone of light widening down onto the turntable
        float top = 0.0F;
        float steps = 40.0F;
        float span = floorY - top;
        for (int i = 0; i < steps; i++) {
            float t = i / steps;
            float y = top + span * t;
            float w = 14.0F + stageW * 0.62F * t;
            pen.rect(cx - w / 2.0F, y, w, span / steps + 0.5F, ScreenKit.alpha(0xFFF4E8CC, 0.022F + 0.03F * t));
        }
        // the turntable: a lit pool, the plate, its rim, and marks turning with the vehicle
        float rx = stageW * 0.36F;
        float ry = rx * 0.17F;
        for (int k = 0; k < 6; k++) {
            float f = 1.35F - k * 0.09F;
            ellipse(pen, cx, floorY + 1, rx * f, ry * f, ScreenKit.alpha(0xFFF4E8CC, 0.035F));
        }
        ellipse(pen, cx, floorY + 1, rx, ry, 0xE0101310);
        ellipse(pen, cx, floorY + 0.5F, rx * 0.97F, ry * 0.94F, 0xFF1A1E17);
        ellipseRing(pen, cx, floorY + 0.5F, rx * 0.97F, ry * 0.94F, ScreenKit.alpha(ScreenKit.AMBER_DIM, 0.8F));
        for (int i = 0; i < 36; i++) {
            double a = Math.toRadians(i * 10.0F + shownYaw);
            float px = cx + (float) Math.cos(a) * rx * 0.86F;
            float py = floorY + 0.5F + (float) Math.sin(a) * ry * 0.84F;
            pen.rect(px - 0.5F, py - 0.25F, i % 3 == 0 ? 1.5F : 0.75F, 0.5F, ScreenKit.alpha(0xFFECE6D4, i % 3 == 0 ? 0.3F : 0.14F));
        }
        // a soft vignette at the edges
        pen.vgrad(0, 0, width, 30, 0x66000000, 0x00000000);
        pen.vgrad(0, height - 40, width, 40, 0x00000000, 0x88000000);
    }

    private static void ellipse(HudPen pen, float cx, float cy, float rx, float ry, int argb) {
        for (float dy = -ry; dy < ry; dy += 0.5F) {
            float m = (dy + 0.25F) / ry;
            float half = rx * (float) Math.sqrt(Math.max(0.0F, 1.0F - m * m));
            if (half > 0.2F) {
                pen.rect(cx - half, cy + dy, half * 2.0F, 0.5F, argb);
            }
        }
    }

    private static void ellipseRing(HudPen pen, float cx, float cy, float rx, float ry, int argb) {
        float prev = -1.0F;
        for (float dy = -ry; dy < ry; dy += 0.5F) {
            float m = (dy + 0.25F) / ry;
            float half = rx * (float) Math.sqrt(Math.max(0.0F, 1.0F - m * m));
            float seg = prev < 0 ? half : Math.max(0.75F, Math.abs(half - prev));
            pen.rect(cx - half, cy + dy, seg, 0.5F, argb);
            pen.rect(cx + half - seg, cy + dy, seg, 0.5F, argb);
            prev = half;
        }
    }

    /* ================================================================== the list */

    private void zone(String id, float x, float y, float w, float h) {
        zones.add(new float[]{x, y, x + w, y + h});
        zoneIds.add(id);
    }

    private String zoneAt(double mx, double my) {
        for (int i = zones.size() - 1; i >= 0; i--) {
            float[] r = zones.get(i);
            if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) {
                return zoneIds.get(i);
            }
        }
        return null;
    }

    private void drawList(HudPen pen, int mx, int my, float pad, float listW) {
        float x = listX;
        float y = pad;
        float h = height - pad * 2;
        kit.panel(x, y, listW, h);
        float right = x + listW - 7;
        kit.heading("المركبات المعروضة", right, y + 13);

        // what the buyer brings: balance on the right, level on the left
        float iy = y + 25;
        Shaped label = pen.kufi("رصيدك", 4.8F, 600);
        pen.text(label, right, iy, HudPen.RIGHT, ScreenKit.MUTED);
        pen.text(pen.pixel(money(ClientDealer.balance), 8.0F, 700), right - pen.width(label) - 4, iy + 0.5F, HudPen.RIGHT, CASH);
        Shaped lv = pen.pixel(String.valueOf(ClientDealer.level), 8.0F, 700);
        pen.text(lv, x + 7, iy + 0.5F, HudPen.LEFT, ScreenKit.BONE);
        pen.text(pen.kufi("مستواك", 4.8F, 600), x + 7 + pen.width(lv) + 3, iy, HudPen.LEFT, ScreenKit.MUTED);
        pen.rect(x + 6, iy + 4, listW - 12, 0.5F, ScreenKit.LINE_SOFT);

        float cy = iy + 8;
        if (ClientDealer.edit) {
            float bw = (listW - 14 - 8) / 3.0F;
            kit.button("add.veh", "+ مركبة", right - bw, cy, bw, 10, ScreenKit.GHOST, mx, my);
            kit.button("add.cat", "+ قسم", right - bw * 2 - 4, cy, bw, 10, ScreenKit.GHOST, mx, my);
            if (filter != 0) {
                kit.button("edit.cat", "تعديل القسم", right - bw * 3 - 8, cy, bw, 10, ScreenKit.GHOST, mx, my);
            }
            cy += 14;
        }

        // the sections, right to left, wrapping
        float cx = right;
        float chipH = 11.0F;
        List<ClientDealer.Category> cats = new ArrayList<>();
        cats.add(new ClientDealer.Category(0, "الكل"));
        cats.addAll(ClientDealer.CATEGORIES);
        for (ClientDealer.Category c : cats) {
            float w = pen.width(pen.kufi(c.name(), 4.6F, 700)) + 12;
            if (cx - w < x + 6) {
                cx = right;
                cy += chipH + 3;
            }
            kit.button("cat:" + c.id(), c.name(), cx - w, cy, w, chipH, c.id() == filter ? ScreenKit.FILLED : ScreenKit.GHOST, mx, my);
            cx -= w + 3;
        }
        cy += chipH + 6;

        // the stock
        List<ClientDealer.Listing> v = visible();
        float rowH = 22.0F;
        listTop = cy;
        listBottom = y + h - 6;
        rowsShown = Math.max(1, (int) ((listBottom - listTop) / rowH));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, v.size() - rowsShown)));
        if (v.isEmpty()) {
            pen.text(pen.kufi(ClientDealer.edit ? "ما فيه مركبات هنا · اضغط + مركبة" : "ما فيه مركبات هنا", 5.2F, 600),
                    x + listW / 2, cy + 14, HudPen.CENTER, ScreenKit.FAINT);
        }
        for (int i = scroll; i < v.size() && i < scroll + rowsShown; i++) {
            row(pen, v.get(i), x + 5, cy + (i - scroll) * rowH, listW - 10, rowH - 2, mx, my);
        }
        if (v.size() > rowsShown) {
            float track = listBottom - listTop;
            float thumb = Math.max(10, track * rowsShown / v.size());
            float at = listTop + (track - thumb) * scroll / Math.max(1, v.size() - rowsShown);
            pen.rect(x + 2, listTop, 1, track, 0x14FFFFFF);
            pen.rect(x + 2, at, 1, thumb, ScreenKit.AMBER_DIM);
        }
    }

    private void row(HudPen pen, ClientDealer.Listing l, float x, float y, float w, float h, int mx, int my) {
        boolean sel = l.id() == selected;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        pen.rect(x, y, w, h, sel ? 0x2AF0A93B : hover ? 0x14FFFFFF : 0x07FFFFFF);
        if (sel) {
            pen.rect(x + w - 1.5F, y, 1.5F, h, ScreenKit.AMBER);
        }
        float right = x + w - 6;
        pen.text(pen.kufi(l.name(), 6.0F, 700), right, y + 8.5F, HudPen.RIGHT, ScreenKit.BONE);
        boolean levelOk = ClientDealer.level >= l.level();
        String sub = (l.level() > 0 ? "مستوى " + l.level() : "بدون مستوى") + (l.limited() ? " · محدودة ×" + l.count() : " · دائمة");
        pen.text(pen.kufi(sub, 4.5F, 600), right, y + 16.5F, HudPen.RIGHT, levelOk ? ScreenKit.MUTED : ScreenKit.RUST);
        boolean afford = ClientDealer.balance >= l.price();
        pen.text(pen.pixel(money(l.price()), 8.0F, 700), x + 5, y + 10.0F, HudPen.LEFT, afford ? CASH : CASH_DIM);
        ClientDealer.Owned o = ClientDealer.OWNED.get(l.entity());
        if (o != null) {
            String have = o.consumable() ? "عندك ×" + o.count() : "تملكها";
            pen.text(pen.kufi(have, 4.4F, 700), x + 5, y + 17.0F, HudPen.LEFT, ScreenKit.SAGE);
        }
        zone("row:" + l.id(), x, y, w, h);
    }

    /* ================================================================== the card */

    private void drawCard(HudPen pen, ClientDealer.Listing l, float x, float y, float w, float h, int mx, int my, long now) {
        kit.panel(x, y, w, h);
        if (l == null) {
            pen.text(pen.kufi("اختر مركبة من القائمة", 6.5F, 600), x + w / 2, y + h / 2 + 2, HudPen.CENTER, ScreenKit.FAINT);
            return;
        }
        float right = x + w - 9;
        pen.text(pen.kufi(l.name(), 9.5F, 700), right, y + 16, HudPen.RIGHT, ScreenKit.BONE);
        ClientDealer.Category c = ClientDealer.category(l.category());
        pen.text(pen.kufi(c == null ? "" : c.name(), 5.2F, 600), right, y + 26, HudPen.RIGHT, ScreenKit.AMBER);

        // what it needs and what it is
        boolean levelOk = ClientDealer.level >= l.level();
        String lv = l.level() > 0
                ? "المستوى المطلوب " + l.level() + (levelOk ? "" : " · مستواك " + ClientDealer.level)
                : "بدون مستوى";
        float cr = chip(pen, lv, right, y + 34, levelOk ? ScreenKit.SAGE : ScreenKit.RUST);
        String kind = l.limited() ? "محدودة · " + l.count() + " استخدام" : "دائمة · تبقى لك";
        chip(pen, kind, cr - 4, y + 34, l.limited() ? ScreenKit.AMBER : ScreenKit.SOFT);
        pen.text(pen.kufi(l.limited()
                        ? "كل مرة تطلّعها تنقص وحدة، وترجع لك إذا خزّنتها"
                        : "تطلّعها وتخزّنها متى ما بغيت", 4.5F, 600),
                right, y + h - 7, HudPen.RIGHT, ScreenKit.FAINT);

        // price and the button
        pen.text(pen.kufi("السعر", 4.8F, 600), x + 9, y + 11, HudPen.LEFT, ScreenKit.MUTED);
        boolean afford = ClientDealer.balance >= l.price();
        pen.text(pen.pixel(money(l.price()), 15.0F, 700), x + 9, y + 27, HudPen.LEFT, afford ? CASH : CASH_DIM);
        float bx = x + 9;
        float by = y + h - 9 - 17;
        float bw = 112;
        String blocked = owns(l) ? "تملكها" : !levelOk ? "مستواك ما يكفي" : !afford ? "رصيدك ما يكفي" : null;
        if (blocked != null) {
            pen.rect(bx, by, bw, 17, 0x0CFFFFFF);
            outline(pen, bx, by, bw, 17, ScreenKit.LINE);
            pen.text(pen.kufi(blocked, 6.0F, 700), bx + bw / 2, by + 11.0F, HudPen.CENTER,
                    owns(l) ? ScreenKit.SAGE : ScreenKit.FAINT);
        } else {
            boolean confirming = confirmBuy == l.id() && now - confirmAt < 3000L;
            kit.button("buy", confirming ? "متأكد؟ اضغط مرة ثانية" : "اشترِ الحين", bx, by, bw, 17, ScreenKit.FILLED, mx, my);
            pen.text(pen.kufi("يبقى لك " + money(ClientDealer.balance - l.price()), 4.5F, 600), bx + bw + 6, by + 10.5F,
                    HudPen.LEFT, ScreenKit.MUTED);
        }

        // the editor's tools float just above the card
        if (ClientDealer.edit) {
            float ty = y - 13;
            boolean del = confirmDelete == l.id() && now - deleteAt < 3000L;
            kit.button("edit.veh", "تعديل", x, ty, 40, 10, ScreenKit.GHOST, mx, my);
            kit.button("del.veh", del ? "متأكد؟" : "حذف", x + 44, ty, 40, 10, ScreenKit.DANGER, mx, my);
            kit.button("up", "↑", x + 88, ty, 14, 10, ScreenKit.GHOST, mx, my);
            kit.button("down", "↓", x + 106, ty, 14, 10, ScreenKit.GHOST, mx, my);
        }
    }

    /** A small labelled box ending at {@code right}; returns where its left edge is. */
    private static float chip(HudPen pen, String text, float right, float y, int color) {
        Shaped s = pen.kufi(text, 4.8F, 700);
        float w = pen.width(s) + 10;
        pen.rect(right - w, y, w, 10, ScreenKit.alpha(color, 0.12F));
        outline(pen, right - w, y, w, 10, ScreenKit.alpha(color, 0.55F));
        pen.text(s, right - w / 2, y + 6.8F, HudPen.CENTER, color);
        return right - w;
    }

    private static void outline(HudPen pen, float x, float y, float w, float h, int argb) {
        pen.rect(x, y, w, 0.5F, argb);
        pen.rect(x, y + h - 0.5F, w, 0.5F, argb);
        pen.rect(x, y, 0.5F, h, argb);
        pen.rect(x + w - 0.5F, y, 0.5F, h, argb);
    }

    private void drawToast(HudPen pen, float cx, float y, long now) {
        if (toast == null) {
            return;
        }
        long age = now - toastAt;
        if (age > 4000L) {
            toast = null;
            return;
        }
        float a = age > 3400L ? 1.0F - (age - 3400L) / 600.0F : 1.0F;
        Shaped s = pen.kufi(toast, 6.0F, 700);
        float w = pen.width(s) + 22;
        int color = toastOk ? ScreenKit.SAGE : ScreenKit.RUST;
        pen.rect(cx - w / 2, y, w, 15, ScreenKit.alpha(0xF00B0D0A, a));
        pen.rect(cx - w / 2, y, 2, 15, ScreenKit.alpha(color, a));
        outline(pen, cx - w / 2, y, w, 15, ScreenKit.alpha(color, 0.5F * a));
        pen.text(s, cx + 1, y + 10, HudPen.CENTER, ScreenKit.alpha(ScreenKit.BONE, a));
    }

    /* ================================================================== the editor */

    private static final class Field {
        final String key;
        final String label;
        final boolean numeric;
        final int max;
        String value;
        float x;
        float y;
        float w;

        Field(String key, String label, String value, boolean numeric, int max) {
            this.key = key;
            this.label = label;
            this.value = value;
            this.numeric = numeric;
            this.max = max;
        }
    }

    private static final class Form {
        final boolean vehicle;
        final int editId;
        final List<Field> fields = new ArrayList<>();
        int category;
        boolean limited;

        Form(boolean vehicle, int editId) {
            this.vehicle = vehicle;
            this.editId = editId;
        }

        Field field(String key) {
            for (Field f : fields) {
                if (f.key.equals(key)) {
                    return f;
                }
            }
            return null;
        }

        String get(String key) {
            Field f = field(key);
            return f == null ? "" : f.value;
        }
    }

    private void openVehicleForm(ClientDealer.Listing l) {
        Form f = new Form(true, l == null ? 0 : l.id());
        f.fields.add(new Field("entity", "رقم المركبة (id)", l == null ? "" : l.entity(), false, 96));
        f.fields.add(new Field("name", "اسم المركبة", l == null ? "" : l.name(), false, 40));
        f.fields.add(new Field("price", "السعر", l == null ? "" : String.valueOf(l.price()), true, 13));
        f.fields.add(new Field("level", "المستوى المطلوب", l == null ? "0" : String.valueOf(l.level()), true, 5));
        f.fields.add(new Field("count", "عدد الاستخدامات", l == null ? "1" : String.valueOf(Math.max(1, l.count())), true, 3));
        f.category = l != null ? l.category() : filter != 0 ? filter
                : ClientDealer.CATEGORIES.isEmpty() ? 0 : ClientDealer.CATEGORIES.get(0).id();
        f.limited = l != null && l.limited();
        form = f;
        focus = f.fields.get(0);
    }

    private void openCategoryForm(ClientDealer.Category c) {
        Form f = new Form(false, c == null ? 0 : c.id());
        f.fields.add(new Field("name", "اسم القسم", c == null ? "" : c.name(), false, 24));
        form = f;
        focus = f.fields.get(0);
    }

    private void drawForm(HudPen pen, int mx, int my, float pad, float listW, long now) {
        float x = listX;
        float y = pad;
        float h = height - pad * 2;
        kit.panel(x, y, listW, h);
        float right = x + listW - 7;
        String title = form.vehicle ? (form.editId > 0 ? "تعديل مركبة" : "مركبة جديدة")
                : (form.editId > 0 ? "تعديل القسم" : "قسم جديد");
        kit.heading(title, right, y + 13);
        float fx = x + 8;
        float fw = listW - 16;
        float cy = y + 30;
        if (form.vehicle) {
            field(pen, form.field("entity"), fx + 44, cy, fw - 44, mx, my, now);
            kit.button("form.pick", "اللي قدامك", fx, cy, 40, 13, ScreenKit.GHOST, mx, my);
            cy += 25;
            field(pen, form.field("name"), fx, cy, fw, mx, my, now);
            cy += 25;
            float half = (fw - 6) / 2;
            field(pen, form.field("price"), fx + half + 6, cy, half, mx, my, now);
            field(pen, form.field("level"), fx, cy, half, mx, my, now);
            cy += 25;
            label(pen, "القسم", fx + fw, cy - 2.5F);
            ClientDealer.Category c = ClientDealer.category(form.category);
            kit.button("form.cat", c == null ? "سو قسم أول" : c.name() + "  ‹›", fx + half + 6, cy, half, 13, ScreenKit.GHOST, mx, my);
            label(pen, "النوع", fx + half, cy - 2.5F);
            kit.button("form.limited", form.limited ? "محدودة" : "دائمة", fx, cy, half, 13,
                    form.limited ? ScreenKit.FILLED : ScreenKit.GHOST, mx, my);
            cy += 25;
            if (form.limited) {
                field(pen, form.field("count"), fx, cy, fw, mx, my, now);
                cy += 25;
            }
            pen.text(pen.kufi("الـ id مثل superbwarfare:humvee · المعاينة على اليسار", 4.4F, 600), fx + fw, cy - 4,
                    HudPen.RIGHT, ScreenKit.FAINT);
            pen.text(pen.kufi("اركب المركبة أو طالعها واضغط «اللي قدامك» عشان ينكتب الـ id", 4.4F, 600), fx + fw, cy + 4,
                    HudPen.RIGHT, ScreenKit.FAINT);
            cy += 12;
        } else {
            field(pen, form.field("name"), fx, cy, fw, mx, my, now);
            cy += 25;
        }
        float bw = (fw - 6) / 2;
        kit.button("form.save", "حفظ", fx + bw + 6, cy, bw, 15, ScreenKit.FILLED, mx, my);
        kit.button("form.cancel", "إلغاء", fx, cy, bw, 15, ScreenKit.GHOST, mx, my);
        if (!form.vehicle && form.editId > 0) {
            boolean del = confirmDelete == -form.editId && now - deleteAt < 3000L;
            kit.button("form.delete", del ? "متأكد؟ اضغط مرة ثانية" : "حذف القسم", fx, cy + 20, fw, 13, ScreenKit.DANGER, mx, my);
        }
    }

    private static void label(HudPen pen, String text, float right, float baseline) {
        pen.text(pen.kufi(text, 4.8F, 600), right, baseline, HudPen.RIGHT, ScreenKit.MUTED);
    }

    private void field(HudPen pen, Field f, float x, float y, float w, int mx, int my, long now) {
        f.x = x;
        f.y = y;
        f.w = w;
        label(pen, f.label, x + w, y - 2.5F);
        boolean focused = f == focus;
        pen.rect(x, y, w, 13, 0xA0000000);
        outline(pen, x, y, w, 13, focused ? ScreenKit.AMBER : ScreenKit.LINE);
        boolean ar = arabic(f.value);
        Shaped s = ar ? pen.kufi(f.value, 5.6F, 600) : pen.pixel(f.value, 7.0F, 600);
        float tw = pen.width(s);
        if (f.value.isEmpty()) {
            pen.text(pen.kufi("…", 5.0F, 600), x + w - 4, y + 8.5F, HudPen.RIGHT, ScreenKit.FAINT);
        } else if (ar) {
            pen.text(s, x + w - 4, y + 9.0F, HudPen.RIGHT, ScreenKit.BONE);
        } else {
            pen.text(s, x + 4, y + 9.0F, HudPen.LEFT, ScreenKit.BONE);
        }
        if (focused && (now / 500L) % 2 == 0) {
            float cx = ar ? x + w - 5 - tw : x + 4 + tw + 0.5F;
            pen.rect(cx, y + 3, 0.75F, 7, ScreenKit.AMBER);
        }
        zone("f:" + f.key, x, y, w, 13);
    }

    private void save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", form.editId);
        if (form.vehicle) {
            t.putString("Entity", form.get("entity").trim());
            t.putString("Name", form.get("name").trim());
            t.putLong("Price", parse(form.get("price")));
            t.putInt("Level", (int) parse(form.get("level")));
            t.putInt("Cat", form.category);
            t.putBoolean("Limited", form.limited);
            t.putInt("Count", (int) Math.max(1, parse(form.get("count"))));
            ClientDealer.send("veh.save", t);
        } else {
            t.putString("Name", form.get("name").trim());
            ClientDealer.send("cat.save", t);
        }
    }

    private static long parse(String s) {
        try {
            return s.isEmpty() ? 0L : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** The vehicle the operator is in, or looking at, as an id for the form. */
    private String pickId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        Entity e = mc.player.getVehicle();
        if (e == null && mc.hitResult instanceof EntityHitResult hit) {
            e = hit.getEntity();
        }
        if (e == null) {
            e = mc.crosshairPickEntity;
        }
        if (e == null) {
            return null;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id == null ? null : id.toString();
    }

    /* ================================================================== input */

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return true;
        }
        String id = kit.hit(mx, my);
        if (id == null) {
            id = zoneAt(mx, my);
        }
        if (id == null) {
            focus = null;
            if (mx < stageX1) {
                dragging = true;
                lastTouch = System.currentTimeMillis();
            }
            return true;
        }
        ScreenKit.click();
        long now = System.currentTimeMillis();
        if (id.startsWith("f:") && form != null) {
            focus = form.field(id.substring(2));
            return true;
        }
        if (id.startsWith("cat:")) {
            filter = Integer.parseInt(id.substring(4));
            scroll = 0;
            settle();
            return true;
        }
        if (id.startsWith("row:")) {
            selected = Integer.parseInt(id.substring(4));
            confirmBuy = -1;
            return true;
        }
        switch (id) {
            case "buy" -> {
                if (confirmBuy == selected && now - confirmAt < 3000L) {
                    CompoundTag t = new CompoundTag();
                    t.putInt("Id", selected);
                    ClientDealer.send("buy", t);
                    confirmBuy = -1;
                } else {
                    confirmBuy = selected;
                    confirmAt = now;
                }
            }
            case "add.veh" -> openVehicleForm(null);
            case "add.cat" -> openCategoryForm(null);
            case "edit.cat" -> openCategoryForm(ClientDealer.category(filter));
            case "edit.veh" -> openVehicleForm(listing(selected));
            case "del.veh" -> {
                if (confirmDelete == selected && now - deleteAt < 3000L) {
                    CompoundTag t = new CompoundTag();
                    t.putInt("Id", selected);
                    ClientDealer.send("veh.delete", t);
                    confirmDelete = -1;
                } else {
                    confirmDelete = selected;
                    deleteAt = now;
                }
            }
            case "up", "down" -> {
                CompoundTag t = new CompoundTag();
                t.putInt("Id", selected);
                t.putInt("By", id.equals("up") ? -1 : 1);
                ClientDealer.send("veh.move", t);
            }
            case "form.save" -> save();
            case "form.cancel" -> {
                form = null;
                focus = null;
            }
            case "form.limited" -> form.limited = !form.limited;
            case "form.cat" -> {
                List<ClientDealer.Category> cats = ClientDealer.CATEGORIES;
                if (!cats.isEmpty()) {
                    int at = 0;
                    for (int i = 0; i < cats.size(); i++) {
                        if (cats.get(i).id() == form.category) {
                            at = i + 1;
                        }
                    }
                    form.category = cats.get(at % cats.size()).id();
                }
            }
            case "form.pick" -> {
                String picked = pickId();
                if (picked != null) {
                    form.field("entity").value = picked;
                } else {
                    result(false, "اركب مركبة أو طالع فيها قبل ما تفتح المعرض");
                }
            }
            case "form.delete" -> {
                if (confirmDelete == -form.editId && now - deleteAt < 3000L) {
                    CompoundTag t = new CompoundTag();
                    t.putInt("Id", form.editId);
                    ClientDealer.send("cat.delete", t);
                    confirmDelete = -1;
                } else {
                    confirmDelete = -form.editId;
                    deleteAt = now;
                }
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            yaw += (float) dx * 1.4F;
            lastTouch = System.currentTimeMillis();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= listX && form == null) {
            scroll -= (int) Math.signum(delta);
        } else if (mx < stageX1) {
            zoom = Math.max(0.6F, Math.min(1.6F, zoom + (float) delta * 0.08F));
            lastTouch = System.currentTimeMillis();
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (form == null || focus == null || c < 32) {
            return false;
        }
        if (focus.numeric && !Character.isDigit(c)) {
            return true;
        }
        if (focus.value.length() < focus.max) {
            focus.value += c;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (form != null) {
            if (key == 256) {
                form = null;
                focus = null;
                return true;
            }
            if (key == 257 || key == 335) {
                save();
                return true;
            }
            if (key == 258) {
                List<Field> shown = new ArrayList<>();
                for (Field f : form.fields) {
                    if (!f.key.equals("count") || form.limited) {
                        shown.add(f);
                    }
                }
                int at = shown.indexOf(focus);
                focus = shown.get((at + 1) % shown.size());
                return true;
            }
            if (focus != null) {
                if (key == 259 && !focus.value.isEmpty()) {
                    focus.value = focus.value.substring(0, focus.value.length() - 1);
                    return true;
                }
                if (Screen.isPaste(key)) {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    for (char c : clip.toCharArray()) {
                        charTyped(c, 0);
                    }
                    return true;
                }
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }
}

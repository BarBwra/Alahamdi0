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
import java.util.Random;

/**
 * The showroom.
 *
 * <pre>
 *  ┌ MLUM MOTORS      [ الكل 5 ]  [ مدرعات 2 ]  [ طيران 3 ]        $balance · level ┐
 *  │                                                                                │
 *  │        ( the vehicle, turning on a lit turntable )        │ section             │
 *  │                                                           │ NAME                │
 *  │                                                           │ needs · kind        │
 *  │                                                           │ $price   [ شراء ]   │
 *  ├────────────────────────────────────────────────────────────────────────────────┤
 *  │  ‹  [card][card][card][card][card][card]  ›   each card a live model + price    │
 *  └────────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>Browsing</b> is a row of cards, each with the vehicle's own model turning in it, its price,
 * and a ribbon for what matters: new this week, already yours, or a level you have not reached.
 * The sections are tabs along the top with how many each holds.</p>
 *
 * <p><b>Buying</b> is a dialog in the middle of the screen: the price, the balance now and after,
 * confirm. When the server says it went through the balance runs down to its new value, coins fall,
 * and the till rings.</p>
 *
 * <p>The vehicle is drawn in this screen only - it is not in the world, nobody else sees it, and it
 * cannot be driven. Editors get an edit switch in the corner that shows the add, edit, move and
 * delete controls; forms take the information column, so the vehicle being entered is previewed
 * live as its id is typed.</p>
 */
@OnlyIn(Dist.CLIENT)
public class DealerScreen extends Screen {

    private static final int CASH = 0xFF8FD16A;
    private static final int CASH_DIM = 0xFF5E7A4C;
    private static final int GOLD = 0xFFF0C04B;

    private final ScreenKit kit = new ScreenKit();
    private final List<float[]> zones = new ArrayList<>();
    private final List<String> zoneIds = new ArrayList<>();
    private String lastZone;

    private int filter;
    private int selected = -1;
    private int carousel;
    private int seen = -1;
    private boolean editing;

    private float yaw = 215.0F;
    private float shownYaw = 215.0F;
    private float zoom = 1.0F;
    private boolean dragging;
    private long lastTouch;
    private long lastFrame;
    private long selectedAt;

    /* the purchase dialog */
    private static final int BUY_NONE = 0;
    private static final int BUY_ASK = 1;
    private static final int BUY_WAIT = 2;
    private static final int BUY_DONE = 3;
    private static final int BUY_FAIL = 4;
    private int buyState = BUY_NONE;
    private ClientDealer.Listing buying;
    private long balanceBefore;
    private long buyAt;
    private String buyMessage = "";
    private final float[][] coins = new float[30][4];

    private int confirmDelete = -1;
    private long deleteAt;
    private String toast;
    private boolean toastOk;
    private long toastAt;

    private Form form;
    private Field focus;

    /* geometry of the last frame */
    private float stageX1;
    private float carY;
    private int carSlots;

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

    private int countIn(int category) {
        int n = 0;
        for (ClientDealer.Listing l : ClientDealer.LISTINGS) {
            if (category == 0 || l.category() == category) {
                n++;
            }
        }
        return n;
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
        for (ClientDealer.Listing l : v) {
            if (l.id() == selected) {
                return;
            }
        }
        select(v.isEmpty() ? -1 : v.get(0).id());
    }

    private void select(int id) {
        if (id != selected) {
            selected = id;
            selectedAt = System.currentTimeMillis();
            yaw = 215.0F;
            shownYaw = 160.0F;
        }
    }

    private boolean ownsDeed(ClientDealer.Listing l) {
        ClientDealer.Owned o = ClientDealer.OWNED.get(l.entity());
        return o != null && !o.consumable() && !l.limited();
    }

    /** Why this cannot be bought right now, or null when it can. */
    private String blocked(ClientDealer.Listing l) {
        if (ownsDeed(l)) {
            return "تملكها";
        }
        if (ClientDealer.level < l.level()) {
            return "تحتاج مستوى " + l.level();
        }
        if (ClientDealer.balance < l.price()) {
            return "رصيدك ما يكفي";
        }
        return null;
    }

    static String money(long v) {
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

    /** The server's answer to a purchase or an edit. */
    public void result(boolean ok, String message) {
        long now = System.currentTimeMillis();
        if (buyState == BUY_WAIT) {
            buyState = ok ? BUY_DONE : BUY_FAIL;
            buyMessage = message;
            buyAt = now;
            if (ok) {
                UiSounds.purchase();
                Random r = new Random();
                for (float[] c : coins) {
                    c[0] = (r.nextFloat() - 0.5F) * 40.0F;
                    c[1] = -40.0F - r.nextFloat() * 60.0F;
                    c[2] = (r.nextFloat() - 0.5F) * 2.0F;
                    c[3] = r.nextFloat() * 0.25F;
                }
            } else {
                UiSounds.tick(false);
            }
            return;
        }
        toast = message;
        toastOk = ok;
        toastAt = now;
        if (ok) {
            UiSounds.coin();
            form = null;
            focus = null;
        } else {
            UiSounds.tick(false);
        }
    }

    /* ================================================================== frame */

    private record Card(ClientDealer.Listing listing, float x, float y, float w, float h) {
    }

    /** A section tab: where it sits, and the vehicle its little picture shows. */
    private record Tab(ClientDealer.Category category, float x, float w, String entity) {
    }

    private static final float TAB_THUMB_W = 30.0F;
    private static final float TAB_THUMB_H = 22.0F;
    private final List<Tab> tabs = new ArrayList<>();
    private float tabsLeft;

    /** Lays the section tabs out, centred; each gets the first vehicle in it as its picture. */
    private void layoutTabs(HudPen pen) {
        tabs.clear();
        List<ClientDealer.Category> cats = new ArrayList<>();
        cats.add(new ClientDealer.Category(0, "الكل"));
        cats.addAll(ClientDealer.CATEGORIES);
        float gap = 10.0F;
        float total = 0;
        float[] widths = new float[cats.size()];
        for (int i = 0; i < cats.size(); i++) {
            widths[i] = pen.width(pen.kufi(cats.get(i).name(), 6.0F, 700)) + TAB_THUMB_W + 26.0F;
            total += widths[i] + gap;
        }
        float x = width / 2.0F + total / 2.0F;
        for (int i = 0; i < cats.size(); i++) {
            ClientDealer.Category c = cats.get(i);
            x -= widths[i];
            String entity = null;
            for (ClientDealer.Listing l : ClientDealer.LISTINGS) {
                if (c.id() == 0 || l.category() == c.id()) {
                    entity = l.entity();
                    break;
                }
            }
            tabs.add(new Tab(c, x, widths[i], entity));
            x -= gap;
        }
        tabsLeft = x;
    }

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
            yaw += dt * 12.0F;
        }
        shownYaw += (yaw - shownYaw) * Math.min(1.0F, dt * 8.0F);

        zones.clear();
        zoneIds.clear();
        float pad = 8.0F;
        float topH = 30.0F;
        float carH = 72.0F;
        carY = height - pad - carH;
        float stageY0 = topH + 6.0F;
        float stageY1 = carY - 6.0F;
        float infoW = Math.min(150.0F, width * 0.3F);
        float infoX = width - pad - infoW;
        stageX1 = infoX - 6.0F;
        float stageCx = (pad + stageX1) / 2.0F;
        float floorY = stageY1 - 16.0F;
        float size = Math.min((stageX1 - pad) * 0.66F, (floorY - stageY0) * 1.35F) * zoom;
        // a new selection rises onto the turntable
        float rise = Math.min(1.0F, (now - selectedAt) / 350.0F);
        rise = 1.0F - (1.0F - rise) * (1.0F - rise);

        List<Card> cards = layoutCards(pad, carH);

        kit.begin(g);
        try {
            backdrop(kit.pen, stageCx, floorY, stageX1 - pad, stageX1);
            for (Card c : cards) {
                cardBack(kit.pen, c, mouseX, mouseY);
            }
            layoutTabs(kit.pen);
            kit.pen.vgrad(0, 0, width, topH, 0xF0040604, 0xC0040604);
            kit.pen.rect(0, topH, width, 0.5F, ScreenKit.LINE);
            for (Tab t : tabs) {
                float tx = t.x() + t.w() - TAB_THUMB_W - 3;
                boolean on = t.category().id() == filter;
                kit.pen.vgrad(tx, 4, TAB_THUMB_W, TAB_THUMB_H, on ? 0xFF2A2716 : 0xFF171A14, 0xFF0C0E0A);
                ellipse(kit.pen, tx + TAB_THUMB_W / 2, 4 + TAB_THUMB_H - 4, TAB_THUMB_W * 0.36F, 1.6F,
                        ScreenKit.alpha(0xFFF4E8CC, on ? 0.12F : 0.06F));
            }
        } finally {
            kit.end();
        }

        String entity = form != null && form.vehicle ? form.get("entity").trim() : null;
        ClientDealer.Listing sel = listing(selected);
        if (entity == null && sel != null) {
            entity = sel.entity();
        }
        boolean drawn = entity != null && !entity.isEmpty() && ResourceLocation.tryParse(entity) != null
                && EntityPreview.showroom(g, entity, stageCx, floorY + (1.0F - rise) * 12.0F, size * (0.9F + 0.1F * rise),
                shownYaw, 14.0F);
        for (Card c : cards) {
            g.enableScissor((int) c.x() + 1, (int) c.y() + 1, (int) (c.x() + c.w()) - 1, (int) (c.y() + 44));
            // pulled back so the whole vehicle fits the card, not just its middle
            EntityPreview.showroom(g, c.listing().entity(), c.x() + c.w() / 2.0F, c.y() + 37.0F, c.w() * 0.40F,
                    c.listing().id() == selected ? shownYaw : 215.0F, 18.0F);
            g.disableScissor();
        }
        for (Tab t : tabs) {
            if (t.entity() == null) {
                continue;
            }
            float tx = t.x() + t.w() - TAB_THUMB_W - 3;
            g.enableScissor((int) tx, 4, (int) (tx + TAB_THUMB_W), (int) (4 + TAB_THUMB_H));
            EntityPreview.showroom(g, t.entity(), tx + TAB_THUMB_W / 2, 4 + TAB_THUMB_H - 4, TAB_THUMB_W * 0.62F, 215.0F, 20.0F);
            g.disableScissor();
        }

        kit.begin(g);
        try {
            HudPen pen = kit.pen;
            topBar(pen, pad, topH, mouseX, mouseY);
            if (!drawn) {
                String why = entity == null || entity.isEmpty()
                        ? (ClientDealer.LISTINGS.isEmpty() ? "المعرض فاضي حالياً" : "اختر مركبة من تحت")
                        : "ما فيه معاينة لهذي المركبة";
                pen.text(pen.kufi(why, 7.0F, 600), stageCx, (stageY0 + floorY) / 2.0F, HudPen.CENTER, ScreenKit.FAINT);
            } else {
                pen.text(pen.kufi("اسحب بالماوس عشان تلفها · العجلة للتقريب", 4.4F, 600), stageCx, stageY1 - 2.0F,
                        HudPen.CENTER, ScreenKit.alpha(ScreenKit.FAINT, 0.85F));
            }
            if (form != null) {
                drawForm(pen, infoX, stageY0, infoW, stageY1 - stageY0, mouseX, mouseY, now);
            } else {
                info(pen, sel, infoX, stageY0, infoW, stageY1 - stageY0, mouseX, mouseY, now);
            }
            carouselFront(pen, cards, pad, carH, mouseX, mouseY, now);
            drawToast(pen, stageCx, stageY0 + 4, now);
            if (buyState != BUY_NONE) {
                buyDialog(pen, mouseX, mouseY, now);
            }
        } finally {
            kit.end();
        }
        hoverSound(mouseX, mouseY);
    }

    private void hoverSound(int mx, int my) {
        String z = zoneAt(mx, my);
        if (z != null && !z.equals(lastZone) && z.startsWith("card:")) {
            UiSounds.hover();
        }
        lastZone = z;
    }

    /* ================================================================== the room */

    private void backdrop(HudPen pen, float cx, float floorY, float stageW, float stageX1) {
        pen.vgrad(0, 0, width, height, 0xFF13160F, 0xFF040504);
        for (float x = 4; x < stageX1; x += 34.0F) {
            pen.vgrad(x, 24, 0.5F, floorY - 38, 0x12FFFFFF, 0x02FFFFFF);
        }
        pen.vgrad(0, floorY - 22, width, 22, 0x00000000, 0x55000000);
        pen.rect(0, floorY - 14, width, 0.5F, 0x16FFFFFF);
        float steps = 40.0F;
        float top = 24.0F;
        float span = floorY - top;
        for (int i = 0; i < steps; i++) {
            float t = i / steps;
            float w = 14.0F + stageW * 0.62F * t;
            pen.rect(cx - w / 2.0F, top + span * t, w, span / steps + 0.5F, ScreenKit.alpha(0xFFF4E8CC, 0.02F + 0.03F * t));
        }
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
        pen.vgrad(0, carY - 10, width, height - carY + 10, 0x00000000, 0xC0000000);
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

    /* ================================================================== the top bar */

    private void topBar(HudPen pen, float pad, float h, int mx, int my) {
        Shaped mark = pen.pixel("MLUM MOTORS", 11.0F, 700);
        pen.glow(mark, pad + 2, 19.0F, HudPen.LEFT, 2.0F, ScreenKit.alpha(ScreenKit.AMBER, 0.35F));
        pen.text(mark, pad + 2, 19.0F, HudPen.LEFT, ScreenKit.BONE);

        // the sections: picture, name, count; an underline on the one shown
        for (Tab t : tabs) {
            ClientDealer.Category c = t.category();
            float x = t.x();
            float w = t.w();
            boolean on = c.id() == filter;
            boolean hot = mx >= x && mx < x + w && my >= 0 && my < h;
            int color = on ? ScreenKit.AMBER : hot ? ScreenKit.BONE : ScreenKit.MUTED;
            float tx = x + w - TAB_THUMB_W - 3;
            outline(pen, tx, 4, TAB_THUMB_W, TAB_THUMB_H, on ? ScreenKit.AMBER : hot ? ScreenKit.AMBER_DIM : ScreenKit.LINE);
            pen.text(pen.kufi(c.name(), 6.0F, 700), tx - 4, 18.0F, HudPen.RIGHT, color);
            String n = String.valueOf(countIn(c.id()));
            Shaped ns = pen.pixel(n, 6.0F, 700);
            float nw = pen.width(ns) + 4;
            pen.rect(x + 1, 12.0F, nw, 8.0F, on ? ScreenKit.alpha(ScreenKit.AMBER, 0.2F) : 0x14FFFFFF);
            pen.text(ns, x + 1 + nw / 2, 18.5F, HudPen.CENTER, color);
            if (on) {
                pen.rect(x, h - 2, w, 2, ScreenKit.AMBER);
            }
            zone("cat:" + c.id(), x, 0, w, h);
        }
        float x = tabsLeft;
        if (editing) {
            kit.button("add.cat", "+", x - 14, 9, 14, 12, ScreenKit.GHOST, mx, my);
            if (filter != 0) {
                kit.button("edit.cat", "تعديل القسم", x - 64, 9, 46, 12, ScreenKit.GHOST, mx, my);
            }
        }

        // the buyer: balance and level, right
        float right = width - pad;
        if (ClientDealer.edit) {
            kit.button("edit", editing ? "خلّصت" : "تعديل", right - 34, 9, 34, 12, editing ? ScreenKit.FILLED : ScreenKit.GHOST, mx, my);
            right -= 40;
        }
        Shaped lv = pen.pixel(String.valueOf(ClientDealer.level), 8.0F, 700);
        pen.text(lv, right, 19.0F, HudPen.RIGHT, ScreenKit.BONE);
        Shaped lvl = pen.kufi("مستواك", 4.8F, 600);
        pen.text(lvl, right - pen.width(lv) - 3, 18.5F, HudPen.RIGHT, ScreenKit.MUTED);
        right -= pen.width(lv) + pen.width(lvl) + 12;
        Shaped bal = pen.pixel(money(ClientDealer.balance), 9.0F, 700);
        float bw = pen.width(bal) + 12;
        pen.rect(right - bw, 8, bw, 14, 0x332B4A1F);
        outline(pen, right - bw, 8, bw, 14, 0xFF3F6B2E);
        pen.text(bal, right - bw / 2, 18.5F, HudPen.CENTER, CASH);
    }

    /* ================================================================== the information column */

    private void info(HudPen pen, ClientDealer.Listing l, float x, float y, float w, float h, int mx, int my, long now) {
        pen.vgrad(x, y, w, h, 0xB00B0D0A, 0x900B0D0A);
        pen.rect(x + w - 1.5F, y, 1.5F, h, ScreenKit.AMBER);
        if (l == null) {
            pen.text(pen.kufi("اختر مركبة", 6.5F, 600), x + w / 2, y + h / 2, HudPen.CENTER, ScreenKit.FAINT);
            return;
        }
        float right = x + w - 8;
        float cy = y + 12;
        ClientDealer.Category c = ClientDealer.category(l.category());
        pen.text(pen.kufi(c == null ? "" : c.name(), 5.0F, 700), right, cy, HudPen.RIGHT, ScreenKit.AMBER);
        if (l.fresh()) {
            ribbon(pen, "جديد", x + 8, cy - 6, ScreenKit.AMBER);
        }
        cy += 14;
        pen.text(pen.kufi(l.name(), 10.0F, 700), right, cy, HudPen.RIGHT, ScreenKit.BONE);
        cy += 10;

        boolean levelOk = ClientDealer.level >= l.level();
        cy = line(pen, "المستوى", l.level() > 0 ? String.valueOf(l.level()) : "بدون", levelOk ? ScreenKit.SAGE : ScreenKit.RUST,
                x + 8, right, cy);
        cy = line(pen, "النوع", l.limited() ? "محدودة · " + l.count() + " استخدام" : "دائمة", l.limited() ? ScreenKit.AMBER : ScreenKit.SOFT,
                x + 8, right, cy);
        ClientDealer.Owned o = ClientDealer.OWNED.get(l.entity());
        cy = line(pen, "عندك", o == null ? "لا" : o.consumable() ? "×" + o.count() : "تملكها", o == null ? ScreenKit.FAINT : ScreenKit.SAGE,
                x + 8, right, cy);
        pen.text(pen.kufi(l.limited() ? "كل مرة تطلّعها تنقص وحدة، وترجع إذا خزّنتها" : "لك للأبد · تطلّعها وتخزّنها متى ما بغيت",
                4.3F, 600), right, cy + 4, HudPen.RIGHT, ScreenKit.FAINT);

        // price and the button, at the foot of the column
        float by = y + h - 26;
        pen.text(pen.kufi("السعر", 4.8F, 600), right, by - 20, HudPen.RIGHT, ScreenKit.MUTED);
        boolean afford = ClientDealer.balance >= l.price();
        pen.text(pen.pixel(money(l.price()), 15.0F, 700), right, by - 5, HudPen.RIGHT, afford ? CASH : CASH_DIM);
        String why = blocked(l);
        if (why != null) {
            pen.rect(x + 8, by, w - 16, 18, 0x0CFFFFFF);
            outline(pen, x + 8, by, w - 16, 18, ScreenKit.LINE);
            pen.text(pen.kufi(why, 6.0F, 700), x + w / 2, by + 11.5F, HudPen.CENTER, ownsDeed(l) ? ScreenKit.SAGE : ScreenKit.FAINT);
        } else {
            kit.button("buy", "شراء", x + 8, by, w - 16, 18, ScreenKit.FILLED, mx, my);
        }

        if (editing) {
            float ty = by - 40;
            float bw = (w - 16 - 9) / 4.0F;
            kit.button("edit.veh", "تعديل", x + 8, ty, bw, 10, ScreenKit.GHOST, mx, my);
            boolean del = confirmDelete == l.id() && now - deleteAt < 3000L;
            kit.button("del.veh", del ? "متأكد؟" : "حذف", x + 8 + bw + 3, ty, bw, 10, ScreenKit.DANGER, mx, my);
            kit.button("up", "›", x + 8 + (bw + 3) * 2, ty, bw, 10, ScreenKit.GHOST, mx, my);
            kit.button("down", "‹", x + 8 + (bw + 3) * 3, ty, bw, 10, ScreenKit.GHOST, mx, my);
        }
    }

    /** A label on the right, its value on the left, a hairline under. Returns the next line's y. */
    private static float line(HudPen pen, String label, String value, int color, float left, float right, float y) {
        y += 10;
        pen.text(pen.kufi(label, 4.8F, 600), right, y, HudPen.RIGHT, ScreenKit.MUTED);
        Shaped v = arabic(value) ? pen.kufi(value, 5.2F, 700) : pen.pixel(value, 7.0F, 700);
        pen.text(v, left, y, HudPen.LEFT, color);
        pen.rect(left, y + 3, right - left, 0.5F, ScreenKit.LINE_SOFT);
        return y;
    }

    private static void ribbon(HudPen pen, String text, float x, float y, int color) {
        Shaped s = pen.kufi(text, 4.6F, 700);
        float w = pen.width(s) + 8;
        pen.rect(x, y, w, 8.5F, color);
        pen.text(s, x + w / 2, y + 6.3F, HudPen.CENTER, ScreenKit.INK);
    }

    /* ================================================================== the carousel */

    private static final float CARD_W = 86.0F;
    private static final float CARD_H = 62.0F;
    private static final float CARD_GAP = 6.0F;

    private List<Card> layoutCards(float pad, float carH) {
        List<ClientDealer.Listing> v = visible();
        float inner = width - pad * 2 - 32;
        carSlots = Math.max(1, (int) ((inner + CARD_GAP) / (CARD_W + CARD_GAP)));
        carousel = Math.max(0, Math.min(carousel, Math.max(0, v.size() - carSlots)));
        int shown = Math.min(carSlots, v.size() - carousel);
        float rowW = shown * CARD_W + Math.max(0, shown - 1) * CARD_GAP;
        float right = width / 2.0F + rowW / 2.0F;
        List<Card> out = new ArrayList<>();
        for (int j = 0; j < shown; j++) {
            ClientDealer.Listing l = v.get(carousel + j);
            boolean sel = l.id() == selected;
            float x = right - (j + 1) * CARD_W - j * CARD_GAP;
            float y = carY + 5 - (sel ? 3 : 0);
            out.add(new Card(l, x, y, CARD_W, CARD_H));
        }
        return out;
    }

    private void cardBack(HudPen pen, Card c, int mx, int my) {
        boolean sel = c.listing().id() == selected;
        boolean hot = mx >= c.x() && mx < c.x() + c.w() && my >= c.y() && my < c.y() + c.h();
        if (sel) {
            for (int k = 0; k < 4; k++) {
                pen.rect(c.x() - k, c.y() - k, c.w() + k * 2, c.h() + k * 2, ScreenKit.alpha(ScreenKit.AMBER, 0.05F));
            }
        }
        pen.vgrad(c.x(), c.y(), c.w(), c.h(), sel ? 0xF02A2716 : hot ? 0xF01E221A : 0xF0161A13, 0xF00C0E0A);
        // the little stage the model stands on
        ellipse(pen, c.x() + c.w() / 2, c.y() + 39, c.w() * 0.34F, 3.2F, ScreenKit.alpha(0xFFF4E8CC, sel ? 0.10F : 0.05F));
    }

    private void carouselFront(HudPen pen, List<Card> cards, float pad, float carH, int mx, int my, long now) {
        List<ClientDealer.Listing> v = visible();
        for (Card c : cards) {
            ClientDealer.Listing l = c.listing();
            boolean sel = l.id() == selected;
            boolean hot = mx >= c.x() && mx < c.x() + c.w() && my >= c.y() && my < c.y() + c.h();
            outline(pen, c.x(), c.y(), c.w(), c.h(), sel ? ScreenKit.AMBER : hot ? ScreenKit.AMBER_DIM : ScreenKit.LINE);
            if (sel) {
                pen.rect(c.x(), c.y() + c.h() - 1.5F, c.w(), 1.5F, ScreenKit.AMBER);
            }
            boolean levelOk = ClientDealer.level >= l.level();
            if (!levelOk) {
                pen.rect(c.x() + 1, c.y() + 1, c.w() - 2, 43, 0x99000000);
                padlock(pen, c.x() + c.w() / 2 - 4, c.y() + 14, ScreenKit.RUST);
                pen.text(pen.kufi("مستوى " + l.level(), 4.8F, 700), c.x() + c.w() / 2, c.y() + 36, HudPen.CENTER, ScreenKit.RUST);
            }
            pen.text(pen.kufi(l.name(), 5.0F, 700), c.x() + c.w() - 4, c.y() + 51, HudPen.RIGHT, sel ? ScreenKit.BONE : ScreenKit.SOFT);
            boolean afford = ClientDealer.balance >= l.price();
            pen.text(pen.pixel(money(l.price()), 6.5F, 700), c.x() + 4, c.y() + 59, HudPen.LEFT, afford ? CASH : CASH_DIM);
            if (l.fresh()) {
                ribbon(pen, "جديد", c.x() + 2, c.y() + 2, ScreenKit.AMBER);
            }
            ClientDealer.Owned o = ClientDealer.OWNED.get(l.entity());
            if (o != null) {
                Shaped s = pen.kufi(o.consumable() ? "×" + o.count() : "لك", 4.5F, 700);
                float w = pen.width(s) + 7;
                pen.rect(c.x() + c.w() - w - 2, c.y() + 2, w, 8, ScreenKit.alpha(ScreenKit.SAGE, 0.85F));
                pen.text(s, c.x() + c.w() - 2 - w / 2, c.y() + 8, HudPen.CENTER, ScreenKit.INK);
            }
            if (l.limited()) {
                pen.text(pen.kufi("محدودة", 4.2F, 700), c.x() + c.w() - 4, c.y() + 59, HudPen.RIGHT, ScreenKit.AMBER);
            }
            zone("card:" + l.id(), c.x(), c.y(), c.w(), c.h());
        }
        // the arrows when there is more than fits
        float midY = carY + 5 + CARD_H / 2 - 9;
        if (carousel > 0) {
            kit.button("car.prev", "›", width - pad - 14, midY, 14, 18, ScreenKit.GHOST, mx, my);
        }
        if (carousel + carSlots < v.size()) {
            kit.button("car.next", "‹", pad, midY, 14, 18, ScreenKit.GHOST, mx, my);
        }
        if (editing) {
            kit.button("add.veh", "+ مركبة جديدة", width / 2 - 40, carY - 13, 80, 11, ScreenKit.GHOST, mx, my);
        }
        if (v.isEmpty()) {
            pen.text(pen.kufi(editing ? "ما فيه مركبات هنا · اضغط + مركبة جديدة" : "ما فيه مركبات في هذا القسم", 6.0F, 600),
                    width / 2.0F, carY + 38, HudPen.CENTER, ScreenKit.FAINT);
        }
    }

    private static void padlock(HudPen pen, float x, float y, int argb) {
        outline(pen, x + 2, y, 4, 5, argb);
        pen.rect(x, y + 4, 8, 7, argb);
        pen.rect(x + 3.5F, y + 6, 1, 3, 0xFF0B0D0A);
    }

    /* ================================================================== buying */

    private void buyDialog(HudPen pen, int mx, int my, long now) {
        ClientDealer.Listing l = buying;
        if (l == null) {
            buyState = BUY_NONE;
            return;
        }
        pen.rect(0, 0, width, height, 0xB8030403);
        float w = 210;
        float h = buyState == BUY_DONE ? 150 : 132;
        float x = (width - w) / 2.0F;
        float y = (height - h) / 2.0F;
        // the dialog rises into place
        float in = Math.min(1.0F, (now - buyAt) / 180.0F);
        y += (1.0F - in) * 10.0F;
        kit.panel(x, y, w, h);
        float right = x + w - 10;

        if (buyState == BUY_DONE) {
            float t = Math.min(1.0F, (now - buyAt) / 1200.0F);
            float e = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            float cx = x + w / 2;
            // a burst of light behind the tick
            float pulse = 0.5F + 0.5F * (float) Math.sin(now / 160.0D);
            for (int k = 0; k < 5; k++) {
                ellipse(pen, cx, y + 30, 26 - k * 4, 26 - k * 4, ScreenKit.alpha(GOLD, 0.05F + 0.03F * pulse));
            }
            ellipse(pen, cx, y + 30, 13, 13, ScreenKit.SAGE);
            tick(pen, cx, y + 30, 0xFF0B0D0A);
            pen.glow(pen.kufi("تم الشراء!", 11.0F, 700), cx, y + 60, HudPen.CENTER, 3.0F, ScreenKit.alpha(GOLD, 0.5F));
            pen.text(pen.kufi("تم الشراء!", 11.0F, 700), cx, y + 60, HudPen.CENTER, ScreenKit.BONE);
            pen.text(pen.kufi(l.name() + " صارت لك · تلقاها في قائمة مركباتك", 5.0F, 600), cx, y + 72, HudPen.CENTER,
                    ScreenKit.SOFT);
            // the balance running down to what is left
            long shown = balanceBefore - Math.round(l.price() * e);
            pen.text(pen.kufi("رصيدك", 5.0F, 600), cx, y + 88, HudPen.CENTER, ScreenKit.MUTED);
            pen.text(pen.pixel(money(shown), 14.0F, 700), cx, y + 104, HudPen.CENTER, CASH);
            float fy = y + 96 - e * 18;
            pen.text(pen.pixel("-" + money(l.price()), 8.0F, 700), cx + 50, fy, HudPen.LEFT,
                    ScreenKit.alpha(ScreenKit.RUST, 1.0F - e * 0.8F));
            coins(pen, cx, y + 30, now);
            kit.button("buy.ok", "تمام", x + w / 2 - 40, y + h - 26, 80, 16, ScreenKit.FILLED, mx, my);
            return;
        }

        pen.text(pen.kufi(buyState == BUY_FAIL ? "ما تم الشراء" : "تأكيد الشراء", 7.5F, 700), right, y + 16, HudPen.RIGHT,
                buyState == BUY_FAIL ? ScreenKit.RUST : ScreenKit.BONE);
        pen.rect(right - 1.5F, y + 9, 1.5F, 8, buyState == BUY_FAIL ? ScreenKit.RUST : ScreenKit.AMBER);
        pen.text(pen.kufi(l.name(), 9.0F, 700), right, y + 33, HudPen.RIGHT, ScreenKit.AMBER);
        float ly = y + 38;
        ly = line(pen, "السعر", money(l.price()), ScreenKit.BONE, x + 10, right, ly);
        ly = line(pen, "رصيدك الحين", money(balanceBefore), CASH, x + 10, right, ly);
        ly = line(pen, "بعد الشراء", money(balanceBefore - l.price()), ScreenKit.SOFT, x + 10, right, ly);
        ly = line(pen, "النوع", l.limited() ? "محدودة · " + l.count() + " استخدام" : "دائمة", ScreenKit.SOFT, x + 10, right, ly);
        float by = y + h - 26;
        if (buyState == BUY_FAIL) {
            pen.text(pen.kufi(buyMessage, 5.5F, 700), x + w / 2, by - 6, HudPen.CENTER, ScreenKit.RUST);
            kit.button("buy.cancel", "رجوع", x + w / 2 - 40, by, 80, 16, ScreenKit.GHOST, mx, my);
        } else if (buyState == BUY_WAIT) {
            int dots = (int) (now / 300L % 4);
            pen.text(pen.kufi("جاري الشراء" + ".".repeat(dots), 6.5F, 700), x + w / 2, by + 11, HudPen.CENTER, ScreenKit.MUTED);
        } else {
            float bw = (w - 26) / 2;
            kit.button("buy.yes", "تأكيد الشراء", x + 10 + bw + 6, by, bw, 16, ScreenKit.FILLED, mx, my);
            kit.button("buy.cancel", "إلغاء", x + 10, by, bw, 16, ScreenKit.GHOST, mx, my);
        }
    }

    /** A tick mark out of two strokes of little squares. */
    private static void tick(HudPen pen, float cx, float cy, int argb) {
        for (float t = 0; t <= 1.0F; t += 0.08F) {
            pen.rect(cx - 6 + t * 4, cy + t * 4, 2.2F, 2.2F, argb);
        }
        for (float t = 0; t <= 1.0F; t += 0.05F) {
            pen.rect(cx - 2 + t * 8, cy + 4 - t * 9, 2.2F, 2.2F, argb);
        }
    }

    /** Gold coins thrown up from the tick, falling and fading. */
    private void coins(HudPen pen, float cx, float cy, long now) {
        float t = (now - buyAt) / 1000.0F;
        for (float[] c : coins) {
            float tt = t - c[3];
            if (tt < 0 || tt > 1.6F) {
                continue;
            }
            float px = cx + c[0] * tt * 2.2F;
            float py = cy + c[1] * tt + 90.0F * tt * tt;
            float a = Math.max(0.0F, 1.0F - tt / 1.6F);
            float s = 2.4F;
            pen.rect(px - s / 2, py - s / 2, s, s, ScreenKit.alpha(GOLD, a));
            pen.rect(px - s / 2, py - s / 2, s * 0.5F, s * 0.5F, ScreenKit.alpha(0xFFFFF1C2, a));
        }
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

    /* ================================================================== zones */

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

    /* ================================================================== the editor */

    private static final class Field {
        final String key;
        final String label;
        final boolean numeric;
        final int max;
        String value;

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

    private void drawForm(HudPen pen, float x, float y, float w, float h, int mx, int my, long now) {
        pen.rect(x, y, w, h, 0xE80B0D0A);
        outline(pen, x, y, w, h, ScreenKit.LINE);
        pen.rect(x + w - 1.5F, y, 1.5F, h, ScreenKit.AMBER);
        float right = x + w - 7;
        String title = form.vehicle ? (form.editId > 0 ? "تعديل مركبة" : "مركبة جديدة")
                : (form.editId > 0 ? "تعديل القسم" : "قسم جديد");
        pen.text(pen.kufi(title, 7.0F, 700), right, y + 13, HudPen.RIGHT, ScreenKit.BONE);
        float fx = x + 7;
        float fw = w - 14;
        float cy = y + 26;
        float step = 21;
        if (form.vehicle) {
            field(pen, form.field("entity"), fx + 36, cy, fw - 36, now);
            kit.button("form.pick", "اللي قدامك", fx, cy, 33, 12, ScreenKit.GHOST, mx, my);
            cy += step;
            field(pen, form.field("name"), fx, cy, fw, now);
            cy += step;
            float half = (fw - 4) / 2;
            field(pen, form.field("price"), fx + half + 4, cy, half, now);
            field(pen, form.field("level"), fx, cy, half, now);
            cy += step;
            label(pen, "القسم", fx + fw, cy - 2.5F);
            ClientDealer.Category c = ClientDealer.category(form.category);
            kit.button("form.cat", c == null ? "سو قسم أول" : c.name(), fx + half + 4, cy, half, 12, ScreenKit.GHOST, mx, my);
            label(pen, "النوع", fx + half, cy - 2.5F);
            kit.button("form.limited", form.limited ? "محدودة" : "دائمة", fx, cy, half, 12,
                    form.limited ? ScreenKit.FILLED : ScreenKit.GHOST, mx, my);
            cy += step;
            if (form.limited) {
                field(pen, form.field("count"), fx, cy, fw, now);
                cy += step;
            }
            pen.text(pen.kufi("اركب المركبة أو طالعها قبل ما تفتح المعرض", 4.2F, 600), fx + fw, cy - 4, HudPen.RIGHT, ScreenKit.FAINT);
            pen.text(pen.kufi("واضغط «اللي قدامك» · المعاينة على اليسار", 4.2F, 600), fx + fw, cy + 3, HudPen.RIGHT, ScreenKit.FAINT);
            cy += 9;
        } else {
            field(pen, form.field("name"), fx, cy, fw, now);
            cy += step;
        }
        float bw = (fw - 4) / 2;
        kit.button("form.save", "حفظ", fx + bw + 4, cy, bw, 14, ScreenKit.FILLED, mx, my);
        kit.button("form.cancel", "إلغاء", fx, cy, bw, 14, ScreenKit.GHOST, mx, my);
        if (!form.vehicle && form.editId > 0) {
            boolean del = confirmDelete == -form.editId && now - deleteAt < 3000L;
            kit.button("form.delete", del ? "متأكد؟ اضغط مرة ثانية" : "حذف القسم", fx, cy + 18, fw, 12, ScreenKit.DANGER, mx, my);
        }
    }

    private static void label(HudPen pen, String text, float right, float baseline) {
        pen.text(pen.kufi(text, 4.6F, 600), right, baseline, HudPen.RIGHT, ScreenKit.MUTED);
    }

    private void field(HudPen pen, Field f, float x, float y, float w, long now) {
        label(pen, f.label, x + w, y - 2.5F);
        boolean focused = f == focus;
        pen.rect(x, y, w, 12, 0xA0000000);
        outline(pen, x, y, w, 12, focused ? ScreenKit.AMBER : ScreenKit.LINE);
        boolean ar = arabic(f.value);
        Shaped s = ar ? pen.kufi(f.value, 5.4F, 600) : pen.pixel(f.value, 6.5F, 600);
        float tw = pen.width(s);
        if (f.value.isEmpty()) {
            pen.text(pen.kufi("…", 5.0F, 600), x + w - 4, y + 8.0F, HudPen.RIGHT, ScreenKit.FAINT);
        } else if (ar) {
            pen.text(s, x + w - 4, y + 8.5F, HudPen.RIGHT, ScreenKit.BONE);
        } else {
            pen.text(s, x + 3, y + 8.5F, HudPen.LEFT, ScreenKit.BONE);
        }
        if (focused && (now / 500L) % 2 == 0) {
            float cx = ar ? x + w - 5 - tw : x + 3 + tw + 0.5F;
            pen.rect(cx, y + 2.5F, 0.75F, 7, ScreenKit.AMBER);
        }
        zone("f:" + f.key, x, y, w, 12);
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
        if (id == null && buyState == BUY_NONE) {
            id = zoneAt(mx, my);
        }
        long now = System.currentTimeMillis();
        if (buyState != BUY_NONE) {
            // the dialog owns every click while it is up
            if (id == null) {
                return true;
            }
            ScreenKit.click();
            switch (id) {
                case "buy.yes" -> {
                    CompoundTag t = new CompoundTag();
                    t.putInt("Id", buying.id());
                    ClientDealer.send("buy", t);
                    buyState = BUY_WAIT;
                }
                case "buy.cancel", "buy.ok" -> {
                    buyState = BUY_NONE;
                    buying = null;
                }
                default -> {
                }
            }
            return true;
        }
        if (id == null) {
            focus = null;
            if (mx < stageX1 && my < carY) {
                dragging = true;
                lastTouch = now;
            }
            return true;
        }
        ScreenKit.click();
        if (id.startsWith("f:") && form != null) {
            focus = form.field(id.substring(2));
            return true;
        }
        if (id.startsWith("cat:")) {
            filter = Integer.parseInt(id.substring(4));
            carousel = 0;
            settle();
            return true;
        }
        if (id.startsWith("card:")) {
            select(Integer.parseInt(id.substring(5)));
            return true;
        }
        switch (id) {
            case "buy" -> {
                ClientDealer.Listing l = listing(selected);
                if (l != null && blocked(l) == null) {
                    buying = l;
                    balanceBefore = ClientDealer.balance;
                    buyState = BUY_ASK;
                    buyAt = now;
                    UiSounds.open();
                }
            }
            case "edit" -> {
                editing = !editing;
                form = null;
                focus = null;
            }
            case "car.prev" -> carousel = Math.max(0, carousel - 1);
            case "car.next" -> carousel++;
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
        if (buyState != BUY_NONE) {
            return true;
        }
        if (my >= carY) {
            carousel -= (int) Math.signum(delta);
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
        if (buyState != BUY_NONE) {
            if (key == 256 && buyState != BUY_WAIT) {
                buyState = BUY_NONE;
                buying = null;
            }
            return true;
        }
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
        // the arrow keys walk the carousel
        if (key == 262 || key == 263) {
            List<ClientDealer.Listing> v = visible();
            int at = 0;
            for (int i = 0; i < v.size(); i++) {
                if (v.get(i).id() == selected) {
                    at = i;
                }
            }
            // right-to-left: the left arrow goes on to the next card
            int next = Math.max(0, Math.min(v.size() - 1, at + (key == 263 ? 1 : -1)));
            if (!v.isEmpty()) {
                select(v.get(next).id());
                if (next < carousel) {
                    carousel = next;
                } else if (next >= carousel + carSlots) {
                    carousel = next - carSlots + 1;
                }
            }
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}

package com.barbwra.mlum.client.diplomacy;

import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.client.screens.ScreenKit;
import com.barbwra.mlum.client.ui.mc.UiSounds;
import com.barbwra.mlum.client.ui.text.Shaped;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The war room: alliances, wars and bounties, one tab each.
 *
 * <p>The right-hand column is what you act on - every faction on the server for the first two tabs,
 * everyone online for bounties - and the left is what is going on: your allies and the requests
 * waiting, your wars with their kills and money, and the board of wanted players. The buttons that
 * cost something or cannot be undone ask for a second click.</p>
 */
@OnlyIn(Dist.CLIENT)
public class DiplomacyScreen extends Screen {

    private static final int CASH = 0xFF8FD16A;
    private static final int BLOOD = 0xFFD9472F;
    private static final String[] TABS = {"التحالفات", "الحروب", "المطلوبين"};

    private final ScreenKit kit = new ScreenKit();
    private final List<float[]> zones = new ArrayList<>();
    private final List<String> zoneIds = new ArrayList<>();

    private int tab;
    private UUID pickedFaction;
    private UUID pickedPlayer;
    private int listScroll;
    private String amount = "";
    private boolean typing;
    private String confirm;
    private long confirmAt;
    private float listX;
    private float listTop;
    private float listBottom;

    public DiplomacyScreen() {
        super(Component.literal("Diplomacy"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static CompoundTag d() {
        return ClientDiplomacy.data;
    }

    private static String money(long v) {
        return "$" + String.format(Locale.ROOT, "%,d", v);
    }

    private static String time(long ms) {
        long h = ms / 3_600_000L;
        if (h >= 24) {
            return (h / 24) + " يوم" + (h % 24 > 0 ? " و " + (h % 24) + " ساعة" : "");
        }
        if (h > 0) {
            return h + " ساعة";
        }
        return Math.max(1, ms / 60_000L) + " دقيقة";
    }

    private boolean confirmed(String id) {
        long now = System.currentTimeMillis();
        if (id.equals(confirm) && now - confirmAt < 3000L) {
            confirm = null;
            return true;
        }
        confirm = id;
        confirmAt = now;
        return false;
    }

    private boolean confirming(String id) {
        return id.equals(confirm) && System.currentTimeMillis() - confirmAt < 3000L;
    }

    /* ================================================================== frame */

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        zones.clear();
        zoneIds.clear();
        kit.begin(g);
        try {
            HudPen pen = kit.pen;
            backdrop(pen);
            float pad = 10.0F;
            float top = 32.0F;
            topBar(pen, top, mx, my);
            if (!d().getBoolean("InFaction")) {
                pen.text(pen.kufi("لازم تكون في منظمة عشان الدبلوماسية", 8.0F, 700), width / 2.0F, height / 2.0F,
                        HudPen.CENTER, ScreenKit.FAINT);
                return;
            }
            float listW = Math.min(170.0F, width * 0.34F);
            listX = width - pad - listW;
            float y0 = top + pad;
            float h = height - y0 - pad;
            kit.panel(listX, y0, listW, h);
            if (tab == 2) {
                playerList(pen, listX, y0, listW, h, mx, my);
            } else {
                factionList(pen, listX, y0, listW, h, mx, my);
            }
            float lx = pad;
            float lw = listX - pad - lx;
            switch (tab) {
                case 0 -> alliances(pen, lx, y0, lw, h, mx, my);
                case 1 -> wars(pen, lx, y0, lw, h, mx, my);
                default -> bounties(pen, lx, y0, lw, h, mx, my);
            }
        } finally {
            kit.end();
        }
    }

    private void backdrop(HudPen pen) {
        pen.vgrad(0, 0, width, height, 0xFF11140F, 0xFF050605);
        // a faint map grid, the war room's table
        for (float x = 0; x < width; x += 24) {
            pen.rect(x, 0, 0.5F, height, 0x08FFFFFF);
        }
        for (float y = 0; y < height; y += 24) {
            pen.rect(0, y, width, 0.5F, 0x08FFFFFF);
        }
        if (tab == 1) {
            pen.vgrad(0, height * 0.5F, width, height * 0.5F, 0x00000000, 0x22D9472F);
        }
    }

    private void topBar(HudPen pen, float h, int mx, int my) {
        pen.vgrad(0, 0, width, h, 0xF0040604, 0xC0040604);
        pen.rect(0, h, width, 0.5F, ScreenKit.LINE);
        float right = width - 10;
        pen.text(pen.kufi("الدبلوماسية", 9.0F, 700), right, 20, HudPen.RIGHT, ScreenKit.BONE);
        Shaped title = pen.kufi("الدبلوماسية", 9.0F, 700);
        float r2 = right - pen.width(title) - 10;
        if (d().getBoolean("InFaction")) {
            pen.text(pen.kufi(d().getString("Name") + " · مستوى " + d().getInt("Level"), 5.5F, 700), r2, 19, HudPen.RIGHT,
                    ScreenKit.AMBER);
        }
        // the tabs, centred
        float tw = 70;
        float x = width / 2.0F + tw * TABS.length / 2.0F;
        for (int i = 0; i < TABS.length; i++) {
            x -= tw;
            boolean on = i == tab;
            boolean hot = mx >= x && mx < x + tw && my < h;
            String label = TABS[i];
            int n = i == 0 ? count("ally") : i == 1 ? d().getList("Wars", Tag.TAG_COMPOUND).size()
                    : d().getList("Bounties", Tag.TAG_COMPOUND).size();
            pen.text(pen.kufi(label, 6.5F, 700), x + tw / 2 + 5, 19, HudPen.CENTER,
                    on ? (i == 1 ? BLOOD : ScreenKit.AMBER) : hot ? ScreenKit.BONE : ScreenKit.MUTED);
            if (n > 0) {
                Shaped ns = pen.pixel(String.valueOf(n), 6.0F, 700);
                float nw = pen.width(ns) + 4;
                float nx = x + tw / 2 - pen.width(pen.kufi(label, 6.5F, 700)) / 2 - nw - 1;
                pen.rect(nx, 13, nw, 8, i == 1 ? 0x55D9472F : 0x33F0A93B);
                pen.text(ns, nx + nw / 2, 19.5F, HudPen.CENTER, ScreenKit.BONE);
            }
            if (on) {
                pen.rect(x + 6, h - 2, tw - 12, 2, i == 1 ? BLOOD : ScreenKit.AMBER);
            }
            zone("tab:" + i, x, 0, tw, h);
        }
        if (d().getBoolean("InFaction")) {
            Shaped bank = pen.pixel(money(d().getInt("Bank")), 8.0F, 700);
            pen.text(pen.kufi("بنك المنظمة", 4.8F, 600), 10, 13, HudPen.LEFT, ScreenKit.MUTED);
            pen.text(bank, 10, 23, HudPen.LEFT, CASH);
        }
    }

    private int count(String status) {
        int n = 0;
        ListTag list = d().getList("Factions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            if (status.equals(list.getCompound(i).getString("Status"))) {
                n++;
            }
        }
        return n;
    }

    /* ================================================================== the right-hand lists */

    private void factionList(HudPen pen, float x, float y, float w, float h, int mx, int my) {
        kit.heading("المنظمات", x + w - 7, y + 13);
        ListTag list = d().getList("Factions", Tag.TAG_COMPOUND);
        float rowH = 20;
        listTop = y + 22;
        listBottom = y + h - 4;
        int rows = Math.max(1, (int) ((listBottom - listTop) / rowH));
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, list.size() - rows)));
        for (int i = listScroll; i < list.size() && i < listScroll + rows; i++) {
            CompoundTag f = list.getCompound(i);
            float ry = listTop + (i - listScroll) * rowH;
            UUID id = f.getUUID("Id");
            String status = f.getString("Status");
            boolean self = status.equals("self");
            boolean sel = id.equals(pickedFaction);
            boolean hot = !self && mx >= x + 4 && mx < x + w - 4 && my >= ry && my < ry + rowH - 2;
            pen.rect(x + 4, ry, w - 8, rowH - 2, sel ? 0x2AF0A93B : hot ? 0x14FFFFFF : 0x07FFFFFF);
            if (sel) {
                pen.rect(x + w - 5.5F, ry, 1.5F, rowH - 2, ScreenKit.AMBER);
            }
            pen.text(pen.kufi(f.getString("Name"), 5.6F, 700), x + w - 10, ry + 8, HudPen.RIGHT,
                    self ? ScreenKit.FAINT : ScreenKit.BONE);
            pen.text(pen.kufi("مستوى " + f.getInt("Level") + " · " + f.getInt("Members") + " أعضاء", 4.4F, 600), x + w - 10, ry + 15,
                    HudPen.RIGHT, ScreenKit.MUTED);
            String chip = switch (status) {
                case "self" -> "أنتم";
                case "ally" -> "حليف";
                case "war" -> "حرب";
                case "asked" -> "طلبتوا";
                case "asking" -> "يطلبون";
                default -> null;
            };
            if (chip != null) {
                int color = switch (status) {
                    case "ally" -> ScreenKit.SAGE;
                    case "war" -> BLOOD;
                    case "self" -> ScreenKit.FAINT;
                    default -> ScreenKit.AMBER;
                };
                Shaped s = pen.kufi(chip, 4.6F, 700);
                float cw = pen.width(s) + 8;
                pen.rect(x + 8, ry + 4, cw, 9, ScreenKit.alpha(color, 0.2F));
                pen.text(s, x + 8 + cw / 2, ry + 10.5F, HudPen.CENTER, color);
            }
            if (!self) {
                zone("f:" + id, x + 4, ry, w - 8, rowH - 2);
            }
        }
    }

    private void playerList(HudPen pen, float x, float y, float w, float h, int mx, int my) {
        kit.heading("المتصلين", x + w - 7, y + 13);
        ListTag list = d().getList("Players", Tag.TAG_COMPOUND);
        float rowH = 18;
        listTop = y + 22;
        listBottom = y + h - 4;
        int rows = Math.max(1, (int) ((listBottom - listTop) / rowH));
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, list.size() - rows)));
        for (int i = listScroll; i < list.size() && i < listScroll + rows; i++) {
            CompoundTag p = list.getCompound(i);
            float ry = listTop + (i - listScroll) * rowH;
            UUID id = p.getUUID("Id");
            boolean friend = p.getBoolean("Friend");
            boolean sel = id.equals(pickedPlayer);
            boolean hot = !friend && mx >= x + 4 && mx < x + w - 4 && my >= ry && my < ry + rowH - 2;
            pen.rect(x + 4, ry, w - 8, rowH - 2, sel ? 0x2AF0A93B : hot ? 0x14FFFFFF : 0x07FFFFFF);
            pen.text(pen.pixel(p.getString("Name"), 7.0F, 700), x + w - 10, ry + 10, HudPen.RIGHT,
                    friend ? ScreenKit.FAINT : ScreenKit.BONE);
            String fac = p.getString("Faction");
            pen.text(pen.kufi(friend ? "منكم" : fac.isEmpty() ? "بدون منظمة" : fac, 4.4F, 600), x + 9, ry + 10, HudPen.LEFT,
                    friend ? ScreenKit.SAGE : ScreenKit.MUTED);
            if (!friend) {
                zone("p:" + id, x + 4, ry, w - 8, rowH - 2);
            }
        }
    }

    private CompoundTag picked(String listKey, UUID id) {
        if (id == null) {
            return null;
        }
        ListTag list = d().getList(listKey, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            if (list.getCompound(i).getUUID("Id").equals(id)) {
                return list.getCompound(i);
            }
        }
        return null;
    }

    /* ================================================================== alliances */

    private void alliances(HudPen pen, float x, float y, float w, float h, int mx, int my) {
        boolean leader = d().getBoolean("Leader");
        int slots = d().getInt("AllySlots");
        long cooldown = d().getLong("AllyCooldown");
        float right = x + w;
        kit.heading("حلفاؤكم", right - 2, y + 10);
        pen.text(pen.pixel(count("ally") + " / " + slots, 7.0F, 700), x + 2, y + 10, HudPen.LEFT, ScreenKit.MUTED);
        float cy = y + 18;
        ListTag list = d().getList("Factions", Tag.TAG_COMPOUND);
        boolean any = false;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag f = list.getCompound(i);
            if (!f.getString("Status").equals("ally")) {
                continue;
            }
            any = true;
            pen.vgrad(x, cy, w, 26, 0xE0182016, 0xE00E120C);
            pen.rect(right - 2, cy, 2, 26, ScreenKit.SAGE);
            pen.text(pen.kufi(f.getString("Name"), 7.0F, 700), right - 8, cy + 12, HudPen.RIGHT, ScreenKit.BONE);
            pen.text(pen.kufi("مستوى " + f.getInt("Level") + " · " + f.getInt("Members") + " أعضاء · ما يضربونكم ويسمعون استغاثتكم",
                    4.6F, 600), right - 8, cy + 21, HudPen.RIGHT, ScreenKit.MUTED);
            if (leader) {
                String id = "break:" + f.getUUID("Id");
                kit.button(id, confirming(id) ? "متأكد؟" : "فك التحالف", x + 6, cy + 7, 52, 12, ScreenKit.DANGER, mx, my);
            }
            cy += 30;
        }
        if (!any) {
            pen.text(pen.kufi(slots == 0 ? "التحالفات تفتح من مستوى 3" : "ما عندكم حلفاء للحين · اختر منظمة من اليمين",
                    5.5F, 600), right - 4, cy + 10, HudPen.RIGHT, ScreenKit.FAINT);
            cy += 18;
        }
        if (cooldown > 0) {
            pen.text(pen.kufi("فكيتوا تحالف قريب · تقدرون تتحالفون من جديد بعد " + time(cooldown), 5.0F, 600), right - 4, cy + 6,
                    HudPen.RIGHT, ScreenKit.RUST);
            cy += 14;
        }

        // requests waiting on you
        cy += 8;
        kit.heading("طلبات تحالف واصلة", right - 2, cy + 6);
        cy += 14;
        boolean asked = false;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag f = list.getCompound(i);
            if (!f.getString("Status").equals("asking")) {
                continue;
            }
            asked = true;
            pen.rect(x, cy, w, 20, 0xC0161A13);
            pen.text(pen.kufi(f.getString("Name") + " تبي تتحالف معكم", 6.0F, 700), right - 8, cy + 13, HudPen.RIGHT, ScreenKit.AMBER);
            if (leader) {
                kit.button("accept:" + f.getUUID("Id"), "قبول", x + 6, cy + 4, 36, 12, ScreenKit.FILLED, mx, my);
                kit.button("reject:" + f.getUUID("Id"), "رفض", x + 46, cy + 4, 36, 12, ScreenKit.GHOST, mx, my);
            }
            cy += 24;
        }
        if (!asked) {
            pen.text(pen.kufi("ما فيه طلبات", 5.0F, 600), right - 4, cy + 6, HudPen.RIGHT, ScreenKit.FAINT);
            cy += 12;
        }

        rules(pen, x, y + h - 74, w, new String[]{
                "التحالف من مستوى 3 (حليف واحد) · من مستوى 7 (حليفين)",
                "الحلفاء ما يقدرون يضربون بعض، ويوصلهم نداء الاستغاثة من بعض",
                "أي قائد يقدر يفك التحالف، وبعدها الطرفين ينتظرون يوم قبل أي تحالف جديد"});
        actionCard(pen, x, y + h - 30, w, mx, my, false);
    }

    /* ================================================================== wars */

    private void wars(HudPen pen, float x, float y, float w, float h, int mx, int my) {
        float right = x + w;
        kit.heading("الحروب", right - 2, y + 10);
        ListTag wars = d().getList("Wars", Tag.TAG_COMPOUND);
        float cy = y + 18;
        if (wars.isEmpty()) {
            pen.text(pen.kufi("ما فيه حروب · الهدوء قبل العاصفة", 6.0F, 600), right - 4, cy + 10, HudPen.RIGHT, ScreenKit.FAINT);
            cy += 20;
        }
        for (int i = 0; i < wars.size(); i++) {
            CompoundTag w0 = wars.getCompound(i);
            float ch = 48;
            pen.vgrad(x, cy, w, ch, 0xE0221310, 0xE00F0B0A);
            pen.rect(right - 2, cy, 2, ch, BLOOD);
            pen.text(pen.kufi("ضد " + w0.getString("Enemy"), 8.0F, 700), right - 8, cy + 13, HudPen.RIGHT, ScreenKit.BONE);
            pen.text(pen.kufi(w0.getBoolean("Declared") ? "أنتم أعلنتوها" : "أعلنوها عليكم", 4.8F, 600), right - 8, cy + 22,
                    HudPen.RIGHT, BLOOD);
            // the score in the middle
            float cx = x + w * 0.42F;
            pen.text(pen.pixel(String.valueOf(w0.getInt("MyKills")), 16.0F, 700), cx + 18, cy + 22, HudPen.CENTER, ScreenKit.SAGE);
            pen.text(pen.pixel(":", 14.0F, 700), cx, cy + 21, HudPen.CENTER, ScreenKit.FAINT);
            pen.text(pen.pixel(String.valueOf(w0.getInt("TheirKills")), 16.0F, 700), cx - 18, cy + 22, HudPen.CENTER, BLOOD);
            pen.text(pen.kufi("قتلاتكم", 4.2F, 600), cx + 18, cy + 30, HudPen.CENTER, ScreenKit.MUTED);
            pen.text(pen.kufi("قتلاتهم", 4.2F, 600), cx - 18, cy + 30, HudPen.CENTER, ScreenKit.MUTED);
            // money
            pen.text(pen.kufi("أخذتوا", 4.6F, 600), x + 8, cy + 11, HudPen.LEFT, ScreenKit.MUTED);
            pen.text(pen.pixel("+" + money(w0.getInt("Won")), 7.0F, 700), x + 34, cy + 11.5F, HudPen.LEFT, CASH);
            pen.text(pen.kufi("خسرتوا", 4.6F, 600), x + 8, cy + 21, HudPen.LEFT, ScreenKit.MUTED);
            pen.text(pen.pixel("-" + money(w0.getInt("Lost")), 7.0F, 700), x + 34, cy + 21.5F, HudPen.LEFT, BLOOD);
            // time left
            long left = w0.getLong("Left");
            long total = Math.max(1, w0.getLong("Total"));
            pen.rect(x + 8, cy + ch - 8, w - 16, 2, 0x22FFFFFF);
            pen.rect(x + 8, cy + ch - 8, (w - 16) * left / total, 2, BLOOD);
            pen.text(pen.kufi("باقي " + time(left), 4.6F, 600), right - 8, cy + ch - 11, HudPen.RIGHT, ScreenKit.MUTED);
            cy += ch + 6;
        }
        rules(pen, x, y + h - 74, w, new String[]{
                "إعلان الحرب للقائد من مستوى 4، ومدتها 48 ساعة ويسمع فيها السيرفر كله",
                "كل قتلة على عضو من الخصم تاخذ 1% من بنكهم لبنككم · وأقصاه 10% طول الحرب",
                "حرب وحدة تعلنونها في نفس الوقت · وما تتحاربون مع نفس المنظمة قبل أسبوع"});
        actionCard(pen, x, y + h - 30, w, mx, my, true);
    }

    /** The picked faction and what can be done with it: ask for an alliance, or declare war. */
    private void actionCard(HudPen pen, float x, float y, float w, int mx, int my, boolean war) {
        CompoundTag f = picked("Factions", pickedFaction);
        pen.rect(x, y, w, 26, 0xD00B0D0A);
        pen.rect(x, y, w, 0.5F, war ? BLOOD : ScreenKit.AMBER_DIM);
        if (f == null) {
            pen.text(pen.kufi("اختر منظمة من القائمة", 6.0F, 600), x + w - 8, y + 16, HudPen.RIGHT, ScreenKit.FAINT);
            return;
        }
        pen.text(pen.kufi(f.getString("Name"), 7.5F, 700), x + w - 8, y + 16, HudPen.RIGHT, ScreenKit.BONE);
        boolean leader = d().getBoolean("Leader");
        String status = f.getString("Status");
        String block = null;
        if (!leader) {
            block = "للقائد بس";
        } else if (war) {
            long cd = f.getLong("WarCooldown");
            if (d().getInt("Level") < 4) {
                block = "تحتاجون مستوى 4";
            } else if (status.equals("ally")) {
                block = "حليفكم";
            } else if (status.equals("war")) {
                block = "في حرب معهم";
            } else if (cd > 0) {
                block = "بعد " + time(cd);
            }
        } else {
            if (status.equals("ally")) {
                block = "حليفكم";
            } else if (status.equals("asked")) {
                block = "طلبكم مرسل";
            } else if (status.equals("war")) {
                block = "في حرب معهم";
            } else if (d().getInt("AllySlots") == 0) {
                block = "تحتاجون مستوى 3";
            }
        }
        float bw = 92;
        if (block != null) {
            pen.rect(x + 6, y + 6, bw, 14, 0x0CFFFFFF);
            pen.text(pen.kufi(block, 5.4F, 700), x + 6 + bw / 2, y + 15.5F, HudPen.CENTER, ScreenKit.FAINT);
        } else if (war) {
            String id = "war:" + pickedFaction;
            kit.button(id, confirming(id) ? "متأكد؟ اضغط مرة ثانية" : "أعلن الحرب", x + 6, y + 6, bw, 14, ScreenKit.DANGER, mx, my);
        } else {
            kit.button("ally:" + pickedFaction, status.equals("asking") ? "اقبل التحالف" : "اطلب تحالف", x + 6, y + 6, bw, 14,
                    ScreenKit.FILLED, mx, my);
        }
    }

    private void rules(HudPen pen, float x, float y, float w, String[] lines) {
        pen.rect(x, y, w, 40, 0x80000000);
        for (int i = 0; i < lines.length; i++) {
            pen.rect(x + w - 6, y + 7 + i * 11, 1.5F, 1.5F, ScreenKit.AMBER_DIM);
            pen.text(pen.kufi(lines[i], 4.6F, 600), x + w - 10, y + 10 + i * 11, HudPen.RIGHT, ScreenKit.MUTED);
        }
    }

    /* ================================================================== bounties */

    private void bounties(HudPen pen, float x, float y, float w, float h, int mx, int my) {
        float right = x + w;
        kit.heading("لوحة المطلوبين", right - 2, y + 10);
        ListTag list = d().getList("Bounties", Tag.TAG_COMPOUND);
        float cw = 104;
        float ch = 64;
        int perRow = Math.max(1, (int) ((w + 6) / (cw + 6)));
        float cy = y + 18;
        if (list.isEmpty()) {
            pen.text(pen.kufi("ما أحد مطلوب حالياً", 6.0F, 600), right - 4, cy + 10, HudPen.RIGHT, ScreenKit.FAINT);
        }
        for (int i = 0; i < list.size() && cy + ch < y + h - 50; i++) {
            CompoundTag b = list.getCompound(i);
            int col = i % perRow;
            float bx = right - (col + 1) * cw - col * 6;
            float by = cy + (i / perRow) * (ch + 6);
            if (by + ch > y + h - 50) {
                break;
            }
            boolean onMe = b.getBoolean("OnMe");
            // a wanted poster: worn paper, the name, the reward
            pen.vgrad(bx, by, cw, ch, 0xFF3A3220, 0xFF2A2417);
            outline(pen, bx, by, cw, ch, onMe ? BLOOD : 0xFF5C4C2C);
            pen.text(pen.pixel("WANTED", 9.0F, 700), bx + cw / 2, by + 12, HudPen.CENTER, onMe ? BLOOD : 0xFFD8C08A);
            pen.rect(bx + 8, by + 15, cw - 16, 0.5F, 0x60D8C08A);
            pen.text(pen.pixel(b.getString("Target"), 8.0F, 700), bx + cw / 2, by + 27, HudPen.CENTER, ScreenKit.BONE);
            pen.text(pen.pixel(money(b.getInt("Amount")), 11.0F, 700), bx + cw / 2, by + 42, HudPen.CENTER, CASH);
            pen.text(pen.kufi("من " + b.getString("By") + " · باقي " + time(b.getLong("Left")), 4.2F, 600), bx + cw / 2, by + 52,
                    HudPen.CENTER, 0xFFB8A57A);
            if (onMe) {
                pen.text(pen.kufi("عليك!", 5.0F, 700), bx + cw / 2, by + 60, HudPen.CENTER, BLOOD);
            }
        }

        // putting one up
        float fy = y + h - 46;
        pen.rect(x, fy, w, 46, 0xD00B0D0A);
        pen.rect(x, fy, w, 0.5F, ScreenKit.AMBER_DIM);
        boolean spend = d().getBoolean("Spend");
        int max = d().getInt("BountyMax");
        CompoundTag target = picked("Players", pickedPlayer);
        pen.text(pen.kufi("حط مكافأة", 6.5F, 700), right - 8, fy + 12, HudPen.RIGHT, ScreenKit.BONE);
        pen.text(pen.kufi(target == null ? "اختر لاعب من اليمين" : "على: " + target.getString("Name"), 5.4F, 700), right - 8, fy + 24,
                HudPen.RIGHT, target == null ? ScreenKit.FAINT : ScreenKit.AMBER);
        pen.text(pen.kufi("من بنك المنظمة · من 500 لين " + money(max) + " · وحدة في نفس الوقت · ترجع لكم بعد 7 أيام إذا ما أحد قبضها",
                4.3F, 600), right - 8, fy + 38, HudPen.RIGHT, ScreenKit.FAINT);
        // the amount field
        float fx = x + 8;
        float fw = 70;
        pen.rect(fx, fy + 8, fw, 13, 0xA0000000);
        outline(pen, fx, fy + 8, fw, 13, typing ? ScreenKit.AMBER : ScreenKit.LINE);
        Shaped as = pen.pixel(amount.isEmpty() ? "" : "$" + amount, 7.5F, 700);
        pen.text(as, fx + 4, fy + 17.5F, HudPen.LEFT, ScreenKit.BONE);
        if (amount.isEmpty()) {
            pen.text(pen.kufi("المبلغ", 4.8F, 600), fx + fw - 4, fy + 17, HudPen.RIGHT, ScreenKit.FAINT);
        }
        if (typing && System.currentTimeMillis() / 500 % 2 == 0) {
            pen.rect(fx + 5 + pen.width(as), fy + 10.5F, 0.75F, 8, ScreenKit.AMBER);
        }
        zone("amount", fx, fy + 8, fw, 13);
        if (spend && target != null) {
            kit.button("bounty", confirming("bounty") ? "متأكد؟" : "حط المكافأة", fx + fw + 6, fy + 8, 60, 13, ScreenKit.FILLED, mx, my);
        } else if (!spend) {
            pen.text(pen.kufi("للقائد والنائب", 5.0F, 700), fx + fw + 6, fy + 17, HudPen.LEFT, ScreenKit.FAINT);
        }
    }

    private static void outline(HudPen pen, float x, float y, float w, float h, int argb) {
        pen.rect(x, y, w, 0.5F, argb);
        pen.rect(x, y + h - 0.5F, w, 0.5F, argb);
        pen.rect(x, y, 0.5F, h, argb);
        pen.rect(x + w - 0.5F, y, 0.5F, h, argb);
    }

    /* ================================================================== input */

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

    private static CompoundTag withId(UUID id) {
        CompoundTag t = new CompoundTag();
        t.putUUID("Id", id);
        return t;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return true;
        }
        String id = kit.hit(mx, my);
        if (id == null) {
            id = zoneAt(mx, my);
        }
        typing = "amount".equals(id);
        if (id == null) {
            return true;
        }
        ScreenKit.click();
        if (id.startsWith("tab:")) {
            tab = Integer.parseInt(id.substring(4));
            listScroll = 0;
            confirm = null;
        } else if (id.startsWith("f:")) {
            pickedFaction = UUID.fromString(id.substring(2));
            confirm = null;
        } else if (id.startsWith("p:")) {
            pickedPlayer = UUID.fromString(id.substring(2));
            confirm = null;
        } else if (id.startsWith("ally:")) {
            UUID f = UUID.fromString(id.substring(5));
            CompoundTag picked = picked("Factions", f);
            boolean asking = picked != null && picked.getString("Status").equals("asking");
            ClientDiplomacy.send(asking ? "ally.accept" : "ally.request", withId(f));
        } else if (id.startsWith("accept:")) {
            ClientDiplomacy.send("ally.accept", withId(UUID.fromString(id.substring(7))));
        } else if (id.startsWith("reject:")) {
            ClientDiplomacy.send("ally.reject", withId(UUID.fromString(id.substring(7))));
        } else if (id.startsWith("break:")) {
            if (confirmed(id)) {
                ClientDiplomacy.send("ally.break", withId(UUID.fromString(id.substring(6))));
            }
        } else if (id.startsWith("war:")) {
            if (confirmed(id)) {
                ClientDiplomacy.send("war.declare", withId(UUID.fromString(id.substring(4))));
                UiSounds.staticBurst(false);
            }
        } else if (id.equals("bounty")) {
            if (confirmed(id) && pickedPlayer != null) {
                CompoundTag t = new CompoundTag();
                t.putUUID("Target", pickedPlayer);
                try {
                    t.putInt("Amount", amount.isEmpty() ? 0 : Integer.parseInt(amount));
                } catch (NumberFormatException e) {
                    t.putInt("Amount", 0);
                }
                ClientDiplomacy.send("bounty.place", t);
                amount = "";
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= listX && my >= listTop && my <= listBottom) {
            listScroll -= (int) Math.signum(delta);
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (typing && Character.isDigit(c) && amount.length() < 9) {
            amount += c;
            return true;
        }
        return typing;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (typing) {
            if (key == 259 && !amount.isEmpty()) {
                amount = amount.substring(0, amount.length() - 1);
                return true;
            }
            if (key == 256) {
                typing = false;
                return true;
            }
        }
        if (key == 258) {
            tab = (tab + 1) % TABS.length;
            listScroll = 0;
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}

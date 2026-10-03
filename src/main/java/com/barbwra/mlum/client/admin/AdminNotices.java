package com.barbwra.mlum.client.admin;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.client.ui.mc.UiText;
import com.barbwra.mlum.client.ui.text.Shaped;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the admin system shows on everyone's screen: the notice a punished player gets, and the
 * restart countdown.
 *
 * <pre>
 *            ┌──────────────── إنذار ────────────────┐
 *            │   السبب: سب في الشات                   │      shown for ten seconds,
 *            │   المدة: 30 دقيقة · من BarBwra          │      fading at the end
 *            └────────────────────────────────────────┘
 *
 *                 ريستارت السيرفر بعد  4:59                    until it happens
 * </pre>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class AdminNotices {

    private AdminNotices() {
    }

    private static final HudPen PEN = new HudPen();
    private static final long NOTICE_MS = 10_000L;

    private static final int BONE = 0xFFECE6D4;
    private static final int MUTED = 0xFFA19E8B;
    private static final int RUST = 0xFFE0613F;
    private static final int AMBER = 0xFFF0A93B;

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        long now = Anim.now();
        boolean notice = ClientAdmin.notice != null && now - ClientAdmin.noticeAt < NOTICE_MS;
        int restart = restartLeft(now);
        if (!notice && restart < 0) {
            return;
        }
        int w = event.getWindow().getGuiScaledWidth();
        HudPen pen = PEN;
        pen.begin(event.getGuiGraphics());
        try {
            if (notice) {
                drawNotice(pen, w, now);
            }
            if (restart >= 0) {
                drawRestart(pen, w, restart, now);
            }
        } finally {
            pen.end();
        }
    }

    private static int restartLeft(long now) {
        if (ClientAdmin.restartSeconds < 0) {
            return -1;
        }
        int left = ClientAdmin.restartSeconds - (int) ((now - ClientAdmin.restartAt) / 1000L);
        return left < 0 ? -1 : left;
    }

    private static int alpha(int argb, float a) {
        return (Mth.clamp(Math.round((argb >>> 24) * a), 0, 255) << 24) | (argb & 0xFFFFFF);
    }

    private static void drawNotice(HudPen pen, int width, long now) {
        CompoundTag n = ClientAdmin.notice;
        float age = (now - ClientAdmin.noticeAt) / (float) NOTICE_MS;
        float a = age > 0.85F ? (1.0F - age) / 0.15F : Math.min(1.0F, age * 12.0F);
        String type = n.getString("Type");
        String title = switch (type) {
            case "warn" -> "إنذار من الإدارة";
            case "mute" -> "أنت مكتوم";
            case "jail" -> "أنت مسجون";
            default -> "عقوبة";
        };
        int accent = type.equals("warn") ? AMBER : RUST;
        float w = 190.0F;
        float x = width / 2.0F - w / 2.0F;
        float y = 40.0F;
        float h = 46.0F;
        pen.rect(x, y, w, h, alpha(0xE6101310, a));
        pen.rect(x, y, w, 1.5F, alpha(accent, a));
        pen.rect(x, y + h - 0.5F, w, 0.5F, alpha(0x40FFFFFF, a));
        Shaped t = pen.kufi(title, 8.0F, 800);
        pen.text(t, width / 2.0F, y + 13.0F, HudPen.CENTER, alpha(accent, a));
        String reason = UiText.logical(n.getString("Reason"));
        pen.text(pen.kufi("السبب: " + reason, 5.5F, 600), width / 2.0F, y + 25.0F, HudPen.CENTER, alpha(BONE, a));
        long until = n.getLong("Until");
        String length = until <= 0L ? (type.equals("warn") ? "" : "بدون مدة") : "باقي " + duration(until - System.currentTimeMillis());
        String by = n.getString("By");
        String line = (length.isEmpty() ? "" : length + " · ") + "من " + by;
        pen.text(pen.kufi(line, 5.0F, 600), width / 2.0F, y + 36.0F, HudPen.CENTER, alpha(MUTED, a));
    }

    private static void drawRestart(HudPen pen, int width, int left, long now) {
        boolean urgent = left <= 60;
        float pulse = urgent && Anim.enabled() ? 0.65F + 0.35F * Math.abs(Mth.sin(now / 300.0F)) : 1.0F;
        String clock = (left / 60) + ":" + String.format("%02d", left % 60);
        Shaped label = pen.kufi("ريستارت السيرفر بعد", 6.0F, 700);
        Shaped time = pen.pixel(clock, 10.0F, 700);
        float total = pen.width(label) + 6.0F + pen.width(time);
        float x = width / 2.0F - total / 2.0F - 8.0F;
        float y = 30.0F;
        pen.rect(x, y - 9.0F, total + 16.0F, 13.0F, 0xD90E110C);
        pen.rect(x, y + 3.5F, total + 16.0F, 0.5F, alpha(urgent ? RUST : AMBER, pulse));
        pen.text(time, x + 8.0F, y + 1.5F, HudPen.LEFT, alpha(urgent ? RUST : AMBER, pulse));
        pen.text(label, x + 8.0F + pen.width(time) + 6.0F, y + 1.0F, HudPen.LEFT, BONE);
    }

    static String duration(long ms) {
        long minutes = Math.max(0L, (ms + 59_999L) / 60_000L);
        if (minutes < 60L) {
            return minutes + " دقيقة";
        }
        long hours = minutes / 60L;
        if (hours < 48L) {
            return hours + " ساعة";
        }
        return (hours / 24L) + " يوم";
    }
}

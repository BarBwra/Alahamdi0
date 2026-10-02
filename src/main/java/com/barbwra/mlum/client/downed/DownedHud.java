package com.barbwra.mlum.client.downed;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.client.hud.HudVisibility;
import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.client.loot.WorldProjector;
import com.barbwra.mlum.client.ui.text.Shaped;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * What the downed system draws: the downed player's own screen, the options over a body, and the
 * markers of faction members calling for help.
 *
 * <pre>
 *      the downed player                    someone walking up
 *
 *          . . o o o o .                      [F]
 *        .               o              ┌──────┐┌──────┐┌──────┐
 *        o     أنت مصاب    o              │ تلويت ││ إنعاش ││ سحب  │
 *        o  انتظر أحد يقومك o              └──────┘└━━━━━━┘└──────┘
 *          o o o o o o o                   بدّل بعجلة الماوس
 *    اضغط F للاستغاثة · امسك F للاستسلام
 * </pre>
 *
 * <p>The ring is sixty dots and loses one every three seconds of a three minute clock; there are no
 * numbers on it, on purpose. In the last sixth it turns red.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class DownedHud {

    private DownedHud() {
    }

    private static final int BONE = 0xFFECE6D4;
    private static final int MUTED = 0xFFA19E8B;
    private static final int FAINT = 0xFF6B6A5C;
    private static final int RUST = 0xFFD9623F;
    private static final int SAGE = 0xFF93C46F;

    private static final HudPen SELF = new HudPen();
    private static final HudPen PROMPT = new HudPen();

    private static int alpha(int argb, float a) {
        int na = Mth.clamp(Math.round((argb >>> 24) * a), 0, 255);
        return (na << 24) | (argb & 0xFFFFFF);
    }

    /* ================================================================== your own screen */

    public static void renderSelf(GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !ClientDowned.selfDowned() || mc.options.hideGui) {
            return;
        }
        long now = Anim.now();
        float left = ClientDowned.left(player);
        float pulse = Anim.enabled() ? 0.7F + 0.3F * Math.abs(Mth.sin(now / 420.0F)) : 0.85F;
        HudPen pen = SELF;
        pen.begin(graphics);
        try {
            // the edges of the view darken red, in steps - a vignette built from rectangles
            float band = Math.max(width, height) * 0.012F;
            for (int i = 0; i < 12; i++) {
                float inset = i * band;
                int c = alpha(0xFF5A0A04, 0.085F * (1.0F - i / 12.0F) * pulse);
                pen.rect(inset, inset, width - inset * 2, band, c);
                pen.rect(inset, height - inset - band, width - inset * 2, band, c);
                pen.rect(inset, inset + band, band, height - inset * 2 - band * 2, c);
                pen.rect(width - inset - band, inset + band, band, height - inset * 2 - band * 2, c);
            }

            float cx = width / 2.0F;
            float cy = height / 2.0F - 12.0F;
            int lit = (int) Math.ceil(left * 60.0F);
            boolean late = left < 1.0F / 6.0F;
            for (int i = 0; i < 60; i++) {
                double ang = i / 60.0D * Math.PI * 2.0D - Math.PI / 2.0D;
                float x = cx + (float) Math.cos(ang) * 24.0F;
                float y = cy + (float) Math.sin(ang) * 24.0F;
                int c = i < lit ? (late ? alpha(RUST, pulse) : BONE) : 0x1FFFFFFF;
                pen.rect(x - 0.75F, y - 0.75F, 1.5F, 1.5F, c);
            }

            ClientDowned.Info info = ClientDowned.info(player.getId());
            float revive = info == null ? 0.0F : info.revive();
            boolean helped = info != null && revive > 0.0F && !info.reviver().isEmpty();
            if (helped) {
                int done = Math.round(revive * 40.0F);
                for (int i = 0; i < 40; i++) {
                    double ang = i / 40.0D * Math.PI * 2.0D - Math.PI / 2.0D;
                    float x = cx + (float) Math.cos(ang) * 17.0F;
                    float y = cy + (float) Math.sin(ang) * 17.0F;
                    pen.rect(x - 0.5F, y - 0.5F, 1.0F, 1.0F, i < done ? SAGE : 0x26FFFFFF);
                }
            }

            Shaped title = pen.kufi(helped ? info.reviver() + " يقومك" : "أنت مصاب", 9.0F, 700);
            pen.shadowed(title, cx, cy + 40.0F, HudPen.CENTER, helped ? SAGE : BONE);
            String sub = info != null && info.dragged() ? "أحد يسحبك" : helped ? "لا تتحرك" : "انتظر أحد يقومك";
            pen.text(pen.kufi(sub, 5.5F, 600), cx, cy + 50.0F, HudPen.CENTER, MUTED);

            Shaped hint = pen.kufi("اضغط F للاستغاثة · امسك F للاستسلام", 5.0F, 600);
            pen.text(hint, cx, height - 28.0F, HudPen.CENTER, FAINT);
            int hold = DownedClientEvents.holdTicks();
            int need = Math.max(1, MlumConfig.giveUpSeconds() * 20);
            if (hold > 5) {
                float w = 60.0F;
                pen.rect(cx - w / 2, height - 24.0F, w, 1.0F, 0x33FFFFFF);
                pen.rect(cx - w / 2, height - 24.0F, w * Math.min(1.0F, hold / (float) need), 1.0F, RUST);
            }
        } finally {
            pen.end();
        }
    }

    /* ================================================================== the options over a body */

    public static void renderPrompt(GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Entity body = DownedClientEvents.target();
        if (body == null || mc.player == null || mc.screen != null || HudVisibility.hidden()) {
            renderDistress(graphics, width, height);
            return;
        }
        Vec3 at = body.getPosition(partialTick).add(0.0D, 0.95D, 0.0D);
        float[] p = new float[2];
        if (!WorldProjector.project(at.x, at.y, at.z, width, height, p)) {
            renderDistress(graphics, width, height);
            return;
        }
        int accent = MlumConfig.fieldHudAccent();
        boolean defib = DownedClientEvents.holds(mc.player.getMainHandItem(), MlumConfig.defibItem());
        boolean oxygen = DownedClientEvents.holds(mc.player.getMainHandItem(), MlumConfig.oxygenItem());
        String[] labels = {"تلويت", defib ? "صعق" : oxygen ? "أكسجين" : "إنعاش", "سحب"};
        int sel = DownedClientEvents.selected();
        float revive = ClientDowned.revive(body);

        HudPen pen = PROMPT;
        pen.begin(graphics);
        try {
            Shaped[] shaped = new Shaped[3];
            float[] widths = new float[3];
            float total = 0.0F;
            for (int i = 0; i < 3; i++) {
                shaped[i] = pen.kufi(labels[i], 5.5F, 600);
                widths[i] = Math.max(26.0F, pen.width(shaped[i]) + 10.0F);
                total += widths[i];
            }
            total += 4.0F;
            float x = p[0] - total / 2.0F;
            float y = p[1] - 6.0F;
            float h = 11.0F;
            for (int i = 0; i < 3; i++) {
                float w = widths[i];
                boolean on = i == sel;
                pen.rect(x + 1, y, w - 2, h, on ? 0xE61A1F17 : 0xCC0F120D);
                pen.rect(x, y + 1, 1, h - 2, on ? 0xE61A1F17 : 0xCC0F120D);
                pen.rect(x + w - 1, y + 1, 1, h - 2, on ? 0xE61A1F17 : 0xCC0F120D);
                int edge = on ? accent : 0x40FFFFFF;
                pen.rect(x + 1, y, w - 2, 0.5F, edge);
                pen.rect(x + 1, y + h - 0.5F, w - 2, 0.5F, edge);
                pen.text(shaped[i], x + w / 2.0F, y + 7.8F, HudPen.CENTER, on ? BONE : MUTED);
                if (on) {
                    Shaped key = pen.pixel("F", 5.0F, 700);
                    float kw = pen.width(key) + 3.0F;
                    pen.rect(x + w / 2.0F - kw / 2.0F, y - 6.5F, kw, 5.5F, accent);
                    pen.text(key, x + w / 2.0F, y - 2.2F, HudPen.CENTER, 0xFF0B0E0A);
                }
                if (i == DownedClientEvents.REVIVE && revive > 0.0F) {
                    pen.rect(x + 1, y + h, (w - 2) * Mth.clamp(revive, 0.0F, 1.0F), 1.0F, SAGE);
                }
                x += w + 2.0F;
            }
            String hint = sel == DownedClientEvents.REVIVE && !defib
                    ? "امسك F · " + (oxygen ? MlumConfig.oxygenSeconds() : MlumConfig.reviveSeconds()) + " ثواني"
                    : "بدّل بعجلة الماوس";
            pen.text(pen.kufi(hint, 4.5F, 600), p[0], y + h + 8.0F, HudPen.CENTER, FAINT);
            distressMarks(pen, width, height);
        } finally {
            pen.end();
        }
    }

    /* ================================================================== faction members calling */

    private static void renderDistress(GuiGraphics graphics, int width, int height) {
        if (ClientDistress.live().isEmpty() || HudVisibility.hidden()) {
            return;
        }
        HudPen pen = PROMPT;
        pen.begin(graphics);
        try {
            distressMarks(pen, width, height);
        } finally {
            pen.end();
        }
    }

    private static void distressMarks(HudPen pen, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        float[] p = new float[2];
        float pulse = Anim.enabled() ? 0.6F + 0.4F * Math.abs(Mth.sin(Anim.now() / 260.0F)) : 1.0F;
        for (ClientDistress.Call call : ClientDistress.live()) {
            Vec3 at = call.pos().add(0.0D, 1.2D, 0.0D);
            boolean onScreen = WorldProjector.project(at.x, at.y, at.z, width, height, p)
                    && p[0] > 8 && p[0] < width - 8 && p[1] > 8 && p[1] < height - 8;
            if (!onScreen) {
                // off screen: pinned to the top edge, as far left or right as the call lies
                Vec3 to = call.pos().subtract(mc.player.position());
                float yaw = (float) (Mth.atan2(to.z, to.x) * (180.0D / Math.PI)) - 90.0F;
                float rel = Mth.wrapDegrees(yaw - mc.player.getYRot());
                p[0] = width / 2.0F + Mth.clamp(rel / 90.0F, -1.0F, 1.0F) * (width / 2.0F - 20.0F);
                p[1] = 34.0F;
            }
            int c = alpha(RUST, pulse);
            pen.rect(p[0] - 1.5F, p[1] - 3.5F, 3.0F, 7.0F, c);
            pen.rect(p[0] - 3.5F, p[1] - 1.5F, 7.0F, 3.0F, c);
            pen.rect(p[0] - 0.5F, p[1] - 0.5F, 1.0F, 1.0F, 0xFFFFFFFF);
            int dist = (int) Math.round(Math.sqrt(mc.player.distanceToSqr(call.pos())));
            pen.shadowed(pen.pixel(call.name() + " · " + dist + "m", 5.0F, 700), p[0], p[1] + 10.0F, HudPen.CENTER, BONE);
        }
    }
}

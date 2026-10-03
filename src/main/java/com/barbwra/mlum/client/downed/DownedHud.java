package com.barbwra.mlum.client.downed;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.client.hud.HudVisibility;
import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.client.hud.field.SearchSpinner;
import com.barbwra.mlum.client.hud.field.Shapes;
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
 *           ╭──────╮                          [F]
 *         ╱   5:42   ╲                  ┌────────┐┌────────┐
 *        │            │                 │  تلويت  ││  إنعاش  │
 *         ╲          ╱                  └────────┘└━━━━━━━━┘
 *           ╰──────╯                       بدّل بعجلة الماوس
 *           أنت مصاب
 *    E للاستغاثة · امسك F للاستسلام
 * </pre>
 *
 * <p>The ring is the time left, running down anticlockwise, with the minutes and seconds inside.
 * It turns red in the last sixth. Holding F to give up paints it red from the top as the hold
 * fills, so letting go in time is a visible choice. While someone revives you, a green ring fills
 * inside it.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class DownedHud {

    private DownedHud() {
    }

    private static final int BONE = 0xFFECE6D4;
    private static final int MUTED = 0xFFA19E8B;
    private static final int FAINT = 0xFF6B6A5C;
    private static final int RUST = 0xFFD9623F;
    private static final int RED = 0xFFE5442E;
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
            float cy = height / 2.0F - 14.0F;
            float radius = 27.0F;
            float thick = 2.5F;
            boolean late = left < 1.0F / 6.0F;
            int hold = DownedClientEvents.holdTicks();
            float giveUp = Mth.clamp(hold / (float) Math.max(1, MlumConfig.giveUpSeconds() * 20), 0.0F, 1.0F);

            // a dark disc behind it all, so the ring reads against a bright sky
            Shapes.disc(pen, cx, cy, radius + 7.0F, 0x59000000);
            Shapes.ring(pen, cx, cy, radius, thick, 0x24FFFFFF);
            int arc = late ? alpha(RUST, pulse) : BONE;
            if (giveUp > 0.0F) {
                // giving up: the white turns red from the top as the hold fills
                Shapes.arc(pen, cx, cy, radius, thick, 0.0F, left * giveUp, RED);
                Shapes.arc(pen, cx, cy, radius, thick, left * giveUp, left, arc);
            } else {
                Shapes.arc(pen, cx, cy, radius, thick, 0.0F, left, arc);
            }

            ClientDowned.Info info = ClientDowned.info(player.getId());
            float revive = info == null ? 0.0F : info.revive();
            boolean helped = info != null && revive > 0.0F && !info.reviver().isEmpty();
            if (helped) {
                Shapes.ring(pen, cx, cy, radius - 5.0F, 1.5F, 0x1FFFFFFF);
                Shapes.arc(pen, cx, cy, radius - 5.0F, 1.5F, 0.0F, revive, SAGE);
            }

            int seconds = info == null ? 0 : Math.max(0, (int) Math.ceil(left * info.total() / 20.0F));
            String clock = (seconds / 60) + ":" + String.format("%02d", seconds % 60);
            Shaped time = pen.pixel(clock, 14.0F, 700);
            int timeColour = giveUp > 0.0F ? RED : late ? alpha(RUST, pulse) : BONE;
            pen.glow(time, cx, cy + 5.0F, HudPen.CENTER, 2.0F, alpha(timeColour, 0.35F));
            pen.text(time, cx, cy + 5.0F, HudPen.CENTER, timeColour);

            float titleY = cy + radius + 16.0F;
            if (helped) {
                pen.shadowed(pen.kufi(info.reviver() + " يقومك", 9.0F, 700), cx, titleY, HudPen.CENTER, SAGE);
            } else {
                // "أنت مصاب" with the second word in red; Arabic runs right to left, so it sits left
                Shaped you = pen.kufi("أنت", 9.0F, 700);
                Shaped hurt = pen.kufi("مصاب", 9.0F, 700);
                float gap = 3.0F;
                float total = pen.width(you) + gap + pen.width(hurt);
                float x0 = cx - total / 2.0F;
                pen.shadowed(hurt, x0, titleY, HudPen.LEFT, RED);
                pen.shadowed(you, x0 + pen.width(hurt) + gap, titleY, HudPen.LEFT, BONE);
            }
            String sub = helped ? "لا تتحرك" : "انتظر أحد يقومك";
            pen.text(pen.kufi(sub, 5.5F, 600), cx, titleY + 10.0F, HudPen.CENTER, MUTED);

            Shaped hint = pen.kufi("E للاستغاثة · امسك F للاستسلام", 5.0F, 600);
            pen.text(hint, cx, height - 28.0F, HudPen.CENTER, giveUp > 0.0F ? RED : FAINT);
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
        int accent = MlumConfig.fieldHudAccent();
        if (DownedClientEvents.reviving()) {
            // hands on the body: the same spinner as a search, with how far the revive has got
            HudPen pen = PROMPT;
            pen.begin(graphics);
            try {
                SearchSpinner.draw(pen, width / 2.0F, height / 2.0F + height * 0.075F, height,
                        DownedClientEvents.revivingSince(), 800.0F, false, ClientDowned.revive(body), accent);
                distressMarks(pen, width, height);
            } finally {
                pen.end();
            }
            return;
        }
        Vec3 at = body.getPosition(partialTick).add(0.0D, 0.95D, 0.0D);
        float[] p = new float[2];
        if (!WorldProjector.project(at.x, at.y, at.z, width, height, p)) {
            renderDistress(graphics, width, height);
            return;
        }
        boolean defib = DownedClientEvents.holds(mc.player.getMainHandItem(), MlumConfig.defibItem());
        boolean oxygen = DownedClientEvents.holds(mc.player.getMainHandItem(), MlumConfig.oxygenItem());
        String[] labels = {"تلويت", defib ? "صعق" : oxygen ? "أكسجين" : "إنعاش"};
        int sel = DownedClientEvents.selected();

        HudPen pen = PROMPT;
        pen.begin(graphics);
        try {
            int n = labels.length;
            Shaped[] shaped = new Shaped[n];
            float[] widths = new float[n];
            float total = 0.0F;
            for (int i = 0; i < n; i++) {
                shaped[i] = pen.kufi(labels[i], 6.0F, 600);
                widths[i] = Math.max(32.0F, pen.width(shaped[i]) + 14.0F);
                total += widths[i];
            }
            total += 2.0F * (n - 1);
            float x = p[0] - total / 2.0F;
            float y = p[1] - 6.0F;
            float h = 13.0F;
            for (int i = 0; i < n; i++) {
                float w = widths[i];
                boolean on = i == sel;
                int fill = on ? 0xE61A1F17 : 0xCC0F120D;
                pen.rect(x + 1, y, w - 2, h, fill);
                pen.rect(x, y + 1, 1, h - 2, fill);
                pen.rect(x + w - 1, y + 1, 1, h - 2, fill);
                int edge = on ? accent : 0x40FFFFFF;
                pen.rect(x + 1, y, w - 2, 0.5F, edge);
                pen.rect(x + 1, y + h - 0.5F, w - 2, 0.5F, edge);
                pen.text(shaped[i], x + w / 2.0F, y + 9.0F, HudPen.CENTER, on ? BONE : MUTED);
                if (on) {
                    Shaped key = pen.pixel("F", 5.5F, 700);
                    float kw = pen.width(key) + 4.0F;
                    pen.rect(x + w / 2.0F - kw / 2.0F, y - 7.5F, kw, 6.5F, accent);
                    pen.text(key, x + w / 2.0F, y - 2.6F, HudPen.CENTER, 0xFF0B0E0A);
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

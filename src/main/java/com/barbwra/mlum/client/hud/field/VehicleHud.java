package com.barbwra.mlum.client.hud.field;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.client.hud.HudVisibility;
import com.barbwra.mlum.client.ui.mc.UiText;
import com.barbwra.mlum.client.ui.text.Shaped;
import com.barbwra.mlum.compat.SbwCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Inside a Superb Warfare vehicle the HUD is the vehicle's: a speedometer at the bottom middle and
 * the vehicle's health (and fuel, when it has a tank) at the bottom right. The player's own wrist
 * device, belt and weapon panel are put away, and so are vanilla's hearts, hunger, armour and
 * hotbar - the hands are off the hotbar in there ({@code DriverRules}), so showing it would only
 * invite clicks that do nothing.
 *
 * <p>Also the client half of that rule: the use and attack keys do nothing (no swing, no
 * half-started use) and no hands or held item are drawn in first person.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class VehicleHud {

    private VehicleHud() {
    }

    private static final HudPen PEN = new HudPen();
    private static final int PANEL_W = 112;
    private static final int PANEL_H = 40;
    private static final int MARGIN = 6;

    /** Smoothed speed in km/h, and the last health seen, for the hit flash. */
    private static float speed;
    private static float lastHealth = -1.0F;
    private static long hitAt;

    @javax.annotation.Nullable
    public static Entity vehicle() {
        return SbwCompat.ridden(Minecraft.getInstance().player);
    }

    public static boolean active() {
        return vehicle() != null;
    }

    /* ================================================================== the hands */

    /** Use and attack do nothing in a vehicle; Superb Warfare's own fire keys run before this. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInteractKey(InputEvent.InteractionKeyMappingTriggered event) {
        if ((event.isAttack() || event.isUseItem()) && active()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderHand(RenderHandEvent event) {
        if (active()) {
            event.setCanceled(true);
        }
    }

    /* ================================================================== the numbers */

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Entity v = vehicle();
        if (v == null) {
            speed = 0.0F;
            lastHealth = -1.0F;
            return;
        }
        double dx = v.getX() - v.xo;
        double dy = v.getY() - v.yo;
        double dz = v.getZ() - v.zo;
        float now = (float) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0D * 3.6D);
        speed += (now - speed) * 0.35F;
        float health = SbwCompat.health(v);
        if (lastHealth >= 0.0F && health < lastHealth - 0.01F) {
            hitAt = System.currentTimeMillis();
        }
        lastHealth = health;
    }

    /* ================================================================== drawing */

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Entity v = vehicle();
        if (v == null || player == null || mc.options.hideGui || HudVisibility.hidden() || mc.screen != null) {
            return;
        }
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        int accent = MlumConfig.fieldHudAccent();
        float k = MlumConfig.fieldHudScale();
        long now = System.currentTimeMillis();
        HudPen pen = PEN;
        pen.begin(event.getGuiGraphics());
        try {
            pen.zoom(width / 2.0F, height, k);
            speedometer(pen, width / 2.0F, height - 16.0F, accent);
            pen.zoom(width - MARGIN, height - MARGIN, k);
            panel(pen, v, player, width - MARGIN - PANEL_W, height - MARGIN - PANEL_H, accent, now);
        } finally {
            pen.unzoom();
            pen.end();
        }
    }

    /** The dial: 240 degrees of arc, filling with speed, the number in the middle. */
    private static void speedometer(HudPen pen, float cx, float cy, int accent) {
        float shown = Math.max(0.0F, speed);
        // the scale grows for whatever is fast: cars, then helicopters, then jets
        float max = shown < 120.0F ? 120.0F : shown < 240.0F ? 240.0F : 480.0F;
        float r = 26.0F;
        Shapes.disc(pen, cx, cy, r + 9.0F, 0x8C000000);
        Shapes.ring(pen, cx, cy, r + 8.5F, 1.0F, FieldHud.alpha(accent, 0.18F));
        float start = 0.6667F;
        float span = 0.6667F;
        arc(pen, cx, cy, r, 3.0F, start, span, 0x26FFFFFF);
        float f = Mth.clamp(shown / max, 0.0F, 1.0F);
        int fill = f > 0.85F ? FieldHud.RUST : accent;
        arc(pen, cx, cy, r, 3.0F, start, span * f, fill);
        // the scale: a mark every tenth, longer at each quarter
        for (int i = 0; i <= 10; i++) {
            double a = (start + span * i / 10.0F) * Math.PI * 2.0D;
            float len = i % 5 == 0 ? 3.0F : 1.5F;
            float rr = r - 4.5F;
            float px = cx + (float) Math.sin(a) * rr;
            float py = cy - (float) Math.cos(a) * rr;
            pen.rect(px - 0.5F, py - len / 2.0F, 1.0F, len, i * 10 <= f * 100 ? FieldHud.alpha(FieldHud.BONE, 0.85F) : 0x40FFFFFF);
        }
        Shaped n = pen.pixel(String.valueOf(Math.round(shown)), 15.0F, 700);
        pen.shadowed(n, cx, cy + 3.0F, HudPen.CENTER, FieldHud.BONE);
        pen.text(pen.pixel("KM/H", 4.5F, 700), cx, cy + 10.0F, HudPen.CENTER, FieldHud.MUTED);
    }

    /** An arc from {@code start} for {@code len} of a turn, wrapping past twelve o'clock. */
    private static void arc(HudPen pen, float cx, float cy, float r, float thick, float start, float len, int argb) {
        if (len <= 0.0F) {
            return;
        }
        float end = start + len;
        if (end <= 1.0F) {
            Shapes.arc(pen, cx, cy, r, thick, start, end, argb);
        } else {
            Shapes.arc(pen, cx, cy, r, thick, start, 1.0F, argb);
            Shapes.arc(pen, cx, cy, r, thick, 0.0F, end - 1.0F, argb);
        }
    }

    /** The vehicle: its name, its health in segments, its fuel. */
    private static void panel(HudPen pen, Entity v, LocalPlayer player, float x, float y, int accent, long now) {
        int shake = now - hitAt < 160L ? ((now / 40L) % 2 == 0 ? 1 : -1) : 0;
        x += shake;
        FieldHud.chamfer(pen, x, y, PANEL_W, PANEL_H, 4, 0, 2, 0, accent);
        pen.rect(x + 10, y, 7, 1, FieldHud.alpha(accent, 0.9F));

        String name = UiText.logical(v.getDisplayName().getString());
        boolean latin = name.chars().allMatch(c -> c < 0x0600 || c > 0x06FF);
        Shaped ns = latin ? pen.pixel(name, 6.0F, 700) : pen.kufi(name, 4.8F, 700);
        pen.text(ns, x + PANEL_W - 5, y + 9.0F, HudPen.RIGHT, FieldHud.BONE);
        boolean driving = SbwCompat.isDriving(player);
        pen.text(pen.kufi(driving ? "السائق" : "راكب", 4.2F, 600), x + 5, y + 8.5F, HudPen.LEFT, FieldHud.MUTED);

        float health = Math.max(0.0F, SbwCompat.health(v));
        float max = SbwCompat.maxHealth(v);
        float f = max > 0.0F ? Mth.clamp(health / max, 0.0F, 1.0F) : 0.0F;
        int color = f > 0.5F ? 0xFF93C46F : f > 0.25F ? FieldHud.WHEAT : FieldHud.RUST;
        boolean flash = now - hitAt < 300L;
        // ten segments, the last one partly lit
        float bx = x + 5;
        float bw = PANEL_W - 10;
        float seg = (bw - 9 * 1.5F) / 10.0F;
        for (int i = 0; i < 10; i++) {
            float sx = bx + i * (seg + 1.5F);
            pen.rect(sx, y + 14, seg, 6, 0x22FFFFFF);
            float fillPart = Mth.clamp(f * 10.0F - i, 0.0F, 1.0F);
            if (fillPart > 0.0F) {
                pen.rect(sx, y + 14, seg * fillPart, 6, flash ? 0xFFFFFFFF : color);
            }
        }
        String hp = max > 0.0F ? Math.round(health) + " / " + Math.round(max) : "—";
        pen.text(pen.pixel(hp, 6.0F, 700), x + PANEL_W - 5, y + 27.0F, HudPen.RIGHT, color);
        pen.text(pen.kufi("الهيكل", 4.2F, 600), x + 5, y + 26.5F, HudPen.LEFT, FieldHud.MUTED);

        float fuel = SbwCompat.energyFraction(v);
        if (fuel >= 0.0F) {
            pen.rect(x + 5, y + PANEL_H - 5, PANEL_W - 10, 2, 0x22FFFFFF);
            pen.rect(x + 5, y + PANEL_H - 5, (PANEL_W - 10) * fuel, 2, fuel < 0.15F ? FieldHud.RUST : FieldHud.STEEL);
            pen.text(pen.pixel("FUEL " + Math.round(fuel * 100) + "%", 4.0F, 700), x + PANEL_W - 5, y + PANEL_H - 7.0F,
                    HudPen.RIGHT, FieldHud.FAINT);
        }
    }
}

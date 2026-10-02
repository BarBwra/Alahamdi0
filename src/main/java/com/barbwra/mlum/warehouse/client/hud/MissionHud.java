package com.barbwra.mlum.warehouse.client.hud;

import com.barbwra.mlum.warehouse.WarehouseConfig;
import com.barbwra.mlum.warehouse.client.ClientMissionData;
import com.barbwra.mlum.warehouse.client.gui.GuiDraw;
import com.barbwra.mlum.warehouse.client.gui.Theme;
import com.barbwra.mlum.warehouse.net.S2CMissionHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The in-run heads-up display: status card, countdowns, and a compass pointing at the drop.
 *
 * <p><b>Everything here is computed on the client, every frame.</b> The server sends the target
 * coordinates once when the run starts and two absolute deadlines once a second; the bearing, the
 * distance, the bar fractions and the countdowns are all derived locally. That is what makes the
 * compass smooth while sprinting and the timer smooth at any framerate - none of it is waiting on
 * a packet.</p>
 *
 * <p>It also replaces the {@code jwbwp_server create_pos} waypoint call the prototype shelled out
 * to. A waypoint marker is a static pin; this is a live instrument, and it costs one console
 * command fewer per mission.</p>
 */
public final class MissionHud {

    private MissionHud() {
    }

    /** Degrees of bearing visible across the whole compass strip. */
    private static final float COMPASS_FOV = 120.0F;
    private static final String[] CARDINALS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    public static void render(GuiGraphics g, int screenWidth, int screenHeight) {
        if (!WarehouseConfig.hudEnabled() || !ClientMissionData.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        S2CMissionHud state = ClientMissionData.state();
        boolean exposed = ClientMissionData.isExposed();

        renderCard(g, mc, state, exposed);
        if (WarehouseConfig.compassEnabled()) {
            renderCompass(g, mc, player, state, screenWidth);
        }
    }

    /* ------------------------------------------------------------------ card */

    private static void renderCard(GuiGraphics g, Minecraft mc, S2CMissionHud state, boolean exposed) {
        int x = 6;
        int y = 6;
        int w = 128;
        int h = 52;

        GuiDraw.glass(g, x, y, w, h);

        int accent = exposed ? Theme.DANGER : Theme.accent();
        if (exposed) {
            // Hazard tape down the leading edge once the position is public.
            GuiDraw.hazard(g, x + 2, y + 2, 3, h - 4, Theme.DANGER, 0x9A);
        }

        /*
         * The exposed banner pulses. It is the one piece of the UI allowed to move on its own,
         * because it is the only state change the player has to notice without looking at it.
         */
        int alpha = 0xFF;
        if (exposed) {
            float pulse = (Mth.sin((System.currentTimeMillis() % 1000L) / 1000.0F * Mth.TWO_PI) + 1.0F) * 0.5F;
            alpha = 0x90 + (int) (pulse * 0x6F);
        }

        g.fill(x + 2, y + 2, x + 5, y + 8, Theme.withAlpha(accent, alpha));
        GuiDraw.drawLeft(g, mc.font, exposed ? "EXPOSED" : "COVERT", x + 9, y + 3,
                Theme.withAlpha(accent, alpha));
        GuiDraw.drawRight(g, mc.font, state.crates() + " × " + state.payout() + "$",
                x + w - 6, y + 3, Theme.SUCCESS);

        long untilLeak = ClientMissionData.millisUntilLeak();
        long untilExpiry = ClientMissionData.millisUntilExpiry();

        if (!exposed) {
            GuiDraw.drawLeft(g, mc.font, "تسريب", x + 6, y + 16, Theme.TEXT_DIM);
            GuiDraw.drawRight(g, mc.font, GuiDraw.clock(untilLeak), x + w - 6, y + 16, Theme.WARN);
            long leakSpan = Math.max(1L, state.leaksAt() - (state.expiresAt()
                    - WarehouseConfig.expireSeconds() * 1000L));
            GuiDraw.thinBar(g, x + 6, y + 26, w - 12, 3, (float) untilLeak / leakSpan, Theme.WARN);
        } else {
            GuiDraw.drawLeft(g, mc.font, "موقعك مكشوف للجميع", x + 6, y + 16, Theme.DANGER);
        }

        GuiDraw.drawLeft(g, mc.font, "الوقت", x + 6, y + 33, Theme.TEXT_DIM);
        GuiDraw.drawRight(g, mc.font, GuiDraw.clock(untilExpiry), x + w - 6, y + 33,
                untilExpiry < 60_000L ? Theme.DANGER : Theme.TEXT);
        GuiDraw.thinBar(g, x + 6, y + 43, w - 12, 3,
                (float) untilExpiry / (WarehouseConfig.expireSeconds() * 1000.0F),
                untilExpiry < 60_000L ? Theme.DANGER : Theme.accent());
    }

    /* --------------------------------------------------------------- compass */

    private static void renderCompass(GuiGraphics g, Minecraft mc, LocalPlayer player,
                                      S2CMissionHud state, int screenWidth) {
        int w = Math.min(WarehouseConfig.compassWidth(), screenWidth - 20);
        int x = (screenWidth - w) / 2;
        int y = 6;
        int h = 15;
        int cx = x + w / 2;

        g.fillGradient(x, y, x + w, y + h, 0xB0070C11, 0x70070C11);
        GuiDraw.outline(g, x, y, w, h, Theme.BORDER_SOFT);

        float yaw = player.getYRot();

        // Cardinal ticks slide past as the player turns.
        for (int i = 0; i < CARDINALS.length; i++) {
            float bearing = i * 45.0F;
            float offset = Mth.wrapDegrees(bearing - yaw);
            if (Math.abs(offset) > COMPASS_FOV / 2.0F) {
                continue;
            }
            int px = cx + Math.round(offset / (COMPASS_FOV / 2.0F) * (w / 2.0F));
            g.fill(px, y + 1, px + 1, y + 5, Theme.withAlpha(Theme.TEXT_MUTED, 0xC0));
            GuiDraw.drawCentered(g, mc.font, CARDINALS[i], px, y + 6, Theme.TEXT_MUTED);
        }

        /*
         * Minecraft yaw runs clockwise from south, so the bearing to a point is
         * -atan2(dx, dz) in degrees. Getting this wrong mirrors the marker, which is
         * exactly the kind of bug that only shows up when you are running the wrong way.
         */
        double dx = state.targetX() - player.getX();
        double dz = state.targetZ() - player.getZ();
        float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float offset = Mth.wrapDegrees(bearing - yaw);

        int distance = (int) Math.sqrt(dx * dx + dz * dz);
        boolean onScreen = Math.abs(offset) <= COMPASS_FOV / 2.0F;
        int markerX = onScreen
                ? cx + Math.round(offset / (COMPASS_FOV / 2.0F) * (w / 2.0F))
                : (offset > 0 ? x + w - 3 : x + 2);

        int color = ClientMissionData.isExposed() ? Theme.DANGER : Theme.SUCCESS;
        // A triangle, drawn as stacked rows - it reads as a pointer where a square reads as a tick.
        for (int row = 0; row < 4; row++) {
            g.fill(markerX - row, y + h - 1 - row, markerX + row + 1, y + h - row, color);
        }
        if (!onScreen) {
            GuiDraw.drawCentered(g, mc.font, offset > 0 ? "▸" : "◂", markerX, y + 3, color);
        }

        GuiDraw.drawCentered(g, mc.font, distance + "m", cx, y + h + 2, Theme.TEXT_DIM);
    }
}

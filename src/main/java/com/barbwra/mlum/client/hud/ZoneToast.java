package com.barbwra.mlum.client.hud;

import com.barbwra.mlum.client.ClientZoneNotice;
import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.client.gui.GuiDraw;
import com.barbwra.mlum.client.gui.Theme;
import com.barbwra.mlum.util.ArabicText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The safe-area popup: a card that slides in from the left edge, holds, and slides back out.
 *
 * <pre>
 *   +--+----------------------------+
 *   |  |  ENTERING                  |
 *   |  |  المنطقة الآمنة             |
 *   |  |  no player can hurt you    |
 *   +--+----------------------------+
 *    ^ colour bar: green in, amber out
 * </pre>
 *
 * <p><b>Mid-left, not centre.</b> The centre of the screen is where the player is aiming and where
 * every other game already puts its most intrusive text. A card pinned to the left edge at eye
 * height is read in peripheral vision without ever covering a target.</p>
 *
 * <p>Everything is driven off one timestamp in {@link ClientZoneNotice}, so the animation runs at
 * framerate and the server sent exactly one packet to start it.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ZoneToast {

    private ZoneToast() {
    }

    private static final int CARD_W = 132;
    private static final int CARD_H = 38;
    private static final int BAR_W = 3;
    private static final int MARGIN = 6;

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (!ClientZoneNotice.isVisible()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (HudVisibility.hidden()) {
            return;
        }

        boolean entered = ClientZoneNotice.entered();
        float[] motion = ClientZoneNotice.motion();
        float slide = motion[0];
        float alpha = motion[1];

        // Travels in from just off the left edge to its resting margin.
        int x = Math.round(-CARD_W + (CARD_W + MARGIN) * slide);
        int y = screenHeight / 2 - CARD_H / 2;

        int accent = entered ? Theme.OK : Theme.FOOD_HI;
        Font font = minecraft.font;

        GuiDraw.panelBody(graphics, x, y, CARD_W, CARD_H,
                Anim.fade(Theme.panelTop(), alpha), Anim.fade(Theme.panelBottom(), alpha));
        GuiDraw.panelOutline(graphics, x, y, CARD_W, CARD_H, Anim.fade(accent, alpha * 0.85F));
        graphics.fill(x + 2, y + 2, x + 2 + BAR_W, y + CARD_H - 2, Anim.fade(accent, alpha));

        String heading = entered ? "دخلت منطقة آمنة" : "غادرت المنطقة الآمنة";
        String note = entered ? "لا يمكن لأحد إيذاؤك هنا" : "احترس - القتال مسموح";

        drawRtl(graphics, font, heading, x + CARD_W - 6, y + 6, Anim.fade(accent, alpha));
        drawRtl(graphics, font, ClientZoneNotice.zoneName(), x + CARD_W - 6, y + 17,
                Anim.fade(Theme.TEXT, alpha));
        drawRtl(graphics, font, note, x + CARD_W - 6, y + 27, Anim.fade(Theme.TEXT_MUTED, alpha));
    }

    /**
     * Right-aligned Arabic.
     *
     * <p>Shaped <i>and</i> reordered here, because this goes through {@code drawString(String, ...)}
     * which has no bidi pass of its own. Text handed to a {@code Component} must only be shaped -
     * Minecraft reorders that path itself, and doing both reverses the line twice.</p>
     */
    private static void drawRtl(GuiGraphics graphics, Font font, String text, int right, int y, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String shown = ArabicText.autoDisplay(text);
        graphics.drawString(font, shown, right - font.width(shown), y, color, false);
    }
}

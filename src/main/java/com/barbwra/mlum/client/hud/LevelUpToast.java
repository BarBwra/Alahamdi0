package com.barbwra.mlum.client.hud;

import com.barbwra.mlum.client.ClientLevelUp;
import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.client.gui.GuiDraw;
import com.barbwra.mlum.client.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The level-up card, replacing what used to be a line of chat.
 *
 * <p>Sits above the middle of the screen rather than in a corner, because unlike the safe-area
 * notice this is not information the player needs to act on - it is the payoff for everything the
 * progression track asks of them, and it should be in front of them for the two seconds it lasts.
 * It sits high enough to clear the hotbar and the vitals rows entirely.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class LevelUpToast {

    private LevelUpToast() {
    }

    private static final int CARD_W = 150;
    private static final int CARD_H = 34;
    /** Measured up from the bottom, clear of the hotbar, vitals and the level bar. */
    private static final int BOTTOM_OFFSET = 96;

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (!ClientLevelUp.isVisible() || HudVisibility.hidden()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        float[] motion = ClientLevelUp.motion();
        float slide = motion[0];
        float alpha = motion[1];

        int x = screenWidth / 2 - CARD_W / 2;
        // rises the last few pixels into place as it arrives
        int y = screenHeight - BOTTOM_OFFSET - CARD_H + Math.round((1.0F - slide) * 10.0F);

        GuiDraw.panelBody(graphics, x, y, CARD_W, CARD_H,
                Anim.fade(Theme.panelTop(), alpha), Anim.fade(Theme.panelBottom(), alpha));
        GuiDraw.panelOutline(graphics, x, y, CARD_W, CARD_H, Anim.fade(Theme.accent(), alpha));
        GuiDraw.brackets(graphics, x, y, CARD_W, CARD_H, Anim.fade(Theme.accentBright(), alpha), 12);

        // A bar of accent down the leading edge, the same spine the tooltips use.
        graphics.fill(x, y, x + 2, y + CARD_H, Anim.fade(Theme.accentBright(), alpha));

        // Shaped at draw time rather than pulled from a lang file: the mod's lang files are baked
        // into visual order at build time, and a string added by hand would not be. autoDisplay
        // handles raw Arabic and leaves anything already baked alone.
        String title = com.barbwra.mlum.util.ArabicText.autoDisplay("وصلت للمستوى");
        String number = String.valueOf(ClientLevelUp.level());

        int tx = x + CARD_W / 2 - font.width(title) / 2;
        graphics.drawString(font, title, tx, y + 8, Anim.fade(Theme.TEXT, alpha), true);

        // The number is the point of the card, so it gets the accent and the lower, wider line.
        int nx = x + CARD_W / 2 - font.width(number) / 2;
        graphics.drawString(font, number, nx, y + 20, Anim.fade(Theme.accentBright(), alpha), true);
    }
}

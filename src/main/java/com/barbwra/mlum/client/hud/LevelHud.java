package com.barbwra.mlum.client.hud;

import com.barbwra.mlum.client.ClientLevelData;
import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.client.gui.GuiDraw;
import com.barbwra.mlum.client.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The progression bar, sitting just above the vanilla experience bar.
 *
 * <pre>
 *              LV 7   ▓▓▓▓▓▓▓▓▓░░░░░░   180/225
 *            [ vanilla xp bar, untouched ]
 *            [ vanilla hotbar            ]
 * </pre>
 *
 * <p><b>Vanilla's bar is deliberately left alone.</b> Enchanting still reads from it and players
 * still expect it, so this is a second bar rather than a replacement - stacked directly above so
 * the two read as one block instead of competing for attention in different corners.</p>
 *
 * <p>Narrower than vanilla's on purpose. Making it the same width would have the two constantly
 * mistaken for each other at a glance; a shorter, brighter bar with a level badge is unambiguous.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class LevelHud {

    private LevelHud() {
    }

    /*
     * Vanilla's own experience bar is cancelled (see ClientEvents), and this takes its exact place:
     * 182 wide, centred, 5 tall, 32px off the bottom. Sitting anywhere else was the whole problem -
     * two bars stacked on top of each other, neither readable, with the health and hunger rows
     * crowding in from above. Occupying the slot instead of adding to it leaves the vanilla vitals
     * exactly where players already expect them.
     */
    private static final int BAR_W = 182;
    private static final int BAR_H = 5;

    /**
     * Where the bar sits, and why it moved.
     *
     * <p>It used to be 32px off the bottom, which put its <i>level badge</i> - drawn nine pixels
     * above the bar - at screenHeight-41. Vanilla draws hearts and hunger at screenHeight-39. The
     * number therefore landed directly on that row, which is exactly what the overlap looked like:
     * a level digit sitting between the hearts and the drumsticks.</p>
     *
     * <p>The bar now takes vanilla's own experience row at screenHeight-29, and
     * {@link com.barbwra.mlum.events.ClientEvents} lifts the health, hunger, armour and air rows a
     * few pixels so nothing shares a line. Both halves are needed: dropping the bar alone would
     * still leave the badge grazing the hearts.</p>
     */
    private static final int BOTTOM_OFFSET = 29;
    /** How far the vanilla vitals rows are pushed up to clear the badge. */
    public static final int VITALS_LIFT = 5;

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (HudVisibility.hidden()) {
            return;
        }

        Font font = minecraft.font;
        int x = screenWidth / 2 - BAR_W / 2;
        int y = screenHeight - BOTTOM_OFFSET;

        float fraction = ClientLevelData.fraction();
        float flourish = ClientLevelData.levelUpProgress();

        /*
         * On a level up the whole widget swells briefly and the bar flashes white. It is the only
         * moment this HUD is allowed to draw attention to itself, which is exactly why it works -
         * a bar that animates constantly stops meaning anything.
         */
        boolean celebrating = flourish < 1.0F;
        int accent = celebrating
                ? Anim.mix(0xFFFFFFFF, Theme.accent(), flourish)
                : Theme.accent();
        int lift = celebrating ? Math.round((1.0F - flourish) * 3.0F) : 0;

        graphics.fill(x - 1, y - 1 - lift, x + BAR_W + 1, y + BAR_H + 1 - lift, 0xC0000000);
        graphics.fill(x, y - lift, x + BAR_W, y + BAR_H - lift, Theme.TRACK);

        int filled = Math.round(BAR_W * Math.max(0.0F, Math.min(1.0F, fraction)));
        if (filled > 0) {
            graphics.fillGradient(x, y - lift, x + filled, y + BAR_H - lift,
                    accent, Anim.fade(accent, 0.65F));
            // Bright leading edge, so the head of the bar is findable at a glance.
            graphics.fill(x + filled - 1, y - lift, x + filled, y + BAR_H - lift, Theme.accentBright());
        }
        GuiDraw.outline(graphics, x - 1, y - 1 - lift, BAR_W + 2, BAR_H + 2, Theme.BORDER);

        /*
         * The level number sits centred above the bar, where vanilla drew its own - outlined in
         * black on all four sides rather than shadowed, because this lands over the hotbar and a
         * single drop shadow is not enough contrast against a bright item.
         */
        String badge = String.valueOf(ClientLevelData.level());
        int bx = x + BAR_W / 2 - font.width(badge) / 2;
        int by = y - 9 - lift;
        graphics.drawString(font, badge, bx + 1, by, 0xFF000000, false);
        graphics.drawString(font, badge, bx - 1, by, 0xFF000000, false);
        graphics.drawString(font, badge, bx, by + 1, 0xFF000000, false);
        graphics.drawString(font, badge, bx, by - 1, 0xFF000000, false);
        graphics.drawString(font, badge, bx, by, accent, false);

        // Points into the level, tucked at the right end where it never fights the number.
        String progress = ClientLevelData.inLevel() + "/" + ClientLevelData.costOfNext();
        graphics.drawString(font, progress, x + BAR_W - font.width(progress), y - 9 - lift,
                Theme.TEXT_MUTED, true);
    }
}

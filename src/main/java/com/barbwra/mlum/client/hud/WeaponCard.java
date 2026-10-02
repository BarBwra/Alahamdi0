package com.barbwra.mlum.client.hud;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.client.gui.GuiDraw;
import com.barbwra.mlum.client.gui.Theme;
import com.barbwra.mlum.compat.SbwCompat;
import com.barbwra.mlum.compat.TaczCompat;
import com.barbwra.mlum.menu.MlumMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The firearm readout in the bottom right: both weapons, in 2D, and what is loaded.
 *
 * <pre>
 *                                   SEMI          021/999
 *                                  +-------------------+
 *                                  | 1 [ gun artwork ] |
 *                                  +-------------------+
 *                                  | 2  ~ other gun ~  |   &lt;- dim, 14px
 *                                  +-------------------+
 * </pre>
 *
 * <p><b>This is the firearms' only home on screen.</b> The hotbar is seven slots now and does not
 * carry them, so unlike an ordinary HUD widget this one may never hide itself - a weapon you cannot
 * see is a weapon you forget you have. It stays up whatever is in your hands, and dims as a whole
 * when the thing in your hands is not one of these two.</p>
 *
 * <p><b>The strip underneath</b> is deliberately understated. It exists so you know what pressing
 * the other key hands you without having to press it, and that is all - so it is short, wide and
 * low-contrast, because a second full-size gun picture in the corner of the screen would cost more
 * view than the information is worth.</p>
 *
 * <p>Everything comes off the item stack's NBT, so there is no TACZ dependency and nothing here
 * breaks if TACZ moves a class.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WeaponCard {

    private WeaponCard() {
    }

    public static final int CARD_W = 96;
    public static final int CARD_H = 46;
    public static final int SLICE_H = 14;
    private static final int SLICE_GAP = 2;
    private static final int MARGIN = 4;
    /** Vanilla's nine-slot hotbar, and the offhand cell that sits beside it. */
    private static final int VANILLA_BAR_W = 182;
    private static final int OFFHAND_W = 29;

    private static final float SWAP_MS = 150.0F;
    private static final int LOW_AMMO = 5;
    /** How far the card fades when neither firearm is in hand. */
    private static final float HOLSTERED = 0.55F;

    private static boolean itemsBroken;
    /** Which weapon the card is showing, so it keeps showing one after you switch to a bandage. */
    private static int shown;
    private static int shownFrom = -1;
    private static long shownAt;

    public static void reset() {
        shown = 0;
        shownFrom = -1;
        itemsBroken = false;
    }

    public static void render(GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (HudVisibility.hidden()) {
            return;
        }
        // A player in a Superb Warfare vehicle is using the vehicle's own weapons and its own
        // readouts; the firearm card would sit on top of them describing a gun that is not in play.
        // Reset first, so stepping out does not replay a swap animation from before the ride.
        if (SbwCompat.isRiding(player)) {
            reset();
            return;
        }

        int selected = player.getInventory().selected;
        boolean inHand = selected < MlumMenu.WEAPON_SLOTS;
        if (inHand && selected != shown) {
            shownFrom = shown;
            shown = selected;
            shownAt = Anim.now();
        }

        /*
         * With only one firearm carried, the card shows that firearm and nothing else - no empty
         * "--" card, no empty strip. Previously the readout always drew both cells, so a player
         * with a single gun in slot 2 got a blank card above a real strip, and one in slot 1 got a
         * real card above a blank strip. An empty cell communicates nothing and costs screen.
         */
        int mainIndex = shown;
        if (player.getInventory().getItem(mainIndex).isEmpty()) {
            mainIndex = mainIndex == 0 ? 1 : 0;
        }
        ItemStack live = player.getInventory().getItem(mainIndex);
        ItemStack spare = player.getInventory().getItem(mainIndex == 0 ? 1 : 0);
        if (live.isEmpty()) {
            return;   // no firearms at all; nothing to say
        }
        boolean showSpare = !spare.isEmpty();

        float alpha = inHand ? 1.0F : HOLSTERED;
        float swap = shownFrom < 0 ? 1.0F : Anim.easeOut(Anim.progress(shownAt, SWAP_MS));

        int x = screenWidth - MARGIN - CARD_W;
        int bottom = screenHeight - MARGIN;

        // On a narrow window - GUI Scale 4 on a 1280px monitor gives only 320 usable pixels - the
        // hotbar reaches past the card's left edge. Rather than let the two overlap, the whole
        // block steps up over the bar. Vanilla's bar is 182 wide and centred, and the offhand cell
        // adds another 29 beside it.
        int hotbarRight = screenWidth / 2 + VANILLA_BAR_W / 2 + OFFHAND_W;
        if (x < hotbarRight + 4) {
            bottom -= 26;
        }

        // With no spare, the card drops into the space the strip would have used.
        int sliceY = bottom - SLICE_H;
        int cardY = showSpare ? sliceY - SLICE_GAP - CARD_H : bottom - CARD_H;
        // the card lifts the last few pixels as a swap lands
        int lift = Math.round((1.0F - swap) * 4.0F);

        drawCard(graphics, minecraft.font, live, mainIndex, x, cardY + lift, alpha, player);
        if (showSpare) {
            drawSlice(graphics, minecraft.font, spare, mainIndex == 0 ? 1 : 0, x, sliceY, alpha * 0.75F);
        }
    }

    private static void drawCard(GuiGraphics graphics, Font font, ItemStack gun, int key,
                                 int x, int y, float alpha, LocalPlayer player) {
        int loaded = TaczCompat.loadedRounds(gun);
        boolean empty = loaded == 0;

        GuiDraw.panelBody(graphics, x, y, CARD_W, CARD_H,
                Anim.fade(Theme.panelTop(), alpha), Anim.fade(Theme.panelBottom(), alpha));
        GuiDraw.panelOutline(graphics, x, y, CARD_W, CARD_H,
                Anim.fade(empty ? Theme.HEALTH : Theme.BORDER_HI, alpha));
        GuiDraw.brackets(graphics, x, y, CARD_W, CARD_H,
                Anim.fade(Theme.accent(empty ? 220 : 150), alpha), 9);

        if (gun.isEmpty()) {
            String none = "--";
            graphics.drawString(font, none, x + (CARD_W - font.width(none)) / 2, y + CARD_H / 2 - 4,
                    Anim.fade(Theme.TEXT_MUTED, alpha), false);
            drawKey(graphics, font, key, x + 4, y + 4, alpha);
            return;
        }

        /* ---- header: fire mode left, rounds right ---- */
        String mode = TaczCompat.fireMode(gun);
        if (!mode.isEmpty()) {
            graphics.drawString(font, GuiDraw.trim(font, GuiDraw.upper(mode), 34),
                    x + 14, y + 4, Anim.fade(Theme.TEXT_MUTED, alpha), false);
        }

        if (loaded >= 0) {
            int color = Theme.accentBright();
            if (empty) {
                color = Theme.HEALTH_HI;
            } else if (loaded <= LOW_AMMO) {
                color = Anim.mix(Theme.FOOD_HI, Theme.HEALTH_HI, Anim.pulse(700.0F));
            }
            // Magazine over carried reserve, the way TACZ's own readout reads: 030/200.
            String rounds = String.format("%03d", Math.min(999, loaded));
            int reserve = TaczCompat.reserveInInventory(player, gun);
            String spare = reserve >= 0 ? "/" + Math.min(9999, reserve) : "";

            int spareW = spare.isEmpty() ? 0 : font.width(spare);
            graphics.drawString(font, rounds, x + CARD_W - 5 - spareW - font.width(rounds), y + 4,
                    Anim.fade(color, alpha), false);
            if (!spare.isEmpty()) {
                graphics.drawString(font, spare, x + CARD_W - 5 - spareW, y + 4,
                        Anim.fade(Theme.TEXT_MUTED, alpha), false);
            }
        }

        drawKey(graphics, font, key, x + 4, y + 4, alpha);
        GuiDraw.divider(graphics, x + 5, y + 15, x + CARD_W - 5);
        drawGun(graphics, gun, x + 5, y + 18, CARD_W - 10, CARD_H - 23, empty, alpha);
    }

    /**
     * The other weapon, squashed into 14px and dimmed.
     *
     * <p>A vertical squash rather than a uniform shrink is deliberate - a gun is a long thin shape,
     * so flattening it stays recognisable at a fraction of the height a scaled copy would need.</p>
     */
    private static void drawSlice(GuiGraphics graphics, Font font, ItemStack gun, int key,
                                  int x, int y, float alpha) {
        graphics.fill(x, y, x + CARD_W, y + SLICE_H, Anim.fade(Theme.panelBottom(), alpha));
        GuiDraw.outline(graphics, x, y, CARD_W, SLICE_H, Anim.fade(Theme.BORDER, alpha));

        graphics.drawString(font, String.valueOf(key + 1), x + 4, y + 3,
                Anim.fade(Theme.TEXT_MUTED, alpha), false);

        if (gun.isEmpty()) {
            return;
        }
        drawGun(graphics, gun, x + 14, y + 1, CARD_W - 18, SLICE_H - 2, false, alpha * 0.85F);

        int rounds = TaczCompat.loadedRounds(gun);
        if (rounds >= 0) {
            String text = String.valueOf(Math.min(999, rounds));
            graphics.drawString(font, text, x + CARD_W - 4 - font.width(text), y + 3,
                    Anim.fade(Theme.TEXT_DIM, alpha), false);
        }
    }

    private static void drawKey(GuiGraphics graphics, Font font, int key, int x, int y, float alpha) {
        graphics.drawString(font, String.valueOf(key + 1), x, y,
                Anim.fade(Theme.accentBright(), alpha), false);
    }

    /**
     * The gun picture, stretched into the box it is given.
     *
     * <p>TACZ ships flat artwork per gun and that is exactly what is wanted here. When it cannot be
     * found - a pack that names things differently, or a gun from another mod - the item itself is
     * drawn instead, which for a TACZ gun is its own 2D inventory icon.</p>
     */
    private static void drawGun(GuiGraphics graphics, ItemStack gun, int x, int y,
                                int w, int h, boolean empty, float alpha) {
        /*
         * An empty gun asks for the "_empty" artwork first, but most packs - including the stock
         * one for many guns - do not ship it. That miss used to fall straight through to the item
         * renderer, so running dry swapped the flat gun picture for a tiny inventory icon. Falling
         * back to the loaded artwork keeps the silhouette; the card's border and round counter have
         * already gone red, so nothing is lost by it.
         */
        ResourceLocation art = TaczGunHud.hudTexture(gun, empty);
        if (art == null && empty) {
            art = TaczGunHud.hudTexture(gun, false);
        }
        if (art != null) {
            graphics.setColor(1.0F, 1.0F, 1.0F, Mth.clamp(alpha, 0.0F, 1.0F));
            // uWidth/vHeight/textureWidth/textureHeight all 1 makes the UVs span 0..1, which
            // stretches the whole image into the box without having to know its pixel size
            graphics.blit(art, x, y, w, h, 0.0F, 0.0F, 1, 1, 1, 1);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        if (itemsBroken) {
            return;
        }
        // A TACZ gun goes through that mod's own item renderer. A throw from in there would leave
        // the pose stack unbalanced over the live world, which is how the vehicle showcase broke in
        // 3.2.0 - so it is fenced off and one failure is enough to stop trying.
        graphics.pose().pushPose();
        try {
            GuiDraw.bigItem(graphics, gun, x + w / 2, y + h / 2, Math.min(w / 16.0F, h / 16.0F));
        } catch (Exception broken) {
            itemsBroken = true;
            MlumInventory.LOGGER.warn("[{}] a firearm's item renderer failed on the HUD; "
                    + "the card falls back to text for this session", MlumInventory.MODID, broken);
        } finally {
            graphics.pose().popPose();
        }
    }
}

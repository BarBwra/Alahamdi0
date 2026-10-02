package com.barbwra.mlum.client.gui;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.bag.ItemTier;
import net.minecraft.world.item.ItemStack;

/**
 * The palette, transcribed from {@code bag-ui.html}.
 *
 * <p>Every constant here is a CSS custom property from that mockup, converted to ARGB. The names
 * are kept close to the CSS so the two can be diffed by eye: {@code --amber} is {@link #AMBER},
 * {@code --line-soft} is {@link #LINE_SOFT}, and so on. When the mockup changes, this file is the
 * only place that has to follow.</p>
 *
 * <h2>Amber leads, bone reads</h2>
 * <p>The split that makes the mockup work is that the <b>accent and the text are different
 * colours</b>. {@link #AMBER} {@code #f0a93b} is structure - the bar before a panel heading, the
 * live tab, the selected border, a number that matters. {@link #BONE} {@code #ece6d4} is prose.
 * Nothing is both, which is why a screen this dense still has a clear first thing to look at.</p>
 *
 * <h2>Transparency is part of the design</h2>
 * <p>Panels are {@code rgba(11,13,10,.86)} and cards {@code rgba(22,26,19,.88)} because the world
 * has to stay visible behind the UI - a player must be able to see a zombie walking up while their
 * bag is open. Those alphas are the mockup's, and {@code panelOpacity} scales them rather than
 * replacing them, so the default is exactly the mockup and the config still tunes it.</p>
 */
public final class Theme {

    private Theme() {
    }

    /* ==================================================================== surfaces */

    /** {@code --page} - only ever seen through a fully opaque panel. */
    public static final int PAGE = 0xFF0B0D0A;
    /** {@code --panel-solid} - toasts and anything that must not let the world through. */
    public static final int PANEL_SOLID = 0xFF11140F;

    private static final int PANEL_RGB = 0x0B0D0A;
    private static final int CARD_RGB = 0x161A13;
    private static final int SLOT_RGB = 0x161A13;
    private static final int SLOT_HI_RGB = 0x1E2319;

    /** {@code rgba(11,13,10,.86)} and {@code rgba(22,26,19,.88)} as the mockup specifies them. */
    private static final int PANEL_ALPHA = 219;      // .86
    private static final int CARD_ALPHA = 224;       // .88

    /* ---------------------------------------------------------------- structure */

    /** {@code --line} / {@code --line-soft}. Borders, not fences: both are quiet by design. */
    public static final int LINE = 0xFF2B3026;
    public static final int LINE_SOFT = 0xFF1F231B;
    /** The 1px border a slot carries, and the two inset highlights that make it look punched in. */
    public static final int SLOT_BORDER = 0xFF272B22;
    public static final int SLOT_SHADOW = 0x80000000;    // inset 2px 2px rgba(0,0,0,.5)
    public static final int SLOT_SHEEN = 0x09FFFFFF;     // inset -1px -1px rgba(255,255,255,.035)

    /** Backpack cells, {@code .cell.pk} - a touch lighter than the base 27. */
    public static final int CELL_PACK = 0xFF191C14;
    public static final int CELL_PACK_BORDER = 0xFF343A29;

    /** A placed multi-cell item, {@code .bitem}, and its hover. */
    public static final int ITEM_BOX = 0xF21C2117;
    public static final int ITEM_BOX_HI = 0xF7242A1D;
    public static final int ITEM_BOX_BORDER = 0xFF3A4030;

    /* ==================================================================== text */

    /** {@code --bone} / {@code --soft} / {@code --muted} / {@code --faint}. */
    public static final int BONE = 0xFFECE6D4;
    public static final int SOFT = 0xFFD4CEBA;
    public static final int MUTED = 0xFFA19E8B;
    public static final int FAINT = 0xFF6E6D5F;
    /** For text printed on an amber fill - {@code #14110a} in the mockup. */
    public static final int INK = 0xFF14110A;

    /* ==================================================================== accent */

    /** {@code --amber} and its two companions. */
    public static final int AMBER = 0xFFF0A93B;
    public static final int AMBER_DIM = 0xFF8A6124;
    /** {@code --amber-glow}, {@code rgba(240,169,59,.13)}. */
    public static final int AMBER_GLOW = 0x21F0A93B;

    /* ==================================================================== semantic */

    public static final int RUST = 0xFFE0613F;
    public static final int SAGE = 0xFF93C46F;
    public static final int STEEL = 0xFF8FB0CC;
    public static final int FOOD = 0xFFD0913E;

    /** The drop preview, {@code .drop} and {@code .drop.bad}. */
    public static final int DROP_OK = 0x2993C46F;
    public static final int DROP_BAD = 0x29E0613F;

    /** The held-item ghost, {@code #ghost} - 55% opacity over a bone wash. */
    public static final int GHOST_FILL = 0x14ECE6D4;
    public static final int GHOST_BORDER = 0xFFECE6D4;

    /* ==================================================================== rarity */

    /**
     * The colour a stack's rarity line and name are drawn in.
     *
     * <p>The tiers themselves live in {@link ItemTier}, in the common package, because the server
     * decides them - they are read from config and travel in a packet. This is only the lookup the
     * renderer needs, so the client never holds a second copy of the mapping.</p>
     */
    public static int rarityColour(ItemStack stack) {
        return ItemTier.of(stack).colour();
    }

    /* ==================================================================== accessors */

    /** Any ARGB colour at an explicit alpha. */
    public static int at(int argb, int alpha) {
        return (alpha & 0xFF) << 24 | (argb & 0x00FFFFFF);
    }

    /** Scales an alpha by the configured panel opacity, so the config tunes rather than replaces. */
    private static int scaled(int rgb, int baseAlpha) {
        int alpha = baseAlpha * Math.max(0, Math.min(255, MlumConfig.panelOpacity())) / 255;
        return (alpha & 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    public static int panel() {
        return scaled(PANEL_RGB, PANEL_ALPHA);
    }

    public static int card() {
        return scaled(CARD_RGB, CARD_ALPHA);
    }

    public static int slot() {
        return scaled(SLOT_RGB, 255);
    }

    public static int slotHover() {
        return scaled(SLOT_HI_RGB, 255);
    }

    /**
     * The backdrop behind the whole UI.
     *
     * <p>Deliberately light, and capped: the spec is that the live world stays visible while the
     * bag is open, so this can never become vanilla's opaque scrim however the config is set.</p>
     */
    public static int backdrop() {
        // The world behind the menu must stay fully visible - the player has to be able to see a
        // zombie walking up while a menu is open. Panels carry their own translucency; nothing
        // darkens the screen behind them.
        return 0;
    }

    /**
     * The accent. Reads the config so a server can retheme, defaulting to the mockup's amber.
     *
     * <p>Everything amber in the UI goes through here rather than through {@link #AMBER} directly -
     * the constant is the mockup's value, this is the live one.</p>
     */
    public static int accent() {
        return MlumConfig.accentColor();
    }

    /** Accent at an arbitrary alpha, for washes and dim ticks. */
    public static int accent(int alpha) {
        return at(accent(), alpha);
    }

    /** The dim companion, {@code --amber-dim}: panel corner ticks and idle hover borders. */
    public static int accentDim() {
        int c = accent();
        return 0xFF000000
                | (((c >> 16) & 0xFF) * 58 / 100) << 16
                | (((c >> 8) & 0xFF) * 58 / 100) << 8
                | ((c & 0xFF) * 58 / 100);
    }

    /** A lighter sibling, for a highlight sitting on top of an accent fill. */
    public static int accentBright() {
        int c = accent();
        return 0xFF000000
                | lift((c >> 16) & 0xFF) << 16
                | lift((c >> 8) & 0xFF) << 8
                | lift(c & 0xFF);
    }

    private static int lift(int channel) {
        return Math.min(255, channel + (255 - channel) * 42 / 100);
    }

    /* ================================================================== migration */

    /**
     * The previous palette's names, re-pointed at the mockup's colours.
     *
     * <p><b>This block is a shim and is meant to die.</b> The bag screen is being rebuilt against
     * the tokens above; the quest, vehicle, level and faction screens are not part of that brief
     * and still speak the old vocabulary. Aliasing rather than editing five screens does two
     * useful things at once: the build never breaks halfway through the migration, and every one
     * of those screens picks up the amber palette for free instead of sitting on the old bone one
     * until somebody gets round to them.</p>
     *
     * <p>Delete an entry the moment its last caller is gone.</p>
     */
    public static final int TEXT = BONE;
    public static final int TEXT_DIM = MUTED;
    public static final int TEXT_MUTED = FAINT;

    public static final int BORDER = LINE;
    public static final int BORDER_HI = 0xFF3B4234;
    public static final int BORDER_SOFT = LINE_SOFT;
    public static final int RULE = LINE_SOFT;
    public static final int BEVEL = SLOT_SHEEN;
    public static final int EDGE = SLOT_SHEEN;
    public static final int SHADE = SLOT_SHADOW;
    public static final int SHADOW = SLOT_SHADOW;
    public static final int VOID = PAGE;

    public static final int SLOT_TOP = 0xFF161A13;
    public static final int SLOT_BOT = 0xFF12150E;
    public static final int SLOT_BEVEL = SLOT_SHEEN;
    public static final int SLOT_LOCKED_TOP = 0x66080605;
    public static final int SLOT_LOCKED_BOT = 0x660F0B09;

    public static final int SCANLINE = 0x14000000;
    public static final int GRIT_DARK = 0x30000000;
    public static final int GRIT_LIGHT = 0x14ECE6D4;

    public static final int HEALTH = RUST;
    public static final int HEALTH_HI = 0xFFEE8064;
    public static final int FOOD_HI = 0xFFE3AC5E;
    public static final int ARMOR = STEEL;
    public static final int ARMOR_HI = 0xFFB0C9E0;
    public static final int OK = SAGE;
    public static final int OK_HI = 0xFFB0D98C;
    public static final int EMBER = 0xFFF07B52;
    public static final int RUST_DEEP = 0xFF5E2C16;
    public static final int HAZARD = 0xFFC9A227;
    public static final int HAZARD_DARK = 0xFF1A1610;
    public static final int STEEL_DIM = 0xFF6E6357;

    public static final int TRACK = 0xFF1D2119;
    public static final int GAUGE_TRACK = 0x501F231B;

    public static int panelTop() {
        return panel();
    }

    public static int panelBottom() {
        return panel();
    }

    public static int plateTop() {
        return card();
    }

    public static int plateBottom() {
        return card();
    }

    public static int headerTop() {
        return scaled(0x151912, 240);
    }

    public static int headerBottom() {
        return scaled(0x0F120C, 240);
    }

    public static int shell() {
        return scaled(PANEL_RGB, 120);
    }

    public static int backdropTop() {
        return backdrop();
    }

    public static int backdropBottom() {
        return backdrop();
    }

    public static int accentDeep() {
        return accentDim();
    }

    public static int rust(int alpha) {
        return at(RUST, alpha);
    }
}

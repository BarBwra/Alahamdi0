package com.barbwra.mlum.warehouse.client.gui;

import com.barbwra.mlum.warehouse.WarehouseConfig;

/**
 * The palette. A hazard-lit loading bay after the lights went out.
 *
 * <p>Everything is drawn from these ARGB ints - the mod ships no GUI textures at all, which keeps
 * it resource-pack proof and makes the accent a one line config change.</p>
 *
 * <p><b>Warm, weathered, and lit from one bad light.</b> The previous palette was a cold cyan
 * terminal: clean, backlit, and completely wrong for this server. Surfaces here are oiled slate and
 * dark rust; text is bone rather than phosphor; the accent is hazard amber, the colour of paint on
 * a loading dock and warning tape on a door. Nothing glows because it is powered - things glow
 * because somebody pointed a work lamp at them.</p>
 *
 * <p><b>Red is reserved.</b> Cargo categories are gold, steel-blue and dusk-violet; blood red means
 * danger and only danger - the leak warning, an expiring deadline, spoiled stock. A red cargo tile
 * beside a red EXPOSED banner is unreadable at exactly the moment the player is under the most
 * pressure, so weapons are violet.</p>
 */
public final class Theme {

    private Theme() {
    }

    /* -------------------------------------------------------------- surfaces */

    /** Oiled slate, warm rather than blue-grey. */
    private static final int GLASS_TOP_RGB = 0x17140F;
    private static final int GLASS_BOT_RGB = 0x0B0908;
    private static final int HEADER_TOP_RGB = 0x2A231A;
    private static final int HEADER_BOT_RGB = 0x181310;

    public static final int BORDER = 0xFF3B332A;
    public static final int BORDER_HI = 0xFF6A5A44;
    public static final int BORDER_SOFT = 0xFF241E18;
    /** The printed rule under a header band. */
    public static final int RULE = 0xFF554631;
    public static final int BEVEL = 0x38A8895C;
    public static final int SHADOW = 0x88000000;
    public static final int TRACK = 0xC00A0806;

    /* ------------------------------------------------------------------- text */

    /** Bone and old paper, not phosphor. */
    public static final int TEXT = 0xFFDCD5C6;
    public static final int TEXT_DIM = 0xFF9C9382;
    public static final int TEXT_MUTED = 0xFF645B4D;

    /* --------------------------------------------------------------- semantic */

    /** Dried blood. Danger, and nothing else. */
    public static final int DANGER = 0xFFC43A2C;
    public static final int DANGER_DIM = 0xFF7A2E22;
    /** Hazard tape. */
    public static final int WARN = 0xFFE8901F;
    /** Field olive - go, safe, satisfied. */
    public static final int SUCCESS = 0xFF7FA653;

    /** Cargo hues, indexed by {@code CargoType.ordinal()}. Never red. */
    public static final int[] CARGO = {
            0xFFC9A94E,   // FOOD    - ration tin gold
            0xFF7FA9C4,   // MEDICAL - cold steel blue
            0xFF8E6A9E,   // WEAPON  - dusk violet
    };

    public static int cargo(int ordinal) {
        return CARGO[Math.max(0, Math.min(CARGO.length - 1, ordinal))];
    }

    /**
     * Upgrade path identity, indexed by {@code UpgradePath.ordinal()}.
     *
     * <p>These are the loudest colours in the mod, and deliberately so. Upgrades were previously
     * drawn in the accent or in muted grey, which put the one screen that spends the player's money
     * at the <i>bottom</i> of the visual hierarchy - players were not noticing it existed. Each path
     * now owns a saturated hue that appears nowhere else, so the tree reads as four distinct tracks
     * from across the room and a lit node is impossible to miss.</p>
     *
     * <p>They sit outside the weathered palette on purpose. This is the one place the UI is allowed
     * to look powered.</p>
     */
    public static final int[] PATH = {
            0xFFA6E83A,   // SPEED     - neon lime
            0xFFE8B53A,   // LOGISTICS - amber gold
            0xFFC8D2DC,   // ARMOUR    - bright steel
            0xFFE86ACB,   // INDUSTRY  - magenta
    };

    public static int path(int ordinal) {
        return PATH[Math.max(0, Math.min(PATH.length - 1, ordinal))];
    }

    /* ------------------------------------------------------------- accessors */

    private static int alpha(int rgb, int a) {
        return (a & 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    /**
     * The frosted panel fill.
     *
     * <p>Deliberately translucent. A solid slab that blanks the world is the single most dated
     * thing a Minecraft UI can do - it turns a heads-up terminal into a modal dialog and it hides
     * the zombie walking up behind you. Peripheral vision is a gameplay feature here, not a
     * concession.</p>
     */
    public static int glassTop() {
        return alpha(GLASS_TOP_RGB, WarehouseConfig.glassOpacity());
    }

    public static int glassBottom() {
        return alpha(GLASS_BOT_RGB, Math.min(255, WarehouseConfig.glassOpacity() + 24));
    }

    public static int headerTop() {
        return alpha(HEADER_TOP_RGB, Math.min(255, WarehouseConfig.glassOpacity() + 44));
    }

    public static int headerBottom() {
        return alpha(HEADER_BOT_RGB, Math.min(255, WarehouseConfig.glassOpacity() + 44));
    }

    /** Corner darkening behind the UI. Light enough that the world still reads through. */
    public static int vignette() {
        return (WarehouseConfig.vignetteOpacity() & 0xFF) << 24;
    }

    public static int accent() {
        return 0xFF000000 | WarehouseConfig.accent();
    }

    /** Accent at an arbitrary alpha, for washes and dim ticks. */
    public static int accent(int a) {
        return (a & 0xFF) << 24 | (WarehouseConfig.accent() & 0x00FFFFFF);
    }

    /** A lighter sibling of the accent, for highlights on top of accent fills. */
    public static int accentBright() {
        int c = WarehouseConfig.accent();
        int r = Math.min(255, ((c >> 16) & 0xFF) + 40);
        int g = Math.min(255, ((c >> 8) & 0xFF) + 44);
        int b = Math.min(255, (c & 0xFF) + 56);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The same colour at a different alpha. */
    public static int withAlpha(int argb, int a) {
        return (a & 0xFF) << 24 | (argb & 0x00FFFFFF);
    }

    /** Darkened toward black by {@code factor} 0..1. Used for glow underlays. */
    public static int darken(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) (((argb >> 16) & 0xFF) * (1.0F - factor));
        int g = (int) (((argb >> 8) & 0xFF) * (1.0F - factor));
        int b = (int) ((argb & 0xFF) * (1.0F - factor));
        return a << 24 | r << 16 | g << 8 | b;
    }
}

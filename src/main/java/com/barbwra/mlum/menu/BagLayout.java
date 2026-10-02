package com.barbwra.mlum.menu;

import com.barbwra.mlum.bag.BagConfig;

/**
 * The bag screen's geometry, transcribed from {@code bag-ui.html} at its own 1280x720 scale.
 *
 * <h2>Why this is a second layout class</h2>
 * <p>{@link MlumLayout} describes the 640x360 screen the mod shipped with, and the menu's slot
 * positions are baked against it on both sides of the network. Widening that class in place would
 * mean every unconverted screen moving at the same moment. This holds the new numbers, the bag
 * screen reads them, and the old ones retire as each screen is converted.</p>
 *
 * <h2>Right to left is a coordinate decision, not a text one</h2>
 * <p>The mockup is {@code direction:rtl}, so column 0 of the bag grid is the <b>rightmost</b> cell
 * and the first layout column sits against the right edge of the screen. Every {@code *X} method
 * here returns a left edge in ordinary screen space, having already done that flip - callers never
 * reason about direction, they just ask for column 3 and get a pixel.</p>
 */
public final class BagLayout {

    private BagLayout() {
    }

    /* ------------------------------------------------------------------ canvas */

    public static final int GUI_W = 1280;
    public static final int GUI_H = 720;

    /** {@code .topbar{height:64px}} and {@code .footer{height:36px}}. */
    public static final int TOPBAR_H = 64;
    public static final int FOOTER_H = 36;

    /** {@code .main{top:64;bottom:36;inset-inline:24;padding-block:16}}. */
    public static final int MARGIN_X = 24;
    public static final int MAIN_TOP = TOPBAR_H + 16;                       // 80
    public static final int MAIN_BOTTOM = GUI_H - FOOTER_H - 16;            // 668
    public static final int MAIN_H = MAIN_BOTTOM - MAIN_TOP;                // 588
    public static final int MAIN_W = GUI_W - MARGIN_X * 2;                  // 1232

    /* ------------------------------------------------------------------ columns */

    /**
     * {@code .inv{grid-template-columns:456px 1fr 424px;gap:22px}}.
     *
     * <p>In RTL the 456 column is on the right, so it is the bag; the 424 is on the left and holds
     * the weapons and the details panel. The centre is what is left: 1232 - 456 - 424 - 44 = 308.</p>
     */
    public static final int COL_GAP = 22;
    public static final int RIGHT_W = 456;
    public static final int LEFT_W = 424;
    public static final int CENTRE_W = MAIN_W - RIGHT_W - LEFT_W - COL_GAP * 2;   // 308

    public static final int RIGHT_X = GUI_W - MARGIN_X - RIGHT_W;           // 800
    public static final int CENTRE_X = RIGHT_X - COL_GAP - CENTRE_W;        // 470
    public static final int LEFT_X = MARGIN_X;                              // 24

    /* ------------------------------------------------------------------ panels */

    /**
     * {@code .panel{padding:12px 14px 14px; border:1px}} - the border is counted in, so content
     * starts 15px in from the panel's outer edge on the sides, 13 at the top and 15 at the bottom.
     * These are measured off the rendered mockup, not just read from the CSS.
     */
    public static final int PANEL_PAD_X = 15;
    public static final int PANEL_PAD_TOP = 13;
    public static final int PANEL_PAD_BOTTOM = 15;

    /**
     * {@code .phead{min-height:30px;padding-bottom:9px;border-bottom:1px;margin-bottom:12px}}.
     *
     * <p>Kept at the mockup's 30px even though the game's font is 8px tall rather than 13.5px. The
     * heading text is centred in the band instead of filling it, which keeps every panel's inner
     * content starting on exactly the pixel the mockup puts it on.</p>
     */
    public static final int PHEAD_H = 28;
    public static final int PHEAD_RULE_DY = PANEL_PAD_TOP + PHEAD_H + 8;    // 49
    public static final int PANEL_CONTENT_DY = PHEAD_RULE_DY + 13;          // 62

    /**
     * A heading whose aside has no pixel number in it (the details panel's) is shorter: the band
     * is its 30px minimum instead of the number's 37px line.
     */
    public static final int PHEAD_RULE_DY_COMPACT = 42;
    public static final int PANEL_CONTENT_DY_COMPACT = 55;

    /** The 12px corner ticks {@code .panel::after} paints on two opposite corners. */
    public static final int PANEL_TICK = 12;

    public static int panelContentTop(int panelY) {
        return panelY + PANEL_CONTENT_DY;
    }

    /* ------------------------------------------------------------- bag grid */

    /** {@code PITCH=44, CELLW=40, SEPH=26} and a 9-wide grid. */
    public static final int PITCH = 44;
    public static final int CELL = 40;
    public static final int SEP_H = 26;
    public static final int COLUMNS = BagConfig.COLUMNS;                    // 9
    public static final int GRID_W = COLUMNS * PITCH - (PITCH - CELL);      // 392

    public static final int BAG_PANEL_Y = MAIN_TOP;                         // 80
    /** {@code .bgrid{margin-inline-start:auto}} - in RTL that pins the grid to the left. */
    public static final int GRID_LEFT = RIGHT_X + PANEL_PAD_X;              // 814
    public static final int GRID_RIGHT = GRID_LEFT + GRID_W;                // 1206
    public static final int GRID_TOP = BAG_PANEL_Y + PANEL_CONTENT_DY;      // 144

    /**
     * {@code yOf(row) = row*PITCH + (row>=3 ? SEPH : 0)} - the separator's height falls between
     * the base 27 and the backpack rows, and every row below it is pushed down by exactly that.
     */
    public static int rowY(int row) {
        return row * PITCH + (row >= BagConfig.BASE_ROWS ? SEP_H : 0);
    }

    public static int cellY(int row) {
        return GRID_TOP + rowY(row);
    }

    /** Column 0 is the rightmost cell. */
    public static int cellX(int col) {
        return GRID_RIGHT - col * PITCH - CELL;
    }

    /** The pixel height a grid of {@code rows} rows occupies. */
    public static int gridHeight(int rows) {
        return rows <= 0 ? 0 : rowY(rows - 1) + CELL;
    }

    /** A multi-cell item's box: it spans from its first cell to the far edge of its last. */
    public static int itemWidth(int width) {
        return width * PITCH - (PITCH - CELL);
    }

    public static int itemHeight(int row, int height) {
        return rowY(row + height - 1) + CELL - rowY(row);
    }

    /** Left edge of an item whose leftmost column is {@code col + width - 1}. */
    public static int itemX(int col, int width) {
        return GRID_RIGHT - (col + width - 1) * PITCH - CELL;
    }

    public static int separatorY() {
        return GRID_TOP + BagConfig.BASE_ROWS * PITCH - 2;
    }

    public static int bagPanelHeight(int rows) {
        return PANEL_CONTENT_DY + gridHeight(rows) + PANEL_PAD_BOTTOM;
    }

    /** {@code .bhint} - the "wear a backpack" line under the grid: 10px margin plus one line. */
    public static final int HINT_H = 28;

    /** The bag panel as drawn: with the hint line added when no backpack is worn. */
    public static int bagPanelHeight(int rows, boolean hasPack) {
        return bagPanelHeight(rows) + (hasPack ? 0 : HINT_H);
    }

    /** Right edge of the right column's content box - where right-aligned content ends. */
    public static final int RIGHT_CONTENT_R = RIGHT_X + RIGHT_W - PANEL_PAD_X;         // 1242

    /* ---------------------------------------------------------- quick access */

    /** {@code .grid.g7} - seven 44px slots, 4px gaps, keys 3 to 9. Slot 0 is rightmost. */
    public static final int QUICK_SLOTS = 7;
    public static final int QUICK_GAP = 4;
    public static final int QUICK_W = QUICK_SLOTS * CELL + (QUICK_SLOTS - 1) * QUICK_GAP;  // 304
    public static final int QUICK_PANEL_H = PANEL_CONTENT_DY + CELL + PANEL_PAD_BOTTOM;

    /** Pinned to the bottom of the right column, per the brief: it never swaps with details. */
    public static int quickPanelY() {
        return MAIN_BOTTOM - QUICK_PANEL_H;
    }

    public static int quickCellX(int index) {
        return GRID_RIGHT - index * (CELL + QUICK_GAP) - CELL;
    }

    public static int quickCellY() {
        return quickPanelY() + PANEL_CONTENT_DY;
    }

    /*
     * The quick-access row as the mockup draws it.
     *
     * The two methods above are only the positions the menu bakes its slots at - a lookup key, not
     * where anything is drawn. The screen draws the row here instead: {@code .g7} slots are 44px,
     * the row is right-aligned in the panel, and the panel sits 14px under the bag panel, so it
     * moves up or down with the worn backpack exactly as the mockup's flex column does.
     */
    public static final int QUICK_CELL = 44;
    public static final int QUICK_ROW_W = QUICK_SLOTS * QUICK_CELL + (QUICK_SLOTS - 1) * QUICK_GAP;  // 332
    public static final int QUICK_PANEL_H2 = PANEL_CONTENT_DY + QUICK_CELL + PANEL_PAD_BOTTOM;        // 122

    public static int quickPanelTop(int rows, boolean hasPack) {
        return BAG_PANEL_Y + bagPanelHeight(Math.max(BagConfig.BASE_ROWS, rows), hasPack) + 14;
    }

    /** Slot 0 (key 3) is the rightmost, as in RTL. */
    public static int quickSlotLeft(int index) {
        return RIGHT_CONTENT_R - index * (QUICK_CELL + QUICK_GAP) - QUICK_CELL;
    }

    public static int quickSlotTop(int rows, boolean hasPack) {
        return quickPanelTop(rows, hasPack) + PANEL_CONTENT_DY;
    }

    /* ------------------------------------------------------------------ gear */

    /**
     * {@code .fig{grid-template-columns:56px 144px 56px;gap:14px}} centred in the middle column.
     *
     * <p>RTL again: the first {@code .eqcol} in the markup is the right-hand one, so helmet, pants
     * and offhand are on the right and vest, boots and the backpack on the left.</p>
     */
    public static final int FIG_COL_W = 56;
    public static final int FIG_BODY_W = 144;
    public static final int FIG_GAP = 14;
    public static final int FIG_W = FIG_COL_W * 2 + FIG_BODY_W + FIG_GAP * 2;   // 284
    public static final int FIG_H = 304;

    /** {@code .eq .slot{width:52px;height:52px}} and {@code .eq label} under it. */
    public static final int GEAR_SLOT = 52;
    public static final int GEAR_ROW_PITCH = 118;

    public static final int FIG_X = CENTRE_X + (CENTRE_W - FIG_W) / 2;      // 482
    public static final int FIG_LEFT_COL_X = FIG_X;                         // 482
    public static final int FIG_BODY_X = FIG_X + FIG_COL_W + FIG_GAP;       // 552
    public static final int FIG_RIGHT_COL_X = FIG_BODY_X + FIG_BODY_W + FIG_GAP;  // 710

    /**
     * The centre column is {@code align-self:center}, so the whole stack is centred vertically.
     *
     * <p>Figure, then nameplate, then vitals, with the mockup's 14px gaps between them.</p>
     */
    public static final int NAMEPLATE_H = 26;
    /** {@code .vital} rows are as tall as their 16px number at line-height 1.6; segments are 8px. */
    public static final int VITAL_ROW_H = 26;
    public static final int VITAL_SEG_H = 8;
    public static final int VITAL_GAP = 7;
    public static final int VITALS_H = VITAL_ROW_H * 3 + VITAL_GAP * 2;     // 92
    public static final int CENTRE_STACK_H = FIG_H + 14 + NAMEPLATE_H + 14 + VITALS_H;

    public static final int FIG_Y = MAIN_TOP + (MAIN_H - CENTRE_STACK_H) / 2;
    public static final int NAMEPLATE_Y = FIG_Y + FIG_H + 14;
    public static final int VITALS_Y = NAMEPLATE_Y + NAMEPLATE_H + 14;

    /** {@code .vitals{width:280px}}, rows of {@code 44px 1fr 26px}. */
    public static final int VITALS_W = 280;
    public static final int VITALS_X = CENTRE_X + (CENTRE_W - VITALS_W) / 2;
    public static final int VITAL_LABEL_W = 44;
    public static final int VITAL_VALUE_W = 26;
    public static final int VITAL_SEGMENTS = 20;

    /** Gear slot indices, in the order the menu builds armour: head, chest, legs, feet. */
    public static final int GEAR_HELMET = 0;
    public static final int GEAR_CHEST = 1;
    public static final int GEAR_LEGS = 2;
    public static final int GEAR_BOOTS = 3;
    public static final int GEAR_OFFHAND = 4;
    public static final int GEAR_BACKPACK = 5;

    /**
     * Where one of the six gear slots sits.
     *
     * <p>Right column top to bottom is helmet, pants, offhand; left column is vest, boots,
     * backpack. The 56px column is 4px wider than the 52px slot, so the slot is inset by 2.</p>
     */
    public static int gearSlotX(int gear) {
        boolean right = gear == GEAR_HELMET || gear == GEAR_LEGS || gear == GEAR_OFFHAND;
        int column = right ? FIG_RIGHT_COL_X : FIG_LEFT_COL_X;
        return column + (FIG_COL_W - GEAR_SLOT) / 2;
    }

    public static int gearSlotY(int gear) {
        int row = switch (gear) {
            case GEAR_HELMET, GEAR_CHEST -> 0;
            case GEAR_LEGS, GEAR_BOOTS -> 1;
            default -> 2;
        };
        return FIG_Y + row * GEAR_ROW_PITCH;
    }

    /* ------------------------------------------------------------- left column */

    /** {@code .gun{height:108px;grid-template-columns:40px 1fr 88px;gap:12px}}, two of them. */
    public static final int GUN_H = 108;
    public static final int GUN_GAP = 10;
    public static final int GUN_KEY_W = 40;
    public static final int GUN_ATT_W = 88;
    public static final int GUN_INNER_GAP = 12;
    public static final int GUN_PAD_X = 12;
    public static final int GUN_PAD_Y = 10;

    /** {@code .att{width:26px;height:26px}}, a 3x2 block with 5px gaps = 88x57. */
    public static final int ATT_CELL = 26;
    public static final int ATT_GAP = 5;
    public static final int ATT_COLS = 3;
    public static final int ATT_ROWS = 2;

    public static final int WEAPONS_PANEL_Y = MAIN_TOP;                     // 80
    public static final int WEAPONS_PANEL_H =
            PANEL_CONTENT_DY + GUN_H * 2 + GUN_GAP + PANEL_PAD_BOTTOM;

    public static final int GUN_X = LEFT_X + PANEL_PAD_X;
    public static final int GUN_W = LEFT_W - PANEL_PAD_X * 2;               // 396

    public static int gunY(int index) {
        return WEAPONS_PANEL_Y + PANEL_CONTENT_DY + index * (GUN_H + GUN_GAP);
    }

    /** RTL: the key column is on the right of the card, the attachment block on the left. */
    public static int gunKeyX(int index) {
        return GUN_X + GUN_W - GUN_PAD_X - GUN_KEY_W;
    }

    public static int gunAttX(int index) {
        return GUN_X + GUN_PAD_X;
    }

    /** RTL: mount 0 is the rightmost cell of the 3x2 block, as the mockup's grid flows. */
    public static int attCellX(int slot) {
        return gunAttX(0) + (ATT_COLS - 1 - slot % ATT_COLS) * (ATT_CELL + ATT_GAP);
    }

    /** {@code .gatt{align-content:center}} - the 57px block is centred in the card's 88px. */
    public static final int ATT_BLOCK_H = ATT_ROWS * ATT_CELL + (ATT_ROWS - 1) * ATT_GAP;       // 57

    public static int attCellY(int gun, int slot) {
        return gunY(gun) + GUN_PAD_Y + (GUN_H - GUN_PAD_Y * 2 - ATT_BLOCK_H) / 2
                + (slot / ATT_COLS) * (ATT_CELL + ATT_GAP);
    }

    /** The artwork column between the mounts and the key column: {@code .gart}. */
    public static final int GUN_ART_X = GUN_X + GUN_PAD_X + GUN_ATT_W + GUN_INNER_GAP;          // 150
    public static final int GUN_ART_R = GUN_X + GUN_W - GUN_PAD_X - GUN_KEY_W - GUN_INNER_GAP;  // 370
    public static final int GUN_ART_W = GUN_ART_R - GUN_ART_X;                                  // 220

    /* ---------------------------------------------------------------- details */

    /** {@code #inspect{min-height:150px}}, {@code .inspect{grid-template-columns:64px 1fr}}. */
    public static final int DETAILS_ICON = 64;
    public static final int DETAILS_GAP = 14;

    public static final int DETAILS_PANEL_Y = WEAPONS_PANEL_Y + WEAPONS_PANEL_H + 14;

    public static int detailsPanelH() {
        return MAIN_BOTTOM - DETAILS_PANEL_Y;
    }

    /** {@code #inspect{min-height:150px}} - the panel is content-sized above this. */
    public static final int DETAILS_MIN_H = 150;

    /* ---------------------------------------------------------------- footer */

    public static final int FOOTER_Y = GUI_H - FOOTER_H;
    public static final int FOOTER_TEXT_Y = FOOTER_Y + (FOOTER_H - 8) / 2;
}

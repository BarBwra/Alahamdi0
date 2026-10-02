package com.barbwra.mlum.menu;

/**
 * Single source of truth for every pixel in every screen.
 *
 * <p><b>Logical canvas.</b> 640x360 <i>logical</i> pixels, deliberately 16:9. Every screen scales
 * the canvas to fill the window and converts mouse coordinates back, so the UI covers a 16:9
 * display exactly at any GUI Scale. Keeping the canvas small is also what makes the cells big.</p>
 *
 * <p><b>Why it lives in the menu package.</b> Slot positions are baked into
 * {@link net.minecraft.world.inventory.Slot} on construction and {@code Slot.x/y} are final. The
 * menu is built on both sides, so the layout must be a pure function of the container row count -
 * never of the window size or a client setting.</p>
 *
 * <p><b>Two layouts, chosen by that row count.</b> With no container open the screen is the full
 * survival readout. With a chest open it becomes a looting screen: the chest and the player's own
 * inventory take the whole width, the vitals and the ground scanner are gone, and only the gear
 * that matters while looting stays - hotbar, firearms, worn equipment.</p>
 *
 * <pre>
 *  INVENTORY VIEW (rows = 0)                 CHEST VIEW (rows = 3 or 6)
 *              [Q][I][V]                                 [Q][I][V]
 *  +------+  +----------+  +-------------+   +--------------+  +--------------+
 *  |محيط  |  |   NAME   |  |  INVENTORY  |   |    CHEST     |  |  INVENTORY   |
 *  | [] x1|  | []  MODEL|  +-------------+   |  [ ][ ][ ]   |  |  [ ][ ][ ]   |
 *  | [] x3|  | []    [] |  |    QUICK    |   |  [ ][ ][ ]   |  +--------------+
 *  | ...  |  |       [] |  +-------------+   |              |  |    QUICK     |
 *  | 16   |  |          |  | HP  AR  FD  |   +--------------+  +--------------+
 *  | rows |  |    []    |  +-------------+                     [][][][][]  gear
 *  |      |  |  offhand |  | [1]  |  [2] |   |  [1]  |  [2]  |
 *  +------+  +----------+  +-------------+   +--------------+
 * </pre>
 *
 * <p><b>What moved in 3.3.0.</b> The scanner was 182px wide to show an 18px icon and a name, and
 * ran out of rows after 11 items; it is now 124px and 16 rows. The status panel was 276x146 for
 * three numbers and is now a 46px strip of three gauges side by side. The two firearm cards had to
 * leave the bottom left, because the scanner now takes that column's whole height - in the
 * inventory view they sit in the space the status panel gave up, which also makes them wide enough
 * that gun names stop being trimmed. While looting they stay under the chest, where nothing else
 * wants the room.</p>
 */
public final class MlumLayout {

    private MlumLayout() {
    }

    /* ------------------------------------------------------------------ global */

    public static final int GUI_W = 640;
    public static final int GUI_H = 360;

    /** Small grid cell, used only by the ground scanner list. */
    public static final int SLOT = 18;
    /** The standard cell: player inventory, quick access, and the chest grid. */
    public static final int INV_SLOT = 28;
    public static final int INV_INSET = (INV_SLOT - 16) / 2;         // 6
    public static final float INV_SCALE = 1.75F;
    /** Worn equipment: armour and offhand. */
    public static final int BIG = 36;
    public static final int BIG_INSET = (BIG - 16) / 2;              // 10
    public static final float BIG_SCALE = 2.0F;
    /** Firearm cell. */
    public static final int WEAPON = 48;
    public static final int WEAPON_INSET = (WEAPON - 16) / 2;        // 16
    public static final float WEAPON_SCALE = 2.5F;

    public static final int HEADER_H = 13;
    public static final int PAD = 4;
    public static final int GAP = 8;
    public static final int MARGIN = 8;

    public static final int TOP = 8;
    public static final int BOTTOM = 348;
    public static final int WATERMARK_Y = 351;

    /** True when a container is open, which is what selects the looting layout. */
    public static boolean chestView(int rows) {
        return rows > 0;
    }

    /* -------------------------------------------------------------- navigation */

    // Quest, Inventory, Vehicle, Level. Kept as a literal rather than NavTab.values().length
    // because this class is loaded on the dedicated server, where NavTab is not.
    /**
     * A row of labelled tabs along the top, not a cluster of icon buttons.
     *
     * <p>Four 14x14 pictograms asked the player to learn what a clipboard and a chevron meant. The
     * words are unambiguous, they cost nothing at this size, and an underline on the live one is a
     * far quieter way to say "you are here" than a filled accent box.</p>
     */
    // One per NavTab. Kept in step by hand because NavTab is a client class and this file is
    // loaded on the dedicated server, where it does not exist.
    public static final int NAV_COUNT = 5;
    public static final int NAV_BTN_W = 58;
    public static final int NAV_BTN_H = 18;
    public static final int NAV_GAP = 6;
    public static final int NAV_Y = 8;
    /** Left aligned after the server badge, the way a title bar reads. */
    public static final int NAV_X = 44;
    public static final int NAV_W = NAV_COUNT * NAV_BTN_W + (NAV_COUNT - 1) * NAV_GAP;

    /** The round server badge in the top left corner. */
    public static final int BADGE_X = 8;
    public static final int BADGE_Y = 6;
    public static final int BADGE = 22;
    /** The hairline under the whole tab row. */
    public static final int NAV_RULE_Y = 29;
    /** Everything below the tab row starts here. */
    public static final int CONTENT_TOP = 38;

    /* ------------------------------------------------- faction view: second row */

    /**
     * A second row of tabs, directly under the first, for the sections inside المنظمة.
     *
     * <p>The rows are told apart by <b>treatment, not position</b>: the top row marks its live tab
     * with an underline, this one with a filled chip. Two identical rows stacked would read as one
     * ten-button bar, and a player would have no way to tell which level of the menu they were
     * moving through.</p>
     *
     * <p>Aligned to {@link #NAV_X} so a section sits under the tab that owns it.</p>
     */
    public static final int SUB_NAV_X = 44;
    public static final int SUB_NAV_Y = 36;
    public static final int SUB_BTN_W = 68;
    public static final int SUB_BTN_H = 17;
    public static final int SUB_GAP = 5;
    /** The hairline under the second row. */
    public static final int SUB_RULE_Y = 57;
    /** Content inside a screen that has both rows starts here. */
    public static final int SUB_CONTENT_TOP = 62;

    public static int subButtonX(int index) {
        return SUB_NAV_X + index * (SUB_BTN_W + SUB_GAP);
    }

    /* ----------------------------------------------- inventory view: columns */

    public static final int LEFT_X = 8;
    public static final int LEFT_W = 124;                                // 8..132
    public static final int MID_X = 140;
    public static final int MID_W = 208;                                 // 140..348
    public static final int RIGHT_X = 356;
    public static final int RIGHT_W = 276;                               // 356..632

    public static final int MID_CX = MID_X + MID_W / 2;                  // 244

    public static int navButtonX(int index) {
        return NAV_X + index * (NAV_BTN_W + NAV_GAP);
    }

    /* ================================================================================
     * INVENTORY VIEW - the loadout layout.
     *
     * Slots are placed by naming a RECTANGLE and centring the real 16px slot inside it,
     * rather than by giving a square a size. That is what lets a firearm sit in a wide
     * 122x44 card and a helmet in a 96x46 one; the old single-size model could only
     * describe squares, which is why everything used to be square.
     * ============================================================================== */

    /** Index badge, then the card. Both firearm cards are primaries - neither is a sidearm. */
    public static final int GUN_IDX_X = 8;
    public static final int GUN_IDX_W = 14;
    public static final int GUN_CARD_X2 = 24;
    public static final int GUN_CARD_W2 = 122;
    public static final int GUN_CARD_H2 = 44;
    public static final int GUN_ROW_1 = CONTENT_TOP;                     // 38
    public static final int GUN_ROW_2 = 90;
    /** Where the weapon sits inside its card - right of the label, like the reference. */
    public static final int GUN_ITEM_DX = 92;

    public static int gunRowY(int index) {
        return index == 0 ? GUN_ROW_1 : GUN_ROW_2;
    }

    /**
     * Six attachment cells beside each firearm: three across, two down.
     *
     * <p>The shape is set by the gun card, not by taste. Six in a row would need 126px and the
     * column has 64 left; three down would stand 65px tall against a 44px card and collide with the
     * second firearm's row. 3x2 is 62x41 - inside the card's height, clear of the centre column,
     * and level with the weapon it belongs to, which is what makes it obvious whose mounts they
     * are.</p>
     */
    public static final int ATT_CELL = 20;
    public static final int ATT_COLS = 3;
    public static final int ATT_ROWS = 2;
    public static final int ATT_GAP = 1;
    public static final int ATT_X = 150;

    public static int attCellX(int slot) {
        return ATT_X + (slot % ATT_COLS) * (ATT_CELL + ATT_GAP);
    }

    /** {@code slot} is 0..5 within one gun's set; {@code gun} picks which card it sits beside. */
    public static int attCellY(int gun, int slot) {
        return gunRowY(gun) + (slot / ATT_COLS) * (ATT_CELL + ATT_GAP);
    }

    /** Now measured against the bag layout's 26px mounts rather than the old 20px ones. */
    public static int attSlotX(int slot) {
        return BagLayout.attCellX(slot) + (BagLayout.ATT_CELL - 16) / 2;
    }

    public static int attSlotY(int gun, int slot) {
        return BagLayout.attCellY(gun, slot) + (BagLayout.ATT_CELL - 16) / 2;
    }

    /**
     * The glider, in the gap the storage column leaves above the inventory grid.
     *
     * <p>The literal matches {@link #STORE_X}, which is declared further down - a static field
     * cannot read one below it without silently seeing zero.</p>
     */
    public static final int GLIDER_X = 375;
    public static final int GLIDER_Y = 48;
    public static final int GLIDER_W = 58;
    public static final int GLIDER_H = 40;

    public static int gliderSlotX() {
        return GLIDER_X + (GLIDER_W - 16) / 2;
    }

    public static int gliderSlotY() {
        return GLIDER_Y + (GLIDER_H - 16) / 2;
    }

    /** Worn gear: a 2x2 of wide cards under the firearms. */
    public static final int GEAR_CARD_W = 96;
    public static final int GEAR_CARD_H = 46;
    public static final int GEAR_COL_1 = 8;
    public static final int GEAR_COL_2 = 108;
    public static final int GEAR_ROW_1 = 154;
    public static final int GEAR_ROW_2 = 204;

    /** Armour index order is head, chest, legs, feet - matching the menu's slot order. */
    public static int gearCardX(int index) {
        return (index == 1 || index == 3) ? GEAR_COL_2 : GEAR_COL_1;
    }

    public static int gearCardY(int index) {
        return index < 2 ? GEAR_ROW_1 : GEAR_ROW_2;
    }

    public static final int OFFHAND_CARD_X = 8;
    public static final int OFFHAND_CARD_Y = 256;
    public static final int OFFHAND_CARD_W = 196;
    public static final int OFFHAND_CARD_H = 34;
    /** The offhand icon sits left, where a wide card has room for a label beside it. */
    public static final int OFFHAND_ITEM_DX = 28;

    /* ---- centre column ---- */
    public static final int CH_X = 214;
    public static final int CH_W = 156;
    public static final int CH_NAME_Y = 38;
    public static final int CH_XP_X = 222;
    public static final int CH_XP_Y = 50;
    public static final int CH_XP_W = 140;
    public static final int CH_XP_H = 3;
    public static final int CH_MODEL_CX = 292;
    public static final int CH_MODEL_FEET = 268;
    public static final int CH_MODEL_SCALE = 76;
    public static final int CH_CLIP_TOP = 60;
    public static final int CH_CLIP_BOTTOM = 296;

    /** Three boxed readouts, label over number, in place of three bars. */
    public static final int STAT_Y = 302;
    public static final int STAT_H = 34;
    public static final int STAT_W = 50;
    public static final int STAT_GAP = 3;

    public static int statX(int index) {
        return CH_X + index * (STAT_W + STAT_GAP);
    }

    /* ---- right column: storage. Centred vertically so there is no hole where the
            glider slot will eventually go. ---- */
    public static final int STORE_X = 375;
    public static final int STORE_LABEL_Y = 115;
    public static final int STORE_GRID_Y = 125;
    public static final int QUICK_LABEL_Y = 233;
    public static final int QUICK_GRID_Y = 243;
    public static final int CELL = 28;

    public static int storeCellX(int col) {
        return STORE_X + col * CELL;
    }

    public static int storeCellY(int row) {
        return STORE_GRID_Y + row * CELL;
    }

    /* ================================================================================
     * SLOT POSITIONS - the only thing the menu calls.
     *
     * Each branches on the container row count, because the looting layout keeps its old
     * arrangement while the inventory view uses the loadout one above. Centring a 16px
     * slot in a rectangle is the whole job.
     * ============================================================================== */

    private static int centreX(int x, int w) {
        return x + (w - 16) / 2;
    }

    private static int centreY(int y, int h) {
        return y + (h - 16) / 2;
    }

    /** Centres a 16px slot in a box of {@code size}. */
    private static int centreIn(int origin, int size) {
        return origin + (size - 16) / 2;
    }

    /**
     * Parked off-canvas.
     *
     * <p>Used for slots that still have to exist - the slot list is baked on both sides and a
     * conditionally absent slot desyncs instantly - but that the live layout has no place for.</p>
     */
    public static final int OFFSCREEN = -9000;

    /**
     * The base 27.
     *
     * <p><b>Off-canvas in the bag view, on purpose.</b> These are vanilla inventory slots 9..35 and
     * a {@code Slot}'s position is final, but in the bag they move: an item's cell is a saved
     * {@code (row, col)} that the player drags around. So the slots keep existing - shift-click,
     * {@code quickMoveStack} and every chest transfer still route through them by index - while the
     * <i>drawing and hit-testing</i> are done by the bag grid, which can move. See {@code BagStore}.</p>
     */
    public static int invSlotX(int rows, int col) {
        return chestView(rows) ? invGridX(invOriginX(rows), col) + INV_INSET : OFFSCREEN;
    }

    public static int invSlotY(int rows, int row) {
        return chestView(rows) ? invGridY(invOriginY(rows), row) + INV_INSET : OFFSCREEN;
    }

    public static int quickSlotX(int rows, int col) {
        return chestView(rows)
                ? quickCellX(rows, col) + INV_INSET
                : centreIn(BagLayout.quickCellX(col), BagLayout.CELL);
    }

    public static int quickSlotY(int rows) {
        return chestView(rows)
                ? quickOriginY(rows) + INV_INSET
                : centreIn(BagLayout.quickCellY(), BagLayout.CELL);
    }

    /** The firearm cell sits in the middle of its card, between the key column and the mounts. */
    public static int gunSlotX(int rows, int index) {
        return chestView(rows)
                ? gunFrameX(rows, index) + WEAPON_INSET
                : BagLayout.GUN_X + BagLayout.GUN_W / 2 - 8;
    }

    public static int gunSlotY(int rows, int index) {
        return chestView(rows)
                ? gunFrameY() + WEAPON_INSET
                : BagLayout.gunY(index) + BagLayout.GUN_H / 2 - 8;
    }

    /** Armour order is head, chest, legs, feet - matching the menu and {@code BagLayout}'s gear ids. */
    public static int armorSlotX(int rows, int index) {
        return chestView(rows)
                ? armorX(rows, index) + BIG_INSET
                : centreIn(BagLayout.gearSlotX(index), BagLayout.GEAR_SLOT);
    }

    public static int armorSlotY(int rows, int index) {
        return chestView(rows)
                ? armorY(rows, index) + BIG_INSET
                : centreIn(BagLayout.gearSlotY(index), BagLayout.GEAR_SLOT);
    }

    public static int offhandSlotX(int rows) {
        return chestView(rows)
                ? offhandX(rows) + BIG_INSET
                : centreIn(BagLayout.gearSlotX(BagLayout.GEAR_OFFHAND), BagLayout.GEAR_SLOT);
    }

    public static int offhandSlotY(int rows) {
        return chestView(rows)
                ? offhandY(rows) + BIG_INSET
                : centreIn(BagLayout.gearSlotY(BagLayout.GEAR_OFFHAND), BagLayout.GEAR_SLOT);
    }

    /**
     * The chest half of the looting layout. Declared here with the other column roots rather than
     * down in the chest section, because the firearm cards are sized from it and a static field
     * cannot read one declared below it - it would silently see zero.
     */
    public static final int CHEST_X = 8;
    public static final int CHEST_W = 300;                               // 8..308

    /* ------------------------------------------------------------ weapon cards */

    /**
     * The two firearm cards. Position depends on the layout, like {@link #armorX}: bottom right in
     * the inventory view, bottom left under the chest while looting.
     */
    public static final int GUN_CARD_Y = 258;
    public static final int GUN_CARD_H = BOTTOM - GUN_CARD_Y;            // 90
    public static final int GUN_CARD_GAP = 6;
    public static final int GUN_CARD_W = (RIGHT_W - GUN_CARD_GAP) / 2;   // 135
    public static final int CHEST_GUN_W = (CHEST_W - GUN_CARD_GAP) / 2;  // 147
    public static final int GUN_FRAME_DY = 20;
    public static final int GUN_LABEL_DY = 74;

    public static int gunCardW(int rows) {
        return chestView(rows) ? CHEST_GUN_W : GUN_CARD_W;
    }

    public static int gunCardX(int rows, int index) {
        int origin = chestView(rows) ? CHEST_X : RIGHT_X;
        return origin + index * (gunCardW(rows) + GUN_CARD_GAP);
    }

    public static int gunFrameX(int rows, int index) {
        return gunCardX(rows, index) + (gunCardW(rows) - WEAPON) / 2;
    }

    public static int gunFrameY() {
        return GUN_CARD_Y + GUN_FRAME_DY;
    }

    /** The chest capacity readout has to stop above the firearm cards. */
    public static final int CHEST_INFO_BOTTOM = GUN_CARD_Y - GAP;        // 250

    /* ---------------------------------------------- inventory view: scanner */

    /**
     * A grid of icons, not a list of names.
     *
     * <p>The list spent 182px of width showing an 18px icon next to a name, and still ran out after
     * 11 items. The same column as a grid holds <b>48</b> in 124px, because the name was the
     * expensive part and the name is what a tooltip is for. Cells are 24px, so the icons are also
     * bigger than the 18px ones they replace.</p>
     */
    public static final int VICINITY_CELL = 24;
    public static final float VICINITY_SCALE = 1.4F;
    public static final int VICINITY_PITCH = 25;
    public static final int VICINITY_COLS = 4;
    public static final int VICINITY_GRID_W = VICINITY_COLS * VICINITY_CELL;             // 96

    public static final int VICINITY_PANEL_Y = TOP;
    public static final int VICINITY_PANEL_H = BOTTOM - VICINITY_PANEL_Y;                // 340
    public static final int VICINITY_GRID_X = LEFT_X + (LEFT_W - VICINITY_GRID_W) / 2;   // 22
    public static final int VICINITY_LIST_Y = VICINITY_PANEL_Y + HEADER_H + PAD;         // 25
    /** One line at the foot of the panel: scan radius, total found, and the hidden count. */
    public static final int VICINITY_FOOTER_Y = VICINITY_PANEL_Y + VICINITY_PANEL_H - 12;   // 336

    /**
     * As many rows as the column holds, rather than a number picked by hand.
     *
     * <p>Deriving it means the grid follows the panel: nudge the panel or the cell size and it
     * refills the space instead of leaving a gap or overrunning the footer.</p>
     */
    public static final int VICINITY_GRID_ROWS =
            (VICINITY_FOOTER_Y - VICINITY_LIST_Y - 4) / VICINITY_PITCH;                  // 12
    public static final int VICINITY_ROWS = VICINITY_GRID_ROWS * VICINITY_COLS;          // 48

    public static int vicinityCellX(int index) {
        return VICINITY_GRID_X + (index % VICINITY_COLS) * VICINITY_CELL;
    }

    public static int vicinityCellY(int index) {
        return VICINITY_LIST_Y + (index / VICINITY_COLS) * VICINITY_PITCH;
    }

    /**
     * The scanner only exists in the inventory view.
     *
     * <p>Hiding it while a chest is open is the spec, and it also means the server stops scanning
     * the ground for a player who is busy looting - the scan is driven off the slot count.</p>
     */
    /**
     * The ground scanner is gone from the loadout layout.
     *
     * <p>The redesign has no column for it - the left side is firearms and worn gear now. Returning
     * zero here does more than hide it: the server's scan is driven off this slot count, so nothing
     * scans the ground for anybody any more. Give it a home in the layout and it comes back on.</p>
     */
    public static int vicinityRows(int containerRows) {
        return 0;
    }

    /* -------------------------------------------------- chest view: the chest */

    public static final int CHEST_Y = 42;
    public static final int CHEST_PAD = 8;
    public static final int CHEST_GRID_W = 9 * INV_SLOT;                 // 252
    public static final int CHEST_GRID_X = CHEST_X + (CHEST_W - CHEST_GRID_W) / 2;   // 32
    public static final int CHEST_GRID_Y = CHEST_Y + HEADER_H + CHEST_PAD;           // 63

    public static int chestPanelH(int rows) {
        return HEADER_H + CHEST_PAD + rows * INV_SLOT + CHEST_PAD;       // 113 or 197
    }

    /* ------------------------------------------- player inventory, both layouts */

    public static final int INV_ROWS = 3;
    public static final int INV_PAD = 8;
    public static final int INV_GRID_W = 9 * INV_SLOT;                               // 252

    public static final int QUICK_GRID_W = 7 * INV_SLOT;                             // 196

    /* inventory view: the right column */
    public static final int INV_PANEL_Y = TOP;                                       // 8
    public static final int INV_GRID_X = RIGHT_X + (RIGHT_W - INV_GRID_W) / 2;       // 368
    public static final int INV_GRID_Y = INV_PANEL_Y + HEADER_H + INV_PAD;           // 29
    public static final int INV_PANEL_H = HEADER_H + INV_PAD + INV_ROWS * INV_SLOT + INV_PAD;  // 113

    public static final int HOTBAR_PANEL_Y = INV_PANEL_Y + INV_PANEL_H + 12;         // 133
    public static final int HOTBAR_GRID_X = RIGHT_X + (RIGHT_W - QUICK_GRID_W) / 2;  // 396
    public static final int HOTBAR_GRID_Y = HOTBAR_PANEL_Y + HEADER_H + INV_PAD;     // 154
    public static final int HOTBAR_PANEL_H = HEADER_H + INV_PAD + INV_SLOT + INV_PAD;// 57

    /* chest view: the right half */
    public static final int PINV_X = 316;
    public static final int PINV_W = 316;                                            // 316..632
    public static final int PINV_Y = CHEST_Y;                                        // 42
    public static final int PINV_GRID_X = PINV_X + (PINV_W - INV_GRID_W) / 2;        // 348
    public static final int PINV_GRID_Y = PINV_Y + HEADER_H + INV_PAD;               // 63
    public static final int PINV_PANEL_H = INV_PANEL_H;                              // 113

    public static final int PQUICK_Y = PINV_Y + PINV_PANEL_H + 8;                    // 163
    public static final int PQUICK_GRID_X = PINV_X + (PINV_W - QUICK_GRID_W) / 2;    // 376
    public static final int PQUICK_GRID_Y = PQUICK_Y + HEADER_H + INV_PAD;           // 184
    public static final int PQUICK_PANEL_H = HOTBAR_PANEL_H;                         // 57

    /** Origins the menu uses, so both layouts build from one set of calls. */
    public static int invOriginX(int rows) {
        return chestView(rows) ? PINV_GRID_X : INV_GRID_X;
    }

    public static int invOriginY(int rows) {
        return chestView(rows) ? PINV_GRID_Y : INV_GRID_Y;
    }

    public static int quickOriginX(int rows) {
        return chestView(rows) ? PQUICK_GRID_X : HOTBAR_GRID_X;
    }

    public static int quickOriginY(int rows) {
        return chestView(rows) ? PQUICK_GRID_Y : HOTBAR_GRID_Y;
    }

    public static int quickCellX(int rows, int col) {
        return quickOriginX(rows) + col * INV_SLOT;
    }

    public static int invPanelX(int rows) {
        return chestView(rows) ? PINV_X : RIGHT_X;
    }

    public static int invPanelW(int rows) {
        return chestView(rows) ? PINV_W : RIGHT_W;
    }

    public static int invPanelY(int rows) {
        return chestView(rows) ? PINV_Y : INV_PANEL_Y;
    }

    public static int quickPanelY(int rows) {
        return chestView(rows) ? PQUICK_Y : HOTBAR_PANEL_Y;
    }

    /* ------------------------------------------------ inventory view: vitals */

    /**
     * Three gauges side by side in one strip, rather than three stacked rows in a 146px panel.
     *
     * <p>Health, armour and food are three small numbers. They did not need a third of the right
     * column, and the space they gave up is what the firearm cards moved into.</p>
     */
    public static final int VITALS_PANEL_Y = HOTBAR_PANEL_Y + HOTBAR_PANEL_H + 12;   // 202
    public static final int VITALS_PANEL_H = 46;                                     // 202..248
    public static final int VITALS_PAD = 12;
    public static final int VITALS_COLS = 3;
    public static final int VITALS_COL_GAP = 9;
    public static final int VITALS_INNER_W = RIGHT_W - VITALS_PAD * 2;               // 252
    public static final int VITALS_COL_W =
            (VITALS_INNER_W - (VITALS_COLS - 1) * VITALS_COL_GAP) / VITALS_COLS;     // 78
    public static final int VITALS_LABEL_Y = VITALS_PANEL_Y + HEADER_H + 5;          // 220
    public static final int VITALS_BAR_Y = VITALS_PANEL_Y + HEADER_H + 17;           // 232
    public static final int VITALS_BAR_H = 8;

    public static int vitalsColX(int index) {
        return RIGHT_X + VITALS_PAD + index * (VITALS_COL_W + VITALS_COL_GAP);
    }

    /* ------------------------------------------- inventory view: centre column */

    public static final int MID_Y = NAV_Y + NAV_BTN_H + GAP;       // 42
    public static final int MID_H = BOTTOM - MID_Y;                // 306

    public static final int NAME_Y = MID_Y + 3;                    // 45
    public static final int MID_DIV_Y = 62;

    /** The column gained 58px from the scanner, so the model gets to be bigger. */
    public static final int MODEL_FEET_Y = 262;
    public static final int MODEL_SCALE = 72;
    public static final int MODEL_CLIP_TOP = 70;
    public static final int MODEL_CLIP_BOTTOM = 264;

    /** Left flank: chestplate on top, boots underneath. */
    public static final int ARMOR_L_X = MID_X + 10;                      // 150
    /** Right flank: helmet on top, leggings underneath. */
    public static final int ARMOR_R_X = MID_X + MID_W - 10 - BIG;        // 302
    public static final int ARMOR_TOP_Y = 92;
    public static final int ARMOR_BOT_Y = 152;

    public static final int OFFHAND_X = MID_CX - BIG / 2;                // 226
    public static final int EQUIP_Y = 272;
    public static final int EQUIP_LABEL_Y = 312;

    /* ------------------------------------------------- chest view: gear strip */

    /** Helmet, chest, legs, boots, offhand in one row under the quick access panel. */
    public static final int GEAR_COUNT = 5;
    public static final int GEAR_GAP = 8;
    public static final int GEAR_W = GEAR_COUNT * BIG + (GEAR_COUNT - 1) * GEAR_GAP;  // 212
    public static final int GEAR_X = PINV_X + (PINV_W - GEAR_W) / 2;                  // 368
    public static final int GEAR_Y = PQUICK_Y + PQUICK_PANEL_H + 12;                  // 232
    public static final int GEAR_LABEL_Y = GEAR_Y + BIG + 3;                          // 271

    public static int gearX(int index) {
        return GEAR_X + index * (BIG + GEAR_GAP);
    }

    /**
     * Armour cell position. Index runs head, chest, legs, feet, matching the menu's slot order.
     *
     * <p>In the inventory view they flank the player model; while looting they line up in a strip,
     * because a chest screen is not the place for a character portrait.</p>
     */
    public static int armorX(int rows, int index) {
        if (chestView(rows)) {
            return gearX(index);
        }
        return (index == 0 || index == 2) ? ARMOR_R_X : ARMOR_L_X;
    }

    public static int armorY(int rows, int index) {
        if (chestView(rows)) {
            return GEAR_Y;
        }
        return index < 2 ? ARMOR_TOP_Y : ARMOR_BOT_Y;
    }

    public static int offhandX(int rows) {
        return chestView(rows) ? gearX(4) : OFFHAND_X;
    }

    public static int offhandY(int rows) {
        return chestView(rows) ? GEAR_Y : EQUIP_Y;
    }

    /* ------------------------------------------------------------ quest screen */

    public static final int QS_LEFT_X = 8;
    public static final int QS_LEFT_W = 306;
    public static final int QS_RIGHT_X = 322;
    public static final int QS_RIGHT_W = 310;
    public static final int QS_TOP = MID_Y;                              // 42

    public static final int QS_ACTIVE_H = 166;
    public static final int QS_COMPLETED_Y = QS_TOP + QS_ACTIVE_H + GAP; // 216
    public static final int QS_COMPLETED_H = BOTTOM - QS_COMPLETED_Y;    // 132
    public static final int QS_ROW_H = 14;
    public static final int QS_LIST_PAD = 5;

    public static final int QS_DETAIL_H = BOTTOM - QS_TOP;               // 306
    public static final int QS_DETAIL_PAD = 14;
    public static final int QS_TITLE_Y = QS_TOP + HEADER_H + 5;          // 60
    public static final int QS_PROGRESS_Y = QS_TOP + HEADER_H + 19;      // 74
    public static final int QS_DIV_Y = QS_TOP + HEADER_H + 35;           // 90
    public static final int QS_DESC_Y = QS_TOP + HEADER_H + 43;          // 98
    public static final int QS_LINE_H = 10;
    public static final int QS_REWARD_DIV_Y = 296;
    public static final int QS_REWARD_LABEL_Y = 302;
    public static final int QS_REWARD_Y = 314;
    public static final int QS_REWARD_CELL = 24;
    public static final int QS_REWARD_GAP = 6;

    public static final int QS_CLAIM_X = QS_RIGHT_X + QS_DETAIL_PAD;     // 336
    public static final int QS_CLAIM_Y = 298;
    public static final int QS_CLAIM_W = 84;
    public static final int QS_CLAIM_H = 14;

    public static int questActiveRows() {
        return (QS_ACTIVE_H - HEADER_H - QS_LIST_PAD - 4) / QS_ROW_H;
    }

    public static int questCompletedRows() {
        return (QS_COMPLETED_H - HEADER_H - QS_LIST_PAD - 4) / QS_ROW_H;
    }

    public static int questDescLines() {
        return (QS_REWARD_DIV_Y - QS_DESC_Y - 4) / QS_LINE_H;
    }

    /* ----------------------------------------------------------- vehicle screen */

    public static final int VS_TOP = MID_Y;                              // 42
    public static final int VS_H = BOTTOM - VS_TOP;                      // 306

    public static final int VS_LIST_X = 8;
    public static final int VS_LIST_W = 240;                             // 8..248
    public static final int VS_ROW_H = 26;
    public static final int VS_LIST_PAD = 5;

    public static final int VS_VIEW_X = 256;
    public static final int VS_VIEW_W = 376;                             // 256..632
    public static final int VS_VIEW_CX = VS_VIEW_X + VS_VIEW_W / 2;      // 444

    public static final int VS_NAME_Y = VS_TOP + HEADER_H + 6;           // 61
    public static final int VS_STAGE_TOP = 78;
    public static final int VS_STAGE_BOTTOM = 286;
    public static final int VS_STATUS_Y = 292;

    public static final int VS_BTN_W = 200;
    public static final int VS_BTN_H = 26;
    public static final int VS_BTN_X = VS_VIEW_X + VS_VIEW_W - 16 - VS_BTN_W;   // 416
    public static final int VS_BTN_Y = BOTTOM - 14 - VS_BTN_H;                  // 308

    public static int vehicleRows() {
        return (VS_H - HEADER_H - VS_LIST_PAD - 4) / VS_ROW_H;
    }

    /* ----------------------------------------------------------------- helpers */

    /** Scanner grid stepping (18px). */
    public static int gridX(int originX, int col) {
        return originX + col * SLOT;
    }

    public static int gridY(int originY, int row) {
        return originY + row * SLOT;
    }

    /** Standard grid stepping (28px) - inventory, quick access and the chest. */
    public static int invGridX(int originX, int col) {
        return originX + col * INV_SLOT;
    }

    public static int invGridY(int originY, int row) {
        return originY + row * INV_SLOT;
    }
}

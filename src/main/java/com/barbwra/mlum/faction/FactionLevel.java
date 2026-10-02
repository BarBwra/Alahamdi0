package com.barbwra.mlum.faction;

/**
 * The faction levelling curve, and the vault capacity it unlocks.
 *
 * <p>Pure arithmetic with no state, so the numbers can be reasoned about - and tested - without a
 * server. Every other class asks these methods rather than doing the sums itself, which is what
 * stops the vault screen and the vault container from ever disagreeing about how many slots exist.
 * That disagreement is the classic way a storage system eats items.</p>
 *
 * <p><b>The curve is linear.</b> Every level costs a flat 10,000 points more than the one before:
 * 10,000 for level 1, 20,000 for level 2, 30,000 for level 3, 40,000 for level 4, and so on. Points
 * are a lifetime total that is never spent, so a faction on 25,000 is level 2.</p>
 *
 * <p>Each level therefore takes the same amount of work as the last - the curve does not get harder
 * as a faction grows, it just keeps going.</p>
 *
 * <p><b>The vault.</b> Capacity is {@code 3 x (level + 1)} rows, poured into pages holding six rows
 * each - so a level 0 faction opens with a single half page and each level adds three more rows,
 * starting a new page whenever the current one fills. Pages past the first must also be bought and
 * wait out a timer; capacity only says how large a page is <i>allowed</i> to be.</p>
 */
public final class FactionLevel {

    private FactionLevel() {
    }

    /** The cost of one level. Flat: every level costs this much more than the one before. */
    public static final int BASE_REQUIREMENT = 10_000;

    /** A page can show six rows - a double chest - and no more. */
    public static final int ROWS_PER_PAGE = 6;

    /** Rows granted per level, and therefore the size of a freshly opened page. */
    public static final int ROWS_PER_LEVEL = 3;

    /** Slots in a row. Matches a vanilla container so the screen can reuse the same cell pitch. */
    public static final int COLUMNS = 9;

    /**
     * A hard ceiling on levels, so a faction cannot grow a vault the screen cannot draw or the save
     * file cannot sensibly hold. Reaching it is not expected; it exists so the maths is total.
     */
    public static final int MAX_LEVEL = 11;

    /* ------------------------------------------------------------------- the curve */

    /** Lifetime points needed to be at {@code level}: 0, 10k, 20k, 30k, 40k, ... */
    public static int requirementFor(int level) {
        if (level <= 0) {
            return 0;
        }
        return BASE_REQUIREMENT * Math.min(level, MAX_LEVEL);
    }

    /**
     * The points still to earn between {@code level} and the next one - always one step.
     *
     * <p>Returns {@link Integer#MAX_VALUE} at the ceiling so "can I level up" is a comparison that
     * is simply never true, rather than a special case every caller has to remember.</p>
     */
    public static int costOf(int level) {
        return level >= MAX_LEVEL ? Integer.MAX_VALUE : BASE_REQUIREMENT;
    }

    /** The level a faction with this many lifetime points has earned. */
    public static int levelFor(int points) {
        if (points < BASE_REQUIREMENT) {
            return 0;
        }
        return Math.min(points / BASE_REQUIREMENT, MAX_LEVEL);
    }

    /**
     * Progress through the current level, 0 to 1, for the level screen's bar.
     *
     * <p>Note the requirement is a threshold rather than a cost, so progress is measured from the
     * <i>previous</i> threshold - otherwise a faction that just levelled would show a bar already
     * half full.</p>
     */
    public static float progress(int points) {
        int level = levelFor(points);
        if (level >= MAX_LEVEL) {
            return 1.0F;
        }
        int floor = requirementFor(level);
        int ceiling = requirementFor(level + 1);
        if (ceiling <= floor) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (points - floor) / (float) (ceiling - floor)));
    }

    /* -------------------------------------------------------------------- the vault */

    /** Total vault rows a faction of this level has earned, across every page. */
    public static int totalRows(int level) {
        return ROWS_PER_LEVEL * (Math.max(0, Math.min(level, MAX_LEVEL)) + 1);
    }

    /** How many pages those rows reach into - the last one possibly only half open. */
    public static int pageCount(int level) {
        int rows = totalRows(level);
        return (rows + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE;
    }

    /**
     * Rows available on a given page, 1-based; 0 when the level has not reached that page yet.
     *
     * <p>This is the single source of truth for a page's size. The container sizes itself from it
     * and the screen draws from it, so a level-up widens both in the same tick.</p>
     */
    public static int rowsOnPage(int level, int page) {
        if (page < 1) {
            return 0;
        }
        int remaining = totalRows(level) - (page - 1) * ROWS_PER_PAGE;
        return Math.max(0, Math.min(ROWS_PER_PAGE, remaining));
    }

    /** Slots on a page, for sizing the container. */
    public static int slotsOnPage(int level, int page) {
        return rowsOnPage(level, page) * COLUMNS;
    }

    /**
     * Cost in {@code survival_instinct:money} to buy a page. The first page is free.
     *
     * <p>Linear rather than doubling - 500, 1000, 1500 - which is what keeps page four at 1500
     * rather than the 2000 a doubling curve would have charged.</p>
     */
    public static int pageCost(int page) {
        return page <= 1 ? 0 : 500 * (page - 1);
    }

    /** How long a bought page stays sealed before it opens. */
    public static long pageUnlockMillis() {
        return 24L * 60L * 60L * 1000L;
    }
}

package com.barbwra.mlum.bag;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * One section of the bag, and every rule about what may sit where in it.
 *
 * <h2>This is the authority</h2>
 * <p>The client draws a green or a red preview box, but it never decides anything. Every move
 * arrives at the server as a request and is re-tested here against the server's own copy of the
 * grid, because the alternative is a client that can claim an item fits where it does not - which
 * is a duplication bug, not a rendering one.</p>
 *
 * <h2>Rows are runtime, not config</h2>
 * <p>A section's height changes while the game is running: take the backpack off and the pack
 * section drops to zero rows. Every method therefore takes the row count as it is *now* rather than
 * caching it, and {@link #reflow} exists precisely because a layout that was legal yesterday can be
 * illegal today - after a config edit, or after a smaller backpack replaces a bigger one.</p>
 */
public final class BagGrid {

    private final BagSection section;
    private final List<BagEntry> entries = new ArrayList<>();
    private int rows;

    public BagGrid(BagSection section, int rows) {
        this.section = section;
        this.rows = Math.max(0, rows);
    }

    public BagSection section() {
        return section;
    }

    public int rows() {
        return rows;
    }

    public void setRows(int value) {
        this.rows = Math.max(0, value);
    }

    public List<BagEntry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int cells() {
        return rows * BagConfig.COLUMNS;
    }

    /** Cells actually taken, which is what the panel header's {@code used / cap} shows. */
    public int usedCells() {
        int used = 0;
        for (BagEntry entry : entries) {
            used += entry.size().cells();
        }
        return used;
    }

    /* ------------------------------------------------------------------ queries */

    @Nullable
    public BagEntry at(int row, int col) {
        for (BagEntry entry : entries) {
            if (entry.covers(row, col)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Whether a footprint of {@code size} at {@code row,col} is legal.
     *
     * <p>{@code ignore} is the entry being moved, and skipping it is what lets an item be nudged one
     * cell sideways: without it, every item would always collide with itself and no move within a
     * section could ever succeed.</p>
     */
    public boolean fits(int row, int col, ItemSize size, @Nullable BagEntry ignore) {
        return inBounds(row, col, size) && overlapping(row, col, size, ignore).isEmpty();
    }

    /**
     * Whether a footprint of {@code size} at {@code row,col} lies wholly inside this section.
     *
     * <p>The separator is a hard edge, not a gap to be spanned - see {@link BagSection} for why half
     * an item in the player and half in a droppable item is unsound.</p>
     */
    public boolean inBounds(int row, int col, ItemSize size) {
        return row >= 0 && col >= 0
                && col + size.width() <= BagConfig.COLUMNS
                && row + size.height() <= rows;
    }

    /**
     * Every entry a footprint of {@code size} at {@code row,col} would sit on top of.
     *
     * <p>{@link #fits} only ever wanted to know whether this list was empty. A drop needs to know
     * <i>what</i> is in the way as well: exactly one entry of the same item is a merge, exactly one
     * of a different item is a swap, and two or more is the only case that is genuinely refused.</p>
     */
    public List<BagEntry> overlapping(int row, int col, ItemSize size, @Nullable BagEntry ignore) {
        List<BagEntry> out = new ArrayList<>(2);
        for (BagEntry entry : entries) {
            if (entry == ignore) {
                continue;
            }
            ItemSize other = entry.size();
            boolean apart = col + size.width() <= entry.col()
                    || entry.col() + other.width() <= col
                    || row + size.height() <= entry.row()
                    || entry.row() + other.height() <= row;
            if (!apart) {
                out.add(entry);
            }
        }
        return out;
    }

    /** True when the footprint is free of everything except the one or two entries given. */
    public boolean freeExcept(int row, int col, ItemSize size, @Nullable BagEntry a, @Nullable BagEntry b) {
        if (!inBounds(row, col, size)) {
            return false;
        }
        for (BagEntry entry : overlapping(row, col, size, null)) {
            if (entry != a && entry != b) {
                return false;
            }
        }
        return true;
    }

    /**
     * What one stack of this item may hold.
     *
     * <p>Read from the stack rather than from the container, because this pack raises stack limits
     * well past 64 - money sits in stacks of 900 - and clamping to the vanilla 64 here would make the
     * bag refuse merges the player can plainly see should happen.</p>
     */
    public static int limit(ItemStack stack) {
        return Math.max(1, stack.getMaxStackSize());
    }

    /**
     * Whether {@code from} may be poured into {@code into}: same item, same tags, stackable, and
     * {@code into} not already full. The one test behind every merge, on both sides of the wire.
     */
    public static boolean canMerge(@Nullable ItemStack into, @Nullable ItemStack from) {
        return into != null && from != null && !into.isEmpty() && !from.isEmpty()
                && into.isStackable()
                && ItemStack.isSameItemSameTags(into, from)
                && into.getCount() < limit(into);
    }

    /** Scans row-major for the first legal spot. Null when the section has no room. */
    @Nullable
    public int[] firstFree(ItemSize size, @Nullable BagEntry ignore) {
        for (int row = 0; row + size.height() <= rows; row++) {
            for (int col = 0; col + size.width() <= BagConfig.COLUMNS; col++) {
                if (fits(row, col, size, ignore)) {
                    return new int[]{row, col};
                }
            }
        }
        return null;
    }

    public boolean hasRoomFor(ItemStack stack) {
        return firstFree(BagConfig.sizeOf(stack), null) != null;
    }

    /* ------------------------------------------------------------------ mutation */

    /** Places at an exact spot, or refuses. The server's answer to a drop request. */
    public boolean place(BagEntry entry, int row, int col) {
        if (!fits(row, col, entry.size(), entry)) {
            return false;
        }
        entry.moveTo(row, col);
        if (!entries.contains(entry)) {
            entries.add(entry);
        }
        return true;
    }

    /** Places anywhere it fits. Used when an item arrives from a chest or a quick-access slot. */
    public boolean add(ItemStack stack) {
        ItemSize size = BagConfig.sizeOf(stack);
        int[] spot = firstFree(size, null);
        if (spot == null) {
            return false;
        }
        entries.add(new BagEntry(stack, spot[0], spot[1]));
        return true;
    }

    public BagEntry addAt(ItemStack stack, int row, int col) {
        BagEntry entry = new BagEntry(stack, row, col);
        entries.add(entry);
        return entry;
    }

    public boolean remove(BagEntry entry) {
        return entries.remove(entry);
    }

    public void clear() {
        entries.clear();
    }

    /**
     * Reconciles a saved layout against the current rules, returning whatever could not be kept.
     *
     * <p>Three things can invalidate a layout between one session and the next: an admin edits
     * {@code item_sizes}, a smaller backpack replaces a bigger one, or the pack comes off entirely.
     * The brief's rule is followed exactly - an item that no longer fits is moved to the first free
     * spot, and if there is none it is handed back for the caller to drop at the player's feet.</p>
     *
     * <p>Order matters: everything illegal is lifted out <i>before</i> anything is re-placed, or an
     * item that was only displaced by a neighbour would fail to find the hole that neighbour was
     * about to vacate.</p>
     */
    public List<ItemStack> reflow() {
        List<BagEntry> displaced = new ArrayList<>();
        for (BagEntry entry : new ArrayList<>(entries)) {
            if (entry.isEmpty()) {
                entries.remove(entry);
                continue;
            }
            if (!fits(entry.row(), entry.col(), entry.size(), entry)) {
                entries.remove(entry);
                displaced.add(entry);
            }
        }

        List<ItemStack> overflow = new ArrayList<>();
        for (BagEntry entry : displaced) {
            int[] spot = firstFree(entry.size(), null);
            if (spot == null) {
                overflow.add(entry.stack());
                continue;
            }
            entry.moveTo(spot[0], spot[1]);
            entries.add(entry);
        }
        return overflow;
    }

    /**
     * Merges {@code stack} into matching entries that still have headroom.
     *
     * <p>Tried before {@link #add} on every incoming item, because a player shift-clicking a second
     * stack of bandages expects them to join the first rather than to consume another cell. Returns
     * whatever is left over.</p>
     */
    public ItemStack merge(ItemStack stack) {
        if (stack.isEmpty() || !stack.isStackable()) {
            return stack;
        }
        ItemStack remaining = stack;
        for (BagEntry entry : entries) {
            if (remaining.isEmpty()) {
                break;
            }
            ItemStack held = entry.stack();
            if (!ItemStack.isSameItemSameTags(held, remaining)) {
                continue;
            }
            int room = limit(held) - held.getCount();
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, remaining.getCount());
            held.grow(moved);
            remaining.shrink(moved);
        }
        return remaining;
    }
}

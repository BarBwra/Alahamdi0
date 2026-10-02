package com.barbwra.mlum.bag;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The base 27 cells: a <b>layout over the player's real inventory</b>, not storage of its own.
 *
 * <h2>Positions only</h2>
 * <p>The base section maps one-to-one onto vanilla inventory slots 9..35 - the same slots the menu
 * already builds {@code Slot}s for and the same ones a hopper, a death drop or any other mod sees.
 * So the only thing this class persists is <b>where each of those slots sits in the grid</b>. The
 * stacks stay exactly where Minecraft keeps them.</p>
 *
 * <p>Storing the stacks here instead would create a second inventory holding the same items: 27
 * slots invisible to everything outside this mod, two writers for one set of items, and a
 * duplication bug the first time a death handler wrote one copy and not the other. The backpack rows
 * are the opposite case - nothing in vanilla owns them - which is why {@link BackpackStore} does
 * store stacks.</p>
 *
 * <h2>Unpositioned slots get placed</h2>
 * <p>A slot with no saved position is new to the grid - it was just picked up, or the player has
 * never opened the bag. {@link #read} assigns it the first free spot, which is why a fresh player
 * sees a sensibly packed bag rather than an empty one with items nowhere.</p>
 */
public final class BagStore {

    private BagStore() {
    }

    private static final String KEY = "MlumBagLayout";

    /** Vanilla main-inventory slots, excluding the hotbar. */
    public static final int FIRST_SLOT = 9;
    public static final int LAST_SLOT = 35;

    /**
     * Builds the base grid from the player's inventory plus the saved positions.
     *
     * <p>Two passes on purpose. Everything with a remembered, still-legal position is placed first,
     * so a player's arrangement survives; only then is anything left over packed into the gaps. One
     * pass would let a newly picked-up item claim a cell that an existing item was about to be
     * restored to, and the player's layout would reshuffle itself on every pickup.</p>
     */
    public static BagGrid read(Player player) {
        BagGrid grid = new BagGrid(BagSection.BASE, BagConfig.BASE_ROWS);
        if (player == null) {
            return grid;
        }
        CompoundTag saved = player.getPersistentData()
                .getCompound(Player.PERSISTED_NBT_TAG)
                .getCompound(KEY);

        java.util.List<Integer> unplaced = new java.util.ArrayList<>();

        for (int slot = FIRST_SLOT; slot <= LAST_SLOT; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            String key = String.valueOf(slot);
            if (!saved.contains(key, Tag.TAG_INT_ARRAY)) {
                unplaced.add(slot);
                continue;
            }
            int[] pos = saved.getIntArray(key);
            if (pos.length != 2) {
                unplaced.add(slot);
                continue;
            }
            BagEntry entry = new BagEntry(stack, pos[0], pos[1], slot);
            if (grid.fits(pos[0], pos[1], entry.size(), null)) {
                grid.entries().add(entry);
            } else {
                unplaced.add(slot);
            }
        }

        for (int slot : unplaced) {
            ItemStack stack = player.getInventory().getItem(slot);
            BagEntry entry = new BagEntry(stack, 0, 0, slot);
            int[] spot = grid.firstFree(entry.size(), null);
            if (spot == null) {
                // No room for its footprint. The item is NOT lost - it is still in the vanilla slot,
                // it simply has nowhere to be drawn. The screen shows the overflow count instead.
                continue;
            }
            entry.moveTo(spot[0], spot[1]);
            grid.entries().add(entry);
        }
        return grid;
    }

    /** Persists just the positions, keyed by inventory slot index. */
    public static void write(Player player, BagGrid grid) {
        if (player == null) {
            return;
        }
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);

        CompoundTag layout = new CompoundTag();
        for (BagEntry entry : grid.entries()) {
            if (entry.isEmpty() || entry.ownedByPack()) {
                continue;
            }
            layout.putIntArray(String.valueOf(entry.source()),
                    new int[]{entry.row(), entry.col()});
        }
        if (layout.isEmpty()) {
            persisted.remove(KEY);
        } else {
            persisted.put(KEY, layout);
        }
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /**
     * Moves a stack between two vanilla slots, so a grid move can swap which slot an entry is.
     *
     * <p>Used when an item crosses the separator: base entries are slots and pack entries are NBT,
     * so crossing means the stack genuinely changes owner rather than just changing coordinates.</p>
     */
    public static boolean give(Player player, ItemStack stack) {
        return giveSlot(player, stack) >= 0;
    }

    /**
     * Puts {@code stack} in the first free base slot and says which one, or -1 when there is none.
     *
     * <p>The slot index is what a {@link BagEntry} needs to be bound to the stack it just became, so
     * a placement can be given an exact grid position in the same pass rather than being found again
     * by searching for a matching item - which cannot tell two identical stacks apart.</p>
     */
    public static int giveSlot(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return -1;
        }
        for (int slot = FIRST_SLOT; slot <= LAST_SLOT; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, stack);
                return slot;
            }
        }
        return -1;
    }

    public static void take(Player player, int slot) {
        if (slot >= FIRST_SLOT && slot <= LAST_SLOT) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
    }
}

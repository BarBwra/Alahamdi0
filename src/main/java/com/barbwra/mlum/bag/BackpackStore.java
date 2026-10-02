package com.barbwra.mlum.bag;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The contents of a backpack, stored inside the backpack {@link ItemStack} itself.
 *
 * <h2>Why the item and not the player</h2>
 * <p>The brief's rule is that the items belong to the <i>backpack</i>: drop it, lose it, or hand it
 * to somebody else and its contents go with it. The only storage that gives that behaviour for free
 * is the stack's own NBT - it is copied when the stack is copied, dropped when it is dropped, and
 * saved with whatever container it ends up in. Keeping the rows on the player instead would mean
 * writing code to chase the pack through every one of those transitions, and every one of those
 * paths is a place items can be duplicated or lost.</p>
 *
 * <h2>Not Survivor's Arsenal's storage</h2>
 * <p>This writes under our own {@value #ROOT} key rather than into whatever the backpack mod uses,
 * because that mod is not on the compile path and its format is not a contract we can rely on.
 * The consequence is worth stating plainly: <b>items already stored by that mod's own GUI are not
 * visible here</b>, which is exactly why the config disables that GUI by default. Both inventories
 * can physically coexist on one stack; only one of them should ever be reachable.</p>
 */
public final class BackpackStore {

    private BackpackStore() {
    }

    /** Namespaced so it can never collide with the backpack mod's own tags. */
    public static final String ROOT = "MlumBackpack";
    private static final String ITEMS = "Items";

    /* ------------------------------------------------------------------ reading */

    /**
     * Reads the pack's contents into a grid sized for the pack it came from.
     *
     * <p>Entries that do not fit - because the config changed, or because this NBT came off a
     * larger pack - are left to {@link BagGrid#reflow}, which the caller runs. Reading is
     * deliberately permissive; validating is a separate step with somewhere to put the overflow.</p>
     */
    public static BagGrid read(ItemStack backpack) {
        BagGrid grid = new BagGrid(BagSection.PACK, BagConfig.addedRows(backpack));
        if (backpack.isEmpty() || !backpack.hasTag()) {
            return grid;
        }
        CompoundTag tag = backpack.getTag();
        if (tag == null || !tag.contains(ROOT, Tag.TAG_COMPOUND)) {
            return grid;
        }
        ListTag list = tag.getCompound(ROOT).getList(ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            BagEntry entry = BagEntry.load(list.getCompound(i));
            if (!entry.isEmpty()) {
                grid.entries().add(entry);
            }
        }
        return grid;
    }

    /** Every item in the pack, ignoring positions. For drops, death and "is it empty". */
    public static List<ItemStack> contents(ItemStack backpack) {
        List<ItemStack> out = new ArrayList<>();
        for (BagEntry entry : read(backpack).entries()) {
            if (!entry.isEmpty()) {
                out.add(entry.stack());
            }
        }
        return out;
    }

    public static boolean hasContents(ItemStack backpack) {
        return !contents(backpack).isEmpty();
    }

    /* ------------------------------------------------------------------ writing */

    /**
     * Writes a grid back into the pack.
     *
     * <p>An empty grid <b>removes</b> the tag rather than writing an empty list. That keeps a fresh
     * backpack byte-identical to one that has been filled and emptied again, which matters more
     * than it sounds: stacking, {@code ItemStack.isSameItemSameTags} and every recipe that matches
     * on NBT all depend on two equivalent packs actually being equal.</p>
     */
    public static void write(ItemStack backpack, BagGrid grid) {
        if (backpack.isEmpty()) {
            return;
        }
        List<BagEntry> filled = new ArrayList<>();
        for (BagEntry entry : grid.entries()) {
            if (!entry.isEmpty()) {
                filled.add(entry);
            }
        }
        if (filled.isEmpty()) {
            clear(backpack);
            return;
        }
        ListTag list = new ListTag();
        for (BagEntry entry : filled) {
            list.add(entry.save());
        }
        CompoundTag root = new CompoundTag();
        root.put(ITEMS, list);
        backpack.getOrCreateTag().put(ROOT, root);
    }

    /** Drops the tag entirely, and the now-empty wrapper with it. */
    public static void clear(ItemStack backpack) {
        CompoundTag tag = backpack.getTag();
        if (tag == null) {
            return;
        }
        tag.remove(ROOT);
        if (tag.isEmpty()) {
            backpack.setTag(null);
        }
    }
}

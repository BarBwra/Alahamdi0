package com.barbwra.mlum.bag;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Where the worn backpack is kept.
 *
 * <h2>Why a persistent-data slot and not an inventory slot</h2>
 * <p>The vanilla player has exactly four armour slots and one offhand, all of them spoken for by
 * the gear the mockup already shows. There is no sixth slot to borrow. The options were a Forge
 * capability, a curio-style dependency, or a single {@link ItemStack} in the player's own persistent
 * data - and the third is the only one that needs no new dependency, survives death the same way
 * {@link Vip} does, and cannot be reached by another mod's inventory-scanning code and quietly
 * emptied.</p>
 *
 * <p>The cost is that the pack is not visible to anything that iterates the player's inventory,
 * which is the point: it is worn, not carried. Anything that needs to see it asks here.</p>
 *
 * <h2>Read-modify-write, always</h2>
 * <p>{@link #worn} returns a copy. Mutating a stack handed out by a getter and expecting the change
 * to persist is the classic NBT bug - the tag was deserialised into a fresh object and nothing is
 * watching it. Callers that change the pack's contents must {@link #setWorn} it back, and
 * {@link #mutate} exists so the common case cannot get that wrong.</p>
 */
public final class BackpackAccess {

    private BackpackAccess() {
    }

    private static final String KEY = "MlumWornBackpack";

    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    /**
     * Whether the Curios slot is doing the storing for this player.
     *
     * <p>When it is, the bag's backpack socket and the Curios {@code back} slot are <b>the same
     * slot</b> rather than two that are kept in step - so Curios renders the pack on the player's
     * back for free and there is no second copy anywhere to be pulled out and duplicated. The
     * persistent-data path below is the fallback for a pack without Curios.</p>
     */
    private static boolean viaCurios(Player player) {
        return com.barbwra.mlum.compat.CuriosBackpack.hasSlot(player);
    }

    /** The worn pack, as a copy. Empty when nothing is worn. */
    public static ItemStack worn(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        if (viaCurios(player)) {
            return com.barbwra.mlum.compat.CuriosBackpack.worn(player);
        }
        CompoundTag persisted = persisted(player);
        if (!persisted.contains(KEY)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(persisted.getCompound(KEY));
    }

    public static void setWorn(Player player, ItemStack stack) {
        if (player == null) {
            return;
        }
        if (viaCurios(player)) {
            com.barbwra.mlum.compat.CuriosBackpack.setWorn(player, stack);
            return;
        }
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (stack == null || stack.isEmpty()) {
            persisted.remove(KEY);
        } else {
            persisted.put(KEY, stack.save(new CompoundTag()));
        }
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /**
     * The tag the worn pack is saved in, or null. The same object until the pack is next written,
     * which is how the open menu notices a change without deserialising the pack every tick.
     */
    @javax.annotation.Nullable
    public static net.minecraft.nbt.Tag wornTag(Player player) {
        if (player == null) {
            return null;
        }
        if (viaCurios(player)) {
            return com.barbwra.mlum.compat.CuriosBackpack.wornTag(player);
        }
        return persisted(player).get(KEY);
    }

    /**
     * Moves a pack left in this mod's own storage into the Curios slot, once.
     *
     * <p>Everyone who was wearing a backpack before this changed has it - <b>and everything inside
     * it</b> - in the old place, where nothing will ever look again. Without this they all silently
     * lose it on the update. Runs on login; does nothing if the Curios slot already has something,
     * so it can never overwrite a pack the player put on themselves.</p>
     *
     * @return true when a pack was actually moved
     */
    public static boolean migrateToCurios(Player player) {
        if (player == null || !viaCurios(player)) {
            return false;
        }
        CompoundTag persisted = persisted(player);
        if (!persisted.contains(KEY)) {
            return false;
        }
        ItemStack old = ItemStack.of(persisted.getCompound(KEY));
        // clear the old home either way, so a pack that cannot move does not come back every login
        CompoundTag root = player.getPersistentData();
        persisted.remove(KEY);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
        if (old.isEmpty()) {
            return false;
        }
        if (!com.barbwra.mlum.compat.CuriosBackpack.worn(player).isEmpty()) {
            // the Curios slot is taken; hand the old one back rather than destroying it
            if (!player.getInventory().add(old)) {
                player.drop(old, false);
            }
            return true;
        }
        com.barbwra.mlum.compat.CuriosBackpack.setWorn(player, old);
        return true;
    }

    public static boolean isWearing(Player player) {
        return !worn(player).isEmpty();
    }

    /** Extra rows the worn pack grants. Zero when nothing is worn or the pack is unlisted. */
    public static int extraRows(Player player) {
        return BagConfig.addedRows(worn(player));
    }

    public static int capacity(Player player) {
        return BagConfig.BASE_CELLS + BagConfig.addedCells(worn(player));
    }

    /**
     * Reads the worn pack, hands it to {@code change}, and writes it back.
     *
     * <p>The only safe way to edit the pack's contents from outside this class.</p>
     */
    public static void mutate(Player player, java.util.function.Consumer<ItemStack> change) {
        ItemStack pack = worn(player);
        if (pack.isEmpty()) {
            return;
        }
        change.accept(pack);
        setWorn(player, pack);
    }

    /** The pack's contents grid, sized for whatever is worn. Empty grid when nothing is. */
    public static BagGrid readGrid(Player player) {
        return BackpackStore.read(worn(player));
    }

    public static void writeGrid(Player player, BagGrid grid) {
        mutate(player, pack -> BackpackStore.write(pack, grid));
    }
}

package com.barbwra.mlum.downed;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.compat.CuriosBackpack;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A downed body's belongings, laid out as a six-row chest so the bag's own chest screen can show
 * them beside the looter's bag.
 *
 * <pre>
 *   rows 1-3   their inventory (slots 9..35)
 *   row 4      their hotbar (0..8)
 *   row 5      helmet, chest, legs, boots, offhand, backpack, then spare cells
 *   row 6      spare cells
 * </pre>
 *
 * <p><b>Nothing is copied.</b> Every cell reads and writes straight through to the real slot on the
 * body, so an item taken here is gone from them at once and there is no second copy to dupe. The
 * backpack cell is the Curios back slot itself; taking the pack takes everything in it, because
 * the pack's rows live in the pack.</p>
 *
 * <p><b>The spare cells are real.</b> The chest screen lets anything be shift-clicked into any
 * cell, and a cell that silently refused would destroy what was put in it. So they are a small
 * buffer of their own, and whatever is left in it when the screen closes goes back to the looter.</p>
 */
public final class DownedLoot implements Container {

    public static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    /** Inventory indices for view cells 36..40: helmet, chest, legs, boots, offhand. */
    private static final int[] GEAR = {39, 38, 37, 36, 40};
    private static final int BACKPACK = 41;

    private final Entity body;
    private final SimpleContainer spare = new SimpleContainer(SIZE - 42);

    public DownedLoot(Entity body) {
        this.body = body;
    }

    /* ------------------------------------------------------------------ where a cell points */

    private ItemStack read(int view) {
        if (body instanceof Player player) {
            if (view == BACKPACK) {
                return CuriosBackpack.available() ? CuriosBackpack.worn(player) : ItemStack.EMPTY;
            }
            int slot = invSlot(view);
            return slot < 0 ? ItemStack.EMPTY : player.getInventory().getItem(slot);
        }
        if (body instanceof DownedDummy dummy) {
            return view < dummy.loot.size() ? dummy.loot.get(view) : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    private void write(int view, ItemStack stack) {
        if (body instanceof Player player) {
            if (view == BACKPACK) {
                if (CuriosBackpack.available()) {
                    CuriosBackpack.setWorn(player, stack);
                }
                return;
            }
            int slot = invSlot(view);
            if (slot >= 0) {
                player.getInventory().setItem(slot, stack);
            }
        } else if (body instanceof DownedDummy dummy && view < dummy.loot.size()) {
            dummy.loot.set(view, stack);
        }
    }

    /** View cell to player inventory index: main rows first, then the hotbar, then gear. */
    private static int invSlot(int view) {
        if (view < 27) {
            return 9 + view;
        }
        if (view < 36) {
            return view - 27;
        }
        if (view < 41) {
            return GEAR[view - 36];
        }
        return -1;
    }

    /* ------------------------------------------------------------------ Container */

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < SIZE; i++) {
            if (!getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return index >= 42 ? spare.getItem(index - 42) : read(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        if (index >= 42) {
            return spare.removeItem(index - 42, count);
        }
        ItemStack current = read(index);
        if (current.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = current.split(count);
        write(index, current.isEmpty() ? ItemStack.EMPTY : current);
        setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        if (index >= 42) {
            return spare.removeItemNoUpdate(index - 42);
        }
        ItemStack current = read(index);
        write(index, ItemStack.EMPTY);
        return current;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index >= 42) {
            spare.setItem(index - 42, stack);
            return;
        }
        write(index, stack);
        setChanged();
    }

    @Override
    public void setChanged() {
        if (body instanceof Player player) {
            player.getInventory().setChanged();
        }
    }

    /** The body is still down, still here, and the looter is still beside it and on their feet. */
    @Override
    public boolean stillValid(Player looter) {
        if (body.isRemoved() || !body.isAlive() || body.level() != looter.level()) {
            return false;
        }
        if (!DownedState.isDowned(body) || DownedState.isDowned(looter)) {
            return false;
        }
        double reach = MlumConfig.reviveRange() + 1.5D;
        return looter.distanceToSqr(body) <= reach * reach;
    }

    /** Whatever the looter left in the spare cells comes back to them. */
    @Override
    public void stopOpen(Player looter) {
        for (int i = 0; i < spare.getContainerSize(); i++) {
            ItemStack left = spare.removeItemNoUpdate(i);
            if (!left.isEmpty() && !looter.getInventory().add(left)) {
                looter.drop(left, false);
            }
        }
    }

    @Override
    public void clearContent() {
        // never: this is a window onto someone's belongings, not storage of its own
    }
}

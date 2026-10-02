package com.barbwra.mlum.menu;

import com.barbwra.mlum.bag.BackpackAccess;
import com.barbwra.mlum.bag.BagConfig;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A one-slot {@link Container} view onto the worn backpack.
 *
 * <p>The pack is stored in the player's persistent data rather than in any inventory - see
 * {@link BackpackAccess} for why - but a {@link net.minecraft.world.inventory.Slot} needs a
 * {@code Container}. This is the adapter: every read goes to the persistent data and every write
 * comes straight back out to it, so there is no second copy to fall out of step.</p>
 *
 * <p><b>Deliberately not caching.</b> An intermediate field would have to be invalidated whenever
 * the pack changed from outside the menu - a death, a command, the enforcer - and every one of those
 * is a path that would otherwise leave a stale pack rendered in the slot.</p>
 */
public class BackpackContainer implements Container {

    private final Player owner;

    public BackpackContainer(Player owner) {
        this.owner = owner;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return BackpackAccess.worn(owner).isEmpty();
    }

    @Override
    public ItemStack getItem(int index) {
        return index == 0 ? BackpackAccess.worn(owner) : ItemStack.EMPTY;
    }

    /**
     * Splitting a backpack makes no sense - it is a single item whose identity carries its contents.
     * Any partial take is therefore the whole thing.
     */
    @Override
    public ItemStack removeItem(int index, int count) {
        return removeItemNoUpdate(index);
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        if (index != 0) {
            return ItemStack.EMPTY;
        }
        ItemStack worn = BackpackAccess.worn(owner);
        BackpackAccess.setWorn(owner, ItemStack.EMPTY);
        return worn;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index == 0) {
            BackpackAccess.setWorn(owner, stack);
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {
        // Nothing to flush: every write already went to the player's persistent data.
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner;
    }

    /** Refused here as well as in the slot, so a hopper or another mod cannot bypass the rule. */
    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return index == 0 && BagConfig.isBackpack(stack);
    }

    @Override
    public void clearContent() {
        BackpackAccess.setWorn(owner, ItemStack.EMPTY);
    }
}

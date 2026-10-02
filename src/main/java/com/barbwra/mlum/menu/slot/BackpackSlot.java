package com.barbwra.mlum.menu.slot;

import com.barbwra.mlum.bag.BagConfig;
import com.barbwra.mlum.bag.Vip;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The worn backpack, in the bottom-left gear position.
 *
 * <h2>Two different refusals</h2>
 * <p>{@link #mayPlace} answers "no" for two quite different reasons and the screen needs to tell
 * them apart: a sword is simply not a backpack, whereas the desert pack <i>is</i> one and the player
 * just is not allowed it. {@link #rejection} exists so the click handler can raise the VIP toast for
 * the second case and stay silent for the first - a player dragging a sword over the slot does not
 * need to be told about VIP.</p>
 *
 * <h2>One at a time, and never nested</h2>
 * <p>A single slot enforces "only one backpack worn" structurally. Backpacks are also barred from
 * the bag's own backpack rows, but that is the grid's rule rather than this slot's, because those
 * cells are not slots - see {@code BagGrid}.</p>
 */
public class BackpackSlot extends Slot {

    /** Why a stack was refused. Null is not used; {@link Reason#OK} means it may go in. */
    public enum Reason {
        OK,
        /** Not a configured backpack at all. */
        NOT_A_BACKPACK,
        /** A real backpack, but VIP-only and this player is not VIP. */
        VIP_ONLY
    }

    private final Player owner;

    public BackpackSlot(Container container, int index, int x, int y, Player owner) {
        super(container, index, x, y);
        this.owner = owner;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    /** The full answer, for a caller that wants to explain itself. */
    public Reason rejection(ItemStack stack) {
        if (stack.isEmpty()) {
            return Reason.OK;
        }
        if (!BagConfig.isBackpack(stack)) {
            return Reason.NOT_A_BACKPACK;
        }
        if (!Vip.mayWear(owner, stack)) {
            return Reason.VIP_ONLY;
        }
        return Reason.OK;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return rejection(stack) == Reason.OK;
    }

    /**
     * Taking the pack off is always allowed.
     *
     * <p>Deliberately no confirmation and no emptying: the brief is explicit that the rows simply
     * disappear and their items stay inside the item. Because the contents live in the stack's NBT,
     * lifting it out of this slot carries them with it and there is nothing to migrate.</p>
     */
    @Override
    public boolean mayPickup(Player player) {
        return true;
    }

    /** Hidden from vanilla's 16px render pass while the bag screen draws it - see {@link SlotVisibility}. */
    @Override
    public boolean isActive() {
        return !SlotVisibility.hidden;
    }
}

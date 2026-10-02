package com.barbwra.mlum.menu.slot;

import com.barbwra.mlum.compat.TaczCompat;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One of the seven quick access cells (vanilla hotbar indices 2-8).
 *
 * <p>Guns are refused here on purpose: TACZ weapons belong in the two dedicated weapon cells, so
 * the loadout is unambiguous. This blocks placing and shift-clicking, and a server side sweep
 * relocates any gun that still lands here through some other route - vanilla will happily drop a
 * picked-up item into a hotbar slot without asking the menu.</p>
 */
public class QuickSlot extends Slot {

    public QuickSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !TaczCompat.isGun(stack);
    }

    /** Hidden from vanilla's 16px render pass while the bag screen draws it - see {@link SlotVisibility}. */
    @Override
    public boolean isActive() {
        return !SlotVisibility.hidden;
    }
}

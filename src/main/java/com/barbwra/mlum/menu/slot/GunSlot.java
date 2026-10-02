package com.barbwra.mlum.menu.slot;

import com.barbwra.mlum.compat.TaczCompat;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A weapon slot, rendered as a TACZ style gun card in the bottom right corner.
 *
 * <p>These are <b>real vanilla hotbar slots</b> (inventory index 0 and 1). That is the whole trick:
 * scrolling the mouse wheel and pressing 1 or 2 already select them, with zero extra code and no
 * risk of desyncing a parallel selection index. All this class adds is the placement restriction.</p>
 */
public class GunSlot extends Slot {

    private final int weaponIndex;

    public GunSlot(Container container, int index, int x, int y, int weaponIndex) {
        super(container, index, x, y);
        this.weaponIndex = weaponIndex;
    }

    /** 0 = primary (upper card), 1 = secondary (lower card). */
    public int getWeaponIndex() {
        return weaponIndex;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return TaczCompat.isGun(stack);
    }

    /** Hidden from vanilla's 16px render pass while the bag screen draws it - see {@link SlotVisibility}. */
    @Override
    public boolean isActive() {
        return !SlotVisibility.hidden;
    }
}

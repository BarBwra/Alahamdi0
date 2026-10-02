package com.barbwra.mlum.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

/** Offhand slot, centred directly under the 3D model. */
public class OffhandSlot extends Slot {

    public OffhandSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
        this.setBackground(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
    }

    /** Hidden from vanilla's 16px render pass while the bag screen draws it - see {@link SlotVisibility}. */
    @Override
    public boolean isActive() {
        return !SlotVisibility.hidden;
    }
}

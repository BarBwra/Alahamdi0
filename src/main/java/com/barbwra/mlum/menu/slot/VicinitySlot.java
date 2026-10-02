package com.barbwra.mlum.menu.slot;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.menu.VicinityContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One row of the ground item list. Read only by design: you can pull items out of the world,
 * but you cannot shove items into it (dropping stays on the Q key), which also means
 * {@code moveItemStackTo} will never accidentally shift-click your gear onto the floor.
 */
public class VicinitySlot extends Slot {

    private final VicinityContainer vicinity;
    private final int row;

    public VicinitySlot(VicinityContainer container, int index, int x, int y) {
        super(container, index, x, y);
        this.vicinity = container;
        this.row = index;
    }

    public int getRow() {
        return row;
    }

    public VicinityContainer getVicinity() {
        return vicinity;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return MlumConfig.allowVicinityPickup() && !getItem().isEmpty();
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }
}

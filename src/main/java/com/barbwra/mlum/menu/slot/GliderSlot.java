package com.barbwra.mlum.menu.slot;

import com.barbwra.mlum.MlumConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The glider cell.
 *
 * <p>Restricted by <b>item id from config</b> rather than by a class check, for the same reason
 * {@code TaczCompat} identifies guns that way: the mod never has to compile against the glider mod,
 * and a pack that swaps to a different one only edits a list. The defaults are the five paragliders
 * from {@code vc_gliders}.</p>
 *
 * <p>Note this only reserves the slot. The glider mod decides for itself whether a paraglider works
 * from here or has to be in a hand - if it only looks at held items, this becomes a tidy place to
 * keep one rather than a functional equip slot, and that is the glider mod's call to make.</p>
 */
public class GliderSlot extends Slot {

    public GliderSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && MlumConfig.gliderItemIds().contains(id.toString());
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    /** Hidden from vanilla's 16px render pass while the bag screen draws it - see {@link SlotVisibility}. */
    @Override
    public boolean isActive() {
        return !SlotVisibility.hidden;
    }
}

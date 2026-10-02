package com.barbwra.mlum.menu.slot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * One of the four split armour slots in the centre column. Same rules as the vanilla
 * {@code InventoryMenu} armour slots (single item, curse of binding, ghost icon), just placed
 * on the left/right flanks of the 3D model instead of a vertical strip.
 */
public class ArmorSlot extends Slot {

    private final EquipmentSlot equipment;
    private final Player owner;

    public ArmorSlot(Container container, int index, int x, int y,
                     EquipmentSlot equipment, Player owner, ResourceLocation emptyIcon) {
        super(container, index, x, y);
        this.equipment = equipment;
        this.owner = owner;
        this.setBackground(InventoryMenu.BLOCK_ATLAS, emptyIcon);
    }

    public EquipmentSlot getEquipmentSlot() {
        return equipment;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.canEquip(equipment, owner);
    }

    @Override
    public boolean mayPickup(Player player) {
        ItemStack stack = this.getItem();
        if (!stack.isEmpty() && !player.isCreative() && EnchantmentHelper.hasBindingCurse(stack)) {
            return false;
        }
        return super.mayPickup(player);
    }

    /** Hidden from vanilla's 16px render pass while the bag screen draws it - see {@link SlotVisibility}. */
    @Override
    public boolean isActive() {
        return !SlotVisibility.hidden;
    }
}

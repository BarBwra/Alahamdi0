package com.barbwra.mlum.bag;

/**
 * Which half of the grid a cell belongs to.
 *
 * <p>The two sections are stored in completely different places - the base 27 in the player's own
 * inventory, the backpack rows inside the backpack {@link net.minecraft.world.item.ItemStack} - so
 * "which section" is not a cosmetic distinction. It decides where an item is written, whether it
 * survives the backpack coming off, and who owns it if two players pass the pack between them.</p>
 *
 * <p>That is also why an item may never straddle the separator: half of it would be in the player
 * and half in an item they could drop.</p>
 */
public enum BagSection {

    /** The 27 cells every player always has. Stored in the player's inventory. */
    BASE,
    /** The rows the worn backpack adds. Stored in the backpack's own NBT. */
    PACK;

    public boolean isPack() {
        return this == PACK;
    }
}

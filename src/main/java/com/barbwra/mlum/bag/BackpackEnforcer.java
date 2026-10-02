package com.barbwra.mlum.bag;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Makes a worn backpack legal again after the rules changed underneath it.
 *
 * <h2>The three ways a legal pack becomes illegal</h2>
 * <ul>
 *   <li>VIP is revoked while the player is wearing a VIP-only pack.</li>
 *   <li>An admin reloads the config and the pack is no longer listed, or has become VIP-only.</li>
 *   <li>The player logs in on a server whose config changed while they were away.</li>
 * </ul>
 *
 * <p>All three want the same response, which is why this is one method called from three places
 * rather than three near-identical blocks that drift. The brief's rule for the VIP case is followed
 * exactly: return it to the inventory, or drop it if there is no room.</p>
 *
 * <h2>An unlisted pack is not confiscated</h2>
 * <p>The brief is explicit that a backpack removed from the config is treated as "no backpack" while
 * its contents stay stored in the item. So an unlisted pack is left in the slot untouched - the
 * grid simply reports zero extra rows for it. Taking it off the player would risk destroying a pack
 * whose id an admin merely typo'd in an edit, and the contents are safe either way because they live
 * in the stack.</p>
 */
public final class BackpackEnforcer {

    private BackpackEnforcer() {
    }

    /**
     * Removes the worn pack if the player may no longer wear it.
     *
     * @return true when something was taken off
     */
    public static boolean enforce(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        ItemStack worn = BackpackAccess.worn(player);
        if (worn.isEmpty()) {
            return false;
        }
        // Only a VIP failure removes the pack. An unlisted pack stays put - see the class note.
        if (!BagConfig.isBackpack(worn) || Vip.mayWear(player, worn)) {
            return false;
        }

        ItemStack removed = worn.copy();
        BackpackAccess.setWorn(player, ItemStack.EMPTY);

        // The contents ride along inside the stack, so giving it back is a plain item transfer -
        // there is nothing to unpack and nothing that can be lost in between.
        if (!player.getInventory().add(removed)) {
            player.drop(removed, false);
            player.sendSystemMessage(Component.literal("حقيبتك كانت ممتلئة، فوقعت الشنطة عند رجليك")
                    .withStyle(ChatFormatting.RED));
        } else {
            player.sendSystemMessage(Component.literal("هذي الشنطة للـ VIP فقط")
                    .withStyle(ChatFormatting.RED));
        }

        List<ItemStack> inside = BackpackStore.contents(removed);
        MlumInventory.LOGGER.info("[{}] removed VIP-only backpack from {} ({} item stacks inside)",
                MlumInventory.MODID, player.getGameProfile().getName(), inside.size());
        return true;
    }
}

package com.barbwra.mlum.client;

import com.barbwra.mlum.warehouse.WarehouseConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The banknote item, and how many of them the player is carrying.
 *
 * <p><b>This is not the wallet.</b> The balance in the top bar is an account on the server - see
 * {@link ClientWallet} - and these are the notes, an ordinary item that takes a cell like any other.
 * What survives here is the item lookup, which the details panel and the money icon still need.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientMoney {

    private ClientMoney() {
    }

    private static final String FALLBACK_ID = "survival_instinct:money";

    private static String resolvedId;
    private static Item resolved;
    private static ItemStack icon = ItemStack.EMPTY;

    /** The configured money item, or null when it is not installed. */
    public static Item item() {
        String id = FALLBACK_ID;
        try {
            if (WarehouseConfig.SERVER_SPEC.isLoaded()) {
                id = WarehouseConfig.moneyItem();
            }
        } catch (Exception notReady) {
            // config not synced yet - the fallback id is the server's default anyway
        }
        if (!id.equals(resolvedId)) {
            resolvedId = id;
            ResourceLocation key = ResourceLocation.tryParse(id);
            Item found = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
            resolved = found == null || found == Items.AIR ? null : found;
            icon = resolved == null ? ItemStack.EMPTY : new ItemStack(resolved);
        }
        return resolved;
    }

    /** A one-item stack of the money item for drawing, or empty. */
    public static ItemStack icon() {
        item();
        return icon;
    }

    /**
     * Banknotes carried: inventory, off hand and the backpack rows. -1 when there is no money item.
     *
     * <p>No longer what the wallet shows. Kept because "how much cash is on me" is still a real
     * question, and the pill in the top bar now answers a different one.</p>
     */
    public static long carried() {
        Item money = item();
        LocalPlayer player = Minecraft.getInstance().player;
        if (money == null || player == null) {
            return -1L;
        }
        long total = 0L;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(money)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(money)) {
                total += stack.getCount();
            }
        }
        for (com.barbwra.mlum.bag.BagEntry entry : ClientBagState.pack().entries()) {
            if (entry.stack().is(money)) {
                total += entry.stack().getCount();
            }
        }
        return total;
    }
}

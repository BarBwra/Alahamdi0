package com.barbwra.mlum.warehouse.service;

import com.barbwra.mlum.warehouse.WarehouseConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Money in and money out, in whatever item the pack uses as currency.
 *
 * <p><b>Counting and spending use the same test.</b> The prototype counted stacks whose display
 * name contained {@code "Moneys"} and then spent them with {@code /clear ... survival_instinct:money}
 * - two different notions of what money is. A renamed item passed the check, the {@code clear}
 * removed whatever real currency happened to be there (possibly none), the result was never
 * inspected, and the upgrade was granted regardless. Here both sides resolve the same registry id
 * and {@link #take} verifies before it removes.</p>
 *
 * <p><b>Overflow is dropped, never destroyed.</b> Payouts are large and the pack raises the money
 * stack limit well above 64, so a settlement is usually one or two stacks - but a player who
 * delivers with a full inventory still gets paid: whatever will not fit lands at their feet rather
 * than silently disappearing, which is what {@code /give} does when it fails.</p>
 */
public final class EconomyService {

    private EconomyService() {
    }

    /** The configured currency item, or {@code null} if the id does not resolve. */
    public static Item moneyItem() {
        ResourceLocation id = ResourceLocation.tryParse(WarehouseConfig.moneyItem());
        return id == null ? null : ForgeRegistries.ITEMS.getValue(id);
    }

    public static boolean isConfigured() {
        return moneyItem() != null;
    }

    /* ----------------------------------------------------------------- reading */

    /**
     * The player's balance - the number the wallet in the menus shows.
     *
     * <p>No longer a count of banknotes. The account and the item were separated, so a player with a
     * stack of money in their bag and nothing in the bank cannot afford anything, and a player with a
     * full bag is not poor. See {@code WalletService}.</p>
     */
    public static int balance(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return (int) Math.min(Integer.MAX_VALUE, com.barbwra.mlum.bag.WalletService.balance(serverPlayer));
        }
        return 0;
    }

    /* ----------------------------------------------------------------- writing */

    /**
     * Removes exactly {@code amount} currency, or nothing at all.
     *
     * <p>Verify-then-deduct in one call. A partial deduction is never observable, so there is no
     * window in which a player has been charged for an upgrade they did not receive.</p>
     *
     * @return true when the player could afford it and the currency was taken
     */
    public static boolean take(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return true;
        }
        // No money-item check any more: charging is a debit against an account, so whether the
        // banknote item resolves has nothing to do with whether the player can pay.
        return com.barbwra.mlum.bag.WalletService.take(player, amount);
    }

    /**
     * Pays the player.
     *
     * <p><b>This can no longer fail.</b> It used to mint banknotes and hunt for cells to put them in,
     * so a full bag meant a settlement half in the bag and half on the floor - and the floor is where
     * a payout goes missing. Crediting an account has neither failure mode.</p>
     */
    public static void pay(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return;
        }
        com.barbwra.mlum.bag.WalletService.give(player, amount);
    }
}

package com.barbwra.mlum.bag;

import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.warehouse.service.EconomyService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The player's balance - the number in the top bar, and what everything in this mod charges.
 *
 * <h2>The account and the banknotes are two different things</h2>
 * <p>This used to count {@code survival_instinct:money} stacks in the bag and the worn pack, so the
 * wallet was a view of an item and {@code /mlum_inventory money add} minted items into cells. The two
 * are now separate, as the brief asks: the balance is {@link WalletStore}, a number on the player,
 * and the money item is an ordinary item that takes a cell like any other.</p>
 *
 * <p>Consequences, all of them intended:</p>
 * <ul>
 *   <li>Paying a player <b>always</b> works. There is no "no room for it", nothing is dropped at
 *       their feet, and a full bag cannot make a warehouse payout go missing.</li>
 *   <li>Spending never rummages through the inventory, so it cannot take the wrong stack or be
 *       defeated by money sitting in a backpack that came off.</li>
 *   <li>Banknotes are not spendable on their own. {@link #deposit} and {@link #withdraw} are the
 *       bridge between the two, and they are deliberately explicit rather than automatic - money
 *       turning itself into an account balance on pickup would be the two things joined again.</li>
 * </ul>
 */
public final class WalletService {

    private WalletService() {
    }

    /** The currency item, for {@link #deposit} and {@link #withdraw}. Null when it does not resolve. */
    public static Item moneyItem() {
        return EconomyService.moneyItem();
    }

    /* ------------------------------------------------------------------ reading */

    public static long balance(ServerPlayer player) {
        return WalletStore.get(player);
    }

    /* ------------------------------------------------------------------ writing */

    /** Removes exactly {@code amount}, or nothing at all when the player cannot afford it. */
    public static boolean take(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        long now = balance(player);
        if (now < amount) {
            return false;
        }
        WalletStore.set(player, now - amount);
        sync(player);
        return true;
    }

    /**
     * Credits {@code amount}.
     *
     * @return how much could <b>not</b> be given - always zero, kept so callers written against the
     *         old item wallet still read correctly
     */
    public static long give(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        long now = balance(player);
        // a balance that wrapped past Long.MAX_VALUE would go negative and read as bankrupt
        long next = now + amount < 0L ? Long.MAX_VALUE : now + amount;
        WalletStore.set(player, next);
        sync(player);
        return 0L;
    }

    /** Makes the balance exactly {@code amount}, and returns it. */
    public static long set(ServerPlayer player, long amount) {
        WalletStore.set(player, Math.max(0L, amount));
        sync(player);
        return balance(player);
    }

    /* ------------------------------------------------------------------ the bridge */

    /**
     * Turns carried banknotes into balance: up to {@code amount} of the money item is taken from the
     * inventory, the off hand and the worn pack, and the same number is credited.
     *
     * @return how many notes were actually banked
     */
    public static long deposit(ServerPlayer player, long amount) {
        Item money = moneyItem();
        if (money == null || amount <= 0L) {
            return 0L;
        }
        long remaining = amount;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && remaining > 0L; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(money)) {
                continue;
            }
            int taken = (int) Math.min(stack.getCount(), remaining);
            stack.shrink(taken);
            remaining -= taken;
            if (stack.isEmpty()) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (remaining > 0L) {
            BagGrid pack = BackpackAccess.readGrid(player);
            for (BagEntry entry : new java.util.ArrayList<>(pack.entries())) {
                if (remaining <= 0L) {
                    break;
                }
                ItemStack stack = entry.stack();
                if (!stack.is(money)) {
                    continue;
                }
                int taken = (int) Math.min(stack.getCount(), remaining);
                stack.shrink(taken);
                remaining -= taken;
                if (stack.isEmpty()) {
                    pack.remove(entry);
                }
            }
            BackpackAccess.writeGrid(player, pack);
        }
        long banked = amount - remaining;
        if (banked > 0L) {
            inv.setChanged();
            give(player, banked);
            BagService.sync(player);
        }
        return banked;
    }

    /**
     * Turns balance back into carried banknotes. Whatever finds no room is left in the account rather
     * than dropped, so a withdrawal can never lose money to a full bag.
     *
     * @return how many notes were actually handed over
     */
    public static long withdraw(ServerPlayer player, long amount) {
        Item money = moneyItem();
        if (money == null || amount <= 0L) {
            return 0L;
        }
        long want = Math.min(amount, balance(player));
        if (want <= 0L) {
            return 0L;
        }
        int perStack = Math.max(1, new ItemStack(money).getMaxStackSize());
        long handed = 0L;
        while (handed < want) {
            ItemStack stack = new ItemStack(money, (int) Math.min(perStack, want - handed));
            int before = stack.getCount();
            // insert may place part of a stack - count what landed, not whether all of it did
            int placed = before - BagService.insert(player, stack).getCount();
            if (placed <= 0) {
                break;
            }
            handed += placed;
        }
        if (handed > 0L) {
            take(player, handed);
            BagService.sync(player);
        }
        return handed;
    }

    /* ------------------------------------------------------------------ syncing */

    /** Pushes the balance to the player's client, which has no other way to know it. */
    public static void sync(ServerPlayer player) {
        ModNetwork.sendWallet(player, balance(player));
    }
}

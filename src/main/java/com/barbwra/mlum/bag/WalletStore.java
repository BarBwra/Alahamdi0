package com.barbwra.mlum.bag;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * The player's balance: one number, on the player.
 *
 * <h2>Why a number and not the money item</h2>
 * <p>The wallet used to be a <i>count of money items</i> carried in the bag. That made one quantity
 * out of two different things: the banknotes are an item - droppable, tradeable, stealable, taking up
 * cells - and the balance in the top bar is an account. Anything that spent money had to find and
 * destroy items, anything that paid had to find room for them, and a full bag simply could not be
 * paid. They are separated here: this is the account, and the money item is now only an item.</p>
 *
 * <p>Stored in {@link Player#PERSISTED_NBT_TAG}, the same place the worn backpack and the VIP flag
 * live, so it survives death and respawn without a {@code SavedData} keyed by a UUID that changes
 * when a player is renamed.</p>
 */
public final class WalletStore {

    private WalletStore() {
    }

    private static final String KEY = "MlumWallet";

    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static long get(Player player) {
        if (player == null) {
            return 0L;
        }
        return Math.max(0L, persisted(player).getLong(KEY));
    }

    public static void set(Player player, long value) {
        if (player == null) {
            return;
        }
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putLong(KEY, Math.max(0L, value));
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }
}

package com.barbwra.mlum.bag;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The VIP flag, stored on the player so it survives relog and death.
 *
 * <h2>Why persistent data and not a save file</h2>
 * <p>{@link Player#getPersistentData()} is Forge's own per-player NBT and it is the one store that
 * already has the two properties this needs: it is written with the player, and - critically - it
 * survives death, because Forge copies the {@code PERSISTED} sub-tag across on respawn. A
 * {@code SavedData} keyed by UUID would work too but would need its own clone hook and its own
 * file; this is the same guarantee for none of the code.</p>
 *
 * <p>The tag lives under {@link Player#PERSISTED_NBT_TAG} specifically. Anything written to the
 * root of the persistent data is <b>lost on death</b>, which for a paid perk is the one failure
 * mode that generates angry tickets.</p>
 */
public final class Vip {

    private Vip() {
    }

    private static final String KEY = "MlumVip";

    public static boolean isVip(Player player) {
        if (player == null) {
            return false;
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return persisted.getBoolean(KEY);
    }

    /**
     * Sets or clears the flag.
     *
     * @return true when the value actually changed, so the caller can skip a pointless message
     */
    public static boolean set(ServerPlayer player, boolean value) {
        if (player == null) {
            return false;
        }
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(KEY) == value) {
            return false;
        }
        if (value) {
            persisted.putBoolean(KEY, true);
        } else {
            // Removed rather than set false, so a player who was never VIP and one who had it
            // revoked are byte-identical. Stops the tag accumulating on every player on the server.
            persisted.remove(KEY);
        }
        root.put(Player.PERSISTED_NBT_TAG, persisted);
        return true;
    }

    /**
     * Whether this player may wear this backpack.
     *
     * <p>The only gate is VIP-only packs. Everything else about whether a stack belongs in the slot
     * is {@link BagConfig#isBackpack}'s business, and keeping the two apart is what lets the slot
     * reject "not a backpack" and "not yours" with different messages.</p>
     */
    public static boolean mayWear(Player player, net.minecraft.world.item.ItemStack backpack) {
        return !BagConfig.isVipOnly(backpack) || isVip(player);
    }
}

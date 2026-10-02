package com.barbwra.mlum.util;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Carries a player's saved data across the rename from Tactical Inventory to MlumInventory.
 *
 * <p>The mod id is part of every persistent key, so renaming it would otherwise orphan every
 * player's quest board and garage - the data would still be on disk, under names nothing reads any
 * more. This copies each key across once, on login, before anything is synced.</p>
 *
 * <p>It is idempotent: a key is copied only when the new name is absent, and the old one is removed
 * afterwards, so a second login does nothing and cannot overwrite newer data with stale data.
 * Keys belonging to the deleted map and team systems are dropped rather than carried.</p>
 */
public final class LegacyMigration {

    private LegacyMigration() {
    }

    private static final String[][] CARRY = {
            {"tacinv:quests", "mlum:quests"},
            {"tacinv:quests_claimed", "mlum:quests_claimed"},
            {"tacinv:vehicles", "mlum:vehicles"},
            {"tacinv:vehicle_active", "mlum:vehicle_active"},
            {"tacinv:vehicle_cooldown", "mlum:vehicle_cooldown"},
    };

    /** Data from systems that no longer exist. Removed so it stops riding along in every save. */
    private static final String[] DROP = {
            "tacinv:markers", "tacinv:hidden_markers",
            "tacinv:team", "tacinv:team_name", "tacinv:team_role",
    };

    public static void run(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);

        int carried = 0;
        for (String[] pair : CARRY) {
            if (!persisted.contains(pair[0])) {
                continue;
            }
            if (!persisted.contains(pair[1])) {
                persisted.put(pair[1], persisted.get(pair[0]).copy());
                carried++;
            }
            persisted.remove(pair[0]);
        }
        for (String key : DROP) {
            persisted.remove(key);
        }

        if (carried > 0) {
            MlumInventory.LOGGER.info("[{}] migrated {} saved entries for {} from the old mod id",
                    MlumInventory.MODID, carried, player.getGameProfile().getName());
        }
    }
}

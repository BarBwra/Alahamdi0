package com.barbwra.mlum.warehouse.service;

import com.barbwra.mlum.warehouse.core.Warehouse;
import com.barbwra.mlum.warehouse.data.WarehouseData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Who owns a warehouse.
 *
 * <p><b>The mod no longer decides who is allowed one.</b> There is no claim command, no quest flag
 * and no eligibility check in here - Skript owns that entirely. When a player finishes whatever
 * Skript decides the requirement is, the console runs:</p>
 *
 * <pre>{@code /mwh terminal <player>}</pre>
 *
 * <p>and {@link #getOrCreate} registers the warehouse on the spot if they do not already have one.
 * One command is the whole integration surface.</p>
 *
 * <p>This replaced a two-step flow - a quest flag written by one command, then a separate claim by
 * the player - which had a failure mode with no recovery: a player told to run {@code /mwh claim}
 * who never ran it simply had no warehouse, and nothing in the game told them why. Creating on
 * first open removes the step that could be skipped.</p>
 *
 * <p>Note what creating a warehouse does <i>not</i> do: it allocates no world, loads no chunks and
 * copies no region files. A warehouse is a row of state. The Skript prototype cloned an entire
 * dimension per player and never unloaded any of them, which is the single largest reason it could
 * not scale.</p>
 */
public final class WarehouseService {

    private WarehouseService() {
    }

    /** The player's warehouse, or {@code null} if they have never been given one. */
    public static Warehouse of(MinecraftServer server, Player player) {
        return WarehouseData.get(server).warehouse(player.getUUID());
    }

    public static boolean owns(MinecraftServer server, Player player) {
        return WarehouseData.get(server).hasWarehouse(player.getUUID());
    }

    /**
     * Returns the player's warehouse, creating one if this is their first time.
     *
     * <p>Called from the terminal command, so the act of an operator opening the terminal for
     * somebody <i>is</i> the act of granting them a warehouse.</p>
     */
    public static Warehouse getOrCreate(MinecraftServer server, ServerPlayer player) {
        WarehouseData data = WarehouseData.get(server);
        Warehouse warehouse = data.warehouse(player.getUUID());
        if (warehouse == null) {
            warehouse = data.createWarehouse(player.getUUID(),
                    player.getGameProfile().getName(), System.currentTimeMillis());
        }
        return warehouse;
    }

    /** Keeps the cached display name fresh, so admin listings do not show stale names. */
    public static void touch(MinecraftServer server, ServerPlayer player) {
        Warehouse warehouse = of(server, player);
        if (warehouse != null) {
            warehouse.setOwnerName(player.getGameProfile().getName());
        }
    }
}

package com.barbwra.mlum.admin;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Watching a player through their own eyes, and going back to exactly where you were.
 *
 * <p>Vanilla spectator mode already knows how to ride another player's camera; this remembers where
 * the admin stood and what mode they were in, puts them in spectator, hands them the camera, and
 * undoes all of it when they stop - or when the one they watch logs off.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class Spectate {

    private Spectate() {
    }

    private record Back(ResourceKey<Level> dim, Vec3 pos, float yRot, float xRot, GameType mode, UUID target) {
    }

    private static final Map<UUID, Back> WATCHING = new HashMap<>();

    public static boolean watching(ServerPlayer admin) {
        return WATCHING.containsKey(admin.getUUID());
    }

    public static void start(ServerPlayer admin, ServerPlayer target) {
        if (admin == target) {
            return;
        }
        if (!WATCHING.containsKey(admin.getUUID())) {
            WATCHING.put(admin.getUUID(), new Back(admin.level().dimension(), admin.position(), admin.getYRot(),
                    admin.getXRot(), admin.gameMode.getGameModeForPlayer(), target.getUUID()));
        } else {
            Back b = WATCHING.get(admin.getUUID());
            WATCHING.put(admin.getUUID(), new Back(b.dim, b.pos, b.yRot, b.xRot, b.mode, target.getUUID()));
        }
        admin.setGameMode(GameType.SPECTATOR);
        if (admin.level() != target.level()) {
            admin.teleportTo((ServerLevel) target.level(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
        }
        admin.setCamera(target);
    }

    public static void stop(ServerPlayer admin) {
        Back b = WATCHING.remove(admin.getUUID());
        if (b == null) {
            return;
        }
        admin.setCamera(admin);
        ServerLevel level = admin.server.getLevel(b.dim);
        if (level != null) {
            admin.teleportTo(level, b.pos.x, b.pos.y, b.pos.z, b.yRot, b.xRot);
        }
        admin.setGameMode(b.mode == GameType.CREATIVE && !Staff.isOp(admin) ? GameType.SURVIVAL : b.mode);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || WATCHING.isEmpty() || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        for (UUID id : WATCHING.keySet().toArray(new UUID[0])) {
            ServerPlayer admin = event.getServer().getPlayerList().getPlayer(id);
            Back b = WATCHING.get(id);
            if (admin == null || b == null) {
                WATCHING.remove(id);
                continue;
            }
            ServerPlayer target = event.getServer().getPlayerList().getPlayer(b.target);
            if (target == null) {
                stop(admin);
            } else if (admin.getCamera() != target) {
                // pressing shift in spectator lets go of the camera; that counts as stopping
                stop(admin);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer admin && WATCHING.containsKey(admin.getUUID())) {
            stop(admin);
        }
    }
}

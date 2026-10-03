package com.barbwra.mlum.admin;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.network.S2CAdmin;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Who is staff, and what they may do.
 *
 * <p>Operators may do everything, always. Everyone else may do exactly what their admin rank
 * holds - see {@link Perms} for what a rank can hold. Every action the admin panel offers is checked
 * here on the server, whatever the client claims.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class Staff {

    private Staff() {
    }

    public static boolean isOp(ServerPlayer player) {
        return player != null && player.server.getPlayerList().isOp(player.getGameProfile());
    }

    @Nullable
    public static StaffRank rank(ServerPlayer player) {
        return player == null ? null : StaffData.get(player.server).rankOf(player.getUUID());
    }

    public static boolean isStaff(ServerPlayer player) {
        return isOp(player) || rank(player) != null;
    }

    public static boolean has(ServerPlayer player, String node) {
        if (player == null) {
            return false;
        }
        if (isOp(player)) {
            return true;
        }
        StaffRank r = rank(player);
        return r != null && r.has(node);
    }

    /** For {@link CommandGate}: does the rank behind this source unlock the command at {@code path}? */
    public static boolean allowsCommand(CommandSourceStack source, String path) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return false;
        }
        StaffRank r = StaffData.get(player.server).rankOf(player.getUUID());
        return r != null && r.has("cmd." + path);
    }

    /** Runs {@code action} only when the player holds {@code node}; otherwise says no. */
    public static boolean require(ServerPlayer player, String node, Consumer<ServerPlayer> action) {
        if (!has(player, node)) {
            com.barbwra.mlum.util.Feedback.bad(player, "ما عندك صلاحية لهذا");
            return false;
        }
        action.accept(player);
        return true;
    }

    /* ================================================================== telling clients */

    /** The player's own standing, and a fresh command list so their newly allowed commands complete. */
    public static void refresh(ServerPlayer player) {
        if (player == null || player.connection == null) {
            return;
        }
        player.server.getCommands().sendCommands(player);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Op", isOp(player));
        StaffRank r = rank(player);
        tag.putString("Rank", r == null ? "" : r.name);
        tag.putInt("Color", r == null ? 0 : r.color);
        ListTag perms = new ListTag();
        if (r != null) {
            for (String p : r.perms) {
                perms.add(StringTag.valueOf(p));
            }
        }
        tag.put("Perms", perms);
        send(player, "staff", tag);
    }

    public static void refreshAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            refresh(p);
        }
    }

    public static void send(ServerPlayer player, String kind, CompoundTag data) {
        if (player != null && player.connection != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CAdmin(kind, data));
        }
    }

    /** To every online player holding {@code node} - alerts, new tickets. */
    public static void tellStaff(MinecraftServer server, String node, String markup) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (has(p, node)) {
                com.barbwra.mlum.util.Feedback.ok(p, markup);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StaffData data = StaffData.get(player.server);
            if (data.members.containsKey(player.getUUID())) {
                data.names.put(player.getUUID(), player.getGameProfile().getName());
                data.setDirty();
            }
            refresh(player);
        }
    }
}

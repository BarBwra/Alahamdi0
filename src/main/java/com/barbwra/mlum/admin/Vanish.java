package com.barbwra.mlum.admin;

import com.barbwra.mlum.MlumInventory;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The admin's vanish: nobody but other staff with the same permission can see them.
 *
 * <p>Four things together. The player's invisible flag hides the name tag and the shadow for
 * everyone; every client that is not allowed to see them skips drawing them at all (armour and held
 * items included - the list of who is vanished is sent to every client); they are taken off the Tab
 * list of everyone who may not see them; and mobs will not target them.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class Vanish {

    private Vanish() {
    }

    private static final Set<UUID> VANISHED = new HashSet<>();

    public static boolean isVanished(UUID id) {
        return VANISHED.contains(id);
    }

    public static boolean toggle(ServerPlayer player) {
        boolean now = !VANISHED.contains(player.getUUID());
        if (now) {
            VANISHED.add(player.getUUID());
            player.setInvisible(true);
        } else {
            VANISHED.remove(player.getUUID());
            player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
        }
        broadcast(player.server);
        return now;
    }

    /** Everyone's view: the vanished list to every client, and the Tab list fixed for each viewer. */
    public static void broadcast(MinecraftServer server) {
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        int[] ids = VANISHED.stream()
                .map(id -> server.getPlayerList().getPlayer(id))
                .filter(p -> p != null)
                .mapToInt(ServerPlayer::getId).toArray();
        CompoundTag tag = new CompoundTag();
        tag.put("Ids", new IntArrayTag(ids));
        for (ServerPlayer viewer : online) {
            Staff.send(viewer, "vanish", tag);
            boolean sees = Staff.has(viewer, Perms.VANISH);
            List<UUID> hidden = new ArrayList<>();
            List<UUID> shown = new ArrayList<>();
            for (ServerPlayer p : online) {
                if (p == viewer) {
                    continue;
                }
                (VANISHED.contains(p.getUUID()) && !sees ? hidden : shown).add(p.getUUID());
            }
            listed(viewer, hidden, false);
            listed(viewer, shown, true);
        }
    }

    /**
     * Shows or hides players on one viewer's Tab list. The update packet has no public way to say
     * "not listed" for a player who is online, so it is written field by field: the action set, then
     * each player's id and the flag - exactly what the packet itself reads.
     */
    private static void listed(ServerPlayer viewer, List<UUID> players, boolean listed) {
        if (players.isEmpty() || viewer.connection == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeEnumSet(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                ClientboundPlayerInfoUpdatePacket.Action.class);
        buf.writeCollection(players, (b, id) -> {
            b.writeUUID(id);
            b.writeBoolean(listed);
        });
        try {
            viewer.connection.send(new ClientboundPlayerInfoUpdatePacket(buf));
        } finally {
            buf.release();
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || VANISHED.isEmpty() || event.getServer().getTickCount() % 10 != 0) {
            return;
        }
        for (UUID id : VANISHED) {
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(id);
            if (p != null && !p.isInvisible()) {
                p.setInvisible(true);
            }
        }
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer p && VANISHED.contains(p.getUUID())
                && event.getEntity() instanceof Mob) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !VANISHED.isEmpty()) {
            broadcast(player.server);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (VANISHED.remove(event.getEntity().getUUID()) && event.getEntity() instanceof ServerPlayer player) {
            player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
            broadcast(player.server);
        }
    }
}

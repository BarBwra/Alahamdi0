package com.barbwra.mlum.admin;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.util.Feedback;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Warnings, mutes, jail, kicks and bans - and every player's record of them.
 *
 * <ul>
 *   <li><b>Warn</b>: on the record, and the player sees it on screen.</li>
 *   <li><b>Mute</b>: chat and private messages refused until it runs out.</li>
 *   <li><b>Jail</b>: taken to the jail and kept within a few blocks of it until it runs out, then
 *       sent to spawn. The jail is wherever an admin last set it.</li>
 *   <li><b>Kick</b>: off the server with the reason.</li>
 *   <li><b>Ban</b>: vanilla's own ban list, so it holds even if this mod is removed, with the
 *       reason and, for a timed ban, the date it ends.</li>
 * </ul>
 * <p>The punished player is shown a notice with the reason and how long, so nobody is left
 * guessing why they cannot talk.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class Punish extends SavedData {

    private static final String FILE = "mlum_punish";
    private static final double JAIL_RADIUS = 6.0D;

    public static final String WARN = "warn";
    public static final String MUTE = "mute";
    public static final String JAIL = "jail";
    public static final String KICK = "kick";
    public static final String BAN = "ban";

    public record Entry(String type, String reason, String by, long at, long until) {
        public boolean active(long now) {
            return until <= 0L ? !type.equals(WARN) && !type.equals(KICK) : until > now;
        }
    }

    private final Map<UUID, List<Entry>> records = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Long> mutedUntil = new HashMap<>();
    private final Map<UUID, Long> jailedUntil = new HashMap<>();
    private String jailDim = "";
    private BlockPos jailPos;

    public static Punish get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("no overworld");
        }
        return overworld.getDataStorage().computeIfAbsent(Punish::load, Punish::new, FILE);
    }

    public List<Entry> of(UUID player) {
        return records.getOrDefault(player, List.of());
    }

    public String nameOf(UUID player) {
        return names.getOrDefault(player, player.toString().substring(0, 8));
    }

    public Map<UUID, List<Entry>> all() {
        return records;
    }

    public boolean muted(UUID player) {
        Long until = mutedUntil.get(player);
        return until != null && (until < 0L || until > System.currentTimeMillis());
    }

    public boolean jailed(UUID player) {
        Long until = jailedUntil.get(player);
        return until != null && (until < 0L || until > System.currentTimeMillis());
    }

    public void setJail(ServerPlayer where) {
        jailDim = where.level().dimension().location().toString();
        jailPos = where.blockPosition();
        setDirty();
    }

    public boolean hasJail() {
        return jailPos != null;
    }

    /**
     * Punishes {@code target}. {@code minutes} 0 means permanent for mute, jail and ban.
     *
     * @return false when it could not be done (no jail set)
     */
    public boolean apply(MinecraftServer server, UUID target, String targetName, String type, String reason,
                         int minutes, String by) {
        long now = System.currentTimeMillis();
        long until = minutes > 0 ? now + minutes * 60_000L : 0L;
        if (type.equals(JAIL) && jailPos == null) {
            return false;
        }
        records.computeIfAbsent(target, k -> new ArrayList<>()).add(0, new Entry(type, reason, by, now, until));
        names.put(target, targetName);
        ServerPlayer online = server.getPlayerList().getPlayer(target);
        switch (type) {
            case MUTE -> mutedUntil.put(target, until > 0L ? until : -1L);
            case JAIL -> {
                jailedUntil.put(target, until > 0L ? until : -1L);
                if (online != null) {
                    toJail(online);
                }
            }
            case KICK -> {
                if (online != null) {
                    online.connection.disconnect(Component.literal("انطردت من السيرفر\n\n" + reason));
                }
            }
            case BAN -> {
                GameProfile profile = online != null ? online.getGameProfile() : new GameProfile(target, targetName);
                server.getPlayerList().getBans().add(new UserBanListEntry(profile, new Date(now), by,
                        until > 0L ? new Date(until) : null, reason));
                if (online != null) {
                    online.connection.disconnect(Component.literal("أخذت باند\n\n" + reason
                            + (until > 0L ? "\n\nينتهي بعد " + minutes + " دقيقة" : "")));
                }
            }
            default -> {
            }
        }
        if (online != null && !type.equals(KICK) && !type.equals(BAN)) {
            notice(online, type, reason, until, by);
        }
        setDirty();
        return true;
    }

    /** Lifts an active mute, jail or ban early. */
    public void lift(MinecraftServer server, UUID target, String type) {
        switch (type) {
            case MUTE -> mutedUntil.remove(target);
            case JAIL -> {
                jailedUntil.remove(target);
                ServerPlayer p = server.getPlayerList().getPlayer(target);
                if (p != null) {
                    release(p);
                }
            }
            case BAN -> {
                GameProfile profile = new GameProfile(target, nameOf(target));
                server.getPlayerList().getBans().remove(profile);
            }
            default -> {
            }
        }
        setDirty();
    }

    private static void notice(ServerPlayer player, String type, String reason, long until, String by) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type);
        tag.putString("Reason", reason);
        tag.putLong("Until", until);
        tag.putString("By", by);
        Staff.send(player, "notice", tag);
    }

    private void toJail(ServerPlayer player) {
        ServerLevel level = jailLevel(player.server);
        if (level != null && jailPos != null) {
            player.teleportTo(level, jailPos.getX() + 0.5D, jailPos.getY(), jailPos.getZ() + 0.5D, player.getYRot(), player.getXRot());
        }
    }

    private static void release(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, player.getYRot(), player.getXRot());
        Feedback.ok(player, "انتهى سجنك");
    }

    private ServerLevel jailLevel(MinecraftServer server) {
        ResourceLocation id = ResourceLocation.tryParse(jailDim);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /* ================================================================== enforcement */

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (get(player.server).muted(player.getUUID())) {
            event.setCanceled(true);
            Feedback.bad(player, "أنت مكتوم");
        }
    }

    private static final java.util.Set<String> WHISPERS = java.util.Set.of("msg", "tell", "w", "me", "teammsg", "tm");

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String input = event.getParseResults().getReader().getString().trim();
        String root = input.startsWith("/") ? input.substring(1) : input;
        int space = root.indexOf(' ');
        root = (space < 0 ? root : root.substring(0, space)).toLowerCase(java.util.Locale.ROOT);
        if (WHISPERS.contains(root) && get(player.server).muted(player.getUUID())) {
            event.setCanceled(true);
            Feedback.bad(player, "أنت مكتوم");
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        Punish data = get(server);
        if (data.jailedUntil.isEmpty() && data.mutedUntil.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        data.mutedUntil.values().removeIf(until -> until > 0L && until <= now);
        for (UUID id : data.jailedUntil.keySet().toArray(new UUID[0])) {
            long until = data.jailedUntil.get(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (until > 0L && until <= now) {
                data.jailedUntil.remove(id);
                data.setDirty();
                if (p != null) {
                    release(p);
                }
                continue;
            }
            if (p == null || data.jailPos == null) {
                continue;
            }
            ServerLevel level = data.jailLevel(server);
            if (p.level() != level || p.blockPosition().distSqr(data.jailPos) > JAIL_RADIUS * JAIL_RADIUS) {
                data.toJail(p);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Punish data = get(player.server);
        data.names.put(player.getUUID(), player.getGameProfile().getName());
        long now = System.currentTimeMillis();
        for (Entry e : data.of(player.getUUID())) {
            if ((e.type.equals(MUTE) && data.muted(player.getUUID())) || (e.type.equals(JAIL) && data.jailed(player.getUUID()))) {
                notice(player, e.type, e.reason, e.until, e.by);
                break;
            }
        }
    }

    /* ================================================================== saving */

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag all = new ListTag();
        for (Map.Entry<UUID, List<Entry>> e : records.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putUUID("Id", e.getKey());
            p.putString("Name", nameOf(e.getKey()));
            if (mutedUntil.containsKey(e.getKey())) {
                p.putLong("Muted", mutedUntil.get(e.getKey()));
            }
            if (jailedUntil.containsKey(e.getKey())) {
                p.putLong("Jailed", jailedUntil.get(e.getKey()));
            }
            ListTag list = new ListTag();
            for (Entry r : e.getValue()) {
                CompoundTag t = new CompoundTag();
                t.putString("Type", r.type);
                t.putString("Reason", r.reason);
                t.putString("By", r.by);
                t.putLong("At", r.at);
                t.putLong("Until", r.until);
                list.add(t);
            }
            p.put("Records", list);
            all.add(p);
        }
        tag.put("Players", all);
        tag.putString("JailDim", jailDim);
        if (jailPos != null) {
            tag.putLong("JailPos", jailPos.asLong());
        }
        return tag;
    }

    public static Punish load(CompoundTag tag) {
        Punish d = new Punish();
        ListTag all = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < all.size(); i++) {
            CompoundTag p = all.getCompound(i);
            UUID id = p.getUUID("Id");
            d.names.put(id, p.getString("Name"));
            if (p.contains("Muted")) {
                d.mutedUntil.put(id, p.getLong("Muted"));
            }
            if (p.contains("Jailed")) {
                d.jailedUntil.put(id, p.getLong("Jailed"));
            }
            List<Entry> list = new ArrayList<>();
            ListTag rs = p.getList("Records", Tag.TAG_COMPOUND);
            for (int k = 0; k < rs.size(); k++) {
                CompoundTag t = rs.getCompound(k);
                list.add(new Entry(t.getString("Type"), t.getString("Reason"), t.getString("By"), t.getLong("At"), t.getLong("Until")));
            }
            d.records.put(id, list);
        }
        d.jailDim = tag.getString("JailDim");
        if (tag.contains("JailPos")) {
            d.jailPos = BlockPos.of(tag.getLong("JailPos"));
        }
        return d;
    }
}

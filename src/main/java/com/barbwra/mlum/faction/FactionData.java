package com.barbwra.mlum.faction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every faction on the server, and who belongs to which.
 *
 * <p>Backed by vanilla {@link SavedData} on the overworld, the same as the warehouse - so factions
 * are written by the same autosave that writes the world, survive a crash the same way, and need no
 * database. {@link #setDirty()} is a flag rather than I/O, so marking a change is free.</p>
 *
 * <p><b>The membership index is derived, never saved.</b> Player-to-faction is rebuilt from the
 * factions themselves on load. Saving it too would create a second copy of the same truth, and the
 * two would eventually disagree - a player showing in a faction that does not list them, or listed
 * twice. Rebuilding is O(members) once per server start, which is nothing.</p>
 */
public final class FactionData extends SavedData {

    private static final String FILE = "mlum_factions";

    /** Faction id to faction. */
    private final Map<UUID, Faction> factions = new LinkedHashMap<>();

    /** Player to faction id. Derived from {@link #factions}; see the class note. */
    private final Map<UUID, UUID> membership = new HashMap<>();

    /** Outstanding invites: invited player to faction id. One at a time, newest wins. */
    private final Map<UUID, UUID> invites = new HashMap<>();

    public static FactionData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("no overworld to store factions on");
        }
        return overworld.getDataStorage().computeIfAbsent(FactionData::load, FactionData::new, FILE);
    }

    /* ------------------------------------------------------------------- reading */

    @Nullable
    public Faction byId(UUID id) {
        return id == null ? null : factions.get(id);
    }

    /** The faction this player belongs to, or null. */
    @Nullable
    public Faction of(UUID player) {
        UUID id = membership.get(player);
        return id == null ? null : factions.get(id);
    }

    /** Case-insensitive name lookup, for the create command's duplicate check. */
    @Nullable
    public Faction byName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (Faction faction : factions.values()) {
            if (faction.name().equalsIgnoreCase(name)) {
                return faction;
            }
        }
        return null;
    }

    public boolean inFaction(UUID player) {
        return membership.containsKey(player);
    }

    /**
     * Factions ranked for the leaderboard: level, then points, then money in the bank.
     *
     * <p>Spelled out in that order because that is the rule as specified, and because a later change
     * to the curve should keep the ranking honest without anyone revisiting this method. Note that
     * the level is <i>derived</i> from the points, so today the first key can never disagree with
     * the second - the bank is what actually separates two factions on identical points, and the
     * creation date is the last resort so the order is total and never flickers between saves.</p>
     */
    public List<Faction> leaderboard() {
        List<Faction> all = new ArrayList<>(factions.values());
        all.sort(Comparator
                .comparingInt(Faction::level).reversed()
                .thenComparing(Comparator.comparingInt(Faction::points).reversed())
                .thenComparing(Comparator.comparingInt(Faction::bank).reversed())
                .thenComparingLong(Faction::createdAt));
        return all;
    }

    public int count() {
        return factions.size();
    }

    /* ------------------------------------------------------------------- writing */

    /**
     * Creates a faction with this player as leader.
     *
     * <p>Returns null when the name is taken or the player is already in one - the two rules that
     * must hold for the membership index to stay single-valued.</p>
     */
    @Nullable
    public Faction create(UUID leader, String leaderName, String name, long now) {
        if (leader == null || name == null || name.isBlank()
                || inFaction(leader) || byName(name) != null) {
            return null;
        }
        Faction faction = new Faction(UUID.randomUUID(), name.trim(), leader, now);
        faction.add(new FactionMember(leader, leaderName, FactionRole.LEADER, now));
        factions.put(faction.id(), faction);
        membership.put(leader, faction.id());
        invites.remove(leader);
        setDirty();
        return faction;
    }

    /** Disbands a faction. The vault goes with it, so callers should warn first. */
    public boolean disband(UUID factionId) {
        Faction faction = factions.remove(factionId);
        if (faction == null) {
            return false;
        }
        membership.values().removeIf(factionId::equals);
        invites.values().removeIf(factionId::equals);
        setDirty();
        return true;
    }

    /** Adds a player to a faction at the joining rank, clearing their invite. */
    public boolean join(UUID player, String playerName, UUID factionId, long now) {
        Faction faction = factions.get(factionId);
        if (faction == null || inFaction(player)) {
            return false;
        }
        faction.add(new FactionMember(player, playerName, FactionRole.defaultRole(), now));
        membership.put(player, factionId);
        invites.remove(player);
        setDirty();
        return true;
    }

    /** Removes a player from whatever faction they are in. Refuses for a leader. */
    public boolean leave(UUID player) {
        Faction faction = of(player);
        if (faction == null || !faction.remove(player)) {
            return false;
        }
        membership.remove(player);
        setDirty();
        return true;
    }

    /* ------------------------------------------------------------------- invites */

    public void invite(UUID player, UUID factionId) {
        if (player != null && factions.containsKey(factionId) && !inFaction(player)) {
            invites.put(player, factionId);
            setDirty();
        }
    }

    /** The faction inviting this player, or null. */
    @Nullable
    public Faction inviteFor(UUID player) {
        UUID id = invites.get(player);
        return id == null ? null : factions.get(id);
    }

    /**
     * Everyone currently holding an invite from this faction.
     *
     * <p>Used by the invite window to mark a name as already asked, so a leader who clicks twice
     * sees "sent" rather than silence and assumes it failed.</p>
     */
    public java.util.Set<UUID> invitedBy(UUID factionId) {
        java.util.Set<UUID> out = new java.util.HashSet<>();
        if (factionId == null) {
            return out;
        }
        invites.forEach((player, id) -> {
            if (factionId.equals(id)) {
                out.add(player);
            }
        });
        return out;
    }

    public void clearInvite(UUID player) {
        inviteMeta.remove(player);
        if (invites.remove(player) != null) {
            setDirty();
        }
    }

    /** Who sent an invite and when - for the invite card. Not saved: after a restart it reads blank. */
    public record InviteMeta(String from, long at) {
    }

    private final Map<UUID, InviteMeta> inviteMeta = new HashMap<>();

    public void noteInviter(UUID player, String from, long at) {
        if (player != null) {
            inviteMeta.put(player, new InviteMeta(from == null ? "" : from, at));
        }
    }

    @Nullable
    public InviteMeta inviteMeta(UUID player) {
        return invites.containsKey(player) ? inviteMeta.get(player) : null;
    }

    /** Keeps the stored display name current, so offline members still render. */
    public void refreshName(UUID player, String name) {
        Faction faction = of(player);
        if (faction == null) {
            return;
        }
        FactionMember member = faction.member(player);
        if (member != null && !member.name().equals(name)) {
            member.setName(name);
            setDirty();
        }
    }

    /* --------------------------------------------------------------- persistence */

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Faction faction : factions.values()) {
            list.add(faction.save());
        }
        tag.put("Factions", list);

        CompoundTag inviteTag = new CompoundTag();
        invites.forEach((player, factionId) -> inviteTag.putUUID(player.toString(), factionId));
        tag.put("Invites", inviteTag);
        return tag;
    }

    public static FactionData load(CompoundTag tag) {
        FactionData data = new FactionData();

        ListTag list = tag.getList("Factions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Faction faction = Faction.load(list.getCompound(i));
            data.factions.put(faction.id(), faction);
            // the index is rebuilt here rather than read from disk - see the class note
            for (UUID member : faction.members().keySet()) {
                data.membership.put(member, faction.id());
            }
        }

        CompoundTag inviteTag = tag.getCompound("Invites");
        for (String key : inviteTag.getAllKeys()) {
            try {
                UUID player = UUID.fromString(key);
                UUID factionId = inviteTag.getUUID(key);
                // an invite to a faction that has since disbanded is dropped, not resurrected
                if (data.factions.containsKey(factionId) && !data.membership.containsKey(player)) {
                    data.invites.put(player, factionId);
                }
            } catch (IllegalArgumentException ignored) {
                // a malformed key is bad data, not a reason to lose every other invite
            }
        }
        return data;
    }
}

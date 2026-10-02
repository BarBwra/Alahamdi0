package com.barbwra.mlum.faction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One faction: its members, its score, its money and its vault.
 *
 * <p><b>Points are a lifetime total, and the level is derived from them.</b> There is no stored
 * level field. That is the same decision the player level system arrived at the hard way - keeping a
 * level alongside the points that produce it means two numbers that can disagree, and they
 * eventually do. {@link #level()} is a function of {@link #points()} and cannot drift.</p>
 *
 * <p><b>Points can go down; the level with them.</b> A death costs the faction ten points, and the
 * floor is zero rather than negative so a struggling faction shows an empty bar rather than a
 * nonsense number on the leaderboard. Losing a level closes vault rows, which is exactly why
 * {@link FactionVault} never destroys a slot it cannot currently show.</p>
 */
public final class Faction {

    /** Stable identity. Names can change; this cannot, so vault and membership survive a rename. */
    private final UUID id;

    private String name;
    private UUID leader;
    private int points;
    private int bank;
    private final long createdAt;

    /** Insertion-ordered so a save/load round trip does not reshuffle the members list. */
    private final Map<UUID, FactionMember> members = new LinkedHashMap<>();

    private FactionVault vault = new FactionVault();

    public Faction(UUID id, String name, UUID leader, long createdAt) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.leader = leader;
        this.createdAt = createdAt;
    }

    /* ---------------------------------------------------------------------- identity */

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String value) {
        if (value != null && !value.isBlank()) {
            this.name = value;
        }
    }

    public UUID leader() {
        return leader;
    }

    public long createdAt() {
        return createdAt;
    }

    public FactionVault vault() {
        return vault;
    }

    /* ------------------------------------------------------------------------ score */

    public int points() {
        return points;
    }

    /** The level these points have earned. Derived, never stored. */
    public int level() {
        return FactionLevel.levelFor(points);
    }

    /** Progress through the current level, for the faction level screen's bar. */
    public float progress() {
        return FactionLevel.progress(points);
    }

    /**
     * Adds or removes points, crediting the earner when there is one.
     *
     * <p>The member's own contribution only ever rises - see {@link FactionMember#addContributed} -
     * while the faction total moves both ways.</p>
     */
    public void addPoints(@Nullable UUID earner, int amount) {
        points = Math.max(0, points + amount);
        if (earner != null && amount > 0) {
            FactionMember member = members.get(earner);
            if (member != null) {
                member.addContributed(amount);
            }
        }
    }

    /**
     * Overwrites the lifetime total outright. Operators only.
     *
     * <p>Nobody's individual contribution is touched: the admin did not earn these points, so
     * attributing them to a member would be a lie the members list would then display forever.
     * The faction total and the sum of contributions are allowed to disagree for exactly this
     * reason - one is the score, the other is a record of who did the work.</p>
     */
    public void setPoints(int value) {
        this.points = Math.max(0, value);
    }

    /* ------------------------------------------------------------------------- bank */

    public int bank() {
        return bank;
    }

    /**
     * Adds to or removes from the bank without crediting a donor. Operators only.
     *
     * <p>Separate from {@link #donate} because a donation is a member's record as well as money,
     * and an operator topping up the treasury did not donate anything.</p>
     */
    public void addBank(int amount) {
        this.bank = Math.max(0, bank + amount);
    }

    /** Records a donation against both the bank and the donor's record. */
    public void donate(UUID donor, int amount) {
        if (amount <= 0) {
            return;
        }
        bank += amount;
        FactionMember member = members.get(donor);
        if (member != null) {
            member.addDonated(amount);
        }
    }

    /** Spends from the bank, refusing rather than going negative. Returns false if short. */
    public boolean withdraw(int amount) {
        if (amount <= 0 || bank < amount) {
            return false;
        }
        bank -= amount;
        return true;
    }

    /* ---------------------------------------------------------------------- members */

    public Map<UUID, FactionMember> members() {
        return members;
    }

    public int size() {
        return members.size();
    }

    @Nullable
    public FactionMember member(UUID player) {
        return members.get(player);
    }

    public boolean contains(UUID player) {
        return members.containsKey(player);
    }

    /** The rank of a player in this faction, or null when they are not in it. */
    @Nullable
    public FactionRole roleOf(UUID player) {
        FactionMember member = members.get(player);
        return member == null ? null : member.role();
    }

    public void add(FactionMember member) {
        if (member != null) {
            members.put(member.id(), member);
        }
    }

    /**
     * Removes a member.
     *
     * <p>Refuses to remove the leader: a faction without one has no route back to having one, and
     * every permission check would fall through. Transferring leadership first is the only way out,
     * which is what {@link #setLeader} is for.</p>
     */
    public boolean remove(UUID player) {
        if (player == null || player.equals(leader)) {
            return false;
        }
        return members.remove(player) != null;
    }

    /** Hands the faction over, demoting the outgoing leader to deputy rather than ejecting them. */
    public void setLeader(UUID player) {
        FactionMember incoming = members.get(player);
        if (incoming == null) {
            return;
        }
        FactionMember outgoing = members.get(leader);
        if (outgoing != null) {
            outgoing.setRole(FactionRole.DEPUTY);
        }
        incoming.setRole(FactionRole.LEADER);
        this.leader = player;
    }

    /**
     * Members ordered for the list screen: highest rank first, then biggest contributor.
     *
     * <p>The contribution tiebreak is what stops a faction's list from looking arbitrary once
     * several people share a rank.</p>
     */
    public List<FactionMember> sortedMembers() {
        List<FactionMember> sorted = new ArrayList<>(members.values());
        sorted.sort(Comparator
                .comparingInt((FactionMember m) -> m.role().ordinal())
                .thenComparing(Comparator.comparingInt(FactionMember::contributed).reversed())
                .thenComparing(FactionMember::name));
        return sorted;
    }

    /* ------------------------------------------------------------------ persistence */

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        if (leader != null) {
            tag.putUUID("Leader", leader);
        }
        tag.putInt("Points", points);
        tag.putInt("Bank", bank);
        tag.putLong("Created", createdAt);

        ListTag memberList = new ListTag();
        for (FactionMember member : members.values()) {
            memberList.add(member.save());
        }
        tag.put("Members", memberList);
        tag.put("Vault", vault.save());
        return tag;
    }

    public static Faction load(CompoundTag tag) {
        Faction faction = new Faction(
                tag.getUUID("Id"),
                tag.getString("Name"),
                tag.hasUUID("Leader") ? tag.getUUID("Leader") : null,
                tag.getLong("Created"));
        faction.points = Math.max(0, tag.getInt("Points"));
        faction.bank = Math.max(0, tag.getInt("Bank"));

        ListTag memberList = tag.getList("Members", Tag.TAG_COMPOUND);
        for (int i = 0; i < memberList.size(); i++) {
            faction.add(FactionMember.load(memberList.getCompound(i)));
        }
        faction.vault = FactionVault.load(tag.getCompound("Vault"));
        return faction;
    }
}

package com.barbwra.mlum.faction;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * One player's membership record inside a faction.
 *
 * <p><b>Why the name is stored and not looked up.</b> The members list has to render for players who
 * are offline, and an offline player's name is only available through the server's profile cache,
 * which can miss. Keeping the last known name here means the list is always drawable; it is
 * refreshed every time the player logs in, so it goes stale only for someone who has not played
 * since changing their name.</p>
 *
 * <p><b>Why contribution is stored per member rather than derived.</b> The faction's point total is
 * a single running number, so it cannot be split back into who earned what. Recording each member's
 * share as it is earned is the only way the members list can show it - and it is what makes kicking
 * someone not silently rewrite the faction's history.</p>
 */
public final class FactionMember {

    private final UUID id;
    private String name;
    private FactionRole role;
    private int donated;
    private int contributed;
    private final long joinedAt;

    public FactionMember(UUID id, String name, FactionRole role, long joinedAt) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.role = role == null ? FactionRole.defaultRole() : role;
        this.joinedAt = joinedAt;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** Refreshed on login so the list never shows a name the player has abandoned. */
    public void setName(String value) {
        if (value != null && !value.isBlank()) {
            this.name = value;
        }
    }

    public FactionRole role() {
        return role;
    }

    public void setRole(FactionRole value) {
        if (value != null) {
            this.role = value;
        }
    }

    /** Money this member has put into the faction bank, lifetime. */
    public int donated() {
        return donated;
    }

    public void addDonated(int amount) {
        if (amount > 0) {
            this.donated += amount;
        }
    }

    /** Faction points this member has earned, lifetime. Never decreases on a death penalty. */
    public int contributed() {
        return contributed;
    }

    /**
     * Credits points earned.
     *
     * <p>Deliberately ignores negative amounts. A death costs the <i>faction</i> ten points, but
     * subtracting it from the member's contribution would let a player who dies often show as having
     * contributed less than nothing, which reads as an accusation rather than a statistic.</p>
     */
    public void addContributed(int amount) {
        if (amount > 0) {
            this.contributed += amount;
        }
    }

    public long joinedAt() {
        return joinedAt;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putString("Role", role.name());
        tag.putInt("Donated", donated);
        tag.putInt("Contributed", contributed);
        tag.putLong("Joined", joinedAt);
        return tag;
    }

    public static FactionMember load(CompoundTag tag) {
        FactionMember member = new FactionMember(
                tag.getUUID("Id"),
                tag.getString("Name"),
                FactionRole.byName(tag.getString("Role")),
                tag.getLong("Joined"));
        member.donated = Math.max(0, tag.getInt("Donated"));
        member.contributed = Math.max(0, tag.getInt("Contributed"));
        return member;
    }
}

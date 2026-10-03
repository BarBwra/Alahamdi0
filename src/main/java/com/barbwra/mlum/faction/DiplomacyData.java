package com.barbwra.mlum.faction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Between factions: who is allied with whom, who is at war, and the bounties on people's heads.
 * Kept with the world as {@code mlum_diplomacy}, apart from the factions themselves so a faction's
 * own save never has to know about anyone else. Entries that name a faction which no longer exists
 * are dropped by {@link Diplomacy}'s housekeeping.
 */
public final class DiplomacyData extends SavedData {

    private static final String FILE = "mlum_diplomacy";

    /** An alliance, either way round. */
    public record Alliance(UUID a, UUID b, long since) {
        public boolean has(UUID id) {
            return a.equals(id) || b.equals(id);
        }

        public UUID other(UUID id) {
            return a.equals(id) ? b : a;
        }
    }

    /** A war one faction declared on another. Kills and money are counted per side. */
    public static final class War {
        public final UUID attacker;
        public final UUID defender;
        public final long start;
        public final long end;
        /** The most each side can lose over the war: a tenth of its bank when the war began. */
        public final int capAttacker;
        public final int capDefender;
        public int attackerKills;
        public int defenderKills;
        /** What each side has lost to the other so far. */
        public int lostAttacker;
        public int lostDefender;

        War(UUID attacker, UUID defender, long start, long end, int capAttacker, int capDefender) {
            this.attacker = attacker;
            this.defender = defender;
            this.start = start;
            this.end = end;
            this.capAttacker = capAttacker;
            this.capDefender = capDefender;
        }

        public boolean between(UUID x, UUID y) {
            return (attacker.equals(x) && defender.equals(y)) || (attacker.equals(y) && defender.equals(x));
        }

        public boolean involves(UUID id) {
            return attacker.equals(id) || defender.equals(id);
        }
    }

    /** Money on a player's head, paid in advance from the faction that set it. */
    public record Bounty(UUID faction, String factionName, UUID target, String targetName, int amount, long placed) {
    }

    public final List<Alliance> alliances = new ArrayList<>();
    public final List<War> wars = new ArrayList<>();
    public final List<Bounty> bounties = new ArrayList<>();
    /** Faction to when it may make a new alliance again, after breaking one. */
    public final Map<UUID, Long> allyCooldown = new HashMap<>();
    /** "a|b" (ids sorted) to when these two may go to war again. */
    public final Map<String, Long> warCooldown = new HashMap<>();

    public static DiplomacyData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("no overworld");
        }
        return overworld.getDataStorage().computeIfAbsent(DiplomacyData::load, DiplomacyData::new, FILE);
    }

    public static String pair(UUID x, UUID y) {
        return x.compareTo(y) < 0 ? x + "|" + y : y + "|" + x;
    }

    public void addAlliance(UUID a, UUID b, long now) {
        alliances.add(new Alliance(a, b, now));
        setDirty();
    }

    public void addWar(UUID attacker, UUID defender, long start, long end, int capAttacker, int capDefender) {
        wars.add(new War(attacker, defender, start, end, capAttacker, capDefender));
        setDirty();
    }

    public void addBounty(Bounty b) {
        bounties.add(b);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag al = new ListTag();
        for (Alliance a : alliances) {
            CompoundTag t = new CompoundTag();
            t.putUUID("A", a.a());
            t.putUUID("B", a.b());
            t.putLong("Since", a.since());
            al.add(t);
        }
        tag.put("Alliances", al);
        ListTag wl = new ListTag();
        for (War w : wars) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Att", w.attacker);
            t.putUUID("Def", w.defender);
            t.putLong("Start", w.start);
            t.putLong("End", w.end);
            t.putInt("CapA", w.capAttacker);
            t.putInt("CapD", w.capDefender);
            t.putInt("KillsA", w.attackerKills);
            t.putInt("KillsD", w.defenderKills);
            t.putInt("LostA", w.lostAttacker);
            t.putInt("LostD", w.lostDefender);
            wl.add(t);
        }
        tag.put("Wars", wl);
        ListTag bl = new ListTag();
        for (Bounty b : bounties) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Faction", b.faction());
            t.putString("FactionName", b.factionName());
            t.putUUID("Target", b.target());
            t.putString("TargetName", b.targetName());
            t.putInt("Amount", b.amount());
            t.putLong("Placed", b.placed());
            bl.add(t);
        }
        tag.put("Bounties", bl);
        CompoundTag ac = new CompoundTag();
        allyCooldown.forEach((id, until) -> ac.putLong(id.toString(), until));
        tag.put("AllyCooldown", ac);
        CompoundTag wc = new CompoundTag();
        warCooldown.forEach(wc::putLong);
        tag.put("WarCooldown", wc);
        return tag;
    }

    public static DiplomacyData load(CompoundTag tag) {
        DiplomacyData d = new DiplomacyData();
        ListTag al = tag.getList("Alliances", Tag.TAG_COMPOUND);
        for (int i = 0; i < al.size(); i++) {
            CompoundTag t = al.getCompound(i);
            d.alliances.add(new Alliance(t.getUUID("A"), t.getUUID("B"), t.getLong("Since")));
        }
        ListTag wl = tag.getList("Wars", Tag.TAG_COMPOUND);
        for (int i = 0; i < wl.size(); i++) {
            CompoundTag t = wl.getCompound(i);
            War w = new War(t.getUUID("Att"), t.getUUID("Def"), t.getLong("Start"), t.getLong("End"),
                    t.getInt("CapA"), t.getInt("CapD"));
            w.attackerKills = t.getInt("KillsA");
            w.defenderKills = t.getInt("KillsD");
            w.lostAttacker = t.getInt("LostA");
            w.lostDefender = t.getInt("LostD");
            d.wars.add(w);
        }
        ListTag bl = tag.getList("Bounties", Tag.TAG_COMPOUND);
        for (int i = 0; i < bl.size(); i++) {
            CompoundTag t = bl.getCompound(i);
            d.bounties.add(new Bounty(t.getUUID("Faction"), t.getString("FactionName"), t.getUUID("Target"),
                    t.getString("TargetName"), t.getInt("Amount"), t.getLong("Placed")));
        }
        CompoundTag ac = tag.getCompound("AllyCooldown");
        for (String k : ac.getAllKeys()) {
            try {
                d.allyCooldown.put(UUID.fromString(k), ac.getLong(k));
            } catch (IllegalArgumentException ignored) {
                // a damaged key is dropped, not fatal
            }
        }
        CompoundTag wc = tag.getCompound("WarCooldown");
        for (String k : wc.getAllKeys()) {
            d.warCooldown.put(k, wc.getLong(k));
        }
        return d;
    }
}

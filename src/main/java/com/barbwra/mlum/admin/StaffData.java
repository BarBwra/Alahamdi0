package com.barbwra.mlum.admin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The admin ranks and who holds which, saved with the world ({@code data/mlum_staff.dat}).
 *
 * <p>One rank per player. Names are kept beside the ids only so the panel can list someone who is
 * offline; the id is what counts.</p>
 */
public final class StaffData extends SavedData {

    private static final String FILE = "mlum_staff";

    public final Map<String, StaffRank> ranks = new LinkedHashMap<>();
    public final Map<UUID, String> members = new LinkedHashMap<>();
    public final Map<UUID, String> names = new LinkedHashMap<>();

    public static StaffData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("no overworld to store staff ranks on");
        }
        return overworld.getDataStorage().computeIfAbsent(StaffData::load, StaffData::new, FILE);
    }

    @Nullable
    public StaffRank rankOf(UUID player) {
        String id = members.get(player);
        return id == null ? null : ranks.get(id);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (StaffRank r : ranks.values()) {
            list.add(r.save());
        }
        tag.put("Ranks", list);
        ListTag mem = new ListTag();
        for (Map.Entry<UUID, String> e : members.entrySet()) {
            CompoundTag m = new CompoundTag();
            m.putUUID("Id", e.getKey());
            m.putString("Rank", e.getValue());
            m.putString("Name", names.getOrDefault(e.getKey(), ""));
            mem.add(m);
        }
        tag.put("Members", mem);
        return tag;
    }

    public static StaffData load(CompoundTag tag) {
        StaffData d = new StaffData();
        ListTag list = tag.getList("Ranks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            StaffRank r = StaffRank.load(list.getCompound(i));
            if (!r.id.isEmpty()) {
                d.ranks.put(r.id, r);
            }
        }
        ListTag mem = tag.getList("Members", Tag.TAG_COMPOUND);
        for (int i = 0; i < mem.size(); i++) {
            CompoundTag m = mem.getCompound(i);
            UUID id = m.getUUID("Id");
            d.members.put(id, m.getString("Rank"));
            d.names.put(id, m.getString("Name"));
        }
        return d;
    }
}

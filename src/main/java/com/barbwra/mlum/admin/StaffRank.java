package com.barbwra.mlum.admin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;

/** An admin rank: a name, a colour, and the permissions it carries. */
public final class StaffRank {

    public final String id;
    public String name;
    public int color;
    public final Set<String> perms = new LinkedHashSet<>();

    public StaffRank(String id, String name, int color) {
        this.id = id;
        this.name = name;
        this.color = color;
    }

    public boolean has(String node) {
        for (String p : perms) {
            if (Perms.covers(p, node)) {
                return true;
            }
        }
        return false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Name", name);
        tag.putInt("Color", color);
        ListTag list = new ListTag();
        for (String p : perms) {
            list.add(StringTag.valueOf(p));
        }
        tag.put("Perms", list);
        return tag;
    }

    public static StaffRank load(CompoundTag tag) {
        StaffRank r = new StaffRank(tag.getString("Id"), tag.getString("Name"), tag.getInt("Color"));
        ListTag list = tag.getList("Perms", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            r.perms.add(list.getString(i));
        }
        return r;
    }
}

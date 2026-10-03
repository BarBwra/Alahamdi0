package com.barbwra.mlum.admin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Support tickets: a player writes what is wrong, staff see it in the panel, go to where it was
 * written, and close it when it is dealt with. Kept with the world, newest first.
 */
public final class Tickets extends SavedData {

    private static final String FILE = "mlum_tickets";
    private static final int KEEP = 200;

    public static final class Ticket {
        public final int id;
        public final UUID player;
        public final String name;
        public final String text;
        public final String dim;
        public final double x;
        public final double y;
        public final double z;
        public final long at;
        public boolean closed;
        public String closedBy = "";

        Ticket(int id, UUID player, String name, String text, String dim, double x, double y, double z, long at) {
            this.id = id;
            this.player = player;
            this.name = name;
            this.text = text;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.at = at;
        }
    }

    private final List<Ticket> tickets = new ArrayList<>();
    private int nextId = 1;

    public static Tickets get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("no overworld");
        }
        return overworld.getDataStorage().computeIfAbsent(Tickets::load, Tickets::new, FILE);
    }

    public List<Ticket> all() {
        return tickets;
    }

    public Ticket byId(int id) {
        for (Ticket t : tickets) {
            if (t.id == id) {
                return t;
            }
        }
        return null;
    }

    public int openCount() {
        int n = 0;
        for (Ticket t : tickets) {
            if (!t.closed) {
                n++;
            }
        }
        return n;
    }

    public Ticket open(ServerPlayer from, String text) {
        Ticket t = new Ticket(nextId++, from.getUUID(), from.getGameProfile().getName(), text,
                from.level().dimension().location().toString(), from.getX(), from.getY(), from.getZ(),
                System.currentTimeMillis());
        tickets.add(0, t);
        while (tickets.size() > KEEP) {
            tickets.remove(tickets.size() - 1);
        }
        setDirty();
        return t;
    }

    public void close(Ticket t, String by) {
        t.closed = true;
        t.closedBy = by;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Ticket t : tickets) {
            CompoundTag c = new CompoundTag();
            c.putInt("Id", t.id);
            c.putUUID("Player", t.player);
            c.putString("Name", t.name);
            c.putString("Text", t.text);
            c.putString("Dim", t.dim);
            c.putDouble("X", t.x);
            c.putDouble("Y", t.y);
            c.putDouble("Z", t.z);
            c.putLong("At", t.at);
            c.putBoolean("Closed", t.closed);
            c.putString("ClosedBy", t.closedBy);
            list.add(c);
        }
        tag.put("Tickets", list);
        tag.putInt("Next", nextId);
        return tag;
    }

    public static Tickets load(CompoundTag tag) {
        Tickets d = new Tickets();
        ListTag list = tag.getList("Tickets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            Ticket t = new Ticket(c.getInt("Id"), c.getUUID("Player"), c.getString("Name"), c.getString("Text"),
                    c.getString("Dim"), c.getDouble("X"), c.getDouble("Y"), c.getDouble("Z"), c.getLong("At"));
            t.closed = c.getBoolean("Closed");
            t.closedBy = c.getString("ClosedBy");
            d.tickets.add(t);
        }
        d.nextId = Math.max(1, tag.getInt("Next"));
        return d;
    }
}

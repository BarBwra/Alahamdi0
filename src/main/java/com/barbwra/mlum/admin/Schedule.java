package com.barbwra.mlum.admin;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The events calendar: commands that run by themselves at a set time on set days.
 *
 * <p>Every event on the server - this mod's or anyone else's - is started by a command, so a
 * calendar of commands schedules all of them, including ones that do not exist yet. Each entry has
 * a name, the days of the week, an hour and minute on the server's clock, and the command; it runs
 * once at that minute, from the console, whether or not anyone is online.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class Schedule extends SavedData {

    private static final String FILE = "mlum_schedule";

    public static final class Job {
        public final int id;
        public String name;
        /** Bit 0 Monday ... bit 6 Sunday. */
        public int days;
        public int hour;
        public int minute;
        public String command;
        /** The day it last ran, as yyyymmdd*10000+hhmm, so a minute is never run twice. */
        public long lastRun;

        public Job(int id, String name, int days, int hour, int minute, String command) {
            this.id = id;
            this.name = name;
            this.days = days;
            this.hour = hour;
            this.minute = minute;
            this.command = command;
        }
    }

    private final List<Job> jobs = new ArrayList<>();
    private int nextId = 1;

    public static Schedule get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("no overworld");
        }
        return overworld.getDataStorage().computeIfAbsent(Schedule::load, Schedule::new, FILE);
    }

    public List<Job> all() {
        return jobs;
    }

    public Job add(String name, int days, int hour, int minute, String command) {
        Job j = new Job(nextId++, name, days & 0x7F, Math.max(0, Math.min(23, hour)), Math.max(0, Math.min(59, minute)), command);
        jobs.add(j);
        setDirty();
        return j;
    }

    public void remove(int id) {
        jobs.removeIf(j -> j.id == id);
        setDirty();
    }

    public static void run(MinecraftServer server, String command) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
        } catch (Exception broken) {
            MlumInventory.LOGGER.warn("[{}] scheduled command failed: {}", MlumInventory.MODID, cmd, broken);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 100 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        Schedule data = get(server);
        if (data.jobs.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int bit = 1 << (now.getDayOfWeek().getValue() - 1);
        long stamp = (now.getYear() * 10000L + now.getMonthValue() * 100L + now.getDayOfMonth()) * 10000L
                + now.getHour() * 100L + now.getMinute();
        for (Job j : data.jobs) {
            if ((j.days & bit) != 0 && j.hour == now.getHour() && j.minute == now.getMinute() && j.lastRun != stamp) {
                j.lastRun = stamp;
                data.setDirty();
                MlumInventory.LOGGER.info("[{}] scheduled event '{}': {}", MlumInventory.MODID, j.name, j.command);
                run(server, j.command);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Job j : jobs) {
            CompoundTag c = new CompoundTag();
            c.putInt("Id", j.id);
            c.putString("Name", j.name);
            c.putInt("Days", j.days);
            c.putInt("Hour", j.hour);
            c.putInt("Minute", j.minute);
            c.putString("Command", j.command);
            c.putLong("LastRun", j.lastRun);
            list.add(c);
        }
        tag.put("Jobs", list);
        tag.putInt("Next", nextId);
        return tag;
    }

    public static Schedule load(CompoundTag tag) {
        Schedule d = new Schedule();
        ListTag list = tag.getList("Jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            Job j = new Job(c.getInt("Id"), c.getString("Name"), c.getInt("Days"), c.getInt("Hour"), c.getInt("Minute"), c.getString("Command"));
            j.lastRun = c.getLong("LastRun");
            d.jobs.add(j);
        }
        d.nextId = Math.max(1, tag.getInt("Next"));
        return d;
    }
}

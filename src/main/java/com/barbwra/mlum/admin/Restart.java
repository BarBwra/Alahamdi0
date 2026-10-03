package com.barbwra.mlum.admin;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Restarting the server on a timetable, with warnings everyone sees.
 *
 * <p>Restarts come from two places: the daily times in the config ({@code restartTimes}, server
 * clock) and the admin panel's "in N minutes". Ten, five and one minute before, and every second of
 * the last ten, every player gets the countdown on screen. At zero the world is saved and the server
 * stops; whatever starts the server again (the host's panel or a start script that loops) brings it
 * back.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class Restart {

    private Restart() {
    }

    /** When the next restart happens, in epoch millis, or 0 for none planned. */
    private static long at;
    private static boolean manual;
    private static int lastWarned = -1;

    public static long at() {
        return at;
    }

    public static void inMinutes(int minutes) {
        at = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;
        manual = true;
        lastWarned = -1;
    }

    public static void cancel(MinecraftServer server) {
        at = 0L;
        manual = false;
        lastWarned = -1;
        CompoundTag tag = new CompoundTag();
        tag.putInt("Seconds", -1);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Staff.send(p, "restart", tag);
        }
    }

    /** The next daily time from the config, or 0. */
    private static long nextDaily() {
        LocalDateTime now = LocalDateTime.now();
        long best = 0L;
        for (String s : MlumConfig.restartTimes()) {
            try {
                LocalTime t = LocalTime.parse(s.trim());
                LocalDateTime when = now.toLocalDate().atTime(t);
                if (!when.isAfter(now.plusSeconds(5))) {
                    when = when.plus(1, ChronoUnit.DAYS);
                }
                long ms = when.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if (best == 0L || ms < best) {
                    best = ms;
                }
            } catch (Exception bad) {
                // a malformed time is skipped
            }
        }
        return best;
    }

    private static final int[] WARN = {600, 300, 60, 30, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (!manual) {
            long daily = nextDaily();
            if (daily != at) {
                at = daily;
                lastWarned = -1;
            }
        }
        if (at <= 0L) {
            return;
        }
        int left = (int) Math.ceil((at - System.currentTimeMillis()) / 1000.0D);
        for (int w : WARN) {
            if (left <= w && (lastWarned < 0 || w < lastWarned) && left > w - 2) {
                lastWarned = w;
                CompoundTag tag = new CompoundTag();
                tag.putInt("Seconds", Math.max(0, left));
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    Staff.send(p, "restart", tag);
                }
                break;
            }
        }
        if (left <= 0) {
            at = 0L;
            manual = false;
            MlumInventory.LOGGER.info("[{}] scheduled restart: saving and stopping", MlumInventory.MODID);
            server.getPlayerList().saveAll();
            server.saveEverything(false, true, true);
            server.halt(false);
        }
    }
}

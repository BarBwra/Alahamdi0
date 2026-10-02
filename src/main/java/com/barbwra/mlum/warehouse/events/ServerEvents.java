package com.barbwra.mlum.warehouse.events;

import com.barbwra.mlum.warehouse.WarehouseMod;
import com.barbwra.mlum.warehouse.command.WarehouseCommand;
import com.barbwra.mlum.warehouse.core.Warehouse;
import com.barbwra.mlum.warehouse.data.WarehouseData;
import com.barbwra.mlum.warehouse.mission.MissionService;
import com.barbwra.mlum.warehouse.net.TerminalSession;
import com.barbwra.mlum.warehouse.sched.DeadlineScheduler;
import com.barbwra.mlum.warehouse.service.ProductionService;
import com.barbwra.mlum.warehouse.service.StorageService;
import com.barbwra.mlum.warehouse.service.WarehouseService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The mod's only repeating work, and it is deliberately almost nothing.
 *
 * <p>Each tick this drains {@link DeadlineScheduler} of whatever is actually due. It does not walk
 * the warehouse list, it does not walk the mission list, and it does not decrement anything. The
 * heavier housekeeping - market samples, spoilage sweeps - is rate-limited to a few times a minute
 * and is itself gated on wall-clock intervals inside the services.</p>
 *
 * <p>For contrast, the prototype ran a full scan of every active mission every second and a full
 * scan of every craft on the server every five seconds, whether or not a single one was due.</p>
 */
@Mod.EventBusSubscriber(modid = WarehouseMod.MODID)
public final class ServerEvents {

    private ServerEvents() {
    }

    /** Housekeeping cadence in ticks. The work behind it is gated on real time regardless. */
    private static final int HOUSEKEEPING_TICKS = 100;
    private static final int SWEEP_EVERY = 30;

    private static int secondCounter;
    private static int tickCounter;
    private static int housekeepingCounter;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        long now = System.currentTimeMillis();
        DeadlineScheduler.get().tick(now);

        // Deliveries need a heartbeat rather than a deadline: proximity to the drop and the exposed
        // runner's broadcast position both change continuously, so there is no single instant to
        // schedule. It returns immediately when nothing is in flight.
        if (++secondCounter >= 20) {
            secondCounter = 0;
            MissionService.tick(event.getServer(), now);
        }

        if (++tickCounter < HOUSEKEEPING_TICKS) {
            return;
        }
        tickCounter = 0;

        MinecraftServer server = event.getServer();
        WarehouseData data = WarehouseData.get(server);

        if (data.market().tick(now)) {
            data.setDirty();
        }

        // Spoilage roughly every two and a half minutes. Crates carry an absolute expiry, so the
        // exact cadence only affects how promptly the shelf is tidied, never whether it happens.
        if (++housekeepingCounter >= SWEEP_EVERY) {
            housekeepingCounter = 0;
            int swept = 0;
            for (Warehouse warehouse : data.warehouses()) {
                swept += StorageService.sweepSpoiled(warehouse, now);
            }
            if (swept > 0) {
                data.setDirty();
            }
        }
    }

    /**
     * Rebuilds the scheduler from persisted state.
     *
     * <p>Nothing timed is stored in the queue itself - see {@link DeadlineScheduler} - so this is
     * where every absolute deadline on disk becomes a live callback again. Anything whose moment
     * passed during downtime fires on the next tick.</p>
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Checked here so the licence verdict lands in the log at boot, not on the first player
        // who happens to open a terminal.
        com.barbwra.mlum.warehouse.License.isValid();
        ProductionService.rearmAll(event.getServer());
        MissionService.rearmAll(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DeadlineScheduler.get().clear();
        secondCounter = 0;
        tickCounter = 0;
        housekeepingCounter = 0;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        WarehouseService.touch(server, player);

        Warehouse warehouse = WarehouseService.of(server, player);
        if (warehouse != null && StorageService.sweepSpoiled(warehouse, System.currentTimeMillis()) > 0) {
            WarehouseData.get(server).setDirty();
        }
    }

    /**
     * Drops the player's terminal session.
     *
     * <p>A client that crashes, times out or is kicked never sends its {@code CLOSE} action, so
     * without this the session map gains a permanent entry per disconnect. On a dedicated server
     * running for weeks that is an unbounded leak; in singleplayer it is invisible because the map
     * dies with the process, which is exactly the class of bug that only ever shows up in
     * production.</p>
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TerminalSession.onLogout(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // registered as the "warehouse" branch of /mlum by MlumCommands
    }
}

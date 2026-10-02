package com.barbwra.mlum.warehouse.events;

import com.barbwra.mlum.warehouse.WarehouseMod;
import com.barbwra.mlum.warehouse.WarehouseConfig;
import com.barbwra.mlum.warehouse.data.WarehouseData;
import com.barbwra.mlum.warehouse.mission.Mission;
import com.barbwra.mlum.warehouse.mission.MissionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The world-facing half of a delivery: dying, disconnecting, and trying to get in a vehicle.
 *
 * <p><b>Cargo is carried on foot.</b> {@link #onMount} refuses every vehicle, horse, boat and
 * rideable entity for the duration of a run. That is the mechanic, not a limitation: the leak timer
 * only means something if closing the distance to the drop actually costs the runner time on the
 * ground where other players can reach them. The prototype had a half-written function that spawned
 * a van and mounted the nearest entity within three blocks - which, after a two-tick wait, was as
 * likely to be a zombie or somebody else's vehicle - and it was never called from anywhere.</p>
 */
@Mod.EventBusSubscriber(modid = WarehouseMod.MODID)
public final class MissionEvents {

    private MissionEvents() {
    }

    /** Rate-limits the "you can't ride that" notice so holding right-click is not a chat flood. */
    private static long lastMountNotice;

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (!WarehouseConfig.blockMounts() || !event.isMounting()) {
            return;
        }
        if (!(event.getEntityMounting() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }

        Mission mission = WarehouseData.get(player.getServer()).mission(player.getUUID());
        if (mission == null || !mission.state().isRunning()) {
            return;
        }

        event.setCanceled(true);

        long now = System.currentTimeMillis();
        if (now - lastMountNotice > 2000L) {
            lastMountNotice = now;
            player.sendSystemMessage(Component.literal(
                    "§c⚠ §fلا يمكنك ركوب أي مركبة أثناء نقل البضاعة."));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim.getServer() == null) {
            return;
        }
        MinecraftServer server = victim.getServer();

        // Resolves an arrow or a thrown potion back to whoever fired it.
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer attacker
                ? attacker
                : null;

        MissionService.onDeath(server, victim, killer);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            MissionService.onLogout(player.getServer(), player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            MissionService.onLogin(player.getServer(), player);
        }
    }
}

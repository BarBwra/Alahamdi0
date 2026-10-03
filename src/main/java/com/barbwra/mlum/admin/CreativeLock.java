package com.barbwra.mlum.admin;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.util.Feedback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Creative mode is for server operators only - no admin rank can reach it.
 *
 * <p>Creative is the one mode that hands out any item for free, straight from the item list. So
 * whatever commands a rank is given, {@code /gamemode} included, a player who is not an operator is
 * refused creative: the switch is cancelled, and anyone found in it anyway (a join, another mod,
 * a command block) is put back in survival. Spectator stays available for watching players.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class CreativeLock {

    private CreativeLock() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChangeMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getNewGameMode() == GameType.CREATIVE && event.getEntity() instanceof ServerPlayer player
                && !Staff.isOp(player)) {
            event.setCanceled(true);
            Feedback.bad(player, "الكريتف للـ OP بس");
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            check(player);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount % 40 == 0) {
            check(player);
        }
    }

    private static void check(ServerPlayer player) {
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE && !Staff.isOp(player)) {
            player.setGameMode(GameType.SURVIVAL);
            Feedback.bad(player, "الكريتف للـ OP بس");
        }
    }
}

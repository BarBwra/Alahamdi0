package com.barbwra.mlum.skill;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.loot.LootRules;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.network.S2CScoutInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * الباحث, level 3: the server tells the scout which of the containers around them are empty.
 *
 * <p>Every forty ticks, for scouts at the top level only, the containers within the level 3 range
 * are looked up and their emptiness sent as one small packet. The client draws the empty ones grey,
 * so a scout does not walk into a house for a chest that has nothing in it.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class ScoutInfo {

    private ScoutInfo() {
    }

    private static final int RANGE = 16;
    private static final int EVERY = 40;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % EVERY != 0 || SkillService.level(player, SkillEffects.SCOUT) < 3) {
            return;
        }
        Level level = player.level();
        BlockPos origin = player.blockPosition();
        int span = (RANGE >> 4) + 1;
        List<Long> keys = new ArrayList<>();
        List<Boolean> empty = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow((origin.getX() >> 4) + dx, (origin.getZ() >> 4) + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (pos.distSqr(origin) > RANGE * RANGE || !LootRules.isLootable(be)) {
                        continue;
                    }
                    long key = LootRules.key(pos, LootRules.partner(level, pos, be.getBlockState()));
                    if (!seen.add(key)) {
                        continue;
                    }
                    keys.add(key);
                    empty.add(isEmpty(be));
                    if (keys.size() >= 256) {
                        break;
                    }
                }
            }
        }
        long[] k = new long[keys.size()];
        boolean[] e = new boolean[keys.size()];
        for (int i = 0; i < k.length; i++) {
            k[i] = keys.get(i);
            e[i] = empty.get(i);
        }
        if (player.connection != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CScoutInfo(k, e));
        }
    }

    private static boolean isEmpty(BlockEntity be) {
        if (be instanceof Container container) {
            return container.isEmpty();
        }
        try {
            IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (handler == null) {
                return false;
            }
            for (int i = 0; i < handler.getSlots(); i++) {
                if (!handler.getStackInSlot(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        } catch (Throwable broken) {
            return false;
        }
    }
}

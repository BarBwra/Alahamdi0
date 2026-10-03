package com.barbwra.mlum.loot;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.events.ServerEvents;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.network.S2CLootSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Searching: a container opens after a short search instead of on the click.
 *
 * <h2>How a search runs</h2>
 * <p>The right-click that would have opened the container starts a search instead. One click is
 * enough - the button does not have to be held. The search lives while the player stays where they
 * started, stays in reach, and nothing hurts them; a left click calls it off. When it finishes, the
 * container is opened through exactly the path a click would have taken - this mod's own chest
 * screen for vanilla chests and barrels, and the block's own behaviour for everything else.</p>
 *
 * <h2>Shift</h2>
 * <p>Held during a search, Shift switches it to the fast rate. A fast search may knock something
 * over: once per search it rolls the noise chance (less with the quiet hands skill), and if it hits, at some random point the search
 * stalls, a clatter plays that everyone nearby hears, and hostiles within the radius come for the
 * player. Fast is a trade, not a shortcut.</p>
 *
 * <h2>Why the server times it</h2>
 * <p>The client only draws. A modified client could skip its own animation, but it cannot make
 * this server open a container any sooner, because the server is the one that opens it.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class LootSearch {

    private LootSearch() {
    }

    /** Further than this from where the search started and it is cancelled. */
    private static final double MOVE_LIMIT_SQ = 0.75D * 0.75D;
    private static final double REACH_SQ = 6.0D * 6.0D;

    private static final class Session {
        final BlockPos pos;
        final long key;
        final BlockHitResult hit;
        final Vec3 origin;
        float progress;
        int pausedUntil;
        boolean rolled;
        float noiseAt = -1.0F;
        int ticks;

        Session(BlockPos pos, long key, BlockHitResult hit, Vec3 origin) {
            this.pos = pos;
            this.key = key;
            this.hit = hit;
            this.origin = origin;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Players whose next right-click is the search finishing, not a new search. */
    private static final Set<UUID> OPENING = new HashSet<>();

    public static boolean isSearching(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /* ================================================================== the click */

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!MlumConfig.lootSearch() || OPENING.contains(player.getUUID())
                || player.isSpectator() || player.isCreative()) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (!LootRules.isLootable(be)) {
            return;
        }
        long key = LootRules.key(pos, LootRules.partner(level, pos, state));

        Session running = SESSIONS.get(player.getUUID());
        if (running != null && running.key == key) {
            // already searching this one - a second click, or vanilla repeating a held button.
            // Cancelled so a held Shift can never place a block
            consume(event);
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            if (running != null) {
                consume(event);
            }
            return;
        }
        boolean holding = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
        if (player.isSecondaryUseActive() && holding) {
            return;   // vanilla: sneak with something in hand places it - a fresh click keeps that
        }
        if (com.barbwra.mlum.downed.DownedService.isDowned(player)) {
            consume(event);
            return;
        }
        if (running != null) {
            cancel(player, running);
        }
        Session session = new Session(pos.immutable(), key, event.getHitVec(), player.position());
        SESSIONS.put(player.getUUID(), session);
        rummage(level, pos);
        send(player, S2CLootSearch.PROGRESS, session);
        consume(event);
    }

    private static void consume(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    /** The player called the search off - a left click. */
    public static void cancelFromClient(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s != null) {
            cancel(player, s);
        }
    }

    /* ================================================================== the clock */

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Session s = e.getValue();
            if (player == null || player.isRemoved()) {
                it.remove();
                continue;
            }
            if (!stillValid(player, s)) {
                it.remove();
                send(player, S2CLootSearch.CANCEL, s);
                continue;
            }
            s.ticks++;
            boolean fast = player.isShiftKeyDown();
            if (fast && !s.rolled) {
                s.rolled = true;
                if (player.getRandom().nextFloat() < com.barbwra.mlum.skill.SkillEffects.noiseChance(player, MlumConfig.lootNoiseChance())) {
                    float from = Math.max(s.progress, 0.15F);
                    s.noiseAt = from + player.getRandom().nextFloat() * Math.max(0.0F, 0.85F - from);
                }
            }
            if (now < s.pausedUntil) {
                send(player, S2CLootSearch.PROGRESS, s, fast, true);
                continue;
            }
            double seconds = fast ? MlumConfig.lootFastSeconds() : MlumConfig.lootSeconds();
            s.progress = seconds <= 0.0D ? 1.0F : s.progress + (float) (1.0D / (seconds * 20.0D));
            if (s.noiseAt >= 0.0F && s.progress >= s.noiseAt) {
                s.progress = s.noiseAt;
                s.noiseAt = -1.0F;
                s.pausedUntil = now + (int) Math.round(MlumConfig.lootNoisePause() * 20.0D);
                noise(player, s.pos);
                send(player, S2CLootSearch.NOISE, s, fast, true);
                continue;
            }
            if (!fast && s.ticks % 12 == 0) {
                rummage(player.level(), s.pos);
            }
            if (s.progress >= 1.0F) {
                it.remove();
                send(player, S2CLootSearch.DONE, s);
                open(player, s);
                continue;
            }
            send(player, S2CLootSearch.PROGRESS, s, fast, false);
        }
    }

    private static boolean stillValid(ServerPlayer player, Session s) {
        if (player.containerMenu != player.inventoryMenu) {
            return false;
        }
        if (player.position().distanceToSqr(s.origin) > MOVE_LIMIT_SQ) {
            return false;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(s.pos)) > REACH_SQ) {
            return false;
        }
        return LootRules.isLootable(player.level().getBlockEntity(s.pos));
    }

    /** Taking damage breaks off a search. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Session s = SESSIONS.get(player.getUUID());
            if (s != null) {
                cancel(player, s);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
        OPENING.remove(event.getEntity().getUUID());
    }

    private static void cancel(ServerPlayer player, Session s) {
        SESSIONS.remove(player.getUUID());
        send(player, S2CLootSearch.CANCEL, s);
    }

    /* ================================================================== finishing */

    private static void open(ServerPlayer player, Session s) {
        Level level = player.level();
        BlockState state = level.getBlockState(s.pos);
        OPENING.add(player.getUUID());
        try {
            if (!ServerEvents.tryTakeover(player, s.pos, state)) {
                state.use(level, player, InteractionHand.MAIN_HAND, s.hit);
            }
        } catch (Exception broken) {
            MlumInventory.LOGGER.warn("[{}] opening {} after a search failed", MlumInventory.MODID, s.pos, broken);
        } finally {
            OPENING.remove(player.getUUID());
        }
    }

    private static void rummage(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.BLOCKS,
                0.55F, 0.85F + level.getRandom().nextFloat() * 0.3F);
    }

    /** Something knocked over: a clatter everyone near hears, and the hostiles in range come running. */
    private static void noise(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5F, 1.7F);
        level.playSound(null, pos, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.BLOCKS, 1.0F, 0.6F);
        int radius = MlumConfig.lootNoiseRadius();
        if (radius <= 0) {
            return;
        }
        AABB box = new AABB(pos).inflate(radius);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive())) {
            mob.setTarget(player);
            mob.getNavigation().moveTo(player, 1.0D);
        }
    }

    /* ================================================================== telling the client */

    private static void send(ServerPlayer player, int state, Session s) {
        send(player, state, s, player.isShiftKeyDown(), false);
    }

    private static void send(ServerPlayer player, int state, Session s, boolean fast, boolean paused) {
        if (player.connection == null) {
            return;
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CLootSearch(state, s.pos, Math.min(1.0F, s.progress), fast, paused));
    }
}

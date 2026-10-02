package com.barbwra.mlum.warehouse.mission;

import com.barbwra.mlum.warehouse.WarehouseMod;
import com.barbwra.mlum.warehouse.WarehouseConfig;
import com.barbwra.mlum.warehouse.catalog.Catalog;
import com.barbwra.mlum.warehouse.catalog.Route;
import com.barbwra.mlum.warehouse.core.Crate;
import com.barbwra.mlum.warehouse.core.UpgradePath;
import com.barbwra.mlum.warehouse.core.Warehouse;
import com.barbwra.mlum.warehouse.data.WarehouseData;
import com.barbwra.mlum.warehouse.market.MarketState;
import com.barbwra.mlum.warehouse.net.ModNetwork;
import com.barbwra.mlum.warehouse.net.S2CMissionHud;
import com.barbwra.mlum.warehouse.service.EconomyService;
import com.barbwra.mlum.warehouse.service.StorageService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The delivery state machine: quoting, dispatching, leaking, settling and failing.
 *
 * <p>Everything here is driven from absolute deadlines stored on the {@link Mission} and a single
 * once-per-second pass over the missions that are actually in flight. There is no per-player timer
 * being decremented anywhere.</p>
 *
 * <p><b>Buffs are transient attribute modifiers, not potions.</b> That one change closes the
 * prototype's worst leak: it applied Speed, Absorption and Resistance "for 999 days" and removed
 * them only in a cleanup path that needed an online player, so any crash, restart or logout during
 * a run left the player permanently buffed - up to Speed VI. Transient modifiers are never written
 * to the player's save data, so a crash clears them by definition, and {@link #clearBuffs} is
 * idempotent so running it twice is harmless.</p>
 *
 * <p><b>Delivery is proximity, checked server-side, every second.</b> There is no confirm command
 * for a player to run. The prototype's {@code /confirmsellwherehouse} took a player argument and
 * only checked distance, which meant the whole delivery step was a distance test anyone with the
 * permission could aim at anybody.</p>
 */
public final class MissionService {

    private MissionService() {
    }

    /* Fixed ids so a modifier can always be removed, even after a restart re-applies it. */
    private static final UUID SPEED_MODIFIER = UUID.fromString("6d1b4f1a-6a1e-4c9b-9c4a-6a1f5d2c7b01");
    private static final UUID ARMOR_MODIFIER = UUID.fromString("6d1b4f1a-6a1e-4c9b-9c4a-6a1f5d2c7b02");
    private static final UUID TOUGHNESS_MODIFIER = UUID.fromString("6d1b4f1a-6a1e-4c9b-9c4a-6a1f5d2c7b03");

    /** Movement bonus per Speed level. Five levels is +30%, well short of the old Speed VI. */
    private static final double SPEED_PER_LEVEL = 0.06D;
    private static final double ARMOR_PER_LEVEL = 2.0D;
    private static final double TOUGHNESS_PER_LEVEL = 1.0D;

    /** How often the exposed-runner bar refreshes its coordinates. */
    private static final long LEAK_REFRESH_MILLIS = 2000L;

    /** Transient - rebuilt on demand, never persisted. */
    private static final Map<UUID, ServerBossEvent> LEAK_BARS = new HashMap<>();
    private static final Map<UUID, Long> LAST_LEAK_REFRESH = new HashMap<>();

    public enum DispatchResult {
        OK,
        NO_WAREHOUSE,
        ALREADY_RUNNING,
        UNKNOWN_ROUTE,
        NO_CRATES,
        TOO_MANY_CRATES,
        /** A requested crate is missing, spoiled, or already committed to another run. */
        INVALID_CRATE,
        /** The route names a dimension the server does not have. */
        BAD_DESTINATION
    }

    /* ================================================================= quoting */

    /**
     * Prices a convoy without committing to it.
     *
     * <p>Safe to call repeatedly - it mutates nothing, which is what lets the dispatch screen
     * re-quote every time the player adds or removes a crate.</p>
     */
    public static PayoutQuote quote(MinecraftServer server, Warehouse warehouse,
                                    List<UUID> crateIds, Route route, long now) {
        WarehouseData data = WarehouseData.get(server);
        MarketState market = data.market();

        long cargo = 0L;
        for (UUID id : crateIds) {
            Crate crate = warehouse.crate(id);
            if (crate != null && !crate.isSpoiled(now)) {
                cargo += StorageService.currentValue(crate, market, now);
            }
        }

        double routeMult = route == null ? 1.0D : route.multiplier();
        int online = server.getPlayerCount();
        double population = WarehouseConfig.populationMultiplier(online);
        double event = data.isEventActive(now) ? WarehouseConfig.eventMultiplier() : 1.0D;

        int total = (int) Math.floor(cargo * routeMult * population * event);

        // Arabic, because the panel this lands in is Arabic. The old English "cargo/route/
        // population" lines were developer output leaking into a player-facing screen.
        List<String> breakdown = new ArrayList<>();
        breakdown.add("قيمة البضاعة: " + cargo + "$");
        breakdown.add(String.format("الطريق: ×%.2f", routeMult));
        breakdown.add(String.format("عدد اللاعبين (%d): ×%.2f", online, population));
        if (event > 1.0D) {
            breakdown.add(String.format("حدث مضاعف: ×%.2f", event));
        }

        return new PayoutQuote(cargo, routeMult, population, event, total, breakdown);
    }

    /* =============================================================== dispatch */

    public static DispatchResult dispatch(MinecraftServer server, ServerPlayer player,
                                          String routeId, List<UUID> crateIds) {
        if (!com.barbwra.mlum.warehouse.License.gate(player)) {
            return DispatchResult.NO_WAREHOUSE;
        }
        WarehouseData data = WarehouseData.get(server);
        Warehouse warehouse = data.warehouse(player.getUUID());
        if (warehouse == null) {
            return DispatchResult.NO_WAREHOUSE;
        }
        if (data.mission(player.getUUID()) != null) {
            return DispatchResult.ALREADY_RUNNING;
        }

        Route route = Catalog.get().route(routeId);
        if (route == null) {
            return DispatchResult.UNKNOWN_ROUTE;
        }
        if (crateIds.isEmpty()) {
            return DispatchResult.NO_CRATES;
        }
        if (crateIds.size() > WarehouseConfig.maxConvoy()) {
            return DispatchResult.TOO_MANY_CRATES;
        }

        ServerLevel level = server.getLevel(route.levelKey());
        if (level == null) {
            return DispatchResult.BAD_DESTINATION;
        }

        long now = System.currentTimeMillis();

        // Validate the whole manifest before removing anything, so a bad id cannot half-consume it.
        List<Crate> committed = new ArrayList<>(crateIds.size());
        for (UUID id : crateIds) {
            Crate crate = warehouse.crate(id);
            if (crate == null || crate.reserved() || crate.isSpoiled(now)) {
                return DispatchResult.INVALID_CRATE;
            }
            committed.add(crate);
        }

        PayoutQuote quote = quote(server, warehouse, crateIds, route, now);

        List<Mission.Manifest> manifest = new ArrayList<>(committed.size());
        for (Crate crate : committed) {
            manifest.add(new Mission.Manifest(crate.cargo(), crate.tier()));
            warehouse.removeCrate(crate.id());
        }

        Mission mission = new Mission(
                UUID.randomUUID(),
                player.getUUID(),
                route.id(),
                manifest,
                quote.total(),
                quote.breakdownText(),
                now,
                now + WarehouseConfig.leakSeconds() * 1000L,
                now + WarehouseConfig.expireSeconds() * 1000L);

        data.putMission(mission);
        data.setDirty();

        // Anything the player was riding stops being an option the moment cargo is picked up.
        player.stopRiding();
        Vec3 spawn = route.spawn();
        player.teleportTo(level, spawn.x, spawn.y, spawn.z, route.spawnYaw(), 0.0F);
        applyBuffs(player, warehouse);

        player.sendSystemMessage(Component.literal("§8[§6التهريب§8] §a✔ §fبدأت مهمة التوصيل بقيمة §e"
                + quote.total() + "§a$"));
        player.sendSystemMessage(Component.literal("§c⚠ §fلديك §e"
                + (WarehouseConfig.leakSeconds() / 60) + " دقائق §fقبل تسريب موقعك. §cلا يمكنك ركوب أي مركبة."));
        return DispatchResult.OK;
    }

    /* ================================================================ ticking */

    /**
     * One pass over the missions that are in flight. Called once a second.
     *
     * <p>Iterates a snapshot, because settling or failing a mission mutates the live map. The
     * prototype removed entries from the list it was looping over and silently skipped whichever
     * mission happened to follow.</p>
     */
    public static void tick(MinecraftServer server, long now) {
        WarehouseData data = WarehouseData.get(server);
        List<Mission> active = data.activeMissions();
        if (active.isEmpty()) {
            return;
        }

        for (Mission mission : active) {
            switch (mission.state()) {
                case GRACE -> {
                    if (now >= mission.graceUntil()) {
                        fail(server, mission, "grace");
                    }
                }
                case TRANSIT, EXPOSED -> tickRunning(server, data, mission, now);
                default -> data.removeMission(mission.runner());
            }
        }
    }

    private static void tickRunning(MinecraftServer server, WarehouseData data, Mission mission, long now) {
        ServerPlayer player = server.getPlayerList().getPlayer(mission.runner());
        if (player == null) {
            // Missed the logout event - park it rather than looping on a player who is not here.
            beginGrace(mission, now);
            data.setDirty();
            return;
        }

        if (now >= mission.expiresAt()) {
            fail(server, mission, "expired");
            return;
        }

        if (mission.state() == MissionState.TRANSIT && mission.hasLeaked(now)) {
            mission.setState(MissionState.EXPOSED);
            data.setDirty();
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "§c⚠ §fتم تسريب موقع مهرب! §e" + player.getGameProfile().getName()), false);
        }

        if (mission.state() == MissionState.EXPOSED) {
            refreshLeakBar(server, mission, player, now);
        }

        Route route = Catalog.get().route(mission.routeId());
        if (route == null) {
            return;
        }

        ModNetwork.sendMissionHud(player, new S2CMissionHud(true, mission.state().ordinal(),
                mission.leaksAt(), mission.expiresAt(), mission.quotedPayout(),
                mission.manifest().size(), route.drop().x, route.drop().z));

        ServerLevel level = server.getLevel(route.levelKey());
        if (level != null && player.level() == level) {
            double radius = WarehouseConfig.deliveryRadius();
            if (player.position().distanceToSqr(route.drop()) <= radius * radius) {
                settle(server, mission, player);
            }
        }
    }

    /** Tells a client to drop its HUD. Sent once, when a run ends for any reason. */
    private static void clearHud(MinecraftServer server, UUID runner) {
        ServerPlayer player = server.getPlayerList().getPlayer(runner);
        if (player != null) {
            ModNetwork.sendMissionHud(player, S2CMissionHud.INACTIVE);
        }
    }

    /* ================================================================ outcomes */

    private static void settle(MinecraftServer server, Mission mission, ServerPlayer player) {
        WarehouseData data = WarehouseData.get(server);
        Warehouse warehouse = data.warehouse(mission.runner());

        if (warehouse != null) {
            for (Mission.Manifest entry : mission.manifest()) {
                warehouse.recordDelivery(entry.cargo(), entry.tier());
                data.market().recordDelivery(entry.cargo(), 1);
            }
            warehouse.recordRun(System.currentTimeMillis(), mission.manifest().size(),
                    mission.quotedPayout(), true, mission.routeId());
        }

        mission.setState(MissionState.SETTLED);
        data.removeMission(mission.runner());
        data.setDirty();

        clearBuffs(player);
        clearLeakBar(mission);
        ModNetwork.sendMissionHud(player, S2CMissionHud.INACTIVE);

        EconomyService.pay(player, mission.quotedPayout());
        player.sendSystemMessage(Component.literal("§a✔ §fتم التسليم بنجاح! §e"
                + mission.quotedPayout() + "§a$"));

        WarehouseMod.LOGGER.info("[mwh] {} settled mission {} for {} ({})",
                player.getGameProfile().getName(), mission.id(), mission.quotedPayout(), mission.breakdown());
    }

    private static void fail(MinecraftServer server, Mission mission, String reason) {
        WarehouseData data = WarehouseData.get(server);
        mission.setState(MissionState.FAILED);

        Warehouse warehouse = data.warehouse(mission.runner());
        if (warehouse != null) {
            warehouse.recordRun(System.currentTimeMillis(), mission.manifest().size(),
                    0, false, mission.routeId());
        }

        data.removeMission(mission.runner());
        data.setDirty();

        ServerPlayer player = server.getPlayerList().getPlayer(mission.runner());
        if (player != null) {
            clearBuffs(player);
            player.sendSystemMessage(Component.literal(switch (reason) {
                case "expired" -> "§c[!] §fانتهى الوقت وتمت مصادرة البضاعة.";
                case "grace" -> "§c[!] §fتركت المهمة لفترة طويلة وتمت مصادرة البضاعة.";
                case "death" -> "§c☠ §fلقد مت وفقدت البضاعة.";
                default -> "§c[!] §fفشلت مهمة التوصيل.";
            }));
        }
        clearLeakBar(mission);
        clearHud(server, mission.runner());

        WarehouseMod.LOGGER.info("[mwh] Mission {} failed ({}).", mission.id(), reason);
    }

    /**
     * Called when a runner dies. A player killer takes a share of what the convoy was worth.
     *
     * <p>The share is taken from the quote as issued. The prototype applied the global 2x event to
     * the killer's cut <i>and</i> then halved it, so during an event a killer collected the full
     * undoubled value of a convoy they did not run.</p>
     */
    public static void onDeath(MinecraftServer server, ServerPlayer victim, ServerPlayer killer) {
        WarehouseData data = WarehouseData.get(server);
        Mission mission = data.mission(victim.getUUID());
        if (mission == null || !mission.state().isRunning()) {
            return;
        }

        if (killer != null && !killer.getUUID().equals(victim.getUUID())) {
            int share = (int) Math.floor(mission.quotedPayout() * WarehouseConfig.killerShare());
            if (share > 0) {
                EconomyService.pay(killer, share);
                killer.sendSystemMessage(Component.literal("§a✔ §fقتلت مهرباً وسرقت §e" + share + "§a$"));
            }
            victim.sendSystemMessage(Component.literal("§c☠ §fقتلك §e"
                    + killer.getGameProfile().getName() + " §fوسرق جزءاً من قيمة بضاعتك."));
        }

        fail(server, mission, "death");
    }

    /* =================================================== disconnect and return */

    public static void onLogout(MinecraftServer server, ServerPlayer player) {
        WarehouseData data = WarehouseData.get(server);
        Mission mission = data.mission(player.getUUID());
        if (mission != null && mission.state().isRunning()) {
            beginGrace(mission, System.currentTimeMillis());
            data.setDirty();
        }
        clearBuffs(player);
        if (mission != null) {
            clearLeakBar(mission);
        }
    }

    private static void beginGrace(Mission mission, long now) {
        mission.setState(MissionState.GRACE);
        mission.setGraceUntil(now + WarehouseConfig.graceSeconds() * 1000L);
    }

    /**
     * Resumes a parked run, crediting back the time spent offline.
     *
     * <p>Without the credit, reconnecting near the end of the grace window would hand the player a
     * hard deadline that had kept running in their absence - turning any disconnect into an
     * automatic loss even when they came straight back.</p>
     */
    public static void onLogin(MinecraftServer server, ServerPlayer player) {
        WarehouseData data = WarehouseData.get(server);
        Mission mission = data.mission(player.getUUID());
        if (mission == null) {
            clearBuffs(player);
            return;
        }
        if (mission.state() != MissionState.GRACE) {
            return;
        }

        long now = System.currentTimeMillis();
        long graceStarted = mission.graceUntil() - WarehouseConfig.graceSeconds() * 1000L;
        mission.extendBy(Math.max(0L, now - graceStarted));
        mission.setGraceUntil(0L);
        mission.setState(mission.hasLeaked(now) ? MissionState.EXPOSED : MissionState.TRANSIT);
        data.setDirty();

        Warehouse warehouse = data.warehouse(player.getUUID());
        if (warehouse != null) {
            applyBuffs(player, warehouse);
        }
        player.sendSystemMessage(Component.literal("§6[التهريب] §fتم استئناف مهمتك."));
    }

    /* =================================================================== buffs */

    public static void applyBuffs(ServerPlayer player, Warehouse warehouse) {
        clearBuffs(player);

        int speed = warehouse.upgradeLevel(UpgradePath.SPEED);
        if (speed > 0) {
            add(player.getAttribute(Attributes.MOVEMENT_SPEED),
                    new AttributeModifier(SPEED_MODIFIER, "mwh:cargo_speed",
                            SPEED_PER_LEVEL * speed, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }

        int armor = warehouse.upgradeLevel(UpgradePath.ARMOR);
        if (armor > 0) {
            add(player.getAttribute(Attributes.ARMOR),
                    new AttributeModifier(ARMOR_MODIFIER, "mwh:cargo_armor",
                            ARMOR_PER_LEVEL * armor, AttributeModifier.Operation.ADDITION));
            add(player.getAttribute(Attributes.ARMOR_TOUGHNESS),
                    new AttributeModifier(TOUGHNESS_MODIFIER, "mwh:cargo_toughness",
                            TOUGHNESS_PER_LEVEL * armor, AttributeModifier.Operation.ADDITION));
        }
    }

    private static void add(AttributeInstance instance, AttributeModifier modifier) {
        if (instance != null && !instance.hasModifier(modifier)) {
            // Transient: never written to the player's save data, so a crash cannot strand it.
            instance.addTransientModifier(modifier);
        }
    }

    /** Idempotent. Safe to call on a player who never had a mission. */
    public static void clearBuffs(ServerPlayer player) {
        remove(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER);
        remove(player.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER);
        remove(player.getAttribute(Attributes.ARMOR_TOUGHNESS), TOUGHNESS_MODIFIER);
    }

    private static void remove(AttributeInstance instance, UUID id) {
        if (instance != null && instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
    }

    /* ================================================================ leak bar */

    private static void refreshLeakBar(MinecraftServer server, Mission mission, ServerPlayer runner, long now) {
        Long last = LAST_LEAK_REFRESH.get(mission.id());
        if (last != null && now - last < LEAK_REFRESH_MILLIS) {
            return;
        }
        LAST_LEAK_REFRESH.put(mission.id(), now);

        ServerBossEvent bar = LEAK_BARS.computeIfAbsent(mission.id(), id -> {
            ServerBossEvent created = new ServerBossEvent(Component.empty(),
                    BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            created.setDarkenScreen(false);
            return created;
        });

        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            bar.addPlayer(online);
        }

        bar.setName(Component.literal("§cتعقب المهرب §e" + runner.getGameProfile().getName()
                        + " §8| §f" + (int) runner.getX() + ", " + (int) runner.getZ())
                .withStyle(ChatFormatting.RED));

        long total = Math.max(1L, mission.expiresAt() - mission.leaksAt());
        bar.setProgress(Math.max(0.0F, Math.min(1.0F, (float) (mission.expiresAt() - now) / total)));
    }

    private static void clearLeakBar(Mission mission) {
        ServerBossEvent bar = LEAK_BARS.remove(mission.id());
        if (bar != null) {
            bar.removeAllPlayers();
        }
        LAST_LEAK_REFRESH.remove(mission.id());
    }

    /* ================================================================== reboot */

    /**
     * Parks every mission that survived a restart.
     *
     * <p>Nobody is online at this point, so anything that was in flight is by definition unattended.
     * Putting it into grace gives the runner the configured window to log back in and resume rather
     * than losing the cargo to a restart they did not cause.</p>
     */
    public static void rearmAll(MinecraftServer server) {
        WarehouseData data = WarehouseData.get(server);
        long now = System.currentTimeMillis();
        int parked = 0;
        for (Mission mission : data.activeMissions()) {
            if (mission.state().isRunning()) {
                beginGrace(mission, now);
                parked++;
            }
        }
        if (parked > 0) {
            data.setDirty();
            WarehouseMod.LOGGER.info("[mwh] Parked {} in-flight mission(s) after restart.", parked);
        }
    }
}

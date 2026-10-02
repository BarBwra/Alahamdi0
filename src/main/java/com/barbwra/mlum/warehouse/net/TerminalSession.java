package com.barbwra.mlum.warehouse.net;

import com.barbwra.mlum.warehouse.catalog.Catalog;
import com.barbwra.mlum.warehouse.catalog.Ingredient;
import com.barbwra.mlum.warehouse.catalog.Recipe;
import com.barbwra.mlum.warehouse.catalog.Route;
import com.barbwra.mlum.warehouse.catalog.UpgradeNode;
import com.barbwra.mlum.warehouse.core.CargoType;
import com.barbwra.mlum.warehouse.core.CraftJob;
import com.barbwra.mlum.warehouse.core.Crate;
import com.barbwra.mlum.warehouse.core.UpgradePath;
import com.barbwra.mlum.warehouse.core.Warehouse;
import com.barbwra.mlum.warehouse.data.WarehouseData;
import com.barbwra.mlum.warehouse.market.MarketState;
import com.barbwra.mlum.warehouse.mission.MissionService;
import com.barbwra.mlum.warehouse.mission.PayoutQuote;
import com.barbwra.mlum.warehouse.service.EconomyService;
import com.barbwra.mlum.warehouse.service.ProductionService;
import com.barbwra.mlum.warehouse.service.StorageService;
import com.barbwra.mlum.warehouse.service.UpgradeService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The server's authority for one open terminal.
 *
 * <p>Every action arrives as an intent - "start recipe X", "toggle crate Y", "buy speed 3" - and is
 * checked against this object before anything happens:</p>
 * <ol>
 *   <li>Is there an open session for this player at all?</li>
 *   <li>Are they still standing within reach of the terminal block they opened?</li>
 *   <li>Are they inside their rate limit?</li>
 *   <li>Does the rule the action itself depends on hold, re-derived from server state?</li>
 * </ol>
 *
 * <p><b>Selection lives here, not on the client.</b> Which crates are in the convoy is server state
 * keyed to the session, so a client cannot dispatch crates it never selected, nor select the same
 * crate twice, nor keep a selection alive after the crate has spoiled. In the prototype the
 * selection was a list of <i>positions</i> in a global array, and deleting a spoiled crate shifted
 * every position after it while the player still had the sell screen open - so the confirm button
 * sold cargo they had never picked.</p>
 */
public final class TerminalSession {

    private static final int BUCKET_CAPACITY = 24;
    private static final long BUCKET_WINDOW_MILLIS = 3000L;

    private static final Map<UUID, TerminalSession> SESSIONS = new HashMap<>();

    private final UUID player;
    private final Set<UUID> selected = new LinkedHashSet<>();
    private String route;

    private int tokens = BUCKET_CAPACITY;
    private long windowStart = System.currentTimeMillis();

    private TerminalSession(UUID player) {
        this.player = player;
    }

    /* ------------------------------------------------------------- lifecycle */

    /**
     * Opens the terminal for a player.
     *
     * <p>There is no world anchor and no proximity requirement: the terminal is opened by command,
     * so there is no block to stand near. Authority rests entirely on session ownership - a session
     * belongs to exactly one player uuid and every action is resolved against <i>that</i> player's
     * warehouse, never against anything the packet claims.</p>
     */
    public static void open(ServerPlayer player) {
        if (!com.barbwra.mlum.warehouse.License.gate(player)) {
            return;
        }
        TerminalSession session = new TerminalSession(player.getUUID());
        // The first route in the catalog is a sane default so the selling tab is never blank.
        List<Route> routes = Catalog.get().routes();
        if (!routes.isEmpty()) {
            session.route = routes.get(0).id();
        }
        SESSIONS.put(player.getUUID(), session);
        session.sync(player);
    }

    public static void close(UUID player) {
        SESSIONS.remove(player);
    }

    public static TerminalSession of(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    /**
     * Drops a player's session.
     *
     * <p>Called from the logout handler. Without it {@link #SESSIONS} grows by one entry per player
     * per login and never shrinks: a client that crashes, times out or is kicked never sends its
     * {@code CLOSE}, so on a long-lived dedicated server the map accumulates a dead session for
     * every disconnect. Singleplayer never shows this, because the map dies with the process.</p>
     */
    public static void onLogout(UUID player) {
        SESSIONS.remove(player);
    }

    /* -------------------------------------------------------------- authority */

    private boolean consumeToken() {
        long now = System.currentTimeMillis();
        if (now - windowStart >= BUCKET_WINDOW_MILLIS) {
            windowStart = now;
            tokens = BUCKET_CAPACITY;
        }
        return tokens-- > 0;
    }

    /* ---------------------------------------------------------------- actions */

    public static void handle(ServerPlayer player, TerminalAction action, String text, int a, int b) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        /*
         * A screen with no session behind it is a screen showing stale data - after a reconnect,
         * a server restart, or an admin revoking the warehouse. Telling the client to close is the
         * difference between "the terminal stopped responding" and a clean dismissal. Silently
         * dropping the packet, which is what this used to do, leaves the player clicking a frozen
         * UI with no indication anything is wrong.
         */
        TerminalSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            ModNetwork.sendCloseTerminal(player);
            return;
        }

        // Rate-limited actions are dropped in silence: a flooding client is misbehaving, and
        // closing its screen would turn a throttle into a denial of service against the player.
        if (!session.consumeToken()) {
            return;
        }

        WarehouseData data = WarehouseData.get(server);
        Warehouse warehouse = data.warehouse(player.getUUID());
        if (warehouse == null) {
            SESSIONS.remove(player.getUUID());
            ModNetwork.sendCloseTerminal(player);
            return;
        }

        switch (action) {
            case REFRESH -> {
            }

            case START_CRAFT -> {
                ProductionService.StartResult result = ProductionService.start(server, player, text);
                if (result != ProductionService.StartResult.OK) {
                    player.sendSystemMessage(Component.literal("§c✖ §f" + describe(result)));
                }
            }

            case TOGGLE_CRATE -> {
                UUID id = parseUuid(text);
                if (id == null) {
                    break;
                }
                if (session.selected.remove(id)) {
                    break;
                }
                Crate crate = warehouse.crate(id);
                long now = System.currentTimeMillis();
                if (crate == null || crate.reserved() || crate.isSpoiled(now)) {
                    break;
                }
                if (session.selected.size() >= com.barbwra.mlum.warehouse.WarehouseConfig.maxConvoy()) {
                    player.sendSystemMessage(Component.literal("§c✖ §fالحد الأقصى "
                            + com.barbwra.mlum.warehouse.WarehouseConfig.maxConvoy() + " صناديق."));
                    break;
                }
                session.selected.add(id);
            }

            case CLEAR_SELECTION -> session.selected.clear();

            case PURGE_SPOILED -> {
                int removed = StorageService.sweepSpoiled(warehouse, System.currentTimeMillis());
                if (removed > 0) {
                    data.setDirty();
                }
            }

            case SET_ROUTE -> {
                if (Catalog.get().route(text) != null) {
                    session.route = text;
                }
            }

            case DISPATCH -> {
                List<UUID> convoy = new ArrayList<>(session.selected);
                MissionService.DispatchResult result =
                        MissionService.dispatch(server, player, session.route, convoy);
                if (result == MissionService.DispatchResult.OK) {
                    session.selected.clear();
                    SESSIONS.remove(player.getUUID());
                    ModNetwork.sendCloseTerminal(player);
                    return;
                }
                player.sendSystemMessage(Component.literal("§c✖ §f" + result));
            }

            case BUY_UPGRADE -> {
                UpgradePath path = UpgradePath.values()[Math.max(0, Math.min(UpgradePath.values().length - 1, a))];
                UpgradeService.PurchaseResult result = UpgradeService.purchase(server, player, path, b);
                player.sendSystemMessage(result == UpgradeService.PurchaseResult.OK
                        ? Component.literal("§a✔ §fتم تفعيل الترقية.")
                        : Component.literal("§c✖ §f" + result));
            }

            case CLOSE -> {
                SESSIONS.remove(player.getUUID());
                return;
            }
        }

        session.sync(player);
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String describe(ProductionService.StartResult result) {
        return switch (result) {
            case NO_FREE_LINE -> "كل خطوط الإنتاج مشغولة.";
            case STORAGE_FULL -> "المخزن ممتلئ.";
            case MISSING_INGREDIENTS -> "الموارد غير كافية.";
            case RECIPE_UNAVAILABLE -> "هذه الوصفة غير متاحة على هذا السيرفر.";
            default -> result.name();
        };
    }

    /* --------------------------------------------------------------- snapshot */

    /** Rebuilds and pushes the whole snapshot. Called after every accepted action. */
    public void sync(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ModNetwork.sendTerminal(player, build(server, player, this));
    }

    /** Pushes a fresh snapshot if this player has a terminal open. Safe to call from anywhere. */
    public static void syncIfOpen(ServerPlayer player) {
        TerminalSession session = SESSIONS.get(player.getUUID());
        if (session != null) {
            session.sync(player);
        }
    }

    private static TerminalSnapshot build(MinecraftServer server, ServerPlayer player, TerminalSession session) {
        WarehouseData data = WarehouseData.get(server);
        Warehouse warehouse = data.warehouse(player.getUUID());
        MarketState market = data.market();
        long now = System.currentTimeMillis();

        /* ---- crates, most valuable first so the manifest opens on what matters ---- */
        List<TerminalSnapshot.CrateView> crates = new ArrayList<>();
        List<Crate> sorted = new ArrayList<>(warehouse.crates());
        sorted.sort(Comparator.comparingInt((Crate c) -> StorageService.currentValue(c, market, now)).reversed());
        for (Crate crate : sorted) {
            crates.add(new TerminalSnapshot.CrateView(crate.id(), crate.cargo().ordinal(), crate.tier(),
                    crate.baseValue(), StorageService.currentValue(crate, market, now),
                    crate.expiresAt(), crate.freshness(now)));
        }

        /* ---- running assembly lines ---- */
        List<TerminalSnapshot.CraftView> crafts = new ArrayList<>();
        for (CraftJob job : warehouse.crafts()) {
            Recipe recipe = Catalog.get().recipe(job.recipeId());
            crafts.add(new TerminalSnapshot.CraftView(job.id(), job.recipeId(),
                    recipe == null ? 0 : recipe.cargo().ordinal(),
                    recipe == null ? 1 : recipe.tier(),
                    job.rolledValue(), job.startedAt(), job.completesAt()));
        }

        /* ---- recipes, with the player's actual holdings already counted ---- */
        List<TerminalSnapshot.RecipeView> recipes = new ArrayList<>();
        for (Recipe recipe : Catalog.get().recipes()) {
            List<TerminalSnapshot.ReqView> reqs = new ArrayList<>();
            for (Ingredient ingredient : recipe.ingredients()) {
                reqs.add(new TerminalSnapshot.ReqView(ingredient.item().toString(),
                        ingredient.display(), ingredient.rarity(),
                        ingredient.count(), ProductionService.held(player, ingredient)));
            }
            recipes.add(new TerminalSnapshot.RecipeView(recipe.id(), recipe.cargo().ordinal(),
                    recipe.tier(), recipe.durationSeconds(), recipe.minValue(), recipe.maxValue(),
                    recipe.isAvailable(), reqs));
        }

        /* ---- routes ---- */
        List<TerminalSnapshot.RouteView> routes = new ArrayList<>();
        for (Route route : Catalog.get().routes()) {
            routes.add(new TerminalSnapshot.RouteView(route.id(), route.display(), route.riskTier(),
                    route.multiplier(), (int) route.distance()));
        }

        /* ---- upgrade tree, every gate resolved against the delivery record ---- */
        List<TerminalSnapshot.UpgradeView> upgrades = new ArrayList<>();
        for (UpgradePath path : UpgradePath.values()) {
            int owned = warehouse.upgradeLevel(path);
            for (int level = 1; level <= UpgradePath.MAX_LEVEL; level++) {
                UpgradeNode node = Catalog.get().upgrade(path, level);
                if (node == null) {
                    continue;
                }
                List<TerminalSnapshot.GateView> gates = new ArrayList<>();
                for (UpgradeService.Progress progress : UpgradeService.progress(warehouse, node)) {
                    gates.add(new TerminalSnapshot.GateView(
                            progress.requirement().cargo().display(),
                            progress.requirement().tierLabel(),
                            progress.current(),
                            progress.requirement().count()));
                }
                upgrades.add(new TerminalSnapshot.UpgradeView(path.ordinal(), level, node.cost(),
                        owned >= level, UpgradeService.isPurchasable(warehouse, node),
                        node.description(), gates));
            }
        }

        /* ---- live quote for whatever is currently in the convoy ---- */
        List<UUID> selected = new ArrayList<>(session.selected);
        Route route = Catalog.get().route(session.route);
        PayoutQuote quote = MissionService.quote(server, warehouse, selected, route, now);

        /* ---- market ---- */
        CargoType[] types = CargoType.values();
        float[] indices = new float[types.length];
        float[][] history = new float[types.length][];
        for (int i = 0; i < types.length; i++) {
            indices[i] = (float) market.index(types[i]);
            history[i] = market.series(types[i]);
        }

        List<TerminalSnapshot.RunView> recent = new ArrayList<>();
        for (Warehouse.DeliveryRecord record : warehouse.recent()) {
            recent.add(new TerminalSnapshot.RunView(record.at(), record.crates(),
                    record.payout(), record.success(), record.route()));
        }

        return new TerminalSnapshot(
                warehouse.crateCount(), warehouse.capacity(),
                warehouse.craftCount(), warehouse.lines(),
                EconomyService.balance(player), warehouse.shelfLifeDays(),
                warehouse.isVip(), data.mission(player.getUUID()) != null,
                data.eventEndsAt(),
                crates, crafts, recipes, routes, upgrades,
                selected, session.route == null ? "" : session.route,
                quote.total(), quote.breakdown(),
                indices, history,
                warehouse.totalEarned(), warehouse.deliveriesSucceeded(),
                warehouse.deliveriesFailed(), recent);
    }
}

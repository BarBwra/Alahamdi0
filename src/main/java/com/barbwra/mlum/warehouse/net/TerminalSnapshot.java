package com.barbwra.mlum.warehouse.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything the terminal draws, resolved on the server into display-ready numbers.
 *
 * <p>The client never computes a price, a requirement or an availability flag - it receives them.
 * That is not an optimisation, it is the security model: if the client cannot derive whether an
 * upgrade is purchasable, it cannot be made to lie about it, and the server has to be asked anyway
 * because it re-checks every action. Sending resolved values just stops the two from disagreeing.</p>
 *
 * <p>Sent whole on open and after any change. It is a few kilobytes for a full warehouse, which is
 * cheaper than the bookkeeping a delta protocol would need at this size.</p>
 */
public record TerminalSnapshot(int crateCount, int capacity,
                               int craftCount, int lines,
                               int balance, int shelfLifeDays,
                               boolean vip, boolean missionActive,
                               long eventEndsAt,
                               List<CrateView> crates,
                               List<CraftView> crafts,
                               List<RecipeView> recipes,
                               List<RouteView> routes,
                               List<UpgradeView> upgrades,
                               List<UUID> selected,
                               String selectedRoute,
                               int quoteTotal,
                               List<String> quoteBreakdown,
                               float[] indices,
                               float[][] history,
                               long totalEarned,
                               int runsOk,
                               int runsFail,
                               List<RunView> recent) {

    /** One finished run, for the stats panel. */
    public record RunView(long at, int crates, int payout, boolean success, String route) {
    }

    public record CrateView(UUID id, int cargo, int tier, int baseValue, int currentValue,
                            long expiresAt, float freshness) {
    }

    public record CraftView(UUID id, String recipeId, int cargo, int tier, int value,
                            long startedAt, long completesAt) {
    }

    /**
     * One requisition line: what is needed, what the player is carrying, and which item it is.
     *
     * <p>{@code item} is the registry id, sent purely so the terminal can draw the real icon beside
     * the label. A player who cannot read the Arabic name - or who simply is not going to - can
     * still recognise the thing they need to go and find.</p>
     */
    public record ReqView(String item, String display, int rarity, int need, int held) {
    }

    public record RecipeView(String id, int cargo, int tier, int durationSeconds,
                             int minValue, int maxValue, boolean available,
                             List<ReqView> ingredients) {
    }

    public record RouteView(String id, String display, int riskTier, double multiplier, int distance) {
    }

    /** One delivery gate, already resolved against the player's record. */
    public record GateView(String cargoDisplay, String tierLabel, int current, int need) {
    }

    public record UpgradeView(int path, int level, int cost, boolean owned, boolean purchasable,
                              List<String> description, List<GateView> gates) {
    }

    /* ================================================================ encoding */

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(crateCount);
        buf.writeVarInt(capacity);
        buf.writeVarInt(craftCount);
        buf.writeVarInt(lines);
        buf.writeVarInt(balance);
        buf.writeVarInt(shelfLifeDays);
        buf.writeBoolean(vip);
        buf.writeBoolean(missionActive);
        buf.writeLong(eventEndsAt);

        buf.writeVarInt(crates.size());
        for (CrateView c : crates) {
            buf.writeUUID(c.id());
            buf.writeVarInt(c.cargo());
            buf.writeVarInt(c.tier());
            buf.writeVarInt(c.baseValue());
            buf.writeVarInt(c.currentValue());
            buf.writeLong(c.expiresAt());
            buf.writeFloat(c.freshness());
        }

        buf.writeVarInt(crafts.size());
        for (CraftView c : crafts) {
            buf.writeUUID(c.id());
            buf.writeUtf(c.recipeId(), 64);
            buf.writeVarInt(c.cargo());
            buf.writeVarInt(c.tier());
            buf.writeVarInt(c.value());
            buf.writeLong(c.startedAt());
            buf.writeLong(c.completesAt());
        }

        buf.writeVarInt(recipes.size());
        for (RecipeView r : recipes) {
            buf.writeUtf(r.id(), 64);
            buf.writeVarInt(r.cargo());
            buf.writeVarInt(r.tier());
            buf.writeVarInt(r.durationSeconds());
            buf.writeVarInt(r.minValue());
            buf.writeVarInt(r.maxValue());
            buf.writeBoolean(r.available());
            buf.writeVarInt(r.ingredients().size());
            for (ReqView req : r.ingredients()) {
                buf.writeUtf(req.item(), 128);
                buf.writeUtf(req.display(), 96);
                buf.writeVarInt(req.rarity());
                buf.writeVarInt(req.need());
                buf.writeVarInt(req.held());
            }
        }

        buf.writeVarInt(routes.size());
        for (RouteView r : routes) {
            buf.writeUtf(r.id(), 64);
            buf.writeUtf(r.display(), 96);
            buf.writeVarInt(r.riskTier());
            buf.writeDouble(r.multiplier());
            buf.writeVarInt(r.distance());
        }

        buf.writeVarInt(upgrades.size());
        for (UpgradeView u : upgrades) {
            buf.writeVarInt(u.path());
            buf.writeVarInt(u.level());
            buf.writeVarInt(u.cost());
            buf.writeBoolean(u.owned());
            buf.writeBoolean(u.purchasable());
            buf.writeVarInt(u.description().size());
            for (String line : u.description()) {
                buf.writeUtf(line, 160);
            }
            buf.writeVarInt(u.gates().size());
            for (GateView gate : u.gates()) {
                buf.writeUtf(gate.cargoDisplay(), 96);
                buf.writeUtf(gate.tierLabel(), 16);
                buf.writeVarInt(gate.current());
                buf.writeVarInt(gate.need());
            }
        }

        buf.writeVarInt(selected.size());
        for (UUID id : selected) {
            buf.writeUUID(id);
        }
        buf.writeUtf(selectedRoute == null ? "" : selectedRoute, 64);
        buf.writeVarInt(quoteTotal);
        buf.writeVarInt(quoteBreakdown.size());
        for (String line : quoteBreakdown) {
            buf.writeUtf(line, 96);
        }

        buf.writeVarInt(indices.length);
        for (float value : indices) {
            buf.writeFloat(value);
        }
        buf.writeVarInt(history.length);
        for (float[] series : history) {
            buf.writeVarInt(series.length);
            for (float value : series) {
                buf.writeFloat(value);
            }
        }

        buf.writeLong(totalEarned);
        buf.writeVarInt(runsOk);
        buf.writeVarInt(runsFail);
        buf.writeVarInt(recent.size());
        for (RunView run : recent) {
            buf.writeLong(run.at());
            buf.writeVarInt(run.crates());
            buf.writeVarInt(run.payout());
            buf.writeBoolean(run.success());
            buf.writeUtf(run.route(), 64);
        }
    }

    public static TerminalSnapshot decode(FriendlyByteBuf buf) {
        int crateCount = buf.readVarInt();
        int capacity = buf.readVarInt();
        int craftCount = buf.readVarInt();
        int lines = buf.readVarInt();
        int balance = buf.readVarInt();
        int shelfLifeDays = buf.readVarInt();
        boolean vip = buf.readBoolean();
        boolean missionActive = buf.readBoolean();
        long eventEndsAt = buf.readLong();

        int n = buf.readVarInt();
        List<CrateView> crates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            crates.add(new CrateView(buf.readUUID(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readFloat()));
        }

        n = buf.readVarInt();
        List<CraftView> crafts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            crafts.add(new CraftView(buf.readUUID(), buf.readUtf(64), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readLong()));
        }

        n = buf.readVarInt();
        List<RecipeView> recipes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf(64);
            int cargo = buf.readVarInt();
            int tier = buf.readVarInt();
            int duration = buf.readVarInt();
            int min = buf.readVarInt();
            int max = buf.readVarInt();
            boolean available = buf.readBoolean();
            int parts = buf.readVarInt();
            List<ReqView> reqs = new ArrayList<>(parts);
            for (int j = 0; j < parts; j++) {
                reqs.add(new ReqView(buf.readUtf(128), buf.readUtf(96),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            }
            recipes.add(new RecipeView(id, cargo, tier, duration, min, max, available, reqs));
        }

        n = buf.readVarInt();
        List<RouteView> routes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            routes.add(new RouteView(buf.readUtf(64), buf.readUtf(96), buf.readVarInt(),
                    buf.readDouble(), buf.readVarInt()));
        }

        n = buf.readVarInt();
        List<UpgradeView> upgrades = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int path = buf.readVarInt();
            int level = buf.readVarInt();
            int cost = buf.readVarInt();
            boolean owned = buf.readBoolean();
            boolean purchasable = buf.readBoolean();
            int descCount = buf.readVarInt();
            List<String> desc = new ArrayList<>(descCount);
            for (int j = 0; j < descCount; j++) {
                desc.add(buf.readUtf(160));
            }
            int gateCount = buf.readVarInt();
            List<GateView> gates = new ArrayList<>(gateCount);
            for (int j = 0; j < gateCount; j++) {
                gates.add(new GateView(buf.readUtf(96), buf.readUtf(16), buf.readVarInt(), buf.readVarInt()));
            }
            upgrades.add(new UpgradeView(path, level, cost, owned, purchasable, desc, gates));
        }

        n = buf.readVarInt();
        List<UUID> selected = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            selected.add(buf.readUUID());
        }
        String selectedRoute = buf.readUtf(64);
        int quoteTotal = buf.readVarInt();
        n = buf.readVarInt();
        List<String> quoteBreakdown = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            quoteBreakdown.add(buf.readUtf(96));
        }

        float[] indices = new float[buf.readVarInt()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = buf.readFloat();
        }
        float[][] history = new float[buf.readVarInt()][];
        for (int i = 0; i < history.length; i++) {
            history[i] = new float[buf.readVarInt()];
            for (int j = 0; j < history[i].length; j++) {
                history[i][j] = buf.readFloat();
            }
        }

        long totalEarned = buf.readLong();
        int runsOk = buf.readVarInt();
        int runsFail = buf.readVarInt();
        n = buf.readVarInt();
        List<RunView> recent = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            recent.add(new RunView(buf.readLong(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readUtf(64)));
        }

        return new TerminalSnapshot(crateCount, capacity, craftCount, lines, balance, shelfLifeDays,
                vip, missionActive, eventEndsAt, crates, crafts, recipes, routes, upgrades,
                selected, selectedRoute, quoteTotal, quoteBreakdown, indices, history,
                totalEarned, runsOk, runsFail, recent);
    }
}

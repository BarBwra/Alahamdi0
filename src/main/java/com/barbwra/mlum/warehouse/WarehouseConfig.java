package com.barbwra.mlum.warehouse;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * Every tunable number, in two files.
 *
 * <p><b>Server</b> holds balance and rules. It is authoritative and never read on the client -
 * anything the client needs to display arrives inside a packet, already resolved, so a player
 * editing their own config cannot change what a mission costs or how long a leak takes.</p>
 *
 * <p><b>Client</b> holds only presentation: colours, opacity, HUD placement. Nothing here can
 * affect game state.</p>
 *
 * <p>Recipes, ingredients, routes and the upgrade tree are deliberately <i>not</i> here. They are
 * lists of structured records rather than scalars, and TOML is a poor fit for that shape - they
 * live in JSON under {@code config/mwh/} and reload with {@code /mwh admin reload}. See
 * {@link com.barbwra.mlum.warehouse.catalog.Catalog}.</p>
 */
public final class WarehouseConfig {

    private WarehouseConfig() {
    }

    /* ================================================================== server */

    public static final ForgeConfigSpec SERVER_SPEC;
    private static final Server SERVER;

    /* ================================================================== client */

    public static final ForgeConfigSpec CLIENT_SPEC;
    private static final Client CLIENT;

    static {
        Pair<Server, ForgeConfigSpec> server = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();

        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private static final class Server {

        final ForgeConfigSpec.IntValue leakSeconds;
        final ForgeConfigSpec.IntValue expireSeconds;
        final ForgeConfigSpec.IntValue graceSeconds;
        final ForgeConfigSpec.IntValue deliveryRadius;
        final ForgeConfigSpec.IntValue maxConvoy;
        final ForgeConfigSpec.BooleanValue blockMounts;
        final ForgeConfigSpec.DoubleValue killerShare;

        final ForgeConfigSpec.IntValue baseCapacity;
        final ForgeConfigSpec.IntValue capacityPerLogistics;
        final ForgeConfigSpec.IntValue baseLines;
        final ForgeConfigSpec.IntValue linesPerCrafting;
        final ForgeConfigSpec.IntValue baseShelfDays;
        final ForgeConfigSpec.IntValue shelfDaysPerLogistics;
        final ForgeConfigSpec.DoubleValue vipCraftFactor;

        final ForgeConfigSpec.ConfigValue<String> licenseKey;
        final ForgeConfigSpec.ConfigValue<String> moneyItem;

        final ForgeConfigSpec.BooleanValue marketEnabled;
        final ForgeConfigSpec.IntValue marketTickMinutes;
        final ForgeConfigSpec.DoubleValue marketFloor;
        final ForgeConfigSpec.DoubleValue marketCeiling;
        final ForgeConfigSpec.DoubleValue marketReversion;
        final ForgeConfigSpec.DoubleValue marketNoise;
        final ForgeConfigSpec.DoubleValue marketSupplyPressure;

        final ForgeConfigSpec.ConfigValue<List<? extends String>> populationTable;
        final ForgeConfigSpec.DoubleValue eventMultiplier;

        Server(ForgeConfigSpec.Builder b) {
            b.comment("Delivery missions.").push("mission");
            leakSeconds = b
                    .comment("Seconds of cover before the runner's position is broadcast to every player.")
                    .defineInRange("leakSeconds", 120, 10, 3600);
            expireSeconds = b
                    .comment("Hard deadline. The cargo is forfeit when this runs out.")
                    .defineInRange("expireSeconds", 600, 60, 7200);
            graceSeconds = b
                    .comment("How long a disconnected runner has to reconnect before the run is failed.",
                            "This is what makes combat logging a loss instead of an escape.")
                    .defineInRange("graceSeconds", 120, 0, 1800);
            deliveryRadius = b
                    .comment("Blocks from the drop point that count as delivered.")
                    .defineInRange("deliveryRadius", 20, 3, 128);
            maxConvoy = b
                    .comment("Maximum crates in one convoy.")
                    .defineInRange("maxConvoyCrates", 7, 1, 27);
            blockMounts = b
                    .comment("Forbid riding any vehicle, horse or entity while carrying cargo.",
                            "The run is meant to be made on foot - this is the core of the risk.")
                    .define("blockMounts", true);
            killerShare = b
                    .comment("Fraction of the convoy's value paid to a player who kills the runner.")
                    .defineInRange("killerShare", 0.5D, 0.0D, 1.0D);
            b.pop();

            b.comment("Warehouse capacity and production.").push("warehouse");
            baseCapacity = b
                    .comment("Crate slots at Logistics 0.")
                    .defineInRange("baseCapacity", 36, 1, 2048);
            capacityPerLogistics = b
                    .comment("Extra crate slots per Logistics level.")
                    .defineInRange("capacityPerLogisticsLevel", 36, 0, 512);
            baseLines = b
                    .comment("Simultaneous production lines at Industry 0.")
                    .defineInRange("baseLines", 5, 1, 64);
            linesPerCrafting = b
                    .comment("Extra production lines per Industry level.")
                    .defineInRange("linesPerIndustryLevel", 1, 0, 16);
            baseShelfDays = b
                    .comment("Days a finished crate keeps before it spoils, at Logistics 0.")
                    .defineInRange("baseShelfLifeDays", 7, 1, 365);
            shelfDaysPerLogistics = b
                    .comment("Extra shelf-life days per Logistics level.")
                    .defineInRange("shelfDaysPerLogisticsLevel", 1, 0, 30);
            vipCraftFactor = b
                    .comment("Assembly duration multiplier for VIP players.")
                    .defineInRange("vipCraftFactor", 0.5D, 0.05D, 1.0D);
            b.pop();

            b.comment("Gating and economy hooks.").push("economy");
            licenseKey = b
                    .comment("Per-server licence key. Without a valid key every warehouse feature",
                            "is disabled. Contact BarBwra for a key.")
                    .define("licenseKey", "");
            moneyItem = b
                    .comment("Registry id of the currency item payouts are made in.",
                            "Paid as items; a full inventory drops the remainder at the player's feet.")
                    .define("moneyItem", "survival_instinct:money");
            b.pop();

            b.comment("The contraband market. Prices drift and respond to what the server delivers.").push("market");
            marketEnabled = b.define("enabled", true);
            marketTickMinutes = b
                    .comment("Real minutes between price samples. Also the graph's resolution.")
                    .defineInRange("tickMinutes", 10, 1, 240);
            marketFloor = b.defineInRange("floor", 0.60D, 0.05D, 1.0D);
            marketCeiling = b.defineInRange("ceiling", 1.60D, 1.0D, 5.0D);
            marketReversion = b
                    .comment("How hard the index is pulled back toward 1.0 each sample.")
                    .defineInRange("reversion", 0.06D, 0.0D, 1.0D);
            marketNoise = b
                    .comment("Standard deviation of the random walk each sample.")
                    .defineInRange("noise", 0.015D, 0.0D, 0.5D);
            marketSupplyPressure = b
                    .comment("How far the index is pushed down by a full hour of deliveries in one",
                            "cargo type. This is what makes flooding a single type stop paying.")
                    .defineInRange("supplyPressure", 0.04D, 0.0D, 1.0D);
            b.pop();

            b.comment("Payout multipliers.").push("payout");
            populationTable = b
                    .comment("Payout multiplier by how many players are online, as \"minOnline=multiplier\".",
                            "The highest entry whose minOnline is <= the current player count wins, so",
                            "\"0=0.50\" is the floor used when nobody else is around.",
                            "",
                            "Example - the server pays half rate below 8 players and full rate at 16+:",
                            "  [\"0=0.50\", \"8=0.75\", \"16=1.00\"]")
                    .defineList("populationTable",
                            List.of("0=0.90", "8=1.00", "16=1.10", "24=1.20"),
                            entry -> entry instanceof String s && s.matches("\\d+\\s*=\\s*[0-9.]+"));
            eventMultiplier = b
                    .comment("Multiplier while a global double-payout event is running.")
                    .defineInRange("eventMultiplier", 2.0D, 1.0D, 10.0D);
            b.pop();
        }
    }

    private static final class Client {

        final ForgeConfigSpec.BooleanValue hudEnabled;
        final ForgeConfigSpec.BooleanValue compassEnabled;
        final ForgeConfigSpec.IntValue compassWidth;
        final ForgeConfigSpec.IntValue accent;
        final ForgeConfigSpec.IntValue glassOpacity;
        final ForgeConfigSpec.IntValue vignetteOpacity;
        final ForgeConfigSpec.BooleanValue scanlines;

        Client(ForgeConfigSpec.Builder b) {
            b.comment("Heads-up display shown while a delivery is running.").push("hud");
            hudEnabled = b.define("enabled", true);
            compassEnabled = b
                    .comment("Client-side compass strip pointing at the drop. Redrawn every frame from",
                            "the player's own yaw, so it stays smooth at any speed and any framerate -",
                            "the server only ever sends the target position.")
                    .define("compass", true);
            compassWidth = b.defineInRange("compassWidth", 180, 60, 640);
            b.pop();

            /*
             * These keys are deliberately named differently from the cyan-era ones they replace
             * (accentColor / panelOpacity / backdropOpacity). A renamed key gets its new default on
             * an existing install; reusing the old names would have left every current config file
             * pinned to the old cold-blue values, and the retheme would only have been visible on
             * a fresh install.
             */
            b.comment("Terminal appearance. The UI ships no textures - every colour is an ARGB int.").push("theme");
            accent = b
                    .comment("Primary accent, 0xRRGGBB. Default is hazard amber.")
                    .defineInRange("accent", 0xE8901F, 0x000000, 0xFFFFFF);
            glassOpacity = b
                    .comment("Opacity of the frosted panels, 0-255. Lower keeps more of the world",
                            "visible behind the terminal; 255 makes it an opaque slab.")
                    .defineInRange("glassOpacity", 172, 0, 255);
            vignetteOpacity = b
                    .comment("Corner darkening behind the terminal, 0-255.")
                    .defineInRange("vignetteOpacity", 118, 0, 255);
            scanlines = b
                    .comment("Faint horizontal scanlines across panels. Costs a few hundred fills.")
                    .define("scanlines", true);
            b.pop();
        }
    }

    /* ============================================================== accessors */

    public static int leakSeconds() {
        return SERVER.leakSeconds.get();
    }

    public static int expireSeconds() {
        return SERVER.expireSeconds.get();
    }

    public static int graceSeconds() {
        return SERVER.graceSeconds.get();
    }

    public static int deliveryRadius() {
        return SERVER.deliveryRadius.get();
    }

    public static int maxConvoy() {
        return SERVER.maxConvoy.get();
    }

    public static boolean blockMounts() {
        return SERVER.blockMounts.get();
    }

    public static double killerShare() {
        return SERVER.killerShare.get();
    }

    public static int capacityFor(int logisticsLevel) {
        return SERVER.baseCapacity.get() + SERVER.capacityPerLogistics.get() * logisticsLevel;
    }

    public static int linesFor(int industryLevel) {
        return SERVER.baseLines.get() + SERVER.linesPerCrafting.get() * industryLevel;
    }

    public static int shelfDaysFor(int logisticsLevel) {
        return SERVER.baseShelfDays.get() + SERVER.shelfDaysPerLogistics.get() * logisticsLevel;
    }

    public static double vipCraftFactor() {
        return SERVER.vipCraftFactor.get();
    }

    public static String licenseKey() {
        return SERVER.licenseKey.get();
    }

    public static String moneyItem() {
        return SERVER.moneyItem.get();
    }


    public static boolean marketEnabled() {
        return SERVER.marketEnabled.get();
    }

    public static int marketTickMinutes() {
        return SERVER.marketTickMinutes.get();
    }

    public static double marketFloor() {
        return SERVER.marketFloor.get();
    }

    public static double marketCeiling() {
        return SERVER.marketCeiling.get();
    }

    public static double marketReversion() {
        return SERVER.marketReversion.get();
    }

    public static double marketNoise() {
        return SERVER.marketNoise.get();
    }

    public static double marketSupplyPressure() {
        return SERVER.marketSupplyPressure.get();
    }

    /**
     * The payout multiplier for a given online player count.
     *
     * <p>Walks the configured table and keeps the highest threshold at or below {@code online}. A
     * malformed row is skipped rather than failing the lookup - a typo in one line of config should
     * cost that line, not every delivery on the server.</p>
     */
    public static double populationMultiplier(int online) {
        double best = 1.0D;
        int bestThreshold = -1;
        for (String entry : SERVER.populationTable.get()) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                int threshold = Integer.parseInt(parts[0].trim());
                double value = Double.parseDouble(parts[1].trim());
                if (threshold <= online && threshold > bestThreshold) {
                    bestThreshold = threshold;
                    best = value;
                }
            } catch (NumberFormatException ignored) {
                // Skipped, as documented above.
            }
        }
        return best;
    }

    public static double eventMultiplier() {
        return SERVER.eventMultiplier.get();
    }

    public static boolean hudEnabled() {
        return CLIENT.hudEnabled.get();
    }

    public static boolean compassEnabled() {
        return CLIENT.compassEnabled.get();
    }

    public static int compassWidth() {
        return CLIENT.compassWidth.get();
    }

    public static int accent() {
        return CLIENT.accent.get();
    }

    public static int glassOpacity() {
        return CLIENT.glassOpacity.get();
    }

    public static int vignetteOpacity() {
        return CLIENT.vignetteOpacity.get();
    }

    public static boolean scanlines() {
        return CLIENT.scanlines.get();
    }
}

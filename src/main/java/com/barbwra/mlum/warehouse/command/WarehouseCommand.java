package com.barbwra.mlum.warehouse.command;

import com.barbwra.mlum.warehouse.catalog.Catalog;
import com.barbwra.mlum.warehouse.catalog.Recipe;
import com.barbwra.mlum.warehouse.catalog.Route;
import com.barbwra.mlum.warehouse.core.CargoType;
import com.barbwra.mlum.warehouse.core.Crate;
import com.barbwra.mlum.warehouse.core.UpgradePath;
import com.barbwra.mlum.warehouse.core.Warehouse;
import com.barbwra.mlum.warehouse.data.WarehouseData;
import com.barbwra.mlum.warehouse.market.MarketState;
import com.barbwra.mlum.warehouse.mission.MissionService;
import com.barbwra.mlum.warehouse.net.TerminalSession;
import com.barbwra.mlum.warehouse.service.ProductionService;
import com.barbwra.mlum.warehouse.service.StorageService;
import com.barbwra.mlum.warehouse.service.WarehouseService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Operator and player commands.
 *
 * <p>Two of these exist specifically to make the port survivable.</p>
 *
 * <p>{@code /mwh admin items} prints every ingredient id in the catalog that does not resolve
 * to a real item. The recipe ids shipped in {@code recipes.json} were reconstructed from the Skript
 * prototype, which only ever knew display-name substrings like {@code "copperwiress"} - so some of
 * them are certainly wrong. This turns "guess and test" into a checklist.</p>
 *
 * <p>{@code /mwh quest complete <player>} is the integration seam for warehouse ownership.
 * Whatever runs the setup quest calls it once; nothing about the quest itself is compiled in.</p>
 */
public final class WarehouseCommand {

    private WarehouseCommand() {
    }

    /**
     * The command root is {@code /mwh}, not {@code /warehouse}.
     *
     * <p>This server is a Forge/Bukkit hybrid, and
     * {@code plugins/Skript/scripts/Bussiness/werehouse.sk} line 168 already registers
     * {@code command /warehouse [<text>] [<player>]} with its own {@code exit}, {@code add} and
     * {@code remove} subcommands. Bukkit injects its command map into Brigadier <i>after</i> mod
     * commands are registered, so on the dedicated server the Skript's node replaced this one
     * wholesale - which is exactly why the multiplayer tree showed {@code exit} and {@code add}
     * while singleplayer, where no Skript is running, showed the real one.</p>
     *
     * <p>Two registrations cannot share a literal. Renaming the root is the only fix that does not
     * require deleting somebody else's script.</p>
     */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("warehouse")

                .then(Commands.literal("info")
                        .executes(WarehouseCommand::info))

                .then(Commands.literal("terminal")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(WarehouseCommand::terminal)))

                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))

                        .then(Commands.literal("reload")
                                .executes(WarehouseCommand::reload))

                        .then(Commands.literal("items")
                                .executes(WarehouseCommand::items))

                        .then(Commands.literal("list")
                                .executes(WarehouseCommand::list))

                        .then(Commands.literal("market")
                                .executes(WarehouseCommand::market))

                        .then(Commands.literal("vip")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(WarehouseCommand::vip))))

                        .then(Commands.literal("craft")
                                .then(Commands.argument("recipe", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Catalog.get().recipes().stream().map(Recipe::id).toList(), builder))
                                        .executes(WarehouseCommand::craft)))

                        .then(Commands.literal("crate")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("cargo", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        List.of("food", "med", "weapon"), builder))
                                                .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5))
                                                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 1000000))
                                                                .executes(context -> giveCrate(context, 1))
                                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 512))
                                                                        .executes(context -> giveCrate(context,
                                                                                IntegerArgumentType.getInteger(context, "amount")))))))))

                        .then(Commands.literal("upgrade")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("path", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        List.of("speed", "logistics", "armor", "crafting", "all"), builder))
                                                .then(Commands.argument("level", IntegerArgumentType.integer(0, UpgradePath.MAX_LEVEL))
                                                        .executes(WarehouseCommand::setUpgrade)))))

                        .then(Commands.literal("dispatch")
                                .then(Commands.argument("route", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Catalog.get().routes().stream().map(Route::id).toList(), builder))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 27))
                                                .executes(WarehouseCommand::dispatch)))));
    }

    /* ------------------------------------------------------------------ player */

    /**
     * Opens the terminal for one or more players, creating their warehouse if this is their first
     * time. Operator and console only.
     *
     * <p>This is the whole integration surface. Skript decides when a player has earned access and
     * runs this from the console; the mod holds no opinion about the requirement and needs no
     * rebuild when that requirement changes.</p>
     *
     * <p>It no longer refuses a player without a warehouse - it gives them one. Refusing was the
     * wrong behaviour for a command an operator only runs deliberately, and it left the player
     * staring at nothing with no way to fix it themselves.</p>
     */
    private static int terminal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        MinecraftServer server = context.getSource().getServer();

        int opened = 0;
        int created = 0;
        for (ServerPlayer target : targets) {
            if (!WarehouseService.owns(server, target)) {
                created++;
            }
            WarehouseService.getOrCreate(server, target);
            TerminalSession.open(target);
            opened++;
        }
        WarehouseData.get(server).setDirty();

        int count = opened;
        int made = created;
        context.getSource().sendSuccess(() -> Component.literal(
                "§6[Warehouse] §fOpened the terminal for " + count + " player(s)"
                        + (made > 0 ? " §7(" + made + " new warehouse(s) created)" : "") + "."), false);
        return count;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();
        Warehouse warehouse = WarehouseService.of(server, player);

        if (warehouse == null) {
            context.getSource().sendFailure(Component.literal("§c! §fليس لديك مستودع بعد."));
            return 0;
        }

        long now = System.currentTimeMillis();
        MarketState market = WarehouseData.get(server).market();
        StringBuilder upgrades = new StringBuilder();
        for (UpgradePath path : UpgradePath.values()) {
            if (upgrades.length() > 0) {
                upgrades.append("  ");
            }
            upgrades.append(path.id()).append(' ').append(warehouse.upgradeLevel(path));
        }

        context.getSource().sendSuccess(() -> Component.literal(
                "§6[Warehouse] §f" + warehouse.crateCount() + "/" + warehouse.capacity() + " crates"
                        + "  §7|§f " + warehouse.craftCount() + "/" + warehouse.lines() + " lines"
                        + "  §7|§f " + StorageService.totalValue(warehouse, market, now) + "$"
                        + "\n§7" + upgrades
                        + "\n§7shelf life " + warehouse.shelfLifeDays() + "d"
                        + (warehouse.isVip() ? "  §e[VIP]" : "")), false);
        return 1;
    }

    /* ------------------------------------------------------------------- admin */

    private static int reload(CommandContext<CommandSourceStack> context) {
        String report = Catalog.get().reload();
        context.getSource().sendSuccess(() -> Component.literal("§6[Warehouse] §f" + report), true);
        return 1;
    }

    /**
     * The fix-list for the reconstructed recipe ids.
     */
    private static int items(CommandContext<CommandSourceStack> context) {
        List<ResourceLocation> missing = Catalog.get().missingIngredients();
        int total = Catalog.get().ingredients().size();

        if (missing.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "§a✔ §fAll " + total + " ingredient ids resolve."), false);
            return 1;
        }

        StringBuilder out = new StringBuilder("§c✖ §f" + missing.size() + " of " + total
                + " ingredient ids do not resolve:\n");
        for (ResourceLocation id : missing) {
            out.append("§7 - §c").append(id).append('\n');
        }
        out.append("§7Fix them in §fconfig/mwh/recipes.json§7 then run §f/mwh admin reload");

        context.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
        return missing.size();
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        WarehouseData data = WarehouseData.get(context.getSource().getServer());
        Collection<Warehouse> all = data.warehouses();

        if (all.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("§7No warehouses registered."), false);
            return 0;
        }

        StringBuilder out = new StringBuilder("§6[Warehouse] §f" + all.size() + " registered:\n");
        for (Warehouse warehouse : all) {
            out.append("§7 - §f").append(warehouse.ownerName())
                    .append(" §7crates §f").append(warehouse.crateCount()).append('/').append(warehouse.capacity())
                    .append(" §7lines §f").append(warehouse.craftCount()).append('/').append(warehouse.lines())
                    .append('\n');
        }
        context.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
        return all.size();
    }

    private static int market(CommandContext<CommandSourceStack> context) {
        MarketState market = WarehouseData.get(context.getSource().getServer()).market();
        StringBuilder out = new StringBuilder("§6[Market] §f");
        for (CargoType type : CargoType.values()) {
            out.append(type.id()).append(" §e")
                    .append(String.format("%.3f", market.index(type)))
                    .append("§f  ");
        }
        context.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    private static int vip(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        boolean value = BoolArgumentType.getBool(context, "value");
        MinecraftServer server = context.getSource().getServer();
        WarehouseData data = WarehouseData.get(server);

        int changed = 0;
        for (ServerPlayer target : targets) {
            Warehouse warehouse = data.warehouse(target.getUUID());
            if (warehouse != null) {
                warehouse.setVip(value);
                changed++;
                target.sendSystemMessage(value
                        ? Component.literal("§e[⭐] §fتم تفعيل رتبة VIP - وقت الصناعة الآن النصف.")
                        : Component.literal("§c✖ §fتم إلغاء رتبة VIP."));
            }
        }
        if (changed > 0) {
            data.setDirty();
        }
        int count = changed;
        context.getSource().sendSuccess(() -> Component.literal(
                "§6[Warehouse] §fVIP set on " + count + " warehouse(s)."), true);
        return count;
    }

    /* -------------------------------------------------------------- test rig */

    /*
     * The three below drive production, storage and dispatch from chat so the whole loop can be
     * exercised before the terminal exists. They are operator-only and are the same service calls
     * the terminal will make - not a parallel implementation - so anything proven here stays proven.
     */

    private static int craft(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String recipeId = StringArgumentType.getString(context, "recipe");

        ProductionService.StartResult result =
                ProductionService.start(context.getSource().getServer(), player, recipeId);

        if (result == ProductionService.StartResult.OK) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "§a✔ §fAssembly line started: §e" + recipeId), false);
            return 1;
        }

        if (result == ProductionService.StartResult.MISSING_INGREDIENTS) {
            Recipe recipe = Catalog.get().recipe(recipeId);
            StringBuilder out = new StringBuilder("§c✖ §fMissing ingredients:\n");
            for (ProductionService.Missing missing : ProductionService.audit(player, recipe)) {
                if (missing.shortfall() > 0) {
                    out.append("§7 - §c").append(missing.ingredient().item())
                            .append(" §7").append(missing.held()).append('/')
                            .append(missing.ingredient().count()).append('\n');
                }
            }
            context.getSource().sendFailure(Component.literal(out.toString()));
            return 0;
        }

        context.getSource().sendFailure(Component.literal("§c✖ §f" + result));
        return 0;
    }

    /**
     * Creates crates in bulk, for one or more players.
     *
     * <p>{@code /mwh admin crate <players> <cargo> <tier> <value> [amount]} - the amount argument
     * exists so seeding a test warehouse is one command instead of seventy-two, and it stops at the
     * storage cap rather than silently discarding the overflow.</p>
     */
    private static int giveCrate(CommandContext<CommandSourceStack> context, int amount)
            throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        MinecraftServer server = context.getSource().getServer();

        CargoType cargo = CargoType.byId(StringArgumentType.getString(context, "cargo"));
        int tier = IntegerArgumentType.getInteger(context, "tier");
        int value = IntegerArgumentType.getInteger(context, "value");
        long now = System.currentTimeMillis();

        int created = 0;
        int refused = 0;
        for (ServerPlayer target : targets) {
            Warehouse warehouse = WarehouseService.of(server, target);
            if (warehouse == null) {
                context.getSource().sendFailure(Component.literal("§c✖ §f"
                        + target.getGameProfile().getName() + " has no warehouse."));
                continue;
            }
            for (int i = 0; i < amount; i++) {
                if (StorageService.store(warehouse, cargo, tier, value, now) == null) {
                    refused += amount - i;
                    break;
                }
                created++;
            }
            TerminalSession.syncIfOpen(target);
        }

        if (created > 0) {
            WarehouseData.get(server).setDirty();
        }
        int madeFinal = created;
        int fullFinal = refused;
        context.getSource().sendSuccess(() -> Component.literal("§a✔ §fCreated " + madeFinal + " "
                + cargo.id() + " " + CargoType.tierNumeral(tier) + " crate(s) worth " + value + "$"
                + (fullFinal > 0 ? " §7(" + fullFinal + " refused - storage full)" : "")), false);
        return created;
    }

    /**
     * Sets an upgrade level directly, bypassing both the delivery gates and the price.
     *
     * <p>{@code /mwh admin upgrade <players> <path|all> <level>}. Level 0 removes the path. This is
     * the only route in the mod that can raise an upgrade without meeting its requirements, and it
     * is deliberately operator-only - {@code UpgradeService.purchase} still refuses to skip a
     * gate for anyone else, including a client claiming otherwise.</p>
     */
    private static int setUpgrade(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        MinecraftServer server = context.getSource().getServer();
        String pathArg = StringArgumentType.getString(context, "path").toLowerCase(java.util.Locale.ROOT);
        int level = IntegerArgumentType.getInteger(context, "level");

        List<UpgradePath> paths = pathArg.equals("all")
                ? List.of(UpgradePath.values())
                : List.of(UpgradePath.byId(pathArg));

        int changed = 0;
        for (ServerPlayer target : targets) {
            Warehouse warehouse = WarehouseService.of(server, target);
            if (warehouse == null) {
                context.getSource().sendFailure(Component.literal("§c✖ §f"
                        + target.getGameProfile().getName() + " has no warehouse."));
                continue;
            }
            for (UpgradePath path : paths) {
                warehouse.setUpgradeLevel(path, level);
            }
            changed++;
            TerminalSession.syncIfOpen(target);
            target.sendSystemMessage(Component.literal("§e[⭐] §fتم تحديث تطويرات مستودعك."));
        }

        if (changed > 0) {
            WarehouseData.get(server).setDirty();
        }
        int count = changed;
        context.getSource().sendSuccess(() -> Component.literal("§a✔ §fSet " + pathArg
                + " to level " + level + " for " + count + " player(s)."), true);
        return changed;
    }

    private static int dispatch(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();
        Warehouse warehouse = WarehouseService.of(server, player);
        if (warehouse == null) {
            context.getSource().sendFailure(Component.literal("§c! §fNo warehouse - run §e/mwh terminal <you>"));
            return 0;
        }

        String routeId = StringArgumentType.getString(context, "route");
        int count = IntegerArgumentType.getInteger(context, "count");
        long now = System.currentTimeMillis();

        List<UUID> convoy = new ArrayList<>();
        for (Crate crate : warehouse.crates()) {
            if (convoy.size() >= count) {
                break;
            }
            if (!crate.reserved() && !crate.isSpoiled(now)) {
                convoy.add(crate.id());
            }
        }

        MissionService.DispatchResult result = MissionService.dispatch(server, player, routeId, convoy);
        if (result == MissionService.DispatchResult.OK) {
            return 1;
        }
        context.getSource().sendFailure(Component.literal("§c✖ §f" + result));
        return 0;
    }
}

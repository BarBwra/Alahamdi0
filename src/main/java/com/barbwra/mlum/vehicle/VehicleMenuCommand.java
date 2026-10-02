package com.barbwra.mlum.vehicle;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;

/**
 * Grants and revokes vehicles. The only way a player ever gets one.
 *
 * <pre>
 * /VehicleMenu &lt;player&gt; give &lt;entityId&gt; ["&lt;display name&gt;"]
 * /VehicleMenu &lt;player&gt; take &lt;entityId&gt;
 * /VehicleMenu &lt;player&gt; clearall
 * /VehicleMenu &lt;player&gt; lock &lt;seconds&gt;      -- impose a PvP lock from Skript
 * /VehicleMenu &lt;player&gt; unlock
 * </pre>
 *
 * <p>Permission level 2, so OP and console only.</p>
 *
 * <p><b>{@code entityId} is a resource location</b> ({@code mod:modded_vehicle}), not a plain word.
 * That matters: Brigadier's unquoted string reader rejects {@code :}, so a word argument could never
 * have read a namespaced id. {@link ResourceLocationArgument} reads it unquoted and suggests every
 * registered entity type as you type.</p>
 *
 * <p>The id is <b>not</b> validated against the registry when giving. A pack owner granting a
 * vehicle from a mod that is temporarily disabled should not get a command error - the vehicle
 * simply shows as unavailable in the menu until the mod is back.</p>
 */
public final class VehicleMenuCommand {

    private VehicleMenuCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("vehicle")
                .requires(source -> source.hasPermission(2));

        root.then(Commands.argument("player", EntityArgument.players())
                .then(Commands.literal("give")
                        .then(Commands.argument("entityId", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                        ForgeRegistries.ENTITY_TYPES.getKeys(), builder))
                                .executes(ctx -> give(ctx, "", false, 1))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(ctx -> give(ctx,
                                                StringArgumentType.getString(ctx, "name"), false, 1))))
                        // stock rather than a title deed: spent on summon, returned by Store
                        .then(Commands.literal("consumable")
                                .then(Commands.argument("entityId", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                ForgeRegistries.ENTITY_TYPES.getKeys(), builder))
                                        .then(Commands.argument("count",
                                                        IntegerArgumentType.integer(1, VehicleEntry.MAX_COUNT))
                                                .executes(ctx -> give(ctx, "", true,
                                                        IntegerArgumentType.getInteger(ctx, "count")))
                                                .then(Commands.argument("name", StringArgumentType.string())
                                                        .executes(ctx -> give(ctx,
                                                                StringArgumentType.getString(ctx, "name"), true,
                                                                IntegerArgumentType.getInteger(ctx, "count"))))))))
                .then(Commands.literal("take")
                        .then(Commands.argument("entityId", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                        ForgeRegistries.ENTITY_TYPES.getKeys(), builder))
                                .executes(VehicleMenuCommand::take)))
                .then(Commands.literal("clearall")
                        .executes(VehicleMenuCommand::clearAll))
                .then(Commands.literal("lock")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                .executes(VehicleMenuCommand::lock)))
                .then(Commands.literal("unlock")
                        .executes(VehicleMenuCommand::unlock)));

        return root;
    }

    /* ----------------------------------------------------------------- actions */

    private static int give(CommandContext<CommandSourceStack> ctx, String displayName,
                            boolean consumable, int amount) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "player");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "entityId");
        VehicleEntry entry = consumable
                ? VehicleEntry.consumable(id.toString(), displayName, amount)
                : VehicleEntry.persistent(id.toString(), displayName);

        int granted = 0;
        for (ServerPlayer player : targets) {
            if (VehicleGarage.give(player, entry)) {
                granted++;
            }
        }
        final int count = granted;
        final String who = describe(targets);
        if (count == 0) {
            ctx.getSource().sendFailure(Component.literal(
                    who + " already owns " + id + " as the other type, or is at the vehicle limit"));
            return 0;
        }
        final String what = consumable ? (amount + "x " + id) : id.toString();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Gave " + what + " to " + who), false);
        return count;
    }

    private static int take(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "player");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "entityId");

        int removed = 0;
        for (ServerPlayer player : targets) {
            // Only despawn when the vehicle that is out is THE one being taken. The old test was
            // "player no longer owns this id", which is true for every id they never owned, so
            // taking a jeep destroyed the helicopter they happened to be flying.
            boolean takingTheActiveOne =
                    id.toString().equalsIgnoreCase(VehicleGarage.activeEntryId(player));
            if (VehicleGarage.take(player, id.toString())) {
                removed++;
            }
            if (takingTheActiveOne) {
                VehicleGarage.despawnActive(player);
                VehicleGarage.sync(player);
            }
        }
        final int count = removed;
        final String who = describe(targets);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Took " + id + " from " + who + " (" + count + ")"), false);
        return count;
    }

    private static int clearAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "player");
        for (ServerPlayer player : targets) {
            VehicleGarage.despawnActive(player);
            VehicleGarage.clear(player);
        }
        final String who = describe(targets);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Cleared all vehicles for " + who), false);
        return targets.size();
    }

    private static int lock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "player");
        final int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        for (ServerPlayer player : targets) {
            CombatTracker.tagFor(player, seconds);
            VehicleGarage.sync(player);
        }
        final String who = describe(targets);
        ctx.getSource().sendSuccess(
                () -> Component.literal(who + " locked out of vehicles for " + seconds + "s"), false);
        return targets.size();
    }

    private static int unlock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "player");
        for (ServerPlayer player : targets) {
            CombatTracker.clear(player);
            VehicleGarage.sync(player);
        }
        final String who = describe(targets);
        ctx.getSource().sendSuccess(() -> Component.literal(who + " unlocked"), false);
        return targets.size();
    }

    private static String describe(Collection<ServerPlayer> targets) {
        if (targets.size() == 1) {
            return targets.iterator().next().getGameProfile().getName();
        }
        return targets.size() + " players";
    }
}

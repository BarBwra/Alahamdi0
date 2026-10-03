package com.barbwra.mlum.dealer;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * {@code /mlum dealer open <player>} - the only way into the dealership.
 *
 * <pre>
 *   /mlum dealer open @p        from a command block at the showroom
 *   /mlum dealer open Ahmed     from the console or chat
 * </pre>
 *
 * <p>Operators and the console (level 2). Opening it for yourself as an operator is also how the
 * stock is edited: the same screen shows the add, edit and delete buttons to anyone allowed to.</p>
 */
public final class DealerCommand {

    private DealerCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("dealer")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("open")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(DealerCommand::open)));
    }

    private static int open(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "players");
        for (ServerPlayer p : targets) {
            Dealer.open(p);
        }
        int n = targets.size();
        ctx.getSource().sendSuccess(() -> Component.literal("Dealership opened for " + n + " player(s)"), false);
        return n;
    }
}

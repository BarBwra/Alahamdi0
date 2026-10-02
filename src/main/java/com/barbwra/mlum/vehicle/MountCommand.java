package com.barbwra.mlum.vehicle;

import com.barbwra.mlum.level.PlayerLevel;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Operator controls for mounts and the progression track.
 *
 * <pre>
 *   /mount color   &lt;players&gt; &lt;RRGGBB&gt;      dye a player's horse armour
 *   /mount upgrade &lt;players&gt; &lt;speed|armor|both&gt; &lt;0-5&gt;
 *   /mount level   &lt;players&gt; &lt;points&gt;      set progression points outright
 * </pre>
 *
 * <p>Colour is deliberately operator-only rather than a purchase: it is the one cosmetic on the
 * server that cannot be earned, which is exactly what makes it worth having.</p>
 */
public final class MountCommand {

    private MountCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("mount")
                .requires(source -> source.hasPermission(2))

                .then(Commands.literal("color")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("hex", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("FFFFFF", "1E1E1E", "C0392B", "2E86C1",
                                                        "27AE60", "F1C40F", "8E44AD"), builder))
                                        .executes(MountCommand::color))));
    }

    private static int color(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        String hex = StringArgumentType.getString(context, "hex").replace("#", "").trim();

        int rgb;
        try {
            rgb = Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException bad) {
            context.getSource().sendFailure(Component.literal(
                    "§c✖ §f'" + hex + "' is not a six digit hex colour."));
            return 0;
        }

        for (ServerPlayer target : targets) {
            MountSetup.setColor(target, rgb);

            target.sendSystemMessage(Component.literal("§e[⭐] §fتم تغيير لون درع حصانك."));
        }
        int count = targets.size();
        context.getSource().sendSuccess(() -> Component.literal(
                "§a✔ §fMount colour set to #" + hex.toUpperCase(Locale.ROOT)
                        + " for " + count + " player(s)."), true);
        return count;
    }


}

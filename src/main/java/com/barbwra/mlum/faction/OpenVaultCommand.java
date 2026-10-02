package com.barbwra.mlum.faction;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /openfactionvault <player> [faction] [page]} - the only way a vault opens.
 *
 * <pre>
 *   /openfactionvault Ahmed                   his own faction's vault, page 1
 *   /openfactionvault Ahmed "أسود الصحراء"     another faction's vault, page 1
 *   /openfactionvault Ahmed "أسود الصحراء" 2   ... page 2
 * </pre>
 *
 * <p><b>Operators and the console only.</b> Permission level 2, which the console always has and an
 * ordinary player never does. There is no in-game door - no block, no menu button - so this command
 * is the complete list of ways the shared storage can be reached.</p>
 *
 * <p><b>Named as a top-level command on purpose.</b> Everything else this mod owns lives under
 * {@code /mlum} to stop it colliding with other mods, but this one is typed by hand and by command
 * blocks often enough that the shorter name earns its place.</p>
 */
public final class OpenVaultCommand {

    private OpenVaultCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("openfactionvault")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> open(ctx, false, 1))
                        .then(Commands.literal("page")
                                .then(Commands.argument("page", IntegerArgumentType.integer(1, 32))
                                        .executes(ctx -> open(ctx, false,
                                                IntegerArgumentType.getInteger(ctx, "page")))))
                        .then(faction()
                                .executes(ctx -> open(ctx, true, 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1, 32))
                                        .executes(ctx -> open(ctx, true,
                                                IntegerArgumentType.getInteger(ctx, "page"))))));
    }

    /** The faction-name argument, completed from the factions that actually exist. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> faction() {
        return Commands.argument("faction", StringArgumentType.string())
                .suggests((ctx, builder) -> {
                    for (Faction entry : FactionData.get(ctx.getSource().getServer()).leaderboard()) {
                        builder.suggest("\"" + entry.name() + "\"");
                    }
                    return builder.buildFuture();
                });
    }

    /**
     * @param named true when a faction was spelled out; false means "whichever one this player is in"
     */
    private static int open(CommandContext<CommandSourceStack> ctx, boolean named, int page)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        FactionData data = FactionData.get(ctx.getSource().getServer());

        Faction faction;
        if (named) {
            String name = StringArgumentType.getString(ctx, "faction");
            faction = data.byName(name);
            if (faction == null) {
                return fail(ctx, "لا توجد منظمة بهذا الاسم: " + name);
            }
        } else {
            faction = data.of(target.getUUID());
            if (faction == null) {
                return fail(ctx, target.getGameProfile().getName() + " ليس في منظمة");
            }
        }

        // The player the vault is opened *for* is told why it failed; so is whoever ran it.
        ServerPlayer notify = ctx.getSource().getEntity() instanceof ServerPlayer caller
                ? caller : null;
        // an operator's tool: opens any faction's vault for anyone, member or not
        if (!FactionVaultAccess.open(target, faction.id(), page, notify, true)) {
            return fail(ctx, "تعذر فتح الخزنة");
        }

        Faction opened = faction;
        ctx.getSource().sendSuccess(() -> arabic("فُتحت خزنة " + opened.name()
                + " الصفحة " + page + " لـ " + target.getGameProfile().getName()), true);
        return 1;
    }

    private static net.minecraft.network.chat.MutableComponent arabic(String text) {
        return Component.literal(com.barbwra.mlum.util.ArabicText.autoDisplay(text));
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendFailure(arabic(message).withStyle(ChatFormatting.RED));
        return 0;
    }
}

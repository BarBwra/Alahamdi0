package com.barbwra.mlum.level;

import com.barbwra.mlum.MlumConfig;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Operator control over levels and the two experience bonuses.
 *
 * <pre>
 * /mlum level set &lt;players&gt; &lt;level&gt;        set a level outright
 * /mlum level give &lt;players&gt; &lt;xp&gt;          award raw experience, bonuses included
 * /mlum level vip &lt;players&gt; &lt;true|false&gt;   permanent per-player bonus
 * /mlum level boost &lt;seconds&gt; [factor]     server-wide bonus for everyone, timed
 * /mlum level boost off
 * /mlum level status                        what is currently active
 * </pre>
 *
 * <p>{@code set} writes vanilla's own level field and nothing else, which is why it now survives a
 * relog - there is no second copy for a later sync to overwrite it with.</p>
 */
public final class LevelCommand {

    private LevelCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("level")
                .requires(source -> source.hasPermission(2))

                .then(Commands.literal("set")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 25000))
                                        .executes(LevelCommand::setLevel))))

                .then(Commands.literal("give")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("xp", IntegerArgumentType.integer(1))
                                        .executes(LevelCommand::giveXp))))

                .then(Commands.literal("vip")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(LevelCommand::setVip))))

                .then(Commands.literal("boost")
                        .then(Commands.literal("off").executes(LevelCommand::boostOff))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 604800))
                                .executes(ctx -> boost(ctx, MlumConfig.globalBoostFactor()))
                                .then(Commands.argument("factor", DoubleArgumentType.doubleArg(1.0D, 100.0D))
                                        .executes(ctx -> boost(ctx,
                                                DoubleArgumentType.getDouble(ctx, "factor"))))))

                .then(Commands.literal("status").executes(LevelCommand::status));
    }

    /* ---------------------------------------------------------------- actions */

    private static int setLevel(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "players");
        final int level = IntegerArgumentType.getInteger(ctx, "level");
        for (ServerPlayer player : targets) {
            PlayerLevel.setLevel(player, level);
        }
        final String who = describe(targets);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Set level " + level + " for " + who), true);
        return targets.size();
    }

    private static int giveXp(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "players");
        final int xp = IntegerArgumentType.getInteger(ctx, "xp");
        for (ServerPlayer player : targets) {
            PlayerLevel.award(player, xp, "command");
        }
        final String who = describe(targets);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Gave " + xp + " xp (before bonuses) to " + who), true);
        return targets.size();
    }

    private static int setVip(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "players");
        final boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        for (ServerPlayer player : targets) {
            XpBoost.setVip(player, enabled);
            PlayerLevel.sync(player);
        }
        final String who = describe(targets);
        ctx.getSource().sendSuccess(() -> Component.literal(
                (enabled ? "Granted" : "Removed") + " x" + fmt(MlumConfig.vipXpFactor())
                        + " VIP experience for " + who), true);
        return targets.size();
    }

    private static int boost(CommandContext<CommandSourceStack> ctx, double factor) {
        final int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        XpBoost.setGlobal(factor, seconds);
        PlayerLevel.syncAll(ctx.getSource().getServer());
        final double applied = factor;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Global x" + fmt(applied) + " experience for " + seconds + "s"), true);
        return 1;
    }

    private static int boostOff(CommandContext<CommandSourceStack> ctx) {
        XpBoost.setGlobal(1.0D, 0);
        PlayerLevel.syncAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("Global experience boost off"), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        StringBuilder out = new StringBuilder();
        out.append("Base rate x").append(fmt(MlumConfig.xpRate()));
        if (XpBoost.globalActive()) {
            out.append("  |  global x").append(fmt(XpBoost.globalFactor()))
                    .append(" for ").append(XpBoost.globalSecondsLeft()).append("s");
        } else {
            out.append("  |  no global boost");
        }
        out.append("  |  VIP factor x").append(fmt(MlumConfig.vipXpFactor()));

        ServerPlayer self = ctx.getSource().getPlayer();
        if (self != null) {
            out.append("  |  yours x").append(fmt(XpBoost.multiplierFor(self)))
                    .append(XpBoost.isVip(self) ? " (VIP)" : "");
        }
        final String text = out.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /* ---------------------------------------------------------------- helpers */

    private static String describe(Collection<ServerPlayer> targets) {
        if (targets.size() == 1) {
            return targets.iterator().next().getGameProfile().getName();
        }
        return targets.size() + " players";
    }

    /** Trims a trailing .0 so "x2" does not read as "x2.0". */
    private static String fmt(double value) {
        return value == Math.rint(value)
                ? String.valueOf((int) value)
                : String.format("%.2f", value);
    }
}

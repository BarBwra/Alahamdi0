package com.barbwra.mlum.quest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

/**
 * The Skript-to-mod bridge. This command <b>only updates the visual board</b> - it awards nothing,
 * tracks nothing and validates no progress. Skript owns the logic; the mod owns the pixels.
 *
 * <pre>
 * /PlayerQuest &lt;type&gt; &lt;line&gt; &lt;title&gt; &lt;progress&gt; &lt;max&gt; &lt;description&gt; [rewards]
 * /PlayerQuest for &lt;targets&gt; &lt;type&gt; &lt;line&gt; &lt;title&gt; &lt;progress&gt; &lt;max&gt; &lt;description&gt; [rewards]
 * /PlayerQuest clear &lt;line&gt;      |  clearall  |  complete &lt;line&gt;      (all accept the "for" form)
 * </pre>
 *
 * <p>Permission level 2, so OP and console only.</p>
 *
 * <p><b>Quoting.</b> {@code title} and {@code description} are brigadier strings, so anything with
 * spaces must be double quoted. That is forced by brigadier's grammar: only the last argument may
 * be greedy, and {@code rewards} has that slot so it can hold several items.</p>
 *
 * <p><b>From Skript, use the {@code for} form</b> - the console is not a player, so the plain form
 * has no one to target:</p>
 * <pre>
 * execute console command "/PlayerQuest for %player% main 1 ""اقتل 100 زومبي"" 57 100 ""%{_desc}%"" minecraft:iron_ingot:5"
 * </pre>
 */
public final class PlayerQuestCommand {

    private PlayerQuestCommand() {
    }

    private static final String[] TYPES = {"main", "side"};

    /** Where a branch gets its target players from. */
    @FunctionalInterface
    private interface TargetResolver {
        Collection<ServerPlayer> resolve(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    /** The {@code quest} branch of {@code /mlum}. See {@link com.barbwra.mlum.MlumCommands}. */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("quest")
                .requires(source -> source.hasPermission(2));

        addActions(root, PlayerQuestCommand::self);

        RequiredArgumentBuilder<CommandSourceStack, EntitySelector> targets =
                Commands.argument("targets", EntityArgument.players());
        addActions(targets, ctx -> EntityArgument.getPlayers(ctx, "targets"));
        root.then(Commands.literal("for").then(targets));

        return root;
    }

    /** Hangs the whole action set off {@code parent}, so self and targeted forms stay identical. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> void addActions(T parent, TargetResolver who) {
        parent.then(Commands.literal("clearall")
                .executes(ctx -> clearAll(ctx, who.resolve(ctx))));

        parent.then(Commands.literal("clear")
                .then(Commands.argument("line", IntegerArgumentType.integer(1, QuestBoard.MAX_LINES))
                        .executes(ctx -> clearLine(ctx, who.resolve(ctx),
                                IntegerArgumentType.getInteger(ctx, "line")))));

        parent.then(Commands.literal("complete")
                .then(Commands.argument("line", IntegerArgumentType.integer(1, QuestBoard.MAX_LINES))
                        .executes(ctx -> complete(ctx, who.resolve(ctx),
                                IntegerArgumentType.getInteger(ctx, "line")))));

        /* the headline form; literals above are matched first, so no clash with <type> */
        parent.then(Commands.argument("type", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TYPES, builder))
                .then(Commands.argument("line", IntegerArgumentType.integer(1, QuestBoard.MAX_LINES))
                        .then(Commands.argument("title", StringArgumentType.string())
                                .then(Commands.argument("progress", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("max", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("description", StringArgumentType.string())
                                                        .executes(ctx -> set(ctx, who.resolve(ctx), ""))
                                                        .then(Commands.argument("rewards", StringArgumentType.greedyString())
                                                                .executes(ctx -> set(ctx, who.resolve(ctx),
                                                                        StringArgumentType.getString(ctx, "rewards")))))))))); 
    }

    /* ----------------------------------------------------------------- actions */

    private static int set(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, String rewards) {
        QuestType type = QuestType.byId(StringArgumentType.getString(ctx, "type"));
        final int line = IntegerArgumentType.getInteger(ctx, "line");
        String title = StringArgumentType.getString(ctx, "title");
        int progress = IntegerArgumentType.getInteger(ctx, "progress");
        int max = IntegerArgumentType.getInteger(ctx, "max");
        String description = StringArgumentType.getString(ctx, "description");

        QuestEntry entry = new QuestEntry(line, type, title, progress, max, description,
                RewardParser.parse(rewards));

        int stored = 0;
        for (ServerPlayer player : targets) {
            if (QuestBoard.put(player, entry)) {
                stored++;
            }
        }
        final String who = describe(targets);
        // a silent drop at the cap would have Skript believing a quest exists that does not
        if (stored < targets.size()) {
            ctx.getSource().sendFailure(Component.literal(
                    "Quest board full (" + QuestBoard.MAX_LINES + " lines) for "
                            + (targets.size() - stored) + " of " + who));
        }
        if (stored == 0) {
            return 0;
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("Quest line " + line + " updated for " + who), false);
        return stored;
    }

    private static int clearLine(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                 final int line) {
        for (ServerPlayer player : targets) {
            QuestBoard.remove(player, line);
        }
        final String who = describe(targets);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Quest line " + line + " cleared for " + who), false);
        return targets.size();
    }

    private static int clearAll(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            QuestBoard.clear(player);
        }
        final String who = describe(targets);
        ctx.getSource().sendSuccess(() -> Component.literal("All quests cleared for " + who), false);
        return targets.size();
    }

    private static int complete(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                                final int line) {
        for (ServerPlayer player : targets) {
            QuestBoard.complete(player, line);
        }
        final String who = describe(targets);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Quest line " + line + " completed for " + who), false);
        return targets.size();
    }

    /* ----------------------------------------------------------------- helpers */

    private static Collection<ServerPlayer> self(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return List.of(ctx.getSource().getPlayerOrException());
    }

    private static String describe(Collection<ServerPlayer> targets) {
        if (targets.size() == 1) {
            return targets.iterator().next().getGameProfile().getName();
        }
        return targets.size() + " players";
    }
}

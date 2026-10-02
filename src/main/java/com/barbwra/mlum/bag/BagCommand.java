package com.barbwra.mlum.bag;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * {@code /mlum_inventory} - the VIP toggle, the config reload, money and skills.
 *
 * <pre>
 *   /mlum_inventory money set      &lt;players&gt; &lt;amount&gt;
 *   /mlum_inventory money add      &lt;players&gt; &lt;amount&gt;
 *   /mlum_inventory money remove   &lt;players&gt; &lt;amount&gt;
 *   /mlum_inventory money get      &lt;player&gt;
 *   /mlum_inventory money deposit  &lt;players&gt; &lt;amount&gt;
 *   /mlum_inventory money withdraw &lt;players&gt; &lt;amount&gt;
 *   /mlum_inventory skill set      &lt;player&gt; &lt;skill&gt; &lt;level&gt;
 *   /mlum_inventory skill get      &lt;player&gt; &lt;skill&gt;
 *   /mlum_inventory skill reset    &lt;player&gt;
 * </pre>
 *
 * <p><b>Money here is the account, not the item.</b> {@code set}, {@code add}, {@code remove} and
 * {@code get} all work on the balance the wallet in the top bar shows, which is the number everything
 * in this mod charges against. It used to be a count of banknote items in the bag, so {@code add}
 * minted items into cells and could half-fail on a full bag; none of that can happen now, and
 * {@code add} always succeeds in full.</p>
 *
 * <p>{@code deposit} and {@code withdraw} are the bridge to the banknote item, for a server that
 * still wants players to be able to carry cash: deposit takes notes out of the bag and credits the
 * account, withdraw does the reverse and leaves in the account anything the bag had no room for.</p>
 *
 * <p>{@code get} returns the balance as the command's result, so {@code execute store result} and
 * scripts can read it.</p>
 *
 * <p>Registered as its own top-level literal rather than under {@code /mlum}, because the brief
 * names it that way and because both of these are typed by an operator at a console rather than
 * pasted into a command block by a script.</p>
 *
 * <p><b>Permission level 2</b>, which is what {@code Commands#hasPermission} calls a gamemaster and
 * what the console always satisfies. It is the level vanilla uses for commands that change another
 * player's state, which is exactly what granting a paid perk is.</p>
 */
public final class BagCommand {

    private BagCommand() {
    }

    public static final String NAME = "mlum_inventory";
    private static final int PERMISSION = 2;

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(NAME)
                .requires(source -> source.hasPermission(PERMISSION))
                .then(Commands.literal("vip")
                        .then(Commands.argument("targets", EntityArgument.players())
                                // "on|off" reads better than true|false at a console, and Brigadier's
                                // bool argument already accepts both spellings.
                                .then(Commands.argument("state", BoolArgumentType.bool())
                                        .executes(ctx -> vip(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                BoolArgumentType.getBool(ctx, "state"))))))
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("money")
                        .then(moneyAction("set"))
                        .then(moneyAction("add"))
                        .then(moneyAction("remove"))
                        .then(moneyAction("deposit"))
                        .then(moneyAction("withdraw"))
                        .then(Commands.literal("get")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(BagCommand::moneyGet))))
                .then(Commands.literal("rank")
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("rank", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (com.barbwra.mlum.rank.Rank r
                                                            : com.barbwra.mlum.rank.RankService.ranks()) {
                                                        builder.suggest(r.id());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(BagCommand::rankSet))))
                        .then(Commands.literal("get")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(BagCommand::rankGet))))
                .then(Commands.literal("skill")
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(skillArgument()
                                                .then(Commands.argument("level", IntegerArgumentType.integer(0,
                                                                com.barbwra.mlum.skill.SkillService.MAX_LEVEL))
                                                        .executes(BagCommand::skillSet)))))
                        .then(Commands.literal("get")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(skillArgument()
                                                .executes(BagCommand::skillGet))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(BagCommand::skillReset))));
    }

    /* ------------------------------------------------------------------ money */

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> moneyAction(String action) {
        return Commands.literal(action)
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                .executes(ctx -> money(ctx, action))));
    }

    private static int money(CommandContext<CommandSourceStack> ctx, String action) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        long amount = LongArgumentType.getLong(ctx, "amount");
        CommandSourceStack source = ctx.getSource();
        boolean needsItem = "deposit".equals(action) || "withdraw".equals(action);
        if (needsItem && WalletService.moneyItem() == null) {
            source.sendFailure(Component.literal(
                    "The money item does not exist - check moneyItem in the warehouse config"));
            return 0;
        }
        int done = 0;
        for (ServerPlayer player : targets) {
            String name = player.getGameProfile().getName();
            switch (action) {
                case "set" -> {
                    long now = WalletService.set(player, amount);
                    source.sendSuccess(() -> Component.literal(name + " now has " + now), true);
                    done++;
                }
                case "add" -> {
                    // an account always takes the whole amount - there is no "did not fit" any more
                    WalletService.give(player, amount);
                    long now = WalletService.balance(player);
                    source.sendSuccess(() -> Component.literal("Gave " + amount + " to " + name
                            + " (" + now + " total)"), true);
                    done++;
                }
                case "deposit" -> {
                    long banked = WalletService.deposit(player, amount);
                    if (banked > 0) {
                        long now = WalletService.balance(player);
                        source.sendSuccess(() -> Component.literal("Banked " + banked + " note(s) from "
                                + name + " (" + now + " total)"), true);
                        done++;
                    } else {
                        source.sendFailure(Component.literal(name + " is not carrying any money items"));
                    }
                }
                case "withdraw" -> {
                    long handed = WalletService.withdraw(player, amount);
                    if (handed > 0) {
                        String note = handed < amount ? " - the rest stayed in the account" : "";
                        source.sendSuccess(() -> Component.literal("Handed " + handed + " note(s) to "
                                + name + note), true);
                        done++;
                    } else {
                        source.sendFailure(Component.literal(name
                                + " has no balance to withdraw, or no room for the notes"));
                    }
                }
                default -> {
                    long before = WalletService.balance(player);
                    if (WalletService.take(player, amount)) {
                        source.sendSuccess(() -> Component.literal("Took " + amount + " from " + name
                                + " (" + (before - amount) + " left)"), true);
                        done++;
                    } else {
                        source.sendFailure(Component.literal(name + " only has " + before + " - nothing taken"));
                    }
                }
            }
        }
        return done;
    }

    private static int moneyGet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        long balance = WalletService.balance(player);
        ctx.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " has " + balance), false);
        return (int) Math.min(Integer.MAX_VALUE, balance);
    }

    /* ------------------------------------------------------------------ ranks */

    /**
     * Hands a rank over. This is what a donation store's webhook runs after a payment clears -
     * nothing in the game ever sells one, so this is the only way a rank changes hands.
     */
    private static int rankSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String id = StringArgumentType.getString(ctx, "rank");
        com.barbwra.mlum.rank.Rank rank = com.barbwra.mlum.rank.RankService.byId(id);
        if (rank == null) {
            ctx.getSource().sendFailure(Component.literal("No rank called " + id
                    + " - check the [ranks] section of the config"));
            return 0;
        }
        for (ServerPlayer player : targets) {
            com.barbwra.mlum.rank.RankService.setRank(player, rank.id());
        }
        int n = targets.size();
        ctx.getSource().sendSuccess(() -> Component.literal("Set " + n + " player(s) to " + rank.name()), true);
        return n;
    }

    private static int rankGet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        com.barbwra.mlum.rank.Rank rank = com.barbwra.mlum.rank.RankService.rankOf(player);
        ctx.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName()
                + " is " + rank.name() + " (" + rank.id() + ")"), false);
        return 1;
    }

    /* ------------------------------------------------------------------ skills */

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> skillArgument() {
        return Commands.argument("skill", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (com.barbwra.mlum.skill.SkillService.Skill skill : com.barbwra.mlum.skill.SkillService.skills()) {
                        builder.suggest(skill.id());
                    }
                    return builder.buildFuture();
                });
    }

    private static int skillSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String id = StringArgumentType.getString(ctx, "skill");
        int level = IntegerArgumentType.getInteger(ctx, "level");
        com.barbwra.mlum.skill.SkillService.Skill skill = com.barbwra.mlum.skill.SkillService.byId(id);
        if (skill == null) {
            ctx.getSource().sendFailure(Component.literal("No skill called " + id));
            return 0;
        }
        com.barbwra.mlum.skill.SkillService.setLevel(player, skill.id(), level);
        com.barbwra.mlum.skill.SkillService.runCommand(player, skill.id(), level);
        com.barbwra.mlum.skill.SkillService.sync(player);
        ctx.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " " + skill.id()
                + " = " + level), true);
        return 1;
    }

    private static int skillGet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String id = StringArgumentType.getString(ctx, "skill");
        com.barbwra.mlum.skill.SkillService.Skill skill = com.barbwra.mlum.skill.SkillService.byId(id);
        if (skill == null) {
            ctx.getSource().sendFailure(Component.literal("No skill called " + id));
            return 0;
        }
        int level = com.barbwra.mlum.skill.SkillService.level(player, skill.id());
        ctx.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " " + skill.id()
                + " = " + level), false);
        return level;
    }

    private static int skillReset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        for (com.barbwra.mlum.skill.SkillService.Skill skill : com.barbwra.mlum.skill.SkillService.skills()) {
            if (com.barbwra.mlum.skill.SkillService.level(player, skill.id()) > 0) {
                com.barbwra.mlum.skill.SkillService.setLevel(player, skill.id(), 0);
                com.barbwra.mlum.skill.SkillService.runCommand(player, skill.id(), 0);
            }
        }
        com.barbwra.mlum.skill.SkillService.sync(player);
        ctx.getSource().sendSuccess(() -> Component.literal("Reset every skill of " + player.getGameProfile().getName()), true);
        return 1;
    }

    /**
     * Grants or revokes VIP.
     *
     * <p>Revoking also takes the pack off, because the brief requires it: a player who loses VIP
     * while wearing the desert backpack must not keep wearing it. {@link BackpackEnforcer} owns that
     * so the same rule runs on login and on the config changing under a player's feet.</p>
     */
    private static int vip(CommandSourceStack source, Collection<ServerPlayer> targets, boolean on) {
        int changed = 0;
        for (ServerPlayer player : targets) {
            if (Vip.set(player, on)) {
                changed++;
            }
            if (!on) {
                BackpackEnforcer.enforce(player);
            }
            player.sendSystemMessage(Component.literal(on
                            ? "تم تفعيل VIP لك"
                            : "تم إلغاء VIP عنك")
                    .withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }

        int total = targets.size();
        int finalChanged = changed;
        source.sendSuccess(() -> Component.literal("VIP " + (on ? "on" : "off") + " for "
                + total + " player(s), " + finalChanged + " changed"), true);
        return total;
    }

    /**
     * Re-reads {@code mlum_inventory.toml} live.
     *
     * <p>Then re-runs the enforcer for everybody online: a reload can turn a pack into a non-pack,
     * or make one VIP-only that was not, and the brief's edge cases require those to take effect
     * without a restart rather than the next time each player happens to relog.</p>
     */
    private static int reload(CommandSourceStack source) {
        String summary = BagConfig.load();
        if (source.getServer() != null) {
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                BackpackEnforcer.enforce(player);
                // every client draws with the server's tables, so they get the new ones now
                com.barbwra.mlum.network.ModNetwork.sendBagConfig(player);
                BagService.sync(player);
            }
        }
        source.sendSuccess(() -> Component.literal("Reloaded " + BagConfig.FILE_NAME + ": " + summary),
                true);
        return 1;
    }
}

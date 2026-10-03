package com.barbwra.mlum.admin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * {@code /mlum staff ...} - the admin ranks from the console or chat, for operators only.
 *
 * <pre>
 * /mlum staff ranks                         every rank and what it holds
 * /mlum staff rank create &lt;id&gt; &lt;name&gt;      a new, empty rank
 * /mlum staff rank delete &lt;id&gt;
 * /mlum staff rank color &lt;id&gt; &lt;RRGGBB&gt;
 * /mlum staff rank add &lt;id&gt; &lt;permission&gt;  any permission - see Perms
 * /mlum staff rank remove &lt;id&gt; &lt;permission&gt;
 * /mlum staff assign &lt;player&gt; &lt;id&gt;
 * /mlum staff unassign &lt;player&gt;
 * /mlum staff check &lt;player&gt; &lt;permission&gt;  for scripts: succeeds only when they hold it
 * /mlum ticket &lt;text&gt;                       anyone: a support ticket
 * </pre>
 *
 * <p>Operator-only is checked inside, not only in {@code requires}: a rank holding
 * {@code cmd.mlum} must still never be able to make itself more powerful.</p>
 */
public final class StaffCommand {

    private StaffCommand() {
    }

    private static boolean op(CommandSourceStack source) {
        return !(source.getEntity() instanceof ServerPlayer p) || Staff.isOp(p);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("staff")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("ranks").executes(StaffCommand::list))
                .then(Commands.literal("rank")
                        .then(Commands.literal("create")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(c -> edit(c, "create")))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(StaffCommand::rankIds)
                                        .executes(c -> edit(c, "delete"))))
                        .then(Commands.literal("color")
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(StaffCommand::rankIds)
                                        .then(Commands.argument("hex", StringArgumentType.word())
                                                .executes(c -> edit(c, "color")))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(StaffCommand::rankIds)
                                        .then(Commands.argument("perm", StringArgumentType.greedyString())
                                                .executes(c -> edit(c, "add")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(StaffCommand::rankIds)
                                        .then(Commands.argument("perm", StringArgumentType.greedyString())
                                                .executes(c -> edit(c, "remove"))))))
                .then(Commands.literal("assign")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(StaffCommand::rankIds)
                                        .executes(c -> assign(c, true)))))
                .then(Commands.literal("unassign")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(c -> assign(c, false))))
                .then(Commands.literal("check")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("perm", StringArgumentType.greedyString())
                                        .executes(StaffCommand::check))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> ticket() {
        return Commands.literal("ticket")
                .executes(c -> {
                    if (c.getSource().getEntity() instanceof ServerPlayer p) {
                        Staff.send(p, "ticket-open", new net.minecraft.nbt.CompoundTag());
                    }
                    return 1;
                })
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(c -> {
                            if (c.getSource().getEntity() instanceof ServerPlayer p) {
                                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                                tag.putString("Text", StringArgumentType.getString(c, "text"));
                                AdminActions.handle(p, "ticket.create", tag);
                            }
                            return 1;
                        }));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> rankIds(
            CommandContext<CommandSourceStack> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        return SharedSuggestionProvider.suggest(StaffData.get(c.getSource().getServer()).ranks.keySet(), b);
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        StaffData data = StaffData.get(c.getSource().getServer());
        if (data.ranks.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("No admin ranks yet."), false);
            return 0;
        }
        for (StaffRank r : data.ranks.values()) {
            long n = data.members.values().stream().filter(r.id::equals).count();
            c.getSource().sendSuccess(() -> Component.literal(r.id + " (" + r.name + ") - " + n + " members - " + String.join(", ", r.perms)), false);
        }
        return data.ranks.size();
    }

    private static int edit(CommandContext<CommandSourceStack> c, String what) {
        if (!op(c.getSource())) {
            c.getSource().sendFailure(Component.literal("Operators only."));
            return 0;
        }
        StaffData data = StaffData.get(c.getSource().getServer());
        String id = Perms.clean(StringArgumentType.getString(c, "id"));
        StaffRank r = data.ranks.get(id);
        switch (what) {
            case "create" -> {
                if (r != null) {
                    c.getSource().sendFailure(Component.literal("Rank " + id + " already exists."));
                    return 0;
                }
                data.ranks.put(id, new StaffRank(id, StringArgumentType.getString(c, "name"), 0xF0A93B));
            }
            case "delete" -> {
                data.ranks.remove(id);
                data.members.values().removeIf(id::equals);
            }
            case "color" -> {
                if (r == null) {
                    return missing(c, id);
                }
                try {
                    r.color = Integer.parseInt(StringArgumentType.getString(c, "hex").replace("#", ""), 16) & 0xFFFFFF;
                } catch (NumberFormatException bad) {
                    c.getSource().sendFailure(Component.literal("Colour as RRGGBB, e.g. F0A93B."));
                    return 0;
                }
            }
            case "add", "remove" -> {
                if (r == null) {
                    return missing(c, id);
                }
                String node = Perms.clean(StringArgumentType.getString(c, "perm"));
                if (what.equals("add")) {
                    r.perms.add(node);
                } else {
                    r.perms.remove(node);
                }
            }
            default -> {
                return 0;
            }
        }
        data.setDirty();
        Staff.refreshAll(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.literal("Done."), true);
        return 1;
    }

    private static int missing(CommandContext<CommandSourceStack> c, String id) {
        c.getSource().sendFailure(Component.literal("No rank " + id + "."));
        return 0;
    }

    private static int assign(CommandContext<CommandSourceStack> c, boolean on) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!op(c.getSource())) {
            c.getSource().sendFailure(Component.literal("Operators only."));
            return 0;
        }
        StaffData data = StaffData.get(c.getSource().getServer());
        Collection<com.mojang.authlib.GameProfile> profiles = GameProfileArgument.getGameProfiles(c, "player");
        String id = on ? Perms.clean(StringArgumentType.getString(c, "id")) : "";
        if (on && !data.ranks.containsKey(id)) {
            return missing(c, id);
        }
        for (com.mojang.authlib.GameProfile p : profiles) {
            if (on) {
                data.members.put(p.getId(), id);
                data.names.put(p.getId(), p.getName());
            } else {
                data.members.remove(p.getId());
            }
        }
        data.setDirty();
        Staff.refreshAll(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.literal("Done."), true);
        return profiles.size();
    }

    private static int check(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String node = Perms.clean(StringArgumentType.getString(c, "perm"));
        for (com.mojang.authlib.GameProfile p : GameProfileArgument.getGameProfiles(c, "player")) {
            ServerPlayer online = c.getSource().getServer().getPlayerList().getPlayer(p.getId());
            boolean has = online != null ? Staff.has(online, node)
                    : StaffData.get(c.getSource().getServer()).rankOf(p.getId()) != null
                    && StaffData.get(c.getSource().getServer()).rankOf(p.getId()).has(node);
            if (!has) {
                c.getSource().sendFailure(Component.literal(p.getName() + " does not have " + node));
                return 0;
            }
        }
        c.getSource().sendSuccess(() -> Component.literal("Yes."), false);
        return 1;
    }
}

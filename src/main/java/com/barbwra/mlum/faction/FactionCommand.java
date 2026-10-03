package com.barbwra.mlum.faction;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /mlum faction ...} - the control surface for the faction system.
 *
 * <p>Everything the المنظمة menu will do is reachable here first. That ordering is deliberate: the
 * commands are where the rules actually live, so the screens end up as a view over logic that has
 * already been exercised, rather than the only way to reach it. It also means a server operator can
 * fix a faction by hand when a player has got it into a state the menu has no button for.</p>
 *
 * <p><b>Permission checks are all rank comparisons</b> against {@link FactionRole}, never a list of
 * names, so a rank inserted into that enum later is respected here without edits.</p>
 */
public final class FactionCommand {

    private FactionCommand() {
    }

    private static final int NAME_MIN = 3;
    private static final int NAME_MAX = 24;

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("faction")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(FactionCommand::create)))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(FactionCommand::invite)))
                .then(Commands.literal("join").executes(FactionCommand::join))
                .then(Commands.literal("reject").executes(FactionCommand::reject))
                .then(Commands.literal("leave").executes(FactionCommand::leave))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(FactionCommand::kick)))
                .then(Commands.literal("promote")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> shift(ctx, true))))
                .then(Commands.literal("demote")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> shift(ctx, false))))
                .then(Commands.literal("diplomacy").executes(ctx -> {
                    Diplomacy.open(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("info").executes(FactionCommand::info))
                .then(Commands.literal("top").executes(FactionCommand::top))
                .then(Commands.literal("points")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(-1_000_000, 1_000_000))
                                        .executes(FactionCommand::adminPoints))))
                .then(admin());
    }

    /* ----------------------------------------------------------------------- admin */

    /**
     * {@code /mlum faction admin ...} - the operator's control surface.
     *
     * <p><b>Factions are named, not selected.</b> Every subcommand takes the faction's own name as a
     * quoted string with tab completion from the live list, rather than a player who happens to be
     * in it. An operator fixing a faction usually does so precisely because nobody from it is
     * online, and naming a player would make that impossible.</p>
     *
     * <p>There is deliberately no {@code setlevel} that stores a level. The level is derived from
     * the points and nothing else, so {@code level} moves the points to that level's threshold -
     * which is the only change that can survive a reload. Storing a level beside the points is the
     * exact mistake the player level system was rewritten to undo.</p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("admin")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(FactionCommand::adminList))
                .then(Commands.literal("info")
                        .then(faction().executes(FactionCommand::adminInfo)))
                .then(Commands.literal("disband")
                        .then(faction().executes(FactionCommand::adminDisband)))
                .then(Commands.literal("rename")
                        .then(faction()
                                .then(Commands.argument("newName", StringArgumentType.string())
                                        .executes(FactionCommand::adminRename))))
                .then(Commands.literal("points")
                        .then(faction()
                                .then(Commands.argument("amount", IntegerArgumentType.integer(-100_000_000, 100_000_000))
                                        .executes(ctx -> adminSetPoints(ctx, false)))))
                .then(Commands.literal("setpoints")
                        .then(faction()
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100_000_000))
                                        .executes(ctx -> adminSetPoints(ctx, true)))))
                .then(Commands.literal("level")
                        .then(faction()
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, FactionLevel.MAX_LEVEL))
                                        .executes(FactionCommand::adminLevel))))
                .then(Commands.literal("fund")
                        .then(faction()
                                .then(Commands.argument("amount", IntegerArgumentType.integer(-100_000_000, 100_000_000))
                                        .executes(FactionCommand::adminFund))))
                .then(Commands.literal("setleader")
                        .then(faction()
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(FactionCommand::adminSetLeader))))
                .then(Commands.literal("add")
                        .then(faction()
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(FactionCommand::adminAdd))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(FactionCommand::adminRemove)));
    }

    /** The faction-name argument, completed from the factions that actually exist. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> faction() {
        return Commands.argument("faction", StringArgumentType.string())
                .suggests((ctx, builder) -> {
                    for (Faction entry : FactionData.get(ctx.getSource().getServer()).leaderboard()) {
                        // Quoted so a name with spaces completes into something that parses.
                        builder.suggest("\"" + entry.name() + "\"");
                    }
                    return builder.buildFuture();
                });
    }

    /** Resolves the named faction, or null after having told the operator why not. */
    @Nullable
    private static Faction named(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "faction");
        Faction faction = FactionData.get(ctx.getSource().getServer()).byName(name);
        if (faction == null) {
            fail(ctx, "لا توجد منظمة بهذا الاسم: " + name);
        }
        return faction;
    }

    private static int adminList(CommandContext<CommandSourceStack> ctx) {
        List<Faction> all = FactionData.get(ctx.getSource().getServer()).leaderboard();
        if (all.isEmpty()) {
            return fail(ctx, "لا توجد منظمات");
        }
        for (Faction faction : all) {
            ctx.getSource().sendSuccess(() -> arabic(faction.name()
                    + " - المستوى " + faction.level()
                    + " - النقاط " + faction.points()
                    + " - الخزينة " + faction.bank()
                    + " - الأعضاء " + faction.size()), false);
        }
        return all.size();
    }

    private static int adminInfo(CommandContext<CommandSourceStack> ctx) {
        Faction faction = named(ctx);
        if (faction == null) {
            return 0;
        }
        ctx.getSource().sendSuccess(() -> arabic(faction.name()
                + " - المستوى " + faction.level()
                + " - النقاط " + faction.points()
                + " - الخزينة " + faction.bank()
                + " - الخزنة " + FactionLevel.totalRows(faction.level()) + " صف"), false);
        for (FactionMember member : faction.sortedMembers()) {
            ctx.getSource().sendSuccess(() -> arabic("  " + member.role().arabic() + " - " + member.name()
                    + " - نقاط " + member.contributed()
                    + " - تبرع " + member.donated()), false);
        }
        return 1;
    }

    private static int adminDisband(CommandContext<CommandSourceStack> ctx) {
        Faction faction = named(ctx);
        if (faction == null) {
            return 0;
        }
        FactionData data = FactionData.get(ctx.getSource().getServer());
        // Captured before the faction is removed, so the former members still get a clean view.
        List<UUID> former = new ArrayList<>(faction.members().keySet());
        String name = faction.name();

        data.disband(faction.id());
        for (UUID id : former) {
            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(id);
            if (online != null) {
                online.sendSystemMessage(arabic("تم حل المنظمة: " + name).withStyle(ChatFormatting.RED));
                FactionService.sync(online);
            }
        }
        return ok(ctx, "تم حل المنظمة: " + name);
    }

    private static int adminRename(CommandContext<CommandSourceStack> ctx) {
        Faction faction = named(ctx);
        if (faction == null) {
            return 0;
        }
        String newName = StringArgumentType.getString(ctx, "newName").trim();
        if (newName.length() < NAME_MIN || newName.length() > NAME_MAX) {
            return fail(ctx, "الاسم يجب أن يكون بين " + NAME_MIN + " و " + NAME_MAX + " حرفاً");
        }
        FactionData data = FactionData.get(ctx.getSource().getServer());
        Faction clash = data.byName(newName);
        if (clash != null && !clash.id().equals(faction.id())) {
            return fail(ctx, "هذا الاسم مستخدم");
        }

        faction.setName(newName);
        data.setDirty();
        FactionService.syncFaction(ctx.getSource().getServer(), faction);
        return ok(ctx, "الاسم الآن: " + newName);
    }

    private static int adminSetPoints(CommandContext<CommandSourceStack> ctx, boolean absolute) {
        Faction faction = named(ctx);
        if (faction == null) {
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        FactionData data = FactionData.get(ctx.getSource().getServer());

        if (absolute) {
            faction.setPoints(amount);
        } else {
            // Null earner: the operator is not a contributor, so nobody's record is credited.
            faction.addPoints(null, amount);
        }
        data.setDirty();
        FactionService.syncFaction(ctx.getSource().getServer(), faction);
        return ok(ctx, faction.name() + " - النقاط " + faction.points()
                + " - المستوى " + faction.level());
    }

    private static int adminLevel(CommandContext<CommandSourceStack> ctx) {
        Faction faction = named(ctx);
        if (faction == null) {
            return 0;
        }
        int level = IntegerArgumentType.getInteger(ctx, "level");
        FactionData data = FactionData.get(ctx.getSource().getServer());

        faction.setPoints(FactionLevel.requirementFor(level));
        data.setDirty();
        FactionService.syncFaction(ctx.getSource().getServer(), faction);
        return ok(ctx, faction.name() + " - المستوى " + faction.level()
                + " - النقاط " + faction.points());
    }

    private static int adminFund(CommandContext<CommandSourceStack> ctx) {
        Faction faction = named(ctx);
        if (faction == null) {
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        FactionData data = FactionData.get(ctx.getSource().getServer());

        faction.addBank(amount);
        data.setDirty();
        FactionService.syncFaction(ctx.getSource().getServer(), faction);
        return ok(ctx, faction.name() + " - الخزينة " + faction.bank());
    }

    private static int adminSetLeader(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Faction faction = named(ctx);
        if (faction == null) {
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        if (!faction.contains(target.getUUID())) {
            return fail(ctx, "هذا اللاعب ليس في هذه المنظمة");
        }

        FactionData data = FactionData.get(ctx.getSource().getServer());
        faction.setLeader(target.getUUID());
        data.setDirty();
        FactionService.syncFaction(ctx.getSource().getServer(), faction);
        return ok(ctx, faction.name() + " - القائد الآن " + target.getGameProfile().getName());
    }

    private static int adminAdd(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Faction faction = named(ctx);
        if (faction == null) {
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        FactionData data = FactionData.get(ctx.getSource().getServer());
        if (data.inFaction(target.getUUID())) {
            return fail(ctx, "هذا اللاعب في منظمة بالفعل");
        }

        if (!data.join(target.getUUID(), target.getGameProfile().getName(),
                faction.id(), System.currentTimeMillis())) {
            return fail(ctx, "تعذر الإضافة");
        }
        FactionService.syncFaction(ctx.getSource().getServer(), faction);
        return ok(ctx, target.getGameProfile().getName() + " أُضيف إلى " + faction.name());
    }

    /**
     * Removes a player from whatever faction they are in - including a leader.
     *
     * <p>The leader guard exists to stop a faction being left headless by accident. An operator
     * removing a leader is not an accident, so leadership is handed to the next member down before
     * the removal; the faction is disbanded only when there is nobody left to hand it to.</p>
     */
    private static int adminRemove(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        FactionData data = FactionData.get(ctx.getSource().getServer());
        Faction faction = data.of(target.getUUID());
        if (faction == null) {
            return fail(ctx, "هذا اللاعب ليس في منظمة");
        }

        if (target.getUUID().equals(faction.leader())) {
            FactionMember heir = null;
            for (FactionMember member : faction.sortedMembers()) {
                if (!member.id().equals(target.getUUID())) {
                    heir = member;
                    break;
                }
            }
            if (heir == null) {
                String name = faction.name();
                data.disband(faction.id());
                FactionService.sync(target);
                return ok(ctx, "كان آخر عضو - تم حل " + name);
            }
            faction.setLeader(heir.id());
        }

        data.leave(target.getUUID());
        data.setDirty();
        FactionService.sync(target);
        FactionService.syncFaction(ctx.getSource().getServer(), faction);
        return ok(ctx, target.getGameProfile().getName() + " أُخرج من " + faction.name());
    }

    /* -------------------------------------------------------------------- lifecycle */

    private static int create(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name").trim();

        if (name.length() < NAME_MIN || name.length() > NAME_MAX) {
            return fail(ctx, "الاسم يجب أن يكون بين " + NAME_MIN + " و " + NAME_MAX + " حرفاً");
        }

        FactionData data = FactionData.get(ctx.getSource().getServer());
        if (data.inFaction(player.getUUID())) {
            return fail(ctx, "أنت بالفعل في منظمة");
        }
        if (data.byName(name) != null) {
            return fail(ctx, "هذا الاسم مستخدم");
        }

        Faction faction = data.create(player.getUUID(), player.getGameProfile().getName(),
                name, System.currentTimeMillis());
        if (faction == null) {
            return fail(ctx, "تعذر إنشاء المنظمة");
        }
        return ok(ctx, "تم إنشاء المنظمة: " + faction.name());
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        FactionData data = FactionData.get(ctx.getSource().getServer());
        Faction faction = data.of(player.getUUID());
        if (faction == null) {
            return fail(ctx, "أنت لست في منظمة");
        }
        // The leader cannot walk out and leave a faction nobody can administer.
        if (player.getUUID().equals(faction.leader())) {
            return fail(ctx, "القائد لا يستطيع المغادرة - انقل القيادة أولاً");
        }
        data.leave(player.getUUID());
        return ok(ctx, "غادرت المنظمة");
    }

    /* ----------------------------------------------------------------- membership */

    private static int invite(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        FactionData data = FactionData.get(ctx.getSource().getServer());
        Faction faction = data.of(player.getUUID());
        FactionRole role = faction == null ? null : faction.roleOf(player.getUUID());
        if (faction == null || role == null || !role.canManageMembers()) {
            return fail(ctx, "لا تملك صلاحية الدعوة");
        }
        if (data.inFaction(target.getUUID())) {
            return fail(ctx, "هذا اللاعب في منظمة بالفعل");
        }

        data.invite(target.getUUID(), faction.id());
        target.sendSystemMessage(arabic("دعوة للانضمام إلى: " + faction.name()).withStyle(ChatFormatting.YELLOW));
        // Pushes the view so the Join button appears in their المنظمة screen without a reopen.
        FactionService.sync(target);
        return ok(ctx, "تم إرسال الدعوة");
    }

    private static int join(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        FactionData data = FactionData.get(ctx.getSource().getServer());

        Faction faction = data.inviteFor(player.getUUID());
        if (faction == null) {
            return fail(ctx, "ليس لديك دعوة");
        }
        if (!data.join(player.getUUID(), player.getGameProfile().getName(),
                faction.id(), System.currentTimeMillis())) {
            return fail(ctx, "تعذر الانضمام");
        }
        return ok(ctx, "انضممت إلى: " + faction.name());
    }

    private static int reject(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        FactionData data = FactionData.get(ctx.getSource().getServer());
        if (data.inviteFor(player.getUUID()) == null) {
            return fail(ctx, "ليس لديك دعوة");
        }
        data.clearInvite(player.getUUID());
        return ok(ctx, "تم رفض الدعوة");
    }

    private static int kick(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        FactionData data = FactionData.get(ctx.getSource().getServer());
        Faction faction = data.of(player.getUUID());
        if (faction == null || !faction.contains(target.getUUID())) {
            return fail(ctx, "هذا اللاعب ليس في منظمتك");
        }
        FactionRole mine = faction.roleOf(player.getUUID());
        FactionRole theirs = faction.roleOf(target.getUUID());
        if (mine == null || !mine.canManageMembers() || theirs == null || !mine.outranks(theirs)) {
            return fail(ctx, "لا تملك صلاحية الطرد");
        }
        data.leave(target.getUUID());
        FactionService.sync(target);
        return ok(ctx, "تم الطرد");
    }

    /**
     * Moves a member one rank up or down.
     *
     * <p>Nobody can promote to or past their own rank - that is what stops a deputy from minting a
     * second leader, and it falls out of the same {@code outranks} comparison used everywhere else.
     * Promotion to {@link FactionRole#LEADER} is not reachable here at all; handing the faction over
     * is a separate, deliberate act.</p>
     */
    private static int shift(CommandContext<CommandSourceStack> ctx, boolean up) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        FactionData data = FactionData.get(ctx.getSource().getServer());
        Faction faction = data.of(player.getUUID());
        if (faction == null || !faction.contains(target.getUUID())) {
            return fail(ctx, "هذا اللاعب ليس في منظمتك");
        }
        FactionRole mine = faction.roleOf(player.getUUID());
        FactionMember member = faction.member(target.getUUID());
        if (mine == null || member == null || !mine.canManageMembers() || !mine.outranks(member.role())) {
            return fail(ctx, "لا تملك صلاحية تغيير الرتبة");
        }

        FactionRole[] ranks = FactionRole.values();
        int index = member.role().ordinal() + (up ? -1 : 1);
        if (index < 0 || index >= ranks.length) {
            return fail(ctx, "لا يمكن تغيير الرتبة أكثر");
        }
        FactionRole next = ranks[index];
        if (next == FactionRole.LEADER || !mine.outranks(next)) {
            return fail(ctx, "لا يمكنك الترقية إلى رتبتك أو أعلى");
        }
        member.setRole(next);
        data.setDirty();
        FactionService.sync(target);
        return ok(ctx, "الرتبة الآن: " + next.arabic());
    }

    /* -------------------------------------------------------------------- readouts */

    private static int info(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Faction faction = FactionData.get(ctx.getSource().getServer()).of(player.getUUID());
        if (faction == null) {
            return fail(ctx, "أنت لست في منظمة");
        }
        ctx.getSource().sendSuccess(() -> arabic(faction.name()
                + " - المستوى " + faction.level()
                + " - النقاط " + faction.points()
                + " - الأعضاء " + faction.size()
                + " - الخزينة " + faction.bank()), false);
        for (FactionMember member : faction.sortedMembers()) {
            ctx.getSource().sendSuccess(() -> arabic("  " + member.role().arabic() + " - " + member.name()
                    + " - نقاط " + member.contributed()
                    + " - تبرع " + member.donated()), false);
        }
        return 1;
    }

    private static int top(CommandContext<CommandSourceStack> ctx) {
        List<Faction> board = FactionData.get(ctx.getSource().getServer()).leaderboard();
        if (board.isEmpty()) {
            return fail(ctx, "لا توجد منظمات بعد");
        }
        int shown = Math.min(10, board.size());
        for (int i = 0; i < shown; i++) {
            Faction faction = board.get(i);
            int rank = i + 1;
            ctx.getSource().sendSuccess(() -> arabic(rank + ". " + faction.name()
                    + " - المستوى " + faction.level()
                    + " - النقاط " + faction.points()), false);
        }
        return shown;
    }

    private static int adminPoints(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        FactionData data = FactionData.get(ctx.getSource().getServer());
        Faction faction = data.of(target.getUUID());
        if (faction == null) {
            return fail(ctx, "هذا اللاعب ليس في منظمة");
        }
        faction.addPoints(amount > 0 ? target.getUUID() : null, amount);
        data.setDirty();
        return ok(ctx, faction.name() + " - النقاط الآن " + faction.points());
    }

    /* --------------------------------------------------------------------- replies */

    /**
     * Arabic replies are shaped at send time.
     *
     * <p>These strings are written in logical order in the source, so they must go through the same
     * shaping the rest of the mod's Arabic does - otherwise they arrive reversed in chat.</p>
     */
    private static net.minecraft.network.chat.MutableComponent arabic(String text) {
        return Component.literal(com.barbwra.mlum.util.ArabicText.autoDisplay(text));
    }

    private static int ok(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> arabic(message).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, @Nullable String message) {
        ctx.getSource().sendFailure(arabic(message == null ? "" : message).withStyle(ChatFormatting.RED));
        return 0;
    }
}

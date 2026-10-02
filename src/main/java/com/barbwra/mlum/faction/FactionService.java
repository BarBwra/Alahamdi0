package com.barbwra.mlum.faction;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.network.C2SFactionAction;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.network.S2CFactionRoster;
import com.barbwra.mlum.network.S2CFactionState;
import com.barbwra.mlum.util.ArabicText;
import com.barbwra.mlum.warehouse.service.EconomyService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The rules behind the المنظمة screen's buttons, and the builder for the view it draws.
 *
 * <p>Sits between the packet and {@link FactionData} so that the screen, the commands and any future
 * caller all go through one set of checks. Every action arrives as an intent - "I pressed kick" -
 * and is re-derived here from the sender's own membership and rank. Nothing the client sent about
 * <i>itself</i> is trusted: not its rank, not its faction, not whether a button was drawn.</p>
 */
public final class FactionService {

    private FactionService() {
    }

    private static final int NAME_MIN = 3;
    private static final int NAME_MAX = 24;

    /** How many factions the leaderboard carries to the client. */
    private static final int TOP_ROWS = 25;

    /* --------------------------------------------------------------------- actions */

    public static void handle(ServerPlayer player, C2SFactionAction.Action action, String argument) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        FactionData data = FactionData.get(server);
        String arg = argument == null ? "" : argument.trim();

        switch (action) {
            case CREATE -> create(player, data, arg);
            case JOIN -> join(player, data);
            case REJECT -> {
                data.clearInvite(player.getUUID());
                tell(player, "تم رفض الدعوة", ChatFormatting.YELLOW);
            }
            case LEAVE -> leave(player, data);
            case ROSTER -> {
                // Answered on its own channel; the view below is unchanged by looking at a list.
                sendRoster(server, player, data);
                return;
            }
            case INVITE -> invite(server, player, data, arg);
            case KICK -> kick(server, player, data, arg);
            case PROMOTE -> shift(server, player, data, arg, true);
            case DEMOTE -> shift(server, player, data, arg, false);
            case RENAME -> rename(server, player, data, arg);
            case TRANSFER -> transfer(server, player, data, arg);
            case DISBAND -> disband(server, player, data, arg);
            case DONATE -> donate(server, player, data, arg);
            case OPEN_VAULT -> {
                openVault(player, data);
                return;
            }
            case REFRESH -> {
                // nothing to do - the sync below is the whole point
            }
        }
        sync(player);
    }

    /* -------------------------------------------------------------------- lifecycle */

    /**
     * Founds a faction, charging the configured price.
     *
     * <p><b>Order matters.</b> Every free check runs first, the money is taken second, and the
     * faction is created third - and if creation somehow fails after the charge, the money goes
     * straight back. Charging before validating would let a player pay for a name that was already
     * taken.</p>
     */
    private static void create(ServerPlayer player, FactionData data, String rawName) {
        String name = rawName.trim();
        if (name.length() < NAME_MIN || name.length() > NAME_MAX) {
            tell(player, "الاسم يجب أن يكون بين " + NAME_MIN + " و " + NAME_MAX + " حرفاً", ChatFormatting.RED);
            return;
        }
        if (data.inFaction(player.getUUID())) {
            tell(player, "أنت بالفعل في منظمة", ChatFormatting.RED);
            return;
        }
        if (data.byName(name) != null) {
            tell(player, "هذا الاسم مستخدم", ChatFormatting.RED);
            return;
        }

        int cost = MlumConfig.factionCreateCost();
        if (cost > 0 && !EconomyService.take(player, cost)) {
            tell(player, "تحتاج " + cost + " من المال لإنشاء منظمة", ChatFormatting.RED);
            return;
        }

        Faction faction = data.create(player.getUUID(), player.getGameProfile().getName(),
                name, System.currentTimeMillis());
        if (faction == null) {
            if (cost > 0) {
                EconomyService.pay(player, cost);
            }
            tell(player, "تعذر إنشاء المنظمة", ChatFormatting.RED);
            return;
        }
        tell(player, "تم إنشاء المنظمة: " + faction.name(), ChatFormatting.GREEN);
    }

    private static void join(ServerPlayer player, FactionData data) {
        Faction faction = data.inviteFor(player.getUUID());
        if (faction == null) {
            tell(player, "ليس لديك دعوة", ChatFormatting.RED);
            return;
        }
        if (data.join(player.getUUID(), player.getGameProfile().getName(),
                faction.id(), System.currentTimeMillis())) {
            tell(player, "انضممت إلى: " + faction.name(), ChatFormatting.GREEN);
            syncFaction(player.getServer(), faction);
        } else {
            tell(player, "تعذر الانضمام", ChatFormatting.RED);
        }
    }

    private static void leave(ServerPlayer player, FactionData data) {
        Faction faction = data.of(player.getUUID());
        if (faction == null) {
            tell(player, "أنت لست في منظمة", ChatFormatting.RED);
            return;
        }
        if (player.getUUID().equals(faction.leader())) {
            tell(player, "القائد لا يستطيع المغادرة - انقل القيادة أولاً", ChatFormatting.RED);
            return;
        }
        data.leave(player.getUUID());
        tell(player, "غادرت المنظمة", ChatFormatting.YELLOW);
        syncFaction(player.getServer(), faction);
    }

    /**
     * Disbands, after the caller has retyped the name.
     *
     * <p>The confirmation is re-checked here and not only in the screen. A screen can be replaced;
     * the vault cannot be brought back.</p>
     */
    private static void disband(MinecraftServer server, ServerPlayer player,
                                FactionData data, String confirmation) {
        Faction faction = data.of(player.getUUID());
        if (faction == null) {
            tell(player, "أنت لست في منظمة", ChatFormatting.RED);
            return;
        }
        if (!player.getUUID().equals(faction.leader())) {
            tell(player, "القائد وحده يستطيع حل المنظمة", ChatFormatting.RED);
            return;
        }
        if (!faction.name().equals(confirmation)) {
            tell(player, "اكتب اسم المنظمة بالضبط للتأكيد", ChatFormatting.RED);
            return;
        }

        // Collect the members before the faction is gone, so everyone gets the empty view.
        List<UUID> former = new ArrayList<>(faction.members().keySet());
        data.disband(faction.id());
        for (UUID id : former) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) {
                tell(online, "تم حل المنظمة: " + faction.name(), ChatFormatting.RED);
                sync(online);
            }
        }
    }

    /**
     * Pays currency from a member's pockets into the faction bank.
     *
     * <p><b>Any rank may donate.</b> Spending the bank is leadership's - see
     * {@link FactionRole#canSpendBank()} - but putting money in is how a ضيف contributes at all,
     * and gating it would make the lowest rank unable to help.</p>
     *
     * <p>The amount is parsed here rather than trusted: a client is free to send "abc", a negative,
     * or a number larger than it is carrying, and all three have to end as a refusal rather than as
     * a bank that grew out of nothing. {@link EconomyService#take} verifies before it removes, so
     * there is no window where the money has left the player but not arrived.</p>
     */
    private static void donate(MinecraftServer server, ServerPlayer player,
                               FactionData data, String rawAmount) {
        Faction faction = data.of(player.getUUID());
        if (faction == null) {
            tell(player, "أنت لست في منظمة", ChatFormatting.RED);
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(rawAmount);
        } catch (NumberFormatException ignored) {
            return;
        }
        if (amount <= 0) {
            return;
        }

        if (!EconomyService.take(player, amount)) {
            tell(player, "رصيدك لا يكفي", ChatFormatting.RED);
            return;
        }

        faction.donate(player.getUUID(), amount);
        data.setDirty();
        tell(player, "تبرعت بـ " + amount + " - الخزينة الآن " + faction.bank(), ChatFormatting.GREEN);
        syncFaction(server, faction);
    }

    /* ------------------------------------------------------------------ membership */

    private static void invite(MinecraftServer server, ServerPlayer player,
                               FactionData data, String rawId) {
        Faction faction = data.of(player.getUUID());
        FactionRole mine = faction == null ? null : faction.roleOf(player.getUUID());
        if (faction == null || mine == null || !mine.canManageMembers()) {
            tell(player, "لا تملك صلاحية الدعوة", ChatFormatting.RED);
            return;
        }
        ServerPlayer target = online(server, rawId);
        if (target == null) {
            tell(player, "هذا اللاعب غير متصل", ChatFormatting.RED);
            return;
        }
        if (data.inFaction(target.getUUID())) {
            tell(player, "هذا اللاعب في منظمة بالفعل", ChatFormatting.RED);
            return;
        }

        data.invite(target.getUUID(), faction.id());
        data.noteInviter(target.getUUID(), player.getGameProfile().getName(), System.currentTimeMillis());
        tell(target, "دعوة للانضمام إلى: " + faction.name(), ChatFormatting.YELLOW);
        // Pushes the view so the Join button appears in their screen without a reopen.
        sync(target);
        tell(player, "تم إرسال الدعوة إلى " + target.getGameProfile().getName(), ChatFormatting.GREEN);
        sendRoster(server, player, data);
    }

    private static void kick(MinecraftServer server, ServerPlayer player,
                             FactionData data, String rawId) {
        Faction faction = data.of(player.getUUID());
        UUID targetId = parse(rawId);
        FactionMember target = faction == null || targetId == null ? null : faction.member(targetId);
        FactionRole mine = faction == null ? null : faction.roleOf(player.getUUID());

        if (faction == null || target == null) {
            tell(player, "هذا اللاعب ليس في منظمتك", ChatFormatting.RED);
            return;
        }
        if (mine == null || !mine.canManageMembers() || !mine.outranks(target.role())) {
            tell(player, "لا تملك صلاحية الطرد", ChatFormatting.RED);
            return;
        }

        String name = target.name();
        data.leave(targetId);
        tell(player, "تم طرد " + name, ChatFormatting.YELLOW);

        ServerPlayer kicked = server.getPlayerList().getPlayer(targetId);
        if (kicked != null) {
            tell(kicked, "تم طردك من: " + faction.name(), ChatFormatting.RED);
            sync(kicked);
        }
        syncFaction(server, faction);
    }

    /**
     * Moves a member one rank up or down.
     *
     * <p>Nobody can promote to or past their own rank - that is what stops a deputy from minting a
     * second leader, and it falls out of the same {@code outranks} comparison used everywhere else.
     * Promotion to {@link FactionRole#LEADER} is not reachable here at all; handing the faction over
     * is {@link #transfer}, a separate and deliberate act.</p>
     */
    private static void shift(MinecraftServer server, ServerPlayer player,
                              FactionData data, String rawId, boolean up) {
        Faction faction = data.of(player.getUUID());
        UUID targetId = parse(rawId);
        FactionMember target = faction == null || targetId == null ? null : faction.member(targetId);
        FactionRole mine = faction == null ? null : faction.roleOf(player.getUUID());

        if (faction == null || target == null) {
            tell(player, "هذا اللاعب ليس في منظمتك", ChatFormatting.RED);
            return;
        }
        if (mine == null || !mine.canManageMembers() || !mine.outranks(target.role())) {
            tell(player, "لا تملك صلاحية تغيير الرتبة", ChatFormatting.RED);
            return;
        }

        FactionRole[] ranks = FactionRole.values();
        int index = target.role().ordinal() + (up ? -1 : 1);
        if (index < 0 || index >= ranks.length) {
            tell(player, "لا يمكن تغيير الرتبة أكثر", ChatFormatting.RED);
            return;
        }
        FactionRole next = ranks[index];
        if (next == FactionRole.LEADER || !mine.outranks(next)) {
            tell(player, "لا يمكنك الترقية إلى رتبتك أو أعلى", ChatFormatting.RED);
            return;
        }

        target.setRole(next);
        data.setDirty();
        tell(player, target.name() + " - الرتبة الآن: " + next.arabic(), ChatFormatting.GREEN);
        syncFaction(server, faction);
    }

    private static void transfer(MinecraftServer server, ServerPlayer player,
                                 FactionData data, String rawId) {
        Faction faction = data.of(player.getUUID());
        UUID targetId = parse(rawId);
        if (faction == null || !player.getUUID().equals(faction.leader())) {
            tell(player, "القائد وحده يستطيع نقل القيادة", ChatFormatting.RED);
            return;
        }
        if (targetId == null || faction.member(targetId) == null) {
            tell(player, "هذا اللاعب ليس في منظمتك", ChatFormatting.RED);
            return;
        }
        if (targetId.equals(player.getUUID())) {
            tell(player, "أنت القائد بالفعل", ChatFormatting.RED);
            return;
        }

        faction.setLeader(targetId);
        data.setDirty();
        tell(player, "تم نقل القيادة إلى " + faction.member(targetId).name(), ChatFormatting.GREEN);
        syncFaction(server, faction);
    }

    private static void rename(MinecraftServer server, ServerPlayer player,
                               FactionData data, String rawName) {
        Faction faction = data.of(player.getUUID());
        if (faction == null || !player.getUUID().equals(faction.leader())) {
            tell(player, "القائد وحده يستطيع تغيير الاسم", ChatFormatting.RED);
            return;
        }
        String name = rawName.trim();
        if (name.length() < NAME_MIN || name.length() > NAME_MAX) {
            tell(player, "الاسم يجب أن يكون بين " + NAME_MIN + " و " + NAME_MAX + " حرفاً", ChatFormatting.RED);
            return;
        }
        if (name.equals(faction.name())) {
            return;
        }
        Faction clash = data.byName(name);
        if (clash != null && !clash.id().equals(faction.id())) {
            tell(player, "هذا الاسم مستخدم", ChatFormatting.RED);
            return;
        }

        faction.setName(name);
        data.setDirty();
        tell(player, "الاسم الآن: " + name, ChatFormatting.GREEN);
        syncFaction(server, faction);
    }

    /* ---------------------------------------------------------------------- vault */

    /**
     * The افتح الخزنة button: the first page of the player's own faction's vault, if their rank may
     * see it. Operators still open any vault from the console with {@code /openfactionvault}.
     */
    private static void openVault(ServerPlayer player, FactionData data) {
        Faction faction = data.of(player.getUUID());
        if (faction == null) {
            tell(player, "أنت لست في منظمة", ChatFormatting.RED);
            return;
        }
        FactionRole role = faction.roleOf(player.getUUID());
        if (!faction.vault().canView(1, role)) {
            tell(player, "رتبتك ما تسمح لك تفتح الخزنة", ChatFormatting.RED);
            return;
        }
        FactionVaultAccess.open(player, faction.id(), 1, player, false);
    }

    /* --------------------------------------------------------------------- roster */

    /** Builds and sends the invite window's player list. */
    private static void sendRoster(MinecraftServer server, ServerPlayer player, FactionData data) {
        Faction faction = data.of(player.getUUID());
        Set<UUID> invited = faction == null ? Set.of() : data.invitedBy(faction.id());

        List<S2CFactionRoster.Entry> entries = new ArrayList<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            Faction theirs = data.of(online.getUUID());
            entries.add(new S2CFactionRoster.Entry(
                    online.getUUID(),
                    online.getGameProfile().getName(),
                    theirs == null ? "" : theirs.name(),
                    invited.contains(online.getUUID())));
        }
        entries.sort((a, b) -> {
            // Invitable players first - the list exists to invite from, so the rest is context.
            boolean freeA = a.factionName().isEmpty();
            boolean freeB = b.factionName().isEmpty();
            if (freeA != freeB) {
                return freeA ? -1 : 1;
            }
            return a.name().compareToIgnoreCase(b.name());
        });
        ModNetwork.sendFactionRoster(player, new S2CFactionRoster(entries));
    }

    /* ------------------------------------------------------------------ the view */

    /** Builds and sends the whole faction view to one player. */
    public static void sync(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ModNetwork.sendFactionState(player, build(server, player));
    }

    private static S2CFactionState build(MinecraftServer server, ServerPlayer player) {
        FactionData data = FactionData.get(server);
        Faction faction = data.of(player.getUUID());

        List<Faction> board = data.leaderboard();
        List<S2CFactionState.Standing> top = new ArrayList<>();
        int rank = 0;
        for (int i = 0; i < board.size(); i++) {
            Faction entry = board.get(i);
            boolean mine = faction != null && entry.id().equals(faction.id());
            if (mine) {
                rank = i + 1;
            }
            if (top.size() < TOP_ROWS) {
                top.add(new S2CFactionState.Standing(entry.name(), entry.level(), entry.points(),
                        entry.bank(), entry.size(), mine));
            }
        }

        int cost = MlumConfig.factionCreateCost();
        int balance = EconomyService.balance(player);

        if (faction == null) {
            Faction invite = data.inviteFor(player.getUUID());
            FactionData.InviteMeta meta = data.inviteMeta(player.getUUID());
            return new S2CFactionState(false, "", 0, 0, 0, 0, 0, "",
                    invite == null ? "" : invite.name(), 0L, 0, data.count(),
                    balance, cost, List.of(), top,
                    meta == null ? "" : meta.from(), meta == null ? 0L : meta.at());
        }

        List<S2CFactionState.Member> members = new ArrayList<>();
        for (FactionMember member : faction.sortedMembers()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member.id());
            members.add(new S2CFactionState.Member(
                    member.id(),
                    member.name(),
                    member.role().name(),
                    member.contributed(),
                    member.donated(),
                    online == null ? 0 : online.experienceLevel,
                    online != null,
                    member.joinedAt()));
        }

        FactionRole mine = faction.roleOf(player.getUUID());
        int level = faction.level();
        return new S2CFactionState(true,
                faction.name(),
                level,
                faction.points(),
                FactionLevel.requirementFor(level),
                FactionLevel.requirementFor(level + 1),
                faction.bank(),
                mine == null ? FactionRole.defaultRole().name() : mine.name(),
                "",
                faction.createdAt(),
                rank,
                data.count(),
                balance,
                cost,
                members,
                top,
                "",
                0L);
    }

    /** Pushes the view to every online member of a faction, after something changed it. */
    public static void syncFaction(@Nullable MinecraftServer server, @Nullable Faction faction) {
        if (server == null || faction == null) {
            return;
        }
        for (UUID id : faction.members().keySet()) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) {
                sync(online);
            }
        }
    }

    /* ------------------------------------------------------------------- helpers */

    /** A UUID sent by the client, or null. Malformed input is a refusal, never an exception. */
    @Nullable
    private static UUID parse(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private static ServerPlayer online(MinecraftServer server, String rawId) {
        UUID id = parse(rawId);
        return id == null ? null : server.getPlayerList().getPlayer(id);
    }

    /** In the menu's toast while a menu is open, in chat otherwise - see {@code Feedback}. */
    private static void tell(ServerPlayer player, String message, ChatFormatting colour) {
        com.barbwra.mlum.util.Feedback.send(player, message, colour == ChatFormatting.RED);
    }
}

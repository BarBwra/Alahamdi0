package com.barbwra.mlum.admin;

import com.barbwra.mlum.events.ServerEvents;
import com.barbwra.mlum.downed.DownedLoot;
import com.barbwra.mlum.util.Feedback;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the admin panel asks the server for, and the pages it gets back.
 *
 * <p>Every action names the permission it needs and is refused without it, whatever the panel on
 * the client showed. Managing ranks is for operators alone. After a change the page it touched is
 * sent again, so the panel always shows the server's truth.</p>
 */
public final class AdminActions {

    private AdminActions() {
    }

    private static final Map<UUID, Long> LAST_TICKET = new HashMap<>();

    public static void handle(ServerPlayer player, String action, CompoundTag data) {
        MinecraftServer server = player.server;
        if (action.equals("ticket.create")) {
            createTicket(player, data.getString("Text"));
            return;
        }
        if (!Staff.has(player, Perms.PANEL)) {
            return;
        }
        switch (action) {
            case "page" -> sendPage(player, data.getString("Page"), data);
            case "tp" -> online(server, data, Perms.TELEPORT, player, target -> {
                player.teleportTo((ServerLevel) target.level(), target.getX(), target.getY(), target.getZ(), player.getYRot(), player.getXRot());
                Feedback.ok(player, "انتقلت لـ {b}" + target.getGameProfile().getName() + "{/b}");
            });
            case "bring" -> online(server, data, Perms.BRING, player, target -> {
                target.teleportTo((ServerLevel) player.level(), player.getX(), player.getY(), player.getZ(), target.getYRot(), target.getXRot());
                Feedback.ok(player, "سحبت {b}" + target.getGameProfile().getName() + "{/b} عندك");
            });
            case "inv" -> online(server, data, Perms.INVENTORY, player, target -> {
                boolean edit = Staff.has(player, Perms.INVENTORY_EDIT);
                ServerEvents.openBodyScreen(player, DownedLoot.inspect(target, edit),
                        Component.literal(target.getGameProfile().getName()), DownedLoot.ROWS, target.getId());
            });
            case "spectate" -> online(server, data, Perms.SPECTATE, player, target -> Spectate.start(player, target));
            case "unspectate" -> {
                Spectate.stop(player);
                sendPage(player, "players", data);
            }
            case "vanish" -> {
                if (Staff.require(player, Perms.VANISH, p -> {
                })) {
                    boolean on = Vanish.toggle(player);
                    Feedback.ok(player, on ? "أنت مخفي الحين" : "رجعت تبين");
                    sendPage(player, "players", data);
                }
            }
            case "restore" -> {
                if (!Staff.has(player, Perms.RESTORE)) {
                    break;
                }
                ServerPlayer target = server.getPlayerList().getPlayer(data.getUUID("Target"));
                if (target == null) {
                    Feedback.bad(player, "اللاعب لازم يكون متصل عشان ترجع له أغراضه");
                } else if (DeathArchive.get(server).restore(target, data.getInt("Index"))) {
                    Feedback.ok(player, "رجعت أغراض {b}" + target.getGameProfile().getName() + "{/b}");
                    Feedback.ok(target, "الإدارة رجعت لك أغراضك");
                } else {
                    Feedback.bad(player, "هالموتة ترجعت قبل");
                }
                sendPage(player, "deaths", data);
            }
            case "punish" -> punish(player, data);
            case "lift" -> {
                String type = data.getString("Type");
                if (!Staff.has(player, "punish." + type)) {
                    break;
                }
                Punish.get(server).lift(server, data.getUUID("Target"), type);
                Feedback.ok(player, "انشالت العقوبة");
                sendPage(player, "punish", data);
            }
            case "jail.set" -> {
                if (Staff.has(player, Perms.JAIL)) {
                    Punish.get(server).setJail(player);
                    Feedback.ok(player, "السجن صار هنا");
                }
            }
            case "ticket.close" -> {
                if (Staff.has(player, Perms.TICKETS)) {
                    Tickets data0 = Tickets.get(server);
                    Tickets.Ticket t = data0.byId(data.getInt("Id"));
                    if (t != null && !t.closed) {
                        data0.close(t, player.getGameProfile().getName());
                        ServerPlayer owner = server.getPlayerList().getPlayer(t.player);
                        if (owner != null) {
                            Feedback.ok(owner, "تذكرتك رقم {n}" + t.id + "{/n} انحلت");
                        }
                    }
                    sendPage(player, "tickets", data);
                }
            }
            case "ticket.tp" -> {
                if (Staff.has(player, Perms.TICKETS)) {
                    Tickets.Ticket t = Tickets.get(server).byId(data.getInt("Id"));
                    ServerLevel level = t == null ? null : level(server, t.dim);
                    if (level != null) {
                        player.teleportTo(level, t.x, t.y, t.z, player.getYRot(), player.getXRot());
                    }
                }
            }
            case "alerts.clear" -> {
                if (Staff.has(player, Perms.ALERTS)) {
                    EconomyWatch.clear();
                    sendPage(player, "alerts", data);
                }
            }
            case "restart.in" -> {
                if (Staff.has(player, Perms.RESTART)) {
                    int minutes = Math.max(1, Math.min(240, data.getInt("Minutes")));
                    Restart.inMinutes(minutes);
                    CompoundTag tag = new CompoundTag();
                    tag.putInt("Seconds", minutes * 60);
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        Staff.send(p, "restart", tag);
                    }
                    sendPage(player, "schedule", data);
                }
            }
            case "restart.cancel" -> {
                if (Staff.has(player, Perms.RESTART)) {
                    Restart.cancel(server);
                    sendPage(player, "schedule", data);
                }
            }
            case "sched.add" -> {
                if (Staff.has(player, Perms.SCHEDULE)) {
                    String cmd = data.getString("Command").trim();
                    if (!cmd.isEmpty()) {
                        Schedule.get(server).add(data.getString("Name").trim(), data.getInt("Days"), data.getInt("Hour"),
                                data.getInt("Minute"), cmd);
                    }
                    sendPage(player, "schedule", data);
                }
            }
            case "sched.remove" -> {
                if (Staff.has(player, Perms.SCHEDULE)) {
                    Schedule.get(server).remove(data.getInt("Id"));
                    sendPage(player, "schedule", data);
                }
            }
            case "sched.run" -> {
                if (Staff.has(player, Perms.SCHEDULE)) {
                    for (Schedule.Job j : Schedule.get(server).all()) {
                        if (j.id == data.getInt("Id")) {
                            Schedule.run(server, j.command);
                            Feedback.ok(player, "شغّلت {b}" + j.name + "{/b}");
                        }
                    }
                }
            }
            default -> {
                if (action.startsWith("rank.")) {
                    ranks(player, action, data);
                }
            }
        }
    }

    /* ================================================================== players */

    private interface OnTarget {
        void run(ServerPlayer target);
    }

    private static void online(MinecraftServer server, CompoundTag data, String node, ServerPlayer player, OnTarget then) {
        if (!Staff.has(player, node) || !data.hasUUID("Target")) {
            Feedback.bad(player, "ما عندك صلاحية لهذا");
            return;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(data.getUUID("Target"));
        if (target == null) {
            Feedback.bad(player, "اللاعب طلع");
            return;
        }
        then.run(target);
    }

    private static void punish(ServerPlayer player, CompoundTag data) {
        String type = data.getString("Type");
        if (!List.of(Punish.WARN, Punish.MUTE, Punish.JAIL, Punish.KICK, Punish.BAN).contains(type)
                || !Staff.has(player, "punish." + type)) {
            Feedback.bad(player, "ما عندك صلاحية لهذا");
            return;
        }
        MinecraftServer server = player.server;
        UUID target = null;
        String name = data.getString("Name").trim();
        if (data.hasUUID("Target")) {
            target = data.getUUID("Target");
        } else if (!name.isEmpty()) {
            GameProfile profile = server.getProfileCache() == null ? null : server.getProfileCache().get(name).orElse(null);
            if (profile != null) {
                target = profile.getId();
                name = profile.getName();
            }
        }
        if (target == null) {
            Feedback.bad(player, "ما لقيت هاللاعب");
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(target);
        if (online != null) {
            name = online.getGameProfile().getName();
            if (Staff.isOp(online) && !Staff.isOp(player)) {
                Feedback.bad(player, "ما تقدر تعاقب OP");
                return;
            }
        }
        String reason = data.getString("Reason").trim();
        if (reason.isEmpty()) {
            reason = "بدون سبب";
        }
        int minutes = Math.max(0, data.getInt("Minutes"));
        if (!Punish.get(server).apply(server, target, name, type, reason, minutes, player.getGameProfile().getName())) {
            Feedback.bad(player, "حدد مكان السجن أول");
            return;
        }
        Feedback.ok(player, "تمت العقوبة على {b}" + name + "{/b}");
        sendPage(player, "punish", data);
    }

    private static void createTicket(ServerPlayer player, String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() < 3) {
            Feedback.bad(player, "اكتب مشكلتك بوضوح");
            return;
        }
        if (t.length() > 300) {
            t = t.substring(0, 300);
        }
        long now = System.currentTimeMillis();
        Long last = LAST_TICKET.get(player.getUUID());
        if (last != null && now - last < 60_000L) {
            Feedback.bad(player, "انتظر دقيقة قبل ما ترسل تذكرة ثانية");
            return;
        }
        LAST_TICKET.put(player.getUUID(), now);
        Tickets.Ticket ticket = Tickets.get(player.server).open(player, t);
        Feedback.ok(player, "وصلت تذكرتك رقم {n}" + ticket.id + "{/n} للإدارة");
        Staff.tellStaff(player.server, Perms.TICKETS, "تذكرة جديدة من {b}" + player.getGameProfile().getName() + "{/b}");
    }

    /* ================================================================== ranks - operators only */

    private static void ranks(ServerPlayer player, String action, CompoundTag data) {
        if (!Staff.isOp(player)) {
            Feedback.bad(player, "إدارة الرتب للـ OP بس");
            return;
        }
        MinecraftServer server = player.server;
        StaffData staff = StaffData.get(server);
        String id = Perms.clean(data.getString("Id"));
        switch (action) {
            case "rank.create" -> {
                if (id.isEmpty() || staff.ranks.containsKey(id)) {
                    Feedback.bad(player, "اختر معرّف جديد للرتبة");
                    return;
                }
                String name = data.getString("Name").trim();
                staff.ranks.put(id, new StaffRank(id, name.isEmpty() ? id : name, data.contains("Color") ? data.getInt("Color") : 0xF0A93B));
            }
            case "rank.delete" -> {
                staff.ranks.remove(id);
                staff.members.values().removeIf(r -> r.equals(id));
            }
            case "rank.rename" -> {
                StaffRank r = staff.ranks.get(id);
                if (r != null && !data.getString("Name").isBlank()) {
                    r.name = data.getString("Name").trim();
                }
            }
            case "rank.color" -> {
                StaffRank r = staff.ranks.get(id);
                if (r != null) {
                    r.color = data.getInt("Color") & 0xFFFFFF;
                }
            }
            case "rank.perm" -> {
                StaffRank r = staff.ranks.get(id);
                String node = Perms.clean(data.getString("Node"));
                if (r != null && !node.isEmpty()) {
                    if (data.getBoolean("On")) {
                        r.perms.add(node);
                    } else {
                        r.perms.remove(node);
                    }
                }
            }
            case "rank.assign" -> {
                UUID target = resolve(server, data);
                if (target == null || !staff.ranks.containsKey(id)) {
                    Feedback.bad(player, "ما لقيت اللاعب أو الرتبة");
                    return;
                }
                staff.members.put(target, id);
                staff.names.put(target, nameOf(server, target, data.getString("Name")));
            }
            case "rank.unassign" -> {
                UUID target = resolve(server, data);
                if (target != null) {
                    staff.members.remove(target);
                }
            }
            default -> {
                return;
            }
        }
        staff.setDirty();
        Staff.refreshAll(server);
        sendPage(player, "ranks", data);
    }

    @Nullable
    private static UUID resolve(MinecraftServer server, CompoundTag data) {
        if (data.hasUUID("Target")) {
            return data.getUUID("Target");
        }
        String name = data.getString("Name").trim();
        if (name.isEmpty()) {
            return null;
        }
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getUUID();
        }
        GameProfile profile = server.getProfileCache() == null ? null : server.getProfileCache().get(name).orElse(null);
        return profile == null ? null : profile.getId();
    }

    private static String nameOf(MinecraftServer server, UUID id, String fallback) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        return p != null ? p.getGameProfile().getName() : fallback;
    }

    @Nullable
    private static ServerLevel level(MinecraftServer server, String dim) {
        ResourceLocation id = ResourceLocation.tryParse(dim);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /* ================================================================== pages */

    public static void sendPage(ServerPlayer player, String page, CompoundTag request) {
        MinecraftServer server = player.server;
        CompoundTag out = new CompoundTag();
        out.putString("Page", page);
        switch (page) {
            case "players" -> {
                ListTag list = new ListTag();
                StaffData staff = StaffData.get(server);
                Punish punish = Punish.get(server);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    CompoundTag t = new CompoundTag();
                    t.putUUID("Id", p.getUUID());
                    t.putString("Name", p.getGameProfile().getName());
                    StaffRank r = staff.rankOf(p.getUUID());
                    t.putString("Rank", Staff.isOp(p) ? "OP" : r == null ? "" : r.name);
                    t.putInt("Color", Staff.isOp(p) ? 0xE5442E : r == null ? 0 : r.color);
                    t.putBoolean("Vanished", Vanish.isVanished(p.getUUID()));
                    t.putBoolean("Muted", punish.muted(p.getUUID()));
                    t.putBoolean("Jailed", punish.jailed(p.getUUID()));
                    t.putString("Dim", p.level().dimension().location().getPath());
                    t.putString("Pos", p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ());
                    t.putInt("Health", Math.round(p.getHealth()));
                    t.putInt("Ping", p.latency);
                    list.add(t);
                }
                out.put("Players", list);
                out.putBoolean("Watching", Spectate.watching(player));
                out.putBoolean("Vanished", Vanish.isVanished(player.getUUID()));
            }
            case "deaths" -> {
                if (!request.hasUUID("Target")) {
                    return;
                }
                UUID target = request.getUUID("Target");
                out.putUUID("Target", target);
                ListTag list = new ListTag();
                for (DeathArchive.Death d : DeathArchive.get(server).of(target)) {
                    CompoundTag t = new CompoundTag();
                    t.putLong("At", d.at);
                    t.putString("Cause", d.cause);
                    t.putString("Place", d.place);
                    t.putBoolean("Restored", d.restored);
                    t.putInt("Count", d.items.size());
                    ListTag items = new ListTag();
                    for (int i = 0; i < Math.min(10, d.items.size()); i++) {
                        items.add(d.items.get(i).save(new CompoundTag()));
                    }
                    t.put("Items", items);
                    list.add(t);
                }
                out.put("Deaths", list);
            }
            case "ranks" -> {
                StaffData staff = StaffData.get(server);
                ListTag ranks = new ListTag();
                for (StaffRank r : staff.ranks.values()) {
                    ranks.add(r.save());
                }
                out.put("Ranks", ranks);
                ListTag members = new ListTag();
                for (Map.Entry<UUID, String> e : staff.members.entrySet()) {
                    CompoundTag m = new CompoundTag();
                    m.putUUID("Id", e.getKey());
                    m.putString("Rank", e.getValue());
                    m.putString("Name", staff.names.getOrDefault(e.getKey(), ""));
                    m.putBoolean("Online", server.getPlayerList().getPlayer(e.getKey()) != null);
                    members.add(m);
                }
                out.put("Members", members);
                ListTag nodes = new ListTag();
                for (Perms.Node n : Perms.ALL) {
                    CompoundTag t = new CompoundTag();
                    t.putString("Id", n.id());
                    t.putString("Label", n.label());
                    t.putString("Group", n.group());
                    nodes.add(t);
                }
                out.put("Nodes", nodes);
                ListTag cmds = new ListTag();
                for (String c : CommandGate.roots(server)) {
                    cmds.add(StringTag.valueOf(c));
                }
                out.put("Commands", cmds);
                out.putBoolean("Op", Staff.isOp(player));
            }
            case "punish" -> {
                Punish punish = Punish.get(server);
                List<CompoundTag> rows = new ArrayList<>();
                long now = System.currentTimeMillis();
                for (Map.Entry<UUID, List<Punish.Entry>> e : punish.all().entrySet()) {
                    for (Punish.Entry r : e.getValue()) {
                        CompoundTag t = new CompoundTag();
                        t.putUUID("Id", e.getKey());
                        t.putString("Name", punish.nameOf(e.getKey()));
                        t.putString("Type", r.type());
                        t.putString("Reason", r.reason());
                        t.putString("By", r.by());
                        t.putLong("At", r.at());
                        t.putLong("Until", r.until());
                        boolean active = switch (r.type()) {
                            case Punish.MUTE -> punish.muted(e.getKey());
                            case Punish.JAIL -> punish.jailed(e.getKey());
                            case Punish.BAN -> server.getPlayerList().getBans().isBanned(new GameProfile(e.getKey(), punish.nameOf(e.getKey())));
                            default -> false;
                        };
                        t.putBoolean("Active", active && (r.until() <= 0L || r.until() > now));
                        rows.add(t);
                    }
                }
                rows.sort((a, b) -> Long.compare(b.getLong("At"), a.getLong("At")));
                ListTag list = new ListTag();
                for (int i = 0; i < Math.min(80, rows.size()); i++) {
                    list.add(rows.get(i));
                }
                out.put("Records", list);
                out.putBoolean("HasJail", punish.hasJail());
            }
            case "tickets" -> {
                ListTag list = new ListTag();
                for (Tickets.Ticket t : Tickets.get(server).all()) {
                    CompoundTag c = new CompoundTag();
                    c.putInt("Id", t.id);
                    c.putString("Name", t.name);
                    c.putString("Text", t.text);
                    c.putLong("At", t.at);
                    c.putBoolean("Closed", t.closed);
                    c.putString("ClosedBy", t.closedBy);
                    list.add(c);
                }
                out.put("Tickets", list);
            }
            case "alerts" -> {
                ListTag list = new ListTag();
                for (EconomyWatch.Alert a : EconomyWatch.alerts()) {
                    CompoundTag c = new CompoundTag();
                    c.putLong("At", a.at());
                    c.putString("Name", a.name());
                    c.putString("Text", a.text());
                    list.add(c);
                }
                out.put("Alerts", list);
            }
            case "schedule" -> {
                ListTag list = new ListTag();
                for (Schedule.Job j : Schedule.get(server).all()) {
                    CompoundTag c = new CompoundTag();
                    c.putInt("Id", j.id);
                    c.putString("Name", j.name);
                    c.putInt("Days", j.days);
                    c.putInt("Hour", j.hour);
                    c.putInt("Minute", j.minute);
                    c.putString("Command", j.command);
                    list.add(c);
                }
                out.put("Jobs", list);
                out.putLong("RestartAt", Restart.at());
                ListTag times = new ListTag();
                for (String s : com.barbwra.mlum.MlumConfig.restartTimes()) {
                    times.add(StringTag.valueOf(s));
                }
                out.put("Times", times);
            }
            default -> {
                return;
            }
        }
        Staff.send(player, "page", out);
    }
}

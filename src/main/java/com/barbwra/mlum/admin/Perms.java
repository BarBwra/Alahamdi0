package com.barbwra.mlum.admin;

import java.util.List;

/**
 * Every permission the mod itself checks, with the label the admin panel shows for it.
 *
 * <h2>Open-ended on purpose</h2>
 * <p>These are only the mod's own. A rank can hold <i>any</i> string on top of them:</p>
 * <ul>
 *   <li>{@code cmd.<command>} lets the rank run a command it normally could not -
 *       {@code cmd.tp}, {@code cmd.time}, {@code cmd.mlum.downed} - for every command on the
 *       server, this mod's, vanilla's and every other mod's. See {@link CommandGate}.</li>
 *   <li>Anything else is stored as written, for scripts or for later systems to check with
 *       {@code /mlum staff check}.</li>
 * </ul>
 * <p>A node also grants everything under it: {@code punish} covers {@code punish.ban}, and
 * {@code cmd.*} or {@code *} cover everything.</p>
 *
 * <h2>What no rank can ever have</h2>
 * <p>Managing ranks, handing out items, and creative mode stay with server operators only. They are
 * not nodes at all, so no rank can be given them by mistake.</p>
 */
public final class Perms {

    private Perms() {
    }

    public record Node(String id, String label, String group) {
    }

    public static final String PANEL = "panel";
    public static final String TELEPORT = "players.teleport";
    public static final String BRING = "players.bring";
    public static final String INVENTORY = "players.inventory";
    public static final String INVENTORY_EDIT = "players.inventory.edit";
    public static final String SPECTATE = "players.spectate";
    public static final String VANISH = "vanish";
    public static final String RESTORE = "restore";
    public static final String WARN = "punish.warn";
    public static final String MUTE = "punish.mute";
    public static final String JAIL = "punish.jail";
    public static final String KICK = "punish.kick";
    public static final String BAN = "punish.ban";
    public static final String HISTORY = "punish.history";
    public static final String TICKETS = "tickets";
    public static final String ALERTS = "alerts";
    public static final String RESTART = "restart";
    public static final String SCHEDULE = "schedule";
    public static final String DEALER = "dealer.edit";

    public static final List<Node> ALL = List.of(
            new Node(PANEL, "يفتح لوحة الأدمن", "عام"),
            new Node(TELEPORT, "ينتقل للاعب", "اللاعبين"),
            new Node(BRING, "يسحب لاعب عنده", "اللاعبين"),
            new Node(INVENTORY, "يشوف شنطة اللاعب", "اللاعبين"),
            new Node(INVENTORY_EDIT, "يعدّل شنطة اللاعب", "اللاعبين"),
            new Node(SPECTATE, "يراقب من عيون اللاعب", "اللاعبين"),
            new Node(VANISH, "وضع الخفاء", "عام"),
            new Node(RESTORE, "يسترجع شنطة موت", "اللاعبين"),
            new Node(WARN, "إنذار", "العقوبات"),
            new Node(MUTE, "كتم", "العقوبات"),
            new Node(JAIL, "سجن", "العقوبات"),
            new Node(KICK, "طرد", "العقوبات"),
            new Node(BAN, "باند", "العقوبات"),
            new Node(HISTORY, "يشوف سجل العقوبات", "العقوبات"),
            new Node(TICKETS, "تذاكر الدعم", "عام"),
            new Node(ALERTS, "تنبيهات التكرار", "عام"),
            new Node(RESTART, "الريستارت المجدول", "السيرفر"),
            new Node(SCHEDULE, "الأحداث المجدولة", "السيرفر"),
            new Node(DEALER, "يعدّل معرض المركبات", "السيرفر"));

    /** Does a rank holding {@code granted} have {@code wanted}? */
    public static boolean covers(String granted, String wanted) {
        if (granted == null || granted.isEmpty()) {
            return false;
        }
        if (granted.equals("*") || granted.equals(wanted)) {
            return true;
        }
        if (granted.endsWith(".*")) {
            return wanted.startsWith(granted.substring(0, granted.length() - 1));
        }
        return wanted.startsWith(granted + ".");
    }

    /** Lower case, no spaces - what every stored node looks like. */
    public static String clean(String node) {
        return node == null ? "" : node.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
    }
}

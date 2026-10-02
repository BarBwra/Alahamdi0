package com.barbwra.mlum.quest;

import com.barbwra.mlum.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Every quest a player currently has on their board, keyed by the {@code line} the Skript command
 * assigned. Purely a display store - this mod holds no quest logic, it renders what Skript sends.
 *
 * <p>Lives in the Forge persistent tag, so a board survives death and dimension changes. Every
 * mutation re-syncs the whole board: it is at most {@value #MAX_LINES} short entries, and sending
 * it whole removes any chance of the client drifting out of step with the server.</p>
 */
public final class QuestBoard {

    private QuestBoard() {
    }

    public static final int MAX_LINES = 20;
    private static final String KEY = "mlum:quests";
    private static final String KEY_CLAIMED = "mlum:quests_claimed";

    /* ---------------------------------------------------------------- storage */

    private static CompoundTag persistentRoot(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Never null. Sorted by line so the UI order is stable. */
    public static List<QuestEntry> get(Player player) {
        CompoundTag persisted = persistentRoot(player);
        if (!persisted.contains(KEY, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = persisted.getList(KEY, Tag.TAG_COMPOUND);
        List<QuestEntry> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(QuestEntry.load(list.getCompound(i)));
        }
        out.sort(Comparator.comparingInt(QuestEntry::line));
        return out;
    }

    private static void store(Player player, List<QuestEntry> entries) {
        entries.sort(Comparator.comparingInt(QuestEntry::line));
        ListTag list = new ListTag();
        for (QuestEntry entry : entries) {
            list.add(entry.save());
        }
        persistentRoot(player).put(KEY, list);
    }

    /* ------------------------------------------------------------- mutations */

    /**
     * Adds or replaces the quest on {@code entry.line()}.
     *
     * <p>Writing a line also clears its claimed flag. Skript is the source of truth for what a line
     * <i>is</i>, so re-sending one means it is a new objective and the reward is claimable again -
     * if that is not what you want, clear the line instead of rewriting it.</p>
     */
    public static boolean put(ServerPlayer player, QuestEntry entry) {
        List<QuestEntry> entries = new ArrayList<>(get(player));
        entries.removeIf(existing -> existing.line() == entry.line());

        boolean stored = entries.size() < MAX_LINES;
        if (stored) {
            entries.add(entry);
        }
        store(player, entries);
        setClaimed(player, entry.line(), false);
        sync(player);
        return stored;
    }

    public static boolean remove(ServerPlayer player, int line) {
        List<QuestEntry> entries = new ArrayList<>(get(player));
        boolean removed = entries.removeIf(existing -> existing.line() == line);
        store(player, entries);
        sync(player);
        return removed;
    }

    public static void clear(ServerPlayer player) {
        persistentRoot(player).remove(KEY);
        persistentRoot(player).remove(KEY_CLAIMED);
        sync(player);
    }

    /** Forces a line to its target, which is what marks it complete. */
    public static boolean complete(ServerPlayer player, int line) {
        List<QuestEntry> entries = new ArrayList<>(get(player));
        for (int i = 0; i < entries.size(); i++) {
            QuestEntry e = entries.get(i);
            if (e.line() == line) {
                int max = Math.max(1, e.max());
                entries.set(i, new QuestEntry(e.line(), e.type(), e.title(), max, max,
                        e.description(), e.rewards()));
                store(player, entries);
                sync(player);
                return true;
            }
        }
        return false;
    }

    /* ----------------------------------------------------------------- claiming */

    /**
     * Which lines the player has already pressed claim on.
     *
     * <p>Claimed state is the one piece of quest data the <b>mod</b> owns rather than Skript. It
     * has to be, or a player could press claim twice before Skript reacted.</p>
     */
    public static Set<Integer> claimedLines(Player player) {
        int[] raw = persistentRoot(player).getIntArray(KEY_CLAIMED);
        if (raw.length == 0) {
            return Set.of();
        }
        Set<Integer> out = new LinkedHashSet<>();
        for (int line : raw) {
            out.add(line);
        }
        return out;
    }

    public static boolean isClaimed(Player player, int line) {
        for (int claimed : persistentRoot(player).getIntArray(KEY_CLAIMED)) {
            if (claimed == line) {
                return true;
            }
        }
        return false;
    }

    public static void claim(ServerPlayer player, int line) {
        setClaimed(player, line, true);
        sync(player);
    }

    private static void setClaimed(ServerPlayer player, int line, boolean claimed) {
        Set<Integer> lines = new LinkedHashSet<>(claimedLines(player));
        boolean changed = claimed ? lines.add(line) : lines.remove(line);
        if (!changed) {
            return;
        }
        int[] raw = new int[lines.size()];
        int i = 0;
        for (int value : lines) {
            raw[i++] = value;
        }
        persistentRoot(player).putIntArray(KEY_CLAIMED, raw);
    }

    public static void sync(ServerPlayer player) {
        ModNetwork.sendQuestBoard(player, get(player), claimedLines(player));
    }
}

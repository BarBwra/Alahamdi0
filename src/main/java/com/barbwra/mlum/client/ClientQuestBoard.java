package com.barbwra.mlum.client;

import com.barbwra.mlum.quest.QuestEntry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Last quest board pushed by the server. Read by the quest screen each frame. */
@OnlyIn(Dist.CLIENT)
public final class ClientQuestBoard {

    private ClientQuestBoard() {
    }

    private static volatile List<QuestEntry> entries = List.of();
    private static volatile Set<Integer> claimed = Set.of();

    public static void set(List<QuestEntry> incoming, Set<Integer> claimedLines) {
        entries = incoming == null ? List.of() : List.copyOf(incoming);
        claimed = claimedLines == null ? Set.of() : Set.copyOf(claimedLines);
    }

    public static boolean isClaimed(int line) {
        return claimed.contains(line);
    }

    public static List<QuestEntry> all() {
        return entries;
    }

    public static List<QuestEntry> active() {
        List<QuestEntry> out = new ArrayList<>();
        for (QuestEntry entry : entries) {
            if (!entry.isComplete()) {
                out.add(entry);
            }
        }
        return out;
    }

    public static List<QuestEntry> completed() {
        List<QuestEntry> out = new ArrayList<>();
        for (QuestEntry entry : entries) {
            if (entry.isComplete()) {
                out.add(entry);
            }
        }
        return out;
    }

    public static void clear() {
        entries = List.of();
        claimed = Set.of();
    }
}

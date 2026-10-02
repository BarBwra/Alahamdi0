package com.barbwra.mlum.quest;

import com.barbwra.mlum.client.gui.Theme;

/**
 * Main, side or daily quest. Drives the label and the colour used everywhere the quest appears.
 *
 * <p><b>Appended, never reordered</b>: the ordinal goes on the wire and into saved boards, so
 * {@link #DAILY} comes after the two that already existed.</p>
 */
public enum QuestType {

    MAIN("main", "gui.mlum.quest.type.main"),
    SIDE("side", "gui.mlum.quest.type.side"),
    DAILY("daily", "gui.mlum.quest.type.daily");

    private final String id;
    private final String key;

    QuestType(String id, String key) {
        this.id = id;
        this.key = key;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return key;
    }

    /** Main quests take the accent colour, side quests plain white, as specced. */
    public int color() {
        return this == MAIN ? Theme.accent() : 0xFFFFFFFF;
    }

    public static QuestType byId(String raw) {
        if (raw != null) {
            String v = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (v.equals("side") || v.equals("s") || v.equals("secondary") || v.equals("جانبية")) {
                return SIDE;
            }
            if (v.equals("daily") || v.equals("d") || v.equals("day") || v.equals("يومية")) {
                return DAILY;
            }
        }
        return MAIN;
    }
}

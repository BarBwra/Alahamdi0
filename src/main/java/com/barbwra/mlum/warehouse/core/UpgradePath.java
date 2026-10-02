package com.barbwra.mlum.warehouse.core;

import java.util.Locale;

/**
 * The four upgrade tracks, each five levels deep.
 *
 * <p>Levels are bought with money but <b>gated on deliveries</b>, not on money alone. That gate is
 * the reason the whole delivery-statistics table exists, and it is the mechanic worth protecting:
 * it forces a player to actually run cargo of specific types and tiers rather than grind currency
 * elsewhere and buy the tree outright.</p>
 *
 * <p>In the Skript version the gate lived only in the code that <i>drew</i> the upgrade icons, and
 * the purchase handler trusted the icon's lore text. Here the gate is checked in
 * {@code UpgradeService} on the server at purchase time and the render is a hint.</p>
 */
public enum UpgradePath {

    /** Movement speed while carrying cargo. Applied as an attribute modifier, never as a potion. */
    SPEED("speed", "مهارة السرعة"),
    /** Storage capacity and crate shelf life. */
    LOGISTICS("logistics", "مهارة اللوجستيات"),
    /** Damage resistance and absorption during a run. */
    ARMOR("armor", "مهارة الدرع"),
    /** Simultaneous production lines. */
    INDUSTRY("crafting", "مهارة الصناعة");

    public static final int MAX_LEVEL = 5;

    private final String id;
    private final String display;

    UpgradePath(String id, String display) {
        this.id = id;
        this.display = display;
    }

    /** On-disk key. Matches the Skript variable suffix so imported saves read straight through. */
    public String id() {
        return id;
    }

    /** Logical (unshaped) Arabic. */
    public String display() {
        return display;
    }

    public static UpgradePath byId(String raw) {
        if (raw != null) {
            String key = raw.toLowerCase(Locale.ROOT);
            for (UpgradePath path : values()) {
                if (path.id.equals(key)) {
                    return path;
                }
            }
        }
        return SPEED;
    }
}

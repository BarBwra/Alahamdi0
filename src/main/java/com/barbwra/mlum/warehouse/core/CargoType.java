package com.barbwra.mlum.warehouse.core;

import java.util.Locale;

/**
 * The three contraband categories.
 *
 * <p>Each carries its own colour, and the choice of hues is deliberate: <b>no cargo colour is ever
 * red</b>. Red belongs exclusively to danger - the leak warning, an expiring deadline, a spoiled
 * crate. A red "weapons" tile sitting next to a red EXPOSED banner is unreadable at exactly the
 * moment the player is under the most pressure, so weapons are violet instead.</p>
 *
 * <p>The {@code id} is the on-disk key. It matches the Skript prototype's strings so an imported
 * save reads straight through without a translation table.</p>
 */
public enum CargoType {

    /** Food authority crates. */
    FOOD("food", "صندوق هيئة الغذاء", 0xFFD9A441),
    /** Medical supply crates. */
    MEDICAL("med", "صندوق علاجات طبية", 0xFF4FC3F7),
    /** Firearms crates. */
    WEAPON("weapon", "صندوق أسلحة نارية", 0xFFB05CE0);

    private final String id;
    private final String display;
    private final int color;

    CargoType(String id, String display, int color) {
        this.id = id;
        this.display = display;
        this.color = color;
    }

    public String id() {
        return id;
    }

    /** Logical (unshaped) Arabic. Run it through {@code ArabicText.display} before drawing. */
    public String display() {
        return display;
    }

    public int color() {
        return color;
    }

    /** Never throws - an unknown id resolves to FOOD so a corrupt record still renders. */
    public static CargoType byId(String raw) {
        if (raw != null) {
            String key = raw.toLowerCase(Locale.ROOT);
            for (CargoType type : values()) {
                if (type.id.equals(key)) {
                    return type;
                }
            }
        }
        return FOOD;
    }

    /** Roman numeral for a crate tier, for display only. */
    public static String tierNumeral(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(tier);
        };
    }
}

package com.barbwra.mlum.bag;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * The six rarity tiers from the mockup, with the colours it uses for each.
 *
 * <p><b>Why this is not in the client package.</b> The server decides a stack's tier - it is read
 * from config, and the answer travels in a packet - so the enum has to exist on a dedicated server
 * where {@code Theme} does not. The colours ride along because they are data, and keeping them
 * beside the tier means the client never has to hold a parallel switch that can fall out of step.</p>
 *
 * <p><b>Vanilla only has four.</b> {@link #fromVanilla} maps them onto the first four tiers so every
 * item on the server is coloured sensibly before an admin writes a line of config; legendary and
 * mythic cannot be derived and are config-only, which is exactly the point of having them.</p>
 */
public enum ItemTier {

    COMMON("common", 0xFFB9B7A6),
    UNCOMMON("uncommon", 0xFF7CC25A),
    RARE("rare", 0xFF4EA8E8),
    EPIC("epic", 0xFFB57CE8),
    LEGENDARY("legendary", 0xFFF0A93B),
    MYTHIC("mythic", 0xFFFF4D6A);

    private final String id;
    private final int colour;

    ItemTier(String id, int colour) {
        this.id = id;
        this.colour = colour;
    }

    public String id() {
        return id;
    }

    /** ARGB, straight from {@code --r-*} in the mockup. */
    public int colour() {
        return colour;
    }

    /** The Arabic label the details panel's rarity chip shows. */
    public String arabic() {
        return switch (this) {
            case COMMON -> "عادي";
            case UNCOMMON -> "غير شائع";
            case RARE -> "نادر";
            case EPIC -> "ملحمي";
            case LEGENDARY -> "أسطوري";
            case MYTHIC -> "خرافي";
        };
    }

    /** Parses a config value. Unknown text is null so the caller can log it and fall back. */
    @Nullable
    public static ItemTier byId(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        for (ItemTier tier : values()) {
            if (tier.id.equals(text)) {
                return tier;
            }
        }
        return null;
    }

    public static ItemTier fromVanilla(Rarity rarity) {
        if (rarity == Rarity.UNCOMMON) {
            return UNCOMMON;
        }
        if (rarity == Rarity.RARE) {
            return RARE;
        }
        if (rarity == Rarity.EPIC) {
            return EPIC;
        }
        return COMMON;
    }

    /** The tier a stack is drawn at: the config's answer if it has one, otherwise vanilla's. */
    public static ItemTier of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return COMMON;
        }
        ItemTier configured = BagConfig.rarityOf(stack);
        return configured != null ? configured : fromVanilla(stack.getRarity());
    }
}

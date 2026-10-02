package com.barbwra.mlum.client.ui.view;

/**
 * One item as the UI sees it. The game fills {@link #handle} with its item stack and every other
 * field from it; the desktop preview fills it from the design's example data.
 */
public final class Item {

    /** What the canvas draws: an ItemStack wrapper in game, a sprite name in the preview. */
    public Object handle;
    public String name = "";
    /** 0 common, 1 uncommon, 2 rare, 3 epic, 4 legendary, 5 mythic. */
    public int rarity;
    public int count = 1;
    /** 0..1 remaining durability, or -1 for items without any. */
    public float durability = -1.0F;
    public String category = "";
    /** A warning chip in rust, e.g. an expiry. Null for none. */
    public String extra;
    public String description = "";
    /** Sell price per unit, or -1 when the trader will not buy it. */
    public long price = -1L;
    /** Footprint in the bag grid. */
    public int w = 1;
    public int h = 1;
    public boolean gun;
    /** Wide artwork (a gun's HUD image) to contain instead of the square icon, or null. */
    public Object art;
    /** Multiplies the picture - dimmed rewards on locked milestones. */
    public int tint = 0xFFFFFFFF;

    public boolean tall() {
        return h > w;
    }

    /**
     * The same item drawn with a different number on it.
     *
     * <p>A copy rather than a setter: these are built fresh every frame but handed around by
     * reference, and a grid cell showing "what is left behind" must not change the count the ghost
     * under the cursor is drawing from.</p>
     */
    public Item withCount(int value) {
        Item copy = new Item();
        copy.handle = handle;
        copy.name = name;
        copy.rarity = rarity;
        copy.count = value;
        copy.durability = durability;
        copy.category = category;
        copy.extra = extra;
        copy.description = description;
        copy.price = price;
        copy.w = w;
        copy.h = h;
        copy.gun = gun;
        copy.art = art;
        copy.tint = tint;
        return copy;
    }

    public boolean glows() {
        return rarity >= 2;
    }

    public static final String[] RARITY_NAMES = {"عادي", "غير شائع", "نادر", "ملحمي", "أسطوري", "خرافي"};

    public String rarityName() {
        return RARITY_NAMES[Math.max(0, Math.min(5, rarity))];
    }
}

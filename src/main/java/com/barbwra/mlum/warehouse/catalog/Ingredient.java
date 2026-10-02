package com.barbwra.mlum.warehouse.catalog;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * One line of a recipe: <i>which item, how many, what to call it, and how rare it looks.</i>
 *
 * <p>JSON shape, one object per requirement:</p>
 * <pre>{@code
 * { "item": "survivorsarsenal:copper_wire", "count": 8, "display": "أسلاك نحاسية", "rarity": 2 }
 * }</pre>
 *
 * <p><b>Matching is by registry id and nothing else.</b> This is the single most important line in
 * the port. The Skript prototype compared the lowercased <i>display name</i> of every stack in the
 * player's inventory against substrings like {@code "plank"} and {@code "battery"}, which meant an
 * anvil-renamed stack of dirt satisfied any recipe, and {@code "plank"} additionally matched
 * vanilla oak planks - a 100$ crate for four of the cheapest block in the game. Names are player
 * data. Registry ids are not.</p>
 *
 * <p><b>{@code display} is cosmetic only.</b> It is the Arabic label drawn in the requisition list;
 * changing it cannot change what the recipe accepts. {@code rarity} is 1 (common) to 5 (rarest) and
 * only picks the colour the line is drawn in, so a pack author can flag the hard-to-find components
 * without touching balance.</p>
 *
 * <p>An ingredient whose mod is absent resolves to {@link #isAvailable() unavailable} rather than
 * crashing: the recipe is then shown greyed out and locked in the terminal, and can never be
 * started. That is what lets this mod load in a pack that is missing an optional dependency.</p>
 */
public record Ingredient(ResourceLocation item, int count, String display, int rarity) {

    /** Rarity tint, 1..5. Deliberately not red - red is reserved for danger states. */
    private static final int[] RARITY_COLORS = {
            0xFF9DAEBC,   // 1 common      - cold grey
            0xFF6FCF97,   // 2 uncommon    - green
            0xFF4FC3F7,   // 3 rare        - blue
            0xFFB05CE0,   // 4 very rare   - violet
            0xFFE8C15A,   // 5 rarest      - gold
    };

    public static Ingredient fromJson(JsonObject o) {
        String id = o.get("item").getAsString();
        int count = o.has("count") ? Math.max(1, o.get("count").getAsInt()) : 1;
        String display = o.has("display") ? o.get("display").getAsString() : id;
        int rarity = o.has("rarity") ? Math.max(1, Math.min(5, o.get("rarity").getAsInt())) : 1;
        return new Ingredient(new ResourceLocation(id), count, display, rarity);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("item", item.toString());
        o.addProperty("count", count);
        o.addProperty("display", display);
        o.addProperty("rarity", rarity);
        return o;
    }

    /** The resolved item, or {@code null} when the owning mod is not installed. */
    public Item resolve() {
        return ForgeRegistries.ITEMS.getValue(item);
    }

    public boolean isAvailable() {
        return ForgeRegistries.ITEMS.containsKey(item);
    }

    /** True when {@code stack} counts toward this requirement. Registry identity, nothing else. */
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && item.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    public int rarityColor() {
        return RARITY_COLORS[Math.max(1, Math.min(5, rarity)) - 1];
    }
}

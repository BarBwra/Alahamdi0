package com.barbwra.mlum.warehouse.catalog;

import com.barbwra.mlum.warehouse.core.CargoType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * One assembly line recipe: a set of {@link Ingredient}s in, one crate out, after a wall-clock wait.
 *
 * <p>JSON shape:</p>
 * <pre>{@code
 * {
 *   "id": "weapon_3",
 *   "cargo": "weapon",
 *   "tier": 3,
 *   "durationSeconds": 2400,
 *   "value": { "min": 500, "max": 750 },
 *   "icon": "survivorsarsenal:military_crate",
 *   "ingredients": [ { "item": "...", "count": 8, "display": "...", "rarity": 3 } ]
 * }
 * }</pre>
 *
 * <p>{@code value} is rolled once when assembly <i>starts</i> and stored on the crate, so a crate's
 * base worth is fixed the moment the player commits the materials. What moves afterwards is the
 * market index it gets multiplied by at sale time - that separation is what lets prices swing
 * without retroactively rewriting the contents of somebody's warehouse.</p>
 *
 * <p>{@code icon} is the item shown in the terminal's recipe grid. It is display only: the terminal
 * sends back a recipe <i>id</i>, never an item, so there is no way to trigger a recipe by holding
 * or clicking a matching item. In the Skript version the crafting handler compared the clicked
 * inventory item against a freshly built stack, which meant any item sharing the same base material
 * fired the wrong recipe.</p>
 */
public record Recipe(String id,
                     CargoType cargo,
                     int tier,
                     int durationSeconds,
                     int minValue,
                     int maxValue,
                     ResourceLocation icon,
                     List<Ingredient> ingredients) {

    public static Recipe fromJson(JsonObject o) {
        JsonObject value = o.getAsJsonObject("value");
        List<Ingredient> parts = new ArrayList<>();
        JsonArray array = o.getAsJsonArray("ingredients");
        if (array != null) {
            for (int i = 0; i < array.size(); i++) {
                parts.add(Ingredient.fromJson(array.get(i).getAsJsonObject()));
            }
        }
        return new Recipe(
                o.get("id").getAsString(),
                CargoType.byId(o.get("cargo").getAsString()),
                Math.max(1, o.get("tier").getAsInt()),
                Math.max(1, o.get("durationSeconds").getAsInt()),
                value.get("min").getAsInt(),
                value.get("max").getAsInt(),
                new ResourceLocation(o.has("icon") ? o.get("icon").getAsString() : "minecraft:barrel"),
                List.copyOf(parts));
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("cargo", cargo.id());
        o.addProperty("tier", tier);
        o.addProperty("durationSeconds", durationSeconds);

        JsonObject value = new JsonObject();
        value.addProperty("min", minValue);
        value.addProperty("max", maxValue);
        o.add("value", value);

        o.addProperty("icon", icon.toString());

        JsonArray array = new JsonArray();
        for (Ingredient part : ingredients) {
            array.add(part.toJson());
        }
        o.add("ingredients", array);
        return o;
    }

    /** Rolled once, when the player commits the materials. */
    public int rollValue(RandomSource random) {
        if (maxValue <= minValue) {
            return minValue;
        }
        return minValue + random.nextInt(maxValue - minValue + 1);
    }

    /**
     * False when any ingredient's mod is missing. Such a recipe is drawn locked and can never be
     * started, which is what lets the pack boot with an optional dependency absent instead of
     * hard-crashing on a null item.
     */
    public boolean isAvailable() {
        for (Ingredient part : ingredients) {
            if (!part.isAvailable()) {
                return false;
            }
        }
        return true;
    }

    /** Logical Arabic, e.g. {@code صندوق أسلحة نارية III}. Shape it before drawing. */
    public String displayName() {
        return cargo.display() + " " + CargoType.tierNumeral(tier);
    }
}

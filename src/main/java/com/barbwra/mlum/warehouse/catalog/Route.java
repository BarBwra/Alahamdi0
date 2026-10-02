package com.barbwra.mlum.warehouse.catalog;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A delivery route: where the runner is dropped, where the cargo has to reach, and what that leg is
 * worth.
 *
 * <p>JSON shape:</p>
 * <pre>{@code
 * {
 *   "id": "quarry_pass",
 *   "display": "ممر المحجر",
 *   "dimension": "minecraft:overworld",
 *   "spawn": { "x": -1059, "y": 4, "z": 1136, "yaw": 140 },
 *   "drop":  { "x": -1658, "y": 4, "z": 396 },
 *   "riskTier": 2,
 *   "multiplier": 1.20
 * }
 * }</pre>
 *
 * <p>Routes are data, not code. The Skript version had three hardcoded spawn/drop pairs chosen at
 * random with no way to add a fourth without editing the script; here a server owner adds one by
 * appending to {@code config/mwh/routes.json} and running the reload command.</p>
 *
 * <p><b>Risk is a player choice, not a dice roll.</b> The runner picks the route in the dispatch
 * screen and sees the multiplier before committing. A longer, more exposed leg pays more. That is
 * the risk/reward axis the prototype was missing - it multiplied payouts by how many players
 * happened to be online instead, which the player has no control over at all.</p>
 */
public record Route(String id,
                    String display,
                    ResourceLocation dimension,
                    Vec3 spawn,
                    float spawnYaw,
                    Vec3 drop,
                    int riskTier,
                    double multiplier) {

    public static Route fromJson(JsonObject o) {
        JsonObject spawn = o.getAsJsonObject("spawn");
        JsonObject drop = o.getAsJsonObject("drop");
        return new Route(
                o.get("id").getAsString(),
                o.has("display") ? o.get("display").getAsString() : o.get("id").getAsString(),
                new ResourceLocation(o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld"),
                new Vec3(spawn.get("x").getAsDouble(), spawn.get("y").getAsDouble(), spawn.get("z").getAsDouble()),
                spawn.has("yaw") ? spawn.get("yaw").getAsFloat() : 0.0F,
                new Vec3(drop.get("x").getAsDouble(), drop.get("y").getAsDouble(), drop.get("z").getAsDouble()),
                Math.max(1, Math.min(3, o.has("riskTier") ? o.get("riskTier").getAsInt() : 1)),
                o.has("multiplier") ? o.get("multiplier").getAsDouble() : 1.0D);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("display", display);
        o.addProperty("dimension", dimension.toString());

        JsonObject spawnJson = new JsonObject();
        spawnJson.addProperty("x", spawn.x);
        spawnJson.addProperty("y", spawn.y);
        spawnJson.addProperty("z", spawn.z);
        spawnJson.addProperty("yaw", spawnYaw);
        o.add("spawn", spawnJson);

        JsonObject dropJson = new JsonObject();
        dropJson.addProperty("x", drop.x);
        dropJson.addProperty("y", drop.y);
        dropJson.addProperty("z", drop.z);
        o.add("drop", dropJson);

        o.addProperty("riskTier", riskTier);
        o.addProperty("multiplier", multiplier);
        return o;
    }

    public ResourceKey<Level> levelKey() {
        return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension);
    }

    /** Straight-line spawn-to-drop distance in blocks, for the dispatch screen. */
    public double distance() {
        return spawn.distanceTo(drop);
    }
}

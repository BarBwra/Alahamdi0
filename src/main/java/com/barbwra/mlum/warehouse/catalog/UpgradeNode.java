package com.barbwra.mlum.warehouse.catalog;

import com.barbwra.mlum.warehouse.core.CargoType;
import com.barbwra.mlum.warehouse.core.UpgradePath;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One node of the upgrade tree: a level on a path, its price, and the deliveries that unlock it.
 *
 * <p>JSON shape:</p>
 * <pre>{@code
 * {
 *   "path": "speed", "level": 3, "cost": 3000,
 *   "description": ["يزيد سرعة الحركة أثناء المهمة", "بدون التأثير على سرعة اللاعب خارجها"],
 *   "requirements": [
 *     { "cargo": "med",    "tiers": [1],       "count": 75 },
 *     { "cargo": "food",   "tiers": [2],       "count": 35 },
 *     { "cargo": "weapon", "tiers": [1,2,3],   "count": 5  }
 *   ]
 * }
 * }</pre>
 *
 * <p>{@code tiers} is a list rather than a single number so one requirement can express "any tier"
 * ({@code [1,2,3]}) or a band ({@code [2,3]}) without a separate syntax. The prototype needed six
 * distinct helper calls per cargo type to say the same thing.</p>
 */
public record UpgradeNode(UpgradePath path,
                          int level,
                          int cost,
                          List<String> description,
                          List<Requirement> requirements) {

    /** A delivery count the player must have reached before this node can be bought. */
    public record Requirement(CargoType cargo, List<Integer> tiers, int count) {

        public static Requirement fromJson(JsonObject o) {
            List<Integer> tiers = new ArrayList<>();
            JsonArray array = o.getAsJsonArray("tiers");
            if (array == null) {
                tiers.add(1);
            } else {
                for (int i = 0; i < array.size(); i++) {
                    tiers.add(array.get(i).getAsInt());
                }
            }
            return new Requirement(
                    CargoType.byId(o.get("cargo").getAsString()),
                    List.copyOf(tiers),
                    Math.max(0, o.get("count").getAsInt()));
        }

        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("cargo", cargo.id());
            JsonArray array = new JsonArray();
            for (int tier : tiers) {
                array.add(tier);
            }
            o.add("tiers", array);
            o.addProperty("count", count);
            return o;
        }

        /** {@code I}, {@code II/III}, or {@code Any} - the tier band, for the requirement line. */
        public String tierLabel() {
            if (tiers.size() >= 3) {
                return "Any";
            }
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < tiers.size(); i++) {
                if (i > 0) {
                    out.append('/');
                }
                out.append(CargoType.tierNumeral(tiers.get(i)));
            }
            return out.toString();
        }
    }

    public static UpgradeNode fromJson(JsonObject o) {
        List<String> desc = new ArrayList<>();
        JsonArray descArray = o.getAsJsonArray("description");
        if (descArray != null) {
            for (int i = 0; i < descArray.size(); i++) {
                desc.add(descArray.get(i).getAsString());
            }
        }

        List<Requirement> reqs = new ArrayList<>();
        JsonArray reqArray = o.getAsJsonArray("requirements");
        if (reqArray != null) {
            for (int i = 0; i < reqArray.size(); i++) {
                reqs.add(Requirement.fromJson(reqArray.get(i).getAsJsonObject()));
            }
        }

        return new UpgradeNode(
                UpgradePath.byId(o.get("path").getAsString()),
                Math.max(1, o.get("level").getAsInt()),
                Math.max(0, o.get("cost").getAsInt()),
                List.copyOf(desc),
                List.copyOf(reqs));
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("path", path.id());
        o.addProperty("level", level);
        o.addProperty("cost", cost);

        JsonArray descArray = new JsonArray();
        for (String line : description) {
            descArray.add(line);
        }
        o.add("description", descArray);

        JsonArray reqArray = new JsonArray();
        for (Requirement req : requirements) {
            reqArray.add(req.toJson());
        }
        o.add("requirements", reqArray);
        return o;
    }
}

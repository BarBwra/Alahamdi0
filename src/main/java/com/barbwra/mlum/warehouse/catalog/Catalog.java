package com.barbwra.mlum.warehouse.catalog;

import com.barbwra.mlum.warehouse.WarehouseMod;
import com.barbwra.mlum.warehouse.core.UpgradePath;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything a server owner can retune without recompiling: recipes, routes and the upgrade tree.
 *
 * <p>Three JSON files under {@code config/mwh/}. On first boot each is copied out of the jar, so a
 * fresh install lands with the full nine-recipe, three-route, twenty-node set already populated and
 * editable. Afterwards the files on disk win - the mod never overwrites them.</p>
 *
 * <p>Reload with {@code /mwh admin reload}. A reload that fails to parse leaves the previous
 * catalog in place and reports the error, because swapping a live server to an empty recipe list
 * mid-session would strand every player with an assembly line they cannot restart.</p>
 *
 * <p><b>Registry ids are not validated at load time on purpose.</b> An ingredient naming an item
 * from a mod that is not installed parses fine and is simply reported as unavailable, which greys
 * the recipe out in the terminal. Failing the whole catalog because one optional dependency is
 * missing would take the warehouse offline for a problem the player cannot see or fix.</p>
 */
public final class Catalog {

    private static final Catalog INSTANCE = new Catalog();

    public static Catalog get() {
        return INSTANCE;
    }

    private Catalog() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final String DIR = "mwh";
    private static final String RECIPES = "recipes.json";
    private static final String ROUTES = "routes.json";
    private static final String UPGRADES = "upgrades.json";

    private final Map<String, Recipe> recipes = new LinkedHashMap<>();
    private final Map<String, Route> routes = new LinkedHashMap<>();
    private final Map<Long, UpgradeNode> upgrades = new LinkedHashMap<>();

    /* ------------------------------------------------------------------ loading */

    private Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve(DIR);
    }

    /** Copies the packaged defaults out on first boot, then loads whatever is on disk. */
    public void loadOrCreateDefaults() {
        try {
            Path dir = configDir();
            Files.createDirectories(dir);
            for (String file : new String[]{RECIPES, ROUTES, UPGRADES}) {
                Path target = dir.resolve(file);
                if (!Files.exists(target)) {
                    copyDefault(file, target);
                }
            }
        } catch (IOException e) {
            WarehouseMod.LOGGER.error("[mwh] Could not prepare config/{} - {}", DIR, e.toString());
        }
        reload();
    }

    private void copyDefault(String file, Path target) throws IOException {
        try (InputStream in = Catalog.class.getResourceAsStream("/data/mwh/default/" + file)) {
            if (in == null) {
                WarehouseMod.LOGGER.error("[mwh] Packaged default {} is missing from the jar.", file);
                return;
            }
            Files.copy(in, target);
            WarehouseMod.LOGGER.info("[mwh] Wrote default config/{}/{}", DIR, file);
        }
    }

    /**
     * Re-reads all three files. Returns a human-readable report for the admin command; on failure
     * the previous catalog is untouched.
     */
    public synchronized String reload() {
        Map<String, Recipe> newRecipes = new LinkedHashMap<>();
        Map<String, Route> newRoutes = new LinkedHashMap<>();
        Map<Long, UpgradeNode> newUpgrades = new LinkedHashMap<>();

        try {
            for (JsonElement e : readArray(RECIPES, "recipes")) {
                Recipe recipe = Recipe.fromJson(e.getAsJsonObject());
                newRecipes.put(recipe.id(), recipe);
            }
            for (JsonElement e : readArray(ROUTES, "routes")) {
                Route route = Route.fromJson(e.getAsJsonObject());
                newRoutes.put(route.id(), route);
            }
            for (JsonElement e : readArray(UPGRADES, "upgrades")) {
                UpgradeNode node = UpgradeNode.fromJson(e.getAsJsonObject());
                newUpgrades.put(key(node.path(), node.level()), node);
            }
        } catch (Exception e) {
            String message = "reload failed, keeping previous catalog: " + e;
            WarehouseMod.LOGGER.error("[mwh] {}", message);
            return message;
        }

        if (newRecipes.isEmpty() || newRoutes.isEmpty()) {
            String message = "reload rejected: recipes and routes cannot both be empty";
            WarehouseMod.LOGGER.error("[mwh] {}", message);
            return message;
        }

        recipes.clear();
        recipes.putAll(newRecipes);
        routes.clear();
        routes.putAll(newRoutes);
        upgrades.clear();
        upgrades.putAll(newUpgrades);

        int unavailable = 0;
        for (Recipe recipe : recipes.values()) {
            if (!recipe.isAvailable()) {
                unavailable++;
            }
        }

        String message = recipes.size() + " recipes (" + unavailable + " missing items), "
                + routes.size() + " routes, " + upgrades.size() + " upgrade nodes";
        WarehouseMod.LOGGER.info("[mwh] Catalog loaded: {}", message);
        return message;
    }

    private JsonArray readArray(String file, String rootKey) throws IOException {
        Path path = configDir().resolve(file);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray array = root.getAsJsonArray(rootKey);
            if (array == null) {
                throw new IOException(file + " has no \"" + rootKey + "\" array");
            }
            return array;
        }
    }

    private static long key(UpgradePath path, int level) {
        return ((long) path.ordinal() << 32) | (level & 0xFFFFFFFFL);
    }

    /* ---------------------------------------------------------------- accessors */

    public List<Recipe> recipes() {
        return List.copyOf(recipes.values());
    }

    public Recipe recipe(String id) {
        return recipes.get(id);
    }

    public List<Route> routes() {
        return List.copyOf(routes.values());
    }

    public Route route(String id) {
        return routes.get(id);
    }

    public UpgradeNode upgrade(UpgradePath path, int level) {
        return upgrades.get(key(path, level));
    }

    public List<UpgradeNode> upgrades() {
        return List.copyOf(upgrades.values());
    }

    /** Every distinct ingredient item across all recipes, for diagnostics and the admin dump. */
    public Set<ResourceLocation> ingredients() {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (Recipe recipe : recipes.values()) {
            for (Ingredient part : recipe.ingredients()) {
                out.add(part.item());
            }
        }
        return out;
    }

    /** Ingredient ids naming an item that is not in the registry - the "fix these" list. */
    public List<ResourceLocation> missingIngredients() {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : ingredients()) {
            if (!net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** Pretty-prints a catalog object, for the admin export command. */
    public static String toJson(JsonElement element) {
        return GSON.toJson(element);
    }
}

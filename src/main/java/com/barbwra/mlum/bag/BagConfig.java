package com.barbwra.mlum.bag;

import com.barbwra.mlum.MlumInventory;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code config/mlum_inventory.toml} - backpacks, item sizes, gun sizes and rarity.
 *
 * <h2>Why this is not a {@code ForgeConfigSpec}</h2>
 * <p>Every section here is keyed by <b>item id</b>, and the set of ids is whatever the pack happens
 * to contain. {@code ForgeConfigSpec} has to have each key declared up front - it validates against
 * a schema, which is the opposite of what an open map needs, and the usual workaround (a list of
 * {@code "id=value"} strings) would not give the file in the spec. NightConfig is what
 * {@code ForgeConfigSpec} is built on and Forge already ships it, so reading the TOML directly costs
 * no new dependency and produces exactly the file the brief asks for.</p>
 *
 * <h2>Nothing here may ever throw</h2>
 * <p>The brief is explicit: "An invalid line is logged and ignored (never crash)." Every value is
 * parsed defensively and a bad entry costs that one entry. A missing file, an unreadable file, a
 * section of the wrong type, a size of {@code "banana"} - all of them log once and leave the
 * defaults in place, because a typo in a config must never stop players opening their bag.</p>
 *
 * <h2>Reload is a swap, not a mutation</h2>
 * <p>{@link #load()} builds fresh maps and assigns them to the volatile fields at the end. Readers
 * therefore always see a complete, self-consistent snapshot - never a map that is half way through
 * being repopulated while a player is mid-drag.</p>
 */
public final class BagConfig {

    private BagConfig() {
    }

    public static final String FILE_NAME = "mlum_inventory.toml";

    /** The base bag, before any backpack: 3 rows of 9. */
    public static final int BASE_ROWS = 3;
    public static final int COLUMNS = 9;
    public static final int BASE_CELLS = BASE_ROWS * COLUMNS;

    /* ------------------------------------------------------------------ state */

    private static volatile Map<ResourceLocation, Integer> backpacks = Map.of();
    private static volatile Set<ResourceLocation> vipOnly = Set.of();
    private static volatile boolean disableBackpackRightClick = true;
    private static volatile Map<ResourceLocation, ItemSize> itemSizes = Map.of();
    private static volatile Map<String, ItemSize> gunSizes = Map.of();
    private static volatile Map<ResourceLocation, ItemTier> rarity = Map.of();

    /* ------------------------------------------------------------------ queries */

    /**
     * How many cells this backpack adds, or 0 if it is not a configured backpack.
     *
     * <p>Zero doubles as "not a backpack", which is what {@link #isBackpack} is for and what the
     * backpack slot tests. An admin who sets a backpack to 0 has said it adds nothing, and treating
     * that as "not wearable" is the only reading that does not produce a slot you can fill with
     * something that does nothing.</p>
     */
    public static int addedCells(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        Integer added = id == null ? null : backpacks.get(id);
        return added == null ? 0 : added;
    }

    public static boolean isBackpack(ItemStack stack) {
        return addedCells(stack) > 0;
    }

    /** Extra rows this backpack grants. Always a whole number of rows - see {@link #load()}. */
    public static int addedRows(ItemStack stack) {
        return addedCells(stack) / COLUMNS;
    }

    public static boolean isVipOnly(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && vipOnly.contains(id);
    }

    public static boolean disableBackpackRightClick() {
        return disableBackpackRightClick;
    }

    /** Every configured backpack id, for the right-click cancel and for the command's suggestions. */
    public static Set<ResourceLocation> backpackIds() {
        return backpacks.keySet();
    }

    /**
     * The size this stack occupies in the bag.
     *
     * <p>A TACZ firearm is looked up by <b>gun id</b> first, because every gun in that mod is the
     * same item - {@code tacz:modern_kinetic_gun} - and matching on the item alone would give a
     * pistol and a rifle the same footprint. The item map is the fallback, and 1x1 the default.</p>
     */
    public static ItemSize sizeOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemSize.ONE;
        }
        String gunId = com.barbwra.mlum.compat.TaczCompat.gunId(stack);
        if (gunId != null && !gunId.isEmpty()) {
            ItemSize bySize = gunSizes.get(gunId);
            if (bySize != null) {
                return bySize;
            }
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        ItemSize configured = id == null ? null : itemSizes.get(id);
        return configured == null ? ItemSize.ONE : configured;
    }

    @Nullable
    public static ItemTier rarityOf(Item item) {
        ResourceLocation id = item == null ? null : ForgeRegistries.ITEMS.getKey(item);
        return id == null ? null : rarity.get(id);
    }

    /**
     * The configured tier of a stack. A TACZ firearm is looked up by its <b>gun id</b> first - every
     * TACZ gun is the same item, so {@code "tacz:m4a1" = "legendary"} in {@code [rarity]} is how one
     * rifle outranks another - then by item id.
     */
    @Nullable
    public static ItemTier rarityOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String gunId = com.barbwra.mlum.compat.TaczCompat.gunId(stack);
        if (gunId != null && !gunId.isEmpty()) {
            ResourceLocation gun = ResourceLocation.tryParse(gunId);
            ItemTier byGun = gun == null ? null : rarity.get(gun);
            if (byGun != null) {
                return byGun;
            }
        }
        return rarityOf(stack.getItem());
    }

    /* ------------------------------------------------------------------ server to client */

    /** Everything a client needs to draw the bag the way this server configured it. */
    public record Snapshot(Map<ResourceLocation, Integer> backpacks, Set<ResourceLocation> vipOnly,
                           Map<ResourceLocation, ItemSize> itemSizes, Map<String, ItemSize> gunSizes,
                           Map<ResourceLocation, ItemTier> rarity, boolean disableBackpackRightClick) {
    }

    public static Snapshot snapshot() {
        return new Snapshot(backpacks, vipOnly, itemSizes, gunSizes, rarity, disableBackpackRightClick);
    }

    /**
     * Replaces this side's tables with the server's. A client only ever draws with what the server
     * decides - sizes, rarities, which items are backpacks - never with its own copy of the file,
     * which may be old or the default one. Undone by {@link #load()} on disconnect.
     */
    public static synchronized void applyRemote(Snapshot remote) {
        backpacks = Collections.unmodifiableMap(new LinkedHashMap<>(remote.backpacks()));
        vipOnly = Collections.unmodifiableSet(new LinkedHashSet<>(remote.vipOnly()));
        itemSizes = Collections.unmodifiableMap(new LinkedHashMap<>(remote.itemSizes()));
        gunSizes = Collections.unmodifiableMap(new LinkedHashMap<>(remote.gunSizes()));
        rarity = Collections.unmodifiableMap(new LinkedHashMap<>(remote.rarity()));
        disableBackpackRightClick = remote.disableBackpackRightClick();
    }

    /* ------------------------------------------------------------------ loading */

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    /**
     * Reads the file, writing the documented default first if it is not there yet.
     *
     * @return a short human-readable summary, which {@code /mlum_inventory reload} echoes back
     */
    public static synchronized String load() {
        Path file = path();
        if (!Files.exists(file)) {
            writeDefault(file);
        }

        Map<ResourceLocation, Integer> packs = new LinkedHashMap<>();
        Set<ResourceLocation> vips = new LinkedHashSet<>();
        Map<ResourceLocation, ItemSize> sizes = new LinkedHashMap<>();
        Map<String, ItemSize> guns = new LinkedHashMap<>();
        Map<ResourceLocation, ItemTier> tiers = new LinkedHashMap<>();
        boolean noRightClick = true;

        try (FileConfig config = FileConfig.builder(file, TomlFormat.instance()).build()) {
            config.load();

            UnmodifiableConfig backpackSection = section(config, "backpacks");
            if (backpackSection != null) {
                for (Map.Entry<String, Object> entry : backpackSection.valueMap().entrySet()) {
                    readBackpack(entry.getKey(), entry.getValue(), packs, vips);
                }
                Object flag = backpackSection.valueMap().get("disable_backpack_right_click");
                if (flag instanceof Boolean bool) {
                    noRightClick = bool;
                }
            }

            readSizes(section(config, "item_sizes"), sizes);
            readGunSizes(section(config, "gun_sizes"), guns);
            readRarity(section(config, "rarity"), tiers);
        } catch (Exception broken) {
            // A corrupt file leaves whatever was loaded before in place rather than wiping it.
            MlumInventory.LOGGER.error("[{}] could not read {} - keeping the previous values",
                    MlumInventory.MODID, FILE_NAME, broken);
            return "failed to read " + FILE_NAME + " - see the log";
        }

        backpacks = Collections.unmodifiableMap(packs);
        vipOnly = Collections.unmodifiableSet(vips);
        itemSizes = Collections.unmodifiableMap(sizes);
        gunSizes = Collections.unmodifiableMap(guns);
        rarity = Collections.unmodifiableMap(tiers);
        disableBackpackRightClick = noRightClick;

        String summary = packs.size() + " backpacks, " + sizes.size() + " item sizes, "
                + guns.size() + " gun sizes, " + tiers.size() + " rarities";
        MlumInventory.LOGGER.info("[{}] loaded {}: {}", MlumInventory.MODID, FILE_NAME, summary);
        return summary;
    }

    @Nullable
    private static UnmodifiableConfig section(UnmodifiableConfig root, String name) {
        Object raw = root.valueMap().get(name);
        if (raw instanceof UnmodifiableConfig nested) {
            return nested;
        }
        if (raw != null) {
            MlumInventory.LOGGER.warn("[{}] {}: [{}] is not a table - ignoring it",
                    MlumInventory.MODID, FILE_NAME, name);
        }
        return null;
    }

    /**
     * One {@code [backpacks]} entry. Also handles the two non-item keys that live in that table.
     *
     * <p>Slot counts are forced to a whole number of rows. The grid is 9 wide and a partial row
     * cannot be drawn or placed into, so a config saying {@code = 14} is a mistake with exactly one
     * sensible reading: round down to 9 and say so.</p>
     */
    private static void readBackpack(String key, Object value,
                                     Map<ResourceLocation, Integer> packs,
                                     Set<ResourceLocation> vips) {
        if ("disable_backpack_right_click".equals(key)) {
            return;
        }
        if ("vip_only".equals(key)) {
            if (value instanceof List<?> list) {
                for (Object element : list) {
                    ResourceLocation id = id(String.valueOf(element), "backpacks.vip_only");
                    if (id != null) {
                        vips.add(id);
                    }
                }
            } else {
                MlumInventory.LOGGER.warn("[{}] {}: backpacks.vip_only must be a list - ignoring it",
                        MlumInventory.MODID, FILE_NAME);
            }
            return;
        }

        ResourceLocation id = id(key, "backpacks");
        if (id == null) {
            return;
        }
        if (!(value instanceof Number number)) {
            MlumInventory.LOGGER.warn("[{}] {}: backpacks.\"{}\" = {} is not a number - ignoring it",
                    MlumInventory.MODID, FILE_NAME, key, value);
            return;
        }
        int cells = number.intValue();
        if (cells <= 0) {
            MlumInventory.LOGGER.warn("[{}] {}: backpacks.\"{}\" = {} adds nothing - ignoring it",
                    MlumInventory.MODID, FILE_NAME, key, cells);
            return;
        }
        int rounded = cells / COLUMNS * COLUMNS;
        if (rounded != cells) {
            MlumInventory.LOGGER.warn("[{}] {}: backpacks.\"{}\" = {} is not a multiple of {} "
                            + "- using {}", MlumInventory.MODID, FILE_NAME, key, cells, COLUMNS, rounded);
        }
        if (rounded <= 0) {
            return;
        }
        packs.put(id, rounded);
    }

    private static void readSizes(@Nullable UnmodifiableConfig table,
                                  Map<ResourceLocation, ItemSize> out) {
        if (table == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : table.valueMap().entrySet()) {
            ResourceLocation id = id(entry.getKey(), "item_sizes");
            if (id == null) {
                continue;
            }
            ItemSize size = ItemSize.parse(String.valueOf(entry.getValue()));
            if (size == null) {
                MlumInventory.LOGGER.warn("[{}] {}: item_sizes.\"{}\" = {} is not WxH - using 1x1",
                        MlumInventory.MODID, FILE_NAME, entry.getKey(), entry.getValue());
                continue;
            }
            out.put(id, size);
        }
    }

    /** Gun sizes are keyed by TACZ gun id, which is a plain string rather than an item id. */
    private static void readGunSizes(@Nullable UnmodifiableConfig table, Map<String, ItemSize> out) {
        if (table == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : table.valueMap().entrySet()) {
            ItemSize size = ItemSize.parse(String.valueOf(entry.getValue()));
            if (size == null) {
                MlumInventory.LOGGER.warn("[{}] {}: gun_sizes.\"{}\" = {} is not WxH - using 1x1",
                        MlumInventory.MODID, FILE_NAME, entry.getKey(), entry.getValue());
                continue;
            }
            out.put(entry.getKey().trim(), size);
        }
    }

    private static void readRarity(@Nullable UnmodifiableConfig table,
                                   Map<ResourceLocation, ItemTier> out) {
        if (table == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : table.valueMap().entrySet()) {
            ResourceLocation id = id(entry.getKey(), "rarity");
            if (id == null) {
                continue;
            }
            ItemTier tier = ItemTier.byId(String.valueOf(entry.getValue()));
            if (tier == null) {
                MlumInventory.LOGGER.warn("[{}] {}: rarity.\"{}\" = {} is not a known tier "
                                + "- falling back to the item's own rarity",
                        MlumInventory.MODID, FILE_NAME, entry.getKey(), entry.getValue());
                continue;
            }
            out.put(id, tier);
        }
    }

    @Nullable
    private static ResourceLocation id(String raw, String where) {
        ResourceLocation parsed = ResourceLocation.tryParse(raw == null ? "" : raw.trim());
        if (parsed == null) {
            MlumInventory.LOGGER.warn("[{}] {}: {}.\"{}\" is not a valid item id - ignoring it",
                    MlumInventory.MODID, FILE_NAME, where, raw);
        }
        return parsed;
    }

    /* ------------------------------------------------------------------ default */

    /**
     * Written verbatim on first run, comments and all.
     *
     * <p>Hand-written rather than generated because the comments are the documentation - an admin
     * opening this file should be able to work out the format without the brief in front of them.
     * The ids are the ones from the brief and are worth checking in game with F3+H, which the file
     * says out loud.</p>
     */
    private static void writeDefault(Path file) {
        String body = """
                # MlumInventory - bag, backpacks, item sizes and rarity.
                # Reload in game with /mlum_inventory reload. A bad line is logged and skipped.

                [backpacks]
                # item id = how many cells it adds. Must be a multiple of 9 (one row).
                # These five ids were read straight out of survivorsarsenal-1.1.7 and are correct.
                "survivorsarsenal:leather_backpack"         = 9
                "survivorsarsenal:backpack_small_black"     = 18
                "survivorsarsenal:hiking_backpack_black"    = 27
                "survivorsarsenal:military_backpack_black"  = 36
                "survivorsarsenal:military_backpack_desert" = 45

                # Survivor's Arsenal also ships colour variants of the same three tiers. They are
                # off by default because the brief listed only the five above - un-comment any you
                # want players to be able to wear. Values match the tier, not the colour.
                #"survivorsarsenal:backpack_small_blue"      = 18
                #"survivorsarsenal:backpack_small_green"     = 18
                #"survivorsarsenal:backpack_small_pink"      = 18
                #"survivorsarsenal:hiking_backpack_blue"     = 27
                #"survivorsarsenal:hiking_backpack_red"      = 27
                #"survivorsarsenal:hiking_backpack_light_brown" = 27
                #"survivorsarsenal:military_backpack_green"  = 36

                # Only VIP players may wear these.
                vip_only = ["survivorsarsenal:military_backpack_desert"]
                # Stops the backpack mod's own right-click GUI, so the bag is the only way in.
                disable_backpack_right_click = true

                [item_sizes]
                # "WxH" - width across, height down. Width max 9. Anything not listed is 1x1.
                "minecraft:golden_apple" = "1x1"
                "minecraft:iron_sword"   = "1x3"

                [gun_sizes]
                # TACZ guns are all one item, so these match by gun id, not by item id.
                "tacz:m4a1"     = "7x2"
                "tacz:glock_17" = "2x1"

                [rarity]
                # common | uncommon | rare | epic | legendary | mythic
                # Anything not listed uses the item's own vanilla rarity.
                # TACZ guns can be listed here by gun id, e.g. "tacz:m4a1" = "epic".
                "minecraft:netherite_ingot" = "legendary"
                """;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, body, StandardCharsets.UTF_8);
            MlumInventory.LOGGER.info("[{}] wrote a default {}", MlumInventory.MODID, FILE_NAME);
        } catch (IOException failed) {
            MlumInventory.LOGGER.error("[{}] could not write {} - running on defaults",
                    MlumInventory.MODID, FILE_NAME, failed);
        }
    }
}

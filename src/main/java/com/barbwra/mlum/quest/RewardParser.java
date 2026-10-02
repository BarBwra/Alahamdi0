package com.barbwra.mlum.quest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the reward string a Skript command sends into real {@link ItemStack}s.
 *
 * <p>Accepted token shapes, separated by spaces or commas:</p>
 * <pre>
 *   minecraft:iron_ingot:5     namespace : path : count
 *   minecraft:iron_ingot       namespace : path            (count 1)
 *   iron_ingot:5               path : count                (assumes minecraft)
 *   iron_ingot                 path                        (assumes minecraft, count 1)
 * </pre>
 *
 * <p>The two-part form is ambiguous - {@code a:b} is either namespace:path or path:count - so it is
 * resolved by trying to read the second part as a number first. Unknown or malformed items are
 * skipped rather than throwing, because a typo in a Skript line should not kill the command.</p>
 */
public final class RewardParser {

    private RewardParser() {
    }

    public static List<ItemStack> parse(String raw) {
        List<ItemStack> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String token : raw.split("[,\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            ItemStack stack = parseToken(token.trim());
            if (!stack.isEmpty()) {
                out.add(stack);
                if (out.size() >= QuestEntry.MAX_REWARDS) {
                    break;
                }
            }
        }
        return out;
    }

    public static ItemStack parseToken(String token) {
        String id = token;
        int count = 1;

        int lastColon = token.lastIndexOf(':');
        if (lastColon > 0) {
            String tail = token.substring(lastColon + 1);
            Integer parsed = tryInt(tail);
            if (parsed != null) {
                count = parsed;
                id = token.substring(0, lastColon);
            }
        }
        if (id.indexOf(':') < 0) {
            id = "minecraft:" + id;
        }

        // tryParse returns null instead of throwing on a malformed id
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(location);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, Mth.clamp(count, 1, 999));
    }

    private static Integer tryInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

package com.barbwra.mlum.client.hud;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.compat.TaczCompat;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the flat 2D artwork TACZ ships for a gun, without compiling against TACZ.
 *
 * <p><b>Why this is possible.</b> TACZ loads its gun packs by registering reload listeners against
 * the game's own {@code ResourceManager}, which means a pack's {@code assets/} tree is mounted like
 * any other resource pack. So {@code assets/tacz/display/guns/ak47_display.json} and the PNG it
 * points at are both readable from here with no TACZ classes involved.</p>
 *
 * <p><b>Three attempts, then give up gracefully.</b> The display file is authoritative and is tried
 * first, because a gun pack is free to name its artwork anything - the stock pack alone has both
 * {@code b93r_hud} and {@code ak47}. Failing that, the conventional path is tried. Failing that,
 * {@link #hudTexture} answers null and the card draws the item itself, which for a TACZ gun is its
 * own 2D inventory icon and still reads as the right weapon.</p>
 *
 * <p>Every answer is cached per gun id, misses included, so a gun with no artwork costs one failed
 * lookup for the session rather than one per frame.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class TaczGunHud {

    private TaczGunHud() {
    }

    /** Gun id to artwork, or to {@link #NONE} when it has been looked for and is not there. */
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static final ResourceLocation NONE = new ResourceLocation(MlumInventory.MODID, "none");

    /** Cleared on resource reload, since a pack change can add or remove artwork. */
    public static void clearCache() {
        CACHE.clear();
        com.barbwra.mlum.client.gui.ArtSize.clear();
    }

    /**
     * The gun's flat artwork, or null when there is none and the caller should draw the item.
     *
     * <p>{@code empty} asks for the alternate art TACZ ships for a dry weapon, which is how its own
     * readout shows that you are out of rounds.</p>
     */
    @Nullable
    public static ResourceLocation hudTexture(ItemStack stack, boolean empty) {
        String gunId = TaczCompat.gunId(stack);
        if (gunId.isEmpty()) {
            return null;
        }
        String key = gunId + (empty ? "#empty" : "");
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached == NONE ? null : cached;
        }

        ResourceLocation found = resolve(gunId, empty);
        CACHE.put(key, found == null ? NONE : found);
        return found;
    }

    @Nullable
    private static ResourceLocation resolve(String gunId, boolean empty) {
        ResourceLocation id = ResourceLocation.tryParse(gunId);
        if (id == null) {
            return null;
        }

        ResourceLocation fromDisplay = fromDisplayFile(id, empty);
        if (fromDisplay != null && exists(fromDisplay)) {
            return fromDisplay;
        }
        // the stock pack's own convention, which most add-on packs copy
        ResourceLocation guess = new ResourceLocation(id.getNamespace(),
                "textures/gun/hud/" + id.getPath() + (empty ? "_empty" : "") + ".png");
        return exists(guess) ? guess : null;
    }

    /**
     * Reads {@code display/guns/<name>_display.json} and pulls its {@code hud} entry, which is a
     * bare id like {@code tacz:gun/hud/ak47} and expands to a normal texture path.
     */
    @Nullable
    private static ResourceLocation fromDisplayFile(ResourceLocation gunId, boolean empty) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getResourceManager() == null) {
            return null;
        }
        ResourceLocation file = new ResourceLocation(gunId.getNamespace(),
                "display/guns/" + gunId.getPath() + "_display.json");
        try {
            Optional<Resource> resource = minecraft.getResourceManager().getResource(file);
            if (resource.isEmpty()) {
                return null;
            }
            try (BufferedReader reader = resource.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                String field = empty ? "hud_empty" : "hud";
                if (!root.has(field) || !root.get(field).isJsonPrimitive()) {
                    return null;
                }
                ResourceLocation texture = ResourceLocation.tryParse(root.get(field).getAsString());
                if (texture == null) {
                    return null;
                }
                return new ResourceLocation(texture.getNamespace(),
                        "textures/" + texture.getPath() + ".png");
            }
        } catch (Exception unreadable) {
            // a malformed or missing display file is a normal outcome for a third party pack
            return null;
        }
    }

    private static boolean exists(ResourceLocation texture) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getResourceManager() != null
                && minecraft.getResourceManager().getResource(texture).isPresent();
    }
}

package com.barbwra.mlum.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Width-to-height ratio of a texture, read once from its PNG header and cached.
 *
 * <p>Firearm artwork comes from whichever gun packs are installed and every pack draws its own
 * proportions. Stretching all of them into one box squashes some rifles and stretches others;
 * knowing the real ratio lets them be fitted the way CSS {@code background-size:contain} does.</p>
 *
 * <p>Cleared on resource reload together with the artwork lookup, since a pack change can replace
 * the file behind a name.</p>
 */
public final class ArtSize {

    private ArtSize() {
    }

    private static final float FALLBACK = 3.0F;
    private static final Map<ResourceLocation, Float> CACHE = new HashMap<>();

    public static void clear() {
        CACHE.clear();
    }

    public static float aspect(ResourceLocation texture) {
        Float cached = CACHE.get(texture);
        if (cached != null) {
            return cached;
        }
        float aspect = FALLBACK;
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                    if (image.getWidth() > 0 && image.getHeight() > 0) {
                        aspect = (float) image.getWidth() / image.getHeight();
                    }
                }
            }
        } catch (Exception unreadable) {
            // keep the fallback; a picture drawn slightly off-ratio beats no picture at all
        }
        CACHE.put(texture, aspect);
        return aspect;
    }
}

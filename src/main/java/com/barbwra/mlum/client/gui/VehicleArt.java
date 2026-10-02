package com.barbwra.mlum.client.gui;

import com.barbwra.mlum.MlumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * What a vehicle looks like in the garage: a configured image, or a drawn silhouette.
 *
 * <p><b>Why there is no entity here at all.</b> The garage used to build the real vehicle and hand
 * it to the game's renderer. That works for a Horse, which carries its own model, and fails for
 * every data-driven vehicle mod - {@code EntityType.create} returns a blank entity with no
 * definition attached, and the renderer has nothing to draw. No amount of seeding position,
 * rotation or age fixes it, because the missing thing is the vehicle's identity. That is the whole
 * reason the log filled up with "cannot render ywzj_vehicle:motorcycle".</p>
 *
 * <p>A picture cannot fail that way. The config names one per vehicle; anything without one falls
 * back to a shape drawn from a bitmap, which is not a placeholder awaiting artwork but the
 * permanent answer for a vehicle nobody has drawn yet.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class VehicleArt {

    private VehicleArt() {
    }

    /** Entity id to texture, or {@link #NONE} once it has been looked for and is not there. */
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static final ResourceLocation NONE = new ResourceLocation("mlum", "none");

    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * The image configured for this vehicle, or null to fall back to a silhouette.
     *
     * <p>The config value is a plain texture path - {@code mlum:textures/vehicle/z10.png} - so it
     * resolves the same whether the file ships inside this jar or arrives in a resource pack. That
     * is deliberate: moving the artwork later is a change of file location, never of code.</p>
     */
    @Nullable
    public static ResourceLocation imageFor(String entityId) {
        ResourceLocation cached = CACHE.get(entityId);
        if (cached != null) {
            return cached == NONE ? null : cached;
        }
        String path = MlumConfig.vehicleImages().get(entityId);
        ResourceLocation found = null;
        if (path != null && !path.isBlank()) {
            ResourceLocation id = ResourceLocation.tryParse(path.trim());
            if (id != null && exists(id)) {
                found = id;
            }
        }
        CACHE.put(entityId, found == null ? NONE : found);
        return found;
    }

    private static boolean exists(ResourceLocation texture) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getResourceManager() != null
                && minecraft.getResourceManager().getResource(texture).isPresent();
    }
}

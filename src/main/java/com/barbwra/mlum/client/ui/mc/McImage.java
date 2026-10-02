package com.barbwra.mlum.client.ui.mc;

import com.barbwra.mlum.client.gui.VehicleArt;
import com.barbwra.mlum.client.gui.VehicleSilhouette;
import com.barbwra.mlum.client.ui.text.Raster;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * A picture the views ask the canvas to draw: a texture file, or - for a vehicle nobody has drawn -
 * its silhouette baked into a raster once and tinted.
 */
@OnlyIn(Dist.CLIENT)
public final class McImage {

    public final ResourceLocation texture;
    public final Raster shape;
    public final int tint;

    private McImage(ResourceLocation texture, Raster shape, int tint) {
        this.texture = texture;
        this.shape = shape;
        this.tint = tint;
    }

    public static McImage of(ResourceLocation texture) {
        return new McImage(texture, null, 0xFFFFFFFF);
    }

    private static final Map<String, McImage> VEHICLES = new HashMap<>();

    /** The garage picture for a vehicle: its configured image, else a silhouette picked from its name. */
    public static McImage vehicle(String entityId, String name) {
        String key = entityId + "|" + name;
        McImage cached = VEHICLES.get(key);
        if (cached != null) {
            return cached;
        }
        McImage made;
        ResourceLocation art = VehicleArt.imageFor(entityId);
        if (art != null) {
            made = of(art);
        } else {
            made = new McImage(null, bake(VehicleSilhouette.forVehicle(entityId, name)), 0xFF9EA397);
        }
        VEHICLES.put(key, made);
        return made;
    }

    public static void clearCache() {
        VEHICLES.clear();
    }

    /** '#' cells become opaque white pixels, everything else transparent. */
    static Raster bake(String[] rows) {
        int h = rows.length;
        int w = 0;
        for (String r : rows) {
            w = Math.max(w, r.length());
        }
        int[] px = new int[Math.max(1, w * h)];
        for (int y = 0; y < h; y++) {
            String r = rows[y];
            for (int x = 0; x < r.length(); x++) {
                if (r.charAt(x) == '#') {
                    px[y * w + x] = 0xFFFFFFFF;
                }
            }
        }
        return new Raster(w, h, px, true, 0, 0);
    }
}

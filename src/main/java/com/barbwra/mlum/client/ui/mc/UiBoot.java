package com.barbwra.mlum.client.ui.mc;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.text.Fonts;
import com.barbwra.mlum.client.ui.text.TextEngine;
import com.barbwra.mlum.util.ArabicText;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Starts the UI the first time a menu opens: loads the five font files, points the text engine's
 * fallback at the game font, and hooks the atlas up to the raster caches.
 *
 * <p>Lazy on purpose. Nothing here runs at game start, so a player who never opens the bag pays
 * nothing, and a broken font file is found - and logged, and survived - at the moment it matters
 * rather than during loading.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class UiBoot {

    private UiBoot() {
    }

    private static boolean booted;

    public static void ensure() {
        if (booted) {
            return;
        }
        booted = true;
        // AWT only ever rasterises into images here. Saying so up front keeps it from reaching for
        // the window system - which on macOS is already owned by the game's own window.
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true");
        }
        Atlas.install();
        TextEngine.setFallbackMeasure((text, size, weight, pixel) -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.font.width(ArabicText.autoDisplay(text)) * size / 9.0F;
        });
        long t0 = System.nanoTime();
        boolean ok = Fonts.load(UiBoot::open);
        if (ok) {
            MlumInventory.LOGGER.info("[{}] UI fonts loaded in {} ms", MlumInventory.MODID,
                    (System.nanoTime() - t0) / 1_000_000L);
        } else {
            MlumInventory.LOGGER.error("[{}] UI fonts failed to load - the menus fall back to the game font",
                    MlumInventory.MODID, Fonts.failure());
        }
        TextEngine.setScale(Px.s);
    }

    /** From the resource manager first (so a resource pack may replace them), then from the jar. */
    private static InputStream open(String file) throws IOException {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getResourceManager() != null) {
                Optional<Resource> res = mc.getResourceManager()
                        .getResource(new ResourceLocation(MlumInventory.MODID, "ui/fonts/" + file));
                if (res.isPresent()) {
                    return res.get().open();
                }
            }
        } catch (Exception ignored) {
            // fall through to the class path
        }
        InputStream in = UiBoot.class.getResourceAsStream("/assets/" + MlumInventory.MODID + "/ui/fonts/" + file);
        if (in == null) {
            throw new IOException("missing " + file);
        }
        return in;
    }
}

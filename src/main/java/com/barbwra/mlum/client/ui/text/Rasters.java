package com.barbwra.mlum.client.ui.text;

import java.util.function.Consumer;

/** The one hook between the raster caches and whoever uploads rasters to the GPU. */
public final class Rasters {

    private static Consumer<Raster> releaser = r -> { };

    private Rasters() {
    }

    public static void setReleaser(Consumer<Raster> consumer) {
        releaser = consumer == null ? r -> { } : consumer;
    }

    /** Called when a cached raster is dropped. Frees whatever the painter made from it. */
    public static void release(Raster raster) {
        if (raster != null && raster.handle != null) {
            try {
                releaser.accept(raster);
            } finally {
                raster.handle = null;
            }
        }
    }
}

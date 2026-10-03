package com.barbwra.mlum.client.ui;

import com.barbwra.mlum.client.ui.text.Raster;
import com.barbwra.mlum.client.ui.text.Shaped;

/**
 * Everything the UI ever draws, in <b>device pixels</b>.
 *
 * <p>Two implementations: the game's (GuiGraphics, one texture atlas, batched quads) and a Java2D
 * one used to render the screens to PNG on a desktop and diff them against the HTML design. The
 * view code only ever talks to this interface, so what the preview shows is what the game draws.</p>
 */
public interface Canvas {

    /** Solid rectangle, [x0,x1) x [y0,y1). */
    void fill(int x0, int y0, int x1, int y1, int argb);

    /** Four-corner gradient: top-left, top-right, bottom-right, bottom-left. */
    void gradient(int x0, int y0, int x1, int y1, int tl, int tr, int br, int bl);

    /**
     * A raster stretched over a rectangle, sampled nearest-neighbour. {@code tint} multiplies it; a
     * mask raster (white + alpha) takes its colour entirely from the tint.
     */
    void raster(Raster raster, float u0, float v0, float u1, float v1, int x0, int y0, int x1, int y1, int tint);

    /**
     * A game item (the painter knows the concrete type) fitted into a box: a square icon centred
     * at a whole-number scale, or wide artwork (a gun's HUD image) contained.
     */
    void item(Object item, int x0, int y0, int x1, int y1, int tint);

    /** An external picture - a vehicle image, a gun's HUD art - stretched over the rectangle. */
    void image(Object image, int x0, int y0, int x1, int y1, int tint);

    /** The player's own model, standing in the rectangle, looking at the mouse. */
    void player(int x0, int y0, int x1, int y1, int mouseX, int mouseY);

    /** Another living entity in the world - a downed player being looted - standing in the rectangle. */
    default void body(int entityId, int x0, int y0, int x1, int y1) {
    }

    /**
     * Any registered entity's real model, fitted into the rectangle - a vehicle in the garage.
     *
     * @param entityId the registry id, e.g. {@code superbwarfare:speedboat}
     * @return false when the type is not installed or its renderer refused, so the caller can draw
     *         the flat picture instead
     */
    default boolean entity(String entityId, int x0, int y0, int x1, int y1) {
        return false;
    }

    /** Text the painter has to draw itself because the UI fonts are unavailable. */
    void fallbackText(Shaped shaped, int penX, int baseline, int argb);

    void pushClip(int x0, int y0, int x1, int y1);

    void popClip();

    /** Moves everything drawn after it nearer the viewer - game items write depth. */
    void pushZ(float dz);

    void popZ();

    /** Multiplies the alpha of everything drawn until the matching {@link #popAlpha}. */
    void pushAlpha(float alpha);

    void popAlpha();

    /** Current device-pixel offset applied to every draw (the static-transition jitter). */
    void setOffset(int dx, int dy);
}

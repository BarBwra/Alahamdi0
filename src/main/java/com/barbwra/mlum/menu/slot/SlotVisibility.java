package com.barbwra.mlum.menu.slot;

/**
 * Lets the bag screen draw its own slots.
 *
 * <p>Vanilla renders every active slot as a 16px item at the slot's baked position, and that
 * render is private in 1.20.1 - it cannot be resized. The bag design wants 44px quick-access
 * slots, 52px gear sockets and a firearm card with artwork, so while the bag screen runs its
 * vanilla render pass it raises {@link #hidden}; the slot classes below report themselves inactive
 * for exactly that pass, vanilla skips them, and the screen draws them at their real size.</p>
 *
 * <p>Only ever set on the render thread, and only for the duration of one
 * {@code super.render} call. Clicks, key presses and the server never see it raised, so slot
 * hit-testing, quick-move and syncing are untouched.</p>
 */
public final class SlotVisibility {

    private SlotVisibility() {
    }

    public static boolean hidden;
}

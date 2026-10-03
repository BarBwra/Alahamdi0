package com.barbwra.mlum.client.ui.mc;

import com.barbwra.mlum.client.ui.layout.Node;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** What a menu screen hands the frame driver: which tab it is, and its content for this frame. */
@OnlyIn(Dist.CLIENT)
public interface UiPage {

    /** Index into {@code Chrome.TAB_IDS}: 0 bag, 1 quests, 2 level, 3 skills, 4 vehicles, 5 faction. */
    int tab();

    /** The bag keeps a clear window in the middle of the shade; every other tab is a flat shade. */
    boolean bagScrim();

    /** {@code .main} for this frame, built from the model with {@code hover} already applied. */
    Node main(String hover);

    /** The tab bar and the key hints along the bottom. A page that stands on its own turns them off. */
    default boolean chrome() {
        return true;
    }

    /** A dialog over the page, or null. */
    default Node modal(String hover) {
        return null;
    }

    /** The carried item under the cursor (framebuffer pixels), or null. */
    default Node ghost(int mouseX, int mouseY) {
        return null;
    }
}
